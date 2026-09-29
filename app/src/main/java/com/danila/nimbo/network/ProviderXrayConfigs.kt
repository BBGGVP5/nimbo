package com.danila.nimbo.network

import com.danila.nimbo.model.Server
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Provider JSON is configuration, not a measured or verified connection. */
internal object ProviderXrayConfigs {
    const val TEMPLATE_PREFIX = "subscription-json:"

    fun description(json: JSONObject): String? =
        listOf("serverDescription", "server_description", "server-description", "description")
            .firstNotNullOfOrNull { key ->
                (json.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }

    fun displayName(json: JSONObject): String? =
        listOf("remarks", "remark", "title", "name")
            .firstNotNullOfOrNull { key ->
                (json.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }

    fun requiresFullConfig(json: JSONObject): Boolean =
        RemnawaveApiClient.hasBalancerOrInjectHosts(json) ||
            RemnawaveApiClient.extractSelectableXrayRoutes(json).isNotEmpty()

    /** Separate array entries must keep separate raw configs, even with shared endpoints. */
    fun templatesFromArray(text: String): List<RemnawaveSubscriptionTemplate> = runCatching {
        val array = JSONArray(text)
        buildList {
            for (index in 0 until array.length()) {
                val config = array.optJSONObject(index) ?: continue
                if (config.optJSONArray("outbounds") == null || !requiresFullConfig(config)) continue
                val raw = config.toString()
                // Content identity prevents a removed/reordered entry from selecting another config.
                val id = UUID.nameUUIDFromBytes(raw.toByteArray(Charsets.UTF_8))
                add(RemnawaveSubscriptionTemplate(
                    uuid = "$TEMPLATE_PREFIX$id",
                    name = displayName(config) ?: "Provider config ${index + 1}",
                    templateType = "XRAY_JSON",
                    viewPosition = index,
                    config = raw
                ))
            }
        }.distinctBy { it.uuid }
    }.getOrDefault(emptyList())

    fun routeServers(config: String?, profileUrl: String): List<Server> {
        val json = config?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyList()
        val routes = RemnawaveApiClient.extractSelectableXrayRoutes(json)
        val name = displayName(json)
        val protocol = SubscriptionManager.detectPrimaryProxyProtocolFromJsonConfig(config) ?: "xray"
        return routes.map { route ->
            val label = if (name != null && routes.size == 1) name
                else listOfNotNull(name, route.label.ifBlank { route.tag }).joinToString(" · ")
            Server(
                name = if (route.isBalancer) "⚖️ $label" else "🔀 $label",
                host = "API", port = 0, uuid = "remote", protocol = protocol,
                serverDescription = description(json), profileUrl = profileUrl,
                remoteBalancerTag = route.tag.takeIf { route.isBalancer },
                remoteOutboundTag = route.tag.takeIf { !route.isBalancer }
            )
        }
    }
}
