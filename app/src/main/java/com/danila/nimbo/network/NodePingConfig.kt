package com.danila.nimbo.network

import com.danila.nimbo.vpn.HealthProxySession
import com.danila.nimbo.vpn.LocalProxyConfig
import org.json.JSONArray
import org.json.JSONObject

/** Build a private probe runtime from the exact node configuration, never the active VPN. */
internal object NodePingConfig {
    const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    private const val DENY = "nimbo-probe-deny"
    const val READINESS_TAG = "nimbo-internal-readiness"

    fun leastPingCandidates(source: String): Set<String> = runCatching {
        val root = JSONObject(source)
        val routing = root.getJSONObject("routing")
        val tag = routing.getJSONArray("rules").getJSONObject(0).optString("balancerTag")
        val balancers = routing.optJSONArray("balancers") ?: return emptySet()
        val balancer = (0 until balancers.length()).map { balancers.getJSONObject(it) }.singleOrNull { it.optString("tag") == tag }
            ?: return emptySet()
        if (balancer.optJSONObject("strategy")?.optString("type") != "leastPing") return emptySet()
        selectedTags(root.getJSONArray("outbounds"), balancer.getJSONArray("selector"))
    }.getOrDefault(emptySet())

    private fun selectedTags(outbounds: JSONArray, selector: JSONArray): Set<String> {
        val prefixes = (0 until selector.length()).map { selector.getString(it) }
        return (0 until outbounds.length()).map { outbounds.getJSONObject(it).optString("tag") }
            .filter { tag -> prefixes.any(tag::startsWith) }.toSet()
    }

    fun prepare(source: String, port: Int, session: HealthProxySession, readinessPort: Int? = null): String? = runCatching {
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
        listOf("dns", "policy").forEach { key ->
            original.opt(key)?.let { config.put(key, it) }
        }
        // Explicit outbounds have no observer dependency: don't launch every subscription probe.
        if (balancerTag != null) {
            val selected = selectedTags(outbounds, routing.getJSONArray("balancers").getJSONObject(0).getJSONArray("selector"))
            listOf("observatory", "burstObservatory").forEach { key ->
                original.optJSONObject(key)?.let { observer ->
                    val copy = JSONObject(observer.toString())
                    observer.optJSONArray("subjectSelector")?.let { selector ->
                        val needed = selectedTags(outbounds, selector).intersect(selected)
                        // Selectors are prefixes, not exact tags; narrow only if expansion is identical.
                        val narrowed = JSONArray(needed.sorted())
                        if (selectedTags(outbounds, narrowed) == needed) copy.put("subjectSelector", narrowed)
                    }
                    config.put(key, copy)
                }
            }
        }
        if (readinessPort != null) {
            require(readinessPort in 1..65535 && readinessPort != port && leastPingCandidates(source).isNotEmpty())
            require((0 until outbounds.length()).none { outbounds.getJSONObject(it).optString("tag") == READINESS_TAG })
            val selector = routing.getJSONArray("balancers").getJSONObject(0).getJSONArray("selector")
            require((0 until selector.length()).none { READINESS_TAG.startsWith(selector.getString(it)) })
            listOf("observatory", "burstObservatory").forEach { key ->
                config.optJSONObject(key)?.optJSONArray("subjectSelector")?.let { subjects ->
                    require((0 until subjects.length()).none { READINESS_TAG.startsWith(subjects.getString(it)) })
                }
            }
            config.put("metrics", JSONObject().put("tag", READINESS_TAG)) // No unauthenticated metrics.listen.
            config.getJSONArray("inbounds").put(JSONObject().put("tag", READINESS_TAG).put("protocol", "http")
                .put("listen", LocalProxyConfig.HOST).put("port", readinessPort)
                .put("settings", JSONObject().put("allowTransparent", false).put("accounts", JSONArray().put(JSONObject()
                    .put("user", session.username).put("pass", session.password)))))
            routing.getJSONArray("rules").put(JSONObject().put("type", "field")
                .put("inboundTag", JSONArray().put(READINESS_TAG)).put("outboundTag", READINESS_TAG))
        }
        require(LocalProxyConfig.authenticateVerifiedRoute(config, session, outboundTag, balancerTag, port))
        config.toString()
    }.getOrNull()
}
