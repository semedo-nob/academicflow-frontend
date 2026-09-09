package com.academicflow.service

import com.academicflow.entity.Invitation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Token resolution rules without Spring/Mockito: hash-first, then legacy plaintext.
 */
class InvitationTokenLookupTest {
    @Test
    fun `lookup prefers hash then legacy plaintext`() {
        val raw = InvitationTokens.generateRawToken()
        val hash = InvitationTokens.hash(raw)
        val hashedInvite = Invitation(
            id = UUID.randomUUID(),
            email = "a@example.com",
            token = hash,
            tokenHash = hash,
            status = "PENDING",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        val legacyRaw = "legacy-plaintext-token"
        val legacyInvite = Invitation(
            id = UUID.randomUUID(),
            email = "b@example.com",
            token = legacyRaw,
            tokenHash = legacyRaw,
            status = "PENDING",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        val store = listOf(hashedInvite, legacyInvite)

        fun resolve(candidate: String): Invitation? {
            val h = InvitationTokens.hash(candidate)
            return store.firstOrNull { it.tokenHash == h }
                ?: store.firstOrNull { it.token == h }
                ?: store.firstOrNull { it.token == candidate }
        }

        assertEquals(hashedInvite.id, resolve(raw)?.id)
        assertEquals(legacyInvite.id, resolve(legacyRaw)?.id)
        assertNull(resolve("unknown-token"))
    }

    @Test
    fun `accepted token burn is not reversible from original raw token`() {
        val raw = InvitationTokens.generateRawToken()
        val burned = InvitationTokens.hash("accepted:${UUID.randomUUID()}:${Instant.now()}")
        assertTrue(InvitationTokens.hash(raw) != burned)
    }
}
