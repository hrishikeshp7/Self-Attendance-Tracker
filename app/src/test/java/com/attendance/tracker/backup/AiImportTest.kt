package com.attendance.tracker.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AiImportTest {

    private val good = """
        Sure! Here you go:
        ```json
        {"version":1,"subjects":[
          {"name":"Physics","target":75,"slots":[{"day":"MON","start":"09:00","end":"10:00"},{"day":"Wednesday","start":"9:00 AM","end":"10:00 AM"}]},
          {"name":"Chem, Lab","slots":[{"day":"fri","start":"14:00","end":"16:00"}]}
        ]}
        ```
        Let me know!
    """.trimIndent()

    @Test
    fun `reads a reply wrapped in chatter and a code fence`() {
        val r = AiImport.parse(good)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(listOf("Physics", "Chem, Lab"), r.subjects.map { it.name })
        assertEquals(75, r.subjects[0].requiredAttendance)
        assertEquals(null, r.subjects[1].requiredAttendance)
        assertEquals(3, r.slots.size)
        assertEquals(DayOfWeek.FRIDAY, r.slots[2].day)
        assertEquals(LocalTime.of(16, 0), r.slots[2].end)
    }

    @Test
    fun `names each problem`() {
        assertTrue(AiImport.parse("no json here").errors.single().contains("No JSON"))
        assertTrue(AiImport.parse("{oops}").errors.single().contains("valid JSON"))
        val r = AiImport.parse("""{"subjects":[{"name":"A","target":150,"slots":[{"day":"Funday","start":"09:00","end":"10:00"},{"day":"MON","start":"10:00","end":"09:00"}]},{"slots":[]}]}""")
        assertEquals(4, r.errors.size)
    }

    @Test
    fun `newer format is rejected and repeated slot counts once`() {
        assertTrue(AiImport.parse("""{"version":2,"subjects":[]}""").errors.single().contains("newer"))
        val slot = """{"day":"MON","start":"09:00","end":"10:00"}"""
        assertEquals(1, AiImport.parse("""{"subjects":[{"name":"A","slots":[$slot,$slot]}]}""").slots.size)
    }

    @Test
    fun `period and one-off dates are carried onto the slots`() {
        val r = AiImport.parse("""{"from":"2026-08-03","until":"2026-12-18","subjects":[{"name":"A","slots":[
            {"day":"MON","start":"09:00","end":"10:00"},{"date":"2026-09-15","start":"11:00","end":"12:00"}]}]}""")
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(java.time.LocalDate.of(2026, 8, 3), r.slots[0].startDate)
        assertEquals(java.time.LocalDate.of(2026, 12, 18), r.slots[0].endDate)
        // a dated slot needs no day: it is worked out from the date (15 Sep 2026 is a Tuesday)
        assertEquals(DayOfWeek.TUESDAY, r.slots[1].day)
        assertEquals(java.time.LocalDate.of(2026, 9, 15), r.slots[1].startDate)
        assertEquals(r.slots[1].startDate, r.slots[1].endDate)
    }

    @Test
    fun `bad dates are reported`() {
        assertEquals(2, AiImport.parse("""{"from":"3rd Aug","until":"2026-13-40","subjects":[{"name":"A"}]}""").errors.size)
        assertTrue(AiImport.parse("""{"from":"2026-12-01","until":"2026-08-01","subjects":[{"name":"A"}]}""").errors.single().contains("before"))
        assertTrue(AiImport.parse("""{"subjects":[{"name":"A","slots":[{"day":"MON","date":"2026-09-15","start":"09:00","end":"10:00"}]}]}""").errors.single().contains("not MON"))
    }
}
