package com.attendance.tracker.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class CsvImporterTest {

    @Test
    fun `template parses cleanly and merges rows of the same subject`() {
        val r = CsvImporter.parse(CsvImporter.TEMPLATE)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(2, r.subjects.size)
        val physics = r.subjects.first { it.name == "Physics" }
        assertEquals(75, physics.requiredAttendance)
        assertEquals(20, physics.attended)
        assertEquals(25, physics.total)
        assertEquals(3, r.slots.size)
        assertEquals(DayOfWeek.WEDNESDAY, r.slots[1].day)
        assertEquals(LocalTime.of(11, 0), r.slots[1].start)
    }

    @Test
    fun `handles BOM, quoted commas, short day names and am-pm times`() {
        val r = CsvImporter.parse("﻿Name,Day,Start,End\r\n\"Maths, Applied\",tue,9:30 AM,10:30am\r\n")
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals("Maths, Applied", r.subjects[0].name)
        assertEquals(DayOfWeek.TUESDAY, r.slots[0].day)
        assertEquals(LocalTime.of(10, 30), r.slots[0].end)
    }

    @Test
    fun `reports row-level errors and missing header`() {
        assertTrue(CsvImporter.parse("foo,bar\n1,2").errors.single().contains("subject"))
        val r = CsvImporter.parse("subject,target_percentage,attended,total,day,start,end\n" +
            ",75,1,2,,,\nA,150,1,2,,,\nB,75,5,2,,,\nC,75,1,2,Funday,09:00,10:00\nD,,,,Monday,10:00,09:00\n")
        assertEquals(5, r.errors.size)
    }

    @Test
    fun `a repeated row is counted as one timetable slot`() {
        val r = CsvImporter.parse("subject,day,start,end\nBio,Tue,11:00,12:30\nBio,Tue,11:00,12:30\n")
        assertEquals(1, r.slots.size)
    }
}
