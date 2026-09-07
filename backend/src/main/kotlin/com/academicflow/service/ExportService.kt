package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.entity.Allocation
import com.academicflow.entity.TimetableEntry
import com.academicflow.repository.AcademicUnitRepository
import com.academicflow.repository.AllocationRepository
import com.academicflow.repository.LecturerRepository
import com.academicflow.repository.OrganizationNodeRepository
import com.academicflow.repository.TimetableEntryRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class ExportService(
    private val allocationRepo: AllocationRepository,
    private val unitRepo: AcademicUnitRepository,
    private val lecturerRepo: LecturerRepository,
    private val orgRepo: OrganizationNodeRepository,
    private val timetableRepo: TimetableEntryRepository
) {
    private val days = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val stampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Africa/Nairobi"))

    @Transactional
    fun export(kind: String, format: String): ResponseEntity<ByteArray> {
        val normalizedKind = kind.lowercase()
        val normalizedFormat = format.lowercase()
        require(normalizedKind in setOf("allocations", "timetable", "full")) {
            "kind must be allocations, timetable, or full"
        }
        require(normalizedFormat in setOf("csv", "pdf")) {
            "format must be csv or pdf"
        }

        if (normalizedKind == "timetable" || normalizedKind == "full") {
            ensureTimetableSlotsForPublished()
        }

        val bytes = when (normalizedFormat) {
            "csv" -> buildCsv(normalizedKind).toByteArray(StandardCharsets.UTF_8)
            else -> buildPdf(normalizedKind)
        }
        val media = if (normalizedFormat == "csv") {
            MediaType.parseMediaType("text/csv")
        } else {
            MediaType.APPLICATION_PDF
        }
        val filename = "academicflow-${normalizedKind}-${stampFmt.format(Instant.now()).replace(' ', '_').replace(":", "")}.$normalizedFormat"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(media)
            .body(bytes)
    }

    private fun publishedAllocations(): List<Allocation> {
        val tenantId = TenantContext.get()
        return allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .filter { it.status in listOf("PUBLISHED", "APPROVED") && it.lecturerId != null }
    }

    /** Backfill missing lecturer timetable rows for already-published allocations. */
    private fun ensureTimetableSlotsForPublished() {
        val tenantId = TenantContext.get()
        val existing = timetableRepo.findByTenantId(tenantId).toMutableList()
        publishedAllocations().forEach { allocation ->
            val lecturerId = allocation.lecturerId ?: return@forEach
            val hasSlot = existing.any {
                it.allocationId == allocation.id ||
                    (it.academicUnitId == allocation.academicUnitId && it.lecturerId == lecturerId)
            }
            if (hasSlot) return@forEach

            val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, lecturerId)
            val day = when {
                lecturer?.availabilityText?.contains("Monday", true) == true -> 1
                lecturer?.availabilityText?.contains("Tuesday", true) == true -> 2
                lecturer?.availabilityText?.contains("Wednesday", true) == true -> 3
                lecturer?.availabilityText?.contains("Thursday", true) == true -> 4
                lecturer?.availabilityText?.contains("Friday", true) == true -> 5
                else -> 3
            }
            val unit = unitRepo.findByTenantIdAndId(tenantId, allocation.academicUnitId)
            val hours = unit?.contactHours?.toInt()?.coerceIn(1, 3) ?: 2
            val start = LocalTime.of(10, 0)
            val saved = timetableRepo.save(
                TimetableEntry(
                    tenantId = tenantId,
                    allocationId = allocation.id,
                    lecturerId = lecturerId,
                    academicUnitId = allocation.academicUnitId,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = start.plusHours(hours.toLong()),
                    room = "TBA"
                )
            )
            existing.add(saved)
        }
    }

    private fun allocationRows(): List<List<String>> {
        val tenantId = TenantContext.get()
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        return publishedAllocations().map { a ->
            val unit = units[a.academicUnitId]
            val lec = a.lecturerId?.let { lecturers[it] }
            listOf(
                unit?.code ?: "",
                unit?.name ?: "",
                lec?.fullName ?: "",
                lec?.staffNumber ?: "",
                lec?.let { orgs[it.organizationNodeId]?.name } ?: "",
                unit?.let { orgs[it.sourceDepartmentId]?.name } ?: "",
                unit?.contactHours?.stripTrailingZeros()?.toPlainString() ?: "",
                unit?.studentCount?.toString() ?: "",
                a.matchScore?.stripTrailingZeros()?.toPlainString() ?: "",
                a.status,
                a.overrideReason ?: ""
            )
        }
    }

    private fun timetableRows(): List<List<String>> {
        val tenantId = TenantContext.get()
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        val publishedUnitIds = publishedAllocations().map { it.academicUnitId }.toSet()
        val publishedLecturerIds = publishedAllocations().mapNotNull { it.lecturerId }.toSet()

        return timetableRepo.findByTenantId(tenantId)
            .filter { e ->
                (e.academicUnitId != null && e.academicUnitId in publishedUnitIds) ||
                    (e.lecturerId != null && e.lecturerId in publishedLecturerIds) ||
                    publishedUnitIds.isEmpty() // fallback: show all if nothing published yet
            }
            .sortedWith(compareBy<TimetableEntry> { it.dayOfWeek }.thenBy { it.startTime }.thenBy { it.lecturerId.toString() })
            .map { e ->
                val lec = e.lecturerId?.let { lecturers[it] }
                val unit = e.academicUnitId?.let { units[it] }
                listOf(
                    days.getOrElse(e.dayOfWeek) { e.dayOfWeek.toString() },
                    e.startTime.toString().substring(0, 5),
                    e.endTime.toString().substring(0, 5),
                    lec?.fullName ?: "",
                    lec?.staffNumber ?: "",
                    unit?.code ?: "",
                    unit?.name ?: "",
                    e.room ?: ""
                )
            }
    }

    private fun buildCsv(kind: String): String {
        val sb = StringBuilder()
        sb.appendLine("# AcademicFlow export — $kind")
        sb.appendLine("# Generated ${stampFmt.format(Instant.now())} Africa/Nairobi")
        sb.appendLine()
        if (kind == "allocations" || kind == "full") {
            sb.appendLine("## Lecturer–Unit Allocations (published)")
            sb.appendLine(
                listOf(
                    "Unit Code", "Unit Name", "Lecturer", "Staff No", "Lecturer Department",
                    "Source Department", "Contact Hours", "Students", "Match Score", "Status", "Override Reason"
                ).joinToString(",") { csvEscape(it) }
            )
            allocationRows().forEach { row ->
                sb.appendLine(row.joinToString(",") { csvEscape(it) })
            }
            sb.appendLine()
        }
        if (kind == "timetable" || kind == "full") {
            sb.appendLine("## Lecturer Timetable")
            sb.appendLine(
                listOf(
                    "Day", "Start", "End", "Lecturer", "Staff No", "Unit Code", "Unit Name", "Room"
                ).joinToString(",") { csvEscape(it) }
            )
            timetableRows().forEach { row ->
                sb.appendLine(row.joinToString(",") { csvEscape(it) })
            }
        }
        return sb.toString()
    }

    private fun buildPdf(kind: String): ByteArray {
        val doc = PDDocument()
        try {
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            var page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            var y = 800f

            fun newPage() {
                cs.close()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                y = 800f
            }

            fun ensureSpace(needed: Float = 40f) {
                if (y < needed) newPage()
            }

            fun writeLine(text: String, useBold: Boolean = false, size: Float = 10f) {
                ensureSpace()
                cs.beginText()
                cs.setFont(if (useBold) bold else font, size)
                cs.newLineAtOffset(40f, y)
                cs.showText(sanitizePdf(text).take(110))
                cs.endText()
                y -= size + 4f
            }

            fun writeTable(headers: List<String>, rows: List<List<String>>, colWidths: List<Float>) {
                ensureSpace(60f)
                var x = 40f
                cs.setFont(bold, 8f)
                headers.forEachIndexed { i, h ->
                    cs.beginText()
                    cs.newLineAtOffset(x, y)
                    cs.showText(sanitizePdf(h).take(18))
                    cs.endText()
                    x += colWidths[i]
                }
                y -= 12f
                cs.moveTo(40f, y + 8f)
                cs.lineTo(555f, y + 8f)
                cs.stroke()
                cs.setFont(font, 8f)
                rows.forEach { row ->
                    ensureSpace(24f)
                    x = 40f
                    row.forEachIndexed { i, cell ->
                        cs.beginText()
                        cs.newLineAtOffset(x, y)
                        cs.showText(sanitizePdf(cell).take(18))
                        cs.endText()
                        x += colWidths.getOrElse(i) { 60f }
                    }
                    y -= 12f
                }
                y -= 10f
            }

            writeLine("AcademicFlow — Published Teaching Export", true, 14f)
            writeLine("Generated ${stampFmt.format(Instant.now())} (Africa/Nairobi)", false, 9f)
            writeLine("Scope: $kind", false, 9f)
            y -= 8f

            if (kind == "allocations" || kind == "full") {
                writeLine("1. How lecturers and units are allocated", true, 12f)
                writeTable(
                    listOf("Code", "Unit", "Lecturer", "Dept", "Hrs", "Students", "Status"),
                    allocationRows().map { listOf(it[0], it[1], it[2], it[4], it[6], it[7], it[9]) },
                    listOf(50f, 120f, 110f, 80f, 35f, 50f, 55f)
                )
                if (allocationRows().isEmpty()) writeLine("No published allocations yet.")
            }

            if (kind == "timetable" || kind == "full") {
                writeLine("2. Lecturer timetable", true, 12f)
                writeTable(
                    listOf("Day", "Start", "End", "Lecturer", "Unit", "Room"),
                    timetableRows().map { listOf(it[0], it[1], it[2], it[3], it[5], it[7]) },
                    listOf(70f, 40f, 40f, 130f, 70f, 60f)
                )
                if (timetableRows().isEmpty()) writeLine("No timetable slots for published allocations.")
            }

            cs.close()
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    private fun csvEscape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needs) "\"$escaped\"" else escaped
    }

    private fun sanitizePdf(value: String): String =
        value.replace(Regex("[^\\x20-\\x7E]"), "?")
}
