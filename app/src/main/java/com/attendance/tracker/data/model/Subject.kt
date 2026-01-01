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
    
    /**
     * Calculate how many classes need to be attended to meet the required attendance
     * Returns 0 if already above required attendance
     */
    val classesToAttend: Int
        get() {
            if (isAboveRequired || totalLectures == 0) return 0
            
            // Formula: (P + x) / (T + x) = R/100
            // Where P = present, T = total, R = required%, x = classes to attend
            // Solving: x = (R*T - 100*P) / (100 - R)
            val numerator = (requiredAttendance * totalLectures) - (100 * presentLectures)
            val denominator = 100 - requiredAttendance
            
            return if (denominator > 0) {
                kotlin.math.ceil(numerator.toDouble() / denominator).toInt().coerceAtLeast(0)
            } else {
                0
            }
        }
    
    /**
     * Calculate how many classes can be bunked while maintaining required attendance
     * Returns 0 if below required attendance or at the threshold
     */
    val classesCanBunk: Int
        get() {
            if (!isAboveRequired || totalLectures == 0) return 0
            
            // Formula: (P) / (T + x) = R/100
            // Where P = present, T = total, R = required%, x = classes can bunk
            // Solving: x = (100*P/R) - T
            val maxTotalWithBunks = (100.0 * presentLectures) / requiredAttendance
            val canBunk = kotlin.math.floor(maxTotalWithBunks - totalLectures).toInt()
            
            return canBunk.coerceAtLeast(0)
        }
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
