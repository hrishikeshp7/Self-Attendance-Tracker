package com.attendance.tracker.ui.screens.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // Day Header
            Text(
                text = selectedDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge,
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
                        text = "Add subjects first to create a schedule",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Subject Schedule List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(subjects, key = { it.id }) { subject ->
                        val isScheduled = scheduleEntries.any { 
                            it.subjectId == subject.id && it.dayOfWeek == selectedDay 
                        }
                        val entry = scheduleEntries.find { 
                            it.subjectId == subject.id && it.dayOfWeek == selectedDay 
                        }

                        ScheduleSubjectItem(
                            subject = subject,
                            allSubjects = allSubjects,
                            isScheduled = isScheduled,
                            onToggle = { checked ->
                                if (checked) {
                                    onAddScheduleEntry(subject.id, selectedDay)
                                } else {
                                    entry?.let { onRemoveScheduleEntry(it) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSubjectItem(
    subject: Subject,
    allSubjects: List<Subject>,
    isScheduled: Boolean,
    onToggle: (Boolean) -> Unit
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
            Text(
                text = subject.getDisplayName(allSubjects),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isScheduled,
                onCheckedChange = onToggle
            )
        }
    }
}
