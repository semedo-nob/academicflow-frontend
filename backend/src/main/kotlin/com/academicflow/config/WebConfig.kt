package com.academicflow.config

import com.academicflow.repository.AppUserRepository
import com.academicflow.service.ScopeService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
    private val scopeService: ScopeService
) : OncePerRequestFilter() {
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

        val email = request.getHeader("X-User-Email")?.trim().orEmpty()
        if (email.isNotBlank()) {
            val matches = userRepo.findByEmailIgnoreCase(email).filter { it.active }
            val user = matches.firstOrNull { it.tenantId == tenant } ?: matches.firstOrNull()
            if (user != null) {
                val memberships = scopeService.loadMemberships(user.tenantId, user.id)
                val requestedDept = request.getHeader("X-Active-Department-Id")?.trim()?.takeIf { it.isNotBlank() }
                    ?.let {
                        try {
                            UUID.fromString(it)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
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

        try {
            filterChain.doFilter(request, response)
        } finally {
            UserContext.clear()
            TenantContext.clear()
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
