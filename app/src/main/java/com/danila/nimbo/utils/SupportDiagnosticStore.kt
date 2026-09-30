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
        val safe = SupportDiagnosticRedactor.config(configJson)
        synchronized(lock) {
            File(folder(context), "config-${if (core == "mihomo") "mihomo" else "xray"}.json").writeText(safe)
        }
    }

    fun captureHeaders(context: Context, url: String, headers: Map<String, List<String>>) {
        val safe = SupportDiagnosticRedactor.headers(headers)
        synchronized(lock) {
            val dir = folder(context)
            File(dir, "headers-${key(url)}.json").writeText(safe)
            dir.listFiles()?.filter { it.name.startsWith("headers-") }?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
        }
    }

    /** Call on IO; return files in a unique cache folder, never auto-upload them. */
    fun export(context: Context): List<File> = synchronized(lock) {
        val prefs = PreferencesManager(context)
        val protocol = VpnManager.selectedServer?.protocol.orEmpty().lowercase(java.util.Locale.ROOT)
        val core = when {
            com.danila.nimbo.vpn.MihomoManager.isConnected -> "mihomo"
            com.danila.nimbo.vpn.AmneziaWgManager.isConnected -> "amneziawg"
            protocol in setOf("wireguard", "amneziawg", "awg", "wg") -> "amneziawg"
            protocol == "mihomo" || prefs.vpnCore == "mihomo" -> "mihomo"
            else -> "xray"
        }
        val snapshots = folder(context)
        val dir = File(context.cacheDir, "support-reports/${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val report = JsonObject().apply {
            addProperty("reportVersion", 1)
            addProperty("appVersion", BuildConfig.VERSION_NAME)
            addProperty("androidSdk", android.os.Build.VERSION.SDK_INT)
            addProperty("vpnState", VpnManager.state.value.name)
            addProperty("selectedCore", core)
            runCatching { VpnLiveUpdatePlatform.snapshot(context) }.getOrNull()?.let {
                add("liveUpdate", com.google.gson.Gson().toJsonTree(it)) // fixed enums and booleans only
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
        val config = File(snapshots, "config-$core.json")
        val profileUrl = VpnManager.selectedServer?.profileUrl
        val profile = prefs.loadProfiles().firstOrNull { it.url == profileUrl }
        val headers = listOfNotNull(profile?.mihomoSourceUrl, profileUrl, profile?.mihomoParentUrl)
            .map { File(snapshots, "headers-${key(it)}.json") }
            .firstOrNull { it.isFile }
        listOf(
            File(dir, "report.json").apply { writeText(GsonBuilder().setPrettyPrinting().create().toJson(report)) },
            File(dir, "core-config-redacted.json").apply { writeText(if (config.exists()) config.readText() else "{\"available\":false}") },
            File(dir, "subscription-headers-redacted.json").apply { writeText(if (headers?.exists() == true) headers.readText() else "{\"available\":false}") }
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
