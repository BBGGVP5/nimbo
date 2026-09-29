package com.danila.nimbo.network

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.json.JSONObject
import java.security.MessageDigest

/** Successful node GET measurements, scoped to a subscription, endpoint and health URL. */
internal object MihomoPingCache {
    private const val MAX_NODES = 512

    private fun preferences(context: Context) =
        context.getSharedPreferences("mihomo_ping_cache", Context.MODE_PRIVATE)

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun key(profileId: String, url: String): String = "${hash(profileId)}:${hash(url)}"

    /** Ignore JSON key order, but invalidate when an outbound's actual fields change. */
    fun fingerprint(outboundJson: String): String = hash(canonical(JsonParser.parseString(outboundJson)))

    private fun canonical(element: JsonElement): String = when {
        element.isJsonObject -> element.asJsonObject.entrySet().sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}") { (name, value) ->
                "${JsonPrimitive(name)}:${canonical(value)}"
            }
        element.isJsonArray -> element.asJsonArray.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        else -> element.toString()
    }

    // A result stays until that node is checked again. Clock changes must not expire it.
    internal fun valid(ms: Int): Boolean = ms in 0..60_000

    fun read(context: Context, profileId: String, url: String, fingerprints: Map<String, String>): Map<String, Int> = synchronized(this) {
        if (profileId.isBlank() || url.isBlank()) return@synchronized emptyMap()
        val saved = preferences(context).getString(key(profileId, url), null) ?: return@synchronized emptyMap()
        runCatching {
            val json = JSONObject(saved)
            json.keys().asSequence().take(MAX_NODES).mapNotNull { name ->
                val item = json.optJSONObject(name) ?: return@mapNotNull null
                val ms = item.optInt("ms", -1)
                if (valid(ms) && fingerprints[name] == item.optString("node")) name to ms else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** A failed recheck removes a stale success instead of keeping a misleading number. */
    fun write(context: Context, profileId: String, url: String, name: String, fingerprint: String, ms: Int?) = synchronized(this) {
        if (profileId.isBlank() || url.isBlank() || name.isBlank() || name.length > 512 || fingerprint.isBlank()) return@synchronized
        val pref = preferences(context)
        val key = key(profileId, url)
        val json = runCatching { JSONObject(pref.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        json.keys().asSequence().toList().forEach { node ->
            val item = json.optJSONObject(node)
            if (item == null || !valid(item.optInt("ms", -1))) json.remove(node)
        }
        json.remove(name)
        if (ms != null && valid(ms) && json.length() < MAX_NODES)
            json.put(name, JSONObject().put("ms", ms).put("at", now).put("node", fingerprint))
        // Results are written off the UI thread. A synchronous commit matters here:
        // the user can leave Profiles (or Android can kill the process) immediately
        // after a check, and apply() does not report a failed disk write.
        check(pref.edit().putString(key, json.toString()).commit()) { "PING_CACHE_WRITE_FAILED" }
    }
}
