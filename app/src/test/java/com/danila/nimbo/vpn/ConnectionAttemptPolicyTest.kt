package com.danila.nimbo.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAttemptPolicyTest {

    @Test
    fun `attempt timeout never exceeds remaining cycle budget`() {
        assertEquals(4_000L, ConnectionAttemptPolicy.attemptTimeoutMs(4_000L))
        assertEquals(15_000L, ConnectionAttemptPolicy.attemptTimeoutMs(60_000L))
        assertEquals(0L, ConnectionAttemptPolicy.attemptTimeoutMs(0L))
    }

    @Test
    fun `failed alternatives cool down but explicit selection is still allowed`() {
        assertTrue(
            ConnectionAttemptPolicy.isCoolingDown(
                failedAtMs = 10_000L,
                nowMs = 20_000L,
                explicitSelection = false
            )
        )
        assertFalse(
            ConnectionAttemptPolicy.isCoolingDown(
                failedAtMs = 10_000L,
                nowMs = 20_000L,
                explicitSelection = true
            )
        )
        assertFalse(
            ConnectionAttemptPolicy.isCoolingDown(
                failedAtMs = 10_000L,
                nowMs = 10_000L + ConnectionAttemptPolicy.CANDIDATE_COOLDOWN_MS,
                explicitSelection = false
            )
        )
    }

    @Test
    fun `restriction requires working internet and blocked target services`() {
        assertTrue(ConnectionAttemptPolicy.isRestricted(listOf(true, false), listOf(false, false)))
        assertFalse(ConnectionAttemptPolicy.isRestricted(listOf(false, false), listOf(false, false)))
        assertFalse(ConnectionAttemptPolicy.isRestricted(listOf(true, true), listOf(true, false)))
    }

    @Test
    fun `deterministic config errors are not retried`() {
        assertFalse(ConnectionAttemptPolicy.shouldRetry("invalid config: unsupported protocol", attempt = 0, maxAttempts = 2))
        assertTrue(ConnectionAttemptPolicy.shouldRetry("connection timeout", attempt = 0, maxAttempts = 2))
        assertFalse(ConnectionAttemptPolicy.shouldRetry("connection timeout", attempt = 1, maxAttempts = 2))
    }

    @Test
    fun `ranking stops early enough to reserve a final connection attempt`() {
        assertFalse(ConnectionAttemptPolicy.canStartRankingProbe(ConnectionAttemptPolicy.FINAL_CONNECT_RESERVE_MS))
        assertTrue(
            ConnectionAttemptPolicy.canStartRankingProbe(
                ConnectionAttemptPolicy.FINAL_CONNECT_RESERVE_MS + ConnectionAttemptPolicy.MIN_USEFUL_ATTEMPT_MS
            )
        )
    }

    @Test
    fun `restricted network detection only runs when auto rotation can use it`() {
        assertFalse(
            ConnectionAttemptPolicy.shouldDetectRestrictedNetwork(
                autoRotationEnabled = false,
                selectedIsAutoBalancer = false
            )
        )
        assertFalse(
            ConnectionAttemptPolicy.shouldDetectRestrictedNetwork(
                autoRotationEnabled = true,
                selectedIsAutoBalancer = true
            )
        )
        assertTrue(
            ConnectionAttemptPolicy.shouldDetectRestrictedNetwork(
                autoRotationEnabled = true,
                selectedIsAutoBalancer = false
            )
        )
    }
}
