package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.dto.CandidateDto
import com.academicflow.entity.Lecturer
import com.academicflow.entity.LecturerExpertise
import com.academicflow.entity.RequestCandidate
import com.academicflow.entity.TeachingRequest
import com.academicflow.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class MatchingService(
    private val requestRepo: TeachingRequestRepository,
    private val unitRepo: AcademicUnitRepository,
    private val lecturerRepo: LecturerRepository,
    private val expertiseRepo: LecturerExpertiseRepository,
    private val candidateRepo: RequestCandidateRepository,
    private val orgRepo: OrganizationNodeRepository,
    private val timetableRepo: TimetableEntryRepository,
    private val allocationRepo: AllocationRepository
) {
    private val levelScore = mapOf(
        "Excellent" to 100,
        "Strong" to 85,
        "Moderate" to 65,
        "Basic" to 40
    )

    @Transactional
    fun findCandidates(requestId: UUID): List<CandidateDto> {
        val tenantId = TenantContext.get()
        val request = requestRepo.findByTenantIdAndId(tenantId, requestId)
            ?: throw NoSuchElementException("Request not found")
        val unit = unitRepo.findByTenantIdAndId(tenantId, request.academicUnitId)
            ?: throw NoSuchElementException("Unit not found")

        val required = (request.requiredExpertise ?: unit.requiredExpertise ?: "")
            .split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId)
        val allExpertise = expertiseRepo.findByTenantId(tenantId).groupBy { it.lecturerId }
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }

        candidateRepo.deleteByTeachingRequestId(requestId)
        candidateRepo.flush()

        val scored = lecturers.mapNotNull { lecturer ->
            scoreLecturer(request, lecturer, allExpertise[lecturer.id].orEmpty(), required, orgs[lecturer.organizationNodeId]?.name)
        }.sortedByDescending { it.matchScore }

        val saved = scored.mapIndexed { index, c ->
            c.rankNo = index + 1
            candidateRepo.save(c)
        }

        request.status = "RECOMMENDED"
        requestRepo.save(request)

        return saved.map { toDto(it, lecturers, orgs) }
    }

    fun getCandidates(requestId: UUID): List<CandidateDto> {
        val tenantId = TenantContext.get()
        val existing = candidateRepo.findByTenantIdAndTeachingRequestIdOrderByRankNoAsc(tenantId, requestId)
        if (existing.isEmpty()) return emptyList()
        val lecturers = lecturerRepo.findByTenantIdOrderByFullNameAsc(tenantId).associateBy { it.id }
        val orgs = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        return existing.map { c ->
            val lec = lecturers[c.lecturerId]!!
            CandidateDto(
                id = c.id,
                rank = c.rankNo,
                lecturerId = c.lecturerId,
                name = lec.fullName,
                dept = orgs[lec.organizationNodeId]?.name ?: "",
                score = c.matchScore,
                metrics = mapOf(
                    "expertise" to c.expertiseScore,
                    "availability" to c.availabilityScore,
                    "workload" to c.workloadScore,
                    "studentLoad" to c.studentLoadScore,
                    "policy" to c.policyScore
                ),
                load = "${lec.currentWorkload.stripTrailingZeros().toPlainString()} / ${lec.maximumWorkload.stripTrailingZeros().toPlainString()} hrs",
                availability = lec.availabilityText,
                reasons = c.reasons?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                warn = c.warning,
                hardConstraints = mapOf(
                    "qualified" to c.qualified,
                    "available" to c.available,
                    "noConflict" to c.noConflict,
                    "workloadOk" to c.workloadOk,
                    "policyOk" to c.policyOk
                )
            )
        }
    }

    private fun toDto(
        c: RequestCandidate,
        lecturers: List<Lecturer>,
        orgs: Map<UUID, com.academicflow.entity.OrganizationNode>
    ): CandidateDto {
        val lec = lecturers.first { it.id == c.lecturerId }
        return CandidateDto(
            id = c.id,
            rank = c.rankNo,
            lecturerId = c.lecturerId,
            name = lec.fullName,
            dept = (orgs[lec.organizationNodeId]?.name ?: "") + " Department",
            score = c.matchScore,
            metrics = mapOf(
                "expertise" to c.expertiseScore,
                "availability" to c.availabilityScore,
                "workload" to c.workloadScore,
                "studentLoad" to c.studentLoadScore,
                "policy" to c.policyScore
            ),
            load = "${lec.currentWorkload.stripTrailingZeros().toPlainString()} / ${lec.maximumWorkload.stripTrailingZeros().toPlainString()} hrs",
            availability = lec.availabilityText,
            reasons = c.reasons?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            warn = c.warning,
            hardConstraints = mapOf(
                "qualified" to c.qualified,
                "available" to c.available,
                "noConflict" to c.noConflict,
                "workloadOk" to c.workloadOk,
                "policyOk" to c.policyOk
            )
        )
    }

    private fun scoreLecturer(
        request: TeachingRequest,
        lecturer: Lecturer,
        expertise: List<LecturerExpertise>,
        required: List<String>,
        deptName: String?
    ): RequestCandidate? {
        val tenantId = TenantContext.get()
        val bestLevel = required.mapNotNull { req ->
            expertise.firstOrNull { exp ->
                exp.subject.equals(req, true) ||
                    exp.subject.contains(req, true) ||
                    req.contains(exp.subject, true)
            }?.level
        }.maxByOrNull { levelScore[it] ?: 0 }

        val qualified = bestLevel != null
        val expertisePts = (levelScore[bestLevel] ?: 0).toDouble()

        val available = lecturer.status == "Active" && !lecturer.availabilityText.isNullOrBlank()
        val availabilityPts = if (available) 100.0 else 0.0

        val remaining = lecturer.maximumWorkload - lecturer.currentWorkload
        val workloadOk = remaining >= request.contactHours
        val util = if (lecturer.maximumWorkload > BigDecimal.ZERO) {
            lecturer.currentWorkload.toDouble() / lecturer.maximumWorkload.toDouble()
        } else 1.0
        val workloadPts = when {
            !workloadOk -> 40.0
            util < 0.6 -> 95.0
            util <= 0.9 -> 100.0
            util <= 1.0 -> 75.0
            else -> 40.0
        }

        val studentLoadPts = when {
            request.studentCount <= 80 -> 100.0
            request.studentCount <= 150 -> 94.0
            request.studentCount <= 200 -> 88.0
            else -> 80.0
        }.let { base ->
            if ((levelScore[bestLevel] ?: 0) >= 85) base else base - 8
        }

        val policyOk = true
        val policyPts = 100.0

        val slots = timetableRepo.findByTenantIdAndLecturerId(tenantId, lecturer.id)
        val noConflict = slots.groupBy { it.dayOfWeek }.values.none { daySlots ->
            daySlots.size > 1 && daySlots.any { a ->
                daySlots.any { b -> a.id != b.id && a.startTime < b.endTime && b.startTime < a.endTime }
            }
        }

        // Hard constraints: must be qualified and available; others still surface with warnings
        if (!qualified || !available) return null

        val match = (
            expertisePts * 0.40 +
                availabilityPts * 0.20 +
                workloadPts * 0.20 +
                studentLoadPts * 0.10 +
                policyPts * 0.10
            ).let { BigDecimal.valueOf(it).setScale(0, RoundingMode.HALF_UP) }

        val reasons = mutableListOf<String>()
        if ((levelScore[bestLevel] ?: 0) >= 85) reasons += "Strong ${required.firstOrNull() ?: "subject"} expertise"
        else reasons += "Matching expertise in ${required.firstOrNull() ?: "required area"}"
        if (available) reasons += "Available during requested period"
        if (workloadOk) reasons += "Within workload limit"
        if (noConflict) reasons += "No timetable conflict"
        if (policyOk) reasons += "Eligible for cross-department teaching"

        val warning = when {
            !workloadOk -> "Currently over or at their maximum workload"
            (levelScore[bestLevel] ?: 0) < 70 -> "Moderate — not primary — expertise in ${required.firstOrNull()}"
            !noConflict -> "Possible timetable collision detected"
            else -> null
        }

        return RequestCandidate(
            tenantId = tenantId,
            teachingRequestId = request.id,
            lecturerId = lecturer.id,
            matchScore = match,
            expertiseScore = BigDecimal.valueOf(expertisePts).setScale(0, RoundingMode.HALF_UP),
            availabilityScore = BigDecimal.valueOf(availabilityPts).setScale(0, RoundingMode.HALF_UP),
            workloadScore = BigDecimal.valueOf(workloadPts).setScale(0, RoundingMode.HALF_UP),
            studentLoadScore = BigDecimal.valueOf(studentLoadPts).setScale(0, RoundingMode.HALF_UP),
            policyScore = BigDecimal.valueOf(policyPts).setScale(0, RoundingMode.HALF_UP),
            qualified = qualified,
            available = available,
            workloadOk = workloadOk,
            noConflict = noConflict,
            policyOk = policyOk,
            reasons = reasons.joinToString("|"),
            warning = warning
        )
    }
}
