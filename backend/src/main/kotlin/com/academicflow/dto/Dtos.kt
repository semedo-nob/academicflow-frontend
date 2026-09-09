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
    val departmentId: UUID? = null,
    val department: String? = null,
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
    val sourceDepartmentId: UUID? = null,
    val sourceDepartment: String? = null,
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
    val createdAt: Instant,
    val courseOfferingId: UUID? = null,
    val createdBy: UUID? = null,
    /** Relative to the signed-in chair: outgoing = my dept asked; incoming = my dept is preferred source. */
    val direction: String? = null,
    val briefingNote: String? = null,
    val attachmentCount: Int = 0,
    val messageCount: Int = 0
)

data class CreateTeachingRequest(
    val requestingDepartmentId: UUID? = null,
    val requestingDepartment: String? = null,
    val preferredDepartmentId: UUID? = null,
    val preferredDepartment: String? = null,
    val academicUnitId: UUID? = null,
    val academicUnit: String? = null,
    val studentCount: Int,
    val contactHours: BigDecimal,
    val requiredExpertise: String?,
    val programme: String? = null,
    val contextLabel: String? = null,
    val createOffering: Boolean = true,
    /** Initial note shared with the source department (outline context, constraints, etc.). */
    val briefingNote: String? = null
)

data class RespondTeachingRequest(
    val action: String, // ACCEPT | DECLINE
    val note: String? = null
)

data class RequestAttachmentDto(
    val id: UUID,
    val teachingRequestId: UUID,
    val fileName: String,
    val contentType: String?,
    val fileSizeBytes: Long?,
    val docType: String,
    val uploadedByName: String?,
    val departmentName: String?,
    val createdAt: Instant
)

data class RequestMessageDto(
    val id: UUID,
    val teachingRequestId: UUID,
    val authorName: String?,
    val authorRole: String?,
    val authorDepartmentName: String?,
    val messageType: String,
    val body: String,
    val relatedLecturerId: UUID?,
    val relatedLecturerName: String?,
    val notifyAuthority: Boolean,
    val createdAt: Instant,
    val mine: Boolean = false
)

data class CreateRequestMessage(
    val body: String,
    /** COMMENT | ELIGIBILITY_NOTE | AUTHORITY_NOTICE */
    val messageType: String = "COMMENT",
    val relatedLecturerId: UUID? = null,
    val relatedLecturerName: String? = null,
    val notifyAuthority: Boolean = false
)

data class TeachingRequestDetailDto(
    val request: TeachingRequestDto,
    val attachments: List<RequestAttachmentDto>,
    val messages: List<RequestMessageDto>
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
    val recommendedLecturerId: UUID? = null,
    val courseOfferingId: UUID? = null,
    val suitabilityScore: BigDecimal? = null,
    val suitabilityBreakdown: String? = null,
    val decisionType: String? = null,
    val decisionNote: String? = null
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
    val teachingRequestId: UUID?,
    val workflowStatus: String = "DRAFT"
)

data class AllocationCommentDto(
    val id: UUID,
    val allocationId: UUID,
    val authorId: UUID,
    val authorName: String,
    val body: String,
    val commentType: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?
)

data class CreateAllocationCommentRequest(
    val body: String,
    val commentType: String = "COMMENT"
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
    val recentActivity: List<AuditLogDto>,
    val scopeDepartmentId: UUID? = null,
    val scopeDepartmentName: String? = null,
    val scopeRole: String? = null
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
    val parentId: UUID? = null,
    val parentName: String? = null
)

data class UpdateOrgNodeRequest(
    val name: String? = null,
    val type: String? = null,
    val parentId: UUID? = null,
    val parentName: String? = null,
    /** When true and both parent fields are null/blank, clear parent (make root). */
    val clearParent: Boolean = false
)

data class OrganizationImportResultDto(
    val created: Int,
    val updated: Int,
    val skipped: Int,
    val errors: Int,
    val details: List<String> = emptyList()
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
data class MappingProfileDto(
    val id: UUID,
    val name: String,
    val entityType: String,
    val columnMap: Map<String, String>,
    val mappingVersion: Int = 1,
    val fileFormat: String? = null,
    val notes: String? = null
)
data class CreateMappingProfileRequest(
    val name: String,
    val entityType: String,
    val columnMap: Map<String, String>,
    val fileFormat: String? = null,
    val notes: String? = null
)
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

data class ColumnMappingSuggestionDto(
    val sourceColumn: String,
    val targetField: String?,
    val confidence: Double,
    val method: String,
    val sampleValues: List<String> = emptyList(),
    val unmapped: Boolean = false
)

data class UpdateImportMappingRequest(
    val columnMap: Map<String, String>,
    val entityType: String? = null,
    val ignoredColumns: List<String> = emptyList(),
    val saveAsProfileName: String? = null
)

data class ImportResultSummaryDto(
    val allocations: Int = 0,
    val lecturers: Int = 0,
    val units: Int = 0,
    val warnings: Int = 0,
    val errors: Int = 0,
    val duplicatesSkipped: Int = 0,
    val unmappedFields: Int = 0,
    val details: List<String> = emptyList()
)

data class ImportUploadResultDto(
    val sessionId: java.util.UUID,
    val fileName: String,
    val entityType: String,
    val status: String,
    val detectedColumns: List<String>,
    val suggestedMap: Map<String, String>,
    val mappingSuggestions: List<ColumnMappingSuggestionDto> = emptyList(),
    val unmappedColumns: List<String> = emptyList(),
    val canonicalFields: List<String> = emptyList(),
    val profileApplied: String? = null,
    val rowCount: Int,
    val preview: List<Map<String, String>>,
    val warnings: List<String> = emptyList()
)

data class ImportSessionDto(
    val id: UUID,
    val fileName: String,
    val entityType: String,
    val status: String,
    val columnMap: String?,
    val createdAt: Instant,
    val resultSummary: ImportResultSummaryDto? = null
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

data class MembershipDto(
    val id: UUID,
    val organizationNodeId: UUID,
    val organizationName: String,
    val organizationType: String,
    val role: String,
    val isPrimary: Boolean
)

data class LoginResponse(
    val email: String,
    val name: String,
    val role: String,
    val tenantId: UUID,
    val userId: UUID? = null,
    val organizationNodeId: UUID? = null,
    val departmentName: String? = null,
    val activeDepartmentId: UUID? = null,
    val activeDepartmentName: String? = null,
    val activeRole: String? = null,
    val memberships: List<MembershipDto> = emptyList()
)

data class AssignChairRequest(
    val userId: UUID? = null,
    val email: String? = null,
    val departmentId: UUID? = null,
    val department: String? = null
)

data class CreateMembershipRequest(
    val userId: UUID,
    val organizationNodeId: UUID,
    val role: String,
    val isPrimary: Boolean = false
)

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

data class InvitationDto(
    val id: UUID,
    val email: String,
    val name: String,
    val role: String,
    val organizationNodeId: UUID?,
    val organizationName: String? = null,
    val status: String,
    /** Present only on create / resend / rotate-link responses — never echo stored secrets from list. */
    val token: String? = null,
    val invitePath: String? = null,
    val createdAt: String,
    val expiresAt: String,
    val deliveryStatus: String? = null,
    val deliveryError: String? = null,
    val emailProvider: String? = null,
    val lastSentAt: String? = null,
    val emailSent: Boolean? = null,
    val message: String? = null,
    val permissions: List<String> = emptyList(),
    val permissionTemplate: String? = null
)

data class CreateInvitationRequest(
    val email: String,
    val name: String,
    val role: String,
    val organizationNodeId: UUID? = null,
    val organization: String? = null,
    val permissionTemplate: String? = null,
    val permissions: List<String>? = null
)

data class AcceptInvitationRequest(
    val token: String,
    val name: String? = null,
    val password: String? = null
)

data class InvitationPreviewDto(
    val email: String,
    val name: String,
    val role: String,
    val institutionName: String,
    val status: String,
    val expired: Boolean
)

data class CourseOfferingDto(
    val id: UUID,
    val academicUnitId: UUID,
    val unitCode: String,
    val unitName: String,
    val programme: String?,
    val contextLabel: String?,
    val levelLabel: String?,
    val displayTitle: String?,
    val requestingDepartment: String?,
    val owningDepartment: String?,
    val status: String
)

data class CourseRequirementDto(
    val id: UUID,
    val type: String,
    val label: String,
    val weight: java.math.BigDecimal,
    val source: String
)

data class CourseOutlineDto(
    val id: UUID,
    val fileName: String,
    val contentType: String?,
    val detectedContentType: String? = null,
    val fileExtension: String? = null,
    val fileSizeBytes: Long? = null,
    val checksumSha256: String? = null,
    val versionNo: Int,
    val extractionConfidence: java.math.BigDecimal,
    val needsReview: Boolean,
    val processingStatus: String = "UPLOADED",
    val extractionMethod: String? = null,
    val processingMessage: String? = null,
    val processingErrorCode: String? = null,
    val createdAt: String,
    val hasFile: Boolean,
    val warnings: List<String> = emptyList()
)

data class CourseOfferingDetailDto(
    val offering: CourseOfferingDto,
    val requirements: List<CourseRequirementDto>,
    val outlines: List<CourseOutlineDto>
)

data class RequirementInputDto(
    val type: String,
    val label: String,
    val weight: java.math.BigDecimal? = null
)

data class CreateCourseOfferingRequest(
    val academicUnitId: UUID,
    val programme: String? = null,
    val contextLabel: String? = null,
    val levelLabel: String? = null,
    val displayTitle: String? = null,
    val academicYearId: UUID? = null,
    val semesterId: UUID? = null,
    val requestingDepartmentId: UUID? = null,
    val owningDepartmentId: UUID? = null,
    val requirements: List<RequirementInputDto> = emptyList()
)

data class SuitabilityBreakdownDto(
    val key: String,
    val label: String,
    val earned: Double,
    val max: Double
)

data class SuitabilityCandidateDto(
    val lecturerId: UUID,
    val name: String,
    val department: String,
    val score: Int,
    val classification: String,
    val positives: List<String>,
    val warnings: List<String>,
    val missingEvidence: List<String>,
    val breakdown: List<SuitabilityBreakdownDto>,
    val crossDepartment: Boolean,
    val load: String
)

data class AllocateFromOfferingRequest(
    val courseOfferingId: UUID,
    val lecturerId: UUID,
    val decisionNote: String? = null
)

