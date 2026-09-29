package com.danila.nimbo.mihomo

import com.google.gson.JsonObject

internal data class MihomoProxyGroup(val name: String, val type: String, val members: List<String>, val selected: String) {
    val selectable get() = type.lowercase() in setOf("select", "selector")
}

/** A live group choice, not a claim that every Mihomo rule uses the same outbound. */
internal data class MihomoActiveSelection(val group: String, val member: String?, val perConnection: Boolean)

internal fun mihomoActiveSelection(snapshot: JsonObject?, preferredGroup: String?): MihomoActiveSelection? {
    val groups = snapshot?.getAsJsonObject("groups") ?: return null
    fun JsonObject.string(key: String): String? = get(key)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString?.takeIf(String::isNotBlank)
    val automatic = setOf("urltest", "url-test", "fallback", "loadbalance", "load-balance")
    val first = preferredGroup?.takeIf(groups::has)
        ?: groups.entrySet().firstOrNull { (_, value) ->
            value.takeIf { it.isJsonObject }?.asJsonObject?.string("type")
                ?.lowercase()?.replace("-", "") in automatic
        }?.key ?: return null
    val root = groups[first]?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    if (root.string("type")?.lowercase()?.replace("-", "") == "loadbalance")
        return MihomoActiveSelection(first, null, true)
    var member = root.string("now") ?: return null
    val visited = mutableSetOf(first)
    repeat(8) {
        if (!visited.add(member)) return null
        val nested = groups[member]?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return MihomoActiveSelection(first, member, false)
        if (nested.string("type")?.lowercase()?.replace("-", "") == "loadbalance")
            return MihomoActiveSelection(first, null, true)
        member = nested.string("now") ?: return null
    }
    return null
}

/** Offline source order is stable; live members include dynamically loaded providers. */
internal fun mihomoProxyGroups(inspection: JsonObject?, snapshot: JsonObject?, choices: Map<String, String>): List<MihomoProxyGroup> {
    val declared = inspection?.getAsJsonObject("declaredGraph")?.getAsJsonArray("groups") ?: return emptyList()
    fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    val live = snapshot?.getAsJsonObject("groups")
    return declared.mapNotNull { element ->
        val group = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        if (group["hidden"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true) return@mapNotNull null
        val name = group.text("name") ?: return@mapNotNull null
        val current = live?.get(name)?.asJsonObject
        val members = (current?.get("all")?.takeIf { it.isJsonArray }?.asJsonArray ?: group.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray)
            ?.mapNotNull { it.takeIf { item -> item.isJsonPrimitive && item.asJsonPrimitive.isString }?.asString }?.distinct().orEmpty()
        val type = current?.text("type") ?: group.text("type").orEmpty()
        val selected = current?.text("now") ?: choices[name]?.takeIf { it in members } ?: if (type.lowercase() in setOf("select", "selector")) members.firstOrNull().orEmpty() else ""
        MihomoProxyGroup(name, type, members, selected)
    }
}
