package com.academicflow.config

import com.academicflow.repository.AppUserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Enforces that platform API calls (`/api/platform/...`) are made by an active SUPER_ADMIN.
 * Uses X-User-Email (set by the frontend after login) — not a substitute for JWT in production,
 * but prevents institution users from calling platform APIs by URL alone.
 */
@Component
class PlatformAuthFilter(
    private val userRepo: AppUserRepository
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI ?: return true
        return !path.startsWith("/api/platform")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val email = request.getHeader("X-User-Email")?.trim().orEmpty()
        if (email.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Platform authentication required")
            return
        }
        val user = userRepo.findByEmailIgnoreCase(email)
            .firstOrNull { it.active && it.role.equals("SUPER_ADMIN", true) && it.accountStatus != "SUSPENDED" }
        if (user == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Super Admin access required")
            return
        }
        PlatformActorContext.set(email, user.id, user.tenantId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            PlatformActorContext.clear()
        }
    }
}

object PlatformActorContext {
    private val email = ThreadLocal<String?>()
    private val userId = ThreadLocal<java.util.UUID?>()
    private val tenantId = ThreadLocal<java.util.UUID?>()

    fun set(e: String, uid: java.util.UUID, tid: java.util.UUID) {
        email.set(e)
        userId.set(uid)
        tenantId.set(tid)
    }

    fun email(): String? = email.get()
    fun userId(): java.util.UUID? = userId.get()
    fun tenantId(): java.util.UUID? = tenantId.get()
    fun clear() {
        email.remove()
        userId.remove()
        tenantId.remove()
    }
}

@Configuration
class PlatformAuthConfig {
    @Bean
    fun platformAuthFilterRegistration(filter: PlatformAuthFilter): FilterRegistrationBean<PlatformAuthFilter> {
        val bean = FilterRegistrationBean(filter)
        bean.order = Ordered.HIGHEST_PRECEDENCE + 20
        bean.addUrlPatterns("/api/platform/*")
        return bean
    }
}
