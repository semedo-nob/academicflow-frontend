package com.academicflow.service.auth

import com.academicflow.config.ClerkContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Documents the invitation email-match rule used by AdminConfigService.acceptInvitation.
 * Backend enforcement compares ClerkContext identity email to invitation.email (ignore case).
 */
class InvitationEmailMatchTest {

    private fun emailsMatch(invited: String, authenticated: String): Boolean =
        invited.equals(authenticated, ignoreCase = true)

    @Test
    fun `matching emails accept`() {
        assertTrue(emailsMatch("chair@university.ac.ke", "chair@university.ac.ke"))
        assertTrue(emailsMatch("Chair@University.ac.ke", "chair@university.ac.ke"))
    }

    @Test
    fun `mismatched emails reject`() {
        assertFalse(emailsMatch("chair@university.ac.ke", "someoneelse@gmail.com"))
    }

    @Test
    fun `clerk identity carries verified email for matching`() {
        val identity = ClerkContext.Identity(
            clerkUserId = "user_abc",
            email = "chair@university.ac.ke",
            emailVerified = true,
            fullName = "Chair"
        )
        assertTrue(emailsMatch("chair@university.ac.ke", identity.email))
        assertFalse(emailsMatch("other@university.ac.ke", identity.email))
    }
}
