package com.attendance.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.tracker.backup.BackupManager
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    // Undo/Redo Manager
    private val undoRedoManager = UndoRedoManager()
    
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

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
    }

    fun loadAttendanceForDate(date: LocalDate) {
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
        _selectedDate.value = date
        loadAttendanceForDate(date)
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
                // Get current state before marking
                val subject = repository.getSubjectById(subjectId)
                val oldRecord = repository.getAttendanceRecord(subjectId, date)

                // Re-tapping the current status is a no-op: nothing to mark or undo
                if (subject != null && oldRecord?.status != status) {
                    // Mark the new status first to get the new count
                    when (status) {
                        AttendanceStatus.PRESENT -> repository.markPresent(subjectId, date)
                        AttendanceStatus.ABSENT -> repository.markAbsent(subjectId, date)
                        AttendanceStatus.NO_CLASS -> repository.markNoClass(subjectId, date)
                    }

                    // Get the updated record to capture the new count
                    val newRecord = repository.getAttendanceRecord(subjectId, date)
                    val updatedSubject = repository.getSubjectById(subjectId)

                    if (newRecord != null && updatedSubject != null) {
                        // Record action for undo/redo
                        val action = AttendanceAction(
                            subjectId = subjectId,
                            date = date,
                            oldStatus = oldRecord?.status,
                            oldCount = oldRecord?.count ?: 0,
                            newStatus = newRecord.status,
                            newCount = newRecord.count,
                            oldPresentCount = subject.presentLectures,
                            oldAbsentCount = subject.absentLectures
                        )
                        undoRedoManager.recordAction(action)
                        updateUndoRedoState()
                    }

                    loadAttendanceForDate(date)
                }
            } finally {
                pendingAttendanceOps.remove(opKey)
            }
        }
    }

    /**
     * Records an extra lecture on [date]. If the day already has the same status the lecture
     * count goes up by one; otherwise it behaves like a normal mark.
     */
    fun addExtraClass(subjectId: Long, status: AttendanceStatus, date: LocalDate = LocalDate.now()) {
        val opKey = "$subjectId|$date"
        if (status == AttendanceStatus.NO_CLASS || !pendingAttendanceOps.add(opKey)) {
            if (status == AttendanceStatus.NO_CLASS) markAttendance(subjectId, status, date)
            return
        }
        viewModelScope.launch {
            try {
                val subject = repository.getSubjectById(subjectId)
                val oldRecord = repository.getAttendanceRecord(subjectId, date)
                if (subject == null || oldRecord?.status != status) {
                    pendingAttendanceOps.remove(opKey)
                    markAttendance(subjectId, status, date)
                    return@launch
                }
                repository.addExtraLecture(subjectId, date)
                val newRecord = repository.getAttendanceRecord(subjectId, date)
                if (newRecord != null) {
                    undoRedoManager.recordAction(
                        AttendanceAction(
                            subjectId = subjectId,
                            date = date,
                            oldStatus = oldRecord.status,
                            oldCount = oldRecord.count,
                            newStatus = newRecord.status,
                            newCount = newRecord.count,
                            oldPresentCount = subject.presentLectures,
                            oldAbsentCount = subject.absentLectures
                        )
                    )
                    updateUndoRedoState()
                }
                loadAttendanceForDate(date)
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
                val record = repository.getAttendanceRecord(subjectId, date)
                val subject = repository.getSubjectById(subjectId)

                if (record != null && subject != null) {
                    // Adjust counts based on the status we're removing
                    when (record.status) {
                        AttendanceStatus.PRESENT -> {
                            repository.updateAttendanceCounts(
                                subjectId,
                                subject.presentLectures - record.count,
                                subject.absentLectures
                            )
                        }
                        AttendanceStatus.ABSENT -> {
                            repository.updateAttendanceCounts(
                                subjectId,
                                subject.presentLectures,
                                subject.absentLectures - record.count
                            )
                        }
                        AttendanceStatus.NO_CLASS -> {
                            // NO_CLASS does not affect present/absent counts
                        }
                    }

                    // Record the clear action for undo
                    val action = AttendanceAction(
                        subjectId = subjectId,
                        date = date,
                        oldStatus = record.status,
                        oldCount = record.count,
                        newStatus = AttendanceStatus.PRESENT, // arbitrary, won't be used since newCount is 0 conceptually
                        newCount = 0,
                        oldPresentCount = subject.presentLectures,
                        oldAbsentCount = subject.absentLectures
                    )
                    undoRedoManager.recordAction(action)
                    updateUndoRedoState()

                    // Delete the record
                    repository.deleteAttendanceRecord(subjectId, date)

                    loadAttendanceForDate(date)
                }
            } finally {
                pendingAttendanceOps.remove(opKey)
            }
        }
    }
    
    fun undo() {
        viewModelScope.launch {
            val action = undoRedoManager.undo()
            if (action != null) {
                // Restore the old state
                val subject = repository.getSubjectById(action.subjectId)
                if (subject != null) {
                    // Restore attendance counts first
                    repository.updateAttendanceCounts(
                        action.subjectId,
                        action.oldPresentCount,
                        action.oldAbsentCount
                    )
                    
                    // Restore or delete the attendance record
                    if (action.oldStatus != null && action.oldCount > 0) {
                        // There was a previous status, restore it with the count
                        repository.setAttendanceStatus(action.subjectId, action.date, action.oldStatus, action.oldCount)
                    } else {
                        // No previous status, delete the record
                        repository.deleteAttendanceRecord(action.subjectId, action.date)
                    }
                    
                    loadAttendanceForDate(action.date)
                    updateUndoRedoState()
                }
            }
        }
    }
    
    fun redo() {
        viewModelScope.launch {
            val action = undoRedoManager.redo()
            if (action != null) {
                // Get current subject to calculate new counts
                val subject = repository.getSubjectById(action.subjectId)
                if (subject != null) {
                    // Calculate what the counts should be after redo. Using the count
                    // contributed by each status (rather than a same-status/different-status
                    // branch) also correctly handles redoing a repeated same-status mark
                    // (e.g. PRESENT count 1 -> PRESENT count 2).
                    val presentDiff = (if (action.newStatus == AttendanceStatus.PRESENT) action.newCount else 0) -
                        (if (action.oldStatus == AttendanceStatus.PRESENT) action.oldCount else 0)
                    val absentDiff = (if (action.newStatus == AttendanceStatus.ABSENT) action.newCount else 0) -
                        (if (action.oldStatus == AttendanceStatus.ABSENT) action.oldCount else 0)

                    // Update subject counts
                    repository.updateAttendanceCounts(
                        action.subjectId,
                        subject.presentLectures + presentDiff,
                        subject.absentLectures + absentDiff
                    )

                    // Set the attendance record with the new status and count, or delete it
                    // if the action being redone was a clear (newCount == 0) — otherwise a
                    // bogus zero-count record would be left behind.
                    if (action.newCount > 0) {
                        repository.setAttendanceStatus(action.subjectId, action.date, action.newStatus, action.newCount)
                    } else {
                        repository.deleteAttendanceRecord(action.subjectId, action.date)
                    }

                    loadAttendanceForDate(action.date)
                    updateUndoRedoState()
                }
            }
        }
    }
    
    private fun updateUndoRedoState() {
        _canUndo.value = undoRedoManager.canUndo
        _canRedo.value = undoRedoManager.canRedo
    }

    // Schedule operations
    fun addScheduleEntry(subjectId: Long, dayOfWeek: DayOfWeek) {
        viewModelScope.launch {
            repository.insertScheduleEntry(
                ScheduleEntry(subjectId = subjectId, dayOfWeek = dayOfWeek)
            )
        }
    }

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

    fun updateCustomColors(primaryColor: Long?, secondaryColor: Long?) {
        viewModelScope.launch {
            themeRepository.updateCustomColors(primaryColor, secondaryColor)
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
                repository.restoreData(data.subjects, data.attendanceRecords, data.scheduleEntries)
                loadAttendanceForDate(LocalDate.now())
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

    /** Parses a CSV for the import preview; writes nothing. */
    fun previewCsvImport(text: String): com.attendance.tracker.backup.CsvImporter.Result =
        com.attendance.tracker.backup.CsvImporter.parse(text)

    /** Writes a previously previewed (error-free) CSV import. */
    fun importCsv(data: com.attendance.tracker.backup.CsvImporter.Result) {
        viewModelScope.launch {
            _backupRestoreStatus.value = BackupRestoreStatus.IN_PROGRESS
            try {
                val r = repository.importCsv(data)
                _backupRestoreStatus.value = BackupRestoreStatus.SUCCESS
                _backupRestoreMessage.value =
                    "Import complete: ${r.subjectsAdded} subjects added" +
                    (if (r.subjectsExisting > 0) " (${r.subjectsExisting} already existed, left unchanged)" else "") +
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
