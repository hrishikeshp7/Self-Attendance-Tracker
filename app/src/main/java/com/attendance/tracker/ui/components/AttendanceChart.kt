package com.attendance.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.attendance.tracker.ui.theme.AbsentRed
import com.attendance.tracker.ui.theme.PresentGreen
import com.attendance.tracker.ui.theme.NoClassGray

/**
 * A circular progress indicator that displays attendance percentage
 * visually like a pie chart / donut chart
 */
@Composable
fun AttendancePieChart(
    percentage: Float,
    requiredPercentage: Int,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 8.dp,
    showPercentageText: Boolean = true
) {
    val isAboveRequired = percentage >= requiredPercentage
    val progressColor = if (isAboveRequired) PresentGreen else AbsentRed
    val backgroundColor = NoClassGray.copy(alpha = 0.3f)
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = strokeWidth.toPx()
            val arcSize = Size(
                width = this.size.width - strokeWidthPx,
                height = this.size.height - strokeWidthPx
            )
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            
            // Background circle
            drawArc(
                color = backgroundColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            
            // Progress arc
            val sweepAngle = (percentage / 100f) * 360f
            drawArc(
                color = progressColor,
                startAngle = -90f, // Start from top
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            
            // Required percentage indicator line
            val requiredAngle = ((requiredPercentage / 100f) * 360f) - 90f
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val radius = (this.size.width - strokeWidthPx) / 2
            val indicatorLength = strokeWidthPx * 1.5f
            
            val startRadius = radius - indicatorLength / 2
            val endRadius = radius + indicatorLength / 2
            
            val radians = requiredAngle * kotlin.math.PI / 180.0
            val startX = centerX + (startRadius * kotlin.math.cos(radians)).toFloat()
            val startY = centerY + (startRadius * kotlin.math.sin(radians)).toFloat()
            val endX = centerX + (endRadius * kotlin.math.cos(radians)).toFloat()
            val endY = centerY + (endRadius * kotlin.math.sin(radians)).toFloat()
            
            drawLine(
                color = Color.White,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        if (showPercentageText) {
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = progressColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A more detailed attendance visualization with present/absent breakdown
 */
@Composable
fun AttendanceDonutChart(
    presentCount: Int,
    absentCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    strokeWidth: Dp = 12.dp
) {
    val presentPercentage = if (totalCount > 0) (presentCount.toFloat() / totalCount) * 100 else 0f
    val absentPercentage = if (totalCount > 0) (absentCount.toFloat() / totalCount) * 100 else 0f
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = strokeWidth.toPx()
            val arcSize = Size(
                width = this.size.width - strokeWidthPx,
                height = this.size.height - strokeWidthPx
            )
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            
            if (totalCount == 0) {
                // Empty state - gray circle
                drawArc(
                    color = NoClassGray.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            } else {
                // Present arc (green)
                val presentSweep = (presentPercentage / 100f) * 360f
                drawArc(
                    color = PresentGreen,
                    startAngle = -90f,
                    sweepAngle = presentSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                )
                
                // Absent arc (red)
                val absentSweep = (absentPercentage / 100f) * 360f
                drawArc(
                    color = AbsentRed,
                    startAngle = -90f + presentSweep,
                    sweepAngle = absentSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${presentPercentage.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (presentPercentage >= 75) PresentGreen else AbsentRed
            )
        }
    }
}
