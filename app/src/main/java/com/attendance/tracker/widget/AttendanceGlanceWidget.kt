package com.attendance.tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Glance-based home screen widget for attendance tracking
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
                    isAmoled = themePreference.themeMode == com.attendance.tracker.data.model.ThemeMode.AMOLED
                )
            }
        }
    }

    @Composable
    private fun AttendanceWidgetContent(subjects: List<Subject>, isAmoled: Boolean) {
        val backgroundColor = if (isAmoled) {
            ColorProvider(Color(0xFF000000)) // Pure black for AMOLED
        } else {
            GlanceTheme.colors.background
        }
        
        val textColor = if (isAmoled) {
            ColorProvider(Color(0xFFE0E0E0)) // Dimmed white for AMOLED
        } else {
            GlanceTheme.colors.onBackground
        }
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            // Title
            Text(
                text = "Attendance",
                style = TextStyle(
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )

            if (subjects.isEmpty()) {
                // Empty state
                Text(
                    text = "No subjects added yet",
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp
                    )
                )
            } else {
                // Show up to 3 subjects in the widget
                subjects.take(3).forEach { subject ->
                    SubjectWidgetRow(subject = subject, textColor = textColor, isAmoled = isAmoled)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }

                if (subjects.size > 3) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "+${subjects.size - 3} more subjects",
                        style = TextStyle(
                            color = if (isAmoled) ColorProvider(Color(0xFF64B5F6)) else GlanceTheme.colors.secondary,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun SubjectWidgetRow(subject: Subject, textColor: ColorProvider, isAmoled: Boolean) {
    @Composable
    private fun SubjectWidgetRow(subject: Subject, textColor: ColorProvider, isAmoled: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject name
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = subject.name,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                // Stats
                Text(
                    text = "${subject.presentLectures}/${subject.totalLectures} attended",
                    style = TextStyle(
                        color = if (isAmoled) ColorProvider(Color(0xFFB0B0B0)) else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }

            // Percentage badge
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
