package com.attendance.tracker.data.repository

import com.attendance.tracker.data.database.AttendanceDao
import com.attendance.tracker.data.database.ScheduleDao
import com.attendance.tracker.data.database.SubjectDao
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate

class AttendanceRepository(
    private val subjectDao: SubjectDao,
    private val attendanceDao: AttendanceDao,
    private val scheduleDao: ScheduleDao
) {
    // Subject operations
    val allSubjects: Flow<List<Subject>> = subjectDao.getAllSubjects()

    suspend fun getSubjectById(id: Long): Subject? = subjectDao.getSubjectById(id)

    suspend fun insertSubject(subject: Subject): Long = subjectDao.insertSubject(subject)

    suspend fun updateSubject(subject: Subject) = subjectDao.updateSubject(subject)

    suspend fun deleteSubject(subject: Subject) = subjectDao.deleteSubject(subject)

    suspend fun markPresent(subjectId: Long, date: LocalDate) {
        subjectDao.markPresent(subjectId)
        attendanceDao.insertAttendance(
            AttendanceRecord(
                subjectId = subjectId,
                date = date,
                status = AttendanceStatus.PRESENT
            )
        )
    }

    suspend fun markAbsent(subjectId: Long, date: LocalDate) {
        subjectDao.markAbsent(subjectId)
        attendanceDao.insertAttendance(
            AttendanceRecord(
                subjectId = subjectId,
                date = date,
                status = AttendanceStatus.ABSENT
            )
        )
    }

    suspend fun markNoClass(subjectId: Long, date: LocalDate) {
        attendanceDao.insertAttendance(
            AttendanceRecord(
                subjectId = subjectId,
                date = date,
                status = AttendanceStatus.NO_CLASS
            )
        )
    }

    suspend fun updateAttendanceCounts(subjectId: Long, present: Int, absent: Int) {
        subjectDao.updateAttendanceCounts(subjectId, present, absent)
    }

    suspend fun updateRequiredAttendance(subjectId: Long, required: Int) {
        subjectDao.updateRequiredAttendance(subjectId, required)
    }

    // Attendance operations
    fun getAttendanceForSubject(subjectId: Long): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForSubject(subjectId)

    fun getAttendanceForDate(date: LocalDate): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForDate(date)

    suspend fun getAttendanceRecord(subjectId: Long, date: LocalDate): AttendanceRecord? =
        attendanceDao.getAttendanceRecord(subjectId, date)

    fun getAttendanceInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceInRange(startDate, endDate)

    // Schedule operations
    val allScheduleEntries: Flow<List<ScheduleEntry>> = scheduleDao.getAllScheduleEntries()

    fun getScheduleForDay(dayOfWeek: DayOfWeek): Flow<List<ScheduleEntry>> =
        scheduleDao.getScheduleForDay(dayOfWeek)

    fun getScheduleForSubject(subjectId: Long): Flow<List<ScheduleEntry>> =
        scheduleDao.getScheduleForSubject(subjectId)

    suspend fun insertScheduleEntry(entry: ScheduleEntry): Long =
        scheduleDao.insertScheduleEntry(entry)

    suspend fun updateScheduleEntry(entry: ScheduleEntry) =
        scheduleDao.updateScheduleEntry(entry)

    suspend fun deleteScheduleEntry(entry: ScheduleEntry) =
        scheduleDao.deleteScheduleEntry(entry)

    suspend fun deleteScheduleForSubject(subjectId: Long) =
        scheduleDao.deleteScheduleForSubject(subjectId)

    suspend fun toggleScheduleEntry(subjectId: Long, dayOfWeek: DayOfWeek) {
        val existingEntries = scheduleDao.getScheduleForSubject(subjectId).first()
        
        val existing = existingEntries.find { it.dayOfWeek == dayOfWeek }
        if (existing != null) {
            scheduleDao.deleteScheduleEntry(subjectId, dayOfWeek)
        } else {
            scheduleDao.insertScheduleEntry(
                ScheduleEntry(subjectId = subjectId, dayOfWeek = dayOfWeek)
            )
        }
    }
}
