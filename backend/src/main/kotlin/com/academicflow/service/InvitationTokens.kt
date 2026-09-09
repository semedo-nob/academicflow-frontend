package com.academicflow.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat

object InvitationTokens {
    private val random = SecureRandom()

    fun generateRawToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.trim().toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
