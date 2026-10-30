package com.attendance.tracker.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.tracker.data.SlotAttendanceStore
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.components.SubjectCard
import com.attendance.tracker.widget.refreshAllWidgets
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    todayAttendance: Map<Long, AttendanceRecord>,
    scheduleEntries: List<ScheduleEntry>,
    onMarkAttendance: (Long, AttendanceStatus, Int) -> Unit,
    onAddExtraClass: (Long, AttendanceStatus) -> Unit,
    onSetDayCounts: (Long, Int, Int) -> Unit = { _, _, _ -> },
    onClearAttendance: (Long) -> Unit = {},
    onAddSubject: () -> Unit,
    onEditSubject: (Subject) -> Unit,
    onSubjectClick: (Subject) -> Unit,
    onViewAllSubjects: (() -> Unit)? = null,
    onOpenTimeOff: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE")
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    // Only subjects actually below their target: "close to the limit" nudges just repeated
    // what each card already says and ate half the screen.
    val belowTarget: List<Subject> = remember(subjects) {
        subjects.filter { it.totalLectures > 0 && !it.isAboveRequired }
    }

    // Determine today's scheduled subjects
    val todaysScheduledIds: Set<Long> = remember(scheduleEntries, today) {
        scheduleEntries.filter { it.occursOn(today) }.map { it.subjectId }.toSet()
    }
    // If the user has not set up any schedule at all, fall back to showing all subjects
    val hasAnySchedule = scheduleEntries.isNotEmpty()
    // Also keep subjects already marked today (e.g. an extra class) so that mark stays
    // visible and can be changed or cleared from here.
    val todaysSubjects: List<Subject> = remember(subjects, todaysScheduledIds, hasAnySchedule, todayAttendance) {
        if (!hasAnySchedule) subjects else subjects.filter { it.id in todaysScheduledIds || it.id in todayAttendance }
    }

    val coroutineScope = rememberCoroutineScope()
    var slotUpdateTrigger by remember { mutableStateOf(0L) }

    val onMarkSlot = { subjectId: Long, slotIndex: Int, totalSlots: Int, status: AttendanceStatus ->
        SlotAttendanceStore.ensureSlotsInitialized(
            context, subjectId, today, totalSlots, todayAttendance[subjectId]
        )
        val res = SlotAttendanceStore.updateSlotAndCompute(
            context, subjectId, today, totalSlots, slotIndex, status
        )
        slotUpdateTrigger = System.currentTimeMillis()
        if (res.isAllNoClass) {
            onMarkAttendance(subjectId, AttendanceStatus.NO_CLASS, 1)
        } else if (res.presentCount > 0 || res.absentCount > 0) {
            onSetDayCounts(subjectId, res.presentCount, res.absentCount)
        } else {
            onClearAttendance(subjectId)
        }
        coroutineScope.launch {
            try {
                refreshAllWidgets(context)
            } catch (_: Exception) {}
        }
    }

    val todaySlots: List<TodaySlotItem> = remember(todaysSubjects, scheduleEntries, today, allSubjects) {
        todaysSubjects.flatMap { subject ->
            val dayEntries = scheduleEntries.filter { it.occursOn(today) && it.subjectId == subject.id }.sortedBy { it.startTime }
            if (dayEntries.size > 1) {
                dayEntries.indices.map { idx ->
                    TodaySlotItem(
                        subject = subject,
                        slotIndex = idx,
                        totalSlots = dayEntries.size,
                        title = "${subject.getDisplayName(allSubjects)} - Lecture ${idx + 1}"
                    )
                }
            } else {
                listOf(
                    TodaySlotItem(
                        subject = subject,
                        slotIndex = 0,
                        totalSlots = 1,
                        title = null
                    )
                )
            }
        }
    }

    // Extra-class dialog state
    var showExtraClassDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = today.format(dayFormatter),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = today.format(dateFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    Surface(
                        onClick = onOpenTimeOff,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BeachAccess,
                                contentDescription = "Time Off",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Time Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSubject,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Subject")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        if (subjects.isEmpty()) {
            EmptySubjectsState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else if (hasAnySchedule && todaySlots.isEmpty()) {
            NoClassesTodayState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onAddExtraClass = { showExtraClassDialog = true },
                onViewAllSubjects = onViewAllSubjects
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (belowTarget.isNotEmpty()) {
                    item { BelowTargetBanner(belowTarget, allSubjects) }
                }

                // Subject Cards – discrete lecture cards when multiple sessions exist today
                items(todaySlots, key = { "${it.subject.id}_${it.slotIndex}" }) { slotItem ->
                    val subject = slotItem.subject
                    val currentRecord = todayAttendance[subject.id]
                    val trigger = slotUpdateTrigger
                    val effStatus = remember(trigger, currentRecord, slotItem) {
                        SlotAttendanceStore.getEffectiveSlotStatus(
                            context, subject.id, today, slotItem.slotIndex, slotItem.totalSlots, currentRecord
                        )
                    }
                    SubjectCard(
                        subject = subject,
                        allSubjects = allSubjects,
                        currentRecord = currentRecord,
                        titleOverride = slotItem.title,
                        effectiveStatus = effStatus,
                        scheduledLectures = slotItem.totalSlots,
                        onMarkPresent = {
                            onMarkSlot(subject.id, slotItem.slotIndex, slotItem.totalSlots, AttendanceStatus.PRESENT)
                        },
                        onMarkAbsent = {
                            onMarkSlot(subject.id, slotItem.slotIndex, slotItem.totalSlots, AttendanceStatus.ABSENT)
                        },
                        onMarkNoClass = {
                            onMarkSlot(subject.id, slotItem.slotIndex, slotItem.totalSlots, AttendanceStatus.NO_CLASS)
                        },
                        onSetDayCounts = { p, a -> onSetDayCounts(subject.id, p, a) },
                        onClearAttendance = { onClearAttendance(subject.id) },
                        onEditClick = { onEditSubject(subject) },
                        onCardClick = { onSubjectClick(subject) }
                    )
                }

                // "Add Extra Class" button at the bottom of the list
                item {
                    OutlinedButton(
                        onClick = { showExtraClassDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Extra Class")
                    }
                }
            }
        }
    }

    // Extra Class Dialog
    if (showExtraClassDialog) {
        ExtraClassDialog(
            subjects = subjects,
            allSubjects = allSubjects,
            onDismiss = { showExtraClassDialog = false },
            onMarkAttendance = { subjectId, status ->
                onAddExtraClass(subjectId, status)
                showExtraClassDialog = false
            }
        )
    }
}

@Composable
private fun ExtraClassDialog(
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    onDismiss: () -> Unit,
    onMarkAttendance: (Long, AttendanceStatus) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Extra Class") },
        text = {
            Column {
                Text(
                    text = "Select the subject and mark attendance:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                subjects.forEach { subject ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subject.getDisplayName(allSubjects),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMarkAttendance(subject.id, AttendanceStatus.PRESENT)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.attendance.tracker.ui.theme.PresentGreen.copy(alpha = 0.12f),
                                    contentColor = com.attendance.tracker.ui.theme.PresentGreen
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).width(60.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("P", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMarkAttendance(subject.id, AttendanceStatus.ABSENT)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.attendance.tracker.ui.theme.AbsentRed.copy(alpha = 0.12f),
                                    contentColor = com.attendance.tracker.ui.theme.AbsentRed
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).width(60.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("A", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun NoClassesTodayState(
    modifier: Modifier = Modifier,
    onAddExtraClass: () -> Unit,
    onViewAllSubjects: (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(40.dp)  // scrolls when short (landscape)
        ) {
            Text(
                text = "🎉",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No classes today",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No subjects are scheduled for today. You can still record an extra class if one was held.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onAddExtraClass,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Extra Class")
            }
            if (onViewAllSubjects != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onViewAllSubjects,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Manage Subjects & Calendar")
                }
            }
        }
    }
}

@Composable
private fun EmptySubjectsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(40.dp)  // scrolls when short (landscape)
        ) {
            Text(
                text = "📚",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No subjects yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap the + button to add your first subject and start tracking attendance",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One collapsed line ("2 subjects below target"); tap to list them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BelowTargetBanner(subjects: List<Subject>, allSubjects: Map<Long, Subject>) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${subjects.size} subject${if (subjects.size != 1) "s" else ""} below target",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "Hide" else "Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (expanded) {
                subjects.forEach { subject ->
                    val needed = subject.classesToAttend
                    Text(
                        text = "${subject.getDisplayName(allSubjects)} · ${"%.0f".format(subject.currentAttendancePercentage)}% of ${subject.requiredAttendance}% · " +
                            if (needed >= 999) "can't reach target" else "attend $needed more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private data class TodaySlotItem(
    val subject: Subject,
    val slotIndex: Int,
    val totalSlots: Int,
    val title: String?
)

