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
