package com.attendance.tracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName

/**
 * A compact horizontal scrollable widget showing only pie charts with percentages
 * Takes up minimal screen space
 */
@Composable
fun CompactAttendanceWidget(
    subjects: List<Subject>,
    allSubjects: List<Subject>,
    modifier: Modifier = Modifier
) {
    if (subjects.isEmpty()) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Title
            Text(
                text = "Attendance Overview",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Horizontal scrollable row of compact widgets
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                subjects.forEach { subject ->
                    CompactSubjectItem(
                        subject = subject,
                        allSubjects = allSubjects
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSubjectItem(
    subject: Subject,
    allSubjects: List<Subject>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Pie chart (smaller)
        AttendancePieChart(
            percentage = subject.currentAttendancePercentage,
            requiredPercentage = subject.requiredAttendance,
            size = 60.dp,
            strokeWidth = 6.dp
        )
        
        // Subject name (compact)
        Text(
            text = subject.getDisplayName(allSubjects),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
