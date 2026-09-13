package com.danila.nimbo.network

import com.danila.nimbo.vpn.HealthProxySession
import com.danila.nimbo.vpn.LocalProxyConfig
import org.json.JSONArray
import org.json.JSONObject

/** Build a private probe runtime from the exact node configuration, never the active VPN. */
internal object NodePingConfig {
    const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    private const val DENY = "nimbo-probe-deny"

    fun prepare(source: String, port: Int, session: HealthProxySession): String? = runCatching {
        require(port in 1..65535 && source.toByteArray().size <= MAX_CONFIG_BYTES)
        val original = JSONObject(source)
        val route = original.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        val inboundTags = route.getJSONArray("inboundTag")
        require(inboundTags.length() == 1 && inboundTags.getString(0) == LocalProxyConfig.INBOUND_TAG)
        require(route.keys().asSequence().all { it in setOf("type", "inboundTag", "outboundTag", "balancerTag") })
        val outboundTag = route.optString("outboundTag").takeIf { it.isNotBlank() }
        val balancerTag = route.optString("balancerTag").takeIf { it.isNotBlank() }
        require((outboundTag != null) != (balancerTag != null))
        val outbounds = original.getJSONArray("outbounds")
        require((0 until outbounds.length()).none { outbounds.getJSONObject(it).optString("tag") == DENY })
        val routing = JSONObject().put("rules", JSONArray().put(JSONObject(route.toString())))
        original.getJSONObject("routing").opt("domainStrategy")?.let { routing.put("domainStrategy", it) }
        if (balancerTag != null) {
            val balancers = original.getJSONObject("routing").getJSONArray("balancers")
            val selected = (0 until balancers.length()).map { balancers.getJSONObject(it) }
                .single { it.optString("tag") == balancerTag }
            val copy = JSONObject(selected.toString())
            if (copy.optString("fallbackTag").isBlank()) copy.put("fallbackTag", DENY)
            routing.put("balancers", JSONArray().put(copy))
        }
        val config = JSONObject().put("log", JSONObject().put("loglevel", "none"))
            .put("outbounds", JSONArray().put(JSONObject().put("tag", DENY).put("protocol", "blackhole")).apply {
                for (i in 0 until outbounds.length()) put(outbounds.getJSONObject(i))
            })
            .put("routing", routing)
            .put("inbounds", JSONArray().put(JSONObject()
                .put("tag", LocalProxyConfig.INBOUND_TAG).put("protocol", "http")
                .put("listen", LocalProxyConfig.HOST).put("port", port)))
        listOf("dns", "policy", "observatory", "burstObservatory").forEach { key ->
            original.opt(key)?.let { config.put(key, it) }
        }
        require(LocalProxyConfig.authenticateVerifiedRoute(config, session, outboundTag, balancerTag, port))
        config.toString()
    }.getOrNull()
}
