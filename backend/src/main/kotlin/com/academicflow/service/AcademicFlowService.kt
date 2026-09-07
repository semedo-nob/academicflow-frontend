package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.dto.*
import com.academicflow.entity.*
import com.academicflow.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class AcademicFlowService(
    private val orgRepo: OrganizationNodeRepository,
    private val lecturerRepo: LecturerRepository,
    private val expertiseRepo: LecturerExpertiseRepository,
    private val unitRepo: AcademicUnitRepository,
    private val requestRepo: TeachingRequestRepository,
    private val allocationRepo: AllocationRepository,
    private val approvalRepo: ApprovalRepository,
    private val conflictRepo: ConflictRepository,
    private val auditRepo: AuditLogRepository,
    private val userRepo: AppUserRepository,
    private val tenantRepo: TenantRepository,
    private val importRepo: ImportSessionRepository,
    private val stagingRepo: ImportStagingRowRepository,
    private val matchingService: MatchingService,
    private val timetableRepo: TimetableEntryRepository,
    private val securityEventRepo: SecurityEventRepository,
    private val platformSettingRepo: PlatformSettingRepository
) {

    fun login(req: LoginRequest): LoginResponse {
        val email = req.email.trim()
        if (platformSettingRepo.findBySettingKey("maintenance_mode")?.settingValue.equals("true", true) == true) {
            val isSuper = userRepo.findByEmailIgnoreCase(email).any { it.role.equals("SUPER_ADMIN", true) }
            if (!isSuper) {
                throw IllegalStateException("AcademicFlow is in maintenance mode. Only platform operators can sign in.")
            }
        }
        val all = userRepo.findByEmailIgnoreCase(email)
        val matches = all.filter {
            it.active && it.accountStatus !in listOf("SUSPENDED", "LOCKED", "DEACTIVATED")
        }
        val user = matches.firstOrNull { it.tenantId == TenantContext.get() }
            ?: matches.firstOrNull()
        if (user == null) {
            val inactive = all.firstOrNull()
            securityEventRepo.save(
                SecurityEvent(
                    severity = "MEDIUM",
                    eventType = "FAILED_LOGIN",
                    email = email,
                    tenantId = inactive?.tenantId,
                    details = "Login failed"
                )
            )
            if (inactive != null) {
                inactive.failedLoginCount += 1
                if (inactive.failedLoginCount >= 5) {
                    inactive.accountStatus = "LOCKED"
                    inactive.active = false
                    securityEventRepo.save(
                        SecurityEvent(
                            severity = "HIGH",
                            eventType = "ACCOUNT_LOCKED",
                            email = email,
                            tenantId = inactive.tenantId,
                            details = "Locked after repeated failed logins"
                        )
                    )
                }
                userRepo.save(inactive)
                val tenant = tenantRepo.findById(inactive.tenantId).orElse(null)
                when (tenant?.status) {
                    "PENDING" -> throw IllegalStateException(
                        "Your institution signup is pending approval. You can sign in once it is approved."
                    )
                    "REJECTED" -> throw IllegalStateException(
                        "This institution account was not approved. Contact the platform administrator."
                    )
                    "SUSPENDED", "ARCHIVED" -> throw IllegalStateException(
                        "This institution has been suspended. Contact the platform administrator."
                    )
                }
                if (inactive.accountStatus in listOf("SUSPENDED", "LOCKED", "DEACTIVATED")) {
                    throw IllegalStateException("This account is ${inactive.accountStatus.lowercase()}. Contact support.")
                }
            }
            throw NoSuchElementException("Invalid email or password")
        }
        val tenant = tenantRepo.findById(user.tenantId).orElse(null)
            ?: throw NoSuchElementException("Institution not found")
        when (tenant.status) {
            "PENDING" -> throw IllegalStateException(
                "Your institution signup is pending approval. You can sign in once it is approved."
            )
            "REJECTED" -> throw IllegalStateException(
                "This institution account was not approved. Contact the platform administrator."
            )
            "SUSPENDED", "ARCHIVED" -> throw IllegalStateException(
                "This institution has been suspended. Contact the platform administrator."
            )
        }
        user.lastLoginAt = Instant.now()
        user.failedLoginCount = 0
        userRepo.save(user)
        tenant.lastActivityAt = Instant.now()
        tenantRepo.save(tenant)
        return LoginResponse(user.email, user.fullName, user.role, user.tenantId)
    }

    fun organizationTree(): List<OrganizationNodeDto> {
        val tenantId = TenantContext.get()
        val nodes = orgRepo.findByTenantIdOrderByNameAsc(tenantId)
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId)
        return nodes.map { n ->
            OrganizationNodeDto(
                id = n.id,
                tenantId = n.tenantId,
                name = n.name,
                type = n.type,
                parentId = n.parentId,
                lecturerCount = lecturers.count { it.organizationNodeId == n.id },
                unitCount = units.count { it.sourceDepartmentId == n.id }
            )
        }
    }

    @Transactional
    fun createOrgNode(req: CreateOrgNodeRequest): OrganizationNodeDto {
        val tenantId = TenantContext.get()
        val node = orgRepo.save(
            OrganizationNode(tenantId = tenantId, name = req.name, type = req.type, parentId = req.parentId)
        )
        audit("Organization node created", "OrganizationNode", node.id.toString(), req.name)
        return OrganizationNodeDto(node.id, node.tenantId, node.name, node.type, node.parentId)
    }

    fun listLecturers(): List<LecturerDto> {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val expertise = expertiseRepo.findByTenantId(tenantId).groupBy { it.lecturerId }
        return lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).map { l ->
            val exp = expertise[l.id].orEmpty()
            LecturerDto(
                id = l.id,
                staffNumber = l.staffNumber,
                name = l.fullName,
                email = l.email,
                departmentId = l.organizationNodeId,
                department = orgs[l.organizationNodeId]?.name ?: "",
                qualifications = l.qualifications,
                currentWorkload = l.currentWorkload,
                maximumWorkload = l.maximumWorkload,
                status = l.status,
                availability = l.availabilityText,
                expertise = exp.map { it.subject },
                expertiseDetail = exp.map { ExpertiseDto(it.subject, it.level) }
            )
        }
    }

    @Transactional
    fun createLecturer(req: CreateLecturerRequest): LecturerDto {
        val tenantId = TenantContext.get()
        val saved = lecturerRepo.save(
            Lecturer(
                tenantId = tenantId,
                staffNumber = req.staffNumber,
                fullName = req.name,
                email = req.email,
                organizationNodeId = req.departmentId,
                qualifications = req.qualifications,
                maximumWorkload = req.maximumWorkload,
                availabilityText = req.availability
            )
        )
        req.expertise.forEach {
            expertiseRepo.save(
                LecturerExpertise(tenantId = tenantId, lecturerId = saved.id, subject = it.subject, level = it.level)
            )
        }
        audit("Lecturer created", "Lecturer", saved.id.toString(), saved.fullName)
        return listLecturers().first { it.id == saved.id }
    }

    fun listUnits(): List<AcademicUnitDto> {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val allocations = allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .filter { it.lecturerId != null }
            .associateBy { it.academicUnitId }
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        return unitRepo.findByTenantIdOrderByCodeAsc(tenantId).map { u ->
            val alloc = allocations[u.id]
            AcademicUnitDto(
                id = u.id,
                code = u.code,
                name = u.name,
                sourceDepartmentId = u.sourceDepartmentId,
                sourceDepartment = orgs[u.sourceDepartmentId]?.name ?: "",
                contactHours = u.contactHours,
                studentCount = u.studentCount,
                requiredExpertise = u.requiredExpertise?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                status = u.status,
                lecturerName = alloc?.lecturerId?.let { lecturers[it]?.fullName }
            )
        }
    }

    @Transactional
    fun createUnit(req: CreateUnitRequest): AcademicUnitDto {
        val tenantId = TenantContext.get()
        val saved = unitRepo.save(
            AcademicUnit(
                tenantId = tenantId,
                code = req.code,
                name = req.name,
                sourceDepartmentId = req.sourceDepartmentId,
                contactHours = req.contactHours,
                studentCount = req.studentCount,
                requiredExpertise = req.requiredExpertise.joinToString(",")
            )
        )
        audit("Academic unit created", "AcademicUnit", saved.id.toString(), "${saved.code} ${saved.name}")
        return listUnits().first { it.id == saved.id }
    }

    fun listRequests(): List<TeachingRequestDto> = mapRequests(requestRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.get()))

    private fun mapRequests(requests: List<TeachingRequest>): List<TeachingRequestDto> {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        return requests.map { r ->
            val unit = units[r.academicUnitId]
            TeachingRequestDto(
                id = r.id,
                requestingDepartment = orgs[r.requestingDepartmentId]?.name ?: "",
                requestingDepartmentId = r.requestingDepartmentId,
                sourceDepartment = r.preferredDepartmentId?.let { orgs[it]?.name },
                preferredDepartmentId = r.preferredDepartmentId,
                academicUnitId = r.academicUnitId,
                academicUnit = unit?.let { "${it.code} — ${it.name}" } ?: "",
                studentCount = r.studentCount,
                contactHours = r.contactHours,
                requiredExpertise = r.requiredExpertise,
                status = r.status,
                createdAt = r.createdAt
            )
        }
    }

    @Transactional
    fun createRequest(req: CreateTeachingRequest): TeachingRequestDto {
        val tenantId = TenantContext.get()
        val saved = requestRepo.save(
            TeachingRequest(
                tenantId = tenantId,
                requestingDepartmentId = req.requestingDepartmentId,
                preferredDepartmentId = req.preferredDepartmentId,
                academicUnitId = req.academicUnitId,
                studentCount = req.studentCount,
                contactHours = req.contactHours,
                requiredExpertise = req.requiredExpertise,
                status = "PENDING"
            )
        )
        val unit = unitRepo.findByTenantIdAndId(tenantId, req.academicUnitId)
        if (unit != null && unit.status == "Unallocated") {
            unit.status = "Pending"
            unitRepo.save(unit)
        }
        audit("Request submitted", "TeachingRequest", saved.id.toString(), "Cross-department teaching request")
        return listRequests().first { it.id == saved.id }
    }

    fun getCandidates(requestId: UUID): List<com.academicflow.dto.CandidateDto> {
        val existing = matchingService.getCandidates(requestId)
        return if (existing.isEmpty()) matchingService.findCandidates(requestId) else existing
    }

    fun findCandidates(requestId: UUID) = matchingService.findCandidates(requestId)

    fun listAllocations(): List<AllocationDto> {
        val tenantId = TenantContext.get()
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        return allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).map { a ->
            val unit = units[a.academicUnitId]
            AllocationDto(
                id = a.id,
                academicUnitId = a.academicUnitId,
                unitCode = unit?.code ?: "",
                unitName = unit?.name ?: "",
                lecturerId = a.lecturerId,
                lecturerName = a.lecturerId?.let { lecturers[it]?.fullName },
                matchScore = a.matchScore,
                status = a.status,
                overrideReason = a.overrideReason,
                teachingRequestId = a.teachingRequestId
            )
        }
    }

    @Transactional
    fun createAllocation(req: CreateAllocationRequest): AllocationDto {
        val tenantId = TenantContext.get()
        if (req.recommendedLecturerId != null && req.recommendedLecturerId != req.lecturerId && req.overrideReason.isNullOrBlank()) {
            throw IllegalArgumentException("Override reason required when selecting a lecturer other than the recommendation")
        }

        // Replace any prior non-published allocation for this unit
        allocationRepo.findByTenantIdAndAcademicUnitId(tenantId, req.academicUnitId)
            .filter { it.status !in listOf("PUBLISHED", "APPROVED") }
            .forEach {
                it.status = "CANCELLED"
                it.updatedAt = Instant.now()
                allocationRepo.save(it)
            }

        val conflicts = detectConflicts(req.lecturerId, req.academicUnitId)
        val blocking = conflicts.any { it.severity == "high" }
        val status = if (blocking) "CONFLICT" else "ASSIGNED"

        val saved = allocationRepo.save(
            Allocation(
                tenantId = tenantId,
                academicUnitId = req.academicUnitId,
                lecturerId = req.lecturerId,
                teachingRequestId = req.teachingRequestId,
                matchScore = req.matchScore,
                status = status,
                overrideReason = req.overrideReason
            )
        )

        val unit = unitRepo.findByTenantIdAndId(tenantId, req.academicUnitId)
        if (unit != null) {
            unit.status = if (status == "CONFLICT") "Conflict" else "Assigned"
            unitRepo.save(unit)
        }

        req.teachingRequestId?.let { rid ->
            requestRepo.findByTenantIdAndId(tenantId, rid)?.let {
                it.status = if (status == "CONFLICT") "CONFLICT" else "ASSIGNED"
                requestRepo.save(it)
            }
        }

        val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, req.lecturerId)
        if (lecturer != null && unit != null && status == "ASSIGNED") {
            lecturer.currentWorkload = lecturer.currentWorkload.add(unit.contactHours)
            lecturerRepo.save(lecturer)
        }

        conflicts.forEach {
            it.allocationId = saved.id
            conflictRepo.save(it)
        }

        audit(
            "Lecturer assigned",
            "Allocation",
            saved.id.toString(),
            req.overrideReason?.let { "Manual override: $it" } ?: "System-supported assignment"
        )

        return listAllocations().first { it.id == saved.id }
    }

    private fun detectConflicts(lecturerId: UUID, unitId: UUID): List<Conflict> {
        val tenantId = TenantContext.get()
        val found = mutableListOf<Conflict>()
        val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, lecturerId) ?: return found
        val unit = unitRepo.findByTenantIdAndId(tenantId, unitId) ?: return found

        if (lecturer.currentWorkload.add(unit.contactHours) > lecturer.maximumWorkload) {
            found += Conflict(
                tenantId = tenantId,
                category = "Workload",
                severity = "med",
                description = "${lecturer.fullName} would exceed maximum workload (${lecturer.currentWorkload}+${unit.contactHours}/${lecturer.maximumWorkload}).",
                relatedEntity = lecturer.fullName
            )
        }

        val slots = timetableRepo.findByTenantIdAndLecturerId(tenantId, lecturerId)
        val hasCollision = slots.groupBy { it.dayOfWeek }.values.any { daySlots ->
            daySlots.size > 1 && daySlots.any { a ->
                daySlots.any { b -> a.id != b.id && a.startTime < b.endTime && b.startTime < a.endTime }
            }
        }
        if (hasCollision) {
            found += Conflict(
                tenantId = tenantId,
                category = "Timetable",
                severity = "high",
                description = "Timetable collision detected for ${lecturer.fullName}.",
                relatedEntity = lecturer.fullName
            )
        }

        val publishedDuplicate = allocationRepo.findByTenantIdAndAcademicUnitId(tenantId, unitId)
            .any { it.status in listOf("PUBLISHED", "APPROVED", "AWAITING_APPROVAL") }
        if (publishedDuplicate) {
            found += Conflict(
                tenantId = tenantId,
                category = "Duplicate",
                severity = "high",
                description = "Unit ${unit.code} already has an approved or published allocation.",
                relatedEntity = unit.code
            )
        }

        return found
    }

    @Transactional
    fun submitForApproval(allocationId: UUID): ApprovalDto {
        val tenantId = TenantContext.get()
        val allocation = allocationRepo.findByTenantIdAndId(tenantId, allocationId)
            ?: throw NoSuchElementException("Allocation not found")
        val openHigh = conflictRepo.findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(tenantId)
            .any { it.allocationId == allocationId && it.severity == "high" }
        if (allocation.status == "CONFLICT" && openHigh) {
            throw IllegalStateException("Resolve high-severity conflicts before submitting for approval")
        }
        allocation.status = "AWAITING_APPROVAL"
        allocation.updatedAt = Instant.now()
        allocationRepo.save(allocation)

        unitRepo.findByTenantIdAndId(tenantId, allocation.academicUnitId)?.let {
            it.status = "Awaiting Approval"
            unitRepo.save(it)
        }

        val approval = approvalRepo.save(
            Approval(tenantId = tenantId, allocationId = allocationId, status = "PENDING")
        )
        audit("Allocation submitted for approval", "Allocation", allocationId.toString(), null)
        return listApprovals().first { it.id == approval.id }
    }

    @Transactional
    fun approveAllocation(allocationId: UUID, approve: Boolean, note: String?): AllocationDto {
        val tenantId = TenantContext.get()
        val allocation = allocationRepo.findByTenantIdAndId(tenantId, allocationId)
            ?: throw NoSuchElementException("Allocation not found")
        val approvals = approvalRepo.findByTenantIdAndAllocationId(tenantId, allocationId)
        val approval = approvals.maxByOrNull { it.submittedAt }
            ?: throw NoSuchElementException("No approval record")

        if (approve) {
            approval.status = "APPROVED"
            approval.decidedAt = Instant.now()
            approval.decisionNote = note
            approvalRepo.save(approval)
            allocation.status = "APPROVED"
            allocationRepo.save(allocation)
            // Publish immediately after approval for demo simplicity of the workflow
            allocation.status = "PUBLISHED"
            allocation.updatedAt = Instant.now()
            allocationRepo.save(allocation)
            unitRepo.findByTenantIdAndId(tenantId, allocation.academicUnitId)?.let {
                it.status = "Published"
                unitRepo.save(it)
            }
            allocation.teachingRequestId?.let { rid ->
                requestRepo.findByTenantIdAndId(tenantId, rid)?.let {
                    it.status = "PUBLISHED"
                    requestRepo.save(it)
                }
            }
            ensureTimetableSlot(allocation)
            audit("Allocation approved and published", "Allocation", allocationId.toString(), note)
        } else {
            approval.status = "REJECTED"
            approval.decidedAt = Instant.now()
            approval.decisionNote = note
            approvalRepo.save(approval)
            allocation.status = "CANCELLED"
            allocationRepo.save(allocation)
            audit("Allocation rejected", "Allocation", allocationId.toString(), note)
        }
        return listAllocations().first { it.id == allocationId }
    }

    private fun ensureTimetableSlot(allocation: Allocation) {
        val tenantId = TenantContext.get()
        val lecturerId = allocation.lecturerId ?: return
        val existing = timetableRepo.findByTenantId(tenantId).any {
            it.allocationId == allocation.id ||
                (it.academicUnitId == allocation.academicUnitId && it.lecturerId == lecturerId)
        }
        if (existing) return

        val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, lecturerId)
        val day = when {
            lecturer?.availabilityText?.contains("Monday", true) == true -> 1
            lecturer?.availabilityText?.contains("Tuesday", true) == true -> 2
            lecturer?.availabilityText?.contains("Wednesday", true) == true -> 3
            lecturer?.availabilityText?.contains("Thursday", true) == true -> 4
            lecturer?.availabilityText?.contains("Friday", true) == true -> 5
            else -> 3
        }
        val unit = unitRepo.findByTenantIdAndId(tenantId, allocation.academicUnitId)
        val hours = unit?.contactHours?.toInt()?.coerceIn(1, 3) ?: 2
        val start = java.time.LocalTime.of(10, 0)
        val end = start.plusHours(hours.toLong())
        timetableRepo.save(
            TimetableEntry(
                tenantId = tenantId,
                allocationId = allocation.id,
                lecturerId = lecturerId,
                academicUnitId = allocation.academicUnitId,
                dayOfWeek = day,
                startTime = start,
                endTime = end,
                room = "TBA"
            )
        )
        audit(
            "Timetable slot created on publish",
            "TimetableEntry",
            allocation.id.toString(),
            "Day $day $start-$end"
        )
    }

    fun listApprovals(): List<ApprovalDto> {
        val tenantId = TenantContext.get()
        val allocations = allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).associateBy { it.id }
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        val conflicts = conflictRepo.findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(tenantId)
        return approvalRepo.findByTenantIdOrderBySubmittedAtDesc(tenantId).map { a ->
            val alloc = allocations[a.allocationId]
            val unit = alloc?.let { units[it.academicUnitId] }
            ApprovalDto(
                id = a.id,
                allocationId = a.allocationId,
                unit = unit?.let { "${it.code} — ${it.name}" } ?: "",
                lecturer = alloc?.lecturerId?.let { lecturers[it]?.fullName },
                status = a.status,
                conflicts = conflicts.count { it.allocationId == a.allocationId },
                submittedAt = a.submittedAt
            )
        }
    }

    fun listConflicts(): List<ConflictDto> =
        conflictRepo.findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(TenantContext.get()).map {
            ConflictDto(it.id, it.category, it.severity, it.description, it.relatedEntity, it.allocationId)
        }

    fun dashboard(): DashboardDto {
        val tenantId = TenantContext.get()
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId)
        val allocations = allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
        val requests = requestRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
        val conflicts = conflictRepo.findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(tenantId)
        val publishedOrAssigned = allocations.count { it.status in listOf("PUBLISHED", "APPROVED", "ASSIGNED", "AWAITING_APPROVAL") }
        val pending = units.count { it.status in listOf("Unallocated", "Pending", "Recommended") }.toLong()
        val cross = requests.count {
            it.preferredDepartmentId != null && it.preferredDepartmentId != it.requestingDepartmentId
        }.toLong()
        val completion = if (units.isEmpty()) 0 else ((publishedOrAssigned.toDouble() / units.size) * 100).toInt()
        return DashboardDto(
            totalLecturers = lecturers.size.toLong(),
            academicUnits = units.size.toLong(),
            allocated = publishedOrAssigned.toLong(),
            pending = pending,
            conflicts = conflicts.size.toLong(),
            crossDeptRequests = cross,
            completionPct = completion,
            recentRequests = mapRequests(requests.take(5)),
            recentActivity = auditRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).take(8).map {
                AuditLogDto(it.id, it.action, it.entityType, it.entityId, it.details, it.createdAt)
            }
        )
    }

    fun auditLogs(): List<AuditLogDto> =
        auditRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.get()).map {
            AuditLogDto(it.id, it.action, it.entityType, it.entityId, it.details, it.createdAt)
        }

    @Transactional
    fun createImportSession(req: CreateImportSessionRequest): ImportSessionDto {
        val tenantId = TenantContext.get()
        val saved = importRepo.save(
            ImportSession(
                tenantId = tenantId,
                fileName = req.fileName,
                entityType = req.entityType,
                status = "MAP",
                columnMap = req.columnMap.entries.joinToString(";") { "${it.key}=${it.value}" }
            )
        )
        val rows = if (req.rows.isNotEmpty()) req.rows else defaultImportRows(req.entityType)
        rows.forEachIndexed { index, row ->
            stagingRepo.save(
                ImportStagingRow(
                    sessionId = saved.id,
                    rowNumber = index + 1,
                    rawJson = row.entries.joinToString(";") { "${it.key}=${it.value}" },
                    valid = true
                )
            )
        }
        audit("Import session created", "ImportSession", saved.id.toString(), "${req.fileName} · ${rows.size} rows")
        return ImportSessionDto(saved.id, saved.fileName, saved.entityType, saved.status, saved.columnMap, saved.createdAt)
    }

    fun listImportSessions(): List<ImportSessionDto> =
        importRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.get()).map {
            ImportSessionDto(it.id, it.fileName, it.entityType, it.status, it.columnMap, it.createdAt)
        }

    fun listImportRows(sessionId: UUID): List<Map<String, String>> {
        val session = importRepo.findById(sessionId).orElseThrow { NoSuchElementException("Import session not found") }
        if (session.tenantId != TenantContext.get()) throw NoSuchElementException("Import session not found")
        return stagingRepo.findBySessionIdOrderByRowNumberAsc(sessionId).map { row ->
            row.rawJson.split(";").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap() + mapOf("_row" to row.rowNumber.toString(), "_valid" to row.valid.toString())
        }
    }

    @Transactional
    fun advanceImportSession(id: UUID): ImportSessionDto {
        val tenantId = TenantContext.get()
        val session = importRepo.findById(id).orElseThrow { NoSuchElementException("Import session not found") }
        if (session.tenantId != tenantId) throw NoSuchElementException("Import session not found")
        val next = when (session.status.uppercase()) {
            "MAP" -> "VALIDATE"
            "VALIDATE" -> "PREVIEW"
            "PREVIEW" -> "IMPORTED"
            "IMPORTED" -> "IMPORTED"
            else -> "VALIDATE"
        }
        if (next == "VALIDATE") {
            stagingRepo.findBySessionIdOrderByRowNumberAsc(id).forEach { row ->
                val map = row.rawJson.split(";").mapNotNull {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap()
                val errors = mutableListOf<String>()
                if (session.entityType.equals("LECTURER", true)) {
                    if (map["name"].isNullOrBlank() && map["Staff Name"].isNullOrBlank()) errors += "Missing name"
                    if (map["staffNumber"].isNullOrBlank() && map["Staff No"].isNullOrBlank()) errors += "Missing staff number"
                }
                row.valid = errors.isEmpty()
                row.errors = errors.joinToString("; ").ifBlank { null }
                stagingRepo.save(row)
            }
        }
        if (next == "IMPORTED" && session.status.uppercase() == "PREVIEW") {
            commitImport(session)
        }
        session.status = next
        importRepo.save(session)
        audit("Import session advanced", "ImportSession", session.id.toString(), "Status → ${session.status}")
        return ImportSessionDto(session.id, session.fileName, session.entityType, session.status, session.columnMap, session.createdAt)
    }

    private fun commitImport(session: ImportSession) {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId)
        val defaultOrg = orgs.firstOrNull { it.type.equals("Department", true) } ?: orgs.firstOrNull()
            ?: throw IllegalStateException("No organization node available for import")
        val columnMap = (session.columnMap ?: "").split(";").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

        stagingRepo.findBySessionIdOrderByRowNumberAsc(session.id).filter { it.valid }.forEach { row ->
            val raw = row.rawJson.split(";").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            fun field(canonical: String, vararg aliases: String): String? {
                columnMap.entries.firstOrNull { it.value == canonical }?.key?.let { src ->
                    raw[src]?.takeIf { it.isNotBlank() }?.let { return it }
                }
                (listOf(canonical) + aliases.toList()).forEach { key ->
                    raw[key]?.takeIf { it.isNotBlank() }?.let { return it }
                }
                return null
            }

            when (session.entityType.uppercase()) {
                "LECTURER" -> {
                    val name = field("name", "Staff Name") ?: return@forEach
                    val staff = field("staffNumber", "Staff No") ?: "IMP/${row.rowNumber}"
                    val email = field("email", "Email") ?: "${staff.lowercase().replace("/", ".")}@uonbi.ac.ke"
                    val deptName = field("organizationNode", "Department")
                    val org = deptName?.let { d -> orgs.firstOrNull { it.name.equals(d, true) } } ?: defaultOrg
                    if (lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).none { it.email.equals(email, true) }) {
                        lecturerRepo.save(
                            Lecturer(
                                tenantId = tenantId,
                                staffNumber = staff,
                                fullName = name,
                                email = email,
                                organizationNodeId = org.id,
                                maximumWorkload = field("maximumWorkload", "Max Hours", "Teaching Hours")?.toBigDecimalOrNull()
                                    ?: BigDecimal("12"),
                                availabilityText = "Monday–Friday",
                                status = "Active"
                            )
                        )
                    }
                }
                "ACADEMIC_UNIT" -> {
                    val code = field("code", "Course Code") ?: return@forEach
                    val name = field("name", "Course Name") ?: code
                    if (unitRepo.findByTenantIdOrderByCodeAsc(tenantId).none { it.code.equals(code, true) }) {
                        unitRepo.save(
                            AcademicUnit(
                                tenantId = tenantId,
                                code = code,
                                name = name,
                                sourceDepartmentId = defaultOrg.id,
                                contactHours = field("contactHours", "Hours", "Teaching Hours")?.toBigDecimalOrNull()
                                    ?: BigDecimal("3"),
                                studentCount = field("studentCount", "Students")?.toIntOrNull() ?: 40,
                                requiredExpertise = field("requiredExpertise") ?: "General",
                                status = "Unallocated"
                            )
                        )
                    }
                }
            }
        }
        audit("Import committed", "ImportSession", session.id.toString(), session.entityType)
    }

    private fun defaultImportRows(entityType: String): List<Map<String, String>> =
        if (entityType.equals("ACADEMIC_UNIT", true)) {
            listOf(
                mapOf(
                    "Course Code" to "CSC 499",
                    "Course Name" to "Special Topics",
                    "Department" to "Computer Science",
                    "Teaching Hours" to "3",
                    "Students" to "45"
                )
            )
        } else {
            listOf(
                mapOf(
                    "Staff No" to "IMP/1001",
                    "Staff Name" to "Dr. Imported Lecturer",
                    "Department" to "Mathematics",
                    "Email" to "imported.lecturer@uonbi.ac.ke",
                    "Teaching Hours" to "12"
                )
            )
        }

    fun listUsers(): List<UserDto> =
        userRepo.findByTenantIdOrderByFullNameAsc(TenantContext.get()).map {
            UserDto(it.id, it.email, it.fullName, it.role, it.active, it.organizationNodeId)
        }

    fun listTimetable(): List<TimetableEntryDto> {
        val tenantId = TenantContext.get()
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val entries = timetableRepo.findByTenantId(tenantId)
        return entries.map { e ->
            val daySlots = entries.filter { it.dayOfWeek == e.dayOfWeek && it.lecturerId == e.lecturerId }
            val conflict = daySlots.any { other ->
                other.id != e.id && e.startTime < other.endTime && other.startTime < e.endTime
            }
            TimetableEntryDto(
                id = e.id,
                allocationId = e.allocationId,
                lecturerId = e.lecturerId,
                lecturerName = e.lecturerId?.let { lecturers[it]?.fullName },
                academicUnitId = e.academicUnitId,
                unitCode = e.academicUnitId?.let { units[it]?.code },
                unitName = e.academicUnitId?.let { units[it]?.name },
                dayOfWeek = e.dayOfWeek,
                startTime = e.startTime.toString().substring(0, 5),
                endTime = e.endTime.toString().substring(0, 5),
                room = e.room,
                conflict = conflict
            )
        }
    }

    @Transactional
    fun createTimetableEntry(req: CreateTimetableEntryRequest): TimetableEntryDto {
        val tenantId = TenantContext.get()
        val saved = timetableRepo.save(
            TimetableEntry(
                tenantId = tenantId,
                allocationId = req.allocationId,
                lecturerId = req.lecturerId,
                academicUnitId = req.academicUnitId,
                dayOfWeek = req.dayOfWeek,
                startTime = java.time.LocalTime.parse(req.startTime),
                endTime = java.time.LocalTime.parse(req.endTime),
                room = req.room
            )
        )
        audit("Timetable entry created", "TimetableEntry", saved.id.toString(), "Day ${req.dayOfWeek} ${req.startTime}-${req.endTime}")
        return listTimetable().first { it.id == saved.id }
    }

    fun workloadSummary(): WorkloadSummaryDto {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
        val rows = lecturers.map { l ->
            val util = if (l.maximumWorkload > java.math.BigDecimal.ZERO) {
                ((l.currentWorkload.toDouble() / l.maximumWorkload.toDouble()) * 100).toInt()
            } else 0
            val status = when {
                util > 100 -> "Overloaded"
                util >= 90 -> "Near limit"
                util < 50 -> "Underloaded"
                else -> "Optimal"
            }
            WorkloadRowDto(
                lecturerId = l.id,
                name = l.fullName,
                department = orgs[l.organizationNodeId]?.name ?: "",
                currentWorkload = l.currentWorkload,
                maximumWorkload = l.maximumWorkload,
                utilization = util,
                status = status
            )
        }
        val total = lecturers.fold(java.math.BigDecimal.ZERO) { acc, l -> acc.add(l.currentWorkload) }
        val avg = if (lecturers.isEmpty()) 0.0 else total.toDouble() / lecturers.size
        return WorkloadSummaryDto(
            totalHours = total,
            averageLoad = Math.round(avg * 10) / 10.0,
            underloaded = rows.count { it.status == "Underloaded" },
            optimal = rows.count { it.status == "Optimal" },
            nearLimit = rows.count { it.status == "Near limit" },
            overloaded = rows.count { it.status == "Overloaded" },
            rows = rows
        )
    }

    @Transactional
    fun resolveConflict(conflictId: UUID): ConflictDto {
        val tenantId = TenantContext.get()
        val conflict = conflictRepo.findById(conflictId).orElseThrow { NoSuchElementException("Conflict not found") }
        if (conflict.tenantId != tenantId) throw NoSuchElementException("Conflict not found")
        conflict.resolved = true
        conflictRepo.save(conflict)
        conflict.allocationId?.let { aid ->
            allocationRepo.findByTenantIdAndId(tenantId, aid)?.let { alloc ->
                if (alloc.status == "CONFLICT") {
                    alloc.status = "ASSIGNED"
                    alloc.updatedAt = java.time.Instant.now()
                    allocationRepo.save(alloc)
                }
            }
        }
        audit("Conflict resolved", "Conflict", conflictId.toString(), conflict.category)
        return ConflictDto(conflict.id, conflict.category, conflict.severity, conflict.description, conflict.relatedEntity, conflict.allocationId)
    }

    fun reportSummary(type: String): ReportSummaryDto {
        val dash = dashboard()
        val wl = workloadSummary()
        val requests = listRequests()
        val approvals = listApprovals()
        val tt = listTimetable()
        val units = listUnits()
        val metrics = when (type.lowercase()) {
            "workload", "lecturer workload" -> mapOf(
                "Total teaching hours" to wl.totalHours.toPlainString(),
                "Average load" to wl.averageLoad.toString(),
                "Underloaded" to wl.underloaded.toString(),
                "Optimal" to wl.optimal.toString(),
                "Near limit" to wl.nearLimit.toString(),
                "Overloaded" to wl.overloaded.toString()
            )
            "conflicts", "conflict report" -> mapOf(
                "Open conflicts" to dash.conflicts.toString(),
                "Pending units" to dash.pending.toString(),
                "Timetable conflict slots" to tt.count { it.conflict }.toString()
            )
            "allocation", "department allocation", "school / faculty allocation", "university-wide allocation" -> mapOf(
                "Academic units" to dash.academicUnits.toString(),
                "Allocated / published" to dash.allocated.toString(),
                "Pending" to dash.pending.toString(),
                "Completion" to "${dash.completionPct}%"
            )
            "cross-department teaching" -> mapOf(
                "Cross-dept requests" to dash.crossDeptRequests.toString(),
                "Open requests" to requests.count { it.status !in listOf("PUBLISHED", "CANCELLED") }.toString()
            )
            "unallocated units" -> mapOf(
                "Unallocated units" to units.count { it.status.equals("Unallocated", true) }.toString(),
                "Total units" to units.size.toString()
            )
            "timetable report" -> mapOf(
                "Timetable entries" to tt.size.toString(),
                "Conflict slots" to tt.count { it.conflict }.toString()
            )
            "teaching request report" -> mapOf(
                "Total requests" to requests.size.toString(),
                "Recommended" to requests.count { it.status.contains("RECOMMEND", true) }.toString(),
                "Published" to requests.count { it.status.equals("PUBLISHED", true) }.toString()
            )
            "approval report" -> mapOf(
                "Approval records" to approvals.size.toString(),
                "Pending approvals" to approvals.count { it.status.equals("PENDING", true) }.toString(),
                "Approved" to approvals.count { it.status.equals("APPROVED", true) }.toString()
            )
            else -> mapOf(
                "Lecturers" to dash.totalLecturers.toString(),
                "Units" to dash.academicUnits.toString(),
                "Cross-dept requests" to dash.crossDeptRequests.toString(),
                "Conflicts" to dash.conflicts.toString(),
                "Completion" to "${dash.completionPct}%"
            )
        }
        return ReportSummaryDto(title = type, generatedAt = java.time.Instant.now(), metrics = metrics)
    }

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
