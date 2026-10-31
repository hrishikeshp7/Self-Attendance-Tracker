package com.attendance.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TimeOffType(val title: String, val status: AttendanceStatus?, val icon: ImageVector) {
    HOLIDAY("Holiday", AttendanceStatus.NO_CLASS, Icons.Default.BeachAccess),
    LEAVE("Absent", AttendanceStatus.ABSENT, Icons.Default.FlightTakeoff),
    CLEAR("Reset", null, Icons.Default.RestartAlt)
}

private enum class QuickPreset {
    TODAY, THIS_WEEK, NEXT_WEEK, CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOffSheet(
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    scheduleEntries: List<ScheduleEntry>,
    initialRange: Pair<LocalDate, LocalDate>? = null,
    onDismiss: () -> Unit,
    onApply: (startDate: LocalDate, endDate: LocalDate, status: AttendanceStatus?, subjectIds: Set<Long>?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }
    val thisMonday = remember { today.with(DayOfWeek.MONDAY) }
    val thisSunday = remember { thisMonday.plusDays(6) }

    var selectedType by remember { mutableStateOf(TimeOffType.HOLIDAY) }
    var selectedPreset by remember {
        mutableStateOf(if (initialRange != null) QuickPreset.CUSTOM else QuickPreset.THIS_WEEK)
    }

    var startDate by remember { mutableStateOf(initialRange?.first ?: thisMonday) }
    var endDate by remember { mutableStateOf(initialRange?.second ?: thisSunday) }

    var applyToAllSubjects by remember { mutableStateOf(true) }
    var selectedSubjectIds by remember { mutableStateOf(subjects.map { it.id }.toSet()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    fun applyPreset(preset: QuickPreset) {
        selectedPreset = preset
        when (preset) {
            QuickPreset.TODAY -> {
                startDate = today
                endDate = today
            }
            QuickPreset.THIS_WEEK -> {
                startDate = thisMonday
                endDate = thisSunday
            }
            QuickPreset.NEXT_WEEK -> {
                val nextMonday = thisMonday.plusWeeks(1)
                startDate = nextMonday
                endDate = nextMonday.plusDays(6)
            }
            QuickPreset.CUSTOM -> {}
        }
    }

    val effectiveSubjectIds = if (applyToAllSubjects) null else selectedSubjectIds
    val impact = remember(startDate, endDate, effectiveSubjectIds, scheduleEntries, subjects) {
        if (endDate.isBefore(startDate)) {
            Pair(0, 0)
        } else {
            val targetIds = effectiveSubjectIds ?: subjects.map { it.id }.toSet()
            var totalLectures = 0
            var daysWithClasses = 0
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val d = curr
                if (scheduleEntries.isNotEmpty()) {
                    val dayEntries = scheduleEntries.filter { it.occursOn(d) && it.subjectId in targetIds }
                    if (dayEntries.isNotEmpty()) {
                        daysWithClasses++
                        totalLectures += dayEntries.size
                    }
                } else {
                    if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) {
                        val count = subjects.count { it.id in targetIds }
                        if (count > 0) {
                            daysWithClasses++
                            totalLectures += count
                        }
                    }
                }
                curr = curr.plusDays(1)
            }
            Pair(totalLectures, daysWithClasses)
        }
    }

    val zone = remember { ZoneId.of("UTC") }
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        startDate = picked
                        if (endDate.isBefore(picked)) endDate = picked
                        selectedPreset = QuickPreset.CUSTOM
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(zone).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        endDate = picked
                        if (startDate.isAfter(picked)) startDate = picked
                        selectedPreset = QuickPreset.CUSTOM
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Time Off",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeOffType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedType = type
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = type.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Date Range Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPreset == QuickPreset.TODAY,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        applyPreset(QuickPreset.TODAY)
                    },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = selectedPreset == QuickPreset.THIS_WEEK,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        applyPreset(QuickPreset.THIS_WEEK)
                    },
                    label = { Text("This Week") }
                )
                FilterChip(
                    selected = selectedPreset == QuickPreset.NEXT_WEEK,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        applyPreset(QuickPreset.NEXT_WEEK)
                    },
                    label = { Text("Next Week") }
                )
            }

            // Date Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Start Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startDate.format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedCard(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "End Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = endDate.format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Scope (Subjects)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Apply to",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = applyToAllSubjects,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            applyToAllSubjects = true
                            selectedSubjectIds = subjects.map { it.id }.toSet()
                        },
                        label = { Text("All Subjects") }
                    )
                    FilterChip(
                        selected = !applyToAllSubjects,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            applyToAllSubjects = false
                        },
                        label = {
                            Text(if (!applyToAllSubjects && selectedSubjectIds.size < subjects.size) "${selectedSubjectIds.size} Selected" else "Choose...")
                        }
                    )
                }
            }

            AnimatedVisibility(visible = !applyToAllSubjects) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { sub ->
                        val isChosen = sub.id in selectedSubjectIds
                        FilterChip(
                            selected = isChosen,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedSubjectIds = if (isChosen) {
                                    if (selectedSubjectIds.size > 1) selectedSubjectIds - sub.id else selectedSubjectIds
                                } else {
                                    selectedSubjectIds + sub.id
                                }
                            },
                            label = { Text(sub.getDisplayName(allSubjects)) }
                        )
                    }
                }
            }

            // Clean Impact Summary
            val (totalLectures, daysCount) = impact
            val summaryText = if (totalLectures > 0) {
                when (selectedType) {
                    TimeOffType.HOLIDAY -> "$totalLectures classes ($daysCount days) marked as Holiday (0% penalty)"
                    TimeOffType.LEAVE -> "$totalLectures classes ($daysCount days) marked Absent"
                    TimeOffType.CLEAR -> "Attendance reset for $totalLectures classes ($daysCount days)"
                }
            } else {
                "No scheduled classes in this date range"
            }

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Apply Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply(startDate, endDate, selectedType.status, effectiveSubjectIds)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = when (selectedType) {
                        TimeOffType.HOLIDAY -> "Apply Holiday"
                        TimeOffType.LEAVE -> "Mark Absent"
                        TimeOffType.CLEAR -> "Reset Attendance"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
