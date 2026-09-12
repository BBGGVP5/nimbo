package com.danila.nimbo.utils

import com.danila.nimbo.model.Server
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ответ панели, когда не пройден HWID-гейт: формально валидный список ссылок,
 * но это не серверы, а текст для человека. Раньше такой ответ считался
 * непустым и затирал рабочий список — после чего не подключалось ничего.
 */
class NoticePlaceholderServerTest {

    private fun server(
        name: String,
        host: String,
        port: Int,
        uuid: String = "00000000-0000-0000-0000-000000000000"
    ) = Server(name = name, host = host, port = port, uuid = uuid, protocol = "vless")

    @Test
    fun realTitanVpsNoticePayloadIsDetected() {
        // Ровно то, что отдаёт api1.titanvps.su без заголовка x-hwid.
        val notices = listOf(
            server("🏴 Включите HWID в настройках", "127.0.0.1", 1),
            server("🏴 Работает только с Happ и INCY", "127.0.0.1", 1),
            server("🏴Обратитесь в поддержку", "127.0.0.1", 1),
            server("🏴@TitanVPSHelp_bot", "127.0.0.1", 1)
        )
        notices.forEach {
            assertTrue("должна быть заглушкой: ${it.name}", isNoticePlaceholderServer(it))
        }
    }

    @Test
    fun loopbackAndUnspecifiedHostsArePlaceholders() {
        assertTrue(isNoticePlaceholderServer(server("x", "127.0.0.1", 443)))
        assertTrue(isNoticePlaceholderServer(server("x", "127.53.1.9", 443)))
        assertTrue(isNoticePlaceholderServer(server("x", "localhost", 443)))
        assertTrue(isNoticePlaceholderServer(server("x", "::1", 443)))
        assertTrue(isNoticePlaceholderServer(server("x", "0.0.0.0", 443)))
        assertTrue(isNoticePlaceholderServer(server("x", "", 443)))
    }

    @Test
    fun unusablePortsArePlaceholders() {
        assertTrue(isNoticePlaceholderServer(server("x", "example.org", 0)))
        assertTrue(isNoticePlaceholderServer(server("x", "example.org", 1)))
    }

    @Test
    fun zeroUuidIsPlaceholder() {
        assertTrue(isNoticePlaceholderServer(server("x", "example.org", 443, "00000000-0000-0000-0000-000000000000")))
    }

    @Test
    fun realServersFromTheSubscriptionAreNotPlaceholders() {
        // Настоящие узлы TITAN VPS и nebulaguard.
        val real = listOf(
            server("🇫🇮 Финляндия 1", "45.137.69.248", 443, "a8902d13-8df2-4764-9720-9c8ca12b6a56"),
            server("🇸🇪 Швеция 2", "89.22.238.19", 5443, "trojan-pass"),
            server("🇩🇪 Германия 3", "5.230.209.21", 20011, "a8902d13-8df2-4764-9720-9c8ca12b6a56"),
            // Обход на подменном адресе — узел странный, но настоящий.
            server("✨ Автобалансер EU | Обход #1", "google.com", 456, "1f1e2aba-f6ee-481e-a2e6-1851e27f2218")
        )
        real.forEach {
            assertFalse("не должна считаться заглушкой: ${it.name}", isNoticePlaceholderServer(it))
        }
    }
}
