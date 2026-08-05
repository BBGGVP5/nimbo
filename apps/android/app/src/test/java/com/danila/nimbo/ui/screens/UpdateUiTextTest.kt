package com.danila.nimbo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
