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
        return dateString?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek?): Int? {
        return day?.value
    }

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? {
        return value?.let { runCatching { DayOfWeek.of(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromAttendanceStatus(status: AttendanceStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toAttendanceStatus(value: String?): AttendanceStatus? {
        return value?.let { valStr ->
            AttendanceStatus.entries.find { it.name == valStr }
        }
    }

    // Stored as minutes since midnight so values sort/compare correctly as plain integers.
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): Int? {
        return time?.let { it.hour * 60 + it.minute }
    }

    @TypeConverter
    fun toLocalTime(minutesSinceMidnight: Int?): LocalTime? {
        return minutesSinceMidnight?.let {
            if (it in 0 until 1440) {
                runCatching { LocalTime.of(it / 60, it % 60) }.getOrNull()
            } else null
        }
    }
}
