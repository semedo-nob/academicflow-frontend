package com.academicflow.repository

import com.academicflow.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrganizationNodeRepository : JpaRepository<OrganizationNode, UUID> {
    fun findByTenantIdOrderByNameAsc(tenantId: UUID): List<OrganizationNode>
}

interface LecturerRepository : JpaRepository<Lecturer, UUID> {
    fun findByTenantIdOrderByFullNameAsc(tenantId: UUID): List<Lecturer>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): Lecturer?
}

interface LecturerExpertiseRepository : JpaRepository<LecturerExpertise, UUID> {
    fun findByTenantIdAndLecturerId(tenantId: UUID, lecturerId: UUID): List<LecturerExpertise>
    fun findByTenantId(tenantId: UUID): List<LecturerExpertise>
}

interface AcademicUnitRepository : JpaRepository<AcademicUnit, UUID> {
    fun findByTenantIdOrderByCodeAsc(tenantId: UUID): List<AcademicUnit>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): AcademicUnit?
}

interface TeachingRequestRepository : JpaRepository<TeachingRequest, UUID> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<TeachingRequest>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): TeachingRequest?
}

interface RequestAttachmentRepository : JpaRepository<RequestAttachment, UUID> {
    fun findByTenantIdAndTeachingRequestIdOrderByCreatedAtAsc(tenantId: UUID, teachingRequestId: UUID): List<RequestAttachment>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): RequestAttachment?
    fun countByTenantIdAndTeachingRequestId(tenantId: UUID, teachingRequestId: UUID): Long
}

interface RequestMessageRepository : JpaRepository<RequestMessage, UUID> {
    fun findByTenantIdAndTeachingRequestIdOrderByCreatedAtAsc(tenantId: UUID, teachingRequestId: UUID): List<RequestMessage>
    fun countByTenantIdAndTeachingRequestId(tenantId: UUID, teachingRequestId: UUID): Long
}

interface RequestCandidateRepository : JpaRepository<RequestCandidate, UUID> {
    fun findByTenantIdAndTeachingRequestIdOrderByRankNoAsc(tenantId: UUID, requestId: UUID): List<RequestCandidate>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RequestCandidate c WHERE c.teachingRequestId = :requestId")
    fun deleteByTeachingRequestId(@Param("requestId") requestId: UUID)
}

interface AllocationRepository : JpaRepository<Allocation, UUID> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<Allocation>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): Allocation?
    fun findByTenantIdAndAcademicUnitId(tenantId: UUID, unitId: UUID): List<Allocation>
    fun findByTenantIdAndLecturerId(tenantId: UUID, lecturerId: UUID): List<Allocation>
}

interface ApprovalRepository : JpaRepository<Approval, UUID> {
    fun findByTenantIdOrderBySubmittedAtDesc(tenantId: UUID): List<Approval>
    fun findByTenantIdAndAllocationId(tenantId: UUID, allocationId: UUID): List<Approval>
}

interface ConflictRepository : JpaRepository<Conflict, UUID> {
    fun findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(tenantId: UUID): List<Conflict>
}

interface TimetableEntryRepository : JpaRepository<TimetableEntry, UUID> {
    fun findByTenantId(tenantId: UUID): List<TimetableEntry>
    fun findByTenantIdAndLecturerId(tenantId: UUID, lecturerId: UUID): List<TimetableEntry>
}

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<AuditLog>
}

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findByTenantIdAndEmail(tenantId: UUID, email: String): AppUser?
    fun findByTenantIdOrderByFullNameAsc(tenantId: UUID): List<AppUser>
    fun findByEmailIgnoreCase(email: String): List<AppUser>
}

interface OrganizationMembershipRepository : JpaRepository<OrganizationMembership, UUID> {
    fun findByTenantIdAndUserId(tenantId: UUID, userId: UUID): List<OrganizationMembership>
    fun findByTenantIdAndUserIdAndStatus(tenantId: UUID, userId: UUID, status: String): List<OrganizationMembership>
    fun findByTenantIdAndOrganizationNodeIdAndRoleAndStatus(
        tenantId: UUID,
        organizationNodeId: UUID,
        role: String,
        status: String
    ): List<OrganizationMembership>
    fun findByTenantIdAndOrganizationNodeId(tenantId: UUID, organizationNodeId: UUID): List<OrganizationMembership>
}

interface StudentRepository : JpaRepository<Student, UUID> {
    fun findByTenantIdOrderByFullNameAsc(tenantId: UUID): List<Student>
    fun findByTenantIdAndOrganizationNodeIdOrderByFullNameAsc(tenantId: UUID, organizationNodeId: UUID): List<Student>
}

interface ImportSessionRepository : JpaRepository<ImportSession, UUID> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<ImportSession>
}

interface ImportStagingRowRepository : JpaRepository<ImportStagingRow, UUID> {
    fun findBySessionIdOrderByRowNumberAsc(sessionId: UUID): List<ImportStagingRow>
}

interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun findByCodeIgnoreCase(code: String): Tenant?
    fun findByStatusOrderByCreatedAtDesc(status: String): List<Tenant>
}

interface AcademicYearRepository : JpaRepository<AcademicYear, UUID> {
    fun findByTenantIdOrderByLabelDesc(tenantId: UUID): List<AcademicYear>
}

interface SemesterRepository : JpaRepository<Semester, UUID> {
    fun findByTenantIdOrderBySequenceNoAsc(tenantId: UUID): List<Semester>
    fun findByTenantIdAndAcademicYearIdOrderBySequenceNoAsc(tenantId: UUID, academicYearId: UUID): List<Semester>
}

interface OrganizationTypeRepository : JpaRepository<OrganizationType, UUID> {
    fun findByTenantIdOrderByLevelNoAsc(tenantId: UUID): List<OrganizationType>
}

interface RoleDefRepository : JpaRepository<RoleDef, UUID> {
    fun findByTenantIdOrderByNameAsc(tenantId: UUID): List<RoleDef>
}

interface InstitutionalRuleRepository : JpaRepository<InstitutionalRule, UUID> {
    fun findByTenantIdOrderByRuleKeyAsc(tenantId: UUID): List<InstitutionalRule>
    fun findByTenantIdAndRuleKey(tenantId: UUID, ruleKey: String): InstitutionalRule?
}

interface SystemSettingRepository : JpaRepository<SystemSetting, UUID> {
    fun findByTenantIdOrderBySettingKeyAsc(tenantId: UUID): List<SystemSetting>
    fun findByTenantIdAndSettingKey(tenantId: UUID, settingKey: String): SystemSetting?
}

interface ImportMappingProfileRepository : JpaRepository<ImportMappingProfile, UUID> {
    fun findByTenantIdOrderByNameAsc(tenantId: UUID): List<ImportMappingProfile>
    fun findByTenantIdAndEntityTypeIgnoreCaseOrderByNameAsc(tenantId: UUID, entityType: String): List<ImportMappingProfile>
}

interface FeatureFlagRepository : JpaRepository<FeatureFlag, UUID> {
    fun findAllByOrderByNameAsc(): List<FeatureFlag>
    fun findByFlagKey(flagKey: String): FeatureFlag?
}

interface PlatformSettingRepository : JpaRepository<PlatformSetting, UUID> {
    fun findAllByOrderBySettingKeyAsc(): List<PlatformSetting>
    fun findBySettingKey(settingKey: String): PlatformSetting?
}

interface SecurityEventRepository : JpaRepository<SecurityEvent, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<SecurityEvent>
}

interface InvitationRepository : JpaRepository<Invitation, UUID> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<Invitation>
    fun findByToken(token: String): Invitation?
    fun findByTenantIdAndEmailIgnoreCaseAndStatus(tenantId: UUID, email: String, status: String): List<Invitation>
}

interface CourseOfferingRepository : JpaRepository<CourseOffering, UUID> {
    fun findByTenantIdOrderByDisplayTitleAsc(tenantId: UUID): List<CourseOffering>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): CourseOffering?
    fun findByTenantIdAndAcademicUnitId(tenantId: UUID, academicUnitId: UUID): List<CourseOffering>
}

interface CourseOutlineRepository : JpaRepository<CourseOutline, UUID> {
    fun findByTenantIdAndCourseOfferingIdOrderByVersionNoDesc(tenantId: UUID, courseOfferingId: UUID): List<CourseOutline>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): CourseOutline?
}

interface CourseRequirementRepository : JpaRepository<CourseRequirement, UUID> {
    fun findByTenantIdAndCourseOfferingIdOrderByRequirementTypeAscLabelAsc(tenantId: UUID, courseOfferingId: UUID): List<CourseRequirement>
    fun deleteByTenantIdAndCourseOfferingIdAndSource(tenantId: UUID, courseOfferingId: UUID, source: String)
}

interface LecturerContextExperienceRepository : JpaRepository<LecturerContextExperience, UUID> {
    fun findByTenantIdAndLecturerId(tenantId: UUID, lecturerId: UUID): List<LecturerContextExperience>
    fun findByTenantId(tenantId: UUID): List<LecturerContextExperience>
}
