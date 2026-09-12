package com.danila.nimbo.network

import com.danila.nimbo.model.Server
import com.danila.nimbo.model.SmartServerGroup
import com.danila.nimbo.model.SmartServerHealth
import com.danila.nimbo.model.SmartServerStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartServerSelectorTest {
    @Test
    fun unstableFastServerLosesToHealthyServerInBalancedMode() {
        val fast = server("fast", "one.example", 40)
        val stable = server("stable", "two.example", 80)
        val group = SmartServerGroup(
            id = "g",
            name = "Auto",
            serverKeys = listOf(fast.selectionKey(), stable.selectionKey()),
            strategy = SmartServerStrategy.BALANCED
        )
        val result = SmartServerSelector.select(
            group,
            listOf(fast, stable),
            mapOf(
                fast.selectionKey() to SmartServerHealth(fast.selectionKey(), 40, successRate = 0.4),
                stable.selectionKey() to SmartServerHealth(stable.selectionKey(), 80, successRate = 0.99)
            ),
            nowMs = 1_000_000L
        )
        assertEquals("stable", result?.server?.name)
    }

    @Test
    fun currentServerIsKeptWhenDifferenceIsBelowThreshold() {
        val one = server("one", "one.example", 50)
        val two = server("two", "two.example", 55)
        val group = SmartServerGroup(
            id = "g",
            name = "Auto",
            serverKeys = listOf(one.selectionKey(), two.selectionKey()),
            strategy = SmartServerStrategy.LOWEST_LATENCY,
            switchThresholdPercent = 20
        )
        val result = SmartServerSelector.select(
            group,
            listOf(one, two),
            emptyMap(),
            currentServerKey = two.selectionKey()
        )
        assertEquals("two", result?.server?.name)
    }

    private fun server(name: String, host: String, ping: Int) = Server(
        name = name,
        host = host,
        port = 443,
        uuid = name,
        protocol = "vless",
        ping = ping
    )
}
