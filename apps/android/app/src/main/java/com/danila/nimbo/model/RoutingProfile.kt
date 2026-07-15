package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName

data class RoutingProfile(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Builtin") val builtin: Boolean? = false,
    @SerializedName("RuleOrder") val ruleOrder: String? = "block-proxy-direct",
    @SerializedName("GlobalProxy") val globalProxy: String? = "true",
    @SerializedName("BypassLocalIp") val bypassLocalIp: String? = "true",
    @SerializedName("RemoteDNSType") val remoteDNSType: String? = "DoU",
    @SerializedName("RemoteDNSDomain") val remoteDNSDomain: String? = "https://cloudflare-dns.com/dns-query",
    @SerializedName("RemoteDNSIP") val remoteDNSIP: String? = "1.1.1.1",
    @SerializedName("DomesticDNSType") val domesticDNSType: String? = "DoU",
    @SerializedName("DomesticDNSDomain") val domesticDNSDomain: String? = "dns.google/dns-query",
    @SerializedName("DomesticDNSIP") val domesticDNSIP: String? = "8.8.8.8",
    @SerializedName("Geoipurl") val geoipUrl: String? = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
    @SerializedName("Geositeurl") val geositeUrl: String? = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
    @SerializedName("DnsHosts") val dnsHosts: Map<String, String>? = emptyMap(),
    @SerializedName("DirectSites") val directSites: List<String>? = emptyList(),
    @SerializedName("DirectIp") val directIp: List<String>? = emptyList(),
    @SerializedName("ProxySites") val proxySites: List<String>? = emptyList(),
    @SerializedName("ProxyIp") val proxyIp: List<String>? = emptyList(),
    @SerializedName("BlockSites") val blockSites: List<String>? = emptyList(),
    @SerializedName("BlockIp") val blockIp: List<String>? = emptyList(),
    @SerializedName("DomainStrategy") val domainStrategy: String? = "IPIfNonMatch",
    @SerializedName("FakeDNS") val fakeDNS: String? = "false",
    @SerializedName("LastUpdated") val lastUpdated: String? = null
) {
    fun isGlobalProxyEnabled(): Boolean {
        return globalProxy.toBoolean()
    }

    fun isBypassLocalIpEnabled(): Boolean {
        return bypassLocalIp.toBoolean()
    }
}
