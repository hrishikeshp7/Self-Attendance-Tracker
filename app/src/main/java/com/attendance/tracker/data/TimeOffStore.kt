package com.attendance.tracker.data

import android.content.Context
import android.content.SharedPreferences
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.data.repository.AttendanceRepository
import java.time.LocalDate

data class TimeOffRule(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: AttendanceStatus,
    val subjectIds: Set<Long>? = null
)

object TimeOffStore {
    private const val PREFS_NAME = "time_off_prefs"
    private const val KEY_RULES = "vacation_rules"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordTimeOff(
        context: Context,
        startDate: LocalDate,
        endDate: LocalDate,
        status: AttendanceStatus?,
        subjectIds: Set<Long>? = null
    ) = recordTimeOff(prefs(context), startDate, endDate, status, subjectIds)

    fun recordTimeOff(
        prefs: SharedPreferences,
        startDate: LocalDate,
        endDate: LocalDate,
        status: AttendanceStatus?,
        subjectIds: Set<Long>? = null
    ) {
        val current = prefs.getStringSet(KEY_RULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val iterator = current.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val rule = parseRule(item)
            if (rule != null) {
                val overlaps = !startDate.isAfter(rule.endDate) && !endDate.isBefore(rule.startDate)
                if (overlaps) {
                    iterator.remove()
                }
            }
        }

        if (status != null) {
            val subsStr = subjectIds?.joinToString(",") ?: "ALL"
            current.add("$startDate:$endDate:${status.name}:$subsStr")
        }

        prefs.edit().putStringSet(KEY_RULES, current).apply()
    }

    fun isVacationAbsent(context: Context, date: LocalDate): Boolean =
        isVacationAbsent(prefs(context), date)

    fun isVacationAbsent(prefs: SharedPreferences, date: LocalDate): Boolean {
        return getActiveRules(prefs).any { rule ->
            rule.status == AttendanceStatus.ABSENT && !date.isBefore(rule.startDate) && !date.isAfter(rule.endDate)
        }
    }

    fun getActiveRules(context: Context): List<TimeOffRule> =
        getActiveRules(prefs(context))

    fun getActiveRules(prefs: SharedPreferences): List<TimeOffRule> {
        val set = prefs.getStringSet(KEY_RULES, null) ?: return emptyList()
        val list = mutableListOf<TimeOffRule>()
        for (item in set) {
            val rule = parseRule(item)
            if (rule != null) {
                list.add(rule)
            }
        }
        return list
    }

    private fun parseRule(str: String): TimeOffRule? {
        val parts = str.split(":")
        if (parts.size >= 3) {
            val s = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            val e = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
            val st = runCatching { AttendanceStatus.valueOf(parts[2]) }.getOrNull() ?: return null
            val subIds = if (parts.size >= 4 && parts[3] != "ALL" && parts[3].isNotBlank()) {
                parts[3].split(",").mapNotNull { it.toLongOrNull() }.toSet()
            } else null
            return TimeOffRule(s, e, st, subIds)
        }
        return null
    }

    suspend fun syncActiveTimeOff(
        context: Context,
        repository: AttendanceRepository,
        today: LocalDate = LocalDate.now()
    ) = syncActiveTimeOff(getActiveRules(context), repository, today)

    suspend fun syncActiveTimeOff(
        rules: List<TimeOffRule>,
        repository: AttendanceRepository,
        today: LocalDate = LocalDate.now()
    ) {
        // 1. Purge any future records (> today)
        val allRecords = repository.getAllAttendanceRecordsOnce()
        val futureRecords = allRecords.filter { it.date.isAfter(today) }
        for (rec in futureRecords) {
            repository.clearDay(rec.subjectId, rec.date)
        }

        // 2. Apply active rules for eligible dates up to today
        for (rule in rules) {
            if (rule.startDate.isAfter(today)) continue
            val maxDate = if (rule.endDate.isAfter(today)) today else rule.endDate
            var d = rule.startDate
            while (!d.isAfter(maxDate)) {
                val existing = repository.getAttendanceInRangeOnce(d, d)
                if (existing.isEmpty()) {
                    repository.applyTimeOff(d, d, rule.status, rule.subjectIds, today = today)
                }
                d = d.plusDays(1)
            }
        }
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
