package com.academicflow.service.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AuthIdentityServiceModeTest {

    private fun service(mode: String, secret: String = "", issuer: String = "") =
        AuthIdentityService(
            userRepo = Mockito.mock(com.academicflow.repository.AppUserRepository::class.java),
            scopeService = Mockito.mock(com.academicflow.service.ScopeService::class.java),
            authMode = mode,
            clerkSecret = secret,
            clerkIssuer = issuer
        )

    @Test
    fun `auto mode enables clerk when secret present`() {
        val s = service("auto", secret = "sk_test_x")
        assertTrue(s.clerkEnabled())
        assertFalse(s.legacyEnabled())
    }

    @Test
    fun `auto mode falls back to legacy without clerk config`() {
        val s = service("auto")
        assertFalse(s.clerkEnabled())
        assertTrue(s.legacyEnabled())
    }

    @Test
    fun `clerk mode requires clerk even without keys configured`() {
        val s = service("clerk")
        assertTrue(s.clerkEnabled())
        assertFalse(s.legacyEnabled())
    }

    @Test
    fun `legacy mode disables clerk even if secret present`() {
        val s = service("legacy", secret = "sk_test_x", issuer = "https://clerk.example")
        assertFalse(s.clerkEnabled())
        assertTrue(s.legacyEnabled())
    }

    @Test
    fun `clerk mode never enables legacy`() {
        val s = service("clerk", secret = "sk_test_x", issuer = "https://clerk.example")
        assertTrue(s.clerkEnabled())
        assertFalse(s.legacyEnabled())
    }
}
