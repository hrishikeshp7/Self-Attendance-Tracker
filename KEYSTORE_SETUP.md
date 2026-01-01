# Android App Signing Key Setup Guide

## Problem Fixed

Previously, the GitHub Actions workflow was generating a **new keystore file for every release**. This caused Android to treat each release as a completely different app, resulting in the error:

```
App not installed as it conflicts with other package
```

Users could not upgrade from one release to another and had to uninstall the old version before installing the new one, losing all their data in the process.

## Solution

The workflow has been updated to use a **persistent keystore** stored in GitHub Secrets. This ensures all releases are signed with the same certificate, allowing seamless upgrades.

## Setup Instructions for Repository Owner

### Step 1: Generate a Keystore (One-time only)

Run this command to generate a keystore file. **Save this file securely - you'll need it for all future releases!**

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YourSecurePassword \
  -keypass YourSecureKeyPassword \
  -dname "CN=AttendanceTracker, OU=Development, O=AttendanceTracker, L=City, ST=State, C=US"
```

**Important Notes:**
- Replace `YourSecurePassword` with a strong password for the keystore
- Replace `YourSecureKeyPassword` with a strong password for the key
- You can customize the `-dname` fields with your actual information
- **Keep this keystore file safe!** If you lose it, you won't be able to update the app for existing users

### Step 2: Encode the Keystore to Base64

```bash
base64 release.keystore > release.keystore.base64
```

On macOS, you might need to use:
```bash
base64 -i release.keystore -o release.keystore.base64
```

### Step 3: Add Secrets to GitHub Repository

1. Go to your GitHub repository
2. Click on **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** and add the following secrets:

   - **Secret Name:** `RELEASE_KEYSTORE_BASE64`
     - **Value:** Copy and paste the entire contents of the `release.keystore.base64` file

   - **Secret Name:** `RELEASE_KEYSTORE_PASSWORD`
     - **Value:** The password you used for `-storepass` (e.g., "YourSecurePassword")

   - **Secret Name:** `RELEASE_KEY_ALIAS`
     - **Value:** `release` (or whatever you used for `-alias`)

   - **Secret Name:** `RELEASE_KEY_PASSWORD`
     - **Value:** The password you used for `-keypass` (e.g., "YourSecureKeyPassword")

### Step 4: Backup Your Keystore

**CRITICAL:** Store the `release.keystore` file in a secure location:
- Password manager
- Encrypted backup drive
- Secure cloud storage

If you lose this file, you will **never** be able to update your app for existing users. They will have to uninstall and lose all their data.

### Step 5: Secure Cleanup

After adding secrets to GitHub, securely delete the local keystore files:

**On Linux:**
```bash
shred -vfz -n 3 release.keystore
shred -vfz -n 3 release.keystore.base64
```

**On macOS:**
```bash
rm -P release.keystore
rm -P release.keystore.base64
```

**On Windows (PowerShell):**
```powershell
# Overwrite with random data before deletion
$file = "release.keystore"
$bytes = New-Object byte[] (Get-Item $file).Length
(New-Object Random).NextBytes($bytes)
[IO.File]::WriteAllBytes($file, $bytes)
Remove-Item $file -Force

# Repeat for .base64 file
$file = "release.keystore.base64"
$bytes = New-Object byte[] (Get-Item $file).Length
(New-Object Random).NextBytes($bytes)
[IO.File]::WriteAllBytes($file, $bytes)
Remove-Item $file -Force
```

These commands securely overwrite the file contents before deletion, making recovery much more difficult.

## How It Works

### Before (Broken)
```
Release 1: Generate new keystore → Sign APK with Key A
Release 2: Generate new keystore → Sign APK with Key B
Result: Key A ≠ Key B → Installation fails!
```

### After (Fixed)
```
Release 1: Use persistent keystore → Sign APK with Key X
Release 2: Use persistent keystore → Sign APK with Key X
Result: Key X = Key X → Installation succeeds! ✓
```

## Verification

After setting up the secrets, the next release build will:
1. Check for `RELEASE_KEYSTORE_BASE64` secret
2. If found, decode it and use it for signing
3. If not found, show a warning and generate a temporary keystore (for testing/forks)

To verify it's working:
1. Trigger a new release build from GitHub Actions
2. Check the workflow logs
3. You should see: "Using persistent keystore from secrets..."
4. If you see the warning message, the secrets are not configured correctly

## Troubleshooting

### "App not installed" error still occurs
- Verify all four secrets are configured correctly
- Check that the keystore passwords match what was used during generation
- Ensure the base64 encoding is complete (no truncated content)

### "Keystore was tampered with" error
- The RELEASE_KEYSTORE_PASSWORD is incorrect
- Re-encode the keystore and update the secret

### "Cannot recover key" error
- The RELEASE_KEY_PASSWORD is incorrect
- Verify you're using the correct key password

## For Existing Users

**Important:** If releases were already made with different signing keys:

1. Users who installed **any previous release** will need to uninstall the app before installing the new properly-signed release
2. This is a **one-time inconvenience** - after this, all future updates will work seamlessly
3. Consider communicating this to users via:
   - Release notes
   - App description
   - Social media/communication channels

After this setup, all future releases will be properly signed with the same key, and users will be able to update normally through Android's built-in update mechanism.

## Additional Resources

- [Android Developer Guide: Sign Your App](https://developer.android.com/studio/publish/app-signing)
- [Keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [GitHub Actions Encrypted Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
