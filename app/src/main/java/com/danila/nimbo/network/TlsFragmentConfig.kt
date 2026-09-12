package com.danila.nimbo.network

/**
 * Validated provider contract for Xray TLS ClientHello fragmentation.
 *
 * Preferred header:
 * `Nimbo-TLS-Fragment: enabled=true; packets=tlshello; length=100-200; interval=10-20`
 *
 * Compact form is also accepted:
 * `Nimbo-TLS-Fragment: tlshello,100-200,10-20`
 */
data class TlsFragmentConfig(
    val enabled: Boolean,
    val packets: String = DEFAULT_PACKETS,
    val length: String = DEFAULT_LENGTH,
    val interval: String = DEFAULT_INTERVAL
) {
    companion object {
        const val DEFAULT_PACKETS = "tlshello"
        const val DEFAULT_LENGTH = "100-200"
        const val DEFAULT_INTERVAL = "10-20"

        fun parse(raw: String?): TlsFragmentConfig? {
            val value = raw?.trim()?.trim('"')?.trim().orEmpty()
            if (value.isBlank()) return null
            when (value.lowercase()) {
                "off", "false", "disabled", "0" -> return TlsFragmentConfig(enabled = false)
                "on", "true", "enabled", "1" -> return TlsFragmentConfig(enabled = true)
            }

            return if ('=' in value) parseKeyValue(value) else parseCompact(value)
        }

        private fun parseKeyValue(value: String): TlsFragmentConfig? {
            val entries = linkedMapOf<String, String>()
            for (part in value.split(';')) {
                val token = part.trim()
                if (token.isEmpty()) continue
                val separator = token.indexOf('=')
                if (separator <= 0 || separator == token.lastIndex) return null
                entries[token.substring(0, separator).trim().lowercase()] =
                    token.substring(separator + 1).trim()
            }

            val enabledRaw = entries["enabled"]
            val enabled = if (enabledRaw == null) true else parseBoolean(enabledRaw) ?: return null
            if (!enabled) return TlsFragmentConfig(enabled = false)
            val packets = normalizePackets(entries["packets"] ?: DEFAULT_PACKETS) ?: return null
            val length = normalizeRange(entries["length"] ?: DEFAULT_LENGTH, 1, 1_024) ?: return null
            val interval = normalizeRange(entries["interval"] ?: DEFAULT_INTERVAL, 0, 1_000) ?: return null
            return TlsFragmentConfig(true, packets, length, interval)
        }

        private fun parseCompact(value: String): TlsFragmentConfig? {
            val parts = value.split(',').map(String::trim)
            if (parts.size !in 1..3 || parts.any(String::isBlank)) return null
            val packets = normalizePackets(parts[0]) ?: return null
            val length = normalizeRange(parts.getOrElse(1) { DEFAULT_LENGTH }, 1, 1_024) ?: return null
            val interval = normalizeRange(parts.getOrElse(2) { DEFAULT_INTERVAL }, 0, 1_000) ?: return null
            return TlsFragmentConfig(true, packets, length, interval)
        }

        private fun parseBoolean(value: String): Boolean? = when (value.trim().lowercase()) {
            "true", "on", "enabled", "1" -> true
            "false", "off", "disabled", "0" -> false
            else -> null
        }

        private fun normalizePackets(value: String): String? {
            if (value.trim().equals(DEFAULT_PACKETS, ignoreCase = true)) return DEFAULT_PACKETS
            return normalizeRange(value, 1, 1_024)
        }

        private fun normalizeRange(value: String, minimum: Int, maximum: Int): String? {
            val match = RANGE.matchEntire(value.trim()) ?: return null
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val second = match.groupValues[2].takeIf(String::isNotEmpty)?.toIntOrNull() ?: first
            if (first !in minimum..maximum || second !in minimum..maximum) return null
            return "${minOf(first, second)}-${maxOf(first, second)}"
        }

        private val RANGE = Regex("^(\\d{1,4})(?:-(\\d{1,4}))?$")
    }
}
