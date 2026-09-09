package com.academicflow.service.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EmailServiceConfig(
    @Value("\${academicflow.email.provider:console}") private val provider: String,
    @Value("\${academicflow.email.from:}") private val fromAddress: String,
    @Value("\${academicflow.email.resend-api-key:}") private val resendApiKey: String,
    @Value("\${academicflow.email.postal-api-url:}") private val postalApiUrl: String,
    @Value("\${academicflow.email.postal-api-key:}") private val postalApiKey: String,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String
) {
    private val log = LoggerFactory.getLogger(EmailServiceConfig::class.java)

    @Bean
    fun emailService(): EmailService {
        val selected = provider.trim().lowercase().ifBlank { "console" }
        val prodLike = activeProfiles.split(',').map { it.trim().lowercase() }.any { it == "prod" || it == "production" }
        if (prodLike && selected == "console") {
            throw IllegalStateException(
                "EMAIL_PROVIDER=console is not allowed with a production Spring profile. Set EMAIL_PROVIDER=resend or postal."
            )
        }
        val service: EmailService = when (selected) {
            "console" -> ConsoleEmailService()
            "resend" -> {
                if (resendApiKey.isBlank()) {
                    log.error("email.resend_misconfigured reason=missing_api_key")
                }
                if (fromAddress.isBlank()) {
                    log.error("email.resend_misconfigured reason=missing_from")
                }
                // Bean still constructed so invitation create can mark delivery FAILED instead of crashing the app in non-prod.
                // ProductionSafetyValidator refuses to start prod without key/from.
                ResendEmailService(apiKey = resendApiKey, fromAddress = fromAddress)
            }
            "postal" -> PostalEmailService(apiUrl = postalApiUrl, apiKey = postalApiKey, fromAddress = fromAddress)
            else -> throw IllegalStateException(
                "Unsupported EMAIL_PROVIDER='$provider'. Supported values: console, resend, postal."
            )
        }
        log.info("email.provider_selected provider={}", service.providerId)
        return service
    }
}
