package com.academicflow.service.suitability

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deterministic, explainable suitability scoring for a course offering vs a lecturer.
 * Weights are intentional and easy to retune — not an opaque AI model.
 */
object SuitabilityEngine {

    data class Requirement(val type: String, val label: String, val weight: Double = 1.0)
    data class Expertise(val subject: String, val level: String)
    data class ContextExp(
        val contextLabel: String?,
        val programme: String?,
        val levelLabel: String?,
        val topicLabel: String?,
        val unitCode: String?,
        val timesTaught: Int
    )
    data class LecturerInput(
        val id: String,
        val name: String,
        val department: String?,
        val currentWorkload: Double,
        val maximumWorkload: Double,
        val contactHoursNeeded: Double,
        val expertise: List<Expertise>,
        val contextExperience: List<ContextExp>
    )
    data class OfferingInput(
        val unitCode: String,
        val unitName: String,
        val programme: String?,
        val contextLabel: String?,
        val levelLabel: String?,
        val owningDepartment: String?,
        val requestingDepartment: String?,
        val requirements: List<Requirement>
    )

    data class DimensionScore(val key: String, val label: String, val earned: Double, val max: Double, val notes: List<String>)
    data class Result(
        val score: Int,
        val classification: String,
        val positives: List<String>,
        val warnings: List<String>,
        val missingEvidence: List<String>,
        val breakdown: List<DimensionScore>,
        val crossDepartment: Boolean
    )

    private val levelPts = mapOf("Excellent" to 100.0, "Strong" to 85.0, "Moderate" to 65.0, "Basic" to 40.0)

    /** Soft topic families — expertise in a parent subject supports related outline topics. */
    private val topicFamilies = mapOf(
        "calculus" to listOf(
            "limits", "differentiation", "integration", "optimization",
            "differential equations", "derivatives", "integrals"
        ),
        "numerical methods" to listOf("numerical methods", "numerical analysis", "approximation"),
        "linear algebra" to listOf("matrices", "vectors", "eigenvalues", "linear systems")
    )

    fun score(offering: OfferingInput, lecturer: LecturerInput): Result {
        val topics = offering.requirements.filter { it.type.equals("TOPIC", true) }
        val expertiseReqs = offering.requirements.filter { it.type.equals("EXPERTISE", true) }
        val positives = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val missing = mutableListOf<String>()

        val ctx = offering.contextLabel
        val prog = offering.programme
        val ctxHits = lecturer.contextExperience.filter {
            (ctx != null && it.contextLabel.equals(ctx, true)) ||
                (prog != null && it.programme.equals(prog, true))
        }
        val otherCtx = lecturer.contextExperience.filter {
            ctxHits.none { h -> h === it } && !it.contextLabel.isNullOrBlank()
        }
        val unitHist = lecturer.contextExperience.filter {
            matches(it.unitCode ?: "", offering.unitCode) || matches(it.topicLabel ?: "", offering.unitName)
        }
        val exactOfferingFit = unitHist.isNotEmpty() && ctxHits.isNotEmpty()

        // Course/unit expertise — max 25
        val subjectNeed = expertiseReqs.ifEmpty {
            listOf(Requirement("EXPERTISE", offering.unitName, 1.0))
        }
        val bestLevels = subjectNeed.mapNotNull { req ->
            lecturer.expertise.firstOrNull { exp ->
                matches(exp.subject, req.label) || matches(exp.subject, offering.unitName)
            }?.let { it to req }
        }.distinctBy { it.first.subject.lowercase() }
        val coverage = if (subjectNeed.isEmpty()) 1.0 else bestLevels.size.toDouble() / subjectNeed.size
        val avgLevel = bestLevels.map { (exp, _) -> levelPts[exp.level] ?: 40.0 }.average().takeIf { !it.isNaN() } ?: 0.0
        var coursePts = if (subjectNeed.isEmpty()) 12.0 else coverage * (avgLevel / 100.0) * 25.0
        // Same unit taught in the target context is strong evidence of course fitness.
        if (exactOfferingFit) coursePts = maxOf(coursePts, 22.0)
        else if (unitHist.isNotEmpty() && bestLevels.isNotEmpty()) coursePts = maxOf(coursePts, 15.0)
        if (bestLevels.isNotEmpty()) {
            positives += "Subject expertise: ${bestLevels.joinToString { "${it.first.subject} (${it.first.level})" }}"
        } else if (!exactOfferingFit) {
            missing += "No recorded expertise matching ${subjectNeed.joinToString { it.label }}"
        }
        if (exactOfferingFit) positives += "Taught ${offering.unitCode} in ${ctx ?: prog} context"
        val courseDim = DimensionScore("course_expertise", "Course expertise", coursePts, 25.0, emptyList())

        // Topic alignment — max 25
        val topicHits = topics.mapNotNull { t ->
            val viaExp = lecturer.expertise.any { matches(it.subject, t.label) }
            val viaHist = lecturer.contextExperience.any { matches(it.topicLabel ?: "", t.label) }
            val viaFamily = lecturer.expertise.any { relatedTopic(it.subject, t.label) }
            val viaContextApps = ctxHits.isNotEmpty() &&
                (t.label.contains("application", true) || (ctx != null && t.label.contains(ctx, true)))
            when {
                viaExp || viaHist || viaFamily || viaContextApps -> t.label
                else -> null
            }
        }
        val topicPts = if (topics.isEmpty()) 12.0 else (topicHits.size.toDouble() / topics.size) * 25.0
        if (topicHits.isNotEmpty()) positives += "Topic coverage: ${topicHits.joinToString()}"
        topics.filter { t -> topicHits.none { it.equals(t.label, true) } }.forEach {
            missing += "Missing evidence for topic: ${it.label}"
        }
        val topicDim = DimensionScore("topic_alignment", "Topic alignment", topicPts, 25.0, emptyList())

        // Programme / context — max 20
        val contextPts = when {
            ctxHits.isNotEmpty() -> 20.0
            otherCtx.isNotEmpty() && ctx != null -> 12.0
            else -> 6.0
        }
        if (ctxHits.isNotEmpty()) {
            positives += "Programme/context experience: ${ctx ?: prog}"
        } else if (otherCtx.isNotEmpty() && ctx != null) {
            warnings += "Mostly ${otherCtx.mapNotNull { it.contextLabel }.distinct().joinToString()} experience — limited ${ctx} evidence"
        } else if (ctx != null) {
            missing += "No teaching history for context: $ctx"
        }
        val contextDim = DimensionScore("programme_experience", "Programme experience", contextPts, 20.0, emptyList())

        // Level — max 15
        val level = offering.levelLabel
        val levelHit = level != null && lecturer.contextExperience.any { it.levelLabel.equals(level, true) }
        val levelPtsScore = when {
            levelHit -> 15.0
            level == null -> 10.0
            else -> 6.0
        }
        if (levelHit) positives += "Target academic-level experience: $level"
        else if (level != null) warnings += "Limited evidence for academic level: $level"
        val levelDim = DimensionScore("level_experience", "Level experience", levelPtsScore, 15.0, emptyList())

        // Teaching history for this unit — max 10
        val histPts = when {
            exactOfferingFit && unitHist.sumOf { it.timesTaught } >= 2 -> 10.0
            unitHist.sumOf { it.timesTaught } >= 2 -> 8.0
            unitHist.isNotEmpty() -> 7.0
            topicHits.isNotEmpty() -> 4.0
            else -> 2.0
        }
        if (unitHist.isNotEmpty()) positives += "Prior teaching linked to ${offering.unitCode}"
        else warnings += "No recent teaching record for this exact course offering"
        val histDim = DimensionScore("teaching_history", "Teaching history", histPts, 10.0, emptyList())

        // Workload advisory (does not hard-fail here)
        val remaining = lecturer.maximumWorkload - lecturer.currentWorkload
        if (remaining < lecturer.contactHoursNeeded) {
            warnings += "Workload: ${lecturer.currentWorkload}/${lecturer.maximumWorkload} hrs — may exceed capacity for this allocation"
        }

        val cross = !offering.requestingDepartment.isNullOrBlank() &&
            !lecturer.department.isNullOrBlank() &&
            !offering.requestingDepartment.equals(lecturer.department, true)
        if (cross) positives += "Cross-department candidate (${lecturer.department} for ${offering.requestingDepartment})"

        val totalMax = 25.0 + 25.0 + 20.0 + 15.0 + 10.0
        val earned = coursePts + topicPts + contextPts + levelPtsScore + histPts
        val pct = ((earned / totalMax) * 100.0).let { BigDecimal.valueOf(it).setScale(0, RoundingMode.HALF_UP).toInt() }
        val classification = when {
            pct >= 85 -> "STRONG MATCH"
            pct >= 70 -> "REVIEW RECOMMENDED"
            pct >= 50 -> "WEAK MATCH"
            else -> "INSUFFICIENT EVIDENCE"
        }

        return Result(
            score = pct,
            classification = classification,
            positives = positives.distinct(),
            warnings = warnings.distinct(),
            missingEvidence = missing.distinct(),
            breakdown = listOf(courseDim, topicDim, contextDim, levelDim, histDim),
            crossDepartment = cross
        )
    }

    private fun relatedTopic(subject: String, topic: String): Boolean {
        val s = subject.lowercase().trim()
        val t = topic.lowercase().trim()
        topicFamilies.forEach { (family, members) ->
            if (matches(s, family) || members.any { matches(s, it) }) {
                if (members.any { matches(t, it) }) return true
            }
        }
        return false
    }

    private fun matches(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val x = a.lowercase().trim()
        val y = b.lowercase().trim()
        return x == y || x.contains(y) || y.contains(x)
    }
}
