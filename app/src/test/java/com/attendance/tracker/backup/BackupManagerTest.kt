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
    private val schedule = listOf(ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0)),
        ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.TUESDAY, startDate = LocalDate.of(2026, 8, 3), endDate = LocalDate.of(2026, 12, 18)))

    @Test
    fun `json export parses back to the same data`() {
        val rules = listOf(
            com.attendance.tracker.data.TimeOffRule(
                startDate = LocalDate.of(2026, 11, 2),
                endDate = LocalDate.of(2026, 11, 6),
                status = AttendanceStatus.ABSENT,
                subjectIds = setOf(1L)
            )
        )
        val slots = listOf(
            com.attendance.tracker.data.SlotEntry(
                subjectId = 1L,
                date = LocalDate.of(2026, 11, 2),
                slotIndex = 0,
                status = AttendanceStatus.PRESENT
            ),
            com.attendance.tracker.data.SlotEntry(
                subjectId = 1L,
                date = LocalDate.of(2026, 11, 2),
                slotIndex = 1,
                status = AttendanceStatus.ABSENT
            )
        )
        val data = BackupManager.parseJson(BackupManager.exportToJson(subjects, records, schedule, rules, slots))
        assertNotNull(data)
        assertEquals(subjects, data!!.subjects)
        assertEquals(records, data.attendanceRecords)
        assertEquals(schedule, data.scheduleEntries)
        assertEquals(rules, data.timeOffRules)
        assertEquals(slots, data.slotAttendance)
    }

    @Test
    fun `backup from a newer app version is rejected`() {
        val json = BackupManager.exportToJson(subjects, records, schedule).replace("\"version\": 4", "\"version\": 99")
        assertNull(BackupManager.parseJson(json))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(BackupManager.parseJson("not json"))
    }

    @Test
    fun `sanitizeCsvCell sanitizes formula injection characters`() {
        assertEquals("'=cmd", BackupManager.sanitizeCsvCell("=cmd"))
        assertEquals("'+cmd", BackupManager.sanitizeCsvCell("+cmd"))
        assertEquals("'-cmd", BackupManager.sanitizeCsvCell("-cmd"))
        assertEquals("'@cmd", BackupManager.sanitizeCsvCell("@cmd"))
        assertEquals("'\tcmd", BackupManager.sanitizeCsvCell("\tcmd"))
        assertEquals("'\rcmd", BackupManager.sanitizeCsvCell("\rcmd"))
        assertEquals("Normal", BackupManager.sanitizeCsvCell("Normal"))
        assertEquals("", BackupManager.sanitizeCsvCell(""))
    }

    @Test
    fun `exportToCsv sanitizes subject names with formula injection prefixes`() {
        val testSubjects = listOf(
            Subject(id = 1, name = "=SUM(A1:A2)"),
            Subject(id = 2, name = "+12345"),
            Subject(id = 3, name = "-Negative"),
            Subject(id = 4, name = "@Admin"),
            Subject(id = 5, name = "\tTabbed"),
            Subject(id = 6, name = "\rCarriage"),
            Subject(id = 7, name = "Safe Subject")
        )
        val testRecords = testSubjects.mapIndexed { idx, s ->
            AttendanceRecord(
                subjectId = s.id,
                date = LocalDate.of(2026, 11, idx + 1),
                status = AttendanceStatus.PRESENT,
                count = 1
            )
        }

        val csv = BackupManager.exportToCsv(testSubjects, testRecords)
        val lines = csv.lines()
        assertEquals("Subject,Date,Status,Count", lines[0])
        org.junit.Assert.assertTrue(csv.contains("\"'=SUM(A1:A2)\",2026-11-01,PRESENT,1"))
        org.junit.Assert.assertTrue(csv.contains("\"'+12345\",2026-11-02,PRESENT,1"))
        org.junit.Assert.assertTrue(csv.contains("\"'-Negative\",2026-11-03,PRESENT,1"))
        org.junit.Assert.assertTrue(csv.contains("\"'@Admin\",2026-11-04,PRESENT,1"))
        org.junit.Assert.assertTrue(csv.contains("\"'\tTabbed\",2026-11-05,PRESENT,1"))
        org.junit.Assert.assertTrue(csv.contains("\"'\rCarriage\",2026-11-06,PRESENT,1"))
        org.junit.Assert.assertTrue(csv.contains("\"Safe Subject\",2026-11-07,PRESENT,1"))
    }
}
