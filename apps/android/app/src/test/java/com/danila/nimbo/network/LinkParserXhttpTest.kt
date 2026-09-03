package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ссылка узла «обхода блокировок» через CDN: постквантовое шифрование VLESS и
 * транспорт XHTTP со своей раскладкой. Разбор обязан донести их до модели —
 * без них соединение поднимается и молчит.
 */
class LinkParserXhttpTest {

    private val link = "vless://00000000-0000-4000-8000-000000000000@mycdn1.example.org:443" +
        "?encryption=mlkem768x25519plus.random.0rtt.100-111-1111.75-0-111.abcdef" +
        "&type=xhttp&path=%2Fassets%2Fapi%2Fv1&host=mycdn1.example.org&mode=packet-up" +
        "&extra=%7B%22xmux%22%3A%7B%22maxConcurrency%22%3A%222-4%22%7D%2C%22seqKey%22%3A%22x_seq%22%7D" +
        "&security=tls&sni=mycdn1.example.org&fp=firefox&alpn=h2#%F0%9F%87%B7%F0%9F%87%BA%20ОБХОД"

    @Test
    fun keepsEncryptionAndXhttpSettings() {
        val server = LinkParser.parse(link)

        requireNotNull(server) { "ссылка должна разбираться" }
        assertEquals("xhttp", server.network)
        assertEquals("/assets/api/v1", server.path)
        assertEquals("packet-up", server.xhttpMode)
        assertEquals(
            "mlkem768x25519plus.random.0rtt.100-111-1111.75-0-111.abcdef",
            server.encryption
        )
        assertTrue(
            "блок extra должен доехать как JSON: ${server.xhttpExtra}",
            server.xhttpExtra.orEmpty().contains("\"seqKey\":\"x_seq\"")
        )
    }
}
