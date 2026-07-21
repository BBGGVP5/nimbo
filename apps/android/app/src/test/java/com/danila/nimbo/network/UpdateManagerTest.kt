package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun normalizedVersionTag_usesSemanticPartOfReleaseTitle() {
        assertEquals("1.2.3", UpdateManager.normalizedVersionTag("Nimbo v1.2.3"))
        assertEquals("1.2.3", UpdateManager.normalizedVersionTag("1.2.3"))
    }

    @Test
    fun releaseNotesForAndroid_keepsChangesAndApkButDropsDesktopInstallers() {
        val notes = """
            ## What's new
            - Faster connection recovery
            ## Files
            - NimboSetup_1.0.1_x64.exe — Windows
            - Nimbo_1.0.1_arm64-v8a.apk — Android
        """.trimIndent()

        val filtered = UpdateManager.releaseNotesForAndroid(notes)

        assertTrue(filtered.contains("Faster connection recovery"))
        assertTrue(filtered.contains("arm64-v8a.apk"))
        assertFalse(filtered.contains(".exe"))
        assertFalse(filtered.contains("Windows"))
    }

    @Test
    fun parseReleaseCandidate_keepsAssetDigestAndUpdatedTime() {
        val release = mapOf<String, Any?>(
            "tag_name" to "v1.0.1",
            "name" to "Nimbo 1.0.1",
            "body" to "versionCode: 2",
            "html_url" to "https://example.test/releases/v1.0.1",
            "target_commitish" to "main",
            "draft" to false,
            "prerelease" to false,
            "published_at" to "2026-07-18T08:00:00Z",
            "assets" to listOf(
                mapOf(
                    "id" to 42.0,
                    "name" to "Nimbo_v1.0.1_universal_release.apk",
                    "browser_download_url" to "https://example.test/nimbo.apk",
                    "size" to 1234.0,
                    "updated_at" to "2026-07-19T09:30:00Z",
                    "digest" to "sha256:${"a".repeat(64)}"
                )
            )
        )

        val candidate = requireNotNull(UpdateManager.parseReleaseCandidate(release, listOf("arm64-v8a")))

        assertEquals(42L, candidate.asset.id)
        assertEquals("2026-07-19T09:30:00Z", candidate.asset.updatedAt)
        assertEquals("a".repeat(64), candidate.asset.sha256)
        assertEquals(2, candidate.versionCode)
        assertTrue(candidate.artifactIdentity.contains("2026-07-19T09:30:00Z"))
    }

    @Test
    fun notificationSummaryUsesFirstMeaningfulReleaseNote() {
        val notes = """
            ## Исправления
            - Исправлен запуск VPN после обновления
            - Улучшена стабильность
        """.trimIndent()

        assertEquals(
            "Исправлен запуск VPN после обновления",
            UpdateManager.notificationSummary(notes)
        )
    }
}
