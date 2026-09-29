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
        assertTrue(TunnelHealthPolicy.REQUEST_TIMEOUT_MS <= 900)
    }

    @Test
    fun `normal startup never waits for an external endpoint`() {
        assertEquals(
            TunnelHealthPolicy.VerificationMode.BACKGROUND,
            TunnelHealthPolicy.verificationMode(verifyTraffic = true, recoveryMode = false)
        )
    }

    @Test
    fun `automatic recovery never waits for an external endpoint`() {
        assertEquals(
            TunnelHealthPolicy.VerificationMode.BACKGROUND,
            TunnelHealthPolicy.verificationMode(verifyTraffic = true, recoveryMode = true)
        )
    }

    @Test
    fun `internal probe connection skips duplicate traffic verification`() {
        assertEquals(
            TunnelHealthPolicy.VerificationMode.SKIP,
            TunnelHealthPolicy.verificationMode(verifyTraffic = false, recoveryMode = false)
        )
    }

    @Test
    fun `successful external probe confirms startup`() {
        assertEquals(
            TunnelHealthPolicy.StartupAcceptance.CONFIRMED,
            TunnelHealthPolicy.startupAcceptance(
                coreRunning = true,
                underlyingNetworkAvailable = true,
                latenciesMs = listOf(-1, 123)
            )
        )
    }

    @Test
    fun `external probe timeout is provisional while tunnel prerequisites stay alive`() {
        assertEquals(
            TunnelHealthPolicy.StartupAcceptance.PROVISIONAL,
            TunnelHealthPolicy.startupAcceptance(
                coreRunning = true,
                underlyingNetworkAvailable = true,
                latenciesMs = listOf(-1, -1)
            )
        )
    }

    @Test
    fun `stopped core rejects startup even when external probe responded`() {
        assertEquals(
            TunnelHealthPolicy.StartupAcceptance.REJECTED,
            TunnelHealthPolicy.startupAcceptance(
                coreRunning = false,
                underlyingNetworkAvailable = true,
                latenciesMs = listOf(80)
            )
        )
    }

    @Test
    fun `missing physical network rejects startup even when core is alive`() {
        assertEquals(
            TunnelHealthPolicy.StartupAcceptance.REJECTED,
            TunnelHealthPolicy.startupAcceptance(
                coreRunning = true,
                underlyingNetworkAvailable = false,
                latenciesMs = listOf(80)
            )
        )
    }
}
