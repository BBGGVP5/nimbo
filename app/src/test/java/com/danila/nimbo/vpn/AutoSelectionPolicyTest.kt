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
