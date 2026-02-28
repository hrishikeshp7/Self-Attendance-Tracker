package com.attendance.tracker.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.components.SubjectCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    todayAttendance: Map<Long, AttendanceRecord>,
    scheduleEntries: List<ScheduleEntry>,
    canUndo: Boolean,
    canRedo: Boolean,
    onMarkAttendance: (Long, AttendanceStatus) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddSubject: () -> Unit,
    onEditSubject: (Subject) -> Unit,
    onSubjectClick: (Subject) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE")
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showAttendanceSnackbar: (String, AttendanceStatus) -> Unit = { subjectName, status ->
        scope.launch {
            val statusText = when (status) {
                AttendanceStatus.PRESENT -> "Marked Present"
                AttendanceStatus.ABSENT -> "Marked Absent"
                AttendanceStatus.NO_CLASS -> "Marked No Class"
            }
            snackbarHostState.showSnackbar(
                message = "$subjectName: $statusText",
                duration = SnackbarDuration.Short
            )
        }
    }

    val weeklyClassCount: Map<Long, Int> = remember(scheduleEntries) {
        scheduleEntries.groupBy { it.subjectId }.mapValues { it.value.size }
    }

    val atRiskSubjects: List<Pair<Subject, String>> = remember(subjects, scheduleEntries) {
        subjects.mapNotNull { subject ->
            val alert = computeAttendanceAlert(subject, weeklyClassCount[subject.id] ?: 0)
            if (alert != null) Pair(subject, alert) else null
        }
    }

    // Determine today's scheduled subjects
    val todayDayOfWeek = today.dayOfWeek
    val todaysScheduledIds: Set<Long> = remember(scheduleEntries, todayDayOfWeek) {
        scheduleEntries.filter { it.dayOfWeek == todayDayOfWeek }.map { it.subjectId }.toSet()
    }
    // If the user has not set up any schedule at all, fall back to showing all subjects
    val hasAnySchedule = scheduleEntries.isNotEmpty()
    val todaysSubjects: List<Subject> = remember(subjects, todaysScheduledIds, hasAnySchedule) {
        if (!hasAnySchedule) subjects else subjects.filter { it.id in todaysScheduledIds }
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
                actions = {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        )
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        if (subjects.isEmpty()) {
            EmptySubjectsState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else if (hasAnySchedule && todaysSubjects.isEmpty()) {
            NoClassesTodayState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onAddExtraClass = { showExtraClassDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // Alert banners
                if (atRiskSubjects.isNotEmpty()) {
                    item {
                        atRiskSubjects.forEach { (subject, message) ->
                            AttendanceAlertBanner(
                                subjectName = subject.name,
                                message = message,
                                isBelow = !subject.isAboveRequired
                            )
                        }
                    }
                }

                // Subject Cards – only today's scheduled subjects, single-mark mode
                items(todaysSubjects, key = { it.id }) { subject ->
                    SubjectCard(
                        subject = subject,
                        allSubjects = allSubjects,
                        currentRecord = todayAttendance[subject.id],
                        onMarkPresent = {
                            onMarkAttendance(subject.id, AttendanceStatus.PRESENT)
                            showAttendanceSnackbar(subject.name, AttendanceStatus.PRESENT)
                        },
                        onMarkAbsent = {
                            onMarkAttendance(subject.id, AttendanceStatus.ABSENT)
                            showAttendanceSnackbar(subject.name, AttendanceStatus.ABSENT)
                        },
                        onMarkNoClass = {
                            onMarkAttendance(subject.id, AttendanceStatus.NO_CLASS)
                            showAttendanceSnackbar(subject.name, AttendanceStatus.NO_CLASS)
                        },
                        onEditClick = { onEditSubject(subject) },
                        onCardClick = { onSubjectClick(subject) },
                        allowMultipleMark = false
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
            onConfirm = { subjectId ->
                val subject = subjects.find { it.id == subjectId }
                if (subject != null) {
                    onMarkAttendance(subject.id, AttendanceStatus.PRESENT)
                    showAttendanceSnackbar(subject.name, AttendanceStatus.PRESENT)
                }
                showExtraClassDialog = false
            }
        )
    }
}

@Composable
private fun ExtraClassDialog(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Extra Class") },
        text = {
            Column {
                Text(
                    text = "Select the subject that had an extra class today:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                subjects.forEach { subject ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedSubjectId == subject.id,
                            onClick = { selectedSubjectId = subject.id }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = subject.getDisplayName(allSubjects),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedSubjectId?.let { onConfirm(it) } },
                enabled = selectedSubjectId != null
            ) {
                Text("Mark Present")
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
private fun NoClassesTodayState(
    modifier: Modifier = Modifier,
    onAddExtraClass: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
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
            modifier = Modifier.padding(40.dp)
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

/**
 * Computes an alert message for a subject based on attendance risk.
 * Returns null if no alert is needed.
 */
private fun computeAttendanceAlert(subject: Subject, classesPerWeek: Int): String? {
    if (subject.totalLectures == 0) return null

    return if (!subject.isAboveRequired) {
        val needed = subject.classesToAttend
        if (needed >= 999) {
            "Cannot recover attendance for ${subject.name} (100% required with absences)"
        } else {
            "⚠\uFE0F ${subject.name}: Attend $needed more class${if (needed != 1) "es" else ""} to reach ${subject.requiredAttendance}%"
        }
    } else if (subject.classesCanBunk in 0..2) {
        if (classesPerWeek > 0) {
            val daysUntilBelow = ((subject.classesCanBunk.toFloat() / classesPerWeek) * 7).roundToInt()
            if (daysUntilBelow <= 14) {
                "⚠\uFE0F ${subject.name}: Likely to fall below ${subject.requiredAttendance}% in ~$daysUntilBelow day${if (daysUntilBelow != 1) "s" else ""} if absent"
            } else {
                null
            }
        } else {
            "⚠\uFE0F ${subject.name}: Can only miss ${subject.classesCanBunk} more class${if (subject.classesCanBunk != 1) "es" else ""}"
        }
    } else {
        null
    }
}

@Composable
private fun AttendanceAlertBanner(
    subjectName: String,
    message: String,
    isBelow: Boolean
) {
    val containerColor = if (isBelow) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isBelow) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

