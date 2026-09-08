package com.academicflow.service.ingestion

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Heading-alias aware outline interpretation. Does not invent facts.
 */
object OutlineSectionParser {

    private val topicHeaders = listOf(
        "topics", "course content", "syllabus", "content", "course topics",
        "weekly topics", "subject content", "module content"
    )
    private val outcomeHeaders = listOf(
        "learning outcomes", "outcomes", "objectives", "course objectives",
        "learning objectives", "course aims", "aims", "intended learning outcomes"
    )
    private val prereqHeaders = listOf(
        "prerequisites", "pre-requisites", "prerequisite", "pre-requisite",
        "entry requirements", "assumed knowledge"
    )

    fun parse(text: String): Triple<List<String>, List<String>, List<String>> {
        if (text.isBlank()) return Triple(emptyList(), emptyList(), emptyList())
        val topics = extractSection(text, topicHeaders).ifEmpty { bulletishLines(text).take(12) }
        val outcomes = extractSection(text, outcomeHeaders)
        val prereqs = extractSection(text, prereqHeaders)
        return Triple(topics.take(30), outcomes.take(20), prereqs.take(15))
    }

    fun confidence(text: String, topics: List<String>, outcomes: List<String>, prereqs: List<String>): BigDecimal {
        val signals = listOf(topics.isNotEmpty(), outcomes.isNotEmpty(), prereqs.isNotEmpty(), text.length > 200).count { it }
        return when (signals) {
            4 -> BigDecimal("0.82")
            3 -> BigDecimal("0.68")
            2 -> BigDecimal("0.52")
            1 -> BigDecimal("0.35")
            else -> BigDecimal("0.20")
        }.setScale(2, RoundingMode.HALF_UP)
    }

    fun extractIdentityFields(text: String): List<ExtractedField> {
        val fields = mutableListOf<ExtractedField>()
        val code = Regex("""(?i)\b(?:course|unit|module|subject)\s*code\s*[:\-]?\s*([A-Z]{2,4}\s*\d{2,4}[A-Z]?)\b""")
            .find(text)?.groupValues?.getOrNull(1)
        if (code != null) fields += ExtractedField("courseCode", code.trim(), "document", "MEDIUM")
        val title = Regex("""(?i)\b(?:course|unit|module)\s*(?:title|name)\s*[:\-]\s*(.+)""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.take(200)
        if (!title.isNullOrBlank()) fields += ExtractedField("courseTitle", title, "document", "MEDIUM")
        return fields
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
            val lower = line.lowercase().trimEnd(':')
            if (line.length < 80 && line.endsWith(":") && !line.startsWith("-") && !line.startsWith("•")) {
                if ((topicHeaders + outcomeHeaders + prereqHeaders).any { lower.startsWith(it) }) break
            }
            when {
                line.matches(Regex("""^\d+(\.\d+)*\s+.+""")) ->
                    collected += line.replace(Regex("""^\d+(\.\d+)*\s+"""), "").trim()
                line.startsWith("-") || line.startsWith("•") || line.startsWith("*") ->
                    collected += line.trimStart('-', '•', '*', ' ').trim()
                collected.isNotEmpty() && line.length < 120 -> collected += line
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
