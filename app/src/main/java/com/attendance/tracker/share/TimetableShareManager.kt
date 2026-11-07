package com.attendance.tracker.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class SharedSubject(
    val name: String,
    val requiredAttendance: Int = 75
)

data class SharedLecture(
    val subjectIndex: Int,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)

data class SharedTimetable(
    val title: String,
    val subjects: List<SharedSubject>,
    val lectures: List<SharedLecture>
)

/**
 * Handles encoding, decoding, QR generation, and deep-linking for 1-tap timetable sharing.
 * Compresses timetable payloads via GZIP + Base64 (URL-safe) to fit in QR codes and deep links.
 */
object TimetableShareManager {

    private const val DEEP_LINK_SCHEME = "attendance"
    private const val DEEP_LINK_HOST = "import"
    private const val QUERY_PARAM = "tt"

    /**
     * Converts current app schedule entries and subjects into a [SharedTimetable].
     */
    fun createSharedTimetable(
        title: String,
        scheduleEntries: List<ScheduleEntry>,
        subjectsMap: Map<Long, Subject>
    ): SharedTimetable {
        val uniqueSubjectIds = scheduleEntries.map { it.subjectId }.distinct()
        val subjectsList = uniqueSubjectIds.mapNotNull { subjectsMap[it] }.filter { !it.isFolder }
        val idToIndex = subjectsList.mapIndexed { index, subject -> subject.id to index }.toMap()

        val lectures = scheduleEntries.mapNotNull { entry ->
            val index = idToIndex[entry.subjectId] ?: return@mapNotNull null
            SharedLecture(
                subjectIndex = index,
                dayOfWeek = entry.dayOfWeek,
                startTime = entry.startTime,
                endTime = entry.endTime,
                startDate = entry.startDate,
                endDate = entry.endDate
            )
        }

        val sharedSubjects = subjectsList.map {
            SharedSubject(name = it.name, requiredAttendance = it.requiredAttendance)
        }

        return SharedTimetable(
            title = title.ifBlank { "Class Timetable" },
            subjects = sharedSubjects,
            lectures = lectures
        )
    }

    /**
     * Compresses and encodes [timetable] into a compact URL-safe Base64 string.
     */
    fun encode(timetable: SharedTimetable): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("t", timetable.title)

        val subArray = JSONArray()
        timetable.subjects.forEach { s ->
            val obj = JSONObject()
            obj.put("n", s.name)
            if (s.requiredAttendance != 75) obj.put("r", s.requiredAttendance)
            subArray.put(obj)
        }
        root.put("s", subArray)

        val lecArray = JSONArray()
        timetable.lectures.forEach { l ->
            val obj = JSONObject()
            obj.put("i", l.subjectIndex)
            obj.put("d", l.dayOfWeek.value) // 1 (Mon) to 7 (Sun)
            obj.put("st", l.startTime.toString())
            obj.put("et", l.endTime.toString())
            l.startDate?.let { obj.put("sd", it.toString()) }
            l.endDate?.let { obj.put("ed", it.toString()) }
            lecArray.put(obj)
        }
        root.put("l", lecArray)

        val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { it.write(jsonBytes) }
        val compressed = byteStream.toByteArray()

        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    /**
     * Decodes and decompresses a raw string or deep-link URI into a [SharedTimetable].
     * Accepts:
     * - "attendance://import?tt=..."
     * - "https://.../?tt=..."
     * - raw Base64 token
     */
    fun decode(input: String): SharedTimetable? {
        return try {
            val token = extractToken(input.trim())
            val compressedBytes = Base64.getUrlDecoder().decode(token)
            val decompressedBytes = GZIPInputStream(ByteArrayInputStream(compressedBytes)).use {
                it.readBytes()
            }
            val jsonStr = String(decompressedBytes, Charsets.UTF_8)
            val root = JSONObject(jsonStr)

            val title = root.optString("t", "Class Timetable")
            val subArray = root.getJSONArray("s")
            val subjects = (0 until subArray.length()).map { i ->
                val obj = subArray.getJSONObject(i)
                SharedSubject(
                    name = obj.getString("n"),
                    requiredAttendance = obj.optInt("r", 75)
                )
            }

            val lecArray = root.getJSONArray("l")
            val lectures = (0 until lecArray.length()).mapNotNull { i ->
                val obj = lecArray.getJSONObject(i)
                val idx = obj.getInt("i")
                val dayVal = obj.getInt("d")
                val stStr = obj.getString("st")
                val etStr = obj.getString("et")
                val day = DayOfWeek.of(dayVal)
                val startTime = LocalTime.parse(stStr)
                val endTime = LocalTime.parse(etStr)
                val startDate = obj.optString("sd").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                val endDate = obj.optString("ed").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                SharedLecture(idx, day, startTime, endTime, startDate, endDate)
            }

            SharedTimetable(title, subjects, lectures)
        } catch (e: Exception) {
            null
        }
    }

    fun extractToken(input: String): String {
        val queryParam = "$QUERY_PARAM="
        val idx = input.indexOf(queryParam)
        if (idx != -1) {
            val after = input.substring(idx + queryParam.length)
            val ampIdx = after.indexOf('&')
            return if (ampIdx != -1) after.substring(0, ampIdx) else after
        }
        return input
    }

    fun buildDeepLink(timetable: SharedTimetable): String {
        return "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST?$QUERY_PARAM=${encode(timetable)}"
    }

    /**
     * Generates a square QR Code [Bitmap] from given [content].
     */
    fun generateQrBitmap(content: String, sizePx: Int = 600): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Opens system share chooser with formatted invitation and deep link.
     */
    fun shareViaChooser(context: Context, timetable: SharedTimetable) {
        val link = buildDeepLink(timetable)
        val message = """
            📅 Timetable: ${timetable.title}
            
            ${timetable.subjects.size} subjects & ${timetable.lectures.size} lectures configured.
            
            ⚡ Import into Attendance Tracker in 1 tap:
            $link
        """.trimIndent()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Timetable: ${timetable.title}")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Timetable with Classmates").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
