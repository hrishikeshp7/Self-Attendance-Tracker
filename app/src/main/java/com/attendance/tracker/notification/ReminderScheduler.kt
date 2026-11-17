package com.attendance.tracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val WORK_NAME = "attendance_daily_reminder"
    const val REMINDER_REQUEST_CODE = 1001

    @VisibleForTesting
    internal fun calculateNextReminderEpochMillis(
        now: LocalDateTime,
        reminderHour: Int,
        reminderMinute: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        var target = now.withHour(reminderHour).withMinute(reminderMinute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Schedules the daily reminder using AlarmManager.
     *
     * @param reschedule Kept for backward compatibility with callers.
     */
    fun scheduleDailyReminder(context: Context, reschedule: Boolean = false) {
        // Cancel legacy WorkManager work to cleanly migrate existing installs
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel legacy WorkManager work", e)
        }

        if (!NotificationHelper.areNotificationsEnabled(context)) {
            cancelDailyReminder(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val (hour, minute) = NotificationHelper.getReminderTime(context)

        val triggerAtMillis = calculateNextReminderEpochMillis(
            now = LocalDateTime.now(),
            reminderHour = hour,
            reminderMinute = minute
        )

        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm permission not granted, falling back to setAndAllowWhileIdle", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()

        // Also cancel leftover legacy WorkManager work
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel legacy WorkManager work", e)
        }
    }
}
