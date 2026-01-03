package com.attendance.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.ui.theme.PresentGreen
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.utils.AttendanceAnalytics

/**
 * Card displaying predictive analytics for a subject
 */
@Composable
fun AttendanceStatsCard(
    analytics: AttendanceAnalytics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📊 Attendance Insights",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            // Current and Predicted Attendance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Current",
                    value = "%.1f%%".format(analytics.currentAttendancePercentage),
                    color = if (analytics.currentAttendancePercentage >= analytics.subject.requiredAttendance) 
                        PresentGreen else AbsentRed
                )
                
                if (analytics.predictedSemesterEndPercentage != null) {
                    StatItem(
                        label = "Predicted",
                        value = "%.1f%%".format(analytics.predictedSemesterEndPercentage),
                        color = if (analytics.predictedSemesterEndPercentage >= analytics.subject.requiredAttendance) 
                            PresentGreen else AbsentRed
                    )
                }
            }
            
            Divider()
            
            // Streaks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Current Streak",
                    value = "${analytics.currentStreak}",
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "Best Streak",
                    value = "${analytics.longestStreak}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Divider()
            
            // Remaining classes and recommendations
            if (analytics.remainingScheduledClasses > 0) {
                Text(
                    text = "📅 ${analytics.remainingScheduledClasses} classes remaining this semester",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (analytics.classesToAttendForTarget > 0) {
                    Text(
                        text = "🎯 Attend next ${analytics.classesToAttendForTarget} classes to reach ${analytics.subject.requiredAttendance}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = AbsentRed
                    )
                } else if (analytics.classesCanBunk > 0) {
                    Text(
                        text = "✨ You can skip ${analytics.classesCanBunk} classes and still maintain ${analytics.subject.requiredAttendance}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = PresentGreen
                    )
                }
            }
            
            // Weekly trend
            if (analytics.weeklyTrend.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Weekly Trend (Last 4 weeks)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TrendGraph(
                    data = analytics.weeklyTrend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
            }
        }
    }
}

/**
 * Simple stat item
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Simple trend graph
 */
@Composable
fun TrendGraph(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val maxValue = data.maxOrNull() ?: 100f
    val minValue = data.minOrNull() ?: 0f
    val range = maxValue - minValue
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val spacing = width / (data.size - 1).coerceAtLeast(1)
        
        // Draw lines connecting points
        for (i in 0 until data.size - 1) {
            val x1 = i * spacing
            val y1 = height - ((data[i] - minValue) / range.coerceAtLeast(1f)) * height
            val x2 = (i + 1) * spacing
            val y2 = height - ((data[i + 1] - minValue) / range.coerceAtLeast(1f)) * height
            
            drawLine(
                color = Color(0xFF6200EE),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        
        // Draw points
        data.forEachIndexed { index, value ->
            val x = index * spacing
            val y = height - ((value - minValue) / range.coerceAtLeast(1f)) * height
            
            drawCircle(
                color = Color(0xFF6200EE),
                radius = 6f,
                center = Offset(x, y)
            )
        }
    }
}
