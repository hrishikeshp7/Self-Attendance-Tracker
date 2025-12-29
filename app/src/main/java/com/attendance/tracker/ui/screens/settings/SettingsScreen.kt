package com.attendance.tracker.ui.screens.settings

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.PresentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    subjects: List<Subject>,
    onUpdateRequiredAttendance: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

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
}

@Composable
private fun RequiredAttendanceItem(
    subject: Subject,
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
                    text = subject.name,
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
