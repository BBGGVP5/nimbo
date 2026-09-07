package com.danila.nimbo.shared.routing

/**
 * Профили маршрутизации — те же, что на Android.
 *
 * Профиль отвечает на один вопрос: что идёт через VPN, а что напрямую. Модули
 * ([NimboModule]) добавляют к этому отдельные правила, но поведение по
 * умолчанию задаёт именно профиль, поэтому он и хранится отдельно.
 *
 * Наборы и их значения повторяют `BuiltinRoutingProfiles` на Android: один и
 * тот же профиль на двух устройствах обязан вести себя одинаково.
 */
data class NimboRoutingProfile(
    val id: String,
    val name: String,
    val description: String,
    val builtin: Boolean = false,
    /** Порядок применения: block-proxy-direct или block-direct-proxy. */
    val ruleOrder: String = "block-proxy-direct",
    /** Весь прочий трафик через VPN или напрямую. */
    val globalProxy: Boolean = true,
    /** Локальные адреса всегда напрямую: принтеры, NAS и роутер. */
    val bypassLocalIp: Boolean = true,
    val domainStrategy: String = "IPIfNonMatch",
    val directSites: List<String> = emptyList(),
    val directIp: List<String> = emptyList(),
    val proxySites: List<String> = emptyList(),
    val proxyIp: List<String> = emptyList(),
    val blockSites: List<String> = emptyList(),
    val blockIp: List<String> = emptyList()
) {
    val ruleCount: Int
        get() = directSites.size + directIp.size + proxySites.size +
            proxyIp.size + blockSites.size + blockIp.size
}

/** Правило профиля после раскладки: домены и адреса Xray принимает раздельно. */
data class NimboRoutingRule(
    val domains: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
    /** Пустой набор совпадений — правило для всего остального трафика. */
    val catchAll: Boolean = false,
    val outboundTag: String
)

object NimboBuiltinRoutingProfiles {
    const val GLOBAL = "global"
    const val BYPASS_LAN = "bypass_lan"
    const val CHINA_DIRECT = "china_direct"
    const val RUSSIA_DIRECT = "russia_direct"
    const val ROSCOMVPN = "roscomvpn"

    fun defaults(): List<NimboRoutingProfile> = listOf(
        NimboRoutingProfile(
            id = GLOBAL,
            name = "Глобальный",
            description = "Весь трафик через VPN",
            builtin = true,
            domainStrategy = "AsIs",
            blockSites = listOf("geosite:category-ads")
        ),
        NimboRoutingProfile(
            id = BYPASS_LAN,
            name = "Обход LAN",
            description = "Локальные адреса напрямую",
            builtin = true,
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
        NimboRoutingProfile(
            id = CHINA_DIRECT,
            name = "Китай",
            description = "Китайские сайты напрямую",
            builtin = true,
            domainStrategy = "IPIfNonMatch",
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
        NimboRoutingProfile(
            id = RUSSIA_DIRECT,
            name = "Россия",
            description = "Российские ресурсы напрямую",
            builtin = true,
            domainStrategy = "IPIfNonMatch",
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
        NimboRoutingProfile(
            id = ROSCOMVPN,
            name = "RoscomVPN",
            description = "Заблокированные в РФ ресурсы — через VPN, остальное напрямую",
            builtin = true,
            ruleOrder = "block-direct-proxy",
            globalProxy = false,
            domainStrategy = "IPIfNonMatch",
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

    fun byId(id: String?): NimboRoutingProfile? =
        defaults().firstOrNull { it.id == id?.trim() }
}

/** Раскладывает профиль в правила Xray в том же порядке, что и Android. */
object NimboRoutingProfileRules {
    fun rules(profile: NimboRoutingProfile): List<NimboRoutingRule> {
        val result = mutableListOf<NimboRoutingRule>()
        val order = when (profile.ruleOrder.trim().lowercase()) {
            "block-direct-proxy" -> listOf("block", "direct", "proxy")
            else -> listOf("block", "proxy", "direct")
        }
        order.forEach { kind ->
            when (kind) {
                "block" -> result += rule(profile.blockSites, profile.blockIp, "block")
                "proxy" -> result += rule(profile.proxySites, profile.proxyIp, "proxy")
                else -> result += rule(profile.directSites, profile.directIp, "direct")
            }
        }
        if (profile.bypassLocalIp) {
            result += NimboRoutingRule(ips = listOf("geoip:private"), outboundTag = "direct")
        }
        // Поведение по умолчанию: без этого правила весь неопознанный трафик
        // ушёл бы в первый исходящий, и выбор «напрямую» ничего бы не значил.
        result += NimboRoutingRule(
            catchAll = true,
            outboundTag = if (profile.globalProxy) "proxy" else "direct"
        )
        return result
    }

    private fun rule(
        domains: List<String>,
        ips: List<String>,
        outboundTag: String
    ): List<NimboRoutingRule> {
        val cleanDomains = domains.map { it.trim() }.filter { it.isNotEmpty() }
        // geoip в списке доменов — на самом деле адрес: люди путают поля, и
        // молча потерять такую строку хуже, чем положить её в нужное.
        val normalizedDomains = cleanDomains.filterNot { it.startsWith("geoip:", ignoreCase = true) }
        val normalizedIps = (ips + cleanDomains.filter { it.startsWith("geoip:", ignoreCase = true) })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val out = mutableListOf<NimboRoutingRule>()
        if (normalizedDomains.isNotEmpty()) {
            out += NimboRoutingRule(domains = normalizedDomains, outboundTag = outboundTag)
        }
        if (normalizedIps.isNotEmpty()) {
            out += NimboRoutingRule(ips = normalizedIps, outboundTag = outboundTag)
        }
        return out
    }
}
