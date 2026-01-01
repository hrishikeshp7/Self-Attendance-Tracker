package com.attendance.tracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.attendance.tracker.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemePreferenceDao {
    @Query("SELECT * FROM theme_preferences WHERE id = 1 LIMIT 1")
    fun getThemePreference(): Flow<ThemePreference?>
    
    @Query("SELECT * FROM theme_preferences WHERE id = 1 LIMIT 1")
    suspend fun getThemePreferenceOnce(): ThemePreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThemePreference(themePreference: ThemePreference)

    @Update
    suspend fun updateThemePreference(themePreference: ThemePreference)
}
