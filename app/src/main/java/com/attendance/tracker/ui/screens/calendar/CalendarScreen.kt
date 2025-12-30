package com.attendance.tracker.ui.screens.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
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
fun CalendarScreen(
    selectedMonth: YearMonth,
    selectedDate: LocalDate,
    attendanceRecords: List<AttendanceRecord>,
    subjects: List<Subject>,
    todayAttendance: Map<Long, AttendanceStatus>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    onMarkAttendance: (Long, AttendanceStatus, LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Helper function to show snackbar with attendance status
    val showAttendanceSnackbar: (String, AttendanceStatus) -> Unit = { subjectName, status ->
        scope.launch {
            val statusText = when (status) {
                AttendanceStatus.PRESENT -> "Marked Present"
                AttendanceStatus.ABSENT -> "Marked Absent"
                AttendanceStatus.NO_CLASS -> "Marked No Class"
            }
            snackbarHostState.showSnackbar(
                message = "$subjectName: $statusText",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
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
                attendanceRecords = attendanceRecords,
                onDateSelected = onDateSelected,
                onMonthChanged = onMonthChanged
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Selected Date Attendance Details
            Text(
                text = selectedDate.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val selectedDateRecords = attendanceRecords.filter { it.date == selectedDate }
            
            if (subjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No subjects added yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(subjects, key = { it.id }) { subject ->
                        val record = selectedDateRecords.find { it.subjectId == subject.id }
                        CalendarAttendanceItem(
                            subject = subject,
                            currentStatus = record?.status,
                            onMarkPresent = { 
                                onMarkAttendance(subject.id, AttendanceStatus.PRESENT, selectedDate)
                                showAttendanceSnackbar(subject.name, AttendanceStatus.PRESENT)
                            },
                            onMarkAbsent = { 
                                onMarkAttendance(subject.id, AttendanceStatus.ABSENT, selectedDate)
                                showAttendanceSnackbar(subject.name, AttendanceStatus.ABSENT)
                            },
                            onMarkNoClass = { 
                                onMarkAttendance(subject.id, AttendanceStatus.NO_CLASS, selectedDate)
                                showAttendanceSnackbar(subject.name, AttendanceStatus.NO_CLASS)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAttendanceItem(
    subject: Subject,
    currentStatus: AttendanceStatus?,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onMarkNoClass: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = subject.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Attendance Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CalendarAttendanceButton(
                    text = "Present",
                    isSelected = currentStatus == AttendanceStatus.PRESENT,
                    color = PresentGreen,
                    onClick = onMarkPresent
                )
                CalendarAttendanceButton(
                    text = "Absent",
                    isSelected = currentStatus == AttendanceStatus.ABSENT,
                    color = AbsentRed,
                    onClick = onMarkAbsent
                )
                CalendarAttendanceButton(
                    text = "No Class",
                    isSelected = currentStatus == AttendanceStatus.NO_CLASS,
                    color = NoClassGray,
                    onClick = onMarkNoClass
                )
            }
        }
    }
}

@Composable
private fun CalendarAttendanceButton(
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

@Composable
private fun AttendanceRecordItem(
    subjectName: String,
    status: AttendanceStatus
) {
    val statusColor = when (status) {
        AttendanceStatus.PRESENT -> PresentGreen
        AttendanceStatus.ABSENT -> AbsentRed
        AttendanceStatus.NO_CLASS -> NoClassGray
    }
    val statusText = when (status) {
        AttendanceStatus.PRESENT -> "Present"
        AttendanceStatus.ABSENT -> "Absent"
        AttendanceStatus.NO_CLASS -> "No Class"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subjectName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }
    }
}
