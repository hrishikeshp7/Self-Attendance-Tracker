package com.attendance.tracker.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.NotificationPreference
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.notification.AttendanceNotificationManager
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.PresentGreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    notificationPreference: NotificationPreference?,
    onUpdateRequiredAttendance: (Long, Int) -> Unit,
    onUpdateNotificationPreference: (Boolean, Int, Int, String) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToCustomizations: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Request notification permission for Android 13+
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Settings") },
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
        ) {
            // Header
            Text(
                text = "Required Attendance Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            Text(
                text = "Set the minimum attendance percentage required for each subject",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Customizations Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

            Spacer(modifier = Modifier.height(8.dp))

            // About Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

            if (subjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No subjects to configure",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(subjects, key = { it.id }) { subject ->
                        RequiredAttendanceItem(
                            subject = subject,
                            allSubjects = allSubjects,
                            onEditClick = {
                                selectedSubject = subject
                                showEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Required Attendance Dialog
    if (showEditDialog && selectedSubject != null) {
        EditRequiredAttendanceDialog(
            subject = selectedSubject!!,
            onDismiss = {
                showEditDialog = false
                selectedSubject = null
            },
            onConfirm = { required ->
                onUpdateRequiredAttendance(selectedSubject!!.id, required)
                showEditDialog = false
                selectedSubject = null
            }
        )
    }

    // Notification Settings Dialog
    if (showNotificationDialog) {
        NotificationSettingsDialog(
            notificationPreference = notificationPreference,
            onDismiss = { showNotificationDialog = false },
            onSave = { enabled, hour, minute, message ->
                onUpdateNotificationPreference(enabled, hour, minute, message)
                
                // Schedule or cancel notification based on enabled state
                AttendanceNotificationManager.createNotificationChannel(context)
                if (enabled) {
                    // Request permission if needed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (notificationPermissionState?.status?.isGranted == false) {
                            notificationPermissionState.launchPermissionRequest()
                        }
                    }
                    AttendanceNotificationManager.scheduleNotification(context, hour, minute)
                } else {
                    AttendanceNotificationManager.cancelNotification(context)
                }
                
                showNotificationDialog = false
            }
        )
    }
}

@Composable
private fun RequiredAttendanceItem(
    subject: Subject,
    allSubjects: List<Subject>,
    onEditClick: () -> Unit
) {
    val statusColor = if (subject.isAboveRequired) PresentGreen else AbsentRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.getDisplayName(allSubjects),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Required: ${subject.requiredAttendance}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Current: ${"%.1f".format(subject.currentAttendancePercentage)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }
    }
}

@Composable
private fun EditRequiredAttendanceDialog(
    subject: Subject,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var requiredAttendance by remember { mutableStateOf(subject.requiredAttendance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Required Attendance") },
        text = {
            Column {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = requiredAttendance,
                    onValueChange = { requiredAttendance = it.filter { c -> c.isDigit() } },
                    label = { Text("Required Attendance (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Enter a value between 0 and 100")
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Preview
                val newRequired = requiredAttendance.toIntOrNull() ?: 0
                val wouldBeAbove = subject.currentAttendancePercentage >= newRequired
                val previewColor = if (wouldBeAbove) PresentGreen else AbsentRed
                Text(
                    text = if (wouldBeAbove) 
                        "✓ Current attendance meets requirement" 
                    else 
                        "✗ Current attendance below requirement",
                    style = MaterialTheme.typography.bodySmall,
                    color = previewColor
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val required = requiredAttendance.toIntOrNull() ?: 75
                    onConfirm(required.coerceIn(0, 100))
                }
            ) {
                Text("Save")
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
private fun NotificationSettingsDialog(
    notificationPreference: NotificationPreference?,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Int, String) -> Unit
) {
    var enabled by remember { mutableStateOf(notificationPreference?.enabled ?: false) }
    var hour by remember { mutableStateOf((notificationPreference?.startTimeHour ?: 9).toString()) }
    var minute by remember { mutableStateOf((notificationPreference?.startTimeMinute ?: 0).toString()) }
    var message by remember { mutableStateOf(notificationPreference?.message ?: "Time to mark your attendance!") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Enable/Disable Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Notifications",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Picker
                Text(
                    text = "Notification Time",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() } && it.length <= 2) {
                                hour = it
                            }
                        },
                        label = { Text("Hour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        supportingText = { Text("0-23") }
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() } && it.length <= 2) {
                                minute = it
                            }
                        },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        supportingText = { Text("0-59") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Notification Message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Notification will persist until attendance is marked for all subjects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    onSave(enabled, h, m, message)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
