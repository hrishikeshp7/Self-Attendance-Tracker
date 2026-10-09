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

    suspend fun deleteSubject(subject: Subject) {
        if (subject.isFolder) {
            // Promote any sub-subjects to top-level rather than orphaning them under a
            // now-deleted folder id — preserves their attendance history and keeps them
            // reachable from the Subjects screen instead of vanishing permanently.
            subjectDao.clearParentForSubjects(subject.id)
        }
        subjectDao.deleteSubject(subject)
    }

    suspend fun markPresent(subjectId: Long, date: LocalDate) = setDayStatus(subjectId, date, AttendanceStatus.PRESENT)

    suspend fun markAbsent(subjectId: Long, date: LocalDate) = setDayStatus(subjectId, date, AttendanceStatus.ABSENT)

    suspend fun markNoClass(subjectId: Long, date: LocalDate) = setDayStatus(subjectId, date, AttendanceStatus.NO_CLASS)

    /**
     * Sets the whole day to [status]. A day that already holds lectures (including a mixed
     * present+absent day) keeps its total lecture count, all switched to [status]; marking
     * a day with the status it already has is a no-op so repeated taps never add a lecture.
     * Subject totals are adjusted by the difference.
     */
    private suspend fun setDayStatus(subjectId: Long, date: LocalDate, status: AttendanceStatus) {
        val existing = attendanceDao.getAttendanceRecord(subjectId, date)
        if (existing?.status == status) return
        val total = if (existing == null) 1 else existing.count + existing.otherCount
        val newPresent = if (status == AttendanceStatus.PRESENT) total else 0
        val newAbsent = if (status == AttendanceStatus.ABSENT) total else 0
        if (existing == null) {
            when (status) {
                AttendanceStatus.PRESENT -> subjectDao.markPresent(subjectId)
                AttendanceStatus.ABSENT -> subjectDao.markAbsent(subjectId)
                AttendanceStatus.NO_CLASS -> {}
            }
        } else {
            getSubjectById(subjectId)?.let {
                subjectDao.updateAttendanceCounts(
                    subjectId,
                    it.presentLectures + newPresent - existing.presentCount,
                    it.absentLectures + newAbsent - existing.absentCount
                )
            }
        }
        attendanceDao.insertAttendance(
            AttendanceRecord(subjectId = subjectId, date = date, status = status, count = total)
        )
    }

    /** Removes the day's record (any status, including No Class); subject totals move by what it held. */
    suspend fun clearDay(subjectId: Long, date: LocalDate) {
        val existing = attendanceDao.getAttendanceRecord(subjectId, date) ?: return
        getSubjectById(subjectId)?.let {
            subjectDao.updateAttendanceCounts(subjectId, it.presentLectures - existing.presentCount, it.absentLectures - existing.absentCount)
        }
        attendanceDao.deleteAttendanceForSubjectOnDate(subjectId, date)
    }

    /**
     * Sets the exact number of present and absent lectures on [date] (the per-lecture editor).
     * Both zero removes the day's record. Subject totals move by the difference.
     */
    suspend fun setDayCounts(subjectId: Long, date: LocalDate, present: Int, absent: Int) {
        val existing = attendanceDao.getAttendanceRecord(subjectId, date)
        val oldPresent = existing?.presentCount ?: 0
        val oldAbsent = existing?.absentCount ?: 0
        if (present == oldPresent && absent == oldAbsent) return
        getSubjectById(subjectId)?.let {
            subjectDao.updateAttendanceCounts(subjectId, it.presentLectures + present - oldPresent, it.absentLectures + absent - oldAbsent)
        }
        when {
            present + absent == 0 -> attendanceDao.deleteAttendanceForSubjectOnDate(subjectId, date)
            present >= absent -> attendanceDao.insertAttendance(
                AttendanceRecord(subjectId = subjectId, date = date, status = AttendanceStatus.PRESENT, count = present, otherCount = absent)
            )
            else -> attendanceDao.insertAttendance(
                AttendanceRecord(subjectId = subjectId, date = date, status = AttendanceStatus.ABSENT, count = absent, otherCount = present)
            )
        }
    }

    /**
     * Adds one more lecture on [date] with [status] (PRESENT or ABSENT), alongside whatever the
     * day already holds: present and absent lectures can coexist on the same date. A day with
     * no record, or marked No Class, just gets marked [status].
     */
    suspend fun addExtraLecture(subjectId: Long, date: LocalDate, status: AttendanceStatus) {
        if (status == AttendanceStatus.NO_CLASS) return
        val record = attendanceDao.getAttendanceRecord(subjectId, date)
        if (record == null || record.status == AttendanceStatus.NO_CLASS) {
            setDayStatus(subjectId, date, status)
            return
        }
        when (status) {
            AttendanceStatus.PRESENT -> subjectDao.markPresent(subjectId)
            else -> subjectDao.markAbsent(subjectId)
        }
        attendanceDao.insertAttendance(
            if (record.status == status) record.copy(count = record.count + 1)
            else record.copy(otherCount = record.otherCount + 1)
        )
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

    // Backup / Restore helpers

    suspend fun getAllSubjectsOnce(): List<Subject> = subjectDao.getAllSubjectsOnce()

    suspend fun getAllAttendanceRecordsOnce(): List<AttendanceRecord> =
        attendanceDao.getAllAttendanceRecordsOnce()

    suspend fun getAllScheduleEntriesOnce(): List<ScheduleEntry> =
        scheduleDao.getAllScheduleEntriesOnce()

    suspend fun restoreData(
        subjects: List<Subject>,
        attendanceRecords: List<AttendanceRecord>,
        scheduleEntries: List<ScheduleEntry>
    ) {
        // Clear in dependency order (child tables first)
        scheduleDao.deleteAllScheduleEntries()
        attendanceDao.deleteAllAttendance()
        subjectDao.deleteAllSubjects()
        // Insert restored data
        subjectDao.insertSubjects(subjects)
        attendanceDao.insertAttendanceRecords(attendanceRecords)
        scheduleDao.insertScheduleEntries(scheduleEntries)
    }

    data class CsvImportSummary(val subjectsAdded: Int, val subjectsExisting: Int, val slotsAdded: Int)

    /**
     * Adds the parsed CSV rows. Subjects are matched by name (case-insensitive, top-level
     * only): an existing subject keeps its target and counts and only gains timetable
     * slots it doesn't already have, so importing the same file twice changes nothing.
     */
    suspend fun importCsv(data: com.attendance.tracker.backup.CsvImporter.Result): CsvImportSummary {
        val idByName = subjectDao.getAllSubjectsOnce()
            .filter { it.parentSubjectId == null && !it.isFolder }
            .associate { it.name.lowercase() to it.id }
            .toMutableMap()
        var added = 0
        var existing = 0
        for (row in data.subjects) {
            if (row.name.lowercase() in idByName) { existing++; continue }
            val total = row.total ?: 0
            val present = row.attended ?: 0
            idByName[row.name.lowercase()] = subjectDao.insertSubject(
                Subject(
                    name = row.name,
                    requiredAttendance = row.requiredAttendance ?: 75,
                    totalLectures = total,
                    presentLectures = present,
                    absentLectures = total - present
                )
            )
            added++
        }
        val known = scheduleDao.getAllScheduleEntriesOnce().toMutableList()
        var slotsAdded = 0
        for (slot in data.slots) {
            val subjectId = idByName[slot.subjectName.lowercase()] ?: continue
            if (known.any { it.subjectId == subjectId && it.dayOfWeek == slot.day && it.startTime == slot.start && it.endTime == slot.end }) continue
            val entry = ScheduleEntry(subjectId = subjectId, dayOfWeek = slot.day, startTime = slot.start, endTime = slot.end)
            scheduleDao.insertScheduleEntry(entry)
            known += entry.copy(id = -1)
            slotsAdded++
        }
        return CsvImportSummary(added, existing, slotsAdded)
    }
}
