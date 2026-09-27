package com.attendance.tracker.ui.screens.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// A small, pleasant rotation of tints used to give each subject a distinct,
// stable avatar color (derived from its id) without needing a color field
// on the Subject model itself.
private val SubjectAvatarPalette = listOf(
    Color(0xFF4361EE), Color(0xFF4CC9F0), Color(0xFFF72585),
    Color(0xFFF9A826), Color(0xFF06D6A0), Color(0xFF7209B7),
    Color(0xFFE63946), Color(0xFF3A86FF)
)

private fun subjectAvatarColor(subjectId: Long): Color =
    SubjectAvatarPalette[(subjectId.mod(SubjectAvatarPalette.size.toLong())).toInt()]

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
    val today = remember { LocalDate.now().dayOfWeek }

    // How many subjects are scheduled on each day, and how many days/week each
    // subject meets — both drive the small counts shown in the tabs and list.
    val countsByDay: Map<DayOfWeek, Int> = remember(scheduleEntries) {
        scheduleEntries.groupBy { it.dayOfWeek }.mapValues { it.value.size }
    }
    val weeklyCountBySubject: Map<Long, Int> = remember(scheduleEntries) {
        scheduleEntries.groupBy { it.subjectId }.mapValues { it.value.size }
    }
    val entryMap = remember(scheduleEntries) {
        scheduleEntries.associateBy { it.subjectId to it.dayOfWeek }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Weekly Schedule")
                        Text(
                            text = "${scheduleEntries.size} class${if (scheduleEntries.size != 1) "es" else ""} scheduled this week",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                DayOfWeek.entries.forEach { day ->
                    val isToday = day == today
                    Tab(
                        selected = selectedDay == day,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(day.ordinal)
                            }
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                        fontWeight = if (selectedDay == day) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                                val count = countsByDay[day] ?: 0
                                if (count > 0) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                    subjects = subjects,
                    allSubjects = allSubjects,
                    entryMap = entryMap,
                    weeklyCountBySubject = weeklyCountBySubject,
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
    weeklyCountBySubject: Map<Long, Int>,
    onAddScheduleEntry: (Long, DayOfWeek) -> Unit,
    onRemoveScheduleEntry: (ScheduleEntry) -> Unit
) {
    val scheduledCount = subjects.count { entryMap.containsKey(it.id to day) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Day Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (scheduledCount > 0) {
                        "$scheduledCount class${if (scheduledCount != 1) "es" else ""} scheduled"
                    } else {
                        "No classes scheduled"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (subjects.isEmpty()) {
            EmptyScheduleState(
                modifier = Modifier.fillMaxSize(),
                message = "Add subjects first to create a schedule"
            )
        } else {
            // Subject Schedule List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjects, key = { it.id }) { subject ->
                    val entry = entryMap[subject.id to day]
                    val isScheduled = entry != null

                    ScheduleSubjectItem(
                        subject = subject,
                        allSubjects = allSubjects,
                        isScheduled = isScheduled,
                        weeklyCount = weeklyCountBySubject[subject.id] ?: 0,
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
private fun EmptyScheduleState(modifier: Modifier = Modifier, message: String) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.EventBusy,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScheduleSubjectItem(
    subject: Subject,
    allSubjects: Map<Long, Subject>,
    isScheduled: Boolean,
    weeklyCount: Int,
    onToggle: (Boolean) -> Unit
) {
    val avatarColor = subjectAvatarColor(subject.id)
    val displayName = subject.getDisplayName(allSubjects)
    val initials = displayName
        .split(" ", "-")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isScheduled) 3.dp else 1.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isScheduled) {
                avatarColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = if (isScheduled) 0.9f else 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (weeklyCount > 0) {
                        "Meets $weeklyCount day${if (weeklyCount != 1) "s" else ""}/week"
                    } else {
                        "Not scheduled yet"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isScheduled,
                onCheckedChange = onToggle
            )
        }
    }
}
