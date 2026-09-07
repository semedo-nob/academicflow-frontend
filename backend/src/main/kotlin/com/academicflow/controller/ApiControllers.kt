package com.academicflow.controller

import com.academicflow.dto.*
import com.academicflow.service.AcademicFlowService
import com.academicflow.service.AdminConfigService
import com.academicflow.service.ExportService
import com.academicflow.service.FileImportService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
    private val exportService: ExportService
) {

    @PostMapping("/auth/login")
    fun login(@RequestBody req: LoginRequest) = try {
        service.login(req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
    } catch (e: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
    }

    @PostMapping("/auth/register-institution")
    fun registerInstitution(@RequestBody req: CreateInstitutionRequest) = try {
        admin.registerInstitution(req)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/dashboard")
    fun dashboard() = service.dashboard()

    @GetMapping("/search")
    fun search(@RequestParam q: String) = fileImportService.search(q)

    @GetMapping("/organization")
    fun organization() = service.organizationTree()

    @PostMapping("/organization")
    fun createOrg(@RequestBody req: CreateOrgNodeRequest) = service.createOrgNode(req)

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

    @PostMapping("/requests")
    fun createRequest(@RequestBody req: CreateTeachingRequest) = service.createRequest(req)

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
    ) = service.approveAllocation(id, approve, note)

    @GetMapping("/approvals")
    fun approvals() = service.listApprovals()

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
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) entityType: String?
    ) = try {
        fileImportService.upload(file, entityType)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PostMapping("/imports/{id}/advance")
    fun advanceImport(@PathVariable id: UUID) = try {
        service.advanceImportSession(id)
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

    @PutMapping("/users/{id}")
    fun updateUser(@PathVariable id: UUID, @RequestBody req: UpdateUserRequest) = try {
        admin.updateUser(id, req)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/imports/{id}/rows")
    fun importRows(@PathVariable id: UUID) = try {
        service.listImportRows(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }
}
