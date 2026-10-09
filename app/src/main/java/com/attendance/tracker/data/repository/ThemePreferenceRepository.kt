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
    
    // Writes use insert (REPLACE), not @Update: an update silently does nothing if the
    // single row hasn't been created yet, dropping the user's choice.
    suspend fun updateThemeMode(themeMode: ThemeMode) {
        val current = getThemePreferenceOnce()
        themePreferenceDao.insertThemePreference(current.copy(themeMode = themeMode))
    }
    
    suspend fun initializeDefaultIfNeeded() {
        if (themePreferenceDao.getThemePreferenceOnce() == null) {
            themePreferenceDao.insertThemePreference(ThemePreference())
        }
    }
}
