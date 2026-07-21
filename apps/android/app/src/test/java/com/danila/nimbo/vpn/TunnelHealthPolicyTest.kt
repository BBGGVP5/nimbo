package com.danila.nimbo.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelHealthPolicyTest {

    @Test
    fun `one independent endpoint is enough to confirm the tunnel`() {
        assertTrue(TunnelHealthPolicy.isHealthy(listOf(-1, 123)))
        assertFalse(TunnelHealthPolicy.isHealthy(listOf(-1, -1)))
        assertFalse(TunnelHealthPolicy.isHealthy(emptyList()))
    }

    @Test
    fun `success count ignores failed probes`() {
        assertEquals(2, TunnelHealthPolicy.successCount(listOf(40, -1, 90)))
    }

    @Test
    fun `health endpoints are independent and use https`() {
        assertTrue(TunnelHealthPolicy.healthTargets.size >= 2)
        assertEquals(
            TunnelHealthPolicy.healthTargets.size,
            TunnelHealthPolicy.healthTargets.map { it.url.removePrefix("https://").substringBefore('/') }.distinct().size
        )
        assertTrue(TunnelHealthPolicy.healthTargets.all { it.url.startsWith("https://") })
    }

    @Test
    fun `health probe timeout stays inside fast connect budget`() {
        assertTrue(TunnelHealthPolicy.REQUEST_TIMEOUT_MS <= 1_500)
    }
}
