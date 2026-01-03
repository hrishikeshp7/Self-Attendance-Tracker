package com.attendance.tracker.utils

import android.content.Context
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.Subject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CsvExporter {
    
    /**
     * Export attendance records to CSV file
     * @param context Android context
     * @param subjects List of subjects
     * @param records List of attendance records
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param fileName Name of the output file
     * @return File object pointing to the created CSV file
     */
    fun exportToCsv(
        context: Context,
        subjects: List<Subject>,
        records: List<AttendanceRecord>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        fileName: String = "attendance_export_${System.currentTimeMillis()}.csv"
    ): File {
        val cacheDir = context.cacheDir
        val csvFile = File(cacheDir, fileName)
        
        // Filter records by date range if specified
        val filteredRecords = records.filter { record ->
            val afterStart = startDate?.let { record.date >= it } ?: true
            val beforeEnd = endDate?.let { record.date <= it } ?: true
            afterStart && beforeEnd
        }
        
        // Create subject map for quick lookup
        val subjectMap = subjects.associateBy { it.id }
        
        csvFile.bufferedWriter().use { writer ->
            // Write header
            writer.write("Date,Subject,Status,Present Count,Absent Count,Attendance %\n")
            
            // Write data rows
            filteredRecords.sortedBy { it.date }.forEach { record ->
                val subject = subjectMap[record.subjectId]
                if (subject != null) {
                    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
                    val attendancePercent = if (subject.totalLectures > 0) {
                        (subject.presentLectures * 100.0 / subject.totalLectures)
                    } else 0.0
                    
                    writer.write(
                        "${record.date.format(formatter)}," +
                        "\"${subject.name}\"," +
                        "${record.status}," +
                        "${subject.presentLectures}," +
                        "${subject.absentLectures}," +
                        "${String.format("%.2f", attendancePercent)}%\n"
                    )
                }
            }
        }
        
        return csvFile
    }
    
    /**
     * Export subject-wise summary to CSV
     */
    fun exportSubjectSummaryToCsv(
        context: Context,
        subjects: List<Subject>,
        fileName: String = "subject_summary_${System.currentTimeMillis()}.csv"
    ): File {
        val cacheDir = context.cacheDir
        val csvFile = File(cacheDir, fileName)
        
        csvFile.bufferedWriter().use { writer ->
            // Write header
            writer.write("Subject,Present,Absent,Total,Attendance %,Required %,Status\n")
            
            // Write data rows
            subjects.filter { !it.isFolder }.forEach { subject ->
                val attendancePercent = if (subject.totalLectures > 0) {
                    (subject.presentLectures * 100.0 / subject.totalLectures)
                } else 0.0
                
                val status = if (attendancePercent >= subject.requiredAttendance) "OK" else "LOW"
                
                writer.write(
                    "\"${subject.name}\"," +
                    "${subject.presentLectures}," +
                    "${subject.absentLectures}," +
                    "${subject.totalLectures}," +
                    "${String.format("%.2f", attendancePercent)}%," +
                    "${subject.requiredAttendance}%," +
                    "$status\n"
                )
            }
        }
        
        return csvFile
    }
}
