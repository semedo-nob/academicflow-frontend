package com.academicflow.config

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
import java.util.UUID

@Component
class TenantFilter(
    @Value("\${academicflow.default-tenant-id}") private val defaultTenant: String
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
        try {
            filterChain.doFilter(request, response)
        } finally {
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
