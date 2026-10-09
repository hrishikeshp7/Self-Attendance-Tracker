package com.attendance.tracker.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ReminderScheduler {

    private const val WORK_NAME = "attendance_daily_reminder"

    /**
     * @param reschedule true when the reminder time changed: cancel-and-reenqueue restarts
     * the daily period from the new initial delay (UPDATE keeps the old enqueue time, so a
     * new time didn't reliably apply). App launches use KEEP so merely opening the app
     * never shifts an already-scheduled reminder.
     */
    fun scheduleDailyReminder(context: Context, reschedule: Boolean = false) {
        val (hour, minute) = NotificationHelper.getReminderTime(context)

        // Calculate initial delay until the next reminder time
        val now = LocalDateTime.now()
        var nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelaySeconds = ChronoUnit.SECONDS.between(now, nextRun)

        val workRequest = PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
