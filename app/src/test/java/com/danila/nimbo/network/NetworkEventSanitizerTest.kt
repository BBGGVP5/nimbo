package com.danila.nimbo.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEventSanitizerTest {
    @Test
    fun removesTokensQueriesAndUuids() {
        val sanitized = NetworkEventSanitizer.sanitize(
            "https://vpn.example/sub?token=abc uuid=550e8400-e29b-41d4-a716-446655440000"
        ).orEmpty()
        assertFalse(sanitized.contains("abc"))
        assertFalse(sanitized.contains("550e8400"))
        assertTrue(sanitized.contains("[hidden]"))
    }
}
