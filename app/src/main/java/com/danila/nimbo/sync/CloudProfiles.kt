package com.danila.nimbo.sync

import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.mihomo.MihomoProtocol
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object CloudProfiles {
    private val gson = Gson()
    fun encode(profiles: List<SubscriptionProfile>): String = JsonObject().apply {
        addProperty("format", "nimbo-cloud-profiles-v1")
        addProperty("updatedAt", System.currentTimeMillis())
        add("profiles", gson.toJsonTree(profiles))
    }.toString()
    fun decode(source: String): List<SubscriptionProfile> {
        require(source.toByteArray().size <= CloudCipher.MAX_BYTES / 2)
        val root = JsonParser.parseString(source).asJsonObject
        require(root["format"]?.asString == "nimbo-cloud-profiles-v1")
        val rows = root.getAsJsonArray("profiles") ?: error("Missing profiles")
        require(rows.size() <= 256)
        val profiles = rows.map { row ->
            val obj = row.asJsonObject
            require(obj["url"]?.asString?.let { it.isNotBlank() && it.length <= 8192 } == true)
            require(obj["name"] == null || (obj["name"].isJsonPrimitive && obj["name"].asJsonPrimitive.isString && obj["name"].asString.length <= 4096))
            require(obj["templates"] == null || obj["templates"].isJsonArray)
            obj.getAsJsonArray("templates")?.forEach { element ->
                val template = element.asJsonObject
                listOf("uuid", "name", "templateType").forEach { field -> require(template[field]?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString } == true) }
            }
            val servers = obj.getAsJsonArray("servers") ?: error("Missing servers")
            require(servers.size() <= 10000)
            servers.forEach { element ->
                val server = element.asJsonObject
                listOf("name", "host", "uuid", "protocol").forEach { field ->
                    require(server[field]?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length <= 65536 } == true)
                }
                require(server["port"]?.asInt?.let { it in 0..65535 } == true)
            }
            val profile = gson.fromJson(obj, SubscriptionProfile::class.java)
            require(profile.servers != null && profile.servers.size <= 10000)
            if (profile.configType.equals("mihomo", true)) MihomoProtocol.checkSource(profile.rawConfig.orEmpty())
            profile
        }
        require(profiles.map { it.url }.distinct().size == profiles.size)
        return profiles
    }
    fun merge(local: List<SubscriptionProfile>, incoming: List<SubscriptionProfile>): List<SubscriptionProfile> {
        val incomingByUrl = incoming.associateBy { it.url }
        return local.map { incomingByUrl[it.url] ?: it } + incoming.filter { item -> local.none { it.url == item.url } }
    }
}
