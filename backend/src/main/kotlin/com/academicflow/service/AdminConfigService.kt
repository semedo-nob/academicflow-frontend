package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.dto.*
import com.academicflow.entity.*
import com.academicflow.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
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
    private val auditRepo: AuditLogRepository
) {
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
                entityType = req.entityType,
                columnMap = req.columnMap.entries.joinToString(";") { "${it.key}=${it.value}" }
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
                active = req.active
            )
        )
        audit("User created", "User", saved.id.toString(), saved.email)
        return UserDto(saved.id, saved.email, saved.fullName, saved.role, saved.active, saved.organizationNodeId)
    }

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
        }.toMap()
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
