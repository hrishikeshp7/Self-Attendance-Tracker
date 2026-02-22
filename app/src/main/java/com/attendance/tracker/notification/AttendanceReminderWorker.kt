package com.attendance.tracker.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendance.tracker.data.database.AttendanceDatabase
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
            // No classes scheduled today – send general reminder
            NotificationHelper.showDailyReminderNotification(applicationContext)
            return Result.success()
        }

        // Check which scheduled subjects have no attendance marked for today
        val unmarkedSubjectNames = mutableListOf<String>()
        for (entry in todayScheduledEntries) {
            val record = attendanceDao.getAttendanceRecord(entry.subjectId, today)
            if (record == null) {
                val subject = subjectDao.getSubjectById(entry.subjectId)
                if (subject != null && !subject.isFolder) {
                    unmarkedSubjectNames.add(subject.name)
                }
            }
        }

        if (unmarkedSubjectNames.isNotEmpty()) {
            // Send missed-mark notification
            NotificationHelper.showMissedMarkNotification(applicationContext, unmarkedSubjectNames)
        } else {
            // All scheduled classes are marked – send a general reminder
            NotificationHelper.showDailyReminderNotification(applicationContext)
        }

        return Result.success()
    }
}
