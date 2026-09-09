package com.academicflow.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "tenants")
class Tenant(
    @Id var id: UUID = UUID.randomUUID(),
    var name: String = "",
    var code: String = "",
    var status: String = "APPROVED",
    var adminEmail: String? = null,
    var adminName: String? = null,
    var decisionNote: String? = null,
    var decidedAt: Instant? = null,
    var onboardingStage: String = "ACTIVE",
    var planCode: String = "STANDARD",
    var lastActivityAt: Instant? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "users")
class AppUser(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var email: String = "",
    var fullName: String = "",
    var role: String = "VIEWER",
    var organizationNodeId: UUID? = null,
    var passwordHash: String? = null,
    /** Stable Clerk user id (e.g. user_…). Null until linked via Clerk auth. */
    var clerkUserId: String? = null,
    var active: Boolean = true,
    var accountStatus: String = "ACTIVE",
    var lastLoginAt: Instant? = null,
    var failedLoginCount: Int = 0,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "organization_nodes")
class OrganizationNode(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var name: String = "",
    var type: String = "",
    var parentId: UUID? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "organization_memberships")
class OrganizationMembership(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var userId: UUID = UUID(0, 0),
    var organizationNodeId: UUID = UUID(0, 0),
    var role: String = "VIEWER",
    var status: String = "ACTIVE",
    var isPrimary: Boolean = false,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "students")
class Student(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var organizationNodeId: UUID = UUID(0, 0),
    var programme: String? = null,
    var studentNumber: String = "",
    var fullName: String = "",
    var email: String? = null,
    var yearOfStudy: Int? = null,
    var status: String = "ACTIVE",
    var userId: UUID? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "academic_years")
class AcademicYear(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var label: String = "",
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null
)

@Entity
@Table(name = "semesters")
class Semester(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var academicYearId: UUID = UUID(0, 0),
    var name: String = "",
    var sequenceNo: Int = 1
)

@Entity
@Table(name = "lecturers")
class Lecturer(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var staffNumber: String = "",
    var fullName: String = "",
    var email: String = "",
    var organizationNodeId: UUID = UUID(0, 0),
    var qualifications: String? = null,
    var currentWorkload: BigDecimal = BigDecimal.ZERO,
    var maximumWorkload: BigDecimal = BigDecimal("12"),
    var availabilityText: String? = null,
    var status: String = "Active",
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "lecturer_expertise")
class LecturerExpertise(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var lecturerId: UUID = UUID(0, 0),
    var subject: String = "",
    var level: String = "Moderate"
)

@Entity
@Table(name = "academic_units")
class AcademicUnit(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var code: String = "",
    var name: String = "",
    var sourceDepartmentId: UUID = UUID(0, 0),
    var contactHours: BigDecimal = BigDecimal("3"),
    var studentCount: Int = 0,
    var academicYearId: UUID? = null,
    var semesterId: UUID? = null,
    var requiredExpertise: String? = null,
    var status: String = "Unallocated",
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "class_groups")
class ClassGroup(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var academicUnitId: UUID = UUID(0, 0),
    var name: String = "",
    var studentCount: Int = 0
)

@Entity
@Table(name = "teaching_requests")
class TeachingRequest(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var requestingDepartmentId: UUID = UUID(0, 0),
    var preferredDepartmentId: UUID? = null,
    var academicUnitId: UUID = UUID(0, 0),
    var courseOfferingId: UUID? = null,
    var studentCount: Int = 0,
    var contactHours: BigDecimal = BigDecimal.ZERO,
    var requiredExpertise: String? = null,
    var academicYearId: UUID? = null,
    var semesterId: UUID? = null,
    var status: String = "DRAFT",
    var briefingNote: String? = null,
    var createdBy: UUID? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "request_candidates")
class RequestCandidate(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var teachingRequestId: UUID = UUID(0, 0),
    var lecturerId: UUID = UUID(0, 0),
    var matchScore: BigDecimal = BigDecimal.ZERO,
    var expertiseScore: BigDecimal = BigDecimal.ZERO,
    var availabilityScore: BigDecimal = BigDecimal.ZERO,
    var workloadScore: BigDecimal = BigDecimal.ZERO,
    var studentLoadScore: BigDecimal = BigDecimal.ZERO,
    var policyScore: BigDecimal = BigDecimal.ZERO,
    var qualified: Boolean = false,
    var available: Boolean = false,
    var workloadOk: Boolean = false,
    var noConflict: Boolean = false,
    var policyOk: Boolean = false,
    var reasons: String? = null,
    var warning: String? = null,
    var rankNo: Int = 0
)

@Entity
@Table(name = "allocations")
class Allocation(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var academicUnitId: UUID = UUID(0, 0),
    var lecturerId: UUID? = null,
    var teachingRequestId: UUID? = null,
    var courseOfferingId: UUID? = null,
    var matchScore: BigDecimal? = null,
    var status: String = "ASSIGNED",
    var overrideReason: String? = null,
    var recommendedLecturerId: UUID? = null,
    var suitabilityScore: BigDecimal? = null,
    var suitabilityBreakdown: String? = null,
    var decisionType: String? = null,
    var decisionNote: String? = null,
    var createdBy: UUID? = null,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var workflowStatus: String = "DRAFT"
)

@Entity
@Table(name = "allocation_comments")
class AllocationComment(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var allocationId: UUID = UUID(0, 0),
    var authorId: UUID = UUID(0, 0),
    var body: String = "",
    var commentType: String = "COMMENT",
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var resolvedAt: Instant? = null,
    var resolvedBy: UUID? = null
)

@Entity
@Table(name = "user_permissions")
class UserPermission(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var userId: UUID = UUID(0, 0),
    var permission: String = "",
    var effect: String = "GRANT",
    var organizationNodeId: UUID? = null,
    var grantedBy: UUID? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "approvals")
class Approval(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var allocationId: UUID = UUID(0, 0),
    var status: String = "PENDING",
    var submittedBy: UUID? = null,
    var decidedBy: UUID? = null,
    var decisionNote: String? = null,
    var submittedAt: Instant = Instant.now(),
    var decidedAt: Instant? = null
)

@Entity
@Table(name = "timetable_entries")
class TimetableEntry(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var allocationId: UUID? = null,
    var lecturerId: UUID? = null,
    var academicUnitId: UUID? = null,
    var classGroupId: UUID? = null,
    var dayOfWeek: Int = 1,
    var startTime: LocalTime = LocalTime.of(8, 0),
    var endTime: LocalTime = LocalTime.of(10, 0),
    var room: String? = null
)

@Entity
@Table(name = "conflicts")
class Conflict(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var allocationId: UUID? = null,
    var category: String = "",
    var severity: String = "med",
    var description: String = "",
    var relatedEntity: String? = null,
    var resolved: Boolean = false,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var actorId: UUID? = null,
    var action: String = "",
    var entityType: String = "",
    var entityId: String? = null,
    var details: String? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "import_sessions")
class ImportSession(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var fileName: String = "",
    var entityType: String = "",
    var status: String = "UPLOADED",
    var columnMap: String? = null,
    var resultSummary: String? = null,
    var createdBy: UUID? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "import_staging_rows")
class ImportStagingRow(
    @Id var id: UUID = UUID.randomUUID(),
    var sessionId: UUID = UUID(0, 0),
    var rowNumber: Int = 0,
    var rawJson: String = "{}",
    var valid: Boolean = true,
    var errors: String? = null
)

@Entity
@Table(name = "organization_types")
class OrganizationType(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var name: String = "",
    var levelNo: Int = 1,
    var description: String? = null
)

@Entity
@Table(name = "roles")
class RoleDef(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var code: String = "",
    var name: String = "",
    var permissions: String = "",
    var description: String? = null
)

@Entity
@Table(name = "institutional_rules")
class InstitutionalRule(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var ruleKey: String = "",
    var ruleValue: String = "",
    var description: String? = null
)

@Entity
@Table(name = "system_settings")
class SystemSetting(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var settingKey: String = "",
    var settingValue: String = "",
    var description: String? = null
)

@Entity
@Table(name = "import_mapping_profiles")
class ImportMappingProfile(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var name: String = "",
    var entityType: String = "",
    var columnMap: String = "",
    var mappingVersion: Int = 1,
    var fileFormat: String? = null,
    var notes: String? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "feature_flags")
class FeatureFlag(
    @Id var id: UUID = UUID.randomUUID(),
    var flagKey: String = "",
    var name: String = "",
    var description: String? = null,
    var enabled: Boolean = true,
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "platform_settings")
class PlatformSetting(
    @Id var id: UUID = UUID.randomUUID(),
    var settingKey: String = "",
    var settingValue: String = "",
    var description: String? = null,
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "security_events")
class SecurityEvent(
    @Id var id: UUID = UUID.randomUUID(),
    var severity: String = "LOW",
    var eventType: String = "",
    var email: String? = null,
    var tenantId: UUID? = null,
    var details: String? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "invitations")
class Invitation(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var email: String = "",
    var fullName: String = "",
    var role: String = "VIEWER",
    var organizationNodeId: UUID? = null,
    /** Stored token value — SHA-256 hex for new invites; may be legacy plaintext until rotated. */
    var token: String = "",
    var tokenHash: String? = null,
    var status: String = "PENDING",
    var invitedBy: UUID? = null,
    var createdAt: Instant = Instant.now(),
    var expiresAt: Instant = Instant.now().plusSeconds(60L * 60 * 24 * 14),
    var acceptedAt: Instant? = null,
    var revokedAt: Instant? = null,
    var deliveryStatus: String = "QUEUED",
    var deliveryError: String? = null,
    var lastSentAt: Instant? = null,
    var emailProvider: String? = null,
    /** JSON array of permission codes assigned at invite time. */
    var permissionsJson: String? = null,
    var permissionTemplate: String? = null
)

@Entity
@Table(name = "course_offerings")
class CourseOffering(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var academicUnitId: UUID = UUID(0, 0),
    var programme: String? = null,
    var contextLabel: String? = null,
    var levelLabel: String? = null,
    var displayTitle: String? = null,
    var academicYearId: UUID? = null,
    var semesterId: UUID? = null,
    var requestingDepartmentId: UUID? = null,
    var owningDepartmentId: UUID? = null,
    var status: String = "ACTIVE",
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "course_outlines")
class CourseOutline(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var courseOfferingId: UUID = UUID(0, 0),
    var fileName: String = "",
    var contentType: String? = null,
    var detectedContentType: String? = null,
    var fileExtension: String? = null,
    var fileSizeBytes: Long? = null,
    var checksumSha256: String? = null,
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "file_bytes", columnDefinition = "BYTEA")
    var fileBytes: ByteArray? = null,
    var extractedText: String? = null,
    var extractedJson: String? = null,
    var rawExtractionJson: String? = null,
    var extractionConfidence: BigDecimal = BigDecimal.ZERO,
    var needsReview: Boolean = true,
    var processingStatus: String = "UPLOADED",
    var extractionMethod: String? = null,
    var processingMessage: String? = null,
    var processingErrorCode: String? = null,
    var versionNo: Int = 1,
    var uploadedBy: UUID? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "course_requirements")
class CourseRequirement(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var courseOfferingId: UUID = UUID(0, 0),
    var requirementType: String = "TOPIC",
    var label: String = "",
    var weight: BigDecimal = BigDecimal.ONE,
    var source: String = "MANUAL",
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "lecturer_context_experience")
class LecturerContextExperience(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var lecturerId: UUID = UUID(0, 0),
    var contextLabel: String? = null,
    var programme: String? = null,
    var levelLabel: String? = null,
    var topicLabel: String? = null,
    var unitCode: String? = null,
    var timesTaught: Int = 1,
    var lastTaughtLabel: String? = null
)

@Entity
@Table(name = "request_attachments")
class RequestAttachment(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var teachingRequestId: UUID = UUID(0, 0),
    var fileName: String = "",
    var contentType: String? = null,
    var fileSizeBytes: Long? = null,
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "file_bytes", columnDefinition = "BYTEA")
    var fileBytes: ByteArray? = null,
    var docType: String = "COURSE_OUTLINE",
    var uploadedBy: UUID? = null,
    var uploadedByName: String? = null,
    var departmentId: UUID? = null,
    var departmentName: String? = null,
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "request_messages")
class RequestMessage(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID(0, 0),
    var teachingRequestId: UUID = UUID(0, 0),
    var authorUserId: UUID? = null,
    var authorName: String? = null,
    var authorRole: String? = null,
    var authorDepartmentId: UUID? = null,
    var authorDepartmentName: String? = null,
    /** COMMENT | ELIGIBILITY_NOTE | AUTHORITY_NOTICE | SYSTEM | ACCEPT_NOTE | DECLINE_NOTE */
    var messageType: String = "COMMENT",
    var body: String = "",
    var relatedLecturerId: UUID? = null,
    var relatedLecturerName: String? = null,
    var notifyAuthority: Boolean = false,
    var createdAt: Instant = Instant.now()
)
