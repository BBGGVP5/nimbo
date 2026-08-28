package com.danila.nimbo.shared.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionPayloadParserTest {
    @Test
    fun parsesEveryPlainShareLinkWithoutInventingRows() {
        val payload = """
            vless://11111111-1111-1111-1111-111111111111@edge.example:443?type=xhttp&security=reality&sni=cdn.example#Primary%20XHTTP
            trojan://secret@backup.example:443?security=tls&type=ws#Backup
            hysteria2://pass@hy.example:8443?sni=hy.example#Fast%20HY2
            naive+https://user:pass@naive.example:443#Naive
        """.trimIndent()

        val result = SubscriptionPayloadParser.parse(payload, "https://sub.example/user")

        assertEquals(4, result.servers.size)
        assertEquals(listOf("vless", "trojan", "hysteria2", "naive"), result.servers.map { it.protocol })
        assertEquals("Primary XHTTP", result.servers.first().name)
        assertEquals("xhttp", result.servers.first().transport)
        assertEquals("reality", result.servers.first().security)
        assertFalse(result.servers.any { it.name.contains("Резервный сервер") })
    }

    @Test
    fun decodesStandardBase64WithoutPadding() {
        val encoded = "dmxlc3M6Ly8xMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTFAZXhhbXBsZS5jb206NDQzI09uZQ"

        val result = SubscriptionPayloadParser.parse(encoded)

        assertEquals(1, result.servers.size)
        assertEquals("One", result.servers.single().name)
        assertEquals(SubscriptionPayloadFormat.BASE64_LINKS, result.format)
    }

    @Test
    fun decodesUrlSafeBase64AndDeduplicatesLinks() {
        val link = "vless://11111111-1111-1111-1111-111111111111@example.com:443#One"
        val encoded = SubscriptionPayloadParser.encodeBase64ForTest("$link\n$link", urlSafe = true).trimEnd('=')

        val result = SubscriptionPayloadParser.parse(encoded)

        assertEquals(1, result.servers.size)
        assertEquals("One", result.servers.single().name)
    }

    @Test
    fun keepsEveryServerFromAProviderStyleBase64Subscription() {
        val links = (1..4).joinToString("\n") { index ->
            "vless://11111111-1111-1111-1111-11111111111$index@edge$index.example:443?type=xhttp&security=reality#Node-$index"
        }
        val encoded = SubscriptionPayloadParser.encodeBase64ForTest(links, urlSafe = false).trimEnd('=')

        val result = SubscriptionPayloadParser.parse(encoded, "https://provider.example/sub/redacted")

        assertEquals(4, result.servers.size)
        assertEquals(listOf("Node-1", "Node-2", "Node-3", "Node-4"), result.servers.map { it.name })
        assertEquals(SubscriptionPayloadFormat.BASE64_LINKS, result.format)
    }

    @Test
    fun parsesLinksNestedInJson() {
        val payload = """
            {
              "name": "Office",
              "servers": [
                {"url": "vless://11111111-1111-1111-1111-111111111111@one.example:443#One"},
                "trojan://secret@two.example:443#Two"
              ]
            }
        """.trimIndent()

        val result = SubscriptionPayloadParser.parse(payload)

        assertEquals("Office", result.title)
        assertEquals(2, result.servers.size)
        assertEquals(SubscriptionPayloadFormat.JSON_LINKS, result.format)
    }

    @Test
    fun keepsNativeXrayJsonAsOneRunnableConfiguration() {
        val payload = """
            {"remarks":"Remote config","outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"edge.example","port":443}]}}]}
        """.trimIndent()

        val result = SubscriptionPayloadParser.parse(payload)

        assertEquals(1, result.servers.size)
        assertTrue(result.servers.single().isNativeXrayJson)
        assertEquals("edge.example", result.servers.single().host)
        assertEquals("vless", result.servers.single().protocol)
        assertEquals(payload, result.servers.single().rawConfiguration)
    }

    @Test
    fun malformedPayloadReturnsDiagnosticAndNoFakeServer() {
        val result = SubscriptionPayloadParser.parse("not a subscription")

        assertTrue(result.servers.isEmpty())
        assertEquals("SUBSCRIPTION_NO_SUPPORTED_NODES", result.diagnosticCode)
    }
}
