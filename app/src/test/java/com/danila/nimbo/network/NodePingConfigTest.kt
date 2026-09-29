package com.danila.nimbo.network

import com.danila.nimbo.model.Server
import com.danila.nimbo.vpn.HealthProxySession
import com.danila.nimbo.vpn.LocalProxyConfig
import com.danila.nimbo.vpn.XrayManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NodePingConfigTest {
    private fun config(node: JSONObject, tag: String = "node") = JSONObject()
        .put("inbounds", JSONArray().put(JSONObject().put("protocol", "tun")))
        .put("env", JSONObject().put("xray.tun.fd", "123"))
        .put("outbounds", JSONArray().put(JSONObject().put("protocol", "freedom").put("tag", "direct")).put(node))
        .put("routing", JSONObject().put("rules", LocalProxyConfig.prependRoute(JSONArray(), tag, null)))
        .put("dns", JSONObject().put("servers", JSONArray().put("9.9.9.9")))

    @Test fun `keeps complete Reality XHTTP node and uses independent authenticated port with deny default`() {
        val server = Server("node", "edge.example", 8443, "user-id", "vless", security = "reality", network = "xhttp",
            sni = "front.example", publicKey = "public-key", shortId = "01", path = "/original", flow = "xtls-rprx-vision")
        val node = JSONObject().put("tag", "node").put("protocol", "vless")
            .put("settings", XrayManager.buildOutboundSettings(server, "vless"))
            .put("streamSettings", XrayManager.buildStreamSettings(server))
        node.getJSONObject("streamSettings").getJSONObject("xhttpSettings")
            .put("extra", JSONObject().put("xPaddingBytes", "17-31").put("headers", JSONObject().put("X-Test", "preserve")))
        val source = config(node)
        val before = source.toString()
        val session = HealthProxySession("first")
        val output = JSONObject(NodePingConfig.prepare(before, 32767, session)!!)
        assertEquals(before, source.toString())
        assertEquals(node.toString(), output.getJSONArray("outbounds").getJSONObject(2).toString())
        assertEquals("blackhole", output.getJSONArray("outbounds").getJSONObject(0).getString("protocol"))
        assertFalse(output.has("env"))
        assertEquals(source.getJSONObject("dns").toString(), output.getJSONObject("dns").toString())
        val inbound = output.getJSONArray("inbounds").getJSONObject(0)
        assertEquals(1, output.getJSONArray("inbounds").length())
        assertEquals(32767, inbound.getInt("port"))
        assertEquals("http", inbound.getString("protocol"))
        assertEquals(session.password, inbound.getJSONObject("settings").getJSONArray("accounts").getJSONObject(0).getString("pass"))
        assertEquals("node", output.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"))
    }

    @Test fun `92 outbounds select the requested virtual node without flattening its credentials`() {
        val source = config(JSONObject().put("tag", "node").put("protocol", "vless"))
        val outbounds = JSONArray()
        repeat(92) { index -> outbounds.put(JSONObject().put("tag", "node-$index").put("protocol", "vless")
            .put("settings", JSONObject().put("address", "same.example").put("port", 443).put("id", "user-$index"))) }
        source.put("outbounds", outbounds)
        source.getJSONObject("routing").put("rules", LocalProxyConfig.prependRoute(JSONArray(), "node-73", null))
        val result = JSONObject(NodePingConfig.prepare(source.toString(), 32001, HealthProxySession("row-73"))!!)
        assertEquals("node-73", result.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"))
        assertEquals(outbounds.getJSONObject(73).toString(), result.getJSONArray("outbounds").getJSONObject(74).toString())
    }

    @Test fun `balancer preserves selected strategy with fail-closed fallback`() {
        val source = config(JSONObject().put("tag", "node").put("protocol", "vless"))
        val balancer = JSONObject().put("tag", "auto").put("selector", JSONArray().put("node"))
            .put("strategy", JSONObject().put("type", "leastPing"))
        source.getJSONObject("routing").put("balancers", JSONArray().put(balancer))
            .put("rules", LocalProxyConfig.prependRoute(JSONArray(), null, "auto"))
        source.put("observatory", JSONObject().put("subjectSelector", JSONArray().put("node")).put("probeUrl", "https://probe.example"))
        val result = JSONObject(NodePingConfig.prepare(source.toString(), 32002, HealthProxySession("auto"))!!)
        val chosen = result.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0)
        assertEquals("leastPing", chosen.getJSONObject("strategy").getString("type"))
        assertEquals("nimbo-probe-deny", chosen.getString("fallbackTag"))
        assertEquals(source.getJSONObject("observatory").toString(), result.getJSONObject("observatory").toString())
        assertTrue(LocalProxyConfig.authenticateVerifiedRoute(result, HealthProxySession("auto"),
            selectedBalancerTag = "auto", proxyPort = 32002))
        for (protocol in listOf("freedom", "dns")) {
            val unsafe = JSONObject(result.toString())
            unsafe.getJSONArray("outbounds").getJSONObject(0).put("protocol", protocol)
            assertFalse(LocalProxyConfig.authenticateVerifiedRoute(unsafe, HealthProxySession("auto"),
                selectedBalancerTag = "auto", proxyPort = 32002))
        }
        val duplicate = JSONObject(result.toString())
        duplicate.getJSONArray("outbounds").put(JSONObject().put("tag", "nimbo-probe-deny").put("protocol", "freedom"))
        assertFalse(LocalProxyConfig.authenticateVerifiedRoute(duplicate, HealthProxySession("auto"),
            selectedBalancerTag = "auto", proxyPort = 32002))
    }

    @Test fun `direct missing conditional and unsafe nested routes cannot produce a Nimbo config`() {
        val source = config(JSONObject().put("tag", "node").put("protocol", "vless"))
        for (tag in listOf("direct", "missing")) {
            val copy = JSONObject(source.toString())
            copy.getJSONObject("routing").put("rules", LocalProxyConfig.prependRoute(JSONArray(), tag, null))
            assertNull(NodePingConfig.prepare(copy.toString(), 32003, HealthProxySession("row")))
        }
        source.getJSONArray("outbounds").getJSONObject(1).put("streamSettings", JSONObject()
            .put("xhttpSettings", JSONObject().put("extra", JSONObject().put("downloadSettings", JSONObject().put("dialerProxy", "direct")))))
        assertNull(NodePingConfig.prepare(source.toString(), 32003, HealthProxySession("row")))
        assertNull(NodePingConfig.prepare("{}", 32003, HealthProxySession("row")))
    }

    @Test fun `native AWG returns unavailable before generating a substituted route`() {
        assertNull(XrayManager.diagnosticConfig(Server("awg", "edge.example", 443, "secret", "awg"), null, emptyList()))
    }

    @Test fun `Nimbo is first and only absent saved preference changes default`() {
        assertEquals(PingProtocol.NIMBO, PingProtocol.settingsOrder.first())
        assertEquals(PingProtocol.TCP, PingProtocol.settingsOrder[1])
        assertEquals(PingProtocol.NIMBO, PingProtocol.fromSavedId(null))
        for (id in 0..5) assertEquals(id, PingProtocol.fromSavedId(id).id)
        assertEquals(PingProtocol.TCP, PingProtocol.fromSavedId(999))
    }
}
