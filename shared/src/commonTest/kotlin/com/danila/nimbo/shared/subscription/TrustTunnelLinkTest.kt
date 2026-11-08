package com.danila.nimbo.shared.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustTunnelLinkTest {
    // hostname=tt.tld, addresses=tt.tld:443, username=u, password=p
    private val minimal = "tt://?AQZ0dC50bGQCCnR0LnRsZDo0NDMFAXUGAXA"

    // v2: two addresses, custom SNI, http3, anti-DPI, client_random, name, DNS, unknown tag 0x3E.
    private val full = "tt://?AAECAQ92cG4uZXhhbXBsZS5jb20CEDIwMy4wLjExMy4xOjg0NDMCEVsyMDAxOmRiODo6MV06NDQzAw9jZG4uZXhhbXBsZS5vcmcFAXUGAXAJAQIKAQELCWEwYjAvZjBmMAwQ0JPQtdGA0LzQsNC90LjRjw0wDXRsczovLzEuMS4xLjEhaHR0cHM6Ly9kbnMuZXhhbXBsZS5jb20vZG5zLXF1ZXJ5PgZmdXR1cmU"

    private val subscriptionOnly = "tt://?AAECDi5odHRwczovL3VzZXI6cGFzc0B2cG4uZXhhbXBsZS5jb20vc3Vic2NyaXB0aW9u"

    @Test
    fun decodesMinimalLink() {
        val endpoint = TrustTunnelLink.decode(minimal)
        assertEquals("tt.tld", endpoint.hostname)
        assertEquals("tt.tld", endpoint.host)
        assertEquals(443, endpoint.port)
        assertEquals("u", endpoint.username)
        assertEquals("p", endpoint.password)
        assertFalse(endpoint.http3)
        assertEquals("TrustTunnel tt.tld", TrustTunnelLink.displayName(endpoint))
        assertTrue(endpoint.unsupportedOnMobile.isEmpty())
    }

    @Test
    fun decodesVersionTwoFields() {
        val endpoint = TrustTunnelLink.decode(full)
        assertEquals(listOf("203.0.113.1:8443", "[2001:db8::1]:443"), endpoint.addresses)
        assertEquals("203.0.113.1", endpoint.host)
        assertEquals(8443, endpoint.port)
        assertEquals("cdn.example.org", endpoint.tlsServerName)
        assertTrue(endpoint.http3)
        assertEquals("Германия", endpoint.name)
        assertEquals(listOf("tls://1.1.1.1", "https://dns.example.com/dns-query"), endpoint.dnsUpstreams)
        assertEquals(listOf("anti-DPI", "client_random"), endpoint.unsupportedOnMobile)
    }

    @Test
    fun fragmentWinsAndPanelDescriptionIsDropped() {
        val endpoint = TrustTunnelLink.decode("$full#NL%20Amsterdam?serverDescription=ZGVzYw")
        assertEquals("NL Amsterdam", endpoint.name)
    }

    @Test
    fun subscriptionOnlyLinkIsNotAServer() {
        assertEquals("https://user:pass@vpn.example.com/subscription", TrustTunnelLink.subscriptionUrl(subscriptionOnly))
        assertFailsWith<IllegalArgumentException> { TrustTunnelLink.decode(subscriptionOnly) }
        assertNull(TrustTunnelLink.subscriptionUrl(minimal))
    }

    @Test
    fun rejectsNewerVersionAndGarbage() {
        assertNull(TrustTunnelLink.decodeOrNull("tt://?AAEDAQFoAgVoOjQ0MwUBdQYBcA"))
        assertNull(TrustTunnelLink.decodeOrNull("tt://?"))
        assertNull(TrustTunnelLink.decodeOrNull("tt://?AQ"))
        assertNull(TrustTunnelLink.decodeOrNull("vless://x@y:1"))
    }

    @Test
    fun encodeRoundTripsIncludingCertificate() {
        val der = byteArrayOf(0x30, 0x03, 1, 2, 3)
        val endpoint = TrustTunnelLink.decode(full).copy(certificatePem = TrustTunnelLink.derChainToPem(der))
        val again = TrustTunnelLink.decode(TrustTunnelLink.encode(endpoint))
        assertEquals(endpoint, again)
    }

    @Test
    fun bridgeRequestCarriesResolvedServerAndPin() {
        val endpoint = TrustTunnelLink.decode(full)
        val request = TrustTunnelLink.bridgeRequest(endpoint, "203.0.113.1:8443", "lu", "lp")
        assertTrue(request.contains("\"server\":\"203.0.113.1:8443\""))
        assertTrue(request.contains("\"sni\":\"cdn.example.org\""))
        assertTrue(request.contains("\"verifyName\":\"vpn.example.com\""))
        assertTrue(request.contains("\"http3\":true"))
        assertTrue(TrustTunnelLink.bridgeErrorMessage("AUTH_REJECTED", endpoint).contains("anti-DPI"))
    }

    @Test
    fun endpointSubscriptionJsonBecomesALink() {
        val body = """{"version":2,"hostname":"vpn.example.com","address":"203.0.113.1:443",
            "username":"alice","password":"secret","has_ipv6":false,"upstream_protocol":"http3",
            "anti_dpi":false,"skip_verification":false,"name":"Acme"}"""
        val link = assertNotNull(TrustTunnelLink.fromSubscriptionJson(body))
        val endpoint = TrustTunnelLink.decode(link)
        assertEquals("Acme", endpoint.name)
        assertTrue(endpoint.http3)
        assertFalse(endpoint.hasIpv6)

        val parsed = SubscriptionPayloadParser.parse(body, "https://vpn.example.com/subscription")
        assertEquals(1, parsed.servers.size)
        assertEquals("trusttunnel", parsed.servers.single().protocol)
    }

    @Test
    fun subscriptionParserKeepsTrustTunnelNodes() {
        val plain = SubscriptionPayloadParser.parse("vless://a@b.tld:443#x\n$minimal#Node\n")
        val node = parsed(plain).single { it.protocol == "trusttunnel" }
        assertEquals("Node", node.name)
        assertEquals("tt.tld", node.host)
        assertEquals("http2", node.transport)

        val wrapped = SubscriptionPayloadParser.encodeBase64ForTest("$full\n", urlSafe = false)
        val decoded = SubscriptionPayloadParser.parse(wrapped)
        assertEquals("http3", parsed(decoded).single().transport)

        assertEquals(listOf(minimal), SubscriptionShareLinks.extract("see $minimal, and watt://x"))
    }

    private fun parsed(result: NormalizedSubscription) = result.servers
}
