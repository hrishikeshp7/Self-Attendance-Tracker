package com.attendance.tracker.backup

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses a hand-made / spreadsheet-exported CSV into subjects and weekly lecture slots.
 * Nothing touches the database here: the caller shows [Result.errors] and the summary
 * first, and only writes [Result.subjects] / [Result.slots] once the user confirms.
 *
 * One row per lecture slot (or per subject). Header names are case-insensitive:
 *   subject (required), target_percentage, attended, total, day, start, end
 * A subject's target/attended/total are taken from the first row that fills them in.
 * `day`, `start` and `end` must be given together; they add a weekly timetable slot.
 */
object CsvImporter {

    data class SubjectRow(
        val name: String,
        val requiredAttendance: Int?,
        val attended: Int?,
        val total: Int?
    )

    data class SlotRow(val subjectName: String, val day: DayOfWeek, val start: LocalTime, val end: LocalTime)

    data class Result(
        val subjects: List<SubjectRow>,
        val slots: List<SlotRow>,
        val errors: List<String>
    )

    const val TEMPLATE =
        "subject,target_percentage,attended,total,day,start,end\n" +
            "Physics,75,20,25,Monday,09:00,10:00\n" +
            "Physics,,,,Wednesday,11:00,12:00\n" +
            "Chemistry,80,18,22,Tuesday,10:00,11:00\n"

    private val columnAliases = mapOf(
        "subject" to "subject", "name" to "subject", "subject name" to "subject",
        "target_percentage" to "target", "target" to "target", "required" to "target",
        "attended" to "attended", "present" to "attended", "attended classes" to "attended",
        "total" to "total", "total classes" to "total",
        "day" to "day", "start" to "start", "end" to "end"
    )

    private val timeFormats = listOf("H:mm", "HH:mm", "h:mm a", "h:mma", "h a")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parse(text: String): Result {
        val rows = parseCsv(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return Result(emptyList(), emptyList(), listOf("The file is empty."))

        val columns = rows[0].map { columnAliases[it.trim().lowercase(Locale.ENGLISH)] }
        if ("subject" !in columns) {
            return Result(emptyList(), emptyList(), listOf("Missing required column \"subject\" in the header row."))
        }

        val errors = mutableListOf<String>()
        val subjects = linkedMapOf<String, SubjectRow>()
        val slots = mutableListOf<SlotRow>()

        for ((index, row) in rows.drop(1).withIndex()) {
            val line = index + 2
            fun cell(key: String) = columns.indexOf(key).takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim() }.orEmpty()

            val name = cell("subject")
            if (name.isEmpty()) { errors += "Row $line: subject name is missing."; continue }

            fun number(key: String, label: String, max: Int = Int.MAX_VALUE): Int? {
                val raw = cell(key)
                if (raw.isEmpty()) return null
                val value = raw.removeSuffix("%").trim().toIntOrNull()
                if (value == null || value < 0 || value > max) {
                    errors += "Row $line: $label \"$raw\" is not a valid number${if (max != Int.MAX_VALUE) " (0-$max)" else ""}."
                    return null
                }
                return value
            }
            val target = number("target", "target_percentage", 100)
            val attended = number("attended", "attended")
            val total = number("total", "total")
            if (attended != null && total != null && attended > total) {
                errors += "Row $line: attended ($attended) is greater than total ($total)."
            }
            if ((attended == null) != (total == null) && (cell("attended").isNotEmpty() || cell("total").isNotEmpty())) {
                errors += "Row $line: give both attended and total, or neither."
            }

            val key = name.lowercase(Locale.ENGLISH)
            val prev = subjects[key]
            subjects[key] = SubjectRow(
                name = prev?.name ?: name,
                requiredAttendance = prev?.requiredAttendance ?: target,
                attended = prev?.attended ?: attended,
                total = prev?.total ?: total
            )

            val dayRaw = cell("day"); val startRaw = cell("start"); val endRaw = cell("end")
            if (dayRaw.isNotEmpty() || startRaw.isNotEmpty() || endRaw.isNotEmpty()) {
                val day = parseDay(dayRaw)
                val start = parseTime(startRaw)
                val end = parseTime(endRaw)
                when {
                    day == null -> errors += "Row $line: day \"$dayRaw\" is not a weekday name."
                    start == null -> errors += "Row $line: start time \"$startRaw\" is not valid (use e.g. 09:00)."
                    end == null -> errors += "Row $line: end time \"$endRaw\" is not valid (use e.g. 10:00)."
                    !end.isAfter(start) -> errors += "Row $line: end time must be after start time."
                    else -> SlotRow(subjects[key]!!.name, day, start, end).let { if (it !in slots) slots += it }  // a repeated row is one slot
                }
            }
        }
        return Result(subjects.values.toList(), slots, errors)
    }

    internal fun parseDay(raw: String): DayOfWeek? {
        val s = raw.trim().lowercase(Locale.ENGLISH)
        if (s.length < 3) return null
        return DayOfWeek.entries.firstOrNull { it.name.lowercase(Locale.ENGLISH).startsWith(s) }
    }

    internal fun parseTime(raw: String): LocalTime? {
        val s = raw.trim().uppercase(Locale.ENGLISH).replace(Regex("(?<=\\d)(AM|PM)"), " $1")
        if (s.isEmpty()) return null
        for (f in timeFormats) {
            try { return LocalTime.parse(s, f) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    /** Minimal RFC-4180 reader: quoted fields, escaped quotes, commas/newlines inside quotes. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.setLength(0) }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
