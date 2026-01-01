package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "theme_preferences")
data class ThemePreference(
    @PrimaryKey
    val id: Int = 1, // Single row for app-wide theme
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customPrimaryColor: Long? = null,
    val customSecondaryColor: Long? = null
)

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM
}
