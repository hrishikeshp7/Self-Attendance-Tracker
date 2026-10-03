package com.attendance.tracker.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attendance.tracker.calendarsync.CalendarSyncManager
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import kotlinx.coroutines.launch
import java.time.Instant
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

    var hasPermission by remember { mutableStateOf(CalendarSyncManager.hasCalendarPermissions(context)) }
    var syncPrefs by remember { mutableStateOf(CalendarSyncManager.getSyncPrefs(context)) }
    var calendars by remember { mutableStateOf<List<CalendarSyncManager.DeviceCalendar>>(emptyList()) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    fun loadCalendarsAndShowPicker() {
        scope.launch {
            isBusy = true
            calendars = CalendarSyncManager.listWritableCalendars(context)
            isBusy = false
            if (calendars.isEmpty()) {
                resultMessage = "No writable calendars were found on this device. Add an account " +
                    "(e.g. Google) in your phone's Settings > Accounts, then try again."
            } else {
                showCalendarPicker = true
            }
        }
    }

    fun startSync(calendarId: Long, label: String) {
        scope.launch {
            isBusy = true
            val count = CalendarSyncManager.syncSchedule(context, calendarId, scheduleEntries, allSubjects)
            CalendarSyncManager.saveSyncPrefs(context, calendarId, label)
            syncPrefs = CalendarSyncManager.getSyncPrefs(context)
            isBusy = false
            resultMessage = "Synced $count lecture${if (count != 1) "s" else ""} to \"$label\"."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants.values.all { it }
        if (hasPermission) {
            loadCalendarsAndShowPicker()
        } else {
            resultMessage = "Calendar access is needed to sync your timetable. You can still use .ics export below without it."
        }
    }

    val icsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ics = CalendarSyncManager.buildIcsContent(scheduleEntries, allSubjects)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(ics.toByteArray(Charsets.UTF_8))
                }
                resultMessage = "Saved. Open that file with Notion Calendar, Outlook, Apple Calendar, " +
                    "or any app that can import an .ics file."
            }
        }
    }

    if (showCalendarPicker) {
        CalendarPickerDialog(
            calendars = calendars,
            onSelect = { calendar ->
                showCalendarPicker = false
                startSync(calendar.id, "${calendar.displayName} (${calendar.accountName})")
            },
            onDismiss = { showCalendarPicker = false }
        )
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
                    text = "Get your weekly timetable into Google Calendar, Notion Calendar, or any " +
                        "other calendar app. On-device sync writes it straight into a calendar already " +
                        "on your phone; .ics export gives you a file any calendar app can import.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (scheduleEntries.isEmpty()) {
                Text(
                    text = "Your timetable is empty — add some lectures on the Schedule tab first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Sync to device calendar", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (syncPrefs.calendarId != null) {
                        Text(
                            text = "Synced to ${syncPrefs.calendarLabel}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        syncPrefs.lastSyncedAtEpochMillis?.let { millis ->
                            Text(
                                text = "Last synced ${formatSyncTime(millis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Not synced yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                when {
                                    !hasPermission -> permissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                    )
                                    syncPrefs.calendarId != null -> startSync(
                                        syncPrefs.calendarId!!,
                                        syncPrefs.calendarLabel ?: "calendar"
                                    )
                                    else -> loadCalendarsAndShowPicker()
                                }
                            },
                            enabled = !isBusy
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (syncPrefs.calendarId != null) "Sync Now" else "Choose Calendar & Sync")
                            }
                        }
                        if (hasPermission && syncPrefs.calendarId != null) {
                            OutlinedButton(onClick = { loadCalendarsAndShowPicker() }, enabled = !isBusy) {
                                Text("Change Calendar")
                            }
                        }
                    }

                    if (syncPrefs.calendarId != null) {
                        TextButton(
                            onClick = {
                                val calendarId = syncPrefs.calendarId ?: return@TextButton
                                scope.launch {
                                    isBusy = true
                                    CalendarSyncManager.removeSyncedEvents(context, calendarId)
                                    CalendarSyncManager.clearSyncPrefs(context)
                                    syncPrefs = CalendarSyncManager.getSyncPrefs(context)
                                    isBusy = false
                                    resultMessage = "Removed the synced events and stopped syncing."
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text("Stop Syncing & Remove Events", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Divider()

            Text("Export as .ics", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Saves a standalone calendar file. Needs no permission — open it with " +
                            "Notion Calendar, Outlook, Apple Calendar, or anywhere else that imports .ics files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { icsExportLauncher.launch("timetable.ics") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Export .ics")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPickerDialog(
    calendars: List<CalendarSyncManager.DeviceCalendar>,
    onSelect: (CalendarSyncManager.DeviceCalendar) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a Calendar") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                calendars.forEach { calendar ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(calendar) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            calendar.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatSyncTime(epochMillis: Long): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return dateTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}
