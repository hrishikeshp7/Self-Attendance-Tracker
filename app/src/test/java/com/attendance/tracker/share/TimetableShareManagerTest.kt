package com.attendance.tracker.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TimetableShareManagerTest {

    @Test
    fun `encode and decode preserves complete timetable`() {
        val timetable = SharedTimetable(
            title = "Computer Science - Section B",
            subjects = listOf(
                SharedSubject(name = "Data Structures", requiredAttendance = 75),
                SharedSubject(name = "Operating Systems", requiredAttendance = 80),
                SharedSubject(name = "Linear Algebra", requiredAttendance = 65)
            ),
            lectures = listOf(
                SharedLecture(
                    subjectIndex = 0,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(10, 0),
                    startDate = LocalDate.of(2026, 8, 1),
                    endDate = LocalDate.of(2026, 12, 1)
                ),
                SharedLecture(
                    subjectIndex = 1,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(10, 15),
                    endTime = LocalTime.of(11, 15)
                ),
                SharedLecture(
                    subjectIndex = 2,
                    dayOfWeek = DayOfWeek.WEDNESDAY,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(16, 0)
                )
            )
        )

        val encoded = TimetableShareManager.encode(timetable)
        assertTrue(encoded.isNotBlank())

        val deepLink = "attendance://import?tt=$encoded"
        val decoded = TimetableShareManager.decode(deepLink)

        assertNotNull(decoded)
        assertEquals(timetable.title, decoded!!.title)
        assertEquals(timetable.subjects.size, decoded.subjects.size)
        assertEquals(timetable.subjects[0].name, decoded.subjects[0].name)
        assertEquals(timetable.subjects[0].requiredAttendance, decoded.subjects[0].requiredAttendance)
        assertEquals(timetable.subjects[1].name, decoded.subjects[1].name)
        assertEquals(timetable.subjects[1].requiredAttendance, decoded.subjects[1].requiredAttendance)
        assertEquals(timetable.lectures.size, decoded.lectures.size)
        assertEquals(timetable.lectures[0].dayOfWeek, decoded.lectures[0].dayOfWeek)
        assertEquals(timetable.lectures[0].startTime, decoded.lectures[0].startTime)
        assertEquals(timetable.lectures[0].endTime, decoded.lectures[0].endTime)
        assertEquals(timetable.lectures[0].startDate, decoded.lectures[0].startDate)
        assertEquals(timetable.lectures[0].endDate, decoded.lectures[0].endDate)
    }

    @Test
    fun `extractToken parses query parameter correctly`() {
        val link = "attendance://import?tt=abc123XYZ&ref=whatsapp"
        assertEquals("abc123XYZ", TimetableShareManager.extractToken(link))

        val plain = "abc123XYZ"
        assertEquals("abc123XYZ", TimetableShareManager.extractToken(plain))
    }

    @Test
    fun `invalid token returns null without throwing exception`() {
        assertNull(TimetableShareManager.decode("invalid-corrupt-data"))
        assertNull(TimetableShareManager.decode(""))
    }
}
