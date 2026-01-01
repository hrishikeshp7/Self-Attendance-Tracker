package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents notification preferences for persistent attendance reminders
 */
@Entity(tableName = "notification_preferences")
data class NotificationPreference(
    @PrimaryKey
    val id: Int = 1, // Single row for notification settings
    val enabled: Boolean = false,
    val startTimeHour: Int = 9, // Default 9 AM
    val startTimeMinute: Int = 0,
    val message: String = "Time to mark your attendance!"
)
