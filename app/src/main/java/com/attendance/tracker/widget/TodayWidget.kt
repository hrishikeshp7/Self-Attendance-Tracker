package com.attendance.tracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.attendance.tracker.MainActivity
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SubjectIdKey = ActionParameters.Key<Long>("subjectId")
private val StatusKey = ActionParameters.Key<String>("status")

private data class TodayRow(val subject: Subject, val record: AttendanceRecord?, val label: String) {
    fun stableId(): Long = (subject.id to listOf(record, label, subject.presentLectures, subject.totalLectures, subject.requiredAttendance)).hashCode().toLong()
}

private data class TodayData(val date: LocalDate, val rows: List<TodayRow>, val hasSchedule: Boolean)

/**
 * The one home-screen widget: today's classes with their standing and Present/Absent buttons.
 * Falls back to every subject when no timetable is set up, like the Home screen.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AttendanceDatabase.getDatabase(context)
        val repository = AttendanceRepository(db.subjectDao(), db.attendanceDao(), db.scheduleDao())
        val today = LocalDate.now()
        // Collected inside the composition: an already-running Glance session is woken by
        // updateAll but does not re-run provideGlance, so a one-off snapshot here went stale.
        val flow = combine(
            repository.allSubjects,
            repository.allScheduleEntries,
            repository.getAttendanceForDate(today)
        ) { all, schedule, records -> buildToday(today, all, schedule, records) }.flowOn(Dispatchers.IO)
        val initial = withContext(Dispatchers.IO) { flow.first() }
        provideContent {
            val data by flow.collectAsState(initial)
            GlanceTheme { TodayContent(data) }
        }
    }

    private fun buildToday(
        today: LocalDate,
        all: List<Subject>,
        schedule: List<ScheduleEntry>,
        records: List<AttendanceRecord>
    ): TodayData {
        val byId = all.associateBy { it.id }
        val subjects = all.filter { !it.isFolder }
        val scheduledIds = schedule.filter { it.dayOfWeek == today.dayOfWeek }.map { it.subjectId }.toSet()
        val shown = if (schedule.isEmpty()) subjects else subjects.filter { it.id in scheduledIds }
        val marks = records.associateBy { it.subjectId }
        return TodayData(today, shown.map { TodayRow(it, marks[it.id], it.getDisplayName(byId)) }, schedule.isNotEmpty())
    }
}

@Composable
private fun TodayContent(data: TodayData) {
    val openApp = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(12.dp)
    ) {
        Text(
            text = "Today · " + data.date.format(DateTimeFormatter.ofPattern("EEE d MMM")),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(openApp)
        )
        if (data.rows.isEmpty()) {
            Text(
                text = if (data.hasSchedule) "No classes today" else "Add subjects in the app",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(openApp)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                // The list adapter caches rows by id; fold the displayed state into it so a mark
                // actually re-renders the row instead of showing the stale one.
                items(data.rows, itemId = { it.stableId() }) { SubjectRow(it) }
            }
        }
    }
}

@Composable
private fun SubjectRow(row: TodayRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.label,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                text = standing(row.subject),
                maxLines = 1,
                style = TextStyle(
                    color = if (row.subject.isAboveRequired) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.error,
                    fontSize = 11.sp
                )
            )
        }
        StatusButton(row, AttendanceStatus.PRESENT, "Present")
        Spacer(GlanceModifier.width(6.dp))
        StatusButton(row, AttendanceStatus.ABSENT, "Absent")
    }
}

@Composable
private fun StatusButton(row: TodayRow, status: AttendanceStatus, label: String) {
    // A day can hold both present and absent lectures, so both buttons can be selected
    val selected = when (status) {
        AttendanceStatus.PRESENT -> (row.record?.presentCount ?: 0) > 0
        else -> (row.record?.absentCount ?: 0) > 0
    }
    // The widget only ever sets the whole day to ONE status for ONE lecture. A day that
    // holds extra lectures (or both statuses) can only be changed inside the app.
    val locked = row.record?.let { it.count + it.otherCount > 1 } ?: false
    val (bg, fg) = when {
        !selected -> GlanceTheme.colors.secondaryContainer to GlanceTheme.colors.onSecondaryContainer
        status == AttendanceStatus.PRESENT -> GlanceTheme.colors.primary to GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.error to GlanceTheme.colors.onError
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .width(80.dp)
            .background(bg)
            .cornerRadius(16.dp)
            .padding(vertical = 6.dp)
            .let {
                // Re-tapping a selected button is a no-op, and a locked day is app-only
                if (selected || locked) it else it.clickable(
                    actionRunCallback<MarkAction>(
                        actionParametersOf(SubjectIdKey to row.subject.id, StatusKey to status.name)
                    )
                )
            }
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        )
    }
}

/** "78% · can skip 2" / "62% · attend 3 more" — text, not just colour, carries the status. */
private fun standing(s: Subject): String {
    if (s.totalLectures == 0) return "No classes yet"
    val pct = "%.0f%%".format(s.currentAttendancePercentage)
    return when {
        !s.isAboveRequired -> if (s.classesToAttend >= 999) "$pct · can't reach target" else "$pct · attend ${s.classesToAttend} more"
        s.classesCanBunk > 0 -> "$pct · can skip ${s.classesCanBunk}"
        else -> "$pct · on target"
    }
}

class MarkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val subjectId = parameters[SubjectIdKey] ?: return
        val status = parameters[StatusKey]?.let { AttendanceStatus.valueOf(it) } ?: return
        withContext(Dispatchers.IO) {
            val db = AttendanceDatabase.getDatabase(context)
            val repository = AttendanceRepository(db.subjectDao(), db.attendanceDao(), db.scheduleDao())
            val today = LocalDate.now()
            when (status) {
                AttendanceStatus.PRESENT -> repository.markPresent(subjectId, today)
                AttendanceStatus.ABSENT -> repository.markAbsent(subjectId, today)
                AttendanceStatus.NO_CLASS -> repository.markNoClass(subjectId, today)
            }
        }
        refreshAllWidgets(context)
    }
}

/** Glance doesn't observe the database; call after anything the widget displays changes. */
suspend fun refreshAllWidgets(context: Context) = TodayWidget().updateAll(context)

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
