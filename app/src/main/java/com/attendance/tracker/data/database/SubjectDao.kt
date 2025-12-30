package com.attendance.tracker.data.database

import androidx.room.*
import com.attendance.tracker.data.model.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): Subject?
    
    @Query("SELECT * FROM subjects WHERE parentSubjectId IS NULL ORDER BY name ASC")
    fun getTopLevelSubjects(): Flow<List<Subject>>
    
    @Query("SELECT * FROM subjects WHERE parentSubjectId = :parentId ORDER BY name ASC")
    fun getSubSubjects(parentId: Long): Flow<List<Subject>>
    
    @Query("SELECT * FROM subjects WHERE isFolder = 0 ORDER BY name ASC")
    fun getActualSubjects(): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Query("UPDATE subjects SET presentLectures = presentLectures + 1, totalLectures = totalLectures + 1 WHERE id = :subjectId")
    suspend fun markPresent(subjectId: Long)

    @Query("UPDATE subjects SET absentLectures = absentLectures + 1, totalLectures = totalLectures + 1 WHERE id = :subjectId")
    suspend fun markAbsent(subjectId: Long)

    @Query("UPDATE subjects SET presentLectures = :present, absentLectures = :absent, totalLectures = :present + :absent WHERE id = :subjectId")
    suspend fun updateAttendanceCounts(subjectId: Long, present: Int, absent: Int)

    @Query("UPDATE subjects SET requiredAttendance = :required WHERE id = :subjectId")
    suspend fun updateRequiredAttendance(subjectId: Long, required: Int)
}
