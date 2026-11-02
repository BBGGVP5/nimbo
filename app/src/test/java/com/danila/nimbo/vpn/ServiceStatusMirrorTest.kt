package com.danila.nimbo.vpn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStatusMirrorTest {
    private fun config() = JSONObject()
        .put("inbounds", JSONArray()
            .put(JSONObject().put("tag", "tun-in").put("protocol", "tun"))
            .put(JSONObject().put("tag", "stale").put("listen", "127.0.0.1").put("port", LocalProxyConfig.MIRROR_PORT)))
        .put("routing", JSONObject().put("rules", JSONArray()
            .put(JSONObject().put("inboundTag", JSONArray().put(LocalProxyConfig.INBOUND_TAG)).put("balancerTag", "narrow"))
            .put(JSONObject().put("inboundTag", "tun-in").put("domain", JSONArray().put("geosite:ru")).put("outboundTag", "direct"))
            .put(JSONObject().put("inboundTag", JSONArray().put("tun-in")).put("balancerTag", "main"))
            .put(JSONObject().put("network", "tcp,udp").put("outboundTag", "block"))))

    @Test
    fun `mirror inbound follows tun-in rules and never the node-pinned health route`() {
        val json = config()
        val session = HealthProxySession("key")
        assertTrue(LocalProxyConfig.addMirrorInbound(json, session))

        val inbounds = json.getJSONArray("inbounds")
        val mirror = (0 until inbounds.length()).map(inbounds::getJSONObject)
            .single { it.optString("tag") == LocalProxyConfig.MIRROR_INBOUND_TAG }
        assertEquals(LocalProxyConfig.HOST, mirror.getString("listen"))
        assertEquals(session.password, mirror.getJSONObject("settings").getJSONArray("accounts").getJSONObject(0).getString("pass"))
        // Конфликтующий вход на том же loopback-порту убран.
        assertFalse((0 until inbounds.length()).any { inbounds.getJSONObject(it).optString("tag") == "stale" })

        val rules = json.getJSONObject("routing").getJSONArray("rules")
        fun tags(i: Int) = rules.getJSONObject(i).opt("inboundTag").toString()
        assertFalse(tags(0).contains(LocalProxyConfig.MIRROR_INBOUND_TAG))
        assertTrue(tags(1).contains(LocalProxyConfig.MIRROR_INBOUND_TAG))
        assertTrue(tags(2).contains(LocalProxyConfig.MIRROR_INBOUND_TAG))
        assertFalse(rules.getJSONObject(3).has("inboundTag"))
    }

    @Test
    fun `no tun inbound means no mirror`() {
        val json = JSONObject().put("inbounds", JSONArray()).put("routing", JSONObject())
        assertFalse(LocalProxyConfig.addMirrorInbound(json, HealthProxySession("key")))
    }
}
