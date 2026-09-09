package com.academicflow.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Requires an authenticated AcademicFlow principal for protected API routes.
 * Public auth/register/invite-preview and actuator health remain open.
 */
@Component
class ApiAuthFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        val path = request.requestURI ?: return true
        if (!path.startsWith("/api/")) return true
        return PUBLIC_PREFIXES.any { path == it || path.startsWith("$it/") } ||
            path.matches(Regex("^/api/auth/invitations/[^/]+$"))
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (UserContext.get() == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private val PUBLIC_PREFIXES = listOf(
            "/api/auth/login",
            "/api/auth/session",
            "/api/auth/mode",
            "/api/auth/register-institution",
            "/api/auth/accept-invitation"
        )
    }
}

@Configuration
class ApiAuthConfig {
    @Bean
    fun apiAuthFilterRegistration(filter: ApiAuthFilter): FilterRegistrationBean<ApiAuthFilter> {
        val bean = FilterRegistrationBean(filter)
        bean.order = Ordered.HIGHEST_PRECEDENCE + 25
        bean.addUrlPatterns("/api/*")
        return bean
    }
}
