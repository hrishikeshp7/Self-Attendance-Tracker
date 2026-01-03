package com.attendance.tracker.ui.screens.exportbackup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.utils.CalendarIntegrationHelper
import com.attendance.tracker.utils.CsvExporter
import com.attendance.tracker.utils.GoogleDriveBackupHelper
import com.attendance.tracker.utils.PdfExporter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBackupScreen(
    subjects: List<Subject>,
    allAttendanceRecords: List<AttendanceRecord>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                // Proceed with backup
                scope.launch {
                    isLoading = true
                    val backupResult = GoogleDriveBackupHelper.backupToGoogleDrive(context, account)
                    isLoading = false
                    
                    if (backupResult.isSuccess) {
                        Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Backup failed: ${backupResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Calendar permission launcher
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CALENDAR] == true &&
            permissions[Manifest.permission.WRITE_CALENDAR] == true) {
            showCalendarDialog = true
        } else {
            Toast.makeText(context, "Calendar permissions are required", Toast.LENGTH_SHORT).show()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & Backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Export Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Data Export",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export to CSV/PDF")
                    }
                }
            }
            
            // Cloud Backup Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cloud Backup",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Backup and restore your data using Google Drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val signInIntent = GoogleDriveBackupHelper.getSignInIntent(context)
                                googleSignInLauncher.launch(signInIntent)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Backup")
                        }
                        
                        Button(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }
                    }
                }
            }
            
            // Calendar Integration Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Calendar Integration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Sync your schedule with device calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = {
                            if (CalendarIntegrationHelper.hasCalendarPermissions(context)) {
                                showCalendarDialog = true
                            } else {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Schedule to Calendar")
                    }
                }
            }
            
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            context = context,
            subjects = subjects,
            allAttendanceRecords = allAttendanceRecords,
            onDismiss = { showExportDialog = false }
        )
    }
    
    // Calendar Dialog
    if (showCalendarDialog) {
        CalendarSyncDialog(
            context = context,
            onDismiss = { showCalendarDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    context: Context,
    subjects: List<Subject>,
    allAttendanceRecords: List<AttendanceRecord>,
    onDismiss: () -> Unit
) {
    var exportFormat by remember { mutableStateOf("CSV") }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select export format:")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = exportFormat == "CSV",
                        onClick = { exportFormat = "CSV" },
                        label = { Text("CSV") }
                    )
                    FilterChip(
                        selected = exportFormat == "PDF",
                        onClick = { exportFormat = "PDF" },
                        label = { Text("PDF") }
                    )
                    FilterChip(
                        selected = exportFormat == "Summary",
                        onClick = { exportFormat = "Summary" },
                        label = { Text("Summary") }
                    )
                }
                
                Text(
                    text = "Date range: All records",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val file = when (exportFormat) {
                            "CSV" -> CsvExporter.exportToCsv(
                                context,
                                subjects,
                                allAttendanceRecords,
                                startDate,
                                endDate
                            )
                            "PDF" -> PdfExporter.exportToPdf(
                                context,
                                subjects,
                                allAttendanceRecords,
                                startDate,
                                endDate
                            )
                            "Summary" -> CsvExporter.exportSubjectSummaryToCsv(context, subjects)
                            else -> return@Button
                        }
                        
                        // Share the file
                        shareFile(context, file)
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export & Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CalendarSyncDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendar Integration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This will create recurring events in your device calendar for your class schedule.")
                Text(
                    "Note: Events will be created for the next 3 months.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        // This would need schedule data from ViewModel
                        // For now, show success message
                        Toast.makeText(context, "Calendar sync feature requires schedule data", Toast.LENGTH_SHORT).show()
                        isExporting = false
                        onDismiss()
                    }
                },
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Export to Calendar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = when {
            file.name.endsWith(".csv") -> "text/csv"
            file.name.endsWith(".pdf") -> "application/pdf"
            else -> "*/*"
        }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(Intent.createChooser(intent, "Share file"))
}
