package com.attendance.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.NoClassGray
import com.attendance.tracker.ui.theme.PresentGreen
import com.attendance.tracker.ui.theme.subjectAvatarColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TimeOffType(val title: String, val subtitle: String, val status: AttendanceStatus?, val icon: ImageVector) {
    HOLIDAY("Holiday", "No penalty", AttendanceStatus.NO_CLASS, Icons.Default.BeachAccess),
    LEAVE("Leave", "Mark absent", AttendanceStatus.ABSENT, Icons.Default.FlightTakeoff),
    CLEAR("Reset", "Clear range", null, Icons.Default.RestartAlt)
}

enum class DurationPreset(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    THIS_WEEK("This Week"),
    NEXT_WEEK("Next Week"),
    CUSTOM("Custom...")
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
        mutableStateOf(
            if (initialRange != null) DurationPreset.CUSTOM else DurationPreset.THIS_WEEK
        )
    }

    var startDate by remember {
        mutableStateOf(
            initialRange?.first ?: thisMonday
        )
    }
    var endDate by remember {
        mutableStateOf(
            initialRange?.second ?: thisSunday
        )
    }

    var applyToAllSubjects by remember { mutableStateOf(true) }
    var selectedSubjectIds by remember {
        mutableStateOf(subjects.map { it.id }.toSet())
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Synchronize dates when preset changes
    fun applyPreset(preset: DurationPreset) {
        selectedPreset = preset
        when (preset) {
            DurationPreset.TODAY -> {
                startDate = today
                endDate = today
            }
            DurationPreset.TOMORROW -> {
                val tomorrow = today.plusDays(1)
                startDate = tomorrow
                endDate = tomorrow
            }
            DurationPreset.THIS_WEEK -> {
                startDate = thisMonday
                endDate = thisSunday
            }
            DurationPreset.NEXT_WEEK -> {
                val nextMonday = thisMonday.plusWeeks(1)
                startDate = nextMonday
                endDate = nextMonday.plusDays(6)
            }
            DurationPreset.CUSTOM -> {
                // Keep current start/end dates
            }
        }
    }

    // Impact calculation
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

    // Date picker dialogs
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
                        selectedPreset = DurationPreset.CUSTOM
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
                        selectedPreset = DurationPreset.CUSTOM
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
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Time Off",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Schedule holidays, vacations, or breaks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // iOS-style Segmented Control for Type
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TimeOffType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        val bgColor by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            animationSpec = tween(150),
                            label = "segmentBg"
                        )
                        val contentColor = when {
                            isSelected && type == TimeOffType.HOLIDAY -> MaterialTheme.colorScheme.primary
                            isSelected && type == TimeOffType.LEAVE -> AbsentRed
                            isSelected && type == TimeOffType.CLEAR -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedType = type
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = type.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor
                                )
                                Text(
                                    text = type.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = contentColor.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Duration
            Text(
                text = "DURATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Preset Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationPreset.entries.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            applyPreset(preset)
                        },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Custom Date Range Display
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateSelectBox(
                    label = "START DATE",
                    date = startDate,
                    formatter = dateFormatter,
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
                DateSelectBox(
                    label = "END DATE",
                    date = endDate,
                    formatter = dateFormatter,
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Scope (Subjects)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "APPLY TO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (applyToAllSubjects) "All Subjects (${subjects.size})" else "${selectedSubjectIds.size} Selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = applyToAllSubjects,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        applyToAllSubjects = true
                        selectedSubjectIds = subjects.map { it.id }.toSet()
                    },
                    label = { Text("All Subjects") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = !applyToAllSubjects,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        applyToAllSubjects = false
                    },
                    label = { Text("Choose Specific...") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            AnimatedVisibility(visible = !applyToAllSubjects) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Tap subjects to include in this time off:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subjects.forEach { sub ->
                            val isChosen = sub.id in selectedSubjectIds
                            val color = subjectAvatarColor(sub.id)
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
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                },
                                label = {
                                    Text(
                                        text = sub.getDisplayName(allSubjects),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Apple Health style Live Impact Card
            val (totalLectures, daysCount) = impact
            val rangeText = if (startDate == endDate) {
                startDate.format(fullDateFormatter)
            } else {
                "${startDate.format(dateFormatter)} – ${endDate.format(dateFormatter)}"
            }

            val impactContainerColor = when (selectedType) {
                TimeOffType.HOLIDAY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                TimeOffType.LEAVE -> AbsentRed.copy(alpha = 0.12f)
                TimeOffType.CLEAR -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            val impactAccentColor = when (selectedType) {
                TimeOffType.HOLIDAY -> MaterialTheme.colorScheme.primary
                TimeOffType.LEAVE -> AbsentRed
                TimeOffType.CLEAR -> MaterialTheme.colorScheme.onSurface
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = impactContainerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = selectedType.icon,
                            contentDescription = null,
                            tint = impactAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedType) {
                                TimeOffType.HOLIDAY -> "Holiday Season ($rangeText)"
                                TimeOffType.LEAVE -> "Leave & Vacation ($rangeText)"
                                TimeOffType.CLEAR -> "Clear Attendance ($rangeText)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = impactAccentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (totalLectures > 0) {
                            when (selectedType) {
                                TimeOffType.HOLIDAY ->
                                    "$totalLectures scheduled lecture${if (totalLectures != 1) "s" else ""} across $daysCount day${if (daysCount != 1) "s" else ""} will be marked as Holiday (No Class)."
                                TimeOffType.LEAVE ->
                                    "$totalLectures scheduled lecture${if (totalLectures != 1) "s" else ""} across $daysCount day${if (daysCount != 1) "s" else ""} will be marked as Absent."
                                TimeOffType.CLEAR ->
                                    "Attendance records for $totalLectures scheduled lecture${if (totalLectures != 1) "s" else ""} across $daysCount day${if (daysCount != 1) "s" else ""} will be reset."
                            }
                        } else {
                            "No lectures currently scheduled in this date range. Will apply to selected dates."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when (selectedType) {
                            TimeOffType.HOLIDAY -> "✨ Attendance percentage is completely protected (0% penalty)."
                            TimeOffType.LEAVE -> "⚠️ These missed classes will count towards absent lectures."
                            TimeOffType.CLEAR -> "Existing marks in this range will be cleared."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Apply Button
            val applyButtonText = when (selectedType) {
                TimeOffType.HOLIDAY -> "Apply Holiday"
                TimeOffType.LEAVE -> "Apply Leave"
                TimeOffType.CLEAR -> "Reset Attendance"
            }
            val buttonColor = when (selectedType) {
                TimeOffType.HOLIDAY -> MaterialTheme.colorScheme.primary
                TimeOffType.LEAVE -> AbsentRed
                TimeOffType.CLEAR -> MaterialTheme.colorScheme.secondary
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply(startDate, endDate, selectedType.status, effectiveSubjectIds)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = applyButtonText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DateSelectBox(
    label: String,
    date: LocalDate,
    formatter: DateTimeFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = date.format(formatter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
