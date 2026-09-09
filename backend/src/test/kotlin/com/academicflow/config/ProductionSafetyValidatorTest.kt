package com.academicflow.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProductionSafetyValidatorTest {

    private fun validator(
        mode: String = "auto",
        secret: String = "",
        issuer: String = "",
        jwks: String = "",
        appBaseUrl: String = "http://127.0.0.1:5173",
        emailProvider: String = "console",
        resendKey: String = "",
        emailFrom: String = "",
        profiles: String = "dev"
    ) = ProductionSafetyValidator(
        authMode = mode,
        clerkSecret = secret,
        clerkIssuer = issuer,
        clerkJwks = jwks,
        appBaseUrl = appBaseUrl,
        emailProvider = emailProvider,
        resendApiKey = resendKey,
        emailFrom = emailFrom,
        activeProfiles = profiles
    )

    @Test
    fun `clerk mode without secret fails closed`() {
        assertThrows(IllegalStateException::class.java) {
            validator(mode = "clerk", issuer = "https://example.clerk.accounts.dev").afterPropertiesSet()
        }
    }

    @Test
    fun `clerk mode without issuer or jwks fails closed`() {
        assertThrows(IllegalStateException::class.java) {
            validator(mode = "clerk", secret = "sk_test_x").afterPropertiesSet()
        }
    }

    @Test
    fun `clerk mode with secret and issuer starts`() {
        assertDoesNotThrow {
            validator(
                mode = "clerk",
                secret = "sk_test_x",
                issuer = "https://example.clerk.accounts.dev"
            ).afterPropertiesSet()
        }
    }

    @Test
    fun `production forbids auto auth mode`() {
        assertThrows(IllegalStateException::class.java) {
            validator(
                mode = "auto",
                profiles = "prod",
                appBaseUrl = "https://app.example.com",
                emailProvider = "resend",
                resendKey = "re_x",
                emailFrom = "noreply@example.com"
            ).afterPropertiesSet()
        }
    }

    @Test
    fun `production forbids localhost app base url`() {
        assertThrows(IllegalStateException::class.java) {
            validator(
                mode = "clerk",
                secret = "sk_test",
                issuer = "https://example.clerk.accounts.dev",
                profiles = "production",
                appBaseUrl = "http://127.0.0.1:5173",
                emailProvider = "resend",
                resendKey = "re_x",
                emailFrom = "noreply@example.com"
            ).afterPropertiesSet()
        }
    }

    @Test
    fun `production forbids trycloudflare app base url`() {
        assertThrows(IllegalStateException::class.java) {
            validator(
                mode = "clerk",
                secret = "sk_test",
                issuer = "https://example.clerk.accounts.dev",
                profiles = "prod",
                appBaseUrl = "https://random.trycloudflare.com",
                emailProvider = "resend",
                resendKey = "re_x",
                emailFrom = "noreply@example.com"
            ).afterPropertiesSet()
        }
    }

    @Test
    fun `production requires resend key when provider is resend`() {
        assertThrows(IllegalStateException::class.java) {
            validator(
                mode = "clerk",
                secret = "sk_test",
                issuer = "https://example.clerk.accounts.dev",
                profiles = "prod",
                appBaseUrl = "https://app.example.com",
                emailProvider = "resend",
                resendKey = "",
                emailFrom = "noreply@example.com"
            ).afterPropertiesSet()
        }
    }

    @Test
    fun `production happy path`() {
        assertDoesNotThrow {
            validator(
                mode = "clerk",
                secret = "sk_test",
                issuer = "https://example.clerk.accounts.dev",
                profiles = "prod",
                appBaseUrl = "https://app.example.com",
                emailProvider = "resend",
                resendKey = "re_x",
                emailFrom = "AcademicFlow <noreply@example.com>"
            ).afterPropertiesSet()
        }
    }
}
