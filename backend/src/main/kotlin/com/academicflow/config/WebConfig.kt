package com.academicflow.config

import com.academicflow.repository.AppUserRepository
import com.academicflow.service.ScopeService
import com.academicflow.service.auth.AuthIdentityService
import com.academicflow.service.auth.ClerkBackendClient
import com.academicflow.service.auth.ClerkJwtVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class TenantFilter(
    @Value("\${academicflow.default-tenant-id}") private val defaultTenant: String,
    private val userRepo: AppUserRepository,
    private val scopeService: ScopeService,
    private val authIdentityService: AuthIdentityService,
    private val clerkJwtVerifier: ClerkJwtVerifier,
    private val clerkBackendClient: ClerkBackendClient
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(TenantFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("X-Tenant-Id")
        val tenant = try {
            UUID.fromString(header?.takeIf { it.isNotBlank() } ?: defaultTenant)
        } catch (_: IllegalArgumentException) {
            UUID.fromString(defaultTenant)
        }
        TenantContext.set(tenant)

        val bearer = request.getHeader("Authorization")
            ?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.trim()
            .orEmpty()

        try {
            if (authIdentityService.clerkEnabled()) {
                if (bearer.isNotBlank()) {
                    if (!clerkJwtVerifier.isConfigured()) {
                        response.sendError(
                            HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "Clerk JWT verification is not configured (set CLERK_ISSUER or CLERK_JWKS_URL)"
                        )
                        return
                    }
                    try {
                        val verified = clerkJwtVerifier.verify(bearer)
                        val claimEmail = verified.claims.getStringClaim("email")
                            ?: verified.claims.getStringClaim("primary_email_address")
                        val identity = clerkBackendClient.resolveIdentity(verified.clerkUserId, claimEmail)
                        ClerkContext.set(identity)
                        val user = userRepo.findByClerkUserId(identity.clerkUserId)
                        if (user != null && user.active && user.accountStatus != "SUSPENDED") {
                            // Prefer the user's home tenant over a client-spoofed header when they differ
                            if (user.tenantId != tenant) {
                                TenantContext.set(user.tenantId)
                            }
                            val requestedDept = parseUuid(request.getHeader("X-Active-Department-Id"))
                            authIdentityService.bindPrincipal(user, requestedDept)
                        }
                    } catch (e: ResponseStatusException) {
                        response.sendError(e.statusCode.value(), e.reason)
                        return
                    } catch (e: Exception) {
                        log.warn("clerk.auth_failed path={} message={}", request.requestURI, e.message)
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired Clerk session")
                        return
                    }
                }
                // AUTH_MODE=clerk never falls through to X-User-Email legacy auth
            } else if (authIdentityService.legacyEnabled()) {
                val email = request.getHeader("X-User-Email")?.trim().orEmpty()
                if (email.isNotBlank()) {
                    val matches = userRepo.findByEmailIgnoreCase(email).filter { it.active }
                    val user = matches.firstOrNull { it.tenantId == tenant } ?: matches.firstOrNull()
                    if (user != null) {
                        val memberships = scopeService.loadMemberships(user.tenantId, user.id)
                        val requestedDept = parseUuid(request.getHeader("X-Active-Department-Id"))
                        try {
                            val (activeId, activeName, activeRole) = scopeService.resolveActiveDepartment(
                                user.role,
                                user.organizationNodeId,
                                memberships,
                                requestedDept
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
                        } catch (e: ResponseStatusException) {
                            response.sendError(e.statusCode.value(), e.reason)
                            return
                        }
                    }
                }
            }

            filterChain.doFilter(request, response)
        } finally {
            UserContext.clear()
            ClerkContext.clear()
            TenantContext.clear()
        }
    }

    private fun parseUuid(raw: String?): UUID? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

@Configuration
class WebConfig(
    @Value("\${academicflow.cors-allowed-origins}") private val corsOrigins: String
) {
    @Bean
    fun corsFilter(): CorsFilter {
        val config = CorsConfiguration()
        config.allowCredentials = true
        corsOrigins.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { config.addAllowedOriginPattern(it) }
        config.addAllowedHeader("*")
        config.addAllowedMethod("*")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return CorsFilter(source)
    }

    @Bean
    fun tenantFilterRegistration(tenantFilter: TenantFilter): FilterRegistrationBean<TenantFilter> {
        val bean = FilterRegistrationBean(tenantFilter)
        bean.order = Ordered.HIGHEST_PRECEDENCE + 10
        return bean
    }
}
