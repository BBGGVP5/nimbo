package com.danila.nimbo.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun sanitize_removesConnectionSecretsAndAddresses() {
        val source = """
            vless://user-secret@example.com:443?security=reality
            ip=192.168.10.42:8443 host=edge.example.org
            uuid=550e8400-e29b-41d4-a716-446655440000 password=hunter2
        """.trimIndent()

        val sanitized = LogSanitizer.sanitize(source)

        assertFalse(sanitized.contains("user-secret"))
        assertFalse(sanitized.contains("192.168.10.42"))
        assertFalse(sanitized.contains("edge.example.org"))
        assertFalse(sanitized.contains("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(sanitized.contains("hunter2"))
        assertTrue(sanitized.contains("[URL]"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun sanitize_removesJsonCredentialsAndLongTokens() {
        val source = """{"id":"secret-user","publicKey":"abcdefghijklmnopqrstuvwxyz1234567890","name":"EU"}"""

        val sanitized = LogSanitizer.sanitize(source)

        assertFalse(sanitized.contains("secret-user"))
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyz1234567890"))
        assertTrue(sanitized.contains("[REDACTED]"))
        assertTrue(sanitized.contains("EU"))
    }

    @Test
    fun sanitize_keepsUsefulNonSensitiveContext() {
        val source = "Connection failed after 3 attempts: timeout"

        assertTrue(LogSanitizer.sanitize(source).contains(source))
    }
}
