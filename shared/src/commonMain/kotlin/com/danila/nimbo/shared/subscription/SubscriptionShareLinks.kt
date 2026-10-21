package com.danila.nimbo.shared.subscription

/** Extract links without guessing unsupported schemes or losing NaiveProxy nodes. */
object SubscriptionShareLinks {
    private val scheme = "(?:vless|vmess|trojan|ss|ssr|hysteria2|hysteria|hy2|hy|tuic|mieru|naive(?:\\+https|\\+quic)?|wg|wireguard|awg|amneziawg|(?<![A-Za-z0-9+.-])tt)"
    private val links = Regex("(?i)$scheme://[^\\s<>\"']+")

    fun extract(text: String): List<String> = links.findAll(text)
        .map { match ->
            var value = match.value.trimEnd(',', ';', ')', '}')
            while (value.endsWith(']') && value.count { it == ']' } > value.count { it == '[' }) value = value.dropLast(1)
            value
        }.distinct().toList()
}
