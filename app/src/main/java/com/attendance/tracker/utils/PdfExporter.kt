package com.attendance.tracker.utils

import android.content.Context
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.Subject
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PdfExporter {
    
    /**
     * Export attendance records to PDF file
     * @param context Android context
     * @param subjects List of subjects
     * @param records List of attendance records
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param fileName Name of the output file
     * @return File object pointing to the created PDF file
     */
    fun exportToPdf(
        context: Context,
        subjects: List<Subject>,
        records: List<AttendanceRecord>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        fileName: String = "attendance_export_${System.currentTimeMillis()}.pdf"
    ): File {
        val cacheDir = context.cacheDir
        val pdfFile = File(cacheDir, fileName)
        
        // Filter records by date range if specified
        val filteredRecords = records.filter { record ->
            val afterStart = startDate?.let { record.date >= it } ?: true
            val beforeEnd = endDate?.let { record.date <= it } ?: true
            afterStart && beforeEnd
        }
        
        // Create subject map for quick lookup
        val subjectMap = subjects.associateBy { it.id }
        
        val writer = PdfWriter(pdfFile)
        val pdfDocument = PdfDocument(writer)
        val document = Document(pdfDocument)
        
        // Title
        val title = Paragraph("Attendance Report")
            .setFontSize(20f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
        document.add(title)
        
        // Date range
        val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        val dateRange = if (startDate != null && endDate != null) {
            "From ${startDate.format(dateFormatter)} to ${endDate.format(dateFormatter)}"
        } else {
            "All Records"
        }
        document.add(Paragraph(dateRange).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("\n"))
        
        // Subject Summary Section
        document.add(Paragraph("Subject Summary").setFontSize(16f).setBold())
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 1f, 1f, 1f, 1.5f, 1.5f)))
            .useAllAvailableWidth()
        
        // Summary table header
        summaryTable.addHeaderCell(createHeaderCell("Subject"))
        summaryTable.addHeaderCell(createHeaderCell("Present"))
        summaryTable.addHeaderCell(createHeaderCell("Absent"))
        summaryTable.addHeaderCell(createHeaderCell("Total"))
        summaryTable.addHeaderCell(createHeaderCell("Attendance %"))
        summaryTable.addHeaderCell(createHeaderCell("Required %"))
        
        // Summary table data
        subjects.filter { !it.isFolder }.forEach { subject ->
            val attendancePercent = if (subject.totalLectures > 0) {
                (subject.presentLectures * 100.0 / subject.totalLectures)
            } else 0.0
            
            summaryTable.addCell(Cell().add(Paragraph(subject.name)))
            summaryTable.addCell(Cell().add(Paragraph(subject.presentLectures.toString())))
            summaryTable.addCell(Cell().add(Paragraph(subject.absentLectures.toString())))
            summaryTable.addCell(Cell().add(Paragraph(subject.totalLectures.toString())))
            
            val percentCell = Cell().add(Paragraph(String.format("%.2f%%", attendancePercent)))
            if (attendancePercent < subject.requiredAttendance) {
                percentCell.setBackgroundColor(ColorConstants.LIGHT_GRAY)
            }
            summaryTable.addCell(percentCell)
            summaryTable.addCell(Cell().add(Paragraph("${subject.requiredAttendance}%")))
        }
        
        document.add(summaryTable)
        document.add(Paragraph("\n"))
        
        // Detailed Records Section
        if (filteredRecords.isNotEmpty()) {
            document.add(Paragraph("Attendance Records").setFontSize(16f).setBold())
            val recordsTable = Table(UnitValue.createPercentArray(floatArrayOf(2f, 3f, 2f)))
                .useAllAvailableWidth()
            
            // Records table header
            recordsTable.addHeaderCell(createHeaderCell("Date"))
            recordsTable.addHeaderCell(createHeaderCell("Subject"))
            recordsTable.addHeaderCell(createHeaderCell("Status"))
            
            // Records table data
            filteredRecords.sortedBy { it.date }.forEach { record ->
                val subject = subjectMap[record.subjectId]
                if (subject != null) {
                    recordsTable.addCell(Cell().add(Paragraph(record.date.format(dateFormatter))))
                    recordsTable.addCell(Cell().add(Paragraph(subject.name)))
                    recordsTable.addCell(Cell().add(Paragraph(record.status.toString())))
                }
            }
            
            document.add(recordsTable)
        }
        
        document.close()
        return pdfFile
    }
    
    private fun createHeaderCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text).setBold())
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
    }
}
