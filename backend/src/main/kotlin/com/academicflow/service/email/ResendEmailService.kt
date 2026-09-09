package com.academicflow.service.email

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ResendEmailService(
    private val apiKey: String,
    private val fromAddress: String,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : EmailService {
    private val log = LoggerFactory.getLogger(ResendEmailService::class.java)

    override val providerId: String = "resend"

    override fun send(message: EmailMessage): EmailSendResult {
        if (apiKey.isBlank()) {
            return EmailSendResult(providerId, false, error = "RESEND_API_KEY is not configured")
        }
        if (fromAddress.isBlank()) {
            return EmailSendResult(providerId, false, error = "EMAIL_FROM is not configured")
        }
        val payload = buildString {
            append('{')
            append("\"from\":").append(jsonString(fromAddress)).append(',')
            append("\"to\":[").append(jsonString(message.to)).append("],")
            append("\"subject\":").append(jsonString(message.subject)).append(',')
            append("\"html\":").append(jsonString(message.htmlBody)).append(',')
            append("\"text\":").append(jsonString(message.textBody))
            append('}')
        }
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(response.body())?.groupValues?.getOrNull(1)
                EmailSendResult(providerId, true, providerMessageId = id)
            } else {
                log.warn("email.resend_failed status={} body={}", response.statusCode(), response.body().take(400))
                EmailSendResult(providerId, false, error = "Resend HTTP ${response.statusCode()}: ${response.body().take(200)}")
            }
        } catch (e: Exception) {
            log.warn("email.resend_error message={}", e.message)
            EmailSendResult(providerId, false, error = e.message ?: "Resend send failed")
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
