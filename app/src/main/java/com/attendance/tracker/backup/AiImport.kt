package com.attendance.tracker.backup

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Import for timetables turned into JSON by the user's own chatbot (Claude, Gemini, ...).
 * The user copies [PROMPT], attaches their timetable to the chatbot, and pastes the reply
 * back. The reply is read into the same [CsvImporter.Result] the CSV import uses, so the
 * preview, duplicate handling and write path are shared.
 */
object AiImport {

    const val PROMPT =
        "Read the attached timetable (PDF, screenshot or text) and reply with ONLY one JSON object in " +
            "a code block, no other text.\n\n" +
            "Format:\n" +
            "{\"version\":1,\"from\":\"2026-08-03\",\"until\":\"2026-12-18\",\"subjects\":[{\"name\":\"Physics\"," +
            "\"target\":75,\"slots\":[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"10:00\"}," +
            "{\"date\":\"2026-09-14\",\"start\":\"14:00\",\"end\":\"15:00\"}]}]}\n\n" +
            "Rules:\n" +
            "- One entry per subject. List every weekly lecture or lab of that subject in \"slots\".\n" +
            "- Use a separate subject for a lab or practical if the timetable lists it separately, " +
            "for example \"Physics Lab\".\n" +
            "- Dates are YYYY-MM-DD. Never guess the year or the dates: if the timetable doesn't show them, " +
            "ask me.\n" +
            "- If the timetable repeats every week, give the period it applies to in \"from\" and " +
            "\"until\" (first and last day, for example the semester or term), and give each slot a " +
            "\"day\" of MON, TUE, WED, THU, FRI, SAT or SUN. If it is for one single week, use that " +
            "week's Monday as \"from\" and Sunday as \"until\".\n" +
            "- If a lecture is listed on a specific calendar date instead, give that slot a \"date\" " +
            "(no \"day\" needed).\n" +
            "- \"start\" and \"end\" are 24-hour HH:MM times.\n" +
            "- \"target\" is the required attendance percentage (0-100). Use 75 if the timetable " +
            "does not say.\n" +
            "- Do not guess. If a time or subject is unclear, ask me before answering."

    fun parse(text: String): CsvImporter.Result {
        val cleaned = text.trim()
        val fenced = if (cleaned.contains("```")) {
            val after = cleaned.substringAfter("```json", "").ifEmpty { cleaned.substringAfter("```") }
            val block = after.substringBefore("```")
            val s = block.indexOf('{')
            val e = block.lastIndexOf('}')
            if (s >= 0 && e > s) block.substring(s, e + 1) else null
        } else null

        val candidate = fenced ?: run {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
        }

        if (candidate == null) return fail("No JSON found. Paste the chatbot's whole reply.")

        val root = try {
            JSONObject(candidate)
        } catch (_: Exception) {
            val subjectsIdx = cleaned.indexOf("\"subjects\"")
            val altCandidate = if (subjectsIdx > 0) {
                val s = cleaned.lastIndexOf('{', subjectsIdx)
                val lastBracket = cleaned.lastIndexOf(']')
                val e = if (lastBracket > 0) cleaned.indexOf('}', lastBracket) else -1
                if (s >= 0 && e > s) cleaned.substring(s, e + 1) else null
            } else null

            try {
                if (altCandidate != null) JSONObject(altCandidate)
                else return fail("That isn't valid JSON. Ask the chatbot to fix the format and try again.")
            } catch (e: Exception) {
                return fail("That isn't valid JSON. Ask the chatbot to fix the format and try again.")
            }
        }
        if (root.optInt("version", 1) > 1) return fail("This format is from a newer version of the app.")
        val array = root.optJSONArray("subjects") ?: return fail("Missing \"subjects\" list.")

        val errors = mutableListOf<String>()
        val from = date(root, "from", "from", errors)
        val until = date(root, "until", "until", errors)
        if (from != null && until != null && until.isBefore(from)) errors += "\"until\" is before \"from\"."
        val subjects = linkedMapOf<String, CsvImporter.SubjectRow>()
        val slots = mutableListOf<CsvImporter.SlotRow>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            val name = obj?.optString("name")?.trim().orEmpty()
            if (name.isEmpty()) { errors += "Subject ${i + 1}: name is missing."; continue }
            val target = obj!!.opt("target")?.let { (it as? Number)?.toInt() ?: it.toString().removeSuffix("%").trim().toDoubleOrNull()?.toInt() }
            if (obj.has("target") && (target == null || target !in 0..100)) {
                errors += "$name: target \"${obj.opt("target")}\" must be a number from 0 to 100."
            }
            val key = name.lowercase()
            val prev = subjects[key]
            subjects[key] = CsvImporter.SubjectRow(prev?.name ?: name, prev?.requiredAttendance ?: target?.takeIf { it in 0..100 }, null, null)

            val list = obj.optJSONArray("slots") ?: JSONArray()
            for (j in 0 until list.length()) {
                val slot = list.optJSONObject(j)
                val label = "$name, slot ${j + 1}"
                val oneOff = if (slot?.has("date") == true) date(slot, "date", "$label: date", errors) else null
                if (slot?.has("date") == true && oneOff == null) continue
                val dayRaw = slot?.optString("day").orEmpty().ifEmpty { oneOff?.dayOfWeek?.name.orEmpty() }
                val startRaw = slot?.optString("start").orEmpty()
                val endRaw = slot?.optString("end").orEmpty()
                val day = CsvImporter.parseDay(dayRaw)
                val slotFrom = oneOff ?: from
                val slotUntil = oneOff ?: until
                val s = CsvImporter.parseTime(startRaw)
                val e = CsvImporter.parseTime(endRaw)
                when {
                    day == null -> errors += "$label: day \"$dayRaw\" isn't a weekday (use MON-SUN)."
                    s == null -> errors += "$label: start \"$startRaw\" isn't a time (use HH:MM)."
                    e == null -> errors += "$label: end \"$endRaw\" isn't a time (use HH:MM)."
                    !e.isAfter(s) -> errors += "$label: end must be after start."
                    oneOff != null && oneOff.dayOfWeek != day -> errors += "$label: $oneOff is a ${oneOff.dayOfWeek.name.take(3)}, not ${day.name.take(3)}."
                    else -> CsvImporter.SlotRow(subjects[key]!!.name, day, s, e, slotFrom, slotUntil).let { if (it !in slots) slots += it }
                }
            }
        }
        return CsvImporter.Result(subjects.values.toList(), slots, errors)
    }

    /** Reads an optional YYYY-MM-DD field; a malformed one is reported and treated as missing. */
    private fun date(obj: JSONObject?, key: String, label: String, errors: MutableList<String>): LocalDate? {
        val raw = obj?.optString(key).orEmpty().trim()
        if (raw.isEmpty()) return null
        return try { LocalDate.parse(raw) } catch (_: DateTimeParseException) {
            errors += "$label \"$raw\" isn't a date (use YYYY-MM-DD)."
            null
        }
    }

    private fun fail(message: String) = CsvImporter.Result(emptyList(), emptyList(), listOf(message))
}
