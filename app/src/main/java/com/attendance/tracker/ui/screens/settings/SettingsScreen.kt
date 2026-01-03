package com.attendance.tracker.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.NotificationPreferences
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.utils.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    notificationPreferences: NotificationPreferences?,
    onUpdateRequiredAttendance: (Long, Int) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToCustomizations: () -> Unit,
    onNavigateToExportBackup: () -> Unit,
    onUpdateNotificationsEnabled: (Boolean) -> Unit,
    onUpdateReminderMinutes: (Int) -> Unit,
    onUpdateLowAttendanceWarnings: (Boolean) -> Unit,
    onUpdateLowAttendanceThreshold: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showReminderDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    
    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onUpdateNotificationsEnabled(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notifications Section
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
                        text = "Notifications",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Enable notifications toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Notifications",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Get reminders for upcoming classes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationPreferences?.notificationsEnabled ?: true,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (!NotificationHelper.hasNotificationPermission(context)) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onUpdateNotificationsEnabled(enabled)
                                    }
                                } else {
                                    onUpdateNotificationsEnabled(enabled)
                                }
                            }
                        )
                    }
                    
                    Divider()
                    
                    // Reminder time setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminder Time",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${notificationPreferences?.reminderMinutesBefore ?: 15} minutes before class",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showReminderDialog = true }) {
                            Text("Change")
                        }
                    }
                    
                    Divider()
                    
                    // Low attendance warnings toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Low Attendance Warnings",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Alert when attendance drops below threshold",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationPreferences?.lowAttendanceWarnings ?: true,
                            onCheckedChange = onUpdateLowAttendanceWarnings
                        )
                    }
                    
                    Divider()
                    
                    // Low attendance threshold setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Warning Threshold",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${notificationPreferences?.lowAttendanceThreshold ?: 75}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showThresholdDialog = true }) {
                            Text("Change")
                        }
                    }
                }
            }

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
            
            // Export & Backup Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToExportBackup
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
                            text = "Export & Backup",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Export data, cloud backup, calendar sync",
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
    
    // Reminder minutes dialog
    if (showReminderDialog) {
        val options = listOf(5, 10, 15, 30, 60)
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("Set Reminder Time") },
            text = {
                Column {
                    options.forEach { minutes ->
                        TextButton(
                            onClick = {
                                onUpdateReminderMinutes(minutes)
                                showReminderDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$minutes minutes before")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Threshold dialog
    if (showThresholdDialog) {
        val options = listOf(50, 60, 65, 70, 75, 80, 85, 90)
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("Set Warning Threshold") },
            text = {
                Column {
                    options.forEach { threshold ->
                        TextButton(
                            onClick = {
                                onUpdateLowAttendanceThreshold(threshold)
                                showThresholdDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$threshold%")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThresholdDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
