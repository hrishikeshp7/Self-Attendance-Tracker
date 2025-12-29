package com.attendance.tracker.data.database

import androidx.room.TypeConverter
import com.attendance.tracker.data.model.AttendanceStatus
import java.time.DayOfWeek
import java.time.LocalDate

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
}
