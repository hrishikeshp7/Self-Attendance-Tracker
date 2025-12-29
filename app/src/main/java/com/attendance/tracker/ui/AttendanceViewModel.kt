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

    // UI State
    val subjects: StateFlow<List<Subject>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleEntries: StateFlow<List<ScheduleEntry>> = repository.allScheduleEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _todayAttendance = MutableStateFlow<Map<Long, AttendanceStatus>>(emptyMap())
    val todayAttendance: StateFlow<Map<Long, AttendanceStatus>> = _todayAttendance.asStateFlow()

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords.asStateFlow()

    init {
        // Load today's attendance when ViewModel is created
        loadAttendanceForDate(LocalDate.now())
    }

    fun loadAttendanceForDate(date: LocalDate) {
        viewModelScope.launch {
            repository.getAttendanceForDate(date).collect { records ->
                _todayAttendance.value = records.associate { it.subjectId to it.status }
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
            when (status) {
                AttendanceStatus.PRESENT -> repository.markPresent(subjectId, date)
                AttendanceStatus.ABSENT -> repository.markAbsent(subjectId, date)
                AttendanceStatus.NO_CLASS -> repository.markNoClass(subjectId, date)
            }
            loadAttendanceForDate(date)
        }
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
}
