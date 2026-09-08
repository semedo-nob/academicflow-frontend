package com.academicflow.service.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AllocationOcrTableParserTest {

    @Test
    fun `parses phoenix-style OCR allocation sheet`() {
        val ocr = """
            ALLOCATION FOR 2025/2026 ACADEMIC YEAR,
            DEPARTMENT OF MATHEMATICS AND STATISTICS
            S/NO | NAME SEM UNITS ALLOCATED
            Dr Moses Wamalwa 1 SHCE 1103 Applied Technical Mathematics (Dip)
            Academic Regitrar 2 SFF11106, Mathematics |
            Patrick Mathagu 1 SPPI 1201 Mathematics 1B
            2 SHCM 1142 Applied Technical Mathematics
            3 AE F 1113 Mathematics for Information for Science
            Dr. Paul Wanjau I 1. SMMQ SMS! 1112, Calculus |
            Lecturer 2. EEGQ 1101, EEGR 1107 Mathematics IA Part II
            3. SMMQ 4134, Mathematical Modelling Year 4
            Dr. Duncan Owego I
            Lecturer
            1.5SMMQ 3117, Real Analysis
            2. SMMQ 4114, Topology |
        """.trimIndent()

        val r = AllocationOcrTableParser.parse(ocr)
        assertNotNull(r)
        assertEquals(listOf("Staff Name", "Course Code", "Course Title"), r!!.headers)
        assertTrue(r.rows.size >= 5, "expected multiple rows but was ${r.rows}")
        assertTrue(r.rows.any { it[0].contains("Wamalwa", ignoreCase = true) && it[1].contains("1103") })
        assertTrue(r.rows.any { it[1].contains("1201") || it[1].contains("1142") })
        assertTrue(r.rows.any { it[0].contains("Owego", ignoreCase = true) })
    }
}
