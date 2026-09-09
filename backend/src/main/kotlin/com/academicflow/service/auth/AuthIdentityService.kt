package com.academicflow.service.auth

import com.academicflow.config.ClerkContext
import com.academicflow.config.TenantContext
import com.academicflow.config.UserContext
import com.academicflow.dto.LoginResponse
import com.academicflow.dto.MembershipDto
import com.academicflow.entity.AppUser
import com.academicflow.repository.AppUserRepository
import com.academicflow.service.ScopeService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuthIdentityService(
    private val userRepo: AppUserRepository,
    private val scopeService: ScopeService,
    @Value("\${academicflow.auth.mode:auto}") private val authMode: String,
    @Value("\${academicflow.clerk.secret-key:}") private val clerkSecret: String,
    @Value("\${academicflow.clerk.issuer:}") private val clerkIssuer: String
) {
    private val log = LoggerFactory.getLogger(AuthIdentityService::class.java)

    fun clerkEnabled(): Boolean {
        val mode = authMode.trim().lowercase()
        if (mode == "legacy") return false
        if (mode == "clerk") return true
        return clerkSecret.isNotBlank() || clerkIssuer.isNotBlank()
    }

    fun legacyEnabled(): Boolean {
        val mode = authMode.trim().lowercase()
        if (mode == "clerk") return false
        if (mode == "legacy") return true
        return !clerkEnabled()
    }

    /**
     * After Clerk sign-in: link or create AcademicFlow user for this tenant and return session payload.
     */
    @Transactional
    fun establishSessionFromClerk(
        identity: ClerkContext.Identity,
        preferredTenantId: UUID? = null
    ): LoginResponse {
        val existingByClerk = userRepo.findByClerkUserId(identity.clerkUserId)
        val tenantId = preferredTenantId
            ?: existingByClerk?.tenantId
            ?: TenantContext.get()
        var user = existingByClerk
        if (user == null) {
            user = userRepo.findByTenantIdAndEmail(tenantId, identity.email)
            if (user != null) {
                if (!user.clerkUserId.isNullOrBlank() && user.clerkUserId != identity.clerkUserId) {
                    throw IllegalStateException("This email is already linked to a different Clerk account")
                }
                user.clerkUserId = identity.clerkUserId
                if (!identity.fullName.isNullOrBlank()) user.fullName = identity.fullName
                user.active = true
                user.accountStatus = "ACTIVE"
                user.lastLoginAt = Instant.now()
                userRepo.save(user)
                log.info("auth.clerk_linked userId={} clerkUserId={}", user.id, identity.clerkUserId)
            }
        }
        if (user == null) {
            // Do not invent memberships here — invitations (or admin provisioning) create users.
            throw IllegalStateException(
                "No AcademicFlow account for this Clerk identity. Open your invitation link or ask an administrator to invite you."
            )
        }
        user.lastLoginAt = Instant.now()
        userRepo.save(user)
        return toLoginResponse(user)
    }

    fun toLoginResponse(user: AppUser): LoginResponse {
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
            departmentName = activeName,
            activeDepartmentId = activeId,
            activeDepartmentName = activeName,
            activeRole = activeRole ?: user.role,
            memberships = memberships.map {
                MembershipDto(
                    it.id,
                    it.organizationNodeId,
                    it.organizationName,
                    it.organizationType,
                    it.role,
                    it.isPrimary
                )
            }
        )
    }

    fun bindPrincipal(user: AppUser, requestedDepartmentId: UUID?) {
        val memberships = scopeService.loadMemberships(user.tenantId, user.id)
        val (activeId, activeName, activeRole) = scopeService.resolveActiveDepartment(
            user.role,
            user.organizationNodeId,
            memberships,
            requestedDepartmentId
        )
        UserContext.set(
            UserContext.Principal(
                userId = user.id,
                email = user.email,
                role = user.role,
                organizationNodeId = user.organizationNodeId,
                tenantId = user.tenantId,
                memberships = memberships,
                activeDepartmentId = activeId,
                activeDepartmentName = activeName,
                activeRole = activeRole
            )
        )
    }
}
