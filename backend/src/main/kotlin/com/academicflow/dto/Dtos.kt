package com.academicflow.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrganizationNodeDto(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val type: String,
    val parentId: UUID?,
    val lecturerCount: Int = 0,
    val unitCount: Int = 0
)

data class ExpertiseDto(val subject: String, val level: String)

data class LecturerDto(
    val id: UUID,
    val staffNumber: String,
    val name: String,
    val email: String,
    val departmentId: UUID,
    val department: String,
    val qualifications: String?,
    val currentWorkload: BigDecimal,
    val maximumWorkload: BigDecimal,
    val status: String,
    val availability: String?,
    val expertise: List<String>,
    val expertiseDetail: List<ExpertiseDto>
)

data class CreateLecturerRequest(
    val staffNumber: String,
    val name: String,
    val email: String,
    val departmentId: UUID,
    val qualifications: String? = null,
    val maximumWorkload: BigDecimal = BigDecimal("12"),
    val availability: String? = null,
    val expertise: List<ExpertiseDto> = emptyList()
)

data class AcademicUnitDto(
    val id: UUID,
    val code: String,
    val name: String,
    val sourceDepartmentId: UUID,
    val sourceDepartment: String,
    val contactHours: BigDecimal,
    val studentCount: Int,
    val requiredExpertise: List<String>,
    val status: String,
    val lecturerName: String? = null
)

data class CreateUnitRequest(
    val code: String,
    val name: String,
    val sourceDepartmentId: UUID,
    val contactHours: BigDecimal = BigDecimal("3"),
    val studentCount: Int = 0,
    val requiredExpertise: List<String> = emptyList()
)

data class TeachingRequestDto(
    val id: UUID,
    val requestingDepartment: String,
    val requestingDepartmentId: UUID,
    val sourceDepartment: String?,
    val preferredDepartmentId: UUID?,
    val academicUnitId: UUID,
    val academicUnit: String,
    val studentCount: Int,
    val contactHours: BigDecimal,
    val requiredExpertise: String?,
    val status: String,
    val createdAt: Instant
)

data class CreateTeachingRequest(
    val requestingDepartmentId: UUID,
    val preferredDepartmentId: UUID?,
    val academicUnitId: UUID,
    val studentCount: Int,
    val contactHours: BigDecimal,
    val requiredExpertise: String?
)

data class CandidateDto(
    val id: UUID?,
    val rank: Int,
    val lecturerId: UUID,
    val name: String,
    val dept: String,
    val score: BigDecimal,
    val metrics: Map<String, BigDecimal>,
    val load: String,
    val availability: String?,
    val reasons: List<String>,
    val warn: String?,
    val hardConstraints: Map<String, Boolean>
)

data class CreateAllocationRequest(
    val academicUnitId: UUID,
    val lecturerId: UUID,
    val teachingRequestId: UUID? = null,
    val matchScore: BigDecimal? = null,
    val overrideReason: String? = null,
    val recommendedLecturerId: UUID? = null
)

data class AllocationDto(
    val id: UUID,
    val academicUnitId: UUID,
    val unitCode: String,
    val unitName: String,
    val lecturerId: UUID?,
    val lecturerName: String?,
    val matchScore: BigDecimal?,
    val status: String,
    val overrideReason: String?,
    val teachingRequestId: UUID?
)

data class ApprovalDto(
    val id: UUID,
    val allocationId: UUID,
    val unit: String,
    val lecturer: String?,
    val status: String,
    val conflicts: Int,
    val submittedAt: Instant
)

data class ConflictDto(
    val id: UUID,
    val category: String,
    val severity: String,
    val description: String,
    val relatedEntity: String?,
    val allocationId: UUID?
)

data class DashboardDto(
    val totalLecturers: Long,
    val academicUnits: Long,
    val allocated: Long,
    val pending: Long,
    val conflicts: Long,
    val crossDeptRequests: Long,
    val completionPct: Int,
    val recentRequests: List<TeachingRequestDto>,
    val recentActivity: List<AuditLogDto>
)

data class AuditLogDto(
    val id: UUID,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val details: String?,
    val createdAt: Instant
)

data class CreateOrgNodeRequest(
    val name: String,
    val type: String,
    val parentId: UUID? = null
)

data class ImportSessionDto(
    val id: UUID,
    val fileName: String,
    val entityType: String,
    val status: String,
    val columnMap: String?,
    val createdAt: Instant
)

data class UserDto(
    val id: UUID,
    val email: String,
    val name: String,
    val role: String,
    val active: Boolean,
    val organizationNodeId: UUID?
)

data class CreateUserRequest(
    val email: String,
    val name: String,
    val role: String,
    val organizationNodeId: UUID? = null,
    val active: Boolean = true
)

data class UpdateUserRequest(
    val role: String? = null,
    val active: Boolean? = null,
    val organizationNodeId: UUID? = null,
    val name: String? = null
)

data class InstitutionDto(
    val id: UUID,
    val name: String,
    val code: String,
    val status: String = "APPROVED",
    val adminEmail: String? = null,
    val adminName: String? = null,
    val decisionNote: String? = null,
    val createdAt: String? = null
)

data class CreateInstitutionRequest(
    val name: String,
    val code: String,
    val adminEmail: String? = null,
    val adminName: String? = null
)

data class InstitutionDecisionRequest(
    val approve: Boolean,
    val note: String? = null
)

data class InstitutionStatusRequest(
    val status: String,
    val note: String? = null
)

data class PlatformOverviewDto(
    val pendingRegistrations: Int,
    val approvedInstitutions: Int,
    val suspendedInstitutions: Int,
    val rejectedInstitutions: Int,
    val totalInstitutions: Int
)

data class InstitutionSignupResponse(
    val id: UUID,
    val name: String,
    val code: String,
    val status: String,
    val message: String
)
data class OrganizationTypeDto(val id: UUID, val name: String, val levelNo: Int, val description: String?)
data class RoleDto(val id: UUID, val code: String, val name: String, val permissions: List<String>, val description: String?)
data class AcademicYearDto(val id: UUID, val label: String, val startDate: String?, val endDate: String?)
data class SemesterDto(val id: UUID, val academicYearId: UUID, val academicYearLabel: String, val name: String, val sequenceNo: Int)
data class CreateAcademicYearRequest(val label: String, val startDate: String? = null, val endDate: String? = null)
data class CreateSemesterRequest(val academicYearId: UUID, val name: String, val sequenceNo: Int = 1)
data class RuleDto(val id: UUID, val key: String, val value: String, val description: String?)
data class UpdateRuleRequest(val value: String)
data class SettingDto(val id: UUID, val key: String, val value: String, val description: String?)
data class UpdateSettingRequest(val value: String)
data class MappingProfileDto(val id: UUID, val name: String, val entityType: String, val columnMap: Map<String, String>)
data class CreateMappingProfileRequest(val name: String, val entityType: String, val columnMap: Map<String, String>)
data class PeriodContextDto(
    val academicYear: AcademicYearDto?,
    val semester: SemesterDto?,
    val years: List<AcademicYearDto>,
    val semesters: List<SemesterDto>
)

data class CreateImportSessionRequest(
    val fileName: String,
    val entityType: String,
    val columnMap: Map<String, String> = emptyMap(),
    val rows: List<Map<String, String>> = emptyList()
)

data class ImportUploadResultDto(
    val sessionId: java.util.UUID,
    val fileName: String,
    val entityType: String,
    val status: String,
    val detectedColumns: List<String>,
    val suggestedMap: Map<String, String>,
    val rowCount: Int,
    val preview: List<Map<String, String>>,
    val warnings: List<String> = emptyList()
)

data class SearchHitDto(
    val type: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val path: String
)

data class SearchResultDto(val hits: List<SearchHitDto>)

data class LoginRequest(val email: String, val password: String? = null)

data class LoginResponse(val email: String, val name: String, val role: String, val tenantId: UUID)

data class TimetableEntryDto(
    val id: UUID,
    val allocationId: UUID?,
    val lecturerId: UUID?,
    val lecturerName: String?,
    val academicUnitId: UUID?,
    val unitCode: String?,
    val unitName: String?,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val room: String?,
    val conflict: Boolean = false
)

data class CreateTimetableEntryRequest(
    val lecturerId: UUID,
    val academicUnitId: UUID? = null,
    val allocationId: UUID? = null,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val room: String? = null
)

data class WorkloadRowDto(
    val lecturerId: UUID,
    val name: String,
    val department: String,
    val currentWorkload: java.math.BigDecimal,
    val maximumWorkload: java.math.BigDecimal,
    val utilization: Int,
    val status: String
)

data class WorkloadSummaryDto(
    val totalHours: java.math.BigDecimal,
    val averageLoad: Double,
    val underloaded: Int,
    val optimal: Int,
    val nearLimit: Int,
    val overloaded: Int,
    val rows: List<WorkloadRowDto>
)

data class ReportSummaryDto(
    val title: String,
    val generatedAt: java.time.Instant,
    val metrics: Map<String, String>
)

