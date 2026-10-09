package com.attendance.tracker.backup

import org.json.JSONArray
import org.json.JSONObject

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
            "{\"version\":1,\"subjects\":[{\"name\":\"Physics\",\"target\":75," +
            "\"slots\":[{\"day\":\"MON\",\"start\":\"09:00\",\"end\":\"10:00\"}]}]}\n\n" +
            "Rules:\n" +
            "- One entry per subject. List every weekly lecture or lab of that subject in \"slots\".\n" +
            "- Use a separate subject for a lab or practical if the timetable lists it separately, " +
            "for example \"Physics Lab\".\n" +
            "- \"day\" is MON, TUE, WED, THU, FRI, SAT or SUN.\n" +
            "- \"start\" and \"end\" are 24-hour HH:MM times.\n" +
            "- \"target\" is the required attendance percentage (0-100). Use 75 if the timetable " +
            "does not say.\n" +
            "- Do not guess. If a time or subject is unclear, ask me before answering."

    fun parse(text: String): CsvImporter.Result {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return fail("No JSON found. Paste the chatbot's whole reply.")
        val root = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            return fail("That isn't valid JSON. Ask the chatbot to fix the format and try again.")
        }
        if (root.optInt("version", 1) > 1) return fail("This format is from a newer version of the app.")
        val array = root.optJSONArray("subjects") ?: return fail("Missing \"subjects\" list.")

        val errors = mutableListOf<String>()
        val subjects = linkedMapOf<String, CsvImporter.SubjectRow>()
        val slots = mutableListOf<CsvImporter.SlotRow>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            val name = obj?.optString("name")?.trim().orEmpty()
            if (name.isEmpty()) { errors += "Subject ${i + 1}: name is missing."; continue }
            val target = obj!!.opt("target")?.let { (it as? Number)?.toInt() ?: it.toString().removeSuffix("%").trim().toIntOrNull() }
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
                val dayRaw = slot?.optString("day").orEmpty()
                val startRaw = slot?.optString("start").orEmpty()
                val endRaw = slot?.optString("end").orEmpty()
                val day = CsvImporter.parseDay(dayRaw)
                val s = CsvImporter.parseTime(startRaw)
                val e = CsvImporter.parseTime(endRaw)
                when {
                    day == null -> errors += "$label: day \"$dayRaw\" isn't a weekday (use MON-SUN)."
                    s == null -> errors += "$label: start \"$startRaw\" isn't a time (use HH:MM)."
                    e == null -> errors += "$label: end \"$endRaw\" isn't a time (use HH:MM)."
                    !e.isAfter(s) -> errors += "$label: end must be after start."
                    else -> CsvImporter.SlotRow(subjects[key]!!.name, day, s, e).let { if (it !in slots) slots += it }
                }
            }
        }
        return CsvImporter.Result(subjects.values.toList(), slots, errors)
    }

    private fun fail(message: String) = CsvImporter.Result(emptyList(), emptyList(), listOf(message))
}
