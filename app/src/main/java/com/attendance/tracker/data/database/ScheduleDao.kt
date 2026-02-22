package com.attendance.tracker.data.database

import androidx.room.*
import com.attendance.tracker.data.model.ScheduleEntry
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_entries ORDER BY dayOfWeek ASC")
    fun getAllScheduleEntries(): Flow<List<ScheduleEntry>>

    @Query("SELECT * FROM schedule_entries WHERE dayOfWeek = :dayOfWeek")
    fun getScheduleForDay(dayOfWeek: DayOfWeek): Flow<List<ScheduleEntry>>

    @Query("SELECT * FROM schedule_entries WHERE dayOfWeek = :dayOfWeek")
    suspend fun getScheduleForDayOnce(dayOfWeek: DayOfWeek): List<ScheduleEntry>

    @Query("SELECT * FROM schedule_entries WHERE subjectId = :subjectId")
    fun getScheduleForSubject(subjectId: Long): Flow<List<ScheduleEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleEntry(entry: ScheduleEntry): Long

    @Update
    suspend fun updateScheduleEntry(entry: ScheduleEntry)

    @Delete
    suspend fun deleteScheduleEntry(entry: ScheduleEntry)

    @Query("DELETE FROM schedule_entries WHERE subjectId = :subjectId")
    suspend fun deleteScheduleForSubject(subjectId: Long)

    @Query("DELETE FROM schedule_entries WHERE subjectId = :subjectId AND dayOfWeek = :dayOfWeek")
    suspend fun deleteScheduleEntry(subjectId: Long, dayOfWeek: DayOfWeek)

    @Query("SELECT * FROM schedule_entries ORDER BY subjectId ASC")
    suspend fun getAllScheduleEntriesOnce(): List<ScheduleEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleEntries(entries: List<ScheduleEntry>)

    @Query("DELETE FROM schedule_entries")
    suspend fun deleteAllScheduleEntries()
}
