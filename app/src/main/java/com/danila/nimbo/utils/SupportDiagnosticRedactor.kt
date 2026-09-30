package com.danila.nimbo.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Fail-closed schema projection. Never export arbitrary free text or map keys. */
internal object SupportDiagnosticRedactor {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val enums = mapOf(
        "type" to setOf("select", "smart", "url-test", "fallback", "load-balance", "http", "file", "inline", "vless", "vmess", "trojan", "ss", "hysteria", "hysteria2", "tuic", "wireguard", "mieru", "anytls", "socks5", "direct", "reject", "field"),
        "protocol" to setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http", "dns", "freedom", "blackhole", "wireguard", "hysteria"),
        "network" to setOf("tcp", "udp", "ws", "grpc", "xhttp", "h2", "httpupgrade", "quic", "raw", "kcp"),
        "security" to setOf("none", "tls", "reality", "auto", "aes-128-gcm", "chacha20-poly1305"),
        "mode" to setOf("rule", "global", "direct", "auto", "packet-up", "stream-up", "stream-one"),
        "flow" to setOf("xtls-rprx-vision", ""),
        "strategy" to setOf("round-robin", "consistent-hashing", "sticky-sessions", "leastPing", "leastLoad"),
        "stack" to setOf("system", "gvisor", "mixed"),
        "log-level" to setOf("silent", "error", "warning", "info", "debug"),
        "enhanced-mode" to setOf("fake-ip", "redir-host"),
        "find-process-mode" to setOf("off", "strict", "always")
    )
    private val numbers = setOf("interval", "timeout", "tolerance", "mtu", "concurrency", "max-failed-times", "keep-alive-idle", "keep-alive-interval", "upload", "download", "total", "expire", "port", "mixed-port", "socks-port", "redir-port", "tproxy-port", "alterId")
    private val fields = numbers + enums.keys + setOf(
        "proxies", "proxy-groups", "proxy-providers", "rule-providers", "rules", "sub-rules", "dns", "tun", "hosts", "name", "server", "uuid", "password", "private-key", "public-key", "preshared-key", "short-id", "reality-opts", "tls", "tlsSettings", "realitySettings", "streamSettings", "grpcSettings", "wsSettings", "xhttpSettings", "settings", "outbounds", "inbounds", "routing", "balancers", "selector", "users", "vnext", "servers", "id", "tag", "outboundTag", "balancerTag", "nameserver", "default-nameserver", "proxy-server-nameserver", "nameserver-policy", "fallback-filter", "fallback", "url", "path", "header", "headers", "authentication", "secret", "sni", "servername", "serverName", "client-fingerprint", "fingerprint", "use", "proxy", "lazy", "enable", "udp", "ipv6", "allow-lan", "tcp-concurrent", "unified-delay", "skip-cert-verify", "geodata-mode", "geo-auto-update", "respect-rules", "auto-route", "auto-detect-interface", "sniffer", "sniff", "sniffing", "destOverride", "enabled", "profile", "store-selected", "store-fake-ip", "available"
    )

    fun config(source: String): String = if (source.length > 4 * 1024 * 1024) "{\"available\":false}" else runCatching {
        var count = 0
        fun project(value: JsonElement, key: String, depth: Int): JsonElement {
            if (++count > 100_000 || depth > 48) return JsonPrimitive("[redacted]")
            if (value.isJsonObject) return JsonObject().apply {
                value.asJsonObject.entrySet().forEachIndexed { i, (k, v) ->
                    add(if (k in fields) k else "redacted-field-$i", project(v, k, depth + 1))
                }
            }
            if (value.isJsonArray) return JsonArray().apply { value.asJsonArray.forEach { add(project(it, key, depth + 1)) } }
            if (value.isJsonNull) return value
            val scalar = value.asJsonPrimitive
            if (scalar.isBoolean || (scalar.isNumber && key in numbers)) return scalar
            if (scalar.isString && scalar.asString in enums[key].orEmpty()) return scalar
            return JsonPrimitive("[redacted]")
        }
        gson.toJson(project(JsonParser.parseString(source), "", 0))
    }.getOrDefault("{\"available\":false}")

    fun headers(headers: Map<String, List<String>>): String {
        val known = setOf("content-type", "content-length", "subscription-userinfo", "profile-title", "profile-update-interval", "support-url", "profile-web-page-url", "nimbo-logo", "nimbo-theme", "nimbo-fallback", "nimbo-mirrors", "content-disposition", "authorization", "set-cookie")
        return gson.toJson(JsonObject().apply {
            headers.entries.forEachIndexed { i, (key, values) ->
                val name = key.lowercase(java.util.Locale.ROOT)
                addProperty(if (name in known) name else "redacted-header-$i", "[redacted: ${values.size} value(s)]")
            }
        })
    }
}
