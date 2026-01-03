package com.attendance.tracker.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.attendance.tracker.MainActivity
import com.attendance.tracker.R

object NotificationHelper {
    private const val CHANNEL_ID_REMINDERS = "class_reminders"
    private const val CHANNEL_ID_LOW_ATTENDANCE = "low_attendance_warnings"
    private const val NOTIFICATION_ID_REMINDER = 1001
    private const val NOTIFICATION_ID_LOW_ATTENDANCE = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming classes"
            }

            val lowAttendanceChannel = NotificationChannel(
                CHANNEL_ID_LOW_ATTENDANCE,
                "Low Attendance Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Warnings when attendance drops below required percentage"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(lowAttendanceChannel)
        }
    }

    fun showClassReminderNotification(
        context: Context,
        subjectName: String,
        minutesBefore: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Upcoming Class: $subjectName")
            .setContentText("Your class starts in $minutesBefore minutes")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_REMINDER, notification)
        }
    }

    fun showLowAttendanceWarning(
        context: Context,
        subjectName: String,
        currentPercentage: Float,
        requiredPercentage: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LOW_ATTENDANCE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Low Attendance Warning: $subjectName")
            .setContentText("Current: %.1f%% | Required: %d%%".format(currentPercentage, requiredPercentage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_LOW_ATTENDANCE, notification)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
