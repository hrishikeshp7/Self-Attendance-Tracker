package com.attendance.tracker.ui.screens.subjects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
fun SubjectsScreen(
    subjects: List<Subject>,
    onAddSubject: (String, Int) -> Unit,
    onAddFolder: (String) -> Unit,
    onUpdateSubject: (Subject) -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onUpdateAttendanceCounts: (Long, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Subjects") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Subject")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (subjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No subjects added yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add a subject",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(subjects, key = { it.id }) { subject ->
                    SubjectListItem(
                        subject = subject,
                        onEditClick = {
                            selectedSubject = subject
                            showEditDialog = true
                        },
                        onDeleteClick = { onDeleteSubject(subject) }
                    )
                }
            }
        }
    }

    // Add Subject Dialog
    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, required ->
                onAddSubject(name, required)
                showAddDialog = false
            },
            onConfirmFolder = { name ->
                onAddFolder(name)
                showAddDialog = false
            }
        )
    }

    // Edit Subject Dialog
    if (showEditDialog && selectedSubject != null) {
        EditSubjectDialog(
            subject = selectedSubject!!,
            onDismiss = { 
                showEditDialog = false
                selectedSubject = null
            },
            onConfirm = { updatedSubject, present, absent ->
                onUpdateSubject(updatedSubject)
                onUpdateAttendanceCounts(updatedSubject.id, present, absent)
                showEditDialog = false
                selectedSubject = null
            }
        )
    }
}

@Composable
private fun SubjectListItem(
    subject: Subject,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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
                if (!subject.isFolder) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Present: ${subject.presentLectures} | Absent: ${subject.absentLectures}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val attendanceColor = if (subject.isAboveRequired) PresentGreen else AbsentRed
                    Text(
                        text = "${"%.1f".format(subject.currentAttendancePercentage)}% (Required: ${subject.requiredAttendance}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = attendanceColor
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AbsentRed)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Subject") },
            text = { Text("Are you sure you want to delete '${subject.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete", color = AbsentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
    onConfirmFolder: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var requiredAttendance by remember { mutableStateOf("75") }
    var isFolder by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFolder) "Add Folder" else "Add Subject") },
        text = {
            Column {
                // Folder/Subject toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Create as folder", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isFolder,
                        onCheckedChange = { isFolder = it }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isFolder) "Folder Name" else "Subject Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (!isFolder) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = requiredAttendance,
                        onValueChange = { requiredAttendance = it.filter { c -> c.isDigit() } },
                        label = { Text("Required Attendance (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        if (isFolder) {
                            onConfirmFolder(name.trim())
                        } else {
                            val required = requiredAttendance.toIntOrNull() ?: 75
                            onConfirm(name.trim(), required.coerceIn(0, 100))
                        }
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
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
private fun EditSubjectDialog(
    subject: Subject,
    onDismiss: () -> Unit,
    onConfirm: (Subject, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(subject.name) }
    var requiredAttendance by remember { mutableStateOf(subject.requiredAttendance.toString()) }
    var presentLectures by remember { mutableStateOf(subject.presentLectures.toString()) }
    var absentLectures by remember { mutableStateOf(subject.absentLectures.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (subject.isFolder) "Edit Folder" else "Edit Subject") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (subject.isFolder) "Folder Name" else "Subject Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!subject.isFolder) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = requiredAttendance,
                        onValueChange = { requiredAttendance = it.filter { c -> c.isDigit() } },
                        label = { Text("Required Attendance (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = presentLectures,
                            onValueChange = { presentLectures = it.filter { c -> c.isDigit() } },
                            label = { Text("Present") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = absentLectures,
                            onValueChange = { absentLectures = it.filter { c -> c.isDigit() } },
                            label = { Text("Absent") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val required = if (subject.isFolder) subject.requiredAttendance else (requiredAttendance.toIntOrNull() ?: 75)
                        val present = if (subject.isFolder) subject.presentLectures else (presentLectures.toIntOrNull() ?: 0)
                        val absent = if (subject.isFolder) subject.absentLectures else (absentLectures.toIntOrNull() ?: 0)
                        val updatedSubject = subject.copy(
                            name = name.trim(),
                            requiredAttendance = required.coerceIn(0, 100)
                        )
                        onConfirm(updatedSubject, present, absent)
                    }
                },
                enabled = name.isNotBlank()
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
