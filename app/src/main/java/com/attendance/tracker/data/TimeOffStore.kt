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
        val toRemove = mutableListOf<String>()
        val toAdd = mutableListOf<String>()

        for (item in current) {
            val rule = parseRule(item) ?: continue
            val overlaps = !startDate.isAfter(rule.endDate) && !endDate.isBefore(rule.startDate)
            if (overlaps) {
                if (subjectIds == null) {
                    // New rule covers ALL subjects, remove overlapping rule
                    toRemove.add(item)
                } else if (rule.subjectIds != null) {
                    val remaining = rule.subjectIds - subjectIds
                    if (remaining.isEmpty()) {
                        toRemove.add(item)
                    } else if (remaining.size != rule.subjectIds.size) {
                        toRemove.add(item)
                        val subsStr = remaining.joinToString(",")
                        toAdd.add("${rule.startDate}:${rule.endDate}:${rule.status.name}:$subsStr")
                    }
                }
            }
        }
        current.removeAll(toRemove)
        current.addAll(toAdd)

        if (status != null) {
            val subsStr = subjectIds?.joinToString(",") ?: "ALL"
            current.add("$startDate:$endDate:${status.name}:$subsStr")
        }

        prefs.edit().putStringSet(KEY_RULES, current).apply()
    }

    fun isVacationAbsent(context: Context, date: LocalDate, subjectIds: Set<Long>? = null): Boolean =
        isVacationAbsent(prefs(context), date, subjectIds)

    fun isVacationAbsent(prefs: SharedPreferences, date: LocalDate, subjectIds: Set<Long>? = null): Boolean {
        return getActiveRules(prefs).any { rule ->
            val dateMatches = rule.status == AttendanceStatus.ABSENT && !date.isBefore(rule.startDate) && !date.isAfter(rule.endDate)
            if (!dateMatches) return@any false
            if (subjectIds == null) {
                rule.subjectIds == null
            } else {
                rule.subjectIds == null || subjectIds.all { it in rule.subjectIds }
            }
        }
    }

    fun getActiveRuleForDate(context: Context, date: LocalDate, subjectId: Long? = null): TimeOffRule? =
        getActiveRuleForDate(prefs(context), date, subjectId)

    fun getActiveRuleForDate(prefs: SharedPreferences, date: LocalDate, subjectId: Long? = null): TimeOffRule? {
        return getActiveRules(prefs).firstOrNull { rule ->
            !date.isBefore(rule.startDate) && !date.isAfter(rule.endDate) &&
                (subjectId == null || rule.subjectIds == null || subjectId in rule.subjectIds)
        }
    }

    fun setRules(context: Context, rules: List<TimeOffRule>) = setRules(prefs(context), rules)

    fun setRules(prefs: SharedPreferences, rules: List<TimeOffRule>) {
        val set = rules.map { rule ->
            val subsStr = rule.subjectIds?.joinToString(",") ?: "ALL"
            "${rule.startDate}:${rule.endDate}:${rule.status.name}:$subsStr"
        }.toSet()
        prefs.edit().putStringSet(KEY_RULES, set).apply()
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
    ) {
        val rules = getActiveRules(context)
        if (rules.isEmpty()) return
        syncActiveTimeOff(rules, repository, today)
    }

    suspend fun syncActiveTimeOff(
        rules: List<TimeOffRule>,
        repository: AttendanceRepository,
        today: LocalDate = LocalDate.now()
    ) {
        if (rules.isEmpty()) return
        // 1. Purge future records covered by active rules, but only if they were not manually
        //    set by the user. Manual records (isManual = true) take precedence over time-off.
        val allRecords = repository.getAllAttendanceRecordsOnce()
        val futureRecords = allRecords.filter { it.date.isAfter(today) }
        for (rec in futureRecords) {
            if (rec.isManual) continue  // never wipe a user-set record
            val covered = rules.any { rule ->
                !rec.date.isBefore(rule.startDate) && !rec.date.isAfter(rule.endDate) &&
                    (rule.subjectIds == null || rec.subjectId in rule.subjectIds)
            }
            if (covered) {
                repository.clearDay(rec.subjectId, rec.date)
            }
        }

        // 2. Apply active rules for eligible dates up to today
        for (rule in rules) {
            if (rule.startDate.isAfter(today)) continue
            val maxDate = if (rule.endDate.isAfter(today)) today else rule.endDate
            var d = rule.startDate
            while (!d.isAfter(maxDate)) {
                val existing = repository.getAttendanceInRangeOnce(d, d)
                val targetIds = rule.subjectIds
                val scheduledForDay = repository.getAllScheduleEntriesOnce()
                    .filter { it.occursOn(d) }
                    .map { it.subjectId }
                    .toSet()
                val targetSubjectSet = targetIds ?: if (scheduledForDay.isNotEmpty()) scheduledForDay else repository.getAllSubjectsOnce().filter { !it.isFolder }.map { it.id }.toSet()
                val markedSubjectIds = existing.map { it.subjectId }.toSet()
                val unrecordedIds = targetSubjectSet.filter { it !in markedSubjectIds }.toSet()
                if (unrecordedIds.isNotEmpty()) {
                    repository.applyTimeOff(d, d, rule.status, unrecordedIds, today = today)
                }
                d = d.plusDays(1)
            }
        }
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
