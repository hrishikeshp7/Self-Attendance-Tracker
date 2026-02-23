package com.attendance.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.NoClassGray
import com.attendance.tracker.ui.theme.PresentGreen

private const val STATS_BACKGROUND_ALPHA = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCard(
    subject: Subject,
    allSubjects: List<Subject>,
    currentRecord: AttendanceRecord?,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onMarkNoClass: () -> Unit,
    onEditClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val attendanceColor = when {
        subject.totalLectures == 0 -> NoClassGray
        subject.isAboveRequired -> PresentGreen
        else -> AbsentRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onCardClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header: name + pie chart + edit icon ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.getDisplayName(allSubjects),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Compact attendance insight
                    if (subject.totalLectures > 0) {
                        val insightText = when {
                            subject.isAboveRequired && subject.classesCanBunk > 0 ->
                                "Can skip ${subject.classesCanBunk} more"
                            subject.isAboveRequired ->
                                "At minimum threshold"
                            else ->
                                "Need ${subject.classesToAttend} more classes"
                        }
                        Text(
                            text = insightText,
                            style = MaterialTheme.typography.labelSmall,
                            color = attendanceColor
                        )
                    } else {
                        Text(
                            text = "No classes recorded yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                AttendancePieChart(
                    percentage = subject.currentAttendancePercentage,
                    requiredPercentage = subject.requiredAttendance,
                    size = 64.dp,
                    strokeWidth = 7.dp
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Stats row ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = STATS_BACKGROUND_ALPHA))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttendanceStatItem(
                    label = "Present",
                    value = subject.presentLectures.toString(),
                    color = PresentGreen
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                AttendanceStatItem(
                    label = "Absent",
                    value = subject.absentLectures.toString(),
                    color = AbsentRed
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                AttendanceStatItem(
                    label = "Total",
                    value = subject.totalLectures.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Action buttons ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceButton(
                    text = "Present",
                    count = if (currentRecord?.status == AttendanceStatus.PRESENT) currentRecord.count else null,
                    isSelected = currentRecord?.status == AttendanceStatus.PRESENT,
                    color = PresentGreen,
                    onClick = onMarkPresent,
                    modifier = Modifier.weight(1f)
                )
                AttendanceButton(
                    text = "Absent",
                    count = if (currentRecord?.status == AttendanceStatus.ABSENT) currentRecord.count else null,
                    isSelected = currentRecord?.status == AttendanceStatus.ABSENT,
                    color = AbsentRed,
                    onClick = onMarkAbsent,
                    modifier = Modifier.weight(1f)
                )
                AttendanceButton(
                    text = "No Class",
                    count = if (currentRecord?.status == AttendanceStatus.NO_CLASS) currentRecord.count else null,
                    isSelected = currentRecord?.status == AttendanceStatus.NO_CLASS,
                    color = NoClassGray,
                    onClick = onMarkNoClass,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AttendanceStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AttendanceButton(
    text: String,
    count: Int?,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonText = if (count != null && count > 1) "$text ($count)" else text
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else color.copy(alpha = 0.12f),
            contentColor = if (isSelected) Color.White else color
        ),
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
