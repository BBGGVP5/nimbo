package com.danila.nimbo.mihomo

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Choices belong to the exact source, not to whichever session is currently running. */
internal object MihomoGroupChoices {
    data class LastChoice(val group: String, val name: String, val fingerprint: String?)

    private fun store(context: Context) = context.getSharedPreferences("mihomo_group_choices", Context.MODE_PRIVATE)
    fun read(context: Context, hash: String): Map<String, String> = runCatching {
        JsonParser.parseString(store(context).getString(hash, "{}")).asJsonObject.entrySet()
            .associate { it.key to it.value.asString }
    }.getOrDefault(emptyMap())
    fun last(context: Context, hash: String): LastChoice? = runCatching {
        val saved = store(context).getString("last:$hash", null) ?: return@runCatching null
        val value = JsonParser.parseString(saved).asJsonObject
        val group = value.get("group")?.asString ?: return@runCatching null
        val name = value.get("name")?.asString ?: return@runCatching null
        if (read(context, hash)[group] != name) return@runCatching null
        LastChoice(group, name, value.get("fingerprint")?.asString)
    }.getOrNull()

    @Synchronized fun write(context: Context, hash: String, group: String, name: String, fingerprint: String? = null) {
        val next = JsonObject().apply { read(context, hash).forEach { (key, value) -> addProperty(key, value) }; addProperty(group, name) }
        val last = JsonObject().apply {
            addProperty("group", group); addProperty("name", name)
            fingerprint?.let { addProperty("fingerprint", it) }
        }
        check(store(context).edit().putString(hash, next.toString())
            .putString("last:$hash", last.toString()).commit())
    }
    fun restore(context: Context, hash: String, generation: Long) {
        val groups = MihomoBridge.response("snapshot", generation = generation).data.getAsJsonObject("groups") ?: return
        read(context, hash).forEach { (group, name) ->
            val value = groups[group]?.asJsonObject ?: return@forEach
            if (value["type"]?.asString?.lowercase() !in setOf("select", "selector")) return@forEach
            if (value.getAsJsonArray("all")?.any { it.asString == name } != true) return@forEach
            MihomoBridge.response("select", generation = generation, fields = JsonObject().apply {
                addProperty("group", group); addProperty("name", name)
            })
        }
    }
}
