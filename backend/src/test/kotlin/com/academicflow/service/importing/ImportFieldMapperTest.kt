package com.academicflow.service.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportFieldMapperTest {

    @Test
    fun `maps Institution A staff and course headers`() {
        val headers = listOf("Staff No.", "Lecturer Name", "Course Code", "Course Title", "Hrs", "Semester")
        val rows = listOf(
            listOf("STF/101", "Dr. Amina Otieno", "BIT 401", "Databases", "3", "Semester 1"),
            listOf("STF/102", "Prof. John Kamau", "CSC 302", "Algorithms", "4", "Semester 1")
        )
        val result = ImportFieldMapper.suggest(headers, rows, "ALLOCATION")
        assertEquals("staffNumber", result.columnMap["Staff No."])
        assertEquals("lecturerName", result.columnMap["Lecturer Name"])
        assertEquals("unitCode", result.columnMap["Course Code"])
        assertEquals("unitName", result.columnMap["Course Title"])
        assertEquals("contactHours", result.columnMap["Hrs"])
        assertEquals("semester", result.columnMap["Semester"])
        assertTrue(result.suggestions.all { it.confidence >= 0.75 || it.targetField == null })
    }

    @Test
    fun `maps Institution B employee and unit headers`() {
        val headers = listOf("Employee ID", "Instructor", "Unit", "Unit Description", "Contact Hrs", "Term")
        val rows = listOf(
            listOf("E9001", "Mary Wanjiku", "DBS 410", "Database Systems", "3", "Term 2")
        )
        val result = ImportFieldMapper.suggest(headers, rows, "ALLOCATION")
        assertEquals("staffNumber", result.columnMap["Employee ID"])
        assertEquals("lecturerName", result.columnMap["Instructor"])
        assertEquals("unitCode", result.columnMap["Unit"])
        assertEquals("unitName", result.columnMap["Unit Description"])
        assertEquals("contactHours", result.columnMap["Contact Hrs"])
        assertEquals("semester", result.columnMap["Term"])
    }

    @Test
    fun `maps Institution C module workload schema`() {
        val headers = listOf("Staff Number", "Teacher", "Module Ref", "Module Name", "Workload")
        val rows = listOf(listOf("SN-44", "Grace Njeri", "MOD-210", "Networks", "6"))
        val result = ImportFieldMapper.suggest(headers, rows, "ALLOCATION")
        assertEquals("staffNumber", result.columnMap["Staff Number"])
        assertEquals("lecturerName", result.columnMap["Teacher"])
        assertEquals("unitCode", result.columnMap["Module Ref"])
        assertEquals("unitName", result.columnMap["Module Name"])
        assertEquals("contactHours", result.columnMap["Workload"])
    }

    @Test
    fun `handles messy schema with extras and fuzzy headers`() {
        val headers = listOf("Lecturer Nam", "COURSE_CODE", "Extra Notes", "Payroll No.", "hrs ")
        val rows = listOf(
            listOf("John Kamau", "BIT 401", "prefer morning", "PAY-22", "3"),
            listOf("Mary Wanjiku", "CSC 302", "", "PAY-23", "4")
        )
        val result = ImportFieldMapper.suggest(headers, rows, "ALLOCATION")
        assertEquals("lecturerName", result.columnMap["Lecturer Nam"])
        assertEquals("unitCode", result.columnMap["COURSE_CODE"])
        assertEquals("staffNumber", result.columnMap["Payroll No."])
        assertEquals("contactHours", result.columnMap["hrs "])
        assertTrue(result.unmappedColumns.contains("Extra Notes") || "Extra Notes" !in result.columnMap)
        assertTrue(result.warnings.any { it.contains("not mapped", ignoreCase = true) })
    }

    @Test
    fun `detects allocation when person and unit columns coexist`() {
        val headers = listOf("Instructor", "Course Code", "Course Title", "Hrs")
        val rows = listOf(listOf("Ada Lovelace", "CSC 101", "Intro", "3"))
        assertEquals("ALLOCATION", ImportFieldMapper.detectEntityType(headers, rows))
    }

    @Test
    fun `applies institution profile with high confidence`() {
        val headers = listOf("Emp #", "Tutor", "Mod", "Desc", "Load")
        val rows = listOf(listOf("1", "X", "A1", "Algo", "2"))
        val profile = mapOf(
            "Emp #" to "staffNumber",
            "Tutor" to "lecturerName",
            "Mod" to "unitCode",
            "Desc" to "unitName",
            "Load" to "contactHours"
        )
        val result = ImportFieldMapper.suggest(headers, rows, "ALLOCATION", profileMap = profile)
        assertEquals("staffNumber", result.columnMap["Emp #"])
        assertTrue(result.suggestions.first { it.sourceColumn == "Emp #" }.method == "institution_profile")
        assertTrue(result.suggestions.first { it.sourceColumn == "Emp #" }.confidence >= 0.99)
    }

    @Test
    fun `maps schema A Course Code Course Title Lecturer Department`() {
        val headers = listOf("Course Code", "Course Title", "Lecturer", "Department")
        val rows = listOf(listOf("MAT 210", "Calculus II", "Dr. Jane", "Mathematics"))
        val map = ImportFieldMapper.suggest(headers, rows, "ALLOCATION").columnMap
        assertEquals("unitCode", map["Course Code"])
        assertEquals("unitName", map["Course Title"])
        assertEquals("lecturerName", map["Lecturer"])
        assertEquals("organizationNode", map["Department"])
    }

    @Test
    fun `maps schema B Unit Code Unit Name Staff Name School`() {
        val headers = listOf("Unit Code", "Unit Name", "Staff Name", "School")
        val rows = listOf(listOf("CSC 101", "Intro CS", "Ada Lovelace", "Computing"))
        val map = ImportFieldMapper.suggest(headers, rows, "ALLOCATION").columnMap
        assertEquals("unitCode", map["Unit Code"])
        assertEquals("unitName", map["Unit Name"])
        assertEquals("lecturerName", map["Staff Name"])
        assertEquals("organizationNode", map["School"])
    }

    @Test
    fun `maps schema C Module Code Module Instructor Dept`() {
        val headers = listOf("Module Code", "Module", "Instructor", "Dept")
        val rows = listOf(listOf("MOD-9", "Networks", "Prof. X", "IT"))
        val map = ImportFieldMapper.suggest(headers, rows, "ALLOCATION").columnMap
        assertEquals("unitCode", map["Module Code"])
        assertEquals("lecturerName", map["Instructor"])
        assertEquals("organizationNode", map["Dept"])
    }

    @Test
    fun `maps schema D with programme and hours`() {
        val headers = listOf("Course_Code", "Course_Name", "Lecturer_Name", "Programme", "Hours")
        val rows = listOf(listOf("BIT401", "Databases", "Mary", "BSc IT", "3"))
        val map = ImportFieldMapper.suggest(headers, rows, "ALLOCATION").columnMap
        assertEquals("unitCode", map["Course_Code"])
        assertEquals("unitName", map["Course_Name"])
        assertEquals("lecturerName", map["Lecturer_Name"])
        assertEquals("programme", map["Programme"])
        assertEquals("contactHours", map["Hours"])
    }
}
