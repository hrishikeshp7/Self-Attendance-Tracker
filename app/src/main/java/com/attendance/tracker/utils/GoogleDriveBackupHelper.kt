package com.attendance.tracker.utils

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for Google Drive backup and restore operations
 */
object GoogleDriveBackupHelper {
    
    private const val BACKUP_FOLDER_NAME = "AttendanceTrackerBackups"
    private const val DATABASE_NAME = "attendance_database"
    
    /**
     * Get Google Sign-In client configured for Drive access
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        
        return GoogleSignIn.getClient(context, signInOptions)
    }
    
    /**
     * Get the sign-in intent for authentication
     */
    fun getSignInIntent(context: Context): Intent {
        return getGoogleSignInClient(context).signInIntent
    }
    
    /**
     * Get currently signed-in account
     */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Sign out from Google Drive
     */
    suspend fun signOut(context: Context) {
        withContext(Dispatchers.IO) {
            getGoogleSignInClient(context).signOut()
        }
    }
    
    /**
     * Get Drive service for the signed-in account
     */
    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Attendance Tracker")
            .build()
    }
    
    /**
     * Backup database to Google Drive
     * @return true if successful, false otherwise
     */
    suspend fun backupToGoogleDrive(context: Context, account: GoogleSignInAccount): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, account)
                
                // Get database file
                val dbPath = context.getDatabasePath(DATABASE_NAME)
                if (!dbPath.exists()) {
                    return@withContext Result.failure(Exception("Database file not found"))
                }
                
                // Create backup folder if it doesn't exist
                val folderId = getOrCreateBackupFolder(driveService)
                
                // Upload database file
                val fileMetadata = com.google.api.services.drive.model.File()
                fileMetadata.name = "attendance_backup_${System.currentTimeMillis()}.db"
                fileMetadata.parents = listOf(folderId)
                
                val mediaContent = com.google.api.client.http.FileContent("application/octet-stream", dbPath)
                
                val file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()
                
                Result.success(file.id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * List available backups from Google Drive
     */
    suspend fun listBackups(context: Context, account: GoogleSignInAccount): Result<List<BackupFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, account)
                val folderId = getOrCreateBackupFolder(driveService)
                
                val result = driveService.files().list()
                    .setQ("'$folderId' in parents and trashed=false")
                    .setOrderBy("modifiedTime desc")
                    .setFields("files(id, name, modifiedTime, size)")
                    .execute()
                
                val backups = result.files.map { file ->
                    BackupFile(
                        id = file.id,
                        name = file.name,
                        modifiedTime = file.modifiedTime?.value ?: 0L,
                        size = file.getSize() ?: 0L
                    )
                }
                
                Result.success(backups)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Restore database from Google Drive
     * @return true if successful, false otherwise
     */
    suspend fun restoreFromGoogleDrive(
        context: Context,
        account: GoogleSignInAccount,
        fileId: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, account)
                
                // Download file to temporary location
                val tempFile = File(context.cacheDir, "temp_restore.db")
                FileOutputStream(tempFile).use { outputStream ->
                    driveService.files().get(fileId)
                        .executeMediaAndDownloadTo(outputStream)
                }
                
                // Close any existing database connections
                // Copy temp file to database location
                val dbPath = context.getDatabasePath(DATABASE_NAME)
                tempFile.copyTo(dbPath, overwrite = true)
                tempFile.delete()
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get or create the backup folder in Google Drive
     */
    private fun getOrCreateBackupFolder(driveService: Drive): String {
        // Search for existing folder
        val result = driveService.files().list()
            .setQ("name='$BACKUP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        
        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            // Create new folder
            val folderMetadata = com.google.api.services.drive.model.File()
            folderMetadata.name = BACKUP_FOLDER_NAME
            folderMetadata.mimeType = "application/vnd.google-apps.folder"
            
            val folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()
            
            folder.id
        }
    }
    
    /**
     * Data class representing a backup file
     */
    data class BackupFile(
        val id: String,
        val name: String,
        val modifiedTime: Long,
        val size: Long
    )
}
