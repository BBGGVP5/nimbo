package com.danila.nimbo.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CrossPlatformSyncTest {

    private val future = 2_000_000_000_000L
    private val key = ByteArray(32) { index -> (index + 1).toByte() }
    private val encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    @Test
    fun `valid private pairing qr parses`() {
        val parsed = CrossSyncProtocol.parseQr(
            "nimbo-sync://pair?v=1&host=192.168.1.20&port=42000&sid=session-1&key=$encodedKey&exp=$future",
            nowMs = future - 10_000
        )

        assertEquals("192.168.1.20", parsed.host)
        assertEquals(42000, parsed.port)
        assertEquals("session-1", parsed.sessionId)
        assertArrayEquals(key, parsed.key)
    }

    @Test
    fun `public host and expired qr are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrossSyncProtocol.parseQr(
                "nimbo-sync://pair?v=1&host=8.8.8.8&port=42000&sid=s&key=$encodedKey&exp=$future",
                nowMs = future - 1000
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrossSyncProtocol.parseQr(
                "nimbo-sync://pair?v=1&host=10.0.0.2&port=42000&sid=s&key=$encodedKey&exp=$future",
                nowMs = future + 1
            )
        }
    }

    @Test
    fun `empty side gets direction recommendation`() {
        val empty = SyncInventory(0, false, false, false)
        val populated = SyncInventory(2, true, true, true)

        assertEquals(
            SyncDirection.DESKTOP_TO_ANDROID,
            CrossSyncProtocol.recommendDirection(android = empty, desktop = populated)
        )
        assertEquals(
            SyncDirection.ANDROID_TO_DESKTOP,
            CrossSyncProtocol.recommendDirection(android = populated, desktop = empty)
        )
        assertNull(CrossSyncProtocol.recommendDirection(android = populated, desktop = populated))
    }

    @Test
    fun `zero subscriptions recommends populated side even when both have settings`() {
        val phoneWithOnlySettings = SyncInventory(0, true, true, true)
        val desktopWithSubscriptions = SyncInventory(3, true, true, true)

        assertEquals(
            SyncDirection.DESKTOP_TO_ANDROID,
            CrossSyncProtocol.recommendDirection(phoneWithOnlySettings, desktopWithSubscriptions)
        )
        assertEquals(
            SyncDirection.ANDROID_TO_DESKTOP,
            CrossSyncProtocol.recommendDirection(desktopWithSubscriptions, phoneWithOnlySettings)
        )
    }

    @Test
    fun `bundle filtering removes disabled categories before transfer`() {
        val bundle = CrossSyncBundle(
            platform = "android",
            deviceName = "Phone",
            createdAtMs = 1L,
            subscriptions = listOf(SyncSubscription("https://example.com/key")),
            appearance = SyncAppearance(),
            connection = SyncConnection(),
            automation = SyncAutomation()
        )

        val filtered = bundle.filtered(
            SyncCategories(subscriptions = true, appearance = false, connection = false, automation = true)
        )

        assertEquals(1, filtered.subscriptions.size)
        assertNull(filtered.appearance)
        assertNull(filtered.connection)
        assertTrue(filtered.automation != null)
    }

    @Test
    fun `subscription preview exposes names but never secret urls`() {
        val previews = CrossSyncProtocol.subscriptionPreviewNames(
            listOf(
                SyncSubscription("https://provider.example/sub/SecretKey", "Основная"),
                SyncSubscription("https://provider.example/sub/SecondSecret", null),
                SyncSubscription("https://provider.example/sub/ThirdSecret", "https://provider.example/sub/ThirdSecret")
            )
        )

        assertEquals(listOf("Основная", "Подписка 2", "Подписка 3"), previews)
        assertTrue(previews.none { it.contains("Secret") || it.contains("://") })
    }

    @Test
    fun `subscriptions merge by canonical url without overwriting local name`() {
        val merged = CrossSyncProtocol.mergeSubscriptions(
            local = listOf(SyncSubscription("HTTPS://EXAMPLE.COM/sub/", "Local")),
            incoming = listOf(
                SyncSubscription("https://example.com/sub", "Remote"),
                SyncSubscription("https://second.example/key", "Second")
            )
        )

        assertEquals(2, merged.size)
        assertEquals("Local", merged.first().name)
        assertTrue(merged.any { it.url == "https://second.example/key" })
    }

    @Test
    fun `aes gcm round trips and rejects tampering`() {
        val plaintext = "subscription-secret".encodeToByteArray()
        val envelope = CrossSyncCrypto.encrypt(key, "session-1", plaintext)

        assertArrayEquals(plaintext, CrossSyncCrypto.decrypt(key, envelope))

        val bytes = Base64.getUrlDecoder().decode(envelope.ciphertext)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val tampered = envelope.copy(
            ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        )
        assertThrows(Exception::class.java) {
            CrossSyncCrypto.decrypt(key, tampered)
        }
    }
}
