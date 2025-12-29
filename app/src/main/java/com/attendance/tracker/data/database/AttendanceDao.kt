package com.attendance.tracker.data.database

import androidx.room.*
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId ORDER BY date DESC")
    fun getAttendanceForSubject(subjectId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: LocalDate): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId AND date = :date")
    suspend fun getAttendanceRecord(subjectId: Long, date: LocalDate): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getAttendanceInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord): Long

    @Update
    suspend fun updateAttendance(record: AttendanceRecord)

    @Delete
    suspend fun deleteAttendance(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE subjectId = :subjectId AND date = :date")
    suspend fun deleteAttendanceForSubjectOnDate(subjectId: Long, date: LocalDate)
}
