package com.danila.nimbo.vpn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProxyConfigTest {

    @Test
    fun `inbound is loopback only and unique`() {
        val inbounds = JSONArray()
            .put(JSONObject().put("tag", "tun-in").put("protocol", "tun"))
            .put(
                JSONObject()
                    .put("tag", LocalProxyConfig.INBOUND_TAG)
                    .put("listen", "0.0.0.0")
                    .put("port", LocalProxyConfig.PORT)
                    .put("protocol", "http")
            )

        LocalProxyConfig.ensureInbound(inbounds)

        val probeInbounds = (0 until inbounds.length())
            .mapNotNull(inbounds::optJSONObject)
            .filter { it.optString("tag") == LocalProxyConfig.INBOUND_TAG }
        assertEquals(1, probeInbounds.size)
        assertEquals(LocalProxyConfig.HOST, probeInbounds.single().getString("listen"))
        assertEquals(LocalProxyConfig.PORT, probeInbounds.single().getInt("port"))
        assertEquals("http", probeInbounds.single().getString("protocol"))
        assertFalse(probeInbounds.single().optJSONObject("settings")?.optBoolean("allowTransparent", true) ?: true)
    }

    @Test
    fun `probe rule wins over imported catch all`() {
        val importedRule = JSONObject()
            .put("type", "field")
            .put("outboundTag", "direct")
        val rules = JSONArray().put(importedRule)

        val result = LocalProxyConfig.prependRoute(
            rules = rules,
            outboundTag = "proxy",
            balancerTag = null
        )

        val probeRule = result.getJSONObject(0)
        assertEquals(LocalProxyConfig.INBOUND_TAG, probeRule.getJSONArray("inboundTag").getString(0))
        assertEquals("proxy", probeRule.getString("outboundTag"))
        assertEquals(importedRule.toString(), result.getJSONObject(1).toString())
    }

    @Test
    fun `balancer route is used when no explicit outbound is selected`() {
        val result = LocalProxyConfig.prependRoute(
            rules = JSONArray(),
            outboundTag = null,
            balancerTag = "main-balancer"
        )

        assertEquals("main-balancer", result.getJSONObject(0).getString("balancerTag"))
        assertFalse(result.getJSONObject(0).has("outboundTag"))
    }

    @Test
    fun `reapplying route keeps one probe rule at the first position`() {
        val initiallyRouted = LocalProxyConfig.prependRoute(
            rules = JSONArray().put(JSONObject().put("type", "field").put("outboundTag", "direct")),
            outboundTag = "proxy",
            balancerTag = null
        )
        val profileRule = JSONObject().put("type", "field").put("outboundTag", "block")
        val profileApplied = JSONArray().put(profileRule)
        for (index in 0 until initiallyRouted.length()) {
            profileApplied.put(initiallyRouted.getJSONObject(index))
        }

        val result = LocalProxyConfig.prependRoute(profileApplied, "proxy", null)

        val probeRuleCount = (0 until result.length())
            .map(result::getJSONObject)
            .count { rule ->
                val inboundTags = rule.optJSONArray("inboundTag") ?: JSONArray()
                (0 until inboundTags.length()).any { inboundTags.optString(it) == LocalProxyConfig.INBOUND_TAG }
            }
        assertEquals(1, probeRuleCount)
        assertEquals(LocalProxyConfig.INBOUND_TAG, result.getJSONObject(0).getJSONArray("inboundTag").getString(0))
    }

    @Test
    fun `first proxy outbound excludes local utility outbounds`() {
        val outbounds = JSONArray()
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            .put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
            .put(JSONObject().put("tag", "node-a").put("protocol", "vless"))

        assertEquals("node-a", LocalProxyConfig.firstProxyOutboundTag(outbounds))
        assertTrue(LocalProxyConfig.firstProxyOutboundTag(JSONArray()) == null)
    }
}
