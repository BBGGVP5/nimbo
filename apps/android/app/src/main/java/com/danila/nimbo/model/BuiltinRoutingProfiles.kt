package com.danila.nimbo.model

/**
 * Stable defaults for Android routing. User edits are stored separately and are
 * overlaid by [resolve], so updating these defaults never overwrites a profile
 * the user has changed.
 */
object BuiltinRoutingProfiles {
    const val GLOBAL = "global"
    const val BYPASS_LAN = "bypass_lan"
    const val CHINA_DIRECT = "china_direct"
    const val RUSSIA_DIRECT = "russia_direct"
    const val ROSCOMVPN = "roscomvpn"

    fun defaults(): List<RoutingProfile> = listOf(
        profile(
            id = GLOBAL,
            name = "Глобальный",
            description = "Весь трафик через VPN",
            domainStrategy = "AsIs",
            blockSites = listOf("geosite:category-ads")
        ),
        profile(
            id = BYPASS_LAN,
            name = "Обход LAN",
            description = "Локальные адреса напрямую",
            domainStrategy = "AsIs",
            directIp = listOf(
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "127.0.0.0/8",
                "fc00::/7",
                "fe80::/10",
                "::1/128"
            ),
            blockSites = listOf("geosite:category-ads")
        ),
        profile(
            id = CHINA_DIRECT,
            name = "Китай",
            description = "Китайские сайты напрямую",
            domainStrategy = "IPIfNonMatch",
            localDnsIp = "223.5.5.5",
            directSites = listOf(
                "geosite:cn",
                "geosite:apple-cn",
                "geosite:google-cn",
                "geosite:microsoft@cn"
            ),
            directIp = listOf(
                "geoip:cn",
                "geoip:private",
                "223.5.5.5/32",
                "119.29.29.29/32"
            ),
            blockSites = listOf("geosite:category-ads-all")
        ),
        profile(
            id = RUSSIA_DIRECT,
            name = "Россия",
            description = "Российские ресурсы напрямую",
            domainStrategy = "IPIfNonMatch",
            localDnsIp = "77.88.8.8",
            directSites = listOf(
                "domain:ru",
                "domain:su",
                "domain:yandex.ru",
                "domain:mail.ru",
                "domain:vk.com",
                "domain:vk.ru",
                "domain:ok.ru",
                "domain:sberbank.ru",
                "domain:gosuslugi.ru",
                "domain:tinkoff.ru",
                "domain:rt.com",
                "domain:wildberries.ru",
                "domain:ozon.ru",
                "domain:avito.ru",
                "domain:hh.ru",
                "domain:2gis.ru",
                "domain:rutube.ru",
                "domain:dzen.ru",
                "domain:kinopoisk.ru",
                "domain:ivi.ru",
                "domain:kion.ru",
                "domain:wink.ru",
                "domain:rbc.ru",
                "domain:lenta.ru",
                "domain:tass.ru",
                "domain:ria.ru",
                "domain:1tv.ru",
                "domain:vesti.ru",
                "domain:meduza.io",
                "domain:tinkoff.com"
            ),
            directIp = listOf("geoip:ru", "geoip:private"),
            blockSites = listOf("geosite:category-ads-all")
        ),
        profile(
            id = ROSCOMVPN,
            name = "RoscomVPN",
            description = "Заблокированные в РФ ресурсы — через VPN, остальное напрямую",
            domainStrategy = "IPIfNonMatch",
            globalProxy = false,
            ruleOrder = "block-direct-proxy",
            localDnsIp = "77.88.8.8",
            directSites = listOf("domain:ru"),
            directIp = listOf("geoip:ru", "geoip:private"),
            proxySites = listOf(
                "domain:openai.com",
                "domain:chatgpt.com",
                "domain:anthropic.com",
                "domain:claude.ai",
                "domain:notion.so",
                "domain:linkedin.com",
                "domain:tradingview.com",
                "domain:patreon.com",
                "domain:onlyfans.com",
                "domain:medium.com",
                "domain:soundcloud.com",
                "domain:spotify.com",
                "domain:bbc.com",
                "domain:dw.com",
                "domain:bandcamp.com",
                "domain:itch.io",
                "domain:speedtest.net",
                "domain:fast.com",
                "domain:figma.com",
                "domain:behance.net",
                "domain:dribbble.com",
                "domain:proton.me",
                "domain:protonmail.com",
                "domain:tutanota.com",
                "domain:cloudflare.com",
                "domain:cloudflareclient.com"
            ),
            blockSites = listOf("geosite:category-ads-all")
        )
    )

    fun byId(id: String?): RoutingProfile? =
        defaults().firstOrNull { it.id == id?.trim() }

    fun resolve(overrides: Map<String, RoutingProfile>): List<RoutingProfile> =
        defaults().map { default ->
            default.id?.let { overrides[it] }?.copy(id = default.id, builtin = true) ?: default
        }

    fun idForLegacyName(name: String?): String? {
        val normalized = name?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return defaults().firstOrNull { it.name.equals(normalized, ignoreCase = true) }?.id
            ?: if (normalized.equals("RosKomVPN", ignoreCase = true)) ROSCOMVPN else null
    }

    fun ruleCount(profile: RoutingProfile): Int = listOf(
        profile.directSites,
        profile.directIp,
        profile.proxySites,
        profile.proxyIp,
        profile.blockSites,
        profile.blockIp
    ).sumOf { it?.size ?: 0 }

    private fun profile(
        id: String,
        name: String,
        description: String,
        domainStrategy: String,
        globalProxy: Boolean = true,
        ruleOrder: String = "block-proxy-direct",
        localDnsIp: String = "8.8.8.8",
        directSites: List<String> = emptyList(),
        directIp: List<String> = emptyList(),
        proxySites: List<String> = emptyList(),
        proxyIp: List<String> = emptyList(),
        blockSites: List<String> = emptyList(),
        blockIp: List<String> = emptyList()
    ) = RoutingProfile(
        id = id,
        name = name,
        description = description,
        builtin = true,
        ruleOrder = ruleOrder,
        globalProxy = globalProxy.toString(),
        bypassLocalIp = "true",
        remoteDNSType = "DoH",
        remoteDNSDomain = "https://1.1.1.1/dns-query",
        remoteDNSIP = "1.1.1.1",
        domesticDNSType = "DoH",
        domesticDNSDomain = "https://dns.google/dns-query",
        domesticDNSIP = localDnsIp,
        directSites = directSites,
        directIp = directIp,
        proxySites = proxySites,
        proxyIp = proxyIp,
        blockSites = blockSites,
        blockIp = blockIp,
        domainStrategy = domainStrategy,
        fakeDNS = "false"
    )
}
