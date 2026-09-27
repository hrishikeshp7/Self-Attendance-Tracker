package com.attendance.tracker.ui.screens.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    scheduleEntries: List<ScheduleEntry>,
    onAddScheduleEntry: (Long, DayOfWeek) -> Unit,
    onRemoveScheduleEntry: (ScheduleEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    // Initialize pager state for days of the week
    val pagerState = rememberPagerState(
        initialPage = DayOfWeek.MONDAY.ordinal,
        pageCount = { DayOfWeek.entries.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // The pager is the single source of truth for the selected day; deriving it
    // (instead of mirroring it into separate state via LaunchedEffects) avoids a
    // race where an in-flight animateScrollToPage gets overwritten by intermediate
    // page changes and lands one day off from the tapped tab.
    val selectedDay = DayOfWeek.entries[pagerState.currentPage]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Schedule") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(day.ordinal)
                            }
                        },
                        text = {
                            Text(
                                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            )
                        }
                    )
                }
            }

            // Optimized lookup for schedule entries
            val entryMap = remember(scheduleEntries) {
                scheduleEntries.associateBy { it.subjectId to it.dayOfWeek }
            }

            // Horizontal Pager for swipeable days
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val day = DayOfWeek.entries[page]
                
                DayScheduleContent(
                    day = day,
                    subjects = subjects,
                    allSubjects = allSubjects,
                    entryMap = entryMap,
                    onAddScheduleEntry = onAddScheduleEntry,
                    onRemoveScheduleEntry = onRemoveScheduleEntry
                )
            }
        }
    }
}

@Composable
private fun DayScheduleContent(
    day: DayOfWeek,
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    entryMap: Map<Pair<Long, DayOfWeek>, ScheduleEntry>,
    onAddScheduleEntry: (Long, DayOfWeek) -> Unit,
    onRemoveScheduleEntry: (ScheduleEntry) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Day Header
        Text(
            text = day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
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
                    val entry = entryMap[subject.id to day]
                    val isScheduled = entry != null

                    ScheduleSubjectItem(
                        subject = subject,
                        allSubjects = allSubjects,
                        isScheduled = isScheduled,
                        onToggle = { checked ->
                            if (checked) {
                                onAddScheduleEntry(subject.id, day)
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

@Composable
private fun ScheduleSubjectItem(
    subject: Subject,
    allSubjects: Map<Long, Subject>,
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
