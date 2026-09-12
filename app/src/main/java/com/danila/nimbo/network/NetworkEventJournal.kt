package com.danila.nimbo.network

import android.content.Context
import com.danila.nimbo.model.NetworkEvent
import com.danila.nimbo.model.NetworkEventSeverity
import com.danila.nimbo.model.NetworkEventType
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object NetworkEventJournal {
    private const val KEY_EVENTS = "network_event_journal_v1"
    private const val MAX_EVENTS = 100
    private val gson = Gson()

    fun record(
        context: Context,
        type: NetworkEventType,
        title: String,
        detail: String? = null,
        severity: NetworkEventSeverity = NetworkEventSeverity.INFO,
        serverName: String? = null,
        transport: NetworkTransport? = null,
        nowMs: Long = System.currentTimeMillis()
    ): NetworkEvent {
        val event = NetworkEvent(
            id = UUID.randomUUID().toString(),
            timestampMs = nowMs,
            type = type,
            title = NetworkEventSanitizer.sanitize(title).orEmpty(),
            detail = NetworkEventSanitizer.sanitize(detail),
            severity = severity,
            serverName = NetworkEventSanitizer.sanitize(serverName),
            transport = transport?.name
        )
        val updated = (list(context) + event).takeLast(MAX_EVENTS)
        PreferencesManager(context).setString(KEY_EVENTS, gson.toJson(updated))
        return event
    }

    fun list(context: Context): List<NetworkEvent> {
        val raw = PreferencesManager(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<NetworkEvent>>(
                raw,
                object : TypeToken<List<NetworkEvent>>() {}.type
            ).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        PreferencesManager(context).setString(KEY_EVENTS, null)
    }
}
