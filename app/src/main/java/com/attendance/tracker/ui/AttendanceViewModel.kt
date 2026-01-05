package com.attendance.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.tracker.data.database.AttendanceDatabase
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
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
        viewModelScope.launch {
            repository.getAttendanceForDate(date).collect { records ->
                _todayAttendance.value = records.associateBy { it.subjectId }
            }
        }
    }

    fun loadAttendanceForMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
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

    fun updateRequiredAttendance(subjectId: Long, required: Int) {
        viewModelScope.launch {
            repository.updateRequiredAttendance(subjectId, required)
        }
    }

    fun updateAttendanceCounts(subjectId: Long, present: Int, absent: Int) {
        viewModelScope.launch {
            repository.updateAttendanceCounts(subjectId, present, absent)
        }
    }

    // Attendance operations
    fun markAttendance(subjectId: Long, status: AttendanceStatus, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            // Get current state before marking
            val subject = repository.getSubjectById(subjectId)
            val oldRecord = repository.getAttendanceRecord(subjectId, date)
            
            if (subject != null) {
                // Record action for undo/redo
                val action = AttendanceAction(
                    subjectId = subjectId,
                    date = date,
                    oldStatus = oldRecord?.status,
                    newStatus = status,
                    oldPresentCount = subject.presentLectures,
                    oldAbsentCount = subject.absentLectures
                )
                undoRedoManager.recordAction(action)
                updateUndoRedoState()
                
                // Mark the new status
                when (status) {
                    AttendanceStatus.PRESENT -> repository.markPresent(subjectId, date)
                    AttendanceStatus.ABSENT -> repository.markAbsent(subjectId, date)
                    AttendanceStatus.NO_CLASS -> repository.markNoClass(subjectId, date)
                }
                loadAttendanceForDate(date)
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
                    if (action.oldStatus != null) {
                        // There was a previous status, restore it without modifying counts
                        repository.setAttendanceStatus(action.subjectId, action.date, action.oldStatus)
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
                // Reapply the new status
                when (action.newStatus) {
                    AttendanceStatus.PRESENT -> repository.markPresent(action.subjectId, action.date)
                    AttendanceStatus.ABSENT -> repository.markAbsent(action.subjectId, action.date)
                    AttendanceStatus.NO_CLASS -> repository.markNoClass(action.subjectId, action.date)
                }
                loadAttendanceForDate(action.date)
                updateUndoRedoState()
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
}
