package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek

/**
 * Represents a weekly schedule entry - which subjects are on which days
 * Now includes time periods and week variations for alternating schedules
 */
@Entity(
    tableName = "schedule_entries",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId", "dayOfWeek", "weekNumber"])]
)
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: DayOfWeek,
    val isScheduled: Boolean = true,
    val startTimeHour: Int = 9, // Default 9 AM
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 10, // Default 10 AM
    val endTimeMinute: Int = 0,
    val weekNumber: Int = 0 // 0 = all weeks, 1 = week 1, 2 = week 2, etc.
) {
    fun getTimeRange(): String {
        val startTime = String.format("%02d:%02d", startTimeHour, startTimeMinute)
        val endTime = String.format("%02d:%02d", endTimeHour, endTimeMinute)
        return "$startTime - $endTime"
    }
}
