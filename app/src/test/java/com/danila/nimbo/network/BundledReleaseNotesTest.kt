package com.danila.nimbo.network

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledReleaseNotesTest {
    @Test
    fun `stable release includes localized home fixes and accepts tag prefix`() {
        val russian = BundledReleaseNotes.forVersion("1.2.0", false).orEmpty()
        val english = BundledReleaseNotes.forVersion("1.2.0", true).orEmpty()
        assertTrue(russian.contains("Стабильная 1.2.0"))
        assertTrue(russian.contains("памяти"))
        assertTrue(english.contains("1.2.0"))
        assertTrue(english.contains("memory", ignoreCase = true))
        assertTrue(russian != english)
        assertEquals(russian, BundledReleaseNotes.forVersion("v1.2.0", false))
        assertEquals(english, BundledReleaseNotes.forVersion("v1.2.0", true))
    }

    @Test
    fun `beta 5 includes localized appearance notes`() {
        val russian = BundledReleaseNotes.forVersion("v1.2.0-beta.5", false).orEmpty()
        val english = BundledReleaseNotes.forVersion("1.2.0-beta.5", true).orEmpty()
        assertTrue(russian.contains("Оформление"))
        assertTrue(english.contains("Appearance"))
        assertTrue(russian.contains("Dotted"))
        assertTrue(english.contains("Manga"))
    }

    @Test
    fun `version 1 0 2 has localized Android changelog`() {
        val russian = BundledReleaseNotes.forVersion("v1.0.2", isEnglish = false).orEmpty()
        val english = BundledReleaseNotes.forVersion("1.0.2", isEnglish = true).orEmpty()

        assertTrue(russian.contains("Проверка БС"))
        assertTrue(russian.contains("SHA-256"))
        assertTrue(english.contains("Allowlist check"))
        assertTrue(english.contains("SHA-256"))
    }

    @Test
    fun `unknown version has no bundled changelog`() {
        assertNull(BundledReleaseNotes.forVersion("9.9.9", isEnglish = false))
    }
}
