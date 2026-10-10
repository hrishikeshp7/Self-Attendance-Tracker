package com.attendance.tracker.ui.screens.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.backup.AiImport
import com.attendance.tracker.backup.CsvImporter
import com.attendance.tracker.ui.AttendanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var csvPreview by remember { mutableStateOf<CsvImporter.Result?>(null) }
    var showAiPaste by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val subjects by viewModel.allSubjectsIncludingFolders.collectAsState()

    // ---------------------------------------------------------------
    // SAF launchers
    // ---------------------------------------------------------------

    // Export JSON – ask user where to save the file
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                writeToUri(context, uri) { viewModel.createJsonBackup() }
            }
        }
    }

    // Export CSV – ask user where to save the file
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                writeToUri(context, uri) { viewModel.createCsvBackup() }
            }
        }
    }

    // CSV import – pick a file, parse it (no writes), then show a preview to confirm
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readFromUri(context, uri)
            csvPreview = if (text == null) {
                CsvImporter.Result(emptyList(), emptyList(), listOf("Couldn't read that file."))
            } else {
                viewModel.previewCsvImport(text)
            }
        }
    }

    val csvTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch { writeToUri(context, uri) { CsvImporter.TEMPLATE } }
    }

    // Google Drive Backup – opens the SAF file-picker pre-navigated to Google Drive.
    // Uses a custom contract so we can embed EXTRA_INITIAL_URI pointing to the Drive root,
    // which causes Android to open the picker directly inside Google Drive instead of local
    // storage.  The file is still written via the standard ContentResolver, so no OAuth or
    // Google Drive SDK is required.
    val googleDriveExportLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContract<String, Uri?>() {
            override fun createIntent(context: Context, input: String): Intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, input)
                    // Pre-navigate the picker to Google Drive "My Drive" root.
                    // Authority: com.google.android.apps.docs.storage (Google Drive SAF provider)
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
    ) { uri ->
        if (uri != null) {
            scope.launch {
                writeToUri(context, uri) { viewModel.createJsonBackup() }
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
                            val json = withContext(Dispatchers.IO) { readFromUri(context, uri) }
                            if (json != null) {
                                viewModel.restoreFromJson(json)
                            } else {
                                android.widget.Toast.makeText(context, "Couldn't read that file.", android.widget.Toast.LENGTH_LONG).show()
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

    if (showAiPaste) {
        AlertDialog(
            onDismissRequest = { showAiPaste = false },
            title = { Text("Paste the chatbot's reply") },
            text = {
                OutlinedTextField(
                    value = aiText,
                    onValueChange = { aiText = it },
                    placeholder = { Text("Paste here") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = aiText.isNotBlank(),
                    onClick = {
                        csvPreview = viewModel.previewAiImport(aiText)
                        replaceExisting = false
                        showAiPaste = false
                    }
                ) { Text("Check") }
            },
            dismissButton = { TextButton(onClick = { showAiPaste = false }) { Text("Cancel") } }
        )
    }

    csvPreview?.let { preview ->
        val ok = preview.errors.isEmpty() && preview.subjects.isNotEmpty()
        val existingNames = subjects.filter { it.parentSubjectId == null && !it.isFolder }.map { it.name.lowercase() }.toSet()
        val duplicates = preview.subjects.filter { it.name.lowercase() in existingNames }
        AlertDialog(
            onDismissRequest = { csvPreview = null },
            title = { Text(if (ok) "Ready to import" else "Can't import yet") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (ok) {
                        Text("${preview.subjects.size} subjects and ${preview.slots.size} timetable slots found.")
                        preview.subjects.forEach { row ->
                            val slots = preview.slots.filter { it.subjectName == row.name }
                            Text(
                                "• ${row.name}" + (if (row.name.lowercase() in existingNames) " (already exists)" else "") +
                                    (if (slots.isEmpty()) "" else "\n   " + slots.joinToString("\n   ") {
                                        "${it.day.name.take(3)} ${it.start}–${it.end}" + when {
                                            it.startDate != null && it.startDate == it.endDate -> "  on ${it.startDate}"
                                            it.startDate != null || it.endDate != null -> "  (${it.startDate ?: "…"} to ${it.endDate ?: "…"})"
                                            else -> "  (every week)"
                                        }
                                    }),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (duplicates.isNotEmpty()) {
                            Text("${duplicates.size} already in the app. What should happen to them?", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !replaceExisting, onClick = { replaceExisting = false })
                                Text("Keep the original")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = replaceExisting, onClick = { replaceExisting = true })
                                Text("Use the new timetable and target")
                            }
                            Text("Attendance counts are never changed.", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("Nothing was imported. Fix these in your file and try again:")
                        preview.errors.take(30).forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.errors.size > 30) Text("…and ${preview.errors.size - 30} more.")
                        if (preview.errors.isEmpty()) Text("No subjects were found in the file.")
                    }
                }
            },
            confirmButton = {
                if (ok) TextButton(onClick = { viewModel.importCsv(preview, replaceExisting && duplicates.isNotEmpty()); csvPreview = null }) { Text("Import") }
                else TextButton(onClick = { csvPreview = null }) { Text("OK") }
            },
            dismissButton = if (ok) { { TextButton(onClick = { csvPreview = null }) { Text("Cancel") } } } else null
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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                              "The file picker will open inside Google Drive so you can " +
                              "choose a folder and save without leaving the app.",
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                buttonLabel = "Backup to Drive"
            ) {
                val today = LocalDate.now().toString()
                googleDriveExportLauncher.launch("attendance_backup_$today.json")
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

            // ---- Import section ----
            Divider()
            Text("Import with AI", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Copy the prompt.\n" +
                            "2. Paste it into Claude, Gemini or any chatbot and attach your timetable (PDF or photo).\n" +
                            "3. Copy its reply and paste it here. You'll see a preview before anything is saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(AiImport.PROMPT))
                            android.widget.Toast.makeText(context, "Prompt copied.", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Text("Copy prompt") }
                        Button(onClick = { showAiPaste = true }) { Text("Paste reply") }
                    }
                }
            }

            Text("Import from CSV", style = MaterialTheme.typography.titleMedium)

            BackupActionCard(
                title = "Import subjects & timetable from CSV",
                description = "Bulk-add subjects (target %, attended, total) and weekly lectures " +
                    "from a spreadsheet. Columns: subject, target_percentage, attended, total, " +
                    "day, start, end. The file is checked first and nothing is saved until you confirm.",
                icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                buttonLabel = "Choose CSV File"
            ) {
                replaceExisting = false
                csvImportLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
            }
            TextButton(onClick = { csvTemplateLauncher.launch("attendance_import_template.csv") }) {
                Text("Download a template CSV")
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

/**
 * Builds [content] and writes it off the main thread (the target may be a network-backed
 * provider like Google Drive), reporting the outcome. Failures used to escape the launched
 * coroutine uncaught and crash the app — or, when no stream opened, fail silently.
 */
private suspend fun writeToUri(context: Context, uri: Uri, content: suspend () -> String) {
    val saved = try {
        val text = content()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } != null
        }
    } catch (e: Exception) {
        false
    }
    android.widget.Toast.makeText(
        context,
        if (saved) "File saved." else "Couldn't save the file.",
        android.widget.Toast.LENGTH_SHORT
    ).show()
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
