package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import org.json.JSONObject
import java.net.IDN
import java.net.InetAddress
import java.net.URLEncoder

/** TLS identity and dial address are distinct for share links carrying peer/SNI. */
internal object NaiveProxyConfig {
    fun peer(server: Server): String {
        val value = server.sni?.takeIf { it.isNotBlank() } ?: server.host
        if (value == server.host) return value.removePrefix("[").removeSuffix("]")
        val ascii = runCatching { IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
        require(!ascii.isNullOrBlank() && ascii.length <= 253 &&
            ascii.split('.').all { it.isNotEmpty() && it.length <= 63 }) { "Некорректное TLS-имя NaiveProxy" }
        return ascii
    }

    fun build(server: Server, localPort: Int, dialAddress: String = server.host): JSONObject {
        require(localPort in 1..65535 && server.port in 1..65535) { "Некорректный порт NaiveProxy" }
        val username = server.naiveUsername?.takeIf(String::isNotBlank) ?: server.uuid
        val password = server.naivePassword?.takeIf(String::isNotBlank) ?: error("NaiveProxy password is missing")
        require(username.isNotBlank()) { "NaiveProxy username is missing" }
        val scheme = if (server.naiveTransport.equals("quic", true) || server.network.equals("quic", true)) "quic" else "https"
        val tlsHost = peer(server)
        val uriHost = if (tlsHost.contains(':')) "[$tlsHost]" else tlsHost
        val result = JSONObject()
            .put("listen", "socks://127.0.0.1:$localPort")
            .put("proxy", "$scheme://${encode(username)}:${encode(password)}@$uriHost:${server.port}")
        if (!tlsHost.equals(server.host, ignoreCase = true)) {
            // Chromium resolver-rule values must be literals, never an untrusted rule fragment.
            val address = dialAddress.removePrefix("[").removeSuffix("]")
            val ipv6 = ':' in address && address.matches(Regex("[0-9A-Fa-f:]+"))
            val ipv4 = address.split('.').let { parts ->
                parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) && (part.toIntOrNull() ?: -1) in 0..255 }
            }
            require(ipv4 || ipv6) { "NaiveProxy dial address must be an IP literal" }
            val numeric = InetAddress.getByName(address).hostAddress ?: error("Invalid NaiveProxy address")
            val target = if (numeric.contains(':')) "[$numeric]" else numeric
            result.put("host-resolver-rules", "MAP $tlsHost $target")
        }
        return result
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
