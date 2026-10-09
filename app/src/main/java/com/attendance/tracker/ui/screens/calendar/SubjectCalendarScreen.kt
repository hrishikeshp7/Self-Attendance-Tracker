package com.attendance.tracker.ui.screens.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
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
    onAddExtraClass: (AttendanceStatus, LocalDate) -> Unit,
    onClearAttendance: (LocalDate) -> Unit,
    onSetDayCounts: (LocalDate, Int, Int) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    
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
    
    var showExtraClassDialog by remember { mutableStateOf(false) }
    var showEditLecturesDialog by remember { mutableStateOf(false) }
    if (showEditLecturesDialog) {
        val rec = subjectRecords.find { it.date == effectiveSingleDate }
        val present = rec?.presentCount ?: 0
        val absent = rec?.absentCount ?: 0
        AlertDialog(
            onDismissRequest = { showEditLecturesDialog = false },
            title = { Text("Lectures on ${effectiveSingleDate.format(DateTimeFormatter.ofPattern("MMM d"))}") },
            text = {
                Column {
                    CountStepper("Present", PresentGreen, present) { onSetDayCounts(effectiveSingleDate, it, absent) }
                    CountStepper("Absent", AbsentRed, absent) { onSetDayCounts(effectiveSingleDate, present, it) }
                }
            },
            confirmButton = { TextButton(onClick = { showEditLecturesDialog = false }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = {
                    onClearAttendance(effectiveSingleDate)
                    showEditLecturesDialog = false
                }) { Text("Clear day", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
    if (showExtraClassDialog) {
        AlertDialog(
            onDismissRequest = { showExtraClassDialog = false },
            title = { Text("Extra class on ${effectiveSingleDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}") },
            text = { Text("Were you present or absent for it?") },
            confirmButton = {
                TextButton(onClick = {
                    onAddExtraClass(AttendanceStatus.PRESENT, effectiveSingleDate)
                    showExtraClassDialog = false
                }) { Text("Present", color = PresentGreen) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExtraClassDialog = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        onAddExtraClass(AttendanceStatus.ABSENT, effectiveSingleDate)
                        showExtraClassDialog = false
                    }) { Text("Absent", color = AbsentRed) }
                }
            }
        )
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
                val rangePresentCount = rangeRecords.sumOf { it.presentCount }
                val rangeAbsentCount  = rangeRecords.sumOf { it.absentCount }
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
                            val dayRecord = selectedDateRecord
                            val dayCount = (dayRecord?.presentCount ?: 0) + (dayRecord?.absentCount ?: 0)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mark Attendance",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (dayCount > 1) {
                                    Text(
                                        text = "${dayRecord!!.presentCount} present · ${dayRecord.absentCount} absent",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // With more than one lecture on the day, the main buttons would flip or
                            // wipe all of them at once, so they give way to the per-lecture editor below.
                            val multi = ((selectedDateRecord?.presentCount ?: 0) + (selectedDateRecord?.absentCount ?: 0)) > 1

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
                                    enabled = !multi,
                                    text = "Present",
                                    isSelected = (selectedDateRecord?.presentCount ?: 0) > 0,
                                    color = PresentGreen,
                                    onClick = {
                                        onMarkAttendance(AttendanceStatus.PRESENT, effectiveSingleDate)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SubjectCalendarAttendanceButton(
                                    enabled = !multi,
                                    text = "Absent",
                                    isSelected = (selectedDateRecord?.absentCount ?: 0) > 0,
                                    color = AbsentRed,
                                    onClick = {
                                        onMarkAttendance(AttendanceStatus.ABSENT, effectiveSingleDate)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SubjectCalendarAttendanceButton(
                                    enabled = !multi,
                                    text = "No Class",
                                    isSelected = selectedDateRecord?.status == AttendanceStatus.NO_CLASS,
                                    color = NoClassGray,
                                    onClick = {
                                        onMarkAttendance(AttendanceStatus.NO_CLASS, effectiveSingleDate)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Extra classes are rare, so this is one quiet row rather than more
                            // buttons: "Extra class?" asks present/absent in a dialog, and works for
                            // any past date. A day can hold both (attended one, missed another).
                            val recorded = selectedDateRecord
                            val dayLectures = (recorded?.presentCount ?: 0) + (recorded?.absentCount ?: 0)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { showExtraClassDialog = true }) { Text("Extra class?") }
                                if (dayLectures > 1) {
                                    TextButton(onClick = { showEditLecturesDialog = true }) { Text("Edit lectures") }
                                } else if (recorded != null) {
                                    TextButton(onClick = { onClearAttendance(effectiveSingleDate) }) {
                                        Text("Clear day", color = MaterialTheme.colorScheme.error)
                                    }
                                }
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
private fun CountStepper(label: String, color: Color, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // One slim tinted pill: "−  2  +". Whole 36dp halves are the tap targets, no outlines.
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(width = 40.dp, height = 36.dp)
                        .clickable(enabled = value > 0, role = Role.Button) { onChange(value - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "−",
                        color = color.copy(alpha = if (value > 0) 1f else 0.3f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 24.dp)
                )
                Box(
                    modifier = Modifier.size(width = 40.dp, height = 36.dp)
                        .clickable(role = Role.Button) { onChange(value + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = color, style = MaterialTheme.typography.titleMedium)
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
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
