package com.attendance.tracker.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderSchedulerTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `calculateNextReminderEpochMillis targets today when reminder time is in future`() {
        val now = LocalDateTime.of(2026, 11, 17, 10, 0, 0)
        val expected = LocalDateTime.of(2026, 11, 17, 18, 0, 0)
            .atZone(zoneId).toInstant().toEpochMilli()

        val actual = ReminderScheduler.calculateNextReminderEpochMillis(
            now = now,
            reminderHour = 18,
            reminderMinute = 0,
            zoneId = zoneId
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `calculateNextReminderEpochMillis targets tomorrow when reminder time has passed`() {
        val now = LocalDateTime.of(2026, 11, 17, 19, 0, 0)
        val expected = LocalDateTime.of(2026, 11, 18, 18, 0, 0)
            .atZone(zoneId).toInstant().toEpochMilli()

        val actual = ReminderScheduler.calculateNextReminderEpochMillis(
            now = now,
            reminderHour = 18,
            reminderMinute = 0,
            zoneId = zoneId
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `calculateNextReminderEpochMillis targets tomorrow when reminder time is exact match`() {
        val now = LocalDateTime.of(2026, 11, 17, 18, 0, 0)
        val expected = LocalDateTime.of(2026, 11, 18, 18, 0, 0)
            .atZone(zoneId).toInstant().toEpochMilli()

        val actual = ReminderScheduler.calculateNextReminderEpochMillis(
            now = now,
            reminderHour = 18,
            reminderMinute = 0,
            zoneId = zoneId
        )

        assertEquals(expected, actual)
    }
}
