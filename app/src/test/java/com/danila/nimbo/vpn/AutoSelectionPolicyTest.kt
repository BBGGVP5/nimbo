package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import org.junit.Assert.*
import org.junit.Test

class AutoSelectionPolicyTest {
    private fun server(name: String, ping: Int?) = Server(
        name = name, host = "$name.example.com", port = 443,
        uuid = "test", protocol = "vless", ping = ping
    )

    @Test fun fastButBlockedCandidateIsRejected() {
        assertFalse(AutoSelectionPolicy.isUsable(listOf(-1, -1, -1)))
        assertFalse(AutoSelectionPolicy.isUsable(listOf(30, -1, -1)))
        assertTrue(AutoSelectionPolicy.isUsable(listOf(650, 900, -1)))
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
}
