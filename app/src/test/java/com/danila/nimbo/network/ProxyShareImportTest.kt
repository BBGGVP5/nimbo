package com.danila.nimbo.network

import org.junit.Assert.*
import org.junit.Test

class ProxyShareImportTest {
    @Test fun naiveKeepsCredentialsAndReadableFragment() {
        val server = LinkParser.parse("naive+https://user+mail:p%40ss%3Aword%2B@example.invalid#Naive EU 🚀")
        assertEquals("naive", server.protocol)
        assertEquals("Naive EU 🚀", server.name)
        assertEquals("user+mail", server.naiveUsername)
        assertEquals("p@ss:word+", server.naivePassword)
        assertEquals(443, server.port)
        assertEquals("https", server.naiveTransport)
    }
    @Test fun naivePeerIsPreservedSeparatelyFromDialIp() {
        val server = LinkParser.parse("naive+https://user:pass@192.0.2.10:10620?padding=true&peer=tls.example.invalid#Naive")
        assertEquals("192.0.2.10", server.host)
        assertEquals("tls.example.invalid", server.sni)
    }
    @Test fun naiveIpv6AndQuicUseSeparateTransport() {
        val server = LinkParser.parse("naive+quic://u:p@[2001:db8::1]:8443#QUIC")
        assertEquals("2001:db8::1", server.host)
        assertEquals("quic", server.naiveTransport)
        assertEquals(8443, server.port)
    }
    @Test fun malformedNaiveDoesNotBecomeVless() {
        for (link in listOf("naive+https://u@example.invalid", "naive+https://u:p@example.invalid:99999")) {
            assertThrows(IllegalArgumentException::class.java) { LinkParser.parse(link) }
        }
    }
    @Test fun tuicRetainsProtocolInsteadOfDefaultingToVless() {
        val server = LinkParser.parse("tuic://id:secret@example.invalid:443#Estonia TUIC")
        assertEquals("tuic", server.protocol)
        assertEquals("quic", server.network)
        assertEquals("Estonia TUIC", server.name)
    }
    @Test fun mieruNeverBecomesVless() {
        val server = LinkParser.parse("mieru://u:p@example.invalid:8443?protocol=TCP")
        assertEquals("mieru", server.protocol)
        assertEquals(com.danila.nimbo.vpn.VpnCorePolicy.Rejection.SHARE_REQUIRES_MIHOMO_PROFILE,
            com.danila.nimbo.vpn.VpnCorePolicy.rejection("auto", server))
    }
    @Test fun unsupportedSchemeNeverBecomesVless() {
        assertThrows(IllegalArgumentException::class.java) { LinkParser.parse("unknown://u@example.invalid") }
    }
}
