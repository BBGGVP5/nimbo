package com.danila.nimbo.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.MainActivity
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.R
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.CustomAppIconManager
import com.danila.nimbo.utils.AppVisibilityTracker
import com.danila.nimbo.ui.screens.UpdateUiText
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

enum class UpdateDownloadStage {
    DOWNLOADING,
    VERIFYING,
    READY
}

data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val stage: UpdateDownloadStage
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val RELEASES_API_URL = "https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=20"
    private const val COMMIT_API_URL = "https://api.github.com/repos/BBGGVP5/nimbo/commits/"
    // Android does not let an app raise the importance of an already-created
    // channel. A versioned channel ensures older low-priority beta channels do
    // not keep suppressing a newly available update notification.
    private const val CHANNEL_ID = "app_updates_v2"
    private const val NOTIFICATION_ID = 1003

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow<UpdateDownloadProgress?>(null)
    val downloadStatus = _downloadStatus.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError = _downloadError.asStateFlow()

    /** True after a paused download: the partial file is kept and can be resumed. */
    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    // The download outlives the dialog that started it: closing the update popup
    // must not cancel a transfer that is already running.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    /** Starts (or resumes) the download outside of the caller's composition scope. */
    fun startDownload(context: Context, updateInfo: UpdateInfo) {
        if (_isDownloading.value) return
        val appContext = context.applicationContext
        downloadJob?.cancel()
        downloadJob = downloadScope.launch {
            downloadAndInstall(appContext, updateInfo)
        }
    }

    /** Stops the transfer but keeps the partial file, so the next start resumes it. */
    fun pauseDownload() {
        if (!_isDownloading.value) return
        _isPaused.value = true
        downloadJob?.cancel()
        downloadJob = null
    }

    fun clearDownloadError() {
        _downloadError.value = null
    }

    /** Bytes already on disk for this exact artifact; > 0 means the download can be resumed. */
    fun resumableBytes(context: Context, updateInfo: UpdateInfo): Long =
        partialFileFor(context, updateInfo).let { if (it.isFile) it.length() else 0L }

    /** The fully downloaded and verified APK, when it is still cached. */
    fun verifiedApkFile(context: Context, updateInfo: UpdateInfo): File? =
        verifiedFileFor(context, updateInfo).takeIf { it.isFile && it.length() > 0L }

    private fun artifactHash(updateInfo: UpdateInfo): String =
        Integer.toHexString(updateInfo.artifactId.hashCode())

    private fun verifiedFileFor(context: Context, updateInfo: UpdateInfo): File =
        File(context.cacheDir, "Nimbo_update_${artifactHash(updateInfo)}.apk")

    private fun partialFileFor(context: Context, updateInfo: UpdateInfo): File =
        File(context.cacheDir, "Nimbo_update_${artifactHash(updateInfo)}.apk.part")

    /** Checks the selected stable/beta channel and compares the exact release asset. */
    suspend fun checkUpdate(context: Context): UpdateInfo? = try {
        checkUpdateOrThrow(context)
    } catch (e: Exception) {
        Log.e(TAG, "Update check failed via GitHub API", e)
        null
    }

    /** Background entry point: network/server failures must reach WorkManager for retry. */
    internal suspend fun checkUpdateInBackground(context: Context): UpdateInfo? =
        checkUpdateOrThrow(context)

    private suspend fun checkUpdateOrThrow(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        val channel = prefs.updateChannel
        val releases = fetchReleaseMaps()
        val candidate = releases
            .asSequence()
            .mapNotNull { parseReleaseCandidate(it, Build.SUPPORTED_ABIS.toList()) }
            .filter { UpdatePolicy.acceptsChannel(it, channel) }
            .maxWithOrNull { left, right -> UpdatePolicy.compareVersions(left.tagName, right.tagName) }
            ?: return@withContext null

        if (prefs.installedUpdateArtifactId == null && candidate.installedCandidates.any { it.sha256 != null }) {
            val installedApk = File(context.applicationInfo.sourceDir)
            val installedDigest = installedApk.takeIf(File::isFile)?.let(::sha256)
            val matchingIdentity = installedDigest?.let {
                UpdatePolicy.matchingInstalledArtifact(BuildConfig.VERSION_NAME, it, candidate)
            }
            if (matchingIdentity != null) {
                prefs.installedUpdateArtifactId = matchingIdentity
                Log.i(TAG, "Bootstrapped installed artifact identity from APK digest")
                return@withContext null
            }
        }

        val kind = UpdatePolicy.decide(
            currentVersion = BuildConfig.VERSION_NAME,
            currentCode = BuildConfig.VERSION_CODE,
            installedArtifactId = prefs.installedUpdateArtifactId,
            candidate = candidate
        ) ?: return@withContext null

        val filteredNotes = releaseNotesForAndroid(candidate.releaseBody)
        val isEnglish = prefs.appLanguage == "en"
        val bundledNotes = BundledReleaseNotes.forVersion(candidate.tagName, isEnglish)
        val notesSource = filteredNotes.ifBlank { bundledNotes.orEmpty() }
        val commitMessage = if (notesSource.isBlank()) fetchCommitMessage(candidate.tagName) else null
        val notes = UpdatePolicy.changelog(notesSource, kind, commitMessage, isEnglish)

        Log.d(
            TAG,
            "Update found: channel=$channel tag=${candidate.tagName} kind=$kind " +
                "asset=${candidate.asset.id} updated=${candidate.asset.updatedAt}"
        )

        UpdateInfo(
            versionCode = candidate.versionCode ?: 0,
            versionName = candidate.tagName,
            downloadUrl = candidate.asset.downloadUrl,
            changelog = notes,
            publishDate = candidate.publishedAt,
            fileSize = candidate.asset.size,
            channel = channel,
            kind = kind,
            artifactId = candidate.artifactIdentity,
            assetId = candidate.asset.id,
            assetName = candidate.asset.name,
            assetUpdatedAt = candidate.asset.updatedAt,
            sha256 = candidate.asset.sha256,
            releaseUrl = candidate.releaseUrl
        )
    }

    /** Compatibility entry point for older callers; new code should pass its Context explicitly. */
    suspend fun checkUpdate(): UpdateInfo? = checkUpdate(NebulaGuardApplication.instance)

    private fun fetchReleaseMaps(): List<Map<String, Any?>> {
        // GitHub assets can be deleted and uploaded again without changing the
        // tag. A cache-buster keeps a proxy/CDN from returning the previous
        // asset id, digest and updated_at for that same release.
        val request = githubRequest("$RELEASES_API_URL&_=${System.currentTimeMillis()}")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub releases request failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val raw = gson.fromJson(body, List::class.java) ?: return emptyList()
            return raw.mapNotNull(::stringKeyMap)
        }
    }

    private fun fetchCommitMessage(tagName: String): String? {
        val encodedTag = Uri.encode(tagName)
        val request = githubRequest(COMMIT_API_URL + encodedTag)
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = gson.fromJson(response.body?.string().orEmpty(), Map::class.java)
                val commit = stringKeyMap(raw?.get("commit")) ?: return@use null
                (commit["message"] as? String)?.trim()?.takeIf(String::isNotEmpty)
            }
        }.onFailure { Log.w(TAG, "Could not load commit message for $tagName", it) }.getOrNull()
    }

    private fun githubRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Cache-Control", "no-cache, no-store")
        .header("Pragma", "no-cache")
        .header("User-Agent", "Nimbo-Android/${BuildConfig.VERSION_NAME}")
        .build()

    internal fun parseReleaseCandidate(
        release: Map<String, Any?>,
        supportedAbis: List<String>
    ): ReleaseCandidate? {
        if (release["draft"] as? Boolean == true) return null
        val tagName = release["tag_name"] as? String ?: return null
        val releaseBody = release["body"] as? String ?: ""
        val assets = parseAssets(release["assets"])
        val bestAsset = getBestAsset(assets, supportedAbis) ?: return null
        val manualVersionCode = Regex("versionCode:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(releaseBody)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

        return ReleaseCandidate(
            tagName = tagName,
            releaseName = (release["name"] as? String).orEmpty().ifBlank { tagName },
            releaseBody = releaseBody,
            releaseUrl = release["html_url"] as? String ?: "",
            targetCommitish = release["target_commitish"] as? String ?: tagName,
            prerelease = release["prerelease"] as? Boolean ?: false,
            publishedAt = release["published_at"] as? String ?: "",
            versionCode = manualVersionCode,
            asset = bestAsset,
            apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        )
    }

    private fun parseAssets(rawAssets: Any?): List<ReleaseAsset> {
        if (rawAssets !is List<*>) return emptyList()
        return rawAssets.mapNotNull { rawItem ->
            val item = stringKeyMap(rawItem) ?: return@mapNotNull null
            val id = (item["id"] as? Number)?.toLong() ?: return@mapNotNull null
            val name = item["name"] as? String ?: return@mapNotNull null
            val downloadUrl = item["browser_download_url"] as? String ?: return@mapNotNull null
            ReleaseAsset(
                id = id,
                name = name,
                downloadUrl = downloadUrl,
                size = (item["size"] as? Number)?.toLong() ?: 0L,
                updatedAt = item["updated_at"] as? String ?: "",
                digest = item["digest"] as? String
            )
        }
    }

    private fun getBestAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
        for (abi in supportedAbis) {
            val deviceAbi = canonicalDeviceAbi(abi) ?: continue
            assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    canonicalAssetAbi(asset.name) == deviceAbi
            }?.let { return it }
        }
        return assets.firstOrNull { it.name.contains("universal", true) && it.name.endsWith(".apk", true) }
            ?: assets.firstOrNull {
                it.name.endsWith(".apk", true) && canonicalAssetAbi(it.name) == null
            }
    }

    private fun canonicalDeviceAbi(value: String): String? {
        val normalized = value.lowercase().replace("-", "").replace("_", "")
        return when (normalized) {
            "arm64v8a", "arm64" -> "arm64"
            "armeabiv7a", "armv7", "armv7a" -> "armv7"
            "x8664", "amd64" -> "x86_64"
            "x86" -> "x86"
            "riscv64" -> "riscv64"
            else -> null
        }
    }

    private fun canonicalAssetAbi(name: String): String? {
        val lower = name.lowercase()
        val normalized = lower.replace("-", "").replace("_", "")
        return when {
            "arm64v8a" in normalized || "arm64" in normalized -> "arm64"
            "armeabiv7a" in normalized || "armv7" in normalized -> "armv7"
            "x8664" in normalized || "amd64" in normalized -> "x86_64"
            Regex("(^|[^a-z0-9])x86([^a-z0-9]|$)").containsMatchIn(lower) -> "x86"
            "riscv64" in normalized -> "riscv64"
            else -> null
        }
    }

    private fun stringKeyMap(raw: Any?): Map<String, Any?>? {
        val map = raw as? Map<*, *> ?: return null
        return map.entries
            .filter { it.key is String }
            .associate { (key, value) -> key as String to value }
    }

    internal fun isSemanticVersionNewer(remote: String, local: String): Boolean =
        UpdatePolicy.isSemanticVersionNewer(remote, local)

    internal fun normalizedVersionTag(value: String): String = UpdatePolicy.normalizedVersionTag(value)

    /** Returns only user-facing Android notes and never exposes raw GitHub release markup. */
    internal fun releaseNotesForAndroid(releaseBody: String): String {
        extractPlatformSection(releaseBody, "android")?.let(::sanitizeReleaseNotes)?.let { notes ->
            if (notes.isNotBlank()) return notes
        }

        var pendingAssetHeading: String? = null
        val visibleLines = mutableListOf<String>()

        releaseBody.lineSequence().forEach { line ->
            if (isReleaseAssetHeading(line)) {
                pendingAssetHeading = line
                return@forEach
            }
            if (isDesktopOnlyReleaseLine(line) || isRawReleaseDecoration(line)) return@forEach
            if (pendingAssetHeading != null && line.isBlank()) return@forEach

            pendingAssetHeading = null
            if (!isReleaseAssetLine(line)) visibleLines += line
        }

        return sanitizeReleaseNotes(visibleLines.joinToString("\n"))
    }

    private fun extractPlatformSection(body: String, platform: String): String? {
        val marker = Regex(
            "<!--\\s*nimbo:$platform:start\\s*-->(.*?)<!--\\s*nimbo:$platform:end\\s*-->",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return marker.find(body)?.groupValues?.getOrNull(1)
    }

    private fun sanitizeReleaseNotes(value: String): String {
        val withoutComments = value.replace(
            Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL)),
            ""
        )
        return withoutComments
            .lineSequence()
            .map { line ->
                line
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "")
                    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trimEnd()
            }
            .flatMap { it.lineSequence() }
            .map { it.trimEnd() }
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("|") ||
                    trimmed.matches(Regex("^:?-{3,}:?(\\s*\\|.*)?$")) ||
                    trimmed.matches(Regex("^>\\s*\\[![A-Z]+].*$", RegexOption.IGNORE_CASE)) ||
                    isReleaseAssetLine(trimmed)
            }
            .joinToString("\n")
            .replace(Regex("(?m)^>\\s?"), "")
            .replace(Regex("[ \\t]+$", RegexOption.MULTILINE), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun isReleaseAssetHeading(line: String): Boolean {
        val heading = line.trim()
            .trimStart('#', '-', '*', '•', ' ')
            .trim()
            .trimEnd(':', '.')
            .trim()
            .lowercase()
        return heading in setOf("files", "файлы", "installers", "установщики")
    }

    private fun isAndroidReleaseLine(line: String): Boolean {
        val value = line.lowercase()
        return value.contains(".apk") || value.contains("android")
    }

    private fun isReleaseAssetLine(line: String): Boolean {
        val value = line.lowercase()
        val hasPackage = listOf(".apk", ".exe", ".msi", ".dmg", ".appimage", ".deb", ".rpm", ".sha256")
            .any(value::contains)
        return hasPackage && (
            value.contains("http://") ||
                value.contains("https://") ||
                value.trimStart('-', '*', ' ', '|').startsWith("nimbo")
            )
    }

    private fun isRawReleaseDecoration(line: String): Boolean {
        val value = line.trim().lowercase()
        return value.startsWith("<") || value.startsWith("![") || value.startsWith("<!-- versioncode")
    }

    private fun isDesktopOnlyReleaseLine(line: String): Boolean {
        val value = line.lowercase()
        if (isAndroidReleaseLine(line)) return false
        return listOf(".exe", ".msi", ".dmg", "appimage", "windows", "win32", "win64", "macos", "mac os")
            .any(value::contains)
    }

    private fun channelForTag(tag: String): UpdateChannel =
        if (tag.contains("beta", ignoreCase = true)) UpdateChannel.BETA else UpdateChannel.STABLE

    /** Gets release notes for the currently installed tag. */
    suspend fun getReleaseInfoForTag(tag: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val encodedTag = Uri.encode(tag)
            val url = "https://api.github.com/repos/BBGGVP5/nimbo/releases/tags/$encodedTag"
            client.newCall(githubRequest(url)).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = gson.fromJson(response.body?.string().orEmpty(), Map::class.java)
                val release = stringKeyMap(raw) ?: return@withContext null
                val tagName = release["tag_name"] as? String ?: tag
                val isEnglish = NebulaGuardApplication.instance.preferencesManager.appLanguage == "en"
                val remoteNotes = releaseNotesForAndroid(release["body"] as? String ?: "")
                UpdateInfo(
                    versionCode = 0,
                    versionName = tagName,
                    downloadUrl = "",
                    changelog = remoteNotes.ifBlank {
                        BundledReleaseNotes.forVersion(tagName, isEnglish).orEmpty()
                    },
                    publishDate = release["published_at"] as? String,
                    releaseUrl = release["html_url"] as? String,
                    channel = channelForTag(tagName)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch release info for tag: $tag", e)
            val isEnglish = NebulaGuardApplication.instance.preferencesManager.appLanguage == "en"
            BundledReleaseNotes.forVersion(tag, isEnglish)?.let { notes ->
                UpdateInfo(
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = tag,
                    downloadUrl = "",
                    changelog = notes,
                    channel = channelForTag(tag)
                )
            }
        }
    }

    /** Downloads into a temporary file and opens the installer only after every available check passes. */
    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext

        _isDownloading.value = true
        _downloadProgress.value = 0.01f
        _downloadStatus.value = UpdateDownloadProgress(
            downloadedBytes = 0L,
            totalBytes = updateInfo.fileSize,
            stage = UpdateDownloadStage.DOWNLOADING
        )
        _downloadError.value = null
        _isPaused.value = false

        val verifiedFile = verifiedFileFor(context, updateInfo)
        val partialFile = partialFileFor(context, updateInfo)

        try {
            val validation = if (verifiedFile.isFile) {
                runCatching { verifyDownloadedApk(context, verifiedFile, updateInfo) }
                    .onFailure {
                        Log.w(TAG, "Cached APK validation failed; downloading it again", it)
                        verifiedFile.delete()
                    }
                    .getOrNull()
            } else {
                null
            }

            var effectiveUpdateInfo = updateInfo
            val verified = validation ?: run {
                normalizePartialFile(partialFile, updateInfo.fileSize)
                ensureDownloadAllowed(context, updateInfo, partialFile.length())
                if (updateInfo.fileSize <= 0L || partialFile.length() != updateInfo.fileSize) {
                    val observedBytes = downloadToFile(updateInfo, partialFile)
                    if (observedBytes > 0L && observedBytes != updateInfo.fileSize) {
                        if (updateInfo.sha256 == null) {
                            throw SecurityException(UpdateUiText.APK_SIZE_MISMATCH)
                        }
                        Log.w(
                            TAG,
                            "GitHub asset size changed after metadata fetch: " +
                                "${updateInfo.fileSize} -> $observedBytes; SHA-256 remains mandatory"
                        )
                        effectiveUpdateInfo = updateInfo.copy(fileSize = observedBytes)
                    }
                } else {
                    _downloadProgress.value = 1f
                }
                _downloadStatus.value = UpdateDownloadProgress(
                    downloadedBytes = partialFile.length(),
                    totalBytes = effectiveUpdateInfo.fileSize,
                    stage = UpdateDownloadStage.VERIFYING
                )
                val checked = try {
                    verifyDownloadedApk(context, partialFile, effectiveUpdateInfo)
                } catch (e: Exception) {
                    // A partial network transfer is useful on retry, but a file that
                    // reached verification and failed size/hash/APK checks is unsafe.
                    partialFile.delete()
                    throw e
                }
                if (verifiedFile.exists()) verifiedFile.delete()
                if (!partialFile.renameTo(verifiedFile)) {
                    partialFile.copyTo(verifiedFile, overwrite = true)
                    partialFile.delete()
                }
                checked
            }

            recordPendingInstallation(context, effectiveUpdateInfo, verified)
            _downloadProgress.value = 1f
            _downloadStatus.value = UpdateDownloadProgress(
                downloadedBytes = verifiedFile.length(),
                totalBytes = effectiveUpdateInfo.fileSize.takeIf { it > 0L } ?: verifiedFile.length(),
                stage = UpdateDownloadStage.READY
            )
            withContext(Dispatchers.Main) { installApk(context, verifiedFile) }
        } catch (e: CancellationException) {
            // Paused by the user: the progress state stays visible so the UI can
            // offer resuming from the bytes that are already on disk.
            Log.i(TAG, "Update download paused at ${partialFile.length()} bytes")
            _isPaused.value = true
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Secure update download failed", e)
            _downloadError.value = e.message ?: "Не удалось проверить обновление"
            _downloadProgress.value = null
            _downloadStatus.value = null
        } finally {
            _isDownloading.value = false
        }
    }

    private fun normalizePartialFile(target: File, expectedBytes: Long) {
        if (expectedBytes > 0L && target.length() > expectedBytes) {
            Log.w(TAG, "Discarding oversized partial APK: ${target.length()} > $expectedBytes")
            target.delete()
        }
    }

    private fun ensureDownloadAllowed(
        context: Context,
        updateInfo: UpdateInfo,
        partialBytes: Long
    ) {
        val prefs = PreferencesManager(context)
        if (prefs.updateWifiOnly && !isWifiConnected(context)) {
            throw IllegalStateException("Загрузка обновлений разрешена только по Wi‑Fi")
        }

        val availableBytes = StatFs(context.cacheDir.absolutePath).availableBytes
        if (!UpdateDownloadPolicy.hasEnoughSpace(availableBytes, updateInfo.fileSize, partialBytes)) {
            val requiredMb = UpdateDownloadPolicy
                .requiredFreeBytes(updateInfo.fileSize, partialBytes)
                .let { (it + 1024L * 1024L - 1L) / (1024L * 1024L) }
            throw IllegalStateException(
                "Недостаточно свободного места. Освободите не менее $requiredMb МБ"
            )
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun downloadToFile(updateInfo: UpdateInfo, target: File): Long {
        val expectedBytes = updateInfo.fileSize
        val existingBytes = target.length()
        val rangeStart = UpdateDownloadPolicy.requestRangeStart(existingBytes, expectedBytes)
        val requestBuilder = Request.Builder()
            .url(updateInfo.downloadUrl)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Nimbo-Android/${BuildConfig.VERSION_NAME}")
        rangeStart?.let { requestBuilder.header("Range", "bytes=$it-") }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416 && expectedBytes > 0L && target.length() == expectedBytes) {
                _downloadProgress.value = 1f
                _downloadStatus.value = UpdateDownloadProgress(
                    target.length(), expectedBytes, UpdateDownloadStage.VERIFYING
                )
                return target.length()
            }
            if (!response.isSuccessful) throw IllegalStateException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("GitHub returned an empty APK")
            val append = UpdateDownloadPolicy.shouldAppend(response.code, rangeStart)
            if (append && !UpdateDownloadPolicy.hasMatchingContentRange(response.header("Content-Range"), rangeStart)) {
                throw SecurityException("Сервер обновлений вернул неверный диапазон файла")
            }
            val completedBeforeResponse = if (append) existingBytes else 0L
            val responseBytes = body.contentLength().takeIf { it > 0L } ?: 0L
            val totalBytes = UpdateDownloadPolicy.resolvedExpectedBytes(
                metadataBytes = expectedBytes,
                responseBytes = responseBytes.takeIf { it > 0L },
                completedBytes = completedBeforeResponse
            )

            if (rangeStart != null && !append) {
                Log.i(TAG, "Update server ignored Range; restarting APK download")
            }

            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        // Pausing cancels the job; the loop leaves the socket at the
                        // next chunk boundary and the partial file survives.
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (totalBytes > 0L) {
                            val completed = completedBeforeResponse + downloaded
                            _downloadProgress.value =
                                (completed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            _downloadStatus.value = UpdateDownloadProgress(
                                completed,
                                totalBytes,
                                UpdateDownloadStage.DOWNLOADING
                            )
                        }
                    }
                    output.fd.sync()
                }
            }
            return target.length()
        }
    }

    private data class VerifiedApk(val versionName: String, val versionCode: Long)

    private fun verifyDownloadedApk(context: Context, file: File, updateInfo: UpdateInfo): VerifiedApk {
        if (!file.isFile || file.length() <= 0) throw SecurityException("Загруженный APK пуст")
        if (updateInfo.fileSize > 0 && file.length() != updateInfo.fileSize) {
            throw SecurityException(UpdateUiText.APK_SIZE_MISMATCH)
        }

        updateInfo.sha256?.let { expected ->
            val actual = sha256(file)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw SecurityException("SHA-256 APK не совпадает с цифровым отпечатком релиза")
            }
        }

        val archive = packageArchiveInfo(context.packageManager, file)
            ?: throw SecurityException("Загруженный файл не является корректным APK")
        if (archive.packageName != context.packageName) {
            throw SecurityException("APK выпущен для другого приложения")
        }
        val archiveVersionName = archive.versionName.orEmpty()
        if (normalizedVersionTag(archiveVersionName) != normalizedVersionTag(updateInfo.versionName)) {
            throw SecurityException("Версия внутри APK не совпадает с релизом")
        }
        if (updateInfo.versionCode > 0 && archive.longVersionCode != updateInfo.versionCode.toLong()) {
            throw SecurityException("versionCode внутри APK не совпадает с релизом")
        }
        if (archive.longVersionCode < BuildConfig.VERSION_CODE.toLong()) {
            throw SecurityException("Android не разрешает откат на более старый versionCode")
        }

        val installed = installedPackageInfo(context)
        val installedCurrentSigners = currentSignerDigests(installed)
        val archiveLineage = signerLineageDigests(archive)
        if (installedCurrentSigners.isEmpty() || !archiveLineage.containsAll(installedCurrentSigners)) {
            throw SecurityException("Сертификат подписи APK не совпадает с установленным Nimbo")
        }

        return VerifiedApk(archiveVersionName, archive.longVersionCode)
    }

    private fun packageArchiveInfo(packageManager: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun installedPackageInfo(context: Context): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun currentSignerDigests(packageInfo: PackageInfo): Set<String> =
        packageInfo.signingInfo?.apkContentsSigners.orEmpty().mapTo(linkedSetOf()) { sha256(it.toByteArray()) }

    private fun signerLineageDigests(packageInfo: PackageInfo): Set<String> {
        val signingInfo = packageInfo.signingInfo ?: return emptySet()
        val signatures = if (signingInfo.hasPastSigningCertificates()) {
            signingInfo.signingCertificateHistory
        } else {
            signingInfo.apkContentsSigners
        }
        return signatures.orEmpty().mapTo(linkedSetOf()) { sha256(it.toByteArray()) }
    }

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun recordPendingInstallation(
        context: Context,
        updateInfo: UpdateInfo,
        verified: VerifiedApk
    ) {
        val packageTime = installedPackageInfo(context).lastUpdateTime
        PreferencesManager(context).apply {
            pendingUpdateArtifactId = updateInfo.artifactId
            pendingUpdateVersionName = verified.versionName
            pendingUpdateVersionCode = verified.versionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pendingUpdatePackageTime = packageTime
            pendingUpdateStartedAt = System.currentTimeMillis()
            pendingUpdateChangelog = updateInfo.changelog
            pendingUpdateReleaseUrl = updateInfo.releaseUrl
        }
    }

    /** Promotes a pending artifact only after Android actually replaced the package. */
    fun confirmPendingInstallation(context: Context) {
        val prefs = PreferencesManager(context)
        val artifactId = prefs.pendingUpdateArtifactId ?: return
        val installed = runCatching { installedPackageInfo(context) }.getOrNull() ?: return
        val expectedName = prefs.pendingUpdateVersionName.orEmpty()
        val confirmed = UpdatePostInstallPolicy.isConfirmed(
            expectedVersionName = expectedName,
            expectedVersionCode = prefs.pendingUpdateVersionCode,
            previousPackageTime = prefs.pendingUpdatePackageTime,
            installedVersionName = installed.versionName.orEmpty(),
            installedVersionCode = installed.longVersionCode,
            installedPackageTime = installed.lastUpdateTime
        )

        if (confirmed) {
            prefs.installedUpdateArtifactId = artifactId
            prefs.lastUpdateNotifiedArtifactId = artifactId
            prefs.lastInstalledUpdateVersion = expectedName
            prefs.lastInstalledUpdateChangelog = prefs.pendingUpdateChangelog
            prefs.lastInstalledUpdateReleaseUrl = prefs.pendingUpdateReleaseUrl
            prefs.showPostUpdateChangelog = true
            prefs.clearPendingUpdate()
            Log.i(TAG, "Confirmed installed update artifact $artifactId")
        } else if (
            installed.longVersionCode > prefs.pendingUpdateVersionCode &&
            normalizedVersionTag(installed.versionName.orEmpty()) != normalizedVersionTag(expectedName)
        ) {
            prefs.clearPendingUpdate()
        }
    }

    /** Opens Android's atomic package installer. Failed/rejected installs retain the current app. */
    fun installApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Installation launch failed", e)
            _downloadError.value = e.message ?: "Не удалось открыть установщик Android"
        }
    }

    /** Shows one notification per exact asset, including same-version repair uploads. */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo): Boolean {
        val prefs = PreferencesManager(context)
        val identity = updateInfo.artifactId.ifBlank { normalizedVersionTag(updateInfo.versionName) }
        val now = System.currentTimeMillis()
        val sameArtifact = identity == prefs.lastUpdateNotifiedArtifactId
        if (!UpdateNotificationPolicy.shouldPost(
                identity = identity,
                lastIdentity = prefs.lastUpdateNotifiedArtifactId,
                kind = updateInfo.kind,
                lastNotifiedAt = prefs.lastUpdateNotificationTime,
                now = now,
                notifiedCount = if (sameArtifact) prefs.updateNotificationCount else 0,
                skippedIdentity = prefs.updateDialogSkippedArtifactId
            )
        ) {
            Log.d(TAG, "Artifact notification is not due yet. Skipping.")
            return true
        }

        // While Nimbo is open, the update screen and foreground check show the
        // state directly. Keep the artifact unconsumed so the same update is
        // still delivered after the user leaves the app.
        if (AppVisibilityTracker.isForeground) {
            Log.d(TAG, "Update found while app is foreground; deferring system notification")
            return true
        }

        val isEnglish = prefs.appLanguage == "en"
        val displayVersion = "v${normalizedVersionTag(updateInfo.versionName)}"
        val title = when {
            updateInfo.kind == UpdateKind.REPAIR && isEnglish -> "Additional update available"
            updateInfo.kind == UpdateKind.REPAIR -> "Доступно дополнительное обновление"
            isEnglish -> "Update available"
            else -> "Доступно обновление"
        }
        val updatedDate = formatAssetDate(updateInfo.assetUpdatedAt)
        val content = when {
            updateInfo.kind == UpdateKind.REPAIR && isEnglish ->
                "An additional update for Nimbo $displayVersion was released" +
                    "${updatedDate?.let { " on $it" }.orEmpty()} with fixes and improvements. Tap to install."
            updateInfo.kind == UpdateKind.REPAIR ->
                "Для Nimbo $displayVersion выпущено дополнительное обновление" +
                    "${updatedDate?.let { " от $it" }.orEmpty()} с исправлениями и улучшениями. Нажмите, чтобы установить."
            isEnglish -> "$displayVersion is available. Tap to see what's new."
            else -> "Версия $displayVersion доступна. Нажмите, чтобы узнать, что нового."
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                if (isEnglish) "App updates" else "Обновления приложения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (isEnglish) "Notifications about new Nimbo versions"
                else "Уведомления о новых версиях Nimbo"
            }
        )

        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = notificationManager.getNotificationChannel(CHANNEL_ID)?.importance
            ?.let { it != NotificationManager.IMPORTANCE_NONE }
            ?: false
        if (!UpdateNotificationPolicy.canPost(
                permissionGranted = permissionGranted,
                appNotificationsEnabled = appNotificationsEnabled,
                channelEnabled = channelEnabled
            )
        ) {
            Log.w(
                TAG,
                "Update notification deferred: permission=$permissionGranted, " +
                    "appEnabled=$appNotificationsEnabled, channelEnabled=$channelEnabled"
            )
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_SCREEN", "updates")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification_nimbo_blue)
            .setLargeIcon(CustomAppIconManager.notificationLargeIcon(context))
            .setColor(0xFF2869D4.toInt())
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSubText(if (isEnglish) "Nimbo update" else "Обновление Nimbo")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // The same notification slot replaces an obsolete card, but a newly
            // uploaded artifact must still make sound/vibration instead of being
            // silently folded into the old release notification.
            .setOnlyAlertOnce(false)
            .setWhen(now)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.icon_notification_nimbo_blue,
                if (isEnglish) "Update" else "Обновить",
                pendingIntent
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
        // Record delivery only after Android accepted the notification call. If the
        // permission/app/channel is blocked, the next background run must retry it.
        prefs.lastUpdateNotifiedArtifactId = identity
        prefs.lastUpdateNotifiedVersion = normalizedVersionTag(updateInfo.versionName)
        // Счётчик нужен политике напоминаний: у той же сборки их ограниченное число.
        prefs.updateNotificationCount = if (sameArtifact) prefs.updateNotificationCount + 1 else 1
        prefs.lastUpdateNotificationTime = now
        return true
    }

    internal fun notificationSummary(changelog: String): String = changelog
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
        .orEmpty()
        .trimStart('-', '*', '•', ' ')
        .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
        .replace("**", "")
        .trim()
        .take(180)

    private fun formatAssetDate(value: String?): String? = runCatching {
        value?.takeIf(String::isNotBlank)?.let {
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(it))
        }
    }.getOrNull()
}
