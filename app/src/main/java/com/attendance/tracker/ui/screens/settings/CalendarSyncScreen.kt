package com.attendance.tracker.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attendance.tracker.calendarsync.CalendarSyncManager
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncScreen(
    scheduleEntries: List<ScheduleEntry>,
    allSubjects: Map<Long, Subject>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resultMessage by remember { mutableStateOf<String?>(null) }
    // How far ahead to write lectures. Defaults to just the current week.
    var rangeEnd by remember { mutableStateOf(CalendarSyncManager.endOfCurrentWeek()) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val icsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ics = CalendarSyncManager.buildIcsContent(scheduleEntries, allSubjects, rangeEnd)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(ics.toByteArray(Charsets.UTF_8))
                }
                resultMessage = "Saved timetable.ics. Open that file with Notion Calendar, Outlook, Apple Calendar, " +
                    "or any app that can import an .ics file."
            }
        }
    }

    if (showEndDatePicker) {
        val zone = ZoneId.of("UTC")
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = rangeEnd.atStartOfDay(zone).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        rangeEnd = maxOf(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), LocalDate.now())
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("Calendar Sync") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar Sync") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    text = "Sync your weekly timetable with Google Calendar, Notion Calendar, Outlook, or Apple Calendar. " +
                        "No intrusive device permissions required.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (scheduleEntries.isEmpty()) {
                Text(
                    text = "Your timetable is empty — add some lectures on the Schedule tab first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text("Sync Duration", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val weekEnd = CalendarSyncManager.endOfCurrentWeek()
                    val options = listOf(
                        "This week" to weekEnd,
                        "2 weeks" to weekEnd.plusWeeks(1),
                        "4 weeks" to weekEnd.plusWeeks(3)
                    )
                    val matchesPreset = options.any { it.second == rangeEnd }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (label, end) ->
                            FilterChip(selected = rangeEnd == end, onClick = { rangeEnd = end }, label = { Text(label) })
                        }
                        FilterChip(
                            selected = !matchesPreset,
                            onClick = { showEndDatePicker = true },
                            label = { Text("Custom end date") }
                        )
                    }
                    Text(
                        text = "Lectures will be included through ${rangeEnd.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Add to Calendar", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add all lectures directly into your favorite calendar app or share with classmates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val ics = CalendarSyncManager.buildIcsContent(scheduleEntries, allSubjects, rangeEnd)
                            CalendarSyncManager.openInCalendarApp(context, ics)
                        },
                        enabled = scheduleEntries.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Calendar App")
                    }

                    OutlinedButton(
                        onClick = {
                            val ics = CalendarSyncManager.buildIcsContent(scheduleEntries, allSubjects, rangeEnd)
                            CalendarSyncManager.shareIcs(context, ics)
                        },
                        enabled = scheduleEntries.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Timetable (.ics)")
                    }
                }
            }

            Text("Save as File", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Save an .ics file directly to device storage or cloud drive for backup and manual import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { icsExportLauncher.launch("timetable.ics") },
                        enabled = scheduleEntries.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save .ics to Storage")
                    }
                }
            }
        }
    }
}
