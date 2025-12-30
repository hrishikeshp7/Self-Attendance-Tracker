package com.attendance.tracker.ui.screens.subjects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
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
    onAddSubject: (String, Int, Long?) -> Unit,
    onAddFolder: (String) -> Unit,
    onUpdateSubject: (Subject) -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onUpdateAttendanceCounts: (Long, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var currentFolder by remember { mutableStateOf<Subject?>(null) }
    
    // Filter subjects based on current view
    val displaySubjects = if (currentFolder == null) {
        // Show top-level items (both folders and subjects)
        subjects.filter { it.parentSubjectId == null }
    } else {
        // Show subjects within the selected folder
        subjects.filter { it.parentSubjectId == currentFolder?.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        currentFolder?.name ?: "Manage Subjects"
                    ) 
                },
                navigationIcon = {
                    if (currentFolder != null) {
                        IconButton(onClick = { currentFolder = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            
            if (displaySubjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (currentFolder != null) "No subjects in this folder" else "No subjects added yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (currentFolder != null) "Tap + to add a subject to this folder" else "Tap + to add a subject or folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(displaySubjects, key = { it.id }) { subject ->
                        if (subject.isFolder) {
                            FolderListItem(
                                folder = subject,
                                onFolderClick = { currentFolder = it },
                                onEditClick = {
                                    selectedSubject = subject
                                    showEditDialog = true
                                },
                                onDeleteClick = { onDeleteSubject(subject) }
                            )
                        } else {
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
        }
    }

    // Add Subject Dialog
    if (showAddDialog) {
        AddSubjectDialog(
            selectedFolder = currentFolder,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, required, parentId ->
                onAddSubject(name, required, parentId)
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
private fun FolderListItem(
    folder: Subject,
    onFolderClick: (Subject) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onFolderClick(folder) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium
                    )
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
            title = { Text("Delete Folder") },
            text = { Text("Are you sure you want to delete '${folder.name}'? This action cannot be undone.") },
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
    selectedFolder: Subject?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Long?) -> Unit,
    onConfirmFolder: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var requiredAttendance by remember { mutableStateOf("75") }
    var isFolder by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (isFolder) "Add Folder" 
                else if (selectedFolder != null) "Add Subject to ${selectedFolder.name}"
                else "Add Subject"
            ) 
        },
        text = {
            Column {
                // Only show folder toggle if not adding to a folder
                if (selectedFolder == null) {
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
                }
                
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
                            onConfirm(name.trim(), required.coerceIn(0, 100), selectedFolder?.id)
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
