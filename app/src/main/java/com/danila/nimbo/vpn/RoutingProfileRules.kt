package com.danila.nimbo.vpn

import com.danila.nimbo.model.RoutingProfile
import org.json.JSONArray
import org.json.JSONObject

/** Converts an Android routing profile into Xray field rules. */
object RoutingProfileRules {
    fun build(profile: RoutingProfile, includeFallback: Boolean): JSONArray = JSONArray().apply {
        val ruleKinds = when (profile.ruleOrder?.trim()?.lowercase()) {
            "block-direct-proxy" -> listOf(RuleKind.BLOCK, RuleKind.DIRECT, RuleKind.PROXY)
            else -> listOf(RuleKind.BLOCK, RuleKind.PROXY, RuleKind.DIRECT)
        }
        ruleKinds.forEach { kind ->
            when (kind) {
                RuleKind.BLOCK -> addRule(profile.blockSites, profile.blockIp, "block")
                RuleKind.PROXY -> addRule(profile.proxySites, profile.proxyIp, "proxy")
                RuleKind.DIRECT -> addRule(profile.directSites, profile.directIp, "direct")
            }
        }
        if (profile.isBypassLocalIpEnabled()) {
            addRule(emptyList(), listOf("geoip:private"), "direct")
        }
        if (includeFallback) {
            put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in"))
                    .put("outboundTag", if (profile.isGlobalProxyEnabled()) "proxy" else "direct")
            )
        }
    }

    private fun JSONArray.addRule(
        domains: List<String>?,
        ips: List<String>?,
        outboundTag: String
    ) {
        val rawDomains = domains.orEmpty().map(String::trim).filter(String::isNotBlank)
        val normalizedDomains = rawDomains.filterNot(::isIpSelector)
        val normalizedIps = (ips.orEmpty() + rawDomains.filter(::isIpSelector))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (normalizedDomains.isNotEmpty()) {
            addDomainRule(normalizedDomains, outboundTag)
        }
        if (normalizedIps.isNotEmpty()) {
            addIpRule(normalizedIps, outboundTag)
        }
    }

    private fun JSONArray.addDomainRule(domains: List<String>, outboundTag: String) {
        put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("tun-in"))
                .put("domain", JSONArray(domains))
                .put("outboundTag", outboundTag)
        )
    }

    private fun JSONArray.addIpRule(ips: List<String>, outboundTag: String) {
        put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("tun-in"))
                .put("ip", JSONArray(ips))
                .put("outboundTag", outboundTag)
        )
    }

    private fun isIpSelector(value: String): Boolean =
        value.startsWith("geoip:", ignoreCase = true)

    private enum class RuleKind { BLOCK, PROXY, DIRECT }
}
