package com.danila.nimbo.shared.subscription

import kotlin.test.*

class ProxyImportRegressionTest {
    @Test fun wrappedNaiveVariantsSurviveExtraction() {
        val links = listOf("naive://u:p@example.invalid#A", "naive+https://u:p@example.invalid:8443#B", "naive+quic://u:p@example.invalid#C", "tuic://id:p@example.invalid#D")
        val body = links.joinToString(",", "[", "]") { "\"$it\"" }
        assertEquals(links, SubscriptionShareLinks.extract(body))
        val result = SubscriptionPayloadParser.parse(body)
        assertEquals(listOf("naive", "naive", "naive", "tuic"), result.servers.map { it.protocol })
        assertEquals(listOf(443, 8443, 443, 443), result.servers.map { it.port })
        assertEquals(listOf("https", "https", "quic", "quic"), result.servers.map { it.transport })
        assertTrue(result.servers.all { it.security == "tls" })
    }
    @Test fun ipv6WithoutExplicitPortIsNotTruncated() {
        val link = "naive+https://u:p@[2001:db8::1]"
        assertEquals(listOf(link), SubscriptionShareLinks.extract(link))
        val server = SubscriptionPayloadParser.parse(link).servers.single()
        assertEquals("2001:db8::1", server.host)
        assertEquals(443, server.port)
    }
    @Test fun mieruIsVisibleWithItsOwnProtocol() {
        val result = SubscriptionPayloadParser.parse("mieru://u:p@example.invalid:8443?protocol=TCP#Mieru")
        assertEquals("mieru", result.servers.single().protocol)
    }
    @Test fun base64SubscriptionRetainsNaiveAndTuic() {
        val links = "naive+quic://u:p@example.invalid#Naive\ntuic://id:p@example.invalid#TUIC"
        val result = SubscriptionPayloadParser.parse(SubscriptionPayloadParser.encodeBase64ForTest(links, true))
        assertEquals(listOf("naive", "tuic"), result.servers.map { it.protocol })
    }
}
