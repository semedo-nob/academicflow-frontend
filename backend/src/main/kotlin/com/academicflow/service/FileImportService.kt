package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.dto.ColumnMappingSuggestionDto
import com.academicflow.dto.CreateImportSessionRequest
import com.academicflow.dto.ImportUploadResultDto
import com.academicflow.dto.SearchHitDto
import com.academicflow.dto.SearchResultDto
import com.academicflow.repository.AcademicUnitRepository
import com.academicflow.repository.ImportMappingProfileRepository
import com.academicflow.repository.LecturerRepository
import com.academicflow.repository.OrganizationNodeRepository
import com.academicflow.repository.TeachingRequestRepository
import com.academicflow.service.importing.ImportFieldMapper
import com.academicflow.service.ingestion.AllocationOcrTableParser
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@Service
class FileImportService(
    private val academicFlowService: AcademicFlowService,
    private val lecturerRepo: LecturerRepository,
    private val unitRepo: AcademicUnitRepository,
    private val requestRepo: TeachingRequestRepository,
    private val orgRepo: OrganizationNodeRepository,
    private val mappingRepo: ImportMappingProfileRepository
) {
    companion object {
        private const val MAX_BYTES = 12 * 1024 * 1024 // 12 MB
    }

    fun search(query: String): SearchResultDto {
        val q = query.trim().lowercase()
        if (q.isBlank()) return SearchResultDto(emptyList())
        val tenantId = TenantContext.get()
        val hits = mutableListOf<SearchHitDto>()

        lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).forEach { l ->
            if (listOf(l.fullName, l.staffNumber, l.email, l.qualifications ?: "").any { it.lowercase().contains(q) }) {
                hits += SearchHitDto("lecturer", l.id.toString(), l.fullName, "${l.staffNumber} · ${l.email}", "/lecturers")
            }
        }
        unitRepo.findByTenantIdOrderByCodeAsc(tenantId).forEach { u ->
            if (listOf(u.code, u.name, u.requiredExpertise ?: "").any { it.lowercase().contains(q) }) {
                hits += SearchHitDto("unit", u.id.toString(), "${u.code} — ${u.name}", u.status, "/units")
            }
        }
        requestRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).forEach { r ->
            val unit = unitRepo.findByTenantIdAndId(tenantId, r.academicUnitId)
            val label = unit?.let { "${it.code} — ${it.name}" } ?: r.id.toString()
            if (label.lowercase().contains(q) || r.status.lowercase().contains(q) || (r.requiredExpertise ?: "").lowercase().contains(q)) {
                hits += SearchHitDto("request", r.id.toString(), label, r.status, "/requests")
            }
        }
        orgRepo.findByTenantIdOrderByNameAsc(tenantId).forEach { o ->
            if (o.name.lowercase().contains(q) || o.type.lowercase().contains(q)) {
                hits += SearchHitDto("organization", o.id.toString(), o.name, o.type, "/organization")
            }
        }
        return SearchResultDto(hits.take(40))
    }

    fun upload(file: MultipartFile, entityTypeHint: String?): ImportUploadResultDto {
        if (file.isEmpty) throw IllegalArgumentException("Empty upload")
        if (file.size > MAX_BYTES) throw IllegalArgumentException("File exceeds 12 MB limit")

        val name = file.originalFilename ?: "upload"
        val lower = name.lowercase()
        val contentType = (file.contentType ?: "").lowercase()
        val allowed = lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt") ||
            lower.endsWith(".pdf") || lower.endsWith(".xlsx") || lower.endsWith(".xls") ||
            contentType.contains("csv") || contentType.contains("pdf") ||
            contentType.contains("sheet") || contentType.contains("excel") || contentType.contains("text")
        if (!allowed) {
            throw IllegalArgumentException("Unsupported file type. Use CSV, TSV, TXT, XLSX, XLS, or PDF.")
        }

        val (headers, rows, warnings) = when {
            lower.endsWith(".xlsx") || lower.endsWith(".xls") || contentType.contains("sheet") || contentType.contains("excel") ->
                parseExcel(file.bytes)
            lower.endsWith(".csv") || contentType.contains("csv") || contentType.contains("text/plain") || lower.endsWith(".txt") ->
                parseCsv(file.bytes)
            lower.endsWith(".pdf") || contentType.contains("pdf") ->
                parsePdf(file.bytes)
            lower.endsWith(".tsv") ->
                parseDelimited(String(file.bytes, StandardCharsets.UTF_8), '\t')
            else -> {
                try {
                    parseCsv(file.bytes)
                } catch (_: Exception) {
                    try {
                        parseExcel(file.bytes)
                    } catch (_: Exception) {
                        parsePdf(file.bytes)
                    }
                }
            }
        }

        if (headers.isEmpty() || rows.isEmpty()) {
            throw IllegalArgumentException("Could not detect tabular data in $name. Use CSV/XLSX or a PDF with a clear table/header row.")
        }

        val detectedType = entityTypeHint?.takeIf { it.isNotBlank() }?.uppercase()
            ?: ImportFieldMapper.detectEntityType(headers, rows)

        val tenantId = TenantContext.get()
        val profiles = mappingRepo.findByTenantIdAndEntityTypeIgnoreCaseOrderByNameAsc(tenantId, detectedType)
        val (profileMap, profileName) = pickBestProfile(headers, profiles)

        val mapping = ImportFieldMapper.suggest(
            headers = headers,
            sampleRows = rows.take(25),
            entityType = detectedType,
            profileMap = profileMap
        )

        val mappedRows = rows.map { row ->
            headers.mapIndexed { i, h -> h to (row.getOrNull(i) ?: "") }.toMap()
        }

        val session = academicFlowService.createImportSession(
            CreateImportSessionRequest(
                fileName = name,
                entityType = detectedType,
                columnMap = mapping.columnMap,
                rows = mappedRows
            )
        )

        return ImportUploadResultDto(
            sessionId = session.id,
            fileName = session.fileName,
            entityType = session.entityType,
            status = session.status,
            detectedColumns = headers,
            suggestedMap = mapping.columnMap,
            mappingSuggestions = mapping.suggestions.map {
                ColumnMappingSuggestionDto(
                    sourceColumn = it.sourceColumn,
                    targetField = it.targetField,
                    confidence = it.confidence,
                    method = it.method,
                    sampleValues = it.sampleValues,
                    unmapped = it.unmapped || it.targetField == null
                )
            },
            unmappedColumns = mapping.unmappedColumns,
            canonicalFields = ImportFieldMapper.canonicalFields(detectedType),
            profileApplied = profileName,
            rowCount = mappedRows.size,
            preview = mappedRows.take(8),
            warnings = warnings + mapping.warnings + listOfNotNull(
                profileName?.let { "Applied institution mapping profile: $it" }
            )
        )
    }

    private fun pickBestProfile(
        headers: List<String>,
        profiles: List<com.academicflow.entity.ImportMappingProfile>
    ): Pair<Map<String, String>?, String?> {
        if (profiles.isEmpty()) return null to null
        val normHeaders = headers.map { ImportFieldMapper.normalize(it) }.toSet()
        var bestScore = 0.0
        var best: com.academicflow.entity.ImportMappingProfile? = null
        profiles.forEach { p ->
            val map = ImportFieldMapper.parseColumnMap(p.columnMap)
            if (map.isEmpty()) return@forEach
            val hits = map.keys.count { k ->
                val nk = ImportFieldMapper.normalize(k)
                headers.any { it.equals(k, true) } || nk in normHeaders
            }
            val score = hits.toDouble() / map.size
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        return if (bestScore >= 0.5 && best != null) {
            ImportFieldMapper.parseColumnMap(best!!.columnMap) to best!!.name
        } else null to null
    }

    private fun parseExcel(bytes: ByteArray): Triple<List<String>, List<List<String>>, List<String>> {
        val warnings = mutableListOf("Parsed spreadsheet via Apache POI.")
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0) ?: throw IllegalArgumentException("Workbook has no sheets")
            val formatter = DataFormatter()
            val matrix = mutableListOf<List<String>>()
            for (row in sheet) {
                if (row == null) continue
                val cells = (0 until row.lastCellNum.coerceAtLeast(0)).map { idx ->
                    formatter.formatCellValue(row.getCell(idx)).trim()
                }
                if (cells.any { it.isNotBlank() }) matrix += cells
            }
            if (matrix.size < 2) throw IllegalArgumentException("Spreadsheet needs a header row and at least one data row")
            val headerIdx = matrix.indexOfFirst { line ->
                val lower = line.joinToString(" ").lowercase()
                listOf("staff", "name", "email", "course", "code", "department", "hours", "lecturer", "unit", "instructor", "module")
                    .any { lower.contains(it) }
            }.takeIf { it >= 0 } ?: 0
            val headers = matrix[headerIdx].mapIndexed { i, h -> h.ifBlank { "Column${i + 1}" } }
            val width = headers.size
            val rows = matrix.drop(headerIdx + 1).map { row ->
                (0 until width).map { i -> row.getOrNull(i) ?: "" }
            }
            if (rows.isEmpty()) warnings += "No data rows found under the header."
            return Triple(headers, rows, warnings)
        }
    }

    private fun parseCsv(bytes: ByteArray): Triple<List<String>, List<List<String>>, List<String>> {
        val text = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        val delim = detectDelimiter(text)
        return parseDelimited(text, delim)
    }

    private fun detectDelimiter(text: String): Char {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val counts = mapOf(',' to first.count { it == ',' }, ';' to first.count { it == ';' }, '\t' to first.count { it == '\t' }, '|' to first.count { it == '|' })
        return counts.maxByOrNull { it.value }?.key?.takeIf { (counts[it] ?: 0) > 0 } ?: ','
    }

    private fun parseDelimited(text: String, delim: Char): Triple<List<String>, List<List<String>>, List<String>> {
        val warnings = mutableListOf<String>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Triple(emptyList(), emptyList(), listOf("Empty file"))
        val headerIdx = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            listOf("staff", "name", "email", "course", "code", "department", "hours", "lecturer", "unit", "instructor", "module", "employee")
                .any { lower.contains(it) }
        }.takeIf { it >= 0 } ?: 0
        val headers = splitCsvLine(lines[headerIdx], delim).map { it.trim().ifBlank { "Column" } }
        val rows = lines.drop(headerIdx + 1).mapNotNull { line ->
            val cols = splitCsvLine(line, delim)
            if (cols.all { it.isBlank() }) null else cols
        }
        if (rows.isEmpty()) warnings += "No data rows found under the header."
        return Triple(headers, rows, warnings)
    }

    private fun splitCsvLine(line: String, delim: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = !inQuotes
                }
                c == delim && !inQuotes -> {
                    result += sb.toString().trim(); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result += sb.toString().trim()
        return result
    }

    private fun parsePdf(bytes: ByteArray): Triple<List<String>, List<List<String>>, List<String>> {
        val warnings = mutableListOf<String>()
        val text = try {
            val doc = Loader.loadPDF(bytes)
            try {
                PDFTextStripper().getText(doc)
            } finally {
                doc.close()
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                throw IllegalArgumentException("This PDF is password protected. Please upload an unlocked copy, or export as CSV/XLSX.")
            }
            throw IllegalArgumentException("Could not open PDF: $msg")
        }

        val meaningful = text.replace(Regex("\\s+"), " ").trim()
        if (meaningful.length < 40) {
            // Attempt OCR for scanned allocation sheets when tesseract is available
            val ocrText = tryOcrPdf(bytes)
            if (ocrText != null && ocrText.replace(Regex("\\s+"), " ").trim().length >= 40) {
                warnings += "PDF had little extractable text — used OCR. Verify imported rows carefully."
                return parsePdfText(ocrText, warnings)
            }
            throw IllegalArgumentException(
                "This PDF looks scanned (no extractable text). For allocations, export as CSV/XLSX. " +
                    "For course outlines, use Allocate by context (OCR will process scanned outlines). " +
                    "Install tesseract-ocr on the server to enable PDF OCR import."
            )
        }

        warnings += "Extracted text from PDF (${text.lines().count { it.isNotBlank() }} lines)."
        return parsePdfText(text, warnings)
    }

    private fun tryOcrPdf(bytes: ByteArray): String? {
        return try {
            val ocr = com.academicflow.service.ingestion.PdfOcrExtractor().extract(bytes, "import.pdf")
            ocr.text.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePdfText(text: String, warnings: MutableList<String>): Triple<List<String>, List<List<String>>, List<String>> {
        // Scanned allocation sheets (OCR) — lecturer + unit code rows
        AllocationOcrTableParser.parse(text)?.let { parsed ->
            return Triple(parsed.headers, parsed.rows, warnings + parsed.warnings)
        }

        val normalized = normalizePdfText(text)
        val csvCandidate = if (normalized.contains(',')) normalized else text

        val csvLike = csvCandidate.lines().map { it.trim() }.filter { line ->
            val commas = line.count { it == ',' }
            val pipes = line.count { it == '|' }
            val tabs = line.count { it == '\t' }
            commas >= 2 || pipes >= 2 || tabs >= 2
        }
        if (csvLike.size >= 2) {
            val sample = csvLike.first()
            val delim = when {
                sample.count { it == '|' } >= 2 -> '|'
                sample.count { it == '\t' } >= 2 -> '\t'
                else -> ','
            }
            return parseDelimited(csvLike.joinToString("\n"), delim).let { (h, r, w) ->
                Triple(h, r, warnings + w)
            }
        }

        if (csvCandidate.count { it == ',' } >= 5) {
            val rebuilt = rebuildCsvFromBlob(csvCandidate)
            if (rebuilt != null) {
                warnings += "Reconstructed table from PDF text stream."
                return parseDelimited(rebuilt, ',').let { (h, r, w) -> Triple(h, r, warnings + w) }
            }
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val headerLine = lines.firstOrNull { l ->
            val lower = l.lowercase()
            (lower.contains("staff") || lower.contains("course") || lower.contains("name") || lower.contains("instructor")) &&
                (lower.contains("dept") || lower.contains("email") || lower.contains("code") || lower.contains("hours") || lower.contains("unit"))
        }
        if (headerLine != null) {
            val headers = headerLine.split(Regex("\\s{2,}|\t|\\|")).map { it.trim() }.filter { it.isNotEmpty() }
            val start = lines.indexOf(headerLine) + 1
            val rows = lines.drop(start).takeWhile { !it.lowercase().startsWith("page ") }.mapNotNull { line ->
                val cols = line.split(Regex("\\s{2,}|\t|\\|")).map { it.trim() }.filter { it.isNotEmpty() }
                if (cols.size >= 2) cols else null
            }
            if (headers.isNotEmpty() && rows.isNotEmpty()) {
                return Triple(headers, rows, warnings)
            }
        }

        val people = lines.mapNotNull { line ->
            val m = Regex("""^(Dr\.|Prof\.|Mr\.|Ms\.)?\s*([A-Z][a-zA-Z'’\-]+(?:\s+[A-Z][a-zA-Z'’\-]+){1,3})\s*[,|\-]\s*(.+)$""").find(line)
            m?.let {
                listOf(it.groupValues[2].trim(), it.groupValues[3].trim())
            }
        }
        if (people.isNotEmpty()) {
            warnings += "Used free-text name/department pattern from PDF."
            return Triple(listOf("Staff Name", "Department"), people, warnings)
        }

        throw IllegalArgumentException(
            "PDF did not contain a recognizable allocation table. Export as CSV/XLSX, or ensure the PDF has a clear header row " +
                "(e.g. Course Code, Lecturer, Department)."
        )
    }

    private fun normalizePdfText(text: String): String =
        text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("""(?<=Email|Hours|Students|Department)(?=[A-Z0-9])"""), "\n")
            .replace(Regex("""(?<=[a-z])(?=[A-Z]{2,}/)"""), "\n")

    private fun rebuildCsvFromBlob(text: String): String? {
        val flat = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val headerPatterns = listOf(
            Regex("""(Staff No\s*,\s*Staff Name\s*,\s*Department\s*,\s*Email(?:\s*,\s*Teaching Hours)?)""", RegexOption.IGNORE_CASE),
            Regex("""(Course Code\s*,\s*Course Name\s*,\s*Department\s*,\s*Teaching Hours(?:\s*,\s*Students)?)""", RegexOption.IGNORE_CASE),
            Regex("""(Staff No\s*,\s*Staff Name\s*,\s*Department)""", RegexOption.IGNORE_CASE),
            Regex("""(Employee ID\s*,\s*Instructor\s*,\s*Unit\s*,\s*Unit Description)""", RegexOption.IGNORE_CASE)
        )
        val match = headerPatterns.firstNotNullOfOrNull { it.find(flat) } ?: return null
        val header = match.value
        val rest = flat.substring(match.range.last + 1).trim().trimStart(',', ' ')
        val colCount = header.count { it == ',' } + 1
        val cells = splitCsvLine(rest, ',')
        if (cells.size < colCount) return null
        val rows = cells.chunked(colCount).filter { it.size == colCount && it.any { c -> c.isNotBlank() } }
        if (rows.isEmpty()) return null
        return (listOf(header) + rows.map { it.joinToString(",") }).joinToString("\n")
    }
}
