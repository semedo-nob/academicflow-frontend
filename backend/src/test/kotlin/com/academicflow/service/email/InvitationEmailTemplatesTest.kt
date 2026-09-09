package com.academicflow.service.email

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationEmailTemplatesTest {

    @Test
    fun `invitation email includes institution department role and invited by`() {
        val msg = InvitationEmailTemplates.invitation(
            institutionName = "Technical University of Kenya",
            departmentName = "Computer Technology",
            roleLabel = "Department Chair",
            inviteeName = "Ada Chair",
            acceptUrl = "https://app.example/invite/secure-token",
            expiresLabel = "2026-09-15",
            invitedByName = "Institution Admin"
        )
        assertTrue(msg.subject.contains("Department Chair"))
        assertTrue(msg.textBody!!.contains("Technical University of Kenya"))
        assertTrue(msg.textBody!!.contains("Department: Computer Technology"))
        assertTrue(msg.textBody!!.contains("Role: Department Chair"))
        assertTrue(msg.textBody!!.contains("Invited by: Institution Admin"))
        assertTrue(msg.textBody!!.contains("https://app.example/invite/secure-token"))
        assertTrue(msg.htmlBody!!.contains("Accept Invitation"))
        assertTrue(msg.htmlBody!!.contains("Computer Technology"))
    }
}
