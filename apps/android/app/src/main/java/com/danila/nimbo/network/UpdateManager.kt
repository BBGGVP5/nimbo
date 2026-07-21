package com.danila.nimbo.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val RELEASES_API_URL = "https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=20"
    private const val COMMIT_API_URL = "https://api.github.com/repos/BBGGVP5/nimbo/commits/"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1003

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError = _downloadError.asStateFlow()

    /** Checks the selected stable/beta channel and compares the exact release asset. */
    suspend fun checkUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesManager(context)
            val channel = prefs.updateChannel
            val releases = fetchReleaseMaps()
            val candidate = releases
                .asSequence()
                .mapNotNull { parseReleaseCandidate(it, Build.SUPPORTED_ABIS.toList()) }
                .firstOrNull { UpdatePolicy.acceptsChannel(it, channel) }
                ?: return@withContext null

            if (prefs.installedUpdateArtifactId == null && candidate.asset.sha256 != null) {
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
            val commitMessage = if (filteredNotes.isBlank()) fetchCommitMessage(candidate.tagName) else null
            val isEnglish = prefs.appLanguage == "en"
            val notes = UpdatePolicy.changelog(filteredNotes, kind, commitMessage, isEnglish)

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
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed via GitHub API", e)
            null
        }
    }

    /** Compatibility entry point for older callers; new code should pass its Context explicitly. */
    suspend fun checkUpdate(): UpdateInfo? = checkUpdate(NebulaGuardApplication.instance)

    private fun fetchReleaseMaps(): List<Map<String, Any?>> {
        val request = githubRequest(RELEASES_API_URL)
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
        .header("Cache-Control", "no-cache")
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
            asset = bestAsset
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
            val normalizedAbi = abi.lowercase().replace("-", "").replace("_", "")
            assets.firstOrNull { asset ->
                val normalizedName = asset.name.lowercase().replace("-", "").replace("_", "")
                normalizedName.contains(normalizedAbi) && normalizedName.endsWith(".apk")
            }?.let { return it }
        }
        return assets.firstOrNull { it.name.contains("universal", true) && it.name.endsWith(".apk", true) }
            ?: assets.firstOrNull { it.name.endsWith(".apk", true) }
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

    /** Keeps shared changes and Android APK details while hiding desktop-only release lines. */
    internal fun releaseNotesForAndroid(releaseBody: String): String {
        var pendingAssetHeading: String? = null
        val visibleLines = mutableListOf<String>()

        releaseBody.lineSequence().forEach { line ->
            if (isReleaseAssetHeading(line)) {
                pendingAssetHeading = line
                return@forEach
            }
            if (isDesktopOnlyReleaseLine(line)) return@forEach
            if (pendingAssetHeading != null && line.isBlank()) return@forEach

            pendingAssetHeading?.let { heading ->
                if (isAndroidReleaseLine(line)) visibleLines += heading
                pendingAssetHeading = null
            }
            visibleLines += line
        }

        return visibleLines.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
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

    private fun isDesktopOnlyReleaseLine(line: String): Boolean {
        val value = line.lowercase()
        if (isAndroidReleaseLine(line)) return false
        return listOf(".exe", ".msi", ".dmg", "appimage", "windows", "win32", "win64", "macos", "mac os")
            .any(value::contains)
    }

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
                UpdateInfo(
                    versionCode = 0,
                    versionName = tagName,
                    downloadUrl = "",
                    changelog = releaseNotesForAndroid(release["body"] as? String ?: ""),
                    publishDate = release["published_at"] as? String,
                    releaseUrl = release["html_url"] as? String
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch release info for tag: $tag", e)
            null
        }
    }

    /** Downloads into a temporary file and opens the installer only after every available check passes. */
    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext

        _isDownloading.value = true
        _downloadProgress.value = 0.01f
        _downloadError.value = null

        val identityHash = Integer.toHexString(updateInfo.artifactId.hashCode())
        val verifiedFile = File(context.cacheDir, "Nimbo_update_$identityHash.apk")
        val partialFile = File(context.cacheDir, "Nimbo_update_$identityHash.apk.part")

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

            val verified = validation ?: run {
                partialFile.delete()
                downloadToFile(updateInfo, partialFile)
                val checked = verifyDownloadedApk(context, partialFile, updateInfo)
                if (verifiedFile.exists()) verifiedFile.delete()
                if (!partialFile.renameTo(verifiedFile)) {
                    partialFile.copyTo(verifiedFile, overwrite = true)
                    partialFile.delete()
                }
                checked
            }

            recordPendingInstallation(context, updateInfo, verified)
            _downloadProgress.value = 1f
            withContext(Dispatchers.Main) { installApk(context, verifiedFile) }
        } catch (e: Exception) {
            partialFile.delete()
            Log.e(TAG, "Secure update download failed", e)
            _downloadError.value = e.message ?: "Не удалось проверить обновление"
            _downloadProgress.value = null
        } finally {
            _isDownloading.value = false
        }
    }

    private fun downloadToFile(updateInfo: UpdateInfo, target: File) {
        val request = Request.Builder()
            .url(updateInfo.downloadUrl)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Nimbo-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("GitHub returned an empty APK")
            val contentLength = body.contentLength().takeIf { it > 0 } ?: updateInfo.fileSize
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        if (contentLength > 0) {
                            _downloadProgress.value = (total.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                    output.fd.sync()
                }
            }
        }
    }

    private data class VerifiedApk(val versionName: String, val versionCode: Long)

    private fun verifyDownloadedApk(context: Context, file: File, updateInfo: UpdateInfo): VerifiedApk {
        if (!file.isFile || file.length() <= 0) throw SecurityException("Загруженный APK пуст")
        if (updateInfo.fileSize > 0 && file.length() != updateInfo.fileSize) {
            throw SecurityException("Размер APK не совпадает с данными GitHub")
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
        }
    }

    /** Promotes a pending artifact only after Android actually replaced the package. */
    fun confirmPendingInstallation(context: Context) {
        val prefs = PreferencesManager(context)
        val artifactId = prefs.pendingUpdateArtifactId ?: return
        val installed = runCatching { installedPackageInfo(context) }.getOrNull() ?: return
        val expectedName = prefs.pendingUpdateVersionName.orEmpty()
        val versionMatches = normalizedVersionTag(installed.versionName.orEmpty()) == normalizedVersionTag(expectedName)
        val codeMatches = installed.longVersionCode >= prefs.pendingUpdateVersionCode
        val packageWasReplaced = installed.lastUpdateTime > prefs.pendingUpdatePackageTime

        if (versionMatches && codeMatches && packageWasReplaced) {
            prefs.installedUpdateArtifactId = artifactId
            prefs.lastUpdateNotifiedArtifactId = artifactId
            prefs.clearPendingUpdate()
            Log.i(TAG, "Confirmed installed update artifact $artifactId")
        } else if (installed.longVersionCode > prefs.pendingUpdateVersionCode && !versionMatches) {
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
        if (prefs.lastUpdateNotifiedArtifactId == identity) {
            Log.d(TAG, "Already notified about artifact $identity. Skipping.")
            return true
        }

        val isEnglish = prefs.appLanguage == "en"
        val displayVersion = "v${normalizedVersionTag(updateInfo.versionName)}"
        val title = when {
            updateInfo.kind == UpdateKind.REPAIR && isEnglish -> "Version repair available"
            updateInfo.kind == UpdateKind.REPAIR -> "Доступно исправление версии"
            isEnglish -> "Update available"
            else -> "Доступно обновление"
        }
        val updatedDate = formatAssetDate(updateInfo.assetUpdatedAt)
        val summary = notificationSummary(updateInfo.changelog.orEmpty()).ifBlank {
            if (isEnglish) "Bug fixes and stability improvements." else "Исправления ошибок и стабильности."
        }
        val content = when {
            updateInfo.kind == UpdateKind.REPAIR && isEnglish ->
                "$displayVersion was republished${updatedDate?.let { " on $it" }.orEmpty()}. $summary"
            updateInfo.kind == UpdateKind.REPAIR ->
                "Версия $displayVersion опубликована повторно${updatedDate?.let { " $it" }.orEmpty()}. $summary"
            isEnglish -> "$displayVersion is available. Tap to see what's new."
            else -> "Версия $displayVersion доступна. Нажмите, чтобы узнать, что нового."
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                if (isEnglish) "App updates" else "Обновления приложения",
                NotificationManager.IMPORTANCE_DEFAULT
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
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
        // Record delivery only after Android accepted the notification call. If the
        // permission/app/channel is blocked, the next background run must retry it.
        prefs.lastUpdateNotifiedArtifactId = identity
        prefs.lastUpdateNotifiedVersion = normalizedVersionTag(updateInfo.versionName)
        prefs.updateNotificationCount = 1
        prefs.lastUpdateNotificationTime = System.currentTimeMillis()
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
