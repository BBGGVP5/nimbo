package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PingWorkPlannerTest {

    @Test
    fun `nodes on the same endpoint produce independent measurements`() {
        val plan = PingWorkPlanner.build(
            listOf(
                PingWorkCandidate("node-a", "shared.example.com" to 443),
                PingWorkCandidate("node-b", "shared.example.com" to 443)
            )
        )

        assertEquals(2, plan.items.size)
        assertEquals(listOf("node-a", "node-b"), plan.items.map { it.resultKey })
    }

    @Test
    fun `unresolved node is reported without hiding resolved nodes`() {
        val plan = PingWorkPlanner.build(
            listOf(
                PingWorkCandidate("resolved", "edge.example.com" to 8443),
                PingWorkCandidate("unresolved", null)
            )
        )

        assertEquals(1, plan.items.size)
        assertEquals("resolved", plan.items.single().resultKey)
        assertTrue("unresolved" in plan.unresolvedKeys)
    }

    @Test
    fun `transport metadata is preserved for protocol aware ping`() {
        val plan = PingWorkPlanner.build(
            listOf(
                PingWorkCandidate(
                    resultKey = "hy2-node",
                    target = "ru.example.com" to 443,
                    serverProtocol = "hysteria",
                    network = "hysteria"
                )
            )
        )

        assertEquals("hysteria", plan.items.single().serverProtocol)
        assertEquals("hysteria", plan.items.single().network)
    }
}
