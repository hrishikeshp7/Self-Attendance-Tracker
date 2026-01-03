package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents user preferences for notifications
 */
@Entity(tableName = "notification_preferences")
data class NotificationPreferences(
    @PrimaryKey
    val id: Int = 1, // Single row for app-wide settings
    val notificationsEnabled: Boolean = true,
    val reminderMinutesBefore: Int = 15, // Default 15 minutes before class
    val lowAttendanceWarnings: Boolean = true,
    val lowAttendanceThreshold: Int = 75 // Warn when attendance drops below this percentage
)
