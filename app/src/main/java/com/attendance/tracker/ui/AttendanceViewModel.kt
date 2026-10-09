package com.attendance.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.attendance.tracker.backup.BackupManager
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import com.attendance.tracker.widget.refreshAllWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AttendanceDatabase.getDatabase(application)
    private val repository = AttendanceRepository(
        database.subjectDao(),
        database.attendanceDao(),
        database.scheduleDao()
    )
    private val themeRepository = com.attendance.tracker.data.repository.ThemePreferenceRepository(
        database.themePreferenceDao()
    )

    // UI State
    val subjects: StateFlow<List<Subject>> = repository.actualSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val allSubjectsIncludingFolders: StateFlow<List<Subject>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjectsMap: StateFlow<Map<Long, Subject>> = allSubjectsIncludingFolders
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val scheduleEntries: StateFlow<List<ScheduleEntry>> = repository.allScheduleEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _todayAttendance = MutableStateFlow<Map<Long, AttendanceRecord>>(emptyMap())
    val todayAttendance: StateFlow<Map<Long, AttendanceRecord>> = _todayAttendance.asStateFlow()

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords.asStateFlow()

    // Job handles for cancelling stale collectors before starting new ones
    private var todayAttendanceJob: Job? = null
    private var monthAttendanceJob: Job? = null

    // Guards markAttendance/clearAttendance against being re-entered for the same
    // subject+date before the previous call's read-modify-write on the attendance
    // record has finished. Without this, two rapid clicks (e.g. a double tap that
    // slips through before the UI state reflects the first mark) could both read the
    // record as "not yet present" and each insert/increment it, leaving a bogus
    // count of 2 after a single intended tap.
    private val pendingAttendanceOps = mutableSetOf<String>()

    // Serialises every attendance read-modify-write (mark/clear/edit). They suspend
    // between reading a subject's counts and writing them back, so without this two edits
    // racing a mark on the same subject could lose one of the two updates.
    private val attendanceMutex = Mutex()

    // Date todayAttendance is currently bound to; see refreshToday().
    private var todayAttendanceDate: LocalDate? = null

    // Theme preferences
    val themePreference = themeRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Load today's attendance when ViewModel is created
        loadAttendanceForDate(LocalDate.now())
        // Initialize theme preferences
        viewModelScope.launch {
            themeRepository.initializeDefaultIfNeeded()
        }
        // Home-screen widgets read the database but are never told it changed; re-render
        // them whenever subject totals, the timetable or today's marks change.
        viewModelScope.launch {
            combine(repository.allSubjects, repository.allScheduleEntries, _todayAttendance) { _, _, _ -> }
                .drop(1)
                .collectLatest {
                    try {
                        refreshAllWidgets(getApplication<Application>())
                    } catch (e: Exception) {
                        // A widget host failure must not take the app down with it
                    }
                }
        }
    }

    /** Re-binds todayAttendance to the current date if the day has rolled over since it was loaded. */
    fun refreshToday() {
        val today = LocalDate.now()
        if (todayAttendanceDate != today) loadAttendanceForDate(today)
    }

    // todayAttendance always tracks one date (today, for the Home screen). The Room flow
    // re-emits on every write, so callers never need to reload it after marking.
    private fun loadAttendanceForDate(date: LocalDate) {
        todayAttendanceDate = date
        todayAttendanceJob?.cancel()
        todayAttendanceJob = viewModelScope.launch {
            repository.getAttendanceForDate(date).collect { records ->
                _todayAttendance.value = records.associateBy { it.subjectId }
            }
        }
    }

    fun loadAttendanceForMonth(yearMonth: YearMonth) {
        monthAttendanceJob?.cancel()
        monthAttendanceJob = viewModelScope.launch {
            val startDate = yearMonth.atDay(1)
            val endDate = yearMonth.atEndOfMonth()
            repository.getAttendanceInRange(startDate, endDate).collect { records ->
                _attendanceRecords.value = records
            }
        }
    }

    fun setSelectedDate(date: LocalDate) {
        // Only the calendar's selection: todayAttendance must stay on today for Home,
        // which always marks LocalDate.now().
        _selectedDate.value = date
    }

    fun setSelectedMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
        loadAttendanceForMonth(yearMonth)
    }

    // Subject operations
    fun addSubject(name: String, requiredAttendance: Int = 75) {
        viewModelScope.launch {
            repository.insertSubject(
                Subject(name = name, requiredAttendance = requiredAttendance)
            )
        }
    }
    
    fun addSubjectFolder(name: String) {
        viewModelScope.launch {
            repository.insertSubject(
                Subject(name = name, isFolder = true)
            )
        }
    }
    
    fun addSubSubject(name: String, parentSubjectId: Long, requiredAttendance: Int = 75) {
        viewModelScope.launch {
            repository.insertSubject(
                Subject(
                    name = name, 
                    requiredAttendance = requiredAttendance,
                    parentSubjectId = parentSubjectId,
                    isFolder = false
                )
            )
        }
    }
    
    fun getSubSubjects(parentId: Long): Flow<List<Subject>> {
        return repository.getSubSubjects(parentId)
    }

    fun updateSubject(subject: Subject) {
        viewModelScope.launch {
            repository.updateSubject(subject)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    // Attendance operations
    fun markAttendance(subjectId: Long, status: AttendanceStatus, date: LocalDate = LocalDate.now()) {
        val opKey = "$subjectId|$date"
        // Ignore this call if a mark/clear for the same subject+date is still in flight.
        if (!pendingAttendanceOps.add(opKey)) return
        viewModelScope.launch {
            try {
                attendanceMutex.withLock { markAttendanceLocked(subjectId, status, date) }
            } finally {
                pendingAttendanceOps.remove(opKey)
            }
        }
    }

    private suspend fun markAttendanceLocked(subjectId: Long, status: AttendanceStatus, date: LocalDate) {
        // Re-tapping the current status is a no-op (the repository ignores it too)
        if (repository.getSubjectById(subjectId) == null) return
        when (status) {
            AttendanceStatus.PRESENT -> repository.markPresent(subjectId, date)
            AttendanceStatus.ABSENT -> repository.markAbsent(subjectId, date)
            AttendanceStatus.NO_CLASS -> repository.markNoClass(subjectId, date)
        }
    }

    /**
     * Records an extra lecture on [date] with [status], on top of whatever the day already
     * holds: a day can have both present and absent lectures (e.g. attended one, missed
     * another). On a day with no mark yet it behaves like a normal mark.
     */
    fun addExtraClass(subjectId: Long, status: AttendanceStatus, date: LocalDate = LocalDate.now()) {
        if (status == AttendanceStatus.NO_CLASS) {
            markAttendance(subjectId, status, date)
            return
        }
        val opKey = "$subjectId|$date"
        if (!pendingAttendanceOps.add(opKey)) return
        viewModelScope.launch {
            try {
                attendanceMutex.withLock {
                    // addExtraLecture marks the day normally when it has no lectures yet
                    if (repository.getSubjectById(subjectId) != null) {
                        repository.addExtraLecture(subjectId, date, status)
                    }
                }
            } finally {
                pendingAttendanceOps.remove(opKey)
            }
        }
    }

    /** Per-lecture editor: sets exactly [present] present and [absent] absent lectures on [date]. */
    fun setDayCounts(subjectId: Long, date: LocalDate, present: Int, absent: Int) {
        val opKey = "$subjectId|$date"
        if (!pendingAttendanceOps.add(opKey)) return
        viewModelScope.launch {
            try {
                attendanceMutex.withLock {
                    repository.setDayCounts(subjectId, date, present.coerceAtLeast(0), absent.coerceAtLeast(0))
                }
            } finally {
                pendingAttendanceOps.remove(opKey)
            }
        }
    }

    fun clearAttendance(subjectId: Long, date: LocalDate = LocalDate.now()) {
        val opKey = "$subjectId|$date"
        // Ignore this call if a mark/clear for the same subject+date is still in flight.
        if (!pendingAttendanceOps.add(opKey)) return
        viewModelScope.launch {
            try {
                attendanceMutex.withLock { repository.clearDay(subjectId, date) }
            } finally {
                pendingAttendanceOps.remove(opKey)
            }
        }
    }

    // Schedule operations
    fun removeScheduleEntry(entry: ScheduleEntry) {
        viewModelScope.launch {
            repository.deleteScheduleEntry(entry)
        }
    }

    /** Creates a new timed lecture slot (used by the weekly calendar view). */
    fun addLectureSlot(subjectId: Long, dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime) {
        viewModelScope.launch {
            repository.insertScheduleEntry(
                ScheduleEntry(
                    subjectId = subjectId,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }

    /** Updates an existing lecture slot's subject/day/time (used by the weekly calendar view). */
    fun updateLectureSlot(
        entry: ScheduleEntry,
        subjectId: Long,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime
    ) {
        viewModelScope.launch {
            repository.updateScheduleEntry(
                entry.copy(
                    subjectId = subjectId,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }

    fun getScheduleForSubject(subjectId: Long): Flow<List<ScheduleEntry>> {
        return repository.getScheduleForSubject(subjectId)
    }

    fun getScheduleForDay(dayOfWeek: DayOfWeek): Flow<List<ScheduleEntry>> {
        return repository.getScheduleForDay(dayOfWeek)
    }

    // Theme operations
    fun updateThemeMode(themeMode: com.attendance.tracker.data.model.ThemeMode) {
        viewModelScope.launch {
            themeRepository.updateThemeMode(themeMode)
        }
    }

    // -----------------------------------------------------------------------
    // Backup / Restore
    // -----------------------------------------------------------------------

    enum class BackupRestoreStatus { IDLE, IN_PROGRESS, SUCCESS, ERROR }

    private val _backupRestoreStatus = MutableStateFlow(BackupRestoreStatus.IDLE)
    val backupRestoreStatus: StateFlow<BackupRestoreStatus> = _backupRestoreStatus.asStateFlow()

    private val _backupRestoreMessage = MutableStateFlow("")
    val backupRestoreMessage: StateFlow<String> = _backupRestoreMessage.asStateFlow()

    /** Collect all data and return a JSON string ready to be written to a file. */
    suspend fun createJsonBackup(): String {
        val subjects = repository.getAllSubjectsOnce()
        val records = repository.getAllAttendanceRecordsOnce()
        val schedule = repository.getAllScheduleEntriesOnce()
        return BackupManager.exportToJson(subjects, records, schedule)
    }

    /** Collect attendance data and return a CSV string ready to be written to a file. */
    suspend fun createCsvBackup(): String {
        val subjects = repository.getAllSubjectsOnce()
        val records = repository.getAllAttendanceRecordsOnce()
        return BackupManager.exportToCsv(subjects, records)
    }

    /** Restore from a JSON backup string. Clears existing data first. */
    fun restoreFromJson(json: String) {
        viewModelScope.launch {
            _backupRestoreStatus.value = BackupRestoreStatus.IN_PROGRESS
            val data = BackupManager.parseJson(json)
            if (data == null) {
                _backupRestoreStatus.value = BackupRestoreStatus.ERROR
                _backupRestoreMessage.value = "Invalid backup file. Please select a valid JSON backup."
                return@launch
            }
            try {
                // One transaction: if any insert fails (e.g. a record pointing at a subject
                // missing from the file) the wipe is rolled back instead of losing all data.
                database.withTransaction {
                    repository.restoreData(data.subjects, data.attendanceRecords, data.scheduleEntries)
                }
                _backupRestoreStatus.value = BackupRestoreStatus.SUCCESS
                _backupRestoreMessage.value =
                    "Restore complete: ${data.subjects.size} subjects, " +
                    "${data.attendanceRecords.size} attendance records, " +
                    "${data.scheduleEntries.size} schedule entries."
            } catch (e: Exception) {
                _backupRestoreStatus.value = BackupRestoreStatus.ERROR
                _backupRestoreMessage.value = "Restore failed: ${e.message}"
            }
        }
    }

    /** Parses an AI chatbot reply for the import preview; writes nothing. */
    fun previewAiImport(text: String): com.attendance.tracker.backup.CsvImporter.Result =
        com.attendance.tracker.backup.AiImport.parse(text)

    /** Parses a CSV for the import preview; writes nothing. */
    fun previewCsvImport(text: String): com.attendance.tracker.backup.CsvImporter.Result =
        com.attendance.tracker.backup.CsvImporter.parse(text)

    /** Writes a previously previewed (error-free) CSV import. */
    fun importCsv(data: com.attendance.tracker.backup.CsvImporter.Result, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            _backupRestoreStatus.value = BackupRestoreStatus.IN_PROGRESS
            try {
                val r = database.withTransaction { repository.importCsv(data, replaceExisting) }
                _backupRestoreStatus.value = BackupRestoreStatus.SUCCESS
                _backupRestoreMessage.value =
                    "Import complete: ${r.subjectsAdded} subjects added" +
                    (if (r.subjectsExisting > 0) " (${r.subjectsExisting} already existed, ${if (replaceExisting) "updated" else "kept as they were"})" else "") +
                    ", ${r.slotsAdded} timetable slots added."
            } catch (e: Exception) {
                _backupRestoreStatus.value = BackupRestoreStatus.ERROR
                _backupRestoreMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun resetBackupRestoreStatus() {
        _backupRestoreStatus.value = BackupRestoreStatus.IDLE
        _backupRestoreMessage.value = ""
    }
}
