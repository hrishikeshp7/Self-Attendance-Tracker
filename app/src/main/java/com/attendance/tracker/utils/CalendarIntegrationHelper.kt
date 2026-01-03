package com.attendance.tracker.utils

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

/**
 * Helper for syncing with device calendar
 */
object CalendarIntegrationHelper {
    
    private const val CALENDAR_NAME = "Attendance Tracker Schedule"
    private const val CALENDAR_ACCOUNT_NAME = "com.attendance.tracker"
    private const val CALENDAR_ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    
    /**
     * Check if calendar permissions are granted
     */
    fun hasCalendarPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get or create the app's calendar
     */
    suspend fun getOrCreateCalendar(context: Context): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                if (!hasCalendarPermissions(context)) {
                    return@withContext Result.failure(SecurityException("Calendar permissions not granted"))
                }
                
                val contentResolver = context.contentResolver
                
                // Check if calendar already exists
                val projection = arrayOf(CalendarContract.Calendars._ID)
                val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
                val selectionArgs = arrayOf(CALENDAR_ACCOUNT_NAME, CALENDAR_ACCOUNT_TYPE)
                
                contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val calendarId = cursor.getLong(0)
                        return@withContext Result.success(calendarId)
                    }
                }
                
                // Create new calendar
                val values = ContentValues().apply {
                    put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
                    put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                    put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
                    put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1976D2.toInt())
                    put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                    put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT_NAME)
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                }
                
                val uri = contentResolver.insert(
                    CalendarContract.Calendars.CONTENT_URI.buildUpon()
                        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                        .build(),
                    values
                )
                
                val calendarId = uri?.lastPathSegment?.toLongOrNull()
                    ?: return@withContext Result.failure(Exception("Failed to create calendar"))
                
                Result.success(calendarId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Export schedule to calendar as recurring events
     * @param subjects Map of subject ID to Subject
     * @param scheduleEntries List of schedule entries
     * @param startTime Default start time for classes
     * @param duration Duration of each class in minutes
     * @param startDate Date to start recurring events from
     * @param endDate Date to end recurring events
     */
    suspend fun exportScheduleToCalendar(
        context: Context,
        subjects: Map<Long, Subject>,
        scheduleEntries: List<ScheduleEntry>,
        startTime: LocalTime = LocalTime.of(9, 0),
        duration: Int = 60,
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate = startDate.plusMonths(3)
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val calendarResult = getOrCreateCalendar(context)
                if (calendarResult.isFailure) {
                    return@withContext Result.failure(calendarResult.exceptionOrNull()!!)
                }
                
                val calendarId = calendarResult.getOrThrow()
                val contentResolver = context.contentResolver
                var eventsCreated = 0
                
                // Group schedule entries by subject
                val entriesBySubject = scheduleEntries.groupBy { it.subjectId }
                
                entriesBySubject.forEach { (subjectId, entries) ->
                    val subject = subjects[subjectId] ?: return@forEach
                    if (subject.isFolder) return@forEach
                    
                    entries.forEach { entry ->
                        // Create recurring event for each day
                        val eventValues = ContentValues().apply {
                            put(CalendarContract.Events.CALENDAR_ID, calendarId)
                            put(CalendarContract.Events.TITLE, subject.name)
                            put(CalendarContract.Events.DESCRIPTION, "Class for ${subject.name}")
                            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                            
                            // Set start and end time
                            val dayOfWeek = entry.dayOfWeek
                            val firstOccurrence = getNextDayOfWeek(startDate, dayOfWeek)
                            val startDateTime = firstOccurrence.atTime(startTime)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val endDateTime = firstOccurrence.atTime(startTime.plusMinutes(duration.toLong()))
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            
                            put(CalendarContract.Events.DTSTART, startDateTime)
                            put(CalendarContract.Events.DTEND, endDateTime)
                            
                            // Set recurrence rule (weekly until end date)
                            val rrule = "FREQ=WEEKLY;UNTIL=${formatDateForRRule(endDate)}"
                            put(CalendarContract.Events.RRULE, rrule)
                            
                            put(CalendarContract.Events.EVENT_COLOR, 0xFF1976D2.toInt())
                        }
                        
                        contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
                        eventsCreated++
                    }
                }
                
                Result.success(eventsCreated)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Import calendar events to create schedule entries
     * @param calendarId ID of calendar to import from
     * @param startDate Start date for import
     * @param endDate End date for import
     * @return List of imported events with their details
     */
    suspend fun importCalendarEvents(
        context: Context,
        calendarId: Long,
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate = startDate.plusMonths(1)
    ): Result<List<CalendarEventInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!hasCalendarPermissions(context)) {
                    return@withContext Result.failure(SecurityException("Calendar permissions not granted"))
                }
                
                val contentResolver = context.contentResolver
                val events = mutableListOf<CalendarEventInfo>()
                
                val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = endDate.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.RRULE
                )
                
                val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
                val selectionArgs = arrayOf(calendarId.toString(), startMillis.toString(), endMillis.toString())
                
                contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
                    val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                    val descIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val rruleIndex = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                    
                    while (cursor.moveToNext()) {
                        val eventId = cursor.getLong(idIndex)
                        val title = cursor.getString(titleIndex) ?: ""
                        val startTime = cursor.getLong(startIndex)
                        val endTime = cursor.getLong(endIndex)
                        val description = cursor.getString(descIndex) ?: ""
                        val rrule = cursor.getString(rruleIndex)
                        
                        val startLocalDateTime = LocalDate.ofInstant(
                            java.time.Instant.ofEpochMilli(startTime),
                            ZoneId.systemDefault()
                        )
                        
                        events.add(
                            CalendarEventInfo(
                                id = eventId,
                                title = title,
                                startDate = startLocalDateTime,
                                dayOfWeek = startLocalDateTime.dayOfWeek,
                                description = description,
                                isRecurring = rrule != null
                            )
                        )
                    }
                }
                
                Result.success(events)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get next occurrence of a specific day of week
     */
    private fun getNextDayOfWeek(from: LocalDate, dayOfWeek: DayOfWeek): LocalDate {
        var date = from
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }
        return date
    }
    
    /**
     * Format date for RRULE (YYYYMMDD format with T235959Z)
     */
    private fun formatDateForRRule(date: LocalDate): String {
        return String.format("%04d%02d%02dT235959Z", date.year, date.monthValue, date.dayOfMonth)
    }
    
    /**
     * Data class for calendar event information
     */
    data class CalendarEventInfo(
        val id: Long,
        val title: String,
        val startDate: LocalDate,
        val dayOfWeek: DayOfWeek,
        val description: String,
        val isRecurring: Boolean
    )
}
