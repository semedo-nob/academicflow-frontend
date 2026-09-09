package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.config.UserContext
import com.academicflow.config.ClerkContext
import com.academicflow.dto.*
import com.academicflow.entity.*
import com.academicflow.repository.*
import com.academicflow.service.auth.AuthIdentityService
import com.academicflow.service.email.EmailDeliveryStatus
import com.academicflow.service.email.EmailMessage
import com.academicflow.service.email.EmailService
import com.academicflow.service.email.InvitationEmailTemplates
import com.academicflow.service.permission.PermissionCodes
import com.academicflow.service.permission.PermissionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AdminConfigService(
    private val tenantRepo: TenantRepository,
    private val userRepo: AppUserRepository,
    private val roleRepo: RoleDefRepository,
    private val orgTypeRepo: OrganizationTypeRepository,
    private val yearRepo: AcademicYearRepository,
    private val semesterRepo: SemesterRepository,
    private val ruleRepo: InstitutionalRuleRepository,
    private val settingRepo: SystemSettingRepository,
    private val mappingRepo: ImportMappingProfileRepository,
    private val orgRepo: OrganizationNodeRepository,
    private val auditRepo: AuditLogRepository,
    private val invitationRepo: InvitationRepository,
    private val membershipRepo: OrganizationMembershipRepository,
    private val emailService: EmailService,
    private val authIdentityService: AuthIdentityService,
    private val permissionService: PermissionService,
    @Value("\${academicflow.app-base-url:http://127.0.0.1:5173}") private val appBaseUrl: String,
    @Value("\${academicflow.invitation-expiry-hours:336}") private val invitationExpiryHours: Long
) {
    private val log = LoggerFactory.getLogger(AdminConfigService::class.java)
    fun listInstitutions(): List<InstitutionDto> =
        tenantRepo.findAll()
            .sortedByDescending { it.createdAt }
            .map { toInstitutionDto(it) }

    /** Public school/institution account signup — waits for platform Super Admin approval. */
    @Transactional
    fun registerInstitution(req: CreateInstitutionRequest): InstitutionSignupResponse {
        val name = req.name.trim()
        val code = req.code.trim().uppercase()
        val adminEmail = req.adminEmail?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Admin email is required")
        val adminName = req.adminName?.trim()?.takeIf { it.isNotEmpty() } ?: "Institution Admin"

        require(name.isNotEmpty()) { "Institution name is required" }
        require(code.matches(Regex("^[A-Z0-9_-]{2,16}$"))) {
            "Code must be 2–16 characters (letters, numbers, _ or -)"
        }
        if (tenantRepo.findByCodeIgnoreCase(code) != null) {
            throw IllegalArgumentException("Institution code already exists")
        }
        if (userRepo.findByEmailIgnoreCase(adminEmail).isNotEmpty()) {
            throw IllegalArgumentException("An account with this email already exists")
        }

        val tenant = tenantRepo.save(
            Tenant(
                name = name,
                code = code,
                status = "PENDING",
                onboardingStage = "REQUESTED",
                adminEmail = adminEmail,
                adminName = adminName
            )
        )
        provisionTenantScaffold(tenant, adminEmail, adminName, activateAdmin = false)

        auditRepo.save(
            AuditLog(
                tenantId = tenant.id,
                action = "Institution signup submitted",
                entityType = "Tenant",
                entityId = tenant.id.toString(),
                details = "$name ($code) · $adminEmail"
            )
        )
        return InstitutionSignupResponse(
            id = tenant.id,
            name = tenant.name,
            code = tenant.code,
            status = tenant.status,
            message = "Signup received. You can sign in after the platform administrator approves your institution."
        )
    }

    @Transactional
    fun decideInstitution(id: UUID, req: InstitutionDecisionRequest): InstitutionDto {
        val tenant = tenantRepo.findById(id).orElseThrow { NoSuchElementException("Institution not found") }
        if (tenant.status != "PENDING") {
            throw IllegalArgumentException("Only pending institutions can be decided")
        }
        if (req.approve) {
            tenant.status = "APPROVED"
            tenant.onboardingStage = "ACTIVE"
            tenant.decisionNote = req.note
            tenant.decidedAt = java.time.Instant.now()
            tenantRepo.save(tenant)
            userRepo.findByTenantIdOrderByFullNameAsc(tenant.id)
                .filter { it.email.equals(tenant.adminEmail, true) || it.role == "INSTITUTION_ADMIN" }
                .forEach {
                    it.active = true
                    userRepo.save(it)
                }
            auditRepo.save(
                AuditLog(
                    tenantId = TenantContext.get(),
                    action = "Institution approved",
                    entityType = "Tenant",
                    entityId = tenant.id.toString(),
                    details = "${tenant.name} (${tenant.code})"
                )
            )
        } else {
            tenant.status = "REJECTED"
            tenant.onboardingStage = "REJECTED"
            tenant.decisionNote = req.note ?: "Rejected"
            tenant.decidedAt = java.time.Instant.now()
            tenantRepo.save(tenant)
            userRepo.findByTenantIdOrderByFullNameAsc(tenant.id).forEach {
                it.active = false
                userRepo.save(it)
            }
            auditRepo.save(
                AuditLog(
                    tenantId = TenantContext.get(),
                    action = "Institution rejected",
                    entityType = "Tenant",
                    entityId = tenant.id.toString(),
                    details = "${tenant.name} (${tenant.code}) · ${tenant.decisionNote}"
                )
            )
        }
        return toInstitutionDto(tenant)
    }

    fun platformOverview(): PlatformOverviewDto {
        val all = tenantRepo.findAll()
        return PlatformOverviewDto(
            pendingRegistrations = all.count { it.status == "PENDING" },
            approvedInstitutions = all.count { it.status == "APPROVED" },
            suspendedInstitutions = all.count { it.status == "SUSPENDED" },
            rejectedInstitutions = all.count { it.status == "REJECTED" },
            totalInstitutions = all.size
        )
    }

    @Transactional
    fun setInstitutionStatus(id: UUID, req: InstitutionStatusRequest): InstitutionDto {
        val status = req.status.trim().uppercase()
        require(status in setOf("SUSPENDED", "APPROVED")) {
            "status must be SUSPENDED or APPROVED"
        }
        val tenant = tenantRepo.findById(id).orElseThrow { NoSuchElementException("Institution not found") }
        if (tenant.id == TenantContext.get() && status == "SUSPENDED") {
            throw IllegalArgumentException("Cannot suspend the platform tenant you are signed into")
        }
        if (tenant.status == "PENDING") {
            throw IllegalArgumentException("Pending registrations must be approved or rejected first")
        }
        if (status == "APPROVED" && tenant.status !in setOf("SUSPENDED", "APPROVED", "REJECTED")) {
            throw IllegalArgumentException("Cannot activate institution from status ${tenant.status}")
        }

        tenant.status = status
        tenant.decisionNote = req.note
        tenant.decidedAt = java.time.Instant.now()
        tenantRepo.save(tenant)

        if (status == "SUSPENDED") {
            userRepo.findByTenantIdOrderByFullNameAsc(tenant.id).forEach {
                it.active = false
                userRepo.save(it)
            }
            auditRepo.save(
                AuditLog(
                    tenantId = TenantContext.get(),
                    action = "Institution suspended",
                    entityType = "Tenant",
                    entityId = tenant.id.toString(),
                    details = "${tenant.name} (${tenant.code}) · ${req.note ?: ""}"
                )
            )
        } else {
            userRepo.findByTenantIdOrderByFullNameAsc(tenant.id)
                .filter { it.email.equals(tenant.adminEmail, true) || it.role == "INSTITUTION_ADMIN" }
                .forEach {
                    it.active = true
                    userRepo.save(it)
                }
            auditRepo.save(
                AuditLog(
                    tenantId = TenantContext.get(),
                    action = "Institution reactivated",
                    entityType = "Tenant",
                    entityId = tenant.id.toString(),
                    details = "${tenant.name} (${tenant.code})"
                )
            )
        }
        return toInstitutionDto(tenant)
    }

    private fun toInstitutionDto(t: Tenant) = InstitutionDto(
        id = t.id,
        name = t.name,
        code = t.code,
        status = t.status,
        adminEmail = t.adminEmail,
        adminName = t.adminName,
        decisionNote = t.decisionNote,
        createdAt = t.createdAt.toString()
    )

    private fun provisionTenantScaffold(
        tenant: Tenant,
        adminEmail: String,
        adminName: String,
        activateAdmin: Boolean
    ) {
        val root = orgRepo.save(
            OrganizationNode(tenantId = tenant.id, name = tenant.name, type = "University", parentId = null)
        )

        listOf(
            Triple("University", 1, "Top-level institution"),
            Triple("School", 2, "Faculty / school under university"),
            Triple("Department", 3, "Teaching department")
        ).forEach { (typeName, level, desc) ->
            orgTypeRepo.save(
                OrganizationType(tenantId = tenant.id, name = typeName, levelNo = level, description = desc)
            )
        }

        listOf(
            RoleSeed(
                "INSTITUTION_ADMIN",
                "Institution Admin",
                "users,roles,organization,years,semesters,rules,settings,imports,audit,allocations,approvals,dashboard,lecturers,units,requests,recommendations,workload,timetable,conflicts,reports",
                "Manage this institution: users, roles, and teaching workflow"
            ),
            RoleSeed(
                "SCHOOL_DEAN",
                "School Dean",
                "dashboard,organization,units,requests,approvals,reports,workload,years,semesters",
                "School-level oversight"
            ),
            RoleSeed(
                "DEPARTMENT_CHAIR",
                "Department Chair",
                "dashboard,organization,lecturers,units,requests,recommendations,allocations,workload,timetable,conflicts,approvals,reports,imports",
                "Department teaching allocation"
            ),
            RoleSeed(
                "LECTURER",
                "Lecturer",
                "dashboard,timetable,workload,units",
                "Own timetable and workload"
            ),
            RoleSeed(
                "VIEWER",
                "Viewer",
                "dashboard,reports,timetable,workload",
                "Read-only access"
            )
        ).forEach { seed ->
            roleRepo.save(
                RoleDef(
                    tenantId = tenant.id,
                    code = seed.code,
                    name = seed.name,
                    permissions = seed.permissions,
                    description = seed.description
                )
            )
        }

        val year = yearRepo.save(
            AcademicYear(
                tenantId = tenant.id,
                label = "2026/2027",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2027, 8, 31)
            )
        )
        semesterRepo.save(Semester(tenantId = tenant.id, academicYearId = year.id, name = "Semester 1", sequenceNo = 1))
        semesterRepo.save(Semester(tenantId = tenant.id, academicYearId = year.id, name = "Semester 2", sequenceNo = 2))

        listOf(
            Triple("max_workload_hours", "12", "Default maximum teaching hours per lecturer"),
            Triple("require_approval", "true", "Allocations must be approved before publish"),
            Triple("allow_cross_department", "true", "Allow cross-department teaching requests"),
            Triple("block_high_conflicts", "true", "Block submit when high-severity conflicts remain")
        ).forEach { (key, value, desc) ->
            ruleRepo.save(InstitutionalRule(tenantId = tenant.id, ruleKey = key, ruleValue = value, description = desc))
        }
        listOf(
            Triple("institution_display_name", tenant.name, "Display name in UI"),
            Triple("default_academic_year", "2026/2027", "Default academic year label"),
            Triple("default_semester", "Semester 1", "Default semester name"),
            Triple("timezone", "Africa/Nairobi", "Institution timezone")
        ).forEach { (key, value, desc) ->
            settingRepo.save(SystemSetting(tenantId = tenant.id, settingKey = key, settingValue = value, description = desc))
        }

        userRepo.save(
            AppUser(
                tenantId = tenant.id,
                email = adminEmail,
                fullName = adminName,
                role = "INSTITUTION_ADMIN",
                organizationNodeId = root.id,
                passwordHash = "local",
                active = activateAdmin
            )
        )
    }

    private data class RoleSeed(val code: String, val name: String, val permissions: String, val description: String)

    fun listOrganizationTypes(): List<OrganizationTypeDto> =
        orgTypeRepo.findByTenantIdOrderByLevelNoAsc(TenantContext.get()).map {
            OrganizationTypeDto(it.id, it.name, it.levelNo, it.description)
        }

    fun listRoles(): List<RoleDto> =
        roleRepo.findByTenantIdOrderByNameAsc(TenantContext.get()).map {
            RoleDto(
                id = it.id,
                code = it.code,
                name = it.name,
                permissions = it.permissions.split(",").map { p -> p.trim() }.filter { p -> p.isNotEmpty() },
                description = it.description
            )
        }

    fun listYears(): List<AcademicYearDto> =
        yearRepo.findByTenantIdOrderByLabelDesc(TenantContext.get()).map { toYearDto(it) }

    fun listSemesters(academicYearId: UUID? = null): List<SemesterDto> {
        val tenantId = TenantContext.get()
        val years = yearRepo.findByTenantIdOrderByLabelDesc(tenantId).associateBy { it.id }
        val rows = if (academicYearId != null) {
            semesterRepo.findByTenantIdAndAcademicYearIdOrderBySequenceNoAsc(tenantId, academicYearId)
        } else {
            semesterRepo.findByTenantIdOrderBySequenceNoAsc(tenantId)
        }
        return rows.map { s ->
            SemesterDto(
                id = s.id,
                academicYearId = s.academicYearId,
                academicYearLabel = years[s.academicYearId]?.label ?: "",
                name = s.name,
                sequenceNo = s.sequenceNo
            )
        }
    }

    fun periodContext(): PeriodContextDto {
        val years = listYears()
        val semesters = listSemesters()
        val defaultYearLabel = settingRepo.findByTenantIdAndSettingKey(TenantContext.get(), "default_academic_year")?.settingValue
        val defaultSemester = settingRepo.findByTenantIdAndSettingKey(TenantContext.get(), "default_semester")?.settingValue
        val year = years.firstOrNull { it.label == defaultYearLabel } ?: years.firstOrNull()
        val semester = semesters.firstOrNull { it.academicYearId == year?.id && it.name == defaultSemester }
            ?: semesters.firstOrNull { it.academicYearId == year?.id }
            ?: semesters.firstOrNull()
        return PeriodContextDto(year, semester, years, semesters)
    }

    @Transactional
    fun createYear(req: CreateAcademicYearRequest): AcademicYearDto {
        val saved = yearRepo.save(
            AcademicYear(
                tenantId = TenantContext.get(),
                label = req.label,
                startDate = req.startDate?.let { LocalDate.parse(it) },
                endDate = req.endDate?.let { LocalDate.parse(it) }
            )
        )
        audit("Academic year created", "AcademicYear", saved.id.toString(), saved.label)
        return toYearDto(saved)
    }

    @Transactional
    fun createSemester(req: CreateSemesterRequest): SemesterDto {
        val tenantId = TenantContext.get()
        val year = yearRepo.findById(req.academicYearId).orElseThrow { NoSuchElementException("Academic year not found") }
        if (year.tenantId != tenantId) throw NoSuchElementException("Academic year not found")
        val saved = semesterRepo.save(
            Semester(
                tenantId = tenantId,
                academicYearId = req.academicYearId,
                name = req.name,
                sequenceNo = req.sequenceNo
            )
        )
        audit("Semester created", "Semester", saved.id.toString(), "${year.label} · ${saved.name}")
        return SemesterDto(saved.id, saved.academicYearId, year.label, saved.name, saved.sequenceNo)
    }

    fun listRules(): List<RuleDto> =
        ruleRepo.findByTenantIdOrderByRuleKeyAsc(TenantContext.get()).map {
            RuleDto(it.id, it.ruleKey, it.ruleValue, it.description)
        }

    @Transactional
    fun updateRule(id: UUID, req: UpdateRuleRequest): RuleDto {
        val rule = ruleRepo.findById(id).orElseThrow { NoSuchElementException("Rule not found") }
        if (rule.tenantId != TenantContext.get()) throw NoSuchElementException("Rule not found")
        rule.ruleValue = req.value
        ruleRepo.save(rule)
        audit("Institutional rule updated", "InstitutionalRule", rule.id.toString(), "${rule.ruleKey}=${rule.ruleValue}")
        return RuleDto(rule.id, rule.ruleKey, rule.ruleValue, rule.description)
    }

    fun listSettings(): List<SettingDto> =
        settingRepo.findByTenantIdOrderBySettingKeyAsc(TenantContext.get()).map {
            SettingDto(it.id, it.settingKey, it.settingValue, it.description)
        }

    @Transactional
    fun updateSetting(id: UUID, req: UpdateSettingRequest): SettingDto {
        val setting = settingRepo.findById(id).orElseThrow { NoSuchElementException("Setting not found") }
        if (setting.tenantId != TenantContext.get()) throw NoSuchElementException("Setting not found")
        setting.settingValue = req.value
        settingRepo.save(setting)
        audit("System setting updated", "SystemSetting", setting.id.toString(), "${setting.settingKey}=${setting.settingValue}")
        return SettingDto(setting.id, setting.settingKey, setting.settingValue, setting.description)
    }

    fun listMappingProfiles(): List<MappingProfileDto> =
        mappingRepo.findByTenantIdOrderByNameAsc(TenantContext.get()).map { toMappingDto(it) }

    @Transactional
    fun createMappingProfile(req: CreateMappingProfileRequest): MappingProfileDto {
        val saved = mappingRepo.save(
            ImportMappingProfile(
                tenantId = TenantContext.get(),
                name = req.name,
                entityType = req.entityType.uppercase(),
                columnMap = req.columnMap.entries.joinToString(";") { "${it.key}=${it.value}" },
                fileFormat = req.fileFormat,
                notes = req.notes,
                mappingVersion = 1
            )
        )
        audit("Import mapping profile created", "ImportMappingProfile", saved.id.toString(), saved.name)
        return toMappingDto(saved)
    }

    @Transactional
    fun createUser(req: CreateUserRequest): UserDto {
        val tenantId = TenantContext.get()
        if (userRepo.findByTenantIdAndEmail(tenantId, req.email) != null) {
            throw IllegalArgumentException("User with this email already exists")
        }
        val orgId = req.organizationNodeId ?: orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull()?.id
        val saved = userRepo.save(
            AppUser(
                tenantId = tenantId,
                email = req.email,
                fullName = req.name,
                role = req.role,
                organizationNodeId = orgId,
                passwordHash = "local",
                active = req.active,
                accountStatus = if (req.active) "ACTIVE" else "PENDING"
            )
        )
        if (orgId != null) {
            upsertMembership(tenantId, saved.id, orgId, req.role, primary = true)
        }
        audit("User created", "User", saved.id.toString(), saved.email)
        return UserDto(saved.id, saved.email, saved.fullName, saved.role, saved.active, saved.organizationNodeId)
    }

    fun listMemberships(departmentId: UUID?): List<MembershipDto> {
        val tenantId = TenantContext.get()
        val nodes = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val rows = if (departmentId != null) {
            membershipRepo.findByTenantIdAndOrganizationNodeId(tenantId, departmentId)
        } else {
            membershipRepo.findAll().filter { it.tenantId == tenantId }
        }
        return rows.filter { it.status == "ACTIVE" }.mapNotNull { m ->
            val node = nodes[m.organizationNodeId] ?: return@mapNotNull null
            MembershipDto(m.id, m.organizationNodeId, node.name, node.type, m.role, m.isPrimary)
        }
    }

    /**
     * Assign exactly one ACTIVE DEPARTMENT_CHAIR for a department.
     * Demotes any prior active chair membership for that department.
     */
    @Transactional
    fun assignDepartmentChair(req: AssignChairRequest): MembershipDto {
        val tenantId = TenantContext.get()
        val deptId = resolveInvitationOrgId(tenantId, req.departmentId, req.department)
            ?: throw IllegalArgumentException("Department is required")
        val node = orgRepo.findById(deptId).orElseThrow { NoSuchElementException("Department not found") }
        if (!node.type.equals("Department", true)) {
            throw IllegalArgumentException("Chair must be assigned to a Department node")
        }
        val user = when {
            req.userId != null -> userRepo.findById(req.userId).orElseThrow { NoSuchElementException("User not found") }
            !req.email.isNullOrBlank() -> userRepo.findByTenantIdAndEmail(tenantId, req.email.trim())
                ?: throw NoSuchElementException("User not found")
            else -> throw IllegalArgumentException("userId or email is required")
        }
        if (user.tenantId != tenantId) throw IllegalArgumentException("User belongs to another institution")

        membershipRepo.findByTenantIdAndOrganizationNodeIdAndRoleAndStatus(
            tenantId, deptId, "DEPARTMENT_CHAIR", "ACTIVE"
        ).forEach { existing ->
            if (existing.userId != user.id) {
                existing.status = "INACTIVE"
                membershipRepo.save(existing)
            }
        }

        user.role = "DEPARTMENT_CHAIR"
        user.organizationNodeId = deptId
        userRepo.save(user)
        val membership = upsertMembership(tenantId, user.id, deptId, "DEPARTMENT_CHAIR", primary = true)
        audit("Department chair assigned", "OrganizationMembership", membership.id.toString(), "${user.email} → ${node.name}")
        return MembershipDto(membership.id, deptId, node.name, node.type, membership.role, membership.isPrimary)
    }

    @Transactional
    fun createMembership(req: CreateMembershipRequest): MembershipDto {
        val tenantId = TenantContext.get()
        val user = userRepo.findById(req.userId).orElseThrow { NoSuchElementException("User not found") }
        if (user.tenantId != tenantId) throw IllegalArgumentException("User belongs to another institution")
        val node = orgRepo.findById(req.organizationNodeId).orElseThrow { NoSuchElementException("Organization node not found") }
        if (req.role.equals("DEPARTMENT_CHAIR", true)) {
            return assignDepartmentChair(
                AssignChairRequest(userId = req.userId, departmentId = req.organizationNodeId)
            )
        }
        val saved = upsertMembership(tenantId, user.id, req.organizationNodeId, req.role.uppercase(), req.isPrimary)
        return MembershipDto(saved.id, node.id, node.name, node.type, saved.role, saved.isPrimary)
    }

    private fun upsertMembership(
        tenantId: UUID,
        userId: UUID,
        organizationNodeId: UUID,
        role: String,
        primary: Boolean
    ): OrganizationMembership {
        val existing = membershipRepo.findByTenantIdAndUserId(tenantId, userId)
            .firstOrNull { it.organizationNodeId == organizationNodeId && it.role.equals(role, true) }
        if (primary) {
            membershipRepo.findByTenantIdAndUserId(tenantId, userId).forEach {
                if (it.isPrimary) {
                    it.isPrimary = false
                    membershipRepo.save(it)
                }
            }
        }
        if (existing != null) {
            existing.status = "ACTIVE"
            existing.isPrimary = primary || existing.isPrimary
            existing.role = role.uppercase()
            return membershipRepo.save(existing)
        }
        return membershipRepo.save(
            OrganizationMembership(
                tenantId = tenantId,
                userId = userId,
                organizationNodeId = organizationNodeId,
                role = role.uppercase(),
                status = "ACTIVE",
                isPrimary = primary
            )
        )
    }

    fun listInvitations(): List<InvitationDto> {
        val tenantId = TenantContext.get()
        val nodes = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        return invitationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).map { toInvitationDto(it, nodes[it.organizationNodeId]?.name) }
    }

    @Transactional
    fun createInvitation(req: CreateInvitationRequest): InvitationDto {
        permissionService.require(PermissionCodes.USERS_INVITE)
        val tenantId = TenantContext.get()
        val email = req.email.trim().lowercase()
        val name = req.name.trim()
        require(email.isNotEmpty() && name.isNotEmpty()) { "Name and email are required" }
        if (userRepo.findByTenantIdAndEmail(tenantId, email)?.active == true) {
            throw IllegalArgumentException("An active user with this email already exists")
        }
        invitationRepo.findByTenantIdAndEmailIgnoreCaseAndStatus(tenantId, email, "PENDING")
            .forEach { revokeInvitationEntity(it, "Superseded by a new invitation") }
        val role = req.role.trim().ifBlank { "VIEWER" }.uppercase()
        val orgId = resolveInvitationOrgId(tenantId, req.organizationNodeId, req.organization)
        validateInvitationTarget(tenantId, role, orgId)

        val template = req.permissionTemplate?.trim()?.uppercase().orEmpty()
        val fromTemplate = when {
            template.isBlank() || template == "ROLE_DEFAULTS" || template == "USE_ROLE_DEFAULTS" ->
                PermissionCodes.defaultsForRole(role)
            template == "LECTURER_DEFAULTS" || template == "LECTURER" ->
                PermissionCodes.TEMPLATES.getValue("LECTURER_DEFAULTS")
            PermissionCodes.TEMPLATES.containsKey(template) ->
                PermissionCodes.TEMPLATES.getValue(template)
            else -> PermissionCodes.defaultsForRole(role)
        }
        val overrides = PermissionCodes.normalize(req.permissions ?: emptyList())
        val assigned = if (template == "CUSTOM") overrides else (fromTemplate + overrides)
        val actorRole = UserContext.get()?.role ?: "INSTITUTION_ADMIN"
        permissionService.assertCanDelegate(actorRole, assigned)

        val rawToken = InvitationTokens.generateRawToken()
        val tokenHash = InvitationTokens.hash(rawToken)
        val saved = invitationRepo.save(
            Invitation(
                tenantId = tenantId,
                email = email,
                fullName = name,
                role = role,
                organizationNodeId = orgId,
                token = tokenHash,
                tokenHash = tokenHash,
                status = "PENDING",
                invitedBy = UserContext.get()?.userId,
                expiresAt = Instant.now().plus(invitationExpiryHours, ChronoUnit.HOURS),
                deliveryStatus = EmailDeliveryStatus.QUEUED.name,
                permissionsJson = encodePermissions(assigned),
                permissionTemplate = template.ifBlank { "ROLE_DEFAULTS" }
            )
        )
        val deptName = orgId?.let { oid -> orgRepo.findByTenantIdAndId(tenantId, oid)?.name }
        val send = deliverInvitationEmail(saved, rawToken, deptName)
        applyDeliveryResult(saved, send)
        invitationRepo.save(saved)
        audit("Invitation created", "Invitation", saved.id.toString(), "${saved.email} · ${saved.role} · delivery=${saved.deliveryStatus}")
        log.info(
            "invitation.created id={} tenantId={} role={} delivery={} provider={}",
            saved.id, tenantId, saved.role, saved.deliveryStatus, saved.emailProvider
        )
        val message = when (saved.deliveryStatus) {
            EmailDeliveryStatus.SENT.name -> "Invitation created and email sent."
            EmailDeliveryStatus.FAILED.name -> "Invitation created, but email delivery failed. Copy the link or retry email."
            else -> "Invitation created. Copy the invitation link to share it."
        }
        return toInvitationDto(saved, deptName, rawToken, emailSent = send.success, message = message)
    }

    @Transactional
    fun resendInvitation(id: UUID): InvitationDto {
        permissionService.require(PermissionCodes.USERS_INVITE)
        val tenantId = TenantContext.get()
        val inv = invitationRepo.findByTenantIdAndId(tenantId, id)
            ?: throw NoSuchElementException("Invitation not found")
        if (inv.status != "PENDING") throw IllegalStateException("Only pending invitations can be resent")
        if (inv.expiresAt.isBefore(Instant.now())) {
            inv.status = "EXPIRED"
            invitationRepo.save(inv)
            throw IllegalStateException("This invitation has expired")
        }
        val rawToken = rotateInvitationToken(inv)
        val deptName = inv.organizationNodeId?.let { oid -> orgRepo.findByTenantIdAndId(tenantId, oid)?.name }
        val send = deliverInvitationEmail(inv, rawToken, deptName)
        applyDeliveryResult(inv, send)
        invitationRepo.save(inv)
        audit("Invitation resent", "Invitation", inv.id.toString(), "${inv.email} · delivery=${inv.deliveryStatus}")
        log.info("invitation.resent id={} delivery={}", inv.id, inv.deliveryStatus)
        val message = if (send.success) "Invitation email resent." else "Email delivery failed. Copy the new invitation link."
        return toInvitationDto(inv, deptName, rawToken, emailSent = send.success, message = message)
    }

    @Transactional
    fun rotateInvitationLink(id: UUID): InvitationDto {
        val tenantId = TenantContext.get()
        val inv = invitationRepo.findByTenantIdAndId(tenantId, id)
            ?: throw NoSuchElementException("Invitation not found")
        if (inv.status != "PENDING") throw IllegalStateException("Only pending invitations can issue a link")
        if (inv.expiresAt.isBefore(Instant.now())) {
            inv.status = "EXPIRED"
            invitationRepo.save(inv)
            throw IllegalStateException("This invitation has expired")
        }
        val rawToken = rotateInvitationToken(inv)
        invitationRepo.save(inv)
        audit("Invitation link rotated", "Invitation", inv.id.toString(), inv.email)
        log.info("invitation.link_rotated id={}", inv.id)
        val deptName = inv.organizationNodeId?.let { oid -> orgRepo.findByTenantIdAndId(tenantId, oid)?.name }
        return toInvitationDto(
            inv,
            deptName,
            rawToken,
            emailSent = false,
            message = "New invitation link created. Previous links for this invite no longer work."
        )
    }

    @Transactional
    fun revokeInvitation(id: UUID): InvitationDto {
        permissionService.require(PermissionCodes.USERS_INVITE)
        val tenantId = TenantContext.get()
        val inv = invitationRepo.findByTenantIdAndId(tenantId, id)
            ?: throw NoSuchElementException("Invitation not found")
        if (inv.status != "PENDING") throw IllegalStateException("Only pending invitations can be revoked")
        revokeInvitationEntity(inv, "Revoked by administrator")
        invitationRepo.save(inv)
        audit("Invitation revoked", "Invitation", inv.id.toString(), inv.email)
        log.info("invitation.revoked id={}", inv.id)
        val deptName = inv.organizationNodeId?.let { oid -> orgRepo.findByTenantIdAndId(tenantId, oid)?.name }
        return toInvitationDto(inv, deptName, message = "Invitation revoked.")
    }

    fun previewInvitation(token: String): InvitationPreviewDto {
        val inv = findInvitationByRawToken(token.trim())
            ?: throw NoSuchElementException("Invitation not found")
        val tenant = tenantRepo.findById(inv.tenantId).orElse(null)
        val expired = inv.expiresAt.isBefore(Instant.now()) || inv.status != "PENDING"
        if (inv.status == "PENDING" && inv.expiresAt.isBefore(Instant.now())) {
            inv.status = "EXPIRED"
            invitationRepo.save(inv)
        }
        return InvitationPreviewDto(
            email = inv.email,
            name = inv.fullName,
            role = inv.role,
            institutionName = tenant?.name ?: "Institution",
            status = inv.status,
            expired = expired || inv.status == "EXPIRED"
        )
    }

    @Transactional
    fun acceptInvitation(req: AcceptInvitationRequest): LoginResponse {
        val inv = findInvitationByRawToken(req.token.trim())
            ?: throw NoSuchElementException("Invitation not found")
        if (inv.status == "REVOKED") throw IllegalStateException("This invitation has been revoked")
        if (inv.status == "ACCEPTED") throw IllegalStateException("This invitation was already accepted")
        if (inv.status != "PENDING") throw IllegalStateException("This invitation is no longer valid")
        if (inv.expiresAt.isBefore(Instant.now())) {
            inv.status = "EXPIRED"
            invitationRepo.save(inv)
            log.info("invitation.expired id={}", inv.id)
            throw IllegalStateException("This invitation has expired")
        }

        val clerkIdentity = ClerkContext.get()
        if (authIdentityService.clerkEnabled()) {
            if (clerkIdentity == null) {
                throw IllegalStateException("Sign in with Clerk to accept this invitation")
            }
            if (!clerkIdentity.email.equals(inv.email, ignoreCase = true)) {
                throw IllegalArgumentException(
                    "This invitation was issued to ${inv.email}. You authenticated as ${clerkIdentity.email}. Sign in with the invited email address."
                )
            }
        }

        val previousTenant = runCatching { TenantContext.get() }.getOrNull()
        TenantContext.set(inv.tenantId)
        try {
            val name = req.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: clerkIdentity?.fullName
                ?: inv.fullName
            val isChair = inv.role.equals("DEPARTMENT_CHAIR", ignoreCase = true)
            if (isChair && inv.organizationNodeId == null) {
                throw IllegalArgumentException("Chair invitation is missing department")
            }
            if (isChair) {
                val dept = orgRepo.findByTenantIdAndId(inv.tenantId, inv.organizationNodeId!!)
                    ?: throw IllegalArgumentException("Department for this invitation no longer exists")
                if (!dept.type.equals("Department", ignoreCase = true)) {
                    throw IllegalArgumentException("Chair invitation must point at a Department node")
                }
            }

            val existing = when {
                clerkIdentity != null ->
                    userRepo.findByClerkUserId(clerkIdentity.clerkUserId)
                        ?: userRepo.findByTenantIdAndEmail(inv.tenantId, inv.email)
                else -> userRepo.findByTenantIdAndEmail(inv.tenantId, inv.email)
            }
            val user = if (existing != null) {
                existing.fullName = name
                existing.role = inv.role
                existing.organizationNodeId = inv.organizationNodeId ?: existing.organizationNodeId
                existing.active = true
                existing.accountStatus = "ACTIVE"
                if (clerkIdentity != null) {
                    existing.clerkUserId = clerkIdentity.clerkUserId
                    existing.passwordHash = existing.passwordHash ?: "clerk"
                } else {
                    existing.passwordHash = req.password?.takeIf { it.isNotBlank() } ?: existing.passwordHash ?: "local"
                }
                userRepo.save(existing)
            } else {
                userRepo.save(
                    AppUser(
                        tenantId = inv.tenantId,
                        email = inv.email,
                        fullName = name,
                        role = inv.role,
                        organizationNodeId = inv.organizationNodeId,
                        clerkUserId = clerkIdentity?.clerkUserId,
                        passwordHash = if (clerkIdentity != null) "clerk" else (req.password?.takeIf { it.isNotBlank() } ?: "local"),
                        active = true,
                        accountStatus = "ACTIVE"
                    )
                )
            }
            inv.status = "ACCEPTED"
            inv.acceptedAt = Instant.now()
            // Invalidate token material after acceptance (single-use)
            val burned = InvitationTokens.hash("accepted:${inv.id}:${Instant.now()}")
            inv.token = burned
            inv.tokenHash = burned
            invitationRepo.save(inv)

            if (isChair) {
                val deptId = inv.organizationNodeId!!
                membershipRepo.findByTenantIdAndOrganizationNodeIdAndRoleAndStatus(
                    inv.tenantId, deptId, "DEPARTMENT_CHAIR", "ACTIVE"
                ).forEach { existingChair ->
                    if (existingChair.userId != user.id) {
                        existingChair.status = "INACTIVE"
                        membershipRepo.save(existingChair)
                    }
                }
                user.role = "DEPARTMENT_CHAIR"
                user.organizationNodeId = deptId
                userRepo.save(user)
                upsertMembership(inv.tenantId, user.id, deptId, "DEPARTMENT_CHAIR", primary = true)
            } else {
                user.organizationNodeId?.let { orgId ->
                    upsertMembership(user.tenantId, user.id, orgId, user.role, primary = true)
                }
            }

            val invitePerms = parseInvitationPermissions(inv.permissionsJson)
            if (invitePerms.isNotEmpty()) {
                permissionService.applyInvitationPermissions(
                    tenantId = inv.tenantId,
                    userId = user.id,
                    permissions = invitePerms,
                    grantedBy = inv.invitedBy,
                    organizationNodeId = inv.organizationNodeId
                )
            }

            auditRepo.save(
                AuditLog(
                    tenantId = inv.tenantId,
                    actorId = user.id,
                    action = if (isChair) "Invitation accepted (chair)" else "Invitation accepted",
                    entityType = "Invitation",
                    entityId = inv.id.toString(),
                    details = "${user.email} · perms=${invitePerms.size}"
                )
            )
            log.info("invitation.accepted id={} userId={} role={}", inv.id, user.id, user.role)
            return authIdentityService.toLoginResponse(user)
        } finally {
            if (previousTenant != null) TenantContext.set(previousTenant)
            else TenantContext.clear()
        }
    }

    private fun validateInvitationTarget(tenantId: UUID, role: String, orgId: UUID?) {
        if (role in listOf("DEPARTMENT_CHAIR", "SCHOOL_ADMIN", "SCHOOL_DEAN", "INSTITUTION_ADMIN", "LECTURER", "STUDENT") && orgId == null) {
            throw IllegalArgumentException("Organization is required for $role invitations")
        }
        if (orgId == null) return
        val org = orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == orgId }
            ?: throw IllegalArgumentException("Organization node not found")
        when (role) {
            "DEPARTMENT_CHAIR", "LECTURER", "STUDENT" -> {
                if (!org.type.equals("Department", ignoreCase = true)) {
                    throw IllegalArgumentException(
                        "$role invites must target a Department node (got ${org.type}: ${org.name})"
                    )
                }
            }
            "SCHOOL_ADMIN", "SCHOOL_DEAN" -> {
                if (!org.type.equals("School", ignoreCase = true) &&
                    !org.type.equals("Faculty", ignoreCase = true)
                ) {
                    throw IllegalArgumentException("School Admin invites must target a School or Faculty node")
                }
            }
            "INSTITUTION_ADMIN" -> {
                if (!org.type.equals("University", ignoreCase = true) &&
                    !org.type.equals("College", ignoreCase = true)
                ) {
                    throw IllegalArgumentException("Institution Admin invites should target the University (or College) root")
                }
            }
        }
    }

    private fun findInvitationByRawToken(raw: String): Invitation? {
        if (raw.isBlank()) return null
        val hash = InvitationTokens.hash(raw)
        return invitationRepo.findByTokenHash(hash)
            ?: invitationRepo.findByToken(hash)
            ?: invitationRepo.findByToken(raw) // legacy plaintext tokens
    }

    private fun rotateInvitationToken(inv: Invitation): String {
        val rawToken = InvitationTokens.generateRawToken()
        val tokenHash = InvitationTokens.hash(rawToken)
        inv.token = tokenHash
        inv.tokenHash = tokenHash
        return rawToken
    }

    private fun revokeInvitationEntity(inv: Invitation, reason: String) {
        inv.status = "REVOKED"
        inv.revokedAt = Instant.now()
        inv.deliveryError = reason
        val burned = InvitationTokens.hash("revoked:${inv.id}:${Instant.now()}")
        inv.token = burned
        inv.tokenHash = burned
    }

    private fun deliverInvitationEmail(
        inv: Invitation,
        rawToken: String,
        departmentName: String?
    ): com.academicflow.service.email.EmailSendResult {
        val tenant = tenantRepo.findById(inv.tenantId).orElse(null)
        val acceptUrl = "${appBaseUrl.trimEnd('/')}/invite/$rawToken"
        val expiresLabel = DateTimeFormatter.ISO_LOCAL_DATE
            .withZone(ZoneOffset.UTC)
            .format(inv.expiresAt)
        val invitedByName = inv.invitedBy?.let { uid ->
            userRepo.findById(uid).orElse(null)?.fullName
        }
        val template = InvitationEmailTemplates.invitation(
            institutionName = tenant?.name ?: "your institution",
            departmentName = departmentName,
            roleLabel = InvitationEmailTemplates.roleLabel(inv.role),
            inviteeName = inv.fullName,
            acceptUrl = acceptUrl,
            expiresLabel = expiresLabel,
            invitedByName = invitedByName
        )
        val message = EmailMessage(
            to = inv.email,
            subject = template.subject,
            htmlBody = template.htmlBody,
            textBody = template.textBody,
            tags = template.tags + mapOf("invitationId" to inv.id.toString())
        )
        return try {
            emailService.send(message)
        } catch (e: Exception) {
            log.warn("invitation.send_failed id={} error={}", inv.id, e.message)
            com.academicflow.service.email.EmailSendResult(
                provider = emailService.providerId,
                success = false,
                error = e.message ?: "Email send failed"
            )
        }
    }

    private fun applyDeliveryResult(inv: Invitation, send: com.academicflow.service.email.EmailSendResult) {
        inv.emailProvider = send.provider
        inv.lastSentAt = Instant.now()
        if (send.success) {
            inv.deliveryStatus = EmailDeliveryStatus.SENT.name
            inv.deliveryError = null
            log.info("invitation.sent id={} provider={}", inv.id, send.provider)
        } else {
            inv.deliveryStatus = EmailDeliveryStatus.FAILED.name
            inv.deliveryError = send.error
            log.warn("invitation.send_failed id={} provider={} error={}", inv.id, send.provider, send.error)
        }
    }

    private fun toInvitationDto(
        i: Invitation,
        organizationName: String? = null,
        rawToken: String? = null,
        emailSent: Boolean? = null,
        message: String? = null
    ) = InvitationDto(
        id = i.id,
        email = i.email,
        name = i.fullName,
        role = i.role,
        organizationNodeId = i.organizationNodeId,
        organizationName = organizationName,
        status = i.status,
        token = rawToken,
        invitePath = rawToken?.let { "/invite/$it" },
        createdAt = i.createdAt.toString(),
        expiresAt = i.expiresAt.toString(),
        deliveryStatus = i.deliveryStatus,
        deliveryError = i.deliveryError,
        emailProvider = i.emailProvider,
        lastSentAt = i.lastSentAt?.toString(),
        emailSent = emailSent,
        message = message,
        permissions = parseInvitationPermissions(i.permissionsJson),
        permissionTemplate = i.permissionTemplate
    )

    private fun parseInvitationPermissions(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val codes = Regex("\"([^\"]+)\"").findAll(raw).map { it.groupValues[1] }.toList()
        return PermissionCodes.normalize(codes).sorted().toList()
    }

    private fun encodePermissions(codes: Collection<String>): String =
        PermissionCodes.normalize(codes).sorted().joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

    @Transactional
    fun updateUser(id: UUID, req: UpdateUserRequest): UserDto {
        val user = userRepo.findById(id).orElseThrow { NoSuchElementException("User not found") }
        if (user.tenantId != TenantContext.get()) throw NoSuchElementException("User not found")
        req.role?.let { user.role = it }
        req.active?.let { user.active = it }
        req.name?.let { user.fullName = it }
        req.organizationNodeId?.let { user.organizationNodeId = it }
        userRepo.save(user)
        audit("User updated", "User", user.id.toString(), "${user.email} · ${user.role}")
        return UserDto(user.id, user.email, user.fullName, user.role, user.active, user.organizationNodeId)
    }

    private fun resolveInvitationOrgId(tenantId: UUID, id: UUID?, name: String?): UUID? {
        if (id != null) {
            orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == id }?.let { return it.id }
        }
        val raw = name?.trim().orEmpty()
        if (raw.isBlank()) return null
        runCatching { UUID.fromString(raw) }.getOrNull()?.let { uuid ->
            orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == uuid }?.let { return it.id }
        }
        return orgRepo.findByTenantIdOrderByNameAsc(tenantId)
            .firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    "${it.name} (${it.type})".equals(raw, ignoreCase = true)
            }
            ?.id
    }

    private fun toYearDto(y: AcademicYear) = AcademicYearDto(
        y.id, y.label, y.startDate?.toString(), y.endDate?.toString()
    )

    private fun toMappingDto(p: ImportMappingProfile) = MappingProfileDto(
        id = p.id,
        name = p.name,
        entityType = p.entityType,
        columnMap = p.columnMap.split(";").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap(),
        mappingVersion = p.mappingVersion,
        fileFormat = p.fileFormat,
        notes = p.notes
    )

    private fun audit(action: String, entityType: String, entityId: String?, details: String?) {
        auditRepo.save(
            AuditLog(
                tenantId = TenantContext.get(),
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details
            )
        )
    }
}
