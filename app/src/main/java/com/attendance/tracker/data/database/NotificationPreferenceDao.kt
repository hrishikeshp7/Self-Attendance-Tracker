package com.attendance.tracker.data.database

import androidx.room.*
import com.attendance.tracker.data.model.NotificationPreference
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationPreferenceDao {
    @Query("SELECT * FROM notification_preferences WHERE id = 1 LIMIT 1")
    fun getNotificationPreference(): Flow<NotificationPreference?>

    @Query("SELECT * FROM notification_preferences WHERE id = 1 LIMIT 1")
    suspend fun getNotificationPreferenceOnce(): NotificationPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationPreference(preference: NotificationPreference)

    @Update
    suspend fun updateNotificationPreference(preference: NotificationPreference)
}
