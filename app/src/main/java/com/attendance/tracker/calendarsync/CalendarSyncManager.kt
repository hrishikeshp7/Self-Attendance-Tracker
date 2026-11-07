package com.attendance.tracker.calendarsync

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.data.model.getDisplayName
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Exports the weekly timetable as an .ics (iCalendar) calendar feed.
 *
 * Compatible with Google Calendar, Notion Calendar, Outlook, Apple Calendar,
 * and any calendar app without requiring dangerous calendar permissions.
 */
object CalendarSyncManager {

    /** Last day (inclusive) of the current week, i.e. the default sync window's end. */
    fun endOfCurrentWeek(): LocalDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    fun openInCalendarApp(context: Context, icsContent: String) {
        val dir = File(context.cacheDir, "calendar_exports").apply { mkdirs() }
        val file = File(dir, "timetable.ics")
        file.writeText(icsContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/calendar")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            shareIcs(context, icsContent)
        }
    }

    fun shareIcs(context: Context, icsContent: String) {
        val dir = File(context.cacheDir, "calendar_exports").apply { mkdirs() }
        val file = File(dir, "timetable.ics")
        file.writeText(icsContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Class Timetable (.ics)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Add to Calendar or Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    // -----------------------------------------------------------------------
    // .ics generation
    // -----------------------------------------------------------------------

    private val icsLocalFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val icsUtcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun buildIcsContent(
        scheduleEntries: List<ScheduleEntry>,
        subjectsById: Map<Long, Subject>,
        endDate: LocalDate
    ): String {
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

            val last = minOf(endDate, entry.endDate ?: endDate)
            val anchorDate = nextOrSameDate(maxOf(today, entry.startDate ?: today), entry.dayOfWeek)
            if (anchorDate.isAfter(last)) continue
            val until = last.atTime(23, 59, 59).atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).format(icsUtcFormatter)
            val dtStart = anchorDate.atTime(entry.startTime)
            val isOvernight = !entry.endTime.isAfter(entry.startTime) && entry.endTime != entry.startTime
            val dtEnd = when {
                entry.endTime.isAfter(entry.startTime) -> anchorDate.atTime(entry.endTime)
                isOvernight -> anchorDate.plusDays(1).atTime(entry.endTime)
                else -> dtStart.plusMinutes(60)
            }

            val uid = if (entry.id > 0) "schedule-${entry.id}" else "schedule-${entry.subjectId}-${entry.dayOfWeek}-${entry.startTime}-${entry.startDate}"
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:$uid@selfattendancetracker\r\n")
            sb.append("DTSTAMP:$dtStamp\r\n")
            sb.append("DTSTART;TZID=${zoneId.id}:${dtStart.format(icsLocalFormatter)}\r\n")
            sb.append("DTEND;TZID=${zoneId.id}:${dtEnd.format(icsLocalFormatter)}\r\n")
            sb.append("RRULE:FREQ=WEEKLY;BYDAY=${rruleDay(entry.dayOfWeek)};UNTIL=$until\r\n")
            sb.append("SUMMARY:${icsEscape(subject.getDisplayName(subjectsById))}\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun icsEscape(text: String): String =
        text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n")

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
