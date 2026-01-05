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
    val topLevelSubjects: Flow<List<Subject>> = subjectDao.getTopLevelSubjects()
    val actualSubjects: Flow<List<Subject>> = subjectDao.getActualSubjects()

    suspend fun getSubjectById(id: Long): Subject? = subjectDao.getSubjectById(id)
    
    fun getSubSubjects(parentId: Long): Flow<List<Subject>> = subjectDao.getSubSubjects(parentId)

    suspend fun insertSubject(subject: Subject): Long = subjectDao.insertSubject(subject)

    suspend fun updateSubject(subject: Subject) = subjectDao.updateSubject(subject)

    suspend fun deleteSubject(subject: Subject) = subjectDao.deleteSubject(subject)

    suspend fun markPresent(subjectId: Long, date: LocalDate) {
        // Check if there's already a record for this subject on this date
        val existingRecord = attendanceDao.getAttendanceRecord(subjectId, date)
        
        if (existingRecord != null && existingRecord.status == AttendanceStatus.PRESENT) {
            // If already marked present, increment the count
            val updatedRecord = existingRecord.copy(count = existingRecord.count + 1)
            attendanceDao.insertAttendance(updatedRecord)
            // Also increment subject counts
            subjectDao.markPresent(subjectId)
        } else if (existingRecord != null) {
            // Different status exists, replace it
            // First, adjust subject counts based on previous status
            when (existingRecord.status) {
                AttendanceStatus.ABSENT -> {
                    // Was absent, now present: decrease absent, increase present
                    val subject = getSubjectById(subjectId)
                    subject?.let {
                        subjectDao.updateAttendanceCounts(
                            subjectId,
                            it.presentLectures + existingRecord.count,
                            it.absentLectures - existingRecord.count
                        )
                    }
                }
                AttendanceStatus.NO_CLASS -> {
                    // Was no class, now present: increase present and total
                    val subject = getSubjectById(subjectId)
                    subject?.let {
                        subjectDao.updateAttendanceCounts(
                            subjectId,
                            it.presentLectures + existingRecord.count,
                            it.absentLectures
                        )
                    }
                }
                else -> {}
            }
            // Insert new present record
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = AttendanceStatus.PRESENT,
                    count = 1
                )
            )
        } else {
            // No existing record, create new one
            subjectDao.markPresent(subjectId)
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = AttendanceStatus.PRESENT,
                    count = 1
                )
            )
        }
    }

    suspend fun markAbsent(subjectId: Long, date: LocalDate) {
        // Check if there's already a record for this subject on this date
        val existingRecord = attendanceDao.getAttendanceRecord(subjectId, date)
        
        if (existingRecord != null && existingRecord.status == AttendanceStatus.ABSENT) {
            // If already marked absent, increment the count
            val updatedRecord = existingRecord.copy(count = existingRecord.count + 1)
            attendanceDao.insertAttendance(updatedRecord)
            // Also increment subject counts
            subjectDao.markAbsent(subjectId)
        } else if (existingRecord != null) {
            // Different status exists, replace it
            // First, adjust subject counts based on previous status
            when (existingRecord.status) {
                AttendanceStatus.PRESENT -> {
                    // Was present, now absent: decrease present, increase absent
                    val subject = getSubjectById(subjectId)
                    subject?.let {
                        subjectDao.updateAttendanceCounts(
                            subjectId,
                            it.presentLectures - existingRecord.count,
                            it.absentLectures + existingRecord.count
                        )
                    }
                }
                AttendanceStatus.NO_CLASS -> {
                    // Was no class, now absent: increase absent and total
                    val subject = getSubjectById(subjectId)
                    subject?.let {
                        subjectDao.updateAttendanceCounts(
                            subjectId,
                            it.presentLectures,
                            it.absentLectures + existingRecord.count
                        )
                    }
                }
                else -> {}
            }
            // Insert new absent record
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = AttendanceStatus.ABSENT,
                    count = 1
                )
            )
        } else {
            // No existing record, create new one
            subjectDao.markAbsent(subjectId)
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = AttendanceStatus.ABSENT,
                    count = 1
                )
            )
        }
    }

    suspend fun markNoClass(subjectId: Long, date: LocalDate) {
        // Check if there's already a record for this subject on this date
        val existingRecord = attendanceDao.getAttendanceRecord(subjectId, date)
        
        if (existingRecord != null && existingRecord.status == AttendanceStatus.NO_CLASS) {
            // If already marked no class, increment the count
            val updatedRecord = existingRecord.copy(count = existingRecord.count + 1)
            attendanceDao.insertAttendance(updatedRecord)
        } else if (existingRecord != null) {
            // Different status exists, replace it
            // Adjust subject counts based on previous status
            when (existingRecord.status) {
                AttendanceStatus.PRESENT -> {
                    // Was present, now no class: decrease present and total
                    val subject = getSubjectById(subjectId)
                    subject?.let {
                        subjectDao.updateAttendanceCounts(
                            subjectId,
                            it.presentLectures - existingRecord.count,
                            it.absentLectures
                        )
                    }
                }
                AttendanceStatus.ABSENT -> {
                    // Was absent, now no class: decrease absent and total
                    val subject = getSubjectById(subjectId)
                    subject?.let {
                        subjectDao.updateAttendanceCounts(
                            subjectId,
                            it.presentLectures,
                            it.absentLectures - existingRecord.count
                        )
                    }
                }
                else -> {}
            }
            // Insert new no class record
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = AttendanceStatus.NO_CLASS,
                    count = 1
                )
            )
        } else {
            // No existing record, create new one
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = AttendanceStatus.NO_CLASS,
                    count = 1
                )
            )
        }
    }
    
    suspend fun setAttendanceStatus(subjectId: Long, date: LocalDate, status: AttendanceStatus, count: Int = 1) {
        // Insert/update attendance record without modifying subject counts
        attendanceDao.insertAttendance(
            AttendanceRecord(
                subjectId = subjectId,
                date = date,
                status = status,
                count = count
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

    suspend fun deleteAttendanceRecord(subjectId: Long, date: LocalDate) {
        attendanceDao.deleteAttendanceForSubjectOnDate(subjectId, date)
    }

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
