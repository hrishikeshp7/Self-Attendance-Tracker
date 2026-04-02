package com.attendance.tracker.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.notification.NotificationHelper
import com.attendance.tracker.notification.ReminderScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    onUpdateRequiredAttendance: (Long, Int) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToCustomizations: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var notificationsEnabled by remember {
        mutableStateOf(NotificationHelper.areNotificationsEnabled(context))
    }
    val (savedHour, savedMinute) = NotificationHelper.getReminderTime(context)
    var reminderHour by remember { mutableIntStateOf(savedHour) }
    var reminderMinute by remember { mutableIntStateOf(savedMinute) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            onConfirm = { hour, minute ->
                reminderHour = hour
                reminderMinute = minute
                NotificationHelper.setReminderTime(context, hour, minute)
                if (notificationsEnabled) {
                    ReminderScheduler.scheduleDailyReminder(context)
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Customizations Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToCustomizations
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Customizations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Themes and color settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Backup & Restore Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToBackupRestore
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Export / import data, Google Drive backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Notifications Settings Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Enable/disable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Reminder",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Remind to mark attendance and alert on missed classes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                notificationsEnabled = enabled
                                NotificationHelper.setNotificationsEnabled(context, enabled)
                                if (enabled) {
                                    ReminderScheduler.scheduleDailyReminder(context)
                                } else {
                                    ReminderScheduler.cancelDailyReminder(context)
                                }
                            }
                        )
                    }

                    // Reminder time picker
                    if (notificationsEnabled) {
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reminder Time",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = formatTime(reminderHour, reminderMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { showTimePicker = true }) {
                                Text("Change")
                            }
                        }
                    }
                }
            }

            // About Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToAbout
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, amPm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reminder Time") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

