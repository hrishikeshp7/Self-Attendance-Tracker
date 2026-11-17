package com.attendance.tracker.calendarsync

import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
        val eventBlock = ics.substringAfter("BEGIN:VEVENT")
        val dtStartMatch = Regex("DTSTART[^:]*:(\\d{8})T(\\d{6})").find(eventBlock)!!
        val dtEndMatch = Regex("DTEND[^:]*:(\\d{8})T(\\d{6})").find(eventBlock)!!

        val startDateStr = dtStartMatch.groupValues[1]
        val endDateStr = dtEndMatch.groupValues[1]
        val startLocalDate = LocalDate.parse(startDateStr, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        val endLocalDate = LocalDate.parse(endDateStr, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)

        assertEquals(startLocalDate.plusDays(1), endLocalDate)
        assertEquals("003000", dtEndMatch.groupValues[2])
    }

    @Test
    fun `vtimezone block is included before vevent and adheres to rfc 5545`() {
        val entry = ScheduleEntry(subjectId = 1, dayOfWeek = today.dayOfWeek, startDate = today, endDate = today.plusWeeks(1))
        val kolkataZone = ZoneId.of("Asia/Kolkata")
        val ics = CalendarSyncManager.exportToIcs(listOf(entry), subjects, today.plusWeeks(1), kolkataZone)

        val vtzIndex = ics.indexOf("BEGIN:VTIMEZONE")
        val veventIndex = ics.indexOf("BEGIN:VEVENT")
        assertTrue("VTIMEZONE must be present", vtzIndex >= 0)
        assertTrue("VTIMEZONE must be placed before VEVENT", vtzIndex < veventIndex)

        val vtzBlock = ics.substringAfter("BEGIN:VTIMEZONE").substringBefore("END:VTIMEZONE")
        assertTrue(vtzBlock.contains("TZID:Asia/Kolkata\r\n"))
        assertTrue(vtzBlock.contains("BEGIN:STANDARD\r\n"))
        assertTrue(vtzBlock.contains("DTSTART:19700101T000000\r\n"))
        assertTrue(vtzBlock.contains("TZOFFSETFROM:+0530\r\n"))
        assertTrue(vtzBlock.contains("TZOFFSETTO:+0530\r\n"))
        assertTrue(vtzBlock.contains("TZNAME:IST\r\n"))
        assertTrue(vtzBlock.contains("END:STANDARD\r\n"))
    }

    @Test
    fun `buildVTimezone formats offsets correctly for multiple zones`() {
        val kolkataVtz = CalendarSyncManager.buildVTimezone(ZoneId.of("Asia/Kolkata"))
        assertTrue(kolkataVtz.contains("TZID:Asia/Kolkata\r\n"))
        assertTrue(kolkataVtz.contains("TZOFFSETFROM:+0530\r\n"))
        assertTrue(kolkataVtz.contains("TZOFFSETTO:+0530\r\n"))
        assertTrue(kolkataVtz.contains("TZNAME:IST\r\n"))

        val nyVtz = CalendarSyncManager.buildVTimezone(ZoneId.of("America/New_York"))
        assertTrue(nyVtz.contains("TZID:America/New_York\r\n"))
        assertTrue(nyVtz.contains("TZOFFSETFROM:-0500\r\n"))
        assertTrue(nyVtz.contains("TZOFFSETTO:-0500\r\n"))
        assertTrue(nyVtz.contains("TZNAME:EST\r\n"))

        val utcVtz = CalendarSyncManager.buildVTimezone(ZoneOffset.UTC)
        assertTrue(utcVtz.contains("TZOFFSETFROM:+0000\r\n"))
        assertTrue(utcVtz.contains("TZOFFSETTO:+0000\r\n"))
    }

    @Test
    fun `buildRrule until calculation safely includes entire final calendar date`() {
        val lastDate = LocalDate.of(2026, 11, 20)
        val kolkataZone = ZoneId.of("Asia/Kolkata")

        // 2026-11-21T00:00:00+05:30 in UTC is 2026-11-20T18:30:00Z
        val kolkataRrule = CalendarSyncManager.buildRrule(DayOfWeek.FRIDAY, lastDate, kolkataZone)
        assertEquals("RRULE:FREQ=WEEKLY;BYDAY=FR;UNTIL=20261120T183000Z", kolkataRrule)

        // UTC: 2026-11-21T00:00:00Z
        val utcRrule = CalendarSyncManager.buildRrule(DayOfWeek.MONDAY, lastDate, ZoneOffset.UTC)
        assertEquals("RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261121T000000Z", utcRrule)

        // ScheduleEntry overload
        val entry = ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.FRIDAY, endDate = lastDate)
        val entryRrule = CalendarSyncManager.buildRrule(entry, lastDate.plusWeeks(2), kolkataZone)
        assertEquals(kolkataRrule, entryRrule)
    }

    @Test
    fun `exportToIcs matches buildIcsContent output`() {
        val entry = ScheduleEntry(subjectId = 1, dayOfWeek = today.dayOfWeek, startDate = today, endDate = today.plusWeeks(1))
        val ics1 = CalendarSyncManager.exportToIcs(listOf(entry), subjects, today.plusWeeks(1))
        val ics2 = CalendarSyncManager.buildIcsContent(listOf(entry), subjects, today.plusWeeks(1))
        assertEquals(ics1, ics2)
    }
}
