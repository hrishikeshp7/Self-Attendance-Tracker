package com.attendance.tracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.attendance.tracker.data.model.NotificationPreferences
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationPreferencesDao {
    @Query("SELECT * FROM notification_preferences WHERE id = 1 LIMIT 1")
    fun getPreferences(): Flow<NotificationPreferences?>

    @Query("SELECT * FROM notification_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferencesOnce(): NotificationPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: NotificationPreferences)

    @Update
    suspend fun updatePreferences(preferences: NotificationPreferences)
}
