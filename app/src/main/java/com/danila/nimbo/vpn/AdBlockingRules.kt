package com.danila.nimbo.vpn

import org.json.JSONArray
import org.json.JSONObject

/** A small runtime overlay. Imported templates and saved routing rules are never mutated. */
internal object AdBlockingRules {
    val suffixes = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googleads.g.doubleclick.net", "adservice.google.com", "ads.yahoo.com",
        "advertising.com", "adsrvr.org", "adnxs.com", "adform.net", "adroll.com",
        "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "scorecardresearch.com",
        "quantserve.com", "ads.facebook.com", "app-measurement.com", "amazon-adsystem.com"
    )

    fun overlay(config: JSONObject, enabled: Boolean): JSONObject {
        if (!enabled) return config
        val effective = JSONObject(config.toString())
        // Final runtime snapshot also enables destination detection when the saved
        // preference changed while a full template was being prepared.
        effective.optJSONArray("inbounds")?.let { inbounds ->
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                if (inbound.optString("protocol") == "tun") inbound.put("sniffing", JSONObject()
                    .put("enabled", true).put("routeOnly", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic"))))
            }
        }
        val outbounds = effective.optJSONArray("outbounds") ?: JSONArray().also { effective.put("outbounds", it) }
        val entries = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
        val tags = entries.map { it.optString("tag") }.toSet()
        val blockTag = entries.firstOrNull {
            it.optString("protocol") == "blackhole" && it.optString("tag").isNotBlank() &&
                entries.count { other -> other.optString("tag") == it.optString("tag") } == 1
        }?.optString("tag") ?: generateSequence("nimbo-ad-block") { "$it-1" }
            .first { it !in tags }.also {
                outbounds.put(JSONObject().put("tag", it).put("protocol", "blackhole"))
            }
        val routing = effective.optJSONObject("routing") ?: JSONObject().also { effective.put("routing", it) }
        val rules = routing.optJSONArray("rules") ?: JSONArray()
        val domainRule = JSONObject().put("type", "field").put("outboundTag", blockTag)
            .put("domain", JSONArray(suffixes.map { "domain:$it" }))
        val updated = JSONArray().put(domainRule)
        for (i in 0 until rules.length()) updated.put(rules.get(i))
        routing.put("rules", updated)
        return effective
    }
}
