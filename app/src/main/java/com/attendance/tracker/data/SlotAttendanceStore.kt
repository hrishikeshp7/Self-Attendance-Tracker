package com.attendance.tracker.data

import android.content.Context
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.AttendanceStatus
import java.time.LocalDate

/**
 * Stores and retrieves per-lecture slot attendance statuses for subjects that have multiple
 * lectures scheduled on a single day (e.g. Bio - Lecture 1, Bio - Lecture 2).
 */
object SlotAttendanceStore {
    private const val PREFS_NAME = "slot_attendance_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSlotStatus(context: Context, subjectId: Long, date: LocalDate, slotIndex: Int): AttendanceStatus? {
        val str = prefs(context).getString("${subjectId}_${date}_$slotIndex", null) ?: return null
        return try {
            AttendanceStatus.valueOf(str)
        } catch (e: Exception) {
            null
        }
    }

    fun setSlotStatus(context: Context, subjectId: Long, date: LocalDate, slotIndex: Int, status: AttendanceStatus?) {
        val key = "${subjectId}_${date}_$slotIndex"
        if (status == null) {
            prefs(context).edit().remove(key).apply()
        } else {
            prefs(context).edit().putString(key, status.name).apply()
        }
    }

    fun clearSlotsForDate(context: Context, subjectId: Long, date: LocalDate, totalSlots: Int = 16) {
        val editor = prefs(context).edit()
        val limit = maxOf(totalSlots, 16)
        for (i in 0 until limit) {
            editor.remove("${subjectId}_${date}_$i")
        }
        editor.apply()
    }

    fun clearAllSlotsForSubject(context: Context, subjectId: Long) {
        val p = prefs(context)
        val prefix = "${subjectId}_"
        val editor = p.edit()
        for (key in p.all.keys) {
            if (key.startsWith(prefix)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun exportAll(context: Context): List<SlotEntry> {
        val all = prefs(context).all
        val entries = mutableListOf<SlotEntry>()
        for ((key, value) in all) {
            val statusStr = value as? String ?: continue
            val status = runCatching { AttendanceStatus.valueOf(statusStr) }.getOrNull() ?: continue
            val parts = key.split("_")
            if (parts.size == 3) {
                val subjectId = parts[0].toLongOrNull() ?: continue
                val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: continue
                val slotIndex = parts[2].toIntOrNull() ?: continue
                entries.add(SlotEntry(subjectId, date, slotIndex, status))
            }
        }
        return entries
    }

    fun restoreAll(context: Context, entries: List<SlotEntry>) {
        val editor = prefs(context).edit()
        for (entry in entries) {
            editor.putString("${entry.subjectId}_${entry.date}_${entry.slotIndex}", entry.status.name)
        }
        editor.apply()
    }

    /**
     * If user interacts with slots and no explicit slot entries exist yet,
     * seed them from the existing [record] so other slots don't lose their status.
     */
    fun ensureSlotsInitialized(
        context: Context,
        subjectId: Long,
        date: LocalDate,
        totalSlots: Int,
        record: AttendanceRecord?
    ) {
        if (record == null) return
        var anyExplicit = false
        for (i in 0 until totalSlots) {
            if (getSlotStatus(context, subjectId, date, i) != null) {
                anyExplicit = true
                break
            }
        }
        if (!anyExplicit) {
            val editor = prefs(context).edit()
            for (i in 0 until totalSlots) {
                val status = when {
                    i < record.presentCount -> AttendanceStatus.PRESENT
                    i < record.presentCount + record.absentCount -> AttendanceStatus.ABSENT
                    record.status == AttendanceStatus.NO_CLASS -> AttendanceStatus.NO_CLASS
                    else -> null
                }
                if (status != null) {
                    editor.putString("${subjectId}_${date}_$i", status.name)
                }
            }
            editor.apply()
        }
    }

    /**
     * Returns the effective status of a slot. Uses explicit per-slot preference if present;
     * otherwise derives from [record] if previously recorded.
     */
    fun getEffectiveSlotStatus(
        context: Context,
        subjectId: Long,
        date: LocalDate,
        slotIndex: Int,
        totalSlots: Int,
        record: AttendanceRecord?
    ): AttendanceStatus? {
        if (record == null) {
            return null
        }

        val explicit = getSlotStatus(context, subjectId, date, slotIndex)
        if (explicit != null) {
            val isValid = when (explicit) {
                AttendanceStatus.PRESENT -> record.presentCount > 0
                AttendanceStatus.ABSENT -> record.absentCount > 0
                AttendanceStatus.NO_CLASS -> true
            }
            if (isValid) return explicit
        }

        if (totalSlots > 1) {
            var anyExplicit = false
            for (i in 0 until totalSlots) {
                val st = getSlotStatus(context, subjectId, date, i)
                if (st != null) {
                    val valid = when (st) {
                        AttendanceStatus.PRESENT -> record.presentCount > 0
                        AttendanceStatus.ABSENT -> record.absentCount > 0
                        AttendanceStatus.NO_CLASS -> true
                    }
                    if (valid) {
                        anyExplicit = true
                        break
                    }
                }
            }
            if (anyExplicit) return null
        }

        if (record.status == AttendanceStatus.NO_CLASS) return AttendanceStatus.NO_CLASS

        // If recorded as aggregate counts:
        return when {
            slotIndex < record.presentCount -> AttendanceStatus.PRESENT
            slotIndex < record.presentCount + record.absentCount -> AttendanceStatus.ABSENT
            else -> null
        }
    }

    /**
     * Updates [slotIndex] to [newStatus] and computes the total (present, absent, isAllNoClass) counts across all slots.
     */
    fun updateSlotAndCompute(
        context: Context,
        subjectId: Long,
        date: LocalDate,
        totalSlots: Int,
        slotIndex: Int,
        newStatus: AttendanceStatus?
    ): SlotComputeResult {
        setSlotStatus(context, subjectId, date, slotIndex, newStatus)

        var presentCount = 0
        var absentCount = 0
        var noClassCount = 0
        var markedCount = 0

        for (i in 0 until totalSlots) {
            val status = getSlotStatus(context, subjectId, date, i)
            if (status != null) {
                markedCount++
                when (status) {
                    AttendanceStatus.PRESENT -> presentCount++
                    AttendanceStatus.ABSENT -> absentCount++
                    AttendanceStatus.NO_CLASS -> noClassCount++
                }
            }
        }

        return SlotComputeResult(
            presentCount = presentCount,
            absentCount = absentCount,
            noClassCount = noClassCount,
            markedCount = markedCount,
            isAllNoClass = totalSlots > 0 && noClassCount == totalSlots
        )
    }
}

data class SlotComputeResult(
    val presentCount: Int,
    val absentCount: Int,
    val noClassCount: Int,
    val markedCount: Int,
    val isAllNoClass: Boolean
)

data class SlotEntry(
    val subjectId: Long,
    val date: LocalDate,
    val slotIndex: Int,
    val status: AttendanceStatus
)
