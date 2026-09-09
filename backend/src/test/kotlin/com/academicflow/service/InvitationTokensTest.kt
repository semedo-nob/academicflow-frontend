package com.academicflow.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationTokensTest {
    @Test
    fun `raw tokens are long enough and unique`() {
        val a = InvitationTokens.generateRawToken()
        val b = InvitationTokens.generateRawToken()
        assertEquals(64, a.length)
        assertNotEquals(a, b)
    }

    @Test
    fun `hash is deterministic and not equal to raw token`() {
        val raw = InvitationTokens.generateRawToken()
        val h1 = InvitationTokens.hash(raw)
        val h2 = InvitationTokens.hash(raw)
        assertEquals(h1, h2)
        assertEquals(64, h1.length)
        assertNotEquals(raw, h1)
        assertFalse(h1.contains(raw.take(8)))
    }

    @Test
    fun `different tokens produce different hashes`() {
        assertNotEquals(
            InvitationTokens.hash("alpha-token"),
            InvitationTokens.hash("beta-token")
        )
    }
}
