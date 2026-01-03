package com.attendance.tracker.utils

import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Data class for attendance analytics
 */
data class AttendanceAnalytics(
    val subject: Subject,
    val currentAttendancePercentage: Float,
    val classesToAttendForTarget: Int,
    val classesCanBunk: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val averageAttendancePerWeek: Float,
    val predictedSemesterEndPercentage: Float?,
    val weeklyTrend: List<Float>,
    val remainingScheduledClasses: Int
)

/**
 * Analytics helper functions
 */
object AttendanceAnalyticsHelper {

    /**
     * Calculate current attendance streak (consecutive present days)
     */
    fun calculateCurrentStreak(attendanceRecords: List<AttendanceRecord>): Int {
        if (attendanceRecords.isEmpty()) return 0
        
        val sortedRecords = attendanceRecords.sortedByDescending { it.date }
        var streak = 0
        
        for (record in sortedRecords) {
            if (record.status == AttendanceStatus.PRESENT) {
                streak++
            } else if (record.status == AttendanceStatus.ABSENT) {
                break
            }
            // NO_CLASS doesn't break the streak
        }
        
        return streak
    }

    /**
     * Calculate longest attendance streak
     */
    fun calculateLongestStreak(attendanceRecords: List<AttendanceRecord>): Int {
        if (attendanceRecords.isEmpty()) return 0
        
        val sortedRecords = attendanceRecords.sortedBy { it.date }
        var longestStreak = 0
        var currentStreak = 0
        
        for (record in sortedRecords) {
            if (record.status == AttendanceStatus.PRESENT) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else if (record.status == AttendanceStatus.ABSENT) {
                currentStreak = 0
            }
            // NO_CLASS doesn't affect streak
        }
        
        return longestStreak
    }

    /**
     * Calculate average attendance per week
     */
    fun calculateAverageAttendancePerWeek(
        attendanceRecords: List<AttendanceRecord>,
        startDate: LocalDate = LocalDate.now().minusMonths(3)
    ): Float {
        val recentRecords = attendanceRecords.filter { it.date >= startDate }
        if (recentRecords.isEmpty()) return 0f
        
        val weeks = ChronoUnit.WEEKS.between(startDate, LocalDate.now()).toInt().coerceAtLeast(1)
        return recentRecords.size.toFloat() / weeks
    }

    /**
     * Calculate weekly trend (last 4 weeks)
     */
    fun calculateWeeklyTrend(attendanceRecords: List<AttendanceRecord>): List<Float> {
        val today = LocalDate.now()
        val trend = mutableListOf<Float>()
        
        for (weekOffset in 3 downTo 0) {
            val weekStart = today.minusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            
            val weekRecords = attendanceRecords.filter { 
                it.date >= weekStart && it.date <= weekEnd 
            }
            
            if (weekRecords.isEmpty()) {
                trend.add(0f)
            } else {
                val presentCount = weekRecords.count { it.status == AttendanceStatus.PRESENT }
                val percentage = (presentCount.toFloat() / weekRecords.size) * 100
                trend.add(percentage)
            }
        }
        
        return trend
    }

    /**
     * Calculate remaining scheduled classes until a specific date (e.g., semester end)
     */
    fun calculateRemainingScheduledClasses(
        scheduleEntries: List<ScheduleEntry>,
        endDate: LocalDate = LocalDate.now().plusMonths(3) // Default: 3 months ahead
    ): Int {
        val today = LocalDate.now()
        var remainingClasses = 0
        var currentDate = today
        
        while (currentDate.isBefore(endDate) || currentDate.isEqual(endDate)) {
            val dayOfWeek = currentDate.dayOfWeek
            if (scheduleEntries.any { it.dayOfWeek == dayOfWeek }) {
                remainingClasses++
            }
            currentDate = currentDate.plusDays(1)
        }
        
        return remainingClasses
    }

    /**
     * Predict semester-end attendance percentage
     */
    fun predictSemesterEndPercentage(
        subject: Subject,
        remainingClasses: Int,
        optimisticScenario: Boolean = false
    ): Float? {
        if (remainingClasses <= 0) return subject.currentAttendancePercentage
        
        val currentTotal = subject.totalLectures
        val currentPresent = subject.presentLectures
        
        // Calculate future attendance
        val futurePresent = if (optimisticScenario) {
            // Assume 100% attendance for remaining classes
            remainingClasses
        } else {
            // Assume same attendance rate as current
            val currentRate = if (currentTotal > 0) {
                subject.currentAttendancePercentage / 100f
            } else {
                0.75f // Default 75% if no history
            }
            (remainingClasses * currentRate).toInt()
        }
        
        val futureTotal = currentTotal + remainingClasses
        val futurePresent = currentPresent + futurePresent
        
        return if (futureTotal > 0) {
            (futurePresent.toFloat() / futureTotal) * 100
        } else {
            null
        }
    }

    /**
     * Build complete analytics for a subject
     */
    fun buildAnalytics(
        subject: Subject,
        attendanceRecords: List<AttendanceRecord>,
        scheduleEntries: List<ScheduleEntry>
    ): AttendanceAnalytics {
        val remainingClasses = calculateRemainingScheduledClasses(scheduleEntries)
        
        return AttendanceAnalytics(
            subject = subject,
            currentAttendancePercentage = subject.currentAttendancePercentage,
            classesToAttendForTarget = subject.classesToAttend,
            classesCanBunk = subject.classesCanBunk,
            currentStreak = calculateCurrentStreak(attendanceRecords),
            longestStreak = calculateLongestStreak(attendanceRecords),
            averageAttendancePerWeek = calculateAverageAttendancePerWeek(attendanceRecords),
            predictedSemesterEndPercentage = predictSemesterEndPercentage(subject, remainingClasses),
            weeklyTrend = calculateWeeklyTrend(attendanceRecords),
            remainingScheduledClasses = remainingClasses
        )
    }
}
