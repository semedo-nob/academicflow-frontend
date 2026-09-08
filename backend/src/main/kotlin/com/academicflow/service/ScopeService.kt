package com.academicflow.service

import com.academicflow.config.TenantContext
import com.academicflow.config.UserContext
import com.academicflow.entity.OrganizationNode
import com.academicflow.repository.OrganizationMembershipRepository
import com.academicflow.repository.OrganizationNodeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Resolves ROLE + INSTITUTION + DEPARTMENT scope for the authenticated principal.
 * Institution admins / school deans see institution-wide (still tenant-isolated).
 * Department chairs / lecturers are limited to authorized organization nodes.
 */
@Service
class ScopeService(
    private val membershipRepo: OrganizationMembershipRepository,
    private val orgRepo: OrganizationNodeRepository
) {
    fun loadMemberships(tenantId: UUID, userId: UUID): List<UserContext.Membership> {
        val nodes = orgRepo.findByTenantIdOrderByNameAsc(tenantId).associateBy { it.id }
        return membershipRepo.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
            .mapNotNull { m ->
                val node = nodes[m.organizationNodeId] ?: return@mapNotNull null
                UserContext.Membership(
                    id = m.id,
                    organizationNodeId = m.organizationNodeId,
                    organizationName = node.name,
                    organizationType = node.type,
                    role = m.role,
                    isPrimary = m.isPrimary
                )
            }
    }

    fun resolveActiveDepartment(
        principalRole: String,
        homeNodeId: UUID?,
        memberships: List<UserContext.Membership>,
        requestedDepartmentId: UUID?
    ): Triple<UUID?, String?, String?> {
        val chairOrLecturerMemberships = memberships.filter {
            it.role.equals("DEPARTMENT_CHAIR", true) || it.role.equals("LECTURER", true)
        }
        val authorizedIds = chairOrLecturerMemberships.map { it.organizationNodeId }.toSet()
            .ifEmpty { listOfNotNull(homeNodeId).toSet() }

        if (requestedDepartmentId != null) {
            if (principalRole.uppercase() in setOf("INSTITUTION_ADMIN", "SUPER_ADMIN", "SCHOOL_DEAN", "SCHOOL_ADMIN")) {
                val node = orgRepo.findById(requestedDepartmentId).orElse(null)
                return Triple(requestedDepartmentId, node?.name, principalRole)
            }
            val match = memberships.firstOrNull { it.organizationNodeId == requestedDepartmentId }
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for that department")
            return Triple(match.organizationNodeId, match.organizationName, match.role)
        }

        val primary = chairOrLecturerMemberships.firstOrNull { it.isPrimary }
            ?: chairOrLecturerMemberships.firstOrNull()
            ?: memberships.firstOrNull { it.organizationNodeId == homeNodeId }
            ?: memberships.firstOrNull()
        return Triple(primary?.organizationNodeId ?: homeNodeId, primary?.organizationName, primary?.role ?: principalRole)
    }

    /** Department node IDs the caller may read/write when department-scoped. */
    fun authorizedDepartmentIds(me: UserContext.Principal? = UserContext.get()): Set<UUID>? {
        if (me == null) return null
        if (me.isInstitutionWide()) return null // null = no department filter (tenant-wide)
        val active = me.activeDepartmentId
        if (active != null) return setOf(active)
        val fromMemberships = me.memberships
            .filter { it.role.equals("DEPARTMENT_CHAIR", true) || it.role.equals("LECTURER", true) }
            .map { it.organizationNodeId }
            .toSet()
        if (fromMemberships.isNotEmpty()) return fromMemberships
        return me.organizationNodeId?.let { setOf(it) }
    }

    fun requireDepartmentAccess(departmentId: UUID, me: UserContext.Principal? = UserContext.get()) {
        val allowed = authorizedDepartmentIds(me) ?: return // institution-wide
        if (departmentId !in allowed) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for department $departmentId")
        }
    }

    fun requireUnitInScope(sourceDepartmentId: UUID, me: UserContext.Principal? = UserContext.get()) {
        requireDepartmentAccess(sourceDepartmentId, me)
    }

    fun requireLecturerInDepartments(
        lecturerDepartmentId: UUID,
        allowedDepartments: Set<UUID>,
        crossDepartmentAllowed: Boolean = false
    ) {
        if (crossDepartmentAllowed) return
        if (lecturerDepartmentId !in allowedDepartments) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Lecturer is not associated with an authorized department for this allocation"
            )
        }
    }

    fun departmentNodes(tenantId: UUID = TenantContext.get()): List<OrganizationNode> =
        orgRepo.findByTenantIdOrderByNameAsc(tenantId).filter {
            it.type.equals("Department", true)
        }
}
