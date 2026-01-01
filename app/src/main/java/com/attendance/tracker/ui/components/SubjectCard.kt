package com.attendance.tracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.NoClassGray
import com.attendance.tracker.ui.theme.PresentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCard(
    subject: Subject,
    allSubjects: List<Subject>,
    currentStatus: AttendanceStatus?,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onMarkNoClass: () -> Unit,
    onEditClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        onClick = onCardClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Subject Name, Edit Button, and Pie Chart Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.getDisplayName(allSubjects),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap to view calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onEditClick,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Edit Subject")
                    }
                }
                
                // Pie Chart for attendance visualization
                AttendancePieChart(
                    percentage = subject.currentAttendancePercentage,
                    requiredPercentage = subject.requiredAttendance,
                    size = 80.dp,
                    strokeWidth = 9.dp
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Attendance Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttendanceStatItem(
                    label = "Present",
                    value = subject.presentLectures.toString(),
                    color = PresentGreen
                )
                AttendanceStatItem(
                    label = "Absent",
                    value = subject.absentLectures.toString(),
                    color = AbsentRed
                )
                AttendanceStatItem(
                    label = "Total",
                    value = subject.totalLectures.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Attendance Percentage Text
            Text(
                text = "Target: ${subject.requiredAttendance}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Bunk Help Information
            if (subject.totalLectures > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                
                if (subject.isAboveRequired) {
                    val canBunk = subject.classesCanBunk
                    if (canBunk > 0) {
                        Text(
                            text = "✓ You can bunk $canBunk ${if (canBunk == 1) "class" else "classes"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PresentGreen,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "✓ At threshold - attend next class",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val needToAttend = subject.classesToAttend
                    if (needToAttend > 0) {
                        Text(
                            text = "⚠ Attend next $needToAttend ${if (needToAttend == 1) "class" else "classes"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AbsentRed,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attendance Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttendanceButton(
                    text = "Present",
                    isSelected = currentStatus == AttendanceStatus.PRESENT,
                    color = PresentGreen,
                    onClick = onMarkPresent
                )
                AttendanceButton(
                    text = "Absent",
                    isSelected = currentStatus == AttendanceStatus.ABSENT,
                    color = AbsentRed,
                    onClick = onMarkAbsent
                )
                AttendanceButton(
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
private fun AttendanceStatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AttendanceButton(
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
