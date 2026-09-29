package com.danila.nimbo.ui.screens

import android.content.Context
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.danila.nimbo.mihomo.MihomoBridge
import com.danila.nimbo.mihomo.MihomoException
import com.danila.nimbo.mihomo.MihomoProfiles
import com.danila.nimbo.mihomo.MihomoProtocol
import com.danila.nimbo.mihomo.MihomoSubscriptionFetcher
import com.danila.nimbo.mihomo.MihomoSubscriptionTemplates
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.NativeMihomoDocument
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.service.SubscriptionUpdateEvents
import com.danila.nimbo.ui.components.LocalFloatingNavHeight
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.MihomoManager
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.DateFormat
import java.util.Date

/** A status and its generation must come from ONE reply, never latestGeneration. */
internal data class MihomoUiSession(
    val generation: Long,
    val sourceHash: String,
    val state: String,
    val tunReady: Boolean,
) {
    fun controls(source: String): Boolean = source.isNotBlank() && sourceHash == source &&
        generation > 0 && state == "running" && tunReady

    fun sameRunningSession(other: MihomoUiSession): Boolean =
        controls(other.sourceHash) && other.controls(sourceHash) && generation == other.generation

    fun protects(source: String): Boolean = source.isNotBlank() && sourceHash == source && state != "stopped"
}

internal fun mihomoCanEnableLanProxy(conflicts: List<String>?, disconnected: Boolean, vpnDesired: Boolean): Boolean =
    conflicts == listOf("direct LAN bypass") && disconnected && !vpnDesired

internal data class MihomoUiProfile(
    val profile: SubscriptionProfile,
    val sourceHash: String,
    val inspection: JsonObject?,
    val error: String? = null,
    val lastUpdatedAt: Long = 0L,
    val parent: SubscriptionProfile? = null,
)

private fun mihomoRefreshLabel(profile: SubscriptionProfile, lastUpdatedAt: Long): String =
    when {
        profile.error?.startsWith("MIHOMO_") == true -> "Ошибка обновления: ${profile.error}"
        profile.mihomoSourceUrl != null -> buildString {
            append("HTTPS · ")
            append(profile.mihomoSourceUrl.toHttpUrlOrNull()?.host.orEmpty().ifBlank { "источник" })
            if (lastUpdatedAt > 0L) {
                append(" · обновлено ")
                append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastUpdatedAt)))
            }
        }
        else -> "Локальный YAML"
    }

internal data class MihomoUiRuntime(val session: MihomoUiSession, val snapshot: JsonObject)

private fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
private fun JsonObject.strings(key: String): List<String> = getAsJsonArray(key)?.mapNotNull {
    it.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }?.asString
}.orEmpty()

private fun readMihomoStatus(): MihomoUiSession {
    val reply = MihomoBridge.response("status")
    return MihomoUiSession(reply.generation, reply.data.text("sourceSHA256"), reply.data.text("state"),
        reply.data["tunReady"]?.asBoolean == true)
}

private fun safeMihomoUiCode(error: Exception): String {
    if (error is CancellationException) throw error
    return if (error is MihomoException && Regex("[A-Z][A-Z0-9_]{0,79}").matches(error.code))
        error.code else "UI_OPERATION_FAILED"
}

/** Only this controller publishes Compose state; native and storage work stays on IO. */
internal class MihomoProfilesUi(private val context: Context) {
    var available by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var profiles by mutableStateOf<List<MihomoUiProfile>>(emptyList())
        private set
    var status by mutableStateOf<MihomoUiSession?>(null)
        private set
    var runtime by mutableStateOf<MihomoUiRuntime?>(null)
        private set
    var selectedUrl by mutableStateOf(VpnManager.selectedServer?.profileUrl)
        private set
    var error by mutableStateOf<String?>(null)
    var templateFailures by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var delayResult by mutableStateOf<Pair<String, Long>?>(null)
        private set
    var autoPickResult by mutableStateOf<Pair<String, Long>?>(null)
        private set
    var conflicts by mutableStateOf<List<String>?>(null)
        private set
    var vpnDesired by mutableStateOf(true)
        private set
    private val mutex = Mutex()

    suspend fun perform(action: suspend () -> Unit) {
        mutex.withLock {
            busy = true
            error = null
            try { action() } catch (e: Exception) {
                error = safeMihomoUiCode(e)
            } finally { busy = false }
        }
    }

    suspend fun load(discoverMissing: Boolean = true) = perform {
        try {
            refreshCompatibility()
            available = withContext(Dispatchers.IO) { MihomoBridge.available() }
            if (discoverMissing && available && !vpnDesired && VpnManager.state.value == VpnState.DISCONNECTED)
                syncSubscriptions(onlyMissing = true)
            reloadProfiles()
            refreshRuntime()
        } catch (e: Exception) {
            status = null
            runtime = null
            delayResult = null
            throw e
        } finally { loaded = true }
    }

    suspend fun refreshSubscriptions() = perform {
        syncSubscriptions(onlyMissing = false)
        reloadProfiles()
        refreshRuntime()
    }

    private suspend fun syncSubscriptions(onlyMissing: Boolean) {
        val failures = withContext(Dispatchers.IO) {
            val preferences = PreferencesManager(context)
            fun canWrite() = !preferences.vpnConnectionDesired && VpnManager.state.value == VpnState.DISCONNECTED
            if (!canWrite()) throw MihomoException("PROFILE_ACTIVE")
            val failures = mutableListOf<Pair<String, String>>()
            val initial = preferences.loadProfiles()
            var changed = false
            for (parent in initial.filterNot(MihomoProfiles::isMihomo).filter { it.url.startsWith("https://", true) }) {
                try {
                    val endpoint = MihomoSubscriptionFetcher.templateUrl(parent.url)
                    val source = MihomoSubscriptionTemplates(endpoint, emptyList())
                    val previous = initial.filter { source.belongsTo(it, parent) }
                    if (onlyMissing && previous.isNotEmpty()) continue
                    val templates = MihomoSubscriptionFetcher.discover(parent.url, includeOriginal = false)
                    preferences.updateProfiles { latest ->
                        if (!canWrite()) throw MihomoException("PROFILE_ACTIVE")
                        val current = latest.firstOrNull { it.url == parent.url }
                        if (current != parent || latest.filter { source.belongsTo(it, parent) } != previous)
                            throw MihomoException("STALE_PROFILE")
                        templates.mergeInto(latest, current).also { changed = changed || it != latest }
                    }
                    preferences.setLastSubscriptionUpdateTime(templates.sourceUrl, System.currentTimeMillis())
                    preferences.setSubscriptionUpdateInterval(templates.sourceUrl, parent.autoUpdateInterval)
                } catch (error: Exception) {
                    failures += parent.displayName.take(120) to safeMihomoUiCode(error)
                }
            }
            if (changed) SubscriptionUpdateEvents.notifyProfilesChanged()
            failures
        }
        templateFailures = failures
    }

    private suspend fun reloadProfiles() {
        val canInspect = available
        val preferences = PreferencesManager(context)
        profiles = withContext(Dispatchers.IO) {
            val parents = preferences.loadProfiles().associateBy { it.url }
            MihomoProfiles.list(context).filter(MihomoProfiles::isMihomo).map { profile ->
                val parent = profile.mihomoParentUrl?.let(parents::get)
                val yaml = profile.rawConfig.orEmpty()
                val hash = MihomoProtocol.sourceHash(yaml)
                val sourceKey = profile.mihomoSourceUrl?.trim()?.takeIf(String::isNotBlank) ?: profile.url
                val lastUpdatedAt = preferences.getLastSubscriptionUpdateTime(sourceKey)
                try {
                    if (!canInspect) MihomoUiProfile(profile, hash, null, lastUpdatedAt = lastUpdatedAt, parent = parent) else {
                        val inspection = MihomoBridge.inspect(yaml)
                        val admission = runCatching { MihomoBridge.preflight(yaml) }
                            .exceptionOrNull()?.let { safeMihomoUiCode(it as? Exception ?: Exception()) }
                        MihomoUiProfile(profile, hash, inspection, admission, lastUpdatedAt, parent)
                    }
                } catch (e: Exception) {
                    MihomoUiProfile(profile, hash, null, safeMihomoUiCode(e), lastUpdatedAt, parent)
                }
            }
        }
        selectedUrl = VpnManager.selectedServer?.profileUrl
    }

    suspend fun poll() = mutex.withLock {
        try {
            refreshCompatibility()
            refreshRuntime()
        } catch (e: Exception) {
            status = null
            runtime = null
            delayResult = null
            error = safeMihomoUiCode(e)
        }
    }

    private suspend fun refreshCompatibility() {
        try {
            val (currentConflicts, desired) = withContext(Dispatchers.IO) {
                MihomoManager.compatibilityConflicts(context) to PreferencesManager(context).vpnConnectionDesired
            }
            conflicts = currentConflicts
            vpnDesired = desired
        } catch (e: Exception) {
            conflicts = null
            vpnDesired = true
            throw e
        }
    }

    /** Only called after explicit confirmation, on Main, with fresh checks and no suspension before the write. */
    fun enableLanProxy() {
        val preferences = PreferencesManager(context)
        conflicts = MihomoManager.compatibilityConflicts(context)
        vpnDesired = preferences.vpnConnectionDesired
        if (!mihomoCanEnableLanProxy(conflicts, VpnManager.state.value == VpnState.DISCONNECTED, vpnDesired))
            throw MihomoException("SETTINGS_CHANGED")
        preferences.lanThroughProxy = true
        conflicts = MihomoManager.compatibilityConflicts(context)
    }

    private suspend fun refreshRuntime() {
        if (!available) { status = null; runtime = null; return }
        val hashes = profiles.map { it.sourceHash }.toSet()
        val (fresh, live) = withContext(Dispatchers.IO) {
            val first = readMihomoStatus()
            if (!first.controls(first.sourceHash) || first.sourceHash !in hashes) first to null
            else {
                val snapshot = MihomoBridge.response("snapshot", generation = first.generation)
                val last = readMihomoStatus()
                if (!first.sameRunningSession(last) || snapshot.generation != first.generation)
                    throw MihomoException("STALE_GENERATION")
                last to MihomoUiRuntime(last, snapshot.data)
            }
        }
        if (runtime?.session != live?.session) {
            delayResult = null
            autoPickResult = null
        }
        status = fresh
        runtime = live
    }

    suspend fun importYaml(name: String, yaml: String) {
        val documents = withContext(Dispatchers.IO) {
            SubscriptionManager.detectNativeMihomoDocuments(yaml)
        }.ifEmpty { throw MihomoException("INVALID_YAML") }
        val fallback = name.ifBlank { "Mihomo" }
        withContext(Dispatchers.IO) {
            MihomoProfiles.importDocuments(context, null, documents.map { it.withFallbackName(fallback) })
        }
        reloadProfiles()
    }

    suspend fun importUrl(name: String, url: String) {
        withContext(Dispatchers.IO) {
            val templates = MihomoSubscriptionFetcher.discover(url)
            val fallback = name.ifBlank { "Mihomo" }
            MihomoProfiles.importDocuments(context, templates.sourceUrl, templates.documents.map { it.withFallbackName(fallback) })
            PreferencesManager(context).setLastSubscriptionUpdateTime(templates.sourceUrl, System.currentTimeMillis())
        }
        reloadProfiles()
    }

    suspend fun refreshUrl(row: MihomoUiProfile) {
        val url = row.profile.mihomoSourceUrl ?: throw MihomoException("INVALID_SUBSCRIPTION_URL")
        if (protectedByApp(row.profile)) throw MihomoException("ACTIVE_PROFILE")
        withContext(Dispatchers.IO) {
            val currentSourceProfiles = MihomoProfiles.list(context).filter { it.mihomoSourceUrl == url }
            val expectedHashes = currentSourceProfiles.associate {
                (it.mihomoDocumentId ?: "default") to MihomoProtocol.sourceHash(it.rawConfig.orEmpty())
            }
            val original = MihomoSubscriptionFetcher.fetch(url)
            val documents = SubscriptionManager.detectNativeMihomoDocuments(original)
                .ifEmpty { throw MihomoException("INVALID_YAML") }
            // Keep the last working source intact if this new revision is not
            // admitted by the current Android adapter. The profile remains
            // inspectable; connection support can expand independently later.
            documents.forEach { document ->
                MihomoBridge.inspect(document.source)
                MihomoBridge.preflight(document.source)
            }
            MihomoProfiles.importDocuments(context, url, documents.map {
                it.withFallbackName(row.parent?.displayName ?: row.profile.name)
            },
                expectedSourceHashes = expectedHashes)
            PreferencesManager(context).setLastSubscriptionUpdateTime(url, System.currentTimeMillis())
        }
        reloadProfiles()
    }

    private fun NativeMihomoDocument.withFallbackName(fallback: String): NativeMihomoDocument =
        if (name.startsWith("Mihomo ")) copy(name = fallback.trim().take(120).ifBlank { "Mihomo" }) else this

    suspend fun select(profile: SubscriptionProfile) {
        // The store updates the selected marker on Main; it must never start a connection.
        withContext(Dispatchers.Main.immediate) { MihomoProfiles.select(context, profile) }
        selectedUrl = profile.url
    }

    fun protectedByApp(profile: SubscriptionProfile): Boolean =
        VpnManager.state.value == VpnState.CONNECTING ||
            (VpnManager.state.value != VpnState.DISCONNECTED && VpnManager.connectedServer.value?.profileUrl == profile.url)

    suspend fun remove(row: MihomoUiProfile) {
        if (protectedByApp(row.profile)) throw MihomoException("ACTIVE_PROFILE")
        // Fail closed if status is unavailable; the store also checks under its own mutation guard.
        withContext(Dispatchers.IO) {
            val fresh = readMihomoStatus()
            if (fresh.protects(row.sourceHash) || fresh.state !in setOf("stopped", "running"))
                throw MihomoException("ACTIVE_PROFILE")
            MihomoProfiles.remove(context, row.profile.url)
        }
        reloadProfiles()
        refreshRuntime()
    }

    suspend fun measureDelays(expected: MihomoUiSession, names: List<String>, onResult: (String, Long?) -> Unit) {
        for (name in names) {
            try {
                control(expected, "delay", JsonObject().apply {
                    addProperty("name", name); addProperty("url", "https://www.gstatic.com/generate_204")
                    addProperty("timeoutMs", 4000); addProperty("expectedStatus", "204")
                })
                onResult(name, delayResult?.second)
            } catch (error: MihomoException) {
                if (error.code != "DELAY_FAILED") throw error
                onResult(name, null)
                refreshRuntime()
                if (runtime?.session?.sameRunningSession(expected) != true) throw MihomoException("STALE_GENERATION")
            }
        }
    }

    /** Use the already-running graph for Nimbo Ping so its DNS and outbound
     * match the working VPN connection; do not change the selected node. */
    suspend fun nimboDelay(expected: MihomoUiSession, name: String, url: String, timeoutMs: Int): Int =
        withContext(Dispatchers.IO) {
            if (!expected.sameRunningSession(readMihomoStatus())) throw MihomoException("STALE_GENERATION")
            val reply = MihomoBridge.response("nimboDelay", generation = expected.generation,
                fields = JsonObject().apply {
                    addProperty("name", name)
                    addProperty("url", url)
                    addProperty("timeoutMs", timeoutMs)
                    addProperty("expectedStatus", "200-299")
                })
            if (reply.generation != expected.generation || !expected.sameRunningSession(readMihomoStatus()))
                throw MihomoException("STALE_GENERATION")
            reply.data["delayMs"]?.asInt ?: throw MihomoException("INVALID_RESPONSE")
        }

    suspend fun control(expected: MihomoUiSession, operation: String, fields: JsonObject) {
        runtime = null // No further action uses a snapshot while an operation is outstanding.
        delayResult = null
        autoPickResult = null
        try {
            val result = withContext(Dispatchers.IO) {
                if (!expected.sameRunningSession(readMihomoStatus())) throw MihomoException("STALE_GENERATION")
                val reply = MihomoBridge.response(operation, generation = expected.generation, fields = fields)
                if (reply.generation != expected.generation || !expected.sameRunningSession(readMihomoStatus()))
                    throw MihomoException("STALE_GENERATION")
                reply.data
            }
            refreshRuntime()
            if (runtime?.session?.sameRunningSession(expected) != true) throw MihomoException("STALE_GENERATION")
            if (operation == "delay") delayResult = fields.text("name") to result["delayMs"].asLong
            if (operation == "autoSelect") autoPickResult = result["selected"].asString to result["delayMs"].asLong
        } catch (e: Exception) { status = null; runtime = null; throw e }
    }
}

internal fun readMihomoDocument(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MihomoProtocol.MAX_SOURCE_BYTES)
                throw MihomoException("INVALID_YAML_SIZE_OR_ENCODING")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw MihomoException("DOCUMENT_READ_FAILED")
    return try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            .also(MihomoProtocol::checkSource)
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw MihomoException("INVALID_YAML_SIZE_OR_ENCODING")
    }
}

@Composable
internal fun MihomoProfileCard(
    profile: SubscriptionProfile, lastUpdatedAt: Long = 0L,
    selected: Boolean, active: Boolean, expanded: Boolean,
    busy: Boolean, canSelect: Boolean, canRemove: Boolean, onExpand: () -> Unit,
    onSelect: () -> Unit, onExport: () -> Unit, onRemove: () -> Unit,
    admissionCode: String? = null,
    onRefresh: (() -> Unit)? = null,
    details: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalNebulaColors.current
    Surface(onClick = onExpand, modifier = Modifier.fillMaxWidth().testTag("mihomo-profile-card"),
        shape = RoundedCornerShape(16.dp), color = colors.panelFill,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.panelBorder)) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(profile.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        Text(mihomoRefreshLabel(profile, lastUpdatedAt), style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary)
        Text(when {
            active && selected -> t("Mihomo YAML · активен · выбран", "Mihomo YAML · active · selected")
            active -> t("Mihomo YAML · активен", "Mihomo YAML · active")
            selected -> t("Mihomo YAML · выбран", "Mihomo YAML · selected")
            else -> t("Mihomo YAML · не выбран", "Mihomo YAML · not selected")
        }, style = MaterialTheme.typography.labelMedium, color = colors.accent)
        if (admissionCode != null) Text(t("YAML сохранён, но текущий Android-адаптер не поддерживает эту конфигурацию",
            "YAML is preserved, but this configuration is outside the current Android adapter"),
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(if (expanded) t("Скрыть группы", "Hide groups") else t("Показать группы", "Show groups"),
            modifier = Modifier.testTag("mihomo-profile-expand"), color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = onSelect, enabled = !busy && canSelect && !selected,
            modifier = Modifier.fillMaxWidth().testTag("mihomo-profile-select"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)) {
            Text(if (selected) t("Выбран", "Selected") else t("Выбрать без подключения", "Select without connecting"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onExport, enabled = !busy, modifier = Modifier.testTag("mihomo-profile-export"),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent)) { Text(t("Экспорт YAML", "Export YAML")) }
            TextButton(onClick = onRemove, enabled = !busy && canRemove && !active,
                modifier = Modifier.testTag("mihomo-profile-remove"),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)) { Text(t("Удалить", "Remove")) }
        }
        if (onRefresh != null) TextButton(onClick = onRefresh,
            enabled = !busy && !active, modifier = Modifier.testTag("mihomo-profile-refresh"),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent)) {
            Text(t("Обновить YAML по ссылке", "Refresh YAML from link"))
        }
        if (expanded) details()
      }
    }
}

