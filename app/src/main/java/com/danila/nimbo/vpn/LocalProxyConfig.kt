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
    const val MIRROR_PORT = 2081
    const val MIRROR_INBOUND_TAG = "nimbo-mirror-in"
    private const val TUN_INBOUND_TAG = "tun-in"

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
                tag.lowercase() !in utilityTags && !hasUnverifiedDialer(outbound, entries)
        }
        val outboundTag = rule.optString("outboundTag")
        val balancerTag = rule.optString("balancerTag")
        if (!selectedOutboundTag.isNullOrBlank() && outboundTag != selectedOutboundTag.trim()) return false
        if (selectedOutboundTag.isNullOrBlank() && !selectedBalancerTag.isNullOrBlank() && balancerTag != selectedBalancerTag.trim()) return false
        if (outboundTag.isNotBlank()) {
            // Without an explicit selection, multiple proxies do not prove which node owns the result.
            if (selectedOutboundTag.isNullOrBlank() && entries.count { it.optString("protocol").lowercase() in allowed } != 1) return false
            if (balancerTag.isNotBlank()) return false
            if (safe(outboundTag)) return true
            if (entries.singleOrNull { it.optString("tag") == outboundTag }?.optString("protocol") != "loopback") return false
        }
        if (balancerTag.isBlank() && outboundTag.isBlank()) return false
        val projection = com.danila.nimbo.network.XrayProbeRouting.project(config, rule) ?: return false
        fun safeFailure(tag: String): Boolean {
            val entry = entries.singleOrNull { it.optString("tag") == tag } ?: return false
            // Projection proves each loopback's sole unconditional route and rejects cycles.
            return safe(tag) || (entry.optString("protocol") in setOf("blackhole", "loopback") && !hasUnverifiedDialer(entry))
        }
        if (outboundTag.isNotBlank() && !safeFailure(outboundTag)) return false
        if ((1 until projection.rules.length()).any {
                val internal = projection.rules.getJSONObject(it)
                internal.optString("outboundTag").takeIf(String::isNotBlank)?.let { tag -> !safeFailure(tag) } == true
            }) return false
        return (0 until projection.balancers.length()).all { index ->
            val balancer = projection.balancers.getJSONObject(index)
            val selector = balancer.optJSONArray("selector") ?: return false
            val prefixes = (0 until selector.length()).map { selector.optString(it) }
            if (prefixes.isEmpty() || prefixes.any { it.isBlank() }) return false
            val selected = entries.filter { node -> prefixes.any { node.optString("tag").startsWith(it) } }
            val fallback = balancer.optString("fallbackTag")
            selected.isNotEmpty() && selected.all { safe(it.optString("tag")) } &&
                (if (fallback.isBlank()) safeFailure(entries.firstOrNull()?.optString("tag").orEmpty()) else safeFailure(fallback))
        }
    }

    /** A terminal ClientHello fragmenter dials the selected node's destination, not a DIRECT target. */
    private fun hasUnverifiedDialer(outbound: JSONObject, entries: List<JSONObject>): Boolean {
        val copy = JSONObject(outbound.toString())
        val sockopt = copy.optJSONObject("streamSettings")?.optJSONObject("sockopt")
        val fragmentTag = sockopt?.optString("dialerProxy")?.takeIf { it.isNotBlank() }
        if (fragmentTag != null) {
            val fragmenter = entries.singleOrNull { it.optString("tag") == fragmentTag } ?: return true
            val settings = fragmenter.optJSONObject("settings") ?: return true
            if (fragmenter.optString("protocol") != "freedom" || settings.optJSONObject("fragment") == null ||
                settings.keys().asSequence().any { it !in setOf("fragment", "noises", "domainStrategy") } ||
                hasUnverifiedDialer(fragmenter) || fragmenter.optJSONObject("streamSettings")?.keys()?.asSequence()
                    ?.any { it != "sockopt" } == true) return true
            sockopt.remove("dialerProxy")
        }
        return hasUnverifiedDialer(copy)
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

    /**
     * Второй loopback-прокси для «Состояния сервисов». В отличие от [INBOUND_TAG] он не
     * прибит к одному узлу: его трафик идёт ровно по тем же правилам, что и `tun-in`
     * (балансировщики, обходы, direct-правила шаблона). Проверка показывает то, что
     * увидит трафик пользователя, а не отдельный узел. Вызывать на финальном JSON,
     * после всех перезаписей правил.
     */
    fun addMirrorInbound(config: JSONObject, session: HealthProxySession): Boolean {
        val inbounds = config.optJSONArray("inbounds") ?: return false
        val tunTags = (0 until inbounds.length()).mapNotNull(inbounds::optJSONObject)
            .filter { it.optString("tag") == TUN_INBOUND_TAG || it.optString("protocol") == "tun" }
            .map { it.optString("tag") }.filter { it.isNotBlank() }.toSet()
        if (tunTags.isEmpty()) return false
        val retained = (0 until inbounds.length()).mapNotNull(inbounds::optJSONObject).filterNot { inbound ->
            val listen = inbound.optString("listen").trim()
            val loopback = listen.isEmpty() || listen == HOST || listen.equals("localhost", ignoreCase = true)
            inbound.optString("tag") == MIRROR_INBOUND_TAG || (loopback && inbound.optInt("port", -1) == MIRROR_PORT)
        }
        while (inbounds.length() > 0) inbounds.remove(inbounds.length() - 1)
        retained.forEach(inbounds::put)
        inbounds.put(JSONObject()
            .put("tag", MIRROR_INBOUND_TAG)
            .put("listen", HOST)
            .put("port", MIRROR_PORT)
            .put("protocol", "http")
            .put("settings", JSONObject().put("allowTransparent", false)
                .put("accounts", JSONArray().put(JSONObject()
                    .put("user", session.username).put("pass", session.password)))))

        val rules = config.optJSONObject("routing")?.optJSONArray("rules") ?: return true
        for (index in 0 until rules.length()) {
            val rule = rules.optJSONObject(index) ?: continue
            // Правила без inboundTag и так действуют на все входы, включая зеркальный.
            when (val tags = rule.opt("inboundTag")) {
                is JSONArray -> if ((0 until tags.length()).any { tags.optString(it) in tunTags }) {
                    tags.put(MIRROR_INBOUND_TAG)
                }
                is String -> if (tags in tunTags) {
                    rule.put("inboundTag", JSONArray().put(tags).put(MIRROR_INBOUND_TAG))
                }
            }
        }
        return true
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
