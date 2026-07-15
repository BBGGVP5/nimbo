package com.danila.nimbo.network

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет, что хвост Remnawave/Happ "#<имя>?serverDescription=<base64>" корректно
 * разбирается: имя очищается, а описание декодируется из base64. Запускается на устройстве,
 * т.к. LinkParser использует android.net.Uri и android.util.Base64.
 */
@RunWith(AndroidJUnit4::class)
class LinkParserFragmentDescriptionTest {

    private fun b64(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    @Test
    fun shadowsocks_fragmentTail_splitsNameAndDecodesDescription() {
        val descB64 = b64("Shadowsocks ✈️")
        val userInfo = b64("aes-256-gcm:secretpass")
        val link = "ss://$userInfo@nebulaguard-nll.mooo.com:8388" +
            "#🇫🇮 Финляндия · Shadowsocks ✈️?serverDescription=$descB64"

        val server = LinkParser.parse(link)

        assertEquals("🇫🇮 Финляндия · Shadowsocks ✈️", server.name)
        assertFalse(server.name.contains("serverDescription"))
        assertFalse(server.name.contains("?"))
        assertEquals("Shadowsocks ✈️", server.serverDescription)
    }

    @Test
    fun trojan_fragmentTail_splitsNameAndDecodesDescription() {
        val descB64 = b64("Trojan 🐎")
        val link = "trojan://secretpass@nebulaguard-nll.mooo.com:5443?security=tls&sni=nebulaguard-nll.mooo.com" +
            "#🇫🇮 Финляндия · Trojan 🐎?serverDescription=$descB64"

        val server = LinkParser.parse(link)

        assertEquals("🇫🇮 Финляндия · Trojan 🐎", server.name)
        assertFalse(server.name.contains("?serverDescription"))
        assertEquals("Trojan 🐎", server.serverDescription)
    }

    @Test
    fun vless_fragmentTail_isNoLongerDiscardedToServer() {
        val descB64 = b64("VLESS RU")
        val link = "vless://1f1e2aba-f6ee-481e-a2e6-1851e27f2218@bg.nebulaguard.ru:1443" +
            "?security=reality&pbk=abc&sni=bg.nebulaguard.ru&type=tcp&flow=xtls-rprx-vision" +
            "#🇷🇺 Россия | VLESS?serverDescription=$descB64"

        val server = LinkParser.parse(link)

        assertEquals("🇷🇺 Россия | VLESS", server.name)
        assertFalse(server.name.equals("Server", ignoreCase = true))
    }

    @Test
    fun cleanFragmentWithoutTail_isPreserved() {
        val link = "vless://1f1e2aba-f6ee-481e-a2e6-1851e27f2218@nebulaguard-de.mooo.com:443" +
            "?security=reality&pbk=abc&sni=nebulaguard-de.mooo.com&type=tcp#🇩🇪 Германия | VLESS"

        val server = LinkParser.parse(link)

        assertEquals("🇩🇪 Германия | VLESS", server.name)
    }

    @Test
    fun realRemnawaveBase64Value_decodesToReadableText() {
        // Точное значение из реальной подписки пользователя.
        val link = "ss://YWVzLTI1Ni1nY206cA==@nebulaguard-nll.mooo.com:8388" +
            "#🇫🇮 Финляндия · Shadowsocks ✈️?serverDescription=U2hhZG93c29ja3Mg4pyI77iP"

        val server = LinkParser.parse(link)

        assertEquals("🇫🇮 Финляндия · Shadowsocks ✈️", server.name)
        assertTrue(
            "expected decoded desc, got '${server.serverDescription}'",
            server.serverDescription?.contains("Shadowsocks") == true
        )
    }
}
