package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.dto.CreateImportSessionRequest
import com.academicflow.dto.ImportUploadResultDto
import com.academicflow.dto.SearchHitDto
import com.academicflow.dto.SearchResultDto
import com.academicflow.repository.AcademicUnitRepository
import com.academicflow.repository.LecturerRepository
import com.academicflow.repository.OrganizationNodeRepository
import com.academicflow.repository.TeachingRequestRepository
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

@Service
class FileImportService(
    private val academicFlowService: AcademicFlowService,
    private val lecturerRepo: LecturerRepository,
    private val unitRepo: AcademicUnitRepository,
    private val requestRepo: TeachingRequestRepository,
    private val orgRepo: OrganizationNodeRepository
) {
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
        val name = file.originalFilename ?: "upload"
        val lower = name.lowercase()
        val contentType = file.contentType ?: ""
        val (headers, rows, warnings) = when {
            lower.endsWith(".csv") || contentType.contains("csv") || contentType.contains("text/plain") ->
                parseCsv(file.bytes)
            lower.endsWith(".pdf") || contentType.contains("pdf") ->
                parsePdf(file.bytes)
            lower.endsWith(".tsv") ->
                parseDelimited(String(file.bytes, StandardCharsets.UTF_8), '\t')
            else -> {
                // try CSV first, then PDF
                try {
                    parseCsv(file.bytes)
                } catch (_: Exception) {
                    parsePdf(file.bytes)
                }
            }
        }

        if (headers.isEmpty() || rows.isEmpty()) {
            throw IllegalArgumentException("Could not detect tabular data in $name. Use CSV or a PDF with a clear table/header row.")
        }

        val detectedType = entityTypeHint?.takeIf { it.isNotBlank() } ?: detectEntityType(headers, rows)
        val suggestedMap = suggestColumnMap(headers, detectedType)
        val mappedRows = rows.map { row ->
            // keep original header keys for staging; mapping applied at commit
            headers.mapIndexed { i, h -> h to (row.getOrNull(i) ?: "") }.toMap()
        }

        val session = academicFlowService.createImportSession(
            CreateImportSessionRequest(
                fileName = name,
                entityType = detectedType,
                columnMap = suggestedMap,
                rows = mappedRows
            )
        )

        return ImportUploadResultDto(
            sessionId = session.id,
            fileName = session.fileName,
            entityType = session.entityType,
            status = session.status,
            detectedColumns = headers,
            suggestedMap = suggestedMap,
            rowCount = mappedRows.size,
            preview = mappedRows.take(8),
            warnings = warnings
        )
    }

    private fun detectEntityType(headers: List<String>, rows: List<List<String>>): String {
        val joined = (headers + rows.flatten().take(40)).joinToString(" ").lowercase()
        val unitScore = listOf("course", "unit", "code", "students", "contact").count { joined.contains(it) }
        val lecturerScore = listOf("staff", "lecturer", "email", "workload", "qualification", "name").count { joined.contains(it) }
        return if (unitScore > lecturerScore) "ACADEMIC_UNIT" else "LECTURER"
    }

    private fun suggestColumnMap(headers: List<String>, entityType: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        headers.forEach { h ->
            val key = h.lowercase().replace("_", " ").trim()
            val target = when (entityType) {
                "ACADEMIC_UNIT" -> when {
                    key.contains("code") || key == "course code" || key == "unit code" -> "code"
                    key.contains("name") || key.contains("title") || key.contains("course") -> "name"
                    key.contains("dept") || key.contains("department") || key.contains("school") -> "organizationNode"
                    key.contains("hour") || key.contains("contact") -> "contactHours"
                    key.contains("student") || key.contains("enrol") -> "studentCount"
                    key.contains("expert") -> "requiredExpertise"
                    key.contains("academic year") || key == "year" || key.contains("year of study") -> "academicYear"
                    key.contains("semester") || key == "sem" || key == "term" -> "semester"
                    else -> null
                }
                else -> when {
                    key.contains("staff") && (key.contains("no") || key.contains("id") || key.contains("number")) -> "staffNumber"
                    key == "staff no" || key == "staff number" || key == "id" -> "staffNumber"
                    key.contains("name") || key.contains("lecturer") -> "name"
                    key.contains("email") || key.contains("mail") -> "email"
                    key.contains("dept") || key.contains("department") || key.contains("school") -> "organizationNode"
                    key.contains("hour") || key.contains("workload") || key.contains("max") -> "maximumWorkload"
                    key.contains("qualif") -> "qualifications"
                    key.contains("avail") -> "availability"
                    else -> null
                }
            }
            if (target != null && map.values.none { it == target }) {
                map[h] = target
            }
        }
        return map
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
            listOf("staff", "name", "email", "course", "code", "department", "hours", "lecturer", "unit").any { lower.contains(it) }
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
        val doc = Loader.loadPDF(bytes)
        val text = try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
        warnings += "Extracted text from PDF (${text.lines().count { it.isNotBlank() }} lines)."

        // Normalize glued PDF text into line-oriented CSV when possible
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

        // Single long CSV-like blob (common with simple PDFs)
        if (csvCandidate.count { it == ',' } >= 5) {
            val rebuilt = rebuildCsvFromBlob(csvCandidate)
            if (rebuilt != null) {
                warnings += "Reconstructed table from PDF text stream."
                return parseDelimited(rebuilt, ',').let { (h, r, w) -> Triple(h, r, warnings + w) }
            }
        }

        // Fallback: key-value / spaced columns using multi-space split near header keywords
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val headerLine = lines.firstOrNull { l ->
            val lower = l.lowercase()
            (lower.contains("staff") || lower.contains("course") || lower.contains("name")) &&
                (lower.contains("dept") || lower.contains("email") || lower.contains("code") || lower.contains("hours"))
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

        // Last resort: treat lines as "Name, Department" style free text for lecturers
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

        throw IllegalArgumentException("PDF did not contain a recognizable table. Export as CSV or include a header row such as Staff No, Staff Name, Department, Email.")
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
            Regex("""(Staff No\s*,\s*Staff Name\s*,\s*Department)""", RegexOption.IGNORE_CASE)
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
