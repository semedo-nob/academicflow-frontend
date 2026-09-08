package com.academicflow.service.importing

import kotlin.math.max
import kotlin.math.min

/**
 * Institution-agnostic column → canonical AcademicFlow field mapper.
 * Deterministic: aliases + fuzzy header match + sample-value shape inference.
 * Does not require LLM/OCR.
 */
object ImportFieldMapper {

    data class Suggestion(
        val sourceColumn: String,
        val targetField: String?,
        val confidence: Double,
        val method: String,
        val sampleValues: List<String> = emptyList(),
        val unmapped: Boolean = false
    )

    data class MappingResult(
        val entityType: String,
        val suggestions: List<Suggestion>,
        /** source → target for accepted mappings (confidence >= autoAccept or profile) */
        val columnMap: Map<String, String>,
        val unmappedColumns: List<String>,
        val warnings: List<String> = emptyList()
    )

    private data class Alias(val field: String, val phrases: List<String>, val weight: Double = 1.0)

    private val lecturerAliases = listOf(
        Alias("staffNumber", listOf(
            "staff no", "staff no.", "staff number", "staff id", "employee id", "employee number",
            "payroll no", "payroll number", "lecturer id", "instructor id", "personnel number", "pf number"
        )),
        Alias("name", listOf(
            "lecturer name", "staff name", "instructor", "lecturer", "teacher", "full name",
            "name of lecturer", "faculty name", "tutor"
        )),
        Alias("email", listOf("email", "e-mail", "mail", "email address", "work email")),
        Alias("organizationNode", listOf("department", "dept", "school", "faculty", "division", "unit home")),
        Alias("maximumWorkload", listOf("max hours", "maximum workload", "teaching hours", "workload", "max load")),
        Alias("qualifications", listOf("qualifications", "qualification", "title")),
        Alias("availability", listOf("availability", "available", "schedule"))
    )

    private val unitAliases = listOf(
        Alias("code", listOf(
            "course code", "unit code", "module code", "module ref", "course id", "unit id", "unit", "course"
        )),
        Alias("name", listOf(
            "course name", "course title", "unit name", "unit description", "module name",
            "module title", "title", "description"
        )),
        Alias("organizationNode", listOf("department", "dept", "school", "faculty", "offering department")),
        Alias("contactHours", listOf("hours", "hrs", "contact hours", "contact hrs", "teaching hours", "workload")),
        Alias("studentCount", listOf("students", "student count", "enrolment", "enrollment", "class size")),
        Alias("requiredExpertise", listOf("expertise", "required expertise", "subject")),
        Alias("academicYear", listOf("academic year", "year of study", "year", "session", "ay")),
        Alias("semester", listOf("semester", "sem", "term", "trimester", "period"))
    )

    private val allocationAliases = listOf(
        Alias("staffNumber", listOf(
            "staff no", "staff no.", "staff number", "employee id", "employee number", "payroll no",
            "lecturer id", "instructor id", "personnel number"
        )),
        Alias("lecturerName", listOf(
            "lecturer name", "staff name", "instructor", "lecturer", "teacher", "tutor", "faculty name"
        )),
        Alias("email", listOf("email", "e-mail", "mail", "email address")),
        Alias("unitCode", listOf(
            "course code", "unit code", "module code", "module ref", "course id", "unit id", "unit", "course", "module"
        )),
        Alias("unitName", listOf(
            "course title", "course name", "unit name", "unit description", "module name", "module title", "title"
        )),
        Alias("organizationNode", listOf("department", "dept", "school", "faculty")),
        Alias("contactHours", listOf("hours", "hrs", "contact hours", "contact hrs", "teaching hours", "workload")),
        Alias("academicYear", listOf("academic year", "year", "session", "ay")),
        Alias("semester", listOf("semester", "sem", "term", "trimester")),
        Alias("programme", listOf("programme", "program", "degree programme", "degree program")),
        Alias("contextLabel", listOf("context", "stream", "track", "specialization context"))
    )

    fun canonicalFields(entityType: String): List<String> = when (entityType.uppercase()) {
        "ACADEMIC_UNIT" -> unitAliases.map { it.field }.distinct()
        "ALLOCATION" -> allocationAliases.map { it.field }.distinct()
        else -> lecturerAliases.map { it.field }.distinct()
    }

    fun requiredFields(entityType: String): List<String> = when (entityType.uppercase()) {
        "ACADEMIC_UNIT" -> listOf("code")
        "ALLOCATION" -> listOf("unitCode") // staffNumber OR lecturerName also required at validate
        else -> listOf("name", "staffNumber")
    }

    fun detectEntityType(headers: List<String>, sampleRows: List<List<String>>): String {
        val joined = (headers + sampleRows.flatten().take(60)).joinToString(" ").lowercase()
        val hasPerson = listOf("staff", "lecturer", "instructor", "employee", "teacher", "tutor").any { joined.contains(it) }
        val hasUnit = listOf("course", "unit", "module").any { joined.contains(it) }
        val unitOnly = listOf("students", "enrol", "expertise").count { joined.contains(it) }
        val lecturerOnly = listOf("email", "qualification", "availability").count { joined.contains(it) }

        return when {
            hasPerson && hasUnit -> "ALLOCATION"
            unitOnly > lecturerOnly && hasUnit && !hasPerson -> "ACADEMIC_UNIT"
            hasUnit && !hasPerson -> "ACADEMIC_UNIT"
            else -> "LECTURER"
        }
    }

    fun suggest(
        headers: List<String>,
        sampleRows: List<List<String>>,
        entityType: String,
        profileMap: Map<String, String>? = null,
        autoAcceptThreshold: Double = 0.82
    ): MappingResult {
        val aliases = when (entityType.uppercase()) {
            "ACADEMIC_UNIT" -> unitAliases
            "ALLOCATION" -> allocationAliases
            else -> lecturerAliases
        }
        val samplesByCol = headers.mapIndexed { i, h ->
            h to sampleRows.mapNotNull { it.getOrNull(i)?.trim()?.takeIf { v -> v.isNotEmpty() } }.take(8)
        }.toMap()

        val usedTargets = mutableSetOf<String>()
        val suggestions = mutableListOf<Suggestion>()
        val warnings = mutableListOf<String>()

        // 1) Apply institution profile exact header matches first
        if (!profileMap.isNullOrEmpty()) {
            headers.forEach { h ->
                val target = profileMap.entries.firstOrNull {
                    it.key.equals(h, true) || normalize(it.key) == normalize(h)
                }?.value
                if (target != null && target !in usedTargets) {
                    usedTargets += target
                    suggestions += Suggestion(
                        sourceColumn = h,
                        targetField = target,
                        confidence = 0.99,
                        method = "institution_profile",
                        sampleValues = samplesByCol[h].orEmpty()
                    )
                }
            }
        }

        headers.filter { h -> suggestions.none { it.sourceColumn == h } }.forEach { h ->
            val norm = normalize(h)
            val samples = samplesByCol[h].orEmpty()

            var bestField: String? = null
            var bestScore = 0.0
            var bestMethod = "none"

            // Exact / semantic alias
            aliases.forEach { alias ->
                alias.phrases.forEach { phrase ->
                    val pn = normalize(phrase)
                    val score = when {
                        norm == pn -> 0.98 * alias.weight
                        norm.contains(pn) || pn.contains(norm) -> 0.92 * alias.weight
                        tokenOverlap(norm, pn) >= 0.66 -> 0.88 * alias.weight
                        levenshteinRatio(norm, pn) >= 0.82 -> 0.84 * alias.weight
                        else -> 0.0
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestField = alias.field
                        bestMethod = if (score >= 0.95) "exact_alias" else if (score >= 0.88) "semantic_alias" else "fuzzy_alias"
                    }
                }
            }

            // Shape inference boost / override when header is weak
            val shape = inferShape(samples)
            if (shape != null) {
                val shapeTarget = shapeTarget(entityType, shape)
                if (shapeTarget != null) {
                    val shapeScore = if (bestField == shapeTarget) min(0.99, bestScore + 0.08) else 0.78
                    if (shapeScore > bestScore && shapeTarget !in usedTargets) {
                        bestScore = shapeScore
                        bestField = shapeTarget
                        bestMethod = if (bestMethod == "none") "data_shape" else "${bestMethod}+data_shape"
                    } else if (bestField == shapeTarget) {
                        bestScore = min(0.99, bestScore + 0.06)
                        bestMethod = "$bestMethod+data_shape"
                    }
                }
            }

            if (bestField != null && bestField in usedTargets) {
                // already claimed — keep as unmapped suggestion
                suggestions += Suggestion(
                    sourceColumn = h,
                    targetField = null,
                    confidence = bestScore,
                    method = "conflict_${bestMethod}",
                    sampleValues = samples,
                    unmapped = true
                )
            } else if (bestField != null && bestScore >= 0.55) {
                usedTargets += bestField!!
                suggestions += Suggestion(
                    sourceColumn = h,
                    targetField = bestField,
                    confidence = bestScore.coerceIn(0.0, 0.99),
                    method = bestMethod,
                    sampleValues = samples,
                    unmapped = false
                )
            } else {
                suggestions += Suggestion(
                    sourceColumn = h,
                    targetField = null,
                    confidence = bestScore,
                    method = if (bestMethod == "none") "unmapped" else bestMethod,
                    sampleValues = samples,
                    unmapped = true
                )
            }
        }

        val columnMap = linkedMapOf<String, String>()
        suggestions.forEach { s ->
            val t = s.targetField ?: return@forEach
            if (s.method == "institution_profile" || s.confidence >= 0.75) {
                if (t !in columnMap.values) columnMap[s.sourceColumn] = t
            }
        }

        val unmapped = suggestions.filter { it.sourceColumn !in columnMap }.map { it.sourceColumn }
        if (unmapped.isNotEmpty()) {
            warnings += "${unmapped.size} column(s) were not mapped automatically."
        }
        val missingRequired = requiredFields(entityType).filter { req -> columnMap.values.none { it == req } }
        if (entityType.equals("ALLOCATION", true)) {
            val hasLecturer = columnMap.values.any { it == "staffNumber" || it == "lecturerName" || it == "email" }
            if (!hasLecturer) warnings += "Allocation import needs a lecturer identifier or name column."
            if (columnMap.values.none { it == "unitCode" || it == "unitName" }) {
                warnings += "Allocation import needs a unit/course code or name column."
            }
        } else if (missingRequired.isNotEmpty()) {
            warnings += "Missing required mappings: ${missingRequired.joinToString(", ")}"
        }

        return MappingResult(
            entityType = entityType.uppercase(),
            suggestions = suggestions,
            columnMap = columnMap,
            unmappedColumns = unmapped,
            warnings = warnings
        )
    }

    fun serializeColumnMap(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun parseColumnMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    fun normalize(s: String): String =
        s.lowercase()
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokenOverlap(a: String, b: String): Double {
        val ta = a.split(" ").filter { it.length > 1 }.toSet()
        val tb = b.split(" ").filter { it.length > 1 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / max(ta.size, tb.size)
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / max(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    private enum class Shape { STAFF_ID, PERSON_NAME, UNIT_CODE, HOURS, EMAIL, YEAR, SEMESTER }

    private fun inferShape(samples: List<String>): Shape? {
        if (samples.isEmpty()) return null
        val email = samples.count { it.contains("@") }.toDouble() / samples.size
        if (email >= 0.5) return Shape.EMAIL
        val hours = samples.count { it.replace(",", "").toDoubleOrNull() != null && it.length <= 5 }.toDouble() / samples.size
        if (hours >= 0.7) return Shape.HOURS
        val unitCode = samples.count {
            Regex("""^[A-Za-z]{2,5}[\s\-/]?\d{2,4}[A-Za-z]?$""").matches(it.trim())
        }.toDouble() / samples.size
        if (unitCode >= 0.5) return Shape.UNIT_CODE
        val year = samples.count {
            Regex("""^\d{4}/\d{2,4}$|^\d{4}$""").matches(it.trim()) || it.contains("20")
        }.toDouble() / samples.size
        if (year >= 0.6 && samples.any { it.contains("/") || it.length == 4 }) return Shape.YEAR
        val semester = samples.count {
            val l = it.lowercase()
            l.contains("sem") || l.contains("term") || l.matches(Regex("""^[123]$"""))
        }.toDouble() / samples.size
        if (semester >= 0.5) return Shape.SEMESTER
        val staffId = samples.count {
            Regex("""^[A-Za-z]{0,4}\d{2,}[A-Za-z0-9/\-]*$""").matches(it.trim()) && !it.contains(" ")
        }.toDouble() / samples.size
        if (staffId >= 0.55) return Shape.STAFF_ID
        val person = samples.count {
            val parts = it.trim().split(Regex("\\s+"))
            parts.size in 2..4 && parts.all { p -> p.firstOrNull()?.isUpperCase() == true || p.endsWith(".") }
        }.toDouble() / samples.size
        if (person >= 0.55) return Shape.PERSON_NAME
        return null
    }

    private fun shapeTarget(entityType: String, shape: Shape): String? {
        val et = entityType.uppercase()
        return when (shape) {
            Shape.EMAIL -> "email"
            Shape.HOURS -> when (et) {
                "LECTURER" -> "maximumWorkload"
                else -> "contactHours"
            }
            Shape.UNIT_CODE -> if (et == "ALLOCATION") "unitCode" else "code"
            Shape.STAFF_ID -> "staffNumber"
            Shape.PERSON_NAME -> if (et == "ALLOCATION") "lecturerName" else "name"
            Shape.YEAR -> "academicYear"
            Shape.SEMESTER -> "semester"
        }
    }
}
