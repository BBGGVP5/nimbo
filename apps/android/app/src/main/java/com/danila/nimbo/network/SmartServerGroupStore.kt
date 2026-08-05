package com.danila.nimbo.network

import android.content.Context
import com.danila.nimbo.model.SmartServerGroup
import com.danila.nimbo.model.SmartServerHealth
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SmartServerGroupStore {
    private const val KEY_GROUPS = "smart_server_groups_v1"
    private const val KEY_HEALTH = "smart_server_health_v1"
    private val gson = Gson()

    fun groups(context: Context): List<SmartServerGroup> = decodeList(
        PreferencesManager(context).getString(KEY_GROUPS, null)
    )

    fun saveGroup(context: Context, group: SmartServerGroup) {
        val current = groups(context).toMutableList()
        val index = current.indexOfFirst { it.id == group.id }
        if (index >= 0) current[index] = group else current += group
        PreferencesManager(context).setString(KEY_GROUPS, gson.toJson(current))
    }

    fun deleteGroup(context: Context, groupId: String) {
        PreferencesManager(context).setString(
            KEY_GROUPS,
            gson.toJson(groups(context).filterNot { it.id == groupId })
        )
    }

    fun health(context: Context): Map<String, SmartServerHealth> {
        val raw = PreferencesManager(context).getString(KEY_HEALTH, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<List<SmartServerHealth>>(
                raw,
                object : TypeToken<List<SmartServerHealth>>() {}.type
            ).orEmpty().associateBy { it.serverKey }
        }.getOrDefault(emptyMap())
    }

    fun recordResult(
        context: Context,
        serverKey: String,
        latencyMs: Int?,
        success: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val map = health(context).toMutableMap()
        val old = map[serverKey] ?: SmartServerHealth(serverKey)
        val sample = if (success) 1.0 else 0.0
        map[serverKey] = old.copy(
            latencyMs = latencyMs ?: old.latencyMs,
            successRate = old.successRate * 0.8 + sample * 0.2,
            consecutiveFailures = if (success) 0 else old.consecutiveFailures + 1,
            lastFailureAtMs = if (success) old.lastFailureAtMs else nowMs,
            lastSuccessAtMs = if (success) nowMs else old.lastSuccessAtMs
        )
        PreferencesManager(context).setString(KEY_HEALTH, gson.toJson(map.values.toList()))
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<T>>(raw, object : TypeToken<List<T>>() {}.type).orEmpty()
        }.getOrDefault(emptyList())
    }
}
