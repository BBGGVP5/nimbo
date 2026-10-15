package com.danila.nimbo.network

import org.json.JSONArray
import org.json.JSONObject

/** Retain only provable internal fallback routes. No provider listeners or direct catch-all. */
internal object XrayProbeRouting {
    data class Projection(val rules: JSONArray, val balancers: JSONArray, val leastPingTags: Set<String>)

    fun project(root: JSONObject, firstRule: JSONObject): Projection? = runCatching {
        val outbounds = root.getJSONArray("outbounds")
        val entries = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }
        val routing = root.getJSONObject("routing")
        val allRules = routing.getJSONArray("rules")
        val allBalancers = routing.optJSONArray("balancers") ?: JSONArray()
        val rules = JSONArray().put(JSONObject(firstRule.toString()))
        val balancers = JSONArray()
        val candidates = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visitRule(rule: JSONObject, visitOutbound: (String) -> Unit, visitBalancer: (String) -> Unit) {
            val outbound = rule.optString("outboundTag")
            val balancer = rule.optString("balancerTag")
            require(outbound.isNotBlank() != balancer.isNotBlank())
            if (outbound.isNotBlank()) visitOutbound(outbound) else visitBalancer(balancer)
        }
        lateinit var visitOutbound: (String) -> Unit
        lateinit var visitBalancer: (String) -> Unit
        fun visit(key: String, block: () -> Unit) {
            require(key !in visiting && visiting.size < 64)
            if (key in visited) return
            visiting += key
            block()
            visiting -= key
            visited += key
        }
        visitOutbound = { tag -> visit("out:$tag") {
            val outbound = entries.single { it.optString("tag") == tag }
            if (outbound.optString("protocol") == "loopback") {
                val inbound = outbound.getJSONObject("settings").getString("inboundTag")
                require(inbound.isNotBlank() && inbound != firstRule.optJSONArray("inboundTag")?.optString(0))
                val matching = (0 until allRules.length()).map { allRules.getJSONObject(it) }.filter { rule ->
                    rule.optJSONArray("inboundTag")?.let { tags -> (0 until tags.length()).any { tags.optString(it) == inbound } }
                        ?: (rule.optString("inboundTag") == inbound)
                }
                val rule = matching.single()
                require(rule.optString("type") == "field")
                require(rule.keys().asSequence().all { it in setOf("type", "inboundTag", "network", "outboundTag", "balancerTag") })
                require(rule.optString("network").replace(" ", "") in setOf("", "tcp,udp", "udp,tcp"))
                // A loopback without a proven unconditional private route could hit the default outbound.
                val copy = JSONObject(rule.toString()).put("inboundTag", JSONArray().put(inbound))
                rules.put(copy)
                visitRule(copy, visitOutbound, visitBalancer)
            }
        } }
        visitBalancer = { tag -> visit("bal:$tag") {
            val balancer = (0 until allBalancers.length()).map { allBalancers.getJSONObject(it) }.single { it.optString("tag") == tag }
            val selectors = balancer.getJSONArray("selector")
            val prefixes = (0 until selectors.length()).map { selectors.getString(it) }
            require(prefixes.isNotEmpty() && prefixes.none { it.isBlank() })
            val members = entries.filter { outbound -> prefixes.any { outbound.optString("tag").startsWith(it) } }
            require(members.isNotEmpty())
            balancers.put(JSONObject(balancer.toString()))
            members.forEach { visitOutbound(it.getString("tag")) }
            if (balancer.optJSONObject("strategy")?.optString("type") == "leastPing") candidates += members.map { it.getString("tag") }
            balancer.optString("fallbackTag").takeIf { it.isNotBlank() }?.let(visitOutbound)
        } }
        visitRule(firstRule, visitOutbound, visitBalancer)
        Projection(rules, balancers, candidates)
    }.getOrNull()
}
