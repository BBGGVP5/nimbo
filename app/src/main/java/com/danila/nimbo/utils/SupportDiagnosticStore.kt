package com.danila.nimbo.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.vpn.VpnManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest

/** Only already-redacted snapshots reach disk. Export does not fetch subscriptions. */
internal object SupportDiagnosticStore {
    private val lock = Any()
    private fun folder(context: Context) = File(context.cacheDir, "support-snapshots").apply { mkdirs() }
    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun captureConfig(context: Context, core: String, configJson: String) {
        val safe = boundedSupportSnapshot(SupportDiagnosticRedactor.config(configJson))
        synchronized(lock) {
            File(folder(context), "config-${if (core == "mihomo") "mihomo" else "xray"}.json").writeText(safe, Charsets.UTF_8)
        }
    }

    fun captureHeaders(context: Context, url: String, headers: Map<String, List<String>>) {
        val safe = boundedSupportSnapshot(SupportDiagnosticRedactor.headers(headers))
        synchronized(lock) {
            val dir = folder(context)
            File(dir, "headers-${key(url)}.json").writeText(safe, Charsets.UTF_8)
            dir.listFiles()?.filter { it.name.startsWith("headers-") }?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
        }
    }

    /** Call on IO; return files in a unique cache folder, never auto-upload them. */
    fun export(context: Context): List<File> = synchronized(lock) {
        val prefs = PreferencesManager(context)
        val protocol = VpnManager.selectedServer?.protocol.orEmpty().lowercase(java.util.Locale.ROOT)
        val selectedCore = when {
            protocol in setOf("wireguard", "amneziawg", "awg", "wg") -> "amneziawg"
            protocol == "mihomo" || prefs.vpnCore == "mihomo" -> "mihomo"
            else -> "xray"
        }
        val runningCores = buildList {
            if (com.danila.nimbo.vpn.MihomoManager.isConnected) add("mihomo")
            if (com.danila.nimbo.vpn.AmneziaWgManager.isConnected) add("amneziawg")
            if (com.danila.nimbo.vpn.XrayManager.isConnected) add("xray")
        }
        val runningCore = runningCores.singleOrNull() ?: if (
            runningCores.isEmpty() && VpnManager.state.value == com.danila.nimbo.vpn.VpnState.DISCONNECTED
        ) "none" else "unknown"
        val snapshotCore = runningCores.singleOrNull() ?: selectedCore
        val snapshots = folder(context)
        val dir = File(context.cacheDir, "support-reports/${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val report = JsonObject().apply {
            addProperty("reportVersion", 1)
            addProperty("appVersion", BuildConfig.VERSION_NAME)
            addProperty("versionCode", BuildConfig.VERSION_CODE)
            addProperty("xrayBuildVersion", BuildConfig.LIBXRAY_VERSION)
            addProperty("androidSdk", android.os.Build.VERSION.SDK_INT)
            addProperty("vpnState", VpnManager.state.value.name)
            addProperty("selectedCore", selectedCore)
            addProperty("selectedCoreScope", "Inferred from selected server protocol and core preference; not a running-core measurement.")
            addProperty("runningCore", runningCore)
            addProperty("runningCoreScope", "Manager connection flags at capture time; unknown if absent or conflicting while connecting/connected.")
            addProperty("configSnapshotCore", snapshotCore)
            try {
                add("liveUpdate", com.google.gson.Gson().toJsonTree(VpnLiveUpdatePlatform.snapshot(context))) // fixed enums and booleans only
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional platform metadata is unavailable; never include exception text.
            }
            com.danila.nimbo.network.NimboPingDiagnostics.latest.value?.let {
                add("latestPing", com.google.gson.Gson().toJsonTree(it)) // fixed enum vocabulary; no node identifiers
            }
            addProperty("subscriptionCount", prefs.loadProfiles().size)
            addProperty("capturedAtMs", System.currentTimeMillis())
            add("logEventCounts", JsonObject().apply {
                Logger.logEntries.value.groupingBy { it.level.name }.eachCount().forEach { (level, count) -> addProperty(level, count) }
            })
            addProperty("privacy", "Free text, node identifiers, addresses, links and credentials are omitted.")
            addProperty("configScope", "Last prepared configuration; may predate current settings. No connection is started by export.")
        }
        val config = File(snapshots, "config-$snapshotCore.json")
        val profileUrl = VpnManager.selectedServer?.profileUrl
        val profile = prefs.loadProfiles().firstOrNull { it.url == profileUrl }
        val headers = listOfNotNull(profile?.mihomoSourceUrl, profileUrl, profile?.mihomoParentUrl)
            .map { File(snapshots, "headers-${key(it)}.json") }
            .firstOrNull { it.isFile }
        listOf(
            File(dir, "report.json").apply { writeText(boundedSupportSnapshot(GsonBuilder().setPrettyPrinting().create().toJson(report)), Charsets.UTF_8) },
            File(dir, "core-config-redacted.json").apply { writeText(if (config.exists()) readSupportSnapshot(config) else "{\"available\":false}", Charsets.UTF_8) },
            File(dir, "subscription-headers-redacted.json").apply { writeText(if (headers?.exists() == true) readSupportSnapshot(headers) else "{\"available\":false}", Charsets.UTF_8) }
        ).also { runCatching { trimSupportReportCache(dir.parentFile!!) } }
    }

    fun share(context: Context, files: List<File>, title: String) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/json"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Nimbo support report", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
