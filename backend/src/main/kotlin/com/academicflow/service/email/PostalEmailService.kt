package com.academicflow.service.email

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Self-hosted Postal HTTP API (`/api/v1/send/message`).
 * @see https://docs.postalserver.io/developer/api
 */
class PostalEmailService(
    private val apiUrl: String,
    private val apiKey: String,
    private val fromAddress: String,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : EmailService {
    private val log = LoggerFactory.getLogger(PostalEmailService::class.java)

    override val providerId: String = "postal"

    override fun send(message: EmailMessage): EmailSendResult {
        if (apiUrl.isBlank()) {
            return EmailSendResult(providerId, false, error = "POSTAL_API_URL is not configured")
        }
        if (apiKey.isBlank()) {
            return EmailSendResult(providerId, false, error = "POSTAL_API_KEY is not configured")
        }
        if (fromAddress.isBlank()) {
            return EmailSendResult(providerId, false, error = "EMAIL_FROM is not configured")
        }
        val endpoint = apiUrl.trimEnd('/') + "/api/v1/send/message"
        val payload = buildString {
            append('{')
            append("\"to\":[").append(jsonString(message.to)).append("],")
            append("\"from\":").append(jsonString(fromAddress)).append(',')
            append("\"subject\":").append(jsonString(message.subject)).append(',')
            append("\"html_body\":").append(jsonString(message.htmlBody)).append(',')
            append("\"plain_body\":").append(jsonString(message.textBody))
            append('}')
        }
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("X-Server-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299 && response.body().contains("\"status\":\"success\"", ignoreCase = true)) {
                val id = Regex("\"message_id\"\\s*:\\s*\"?([^\",}]+)\"").find(response.body())?.groupValues?.getOrNull(1)
                EmailSendResult(providerId, true, providerMessageId = id)
            } else {
                log.warn("email.postal_failed status={} body={}", response.statusCode(), response.body().take(400))
                EmailSendResult(providerId, false, error = "Postal HTTP ${response.statusCode()}: ${response.body().take(200)}")
            }
        } catch (e: Exception) {
            log.warn("email.postal_error message={}", e.message)
            EmailSendResult(providerId, false, error = e.message ?: "Postal send failed")
        }
    }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { c ->
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }
}
