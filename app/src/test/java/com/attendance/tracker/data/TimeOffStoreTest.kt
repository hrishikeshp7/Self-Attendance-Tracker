package com.attendance.tracker.data

import android.content.SharedPreferences
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.repository.AttendanceRepository
import com.attendance.tracker.data.repository.FakeAttendanceDao
import com.attendance.tracker.data.repository.FakeScheduleDao
import com.attendance.tracker.data.repository.FakeSubjectDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TimeOffStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val storage = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (storage[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (storage[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (storage[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (storage[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (storage[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values?.toSet()
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removed.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) storage.clear()
                for (k in removed) storage.remove(k)
                storage.putAll(pending)
            }
        }
    }

    @Test
    fun `isVacationAbsent returns true within recorded absent range and false outside`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)

        // Before recording
        assertFalse(TimeOffStore.isVacationAbsent(prefs, monday))

        // Record absent time-off Mon-Fri
        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.ABSENT)

        // Inside range
        assertTrue(TimeOffStore.isVacationAbsent(prefs, monday))
        assertTrue(TimeOffStore.isVacationAbsent(prefs, monday.plusDays(2))) // Wednesday
        assertTrue(TimeOffStore.isVacationAbsent(prefs, friday))

        // Outside range
        assertFalse(TimeOffStore.isVacationAbsent(prefs, monday.minusDays(1))) // Sunday
        assertFalse(TimeOffStore.isVacationAbsent(prefs, friday.plusDays(1))) // Saturday
    }

    @Test
    fun `resetting time off clears absent range`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)

        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.ABSENT)
        assertTrue(TimeOffStore.isVacationAbsent(prefs, monday))

        // Clear / reset
        TimeOffStore.recordTimeOff(prefs, monday, friday, null)
        assertFalse(TimeOffStore.isVacationAbsent(prefs, monday))
    }

    @Test
    fun `applying holiday time off removes overlapping absent range`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)

        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.ABSENT)
        assertTrue(TimeOffStore.isVacationAbsent(prefs, monday))

        // Overwrite with NO_CLASS (Holiday)
        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.NO_CLASS)
        assertFalse(TimeOffStore.isVacationAbsent(prefs, monday))
    }

    @Test
    fun `getActiveRules parses rules with subjectIds properly`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)

        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.ABSENT, setOf(1L, 2L))
        val rules = TimeOffStore.getActiveRules(prefs)
        org.junit.Assert.assertEquals(1, rules.size)
        org.junit.Assert.assertEquals(monday, rules[0].startDate)
        org.junit.Assert.assertEquals(friday, rules[0].endDate)
        org.junit.Assert.assertEquals(AttendanceStatus.ABSENT, rules[0].status)
        org.junit.Assert.assertEquals(setOf(1L, 2L), rules[0].subjectIds)
    }

    @Test
    fun `getActiveRuleForDate returns matching rule within date range`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)

        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.ABSENT)
        val ruleMon = TimeOffStore.getActiveRuleForDate(prefs, monday)
        org.junit.Assert.assertNotNull(ruleMon)
        org.junit.Assert.assertEquals(AttendanceStatus.ABSENT, ruleMon?.status)
        org.junit.Assert.assertEquals(monday, ruleMon?.startDate)
        org.junit.Assert.assertEquals(friday, ruleMon?.endDate)

        val ruleWed = TimeOffStore.getActiveRuleForDate(prefs, monday.plusDays(2))
        org.junit.Assert.assertNotNull(ruleWed)

        val ruleSun = TimeOffStore.getActiveRuleForDate(prefs, monday.minusDays(1))
        org.junit.Assert.assertNull(ruleSun)

        val ruleNextSat = TimeOffStore.getActiveRuleForDate(prefs, friday.plusDays(1))
        org.junit.Assert.assertNull(ruleNextSat)
    }

    @Test
    fun `setRules replaces active rules and round-trips`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)

        val rulesToSet = listOf(
            TimeOffRule(monday, friday, AttendanceStatus.ABSENT, setOf(5L, 6L))
        )
        TimeOffStore.setRules(prefs, rulesToSet)

        val loaded = TimeOffStore.getActiveRules(prefs)
        org.junit.Assert.assertEquals(1, loaded.size)
        org.junit.Assert.assertEquals(monday, loaded[0].startDate)
        org.junit.Assert.assertEquals(friday, loaded[0].endDate)
        org.junit.Assert.assertEquals(AttendanceStatus.ABSENT, loaded[0].status)
        org.junit.Assert.assertEquals(setOf(5L, 6L), loaded[0].subjectIds)
    }

    @Test
    fun `resetting a specific subject from an all-subject rule preserves other subjects and outer dates`() {
        val prefs = FakeSharedPreferences()
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)
        val wednesday = monday.plusDays(2)

        // Set ALL subjects holiday for Monday..Friday
        TimeOffStore.recordTimeOff(prefs, monday, friday, AttendanceStatus.NO_CLASS)

        // Reset Wednesday for subject 1L only (allSubjectIds = {1L, 2L})
        TimeOffStore.recordTimeOff(
            prefs = prefs,
            startDate = wednesday,
            endDate = wednesday,
            status = null,
            subjectIds = setOf(1L),
            allSubjectIds = setOf(1L, 2L)
        )

        val rules = TimeOffStore.getActiveRules(prefs)
        // Rule for Mon..Tue preserved
        val monRule = TimeOffStore.getActiveRuleForDate(prefs, monday, 1L)
        org.junit.Assert.assertNotNull(monRule)
        org.junit.Assert.assertEquals(AttendanceStatus.NO_CLASS, monRule?.status)

        // Rule for Thu..Fri preserved
        val thuRule = TimeOffStore.getActiveRuleForDate(prefs, friday, 1L)
        org.junit.Assert.assertNotNull(thuRule)
        org.junit.Assert.assertEquals(AttendanceStatus.NO_CLASS, thuRule?.status)

        // Wednesday for subject 1L is CLEARED (null)
        val wedRuleSub1 = TimeOffStore.getActiveRuleForDate(prefs, wednesday, 1L)
        org.junit.Assert.assertNull(wedRuleSub1)

        // Wednesday for subject 2L is KEPT (NO_CLASS)
        val wedRuleSub2 = TimeOffStore.getActiveRuleForDate(prefs, wednesday, 2L)
        org.junit.Assert.assertNotNull(wedRuleSub2)
        org.junit.Assert.assertEquals(AttendanceStatus.NO_CLASS, wedRuleSub2?.status)
    }

    @Test
    fun `newly scheduled subject does not inherit past time off absences after previous subject removed`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val subjectDao = FakeSubjectDao()
        val attendanceDao = FakeAttendanceDao()
        val scheduleDao = FakeScheduleDao()
        val repository = AttendanceRepository(subjectDao, attendanceDao, scheduleDao)

        val pastMonday = LocalDate.of(2026, 11, 2)
        val pastFriday = LocalDate.of(2026, 11, 6)
        val today = LocalDate.of(2026, 11, 16) // 2 weeks later

        val subjectA = Subject(id = 1L, name = "Subject A")
        val subjectB = Subject(id = 2L, name = "Subject B")
        subjectDao.insertSubject(subjectA)
        subjectDao.insertSubject(subjectB)

        // 1. Subject A is scheduled on Mondays
        val scheduleEntryA = ScheduleEntry(id = 1L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY)
        scheduleDao.insertScheduleEntry(scheduleEntryA)

        // Mark absent via time off for past week (Nov 2 - Nov 6) for All Subjects (subjectIds = null)
        TimeOffStore.recordTimeOff(prefs, pastMonday, pastFriday, AttendanceStatus.ABSENT)
        repository.applyTimeOff(pastMonday, pastFriday, AttendanceStatus.ABSENT, today = today)

        // Verify Subject A was marked absent for past Monday
        val recordA = repository.getAttendanceRecord(1L, pastMonday)
        assertNotNull(recordA)
        assertEquals(AttendanceStatus.ABSENT, recordA?.status)
        assertEquals(1, subjectDao.getSubjectById(1L)?.absentLectures)
        assertEquals(1, subjectDao.getSubjectById(1L)?.totalLectures)

        // 2. Subject A is removed from schedule
        scheduleDao.deleteScheduleEntry(scheduleEntryA)

        // Subject B is scheduled today with an ongoing recurrence (startDate == null)
        val scheduleEntryB = ScheduleEntry(id = 2L, subjectId = 2L, dayOfWeek = DayOfWeek.MONDAY)
        scheduleDao.insertScheduleEntry(scheduleEntryB)

        // 3. syncActiveTimeOff runs today
        TimeOffStore.syncActiveTimeOff(prefs, repository, today = today)

        // 4. Subject B must NOT get marked absent for that past week
        val recordB = repository.getAttendanceRecord(2L, pastMonday)
        assertNull(recordB)
        assertEquals(0, subjectDao.getSubjectById(2L)?.absentLectures)
        assertEquals(0, subjectDao.getSubjectById(2L)?.totalLectures)
    }

    @Test
    fun `active time off rolls over to today but does not backfill past days for newly scheduled subjects`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val subjectDao = FakeSubjectDao()
        val attendanceDao = FakeAttendanceDao()
        val scheduleDao = FakeScheduleDao()
        val repository = AttendanceRepository(subjectDao, attendanceDao, scheduleDao)

        val mon = LocalDate.of(2026, 11, 16)
        val wed = LocalDate.of(2026, 11, 18)
        val fri = LocalDate.of(2026, 11, 20)

        val subjectA = Subject(id = 1L, name = "Subject A")
        val subjectB = Subject(id = 2L, name = "Subject B")
        subjectDao.insertSubject(subjectA)
        subjectDao.insertSubject(subjectB)

        // Subject A is scheduled on Mondays and Wednesdays
        val entryA = ScheduleEntry(id = 1L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY)
        scheduleDao.insertScheduleEntry(entryA)

        // Record time off for Mon..Fri as ABSENT
        TimeOffStore.recordTimeOff(prefs, mon, fri, AttendanceStatus.ABSENT)
        repository.applyTimeOff(mon, fri, AttendanceStatus.ABSENT, today = mon)

        // Subject A marked absent on Monday
        assertNotNull(repository.getAttendanceRecord(1L, mon))

        // On Wednesday (today), Subject A is removed from schedule and Subject B is scheduled for Mondays & Wednesdays
        scheduleDao.deleteScheduleEntry(entryA)
        val entryBMon = ScheduleEntry(id = 2L, subjectId = 2L, dayOfWeek = DayOfWeek.MONDAY, startDate = wed)
        val entryBWed = ScheduleEntry(id = 3L, subjectId = 2L, dayOfWeek = DayOfWeek.WEDNESDAY, startDate = wed)
        scheduleDao.insertScheduleEntry(entryBMon)
        scheduleDao.insertScheduleEntry(entryBWed)

        // syncActiveTimeOff runs on Wednesday
        TimeOffStore.syncActiveTimeOff(prefs, repository, today = wed)

        // Subject B must NOT be marked absent for Monday
        assertNull(repository.getAttendanceRecord(2L, mon))
        // Subject B SHOULD be marked absent for Wednesday (today, which is during active time off)
        val recBWed = repository.getAttendanceRecord(2L, wed)
        assertNotNull(recBWed)
        assertEquals(AttendanceStatus.ABSENT, recBWed?.status)
        assertEquals(1, subjectDao.getSubjectById(2L)?.absentLectures)
    }

    @Test
    fun `syncActiveTimeOff ignores expired rules completely even when schedule entries have null startDate`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val subjectDao = FakeSubjectDao()
        val attendanceDao = FakeAttendanceDao()
        val scheduleDao = FakeScheduleDao()
        val repository = AttendanceRepository(subjectDao, attendanceDao, scheduleDao)

        val pastMon = LocalDate.of(2026, 10, 12)
        val pastFri = LocalDate.of(2026, 10, 16)
        val today = LocalDate.of(2026, 11, 16)

        val subject = Subject(id = 10L, name = "Chemistry")
        subjectDao.insertSubject(subject)

        // Record past time off
        TimeOffStore.recordTimeOff(prefs, pastMon, pastFri, AttendanceStatus.ABSENT)

        // Chemistry is scheduled on Mondays with null startDate
        scheduleDao.insertScheduleEntry(ScheduleEntry(id = 100L, subjectId = 10L, dayOfWeek = DayOfWeek.MONDAY, startDate = null))

        // Run syncActiveTimeOff
        TimeOffStore.syncActiveTimeOff(prefs, repository, today = today)

        // No past records should be created
        val pastRecords = repository.getAttendanceInRangeOnce(pastMon, pastFri)
        assertTrue(pastRecords.isEmpty())
        assertEquals(0, subjectDao.getSubjectById(10L)?.absentLectures)
    }

    @Test
    fun `getActiveRuleForDate with rules list evaluates active rule in-memory correctly`() {
        val start = LocalDate.of(2026, 11, 2)
        val end = LocalDate.of(2026, 11, 6)
        val rules = listOf(
            TimeOffRule(start, end, AttendanceStatus.NO_CLASS, setOf(1L, 2L))
        )

        // Matching date and subject
        val rule1 = TimeOffStore.getActiveRuleForDate(rules, LocalDate.of(2026, 11, 3), 1L)
        assertNotNull(rule1)
        assertEquals(AttendanceStatus.NO_CLASS, rule1?.status)

        // Matching date without subject filter
        val ruleAll = TimeOffStore.getActiveRuleForDate(rules, LocalDate.of(2026, 11, 3))
        assertNotNull(ruleAll)

        // Matching date but different subject
        val ruleDiffSubject = TimeOffStore.getActiveRuleForDate(rules, LocalDate.of(2026, 11, 3), 3L)
        assertNull(ruleDiffSubject)

        // Outside date range
        val ruleOutside = TimeOffStore.getActiveRuleForDate(rules, LocalDate.of(2026, 11, 7), 1L)
        assertNull(ruleOutside)
    }

    @Test
    fun `unscheduled new subject during active time off does not get marked absent for past days`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val subjectDao = FakeSubjectDao()
        val attendanceDao = FakeAttendanceDao()
        val scheduleDao = FakeScheduleDao()
        val repository = AttendanceRepository(subjectDao, attendanceDao, scheduleDao)

        val mon = LocalDate.of(2026, 11, 16)
        val wed = LocalDate.of(2026, 11, 18)
        val fri = LocalDate.of(2026, 11, 20)

        // Time off is recorded for Mon..Fri with no timetable schedule
        TimeOffStore.recordTimeOff(prefs, mon, fri, AttendanceStatus.ABSENT)

        // Subject created on Wednesday (totalLectures = 0, no past records)
        val subject = Subject(id = 5L, name = "New Subject", totalLectures = 0, presentLectures = 0, absentLectures = 0)
        subjectDao.insertSubject(subject)

        // Run syncActiveTimeOff on Wednesday
        TimeOffStore.syncActiveTimeOff(prefs, repository, today = wed)

        // New subject must NOT have records for past Monday
        val pastRec = repository.getAttendanceRecord(5L, mon)
        assertNull(pastRec)
        // New subject DOES get marked for Wednesday (today)
        val todayRec = repository.getAttendanceRecord(5L, wed)
        assertNotNull(todayRec)
        assertEquals(AttendanceStatus.ABSENT, todayRec?.status)
    }
}
