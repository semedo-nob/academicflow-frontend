package com.academicflow.service.suitability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OutlineTextExtractorTest {

    @Test
    fun `structured outline extracts topics outcomes and prerequisites`() {
        val text = """
            Course: MAT 210 Calculus II
            Prerequisites:
            - MAT 110 Calculus I
            Learning Outcomes:
            - Apply differentiation to computing problems
            - Implement numerical integration
            Topics:
            - Differentiation
            - Integration
            - Numerical methods
            - Computing applications
        """.trimIndent()
        val r = OutlineTextExtractor.extract("outline.txt", "text/plain", text.toByteArray())
        assertTrue(r.topics.any { it.contains("Differentiation", ignoreCase = true) })
        assertTrue(r.outcomes.isNotEmpty())
        assertTrue(r.prerequisites.isNotEmpty())
        assertTrue(r.confidence >= BigDecimal("0.60"))
    }

    @Test
    fun `blank or unscannable content needs review with low confidence`() {
        val r = OutlineTextExtractor.extract("scan.pdf", "application/pdf", ByteArray(8))
        assertTrue(r.needsReview)
        assertTrue(r.confidence <= BigDecimal("0.20"))
        assertTrue(r.warnings.any { it.contains("review", ignoreCase = true) || it.contains("OCR", ignoreCase = true) })
    }
}
