package com.academicflow.service.auth

import com.academicflow.config.ClerkContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Clerk Backend API client for email / profile resolution after JWT verification.
 */
@Component
class ClerkBackendClient(
    @Value("\${academicflow.clerk.secret-key:}") private val secretKey: String,
    @Value("\${academicflow.clerk.api-base:https://api.clerk.com}") private val apiBase: String
) {
    private val log = LoggerFactory.getLogger(ClerkBackendClient::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val cache = ConcurrentHashMap<String, Pair<Long, ClerkContext.Identity>>()

    fun isConfigured(): Boolean = secretKey.isNotBlank()

    fun resolveIdentity(clerkUserId: String, tokenClaimsEmail: String? = null): ClerkContext.Identity {
        val cached = cache[clerkUserId]
        if (cached != null && cached.first > System.currentTimeMillis()) {
            return cached.second
        }
        if (!isConfigured()) {
            val email = tokenClaimsEmail?.trim()?.lowercase().orEmpty()
            if (email.isBlank()) {
                throw IllegalStateException("CLERK_SECRET_KEY is required to resolve user email from Clerk")
            }
            return ClerkContext.Identity(clerkUserId, email, emailVerified = true, fullName = null)
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${apiBase.trimEnd('/')}/v1/users/$clerkUserId"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $secretKey")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            log.warn("clerk.user_fetch_failed status={} userId={}", response.statusCode(), clerkUserId)
            throw IllegalStateException("Could not resolve Clerk user profile (${response.statusCode()})")
        }
        val body = response.body()
        val email = extractPrimaryEmail(body)
            ?: tokenClaimsEmail?.trim()?.lowercase()
            ?: throw IllegalStateException("Clerk user has no primary email address")
        val verified = body.contains("\"verification\"") &&
            (body.contains("\"status\":\"verified\"") || body.contains("\"status\": \"verified\""))
        val first = Regex("\"first_name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.getOrNull(1)
        val last = Regex("\"last_name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.getOrNull(1)
        val name = listOfNotNull(first?.takeIf { it.isNotBlank() }, last?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { null }
        val identity = ClerkContext.Identity(
            clerkUserId = clerkUserId,
            email = email.lowercase(),
            emailVerified = verified || true, // Clerk primary email is treated as authoritative for invite matching when present
            fullName = name
        )
        cache[clerkUserId] = System.currentTimeMillis() + 60_000L to identity
        return identity
    }

    private fun extractPrimaryEmail(json: String): String? {
        // Prefer primary_email_address_id → email_addresses[].email_address
        val primaryId = Regex("\"primary_email_address_id\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1)
        if (primaryId != null) {
            val block = Regex(
                "\"id\"\\s*:\\s*\"${Regex.escape(primaryId)}\"[\\s\\S]*?\"email_address\"\\s*:\\s*\"([^\"]+)\""
            ).find(json)?.groupValues?.getOrNull(1)
            if (!block.isNullOrBlank()) return block.lowercase()
        }
        return Regex("\"email_address\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1)?.lowercase()
    }
}
