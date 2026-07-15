package com.danila.nimbo.utils

object LogSanitizer {
    private val uri = Regex(
        """(?i)\b(?:https?|vless|vmess|trojan|ss|hysteria2?)://[^\s]+"""
    )
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?\b""")
    private val ipv6 = Regex("""\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}(?::\d+)?\b""")
    private val host = Regex(
        """\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(?::\d+)?\b""",
        RegexOption.IGNORE_CASE
    )
    private val uuid = Regex(
        """\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"""
    )
    private val jwt = Regex(
        """\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"""
    )
    private val secretAssignment = Regex(
        """(?i)\b(token|access_token|apikey|api_key|authorization|auth|password|passwd|secret|private_key|public_key|short_id|uuid)\s*[:=]\s*["']?([^\s,"';}]+)"""
    )
    private val jsonSecret = Regex(
        """(?i)"(id|uuid|password|token|authorization|privateKey|publicKey|shortId)"\s*:\s*"[^"]*""""
    )
    private val longToken = Regex("""\b[A-Za-z0-9_+/=-]{32,}\b""")

    fun sanitize(input: String): String {
        return input
            .replace(uri, "[URL]")
            .replace(jwt, "[JWT]")
            .replace(ipv6, "[IPV6]")
            .replace(ipv4, "[IP]")
            .replace(host, "[HOST]")
            .replace(uuid, "[UUID]")
            .replace(jsonSecret) { match -> """"${match.groupValues[1]}":"[REDACTED]"""" }
            .replace(secretAssignment) { match -> "${match.groupValues[1]}=[REDACTED]" }
            .replace(longToken, "[TOKEN]")
    }
}
