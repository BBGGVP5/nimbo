package com.danila.nimbo.ui.screens

import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.components.cleanServerName
import com.danila.nimbo.ui.components.extractFlagEmoji
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerPresentationTest {
    private fun server(description: String?) = Server(name = "🇫🇮 Финляндия · XHTTP 🚀", host = "example.invalid",
        port = 443, uuid = "fixture", protocol = "vless", serverDescription = description)

    @Test fun providerDescriptionIsRetained() {
        assertEquals("10 Гбит/с · резерв", readableServerDescription(server(" 10 Гбит/с · резерв ")))
    }
    @Test fun missingDescriptionDoesNotInventMetadata() {
        listOf(null, "", "null", "example.invalid", "vless://private@host").forEach {
            assertEquals("", readableServerDescription(server(it)))
        }
    }
    @Test fun flagIsSeparateWithoutLosingProviderName() {
        assertEquals("🇫🇮", extractFlagEmoji(server(null).name))
        assertEquals("Финляндия · XHTTP 🚀", cleanServerName(server(null).name))
    }
    @Test fun namedCountryWorksWithoutEmojiAndUnknownNamesRemainUnknown() {
        assertEquals("🇫🇮", extractFlagEmoji("Финляндия · резерв"))
        assertEquals("", extractFlagEmoji("Обход LTE #2"))
        assertEquals("Обход LTE #2 ❤️", cleanServerName("Обход LTE #2 ❤️"))
    }
}
