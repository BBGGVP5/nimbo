package com.danila.nimbo.vpn

import com.danila.nimbo.model.RoutingProfile
import com.danila.nimbo.model.BuiltinRoutingProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `domain and GeoIP direct matches are independent Xray rules`() {
        val rules = RoutingProfileRules.build(
            RoutingProfile(
                bypassLocalIp = "false",
                directSites = listOf("domain:ru"),
                directIp = listOf("geoip:ru")
            ),
            includeFallback = false
        )

        assertEquals(2, rules.length())
        assertEquals("direct", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("domain:ru", rules.getJSONObject(0).getJSONArray("domain").getString(0))
        assertFalse(rules.getJSONObject(0).has("ip"))
        assertEquals("geoip:ru", rules.getJSONObject(1).getJSONArray("ip").getString(0))
        assertFalse(rules.getJSONObject(1).has("domain"))
    }

    @Test
    fun `GeoIP selector mistakenly stored as a site is emitted as an IP rule`() {
        val rules = RoutingProfileRules.build(
            RoutingProfile(
                bypassLocalIp = "false",
                directSites = listOf("domain:ru", "geoip:ru")
            ),
            includeFallback = false
        )

        assertEquals("domain:ru", rules.getJSONObject(0).getJSONArray("domain").getString(0))
        assertEquals("geoip:ru", rules.getJSONObject(1).getJSONArray("ip").getString(0))
        assertFalse(rules.getJSONObject(1).has("domain"))
    }

    @Test
    fun `every built in profile preserves its selectors and fallback destination`() {
        BuiltinRoutingProfiles.defaults().forEach { profile ->
            val rules = RoutingProfileRules.build(profile, includeFallback = true)
            val directDomainSelectors = (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .filter { it.optString("outboundTag") == "direct" && it.has("domain") }
                .flatMap { rule ->
                    val domains = rule.getJSONArray("domain")
                    (0 until domains.length()).map(domains::getString)
                }
            val directIpSelectors = (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .filter { it.optString("outboundTag") == "direct" && it.has("ip") }
                .flatMap { rule ->
                    val ips = rule.getJSONArray("ip")
                    (0 until ips.length()).map(ips::getString)
                }

            assertTrue(directDomainSelectors.containsAll(profile.directSites.orEmpty()))
            assertTrue(directIpSelectors.containsAll(profile.directIp.orEmpty()))
            assertEquals(
                if (profile.isGlobalProxyEnabled()) "proxy" else "direct",
                rules.getJSONObject(rules.length() - 1).getString("outboundTag")
            )
        }
    }
}
