package com.academicflow.config

import java.util.UUID

/**
 * Request-scoped identity from X-User-Email (+ optional X-Active-Department-Id).
 * Authorization is ROLE + INSTITUTION + DEPARTMENT — not role alone.
 */
object UserContext {
    data class Membership(
        val id: UUID,
        val organizationNodeId: UUID,
        val organizationName: String,
        val organizationType: String,
        val role: String,
        val isPrimary: Boolean
    )

    data class Principal(
        val userId: UUID,
        val email: String,
        val role: String,
        /** Home / denormalized node on users table (legacy). */
        val organizationNodeId: UUID?,
        val tenantId: UUID,
        val memberships: List<Membership> = emptyList(),
        /** Active department context for this request (must be authorized). */
        val activeDepartmentId: UUID? = null,
        val activeDepartmentName: String? = null,
        val activeRole: String? = null
    ) {
        fun isInstitutionWide(): Boolean =
            role.uppercase() in setOf(
                "INSTITUTION_ADMIN",
                "SUPER_ADMIN",
                "SCHOOL_DEAN",
                "SCHOOL_ADMIN"
            )

        fun isDepartmentScoped(): Boolean =
            !isInstitutionWide() && (
                role.equals("DEPARTMENT_CHAIR", true) ||
                    role.equals("LECTURER", true) ||
                    activeRole.equals("DEPARTMENT_CHAIR", true) ||
                    activeRole.equals("LECTURER", true)
            )
    }

    private val holder = ThreadLocal<Principal?>()

    fun set(principal: Principal?) = holder.set(principal)
    fun get(): Principal? = holder.get()
    fun clear() = holder.remove()
    fun require(): Principal = get() ?: throw IllegalStateException("Authentication required")
}
