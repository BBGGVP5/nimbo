package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Узлы «обхода блокировок» через CDN описываются не одним адресом: сервер ждёт
 * постквантовое шифрование VLESS и свою раскладку XHTTP — режим, имена ключей
 * сессии, набивку. Раньше приложение подставляло `encryption: none` и
 * `mode: auto`, а блок `extra` терял вовсе: TLS-соединение поднималось, пинг
 * шёл, а данные не ходили. Проверяем, что всё это доезжает до конфигурации.
 */
class XrayXhttpOutboundTest {

    private val extra = """
        {"xmux":{"cMaxReuseTimes":0,"maxConcurrency":"2-4"},"seqKey":"x_seq",
         "noSSEHeader":true,"xPaddingKey":"x_padding","sessionIDKey":"SESSIONID",
         "uplinkHTTPMethod":"GET","uplinkDataPlacement":"body"}
    """.trimIndent()

    private fun server(encryption: String?, mode: String?, extraJson: String?) = Server(
        name = "ОБХОД БЛОКИРОВОК",
        host = "mycdn1.example.org",
        port = 443,
        uuid = "00000000-0000-4000-8000-000000000000",
        protocol = "vless",
        security = "tls",
        network = "xhttp",
        path = "/assets/api/v1",
        hostHeader = "mycdn1.example.org",
        sni = "mycdn1.example.org",
        alpn = "h2",
        tls = true,
        encryption = encryption,
        xhttpMode = mode,
        xhttpExtra = extraJson
    )

    @Test
    fun vlessEncryptionAndXhttpExtraReachTheOutbound() {
        val node = server("mlkem768x25519plus.random.0rtt.100-111-1111.abc", "packet-up", extra)

        val user = XrayManager.buildOutboundSettings(node, "vless")
            .getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals("mlkem768x25519plus.random.0rtt.100-111-1111.abc", user.getString("encryption"))

        val xhttp = XrayManager.buildStreamSettings(node)!!.getJSONObject("xhttpSettings")
        assertEquals("packet-up", xhttp.getString("mode"))
        assertEquals("/assets/api/v1", xhttp.getString("path"))
        assertEquals("mycdn1.example.org", xhttp.getString("host"))

        val carried = xhttp.getJSONObject("extra")
        assertEquals("x_seq", carried.getString("seqKey"))
        assertEquals("GET", carried.getString("uplinkHTTPMethod"))
        assertEquals("2-4", carried.getJSONObject("xmux").getString("maxConcurrency"))
    }

    @Test
    fun plainNodeKeepsPreviousDefaults() {
        val node = server(null, null, null)

        val user = XrayManager.buildOutboundSettings(node, "vless")
            .getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals("none", user.getString("encryption"))

        val xhttp = XrayManager.buildStreamSettings(node)!!.getJSONObject("xhttpSettings")
        assertEquals("auto", xhttp.getString("mode"))
        assertNull("узлу без extra его дописывать нечего", xhttp.optJSONObject("extra"))
    }
}
