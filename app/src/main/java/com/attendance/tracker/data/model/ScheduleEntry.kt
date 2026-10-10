package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Default slot used when a schedule entry is created without an explicit time. */
val DEFAULT_LECTURE_START_TIME: LocalTime = LocalTime.of(9, 0)
val DEFAULT_LECTURE_END_TIME: LocalTime = LocalTime.of(10, 0)

/**
 * Represents a weekly recurring lecture slot - a subject on a given day of the week,
 * with a start/end time. A subject can have more than one entry on the same day (e.g.
 * a lecture and a lab), and entries on the same day may overlap in time — the calendar
 * UI lays overlapping entries out side by side rather than rejecting them.
 *
 * [startDate]/[endDate] (inclusive, either may be null = unbounded) limit the dates the
 * slot applies to, e.g. one semester. A one-off lecture has startDate == endDate.
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
    indices = [Index(value = ["subjectId", "dayOfWeek"], unique = false)]
)
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime = DEFAULT_LECTURE_START_TIME,
    val endTime: LocalTime = DEFAULT_LECTURE_END_TIME,
    val isScheduled: Boolean = true,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    /** True if this slot is held on [date]: matching weekday and inside the date range. */
    fun occursOn(date: LocalDate): Boolean =
        dayOfWeek == date.dayOfWeek &&
            (startDate == null || !date.isBefore(startDate)) &&
            (endDate == null || !date.isAfter(endDate))
}

/** True if the two inclusive date ranges share at least one date (null = unbounded). */
fun datesOverlap(aStart: LocalDate?, aEnd: LocalDate?, bStart: LocalDate?, bEnd: LocalDate?): Boolean =
    (aStart == null || bEnd == null || !aStart.isAfter(bEnd)) &&
        (bStart == null || aEnd == null || !bStart.isAfter(aEnd))
