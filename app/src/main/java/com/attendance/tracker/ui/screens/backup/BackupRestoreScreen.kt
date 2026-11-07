package com.attendance.tracker.ui.screens.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onNavigateBack: () -> Unit,
    onNavigateToAiImport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by viewModel.backupRestoreStatus.collectAsState()
    val message by viewModel.backupRestoreMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var csvPreview by remember { mutableStateOf<CsvImporter.Result?>(null) }
    var replaceExisting by remember { mutableStateOf(false) }
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
            scope.launch {
                val text = withContext(Dispatchers.IO) { readFromUri(context, uri) }
                csvPreview = if (text == null) {
                    CsvImporter.Result(emptyList(), emptyList(), listOf("Couldn't read that file."))
                } else {
                    viewModel.previewCsvImport(text)
                }
            }
        }
    }

    val csvTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch { writeToUri(context, uri) { CsvImporter.TEMPLATE } }
    }

    // Google Drive Backup – opens SAF file-picker pre-navigated to Google Drive root
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
    ) { uri ->
        if (uri != null) {
            scope.launch {
                writeToUri(context, uri) { viewModel.createJsonBackup() }
            }
        }
    }

    // Restore – ask user to pick a JSON file
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
                    "This will replace ALL current data with the selected backup file. " +
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
                ) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) { Text("Cancel") }
            }
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

    // Status / result snack-bar
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
        ) {
            // Segmented Tabs: Backup vs Restore & Import
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { Divider() }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Backup / Export", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Restore & CSV", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    // ───────────────────────────────────────────────
                    // TAB 1: BACKUP (EXPORT)
                    // ───────────────────────────────────────────────

                    // Google Drive Featured Hero Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Google Drive Cloud Backup",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Safely store your backup in Google Drive",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val today = LocalDate.now().toString()
                                    googleDriveExportLauncher.launch("attendance_backup_$today.json")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Backup to Google Drive")
                            }
                        }
                    }

                    // Local File Exports Group
                    Text(
                        text = "Local Storage Export",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            ActionListItem(
                                title = "Full App Backup (.json)",
                                subtitle = "Complete backup including subjects, attendance & timetable",
                                icon = Icons.Default.FileDownload,
                                onClick = {
                                    val today = LocalDate.now().toString()
                                    exportJsonLauncher.launch("attendance_backup_$today.json")
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            ActionListItem(
                                title = "Spreadsheet Export (.csv)",
                                subtitle = "Export attendance records for Excel or Google Sheets",
                                icon = Icons.Default.TableChart,
                                onClick = {
                                    val today = LocalDate.now().toString()
                                    exportCsvLauncher.launch("attendance_export_$today.csv")
                                }
                            )
                        }
                    }

                } else {
                    // ───────────────────────────────────────────────
                    // TAB 2: RESTORE & CSV IMPORT
                    // ───────────────────────────────────────────────

                    // Restore Warning Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.RestorePage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Restore App Backup",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Replaces all current subjects, logs, and timetable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Backup File (.json)")
                            }
                        }
                    }

                    // CSV Timetable Import Group
                    Text(
                        text = "Spreadsheet Import",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            ActionListItem(
                                title = "Import Subjects & Timetable",
                                subtitle = "Bulk-add subjects and timetable slots from a CSV file",
                                icon = Icons.Default.FileUpload,
                                onClick = {
                                    replaceExisting = false
                                    csvImportLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            ActionListItem(
                                title = "Download CSV Template",
                                subtitle = "Sample format with required columns for import",
                                icon = Icons.Default.HistoryEdu,
                                onClick = {
                                    csvTemplateLauncher.launch("attendance_import_template.csv")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable modern list row
// ---------------------------------------------------------------------------

@Composable
private fun ActionListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// I/O helpers
// ---------------------------------------------------------------------------

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
