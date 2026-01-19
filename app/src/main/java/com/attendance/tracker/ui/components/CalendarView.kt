package com.attendance.tracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarView(
    selectedMonth: YearMonth,
    selectedDate: LocalDate,
    attendanceRecords: List<AttendanceRecord>,
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
    
    // Reset base and pager when month changes externally (e.g., arrow buttons)
    LaunchedEffect(selectedMonth) {
        if (selectedMonth != baseMonth) {
            baseMonth = selectedMonth
            if (pagerState.currentPage != CALENDAR_INITIAL_PAGE) {
                pagerState.animateScrollToPage(CALENDAR_INITIAL_PAGE)
            }
        }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Month Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChanged(selectedMonth.minusMonths(1)) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
            }
            Text(
                text = "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedMonth.year}",
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = { onMonthChanged(selectedMonth.plusMonths(1)) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next Month")
            }
        }

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
                onDateSelected = onDateSelected
            )
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    attendanceRecords: List<AttendanceRecord>,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Day of Week Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val daysOfWeek = listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            )
            daysOfWeek.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 6.dp),
            contentPadding = PaddingValues(2.dp),
            userScrollEnabled = false
        ) {
            items(calendarDays) { date ->
                CalendarDay(
                    date = date,
                    isSelected = date == selectedDate,
                    isToday = date == LocalDate.now(),
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
    attendanceRecords: List<AttendanceRecord>,
    onClick: () -> Unit
) {
    if (date == null) {
        Box(modifier = Modifier.size(40.dp))
        return
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Determine attendance statuses for the day (can have multiple)
    val hasPresent = attendanceRecords.any { it.status == AttendanceStatus.PRESENT }
    val hasAbsent = attendanceRecords.any { it.status == AttendanceStatus.ABSENT }
    val hasNoClass = attendanceRecords.any { it.status == AttendanceStatus.NO_CLASS }

    Column(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
        
        // Show attendance indicator dots
        if (hasPresent || hasAbsent || hasNoClass) {
            Spacer(modifier = Modifier.height(1.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hasPresent) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(PresentGreen)
                    )
                    if (hasAbsent || hasNoClass) Spacer(modifier = Modifier.width(1.dp))
                }
                if (hasAbsent) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(AbsentRed)
                    )
                    if (hasNoClass) Spacer(modifier = Modifier.width(1.dp))
                }
                if (hasNoClass) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(NoClassGray)
                    )
                }
            }
        }
    }
}
