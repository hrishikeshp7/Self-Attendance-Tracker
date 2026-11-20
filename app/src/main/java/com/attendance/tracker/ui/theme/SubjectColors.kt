package com.attendance.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A curated rotation of vibrant, high-contrast tints used to give each subject a distinct,
 * stable color (derived from its id or active position) without needing a color field on the
 * Subject model itself. Shared by every schedule-related screen so a subject reads as the same
 * color everywhere.
 *
 * When [activeSubjectIds] is provided, colors are assigned based on the subject's position
 * among all sorted active subject IDs, guaranteeing that distinct active subjects get distinct
 * colors (up to the palette size).
 */
val SubjectAvatarPalette = listOf(
    Color(0xFF4361EE), // Royal Blue
    Color(0xFF00B4D8), // Cyan / Ocean Blue
    Color(0xFFF72585), // Vivid Pink
    Color(0xFFF77F00), // Amber / Orange
    Color(0xFF06D6A0), // Mint / Emerald
    Color(0xFF7209B7), // Purple
    Color(0xFFE63946), // Red
    Color(0xFF028090), // Slate Teal
    Color(0xFF8338EC), // Violet
    Color(0xFFFB5607), // Deep Orange
    Color(0xFF059669), // Forest Green
    Color(0xFFD90429), // Crimson
    Color(0xFF3A86FF), // Azure Blue
    Color(0xFF8E44AD), // Dark Magenta
    Color(0xFF2A9D8F), // Teal Green
    Color(0xFFE76F51), // Coral
    Color(0xFF1D3557), // Navy
    Color(0xFF5A7235), // Olive Green
    Color(0xFFB5179E), // Fuchsia
    Color(0xFF0077B6)  // Pacific Blue
)

fun subjectAvatarColor(subjectId: Long, activeSubjectIds: Collection<Long>? = null): Color {
    val palette = SubjectAvatarPalette
    val index = if (activeSubjectIds != null && activeSubjectIds.isNotEmpty()) {
        val sorted = activeSubjectIds.distinct().sorted()
        val pos = sorted.indexOf(subjectId)
        if (pos >= 0) pos else (subjectId.mod(palette.size.toLong())).toInt()
    } else {
        (subjectId.mod(palette.size.toLong())).toInt()
    }
    return palette[index % palette.size]
}
