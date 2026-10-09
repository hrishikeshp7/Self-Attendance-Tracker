package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Represents an attendance record for a specific subject on a specific date
 */
@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId", "date"], unique = true)]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val date: LocalDate,
    val status: AttendanceStatus,
    val count: Int = 1,
    // Lectures that day with the OTHER of PRESENT/ABSENT (e.g. status PRESENT, count 1,
    // otherCount 1 = one lecture attended, one missed). Always 0 for NO_CLASS.
    val otherCount: Int = 0
) {
    val presentCount: Int
        get() = when (status) {
            AttendanceStatus.PRESENT -> count
            AttendanceStatus.ABSENT -> otherCount
            AttendanceStatus.NO_CLASS -> 0
        }
    val absentCount: Int
        get() = when (status) {
            AttendanceStatus.ABSENT -> count
            AttendanceStatus.PRESENT -> otherCount
            AttendanceStatus.NO_CLASS -> 0
        }
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    NO_CLASS
}
