package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.dto.*
import com.academicflow.entity.*
import com.academicflow.repository.*
import com.academicflow.service.ingestion.DocumentErrorCode
import com.academicflow.service.ingestion.DocumentIngestionException
import com.academicflow.service.ingestion.DocumentIngestionService
import com.academicflow.service.ingestion.ProcessingStatus
import com.academicflow.service.suitability.SuitabilityEngine
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class CourseOfferingService(
    private val offeringRepo: CourseOfferingRepository,
    private val outlineRepo: CourseOutlineRepository,
    private val requirementRepo: CourseRequirementRepository,
    private val experienceRepo: LecturerContextExperienceRepository,
    private val unitRepo: AcademicUnitRepository,
    private val lecturerRepo: LecturerRepository,
    private val expertiseRepo: LecturerExpertiseRepository,
    private val orgRepo: OrganizationNodeRepository,
    private val allocationRepo: AllocationRepository,
    private val requestRepo: TeachingRequestRepository,
    private val conflictRepo: ConflictRepository,
    private val auditRepo: AuditLogRepository,
    private val documentIngestion: DocumentIngestionService,
    private val scopeService: ScopeService
) {
    companion object {
        private const val MAX_OUTLINE_BYTES = 20 * 1024 * 1024
    }

    fun listOfferings(): List<CourseOfferingDto> {
        val tenantId = TenantContext.get()
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val allowed = scopeService.authorizedDepartmentIds()
        return offeringRepo.findByTenantIdOrderByDisplayTitleAsc(tenantId)
            .filter { o ->
                if (allowed == null) true
                else o.requestingDepartmentId in allowed ||
                    o.owningDepartmentId in allowed ||
                    units[o.academicUnitId]?.sourceDepartmentId in allowed
            }
            .map { o ->
            toOfferingDto(o, units[o.academicUnitId], orgs)
        }
    }

    fun getOffering(id: UUID): CourseOfferingDetailDto {
        val tenantId = TenantContext.get()
        val o = offeringRepo.findByTenantIdAndId(tenantId, id) ?: throw NoSuchElementException("Course offering not found")
        val unit = unitRepo.findByTenantIdAndId(tenantId, o.academicUnitId)
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val reqs = requirementRepo.findByTenantIdAndCourseOfferingIdOrderByRequirementTypeAscLabelAsc(tenantId, id)
        val outlines = outlineRepo.findByTenantIdAndCourseOfferingIdOrderByVersionNoDesc(tenantId, id)
        return CourseOfferingDetailDto(
            offering = toOfferingDto(o, unit, orgs),
            requirements = reqs.map { CourseRequirementDto(it.id, it.requirementType, it.label, it.weight, it.source) },
            outlines = outlines.map { toOutlineDto(it) }
        )
    }

    @Transactional
    fun createOffering(req: CreateCourseOfferingRequest): CourseOfferingDto {
        val tenantId = TenantContext.get()
        unitRepo.findByTenantIdAndId(tenantId, req.academicUnitId) ?: throw NoSuchElementException("Academic unit not found")
        val saved = offeringRepo.save(
            CourseOffering(
                tenantId = tenantId,
                academicUnitId = req.academicUnitId,
                programme = req.programme,
                contextLabel = req.contextLabel,
                levelLabel = req.levelLabel,
                displayTitle = req.displayTitle,
                academicYearId = req.academicYearId,
                semesterId = req.semesterId,
                requestingDepartmentId = req.requestingDepartmentId,
                owningDepartmentId = req.owningDepartmentId,
                status = "ACTIVE"
            )
        )
        req.requirements.forEach { r ->
            requirementRepo.save(
                CourseRequirement(
                    tenantId = tenantId,
                    courseOfferingId = saved.id,
                    requirementType = r.type.uppercase(),
                    label = r.label.trim(),
                    weight = r.weight ?: BigDecimal.ONE,
                    source = "MANUAL"
                )
            )
        }
        audit("Course offering created", "CourseOffering", saved.id.toString(), saved.displayTitle)
        val units = unitRepo.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        return toOfferingDto(saved, units[saved.academicUnitId], orgs)
    }

    @Transactional
    fun replaceRequirements(offeringId: UUID, requirements: List<RequirementInputDto>): CourseOfferingDetailDto {
        val tenantId = TenantContext.get()
        offeringRepo.findByTenantIdAndId(tenantId, offeringId) ?: throw NoSuchElementException("Course offering not found")
        requirementRepo.deleteByTenantIdAndCourseOfferingIdAndSource(tenantId, offeringId, "MANUAL")
        requirements.forEach { r ->
            if (r.label.isNotBlank()) {
                requirementRepo.save(
                    CourseRequirement(
                        tenantId = tenantId,
                        courseOfferingId = offeringId,
                        requirementType = r.type.uppercase(),
                        label = r.label.trim(),
                        weight = r.weight ?: BigDecimal.ONE,
                        source = "MANUAL"
                    )
                )
            }
        }
        audit("Course requirements updated", "CourseOffering", offeringId.toString(), "${requirements.size} items")
        return getOffering(offeringId)
    }

    @Transactional
    fun uploadOutline(offeringId: UUID, file: MultipartFile): CourseOutlineDto {
        val tenantId = TenantContext.get()
        offeringRepo.findByTenantIdAndId(tenantId, offeringId)
            ?: throw DocumentIngestionException(DocumentErrorCode.MISSING_COURSE_CONTEXT, "Course offering not found")
        if (file.isEmpty) {
            throw DocumentIngestionException(DocumentErrorCode.MISSING_FILE, "Empty outline file")
        }
        if (file.size > MAX_OUTLINE_BYTES) {
            throw DocumentIngestionException(DocumentErrorCode.FILE_TOO_LARGE, "Outline exceeds 20 MB limit")
        }

        val bytes = file.bytes
        val ingestion = documentIngestion.ingest(file.originalFilename, file.contentType, bytes)

        // Password-protected PDFs: store original, clear review status — do not pretend upload failed.
        if (ingestion.errorCode == DocumentErrorCode.PDF_PASSWORD_PROTECTED) {
            // fall through to persist
        }

        val nextVersion = (outlineRepo.findByTenantIdAndCourseOfferingIdOrderByVersionNoDesc(tenantId, offeringId)
            .firstOrNull()?.versionNo ?: 0) + 1

        val saved = outlineRepo.save(
            CourseOutline(
                tenantId = tenantId,
                courseOfferingId = offeringId,
                fileName = ingestion.safeFileName,
                contentType = file.contentType ?: ingestion.detectedContentType,
                detectedContentType = ingestion.detectedContentType,
                fileExtension = ingestion.extension,
                fileSizeBytes = ingestion.fileSizeBytes,
                checksumSha256 = ingestion.checksumSha256,
                fileBytes = bytes,
                extractedText = ingestion.text.ifBlank { null },
                extractedJson = "topics=${ingestion.topics.joinToString(",")}|outcomes=${ingestion.outcomes.joinToString(",")}|prereqs=${ingestion.prerequisites.joinToString(",")}",
                rawExtractionJson = ingestion.rawJson,
                extractionConfidence = ingestion.confidence,
                needsReview = ingestion.needsReview,
                processingStatus = ingestion.processingStatus.name,
                extractionMethod = ingestion.extractionMethod,
                processingMessage = ingestion.processingMessage,
                processingErrorCode = ingestion.errorCode?.name,
                versionNo = nextVersion
            )
        )

        // Replace EXTRACTED requirements only when we have usable text — never wipe manual on OCR_REQUIRED blank.
        if (ingestion.text.isNotBlank() &&
            ingestion.processingStatus != ProcessingStatus.FAILED &&
            ingestion.errorCode != DocumentErrorCode.PDF_PASSWORD_PROTECTED
        ) {
            requirementRepo.deleteByTenantIdAndCourseOfferingIdAndSource(tenantId, offeringId, "EXTRACTED")
            ingestion.topics.forEach {
                requirementRepo.save(
                    CourseRequirement(
                        tenantId = tenantId,
                        courseOfferingId = offeringId,
                        requirementType = "TOPIC",
                        label = it,
                        source = "EXTRACTED"
                    )
                )
            }
            ingestion.outcomes.forEach {
                requirementRepo.save(
                    CourseRequirement(
                        tenantId = tenantId,
                        courseOfferingId = offeringId,
                        requirementType = "OUTCOME",
                        label = it,
                        source = "EXTRACTED"
                    )
                )
            }
            ingestion.prerequisites.forEach {
                requirementRepo.save(
                    CourseRequirement(
                        tenantId = tenantId,
                        courseOfferingId = offeringId,
                        requirementType = "PREREQUISITE",
                        label = it,
                        source = "EXTRACTED"
                    )
                )
            }
        }

        audit(
            "Course outline uploaded",
            "CourseOutline",
            saved.id.toString(),
            "${ingestion.safeFileName} v$nextVersion status=${ingestion.processingStatus} method=${ingestion.extractionMethod} confidence=${ingestion.confidence}"
        )
        return toOutlineDto(saved, ingestion.warnings)
    }

    private fun toOutlineDto(o: CourseOutline, warnings: List<String> = emptyList()) = CourseOutlineDto(
        id = o.id,
        fileName = o.fileName,
        contentType = o.contentType,
        detectedContentType = o.detectedContentType,
        fileExtension = o.fileExtension,
        fileSizeBytes = o.fileSizeBytes,
        checksumSha256 = o.checksumSha256,
        versionNo = o.versionNo,
        extractionConfidence = o.extractionConfidence,
        needsReview = o.needsReview,
        processingStatus = o.processingStatus,
        extractionMethod = o.extractionMethod,
        processingMessage = o.processingMessage,
        processingErrorCode = o.processingErrorCode,
        createdAt = o.createdAt.toString(),
        hasFile = o.fileBytes != null,
        warnings = warnings
    )

    fun downloadOutline(outlineId: UUID): Pair<CourseOutline, ByteArray> {
        val tenantId = TenantContext.get()
        val outline = outlineRepo.findByTenantIdAndId(tenantId, outlineId)
            ?: throw NoSuchElementException("Outline not found")
        val bytes = outline.fileBytes ?: throw NoSuchElementException("Outline file missing")
        return outline to bytes
    }

    fun suitabilityForOffering(offeringId: UUID): List<SuitabilityCandidateDto> {
        val tenantId = TenantContext.get()
        val o = offeringRepo.findByTenantIdAndId(tenantId, offeringId) ?: throw NoSuchElementException("Course offering not found")
        val unit = unitRepo.findByTenantIdAndId(tenantId, o.academicUnitId) ?: throw NoSuchElementException("Unit not found")
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        val reqs = requirementRepo.findByTenantIdAndCourseOfferingIdOrderByRequirementTypeAscLabelAsc(tenantId, offeringId)
        val allExp = expertiseRepo.findByTenantId(tenantId).groupBy { it.lecturerId }
        val allCtx = experienceRepo.findByTenantId(tenantId).groupBy { it.lecturerId }
        val offeringInput = SuitabilityEngine.OfferingInput(
            unitCode = unit.code,
            unitName = unit.name,
            programme = o.programme,
            contextLabel = o.contextLabel,
            levelLabel = o.levelLabel,
            owningDepartment = o.owningDepartmentId?.let { orgs[it]?.name },
            requestingDepartment = o.requestingDepartmentId?.let { orgs[it]?.name },
            requirements = reqs.map { SuitabilityEngine.Requirement(it.requirementType, it.label, it.weight.toDouble()) }
        )

        return lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
            .filter { it.status.equals("Active", true) }
            .map { lec ->
                val result = SuitabilityEngine.score(
                    offeringInput,
                    SuitabilityEngine.LecturerInput(
                        id = lec.id.toString(),
                        name = lec.fullName,
                        department = orgs[lec.organizationNodeId]?.name,
                        currentWorkload = lec.currentWorkload.toDouble(),
                        maximumWorkload = lec.maximumWorkload.toDouble(),
                        contactHoursNeeded = unit.contactHours.toDouble(),
                        expertise = allExp[lec.id].orEmpty().map { SuitabilityEngine.Expertise(it.subject, it.level) },
                        contextExperience = allCtx[lec.id].orEmpty().map {
                            SuitabilityEngine.ContextExp(it.contextLabel, it.programme, it.levelLabel, it.topicLabel, it.unitCode, it.timesTaught)
                        }
                    )
                )
                SuitabilityCandidateDto(
                    lecturerId = lec.id,
                    name = lec.fullName,
                    department = orgs[lec.organizationNodeId]?.name ?: "",
                    score = result.score,
                    classification = result.classification,
                    positives = result.positives,
                    warnings = result.warnings,
                    missingEvidence = result.missingEvidence,
                    breakdown = result.breakdown.map {
                        SuitabilityBreakdownDto(it.key, it.label, it.earned, it.max)
                    },
                    crossDepartment = result.crossDepartment,
                    load = "${lec.currentWorkload.stripTrailingZeros().toPlainString()} / ${lec.maximumWorkload.stripTrailingZeros().toPlainString()} hrs"
                )
            }
            .sortedByDescending { it.score }
    }

    @Transactional
    fun allocateFromOffering(req: AllocateFromOfferingRequest): AllocationDto {
        val tenantId = TenantContext.get()
        val o = offeringRepo.findByTenantIdAndId(tenantId, req.courseOfferingId)
            ?: throw NoSuchElementException("Course offering not found")
        val unit = unitRepo.findByTenantIdAndId(tenantId, o.academicUnitId)
        val allowed = scopeService.authorizedDepartmentIds()
        if (allowed != null) {
            val unitDept = unit?.sourceDepartmentId
            val ok = o.requestingDepartmentId in allowed ||
                o.owningDepartmentId in allowed ||
                (unitDept != null && unitDept in allowed)
            if (!ok) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Course offering is outside your department scope")
            }
        }

        val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, req.lecturerId)
            ?: throw NoSuchElementException("Lecturer not found")
        val linkedRequest = requestRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .firstOrNull { it.courseOfferingId == o.id || (it.academicUnitId == o.academicUnitId && it.status !in listOf("DECLINED", "CANCELLED", "PUBLISHED")) }
        val crossOk = linkedRequest != null &&
            (linkedRequest.preferredDepartmentId == lecturer.organizationNodeId ||
                linkedRequest.requestingDepartmentId == unit?.sourceDepartmentId)
        if (allowed != null && !crossOk && lecturer.organizationNodeId !in allowed) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Cannot allocate to a lecturer outside your department without an accepted cross-department request"
            )
        }

        val candidates = suitabilityForOffering(req.courseOfferingId)
        val top = candidates.firstOrNull()
        val selected = candidates.firstOrNull { it.lecturerId == req.lecturerId }
            ?: throw NoSuchElementException("Lecturer not in suitability set — refresh recommendations and try again")

        val decisionType = when {
            top != null && top.lecturerId == req.lecturerId -> "ACCEPTED_RECOMMENDATION"
            top != null -> "OVERRIDE"
            else -> "MANUAL"
        }
        if (decisionType == "OVERRIDE" && req.decisionNote.isNullOrBlank()) {
            throw IllegalArgumentException("Override reason required when selecting a lecturer other than the recommendation")
        }
        val breakdown = selected.breakdown.joinToString("|") { "${it.key}:${it.earned}/${it.max}" }

        allocationRepo.findByTenantIdAndAcademicUnitId(tenantId, o.academicUnitId)
            .filter { it.status !in listOf("PUBLISHED", "APPROVED", "CANCELLED") }
            .forEach {
                it.status = "SUPERSEDED"
                it.updatedAt = Instant.now()
                allocationRepo.save(it)
            }

        val conflicts = detectOfferingConflicts(req.lecturerId, o.academicUnitId)
        val blocking = conflicts.any { it.severity.equals("high", true) }
        val status = if (blocking) "CONFLICT" else "ASSIGNED"

        val saved = allocationRepo.save(
            Allocation(
                tenantId = tenantId,
                academicUnitId = o.academicUnitId,
                lecturerId = req.lecturerId,
                teachingRequestId = linkedRequest?.id,
                courseOfferingId = o.id,
                matchScore = BigDecimal.valueOf(selected.score.toLong()),
                status = status,
                overrideReason = req.decisionNote,
                recommendedLecturerId = top?.lecturerId,
                suitabilityScore = BigDecimal.valueOf(selected.score.toLong()),
                suitabilityBreakdown = breakdown,
                decisionType = decisionType,
                decisionNote = buildString {
                    append("Selected ${selected.name} (${selected.score}% ${selected.classification})")
                    if (top != null && top.lecturerId != req.lecturerId) {
                        append(" · Recommended was ${top.name} (${top.score}%)")
                    }
                    if (!req.decisionNote.isNullOrBlank()) append(" · ${req.decisionNote}")
                }
            )
        )

        unit?.let {
            it.status = if (status == "CONFLICT") "Conflict" else "Allocated"
            unitRepo.save(it)
        }

        linkedRequest?.let {
            it.status = if (status == "CONFLICT") "CONFLICT" else "ASSIGNED"
            if (it.courseOfferingId == null) it.courseOfferingId = o.id
            requestRepo.save(it)
        }

        if (status == "ASSIGNED" && unit != null) {
            lecturer.currentWorkload = lecturer.currentWorkload.add(unit.contactHours)
            lecturerRepo.save(lecturer)
        }

        conflicts.forEach {
            it.allocationId = saved.id
            conflictRepo.save(it)
        }

        audit(
            "Course offering allocated",
            "Allocation",
            saved.id.toString(),
            "offering=${o.id} lecturer=${req.lecturerId} decision=$decisionType score=${selected.score} status=$status"
        )

        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        return AllocationDto(
            id = saved.id,
            academicUnitId = saved.academicUnitId,
            unitCode = unit?.code ?: "",
            unitName = unit?.name ?: "",
            lecturerId = saved.lecturerId,
            lecturerName = saved.lecturerId?.let { lecturers[it]?.fullName },
            matchScore = saved.matchScore,
            status = saved.status,
            overrideReason = saved.overrideReason,
            teachingRequestId = saved.teachingRequestId
        )
    }

    private fun detectOfferingConflicts(lecturerId: UUID, unitId: UUID): List<Conflict> {
        val tenantId = TenantContext.get()
        val found = mutableListOf<Conflict>()
        val lecturer = lecturerRepo.findByTenantIdAndId(tenantId, lecturerId) ?: return found
        val unit = unitRepo.findByTenantIdAndId(tenantId, unitId) ?: return found
        if (lecturer.currentWorkload.add(unit.contactHours) > lecturer.maximumWorkload) {
            found += Conflict(
                tenantId = tenantId,
                category = "WORKLOAD",
                severity = "high",
                description = "${lecturer.fullName} would exceed maximum workload with ${unit.code}",
                relatedEntity = lecturer.fullName
            )
        }
        return found
    }

    private fun toOfferingDto(
        o: CourseOffering,
        unit: AcademicUnit?,
        orgs: Map<UUID, OrganizationNode>
    ) = CourseOfferingDto(
        id = o.id,
        academicUnitId = o.academicUnitId,
        unitCode = unit?.code ?: "",
        unitName = unit?.name ?: "",
        programme = o.programme,
        contextLabel = o.contextLabel,
        levelLabel = o.levelLabel,
        displayTitle = o.displayTitle ?: unit?.name,
        requestingDepartment = o.requestingDepartmentId?.let { orgs[it]?.name },
        owningDepartment = o.owningDepartmentId?.let { orgs[it]?.name },
        status = o.status
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
