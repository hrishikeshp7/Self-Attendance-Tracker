package com.attendance.tracker.backup

import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class BackupManagerTest {
    private val subjects = listOf(Subject(id = 1, name = "Physics", totalLectures = 3, presentLectures = 2, absentLectures = 1))
    private val records = listOf(AttendanceRecord(subjectId = 1, date = LocalDate.of(2024, 1, 15), status = AttendanceStatus.PRESENT, count = 2, otherCount = 1))
    private val schedule = listOf(ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0)))

    @Test
    fun `json export parses back to the same data`() {
        val data = BackupManager.parseJson(BackupManager.exportToJson(subjects, records, schedule))
        assertNotNull(data)
        assertEquals(subjects, data!!.subjects)
        assertEquals(records, data.attendanceRecords)
        assertEquals(schedule, data.scheduleEntries)
    }

    @Test
    fun `backup from a newer app version is rejected`() {
        val json = BackupManager.exportToJson(subjects, records, schedule).replace("\"version\": 3", "\"version\": 99")
        assertNull(BackupManager.parseJson(json))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(BackupManager.parseJson("not json"))
    }
}
