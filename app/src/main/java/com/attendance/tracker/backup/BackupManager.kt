package com.attendance.tracker.backup

import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Handles serialisation and deserialisation of attendance data for backup / restore.
 *
 * Supported formats:
 *  - JSON  – full backup/restore (subjects + attendance records + schedule entries)
 *  - CSV   – attendance records only (human-readable export)
 *
 * Google Drive is supported transparently: a dedicated SAF launcher in the UI sets
 * `DocumentsContract.EXTRA_INITIAL_URI` to the Google Drive root
 * (`com.google.android.apps.docs.storage / mydrive`), so the system file-picker
 * opens directly inside Google Drive.  No OAuth or Google Drive SDK is needed – the
 * file is written via the standard `ContentResolver`.
 */
object BackupManager {

    private const val BACKUP_VERSION = 2

    // -----------------------------------------------------------------------
    // JSON export
    // -----------------------------------------------------------------------

    fun exportToJson(
        subjects: List<Subject>,
        attendanceRecords: List<AttendanceRecord>,
        scheduleEntries: List<ScheduleEntry>
    ): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportDate", LocalDate.now().toString())

        // Subjects
        val subjectsArray = JSONArray()
        subjects.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("requiredAttendance", s.requiredAttendance)
            obj.put("totalLectures", s.totalLectures)
            obj.put("presentLectures", s.presentLectures)
            obj.put("absentLectures", s.absentLectures)
            obj.put("parentSubjectId", s.parentSubjectId ?: JSONObject.NULL)
            obj.put("isFolder", s.isFolder)
            subjectsArray.put(obj)
        }
        root.put("subjects", subjectsArray)

        // Attendance records
        val recordsArray = JSONArray()
        attendanceRecords.forEach { r ->
            val obj = JSONObject()
            obj.put("subjectId", r.subjectId)
            obj.put("date", r.date.toString())
            obj.put("status", r.status.name)
            obj.put("count", r.count)
            obj.put("isExtraClass", r.isExtraClass)
            recordsArray.put(obj)
        }
        root.put("attendanceRecords", recordsArray)

        // Schedule entries
        val scheduleArray = JSONArray()
        scheduleEntries.forEach { e ->
            val obj = JSONObject()
            obj.put("subjectId", e.subjectId)
            obj.put("dayOfWeek", e.dayOfWeek.name)
            obj.put("isScheduled", e.isScheduled)
            scheduleArray.put(obj)
        }
        root.put("scheduleEntries", scheduleArray)

        return root.toString(2) // pretty-print with 2-space indent
    }

    // -----------------------------------------------------------------------
    // JSON import / restore
    // -----------------------------------------------------------------------

    data class BackupData(
        val subjects: List<Subject>,
        val attendanceRecords: List<AttendanceRecord>,
        val scheduleEntries: List<ScheduleEntry>
    )

    /**
     * Parses a JSON backup string.  Returns null if the string is not valid backup JSON.
     */
    fun parseJson(json: String): BackupData? {
        return try {
            val root = JSONObject(json)

            // --- subjects ---
            val subjectsArray = root.getJSONArray("subjects")
            val subjects = (0 until subjectsArray.length()).map { i ->
                val obj = subjectsArray.getJSONObject(i)
                Subject(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    requiredAttendance = obj.optInt("requiredAttendance", 75),
                    totalLectures = obj.optInt("totalLectures", 0),
                    presentLectures = obj.optInt("presentLectures", 0),
                    absentLectures = obj.optInt("absentLectures", 0),
                    parentSubjectId = if (obj.isNull("parentSubjectId")) null
                                      else obj.getLong("parentSubjectId"),
                    isFolder = obj.optBoolean("isFolder", false)
                )
            }

            // --- attendance records ---
            val recordsArray = root.getJSONArray("attendanceRecords")
            val attendanceRecords = (0 until recordsArray.length()).map { i ->
                val obj = recordsArray.getJSONObject(i)
                AttendanceRecord(
                    subjectId = obj.getLong("subjectId"),
                    date = LocalDate.parse(obj.getString("date")),
                    status = AttendanceStatus.valueOf(obj.getString("status")),
                    count = obj.optInt("count", 1),
                    isExtraClass = obj.optBoolean("isExtraClass", false)
                )
            }

            // --- schedule entries ---
            val scheduleArray = root.getJSONArray("scheduleEntries")
            val scheduleEntries = (0 until scheduleArray.length()).map { i ->
                val obj = scheduleArray.getJSONObject(i)
                ScheduleEntry(
                    subjectId = obj.getLong("subjectId"),
                    dayOfWeek = DayOfWeek.valueOf(obj.getString("dayOfWeek")),
                    isScheduled = obj.optBoolean("isScheduled", true)
                )
            }

            BackupData(subjects, attendanceRecords, scheduleEntries)
        } catch (e: Exception) {
            null
        }
    }

    // -----------------------------------------------------------------------
    // CSV export (attendance records, human-readable)
    // -----------------------------------------------------------------------

    fun exportToCsv(
        subjects: List<Subject>,
        attendanceRecords: List<AttendanceRecord>
    ): String {
        val subjectMap = subjects.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("Subject,Date,Status,Count,IsExtraClass")
        attendanceRecords.sortedWith(compareBy({ it.date }, { it.subjectId }))
            .forEach { r ->
                val name = subjectMap[r.subjectId]?.name ?: r.subjectId.toString()
                sb.appendLine("${csvEscape(name)},${r.date},${r.status.name},${r.count},${r.isExtraClass}")
            }
        return sb.toString()
    }

    /** Wraps a CSV field in double quotes and escapes internal double-quote characters. */
    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
