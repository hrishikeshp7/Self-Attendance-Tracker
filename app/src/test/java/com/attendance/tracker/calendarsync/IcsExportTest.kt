package com.attendance.tracker.calendarsync

import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IcsExportTest {
    private val subjects = mapOf(1L to Subject(id = 1, name = "Physics"))
    private val today = LocalDate.now()
    private fun events(vararg e: ScheduleEntry, end: LocalDate = today.plusWeeks(8)) =
        CalendarSyncManager.buildIcsContent(e.toList(), subjects, end).split("BEGIN:VEVENT").size - 1

    @Test
    fun `slots outside their date range are not exported`() {
        val day = today.dayOfWeek
        assertEquals(1, events(ScheduleEntry(subjectId = 1, dayOfWeek = day)))
        assertEquals(0, events(ScheduleEntry(subjectId = 1, dayOfWeek = day, startDate = today.minusWeeks(9), endDate = today.minusWeeks(1))))
        assertEquals(0, events(ScheduleEntry(subjectId = 1, dayOfWeek = day, startDate = today.plusWeeks(9), endDate = today.plusWeeks(12))))
    }

    @Test
    fun `a range ends at the slot's own end date`() {
        val e = ScheduleEntry(subjectId = 1, dayOfWeek = today.dayOfWeek, startDate = today, endDate = today.plusWeeks(2))
        val ics = CalendarSyncManager.buildIcsContent(listOf(e), subjects, today.plusWeeks(8))
        val until = Regex("UNTIL=(\\d{8})").find(ics)!!.groupValues[1]
        assertTrue(until <= today.plusWeeks(2).toString().replace("-", "") + "9")  // UTC conversion can land a day later
        assertTrue(until >= today.plusWeeks(2).toString().replace("-", ""))
    }

    @Test
    fun `overnight slot sets DTEND on the following calendar day`() {
        val entry = ScheduleEntry(
            subjectId = 1,
            dayOfWeek = today.dayOfWeek,
            startTime = java.time.LocalTime.of(23, 30),
            endTime = java.time.LocalTime.of(0, 30),
            startDate = today,
            endDate = today.plusWeeks(1)
        )
        val ics = CalendarSyncManager.buildIcsContent(listOf(entry), subjects, today.plusWeeks(1))
        val dtStartMatch = Regex("DTSTART[^:]*:(\\d{8})T(\\d{6})").find(ics)!!
        val dtEndMatch = Regex("DTEND[^:]*:(\\d{8})T(\\d{6})").find(ics)!!

        val startDateStr = dtStartMatch.groupValues[1]
        val endDateStr = dtEndMatch.groupValues[1]
        val startLocalDate = LocalDate.parse(startDateStr, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        val endLocalDate = LocalDate.parse(endDateStr, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)

        assertEquals(startLocalDate.plusDays(1), endLocalDate)
        assertEquals("003000", dtEndMatch.groupValues[2])
    }
}
