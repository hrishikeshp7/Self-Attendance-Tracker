package com.attendance.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.NoClassGray
import com.attendance.tracker.ui.theme.PresentGreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val DAY_CELL_SIZE = 44.dp
private val DAY_CELL_SPACING = 6.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarView(
    selectedMonth: YearMonth,
    selectedDate: LocalDate,
    attendanceRecords: List<AttendanceRecord>,
    rangeStart: LocalDate? = null,
    rangeEnd: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    // Constants for pager configuration
    val CALENDAR_INITIAL_PAGE = 10000
    val CALENDAR_MAX_PAGES = 20000

    // Track base month for offset calculations - updates when month changes externally
    var baseMonth by rememberSaveable { mutableStateOf(selectedMonth) }
    var lastPagerPage by rememberSaveable { mutableStateOf(CALENDAR_INITIAL_PAGE) }

    // Initialize pager state centered at a large value to allow bidirectional swiping
    val pagerState = rememberPagerState(
        initialPage = CALENDAR_INITIAL_PAGE,
        pageCount = { CALENDAR_MAX_PAGES } // Large number to simulate infinite scrolling
    )

    // Track month changes from swipe gestures
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPagerPage) {
            lastPagerPage = pagerState.currentPage
            val offset = pagerState.currentPage - CALENDAR_INITIAL_PAGE
            if (offset != 0) {
                val newMonth = baseMonth.plusMonths(offset.toLong())
                if (newMonth != selectedMonth) {
                    onMonthChanged(newMonth)
                }
            }
        }
    }

    // Reset base and pager when month changes externally (e.g., arrow buttons, Today button)
    LaunchedEffect(selectedMonth) {
        if (selectedMonth != baseMonth) {
            baseMonth = selectedMonth
            if (pagerState.currentPage != CALENDAR_INITIAL_PAGE) {
                pagerState.animateScrollToPage(CALENDAR_INITIAL_PAGE)
            }
        }
    }

    val isCurrentMonth = selectedMonth == YearMonth.now()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            // Month Navigation Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = { onMonthChanged(selectedMonth.minusMonths(1)) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedMonth,
                        transitionSpec = {
                            // Slide by the full height (not half) and fade the outgoing/incoming
                            // labels so they never sit fully opaque on top of each other mid-transition
                            // — a half-height slide with no fade left both month names visibly
                            // overlapping for the whole animation.
                            if (targetState > initialState) {
                                (slideInVertically { h -> h } + fadeIn()) togetherWith
                                    (slideOutVertically { h -> -h } + fadeOut())
                            } else {
                                (slideInVertically { h -> -h } + fadeIn()) togetherWith
                                    (slideOutVertically { h -> h } + fadeOut())
                            }
                        },
                        label = "month-label"
                    ) { month ->
                        Text(
                            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!isCurrentMonth) {
                        FilledTonalIconButton(
                            onClick = { onMonthChanged(YearMonth.now()) },
                            modifier = Modifier.size(28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Filled.Today,
                                contentDescription = "Jump to current month",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                FilledTonalIconButton(onClick = { onMonthChanged(selectedMonth.plusMonths(1)) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal Pager for swipeable months
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val offset = page - CALENDAR_INITIAL_PAGE
                val monthToDisplay = baseMonth.plusMonths(offset.toLong())

                MonthCalendarGrid(
                    month = monthToDisplay,
                    selectedDate = selectedDate,
                    attendanceRecords = attendanceRecords,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    onDateSelected = onDateSelected
                )
            }

            CalendarLegend()
        }
    }
}

@Composable
private fun CalendarLegend() {
    Divider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(color = PresentGreen, label = "Present")
        LegendItem(color = AbsentRed, label = "Absent")
        LegendItem(color = NoClassGray, label = "No Class")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthCalendarGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    attendanceRecords: List<AttendanceRecord>,
    rangeStart: LocalDate? = null,
    rangeEnd: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Day of Week Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val daysOfWeek = listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            )
            daysOfWeek.forEach { day ->
                val isWeekend = day == DayOfWeek.SUNDAY || day == DayOfWeek.SATURDAY
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (isWeekend) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Calendar Grid
        val firstDayOfMonth = month.atDay(1)
        val lastDayOfMonth = month.atEndOfMonth()
        // DayOfWeek.value: Monday=1, Tuesday=2, ..., Sunday=7
        // For Sunday-first calendar: Sunday=0, Monday=1, ..., Saturday=6
        val startOffset = if (firstDayOfMonth.dayOfWeek == DayOfWeek.SUNDAY) 0
                          else firstDayOfMonth.dayOfWeek.value
        val daysInMonth = lastDayOfMonth.dayOfMonth

        val calendarDays = buildList {
            // Add empty cells for days before the first day of the month
            repeat(startOffset) { add(null) }
            // Add days of the month
            for (day in 1..daysInMonth) {
                add(month.atDay(day))
            }
        }
        val rowCount = (calendarDays.size + 6) / 7
        val gridHeight = (DAY_CELL_SIZE + DAY_CELL_SPACING) * rowCount

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .padding(horizontal = 6.dp),
            contentPadding = PaddingValues(2.dp),
            userScrollEnabled = false
        ) {
            // Normalize so start ≤ end regardless of tap order
            val normalizedStart = if (rangeStart != null && rangeEnd != null) minOf(rangeStart, rangeEnd) else rangeStart
            val normalizedEnd   = if (rangeStart != null && rangeEnd != null) maxOf(rangeStart, rangeEnd) else rangeEnd
            items(calendarDays) { date ->
                val isInRange = date != null && normalizedStart != null && normalizedEnd != null &&
                        !date.isBefore(normalizedStart) && !date.isAfter(normalizedEnd)
                // Both guards include normalizedStart != normalizedEnd so a degenerate
                // single-day range falls back to the regular isSelected highlight instead.
                val isRangeStart = date != null && date == normalizedStart &&
                        normalizedEnd != null && normalizedStart != normalizedEnd
                val isRangeEnd   = date != null && date == normalizedEnd   &&
                        normalizedStart != normalizedEnd
                CalendarDay(
                    date = date,
                    isSelected = date == selectedDate,
                    isToday = date == LocalDate.now(),
                    isRangeStart = isRangeStart,
                    isRangeEnd = isRangeEnd,
                    isInRange = isInRange,
                    attendanceRecords = date?.let { d ->
                        attendanceRecords.filter { it.date == d }
                    } ?: emptyList(),
                    onClick = { date?.let { onDateSelected(it) } }
                )
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate?,
    isSelected: Boolean,
    isToday: Boolean,
    isRangeStart: Boolean = false,
    isRangeEnd: Boolean = false,
    isInRange: Boolean = false,
    attendanceRecords: List<AttendanceRecord>,
    onClick: () -> Unit
) {
    if (date == null) {
        Box(modifier = Modifier.size(DAY_CELL_SIZE + DAY_CELL_SPACING))
        return
    }

    val isRangeEndpoint = isRangeStart || isRangeEnd
    val isDark = isSystemInDarkTheme()

    // Determine attendance statuses for the day (can have multiple)
    val hasPresent = attendanceRecords.any { it.presentCount > 0 }
    val hasAbsent = attendanceRecords.any { it.absentCount > 0 }
    val hasNoClass = attendanceRecords.any { it.status == AttendanceStatus.NO_CLASS }
    val statusCount = listOf(hasPresent, hasAbsent, hasNoClass).count { it }
    val isMixedStatus = statusCount > 1
    val singleStatusColor = when {
        statusCount == 1 && hasPresent -> PresentGreen
        statusCount == 1 && hasAbsent -> AbsentRed
        statusCount == 1 && hasNoClass -> NoClassGray
        else -> null
    }

    val indicatorShape: Shape = if (isRangeEndpoint || isSelected) CircleShape else RoundedCornerShape(14.dp)
    val indicatorFill: Color? = when {
        isRangeEndpoint || isSelected -> MaterialTheme.colorScheme.primary
        singleStatusColor != null -> singleStatusColor.copy(alpha = if (isDark) 0.30f else 0.16f)
        else -> null
    }
    val indicatorBorder: Color? = if (isToday && !isRangeEndpoint && !isSelected) {
        MaterialTheme.colorScheme.primary
    } else null
    val textColor = when {
        isRangeEndpoint || isSelected -> MaterialTheme.colorScheme.onPrimary
        isInRange -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val fontWeight = if (isToday || isSelected || isRangeEndpoint) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .size(DAY_CELL_SIZE + DAY_CELL_SPACING)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Range connector band, drawn beneath the day indicator so consecutive
        // selected days read as one continuous highlighted strip.
        if (isInRange && !isRangeEndpoint) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else if (isRangeStart) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((DAY_CELL_SIZE + DAY_CELL_SPACING) / 2)
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else if (isRangeEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((DAY_CELL_SIZE + DAY_CELL_SPACING) / 2)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        Box(
            modifier = Modifier
                .size(DAY_CELL_SIZE)
                .clip(indicatorShape)
                .then(if (indicatorFill != null) Modifier.background(indicatorFill) else Modifier)
                .then(
                    if (indicatorBorder != null) Modifier.border(1.5.dp, indicatorBorder, indicatorShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = fontWeight,
                    color = textColor
                )

                // Mixed-status days (e.g. present for one lecture, absent for another
                // on the same date) get a small dot row since a single tint can't
                // represent more than one status.
                if (isMixedStatus) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasPresent) StatusDot(PresentGreen)
                        if (hasAbsent) StatusDot(AbsentRed)
                        if (hasNoClass) StatusDot(NoClassGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(color)
    )
}
