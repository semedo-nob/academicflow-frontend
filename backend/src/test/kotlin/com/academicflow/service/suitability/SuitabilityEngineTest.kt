package com.academicflow.service.suitability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuitabilityEngineTest {

    private fun lecturer(
        name: String,
        dept: String,
        expertise: List<Pair<String, String>>,
        ctx: List<SuitabilityEngine.ContextExp>,
        current: Double = 6.0,
        max: Double = 12.0
    ) = SuitabilityEngine.LecturerInput(
        id = name,
        name = name,
        department = dept,
        currentWorkload = current,
        maximumWorkload = max,
        contactHoursNeeded = 4.0,
        expertise = expertise.map { SuitabilityEngine.Expertise(it.first, it.second) },
        contextExperience = ctx
    )

    private val computingOffering = SuitabilityEngine.OfferingInput(
        unitCode = "MAT 210",
        unitName = "Calculus II",
        programme = "BSc Information Technology",
        contextLabel = "Computing",
        levelLabel = "Year 2",
        owningDepartment = "Mathematics",
        requestingDepartment = "Computer Science",
        requirements = listOf(
            SuitabilityEngine.Requirement("TOPIC", "Differentiation"),
            SuitabilityEngine.Requirement("TOPIC", "Integration"),
            SuitabilityEngine.Requirement("TOPIC", "Numerical methods"),
            SuitabilityEngine.Requirement("TOPIC", "Computing applications"),
            SuitabilityEngine.Requirement("EXPERTISE", "Calculus"),
            SuitabilityEngine.Requirement("EXPERTISE", "Numerical Methods")
        )
    )

    private val businessOffering = computingOffering.copy(
        programme = "Bachelor of Commerce",
        contextLabel = "Business",
        levelLabel = "Year 1",
        requestingDepartment = null,
        requirements = listOf(
            SuitabilityEngine.Requirement("TOPIC", "Limits"),
            SuitabilityEngine.Requirement("TOPIC", "Optimization"),
            SuitabilityEngine.Requirement("TOPIC", "Financial applications"),
            SuitabilityEngine.Requirement("EXPERTISE", "Business Mathematics"),
            SuitabilityEngine.Requirement("EXPERTISE", "Calculus")
        )
    )

    @Test
    fun `exact course-context match scores high`() {
        val jane = lecturer(
            "Dr. Jane",
            "Mathematics",
            listOf("Calculus" to "Strong", "Numerical Methods" to "Moderate", "Linear Algebra" to "Excellent"),
            listOf(
                SuitabilityEngine.ContextExp("Computing", "BSc Information Technology", "Year 2", "Integration", "MAT 210", 3),
                SuitabilityEngine.ContextExp("Computing", "BSc Information Technology", "Year 2", "Numerical methods", "MAT 210", 2)
            )
        )
        val r = SuitabilityEngine.score(computingOffering, jane)
        assertTrue(r.score >= 80, "expected strong score but was ${r.score}")
        assertEquals("STRONG MATCH", r.classification)
        assertTrue(r.crossDepartment)
        assertTrue(r.positives.any { it.contains("Computing", ignoreCase = true) })
    }

    @Test
    fun `same subject different context produces partial match with warning`() {
        val businessLecturer = lecturer(
            "Prof. David",
            "Mathematics",
            listOf("Calculus" to "Strong", "Business Mathematics" to "Excellent"),
            listOf(
                SuitabilityEngine.ContextExp("Business", "Bachelor of Commerce", "Year 1", "Optimization", "MAT 210", 4),
                SuitabilityEngine.ContextExp("Business", "Bachelor of Commerce", "Year 1", "Financial applications", "MAT 210", 3)
            )
        )
        val r = SuitabilityEngine.score(computingOffering, businessLecturer)
        assertTrue(r.score in 50..84, "expected review/weak band but was ${r.score}")
        assertTrue(r.warnings.any { it.contains("Business", ignoreCase = true) || it.contains("Computing", ignoreCase = true) })
    }

    @Test
    fun `different academic level reduces score`() {
        val lec = lecturer(
            "Sam",
            "Mathematics",
            listOf("Calculus" to "Strong"),
            listOf(SuitabilityEngine.ContextExp("Computing", "BSc IT", "Year 1", "Integration", "MAT 210", 1))
        )
        val r = SuitabilityEngine.score(computingOffering, lec)
        assertTrue(r.warnings.any { it.contains("Year 2", ignoreCase = true) } || r.score < 90)
    }

    @Test
    fun `topic overlap without history warns`() {
        val lec = lecturer(
            "Grace",
            "Mathematics",
            listOf("Calculus" to "Excellent", "Numerical Methods" to "Strong", "Differentiation" to "Strong"),
            emptyList()
        )
        val r = SuitabilityEngine.score(computingOffering, lec)
        assertTrue(r.warnings.any { it.contains("No recent teaching", ignoreCase = true) })
        assertTrue(r.positives.any { it.contains("Topic", ignoreCase = true) || it.contains("expertise", ignoreCase = true) })
    }

    @Test
    fun `workload overload adds warning`() {
        val lec = lecturer(
            "Overloaded",
            "Mathematics",
            listOf("Calculus" to "Strong"),
            listOf(SuitabilityEngine.ContextExp("Computing", "BSc IT", "Year 2", "Integration", "MAT 210", 2)),
            current = 12.0,
            max = 12.0
        )
        val r = SuitabilityEngine.score(computingOffering, lec)
        assertTrue(r.warnings.any { it.contains("Workload", ignoreCase = true) })
    }

    @Test
    fun `business offering prefers business context lecturer`() {
        val business = lecturer(
            "David",
            "Mathematics",
            listOf("Calculus" to "Strong", "Business Mathematics" to "Excellent"),
            listOf(SuitabilityEngine.ContextExp("Business", "Bachelor of Commerce", "Year 1", "Optimization", "MAT 210", 4))
        )
        val computing = lecturer(
            "Jane",
            "Mathematics",
            listOf("Calculus" to "Strong"),
            listOf(SuitabilityEngine.ContextExp("Computing", "BSc IT", "Year 2", "Integration", "MAT 210", 3))
        )
        val b = SuitabilityEngine.score(businessOffering, business)
        val c = SuitabilityEngine.score(businessOffering, computing)
        assertTrue(b.score > c.score, "business lecturer should outrank computing lecturer for business offering")
    }
}
