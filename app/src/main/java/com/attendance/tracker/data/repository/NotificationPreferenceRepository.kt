package com.attendance.tracker.data.repository

import com.attendance.tracker.data.database.NotificationPreferenceDao
import com.attendance.tracker.data.model.NotificationPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NotificationPreferenceRepository(
    private val notificationPreferenceDao: NotificationPreferenceDao
) {
    val notificationPreference: Flow<NotificationPreference?> = 
        notificationPreferenceDao.getNotificationPreference()

    suspend fun getNotificationPreferenceOnce(): NotificationPreference? =
        notificationPreferenceDao.getNotificationPreferenceOnce()

    suspend fun initializeDefaultIfNeeded() {
        if (getNotificationPreferenceOnce() == null) {
            notificationPreferenceDao.insertNotificationPreference(NotificationPreference())
        }
    }

    suspend fun updateNotificationPreference(
        enabled: Boolean,
        startTimeHour: Int,
        startTimeMinute: Int,
        message: String
    ) {
        val current = getNotificationPreferenceOnce() ?: NotificationPreference()
        notificationPreferenceDao.updateNotificationPreference(
            current.copy(
                enabled = enabled,
                startTimeHour = startTimeHour,
                startTimeMinute = startTimeMinute,
                message = message
            )
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        val current = getNotificationPreferenceOnce() ?: NotificationPreference()
        notificationPreferenceDao.updateNotificationPreference(
            current.copy(enabled = enabled)
        )
    }
}
