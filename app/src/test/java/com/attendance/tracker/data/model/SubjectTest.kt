package com.attendance.tracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectTest {

    @Test
    fun `current attendance percentage is zero with no lectures`() {
        val subject = Subject(name = "Physics")
        assertEquals(0f, subject.currentAttendancePercentage)
    }

    @Test
    fun `classesToAttend computes the minimum classes needed to reach the requirement`() {
        // 5 present out of 10 total (50%), need 75%: (75*10 - 100*5) / (100-75) = 10
        val subject = Subject(name = "Physics", requiredAttendance = 75, totalLectures = 10, presentLectures = 5)
        assertEquals(10, subject.classesToAttend)
    }

    @Test
    fun `classesToAttend is zero once already above the requirement`() {
        val subject = Subject(name = "Physics", requiredAttendance = 75, totalLectures = 10, presentLectures = 8)
        assertEquals(0, subject.classesToAttend)
    }

    @Test
    fun `classesToAttend flags an impossible recovery at 100 percent required`() {
        val subject = Subject(name = "Physics", requiredAttendance = 100, totalLectures = 5, presentLectures = 4)
        assertEquals(999, subject.classesToAttend)
    }

    @Test
    fun `classesCanBunk computes how many absences can still be tolerated`() {
        // 9 present out of 10 total (90%), need 75%: floor(100*9/75 - 10) = floor(12 - 10) = 2
        val subject = Subject(name = "Physics", requiredAttendance = 75, totalLectures = 10, presentLectures = 9)
        assertEquals(2, subject.classesCanBunk)
    }

    @Test
    fun `classesCanBunk is zero when below the requirement`() {
        val subject = Subject(name = "Physics", requiredAttendance = 75, totalLectures = 10, presentLectures = 5)
        assertEquals(0, subject.classesCanBunk)
    }

    @Test
    fun `getDisplayName prefixes the parent folder name for sub-subjects`() {
        val folder = Subject(id = 1, name = "Pathology", isFolder = true)
        val subSubject = Subject(id = 2, name = "Lecture", parentSubjectId = 1)
        assertEquals("Pathology / Lecture", subSubject.getDisplayName(listOf(folder, subSubject)))
    }

    @Test
    fun `getDisplayName falls back to the plain name when no folder is found`() {
        val subSubject = Subject(id = 2, name = "Lecture", parentSubjectId = 99)
        assertEquals("Lecture", subSubject.getDisplayName(listOf(subSubject)))
    }

    @Test
    fun `exact threshold counts as meeting it despite float rounding`() {
        // 53/100 as float is 52.999996f, which a float comparison treats as below 53%
        val subject = Subject(name = "Physics", requiredAttendance = 53, totalLectures = 100, presentLectures = 53, absentLectures = 47)
        assertTrue(subject.isAboveRequired)
        assertEquals(0, subject.classesToAttend)
    }

    @Test
    fun `subject with no lectures is not above a positive requirement`() {
        assertFalse(Subject(name = "Physics", requiredAttendance = 75).isAboveRequired)
    }
}
