package com.attendance.tracker.data.repository

import com.attendance.tracker.data.database.AttendanceDao
import com.attendance.tracker.data.database.ScheduleDao
import com.attendance.tracker.data.database.SubjectDao
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * In-memory fakes for the Room DAOs so [AttendanceRepository]'s business logic can be
 * exercised without a real database.
 */
private class FakeSubjectDao : SubjectDao {
    val subjects = mutableMapOf<Long, Subject>()

    override fun getAllSubjects(): Flow<List<Subject>> = MutableStateFlow(subjects.values.toList())
    override suspend fun getSubjectById(id: Long): Subject? = subjects[id]
    override suspend fun getSubjectsByIdsOnce(ids: List<Long>): List<Subject> =
        subjects.values.filter { it.id in ids }
    override fun getTopLevelSubjects(): Flow<List<Subject>> =
        MutableStateFlow(subjects.values.filter { it.parentSubjectId == null })
    override fun getSubSubjects(parentId: Long): Flow<List<Subject>> =
        MutableStateFlow(subjects.values.filter { it.parentSubjectId == parentId })
    override fun getActualSubjects(): Flow<List<Subject>> =
        MutableStateFlow(subjects.values.filter { !it.isFolder })

    override suspend fun insertSubject(subject: Subject): Long {
        subjects[subject.id] = subject
        return subject.id
    }

    override suspend fun updateSubject(subject: Subject) {
        subjects[subject.id] = subject
    }

    override suspend fun deleteSubject(subject: Subject) {
        subjects.remove(subject.id)
    }

    override suspend fun clearParentForSubjects(parentId: Long) {
        subjects.replaceAll { _, s -> if (s.parentSubjectId == parentId) s.copy(parentSubjectId = null) else s }
    }

    override suspend fun markPresent(subjectId: Long) {
        val s = subjects[subjectId] ?: return
        subjects[subjectId] = s.copy(presentLectures = s.presentLectures + 1, totalLectures = s.totalLectures + 1)
    }

    override suspend fun markAbsent(subjectId: Long) {
        val s = subjects[subjectId] ?: return
        subjects[subjectId] = s.copy(absentLectures = s.absentLectures + 1, totalLectures = s.totalLectures + 1)
    }

    override suspend fun updateAttendanceCounts(subjectId: Long, present: Int, absent: Int) {
        val s = subjects[subjectId] ?: return
        subjects[subjectId] = s.copy(presentLectures = present, absentLectures = absent, totalLectures = present + absent)
    }

    override suspend fun getAllSubjectsOnce(): List<Subject> = subjects.values.sortedBy { it.id }
    override suspend fun insertSubjects(subjects: List<Subject>) {
        subjects.forEach { this.subjects[it.id] = it }
    }
    override suspend fun deleteAllSubjects() {
        subjects.clear()
    }
}

private class FakeAttendanceDao : AttendanceDao {
    val records = mutableMapOf<Pair<Long, LocalDate>, AttendanceRecord>()

    override fun getAttendanceForSubject(subjectId: Long): Flow<List<AttendanceRecord>> =
        MutableStateFlow(records.values.filter { it.subjectId == subjectId })

    override fun getAttendanceForDate(date: LocalDate): Flow<List<AttendanceRecord>> =
        MutableStateFlow(records.values.filter { it.date == date })

    override suspend fun getAttendanceRecord(subjectId: Long, date: LocalDate): AttendanceRecord? =
        records[subjectId to date]

    override suspend fun getAttendanceRecordsForSubjectsOnDateOnce(
        subjectIds: List<Long>,
        date: LocalDate
    ): List<AttendanceRecord> = records.values.filter { it.subjectId in subjectIds && it.date == date }

    override fun getAttendanceInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<AttendanceRecord>> =
        MutableStateFlow(records.values.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) })

    override suspend fun insertAttendance(record: AttendanceRecord): Long {
        records[record.subjectId to record.date] = record
        return record.id
    }

    override suspend fun updateAttendance(record: AttendanceRecord) {
        records[record.subjectId to record.date] = record
    }

    override suspend fun deleteAttendance(record: AttendanceRecord) {
        records.remove(record.subjectId to record.date)
    }

    override suspend fun deleteAttendanceForSubjectOnDate(subjectId: Long, date: LocalDate) {
        records.remove(subjectId to date)
    }

    override suspend fun getAllAttendanceRecordsOnce(): List<AttendanceRecord> = records.values.sortedBy { it.date }
    override suspend fun insertAttendanceRecords(records: List<AttendanceRecord>) {
        records.forEach { this.records[it.subjectId to it.date] = it }
    }
    override suspend fun deleteAllAttendance() {
        records.clear()
    }
}

private class FakeScheduleDao : ScheduleDao {
    val entries = mutableListOf<ScheduleEntry>()

    override fun getAllScheduleEntries(): Flow<List<ScheduleEntry>> = MutableStateFlow(entries.toList())
    override fun getScheduleForDay(dayOfWeek: DayOfWeek): Flow<List<ScheduleEntry>> =
        MutableStateFlow(entries.filter { it.dayOfWeek == dayOfWeek })
    override suspend fun getScheduleForDayOnce(dayOfWeek: DayOfWeek): List<ScheduleEntry> =
        entries.filter { it.dayOfWeek == dayOfWeek }
    override fun getScheduleForSubject(subjectId: Long): Flow<List<ScheduleEntry>> =
        MutableStateFlow(entries.filter { it.subjectId == subjectId })

    override suspend fun insertScheduleEntry(entry: ScheduleEntry): Long {
        entries.add(entry)
        return entries.size.toLong()
    }

    override suspend fun updateScheduleEntry(entry: ScheduleEntry) {}
    override suspend fun deleteScheduleEntry(entry: ScheduleEntry) {
        entries.remove(entry)
    }
    override suspend fun deleteScheduleForSubject(subjectId: Long) {
        entries.removeAll { it.subjectId == subjectId }
    }
    override suspend fun deleteScheduleEntry(subjectId: Long, dayOfWeek: DayOfWeek) {
        entries.removeAll { it.subjectId == subjectId && it.dayOfWeek == dayOfWeek }
    }
    override suspend fun getAllScheduleEntriesOnce(): List<ScheduleEntry> = entries.toList()
    override suspend fun insertScheduleEntries(entries: List<ScheduleEntry>) {
        this.entries.addAll(entries)
    }
    override suspend fun deleteAllScheduleEntries() {
        entries.clear()
    }
}

class AttendanceRepositoryTest {

    private lateinit var subjectDao: FakeSubjectDao
    private lateinit var attendanceDao: FakeAttendanceDao
    private lateinit var repository: AttendanceRepository
    private val date = LocalDate.of(2024, 1, 15)

    @Before
    fun setUp() {
        subjectDao = FakeSubjectDao()
        attendanceDao = FakeAttendanceDao()
        repository = AttendanceRepository(subjectDao, attendanceDao, FakeScheduleDao())
    }

    private suspend fun addSubject(id: Long = 1L) {
        subjectDao.insertSubject(Subject(id = id, name = "Physics"))
    }

    @Test
    fun `repeated present or absent taps on the same day never increment the count`() = runBlocking {
        addSubject()
        repeat(3) { repository.markPresent(1L, date) }
        assertEquals(1, repository.getAttendanceRecord(1L, date)?.count)
        repeat(3) { repository.markAbsent(1L, date) }
        val subject = repository.getSubjectById(1L)!!
        val record = repository.getAttendanceRecord(1L, date)
        assertEquals(AttendanceStatus.ABSENT, record?.status)
        assertEquals(1, record?.count)
        assertEquals(0, subject.presentLectures)
        assertEquals(1, subject.absentLectures)
        assertEquals(1, subject.totalLectures)
    }

    @Test
    fun `repeated no class taps keep count at 1 and switching carries it over`() = runBlocking {
        addSubject()
        repeat(3) { repository.markNoClass(1L, date) }
        assertEquals(1, repository.getAttendanceRecord(1L, date)?.count)

        repository.markAbsent(1L, date)
        repository.markPresent(1L, date)
        val subject = repository.getSubjectById(1L)!!
        assertEquals(1, subject.presentLectures)
        assertEquals(0, subject.absentLectures)
        assertEquals(1, repository.getAttendanceRecord(1L, date)?.count)
    }

    @Test
    fun `deleting a folder promotes its sub-subjects to top-level instead of orphaning them`() = runBlocking {
        val folder = Subject(id = 10L, name = "Pathology", isFolder = true)
        subjectDao.insertSubject(folder)
        subjectDao.insertSubject(Subject(id = 11L, name = "Lecture", parentSubjectId = 10L))
        subjectDao.insertSubject(Subject(id = 12L, name = "Practical", parentSubjectId = 10L))

        repository.deleteSubject(folder)

        assertNull(repository.getSubjectById(10L))
        val lecture = repository.getSubjectById(11L)!!
        val practical = repository.getSubjectById(12L)!!
        // Both children must become reachable as top-level subjects again — otherwise,
        // with their parentSubjectId still pointing at the deleted folder, they'd be
        // invisible both at top-level and inside the (now-nonexistent) folder.
        assertNull(lecture.parentSubjectId)
        assertNull(practical.parentSubjectId)
    }
}
