package com.attendance.tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Large scrollable widget (5x2) with attendance marking capability
 * Full horizontal space, 2 vertical tiles
 */
class LargeAttendanceWidget : GlanceAppWidget() {

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
        
        val todayAttendance = withContext(Dispatchers.IO) {
            repository.getAttendanceForDate(LocalDate.now()).first()
        }

        provideContent {
            GlanceTheme {
                LargeWidgetContent(
                    subjects = subjects,
                    todayAttendance = todayAttendance,
                    isAmoled = themePreference.themeMode == com.attendance.tracker.data.model.ThemeMode.AMOLED
                )
            }
        }
    }

    @Composable
    private fun LargeWidgetContent(
        subjects: List<Subject>,
        todayAttendance: List<AttendanceRecord>,
        isAmoled: Boolean
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
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            // Title
            Text(
                text = "Quick Attendance",
                style = TextStyle(
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )

            if (subjects.isEmpty()) {
                Text(
                    text = "No subjects added yet",
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp
                    )
                )
            } else {
                // Scrollable row of subjects (up to 5 visible)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    subjects.take(5).forEach { subject ->
                        val currentStatus = todayAttendance.find { it.subjectId == subject.id }?.status
                        SubjectAttendanceItem(
                            subject = subject,
                            currentStatus = currentStatus,
                            textColor = textColor,
                            isAmoled = isAmoled
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun SubjectAttendanceItem(
        subject: Subject,
        currentStatus: AttendanceStatus?,
        textColor: ColorProvider,
        isAmoled: Boolean
    ) {
        Column(
            modifier = GlanceModifier
                .width(90.dp)
                .background(
                    if (isAmoled) ColorProvider(Color(0xFF1A1A1A)) else GlanceTheme.colors.surfaceVariant
                )
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject name
            Text(
                text = subject.name,
                style = TextStyle(
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
            
            Spacer(modifier = GlanceModifier.height(4.dp))
            
            // Percentage
            val percentage = subject.currentAttendancePercentage
            val isAboveRequired = percentage >= subject.requiredAttendance
            Text(
                text = String.format("%.0f%%", percentage),
                style = TextStyle(
                    color = if (isAboveRequired) {
                        ColorProvider(Color(0xFF4CAF50))
                    } else {
                        ColorProvider(Color(0xFFF44336))
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Spacer(modifier = GlanceModifier.height(6.dp))
            
            // Action buttons (compact)
            AttendanceButton(
                text = "P",
                isSelected = currentStatus == AttendanceStatus.PRESENT,
                color = Color(0xFF4CAF50),
                onClick = actionRunCallback<MarkAttendanceAction>(
                    actionParametersOf(
                        SubjectIdKey to subject.id,
                        StatusKey to AttendanceStatus.PRESENT.name
                    )
                )
            )
            
            Spacer(modifier = GlanceModifier.height(3.dp))
            
            AttendanceButton(
                text = "A",
                isSelected = currentStatus == AttendanceStatus.ABSENT,
                color = Color(0xFFF44336),
                onClick = actionRunCallback<MarkAttendanceAction>(
                    actionParametersOf(
                        SubjectIdKey to subject.id,
                        StatusKey to AttendanceStatus.ABSENT.name
                    )
                )
            )
            
            Spacer(modifier = GlanceModifier.height(3.dp))
            
            AttendanceButton(
                text = "NC",
                isSelected = currentStatus == AttendanceStatus.NO_CLASS,
                color = Color(0xFF757575),
                onClick = actionRunCallback<MarkAttendanceAction>(
                    actionParametersOf(
                        SubjectIdKey to subject.id,
                        StatusKey to AttendanceStatus.NO_CLASS.name
                    )
                )
            )
        }
    }

    @Composable
    private fun AttendanceButton(
        text: String,
        isSelected: Boolean,
        color: Color,
        onClick: androidx.glance.action.Action
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(22.dp)
                .background(
                    if (isSelected) ColorProvider(color) else ColorProvider(color.copy(alpha = 0.3f))
                )
                .clickable(onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = if (isSelected) ColorProvider(Color.White) else ColorProvider(color),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

/**
 * Action parameters
 */
val SubjectIdKey = ActionParameters.Key<Long>("subject_id")
val StatusKey = ActionParameters.Key<String>("status")

/**
 * Action callback for marking attendance
 */
class MarkAttendanceAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val subjectId = parameters[SubjectIdKey] ?: return
        val statusName = parameters[StatusKey] ?: return
        val status = AttendanceStatus.valueOf(statusName)
        
        // Update attendance in database
        val database = AttendanceDatabase.getDatabase(context)
        val repository = AttendanceRepository(
            database.subjectDao(),
            database.attendanceDao(),
            database.scheduleDao()
        )
        
        withContext(Dispatchers.IO) {
            when (status) {
                AttendanceStatus.PRESENT -> repository.markPresent(subjectId, LocalDate.now())
                AttendanceStatus.ABSENT -> repository.markAbsent(subjectId, LocalDate.now())
                AttendanceStatus.NO_CLASS -> repository.markNoClass(subjectId, LocalDate.now())
            }
        }
        
        // Trigger widget update
        LargeAttendanceWidget().update(context, glanceId)
    }
}

/**
 * Widget receiver for large widget
 */
class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeAttendanceWidget()
}
