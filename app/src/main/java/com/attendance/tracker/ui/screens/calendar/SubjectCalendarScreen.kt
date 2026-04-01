package com.attendance.tracker.ui.screens.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    allSubjects: List<Subject>,
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

    val handleDateClick: (LocalDate) -> Unit = { date ->
        when {
            rangeEnd != null -> {
                // Reset: start fresh with a new single-date selection
                rangeEnd = null
                rangeStart = date
                onDateSelected(date)
            }
            rangeStart == date -> {
                // Tapped the same date again: stay in single-date mode
            }
            rangeStart == null -> {
                // First explicit tap: select a single date
                rangeStart = date
                onDateSelected(date)
            }
            else -> {
                // Second tap on a different date: form a range
                rangeEnd = date
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
                val rangePresentCount = rangeRecords.count { it.status == AttendanceStatus.PRESENT }
                val rangeAbsentCount  = rangeRecords.count { it.status == AttendanceStatus.ABSENT }
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
                        // Keep rangeStart as the still-selected single date
                    }
                )
            } else {
                // ── Single-date mode: show mark-attendance section ──
                val selectedDateRecord = subjectRecords.find { it.date == effectiveSingleDate }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = effectiveSingleDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Hint about range selection
                Text(
                    text = "Tap another date to view range statistics",
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

                            // Attendance Action Buttons — single-click policy:
                            // clicking an already-selected status is a no-op.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                SubjectCalendarAttendanceButton(
                                    text = "Present",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.PRESENT,
                                    color = PresentGreen,
                                    onClick = {
                                        if (selectedDateRecord?.status != AttendanceStatus.PRESENT) {
                                            onMarkAttendance(AttendanceStatus.PRESENT, effectiveSingleDate)
                                            showAttendanceSnackbar(AttendanceStatus.PRESENT)
                                        }
                                    }
                                )
                                SubjectCalendarAttendanceButton(
                                    text = "Absent",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.ABSENT,
                                    color = AbsentRed,
                                    onClick = {
                                        if (selectedDateRecord?.status != AttendanceStatus.ABSENT) {
                                            onMarkAttendance(AttendanceStatus.ABSENT, effectiveSingleDate)
                                            showAttendanceSnackbar(AttendanceStatus.ABSENT)
                                        }
                                    }
                                )
                                SubjectCalendarAttendanceButton(
                                    text = "No Class",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.NO_CLASS,
                                    color = NoClassGray,
                                    onClick = {
                                        if (selectedDateRecord?.status != AttendanceStatus.NO_CLASS) {
                                            onMarkAttendance(AttendanceStatus.NO_CLASS, effectiveSingleDate)
                                            showAttendanceSnackbar(AttendanceStatus.NO_CLASS)
                                        }
                                    }
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else color.copy(alpha = 0.3f),
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else color
        ),
        modifier = Modifier.width(100.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
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
