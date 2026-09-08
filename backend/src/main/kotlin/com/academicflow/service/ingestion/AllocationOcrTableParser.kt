package com.academicflow.service.ingestion

/**
 * Parses messy OCR text from scanned university allocation sheets into tabular rows.
 * Keeps OCR values as-is (does not invent corrections).
 */
object AllocationOcrTableParser {

    private val titlePrefix = Regex("""^(?:Dr\.?|Prof\.?|Mr\.?|Ms\.?|Mrs\.?)\s+""", RegexOption.IGNORE_CASE)
    private val unitCode = Regex(
        """\b([A-Z]{2,5})[\s|/]*([0-9O]{3,4}[A-Z]?)\b"""
    )

    data class Result(
        val headers: List<String>,
        val rows: List<List<String>>,
        val warnings: List<String>
    )

    fun parse(text: String): Result? {
        if (text.isBlank()) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.none {
                it.contains("ALLOCAT", ignoreCase = true) ||
                    it.contains("UNITS", ignoreCase = true) ||
                    unitCode.containsMatchIn(it)
            }
        ) {
            return null
        }

        var current: String? = null
        val rows = mutableListOf<List<String>>()
        val warnings = mutableListOf("Parsed lecturer/unit rows from OCR allocation sheet — verify codes before commit.")

        fun addUnits(lecturer: String, segment: String) {
            val matches = unitCode.findAll(segment).toList()
            if (matches.isEmpty()) return
            matches.forEachIndexed { idx, m ->
                val letters = m.groupValues[1]
                val digits = m.groupValues[2].replace('O', '0')
                val code = "$letters $digits"
                val nextStart = matches.getOrNull(idx + 1)?.range?.first ?: segment.length
                val title = segment.substring(m.range.last + 1, nextStart)
                    .replace(Regex("""^\s*[|,.\-]+\s*"""), "")
                    .replace(Regex("""\s+\d+[.]?\s*$"""), "")
                    .trim()
                    .trimStart(',', '.', '|', ' ')
                    .take(120)
                rows += listOf(lecturer, code, title)
            }
        }

        fun isBogusName(name: String): Boolean {
            val n = name.lowercase()
            return n.contains("allocation") || n.contains("department") || n.contains("units") ||
                n.contains("mathematics") || n.contains("statistics") || n.contains("semester") ||
                n.startsWith("tallocation") || n.startsWith("sno") || name.length > 40
        }

        fun extractLeadingName(line: String): Pair<String, String>? {
            val titled = titlePrefix.find(line)
            val body = if (titled != null) line.substring(titled.range.last + 1).trim() else line
            val words = body.split(Regex("""\s+"""))
            val nameWords = mutableListOf<String>()
            for (w in words) {
                if (w.matches(Regex("""[A-Z][a-zA-Z'’\-]{1,}"""))) {
                    nameWords += w
                    if (nameWords.size >= 3) break
                } else if (nameWords.isNotEmpty()) {
                    break
                } else {
                    return null
                }
            }
            if (nameWords.size < 2) return null
            val name = nameWords.joinToString(" ")
            if (isBogusName(name)) return null
            val rest = body.substring(body.indexOf(nameWords.last()) + nameWords.last().length).trim()
            return name to rest
        }

        for (raw in lines) {
            val line = raw.replace('|', ' ').replace(Regex("""\s+"""), " ").trim()
            if (line.length < 3) continue
            if (line.matches(Regex("""(?i)^(?:s/?no|name|sem|units?\s+allocated|department of|allocation for|t?allocation).*"""))) continue
            if (line.equals("Lecturer", ignoreCase = true)) continue
            if (line.matches(Regex("""(?i)^(?:academic\s+regitrar|academic\s+registrar)$"""))) continue
            if (line.matches(Regex("""(?i)^goa\s*wn$"""))) continue

            val hasCode = unitCode.containsMatchIn(line)
            val leading = extractLeadingName(line)

            if (leading != null && (titlePrefix.containsMatchIn(line) || (!hasCode && line.length < 55))) {
                current = leading.first
                if (hasCode) addUnits(current!!, leading.second)
                continue
            }

            // Untitled name line like "Patrick Mathagu 1 SPPI 1201 ..."
            if (leading != null && hasCode && leading.first.split(" ").size >= 2) {
                current = leading.first
                addUnits(current!!, leading.second)
                continue
            }

            if (current != null && hasCode) {
                val cleaned = line.replace(Regex("""^\d+[.]?\s*"""), "")
                addUnits(current!!, cleaned)
            }
        }

        if (rows.size < 2) return null
        val distinct = rows.distinctBy { (it[0] + "|" + it[1]).lowercase() }
        if (distinct.size < 2) return null
        return Result(
            headers = listOf("Staff Name", "Course Code", "Course Title"),
            rows = distinct,
            warnings = warnings
        )
    }
}
