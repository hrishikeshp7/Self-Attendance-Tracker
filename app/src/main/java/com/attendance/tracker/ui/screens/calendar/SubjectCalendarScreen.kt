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
                selectedDate = selectedDate,
                attendanceRecords = subjectRecords,
                onDateSelected = onDateSelected,
                onMonthChanged = onMonthChanged
            )

            // Attendance Statistics Bar for selected date
            val selectedDateRecord = subjectRecords.find { it.date == selectedDate }
            // Calculate statistics in a single pass
            val (presentCount, absentCount, totalCount) = subjectRecords.fold(Triple(0, 0, 0)) { acc, record ->
                when (record.status) {
                    AttendanceStatus.PRESENT -> Triple(acc.first + 1, acc.second, acc.third + 1)
                    AttendanceStatus.ABSENT -> Triple(acc.first, acc.second + 1, acc.third + 1)
                    else -> acc
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        value = totalCount.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Selected Date Attendance Details
            Text(
                text = selectedDate.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
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
                    
                    // Attendance Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SubjectCalendarAttendanceButton(
                            text = "Present",
                            isSelected = selectedDateRecord?.status == AttendanceStatus.PRESENT,
                            color = PresentGreen,
                            onClick = { 
                                onMarkAttendance(AttendanceStatus.PRESENT, selectedDate)
                                showAttendanceSnackbar(AttendanceStatus.PRESENT)
                            }
                        )
                        SubjectCalendarAttendanceButton(
                            text = "Absent",
                            isSelected = selectedDateRecord?.status == AttendanceStatus.ABSENT,
                            color = AbsentRed,
                            onClick = { 
                                onMarkAttendance(AttendanceStatus.ABSENT, selectedDate)
                                showAttendanceSnackbar(AttendanceStatus.ABSENT)
                            }
                        )
                        SubjectCalendarAttendanceButton(
                            text = "No Class",
                            isSelected = selectedDateRecord?.status == AttendanceStatus.NO_CLASS,
                            color = NoClassGray,
                            onClick = { 
                                onMarkAttendance(AttendanceStatus.NO_CLASS, selectedDate)
                                showAttendanceSnackbar(AttendanceStatus.NO_CLASS)
                            }
                        )
                    }
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
