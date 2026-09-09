package com.academicflow.service.email

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmailProvidersTest {
    @Test
    fun `console provider succeeds without network`() {
        val service = ConsoleEmailService()
        val result = service.send(
            EmailMessage(
                to = "chair@example.com",
                subject = "Test",
                htmlBody = "<p>Hi</p>",
                textBody = "Hi"
            )
        )
        assertTrue(result.success)
        assertEquals("console", result.provider)
    }

    @Test
    fun `resend fails closed without api key`() {
        val service = ResendEmailService(apiKey = "", fromAddress = "noreply@example.com")
        val result = service.send(
            EmailMessage("a@b.com", "S", "<p>x</p>", "x")
        )
        assertFalse(result.success)
        assertTrue(result.error!!.contains("RESEND_API_KEY"))
    }

    @Test
    fun `postal fails closed without url`() {
        val service = PostalEmailService(apiUrl = "", apiKey = "k", fromAddress = "noreply@example.com")
        val result = service.send(
            EmailMessage("a@b.com", "S", "<p>x</p>", "x")
        )
        assertFalse(result.success)
        assertTrue(result.error!!.contains("POSTAL_API_URL"))
    }

    @Test
    fun `unsupported provider fails fast`() {
        val config = EmailServiceConfig(
            provider = "sendgrid",
            fromAddress = "a@b.com",
            resendApiKey = "",
            postalApiUrl = "",
            postalApiKey = "",
            activeProfiles = "dev"
        )
        assertThrows(IllegalStateException::class.java) { config.emailService() }
    }

    @Test
    fun `console blocked on production profile`() {
        val config = EmailServiceConfig(
            provider = "console",
            fromAddress = "a@b.com",
            resendApiKey = "",
            postalApiUrl = "",
            postalApiKey = "",
            activeProfiles = "production"
        )
        assertThrows(IllegalStateException::class.java) { config.emailService() }
    }

    @Test
    fun `invitation template includes institution and accept url`() {
        val msg = InvitationEmailTemplates.invitation(
            institutionName = "Demo University",
            departmentName = "Computer Science",
            roleLabel = "Department Chair",
            inviteeName = "Jane",
            acceptUrl = "https://academicflow.app/invite/abc",
            expiresLabel = "2026-10-01"
        )
        assertTrue(msg.subject.contains("Department Chair"))
        assertTrue(msg.textBody.contains("Demo University"))
        assertTrue(msg.htmlBody.contains("https://academicflow.app/invite/abc"))
        assertFalse(msg.htmlBody.contains("aaaaaaaa-aaaa"))
    }
}
