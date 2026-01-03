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
    analytics: com.attendance.tracker.utils.AttendanceAnalytics?,
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Analytics Card
            if (analytics != null) {
                item {
                    com.attendance.tracker.ui.components.AttendanceStatsCard(
                        analytics = analytics,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Calendar View
            item {
                CalendarView(
                    selectedMonth = selectedMonth,
                    selectedDate = selectedDate,
                    attendanceRecords = subjectRecords,
                    onDateSelected = onDateSelected,
                    onMonthChanged = onMonthChanged
                )
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Selected Date Attendance Details
            item {
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
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
            item {
                val selectedDateRecord = subjectRecords.find { it.date == selectedDate }
                
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
}

@Composable
private fun SubjectCalendarAttendanceButton(
    text: String,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
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
