package com.attendance.tracker.data.repository

import com.attendance.tracker.data.database.ThemePreferenceDao
import com.attendance.tracker.data.model.ThemeMode
import com.attendance.tracker.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow

class ThemePreferenceRepository(private val themePreferenceDao: ThemePreferenceDao) {
    
    val themePreference: Flow<ThemePreference?> = themePreferenceDao.getThemePreference()
    
    suspend fun getThemePreferenceOnce(): ThemePreference {
        return themePreferenceDao.getThemePreferenceOnce() ?: ThemePreference()
    }
    
    suspend fun updateThemeMode(themeMode: ThemeMode) {
        val current = getThemePreferenceOnce()
        themePreferenceDao.updateThemePreference(current.copy(themeMode = themeMode))
    }
    
    suspend fun updateCustomColors(primaryColor: Long?, secondaryColor: Long?) {
        val current = getThemePreferenceOnce()
        themePreferenceDao.updateThemePreference(
            current.copy(
                customPrimaryColor = primaryColor,
                customSecondaryColor = secondaryColor
            )
        )
    }
    
    suspend fun initializeDefaultIfNeeded() {
        if (themePreferenceDao.getThemePreferenceOnce() == null) {
            themePreferenceDao.insertThemePreference(ThemePreference())
        }
    }
}
