package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRequestIdentityTest {
    @Test
    fun `canonical hwid is a lowercase UUID accepted by the response rule`() {
        val hwid = SubscriptionRequestIdentity.canonicalUuid("A0A1A2A3-0000-4000-8000-000000000000")

        assertEquals("a0a1a2a3-0000-4000-8000-000000000000", hwid)
        assertTrue(
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
                .matches(hwid)
        )
    }

    @Test
    fun `subscription user agent is always Nimbo`() {
        assertEquals("Nimbo/2.0.0/Android", SubscriptionRequestIdentity.userAgent("2.0.0"))
    }
}
