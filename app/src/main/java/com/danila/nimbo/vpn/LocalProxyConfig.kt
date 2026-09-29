package com.danila.nimbo.vpn

import org.json.JSONArray
import org.json.JSONObject

/**
 * Loopback HTTP proxy used only by Nimbo's own end-to-end checks.
 *
 * The listener is deliberately bound to 127.0.0.1. A first-match routing rule for
 * [INBOUND_TAG] must be prepended to imported rules so a remote catch-all cannot
 * silently send health checks to `direct`.
 */
internal object LocalProxyConfig {
    const val HOST = "127.0.0.1"
    const val PORT = 2080
    const val INBOUND_TAG = "nimbo-health-in"

    private val utilityProtocols = setOf("freedom", "blackhole", "dns", "loopback")
    private val utilityTags = setOf("direct", "block", "dns", "dns-out")

    fun ensureInbound(inbounds: JSONArray) {
        val retained = mutableListOf<JSONObject>()
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(index) ?: continue
            if (!isProbeInboundOrPortConflict(inbound)) {
                retained += inbound
            }
        }

        while (inbounds.length() > 0) {
            inbounds.remove(inbounds.length() - 1)
        }
        retained.forEach(inbounds::put)
        inbounds.put(buildInbound())
    }

    fun prependRoute(
        rules: JSONArray,
        outboundTag: String?,
        balancerTag: String?
    ): JSONArray {
        val normalizedOutbound = outboundTag?.trim().orEmpty()
        val normalizedBalancer = balancerTag?.trim().orEmpty()
        val result = JSONArray()

        when {
            normalizedOutbound.isNotEmpty() -> result.put(buildRoute("outboundTag", normalizedOutbound))
            normalizedBalancer.isNotEmpty() -> result.put(buildRoute("balancerTag", normalizedBalancer))
        }

        for (index in 0 until rules.length()) {
            val rule = rules.optJSONObject(index) ?: continue
            if (!isProbeRoute(rule)) {
                result.put(rule)
            }
        }
        return result
    }

    fun firstProxyOutboundTag(outbounds: JSONArray): String? {
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val protocol = outbound.optString("protocol").trim().lowercase()
            val tag = outbound.optString("tag").trim()
            if (tag.isNotEmpty() && protocol !in utilityProtocols && tag.lowercase() !in utilityTags) {
                return tag
            }
        }
        return null
    }

    /** Verify the final JSON, after imported routing/profile rewrites, before enabling probes. */
    fun authenticateVerifiedRoute(
        config: JSONObject,
        session: HealthProxySession,
        selectedOutboundTag: String? = null,
        selectedBalancerTag: String? = null,
        proxyPort: Int = PORT
    ): Boolean {
        val inbounds = config.optJSONArray("inbounds") ?: return false
        val inbound = (0 until inbounds.length()).mapNotNull(inbounds::optJSONObject)
            .singleOrNull { it.optString("tag") == INBOUND_TAG } ?: return false
        // Always authenticate the listener, even when the route cannot be verified.
        inbound.put("settings", JSONObject().put("allowTransparent", false)
            .put("accounts", JSONArray().put(JSONObject()
                .put("user", session.username).put("pass", session.password))))
        if (inbound.optString("listen") != HOST || inbound.optInt("port") != proxyPort ||
            inbound.optString("protocol") != "http") return false
        val routing = config.optJSONObject("routing") ?: return false
        val rule = routing.optJSONArray("rules")?.optJSONObject(0) ?: return false
        val inboundTags = rule.optJSONArray("inboundTag") ?: return false
        if (inboundTags.length() != 1 || inboundTags.optString(0) != INBOUND_TAG ||
            rule.optString("type") != "field") return false
        // Extra predicates could miss this URL and allow a later direct catch-all.
        if (rule.keys().asSequence().any { it !in setOf("type", "inboundTag", "outboundTag", "balancerTag") }) return false
        val outbounds = config.optJSONArray("outbounds") ?: return false
        val entries = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
        val allowed = setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http", "hysteria", "hysteria2", "tuic", "wireguard")
        fun safe(tag: String): Boolean {
            val outbound = entries.singleOrNull { it.optString("tag") == tag } ?: return false
            return tag.isNotBlank() && outbound.optString("protocol").lowercase() in allowed &&
                tag.lowercase() !in utilityTags && !hasUnverifiedDialer(outbound)
        }
        val outboundTag = rule.optString("outboundTag")
        val balancerTag = rule.optString("balancerTag")
        if (!selectedOutboundTag.isNullOrBlank() && outboundTag != selectedOutboundTag.trim()) return false
        if (selectedOutboundTag.isNullOrBlank() && !selectedBalancerTag.isNullOrBlank() && balancerTag != selectedBalancerTag.trim()) return false
        if (outboundTag.isNotBlank()) {
            // Without an explicit selection, multiple proxies do not prove which node owns the result.
            if (selectedOutboundTag.isNullOrBlank() && entries.count { it.optString("protocol").lowercase() in allowed } != 1) return false
            return balancerTag.isBlank() && safe(outboundTag)
        }
        if (balancerTag.isBlank()) return false
        val balancers = routing.optJSONArray("balancers") ?: return false
        val balancer = (0 until balancers.length()).mapNotNull(balancers::optJSONObject)
            .singleOrNull { it.optString("tag") == balancerTag } ?: return false
        val selector = balancer.optJSONArray("selector") ?: return false
        val prefixes = (0 until selector.length()).map { selector.optString(it) }
        if (prefixes.isEmpty() || prefixes.any { it.isBlank() }) return false
        val selected = entries.filter { node -> prefixes.any { node.optString("tag").startsWith(it) } }
        val fallback = balancer.optString("fallbackTag")
        fun safeFailure(tag: String): Boolean {
            val entry = entries.singleOrNull { it.optString("tag") == tag } ?: return false
            return safe(tag) || (entry.optString("protocol") == "blackhole" && !hasUnverifiedDialer(entry))
        }
        return selected.isNotEmpty() && selected.all { safe(it.optString("tag")) } &&
            // Xray falls back to the default outbound when a balancer returns no tag.
            // A direct default with no explicit safe fallback is not a verified route.
            (if (fallback.isBlank()) safeFailure(entries.firstOrNull()?.optString("tag").orEmpty()) else safeFailure(fallback))
    }

    /** Inspect structured fields, including XHTTP extra/downloadSettings and arrays.
     * Ordinary header/credential strings containing these words are not routing fields. */
    private fun hasUnverifiedDialer(value: Any?): Boolean = when (value) {
        is JSONObject -> value.keys().asSequence().any { key ->
            val child = value.opt(key)
            val routeField = key.equals("dialerProxy", true) || key.equals("proxySettings", true)
            val populated = when (child) {
                null, JSONObject.NULL -> false
                is String -> child.isNotBlank()
                is JSONObject -> child.length() > 0
                is JSONArray -> child.length() > 0
                else -> true
            }
            (routeField && populated) || hasUnverifiedDialer(child)
        }
        is JSONArray -> (0 until value.length()).any { hasUnverifiedDialer(value.opt(it)) }
        else -> false
    }

    private fun buildInbound(): JSONObject = JSONObject()
        .put("tag", INBOUND_TAG)
        .put("listen", HOST)
        .put("port", PORT)
        .put("protocol", "http")
        .put("settings", JSONObject().put("allowTransparent", false))

    private fun buildRoute(key: String, tag: String): JSONObject = JSONObject()
        .put("type", "field")
        .put("inboundTag", JSONArray().put(INBOUND_TAG))
        .put(key, tag)

    private fun isProbeInboundOrPortConflict(inbound: JSONObject): Boolean {
        if (inbound.optString("tag") == INBOUND_TAG) return true
        val listen = inbound.optString("listen").trim()
        val isLoopback = listen.isEmpty() || listen == HOST || listen.equals("localhost", ignoreCase = true)
        return isLoopback && inbound.optInt("port", -1) == PORT
    }

    private fun isProbeRoute(rule: JSONObject): Boolean {
        val tags = rule.optJSONArray("inboundTag") ?: return false
        for (index in 0 until tags.length()) {
            if (tags.optString(index) == INBOUND_TAG) return true
        }
        return false
    }
}
