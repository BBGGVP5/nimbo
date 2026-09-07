package com.danila.nimbo.network

import java.net.IDN
import java.net.URI

internal data class ParsedConnectivityTarget(
    val host: String,
    val port: Int,
    val url: String
)

internal object ConnectivityProbePolicy {
    fun parseTarget(raw: String): ParsedConnectivityTarget? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.any(Char::isWhitespace)) return null

        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.userInfo != null) return null

        val rawHost = uri.host?.trim()?.removeSurrounding("[", "]") ?: return null
        val host = runCatching { IDN.toASCII(rawHost) }.getOrNull()?.lowercase() ?: return null
        if (!isValidHost(host)) return null

        val port = when {
            uri.port in 1..65535 -> uri.port
            uri.port != -1 -> return null
            uri.scheme == "http" -> 80
            else -> 443
        }
        return ParsedConnectivityTarget(host = host, port = port, url = uri.toASCIIString())
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253) return false
        if (':' in host) return host.matches(Regex("[0-9a-fA-F:]+"))
        return host.split('.').all { label ->
            label.isNotBlank() &&
                label.length <= 63 &&
                !label.startsWith('-') &&
                !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
