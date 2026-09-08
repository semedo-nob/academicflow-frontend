package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.config.UserContext
import com.academicflow.dto.*
import com.academicflow.entity.*
import com.academicflow.repository.*
import com.academicflow.service.importing.ImportFieldMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
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
    private val requestAttachmentRepo: RequestAttachmentRepository,
    private val requestMessageRepo: RequestMessageRepository,
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
    private val platformSettingRepo: PlatformSettingRepository,
    private val yearRepo: AcademicYearRepository,
    private val semesterRepo: SemesterRepository,
    private val mappingRepo: ImportMappingProfileRepository,
    private val courseOfferingService: CourseOfferingService,
    private val scopeService: ScopeService,
    private val membershipRepo: OrganizationMembershipRepository
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
            it.active && it.accountStatus !in listOf("SUSPENDED", "LOCKED", "DEACTIVATED", "PENDING")
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
                if (inactive.accountStatus in listOf("SUSPENDED", "LOCKED", "DEACTIVATED", "PENDING")) {
                    throw IllegalStateException(
                        if (inactive.accountStatus == "PENDING")
                            "This account is waiting for an invitation to be accepted. Use your invite link to activate it."
                        else
                            "This account is ${inactive.accountStatus.lowercase()}. Contact support."
                    )
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
        val deptName = user.organizationNodeId?.let { orgId ->
            orgRepo.findById(orgId).orElse(null)?.name
        }
        val memberships = scopeService.loadMemberships(user.tenantId, user.id)
        val (activeId, activeName, activeRole) = scopeService.resolveActiveDepartment(
            user.role,
            user.organizationNodeId,
            memberships,
            null
        )
        return LoginResponse(
            email = user.email,
            name = user.fullName,
            role = user.role,
            tenantId = user.tenantId,
            userId = user.id,
            organizationNodeId = user.organizationNodeId,
            departmentName = activeName ?: deptName,
            activeDepartmentId = activeId,
            activeDepartmentName = activeName,
            activeRole = activeRole,
            memberships = memberships.map {
                MembershipDto(it.id, it.organizationNodeId, it.organizationName, it.organizationType, it.role, it.isPrimary)
            }
        )
    }

    fun me(): LoginResponse {
        val me = UserContext.get() ?: throw NoSuchElementException("Not authenticated")
        val user = userRepo.findById(me.userId).orElseThrow { NoSuchElementException("User not found") }
        return LoginResponse(
            email = user.email,
            name = user.fullName,
            role = user.role,
            tenantId = user.tenantId,
            userId = user.id,
            organizationNodeId = user.organizationNodeId,
            departmentName = me.activeDepartmentName,
            activeDepartmentId = me.activeDepartmentId,
            activeDepartmentName = me.activeDepartmentName,
            activeRole = me.activeRole,
            memberships = me.memberships.map {
                MembershipDto(it.id, it.organizationNodeId, it.organizationName, it.organizationType, it.role, it.isPrimary)
            }
        )
    }

    fun organizationTree(): List<OrganizationNodeDto> {
        val tenantId = TenantContext.get()
        val nodes = orgRepo.findByTenantIdOrderByNameAsc(tenantId)
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId)
        val allowed = scopeService.authorizedDepartmentIds()
        return nodes.mapNotNull { n ->
            if (allowed != null && n.type.equals("Department", true) && n.id !in allowed) {
                // Still show ancestors for tree shape, but zero out counts for foreign depts
            }
            val scopedLecturers = if (allowed == null) lecturers else lecturers.filter { it.organizationNodeId in allowed }
            val scopedUnits = if (allowed == null) units else units.filter { it.sourceDepartmentId in allowed }
            OrganizationNodeDto(
                id = n.id,
                tenantId = n.tenantId,
                name = n.name,
                type = n.type,
                parentId = n.parentId,
                lecturerCount = scopedLecturers.count { it.organizationNodeId == n.id },
                unitCount = scopedUnits.count { it.sourceDepartmentId == n.id }
            )
        }
    }

    @Transactional
    fun createOrgNode(req: CreateOrgNodeRequest): OrganizationNodeDto {
        val tenantId = TenantContext.get()
        val parentId = resolveOrgParentId(tenantId, req.parentId, req.parentName)
        val node = orgRepo.save(
            OrganizationNode(tenantId = tenantId, name = req.name.trim(), type = req.type.trim().ifBlank { "Department" }, parentId = parentId)
        )
        audit("Organization node created", "OrganizationNode", node.id.toString(), req.name)
        return OrganizationNodeDto(node.id, node.tenantId, node.name, node.type, node.parentId)
    }

    fun listLecturers(): List<LecturerDto> {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val expertise = expertiseRepo.findByTenantId(tenantId).groupBy { it.lecturerId }
        val allowed = scopeService.authorizedDepartmentIds()
        return lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
            .filter { allowed == null || it.organizationNodeId in allowed }
            .map { l ->
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
        val departmentId = resolveDepartmentId(tenantId, req.departmentId, req.department)
        scopeService.requireDepartmentAccess(departmentId)
        val saved = lecturerRepo.save(
            Lecturer(
                tenantId = tenantId,
                staffNumber = req.staffNumber,
                fullName = req.name,
                email = req.email,
                organizationNodeId = departmentId,
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
        val allowed = scopeService.authorizedDepartmentIds()
        return unitRepo.findByTenantIdOrderByCodeAsc(tenantId)
            .filter { allowed == null || it.sourceDepartmentId in allowed }
            .map { u ->
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
        val sourceDepartmentId = resolveDepartmentId(tenantId, req.sourceDepartmentId, req.sourceDepartment)
        scopeService.requireDepartmentAccess(sourceDepartmentId)
        val saved = unitRepo.save(
            AcademicUnit(
                tenantId = tenantId,
                code = req.code,
                name = req.name,
                sourceDepartmentId = sourceDepartmentId,
                contactHours = req.contactHours,
                studentCount = req.studentCount,
                requiredExpertise = req.requiredExpertise.joinToString(",")
            )
        )
        audit("Academic unit created", "AcademicUnit", saved.id.toString(), "${saved.code} ${saved.name}")
        return listUnits().first { it.id == saved.id }
    }

    fun listRequests(): List<TeachingRequestDto> {
        val all = requestRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.get())
        val allowed = scopeService.authorizedDepartmentIds()
        val scoped = if (allowed == null) all
        else all.filter { it.requestingDepartmentId in allowed || it.preferredDepartmentId in allowed }
        return mapRequests(scoped)
    }

    private fun mapRequests(requests: List<TeachingRequest>): List<TeachingRequestDto> {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val me = UserContext.get()
        val myDept = me?.activeDepartmentId ?: me?.organizationNodeId
        return requests.map { r ->
            val unit = units[r.academicUnitId]
            val direction = when {
                myDept == null -> null
                r.requestingDepartmentId == myDept -> "outgoing"
                r.preferredDepartmentId == myDept -> "incoming"
                else -> "other"
            }
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
                createdAt = r.createdAt,
                courseOfferingId = r.courseOfferingId,
                createdBy = r.createdBy,
                direction = direction,
                briefingNote = r.briefingNote,
                attachmentCount = requestAttachmentRepo.countByTenantIdAndTeachingRequestId(tenantId, r.id).toInt(),
                messageCount = requestMessageRepo.countByTenantIdAndTeachingRequestId(tenantId, r.id).toInt()
            )
        }
    }

    private fun requireRequestAccess(request: TeachingRequest) {
        val me = UserContext.get()
        if (me?.isInstitutionWide() == true) return
        val allowed = scopeService.authorizedDepartmentIds()
        if (allowed == null) return
        val ok = request.requestingDepartmentId in allowed ||
            (request.preferredDepartmentId != null && request.preferredDepartmentId in allowed)
        if (!ok) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this teaching request")
        }
    }

    private fun requireRequest(id: UUID): TeachingRequest {
        val tenantId = TenantContext.get()
        val request = requestRepo.findByTenantIdAndId(tenantId, id)
            ?: throw NoSuchElementException("Request not found")
        requireRequestAccess(request)
        return request
    }

    fun getRequestDetail(id: UUID): TeachingRequestDetailDto {
        val tenantId = TenantContext.get()
        val request = requireRequest(id)
        val dto = mapRequests(listOf(request)).first()
        val attachments = requestAttachmentRepo
            .findByTenantIdAndTeachingRequestIdOrderByCreatedAtAsc(tenantId, id)
            .map { toAttachmentDto(it) }
        val meId = UserContext.get()?.userId
        val messages = requestMessageRepo
            .findByTenantIdAndTeachingRequestIdOrderByCreatedAtAsc(tenantId, id)
            .map { toMessageDto(it, meId) }
        return TeachingRequestDetailDto(request = dto, attachments = attachments, messages = messages)
    }

    private fun toAttachmentDto(a: RequestAttachment) = RequestAttachmentDto(
        id = a.id,
        teachingRequestId = a.teachingRequestId,
        fileName = a.fileName,
        contentType = a.contentType,
        fileSizeBytes = a.fileSizeBytes,
        docType = a.docType,
        uploadedByName = a.uploadedByName,
        departmentName = a.departmentName,
        createdAt = a.createdAt
    )

    private fun toMessageDto(m: RequestMessage, meId: UUID?) = RequestMessageDto(
        id = m.id,
        teachingRequestId = m.teachingRequestId,
        authorName = m.authorName,
        authorRole = m.authorRole,
        authorDepartmentName = m.authorDepartmentName,
        messageType = m.messageType,
        body = m.body,
        relatedLecturerId = m.relatedLecturerId,
        relatedLecturerName = m.relatedLecturerName,
        notifyAuthority = m.notifyAuthority,
        createdAt = m.createdAt,
        mine = meId != null && m.authorUserId == meId
    )

    private fun postSystemMessage(
        requestId: UUID,
        body: String,
        messageType: String = "SYSTEM",
        notifyAuthority: Boolean = false
    ) {
        val tenantId = TenantContext.get()
        val me = UserContext.get()
        val deptId = me?.activeDepartmentId ?: me?.organizationNodeId
        val deptName = me?.activeDepartmentName
            ?: deptId?.let { orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { n -> n.id == it }?.name }
        val author = me?.userId?.let { userRepo.findById(it).orElse(null) }
        requestMessageRepo.save(
            RequestMessage(
                tenantId = tenantId,
                teachingRequestId = requestId,
                authorUserId = me?.userId,
                authorName = author?.fullName ?: me?.email,
                authorRole = me?.activeRole ?: me?.role,
                authorDepartmentId = deptId,
                authorDepartmentName = deptName,
                messageType = messageType,
                body = body,
                notifyAuthority = notifyAuthority
            )
        )
    }

    @Transactional
    fun uploadRequestAttachment(
        requestId: UUID,
        file: org.springframework.web.multipart.MultipartFile,
        docType: String?
    ): RequestAttachmentDto {
        val request = requireRequest(requestId)
        if (file.isEmpty) throw IllegalArgumentException("Empty file")
        if (file.size > 20L * 1024 * 1024) throw IllegalArgumentException("Attachment exceeds 20 MB limit")
        val tenantId = TenantContext.get()
        val me = UserContext.get()
        val deptId = me?.activeDepartmentId ?: me?.organizationNodeId
        val deptName = me?.activeDepartmentName
            ?: deptId?.let { id -> orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == id }?.name }
        val author = me?.userId?.let { userRepo.findById(it).orElse(null) }
        val type = (docType ?: "COURSE_OUTLINE").trim().uppercase().ifBlank { "COURSE_OUTLINE" }
        val allowedTypes = setOf("COURSE_OUTLINE", "SUPPORTING_DOC", "TIMETABLE", "OTHER")
        if (type !in allowedTypes) {
            throw IllegalArgumentException("docType must be one of ${allowedTypes.joinToString()}")
        }
        val safeName = (file.originalFilename ?: "document")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "document.bin" }
        val saved = requestAttachmentRepo.save(
            RequestAttachment(
                tenantId = tenantId,
                teachingRequestId = request.id,
                fileName = safeName,
                contentType = file.contentType,
                fileSizeBytes = file.size,
                fileBytes = file.bytes,
                docType = type,
                uploadedBy = me?.userId,
                uploadedByName = author?.fullName ?: me?.email,
                departmentId = deptId,
                departmentName = deptName
            )
        )
        // Mirror course outlines onto the linked offering for suitability matching when present.
        if (type == "COURSE_OUTLINE" && request.courseOfferingId != null) {
            try {
                courseOfferingService.uploadOutline(request.courseOfferingId!!, file)
            } catch (_: Exception) {
                // Attachment is source of truth for collaboration; outline mirror is best-effort.
            }
        }
        postSystemMessage(
            request.id,
            "Attached $type: $safeName",
            messageType = "SYSTEM"
        )
        audit("Request attachment uploaded", "TeachingRequest", request.id.toString(), "$type $safeName")
        return toAttachmentDto(saved)
    }

    fun downloadRequestAttachment(attachmentId: UUID): Pair<RequestAttachment, ByteArray> {
        val tenantId = TenantContext.get()
        val attachment = requestAttachmentRepo.findByTenantIdAndId(tenantId, attachmentId)
            ?: throw NoSuchElementException("Attachment not found")
        requireRequest(attachment.teachingRequestId)
        val bytes = attachment.fileBytes ?: throw NoSuchElementException("Attachment file missing")
        return attachment to bytes
    }

    @Transactional
    fun postRequestMessage(requestId: UUID, req: CreateRequestMessage): RequestMessageDto {
        val request = requireRequest(requestId)
        val body = req.body.trim()
        if (body.isEmpty()) throw IllegalArgumentException("Message body is required")
        val type = req.messageType.trim().uppercase().ifBlank { "COMMENT" }
        val allowed = setOf("COMMENT", "ELIGIBILITY_NOTE", "AUTHORITY_NOTICE")
        if (type !in allowed) {
            throw IllegalArgumentException("messageType must be one of ${allowed.joinToString()}")
        }
        val tenantId = TenantContext.get()
        val me = UserContext.get()
        val deptId = me?.activeDepartmentId ?: me?.organizationNodeId
        val deptName = me?.activeDepartmentName
            ?: deptId?.let { id -> orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == id }?.name }
        val author = me?.userId?.let { userRepo.findById(it).orElse(null) }
        var lecturerId = req.relatedLecturerId
        var lecturerName = req.relatedLecturerName?.trim()?.ifBlank { null }
        if (lecturerId != null) {
            val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, lecturerId)
                ?: throw IllegalArgumentException("Lecturer not found")
            lecturerName = lecturer.fullName
        } else if (!lecturerName.isNullOrBlank()) {
            lecturerId = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
                .firstOrNull { it.fullName.equals(lecturerName, true) }?.id
        }
        val notify = req.notifyAuthority || type == "AUTHORITY_NOTICE"
        val saved = requestMessageRepo.save(
            RequestMessage(
                tenantId = tenantId,
                teachingRequestId = request.id,
                authorUserId = me?.userId,
                authorName = author?.fullName ?: me?.email,
                authorRole = me?.activeRole ?: me?.role,
                authorDepartmentId = deptId,
                authorDepartmentName = deptName,
                messageType = type,
                body = body,
                relatedLecturerId = lecturerId,
                relatedLecturerName = lecturerName,
                notifyAuthority = notify
            )
        )
        if (notify) {
            audit(
                "Academic authority notified",
                "TeachingRequest",
                request.id.toString(),
                body.take(240)
            )
        } else {
            audit("Request message posted", "TeachingRequest", request.id.toString(), type)
        }
        return toMessageDto(saved, me?.userId)
    }

    @Transactional
    fun createRequest(req: CreateTeachingRequest): TeachingRequestDto {
        val tenantId = TenantContext.get()
        val me = UserContext.get()
        val requestingDepartmentId = resolveDepartmentId(
            tenantId,
            req.requestingDepartmentId ?: me?.organizationNodeId,
            req.requestingDepartment
        )
        val preferredDepartmentId =
            if (req.preferredDepartmentId == null && req.preferredDepartment.isNullOrBlank()) null
            else resolveDepartmentId(tenantId, req.preferredDepartmentId, req.preferredDepartment)
        if (preferredDepartmentId != null && preferredDepartmentId == requestingDepartmentId) {
            throw IllegalArgumentException("Preferred department must differ from the requesting department")
        }
        val academicUnitId = resolveAcademicUnitId(
            tenantId,
            req.academicUnitId,
            req.academicUnit,
            preferredDepartmentId ?: requestingDepartmentId
        )
        val initialStatus = if (preferredDepartmentId != null) "AWAITING_RESPONSE" else "PENDING"
        val briefing = req.briefingNote?.trim()?.ifBlank { null }
        val saved = requestRepo.save(
            TeachingRequest(
                tenantId = tenantId,
                requestingDepartmentId = requestingDepartmentId,
                preferredDepartmentId = preferredDepartmentId,
                academicUnitId = academicUnitId,
                studentCount = req.studentCount,
                contactHours = req.contactHours,
                requiredExpertise = req.requiredExpertise,
                status = initialStatus,
                briefingNote = briefing,
                createdBy = me?.userId
            )
        )

        if (req.createOffering) {
            val unit = unitRepo.findByTenantIdAndId(tenantId, academicUnitId)
            val offering = courseOfferingService.createOffering(
                CreateCourseOfferingRequest(
                    academicUnitId = academicUnitId,
                    programme = req.programme,
                    contextLabel = req.contextLabel ?: "Cross-department request",
                    displayTitle = unit?.name,
                    requestingDepartmentId = requestingDepartmentId,
                    owningDepartmentId = preferredDepartmentId ?: unit?.sourceDepartmentId,
                    requirements = req.requiredExpertise
                        ?.split(",", ";")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.map { RequirementInputDto("TOPIC", it) }
                        .orEmpty()
                )
            )
            saved.courseOfferingId = offering.id
            requestRepo.save(saved)
        }

        val unit = unitRepo.findByTenantIdAndId(tenantId, academicUnitId)
        if (unit != null && unit.status == "Unallocated") {
            unit.status = "Pending"
            unitRepo.save(unit)
        }
        if (!briefing.isNullOrBlank()) {
            postSystemMessage(saved.id, briefing, messageType = "COMMENT")
        } else {
            postSystemMessage(
                saved.id,
                "Cross-department teaching request submitted for review.",
                messageType = "SYSTEM"
            )
        }
        audit("Request submitted", "TeachingRequest", saved.id.toString(), "Cross-department teaching request")
        return listRequests().first { it.id == saved.id }
    }

    @Transactional
    fun respondToRequest(id: UUID, req: RespondTeachingRequest): TeachingRequestDto {
        val tenantId = TenantContext.get()
        val me = UserContext.get()
        val request = requestRepo.findByTenantIdAndId(tenantId, id)
            ?: throw NoSuchElementException("Request not found")
        requireRequestAccess(request)
        val action = req.action.trim().uppercase()
        if (action !in setOf("ACCEPT", "DECLINE")) {
            throw IllegalArgumentException("action must be ACCEPT or DECLINE")
        }
        val myDept = me?.activeDepartmentId ?: me?.organizationNodeId
        val isAdmin = me?.isInstitutionWide() == true
        if (!isAdmin && myDept != null && request.preferredDepartmentId != null && request.preferredDepartmentId != myDept) {
            throw IllegalStateException("Only the preferred / source department chair can respond to this request")
        }
        val note = req.note?.trim()?.ifBlank { null }
        when (action) {
            "ACCEPT" -> {
                request.status = "ACCEPTED"
                postSystemMessage(
                    request.id,
                    note ?: "Request accepted. Matching and allocation may proceed.",
                    messageType = "ACCEPT_NOTE"
                )
                audit("Request accepted", "TeachingRequest", request.id.toString(), note)
            }
            "DECLINE" -> {
                request.status = "DECLINED"
                postSystemMessage(
                    request.id,
                    note ?: "Request declined.",
                    messageType = "DECLINE_NOTE"
                )
                audit("Request declined", "TeachingRequest", request.id.toString(), note)
            }
        }
        requestRepo.save(request)
        return listRequests().first { it.id == request.id }
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
        val allowed = scopeService.authorizedDepartmentIds()
        return allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .filter { a ->
                if (allowed == null) true
                else {
                    val unit = units[a.academicUnitId]
                    unit != null && unit.sourceDepartmentId in allowed
                }
            }
            .map { a ->
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

        val unit = unitRepo.findByTenantIdAndId(tenantId, req.academicUnitId)
            ?: throw NoSuchElementException("Academic unit not found")
        val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, req.lecturerId)
            ?: throw NoSuchElementException("Lecturer not found")

        val allowedDepts = scopeService.authorizedDepartmentIds()
        val linkedRequest = req.teachingRequestId?.let { requestRepo.findByTenantIdAndId(tenantId, it) }
        val crossDeptOk = linkedRequest != null &&
            linkedRequest.status.uppercase() in setOf("ACCEPTED", "RECOMMENDED", "MATCHED", "ASSIGNED", "AWAITING_RESPONSE") &&
            (
                linkedRequest.preferredDepartmentId == lecturer.organizationNodeId ||
                    linkedRequest.requestingDepartmentId == unit.sourceDepartmentId
            )

        if (allowedDepts != null) {
            // Chair may allocate units in their department, OR complete an accepted cross-dept request
            val unitOk = unit.sourceDepartmentId in allowedDepts ||
                (linkedRequest != null && linkedRequest.requestingDepartmentId in allowedDepts)
            if (!unitOk) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Unit is outside your department scope")
            }
            scopeService.requireLecturerInDepartments(
                lecturer.organizationNodeId,
                allowedDepts + setOfNotNull(linkedRequest?.preferredDepartmentId),
                crossDepartmentAllowed = crossDeptOk
            )
            if (!crossDeptOk && lecturer.organizationNodeId != unit.sourceDepartmentId &&
                lecturer.organizationNodeId !in allowedDepts
            ) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cannot allocate this unit to a lecturer outside your department without an accepted cross-department request"
                )
            }
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
                courseOfferingId = req.courseOfferingId,
                matchScore = req.matchScore,
                status = status,
                overrideReason = req.overrideReason,
                recommendedLecturerId = req.recommendedLecturerId,
                suitabilityScore = req.suitabilityScore,
                suitabilityBreakdown = req.suitabilityBreakdown,
                decisionType = req.decisionType
                    ?: if (req.recommendedLecturerId != null && req.recommendedLecturerId != req.lecturerId) "OVERRIDE"
                    else if (req.recommendedLecturerId == req.lecturerId) "ACCEPTED_RECOMMENDATION"
                    else null,
                decisionNote = req.decisionNote
            )
        )

        val unitRow = unitRepo.findByTenantIdAndId(tenantId, req.academicUnitId)
        if (unitRow != null) {
            unitRow.status = if (status == "CONFLICT") "Conflict" else "Assigned"
            unitRepo.save(unitRow)
        }

        req.teachingRequestId?.let { rid ->
            requestRepo.findByTenantIdAndId(tenantId, rid)?.let {
                it.status = if (status == "CONFLICT") "CONFLICT" else "ASSIGNED"
                requestRepo.save(it)
            }
        }

        val lecturerRow = lecturerRepo.findByTenantIdAndId(tenantId, req.lecturerId)
        if (lecturerRow != null && unitRow != null && status == "ASSIGNED") {
            lecturerRow.currentWorkload = lecturerRow.currentWorkload.add(unitRow.contactHours)
            lecturerRepo.save(lecturerRow)
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
        val allowed = scopeService.authorizedDepartmentIds()
        val me = UserContext.get()
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
            .filter { allowed == null || it.organizationNodeId in allowed }
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId)
            .filter { allowed == null || it.sourceDepartmentId in allowed }
        val unitIds = units.map { it.id }.toSet()
        val allocations = allocationRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .filter { allowed == null || it.academicUnitId in unitIds }
        val requests = requestRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).filter { r ->
            if (allowed == null) true
            else r.requestingDepartmentId in allowed || r.preferredDepartmentId in allowed
        }
        val conflicts = conflictRepo.findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(tenantId)
            .filter { c ->
                if (allowed == null) true
                else c.allocationId == null || allocations.any { it.id == c.allocationId }
            }
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
            },
            scopeDepartmentId = me?.activeDepartmentId,
            scopeDepartmentName = me?.activeDepartmentName,
            scopeRole = me?.activeRole ?: me?.role
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
                entityType = req.entityType.uppercase(),
                status = "MAP",
                columnMap = ImportFieldMapper.serializeColumnMap(req.columnMap)
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
        audit("Import session created", "ImportSession", saved.id.toString(), "${req.fileName} · ${rows.size} rows · ${req.entityType}")
        return toImportSessionDto(saved)
    }

    fun listImportSessions(): List<ImportSessionDto> =
        importRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.get()).map { toImportSessionDto(it) }

    fun listImportRows(sessionId: UUID): List<Map<String, String>> {
        val session = importRepo.findById(sessionId).orElseThrow { NoSuchElementException("Import session not found") }
        if (session.tenantId != TenantContext.get()) throw NoSuchElementException("Import session not found")
        return stagingRepo.findBySessionIdOrderByRowNumberAsc(sessionId).map { row ->
            row.rawJson.split(";").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap() + mapOf(
                "_row" to row.rowNumber.toString(),
                "_valid" to row.valid.toString(),
                "_errors" to (row.errors ?: "")
            )
        }
    }

    @Transactional
    fun updateImportMapping(id: UUID, req: UpdateImportMappingRequest): ImportUploadResultDto {
        val tenantId = TenantContext.get()
        val session = importRepo.findById(id).orElseThrow { NoSuchElementException("Import session not found") }
        if (session.tenantId != tenantId) throw NoSuchElementException("Import session not found")
        if (session.status.equals("IMPORTED", true)) {
            throw IllegalStateException("Cannot change mapping after import is committed")
        }
        req.entityType?.takeIf { it.isNotBlank() }?.let { session.entityType = it.uppercase() }
        val cleaned = req.columnMap.filter { (src, target) ->
            target.isNotBlank() && !target.equals("IGNORE", true) && src !in req.ignoredColumns
        }
        // Never wipe a good stored map with an empty client payload.
        if (cleaned.isNotEmpty() || session.columnMap.isNullOrBlank()) {
            session.columnMap = ImportFieldMapper.serializeColumnMap(cleaned)
        }
        session.status = "MAP"
        importRepo.save(session)

        req.saveAsProfileName?.trim()?.takeIf { it.isNotEmpty() }?.let { profileName ->
            mappingRepo.save(
                ImportMappingProfile(
                    tenantId = tenantId,
                    name = profileName,
                    entityType = session.entityType,
                    columnMap = session.columnMap ?: "",
                    mappingVersion = 1,
                    notes = "Saved from import session ${session.id}"
                )
            )
            audit("Import mapping profile saved from session", "ImportMappingProfile", session.id.toString(), profileName)
        }

        val effectiveMap = ImportFieldMapper.parseColumnMap(session.columnMap).ifEmpty { cleaned }
        val rows = listImportRows(id).map { it.filterKeys { k -> !k.startsWith("_") } }
        val headers = if (rows.isNotEmpty()) rows.first().keys.toList() else effectiveMap.keys.toList()
        val sample = rows.take(12).map { r -> headers.map { h -> r[h] ?: "" } }
        val suggestions = ImportFieldMapper.suggest(headers, sample, session.entityType, effectiveMap)
        audit("Import mapping updated", "ImportSession", session.id.toString(), "${effectiveMap.size} mapped fields")
        return ImportUploadResultDto(
            sessionId = session.id,
            fileName = session.fileName,
            entityType = session.entityType,
            status = session.status,
            detectedColumns = headers,
            suggestedMap = effectiveMap,
            mappingSuggestions = suggestions.suggestions.map {
                ColumnMappingSuggestionDto(it.sourceColumn, it.targetField, it.confidence, it.method, it.sampleValues, it.unmapped)
            },
            unmappedColumns = headers.filter { it !in effectiveMap },
            canonicalFields = ImportFieldMapper.canonicalFields(session.entityType),
            profileApplied = req.saveAsProfileName,
            rowCount = rows.size,
            preview = rows.take(8),
            warnings = suggestions.warnings + listOfNotNull(
                req.ignoredColumns.takeIf { it.isNotEmpty() }?.let { "Ignoring ${it.size} column(s) as metadata-only." }
            )
        )
    }

    @Transactional
    fun advanceImportSession(id: UUID): ImportSessionDto {
        val tenantId = TenantContext.get()
        val session = importRepo.findById(id).orElseThrow { NoSuchElementException("Import session not found") }
        if (session.tenantId != tenantId) throw NoSuchElementException("Import session not found")

        // Empty prior commit (e.g. OCR rows failed alias validation) — allow retry without re-upload.
        if (session.status.equals("IMPORTED", true) && isEmptyImportSummary(session.resultSummary)) {
            return reprocessImportSession(session)
        }

        val next = when (session.status.uppercase()) {
            "MAP" -> "VALIDATE"
            "VALIDATE" -> "PREVIEW"
            "PREVIEW" -> "IMPORTED"
            "IMPORTED" -> "IMPORTED"
            else -> "VALIDATE"
        }
        if (next == "VALIDATE") {
            validateImportRows(session)
        }
        if (next == "IMPORTED" && session.status.uppercase() == "PREVIEW") {
            val summary = commitImport(session)
            session.resultSummary = encodeSummary(summary)
            audit(
                "Import committed",
                "ImportSession",
                session.id.toString(),
                "allocations=${summary.allocations};lecturers=${summary.lecturers};units=${summary.units};dupes=${summary.duplicatesSkipped};errors=${summary.errors}"
            )
        }
        session.status = next
        importRepo.save(session)
        audit("Import session advanced", "ImportSession", session.id.toString(), "Status → ${session.status}")
        return toImportSessionDto(session)
    }

    @Transactional
    fun reprocessImportSession(id: UUID): ImportSessionDto {
        val tenantId = TenantContext.get()
        val session = importRepo.findById(id).orElseThrow { NoSuchElementException("Import session not found") }
        if (session.tenantId != tenantId) throw NoSuchElementException("Import session not found")
        return reprocessImportSession(session)
    }

    private fun reprocessImportSession(session: ImportSession): ImportSessionDto {
        ensureColumnMap(session)
        validateImportRows(session)
        val summary = commitImport(session)
        session.resultSummary = encodeSummary(summary)
        session.status = "IMPORTED"
        importRepo.save(session)
        audit(
            "Import reprocessed",
            "ImportSession",
            session.id.toString(),
            "allocations=${summary.allocations};lecturers=${summary.lecturers};units=${summary.units};dupes=${summary.duplicatesSkipped};errors=${summary.errors}"
        )
        return toImportSessionDto(session)
    }

    private fun isEmptyImportSummary(raw: String?): Boolean {
        val s = decodeSummary(raw) ?: return true
        return s.allocations == 0 && s.lecturers == 0 && s.units == 0
    }

    /** When mapping was wiped or never saved, rebuild from staging headers + aliases. */
    private fun ensureColumnMap(session: ImportSession) {
        if (!session.columnMap.isNullOrBlank()) return
        val rows = stagingRepo.findBySessionIdOrderByRowNumberAsc(session.id)
        val first = rows.firstOrNull() ?: return
        val headers = parseRaw(first.rawJson).keys.toList()
        if (headers.isEmpty()) return
        val sample = rows.take(25).map { row ->
            val raw = parseRaw(row.rawJson)
            headers.map { h -> raw[h] ?: "" }
        }
        val mapping = ImportFieldMapper.suggest(headers, sample, session.entityType)
        if (mapping.columnMap.isNotEmpty()) {
            session.columnMap = ImportFieldMapper.serializeColumnMap(mapping.columnMap)
            importRepo.save(session)
        }
    }

    private fun validateImportRows(session: ImportSession) {
        ensureColumnMap(session)
        val columnMap = ImportFieldMapper.parseColumnMap(session.columnMap)
        stagingRepo.findBySessionIdOrderByRowNumberAsc(session.id).forEach { row ->
            val raw = parseRaw(row.rawJson)
            fun field(canonical: String, vararg aliases: String): String? {
                columnMap.entries.firstOrNull { it.value == canonical }?.key?.let { src ->
                    raw.entries.firstOrNull { it.key.equals(src, ignoreCase = true) }
                        ?.value?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                }
                (listOf(canonical) + aliases.toList()).forEach { key ->
                    raw.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                        ?.value?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                }
                return null
            }
            val errors = mutableListOf<String>()
            when (session.entityType.uppercase()) {
                "LECTURER" -> {
                    if (field("name", "Staff Name", "Instructor").isNullOrBlank()) errors += "Missing lecturer name"
                    if (field("staffNumber", "Staff No", "Employee ID").isNullOrBlank()) errors += "Missing staff/employee id"
                }
                "ACADEMIC_UNIT" -> {
                    if (field("code", "Course Code", "Unit").isNullOrBlank() &&
                        field("name", "Course Name", "Unit Description").isNullOrBlank()
                    ) {
                        errors += "Missing unit code or name"
                    }
                    field("contactHours", "Hours", "Hrs")?.let {
                        if (it.toBigDecimalOrNull() == null) errors += "Invalid contact hours: $it"
                    }
                }
                "ALLOCATION" -> {
                    // Aliases must cover OCR allocation headers (Staff Name / Course Title).
                    val hasLecturer = !field("staffNumber", "Staff No", "Employee ID", "Staff Number").isNullOrBlank() ||
                        !field(
                            "lecturerName",
                            "Instructor",
                            "Lecturer Name",
                            "Staff Name",
                            "Teacher",
                            "Tutor",
                            "Faculty Name"
                        ).isNullOrBlank() ||
                        !field("email", "Email").isNullOrBlank()
                    val hasUnit = !field("unitCode", "Course Code", "Unit", "Module Ref").isNullOrBlank() ||
                        !field("unitName", "Course Title", "Unit Description", "Course Name", "Module Name").isNullOrBlank()
                    if (!hasLecturer) errors += "Missing lecturer identity"
                    if (!hasUnit) errors += "Missing unit/course identity"
                    field("contactHours", "Hours", "Hrs", "Contact Hrs", "Workload")?.let {
                        if (it.toBigDecimalOrNull() == null) errors += "Invalid hours: $it"
                    }
                }
            }
            row.valid = errors.isEmpty()
            row.errors = errors.joinToString("; ").ifBlank { null }
            stagingRepo.save(row)
        }
    }

    private fun commitImport(session: ImportSession): ImportResultSummaryDto {
        val tenantId = TenantContext.get()
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId)
        val me = UserContext.get()
        val allowedDeptIds = scopeService.authorizedDepartmentIds()
        val tenantDefaultOrg = orgs.firstOrNull { it.type.equals("Department", true) } ?: orgs.firstOrNull()
            ?: throw IllegalStateException("No organization node available for import")
        val scopedDefaultOrg = when {
            allowedDeptIds == null -> tenantDefaultOrg
            me?.activeDepartmentId != null ->
                orgs.firstOrNull { it.id == me.activeDepartmentId && it.id in allowedDeptIds }
                    ?: orgs.firstOrNull { it.id in allowedDeptIds }
                    ?: throw IllegalStateException("No department in your scope for import")
            else ->
                orgs.firstOrNull { it.id in allowedDeptIds }
                    ?: throw IllegalStateException("No department in your scope for import")
        }
        val columnMap = ImportFieldMapper.parseColumnMap(session.columnMap)
        val unmappedFields = columnMap.entries.count { false }.let {
            // count staging header keys not in map — approximate from first row
            val first = stagingRepo.findBySessionIdOrderByRowNumberAsc(session.id).firstOrNull()
            if (first == null) 0
            else parseRaw(first.rawJson).keys.count { k -> k !in columnMap }
        }

        var lecturersCreated = 0
        var unitsCreated = 0
        var allocationsCreated = 0
        var duplicatesSkipped = 0
        var errors = 0
        var warnings = if (unmappedFields > 0) 1 else 0
        val details = mutableListOf<String>()
        val tenant = tenantRepo.findById(tenantId).orElse(null)
        val emailDomain = tenant?.code?.lowercase()?.replace(" ", "")?.takeIf { it.isNotBlank() }?.let { "$it.ac.ke" }
            ?: "academicflow.local"
        if (allowedDeptIds != null) {
            details += "Chair-scoped import: rows commit only to ${scopedDefaultOrg.name} (other departments skipped)."
            warnings++
        }

        stagingRepo.findBySessionIdOrderByRowNumberAsc(session.id).filter { it.valid }.forEach { row ->
            val raw = parseRaw(row.rawJson)
            fun field(canonical: String, vararg aliases: String): String? {
                columnMap.entries.firstOrNull { it.value == canonical }?.key?.let { src ->
                    raw.entries.firstOrNull { it.key.equals(src, ignoreCase = true) }
                        ?.value?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                }
                (listOf(canonical) + aliases.toList()).forEach { key ->
                    raw.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                        ?.value?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                }
                return null
            }

            try {
                when (session.entityType.uppercase()) {
                    "LECTURER" -> {
                        val name = field("name", "Staff Name") ?: return@forEach
                        val staff = field("staffNumber", "Staff No") ?: "IMP/${row.rowNumber}"
                        val email = field("email", "Email")
                            ?: "${staff.lowercase().replace(Regex("[^a-z0-9]+"), ".")}@$emailDomain"
                        val deptName = field("organizationNode", "Department")
                        val org = resolveOrgForImport(orgs, deptName, scopedDefaultOrg, allowedDeptIds)
                        if (org == null) {
                            errors++
                            details += "Row ${row.rowNumber}: department “${deptName ?: "?"}” is outside your department scope"
                            return@forEach
                        }
                        val existing = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
                            .firstOrNull { it.staffNumber.equals(staff, true) || it.email.equals(email, true) }
                        if (existing != null) {
                            duplicatesSkipped++
                        } else {
                            lecturerRepo.save(
                                Lecturer(
                                    tenantId = tenantId,
                                    staffNumber = staff,
                                    fullName = name,
                                    email = email,
                                    organizationNodeId = org.id,
                                    maximumWorkload = field("maximumWorkload", "Max Hours", "Teaching Hours")?.toBigDecimalOrNull()
                                        ?: BigDecimal("12"),
                                    availabilityText = field("availability") ?: "Monday–Friday",
                                    status = "Active"
                                )
                            )
                            lecturersCreated++
                        }
                    }
                    "ACADEMIC_UNIT" -> {
                        val code = field("code", "Course Code") ?: return@forEach
                        val name = field("name", "Course Name") ?: code
                        val deptName = field("organizationNode", "Department")
                        val org = resolveOrgForImport(orgs, deptName, scopedDefaultOrg, allowedDeptIds)
                        if (org == null) {
                            errors++
                            details += "Row ${row.rowNumber}: department “${deptName ?: "?"}” is outside your department scope"
                            return@forEach
                        }
                        val yearLabel = field("academicYear", "Academic Year", "Year", "Year of Study")
                        val semesterName = field("semester", "Semester", "Term")
                        val (yearId, semesterId) = resolvePeriod(tenantId, yearLabel, semesterName)
                        val exists = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).any {
                            it.code.equals(code, true) &&
                                it.academicYearId == yearId &&
                                it.semesterId == semesterId
                        }
                        if (exists) {
                            duplicatesSkipped++
                        } else {
                            unitRepo.save(
                                AcademicUnit(
                                    tenantId = tenantId,
                                    code = code,
                                    name = name,
                                    sourceDepartmentId = org.id,
                                    contactHours = field("contactHours", "Hours", "Teaching Hours")?.toBigDecimalOrNull()
                                        ?: BigDecimal("3"),
                                    studentCount = field("studentCount", "Students")?.toIntOrNull() ?: 40,
                                    academicYearId = yearId,
                                    semesterId = semesterId,
                                    requiredExpertise = field("requiredExpertise", "Expertise") ?: "General",
                                    status = "Unallocated"
                                )
                            )
                            unitsCreated++
                        }
                    }
                    "ALLOCATION" -> {
                        val staff = field("staffNumber", "Staff No", "Employee ID", "Staff Number")
                        val lecturerName = field("lecturerName", "Instructor", "Lecturer Name", "Teacher", "Staff Name")
                        val email = field("email")
                        val unitCode = field("unitCode", "Course Code", "Unit", "Module Ref")
                        val unitName = field("unitName", "Course Title", "Unit Description", "Module Name", "Course Name")
                        val deptName = field("organizationNode", "Department")
                        val hours = field("contactHours", "Hours", "Hrs", "Contact Hrs", "Workload", "Contact Hours")
                            ?.toBigDecimalOrNull()
                        val yearLabel = field("academicYear", "Academic Year", "Year")
                        val semesterName = field("semester", "Semester", "Term")
                        val org = resolveOrgForImport(orgs, deptName, scopedDefaultOrg, allowedDeptIds)
                        if (org == null) {
                            errors++
                            details += "Row ${row.rowNumber}: department “${deptName ?: "?"}” is outside your department scope"
                            return@forEach
                        }
                        val (yearId, semesterId) = resolvePeriod(tenantId, yearLabel, semesterName)

                        if ((staff.isNullOrBlank() && lecturerName.isNullOrBlank() && email.isNullOrBlank()) ||
                            (unitCode.isNullOrBlank() && unitName.isNullOrBlank())
                        ) {
                            errors++
                            details += "Row ${row.rowNumber}: incomplete allocation identity"
                            return@forEach
                        }

                        val resolvedStaff = staff ?: "IMP/${row.rowNumber}"
                        val resolvedEmail = email
                            ?: "${resolvedStaff.lowercase().replace(Regex("[^a-z0-9]+"), ".")}@$emailDomain"
                        var lecturer = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).firstOrNull {
                            (!staff.isNullOrBlank() && it.staffNumber.equals(staff, true)) ||
                                it.email.equals(resolvedEmail, true) ||
                                (!lecturerName.isNullOrBlank() && it.fullName.equals(lecturerName, true))
                        }
                        if (lecturer != null && allowedDeptIds != null && lecturer.organizationNodeId !in allowedDeptIds) {
                            errors++
                            details += "Row ${row.rowNumber}: lecturer “${lecturer.fullName}” belongs to another department"
                            return@forEach
                        }
                        if (lecturer == null) {
                            lecturer = lecturerRepo.save(
                                Lecturer(
                                    tenantId = tenantId,
                                    staffNumber = resolvedStaff,
                                    fullName = lecturerName ?: resolvedStaff,
                                    email = resolvedEmail,
                                    organizationNodeId = org.id,
                                    maximumWorkload = BigDecimal("12"),
                                    availabilityText = "Monday–Friday",
                                    status = "Active"
                                )
                            )
                            lecturersCreated++
                        }

                        val code = unitCode ?: (unitName!!.take(12).uppercase().replace(Regex("\\s+"), "-"))
                        var unit = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).firstOrNull {
                            it.code.equals(code, true) &&
                                (yearId == null || it.academicYearId == yearId) &&
                                (semesterId == null || it.semesterId == semesterId)
                        } ?: unitRepo.findByTenantIdOrderByCodeAsc(tenantId).firstOrNull { it.code.equals(code, true) }

                        if (unit != null && allowedDeptIds != null && unit.sourceDepartmentId !in allowedDeptIds) {
                            errors++
                            details += "Row ${row.rowNumber}: unit “${unit.code}” belongs to another department"
                            return@forEach
                        }

                        if (unit == null) {
                            unit = unitRepo.save(
                                AcademicUnit(
                                    tenantId = tenantId,
                                    code = code,
                                    name = unitName ?: code,
                                    sourceDepartmentId = org.id,
                                    contactHours = hours ?: BigDecimal("3"),
                                    studentCount = 40,
                                    academicYearId = yearId,
                                    semesterId = semesterId,
                                    requiredExpertise = "General",
                                    status = "Allocated"
                                )
                            )
                            unitsCreated++
                        } else if (hours != null) {
                            unit.contactHours = hours
                            unit.status = "Allocated"
                            unitRepo.save(unit)
                        }

                        val existingAlloc = allocationRepo.findByTenantIdAndAcademicUnitId(tenantId, unit.id)
                            .firstOrNull { it.lecturerId == lecturer.id && it.status !in listOf("CANCELLED") }
                        if (existingAlloc != null) {
                            duplicatesSkipped++
                        } else {
                            allocationRepo.findByTenantIdAndAcademicUnitId(tenantId, unit.id)
                                .filter { it.status !in listOf("PUBLISHED", "APPROVED") && it.lecturerId != lecturer.id }
                                .forEach {
                                    it.status = "SUPERSEDED"
                                    it.updatedAt = Instant.now()
                                    allocationRepo.save(it)
                                }
                            allocationRepo.save(
                                Allocation(
                                    tenantId = tenantId,
                                    academicUnitId = unit.id,
                                    lecturerId = lecturer.id,
                                    matchScore = BigDecimal("100"),
                                    status = "ASSIGNED",
                                    overrideReason = "Imported allocation"
                                )
                            )
                            if (hours != null) {
                                lecturer.currentWorkload = lecturer.currentWorkload.add(hours)
                                lecturerRepo.save(lecturer)
                            }
                            allocationsCreated++
                        }
                    }
                }
            } catch (ex: Exception) {
                errors++
                details += "Row ${row.rowNumber}: ${ex.message ?: "failed"}"
            }
        }

        return ImportResultSummaryDto(
            allocations = allocationsCreated,
            lecturers = lecturersCreated,
            units = unitsCreated,
            warnings = warnings,
            errors = errors,
            duplicatesSkipped = duplicatesSkipped,
            unmappedFields = unmappedFields,
            details = details.take(40)
        )
    }

    /**
     * Resolve department for an import row.
     * Department-scoped users: blank department → active/home dept; named other dept → null (reject).
     */
    private fun resolveOrgForImport(
        orgs: List<OrganizationNode>,
        deptName: String?,
        defaultOrg: OrganizationNode,
        allowedDeptIds: Set<UUID>?
    ): OrganizationNode? {
        if (deptName.isNullOrBlank()) {
            return if (allowedDeptIds == null || defaultOrg.id in allowedDeptIds) defaultOrg else null
        }
        val matched = orgs.firstOrNull { it.name.equals(deptName, true) }
            ?: orgs.firstOrNull { ImportFieldMapper.normalize(it.name) == ImportFieldMapper.normalize(deptName) }
        if (matched == null) {
            // Unknown department label — for chairs, do not silently fall back to another dept.
            return if (allowedDeptIds == null) defaultOrg else null
        }
        if (allowedDeptIds != null && matched.id !in allowedDeptIds) return null
        return matched
    }

    private fun resolveOrg(
        orgs: List<OrganizationNode>,
        deptName: String?,
        defaultOrg: OrganizationNode
    ): OrganizationNode {
        if (deptName.isNullOrBlank()) return defaultOrg
        return orgs.firstOrNull { it.name.equals(deptName, true) }
            ?: orgs.firstOrNull { ImportFieldMapper.normalize(it.name) == ImportFieldMapper.normalize(deptName) }
            ?: defaultOrg
    }

    private fun parseRaw(rawJson: String): Map<String, String> =
        rawJson.split(";").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

    private fun toImportSessionDto(session: ImportSession) = ImportSessionDto(
        id = session.id,
        fileName = session.fileName,
        entityType = session.entityType,
        status = session.status,
        columnMap = session.columnMap,
        createdAt = session.createdAt,
        resultSummary = decodeSummary(session.resultSummary)
    )

    private fun encodeSummary(s: ImportResultSummaryDto): String =
        listOf(
            "allocations=${s.allocations}",
            "lecturers=${s.lecturers}",
            "units=${s.units}",
            "warnings=${s.warnings}",
            "errors=${s.errors}",
            "duplicatesSkipped=${s.duplicatesSkipped}",
            "unmappedFields=${s.unmappedFields}",
            "details=${s.details.joinToString(" || ")}"
        ).joinToString("|")

    private fun decodeSummary(raw: String?): ImportResultSummaryDto? {
        if (raw.isNullOrBlank()) return null
        fun num(key: String): Int {
            val token = raw.split("|").firstOrNull {
                it.startsWith("$key=") && !it.startsWith("details=")
            } ?: return 0
            return token.substringAfter("=").toIntOrNull() ?: 0
        }
        // details is last and may contain "|" from " || " separators — take remainder after marker
        val details = raw.substringAfter("details=", "")
            .split(" || ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return ImportResultSummaryDto(
            allocations = num("allocations"),
            lecturers = num("lecturers"),
            units = num("units"),
            warnings = num("warnings"),
            errors = num("errors"),
            duplicatesSkipped = num("duplicatesSkipped"),
            unmappedFields = num("unmappedFields"),
            details = details
        )
    }

    private fun resolvePeriod(tenantId: UUID, yearLabel: String?, semesterName: String?): Pair<UUID?, UUID?> {
        if (yearLabel.isNullOrBlank() && semesterName.isNullOrBlank()) {
            val years = yearRepo.findByTenantIdOrderByLabelDesc(tenantId)
            val year = years.firstOrNull()
            val sem = year?.let {
                semesterRepo.findByTenantIdAndAcademicYearIdOrderBySequenceNoAsc(tenantId, it.id).firstOrNull()
            }
            return year?.id to sem?.id
        }
        val label = yearLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "Imported"
        var year = yearRepo.findByTenantIdOrderByLabelDesc(tenantId).firstOrNull { it.label.equals(label, true) }
        if (year == null) {
            year = yearRepo.save(AcademicYear(tenantId = tenantId, label = label))
        }
        val semLabel = semesterName?.trim()?.takeIf { it.isNotEmpty() } ?: "Semester 1"
        var sem = semesterRepo.findByTenantIdAndAcademicYearIdOrderBySequenceNoAsc(tenantId, year.id)
            .firstOrNull { it.name.equals(semLabel, true) }
        if (sem == null) {
            val seq = when {
                semLabel.contains("2") -> 2
                semLabel.contains("3") -> 3
                else -> 1
            }
            sem = semesterRepo.save(
                Semester(tenantId = tenantId, academicYearId = year.id, name = semLabel, sequenceNo = seq)
            )
        }
        return year.id to sem.id
    }

    private fun defaultImportRows(entityType: String): List<Map<String, String>> =
        when (entityType.uppercase()) {
            "ACADEMIC_UNIT" -> listOf(
                mapOf(
                    "Course Code" to "CSC 499",
                    "Course Name" to "Special Topics",
                    "Department" to "Computer Science",
                    "Teaching Hours" to "3",
                    "Students" to "45",
                    "Academic Year" to "2026/2027",
                    "Semester" to "Semester 1"
                )
            )
            "ALLOCATION" -> listOf(
                mapOf(
                    "Staff No." to "STF/2001",
                    "Lecturer Name" to "Dr. Jane Example",
                    "Course Code" to "BIT 401",
                    "Course Title" to "Databases",
                    "Hrs" to "3",
                    "Semester" to "Semester 1"
                )
            )
            else -> listOf(
                mapOf(
                    "Staff No" to "IMP/1001",
                    "Staff Name" to "Dr. Imported Lecturer",
                    "Department" to "Mathematics",
                    "Email" to "imported.lecturer@academicflow.local",
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

    /** Resolve department by id or free-text name; create a Department node when the name is new. */
    private fun resolveDepartmentId(tenantId: UUID, id: UUID?, name: String?): UUID {
        if (id != null) {
            val byId = orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == id }
            if (byId != null) return byId.id
        }
        val raw = name?.trim().orEmpty()
        require(raw.isNotBlank()) { "Department is required — type a department name" }
        parseUuid(raw)?.let { uuid ->
            val byId = orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == uuid }
            if (byId != null) return byId.id
        }
        val nodes = orgRepo.findByTenantIdOrderByNameAsc(tenantId)
        val existing = nodes.firstOrNull {
            it.type.equals("Department", true) && it.name.equals(raw, ignoreCase = true)
        } ?: nodes.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (existing != null) return existing.id
        val parentId = nodes.firstOrNull {
            it.type.equals("University", true) || it.type.equals("School", true) || it.type.equals("Faculty", true)
        }?.id
        val created = orgRepo.save(
            OrganizationNode(tenantId = tenantId, name = raw, type = "Department", parentId = parentId)
        )
        audit("Organization node created", "OrganizationNode", created.id.toString(), "Auto-created from typed department: $raw")
        return created.id
    }

    private fun resolveOrgParentId(tenantId: UUID, id: UUID?, name: String?): UUID? {
        if (id != null) {
            val byId = orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == id }
            if (byId != null) return byId.id
        }
        val raw = name?.trim().orEmpty()
        if (raw.isBlank()) return null
        parseUuid(raw)?.let { uuid ->
            return orgRepo.findByTenantIdOrderByNameAsc(tenantId).firstOrNull { it.id == uuid }?.id
        }
        return orgRepo.findByTenantIdOrderByNameAsc(tenantId)
            .firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    "${it.name} (${it.type})".equals(raw, ignoreCase = true)
            }
            ?.id
    }

    /**
     * Resolve academic unit by id, code, "CODE — Name", or free name.
     * Creates a unit under [fallbackDepartmentId] when the typed value is new.
     */
    private fun resolveAcademicUnitId(
        tenantId: UUID,
        id: UUID?,
        typed: String?,
        fallbackDepartmentId: UUID
    ): UUID {
        if (id != null) {
            unitRepo.findByTenantIdAndId(tenantId, id)?.let { return it.id }
        }
        val raw = typed?.trim().orEmpty()
        require(raw.isNotBlank()) { "Academic unit is required — type a unit code or name" }
        parseUuid(raw)?.let { uuid ->
            unitRepo.findByTenantIdAndId(tenantId, uuid)?.let { return it.id }
        }
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId)
        val byCode = units.firstOrNull { it.code.equals(raw, ignoreCase = true) }
        if (byCode != null) return byCode.id
        val byLabel = units.firstOrNull {
            "${it.code} — ${it.name}".equals(raw, ignoreCase = true) ||
                it.name.equals(raw, ignoreCase = true)
        }
        if (byLabel != null) return byLabel.id

        val parts = raw.split("—", "–", "-", limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
        val code = if (parts.size >= 2) parts[0].take(32) else raw.take(16).uppercase().replace(Regex("\\s+"), "-")
        val unitName = if (parts.size >= 2) parts[1] else raw
        val created = unitRepo.save(
            AcademicUnit(
                tenantId = tenantId,
                code = code,
                name = unitName,
                sourceDepartmentId = fallbackDepartmentId,
                contactHours = BigDecimal("3"),
                studentCount = 0,
                status = "Pending"
            )
        )
        audit("Academic unit created", "AcademicUnit", created.id.toString(), "Auto-created from typed unit: $raw")
        return created.id
    }

    private fun parseUuid(value: String): UUID? =
        runCatching { UUID.fromString(value.trim()) }.getOrNull()
}
