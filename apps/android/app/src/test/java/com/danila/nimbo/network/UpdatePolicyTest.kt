package com.danila.nimbo.network

import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val currentAsset = ReleaseCandidate(
        tagName = "v1.0.1",
        releaseName = "Nimbo 1.0.1",
        releaseBody = "",
        releaseUrl = "https://example.test/release",
        targetCommitish = "main",
        prerelease = false,
        publishedAt = "2026-07-18T10:00:00Z",
        versionCode = 2,
        asset = ReleaseAsset(
            id = 100,
            name = "Nimbo_v1.0.1_universal_release.apk",
            downloadUrl = "https://example.test/100.apk",
            size = 1000,
            updatedAt = "2026-07-18T10:00:00Z",
            digest = "sha256:aaaa"
        )
    )

    @Test
    fun installedSameVersionArtifactIsNotOfferedAgain() {
        assertNull(
            UpdatePolicy.decide(
                currentVersion = "1.0.1",
                currentCode = 2,
                installedArtifactId = currentAsset.artifactIdentity,
                candidate = currentAsset
            )
        )
    }

    @Test
    fun matchingInstalledApkDigestBootstrapsArtifactIdentity() {
        val digestedAsset = currentAsset.copy(
            asset = currentAsset.asset.copy(digest = "sha256:${"a".repeat(64)}")
        )
        assertEquals(
            digestedAsset.artifactIdentity,
            UpdatePolicy.matchingInstalledArtifact(
                currentVersion = "1.0.1",
                installedSha256 = "a".repeat(64),
                candidate = digestedAsset
            )
        )
        assertNull(
            UpdatePolicy.matchingInstalledArtifact(
                currentVersion = "1.0.1",
                installedSha256 = "b".repeat(64),
                candidate = digestedAsset
            )
        )
    }

    @Test
    fun reuploadedAssetForSameVersionIsARepair() {
        val changed = currentAsset.copy(
            asset = currentAsset.asset.copy(
                id = 101,
                updatedAt = "2026-07-19T09:30:00Z",
                digest = "sha256:bbbb"
            )
        )

        assertEquals(
            UpdateKind.REPAIR,
            UpdatePolicy.decide("1.0.1", 2, currentAsset.artifactIdentity, changed)
        )
    }

    @Test
    fun newerVersionIsARegularUpdate() {
        val newer = currentAsset.copy(tagName = "v1.1.0", releaseName = "Nimbo 1.1.0", versionCode = 3)

        assertEquals(
            UpdateKind.VERSION,
            UpdatePolicy.decide("1.0.1", 2, currentAsset.artifactIdentity, newer)
        )
    }

    @Test
    fun explicitOlderVersionCodeIsNeverOffered() {
        val incompatible = currentAsset.copy(tagName = "v2.0.0", versionCode = 1)

        assertNull(UpdatePolicy.decide("1.0.1", 2, currentAsset.artifactIdentity, incompatible))
    }

    @Test
    fun channelFilteringKeepsStableOutOfStableAndPrereleasesInBeta() {
        val beta = currentAsset.copy(tagName = "v1.1.0-beta.1", prerelease = true)

        assertTrue(UpdatePolicy.acceptsChannel(currentAsset, UpdateChannel.STABLE))
        assertEquals(false, UpdatePolicy.acceptsChannel(beta, UpdateChannel.STABLE))
        assertTrue(UpdatePolicy.acceptsChannel(beta, UpdateChannel.BETA))
        assertTrue(UpdatePolicy.acceptsChannel(currentAsset, UpdateChannel.BETA))
    }

    @Test
    fun repairWithoutNotesUsesBugFixFallback() {
        assertEquals(
            "Исправленный файл релиза: исправления ошибок и улучшения стабильности.",
            UpdatePolicy.changelog("", UpdateKind.REPAIR, commitMessage = null, isEnglish = false)
        )
        assertEquals(
            "Fixed release file: bug fixes and stability improvements.",
            UpdatePolicy.changelog("", UpdateKind.REPAIR, commitMessage = null, isEnglish = true)
        )
    }

    @Test
    fun blankReleaseNotesUseCommitSubject() {
        assertEquals(
            "Fix updater cache\n\nExtra commit details",
            UpdatePolicy.changelog("", UpdateKind.VERSION, "Fix updater cache\n\nExtra commit details", false)
        )
    }
}
