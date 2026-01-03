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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * Interactive widget for quick attendance marking
 * Shows subjects with Present/Absent/No Class buttons
 */
class InteractiveAttendanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AttendanceDatabase.getDatabase(context)
        val repository = AttendanceRepository(
            database.subjectDao(),
            database.attendanceDao(),
            database.scheduleDao()
        )
        
        val subjects = withContext(Dispatchers.IO) {
            repository.actualSubjects.first()
        }

        provideContent {
            GlanceTheme {
                InteractiveWidgetContent(subjects = subjects)
            }
        }
    }

    @Composable
    private fun InteractiveWidgetContent(subjects: List<Subject>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp)
        ) {
            // Title
            Text(
                text = "Quick Attendance",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )

            if (subjects.isEmpty()) {
                Text(
                    text = "No subjects available",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            } else {
                // Show up to 3 subjects with action buttons
                subjects.take(3).forEach { subject ->
                    SubjectWithActions(subject = subject)
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }
            }
        }
    }

    @Composable
    private fun SubjectWithActions(subject: Subject) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp)
        ) {
            // Subject name and percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = subject.name,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Spacer(modifier = GlanceModifier.height(6.dp))
            
            // Action buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Present button
                Button(
                    text = "P",
                    onClick = actionRunCallback<InteractiveMarkAttendanceAction>(
                        actionParametersOf(
                            InteractiveSubjectIdKey to subject.id,
                            InteractiveStatusKey to AttendanceStatus.PRESENT.name
                        )
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0xFF4CAF50)),
                        contentColor = ColorProvider(Color.White)
                    )
                )
                
                Spacer(modifier = GlanceModifier.width(4.dp))
                
                // Absent button
                Button(
                    text = "A",
                    onClick = actionRunCallback<InteractiveMarkAttendanceAction>(
                        actionParametersOf(
                            InteractiveSubjectIdKey to subject.id,
                            InteractiveStatusKey to AttendanceStatus.ABSENT.name
                        )
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0xFFF44336)),
                        contentColor = ColorProvider(Color.White)
                    )
                )
                
                Spacer(modifier = GlanceModifier.width(4.dp))
                
                // No Class button
                Button(
                    text = "NC",
                    onClick = actionRunCallback<InteractiveMarkAttendanceAction>(
                        actionParametersOf(
                            InteractiveSubjectIdKey to subject.id,
                            InteractiveStatusKey to AttendanceStatus.NO_CLASS.name
                        )
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0xFF9E9E9E)),
                        contentColor = ColorProvider(Color.White)
                    )
                )
            }
        }
    }
}

/**
 * Action callback for marking attendance from interactive widget
 */
class InteractiveMarkAttendanceAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val subjectId = parameters[InteractiveSubjectIdKey] ?: return
        val statusName = parameters[InteractiveStatusKey] ?: return
        val status = AttendanceStatus.valueOf(statusName)
        
        // Mark attendance in database
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
        
        // Update widget to reflect changes
        InteractiveAttendanceWidget().updateAll(context)
    }
}

/**
 * Widget receiver for interactive widget
 */
class InteractiveAttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InteractiveAttendanceWidget()
}

// Action parameter keys for interactive widget
internal val InteractiveSubjectIdKey = ActionParameters.Key<Long>("interactive_subject_id")
internal val InteractiveStatusKey = ActionParameters.Key<String>("interactive_status")
