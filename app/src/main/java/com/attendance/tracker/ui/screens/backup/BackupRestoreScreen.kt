package com.attendance.tracker.ui.screens.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.ui.AttendanceViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: AttendanceViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by viewModel.backupRestoreStatus.collectAsState()
    val message by viewModel.backupRestoreMessage.collectAsState()

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // ---------------------------------------------------------------
    // SAF launchers
    // ---------------------------------------------------------------

    // Export JSON – ask user where to save the file
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.createJsonBackup()
                writeToUri(context, uri, json)
            }
        }
    }

    // Export CSV – ask user where to save the file
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = viewModel.createCsvBackup()
                writeToUri(context, uri, csv)
            }
        }
    }

    // Restore – ask user to pick a JSON file (also shows Google Drive)
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    // ---------------------------------------------------------------
    // Restore confirmation dialog
    // ---------------------------------------------------------------
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Restore Backup?") },
            text = {
                Text(
                    "This will replace ALL current data with the selected backup. " +
                    "This action cannot be undone. Continue?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        val uri = pendingRestoreUri ?: return@TextButton
                        scope.launch {
                            val json = readFromUri(context, uri)
                            if (json != null) {
                                viewModel.restoreFromJson(json)
                            }
                        }
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---------------------------------------------------------------
    // Status / result snack-bar
    // ---------------------------------------------------------------
    if (status == AttendanceViewModel.BackupRestoreStatus.SUCCESS ||
        status == AttendanceViewModel.BackupRestoreStatus.ERROR) {
        AlertDialog(
            onDismissRequest = { viewModel.resetBackupRestoreStatus() },
            title = {
                Text(
                    if (status == AttendanceViewModel.BackupRestoreStatus.SUCCESS) "Success"
                    else "Error"
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetBackupRestoreStatus() }) { Text("OK") }
            }
        )
    }

    // ---------------------------------------------------------------
    // Screen UI
    // ---------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->

        if (status == AttendanceViewModel.BackupRestoreStatus.IN_PROGRESS) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing…")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ---- Info banner ----
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Backup your attendance data to a local file or to Google Drive. " +
                           "Use the same file to restore on a new device with a single tap.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // ---- Export section ----
            Text("Export", style = MaterialTheme.typography.titleMedium)

            BackupActionCard(
                title = "Export to JSON",
                description = "Full backup – subjects, attendance records and schedule. " +
                              "Use this file to restore on another device.",
                icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                buttonLabel = "Export JSON"
            ) {
                val today = LocalDate.now().toString()
                exportJsonLauncher.launch("attendance_backup_$today.json")
            }

            BackupActionCard(
                title = "Export to CSV",
                description = "Attendance records in a spreadsheet-friendly format. " +
                              "Useful for analysis – cannot be used for restore.",
                icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                buttonLabel = "Export CSV"
            ) {
                val today = LocalDate.now().toString()
                exportCsvLauncher.launch("attendance_export_$today.csv")
            }

            BackupActionCard(
                title = "Google Drive Backup",
                description = "Save your JSON backup directly to Google Drive. " +
                              "When the file picker opens, navigate to Google Drive and save there.",
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                buttonLabel = "Backup to Drive"
            ) {
                val today = LocalDate.now().toString()
                exportJsonLauncher.launch("attendance_backup_$today.json")
            }

            // ---- Restore section ----
            Divider()
            Text("Restore", style = MaterialTheme.typography.titleMedium)

            BackupActionCard(
                title = "Restore from Backup",
                description = "Select a previously exported JSON file from local storage " +
                              "or Google Drive. All current data will be replaced.",
                icon = { Icon(Icons.Default.RestorePage, contentDescription = null) },
                buttonLabel = "Select Backup File",
                buttonColors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream"))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable action card
// ---------------------------------------------------------------------------

@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    buttonLabel: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon()
                Text(text = title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onClick,
                colors = buttonColors,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// I/O helpers
// ---------------------------------------------------------------------------

private fun writeToUri(context: Context, uri: Uri, content: String) {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
    }
}

private fun readFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
    } catch (e: Exception) {
        null
    }
}
