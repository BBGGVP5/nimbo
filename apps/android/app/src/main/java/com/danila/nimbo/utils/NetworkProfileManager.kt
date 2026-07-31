package com.danila.nimbo.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import com.danila.nimbo.model.NetworkPreset
import com.danila.nimbo.model.Server
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class NetworkPresetType {
    HOME,
    PUBLIC_WIFI,
    ROAMING,
    OTHER
}

object NetworkProfileManager {
    private const val KEY_PRESETS_JSON = "network_presets_json_v1"
    private const val KEY_ACTIVE_PRESET_ID = "network_preset_active_id"
    private const val KEY_AUTO_APPLY = "network_preset_auto_apply_enabled"
    private const val KEY_LAST_APPLIED = "network_preset_last_applied"

    private const val PRESET_HOME = "preset_home"
    private const val PRESET_PUBLIC_WIFI = "preset_public_wifi"
    private const val PRESET_ROAMING = "preset_roaming"

    private val gson = Gson()

    data class ApplyResult(
        val preset: NetworkPreset,
        val selectedServer: Server?
    )

    fun isAutoApplyEnabled(context: Context): Boolean {
        return PreferencesManager(context).getBoolean(KEY_AUTO_APPLY, false)
    }

    fun setAutoApplyEnabled(context: Context, enabled: Boolean) {
        PreferencesManager(context).setBoolean(KEY_AUTO_APPLY, enabled)
    }

    fun getActivePresetId(context: Context): String {
        val prefs = PreferencesManager(context)
        return prefs.getString(KEY_ACTIVE_PRESET_ID, PRESET_HOME) ?: PRESET_HOME
    }

    fun setActivePresetId(context: Context, presetId: String) {
        val prefs = PreferencesManager(context)
        prefs.setString(KEY_ACTIVE_PRESET_ID, presetId)
    }

    fun getPresets(context: Context): List<NetworkPreset> {
        val prefs = PreferencesManager(context)
        val raw = prefs.getString(KEY_PRESETS_JSON, null)
        val loadedRaw = runCatching {
            gson.fromJson<List<NetworkPreset>>(
                raw,
                object : TypeToken<List<NetworkPreset>>() {}.type
            )
        }.getOrNull().orEmpty()
        val loaded = loadedRaw.mapNotNull { preset ->
            runCatching {
                if (preset.id.isBlank() || preset.name.isBlank()) return@runCatching null
                preset.copy(
                    selectedServerKeys = preset.selectedServerKeys.filter { it.isNotBlank() }
                )
            }.getOrNull()
        }

        val defaults = defaultPresets()
        if (loaded.isEmpty()) {
            savePresets(context, defaults)
            return defaults
        }

        val byId = loaded.associateBy { it.id }.toMutableMap()
        defaults.forEach { preset ->
            if (!byId.containsKey(preset.id)) byId[preset.id] = preset
        }
        val merged = byId.values.sortedBy { presetOrder(it.id) }
        savePresets(context, merged)
        return merged
    }

    fun savePreset(context: Context, preset: NetworkPreset) {
        val now = System.currentTimeMillis()
        val updatedPreset = preset.copy(updatedAtMs = now)
        val current = getPresets(context).toMutableList()
        val index = current.indexOfFirst { it.id == updatedPreset.id }
        if (index == -1) {
            current.add(updatedPreset.copy(createdAtMs = now))
        } else {
            current[index] = updatedPreset.copy(createdAtMs = current[index].createdAtMs)
        }
        savePresets(context, current.sortedBy { presetOrder(it.id) })
    }

    fun createCustomPresetFrom(
        context: Context,
        base: NetworkPreset,
        customName: String
    ): NetworkPreset {
        val id = "preset_custom_${System.currentTimeMillis()}"
        val preset = base.copy(
            id = id,
            name = customName.ifBlank { "Новый пресет" },
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
        savePreset(context, preset)
        return preset
    }

    fun deleteCustomPreset(context: Context, presetId: String) {
        if (presetId == PRESET_HOME || presetId == PRESET_PUBLIC_WIFI || presetId == PRESET_ROAMING) return
        val filtered = getPresets(context).filterNot { it.id == presetId }
        savePresets(context, filtered)
        if (getActivePresetId(context) == presetId) {
            setActivePresetId(context, PRESET_HOME)
        }
    }

    fun getPresetById(context: Context, presetId: String): NetworkPreset? {
        return getPresets(context).firstOrNull { it.id == presetId }
    }

    fun detectCurrentPreset(context: Context): NetworkPresetType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkPresetType.OTHER
        val network = cm.activeNetwork ?: return NetworkPresetType.OTHER
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkPresetType.OTHER

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                if (isMetered) NetworkPresetType.PUBLIC_WIFI else NetworkPresetType.HOME
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && isRoaming(context) -> {
                NetworkPresetType.ROAMING
            }
            else -> NetworkPresetType.OTHER
        }
    }

    fun applyPresetById(
        context: Context,
        presetId: String,
        availableServers: List<Server>
    ): ApplyResult? {
        val preset = getPresetById(context, presetId) ?: return null
        val selectedServer = findBestServerForPreset(preset, availableServers)
        applyPreset(context, preset)
        setActivePresetId(context, preset.id)
        return ApplyResult(preset = preset, selectedServer = selectedServer)
    }

    fun applyPresetForCurrentNetworkIfEnabled(
        context: Context,
        availableServers: List<Server>
    ): ApplyResult? {
        if (!isAutoApplyEnabled(context)) return null
        val presetType = detectCurrentPreset(context)
        val preset = getPresets(context).firstOrNull {
            it.type == presetType
        } ?: return null

        val prefs = PreferencesManager(context)
        val last = prefs.getString(KEY_LAST_APPLIED, null)
        if (last == preset.id) {
            val selectedServer = findBestServerForPreset(preset, availableServers)
            return ApplyResult(preset = preset, selectedServer = selectedServer)
        }

        val result = applyPresetById(
            context = context,
            presetId = preset.id,
            availableServers = availableServers
        )
        prefs.setString(KEY_LAST_APPLIED, preset.id)
        return result
    }

    fun applyPreset(context: Context, preset: NetworkPreset) {
        val prefs = PreferencesManager(context)
        prefs.killSwitch = preset.killSwitch
        prefs.autoBypassByNetwork = preset.autoBypassByNetwork
        prefs.pingThroughProxy = preset.pingThroughProxy
        prefs.pingOnStartup = preset.pingOnStartup
        prefs.pingOnUpdate = preset.pingOnUpdate
        prefs.updateSubOnStartup = preset.updateSubOnStartup
        prefs.subscriptionAutoUpdate = preset.subscriptionAutoUpdate
        setActivePresetId(context, preset.id)
    }

    private fun isRoaming(context: Context): Boolean {
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return@runCatching false
            @Suppress("DEPRECATION")
            tm.isNetworkRoaming
        }.getOrDefault(false)
    }

    fun buildServerKey(server: Server): String {
        return server.selectionKey()
    }

    private fun buildLegacyServerKey(server: Server): String {
        val profile = server.profileUrl.orEmpty()
        return "${server.host}|${server.port}|${server.uuid}|$profile"
    }

    private fun findBestServerForPreset(
        preset: NetworkPreset,
        availableServers: List<Server>
    ): Server? {
        val selectedByKeys = if (preset.selectedServerKeys.isNotEmpty()) {
            val keys = preset.selectedServerKeys.toSet()
            availableServers.filter { server ->
                keys.contains(buildServerKey(server)) || keys.contains(buildLegacyServerKey(server))
            }
        } else {
            emptyList()
        }

        val candidates = when {
            selectedByKeys.isNotEmpty() -> selectedByKeys
            !preset.serverHost.isNullOrBlank() -> availableServers.filter { server ->
                server.host.equals(preset.serverHost, ignoreCase = true) &&
                    (preset.serverPort == null || server.port == preset.serverPort) &&
                    (preset.serverUuid.isNullOrBlank() || server.uuid == preset.serverUuid) &&
                    (preset.serverProfileUrl.isNullOrBlank() || server.profileUrl == preset.serverProfileUrl)
            }
            else -> emptyList()
        }
        if (candidates.isEmpty()) return null

        val available = candidates
            .filter { (it.ping ?: -1) >= 0 }
            .sortedBy { it.ping ?: Int.MAX_VALUE }
        if (available.isNotEmpty()) return available.first()

        return candidates
            .sortedBy { it.ping ?: Int.MAX_VALUE }
            .firstOrNull()
    }

    private fun savePresets(context: Context, presets: List<NetworkPreset>) {
        val prefs = PreferencesManager(context)
        prefs.setString(KEY_PRESETS_JSON, gson.toJson(presets))
    }

    private fun defaultPresets(): List<NetworkPreset> {
        return listOf(
            NetworkPreset(
                id = PRESET_HOME,
                name = "Дом",
                type = NetworkPresetType.HOME,
                iconGlyph = "🏠",
                killSwitch = false,
                autoBypassByNetwork = false,
                pingThroughProxy = false,
                pingOnStartup = true,
                pingOnUpdate = true,
                updateSubOnStartup = true,
                subscriptionAutoUpdate = true
            ),
            NetworkPreset(
                id = PRESET_PUBLIC_WIFI,
                name = "Публичный Wi-Fi",
                type = NetworkPresetType.PUBLIC_WIFI,
                iconGlyph = "📶",
                killSwitch = true,
                autoBypassByNetwork = true,
                pingThroughProxy = true,
                pingOnStartup = true,
                pingOnUpdate = true,
                updateSubOnStartup = true,
                subscriptionAutoUpdate = true
            ),
            NetworkPreset(
                id = PRESET_ROAMING,
                name = "Роуминг",
                type = NetworkPresetType.ROAMING,
                iconGlyph = "🌍",
                killSwitch = true,
                autoBypassByNetwork = true,
                pingThroughProxy = false,
                pingOnStartup = false,
                pingOnUpdate = false,
                updateSubOnStartup = false,
                subscriptionAutoUpdate = false
            )
        )
    }

    private fun presetOrder(id: String): Int {
        return when (id) {
            PRESET_HOME -> 0
            PRESET_PUBLIC_WIFI -> 1
            PRESET_ROAMING -> 2
            else -> 100
        }
    }
}

