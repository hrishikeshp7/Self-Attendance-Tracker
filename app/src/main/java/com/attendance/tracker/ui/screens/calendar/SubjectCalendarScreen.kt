package com.attendance.tracker.ui.screens.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.components.CalendarView
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.NoClassGray
import com.attendance.tracker.ui.theme.PresentGreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCalendarScreen(
    subject: Subject,
    allSubjects: Map<Long, Subject>,
    selectedMonth: YearMonth,
    selectedDate: LocalDate,
    attendanceRecords: List<AttendanceRecord>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    onMarkAttendance: (AttendanceStatus, LocalDate) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Filter attendance records for this subject only
    val subjectRecords = remember(attendanceRecords, subject.id) {
        attendanceRecords.filter { it.subjectId == subject.id }
    }

    // Range selection state (null = user hasn't explicitly selected yet)
    var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
    var rangeEnd   by remember { mutableStateOf<LocalDate?>(null) }
    // Range selection must be explicitly opted into — otherwise a plain tap on a
    // second date always just moves the single-date selection there. Without this,
    // there was no way to pick a different date without accidentally spanning a
    // range from whatever was previously selected.
    var isRangeMode by remember { mutableStateOf(false) }

    val handleDateClick: (LocalDate) -> Unit = { date ->
        if (!isRangeMode) {
            // Single-date mode: every tap simply moves the selection to that date.
            rangeStart = date
            rangeEnd = null
            onDateSelected(date)
        } else {
            when {
                rangeStart == null || rangeEnd != null -> {
                    // Start a fresh range anchor
                    rangeStart = date
                    rangeEnd = null
                    onDateSelected(date)
                }
                rangeStart == date -> {
                    // Tapped the anchor again: keep waiting for the second date
                }
                else -> {
                    // Second tap on a different date: complete the range
                    rangeEnd = date
                }
            }
        }
    }

    // Effective single date for mark-attendance (falls back to ViewModel value before first tap)
    val effectiveSingleDate = rangeStart ?: selectedDate
    
    // Helper function to show snackbar with attendance status
    val showAttendanceSnackbar: (AttendanceStatus) -> Unit = { status ->
        scope.launch {
            val statusText = when (status) {
                AttendanceStatus.PRESENT -> "Marked Present"
                AttendanceStatus.ABSENT -> "Marked Absent"
                AttendanceStatus.NO_CLASS -> "Marked No Class"
            }
            snackbarHostState.showSnackbar(
                message = "$statusText",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subject.getDisplayName(allSubjects)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // On months that need 6 calendar rows, the calendar card alone can take up
                // most of the screen height; without scrolling, the panel below it (mark
                // attendance / range stats) had nowhere to go and rendered squeezed into a
                // sliver. Scrolling lets it lay out at full size and simply extend below the fold.
                .verticalScroll(rememberScrollState())
        ) {
            // Calendar View
            CalendarView(
                selectedMonth = selectedMonth,
                selectedDate = effectiveSingleDate,
                rangeStart = if (rangeEnd != null) (rangeStart ?: selectedDate) else null,
                rangeEnd = rangeEnd,
                attendanceRecords = subjectRecords,
                onDateSelected = handleDateClick,
                onMonthChanged = onMonthChanged
            )

            // Context-sensitive panel below the calendar
            if (rangeEnd != null) {
                // ── Range mode: show attendance stats for the selected range ──
                // Normalize so start ≤ end regardless of which end the user tapped first.
                // (Same normalization is applied in CalendarView/MonthCalendarGrid for visual highlighting.)
                val normalizedStart = minOf(rangeStart!!, rangeEnd!!)
                val normalizedEnd   = maxOf(rangeStart!!, rangeEnd!!)
                val rangeRecords = remember(subjectRecords, normalizedStart, normalizedEnd) {
                    subjectRecords.filter {
                        !it.date.isBefore(normalizedStart) && !it.date.isAfter(normalizedEnd)
                    }
                }
                // Sum each day's lecture count rather than counting matching days, so a
                // multi-lecture day (e.g. count = 2) contributes 2 — consistent with how
                // the subject's own presentLectures/absentLectures aggregate is computed.
                val rangePresentCount = rangeRecords.filter { it.status == AttendanceStatus.PRESENT }.sumOf { it.count }
                val rangeAbsentCount  = rangeRecords.filter { it.status == AttendanceStatus.ABSENT }.sumOf { it.count }
                val rangeTotal        = rangePresentCount + rangeAbsentCount
                val rangePercentage   = if (rangeTotal > 0) rangePresentCount * 100f / rangeTotal else 0f

                RangeStatsSection(
                    rangeStart     = normalizedStart,
                    rangeEnd       = normalizedEnd,
                    presentCount   = rangePresentCount,
                    absentCount    = rangeAbsentCount,
                    total          = rangeTotal,
                    percentage     = rangePercentage,
                    onClearRange   = {
                        rangeEnd = null
                        isRangeMode = false
                        // Keep rangeStart as the still-selected single date
                    }
                )
            } else {
                // ── Single-date mode: show mark-attendance section ──
                val selectedDateRecord = subjectRecords.find { it.date == effectiveSingleDate }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = effectiveSingleDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = {
                        isRangeMode = !isRangeMode
                        rangeEnd = null
                    }) {
                        Text(if (isRangeMode) "Cancel Range" else "Select Range")
                    }
                }

                // Hint about range selection — only shown once the user has explicitly
                // opted into range mode, so a normal tap never surprises them with a range.
                Text(
                    text = if (isRangeMode) {
                        "Tap another date to complete the range"
                    } else {
                        "Use \"Select Range\" to compare attendance across multiple dates"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )

                val isFutureDate = effectiveSingleDate.isAfter(LocalDate.now())

                if (isFutureDate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cannot mark attendance for future dates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Mark Attendance",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Attendance Action Buttons.
                            // Each button gets an equal share of the width via `weight(1f)`
                            // with a fixed gap between them, instead of a fixed 100.dp width
                            // under `SpaceEvenly` — on narrower screens that left almost no
                            // gap, so the three pill-shaped buttons visually merged into one
                            // flattened bar.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SubjectCalendarAttendanceButton(
                                    text = "Present",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.PRESENT,
                                    color = PresentGreen,
                                    onClick = {
                                        onMarkAttendance(AttendanceStatus.PRESENT, effectiveSingleDate)
                                        showAttendanceSnackbar(AttendanceStatus.PRESENT)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SubjectCalendarAttendanceButton(
                                    text = "Absent",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.ABSENT,
                                    color = AbsentRed,
                                    onClick = {
                                        onMarkAttendance(AttendanceStatus.ABSENT, effectiveSingleDate)
                                        showAttendanceSnackbar(AttendanceStatus.ABSENT)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SubjectCalendarAttendanceButton(
                                    text = "No Class",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.NO_CLASS,
                                    color = NoClassGray,
                                    onClick = {
                                        onMarkAttendance(AttendanceStatus.NO_CLASS, effectiveSingleDate)
                                        showAttendanceSnackbar(AttendanceStatus.NO_CLASS)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeStatsSection(
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    presentCount: Int,
    absentCount: Int,
    total: Int,
    percentage: Float,
    onClearRange: () -> Unit
) {
    val dateRangeFormatter = DateTimeFormatter.ofPattern("MMM d")
    val rangeLabel = "${rangeStart.format(dateRangeFormatter)} – ${rangeEnd.format(dateRangeFormatter)}"

    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rangeLabel,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClearRange) {
                Text("Clear Range")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttendanceStatItem(
                        label = "Present",
                        value = presentCount.toString(),
                        color = PresentGreen
                    )
                    AttendanceStatItem(
                        label = "Absent",
                        value = absentCount.toString(),
                        color = AbsentRed
                    )
                    AttendanceStatItem(
                        label = "Total",
                        value = total.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    AttendanceStatItem(
                        label = "Attendance",
                        value = if (total > 0) "%.1f%%".format(percentage) else "—",
                        color = if (percentage >= 75f) PresentGreen else AbsentRed
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectCalendarAttendanceButton(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else color.copy(alpha = 0.3f),
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AttendanceStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
