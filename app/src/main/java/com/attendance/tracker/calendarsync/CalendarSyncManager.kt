package com.attendance.tracker.calendarsync

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Gets the weekly timetable into other calendar apps two ways:
 *
 *  - On-device sync: writes recurring weekly events straight into a calendar already on
 *    the device (via the standard Android Calendar Provider), such as the user's Google
 *    account calendar. Whatever app renders that account's calendar — Google Calendar,
 *    Notion Calendar once the account is linked there, etc. — then shows the lectures.
 *    Requires READ_CALENDAR/WRITE_CALENDAR at runtime; no accounts or API keys involved.
 *  - .ics export: a standalone file any calendar app can import, needing no permission
 *    at all.
 *
 * Re-syncing is a full refresh: every event this app previously wrote to the target
 * calendar (tagged via [SYNC_MARKER]) is removed and replaced, so edits/deletes to the
 * in-app timetable are reflected rather than accumulating stale duplicates.
 */
object CalendarSyncManager {

    data class DeviceCalendar(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val isPrimary: Boolean
    )

    data class SyncPrefs(
        val calendarId: Long?,
        val calendarLabel: String?,
        val lastSyncedAtEpochMillis: Long?
    )

    private const val PREF_NAME = "calendar_sync_prefs"
    private const val KEY_CALENDAR_ID = "calendar_id"
    private const val KEY_CALENDAR_LABEL = "calendar_label"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"

    // Tags every event this app writes, so a re-sync can find and clear exactly those
    // events (and nothing the user or another app added to the same calendar).
    private const val SYNC_MARKER = "[SelfAttendanceTracker]"

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /** Calendars the user can actually write events into (skips read-only subscriptions). */
    suspend fun listWritableCalendars(context: Context): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val results = mutableListOf<DeviceCalendar>()
        context.applicationContext.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val primaryCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                results.add(
                    DeviceCalendar(
                        id = cursor.getLong(idCol),
                        displayName = cursor.getString(nameCol) ?: "Calendar",
                        accountName = cursor.getString(accountCol) ?: "",
                        isPrimary = cursor.getInt(primaryCol) != 0
                    )
                )
            }
        }
        results.sortedByDescending { it.isPrimary }
    }

    /**
     * Replaces every previously-synced event on [calendarId] with one recurring weekly
     * event per (non-folder) schedule entry. Returns the number of events written.
     */
    suspend fun syncSchedule(
        context: Context,
        calendarId: Long,
        scheduleEntries: List<ScheduleEntry>,
        subjectsById: Map<Long, Subject>
    ): Int = withContext(Dispatchers.IO) {
        removeSyncedEvents(context, calendarId)
        val resolver = context.applicationContext.contentResolver
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        var count = 0

        for (entry in scheduleEntries) {
            val subject = subjectsById[entry.subjectId] ?: continue
            if (subject.isFolder) continue

            val anchorDate = nextOrSameDate(today, entry.dayOfWeek)
            val dtStart = anchorDate.atTime(entry.startTime).atZone(zoneId).toInstant().toEpochMilli()
            val durationSeconds = java.time.Duration.between(entry.startTime, entry.endTime).seconds

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, subject.getDisplayName(subjectsById))
                put(CalendarContract.Events.DESCRIPTION, SYNC_MARKER)
                put(CalendarContract.Events.DTSTART, dtStart)
                // A recurring event (one with an RRULE) must be given its length via DURATION,
                // not DTEND — the Calendar Provider rejects (or silently mishandles) an insert
                // that sets both DTEND and RRULE together.
                put(CalendarContract.Events.DURATION, "PT${durationSeconds}S")
                put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
                put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=${rruleDay(entry.dayOfWeek)}")
                put(CalendarContract.Events.HAS_ALARM, 0)
            }
            if (resolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) {
                count++
            }
        }
        count
    }

    /** Removes every event this app previously wrote to [calendarId]. Returns how many. */
    suspend fun removeSyncedEvents(context: Context, calendarId: Long): Int = withContext(Dispatchers.IO) {
        context.applicationContext.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} = ?",
            arrayOf(calendarId.toString(), SYNC_MARKER)
        )
    }

    fun getSyncPrefs(context: Context): SyncPrefs {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val calendarId = prefs.getLong(KEY_CALENDAR_ID, -1L).takeIf { it >= 0 }
        return SyncPrefs(
            calendarId = calendarId,
            calendarLabel = prefs.getString(KEY_CALENDAR_LABEL, null),
            lastSyncedAtEpochMillis = prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }
        )
    }

    fun saveSyncPrefs(context: Context, calendarId: Long, calendarLabel: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CALENDAR_ID, calendarId)
            .putString(KEY_CALENDAR_LABEL, calendarLabel)
            .putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearSyncPrefs(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // -----------------------------------------------------------------------
    // .ics export — no permission needed, works with any calendar app
    // -----------------------------------------------------------------------

    private val icsLocalFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val icsUtcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun buildIcsContent(scheduleEntries: List<ScheduleEntry>, subjectsById: Map<Long, Subject>): String {
        val zoneId = ZoneId.systemDefault()
        val dtStamp = Instant.now().atZone(ZoneOffset.UTC).format(icsUtcFormatter)
        val today = LocalDate.now()

        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Self Attendance Tracker//Timetable//EN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")

        for (entry in scheduleEntries) {
            val subject = subjectsById[entry.subjectId] ?: continue
            if (subject.isFolder) continue

            val anchorDate = nextOrSameDate(today, entry.dayOfWeek)
            val dtStart = anchorDate.atTime(entry.startTime)
            val dtEnd = anchorDate.atTime(entry.endTime)

            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:schedule-${entry.id}@selfattendancetracker\r\n")
            sb.append("DTSTAMP:$dtStamp\r\n")
            sb.append("DTSTART;TZID=${zoneId.id}:${dtStart.format(icsLocalFormatter)}\r\n")
            sb.append("DTEND;TZID=${zoneId.id}:${dtEnd.format(icsLocalFormatter)}\r\n")
            sb.append("RRULE:FREQ=WEEKLY;BYDAY=${rruleDay(entry.dayOfWeek)}\r\n")
            sb.append("SUMMARY:${icsEscape(subject.getDisplayName(subjectsById))}\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun icsEscape(text: String): String =
        text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun nextOrSameDate(from: LocalDate, dayOfWeek: DayOfWeek): LocalDate {
        var date = from
        while (date.dayOfWeek != dayOfWeek) date = date.plusDays(1)
        return date
    }

    private fun rruleDay(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }
}
