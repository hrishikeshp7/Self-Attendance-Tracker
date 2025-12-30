package com.attendance.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a subject/course for attendance tracking
 */
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val requiredAttendance: Int = 75, // Default 75% required attendance
    val totalLectures: Int = 0,
    val presentLectures: Int = 0,
    val absentLectures: Int = 0,
    val parentSubjectId: Long? = null, // Null for top-level subjects, or ID of parent subject for sub-subjects
    val isFolder: Boolean = false // True if this is a folder/group (like "Pathology"), false for actual subjects (like "Pathology - Lecture")
) {
    val currentAttendancePercentage: Float
        get() = if (totalLectures > 0) (presentLectures.toFloat() / totalLectures) * 100 else 0f
    
    val isAboveRequired: Boolean
        get() = currentAttendancePercentage >= requiredAttendance
}

/**
 * Get the display name for a subject, including folder name if applicable
 */
fun Subject.getDisplayName(allSubjects: List<Subject>): String {
    return if (parentSubjectId != null) {
        val folder = allSubjects.find { it.id == parentSubjectId }
        if (folder != null) {
            "${folder.name} / $name"
        } else {
            name
        }
    } else {
        name
    }
}
