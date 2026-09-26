package com.attendance.tracker.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.database.SubjectDao
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
        val todayDayOfWeek = today.dayOfWeek

        // Get all schedule entries for today
        val scheduleDao = database.scheduleDao()
        val attendanceDao = database.attendanceDao()
        val subjectDao = database.subjectDao()

        val todayScheduledEntries = scheduleDao.getScheduleForDayOnce(todayDayOfWeek)
        if (todayScheduledEntries.isEmpty()) {
            // No classes scheduled today – fall back to an at-risk check, then a general reminder
            notifyAtRiskOrDefault(subjectDao)
            return Result.success()
        }

        // Check which scheduled subjects have no attendance marked for today
        val subjectIds = todayScheduledEntries.map { it.subjectId }.distinct()
        val todayAttendanceRecords = attendanceDao.getAttendanceRecordsForSubjectsOnDateOnce(subjectIds, today)
        val markedSubjectIds = todayAttendanceRecords.map { it.subjectId }.toSet()
        val subjectsMap = subjectDao.getSubjectsByIdsOnce(subjectIds).associateBy { it.id }

        val unmarkedSubjectNames = mutableListOf<String>()
        for (entry in todayScheduledEntries) {
            if (entry.subjectId !in markedSubjectIds) {
                val subject = subjectsMap[entry.subjectId]
                if (subject != null && !subject.isFolder) {
                    unmarkedSubjectNames.add(subject.name)
                }
            }
        }

        if (unmarkedSubjectNames.isNotEmpty()) {
            // Send missed-mark notification
            NotificationHelper.showMissedMarkNotification(applicationContext, unmarkedSubjectNames)
        } else {
            // All scheduled classes are marked – check for at-risk subjects before a general reminder
            notifyAtRiskOrDefault(subjectDao)
        }

        return Result.success()
    }

    /**
     * Warns about subjects whose attendance has fallen below the required percentage;
     * falls back to the general daily reminder when nothing is at risk.
     */
    private suspend fun notifyAtRiskOrDefault(subjectDao: SubjectDao) {
        val atRiskSubjectNames = subjectDao.getAllSubjectsOnce()
            .filter { !it.isFolder && it.totalLectures > 0 && !it.isAboveRequired }
            .map { it.name }

        if (atRiskSubjectNames.isNotEmpty()) {
            NotificationHelper.showAtRiskNotification(applicationContext, atRiskSubjectNames)
        } else {
            NotificationHelper.showDailyReminderNotification(applicationContext)
        }
    }
}
