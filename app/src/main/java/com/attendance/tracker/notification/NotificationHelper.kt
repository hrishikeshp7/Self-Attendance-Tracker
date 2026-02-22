package com.attendance.tracker.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.attendance.tracker.MainActivity
import com.attendance.tracker.R

object NotificationHelper {

    const val CHANNEL_ID_REMINDER = "attendance_reminder"
    const val CHANNEL_ID_ALERT = "attendance_alert"

    const val NOTIFICATION_ID_DAILY_REMINDER = 1001
    const val NOTIFICATION_ID_MISSED_MARK = 1002
    const val NOTIFICATION_ID_AT_RISK = 1003

    const val PREF_NAME = "notification_prefs"
    const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val PREF_REMINDER_HOUR = "reminder_hour"
    const val PREF_REMINDER_MINUTE = "reminder_minute"

    fun createNotificationChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reminderChannel = NotificationChannel(
            CHANNEL_ID_REMINDER,
            "Daily Attendance Reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminder to mark class attendance"
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ID_ALERT,
            "Attendance Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for missed marks and low attendance warnings"
        }

        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    fun showDailyReminderNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Mark Today's Attendance")
            .setContentText("Don't forget to mark attendance for today's classes.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DAILY_REMINDER, notification)
    }

    fun showMissedMarkNotification(context: Context, subjectNames: List<String>) {
        if (subjectNames.isEmpty() || !hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (subjectNames.size == 1) {
            "You haven't marked attendance for: ${subjectNames[0]}"
        } else {
            "You haven't marked attendance for ${subjectNames.size} scheduled classes today."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Attendance Not Marked")
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bodyText + if (subjectNames.size > 1) "\n" + subjectNames.joinToString(", ") else "")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MISSED_MARK, notification)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun getReminderTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(PREF_REMINDER_HOUR, 18)
        val minute = prefs.getInt(PREF_REMINDER_MINUTE, 0)
        return Pair(hour, minute)
    }

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_REMINDER_HOUR, hour)
            .putInt(PREF_REMINDER_MINUTE, minute)
            .apply()
    }
}
