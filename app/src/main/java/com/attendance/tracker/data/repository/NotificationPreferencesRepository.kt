package com.attendance.tracker.data.repository

import com.attendance.tracker.data.database.NotificationPreferencesDao
import com.attendance.tracker.data.model.NotificationPreferences
import kotlinx.coroutines.flow.Flow

class NotificationPreferencesRepository(
    private val notificationPreferencesDao: NotificationPreferencesDao
) {
    val preferences: Flow<NotificationPreferences?> = notificationPreferencesDao.getPreferences()

    suspend fun getPreferencesOnce(): NotificationPreferences? {
        return notificationPreferencesDao.getPreferencesOnce()
    }

    suspend fun initializeDefaultIfNeeded() {
        if (notificationPreferencesDao.getPreferencesOnce() == null) {
            notificationPreferencesDao.insertPreferences(NotificationPreferences())
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        val current = notificationPreferencesDao.getPreferencesOnce() ?: NotificationPreferences()
        notificationPreferencesDao.updatePreferences(current.copy(notificationsEnabled = enabled))
    }

    suspend fun updateReminderMinutes(minutes: Int) {
        val current = notificationPreferencesDao.getPreferencesOnce() ?: NotificationPreferences()
        notificationPreferencesDao.updatePreferences(current.copy(reminderMinutesBefore = minutes))
    }

    suspend fun updateLowAttendanceWarnings(enabled: Boolean) {
        val current = notificationPreferencesDao.getPreferencesOnce() ?: NotificationPreferences()
        notificationPreferencesDao.updatePreferences(current.copy(lowAttendanceWarnings = enabled))
    }

    suspend fun updateLowAttendanceThreshold(threshold: Int) {
        val current = notificationPreferencesDao.getPreferencesOnce() ?: NotificationPreferences()
        notificationPreferencesDao.updatePreferences(current.copy(lowAttendanceThreshold = threshold))
    }
}
