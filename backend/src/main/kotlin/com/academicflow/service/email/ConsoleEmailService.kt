package com.academicflow.service.email

import org.slf4j.LoggerFactory

/**
 * Local / CI provider: logs the message and treats delivery as successful without calling a network API.
 * Never use in production (fail-closed via configuration).
 */
class ConsoleEmailService : EmailService {
    private val log = LoggerFactory.getLogger(ConsoleEmailService::class.java)

    override val providerId: String = "console"

    override fun send(message: EmailMessage): EmailSendResult {
        log.info(
            "email.console to={} subject={} tags={} textPreview={}",
            message.to,
            message.subject,
            message.tags,
            message.textBody.take(240).replace('\n', ' ')
        )
        return EmailSendResult(provider = providerId, success = true, providerMessageId = "console-${System.nanoTime()}")
    }
}
