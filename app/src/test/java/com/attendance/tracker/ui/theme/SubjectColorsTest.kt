package com.attendance.tracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubjectColorsTest {

    @Test
    fun `distinct active subjects receive distinct colors up to palette size`() {
        val subjectIds = listOf(1L, 2L, 5L, 8L, 9L, 12L, 15L, 20L)
        val colors = subjectIds.map { subjectAvatarColor(it, subjectIds) }

        // All colors must be unique
        assertEquals(subjectIds.size, colors.distinct().size)
    }

    @Test
    fun `colors remain stable for same subject given same active subject set`() {
        val subjectIds = listOf(10L, 20L, 30L)
        val color1 = subjectAvatarColor(20L, subjectIds)
        val color2 = subjectAvatarColor(20L, subjectIds)
        assertEquals(color1, color2)
    }

    @Test
    fun `palette has at least 16 distinct colors`() {
        org.junit.Assert.assertTrue(SubjectAvatarPalette.size >= 16)
        assertEquals(SubjectAvatarPalette.size, SubjectAvatarPalette.distinct().size)
    }
}
