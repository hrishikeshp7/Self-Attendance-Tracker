package com.attendance.tracker.data

import android.content.SharedPreferences
import com.attendance.tracker.data.model.AttendanceStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
