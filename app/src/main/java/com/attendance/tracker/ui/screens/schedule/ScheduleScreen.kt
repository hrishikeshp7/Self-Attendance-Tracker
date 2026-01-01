package com.attendance.tracker.ui.screens.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    scheduleEntries: List<ScheduleEntry>,
    onAddScheduleEntry: (Long, DayOfWeek) -> Unit,
    onRemoveScheduleEntry: (ScheduleEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var selectedWeek by remember { mutableStateOf(0) } // 0 = all weeks, 1 = week 1, etc.
    var showAddDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<ScheduleEntry?>(null) }
    
    // Initialize pager state for days of the week
    val pagerState = rememberPagerState(
        initialPage = DayOfWeek.MONDAY.ordinal,
        pageCount = { DayOfWeek.entries.size }
    )
    
    // Sync selected day with pager state changes (from swipe)
    LaunchedEffect(pagerState.currentPage) {
        val newDay = DayOfWeek.entries[pagerState.currentPage]
        if (selectedDay != newDay) {
            selectedDay = newDay
        }
    }
    
    // Sync pager with selected day changes (from tab clicks)
    LaunchedEffect(selectedDay, pagerState.isScrollInProgress) {
        if (pagerState.currentPage != selectedDay.ordinal && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(selectedDay.ordinal)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Schedule") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (subjects.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add schedule entry")
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Week Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedWeek == 0,
                    onClick = { selectedWeek = 0 },
                    label = { Text("All Weeks") }
                )
                FilterChip(
                    selected = selectedWeek == 1,
                    onClick = { selectedWeek = 1 },
                    label = { Text("Week 1") }
                )
                FilterChip(
                    selected = selectedWeek == 2,
                    onClick = { selectedWeek = 2 },
                    label = { Text("Week 2") }
                )
                FilterChip(
                    selected = selectedWeek == 3,
                    onClick = { selectedWeek = 3 },
                    label = { Text("Week 3") }
                )
                FilterChip(
                    selected = selectedWeek == 4,
                    onClick = { selectedWeek = 4 },
                    label = { Text("Week 4") }
                )
            }

            // Day Selector
            ScrollableTabRow(
                selectedTabIndex = selectedDay.ordinal,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 8.dp
            ) {
                DayOfWeek.entries.forEach { day ->
                    Tab(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        text = {
                            Text(
                                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            )
                        }
                    )
                }
            }

            // Horizontal Pager for swipeable days
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val day = DayOfWeek.entries[page]
                
                DayScheduleContent(
                    day = day,
                    weekNumber = selectedWeek,
                    subjects = subjects,
                    allSubjects = allSubjects,
                    scheduleEntries = scheduleEntries,
                    onEditEntry = { entry ->
                        entryToEdit = entry
                    },
                    onRemoveScheduleEntry = onRemoveScheduleEntry
                )
            }
        }
    }

    // Add/Edit Schedule Dialog
    if (showAddDialog) {
        AddScheduleEntryDialog(
            subjects = subjects,
            allSubjects = allSubjects,
            selectedDay = selectedDay,
            selectedWeek = selectedWeek,
            onDismiss = { showAddDialog = false },
            onSave = { subjectId, day, weekNum, startHour, startMin, endHour, endMin ->
                // For now, use the existing simple add method - we'll need to update the repository
                onAddScheduleEntry(subjectId, day)
                showAddDialog = false
            }
        )
    }
    
    if (entryToEdit != null) {
        EditScheduleEntryDialog(
            entry = entryToEdit!!,
            subject = subjects.find { it.id == entryToEdit!!.subjectId },
            allSubjects = allSubjects,
            onDismiss = { entryToEdit = null },
            onDelete = {
                onRemoveScheduleEntry(entryToEdit!!)
                entryToEdit = null
            }
        )
    }
}

@Composable
private fun DayScheduleContent(
    day: DayOfWeek,
    weekNumber: Int,
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    scheduleEntries: List<ScheduleEntry>,
    onEditEntry: (ScheduleEntry) -> Unit,
    onRemoveScheduleEntry: (ScheduleEntry) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Filter schedule entries by day and week
        val filteredEntries = scheduleEntries.filter { entry ->
            entry.dayOfWeek == day && (weekNumber == 0 || entry.weekNumber == weekNumber || entry.weekNumber == 0)
        }.sortedBy { it.startTimeHour * 60 + it.startTimeMinute }

        if (subjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add subjects first to create a schedule",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No schedule for ${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (weekNumber > 0) {
                        Text(
                            text = "(Week $weekNumber)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add a class",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            // Subject Schedule List with time slots
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    val subject = subjects.find { it.id == entry.subjectId }
                    if (subject != null) {
                        ScheduleEntryCard(
                            entry = entry,
                            subject = subject,
                            allSubjects = allSubjects,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onRemoveScheduleEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntryCard(
    entry: ScheduleEntry,
    subject: Subject,
    allSubjects: List<Subject>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                Text(
                    text = entry.getTimeRange(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (entry.weekNumber > 0) {
                    Text(
                        text = "Week ${entry.weekNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleEntryDialog(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    selectedDay: DayOfWeek,
    selectedWeek: Int,
    onDismiss: () -> Unit,
    onSave: (Long, DayOfWeek, Int, Int, Int, Int, Int) -> Unit
) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var startHour by remember { mutableStateOf("9") }
    var startMinute by remember { mutableStateOf("0") }
    var endHour by remember { mutableStateOf("10") }
    var endMinute by remember { mutableStateOf("0") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Schedule Entry") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${selectedDay.getDisplayName(TextStyle.FULL, Locale.getDefault())}${if (selectedWeek > 0) " - Week $selectedWeek" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Subject Selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedSubject?.getDisplayName(allSubjects) ?: "Select Subject",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.getDisplayName(allSubjects)) },
                                onClick = {
                                    selectedSubject = subject
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time inputs
                Text(
                    text = "Start Time",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() } && it.length <= 2) {
                                startHour = it
                            }
                        },
                        label = { Text("Hour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("0-23") }
                    )
                    OutlinedTextField(
                        value = startMinute,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() } && it.length <= 2) {
                                startMinute = it
                            }
                        },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("0-59") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "End Time",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() } && it.length <= 2) {
                                endHour = it
                            }
                        },
                        label = { Text("Hour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("0-23") }
                    )
                    OutlinedTextField(
                        value = endMinute,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() } && it.length <= 2) {
                                endMinute = it
                            }
                        },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("0-59") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedSubject != null) {
                        val sHour = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                        val sMin = startMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val eHour = endHour.toIntOrNull()?.coerceIn(0, 23) ?: 10
                        val eMin = endMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        onSave(selectedSubject!!.id, selectedDay, selectedWeek, sHour, sMin, eHour, eMin)
                    }
                },
                enabled = selectedSubject != null
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
private fun EditScheduleEntryDialog(
    entry: ScheduleEntry,
    subject: Subject?,
    allSubjects: List<Subject>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Entry") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (subject != null) {
                    Text(
                        text = subject.getDisplayName(allSubjects),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Time: ${entry.getTimeRange()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Day: ${entry.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (entry.weekNumber > 0) {
                        Text(
                            text = "Week: ${entry.weekNumber}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
