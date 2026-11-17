package com.attendance.tracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WidgetDayRolloverTest {

    @Test
    fun `calculateInitialDelaySeconds anchors to 00 00 30 next day`() {
        val now = LocalDateTime.of(2026, 11, 17, 23, 59, 30)
        val delay = WidgetDayRollover.calculateInitialDelaySeconds(now)
        // 30 seconds until midnight + 30 seconds after midnight = 60 seconds
        assertEquals(60L, delay)
    }

    @Test
    fun `calculateInitialDelaySeconds at noon computes delay until next midnight plus 30s`() {
        val now = LocalDateTime.of(2026, 11, 17, 12, 0, 0)
        val delay = WidgetDayRollover.calculateInitialDelaySeconds(now)
        // 12 hours (43200s) + 30s = 43230s
        assertEquals(43230L, delay)
    }

    @Test
    fun `calculateInitialDelaySeconds at exactly 00 00 30 anchors to next midnight`() {
        val now = LocalDateTime.of(2026, 11, 17, 0, 0, 30)
        val delay = WidgetDayRollover.calculateInitialDelaySeconds(now)
        // 24 hours = 86400s
        assertEquals(86400L, delay)
    }
}
