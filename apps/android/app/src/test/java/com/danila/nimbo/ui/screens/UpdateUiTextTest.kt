package com.danila.nimbo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class UpdateUiTextTest {

    @Test
    fun `file size follows selected russian language`() {
        assertEquals("29,31 МБ", UpdateUiText.fileSize(30_733_000L, "ru", decimals = 2))
        assertEquals("512,0 КБ", UpdateUiText.fileSize(524_288L, "ru", decimals = 1))
    }

    @Test
    fun `file size follows selected english language`() {
        assertEquals("29.31 MB", UpdateUiText.fileSize(30_733_000L, "en", decimals = 2))
        assertEquals("512.0 KB", UpdateUiText.fileSize(524_288L, "en", decimals = 1))
    }

    @Test
    fun `download error follows selected language`() {
        assertEquals(
            "Размер APK не совпадает с данными GitHub",
            UpdateUiText.error(UpdateUiText.APK_SIZE_MISMATCH, "ru")
        )
        assertEquals(
            "The APK size does not match GitHub data",
            UpdateUiText.error(UpdateUiText.APK_SIZE_MISMATCH, "en")
        )
    }

    @Test
    fun `version label keeps channel readable`() {
        assertEquals("v1.1.0 Beta 3", UpdateUiText.versionLabel("v1.1.0-beta.3", "ru"))
        assertEquals("v1.1.0 Beta 3", UpdateUiText.versionLabel("1.1.0-beta.3", "en"))
        assertEquals("v1.0.2", UpdateUiText.versionLabel("v1.0.2", "ru"))
    }

    @Test
    fun `release date follows selected language and timezone`() {
        val zone = ZoneId.of("Europe/Samara")
        assertEquals(
            "5 августа 2026 · 18:40",
            UpdateUiText.releaseDate("2026-08-05T14:40:00Z", "ru", zone)
        )
        assertEquals(
            "August 5, 2026 · 6:40 PM",
            UpdateUiText.releaseDate("2026-08-05T14:40:00Z", "en", zone)
        )
        assertEquals(null, UpdateUiText.releaseDate(null, "ru", zone))
    }
}
