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

    @Query("SELECT * FROM subjects WHERE id IN (:ids)")
    suspend fun getSubjectsByIdsOnce(ids: List<Long>): List<Subject>
    
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

    // parentSubjectId has no foreign-key/cascade relationship, so deleting a folder
    // without this would leave its children pointing at a nonexistent parent id —
    // invisible both at top-level (parentSubjectId still non-null) and inside the
    // folder (which no longer exists), permanently unreachable from the Subjects screen.
    @Query("UPDATE subjects SET parentSubjectId = NULL WHERE parentSubjectId = :parentId")
    suspend fun clearParentForSubjects(parentId: Long)

    @Query("UPDATE subjects SET presentLectures = presentLectures + 1, totalLectures = totalLectures + 1 WHERE id = :subjectId")
    suspend fun markPresent(subjectId: Long)

    @Query("UPDATE subjects SET absentLectures = absentLectures + 1, totalLectures = totalLectures + 1 WHERE id = :subjectId")
    suspend fun markAbsent(subjectId: Long)

    @Query("UPDATE subjects SET presentLectures = :present, absentLectures = :absent, totalLectures = :present + :absent WHERE id = :subjectId")
    suspend fun updateAttendanceCounts(subjectId: Long, present: Int, absent: Int)

    @Query("SELECT * FROM subjects ORDER BY id ASC")
    suspend fun getAllSubjectsOnce(): List<Subject>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<Subject>)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()
}
