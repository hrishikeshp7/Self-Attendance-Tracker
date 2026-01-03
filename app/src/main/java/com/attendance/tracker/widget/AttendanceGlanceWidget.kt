package com.attendance.tracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.attendance.tracker.MainActivity
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Unified read-only Glance widget for attendance overview
 * Shows overall attendance stats and subjects with low attendance
 */
class AttendanceGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load data from database
        val database = AttendanceDatabase.getDatabase(context)
        val repository = AttendanceRepository(database.subjectDao(), database.attendanceDao(), database.scheduleDao())
        val themeRepository = com.attendance.tracker.data.repository.ThemePreferenceRepository(database.themePreferenceDao())
        
        val subjects = withContext(Dispatchers.IO) {
            repository.actualSubjects.first()
        }
        
        val themePreference = withContext(Dispatchers.IO) {
            themeRepository.getThemePreferenceOnce()
        }

        provideContent {
            GlanceTheme {
                AttendanceWidgetContent(
                    subjects = subjects,
                    isAmoled = themePreference.themeMode == com.attendance.tracker.data.model.ThemeMode.AMOLED,
                    context = context
                )
            }
        }
    }

    @Composable
    private fun AttendanceWidgetContent(
        subjects: List<Subject>,
        isAmoled: Boolean,
        context: Context
    ) {
        val backgroundColor = if (isAmoled) {
            ColorProvider(Color(0xFF000000))
        } else {
            GlanceTheme.colors.background
        }
        
        val textColor = if (isAmoled) {
            ColorProvider(Color(0xFFE0E0E0))
        } else {
            GlanceTheme.colors.onBackground
        }
        
        // Calculate overall statistics
        val totalPresent = subjects.sumOf { it.presentLectures }
        val totalLectures = subjects.sumOf { it.totalLectures }
        val overallPercentage = if (totalLectures > 0) {
            (totalPresent.toFloat() / totalLectures.toFloat() * 100)
        } else {
            0f
        }
        
        // Find subjects with low attendance (below their required threshold)
        val lowAttendanceSubjects = subjects.filter { 
            it.totalLectures > 0 && it.currentAttendancePercentage < it.requiredAttendance 
        }.sortedBy { it.currentAttendancePercentage }
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            // Title and Overall Stats
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Attendance Overview",
                        style = TextStyle(
                            color = textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (subjects.isNotEmpty()) {
                        Text(
                            text = "${subjects.size} subjects",
                            style = TextStyle(
                                color = if (isAmoled) ColorProvider(Color(0xFFB0B0B0)) else GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                
                if (totalLectures > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format("%.1f%%", overallPercentage),
                            style = TextStyle(
                                color = if (overallPercentage >= 75) {
                                    ColorProvider(Color(0xFF4CAF50))
                                } else {
                                    ColorProvider(Color(0xFFF44336))
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Overall",
                            style = TextStyle(
                                color = if (isAmoled) ColorProvider(Color(0xFFB0B0B0)) else GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            if (subjects.isEmpty()) {
                // Empty state
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "No subjects added yet.\nTap to open app.",
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp
                    )
                )
            } else {
                // Show subjects section
                if (lowAttendanceSubjects.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "⚠ Low Attendance",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF44336)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    
                    // Show up to 3 subjects with low attendance
                    lowAttendanceSubjects.take(3).forEach { subject ->
                        SubjectWidgetRow(subject = subject, textColor = textColor, isAmoled = isAmoled, isLowAttendance = true)
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                    
                    if (lowAttendanceSubjects.size > 3) {
                        Text(
                            text = "+${lowAttendanceSubjects.size - 3} more need attention",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFF44336)),
                                fontSize = 10.sp
                            )
                        )
                    }
                } else {
                    // All subjects are above threshold - show top subjects
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "✓ All subjects on track",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF4CAF50)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    
                    // Show up to 3 subjects with best attendance
                    subjects.sortedByDescending { it.currentAttendancePercentage }.take(3).forEach { subject ->
                        SubjectWidgetRow(subject = subject, textColor = textColor, isAmoled = isAmoled, isLowAttendance = false)
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                }
                
                // Footer hint
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "Tap to open app",
                    style = TextStyle(
                        color = if (isAmoled) ColorProvider(Color(0xFF64B5F6)) else GlanceTheme.colors.secondary,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun SubjectWidgetRow(
        subject: Subject,
        textColor: ColorProvider,
        isAmoled: Boolean,
        isLowAttendance: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(
                    if (isAmoled) ColorProvider(Color(0xFF1A1A1A)) else GlanceTheme.colors.surfaceVariant
                )
                .padding(6.dp),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject info
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = subject.name,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${subject.presentLectures}/${subject.totalLectures} • Target: ${subject.requiredAttendance}%",
                    style = TextStyle(
                        color = if (isAmoled) ColorProvider(Color(0xFFB0B0B0)) else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                if (isLowAttendance && subject.classesToAttend > 0) {
                    Text(
                        text = "Attend next ${subject.classesToAttend} ${if (subject.classesToAttend == 1) "class" else "classes"}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF44336)),
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Percentage
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val percentage = subject.currentAttendancePercentage
                val isAboveRequired = percentage >= subject.requiredAttendance
                
                Text(
                    text = String.format("%.1f%%", percentage),
                    style = TextStyle(
                        color = if (isAboveRequired) {
                            ColorProvider(Color(0xFF4CAF50))
                        } else {
                            ColorProvider(Color(0xFFF44336))
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Widget receiver that handles widget updates
 */
class AttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AttendanceGlanceWidget()
}
