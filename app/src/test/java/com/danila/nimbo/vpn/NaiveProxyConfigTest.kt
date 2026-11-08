package com.danila.nimbo.vpn

import com.danila.nimbo.network.LinkParser
import org.junit.Assert.*
import org.junit.Test

class NaiveProxyConfigTest {
    @Test fun peerControlsTlsWhileResolverKeepsOriginalEndpoint() {
        val server = LinkParser.parse("naive+https://u%2B:p%40ss@192.0.2.10:10620?peer=tls.example.invalid")
        val config = NaiveProxyConfig.build(server, 1081)
        assertEquals("https://u%2B:p%40ss@tls.example.invalid:10620", config.getString("proxy"))
        assertEquals("MAP tls.example.invalid 192.0.2.10", config.getString("host-resolver-rules"))
        assertEquals("socks://127.0.0.1:1081", config.getString("listen"))
        assertFalse(config.has("insecure"))
    }
    @Test fun ordinaryQuicDoesNotNeedResolverOverride() {
        val config = NaiveProxyConfig.build(LinkParser.parse("naive+quic://u:p@example.invalid"), 1081)
        assertEquals("quic://u:p@example.invalid:443", config.getString("proxy"))
        assertFalse(config.has("host-resolver-rules"))
    }
    @Test fun peerCannotInjectResolverRules() {
        val server = LinkParser.parse("naive+https://u:p@192.0.2.10?peer=x%2CEXCLUDE%20*")
        assertThrows(IllegalArgumentException::class.java) { NaiveProxyConfig.build(server, 1081) }
    }
    @Test fun ipv6EndpointKeepsTlsNameAndUsesBracketedResolverTarget() {
        val server = LinkParser.parse("naive+https://u:p@[2001:db8::1]:443?peer=tls.example.invalid")
        val config = NaiveProxyConfig.build(server, 1081)
        assertTrue(config.getString("host-resolver-rules").startsWith("MAP tls.example.invalid [2001:db8:"))
    }
}
