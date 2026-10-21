package com.danila.nimbo.network

import com.danila.nimbo.vpn.XrayManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustTunnelAndroidTest {
    // hostname=tt.tld, addresses=tt.tld:443, username=u, password=p
    private val link = "tt://?AQZ0dC50bGQCCnR0LnRsZDo0NDMFAXUGAXA"

    @Test
    fun parsesDeepLinkIntoServer() {
        val server = LinkParser.parse("$link#Netherlands")
        assertEquals("Netherlands", server.name)
        assertEquals("trusttunnel", server.protocol)
        assertEquals("tt.tld", server.host)
        assertEquals(443, server.port)
        assertEquals("http2", server.network)
        assertTrue(server.isTrustTunnel())
        assertTrue(server.usesLocalSocksSidecar())
        assertEquals("$link#Netherlands", server.trustTunnelLink)
    }

    @Test
    fun xrayUsesTheAuthenticatedBridgeAndNoStreamSettings() {
        val server = LinkParser.parse(link).copy(
            trustTunnelSocksPort = 41000,
            trustTunnelSocksUsername = "lu",
            trustTunnelSocksPassword = "lp"
        )
        val settings = XrayManager.buildOutboundSettings(server, "socks")
        val target = settings.getJSONArray("servers").getJSONObject(0)
        assertEquals("127.0.0.1", target.getString("address"))
        assertEquals(41000, target.getInt("port"))
        assertEquals("lu", target.getJSONArray("users").getJSONObject(0).getString("user"))
        assertNull(XrayManager.buildStreamSettings(server))
    }

    @Test
    fun endpointSubscriptionJsonYieldsALink() {
        val body = """{"hostname":"vpn.example.com","address":"203.0.113.1:443","username":"alice",""" +
            """"password":"secret","upstream_protocol":"http2"}"""
        val links = SubscriptionManager.parseServerLinksFromApiJson(body).orEmpty()
        assertEquals(1, links.size)
        assertEquals("vpn.example.com", LinkParser.parse(links.single()).sni)
    }

    @Test
    fun pingSweepPrefersTheSidecarProbe() = runBlocking {
        val results = mutableMapOf<String, Int>()
        NimboPingSweep.run(
            listOf(NimboPingTarget("tt", null) { 42 }, NimboPingTarget("vless", "cfg")),
            measure = { 7 },
            publish = { key, value -> results[key] = value }
        )
        assertEquals(mapOf("tt" to 42, "vless" to 7), results)
    }
}
