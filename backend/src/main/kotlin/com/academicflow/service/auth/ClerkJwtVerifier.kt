package com.academicflow.service.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

data class VerifiedClerkToken(
    val clerkUserId: String,
    val sessionId: String?,
    val claims: JWTClaimsSet
)

/**
 * Verifies Clerk session JWTs using the instance JWKS endpoint.
 */
@Component
class ClerkJwtVerifier(
    @Value("\${academicflow.clerk.issuer:}") private val issuer: String,
    @Value("\${academicflow.clerk.jwks-url:}") private val jwksUrlOverride: String
) {
    private val log = LoggerFactory.getLogger(ClerkJwtVerifier::class.java)
    private val processor = AtomicReference<DefaultJWTProcessor<SecurityContext>?>()

    fun isConfigured(): Boolean = issuer.isNotBlank() || jwksUrlOverride.isNotBlank()

    fun verify(bearerToken: String): VerifiedClerkToken {
        val jwtProcessor = processor.get() ?: buildProcessor().also { processor.set(it) }
        val claims = jwtProcessor.process(bearerToken.trim(), null)
        val sub = claims.subject?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Clerk token missing subject")
        if (issuer.isNotBlank() && !claims.issuer.isNullOrBlank()) {
            val expected = issuer.trimEnd('/')
            val actual = claims.issuer.trimEnd('/')
            if (actual != expected) {
                throw IllegalArgumentException("Clerk token issuer mismatch")
            }
        }
        return VerifiedClerkToken(
            clerkUserId = sub,
            sessionId = claims.getStringClaim("sid"),
            claims = claims
        )
    }

    private fun buildProcessor(): DefaultJWTProcessor<SecurityContext> {
        val jwksUrl = jwksUrlOverride.takeIf { it.isNotBlank() }
            ?: issuer.trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/.well-known/jwks.json" }
            ?: throw IllegalStateException("Clerk JWKS URL / issuer is not configured")
        log.info("clerk.jwks_configured url={}", jwksUrl)
        val retriever = DefaultResourceRetriever(5_000, 5_000)
        val keySource: JWKSource<SecurityContext> = RemoteJWKSet(URI(jwksUrl).toURL(), retriever)
        val proc = DefaultJWTProcessor<SecurityContext>()
        proc.jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, keySource)
        return proc
    }
}
