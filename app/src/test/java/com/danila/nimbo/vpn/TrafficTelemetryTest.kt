package com.danila.nimbo.vpn

import com.danila.nimbo.mihomo.MihomoReply
import com.danila.nimbo.mihomo.MihomoException
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TrafficTelemetryTest {
    private fun decode(json: String, generation: Long = 42, expected: Long = 42) =
        TrafficTelemetry.decode(MihomoReply(JsonParser.parseString(json).asJsonObject, generation), expected)

    @Test fun acceptsMeasuredCumulativeRoutesAndActiveConnections() {
        val value = decode("""{"upload":100,"download":200,"proxyUpload":60,"proxyDownload":40,
            "directUpload":20,"directDownload":80,"routeAvailable":true,"tcpConnections":3,"udpConnections":0}""")!!
        assertEquals(100L, value.upload)
        assertEquals(200L, value.download)
        assertEquals(0.5, value.route!!.proxyShare!!, 0.000001)
        assertEquals(3L, value.tcpConnections)
        assertEquals(0L, value.udpConnections)
    }

    @Test fun missingCountersRemainUnavailableAndConnectionCountsNeverBecomeRouteBytes() {
        assertNull(decode("{}")) // old bridge / missing totals
        val value = decode("""{"upload":100,"download":200,"tcpConnections":900,"udpConnections":12}""")!!
        assertNull(value.route)
        val onlyTotals = decode("""{"upload":0,"download":0}""")!!
        assertNull(onlyTotals.tcpConnections)
        assertNull(onlyTotals.udpConnections)
        assertNull(onlyTotals.route)
    }

    @Test fun routeAvailabilityMustBeExplicitAndAllFourCountersMeasured() {
        assertNull(decode("""{"upload":0,"download":0,"routeAvailable":false,
            "proxyUpload":1,"proxyDownload":2,"directUpload":3,"directDownload":4}""")!!.route)
        assertNull(decode("""{"upload":0,"download":0,"routeAvailable":true,
            "proxyUpload":1,"proxyDownload":2,"directUpload":3}""")!!.route)
        val zero = decode("""{"upload":0,"download":0,"routeAvailable":true,
            "proxyUpload":0,"proxyDownload":0,"directUpload":0,"directDownload":0}""")!!.route!!
        assertNull(zero.proxyShare)
        assertEquals(0.0, zero.proxyBytes, 0.0)
    }

    @Test fun staleMalformedNegativeOrOverflowCountersAreRejected() {
        assertNull(decode("""{"upload":1,"download":2}""", generation = 41))
        listOf("-1", "1.5", "1e3", "9223372036854775808", "\"12\"", "null", "true").forEach {
            assertNull(it, decode("""{"upload":$it,"download":0}"""))
        }
        val badProtocol = decode("""{"upload":0,"download":0,"tcpConnections":-1,"udpConnections":"0"}""")!!
        assertNull(badProtocol.tcpConnections)
        assertNull(badProtocol.udpConnections)
    }

    @Test fun cumulativeWindowAddsBytesOnceAndRejectsPreviousGenerationAndCounterReset() {
        val window = TrafficTelemetryWindow(42)
        val first = decode("""{"upload":100,"download":250}""")!!
        assertEquals(TrafficDelta(100, 250), window.sample(first))
        assertEquals(TrafficDelta(0, 0), window.sample(first))
        assertNull(window.sample(first.copy(generation = 43, upload = 999)))
        assertNull(window.sample(first.copy(upload = 1)))
        assertEquals(TrafficDelta(10, 50), window.sample(first.copy(upload = 110, download = 300)))
        assertEquals(TrafficDelta(5, 7), TrafficTelemetryWindow(43).sample(first.copy(generation = 43, upload = 5, download = 7)))
    }

    @Test fun routeShareDoesNotOverflowLongWhenSummingBytes() {
        assertEquals(0.5, RouteTraffic(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE).proxyShare!!, 0.0)
    }

    @Test fun oldBridgeIsProbedOnlyOncePerGenerationAndCanRetryAfterReconnect() {
        listOf("UNKNOWN_OPERATION", "INVALID_REQUEST").forEach { code ->
            val poller = MihomoTelemetryPoller()
            var calls = 0
            val unsupported: (Long) -> MihomoReply = { calls++; throw MihomoException(code) }
            assertNull(poller.read(42, unsupported))
            repeat(10) { assertNull(poller.read(42, unsupported)) }
            assertEquals(code, 1, calls)
            assertNull(poller.read(43, unsupported))
            assertEquals(code, 2, calls)
            poller.reset()
            val value = poller.read(43) { MihomoReply(JsonParser.parseString("""{"upload":5,"download":8}""").asJsonObject, it) }
            assertEquals(5L, value!!.upload)
        }
    }

    @Test fun transientTelemetryErrorsNeverEscapeAndNextMeasuredReadCanRecover() {
        val poller = MihomoTelemetryPoller()
        assertNull(poller.read(42) { throw IllegalStateException("native failed") })
        assertNull(poller.read(42) { MihomoReply(JsonParser.parseString("{}").asJsonObject, it) })
        assertNull(poller.read(42) { MihomoReply(JsonParser.parseString("""{"upload":1,"download":2}""").asJsonObject, 41) })
        assertNotNull(poller.read(42) { MihomoReply(JsonParser.parseString("""{"upload":1,"download":2}""").asJsonObject, it) })
    }
}
