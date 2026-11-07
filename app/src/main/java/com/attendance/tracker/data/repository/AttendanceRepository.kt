package com.attendance.tracker.data.repository

import androidx.room.withTransaction
import com.attendance.tracker.data.database.AttendanceDao
import com.attendance.tracker.data.database.ScheduleDao
import com.attendance.tracker.data.database.SubjectDao
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.datesOverlap
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.share.SharedTimetable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate

class AttendanceRepository(
    private val subjectDao: SubjectDao,
    private val attendanceDao: AttendanceDao,
    private val scheduleDao: ScheduleDao,
    private val database: androidx.room.RoomDatabase? = null
) {
    companion object {
        val writeMutex = Mutex()
    }

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return writeMutex.withLock {
            if (database != null) {
                database.withTransaction { block() }
            } else {
                block()
            }
        }
    }
    // Subject operations
    val allSubjects: Flow<List<Subject>> = subjectDao.getAllSubjects()
    val topLevelSubjects: Flow<List<Subject>> = subjectDao.getTopLevelSubjects()
    val actualSubjects: Flow<List<Subject>> = subjectDao.getActualSubjects()

    suspend fun getSubjectById(id: Long): Subject? = subjectDao.getSubjectById(id)
    
    fun getSubSubjects(parentId: Long): Flow<List<Subject>> = subjectDao.getSubSubjects(parentId)

    suspend fun insertSubject(subject: Subject): Long = subjectDao.insertSubject(subject)

    suspend fun updateSubject(subject: Subject) = subjectDao.updateSubject(subject)

    suspend fun deleteSubject(subject: Subject) = runInTransaction {
        if (subject.isFolder) {
            // Promote any sub-subjects to top-level rather than orphaning them under a
            // now-deleted folder id — preserves their attendance history and keeps them
            // reachable from the Subjects screen instead of vanishing permanently.
            // The folder name is folded into each child's name ("PSM / Lectures") so they
            // stay distinguishable once the folder is gone.
            subjectDao.getAllSubjectsOnce().filter { it.parentSubjectId == subject.id }.forEach {
                subjectDao.updateSubject(it.copy(name = "${subject.name} / ${it.name}"))
            }
            subjectDao.clearParentForSubjects(subject.id)
        }
        subjectDao.deleteSubject(subject)
    }

    suspend fun markPresent(subjectId: Long, date: LocalDate, count: Int = 1) =
        setDayStatus(subjectId, date, AttendanceStatus.PRESENT, targetCount = count, isManual = true)

    suspend fun markAbsent(subjectId: Long, date: LocalDate, count: Int = 1) =
        setDayStatus(subjectId, date, AttendanceStatus.ABSENT, targetCount = count, isManual = true)

    suspend fun markNoClass(subjectId: Long, date: LocalDate) =
        setDayStatus(subjectId, date, AttendanceStatus.NO_CLASS, isManual = true)

    /**
     * Sets the whole day to [status]. A day that already holds lectures (including a mixed
     * present+absent day) keeps its total lecture count, all switched to [status]; marking
     * a day with the status it already has is a no-op so repeated taps never add a lecture.
     * Subject totals are adjusted by the difference.
     * When marking a new day, [targetCount] specifies how many scheduled lectures were held.
     * [isManual] should be true when the user explicitly sets the status (protects the record
     * from being overridden by time-off / vacation syncs).
     */
    private suspend fun setDayStatus(
        subjectId: Long,
        date: LocalDate,
        status: AttendanceStatus,
        targetCount: Int = 1,
        isManual: Boolean = false
    ) = runInTransaction {
        val existing = attendanceDao.getAttendanceRecord(subjectId, date)
        if (existing != null && existing.status == status && existing.otherCount == 0 && existing.count >= maxOf(targetCount, 1)) return@runInTransaction
        val total = if (existing == null) maxOf(targetCount, 1) else maxOf(existing.count + existing.otherCount, targetCount)
        val newPresent = if (status == AttendanceStatus.PRESENT) total else 0
        val newAbsent = if (status == AttendanceStatus.ABSENT) total else 0
        if (existing == null) {
            when (status) {
                AttendanceStatus.PRESENT -> {
                    if (total == 1) {
                        subjectDao.markPresent(subjectId)
                    } else {
                        getSubjectById(subjectId)?.let {
                            subjectDao.updateAttendanceCounts(
                                subjectId,
                                it.presentLectures + total,
                                it.absentLectures
                            )
                        }
                    }
                }
                AttendanceStatus.ABSENT -> {
                    if (total == 1) {
                        subjectDao.markAbsent(subjectId)
                    } else {
                        getSubjectById(subjectId)?.let {
                            subjectDao.updateAttendanceCounts(
                                subjectId,
                                it.presentLectures,
                                it.absentLectures + total
                            )
                        }
                    }
                }
                AttendanceStatus.NO_CLASS -> {}
            }
        } else {
            getSubjectById(subjectId)?.let {
                subjectDao.updateAttendanceCounts(
                    subjectId,
                    maxOf(0, it.presentLectures + newPresent - existing.presentCount),
                    maxOf(0, it.absentLectures + newAbsent - existing.absentCount)
                )
            }
        }
        attendanceDao.insertAttendance(
            AttendanceRecord(subjectId = subjectId, date = date, status = status, count = total,
                // Preserve isManual: once a record is manually set, keep it manual even on
                // subsequent updates so it's never silently overridden by time-off.
                isManual = isManual || (existing?.isManual == true))
        )
    }

    /** Removes the day's record (any status, including No Class); subject totals move by what it held. */
    suspend fun clearDay(subjectId: Long, date: LocalDate) = runInTransaction {
        val existing = attendanceDao.getAttendanceRecord(subjectId, date) ?: return@runInTransaction
        getSubjectById(subjectId)?.let {
            subjectDao.updateAttendanceCounts(
                subjectId,
                maxOf(0, it.presentLectures - existing.presentCount),
                maxOf(0, it.absentLectures - existing.absentCount)
            )
        }
        attendanceDao.deleteAttendanceForSubjectOnDate(subjectId, date)
    }

    /**
     * Sets the exact number of present and absent lectures on [date] (the per-lecture editor).
     * Both zero removes the day's record. Subject totals move by the difference.
     */
    suspend fun setDayCounts(subjectId: Long, date: LocalDate, present: Int, absent: Int) = runInTransaction {
        val existing = attendanceDao.getAttendanceRecord(subjectId, date)
        val oldPresent = existing?.presentCount ?: 0
        val oldAbsent = existing?.absentCount ?: 0
        if (present == oldPresent && absent == oldAbsent) return@runInTransaction
        getSubjectById(subjectId)?.let {
            subjectDao.updateAttendanceCounts(
                subjectId,
                maxOf(0, it.presentLectures + present - oldPresent),
                maxOf(0, it.absentLectures + absent - oldAbsent)
            )
        }
        when {
            present + absent == 0 -> attendanceDao.deleteAttendanceForSubjectOnDate(subjectId, date)
            present >= absent -> attendanceDao.insertAttendance(
                AttendanceRecord(subjectId = subjectId, date = date, status = AttendanceStatus.PRESENT, count = present, otherCount = absent, isManual = true)
            )
            else -> attendanceDao.insertAttendance(
                AttendanceRecord(subjectId = subjectId, date = date, status = AttendanceStatus.ABSENT, count = absent, otherCount = present, isManual = true)
            )
        }
    }

    /**
     * Adds one more lecture on [date] with [status] (PRESENT or ABSENT), alongside whatever the
     * day already holds: present and absent lectures can coexist on the same date. A day with
     * no record, or marked No Class, just gets marked [status].
     */
    suspend fun addExtraLecture(subjectId: Long, date: LocalDate, status: AttendanceStatus) = runInTransaction {
        if (status == AttendanceStatus.NO_CLASS) return@runInTransaction
        val record = attendanceDao.getAttendanceRecord(subjectId, date)
        when (status) {
            AttendanceStatus.PRESENT -> subjectDao.markPresent(subjectId)
            else -> subjectDao.markAbsent(subjectId)
        }
        if (record == null) {
            attendanceDao.insertAttendance(
                AttendanceRecord(
                    subjectId = subjectId,
                    date = date,
                    status = status,
                    count = 1,
                    otherCount = 0,
                    isManual = true
                )
            )
        } else if (record.status == AttendanceStatus.NO_CLASS) {
            attendanceDao.insertAttendance(
                record.copy(
                    status = status,
                    count = 1,
                    otherCount = 0,
                    isManual = true
                )
            )
        } else {
            attendanceDao.insertAttendance(
                if (record.status == status) record.copy(count = record.count + 1, isManual = true)
                else record.copy(otherCount = record.otherCount + 1, isManual = true)
            )
        }
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

    suspend fun getAttendanceInRangeOnce(startDate: LocalDate, endDate: LocalDate): List<AttendanceRecord> =
        attendanceDao.getAttendanceInRangeOnce(startDate, endDate)

    /**
     * Applies Time Off (Holiday, Leave/Bunk, or Reset) across [startDate]..[endDate].
     * Targets subjects occurring on each date based on the schedule (or all actual subjects if unscheduled).
     * Returns total count of affected lecture slots.
     */
    suspend fun applyTimeOff(
        startDate: LocalDate,
        endDate: LocalDate,
        status: AttendanceStatus?,
        subjectIds: Set<Long>? = null,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (endDate.isBefore(startDate)) return 0
        val scheduleList = scheduleDao.getAllScheduleEntriesOnce()
        val actualSubs = subjectDao.getAllSubjectsOnce().filter { !it.isFolder }
        val allSubIds = actualSubs.map { it.id }.toSet()
        val targetSubjectIds = subjectIds ?: allSubIds

        // Never mark future dates in advance; only mark dates up to today in database.
        // When resetting (status == null), allow clearing up to endDate.
        val effectiveEnd = if (status != null && endDate.isAfter(today)) today else endDate

        var affectedSlots = 0
        var curr = startDate
        if (!effectiveEnd.isBefore(startDate)) {
            while (!curr.isAfter(effectiveEnd)) {
                val date = curr
                if (scheduleList.isNotEmpty()) {
                    val dayEntries = scheduleList.filter { it.occursOn(date) && it.subjectId in targetSubjectIds }
                    val subjectCounts = dayEntries.groupBy { it.subjectId }.mapValues { it.value.size }
                    for ((subId, count) in subjectCounts) {
                        if (status != null) {
                            // Don't override a record the user manually set
                            val existing = attendanceDao.getAttendanceRecord(subId, date)
                            if (existing?.isManual == true) continue
                            setDayStatus(subId, date, status, targetCount = count)
                        } else {
                            clearDay(subId, date)
                        }
                        affectedSlots += count
                    }
                    if (status == null) {
                        val existingForDate = attendanceDao.getAttendanceRecordsForSubjectsOnDateOnce(targetSubjectIds.toList(), date)
                        for (rec in existingForDate) {
                            if (!subjectCounts.containsKey(rec.subjectId)) {
                                clearDay(rec.subjectId, date)
                                affectedSlots += (rec.presentCount + rec.absentCount).coerceAtLeast(1)
                            }
                        }
                    }
                } else {
                    if (status != null) {
                        if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                            for (sub in actualSubs) {
                                if (sub.id in targetSubjectIds) {
                                    // Don't override a record the user manually set
                                    val existing = attendanceDao.getAttendanceRecord(sub.id, date)
                                    if (existing?.isManual == true) continue
                                    setDayStatus(sub.id, date, status, targetCount = 1)
                                    affectedSlots += 1
                                }
                            }
                        }
                    } else {
                        val existingForDate = attendanceDao.getAttendanceRecordsForSubjectsOnDateOnce(targetSubjectIds.toList(), date)
                        for (rec in existingForDate) {
                            clearDay(rec.subjectId, date)
                            affectedSlots += (rec.presentCount + rec.absentCount).coerceAtLeast(1)
                        }
                    }
                }
                curr = curr.plusDays(1)
            }
        }

        return affectedSlots
    }

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

    suspend fun insertScheduleEntries(entries: List<ScheduleEntry>) =
        scheduleDao.insertScheduleEntries(entries)

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
            scheduleDao.deleteScheduleEntry(existing)
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

    suspend fun importSharedTimetable(
        shared: SharedTimetable,
        replaceSchedule: Boolean
    ): Pair<Int, Int> {
        val existingSubjects = getAllSubjectsOnce()
        val subjectMap = existingSubjects.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val indexToSubjectId = mutableMapOf<Int, Long>()

        var newSubjectsCount = 0
        shared.subjects.forEachIndexed { index, sharedSubject ->
            val key = sharedSubject.name.trim().lowercase()
            val existing = subjectMap[key]
            val subjectId = if (existing != null) {
                existing.id
            } else {
                val newId = insertSubject(
                    Subject(
                        name = sharedSubject.name.trim(),
                        requiredAttendance = sharedSubject.requiredAttendance
                    )
                )
                newSubjectsCount++
                val created = getSubjectById(newId)
                if (created != null) subjectMap[key] = created
                newId
            }
            indexToSubjectId[index] = subjectId
        }

        if (replaceSchedule) {
            scheduleDao.deleteAllScheduleEntries()
        }

        val entriesToInsert = shared.lectures.mapNotNull { lecture ->
            val subId = indexToSubjectId[lecture.subjectIndex] ?: return@mapNotNull null
            ScheduleEntry(
                subjectId = subId,
                dayOfWeek = lecture.dayOfWeek,
                startTime = lecture.startTime,
                endTime = lecture.endTime,
                startDate = lecture.startDate,
                endDate = lecture.endDate
            )
        }

        scheduleDao.insertScheduleEntries(entriesToInsert)
        return Pair(newSubjectsCount, entriesToInsert.size)
    }

    data class CsvImportSummary(val subjectsAdded: Int, val subjectsExisting: Int, val slotsAdded: Int)

    /**
     * Adds the parsed CSV rows. Subjects are matched by name (case-insensitive, top-level
     * only): an existing subject is left completely untouched, so importing the same file
     * twice changes nothing.
     * Timetable slots may carry a date range: an existing subject keeps its slots for any
     * period they already cover, but gains slots for periods it has none for (e.g. next
     * semester's timetable).
     * With [replaceExisting] an existing subject instead takes the file's target (if given)
     * and replaces its slots that overlap the file's dates; attendance is never touched.
     */
    suspend fun importCsv(
        data: com.attendance.tracker.backup.CsvImporter.Result,
        replaceExisting: Boolean = false
    ): CsvImportSummary {
        val current = subjectDao.getAllSubjectsOnce()
            .filter { it.parentSubjectId == null && !it.isFolder }
            .associateBy { it.name.lowercase() }
        val idByName = current.mapValues { it.value.id }.toMutableMap()
        var added = 0
        var existing = 0
        for (row in data.subjects) {
            val old = current[row.name.lowercase()]
            if (old != null) {
                existing++
                if (replaceExisting) {
                    row.requiredAttendance?.let { subjectDao.updateSubject(old.copy(requiredAttendance = it)) }
                }
                continue
            }
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
        val before = scheduleDao.getAllScheduleEntriesOnce()
        var slotsAdded = 0
        for (slot in data.slots) {
            val subjectId = idByName[slot.subjectName.lowercase()] ?: continue
            if (slot.subjectName.lowercase() in current) {
                // Timetable slots of the same subject whose dates overlap this one
                val clashing = before.filter {
                    it.subjectId == subjectId &&
                        datesOverlap(it.startDate, it.endDate, slot.startDate, slot.endDate)
                }
                if (!replaceExisting) { if (clashing.isNotEmpty()) continue }  // that slot is already covered
                else clashing.forEach { scheduleDao.deleteScheduleEntry(it) }
            }
            scheduleDao.insertScheduleEntry(
                ScheduleEntry(
                    subjectId = subjectId, dayOfWeek = slot.day, startTime = slot.start, endTime = slot.end,
                    startDate = slot.startDate, endDate = slot.endDate
                )
            )
            slotsAdded++
        }
        return CsvImportSummary(added, existing, slotsAdded)
    }
}
