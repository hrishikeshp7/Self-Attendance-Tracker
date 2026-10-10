package com.attendance.tracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleEntryTest {
    private val mon = LocalDate.of(2026, 10, 5) // a Monday

    @Test
    fun `unbounded slot occurs on every matching weekday only`() {
        val e = ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY)
        assertTrue(e.occursOn(mon)); assertTrue(e.occursOn(mon.plusWeeks(40))); assertTrue(e.occursOn(mon.minusWeeks(40)))
        assertFalse(e.occursOn(mon.plusDays(1)))
    }

    @Test
    fun `date range is inclusive at both ends`() {
        val e = ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startDate = mon, endDate = mon.plusWeeks(2))
        assertFalse(e.occursOn(mon.minusWeeks(1)))
        assertTrue(e.occursOn(mon)); assertTrue(e.occursOn(mon.plusWeeks(2)))
        assertFalse(e.occursOn(mon.plusWeeks(3)))
    }

    @Test
    fun `one-off slot occurs on its date only`() {
        val e = ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startDate = mon, endDate = mon)
        assertTrue(e.occursOn(mon)); assertFalse(e.occursOn(mon.plusWeeks(1)))
    }

    @Test
    fun `range overlap treats null as unbounded`() {
        assertTrue(datesOverlap(null, null, mon, mon))
        assertTrue(datesOverlap(mon, mon.plusDays(10), mon.plusDays(10), null))
        assertFalse(datesOverlap(mon, mon.plusDays(10), mon.plusDays(11), null))
        assertEquals(false, datesOverlap(null, mon, mon.plusDays(1), mon.plusDays(5)))
    }
}
