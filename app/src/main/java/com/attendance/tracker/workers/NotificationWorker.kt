package com.attendance.tracker.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.repository.AttendanceRepository
import com.attendance.tracker.data.repository.NotificationPreferencesRepository
import com.attendance.tracker.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek

/**
 * Worker that checks for upcoming classes and sends notifications
 */
class NotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val database = AttendanceDatabase.getDatabase(context)
            val notificationPrefsRepo = NotificationPreferencesRepository(database.notificationPreferencesDao())
            val attendanceRepo = AttendanceRepository(
                database.subjectDao(),
                database.attendanceDao(),
                database.scheduleDao()
            )

            // Initialize notification preferences if needed
            notificationPrefsRepo.initializeDefaultIfNeeded()
            
            val preferences = notificationPrefsRepo.getPreferencesOnce()
            
            // Check if notifications are enabled
            if (preferences?.notificationsEnabled == true) {
                // Get today's day of week
                val today = DayOfWeek.from(java.time.LocalDate.now())
                
                // Get schedule for today
                val scheduleEntries = attendanceRepo.getScheduleForDay(today).first()
                
                // For each scheduled subject, you could check if a class is about to start
                // This is a simplified version - in production, you'd want to store class times
                // and calculate when to send notifications based on the actual time
                
                // For now, we'll just check if there are scheduled classes
                if (scheduleEntries.isNotEmpty()) {
                    // This is a placeholder - in a real implementation, you'd need to:
                    // 1. Store actual class times in the schedule
                    // 2. Calculate when to send notifications
                    // 3. Schedule WorkManager tasks at specific times
                }
            }

            // Check for low attendance warnings
            if (preferences?.lowAttendanceWarnings == true) {
                val subjects = attendanceRepo.actualSubjects.first()
                subjects.forEach { subject ->
                    if (subject.totalLectures > 0 && 
                        subject.currentAttendancePercentage < preferences.lowAttendanceThreshold) {
                        NotificationHelper.showLowAttendanceWarning(
                            context,
                            subject.name,
                            subject.currentAttendancePercentage,
                            subject.requiredAttendance
                        )
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }
}
