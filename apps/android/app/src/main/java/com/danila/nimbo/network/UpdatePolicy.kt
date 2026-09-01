package com.danila.nimbo.network

import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateKind

internal data class ReleaseAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val updatedAt: String,
    val digest: String?
) {
    val sha256: String?
        get() = digest
            ?.trim()
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}

internal data class ReleaseCandidate(
    val tagName: String,
    val releaseName: String,
    val releaseBody: String,
    val releaseUrl: String,
    val targetCommitish: String,
    val prerelease: Boolean,
    val publishedAt: String,
    val versionCode: Int?,
    val asset: ReleaseAsset,
    /**
     * Every APK published in the release, not only the one picked for this
     * device. A build installed by hand from the site is usually the universal
     * APK, and it still has to be recognised as "this release is installed".
     */
    val apkAssets: List<ReleaseAsset> = emptyList()
) {
    /** Assets an installed APK may have come from; falls back to the picked one. */
    val installedCandidates: List<ReleaseAsset>
        get() = apkAssets.ifEmpty { listOf(asset) }

    val artifactIdentity: String
        get() = listOf(
            asset.id.toString(),
            asset.updatedAt,
            asset.digest.orEmpty().lowercase(),
            asset.size.toString(),
            asset.name
        ).joinToString("|")
}

internal object UpdatePolicy {
    fun acceptsChannel(candidate: ReleaseCandidate, channel: UpdateChannel): Boolean =
        channel == UpdateChannel.BETA || !candidate.prerelease

    fun matchingInstalledArtifact(
        currentVersion: String,
        installedSha256: String,
        candidate: ReleaseCandidate
    ): String? {
        val sameVersion = normalizedVersionTag(candidate.tagName) == normalizedVersionTag(currentVersion)
        val sameDigest = candidate.installedCandidates.any { asset ->
            asset.sha256?.equals(installedSha256, ignoreCase = true) == true
        }
        return candidate.artifactIdentity.takeIf { sameVersion && sameDigest }
    }

    fun decide(
        currentVersion: String,
        currentCode: Int,
        installedArtifactId: String?,
        candidate: ReleaseCandidate
    ): UpdateKind? {
        val explicitCode = candidate.versionCode
        if (explicitCode != null && explicitCode < currentCode) return null
        if (explicitCode != null && explicitCode > currentCode) return UpdateKind.VERSION
        if (isSemanticVersionNewer(candidate.tagName, currentVersion)) return UpdateKind.VERSION

        val isSameVersion = normalizedVersionTag(candidate.tagName) == normalizedVersionTag(currentVersion)
        val canReplaceInstalledBuild = explicitCode == null || explicitCode >= currentCode
        return if (
            isSameVersion &&
            canReplaceInstalledBuild &&
            candidate.artifactIdentity != installedArtifactId
        ) {
            UpdateKind.REPAIR
        } else {
            null
        }
    }

    fun changelog(
        releaseNotes: String,
        kind: UpdateKind,
        commitMessage: String?,
        isEnglish: Boolean
    ): String {
        val details = releaseNotes.trim().takeIf { it.isNotEmpty() }
            ?: commitMessage?.trim()?.takeIf { it.isNotEmpty() }
        if (kind == UpdateKind.REPAIR && details != null) {
            val repairNotice = if (isEnglish) {
                "An additional update with fixes and improvements is available for the installed version."
            } else {
                "Для установленной версии доступно дополнительное обновление с исправлениями и улучшениями."
            }
            return "$repairNotice\n\n$details"
        }
        details?.let { return it }
        return when (kind) {
            UpdateKind.REPAIR -> if (isEnglish) {
                "An additional update with bug fixes and stability improvements."
            } else {
                "Дополнительное обновление с исправлениями ошибок и улучшениями стабильности."
            }
            UpdateKind.VERSION -> if (isEnglish) {
                "Bug fixes and stability improvements."
            } else {
                "Исправления ошибок и улучшения стабильности."
            }
        }
    }

    fun isSemanticVersionNewer(remote: String, local: String): Boolean {
        val remoteVersion = parseSemanticVersion(remote) ?: return false
        val localVersion = parseSemanticVersion(local) ?: return false
        return remoteVersion.compareTo(localVersion) > 0
    }

    fun compareVersions(left: String, right: String): Int {
        val leftVersion = parseSemanticVersion(normalizedVersionTag(left))
        val rightVersion = parseSemanticVersion(normalizedVersionTag(right))
        return when {
            leftVersion != null && rightVersion != null -> leftVersion.compareTo(rightVersion)
            else -> normalizedVersionTag(left).compareTo(normalizedVersionTag(right), ignoreCase = true)
        }
    }

    fun normalizedVersionTag(value: String): String {
        val semanticVersion = Regex("""[vV]?(\d+(?:\.\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)""")
            .find(value.trim())
            ?.value
        return (semanticVersion ?: value).trim().lowercase().removePrefix("v")
    }

    private data class SemanticVersion(
        val core: List<Int>,
        val prerelease: List<String>?
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            core.indices.forEach { index ->
                core[index].compareTo(other.core[index]).takeIf { it != 0 }?.let { return it }
            }
            if (prerelease == null && other.prerelease == null) return 0
            if (prerelease == null) return 1
            if (other.prerelease == null) return -1

            val count = maxOf(prerelease.size, other.prerelease.size)
            for (index in 0 until count) {
                val left = prerelease.getOrNull(index) ?: return -1
                val right = other.prerelease.getOrNull(index) ?: return 1
                val leftNumber = left.toLongOrNull()
                val rightNumber = right.toLongOrNull()
                val comparison = when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> left.compareTo(right, ignoreCase = true)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }
    }

    private fun parseSemanticVersion(value: String): SemanticVersion? {
        val match = Regex(
            """^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$"""
        )
            .matchEntire(value.trim())
            ?: return null
        val prerelease = match.groupValues[4]
            .takeIf(String::isNotEmpty)
            ?.split('.')
        return SemanticVersion(
            core = (1..3).map { index -> match.groupValues[index].toIntOrNull() ?: 0 },
            prerelease = prerelease
        )
    }
}
