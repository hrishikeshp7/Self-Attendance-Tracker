# Research Report: Google Drive Backup & Restore Implementation

**Project:** Self-Attendance-Tracker  
**Date:** 2026-11-06  
**Status:** Ready for Implementation  

---

## 1. Executive Summary

The current "Google Drive Backup" implementation in [BackupRestoreScreen.kt](../app/src/main/java/com/attendance/tracker/ui/screens/backup/BackupRestoreScreen.kt) does not actually interface with Google Drive. Instead, it launches the system Storage Access Framework (SAF) document picker with an advisory path hint (`EXTRA_INITIAL_URI`) pointing to the Google Drive package.

On modern Android versions (Android 11+) and various OEM skins (Samsung One UI, Xiaomi HyperOS, OnePlus OxygenOS), this hint is ignored, redirecting users to local storage or recent files. Furthermore:
- The user must manually navigate to Drive, create/select folders, and name files.
- The app has no concept of cloud backup state (timestamps, sizes, presence of existing backups).
- Cloud restore is nonexistent; users must manually search their local or cloud storage via `OpenDocument()`.
- Android OS-level Auto Backup ([data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml)) explicitly excludes the Room database, meaning device-to-device Google Drive transfers omit user attendance data entirely.

This report outlines the architecture, requirements, and concrete code changes to build a **true, one-tap Google Drive cloud backup and restore system**.

---

## 2. Root Cause Analysis

### 2.1 Storage Access Framework (SAF) Limitation

Current code in `BackupRestoreScreen.kt`:
```kotlin
val googleDriveExportLauncher = rememberLauncherForActivityResult(
    contract = object : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, input)
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildRootUri(
                        "com.google.android.apps.docs.storage",
                        "mydrive"
                    )
                )
            }
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
)
```

1. **Advisory Hint:** `DocumentsContract.EXTRA_INITIAL_URI` is documented by Android as a suggestion to the system file picker. The file picker is never obligated to honor it.
2. **Provider Authority Discrepancy:** On devices where Google Drive is not configured as the active document provider or where permissions/sandboxing restrict cross-authority URI resolution, Android defaults to `Downloads/` or device root.
3. **Manual Burden:** Even when Drive opens, the user must manually approve the upload path, breaking the seamless in-app backup experience.

### 2.2 Incomplete Android OS-Level Cloud Backup

In [data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml) and [backup_rules.xml](../app/src/main/res/xml/backup_rules.xml):
```xml
<cloud-backup>
    <include domain="sharedpref" path="."/>
    <exclude domain="sharedpref" path="device.xml"/>
</cloud-backup>
```
The database domain (`<include domain="database" path="."/>`) is omitted. When users get a new device and restore from Google Cloud, their preferences restore but attendance records and subjects are lost.

---

## 3. Architecture & Target Design

```
┌─────────────────────────────────────────────────────────────┐
│                   AttendanceTracker App                     │
│                                                             │
│  ┌───────────────────────┐        ┌──────────────────────┐  │
│  │   BackupRestoreScreen │        │  AttendanceViewModel │  │
│  │   - Account header    │───────>│  - Backup state flow │  │
│  │   - 1-tap Backup Now  │        │  - Restore action    │  │
│  │   - 1-tap Restore     │        └──────────┬───────────┘  │
│  └───────────────────────┘                   │              │
│                                              ▼              │
│                            ┌─────────────────────────────┐  │
│                            │     GoogleDriveService      │  │
│                            │ (REST v3 + OkHttp + Coroutines)│
│                            └──────────────┬──────────────┘  │
└───────────────────────────────────────────┼─────────────────┘
                                            │ OAuth 2.0 Token
                                            ▼
                     ┌────────────────────────────────────────┐
                     │          Google Drive REST API         │
                     │  - https://www.googleapis.com/drive/v3 │
                     │  - Space: appDataFolder                │
                     └────────────────────────────────────────┘
```

### Storage Location Comparison

| Factor | Option A: `appDataFolder` (Recommended) | Option B: `drive.file` (Visible Folder) |
|---|---|---|
| **OAuth Scope** | `https://www.googleapis.com/auth/drive.appdata` | `https://www.googleapis.com/auth/drive.file` |
| **Location** | Hidden application sandbox on Drive | User-visible folder (e.g., `AttendanceTracker/`) |
| **Tamper Proof** | Cannot be deleted or renamed accidentally by user | User can edit, move, or delete via Drive app |
| **User Experience** | Seamless 1-tap sync; zero clutter | User can see and download `.json` files manually |
| **Verification** | Low risk; restricted to app-only data | Low risk; restricted to files created by the app |

> [!TIP]
> Use **`appDataFolder`** for standard cloud backup/restore. The app already provides "Export to JSON" via SAF for users who want to manually handle their backup files.

---

## 4. Prerequisites (Google Cloud Console)

To authenticate and talk to Google Drive API:

1. **Google Cloud Project**:
   - Create a project at [Google Cloud Console](https://console.cloud.google.com/).
   - Navigate to **APIs & Services > Library**, search for **Google Drive API**, and click **Enable**.
2. **OAuth Consent Screen**:
   - Set User Type to **External**.
   - Fill in App Name ("Attendance Tracker") and Developer Contact Email.
   - Add scope: `https://www.googleapis.com/auth/drive.appdata`.
3. **OAuth 2.0 Client ID (Android)**:
   - Create Credentials -> **OAuth client ID** -> **Android**.
   - Package Name: `com.attendance.tracker`.
   - Add **SHA-1 Fingerprint**:
     - For debug: Run `./gradlew signingReport` (from debug keystore).
     - For release: Extract SHA-1 from `release.keystore` (used in GitHub Actions `.github/workflows/release.yml`).

---

## 5. Technical Implementation Details

### Step 1: Update Dependencies

In `app/build.gradle.kts`:

```kotlin
dependencies {
    // Google Sign-In & Play Services Auth
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Lightweight HTTP Client for Drive REST API (avoids heavy Google Java Client SDK)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Step 2: Implement Google Drive REST Service

Create `app/src/main/java/com/attendance/tracker/backup/GoogleDriveService.kt`:

```kotlin
package com.attendance.tracker.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GoogleDriveService(private val client: OkHttpClient = OkHttpClient()) {

    data class DriveBackupMeta(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val size: Long
    )

    /** Queries the most recent backup file stored in appDataFolder. */
    suspend fun getLatestBackup(token: String): DriveBackupMeta? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files?" +
                "spaces=appDataFolder&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime desc&pageSize=1"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val files = json.optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) return@withContext null
            val f = files.getJSONObject(0)
            DriveBackupMeta(
                id = f.getString("id"),
                name = f.getString("name"),
                modifiedTime = f.optString("modifiedTime", ""),
                size = f.optLong("size", 0L)
            )
        }
    }

    /** Uploads a new backup or updates existing file in appDataFolder. */
    suspend fun uploadBackup(token: String, filename: String, jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        val existing = getLatestBackup(token)

        val metadata = JSONObject().apply {
            put("name", filename)
            if (existing == null) {
                put("parents", org.json.JSONArray(listOf("appDataFolder")))
            }
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                filename,
                jsonContent.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .build()

        val url = if (existing != null) {
            "https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=multipart"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(if (existing != null) "PATCH" else "POST", body)
            .build()

        client.newCall(request).execute().use { it.isSuccessful }
    }

    /** Downloads backup file content by file ID. */
    suspend fun downloadBackup(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }
}
```

### Step 3: Implement Auth Helper

Create `app/src/main/java/com/attendance/tracker/backup/GoogleAuthHelper.kt`:

```kotlin
package com.attendance.tracker.backup

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun getClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(context, account.account!!, "oauth2:$DRIVE_APPDATA_SCOPE")
            } catch (e: Exception) {
                null
            }
        }
}
```

### Step 4: ViewModel Integration

In `AttendanceViewModel.kt`:

1. **State:**
   ```kotlin
   private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(null)
   val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount.asStateFlow()

   private val _lastCloudBackup = MutableStateFlow<GoogleDriveService.DriveBackupMeta?>(null)
   val lastCloudBackup: StateFlow<GoogleDriveService.DriveBackupMeta?> = _lastCloudBackup.asStateFlow()
   ```

2. **Actions:**
   - `backupToGoogleDrive(context: Context)`:
     Generates JSON via `createJsonBackup()`, obtains OAuth token via `GoogleAuthHelper.getAccessToken()`, calls `GoogleDriveService.uploadBackup()`, and refreshes `_lastCloudBackup`.
   - `restoreFromGoogleDrive(context: Context)`:
     Fetches `getLatestBackup()`, downloads JSON content, and delegates directly to existing `restoreFromJson(json)` which already executes Room transaction rollback on failure.

### Step 5: Screen UI Updates

In `BackupRestoreScreen.kt`:
- Remove `googleDriveExportLauncher` SAF hack.
- Add Google Account Card:
  - When disconnected: "Connect Google Drive" button.
  - When connected: Displays "Connected as `user@example.com`" with a disconnect option.
- Add Dedicated Cloud Cards:
  - **Backup to Drive:** Shows last backup date/size and "Backup Now" button.
  - **Restore from Drive:** Shows latest found cloud backup and "Restore" button with confirmation alert dialog.
- Preserve local JSON/CSV export buttons as offline fallbacks.

### Step 6: Fix Android OS Auto-Backup

In `app/src/main/res/xml/data_extraction_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="."/>
        <include domain="database" path="."/>
        <exclude domain="sharedpref" path="device.xml"/>
    </cloud-backup>
</data-extraction-rules>
```

In `app/src/main/res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="sharedpref" path="."/>
    <include domain="database" path="."/>
    <exclude domain="sharedpref" path="device.xml"/>
</full-backup-content>
```

---

## 6. Implementation Checklist for Tomorrow

- [ ] Register Google Cloud Console Project and generate Android OAuth 2.0 Client ID with package `com.attendance.tracker` + debug/release SHA-1.
- [ ] Add `play-services-auth` and `okhttp` to `app/build.gradle.kts`.
- [ ] Create `GoogleDriveService.kt` and `GoogleAuthHelper.kt` in `backup/`.
- [ ] Wire StateFlows and backup/restore methods into `AttendanceViewModel.kt`.
- [ ] Update `BackupRestoreScreen.kt` with Google Sign-in launcher and Drive cards.
- [ ] Update `data_extraction_rules.xml` and `backup_rules.xml` to include `database`.
- [ ] Verify test suite passes (`./gradlew testDebugUnitTest`).
