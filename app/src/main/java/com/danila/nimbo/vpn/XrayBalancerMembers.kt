package com.danila.nimbo.vpn

import org.json.JSONObject

/** Expanded provider templates already own their members. Never rebuild them from the profile. */
internal object XrayBalancerMembers {
    fun needsInjection(root: JSONObject): Boolean {
        if ((root.optJSONObject("remnawave")?.optJSONArray("injectHosts")?.length() ?: 0) > 0) return true
        val outbounds = root.optJSONArray("outbounds") ?: return true
        val balancers = root.optJSONObject("routing")?.optJSONArray("balancers") ?: return true
        if (balancers.length() == 0) return true
        return (0 until balancers.length()).all { index ->
            val selectors = balancers.optJSONObject(index)?.optJSONArray("selector") ?: return@all true
            val prefixes = (0 until selectors.length()).map { selectors.optString(it) }.filter { it.isNotBlank() }
            prefixes.isEmpty() || (0 until outbounds.length()).none { i ->
                val outbound = outbounds.optJSONObject(i) ?: return@none false
                val settings = outbound.optJSONObject("settings")
                val address = settings?.optJSONArray("vnext")?.optJSONObject(0)?.optString("address")
                    ?: settings?.optJSONArray("servers")?.optJSONObject(0)?.optString("address")
                    ?: settings?.optString("address")
                prefixes.any { outbound.optString("tag").startsWith(it) } &&
                    outbound.optString("protocol") !in setOf("freedom", "blackhole", "dns", "loopback") &&
                    !address.isNullOrBlank() && address.lowercase() !in setOf("api", "localhost", "127.0.0.1", "0.0.0.0", "::1", "::")
            }
        }
    }
}
