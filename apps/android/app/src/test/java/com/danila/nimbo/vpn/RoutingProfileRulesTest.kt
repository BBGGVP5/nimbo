package com.danila.nimbo.vpn

import com.danila.nimbo.model.RoutingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoutingProfileRulesTest {
    @Test
    fun `rules are emitted in block proxy direct order with a global fallback`() {
        val profile = RoutingProfile(
            ruleOrder = "block-proxy-direct",
            globalProxy = "true",
            bypassLocalIp = "false",
            blockSites = listOf("domain:ads.example"),
            proxySites = listOf("domain:blocked.example"),
            directSites = listOf("domain:local.example")
        )

        val rules = RoutingProfileRules.build(profile, includeFallback = true)

        assertEquals("block", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("proxy", rules.getJSONObject(1).getString("outboundTag"))
        assertEquals("direct", rules.getJSONObject(2).getString("outboundTag"))
        assertEquals("proxy", rules.getJSONObject(3).getString("outboundTag"))
    }

    @Test
    fun `template rules omit a catch all fallback`() {
        val rules = RoutingProfileRules.build(
            RoutingProfile(proxyIp = listOf("1.1.1.1"), globalProxy = "false", bypassLocalIp = "false"),
            includeFallback = false
        )

        assertEquals(1, rules.length())
        assertEquals("proxy", rules.getJSONObject(0).getString("outboundTag"))
        assertFalse(rules.getJSONObject(0).has("domain"))
    }

    @Test
    fun `local addresses are direct when the profile bypass setting is enabled`() {
        val rules = RoutingProfileRules.build(RoutingProfile(bypassLocalIp = "true"), includeFallback = false)

        assertEquals(1, rules.length())
        assertEquals("direct", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("geoip:private", rules.getJSONObject(0).getJSONArray("ip").getString(0))
    }
}
