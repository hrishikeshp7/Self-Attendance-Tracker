package com.attendance.tracker.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendance.tracker.data.TimeOffStore
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.database.SubjectDao
import com.attendance.tracker.data.model.AttendanceStatus
import java.time.LocalDate

class AttendanceReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!NotificationHelper.areNotificationsEnabled(applicationContext)) {
            return Result.success()
        }

        NotificationHelper.createNotificationChannels(applicationContext)

        val database = AttendanceDatabase.getDatabase(applicationContext)
        val today = LocalDate.now()

        // 1. Check if an all-subject holiday / vacation rule is active today
        val activeRule = TimeOffStore.getActiveRuleForDate(applicationContext, today)
        if (activeRule != null && activeRule.status == AttendanceStatus.NO_CLASS && activeRule.subjectIds == null) {
            return Result.success()
        }

        // Get all schedule entries for today
        val scheduleDao = database.scheduleDao()
        val attendanceDao = database.attendanceDao()
        val subjectDao = database.subjectDao()

        val todayScheduledEntries = scheduleDao.getScheduleForDayOnce(today.dayOfWeek)
            .filter { it.occursOn(today) }
            .filter { entry ->
                val subRule = TimeOffStore.getActiveRuleForDate(applicationContext, today, entry.subjectId)
                subRule?.status != AttendanceStatus.NO_CLASS
            }

        if (todayScheduledEntries.isEmpty()) {
            // No classes scheduled today – notify at-risk subjects if any, but do not send "Mark Today's Attendance"
            notifyAtRiskOnly(subjectDao)
            return Result.success()
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
            NotificationHelper.showMissedMarkNotification(applicationContext, unmarkedSubjectNames)
        } else {
            // All scheduled classes are marked – only check for at-risk subjects
            notifyAtRiskOnly(subjectDao)
        }

        return Result.success()
    }

    /**
     * Warns about subjects whose attendance has fallen below the required percentage.
     */
    private suspend fun notifyAtRiskOnly(subjectDao: SubjectDao) {
        val atRiskSubjectNames = subjectDao.getAllSubjectsOnce()
            .filter { !it.isFolder && it.totalLectures > 0 && !it.isAboveRequired }
            .map { it.name }

        if (atRiskSubjectNames.isNotEmpty()) {
            NotificationHelper.showAtRiskNotification(applicationContext, atRiskSubjectNames)
        }
    }
}
