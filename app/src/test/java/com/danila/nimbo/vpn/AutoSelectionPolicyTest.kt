package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import org.junit.Assert.*
import org.junit.Test

class AutoSelectionPolicyTest {
    @Test fun missingCoreOrProxyAccumulatesFailuresInsteadOfResettingThem() {
        var failures = 0
        repeat(3) { failures = AutoSelectionPolicy.nextFailedChecks(failures, true, false) }
        assertTrue(AutoSelectionPolicy.shouldFailOver(failures, 90_000L))
    }

    @Test fun realSuccessOrNetworkHandoffBreaksTheFailureStreak() {
        assertEquals(0, AutoSelectionPolicy.nextFailedChecks(2, true, true))
        assertEquals(0, AutoSelectionPolicy.nextFailedChecks(2, false, false))
        assertFalse(AutoSelectionPolicy.shouldFailOver(
            AutoSelectionPolicy.nextFailedChecks(0, true, false), 180_000L))
    }

    private fun server(name: String, ping: Int?) = Server(
        name = name, host = "$name.example.com", port = 443,
        uuid = "test", protocol = "vless", ping = ping
    )

    private val now = VerifiedLatencyStore.FRESH_WINDOW_MS + 10_000L

    @Test fun restrictedBypassHintOutranksUnscopedRegularSuccessButNotViability() {
        val verified = server("regular", 900)
        val unknown = server("Обход LTE unknown", 1)
        val failed = server("Обход LTE failed", 0)
        val evidence = mapOf(
            verified.pingMeasurementKey() to VerifiedLatency(600, true, now),
            failed.pingMeasurementKey() to VerifiedLatency(-1, false, now)
        )
        assertEquals(listOf(unknown, verified, failed), AutoSelectionPolicy.order(
            listOf(failed, unknown, verified), true, evidence, now))
        assertEquals(listOf(verified, unknown, failed), AutoSelectionPolicy.order(
            listOf(failed, unknown, verified), false, evidence, now))
    }

    @Test fun untestedBypassSurvivesCandidateCapAheadOfUnscopedCachedSuccesses() {
        val cached = (1..8).map { server("wifi-$it", it) }
        val bypass = server("Обход LTE untested", null)
        val evidence = cached.associate { it.pingMeasurementKey() to VerifiedLatency(1, true, now) }
        val ranked = AutoSelectionPolicy.order(cached + bypass, true, evidence, now)
        for (cap in 1..cached.size) {
            assertEquals(bypass, ranked.take(cap).first())
        }
        assertEquals(cached.size + 1, ranked.size)
    }

    @Test fun restrictedSelectionUsesEvidenceThenLatencyWithinEachBypassClass() {
        for (prefix in listOf("regular", "Обход LTE")) {
            val unknown = server("$prefix unknown", 1)
            val slow = server("$prefix slow", 2)
            val fast = server("$prefix fast", 900)
            val evidence = mapOf(
                slow.pingMeasurementKey() to VerifiedLatency(800, true, now),
                fast.pingMeasurementKey() to VerifiedLatency(600, true, now)
            )
            assertEquals(listOf(fast, slow, unknown), AutoSelectionPolicy.order(
                listOf(unknown, slow, fast), true, evidence, now))
        }
    }

    @Test fun stableOrderWithoutPreferencePreservesRestrictedBypassPriority() {
        val cached = server("regular", 1)
        val bypass = server("Обход LTE untested", null)
        val evidence = mapOf(cached.pingMeasurementKey() to VerifiedLatency(1, true, now))
        val ranked = AutoSelectionPolicy.order(listOf(cached, bypass), true, evidence, now)
        assertEquals(listOf(bypass, cached), AutoSelectionPolicy.stableOrder(
            ranked, null, null, emptyMap(), 1_000L, evidence, now))
    }

    @Test fun explicitRememberedPreferenceIsStickinessRatherThanCurrentNetworkProof() {
        val preferred = server("regular", 101)
        val bypass = server("Обход LTE untested", null)
        for (evidence in listOf(emptyMap(), mapOf(
            preferred.pingMeasurementKey() to VerifiedLatency(101, true, now)
        ))) {
            val ranked = AutoSelectionPolicy.order(listOf(preferred, bypass), true, evidence, now)
            // The existing explicit preference contract is independent of cached success.
            assertEquals(listOf(preferred, bypass), AutoSelectionPolicy.stableOrder(
                ranked, AutoSelectionPolicy.candidateKey(preferred), null, emptyMap(),
                1_000L, evidence, now))
        }
        assertFalse(AutoSelectionPolicy.shouldFailOver(0, Long.MAX_VALUE))
    }

    @Test fun failedBypassCannotOutrankUntestedRegularCandidate() {
        val failed = server("Обход LTE failed", 1)
        val unknown = server("unknown", null)
        val evidence = mapOf(failed.pingMeasurementKey() to VerifiedLatency(-1, false, now))
        assertEquals(listOf(unknown, failed), AutoSelectionPolicy.order(
            listOf(failed, unknown), true, evidence, now))
    }

    @Test fun staleAndFutureEvidenceCannotOutrankUntestedCandidate() {
        val unknown = server("unknown", null)
        val suspect = server("Обход LTE cached", 1)
        val timestamps = listOf(now - VerifiedLatencyStore.FRESH_WINDOW_MS - 1, now + 1, Long.MIN_VALUE, Long.MAX_VALUE)
        for (timestamp in timestamps) {
            for (ok in listOf(true, false)) {
                val evidence = mapOf(suspect.pingMeasurementKey() to VerifiedLatency(if (ok) 1 else -1, ok, timestamp))
                for (restricted in listOf(true, false)) {
                    assertEquals("timestamp=$timestamp ok=$ok restricted=$restricted", listOf(unknown, suspect),
                        AutoSelectionPolicy.order(listOf(suspect, unknown), restricted, evidence, now))
                }
            }
        }
    }

    @Test fun freshnessBoundaryIsInclusiveButExpiresOneMillisecondLater() {
        val cached = server("cached", 1)
        val unknown = server("unknown", null)
        val evidence = mapOf(cached.pingMeasurementKey() to VerifiedLatency(800, true, now - VerifiedLatencyStore.FRESH_WINDOW_MS))
        assertEquals(cached, AutoSelectionPolicy.order(listOf(unknown, cached), false, evidence, now).first())
        assertEquals(unknown, AutoSelectionPolicy.order(listOf(cached, unknown), false, evidence, now + 1).first())
    }

    @Test fun invalidSuccessfulLatencyIsNotUsableEvidence() {
        val invalid = server("invalid", 1)
        val unknown = server("unknown", null)
        val evidence = mapOf(invalid.pingMeasurementKey() to VerifiedLatency(-1, true, now))
        assertEquals(listOf(unknown, invalid), AutoSelectionPolicy.order(listOf(invalid, unknown), false, evidence, now))
    }

    @Test fun verifiedLatencyUsesRawMeasurementsIncludingZeroAndStableTies() {
        val slow = server("slow", 1)
        val first = server("first", 900)
        val second = server("second", 2)
        val evidence = mapOf(
            slow.pingMeasurementKey() to VerifiedLatency(800, true, now),
            first.pingMeasurementKey() to VerifiedLatency(0, true, now),
            second.pingMeasurementKey() to VerifiedLatency(0, true, now)
        )
        assertEquals(listOf(first, second, slow), AutoSelectionPolicy.order(listOf(slow, first, second), false, evidence, now))
    }

    @Test fun allFailedCandidatesRemainAvailableInSubscriptionOrder() {
        val first = server("first", 500)
        val second = server("second", 1)
        val evidence = listOf(first, second).associate { it.pingMeasurementKey() to VerifiedLatency(-1, false, now) }
        assertEquals(listOf(first, second), AutoSelectionPolicy.order(listOf(first, second), false, evidence, now))
    }

    @Test fun rememberedFailedOrStaleRouteDoesNotLeapfrogUntestedCandidates() {
        val remembered = server("remembered", 1)
        val unknown = server("unknown", null)
        for (verdict in listOf(
            VerifiedLatency(-1, false, now),
            VerifiedLatency(1, true, now - VerifiedLatencyStore.FRESH_WINDOW_MS - 1),
            VerifiedLatency(1, true, now + 1),
            VerifiedLatency(-1, true, now)
        )) {
            val evidence = mapOf(remembered.pingMeasurementKey() to verdict)
            val ranked = AutoSelectionPolicy.order(listOf(remembered, unknown), true, evidence, now)
            assertEquals(listOf(unknown, remembered), AutoSelectionPolicy.stableOrder(
                ranked, AutoSelectionPolicy.candidateKey(remembered), null, emptyMap(),
                nowMs = 1_000L, verified = evidence, verificationNowMs = now))
        }
    }

    @Test fun verifiedWorkingPreferenceSurvivesTinyLatencyWins() {
        val current = server("current", 101)
        val faster = server("faster", 100)
        val evidence = mapOf(
            current.pingMeasurementKey() to VerifiedLatency(101, true, now),
            faster.pingMeasurementKey() to VerifiedLatency(100, true, now)
        )
        val ranked = AutoSelectionPolicy.order(listOf(current, faster), false, evidence, now)
        assertEquals(faster, ranked.first())
        assertEquals(listOf(current, faster), AutoSelectionPolicy.stableOrder(
            ranked, AutoSelectionPolicy.candidateKey(current), null, emptyMap(),
            nowMs = 1_000L, verified = evidence, verificationNowMs = now))
        assertFalse(AutoSelectionPolicy.shouldFailOver(0, Long.MAX_VALUE))
    }

    @Test fun monotonicCooldownOverridesFreshWallClockSuccessUntilExpiry() {
        val preferred = server("preferred", 1)
        val unknown = server("unknown", null)
        val key = AutoSelectionPolicy.candidateKey(preferred)
        val evidence = mapOf(preferred.pingMeasurementKey() to VerifiedLatency(1, true, now))
        val failedAt = 1_000L
        val expires = failedAt + ConnectionAttemptPolicy.CANDIDATE_COOLDOWN_MS
        fun ordered(cooldownNow: Long) = AutoSelectionPolicy.stableOrder(
            listOf(preferred, unknown), key, null, mapOf(key to failedAt), cooldownNow,
            verified = evidence, verificationNowMs = now)
        assertEquals(listOf(unknown, preferred), ordered(expires - 1))
        assertEquals(listOf(preferred, unknown), ordered(expires))
    }

    @Test fun oneVerifiedTunnelRequestIsEnoughWhenOtherControlSitesAreBlocked() {
        assertFalse(AutoSelectionPolicy.isUsable(listOf(-1, -1, -1, -1)))
        assertTrue(AutoSelectionPolicy.isUsable(listOf(30, -1, -1, -1)))
        assertTrue(AutoSelectionPolicy.isUsable(listOf(650, 900, -1, -1)))
    }

    @Test fun failedPingsDoNotBeatMeasuredServersOrExcludeCandidates() {
        val candidates = listOf(server("unknown", null), server("failed", -1), server("fast", 45))
        val ranked = AutoSelectionPolicy.order(candidates, false)
        assertEquals("fast", ranked.first().name)
        assertEquals(3, ranked.size)
    }

    @Test fun namedLteServersGetPriorityUnderRestrictionsWithoutDroppingRegularOnes() {
        val regular = server("Germany", 20)
        val bypass = server("Обход LTE 2", 300)
        assertEquals(bypass, AutoSelectionPolicy.order(listOf(regular, bypass), true).first())
        assertEquals(2, AutoSelectionPolicy.order(listOf(regular, bypass), true).size)
    }

    @Test fun distinctTransportsAreNotCollapsed() {
        val first = server("route", 30)
        val second = first.copy(network = "ws", path = "/ws")
        assertEquals(2, AutoSelectionPolicy.order(listOf(first, first, second), false).size)
        assertNotEquals(AutoSelectionPolicy.candidateKey(first), AutoSelectionPolicy.candidateKey(second))
        assertEquals(64, AutoSelectionPolicy.candidateKey(first).length)
    }

    @Test fun redirectsAndPortalPagesCannotConfirmConnectivity() {
        val target = AutoSelectionPolicy.targets.first()
        assertFalse(AutoSelectionPolicy.acceptsResponse(target, 302))
        assertFalse(AutoSelectionPolicy.acceptsResponse(target, 200))
        assertFalse(AutoSelectionPolicy.acceptsResponse(target, 403))
        assertTrue(AutoSelectionPolicy.acceptsResponse(target, 204))
    }

    @Test fun bypassTwoWinsWhenFasterBypassThreeCannotCarryTraffic() {
        val two = server("Обход LTE 2", 180)
        val three = server("Обход LTE 3", 20)
        val reports = mapOf(three to listOf(-1, -1, -1), two to listOf(700, 400, -1))
        val winner = AutoSelectionPolicy.order(listOf(two, three), true)
            .firstOrNull { AutoSelectionPolicy.isUsable(reports.getValue(it)) }
        assertEquals(two, winner)
    }

    @Test fun healthyCurrentRouteStaysSelectedEvenWhenAnotherPingIsLower() {
        val current = server("current", 220)
        val faster = server("faster", 20)
        val ranked = AutoSelectionPolicy.order(listOf(current, faster), false)
        assertEquals(faster, ranked.first())
        val stable = AutoSelectionPolicy.stableOrder(ranked, AutoSelectionPolicy.candidateKey(current),
            null, emptyMap(), 1_000L)
        assertEquals(current, stable.first())
    }

    @Test fun failoverRequiresRepeatedTotalFailuresAndDwellTime() {
        assertFalse(AutoSelectionPolicy.shouldFailOver(1, 100_000L))
        assertFalse(AutoSelectionPolicy.shouldFailOver(2, 100_000L))
        assertFalse(AutoSelectionPolicy.shouldFailOver(3, 80_000L))
        assertTrue(AutoSelectionPolicy.shouldFailOver(3, 90_000L))
    }

    @Test fun failedCurrentRouteCoolsDownButRemainsLastResort() {
        val current = server("current", 5)
        val alternate = server("alternate", 100)
        val key = AutoSelectionPolicy.candidateKey(current)
        val ranked = AutoSelectionPolicy.order(listOf(current, alternate), false)
        val ordered = AutoSelectionPolicy.stableOrder(ranked, key, key,
            mapOf(key to 1_000L), 1_010L)
        assertEquals(alternate, ordered.first())
        assertEquals(current, ordered.last())
        assertEquals(2, ordered.size)
    }
}
