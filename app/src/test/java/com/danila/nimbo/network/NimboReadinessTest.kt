package com.danila.nimbo.network

import com.danila.nimbo.vpn.HealthProxySession
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NimboReadinessTest {
    @Test fun `two reserved listeners never receive the same port`() {
        repeat(100) {
            val (target, readiness) = NimboProbePorts.allocate(true)
            assertNotEquals(target, readiness)
            assertTrue(target in 1..65535 && readiness!! in 1..65535)
        }
        assertNull(NimboProbePorts.allocate(false).second)
    }
    private fun source(balanced: Boolean = true): JSONObject = JSONObject("""{
      "outbounds":[{"tag":"node-a","protocol":"http"},{"tag":"node-b","protocol":"http"}],
      "routing":{"rules":[{"type":"field","inboundTag":["nimbo-health-in"],"balancerTag":"auto"}],
        "balancers":[{"tag":"auto","selector":["node-a"],"strategy":{"type":"leastPing"}}]},
      "observatory":{"subjectSelector":["node-"],"probeUrl":"http://fixture.invalid","enableConcurrency":true},
      "burstObservatory":{"subjectSelector":["node-"]}
    }""").apply {
        if (!balanced) getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
            .apply { remove("balancerTag"); put("outboundTag", "node-a") }
    }

    @Test fun `explicit node has no observer IO or readiness listener`() {
        val output = JSONObject(NodePingConfig.prepare(source(false).toString(), 32000, HealthProxySession("row"))!!)
        assertFalse(output.has("observatory")); assertFalse(output.has("burstObservatory")); assertFalse(output.has("metrics"))
        assertEquals(1, output.getJSONArray("inbounds").length())
    }
    @Test fun `leastPing readiness is authenticated tagged local metrics and original route remains first`() {
        val session = HealthProxySession("row")
        val output = JSONObject(NodePingConfig.prepare(source().toString(), 32000, session, 32001)!!)
        assertEquals(setOf("node-a"), NodePingConfig.leastPingCandidates(output.toString()))
        assertFalse(output.getJSONObject("metrics").has("listen"))
        val inbound = output.getJSONArray("inbounds").getJSONObject(1)
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(session.password, inbound.getJSONObject("settings").getJSONArray("accounts").getJSONObject(0).getString("pass"))
        assertEquals("auto", output.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("balancerTag"))
        assertEquals("leastPing", output.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).getJSONObject("strategy").getString("type"))
        for (key in listOf("observatory", "burstObservatory")) assertEquals("[\"node-a\"]", output.getJSONObject(key).getJSONArray("subjectSelector").toString())
    }
    @Test fun `reserved metrics tag cannot collide with a node or selector`() {
        val root = source()
        root.getJSONArray("outbounds").put(JSONObject().put("tag", NodePingConfig.READINESS_TAG).put("protocol", "http"))
        assertNull(NodePingConfig.prepare(root.toString(), 32000, HealthProxySession("row"), 32001))
        val broad = source()
        broad.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).put("selector", JSONArray().put("n"))
        assertNull(NodePingConfig.prepare(broad.toString(), 32000, HealthProxySession("row"), 32001))
    }
    @Test fun `observations must belong to selected alive candidate`() {
        assertFalse(NimboPingReadiness.ready("""{"observatory":{"other":{"alive":true,"delay":0}}}""", setOf("node-a")))
        assertTrue(NimboPingReadiness.ready("""{"observatory":{"node-a":{"alive":true,"delay":0}}}""", setOf("node-a")))
        assertTrue(NimboPingReadiness.ready("""{"observatory":{"node-a":{"alive":true}}}""", setOf("node-a")))
        assertFalse(NimboPingReadiness.ready("""{"observatory":{"node-a":{"alive":false,"delay":0}}}""", setOf("node-a")))
        assertFalse(NimboPingReadiness.ready("x".repeat(NimboPingReadiness.MAX_BODY_BYTES + 1), setOf("node-a")))
    }
    @Test fun `readiness obeys deadline and never calls target while unavailable`() = runBlocking {
        var now = 0L; var polls = 0; var target = 0
        val result = NimboPingReadiness.measureOnce({ NimboPingReadiness.await(setOf("a"), 500, { now },
            read = { polls++; "{}" }, pause = { now += it }) }, { target++; 10 })
        assertEquals(-1, result); assertEquals(0, target); assertTrue(polls in 1..5)
    }
    @Test fun `late ready snapshot past deadline is not success`() = runBlocking {
        var now = 0L
        assertFalse(NimboPingReadiness.await(setOf("a"), 500, { now }, read = {
            now = 501; """{"observatory":{"a":{"alive":true,"delay":1}}}"""
        }))
    }
    @Test fun `loop count bounds even a stalled clock`() = runBlocking {
        var polls = 0
        assertFalse(NimboPingReadiness.await(setOf("a"), 500, { 0L }, read = { polls++; "{}" }, pause = {}))
        assertEquals(130, polls)
    }
    @Test fun `successful readiness makes exactly one failing target request without retry`() = runBlocking {
        var calls = 0
        assertEquals(-1, NimboPingReadiness.measureOnce({ true }, { calls++; -1 }))
        assertEquals(1, calls)
    }
    @Test fun `cancelled readiness stops polling and does not send target`() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var targets = 0
        val job = launch {
            NimboPingReadiness.measureOnce({ NimboPingReadiness.await(setOf("a"), 5000, { 0L }, read = {
                entered.complete(Unit); awaitCancellation()
            }) }, { targets++; 0 })
        }
        entered.await(); job.cancelAndJoin(); assertEquals(0, targets)
    }
    @Test fun `approximation is presentation only preserving other protocols failures and zero`() {
        assertEquals(300, displayPingMs(990, 5)); assertEquals("≈420", displayPingLabel(1387, 5))
        assertEquals("≈0", displayPingLabel(0, 5)); assertEquals("—", displayPingLabel(-1, 5))
        assertNull(displayPingMs(null, 5)); assertEquals(-1, displayPingMs(-1, 5))
        for (protocol in 0..4) assertEquals("990", displayPingLabel(990, protocol))
        assertEquals(4, pingBars(displayPingMs(326, 5))); assertEquals(3, pingBars(displayPingMs(330, 5)))
    }
}
