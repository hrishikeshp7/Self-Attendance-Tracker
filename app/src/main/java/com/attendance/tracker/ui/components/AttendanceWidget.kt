package com.attendance.tracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.NoClassGray
import com.attendance.tracker.ui.theme.PresentGreen

/**
 * A horizontal scrollable widget displaying attendance for all subjects
 * Each card shows a pie chart with percentage and action buttons
 */
@Composable
fun AttendanceWidget(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    todayAttendance: Map<Long, AttendanceStatus>,
    onMarkAttendance: (Long, AttendanceStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    if (subjects.isEmpty()) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Title
            Text(
                text = "Quick Attendance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Horizontal scrollable row of subject widgets
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                subjects.forEach { subject ->
                    SubjectWidgetItem(
                        subject = subject,
                        allSubjects = allSubjects,
                        currentStatus = todayAttendance[subject.id],
                        onMarkPresent = { onMarkAttendance(subject.id, AttendanceStatus.PRESENT) },
                        onMarkAbsent = { onMarkAttendance(subject.id, AttendanceStatus.ABSENT) },
                        onMarkNoClass = { onMarkAttendance(subject.id, AttendanceStatus.NO_CLASS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectWidgetItem(
    subject: Subject,
    allSubjects: List<Subject>,
    currentStatus: AttendanceStatus?,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onMarkNoClass: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(180.dp)
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subject name
            Text(
                text = subject.getDisplayName(allSubjects),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pie chart
            AttendancePieChart(
                percentage = subject.currentAttendancePercentage,
                requiredPercentage = subject.requiredAttendance,
                size = 70.dp,
                strokeWidth = 7.dp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Attendance action buttons (compact)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompactAttendanceButton(
                    text = "P",
                    isSelected = currentStatus == AttendanceStatus.PRESENT,
                    color = PresentGreen,
                    onClick = onMarkPresent
                )
                CompactAttendanceButton(
                    text = "A",
                    isSelected = currentStatus == AttendanceStatus.ABSENT,
                    color = AbsentRed,
                    onClick = onMarkAbsent
                )
                CompactAttendanceButton(
                    text = "NC",
                    isSelected = currentStatus == AttendanceStatus.NO_CLASS,
                    color = NoClassGray,
                    onClick = onMarkNoClass
                )
            }
        }
    }
}

@Composable
private fun CompactAttendanceButton(
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
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
