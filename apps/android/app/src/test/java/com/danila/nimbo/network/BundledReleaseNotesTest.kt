package com.danila.nimbo.network

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledReleaseNotesTest {
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
