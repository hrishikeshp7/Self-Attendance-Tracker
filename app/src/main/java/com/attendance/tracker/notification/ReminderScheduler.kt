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

    fun scheduleDailyReminder(context: Context) {
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
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
