package com.academicflow.service.permission

import com.academicflow.config.UserContext
import com.academicflow.entity.AuditLog
import com.academicflow.entity.UserPermission
import com.academicflow.repository.AuditLogRepository
import com.academicflow.repository.UserPermissionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class PermissionService(
    private val userPermissionRepo: UserPermissionRepository,
    private val auditRepo: AuditLogRepository
) {
    fun effectivePermissions(role: String, userId: UUID, tenantId: UUID): Set<String> {
        val base = PermissionCodes.defaultsForRole(role).toMutableSet()
        val rows = userPermissionRepo.findByTenantIdAndUserId(tenantId, userId)
        rows.filter { it.effect.equals("GRANT", true) }.forEach { base.add(it.permission.uppercase()) }
        rows.filter { it.effect.equals("REVOKE", true) }.forEach { base.remove(it.permission.uppercase()) }
        return base
    }

    fun effectiveForCurrentUser(): Set<String> {
        val me = UserContext.require()
        return effectivePermissions(me.role, me.userId, me.tenantId)
    }

    fun has(permission: String): Boolean =
        effectiveForCurrentUser().contains(permission.uppercase())

    fun require(permission: String) {
        if (!has(permission)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: $permission")
        }
    }

    fun requireAny(vararg permissions: String) {
        val effective = effectiveForCurrentUser()
        if (permissions.none { effective.contains(it.uppercase()) }) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Missing permission (need one of): ${permissions.joinToString()}"
            )
        }
    }

    /**
     * Replace explicit GRANT overrides for a user. Caller must already hold USERS.MANAGE
     * and may only assign permissions they can delegate.
     */
    @Transactional
    fun replaceGrants(
        targetUserId: UUID,
        permissions: Collection<String>,
        organizationNodeId: UUID? = null
    ): Set<String> {
        val me = UserContext.require()
        require(PermissionCodes.USERS_MANAGE)
        val requested = PermissionCodes.normalize(permissions)
        val allowed = PermissionCodes.delegatableBy(me.role)
        val illegal = requested - allowed
        if (illegal.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Cannot delegate permissions you are not authorized to grant: ${illegal.joinToString()}"
            )
        }
        val existing = userPermissionRepo.findByTenantIdAndUserId(me.tenantId, targetUserId)
            .filter { it.effect.equals("GRANT", true) && it.organizationNodeId == organizationNodeId }
        existing.forEach { userPermissionRepo.delete(it) }
        requested.forEach { code ->
            userPermissionRepo.save(
                UserPermission(
                    tenantId = me.tenantId,
                    userId = targetUserId,
                    permission = code,
                    effect = "GRANT",
                    organizationNodeId = organizationNodeId,
                    grantedBy = me.userId
                )
            )
        }
        auditRepo.save(
            AuditLog(
                tenantId = me.tenantId,
                actorId = me.userId,
                action = "Permissions updated",
                entityType = "User",
                entityId = targetUserId.toString(),
                details = "grants=${requested.sorted().joinToString()}"
            )
        )
        return requested
    }

    /** Apply invitation starting permissions as GRANT rows (already validated at invite time). */
    @Transactional
    fun applyInvitationPermissions(
        tenantId: UUID,
        userId: UUID,
        permissions: Collection<String>,
        grantedBy: UUID?,
        organizationNodeId: UUID?
    ) {
        val codes = PermissionCodes.normalize(permissions)
        codes.forEach { code ->
            val exists = userPermissionRepo.findByTenantIdAndUserId(tenantId, userId)
                .any { it.permission.equals(code, true) && it.effect.equals("GRANT", true) }
            if (!exists) {
                userPermissionRepo.save(
                    UserPermission(
                        tenantId = tenantId,
                        userId = userId,
                        permission = code,
                        effect = "GRANT",
                        organizationNodeId = organizationNodeId,
                        grantedBy = grantedBy
                    )
                )
            }
        }
    }

    fun assertCanDelegate(actorRole: String, permissions: Collection<String>) {
        val requested = PermissionCodes.normalize(permissions)
        val allowed = PermissionCodes.delegatableBy(actorRole)
        val illegal = requested - allowed
        if (illegal.isNotEmpty()) {
            throw IllegalArgumentException(
                "Cannot invite with permissions you cannot delegate: ${illegal.joinToString()}"
            )
        }
    }
}
