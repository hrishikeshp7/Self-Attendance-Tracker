package com.attendance.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A small, pleasant rotation of tints used to give each subject a distinct, stable
 * color (derived from its id) without needing a color field on the Subject model
 * itself. Shared by every schedule-related screen so a subject reads as the same
 * color everywhere.
 */
private val SubjectAvatarPalette = listOf(
    Color(0xFF4361EE), Color(0xFF4CC9F0), Color(0xFFF72585),
    Color(0xFFF9A826), Color(0xFF06D6A0), Color(0xFF7209B7),
    Color(0xFFE63946), Color(0xFF3A86FF)
)

fun subjectAvatarColor(subjectId: Long): Color =
    SubjectAvatarPalette[(subjectId.mod(SubjectAvatarPalette.size.toLong())).toInt()]
