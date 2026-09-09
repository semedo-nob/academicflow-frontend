package com.academicflow.service.email

/**
 * Provider-agnostic outbound email. Invitation / business code must depend only on this interface.
 */
interface EmailService {
    val providerId: String
    fun send(message: EmailMessage): EmailSendResult
}
