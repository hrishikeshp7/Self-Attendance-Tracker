package com.attendance.tracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.attendance.tracker.data.TimeOffStore
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.database.SubjectDao
import com.attendance.tracker.data.model.AttendanceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "Rescheduling daily reminder on action: $action")
                ReminderScheduler.scheduleDailyReminder(context)
            }
            else -> {
                Log.d(TAG, "Executing daily reminder alarm trigger on action: $action")
                val pendingResult = goAsync()
                val appContext = context.applicationContext ?: context
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        checkAndNotifyAttendance(appContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking attendance reminder", e)
                    } finally {
                        ReminderScheduler.scheduleDailyReminder(appContext)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun checkAndNotifyAttendance(context: Context) {
        if (!NotificationHelper.areNotificationsEnabled(context)) {
            return
        }

        NotificationHelper.createNotificationChannels(context)

        val database = AttendanceDatabase.getDatabase(context)
        val today = LocalDate.now()

        // 1. Check if an all-subject holiday / vacation rule is active today
        val activeRule = TimeOffStore.getActiveRuleForDate(context, today)
        if (activeRule != null && activeRule.status == AttendanceStatus.NO_CLASS && activeRule.subjectIds == null) {
            return
        }

        // Get all schedule entries for today
        val scheduleDao = database.scheduleDao()
        val attendanceDao = database.attendanceDao()
        val subjectDao = database.subjectDao()

        val todayScheduledEntries = scheduleDao.getScheduleForDayOnce(today.dayOfWeek)
            .filter { it.occursOn(today) }
            .filter { entry ->
                val subRule = TimeOffStore.getActiveRuleForDate(context, today, entry.subjectId)
                subRule?.status != AttendanceStatus.NO_CLASS
            }

        if (todayScheduledEntries.isEmpty()) {
            val allScheduleEntries = scheduleDao.getAllScheduleEntriesOnce()
            if (allScheduleEntries.isEmpty()) {
                val actualSubjects = subjectDao.getAllSubjectsOnce().filter { !it.isFolder }
                if (actualSubjects.isNotEmpty() && today.dayOfWeek != java.time.DayOfWeek.SATURDAY && today.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                    val markedIds = attendanceDao.getAttendanceRecordsForSubjectsOnDateOnce(actualSubjects.map { it.id }, today).map { it.subjectId }.toSet()
                    val unmarked = actualSubjects.filter { it.id !in markedIds }
                    if (unmarked.isNotEmpty()) {
                        NotificationHelper.showDailyReminderNotification(context)
                        return
                    }
                }
            }
            // No classes scheduled today – notify at-risk subjects if any
            notifyAtRiskOnly(context, subjectDao)
            return
        }

        // Check which scheduled subjects have no attendance marked for today
        val subjectIds = todayScheduledEntries.map { it.subjectId }.distinct()
        val todayAttendanceRecords = attendanceDao.getAttendanceRecordsForSubjectsOnDateOnce(subjectIds, today)
        val markedSubjectIds = todayAttendanceRecords.map { it.subjectId }.toSet()
        val subjectsMap = subjectDao.getSubjectsByIdsOnce(subjectIds).associateBy { it.id }

        // Per subject, not per slot: a subject with two lectures today (e.g. lecture + lab)
        // must be listed once, not twice with an inflated count.
        val unmarkedSubjectNames = subjectIds
            .filter { it !in markedSubjectIds }
            .mapNotNull { subjectsMap[it] }
            .filter { !it.isFolder }
            .map { it.name }

        if (unmarkedSubjectNames.isNotEmpty()) {
            // Send missed-mark notification
            NotificationHelper.showMissedMarkNotification(context, unmarkedSubjectNames)
        } else {
            // All scheduled classes are marked – only check for at-risk subjects
            notifyAtRiskOnly(context, subjectDao)
        }
    }

    private suspend fun notifyAtRiskOnly(context: Context, subjectDao: SubjectDao) {
        val atRiskSubjectNames = subjectDao.getAllSubjectsOnce()
            .filter { !it.isFolder && it.totalLectures > 0 && !it.isAboveRequired }
            .map { it.name }

        if (atRiskSubjectNames.isNotEmpty()) {
            NotificationHelper.showAtRiskNotification(context, atRiskSubjectNames)
        }
    }

    companion object {
        private const val TAG = "DailyReminderReceiver"
        const val ACTION_DAILY_REMINDER = "com.attendance.tracker.action.DAILY_REMINDER"
    }
}
