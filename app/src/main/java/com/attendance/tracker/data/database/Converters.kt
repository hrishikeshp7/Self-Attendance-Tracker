package com.attendance.tracker.data.database

import androidx.room.TypeConverter
import com.attendance.tracker.data.model.AttendanceStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Type converters for Room database
 */
class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek?): Int? {
        return day?.value
    }

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? {
        return value?.let { DayOfWeek.of(it) }
    }

    @TypeConverter
    fun fromAttendanceStatus(status: AttendanceStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toAttendanceStatus(value: String?): AttendanceStatus? {
        return value?.let { AttendanceStatus.valueOf(it) }
    }

    // Stored as minutes since midnight so values sort/compare correctly as plain integers.
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): Int? {
        return time?.let { it.hour * 60 + it.minute }
    }

    @TypeConverter
    fun toLocalTime(minutesSinceMidnight: Int?): LocalTime? {
        return minutesSinceMidnight?.let { LocalTime.of(it / 60, it % 60) }
    }
}
