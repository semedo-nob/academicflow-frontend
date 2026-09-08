package com.academicflow.service.suitability

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Lightweight outline text extraction. Structured parse first; no OCR.
 * Uncertain extractions are flagged needsReview with low confidence.
 */
object OutlineTextExtractor {

    data class Extraction(
        val text: String,
        val topics: List<String>,
        val outcomes: List<String>,
        val prerequisites: List<String>,
        val confidence: BigDecimal,
        val needsReview: Boolean,
        val warnings: List<String>
    )

    fun extract(fileName: String, contentType: String?, bytes: ByteArray): Extraction {
        val lower = fileName.lowercase()
        val text = try {
            when {
                lower.endsWith(".pdf") || (contentType ?: "").contains("pdf") -> extractPdf(bytes)
                lower.endsWith(".docx") || (contentType ?: "").contains("wordprocessingml") -> extractDocx(bytes)
                lower.endsWith(".txt") || lower.endsWith(".md") || (contentType ?: "").startsWith("text/") ->
                    String(bytes, Charsets.UTF_8)
                else -> String(bytes, Charsets.UTF_8)
            }.trim()
        } catch (_: Exception) {
            return Extraction(
                "",
                emptyList(),
                emptyList(),
                emptyList(),
                BigDecimal.ZERO,
                true,
                listOf("Could not parse document text — needs manual review (OCR not applied).")
            )
        }

        if (text.isBlank()) {
            return Extraction("", emptyList(), emptyList(), emptyList(), BigDecimal.ZERO, true, listOf("No extractable text — needs manual review (OCR not applied)."))
        }

        val topics = extractSection(text, listOf("topics", "course content", "syllabus", "content"))
            .ifEmpty { bulletishLines(text).take(12) }
        val outcomes = extractSection(text, listOf("learning outcomes", "outcomes", "objectives"))
        val prereqs = extractSection(text, listOf("prerequisites", "pre-requisites", "prerequisite"))

        val signals = listOf(topics.isNotEmpty(), outcomes.isNotEmpty(), prereqs.isNotEmpty(), text.length > 200).count { it }
        val confidence = when (signals) {
            4 -> BigDecimal("0.82")
            3 -> BigDecimal("0.68")
            2 -> BigDecimal("0.52")
            1 -> BigDecimal("0.35")
            else -> BigDecimal("0.20")
        }.setScale(2, RoundingMode.HALF_UP)

        val warnings = mutableListOf<String>()
        if (confidence < BigDecimal("0.60")) warnings += "Low-confidence extraction — chairperson should review before matching."
        if (topics.isEmpty()) warnings += "No topics detected automatically."

        return Extraction(
            text = text.take(20000),
            topics = topics.take(30),
            outcomes = outcomes.take(20),
            prerequisites = prereqs.take(15),
            confidence = confidence,
            needsReview = confidence < BigDecimal("0.75"),
            warnings = warnings
        )
    }

    private fun extractPdf(bytes: ByteArray): String {
        val doc = Loader.loadPDF(bytes)
        return try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }

    private fun extractDocx(bytes: ByteArray): String {
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            return doc.paragraphs.joinToString("\n") { it.text ?: "" }
        }
    }

    private fun extractSection(text: String, headers: List<String>): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val start = lines.indexOfFirst { line ->
            val l = line.lowercase().trimEnd(':')
            headers.any { h -> l == h || l.startsWith("$h:") || l.startsWith(h) }
        }
        if (start < 0) return emptyList()
        val collected = mutableListOf<String>()
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.length < 80 && line.endsWith(":") && !line.startsWith("-") && !line.startsWith("•")) break
            if (line.matches(Regex("""^\d+(\.\d+)*\s+.+"""))) {
                collected += line.replace(Regex("""^\d+(\.\d+)*\s+"""), "").trim()
            } else if (line.startsWith("-") || line.startsWith("•") || line.startsWith("*")) {
                collected += line.trimStart('-', '•', '*', ' ').trim()
            } else if (collected.isNotEmpty() && line.length < 120) {
                collected += line
            }
            if (collected.size >= 25) break
        }
        return collected.filter { it.length in 3..200 }.distinct()
    }

    private fun bulletishLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter {
            (it.startsWith("-") || it.startsWith("•") || it.matches(Regex("""^\d+\.\s+.+"""))) && it.length in 5..160
        }.map { it.trimStart('-', '•', ' ').replace(Regex("""^\d+\.\s+"""), "").trim() }
}
