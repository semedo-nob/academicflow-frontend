package com.academicflow.controller

import com.academicflow.dto.*
import com.academicflow.service.AcademicFlowService
import com.academicflow.service.AdminConfigService
import com.academicflow.service.CourseOfferingService
import com.academicflow.service.ExportService
import com.academicflow.service.FileImportService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api")
class ApiControllers(
    private val service: AcademicFlowService,
    private val admin: AdminConfigService,
    private val fileImportService: FileImportService,
    private val exportService: ExportService,
    private val courseOfferingService: CourseOfferingService,
    private val authIdentityService: com.academicflow.service.auth.AuthIdentityService,
    private val permissionService: com.academicflow.service.permission.PermissionService
) {

    @PostMapping("/auth/login")
    fun login(@RequestBody req: LoginRequest) = try {
        if (authIdentityService.clerkEnabled() && !authIdentityService.legacyEnabled()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Password login is disabled. Sign in with Clerk."
            )
        }
        service.login(req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @PostMapping("/auth/session")
    fun establishClerkSession() = try {
        val identity = com.academicflow.config.ClerkContext.get()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clerk session required")
        authIdentityService.establishSessionFromClerk(identity)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/auth/mode")
    fun authMode() = mapOf(
        "clerkEnabled" to authIdentityService.clerkEnabled(),
        "legacyEnabled" to authIdentityService.legacyEnabled()
    )

    @PostMapping("/auth/register-institution")
    fun registerInstitution(@RequestBody req: CreateInstitutionRequest) = try {
        admin.registerInstitution(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/auth/invitations/{token}")
    fun previewInvitation(@PathVariable token: String) = try {
        admin.previewInvitation(token)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/auth/accept-invitation")
    fun acceptInvitation(@RequestBody req: AcceptInvitationRequest) = try {
        admin.acceptInvitation(req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/auth/me")
    fun me() = try {
        service.me()
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
    }

    @GetMapping("/dashboard")
    fun dashboard() = service.dashboard()

    @GetMapping("/search")
    fun search(@RequestParam q: String) = fileImportService.search(q)

    @GetMapping("/organization")
    fun organization() = service.organizationTree()

    @PostMapping("/organization")
    fun createOrg(@RequestBody req: CreateOrgNodeRequest) = try {
        service.createOrgNode(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @PutMapping("/organization/{id}")
    fun updateOrg(@PathVariable id: UUID, @RequestBody req: UpdateOrgNodeRequest) = try {
        service.updateOrgNode(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @DeleteMapping("/organization/{id}")
    fun deleteOrg(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "false") cascade: Boolean
    ) = try {
        service.deleteOrgNode(id, cascade)
        mapOf("ok" to true, "id" to id, "cascade" to cascade)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @PostMapping("/organization/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun importOrganization(
        @RequestParam(value = "file", required = false) file: MultipartFile?,
        @RequestBody(required = false) body: String?
    ) = try {
        val text = when {
            file != null && !file.isEmpty -> file.bytes.toString(Charsets.UTF_8)
            !body.isNullOrBlank() -> body
            else -> throw IllegalArgumentException("Provide a CSV file (multipart field 'file') or plain-text body")
        }
        service.importOrganizationNodes(text)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @GetMapping("/lecturers")
    fun lecturers() = service.listLecturers()

    @PostMapping("/lecturers")
    fun createLecturer(@RequestBody req: CreateLecturerRequest) = service.createLecturer(req)

    @GetMapping("/academic-units")
    fun units() = service.listUnits()

    @PostMapping("/academic-units")
    fun createUnit(@RequestBody req: CreateUnitRequest) = service.createUnit(req)

    @GetMapping("/requests")
    fun requests() = service.listRequests()

    @GetMapping("/requests/{id}")
    fun requestDetail(@PathVariable id: UUID) = try {
        service.getRequestDetail(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/requests")
    fun createRequest(@RequestBody req: CreateTeachingRequest) = try {
        service.createRequest(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/requests/{id}/respond")
    fun respondToRequest(@PathVariable id: UUID, @RequestBody req: RespondTeachingRequest) = try {
        service.respondToRequest(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @PostMapping("/requests/{id}/attachments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadRequestAttachment(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "docType", required = false) docType: String?
    ) = try {
        service.uploadRequestAttachment(id, file, docType)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/request-attachments/{id}/download")
    fun downloadRequestAttachment(@PathVariable id: UUID): ResponseEntity<ByteArray> = try {
        val (attachment, bytes) = service.downloadRequestAttachment(id)
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${attachment.fileName}\"")
            .contentType(MediaType.parseMediaType(attachment.contentType ?: "application/octet-stream"))
            .body(bytes)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/requests/{id}/messages")
    fun postRequestMessage(@PathVariable id: UUID, @RequestBody req: CreateRequestMessage) = try {
        service.postRequestMessage(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/requests/{id}/candidates")
    fun candidates(@PathVariable id: UUID) = service.getCandidates(id)

    @PostMapping("/requests/{id}/candidates")
    fun findCandidates(@PathVariable id: UUID) = service.findCandidates(id)

    @GetMapping("/allocations")
    fun allocations() = service.listAllocations()

    @PostMapping("/allocations")
    fun createAllocation(@RequestBody req: CreateAllocationRequest) = try {
        service.createAllocation(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/allocations/{id}/submit")
    fun submit(@PathVariable id: UUID) = try {
        service.submitForApproval(id)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
    }

    @PostMapping("/allocations/{id}/approve")
    fun approve(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "true") approve: Boolean,
        @RequestParam(required = false) note: String?
    ) = try {
        service.approveAllocation(id, approve, note)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
    }

    @GetMapping("/allocations/{id}/comments")
    fun allocationComments(@PathVariable id: UUID) = try {
        service.listAllocationComments(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/allocations/{id}/comments")
    fun addAllocationComment(@PathVariable id: UUID, @RequestBody req: CreateAllocationCommentRequest) = try {
        service.addAllocationComment(id, req.body, req.commentType)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/approvals")
    fun approvals() = service.listApprovals()

    @GetMapping("/me/permissions")
    fun myPermissions() =
        mapOf(
            "permissions" to permissionService.effectiveForCurrentUser().sorted(),
            "catalog" to com.academicflow.service.permission.PermissionCodes.ALL.sorted(),
            "templates" to com.academicflow.service.permission.PermissionCodes.TEMPLATES.mapValues { it.value.sorted() }
        )

    @GetMapping("/permissions/catalog")
    fun permissionCatalog() = mapOf(
        "permissions" to com.academicflow.service.permission.PermissionCodes.ALL.sorted(),
        "templates" to com.academicflow.service.permission.PermissionCodes.TEMPLATES.mapValues { it.value.sorted() },
        "roleDefaults" to listOf(
            "SUPER_ADMIN", "INSTITUTION_ADMIN", "DEPARTMENT_CHAIR", "LECTURER", "STUDENT", "STAFF", "VIEWER"
        ).associateWith { com.academicflow.service.permission.PermissionCodes.defaultsForRole(it).sorted() }
    )

    @GetMapping("/conflicts")
    fun conflicts() = service.listConflicts()

    @GetMapping("/audit")
    fun audit() = service.auditLogs()

    @GetMapping("/imports")
    fun imports() = service.listImportSessions()

    @PostMapping("/imports")
    fun createImport(@RequestBody req: CreateImportSessionRequest) = service.createImportSession(req)

    @PostMapping("/imports/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImport(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) entityType: String?
    ) = fileImportService.upload(file, entityType)

    @PostMapping("/imports/{id}/mapping")
    fun updateImportMapping(@PathVariable id: UUID, @RequestBody req: UpdateImportMappingRequest) = try {
        service.updateImportMapping(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/imports/{id}/advance")
    fun advanceImport(@PathVariable id: UUID) = try {
        service.advanceImportSession(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/imports/{id}/reprocess")
    fun reprocessImport(@PathVariable id: UUID) = try {
        service.reprocessImportSession(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/imports/{id}/rows")
    fun importRows(@PathVariable id: UUID) = try {
        service.listImportRows(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/users")
    fun users() = service.listUsers()

    @GetMapping("/timetable")
    fun timetable() = service.listTimetable()

    @PostMapping("/timetable")
    fun createTimetable(@RequestBody req: CreateTimetableEntryRequest) = service.createTimetableEntry(req)

    @GetMapping("/workload")
    fun workload() = service.workloadSummary()

    @PostMapping("/conflicts/{id}/resolve")
    fun resolveConflict(@PathVariable id: UUID) = try {
        service.resolveConflict(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/reports/{type}")
    fun report(@PathVariable type: String) = service.reportSummary(type)

    @GetMapping("/exports/{kind}/{format}")
    fun export(
        @PathVariable kind: String,
        @PathVariable format: String
    ) = try {
        exportService.export(kind, format)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/course-offerings")
    fun courseOfferings() = courseOfferingService.listOfferings()

    @GetMapping("/course-offerings/{id}")
    fun courseOffering(@PathVariable id: UUID) = try {
        courseOfferingService.getOffering(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/course-offerings")
    fun createCourseOffering(@RequestBody req: CreateCourseOfferingRequest) = try {
        courseOfferingService.createOffering(req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PutMapping("/course-offerings/{id}/requirements")
    fun replaceRequirements(@PathVariable id: UUID, @RequestBody body: List<RequirementInputDto>) = try {
        courseOfferingService.replaceRequirements(id, body)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/course-offerings/{id}/outline", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadOutline(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile
    ) = try {
        courseOfferingService.uploadOutline(id, file)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/course-outlines/{id}/download")
    fun downloadOutline(@PathVariable id: UUID): ResponseEntity<ByteArray> = try {
        val (outline, bytes) = courseOfferingService.downloadOutline(id)
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${outline.fileName}\"")
            .contentType(MediaType.parseMediaType(outline.contentType ?: "application/octet-stream"))
            .body(bytes)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/course-offerings/{id}/suitability")
    fun suitability(@PathVariable id: UUID) = try {
        courseOfferingService.suitabilityForOffering(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/course-offerings/allocate")
    fun allocateOffering(@RequestBody req: AllocateFromOfferingRequest) = try {
        courseOfferingService.allocateFromOffering(req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }
}

@RestController
@RequestMapping("/api/admin")
class AdminControllers(private val admin: AdminConfigService, private val service: AcademicFlowService) {

    @GetMapping("/institutions")
    fun institutions() = admin.listInstitutions()

    @GetMapping("/platform/overview")
    fun platformOverview() = admin.platformOverview()

    @PostMapping("/institutions/{id}/decision")
    fun decideInstitution(
        @PathVariable id: UUID,
        @RequestBody req: InstitutionDecisionRequest
    ) = try {
        admin.decideInstitution(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/institutions/{id}/status")
    fun setInstitutionStatus(
        @PathVariable id: UUID,
        @RequestBody req: InstitutionStatusRequest
    ) = try {
        admin.setInstitutionStatus(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/organization-types")
    fun organizationTypes() = admin.listOrganizationTypes()

    @GetMapping("/roles")
    fun roles() = admin.listRoles()

    @GetMapping("/academic-years")
    fun years() = admin.listYears()

    @PostMapping("/academic-years")
    fun createYear(@RequestBody req: CreateAcademicYearRequest) = admin.createYear(req)

    @GetMapping("/semesters")
    fun semesters(@RequestParam(required = false) academicYearId: UUID?) = admin.listSemesters(academicYearId)

    @PostMapping("/semesters")
    fun createSemester(@RequestBody req: CreateSemesterRequest) = admin.createSemester(req)

    @GetMapping("/period")
    fun period() = admin.periodContext()

    @GetMapping("/rules")
    fun rules() = admin.listRules()

    @PutMapping("/rules/{id}")
    fun updateRule(@PathVariable id: UUID, @RequestBody req: UpdateRuleRequest) = try {
        admin.updateRule(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/settings")
    fun settings() = admin.listSettings()

    @PutMapping("/settings/{id}")
    fun updateSetting(@PathVariable id: UUID, @RequestBody req: UpdateSettingRequest) = try {
        admin.updateSetting(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/mapping-profiles")
    fun mappingProfiles() = admin.listMappingProfiles()

    @PostMapping("/mapping-profiles")
    fun createMapping(@RequestBody req: CreateMappingProfileRequest) = admin.createMappingProfile(req)

    @PostMapping("/users")
    fun createUser(@RequestBody req: CreateUserRequest) = try {
        admin.createUser(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/invitations")
    fun invitations() = admin.listInvitations()

    @PostMapping("/invitations")
    fun createInvitation(@RequestBody req: CreateInvitationRequest) = try {
        admin.createInvitation(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/invitations/{id}/resend")
    fun resendInvitation(@PathVariable id: UUID) = try {
        admin.resendInvitation(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/invitations/{id}/rotate-link")
    fun rotateInvitationLink(@PathVariable id: UUID) = try {
        admin.rotateInvitationLink(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/invitations/{id}/revoke")
    fun revokeInvitation(@PathVariable id: UUID) = try {
        admin.revokeInvitation(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PutMapping("/users/{id}")
    fun updateUser(@PathVariable id: UUID, @RequestBody req: UpdateUserRequest) = try {
        admin.updateUser(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/memberships")
    fun memberships(@RequestParam(required = false) departmentId: UUID?) = admin.listMemberships(departmentId)

    @PostMapping("/memberships")
    fun createMembership(@RequestBody req: CreateMembershipRequest) = try {
        admin.createMembership(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/departments/assign-chair")
    fun assignChair(@RequestBody req: AssignChairRequest) = try {
        admin.assignDepartmentChair(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }
}
