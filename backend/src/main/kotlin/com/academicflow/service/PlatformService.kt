package com.academicflow.service

import com.academicflow.config.PlatformActorContext
import com.academicflow.entity.*
import com.academicflow.repository.*
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

data class PlatformCommandCenterDto(
    val customers: Map<String, Int>,
    val users: Map<String, Int>,
    val activity: Map<String, Int>,
    val attention: List<String>,
    val health: List<Map<String, String>>,
    val recentInstitutions: List<Map<String, Any?>>,
    val productVersion: String
)

data class PlatformInstitutionDetailDto(
    val id: UUID,
    val name: String,
    val code: String,
    val status: String,
    val onboardingStage: String,
    val planCode: String,
    val adminEmail: String?,
    val adminName: String?,
    val createdAt: String,
    val lastActivityAt: String?,
    val decisionNote: String?,
    val usage: Map<String, Int>,
    val administrators: List<Map<String, Any?>>,
    val recentAudit: List<Map<String, Any?>>
)

@Service
class PlatformService(
    private val tenantRepo: TenantRepository,
    private val userRepo: AppUserRepository,
    private val lecturerRepo: LecturerRepository,
    private val unitRepo: AcademicUnitRepository,
    private val orgRepo: OrganizationNodeRepository,
    private val requestRepo: TeachingRequestRepository,
    private val allocationRepo: AllocationRepository,
    private val conflictRepo: ConflictRepository,
    private val importRepo: ImportSessionRepository,
    private val auditRepo: AuditLogRepository,
    private val mappingRepo: ImportMappingProfileRepository,
    private val flagRepo: FeatureFlagRepository,
    private val settingRepo: PlatformSettingRepository,
    private val securityRepo: SecurityEventRepository,
    private val dataSource: DataSource,
    private val flyway: Flyway,
    @Value("\${academicflow.version:1.0.0}") private val appVersion: String
) {
    fun commandCenter(): PlatformCommandCenterDto {
        val tenants = tenantRepo.findAll()
        val users = userRepo.findAll()
        val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val dayAgo = Instant.now().minus(1, ChronoUnit.DAYS)

        val customers = mapOf(
            "total" to tenants.size,
            "active" to tenants.count { it.status == "APPROVED" },
            "pending" to tenants.count { it.status == "PENDING" },
            "suspended" to tenants.count { it.status == "SUSPENDED" },
            "rejected" to tenants.count { it.status == "REJECTED" },
            "archived" to tenants.count { it.status == "ARCHIVED" },
            "recentlyOnboarded" to tenants.count {
                it.status == "APPROVED" && it.decidedAt != null && it.decidedAt!!.isAfter(weekAgo)
            }
        )
        val userMetrics = mapOf(
            "total" to users.size,
            "active" to users.count { it.active && it.accountStatus == "ACTIVE" },
            "activeToday" to users.count { it.lastLoginAt != null && it.lastLoginAt!!.isAfter(dayAgo) },
            "activeThisWeek" to users.count { it.lastLoginAt != null && it.lastLoginAt!!.isAfter(weekAgo) },
            "newThisWeek" to users.count { it.createdAt.isAfter(weekAgo) },
            "failedLoginEvents" to securityRepo.findAllByOrderByCreatedAtDesc()
                .count { it.eventType == "FAILED_LOGIN" && it.createdAt.isAfter(weekAgo) }
        )
        val activity = mapOf(
            "teachingRequests" to requestRepo.count().toInt(),
            "allocations" to allocationRepo.count().toInt(),
            "pendingAllocations" to allocationRepo.findAll().count {
                it.status in listOf("ASSIGNED", "AWAITING_APPROVAL", "RECOMMENDED")
            },
            "openConflicts" to conflictRepo.findAll().count { !it.resolved },
            "imports" to importRepo.count().toInt(),
            "failedImports" to importRepo.findAll().count { it.status.equals("FAILED", true) }
        )
        val attention = buildList {
            if (customers["pending"]!! > 0) add("${customers["pending"]} institution registration(s) awaiting review")
            if (activity["failedImports"]!! > 0) add("${activity["failedImports"]} failed import(s) across the platform")
            if (activity["openConflicts"]!! > 0) add("${activity["openConflicts"]} open conflict(s) across institutions")
            if (userMetrics["failedLoginEvents"]!! > 0) add("${userMetrics["failedLoginEvents"]} failed login event(s) this week")
            val noAdmin = tenants.filter { it.status == "APPROVED" && it.adminEmail.isNullOrBlank() }
            if (noAdmin.isNotEmpty()) add("${noAdmin.size} approved institution(s) missing admin email")
        }
        val recent = tenants.sortedByDescending { it.createdAt }.take(8).map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "code" to it.code,
                "status" to it.status,
                "createdAt" to it.createdAt.toString()
            )
        }
        return PlatformCommandCenterDto(
            customers = customers,
            users = userMetrics,
            activity = activity,
            attention = attention,
            health = healthChecks(),
            recentInstitutions = recent,
            productVersion = appVersion
        )
    }

    fun healthChecks(): List<Map<String, String>> {
        val checks = mutableListOf<Map<String, String>>()
        checks += mapOf("component" to "API", "status" to "Healthy", "detail" to "Application process running")
        try {
            dataSource.connection.use { c ->
                c.createStatement().use { st -> st.execute("SELECT 1") }
            }
            checks += mapOf("component" to "Database", "status" to "Healthy", "detail" to "PostgreSQL reachable")
        } catch (e: Exception) {
            checks += mapOf("component" to "Database", "status" to "Down", "detail" to (e.message ?: "Connection failed"))
        }
        checks += mapOf("component" to "Authentication", "status" to "Healthy", "detail" to "Login endpoint available")
        checks += mapOf(
            "component" to "Background Jobs",
            "status" to "Healthy",
            "detail" to "No dedicated job runner configured (inline processing)"
        )
        try {
            val info = flyway.info()
            val current = info.current()?.version?.toString() ?: "unknown"
            checks += mapOf(
                "component" to "Migrations",
                "status" to "Healthy",
                "detail" to "Flyway at version $current"
            )
        } catch (e: Exception) {
            checks += mapOf("component" to "Migrations", "status" to "Unknown", "detail" to (e.message ?: "Unavailable"))
        }
        checks += mapOf("component" to "Storage", "status" to "Healthy", "detail" to "Database-backed storage")
        return checks
    }

    fun systemInfo(): Map<String, Any?> {
        val migration = try {
            flyway.info().current()?.version?.toString()
        } catch (_: Exception) {
            null
        }
        return mapOf(
            "productName" to (settingRepo.findBySettingKey("product_name")?.settingValue ?: "AcademicFlow"),
            "backendVersion" to appVersion,
            "frontendVersion" to appVersion,
            "databaseMigration" to migration,
            "environment" to (System.getenv("SPRING_PROFILES_ACTIVE") ?: "default"),
            "javaVersion" to System.getProperty("java.version"),
            "maintenanceMode" to (settingRepo.findBySettingKey("maintenance_mode")?.settingValue == "true")
        )
    }

    fun listInstitutions(status: String?): List<Map<String, Any?>> {
        return tenantRepo.findAll()
            .filter { status.isNullOrBlank() || it.status.equals(status, true) }
            .sortedByDescending { it.createdAt }
            .map { institutionSummary(it) }
    }

    fun institutionDetail(id: UUID): PlatformInstitutionDetailDto {
        val t = tenantRepo.findById(id).orElseThrow { NoSuchElementException("Institution not found") }
        val users = userRepo.findByTenantIdOrderByFullNameAsc(t.id)
        val admins = users.filter {
            it.role in listOf("INSTITUTION_ADMIN", "SUPER_ADMIN", "SCHOOL_DEAN")
        }.map {
            mapOf(
                "id" to it.id,
                "name" to it.fullName,
                "email" to it.email,
                "role" to it.role,
                "active" to it.active,
                "accountStatus" to it.accountStatus,
                "lastLoginAt" to it.lastLoginAt?.toString()
            )
        }
        val usage = mapOf(
            "users" to users.size,
            "lecturers" to lecturerRepo.findByTenantIdOrderByFullNameAsc(t.id).size,
            "departments" to orgRepo.findByTenantIdOrderByNameAsc(t.id).count { it.type.equals("Department", true) },
            "academicUnits" to unitRepo.findByTenantIdOrderByCodeAsc(t.id).size,
            "requests" to requestRepo.findByTenantIdOrderByCreatedAtDesc(t.id).size,
            "allocations" to allocationRepo.findByTenantIdOrderByCreatedAtDesc(t.id).size,
            "openConflicts" to conflictRepo.findByTenantIdAndResolvedFalseOrderByCreatedAtDesc(t.id).size,
            "imports" to importRepo.findByTenantIdOrderByCreatedAtDesc(t.id).size
        )
        val recentAudit = auditRepo.findByTenantIdOrderByCreatedAtDesc(t.id).take(15).map {
            mapOf(
                "id" to it.id,
                "action" to it.action,
                "entityType" to it.entityType,
                "details" to it.details,
                "createdAt" to it.createdAt.toString()
            )
        }
        return PlatformInstitutionDetailDto(
            id = t.id,
            name = t.name,
            code = t.code,
            status = t.status,
            onboardingStage = t.onboardingStage,
            planCode = t.planCode,
            adminEmail = t.adminEmail,
            adminName = t.adminName,
            createdAt = t.createdAt.toString(),
            lastActivityAt = t.lastActivityAt?.toString(),
            decisionNote = t.decisionNote,
            usage = usage,
            administrators = admins,
            recentAudit = recentAudit
        )
    }

    private fun institutionSummary(t: Tenant): Map<String, Any?> {
        val users = userRepo.findByTenantIdOrderByFullNameAsc(t.id).size
        return mapOf(
            "id" to t.id,
            "name" to t.name,
            "code" to t.code,
            "status" to t.status,
            "onboardingStage" to t.onboardingStage,
            "planCode" to t.planCode,
            "adminEmail" to t.adminEmail,
            "adminName" to t.adminName,
            "userCount" to users,
            "createdAt" to t.createdAt.toString(),
            "lastActivityAt" to t.lastActivityAt?.toString(),
            "decisionNote" to t.decisionNote
        )
    }

    @Transactional
    fun setInstitutionLifecycle(id: UUID, status: String, note: String?): Map<String, Any?> {
        val normalized = status.trim().uppercase()
        require(normalized in setOf("APPROVED", "SUSPENDED", "REJECTED", "ARCHIVED", "PENDING")) {
            "Invalid status"
        }
        val t = tenantRepo.findById(id).orElseThrow { NoSuchElementException("Institution not found") }
        if (t.id == PlatformActorContext.tenantId() && normalized == "SUSPENDED") {
            throw IllegalArgumentException("Cannot suspend the platform tenant you are signed into")
        }
        val previous = t.status
        t.status = normalized
        t.onboardingStage = when (normalized) {
            "PENDING" -> "REQUESTED"
            "APPROVED" -> "ACTIVE"
            "SUSPENDED" -> "SUSPENDED"
            "REJECTED" -> "REJECTED"
            "ARCHIVED" -> "ARCHIVED"
            else -> t.onboardingStage
        }
        t.decisionNote = note
        t.decidedAt = Instant.now()
        tenantRepo.save(t)

        when (normalized) {
            "SUSPENDED", "REJECTED", "ARCHIVED" -> {
                userRepo.findByTenantIdOrderByFullNameAsc(t.id).forEach {
                    it.active = false
                    it.accountStatus = if (normalized == "ARCHIVED") "DEACTIVATED" else "SUSPENDED"
                    userRepo.save(it)
                }
            }
            "APPROVED" -> {
                userRepo.findByTenantIdOrderByFullNameAsc(t.id)
                    .filter { it.email.equals(t.adminEmail, true) || it.role == "INSTITUTION_ADMIN" }
                    .forEach {
                        it.active = true
                        it.accountStatus = "ACTIVE"
                        userRepo.save(it)
                    }
            }
        }
        platformAudit(
            "INSTITUTION_$normalized",
            "Tenant",
            t.id.toString(),
            "${t.name} ($previous → $normalized)${note?.let { " · $it" } ?: ""}"
        )
        return institutionSummary(t)
    }

    fun listUsers(q: String?): List<Map<String, Any?>> {
        val tenants = tenantRepo.findAll().associateBy { it.id }
        val query = q?.trim()?.lowercase().orEmpty()
        return userRepo.findAll()
            .filter {
                query.isEmpty() ||
                    it.email.lowercase().contains(query) ||
                    it.fullName.lowercase().contains(query) ||
                    it.role.lowercase().contains(query)
            }
            .sortedByDescending { it.createdAt }
            .take(500)
            .map { u ->
                val tenant = tenants[u.tenantId]
                mapOf(
                    "id" to u.id,
                    "email" to u.email,
                    "name" to u.fullName,
                    "role" to u.role,
                    "accountStatus" to u.accountStatus,
                    "active" to u.active,
                    "tenantId" to u.tenantId,
                    "institutionName" to (tenant?.name ?: ""),
                    "institutionCode" to (tenant?.code ?: ""),
                    "lastLoginAt" to u.lastLoginAt?.toString(),
                    "failedLoginCount" to u.failedLoginCount,
                    "createdAt" to u.createdAt.toString()
                )
            }
    }

    @Transactional
    fun setUserAccountStatus(userId: UUID, status: String, note: String?): Map<String, Any?> {
        val normalized = status.trim().uppercase()
        require(normalized in setOf("ACTIVE", "SUSPENDED", "LOCKED", "DEACTIVATED")) { "Invalid account status" }
        val user = userRepo.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        if (user.role.equals("SUPER_ADMIN", true) && normalized != "ACTIVE") {
            throw IllegalArgumentException("Cannot suspend or lock a Super Admin account from this console")
        }
        val previous = user.accountStatus
        user.accountStatus = normalized
        user.active = normalized == "ACTIVE"
        userRepo.save(user)
        platformAudit(
            "USER_$normalized",
            "User",
            user.id.toString(),
            "${user.email} ($previous → $normalized)${note?.let { " · $it" } ?: ""}"
        )
        return mapOf(
            "id" to user.id,
            "email" to user.email,
            "accountStatus" to user.accountStatus,
            "active" to user.active
        )
    }

    fun analytics(): Map<String, Any?> {
        val tenants = tenantRepo.findAll()
        val byInstitution = tenants.map { t ->
            mapOf(
                "id" to t.id,
                "name" to t.name,
                "code" to t.code,
                "status" to t.status,
                "users" to userRepo.findByTenantIdOrderByFullNameAsc(t.id).size,
                "requests" to requestRepo.findByTenantIdOrderByCreatedAtDesc(t.id).size,
                "allocations" to allocationRepo.findByTenantIdOrderByCreatedAtDesc(t.id).size,
                "imports" to importRepo.findByTenantIdOrderByCreatedAtDesc(t.id).size
            )
        }.sortedByDescending { it["users"] as Int }
        return mapOf(
            "totals" to mapOf(
                "institutions" to tenants.size,
                "users" to userRepo.count(),
                "requests" to requestRepo.count(),
                "allocations" to allocationRepo.count(),
                "imports" to importRepo.count(),
                "mappingProfiles" to mappingRepo.count()
            ),
            "featureUsage" to mapOf(
                "teachingRequests" to requestRepo.count(),
                "allocations" to allocationRepo.count(),
                "imports" to importRepo.count(),
                "openConflicts" to conflictRepo.findAll().count { !it.resolved }
            ),
            "institutions" to byInstitution
        )
    }

    fun securityEvents(): List<Map<String, Any?>> =
        securityRepo.findAllByOrderByCreatedAtDesc().take(200).map {
            mapOf(
                "id" to it.id,
                "severity" to it.severity,
                "eventType" to it.eventType,
                "email" to it.email,
                "tenantId" to it.tenantId,
                "details" to it.details,
                "createdAt" to it.createdAt.toString()
            )
        }

    fun platformAuditLogs(): List<Map<String, Any?>> {
        // Prefer platform-tenant audits plus institution lifecycle events mirrored there
        val platformTenant = PlatformActorContext.tenantId()
        val logs = if (platformTenant != null) {
            auditRepo.findByTenantIdOrderByCreatedAtDesc(platformTenant)
        } else {
            auditRepo.findAll().sortedByDescending { it.createdAt }
        }
        return logs.take(300).map {
            mapOf(
                "id" to it.id,
                "action" to it.action,
                "entityType" to it.entityType,
                "entityId" to it.entityId,
                "details" to it.details,
                "createdAt" to it.createdAt.toString(),
                "actorId" to it.actorId
            )
        }
    }

    fun listFlags(): List<Map<String, Any?>> =
        flagRepo.findAllByOrderByNameAsc().map {
            mapOf(
                "id" to it.id,
                "key" to it.flagKey,
                "name" to it.name,
                "description" to it.description,
                "enabled" to it.enabled,
                "updatedAt" to it.updatedAt.toString()
            )
        }

    @Transactional
    fun setFlag(id: UUID, enabled: Boolean): Map<String, Any?> {
        val flag = flagRepo.findById(id).orElseThrow { NoSuchElementException("Feature flag not found") }
        val previous = flag.enabled
        flag.enabled = enabled
        flag.updatedAt = Instant.now()
        flagRepo.save(flag)
        platformAudit(
            if (enabled) "FEATURE_ENABLED" else "FEATURE_DISABLED",
            "FeatureFlag",
            flag.id.toString(),
            "${flag.flagKey} ($previous → $enabled)"
        )
        return mapOf("id" to flag.id, "key" to flag.flagKey, "enabled" to flag.enabled)
    }

    fun listSettings(): List<Map<String, Any?>> =
        settingRepo.findAllByOrderBySettingKeyAsc().map {
            mapOf(
                "id" to it.id,
                "key" to it.settingKey,
                "value" to it.settingValue,
                "description" to it.description,
                "updatedAt" to it.updatedAt.toString()
            )
        }

    @Transactional
    fun updateSetting(id: UUID, value: String): Map<String, Any?> {
        val setting = settingRepo.findById(id).orElseThrow { NoSuchElementException("Setting not found") }
        val previous = setting.settingValue
        setting.settingValue = value
        setting.updatedAt = Instant.now()
        settingRepo.save(setting)
        platformAudit(
            "SYSTEM_SETTING_CHANGED",
            "PlatformSetting",
            setting.id.toString(),
            "${setting.settingKey}: $previous → $value"
        )
        return mapOf("id" to setting.id, "key" to setting.settingKey, "value" to setting.settingValue)
    }

    fun dataQuality(): Map<String, Any?> {
        val tenants = tenantRepo.findAll()
        val incomplete = tenants.filter { it.status == "APPROVED" && it.adminEmail.isNullOrBlank() }
        val pending = tenants.filter { it.status == "PENDING" }
        val failedImports = importRepo.findAll().filter { it.status.equals("FAILED", true) }
        val orphanUsers = userRepo.findAll().filter { tenantRepo.findById(it.tenantId).isEmpty }
        return mapOf(
            "institutionsMissingAdmin" to incomplete.map { mapOf("id" to it.id, "name" to it.name, "code" to it.code) },
            "pendingOnboarding" to pending.map { mapOf("id" to it.id, "name" to it.name, "code" to it.code) },
            "failedImports" to failedImports.take(50).map {
                mapOf(
                    "id" to it.id,
                    "tenantId" to it.tenantId,
                    "fileName" to it.fileName,
                    "status" to it.status,
                    "createdAt" to it.createdAt.toString()
                )
            },
            "orphanedAccounts" to orphanUsers.size
        )
    }

    fun supportLookup(q: String): Map<String, Any?> {
        val query = q.trim()
        if (query.isEmpty()) return mapOf("institutions" to emptyList<Any>(), "users" to emptyList<Any>())
        return mapOf(
            "institutions" to listInstitutions(null).filter {
                (it["name"] as String).contains(query, true) || (it["code"] as String).contains(query, true)
            }.take(20),
            "users" to listUsers(query).take(20)
        )
    }

    fun globalSearch(q: String): List<Map<String, Any?>> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        val results = mutableListOf<Map<String, Any?>>()
        listInstitutions(null).filter {
            (it["name"] as String).contains(query, true) || (it["code"] as String).contains(query, true)
        }.take(10).forEach {
            results += mapOf(
                "type" to "Institution",
                "title" to it["name"],
                "subtitle" to "${it["code"]} · ${it["status"]}",
                "path" to "/platform/institutions/${it["id"]}"
            )
        }
        listUsers(query).take(10).forEach {
            results += mapOf(
                "type" to "User",
                "title" to it["name"],
                "subtitle" to "${it["email"]} · ${it["institutionName"]}",
                "path" to "/platform/users"
            )
        }
        return results
    }

    @Transactional
    fun recordSecurityEvent(severity: String, type: String, email: String?, tenantId: UUID?, details: String?) {
        securityRepo.save(
            SecurityEvent(
                severity = severity,
                eventType = type,
                email = email,
                tenantId = tenantId,
                details = details
            )
        )
    }

    private fun platformAudit(action: String, entityType: String, entityId: String?, details: String?) {
        val tenantId = PlatformActorContext.tenantId() ?: return
        auditRepo.save(
            AuditLog(
                tenantId = tenantId,
                actorId = PlatformActorContext.userId(),
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details
            )
        )
    }
}
