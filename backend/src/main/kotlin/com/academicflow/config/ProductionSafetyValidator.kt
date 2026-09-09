package com.academicflow.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Fail closed for production/Clerk-required deployments.
 * Never silently fall back to legacy header auth when AUTH_MODE=clerk.
 */
@Component
class ProductionSafetyValidator(
    @Value("\${academicflow.auth.mode:auto}") private val authMode: String,
    @Value("\${academicflow.clerk.secret-key:}") private val clerkSecret: String,
    @Value("\${academicflow.clerk.issuer:}") private val clerkIssuer: String,
    @Value("\${academicflow.clerk.jwks-url:}") private val clerkJwks: String,
    @Value("\${academicflow.app-base-url:}") private val appBaseUrl: String,
    @Value("\${academicflow.email.provider:console}") private val emailProvider: String,
    @Value("\${academicflow.email.resend-api-key:}") private val resendApiKey: String,
    @Value("\${academicflow.email.from:}") private val emailFrom: String,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String
) : InitializingBean {
    private val log = LoggerFactory.getLogger(ProductionSafetyValidator::class.java)

    override fun afterPropertiesSet() {
        val mode = authMode.trim().lowercase()
        val profiles = activeProfiles.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val prodLike = profiles.any { it == "prod" || it == "production" }

        if (mode == "clerk") {
            if (clerkSecret.isBlank()) {
                throw IllegalStateException(
                    "AUTH_MODE=clerk requires CLERK_SECRET_KEY. Refusing to start with legacy/header auth fallback."
                )
            }
            if (clerkIssuer.isBlank() && clerkJwks.isBlank()) {
                throw IllegalStateException(
                    "AUTH_MODE=clerk requires CLERK_ISSUER or CLERK_JWKS_URL for JWT verification. Refusing to start."
                )
            }
            log.info("auth.clerk_mode_validated issuerConfigured={} jwksOverride={}", clerkIssuer.isNotBlank(), clerkJwks.isNotBlank())
        }

        if (prodLike) {
            if (mode == "auto" || mode == "legacy") {
                throw IllegalStateException(
                    "Production profile forbids AUTH_MODE=$mode. Set AUTH_MODE=clerk with CLERK_SECRET_KEY and CLERK_ISSUER."
                )
            }
            val base = appBaseUrl.trim().lowercase()
            if (base.isBlank() || base.contains("localhost") || base.contains("127.0.0.1") || base.contains("trycloudflare.com")) {
                throw IllegalStateException(
                    "Production requires APP_BASE_URL to be a durable public HTTPS origin (not localhost or temporary tunnels)."
                )
            }
            if (!base.startsWith("https://")) {
                throw IllegalStateException("Production APP_BASE_URL must use https://")
            }
            val provider = emailProvider.trim().lowercase()
            if (provider == "resend") {
                if (resendApiKey.isBlank()) {
                    throw IllegalStateException("EMAIL_PROVIDER=resend requires RESEND_API_KEY in production.")
                }
                if (emailFrom.isBlank()) {
                    throw IllegalStateException("EMAIL_PROVIDER=resend requires EMAIL_FROM in production.")
                }
            }
        }
    }
}
