package com.attendance.tracker.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.theme.subjectAvatarColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HOUR_HEIGHT = 64.dp
private val GRID_TOP_PADDING = 8.dp
private val HALF_HOUR_HEIGHT = HOUR_HEIGHT / 2
private val DAY_COLUMN_WIDTH = 108.dp
private val TIME_AXIS_WIDTH = 48.dp
private const val DEFAULT_GRID_START_HOUR = 7
private const val DEFAULT_GRID_END_HOUR = 21 // exclusive

private val timeLabelFormatter = DateTimeFormatter.ofPattern("h a")
private val timeSheetFormatter = DateTimeFormatter.ofPattern("h:mm a")

/**
 * A Google Calendar / Notion Calendar style weekly timetable for scheduling lectures:
 * day columns across the top, a time axis down the side, and lecture blocks positioned
 * by their start/end time. Tapping an empty slot creates a lecture there; tapping an
 * existing block edits or deletes it.
 *
 * This is the default Schedule experience; the older day-list/toggle screen
 * ([ScheduleScreen]) is still reachable from Settings for anyone who prefers it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyCalendarScreen(
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    scheduleEntries: List<ScheduleEntry>,
    onAddLecture: (subjectId: Long, dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime) -> Unit,
    onUpdateLecture: (entry: ScheduleEntry, subjectId: Long, dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime) -> Unit,
    onDeleteLecture: (ScheduleEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorState by remember { mutableStateOf<LectureEditorState?>(null) }
    var showAddSubjectsHint by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Timetable")
                        Text(
                            text = "${scheduleEntries.size} lecture${if (scheduleEntries.size != 1) "s" else ""} scheduled this week",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (subjects.isEmpty()) {
                        showAddSubjectsHint = true
                    } else {
                        val start = LocalTime.of(9, 0)
                        editorState = LectureEditorState(
                            existingEntry = null,
                            subjectId = subjects.first().id,
                            dayOfWeek = LocalDate.now().dayOfWeek,
                            startTime = start,
                            endTime = defaultEndTime(start)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Lecture")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (subjects.isEmpty()) {
            EmptyTimetableState(modifier = Modifier.fillMaxSize().padding(paddingValues))
        } else {
            WeekGrid(
                allSubjects = allSubjects,
                scheduleEntries = scheduleEntries,
                onCellTap = { day, time ->
                    editorState = LectureEditorState(
                        existingEntry = null,
                        subjectId = subjects.first().id,
                        dayOfWeek = day,
                        startTime = time,
                        endTime = defaultEndTime(time)
                    )
                },
                onBlockTap = { entry ->
                    editorState = LectureEditorState(
                        existingEntry = entry,
                        subjectId = entry.subjectId,
                        dayOfWeek = entry.dayOfWeek,
                        startTime = entry.startTime,
                        endTime = entry.endTime
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }

    editorState?.let { state ->
        LectureEditorSheet(
            state = state,
            subjects = subjects,
            allSubjects = allSubjects,
            onDismiss = { editorState = null },
            onSave = { subjectId, day, start, end ->
                val existing = state.existingEntry
                if (existing != null) {
                    onUpdateLecture(existing, subjectId, day, start, end)
                } else {
                    onAddLecture(subjectId, day, start, end)
                }
            },
            onDelete = state.existingEntry?.let { entry -> { onDeleteLecture(entry) } }
        )
    }

    if (showAddSubjectsHint) {
        AlertDialog(
            onDismissRequest = { showAddSubjectsHint = false },
            title = { Text("Add a subject first") },
            text = { Text("You need at least one subject before you can schedule a lecture for it.") },
            confirmButton = {
                TextButton(onClick = { showAddSubjectsHint = false }) { Text("OK") }
            }
        )
    }
}

private fun defaultEndTime(start: LocalTime): LocalTime {
    val endMinutes = start.hour * 60 + start.minute + 60
    return if (endMinutes >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(endMinutes / 60, endMinutes % 60)
}

private fun endAfter(start: LocalTime, minutes: Int): LocalTime {
    val end = start.hour * 60 + start.minute + minutes
    return if (end >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(end / 60, end % 60)
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, time: LocalTime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time.format(timeSheetFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Change $label time",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private data class LectureEditorState(
    val existingEntry: ScheduleEntry?,
    val subjectId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime
)

@Composable
private fun EmptyTimetableState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Text(text = "📅", fontSize = 64.sp)
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
                text = "Add a subject from the Subjects tab, then come back here to build your timetable.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -----------------------------------------------------------------------
// Week grid
// -----------------------------------------------------------------------

@Composable
private fun WeekGrid(
    allSubjects: Map<Long, Subject>,
    scheduleEntries: List<ScheduleEntry>,
    onCellTap: (DayOfWeek, LocalTime) -> Unit,
    onBlockTap: (ScheduleEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now().dayOfWeek }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Expand the visible range to fit any lecture outside the default 7 AM–9 PM window,
    // so a slot set unusually early/late is never scrolled out of reach.
    val gridStartHour = remember(scheduleEntries) {
        val earliest = scheduleEntries.minOfOrNull { it.startTime.hour } ?: DEFAULT_GRID_START_HOUR
        minOf(DEFAULT_GRID_START_HOUR, earliest)
    }
    val gridEndHour = remember(scheduleEntries) {
        val latest = scheduleEntries.maxOfOrNull { entry ->
            if (entry.endTime.minute > 0) entry.endTime.hour + 1 else entry.endTime.hour
        } ?: DEFAULT_GRID_END_HOUR
        maxOf(DEFAULT_GRID_END_HOUR, latest).coerceAtMost(24)
    }

    val entriesByDay = remember(scheduleEntries) { scheduleEntries.groupBy { it.dayOfWeek } }
    val countsByDay = remember(scheduleEntries) { scheduleEntries.groupBy { it.dayOfWeek }.mapValues { it.value.size } }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
        }
    }

    Column(modifier = modifier) {
        // Day header row — scrolls horizontally in lockstep with the grid body below.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(TIME_AXIS_WIDTH))
            Row(modifier = Modifier.horizontalScroll(hScroll)) {
                DayOfWeek.entries.forEach { day ->
                    DayHeaderCell(
                        day = day,
                        isToday = day == today,
                        count = countsByDay[day] ?: 0,
                        modifier = Modifier.width(DAY_COLUMN_WIDTH)
                    )
                }
            }
        }
        Divider()

        Row(modifier = Modifier.weight(1f)) {
            // Time axis — scrolls vertically in lockstep with the grid body.
            Column(
                modifier = Modifier
                    .width(TIME_AXIS_WIDTH)
                    .verticalScroll(vScroll)
                    .padding(top = GRID_TOP_PADDING)
            ) {
                // Each label's Box starts exactly at its hour boundary (y=0 for gridStartHour),
                // matching the background cells below and the lecture blocks' own offsets —
                // they all share the same origin so a tap and a block line up with the label.
                for (hour in gridStartHour until gridEndHour) {
                    Box(modifier = Modifier.height(HOUR_HEIGHT)) {
                        Text(
                            text = LocalTime.of(hour, 0).format(timeLabelFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                // Centre the label on its hour line, like a real calendar.
                                .offset(y = (-7).dp)
                                .padding(end = 6.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .padding(top = GRID_TOP_PADDING)
            ) {
                // Background grid: one column per day, split into tappable half-hour cells.
                Row {
                    DayOfWeek.entries.forEach { day ->
                        DayColumnCells(
                            day = day,
                            gridStartHour = gridStartHour,
                            gridEndHour = gridEndHour,
                            isToday = day == today,
                            onCellTap = onCellTap
                        )
                    }
                }

                // Lecture blocks, layered on top so they intercept taps meant for editing.
                DayOfWeek.entries.forEachIndexed { dayIndex, day ->
                    val dayEntries = entriesByDay[day].orEmpty()
                    layoutDayEntries(dayEntries).forEach { (entry, lane) ->
                        LectureBlock(
                            entry = entry,
                            subject = allSubjects[entry.subjectId],
                            allSubjects = allSubjects,
                            dayIndex = dayIndex,
                            lane = lane.index,
                            totalLanes = lane.total,
                            gridStartHour = gridStartHour,
                            onClick = { onBlockTap(entry) }
                        )
                    }
                }

                // "Now" indicator across today's column.
                if (now.hour in gridStartHour until gridEndHour) {
                    val dayIndex = DayOfWeek.entries.indexOf(today)
                    val minutesFromStart = (now.hour - gridStartHour) * 60 + now.minute
                    val yOffset = HOUR_HEIGHT * (minutesFromStart / 60f)
                    Box(
                        modifier = Modifier
                            .offset(x = DAY_COLUMN_WIDTH * dayIndex, y = yOffset)
                            .width(DAY_COLUMN_WIDTH)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeaderCell(
    day: DayOfWeek,
    isToday: Boolean,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (count > 0) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayColumnCells(
    day: DayOfWeek,
    gridStartHour: Int,
    gridEndHour: Int,
    isToday: Boolean,
    onCellTap: (DayOfWeek, LocalTime) -> Unit
) {
    val slotMinutes = remember(gridStartHour, gridEndHour) {
        val start = gridStartHour * 60
        val end = gridEndHour * 60
        generateSequence(start) { it + 30 }.takeWhile { it < end }.toList()
    }
    val dayDividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(DAY_COLUMN_WIDTH)
            .background(
                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else Color.Transparent
            )
            // Vertical separator on each day's right edge so days read as distinct columns.
            .drawBehind {
                drawLine(
                    color = dayDividerColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        slotMinutes.forEach { minuteOfDay ->
            val isHourMark = minuteOfDay % 60 == 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HALF_HOUR_HEIGHT)
                    .clickable {
                        onCellTap(day, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
                    }
            ) {
                // Box defaults to top-start content alignment, so this 1dp divider sits
                // right on the hour boundary rather than centered in the half-hour cell.
                // Hour lines are solid; half-hour lines are fainter so time reads at a glance.
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isHourMark) 0.9f else 0.35f)
                )
            }
        }
    }
}

private data class LaneInfo(val index: Int, val total: Int)

/**
 * Assigns each of a day's entries to a horizontal "lane" so entries that overlap in time
 * render side by side instead of on top of each other, similar to Google Calendar. Uses a
 * simple greedy interval-scheduling pass rather than true per-cluster lane packing, which
 * is more than enough for the handful of overlaps a class timetable ever has.
 */
private fun layoutDayEntries(entries: List<ScheduleEntry>): List<Pair<ScheduleEntry, LaneInfo>> {
    if (entries.isEmpty()) return emptyList()
    val sorted = entries.sortedBy { it.startTime }
    val laneEndTimes = mutableListOf<LocalTime>()
    val laneOf = mutableMapOf<Long, Int>()
    for (entry in sorted) {
        var placedLane = -1
        for (i in laneEndTimes.indices) {
            if (!entry.startTime.isBefore(laneEndTimes[i])) {
                placedLane = i
                break
            }
        }
        if (placedLane == -1) {
            laneEndTimes.add(entry.endTime)
            placedLane = laneEndTimes.size - 1
        } else {
            laneEndTimes[placedLane] = entry.endTime
        }
        laneOf[entry.id] = placedLane
    }
    val totalLanes = laneEndTimes.size.coerceAtLeast(1)
    return sorted.map { it to LaneInfo(laneOf[it.id] ?: 0, totalLanes) }
}

@Composable
private fun LectureBlock(
    entry: ScheduleEntry,
    subject: Subject?,
    allSubjects: Map<Long, Subject>,
    dayIndex: Int,
    lane: Int,
    totalLanes: Int,
    gridStartHour: Int,
    onClick: () -> Unit
) {
    val startMinutes = entry.startTime.hour * 60 + entry.startTime.minute
    val endMinutes = (entry.endTime.hour * 60 + entry.endTime.minute).coerceAtLeast(startMinutes + 15)
    val gridStartMinutes = gridStartHour * 60

    val topOffset = HOUR_HEIGHT * ((startMinutes - gridStartMinutes) / 60f)
    val blockHeight = (HOUR_HEIGHT * ((endMinutes - startMinutes) / 60f)).coerceAtLeast(28.dp)
    val laneWidth: Dp = DAY_COLUMN_WIDTH / totalLanes
    val xOffset = DAY_COLUMN_WIDTH * dayIndex + laneWidth * lane
    val color = subjectAvatarColor(entry.subjectId)

    Box(
        modifier = Modifier
            .offset(x = xOffset + 2.dp, y = topOffset + 1.dp)
            .width((laneWidth - 4.dp).coerceAtLeast(24.dp))
            .height((blockHeight - 2.dp).coerceAtLeast(26.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp)
    ) {
        Column {
            Text(
                text = subject?.getDisplayName(allSubjects) ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.startTime.format(timeLabelFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -----------------------------------------------------------------------
// Add / edit sheet
// -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LectureEditorSheet(
    state: LectureEditorState,
    subjects: List<Subject>,
    allSubjects: Map<Long, Subject>,
    onDismiss: () -> Unit,
    onSave: (subjectId: Long, dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var subjectId by remember { mutableStateOf(state.subjectId) }
    var dayOfWeek by remember { mutableStateOf(state.dayOfWeek) }
    var startTime by remember { mutableStateOf(state.startTime) }
    var endTime by remember { mutableStateOf(state.endTime) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isEditing = state.existingEntry != null
    val isTimeValid = endTime.isAfter(startTime)
    val selectedSubject = allSubjects[subjectId]

    // Plays the sheet's collapse animation before actually tearing it down, instead of
    // yanking it out of composition the instant Save/Delete is tapped.
    val dismissAfterHide: () -> Unit = {
        coroutineScope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (isEditing) "Edit Lecture" else "Add Lecture",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            ExposedDropdownMenuBox(
                expanded = subjectMenuExpanded,
                onExpandedChange = { subjectMenuExpanded = it }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedSubject?.getDisplayName(allSubjects) ?: "Select a subject",
                    onValueChange = {},
                    label = { Text("Subject") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = subjectMenuExpanded,
                    onDismissRequest = { subjectMenuExpanded = false }
                ) {
                    subjects.forEach { subject ->
                        DropdownMenuItem(
                            text = { Text(subject.getDisplayName(allSubjects)) },
                            onClick = {
                                subjectId = subject.id
                                subjectMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Day",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = dayOfWeek == day,
                        onClick = { dayOfWeek = day },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Time  (tap a box to open the clock)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeField(
                    label = "Starts",
                    time = startTime,
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "Ends",
                    time = endTime,
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toInt()
            Text(
                text = if (isTimeValid) "Duration: ${formatDuration(durationMinutes)} — or pick a quick length:"
                else "Quick length:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(30, 45, 60, 90, 120, 180).forEach { minutes ->
                    FilterChip(
                        selected = isTimeValid && durationMinutes == minutes,
                        onClick = { endTime = endAfter(startTime, minutes) },
                        label = { Text(formatDuration(minutes)) }
                    )
                }
            }
            if (!isTimeValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "End time must be after start time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            onDelete()
                            dismissAfterHide()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        onSave(subjectId, dayOfWeek, startTime, endTime)
                        dismissAfterHide()
                    },
                    enabled = isTimeValid && selectedSubject != null
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (showStartPicker) {
        LectureTimePickerDialog(
            title = "Select start time",
            initial = startTime,
            onConfirm = {
                // Keep the lecture's length when moving its start, as calendar apps do.
                val length = if (isTimeValid) java.time.Duration.between(startTime, endTime).toMinutes().toInt() else 60
                startTime = it
                endTime = endAfter(it, length)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        LectureTimePickerDialog(
            title = "Select end time",
            initial = endTime,
            onConfirm = {
                endTime = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LectureTimePickerDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
