package com.danila.nimbo.shared.ui

import kotlin.test.*

class NimboTrafficTelemetryTest {
    @Test fun unsupportedAndStoppedTelemetryRemainUnavailable() {
        val defaults = NimboUiState()
        assertFalse(defaults.adBlockingEnabled)
        assertNull(defaults.routeTraffic)
        assertNull(defaults.tcpConnections)
        assertNull(defaults.udpConnections)
        val stopped = defaults.copy(routeTraffic = NimboRouteTraffic(100, 200, 300, 400), tcpConnections = 4)
        assertNull(measuredRoutes(stopped))
        assertEquals("Недоступно", activeConnectionLabel(stopped, stopped.tcpConnections))
        assertEquals("Недоступно", activeConnectionLabel(stopped.copy(vpnState = "connected"), null))
    }

    @Test fun routeShareUsesBytesAndKeepsZeroEmpty() {
        assertEquals(1f, NimboRouteTraffic(60, 40).proxyFraction)
        assertEquals(.25f, NimboRouteTraffic(60, 40, 100, 200).proxyFraction)
        assertNull(NimboRouteTraffic().proxyFraction)
        assertNull(NimboRouteTraffic(-1).proxyFraction)
        assertEquals(.5f, NimboRouteTraffic(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE).proxyFraction)
        assertEquals("0", activeConnectionLabel(NimboUiState(vpnState = "connected"), 0))
        assertEquals("Недоступно", activeConnectionLabel(NimboUiState(vpnState = "connected"), -1))
    }

    @Test fun decoderRejectsMissingNegativeAndMalformedCounters() {
        assertNull(NimboTelemetryDecoder.decode(null))
        assertNull(NimboTelemetryDecoder.decode("{}"))
        assertNull(NimboTelemetryDecoder.decode("invalid"))
        assertNull(NimboTelemetryDecoder.decode("""{"upload":-1,"download":0}"""))
        assertNull(NimboTelemetryDecoder.decode("""{"upload":"1","download":0}"""))
        val unavailable = NimboTelemetryDecoder.decode("""{"upload":1,"download":2,"routeAvailable":true,"proxyUpload":0,"proxyDownload":0,"directUpload":0,"tcpConnections":-1,"udpConnections":2147483648}""")!!
        assertNull(unavailable.routeTraffic)
        assertNull(unavailable.tcpConnections)
        assertNull(unavailable.udpConnections)
        val measured = NimboTelemetryDecoder.decode("""{"upload":10,"download":20,"routeAvailable":true,"proxyUpload":10,"proxyDownload":20,"directUpload":0,"directDownload":0,"tcpConnections":0,"udpConnections":3}""")!!
        assertEquals(1f, measured.routeTraffic?.proxyFraction)
        assertEquals(0, measured.tcpConnections)
        assertEquals(3, measured.udpConnections)
        val legacy = NimboTelemetryDecoder.decode("""{"upload":0,"download":0,"routeAvailable":false,"proxyUpload":0,"proxyDownload":0,"directUpload":0,"directDownload":0}""")!!
        assertNull(legacy.routeTraffic)
    }
}
