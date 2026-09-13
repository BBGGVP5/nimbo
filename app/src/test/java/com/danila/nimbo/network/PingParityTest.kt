package com.danila.nimbo.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PingParityTest {
    @Test fun `protocol IDs and default remain compatible`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), PingProtocol.entries.map { it.id })
        assertEquals(PingProtocol.TCP, PingConfig().protocol)
        assertEquals(PingProtocol.TCP, PingProtocol.fromId(99))
        assertEquals(PingProtocol.NIMBO, PingProtocol.fromId(5))
        PingProtocol.entries.forEach { assertEquals(it, PingProtocol.fromId(it.id)) }
    }

    @Test fun `Nimbo never becomes HEAD or a direct fallback`() {
        listOf(false, true).forEach { proxy ->
            val config = PingConfig(protocol = PingProtocol.NIMBO, useProxy = proxy)
            assertEquals(PingProtocol.NIMBO, PingManager.effectiveProtocol(config))
            assertEquals(listOf(PingProtocol.NIMBO), PingManager.protocolAttempts(config, "hysteria", "quic"))
        }
    }

    @Test fun `Nimbo disconnected returns unavailable even with direct switch off`() = runBlocking {
        assertEquals(-1, PingManager.ping("must-not-resolve.invalid", config = PingConfig(protocol = PingProtocol.NIMBO)))
    }

    @Test fun `explicit ICMP never turns into a TCP success`() {
        assertEquals(listOf(PingProtocol.ICMP), PingManager.protocolAttempts(PingConfig(protocol = PingProtocol.ICMP), "vless", "tcp"))
    }

    @Test fun `display keys retain stored dots and append both`() {
        assertEquals(listOf("numeric", "bars", "both", "dots"), PingDisplay.entries.map { it.key })
        assertEquals(PingDisplay.DOTS, PingDisplay.fromId(2))
        assertEquals(PingDisplay.BOTH, PingDisplay.fromId(3))
        assertEquals(PingDisplay.NUMERIC, PingDisplay.fromId(99))
        PingDisplay.entries.forEach { assertEquals(it, PingDisplay.fromKey(it.key)) }
    }

    @Test fun `bar boundaries include zero but exclude missing failure and progress`() {
        mapOf(0 to 4, 99 to 4, 100 to 3, 199 to 3, 200 to 2, 399 to 2, 400 to 1, 9999 to 1, -1 to 0)
            .forEach { (ms, bars) -> assertEquals(bars, pingBars(ms)) }
        assertEquals(0, pingBars(null))
        assertEquals(0, pingBars(0, inProgress = true))
        assertEquals(0, pingBars(99, inProgress = true))
    }

    @Test fun `bulk and single active route attribution requires exact identity`() {
        val owner = "profile-A|node-A|same-host:443"
        val unrelated = "profile-B|node-B|same-host:443"
        assertEquals(owner, ActiveRoutePingPolicy.resultKey(owner, null))
        assertEquals(owner, ActiveRoutePingPolicy.resultKey(owner, listOf(unrelated, owner)))
        assertNull(ActiveRoutePingPolicy.resultKey(owner, listOf(unrelated)))
        assertNull(ActiveRoutePingPolicy.resultKey(null, listOf(owner)))
        assertNull(ActiveRoutePingPolicy.resultKey(owner, emptyList()))
    }

    @Test fun `settings changes invalidate in-flight samples but display does not`() {
        val settings = PingMeasurementSettings(0, "https://example.test/", 3, false)
        val guard = PingRunGuard()
        val run = guard.begin(settings)
        assertFalse(guard.accepts(run, settings.copy(protocol = 5)))
        assertFalse(guard.accepts(run, settings.copy(url = "https://other.test/")))
        assertFalse(guard.accepts(run, settings.copy(timeoutSeconds = 10)))
        assertFalse(guard.accepts(run, settings.copy(throughProxy = true)))
        assertTrue(guard.accepts(run, settings))
    }

    @Test fun `cancel and replacement prevent an old run from flushing into the next one`() {
        val settings = PingMeasurementSettings(5, "https://example.test/", 3, false)
        val guard = PingRunGuard()
        val first = guard.begin(settings)
        guard.cancel()
        assertFalse(guard.accepts(first, settings))
        val second = guard.begin(settings)
        assertFalse(guard.accepts(first, settings))
        assertTrue(guard.accepts(second, settings))
    }
}
