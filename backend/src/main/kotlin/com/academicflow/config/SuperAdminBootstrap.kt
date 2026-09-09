package com.academicflow.config

import com.academicflow.entity.AppUser
import com.academicflow.repository.AppUserRepository
import com.academicflow.repository.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Promotes/creates a platform SUPER_ADMIN by email from environment.
 * Never stores or requires a password here — authentication is Clerk (or legacy demo only).
 */
@Component
class SuperAdminBootstrap(
    private val userRepo: AppUserRepository,
    private val tenantRepo: TenantRepository,
    @Value("\${academicflow.super-admin-email:}") private val superAdminEmail: String,
    @Value("\${academicflow.super-admin-name:Platform Admin}") private val superAdminName: String,
    @Value("\${academicflow.default-tenant-id}") private val defaultTenant: String
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(SuperAdminBootstrap::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val email = superAdminEmail.trim().lowercase()
        if (email.isBlank()) return
        val existing = userRepo.findByEmailIgnoreCase(email).firstOrNull()
        if (existing != null) {
            if (!existing.role.equals("SUPER_ADMIN", true)) {
                existing.role = "SUPER_ADMIN"
                existing.active = true
                existing.accountStatus = "ACTIVE"
                userRepo.save(existing)
                log.info("bootstrap.super_admin_promoted email={}", email)
            }
            return
        }
        val tenantId = try {
            java.util.UUID.fromString(defaultTenant)
        } catch (_: Exception) {
            tenantRepo.findAll().firstOrNull()?.id ?: return
        }
        userRepo.save(
            AppUser(
                tenantId = tenantId,
                email = email,
                fullName = superAdminName,
                role = "SUPER_ADMIN",
                passwordHash = "clerk",
                active = true,
                accountStatus = "ACTIVE"
            )
        )
        log.info("bootstrap.super_admin_created email={}", email)
    }
}
