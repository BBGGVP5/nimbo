package com.danila.nimbo.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionManagerXrayBase64Test {
    @Test
    fun `XRAY_BASE64 payload decodes into a usable Xray client config`() {
        val config = """{"outbounds":[{"tag":"proxy","protocol":"vless"}]}"""
        val payload = Base64.getEncoder().encodeToString(config.toByteArray())

        val decoded = SubscriptionManager.decodeSubscriptionBase64(payload)

        assertEquals(config, decoded)
        assertTrue(RemnawaveApiClient.isUsableXrayClientConfig(org.json.JSONObject(decoded!!)))
    }

    @Test
    fun `client Xray config extracts VLESS XHTTP outbound`() {
        val config = """
            {
              "outbounds": [{
                "tag": "Example-XHTTP",
                "protocol": "vless",
                "settings": { "vnext": [{
                  "address": "edge.example",
                  "port": 443,
                  "users": [{ "id": "11111111-2222-4333-8444-555555555555", "encryption": "none" }]
                }] },
                "streamSettings": {
                  "network": "xhttp",
                  "security": "tls",
                  "tlsSettings": { "serverName": "front.example", "fingerprint": "edge" },
                  "xhttpSettings": { "mode": "auto", "path": "/direct/" }
                }
              }, { "tag": "direct", "protocol": "freedom" }]
            }
        """.trimIndent()

        val link = SubscriptionManager.parseServerLinksFromClientJsonConfig(config).single()
        val server = LinkParser.parse(link)

        assertEquals("Example-XHTTP", server.name)
        assertEquals("edge.example", server.host)
        assertEquals(443, server.port)
        assertEquals("vless", server.protocol)
        assertEquals("xhttp", server.network)
        assertEquals("/direct/", server.path)
        assertEquals("front.example", server.sni)
        assertEquals("edge", server.fingerprint)
        assertEquals("11111111-2222-4333-8444-555555555555", server.uuid)
    }
}
