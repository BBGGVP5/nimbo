package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Узел «Обход 34» из реальной подписки TITAN VPS: VLESS с постквантовым
 * encryption поверх XHTTP в режиме stream-up, без TLS, со своим блоком extra
 * (xmux + scMaxEachPostBytes) и sockopt.
 *
 * Такой конфиг работает в Happ, который запускает JSON как есть. Nimbo же
 * разбирает конфиг в share-ссылку и пересобирает его заново, поэтому всё, что
 * не доехало до ссылки, на сервере молча не сойдётся.
 */
class XhttpStreamUpRoundTripTest {

    private val encryption =
        "mlkem768x25519plus.native.0rtt.KavI4ykTvXYxQVzPabcdefghijklmnopqrstuvwxyz0123456789"

    private val config = """
        [
          {
            "remarks": "🇷🇺Обход 34 (test android)",
            "outbounds": [
              {
                "protocol": "vless",
                "tag": "proxy",
                "settings": {
                  "vnext": [
                    {
                      "address": "185.40.152.135",
                      "port": 55443,
                      "users": [
                        {
                          "encryption": "$encryption",
                          "flow": "",
                          "id": "343bf6ed-e3b7-4000-8000-000000000000"
                        }
                      ]
                    }
                  ]
                },
                "streamSettings": {
                  "network": "xhttp",
                  "security": "none",
                  "sockopt": { "tcpFastOpen": true, "tcpUserTimeout": 0, "tcpWindowClamp": 0 },
                  "xhttpSettings": {
                    "extra": {
                      "scMaxEachPostBytes": "100000-200000",
                      "xmux": { "cMaxReuseTimes": "32-64", "maxConcurrency": "2-4" }
                    },
                    "host": "",
                    "mode": "stream-up",
                    "path": "/upd"
                  }
                }
              },
              { "protocol": "freedom", "tag": "direct", "settings": { "domainStrategy": "UseIPv4" } },
              { "protocol": "blackhole", "tag": "block", "settings": {} }
            ]
          }
        ]
    """.trimIndent()

    private val link: String
        get() = SubscriptionManager.parseServerLinksFromClientJsonConfig(config).single()

    @Test
    fun linkCarriesEndpointAndName() {
        val link = link
        assertTrue("адрес и порт: $link", link.startsWith("vless://343bf6ed-e3b7-4000-8000-000000000000@185.40.152.135:55443"))
        assertTrue("имя из remarks: $link", link.endsWith("#%D0%9F%D1%80%D0%BE%D0%B2%D0%B5%D1%80%D0%BA%D0%B0") || link.contains("%D0%9E%D0%B1%D1%85%D0%BE%D0%B4"))
    }

    @Test
    fun postQuantumEncryptionSurvives() {
        val server = LinkParser.parse(link)
        requireNotNull(server)
        assertEquals(encryption, server.encryption)
    }

    @Test
    fun xhttpStreamUpTransportSurvives() {
        val server = LinkParser.parse(link)
        requireNotNull(server)
        assertEquals("xhttp", server.network)
        assertEquals("stream-up", server.xhttpMode)
        assertEquals("/upd", server.path)
    }

    @Test
    fun xhttpExtraSurvivesAsJson() {
        val server = LinkParser.parse(link)
        requireNotNull(server)
        val extra = server.xhttpExtra.orEmpty()
        assertTrue("xmux должен доехать: $extra", extra.contains("\"maxConcurrency\":\"2-4\""))
        assertTrue("cMaxReuseTimes должен доехать: $extra", extra.contains("\"cMaxReuseTimes\":\"32-64\""))
        assertTrue("scMaxEachPostBytes должен доехать: $extra", extra.contains("\"scMaxEachPostBytes\":\"100000-200000\""))
    }

    @Test
    fun securityStaysNoneAndEmptyHostIsNotEmitted() {
        val link = link
        assertTrue("security=none должен быть в ссылке: $link", link.contains("security=none"))
        // host в конфиге пустой — пустой Host-заголовок ломает виртуальный хост на сервере.
        assertFalse("пустой host не должен попадать в ссылку: $link", link.contains("host=&") || link.endsWith("host="))

        val server = LinkParser.parse(link)
        requireNotNull(server)
        assertEquals("none", server.security)
        assertTrue("hostHeader должен остаться пустым: ${server.hostHeader}", server.hostHeader.isNullOrBlank())
    }
}
