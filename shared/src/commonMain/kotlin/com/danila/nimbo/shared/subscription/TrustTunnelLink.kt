package com.danila.nimbo.shared.subscription

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * TrustTunnel (AdGuard) endpoint decoded from a `tt://?<base64url TLV>` deep
 * link, format version 2. Spec: TrustTunnel/TrustTunnel DEEP_LINK.md.
 * The same decoder serves Android and iOS (through NimboShared).
 */
data class TrustTunnelEndpoint(
    val hostname: String,
    /** `host:port` entries in link order; IPv6 hosts are bracketed. */
    val addresses: List<String>,
    val customSni: String? = null,
    val hasIpv6: Boolean = true,
    val username: String,
    val password: String,
    val skipVerification: Boolean = false,
    /** PEM chain to pin instead of system CAs. */
    val certificatePem: String? = null,
    val http3: Boolean = false,
    val antiDpi: Boolean = false,
    val clientRandomPrefix: String? = null,
    val clientRandomAuthKey: String? = null,
    val name: String? = null,
    val dnsUpstreams: List<String> = emptyList(),
    val subscriptionUrl: String? = null
) {
    /** SNI sent in the ClientHello. */
    val tlsServerName: String get() = customSni?.takeIf { it.isNotBlank() } ?: hostname

    val host: String get() = TrustTunnelLink.splitHostPort(addresses.first()).first

    val port: Int get() = TrustTunnelLink.splitHostPort(addresses.first()).second

    /**
     * Link options the mobile client (Mihomo's TrustTunnel transport) cannot
     * honour. Shown to the user instead of silently ignoring them.
     */
    val unsupportedOnMobile: List<String>
        get() = buildList {
            if (antiDpi) add("anti-DPI")
            if (!clientRandomPrefix.isNullOrBlank() || !clientRandomAuthKey.isNullOrBlank()) add("client_random")
        }
}

object TrustTunnelLink {
    const val PROTOCOL = "trusttunnel"
    const val MAX_VERSION = 2L

    private const val TAG_VERSION = 0x00L
    private const val TAG_HOSTNAME = 0x01L
    private const val TAG_ADDRESSES = 0x02L
    private const val TAG_CUSTOM_SNI = 0x03L
    private const val TAG_HAS_IPV6 = 0x04L
    private const val TAG_USERNAME = 0x05L
    private const val TAG_PASSWORD = 0x06L
    private const val TAG_SKIP_VERIFICATION = 0x07L
    private const val TAG_CERTIFICATE = 0x08L
    private const val TAG_UPSTREAM_PROTOCOL = 0x09L
    private const val TAG_ANTI_DPI = 0x0AL
    private const val TAG_CLIENT_RANDOM_PREFIX = 0x0BL
    private const val TAG_NAME = 0x0CL
    private const val TAG_DNS_UPSTREAMS = 0x0DL
    private const val TAG_SUBSCRIPTION_URL = 0x0EL
    private const val TAG_CLIENT_RANDOM_AUTH_KEY = 0x0FL

    private val json = Json { ignoreUnknownKeys = true }

    fun isLink(value: String): Boolean = value.trimStart().startsWith("tt://", ignoreCase = true)

    /** Subscription URL of a format-v2 link, or null. Never throws. */
    fun subscriptionUrl(link: String): String? = runCatching { decodeRaw(link).subscriptionUrl }.getOrNull()

    /**
     * Decodes a link with static connection parameters. Throws
     * [IllegalArgumentException] with a user-facing reason otherwise.
     */
    fun decode(link: String): TrustTunnelEndpoint {
        val raw = decodeRaw(link)
        val hostname = raw.hostname?.trim().orEmpty()
        val username = raw.username?.trim().orEmpty()
        val password = raw.password.orEmpty()
        if (hostname.isEmpty() || raw.addresses.isEmpty() || username.isEmpty() || password.isEmpty()) {
            require(raw.subscriptionUrl == null) {
                "Ссылка TrustTunnel содержит только адрес подписки — добавьте её как подписку"
            }
            throw IllegalArgumentException("В ссылке TrustTunnel нет адреса, логина или пароля")
        }
        return TrustTunnelEndpoint(
            hostname = hostname,
            addresses = raw.addresses.map(::normalizeAddress).distinct(),
            customSni = raw.customSni?.trim()?.takeIf(String::isNotEmpty),
            hasIpv6 = raw.hasIpv6 ?: true,
            username = username,
            password = password,
            skipVerification = raw.skipVerification,
            certificatePem = raw.certificateDer?.let(::derChainToPem),
            http3 = raw.http3,
            antiDpi = raw.antiDpi,
            clientRandomPrefix = raw.clientRandomPrefix?.trim()?.takeIf(String::isNotEmpty),
            clientRandomAuthKey = raw.clientRandomAuthKey?.trim()?.takeIf(String::isNotEmpty),
            name = fragmentName(link) ?: raw.name?.trim()?.takeIf(String::isNotEmpty),
            dnsUpstreams = raw.dnsUpstreams,
            subscriptionUrl = raw.subscriptionUrl
        )
    }

    fun decodeOrNull(link: String): TrustTunnelEndpoint? = runCatching { decode(link) }.getOrNull()

    fun displayName(endpoint: TrustTunnelEndpoint): String = endpoint.name ?: "TrustTunnel ${endpoint.hostname}"

    /** True for the per-user JSON served by an endpoint's `[subscription]`. */
    fun isSubscriptionJson(root: JsonObject): Boolean =
        listOf("hostname", "address", "username", "password").all { (root[it] as? JsonPrimitive)?.isString == true } &&
            root.containsKey("upstream_protocol")

    /** Converts the endpoint subscription JSON into an equivalent deep link. */
    fun fromSubscriptionJson(root: JsonObject): String? {
        if (!isSubscriptionJson(root)) return null
        fun text(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        fun flag(key: String, default: Boolean) = (root[key] as? JsonPrimitive)?.booleanOrNull ?: default
        val certificate = text("certificate")?.let { value ->
            if (value.contains("-----BEGIN")) value else decodeBase64(value, urlSafe = false)?.let(::derChainToPem)
        }
        val endpoint = TrustTunnelEndpoint(
            hostname = text("hostname") ?: return null,
            addresses = listOf(normalizeAddress(text("address") ?: return null)),
            customSni = text("custom_sni"),
            hasIpv6 = flag("has_ipv6", true),
            username = text("username") ?: return null,
            password = (root["password"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotEmpty) ?: return null,
            skipVerification = flag("skip_verification", false),
            certificatePem = certificate,
            http3 = text("upstream_protocol").equals("http3", ignoreCase = true),
            antiDpi = flag("anti_dpi", false),
            clientRandomPrefix = text("client_random_prefix"),
            name = text("name"),
            dnsUpstreams = (root["dns_upstreams"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty()
        )
        return encode(endpoint)
    }

    fun fromSubscriptionJson(body: String): String? =
        (runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() as? JsonObject)?.let(::fromSubscriptionJson)

    /**
     * Request for the Go bridge (`nimbo/trusttunnelcore`). [server] must be an
     * already resolved `ip:port`: resolving inside the tunnel could loop.
     */
    fun bridgeRequest(
        endpoint: TrustTunnelEndpoint,
        server: String,
        socksUsername: String = "",
        socksPassword: String = "",
        timeoutMs: Int = 10_000
    ): String = buildJsonObject {
        put("server", server)
        put("sni", endpoint.tlsServerName)
        put("verifyName", endpoint.hostname)
        put("username", endpoint.username)
        put("password", endpoint.password)
        put("http3", endpoint.http3)
        put("skipVerification", endpoint.skipVerification)
        endpoint.certificatePem?.let { put("certificate", it) }
        put("listen", "127.0.0.1:0")
        put("socksUsername", socksUsername)
        put("socksPassword", socksPassword)
        put("timeoutMs", timeoutMs)
    }.toString()

    /** Human text for a bridge error code. */
    fun bridgeErrorMessage(code: String?, endpoint: TrustTunnelEndpoint?): String {
        val base = when (code) {
            "AUTH_REJECTED" -> "Сервер TrustTunnel отклонил логин или пароль"
            "TLS_VERIFY" -> "Сертификат сервера TrustTunnel не прошёл проверку"
            "TIMEOUT" -> "Сервер TrustTunnel не ответил вовремя"
            "UNREACHABLE" -> "Сервер TrustTunnel недоступен"
            "LISTEN" -> "Не удалось открыть локальный порт TrustTunnel"
            "REQUEST", "CONFIG" -> "Некорректные параметры TrustTunnel"
            else -> "Не удалось подключиться к TrustTunnel"
        }
        val unsupported = endpoint?.unsupportedOnMobile.orEmpty()
        return if (unsupported.isEmpty()) base
        else "$base. На телефоне не поддерживается: ${unsupported.joinToString()}; возможно, сервер их требует"
    }

    fun encode(endpoint: TrustTunnelEndpoint): String {
        val out = ArrayList<Byte>()
        fun field(tag: Long, value: ByteArray) {
            writeVarint(out, tag)
            writeVarint(out, value.size.toLong())
            value.forEach(out::add)
        }
        field(TAG_VERSION, byteArrayOf(MAX_VERSION.toByte()))
        field(TAG_HOSTNAME, endpoint.hostname.encodeToByteArray())
        endpoint.addresses.forEach { field(TAG_ADDRESSES, it.encodeToByteArray()) }
        endpoint.customSni?.let { field(TAG_CUSTOM_SNI, it.encodeToByteArray()) }
        if (!endpoint.hasIpv6) field(TAG_HAS_IPV6, byteArrayOf(0))
        field(TAG_USERNAME, endpoint.username.encodeToByteArray())
        field(TAG_PASSWORD, endpoint.password.encodeToByteArray())
        if (endpoint.skipVerification) field(TAG_SKIP_VERIFICATION, byteArrayOf(1))
        endpoint.certificatePem?.let(::pemChainToDer)?.let { field(TAG_CERTIFICATE, it) }
        field(TAG_UPSTREAM_PROTOCOL, byteArrayOf(if (endpoint.http3) 2 else 1))
        if (endpoint.antiDpi) field(TAG_ANTI_DPI, byteArrayOf(1))
        endpoint.clientRandomPrefix?.let { field(TAG_CLIENT_RANDOM_PREFIX, it.encodeToByteArray()) }
        endpoint.name?.let { field(TAG_NAME, it.encodeToByteArray()) }
        if (endpoint.dnsUpstreams.isNotEmpty()) {
            val list = ArrayList<Byte>()
            endpoint.dnsUpstreams.forEach { dns ->
                val bytes = dns.encodeToByteArray()
                writeVarint(list, bytes.size.toLong())
                bytes.forEach(list::add)
            }
            field(TAG_DNS_UPSTREAMS, list.toByteArray())
        }
        endpoint.subscriptionUrl?.let { field(TAG_SUBSCRIPTION_URL, it.encodeToByteArray()) }
        endpoint.clientRandomAuthKey?.let { field(TAG_CLIENT_RANDOM_AUTH_KEY, it.encodeToByteArray()) }
        return "tt://?" + encodeBase64(out.toByteArray(), urlSafe = true, pad = false)
    }

    // ---- TLV ----

    private class Raw {
        var version = 0L
        var hostname: String? = null
        val addresses = mutableListOf<String>()
        var customSni: String? = null
        var hasIpv6: Boolean? = null
        var username: String? = null
        var password: String? = null
        var skipVerification = false
        var certificateDer: ByteArray? = null
        var http3 = false
        var antiDpi = false
        var clientRandomPrefix: String? = null
        var name: String? = null
        var dnsUpstreams: List<String> = emptyList()
        var subscriptionUrl: String? = null
        var clientRandomAuthKey: String? = null
    }

    private fun decodeRaw(link: String): Raw {
        val trimmed = link.trim()
        require(isLink(trimmed)) { "Это не ссылка TrustTunnel" }
        var payload = trimmed.substring(5).substringBefore('#')
        payload = payload.removePrefix("?").filterNot(Char::isWhitespace).trimEnd('=')
        require(payload.isNotEmpty()) { "Пустая ссылка TrustTunnel" }
        val bytes = decodeBase64(payload, urlSafe = true) ?: decodeBase64(payload, urlSafe = false)
            ?: throw IllegalArgumentException("Ссылка TrustTunnel повреждена")
        val raw = Raw()
        var cursor = 0
        while (cursor < bytes.size) {
            val (tag, afterTag) = readVarint(bytes, cursor)
            val (length, afterLength) = readVarint(bytes, afterTag)
            require(length in 0..(bytes.size - afterLength).toLong()) { "Ссылка TrustTunnel обрезана" }
            val value = bytes.copyOfRange(afterLength, afterLength + length.toInt())
            cursor = afterLength + length.toInt()
            when (tag) {
                TAG_VERSION -> raw.version = readVarint(value, 0).first
                TAG_HOSTNAME -> raw.hostname = value.decodeToString()
                TAG_ADDRESSES -> value.decodeToString().trim().takeIf(String::isNotEmpty)?.let(raw.addresses::add)
                TAG_CUSTOM_SNI -> raw.customSni = value.decodeToString()
                TAG_HAS_IPV6 -> raw.hasIpv6 = bool(value)
                TAG_USERNAME -> raw.username = value.decodeToString()
                TAG_PASSWORD -> raw.password = value.decodeToString()
                TAG_SKIP_VERIFICATION -> raw.skipVerification = bool(value)
                TAG_CERTIFICATE -> raw.certificateDer = value.takeIf { it.isNotEmpty() }
                TAG_UPSTREAM_PROTOCOL -> raw.http3 = readVarint(value, 0).first == 2L
                TAG_ANTI_DPI -> raw.antiDpi = bool(value)
                TAG_CLIENT_RANDOM_PREFIX -> raw.clientRandomPrefix = value.decodeToString()
                TAG_NAME -> raw.name = value.decodeToString()
                TAG_DNS_UPSTREAMS -> raw.dnsUpstreams = stringArray(value)
                TAG_SUBSCRIPTION_URL -> {
                    val url = value.decodeToString().trim()
                    require(url.startsWith("https://", ignoreCase = true)) { "Подписка TrustTunnel должна быть по https" }
                    raw.subscriptionUrl = url
                }
                TAG_CLIENT_RANDOM_AUTH_KEY -> raw.clientRandomAuthKey = value.decodeToString()
                else -> Unit // Unknown tags are forward-compatible extensions.
            }
        }
        require(raw.version <= MAX_VERSION) { "Ссылка TrustTunnel новой версии ${raw.version} пока не поддерживается" }
        return raw
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        require(start < bytes.size) { "Ссылка TrustTunnel обрезана" }
        val first = bytes[start].toInt() and 0xff
        val size = 1 shl (first ushr 6)
        require(start + size <= bytes.size) { "Ссылка TrustTunnel обрезана" }
        var value = (first and 0x3f).toLong()
        for (i in 1 until size) value = (value shl 8) or (bytes[start + i].toLong() and 0xff)
        return value to start + size
    }

    private fun writeVarint(out: MutableList<Byte>, value: Long) {
        when {
            value < (1L shl 6) -> out.add(value.toByte())
            value < (1L shl 14) -> {
                out.add(((value ushr 8) or 0x40).toByte()); out.add(value.toByte())
            }
            else -> {
                out.add(((value ushr 24) or 0x80).toByte()); out.add((value ushr 16).toByte())
                out.add((value ushr 8).toByte()); out.add(value.toByte())
            }
        }
    }

    private fun bool(value: ByteArray): Boolean {
        require(value.size == 1) { "Ссылка TrustTunnel повреждена" }
        return value[0].toInt() != 0
    }

    private fun stringArray(value: ByteArray): List<String> {
        val out = mutableListOf<String>()
        var cursor = 0
        while (cursor < value.size) {
            val (length, start) = readVarint(value, cursor)
            require(length in 0..(value.size - start).toLong()) { "Ссылка TrustTunnel повреждена" }
            value.copyOfRange(start, start + length.toInt()).decodeToString().trim()
                .takeIf(String::isNotEmpty)?.let(out::add)
            cursor = start + length.toInt()
        }
        return out
    }

    private fun fragmentName(link: String): String? {
        val fragment = link.substringAfter('#', "").takeIf(String::isNotEmpty) ?: return null
        val decoded = percentDecode(fragment)
        return decoded.substringBefore("?serverDescription=").trim().takeIf(String::isNotEmpty)
    }

    private fun percentDecode(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte()); i += 3; continue
                }
            }
            c.toString().encodeToByteArray().forEach(bytes::add)
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    // ---- addresses ----

    fun splitHostPort(raw: String, defaultPort: Int = 443): Pair<String, Int> {
        val value = raw.trim()
        if (value.startsWith("[")) {
            val host = value.substring(1).substringBefore(']')
            val port = value.substringAfter("]:", "").toIntOrNull() ?: defaultPort
            return host to port
        }
        val colons = value.count { it == ':' }
        if (colons == 1) {
            val port = value.substringAfter(':').toIntOrNull()
            require(port != null && port in 1..65535) { "Некорректный порт TrustTunnel" }
            return value.substringBefore(':') to port
        }
        return value to defaultPort
    }

    private fun normalizeAddress(raw: String): String {
        val (host, port) = splitHostPort(raw)
        require(host.isNotBlank()) { "В ссылке TrustTunnel нет адреса сервера" }
        return if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }

    // ---- certificates ----

    fun derChainToPem(der: ByteArray): String {
        val out = StringBuilder()
        var cursor = 0
        while (cursor < der.size) {
            val length = derElementLength(der, cursor) ?: (der.size - cursor)
            val end = minOf(cursor + length, der.size)
            val body = encodeBase64(der.copyOfRange(cursor, end), urlSafe = false, pad = true)
            out.append("-----BEGIN CERTIFICATE-----\n")
            body.chunked(64).forEach { out.append(it).append('\n') }
            out.append("-----END CERTIFICATE-----\n")
            cursor = end
        }
        return out.toString()
    }

    private fun derElementLength(der: ByteArray, start: Int): Int? {
        if ((der[start].toInt() and 0xff) != 0x30 || start + 1 >= der.size) return null
        val first = der[start + 1].toInt() and 0xff
        if (first < 0x80) return 2 + first
        val count = first and 0x7f
        if (count == 0 || count > 4 || start + 2 + count > der.size) return null
        var length = 0
        for (i in 0 until count) length = (length shl 8) or (der[start + 2 + i].toInt() and 0xff)
        return 2 + count + length
    }

    private fun pemChainToDer(pem: String): ByteArray? {
        val out = ArrayList<Byte>()
        val body = StringBuilder()
        var inside = false
        for (line in pem.lines().map(String::trim)) {
            when {
                line.startsWith("-----BEGIN") -> { inside = true; body.clear() }
                line.startsWith("-----END") -> {
                    inside = false
                    decodeBase64(body.toString(), urlSafe = false)?.forEach(out::add) ?: return null
                }
                inside -> body.append(line)
            }
        }
        return out.takeIf { it.isNotEmpty() }?.toByteArray()
    }

    // ---- base64 (no platform APIs in commonMain) ----

    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun decodeBase64(value: String, urlSafe: Boolean): ByteArray? {
        val alphabet = if (urlSafe) URL_SAFE else STANDARD
        val clean = value.filterNot(Char::isWhitespace).trimEnd('=')
        if (clean.length % 4 == 1) return null
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val index = alphabet.indexOf(c)
            if (index < 0) return null
            buffer = (buffer shl 6) or index
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun encodeBase64(bytes: ByteArray, urlSafe: Boolean, pad: Boolean): String {
        val alphabet = if (urlSafe) URL_SAFE else STANDARD
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else -1
            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 3) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            if (b1 >= 0) out.append(alphabet[((b1 and 15) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)]) else if (pad) out.append('=')
            if (b2 >= 0) out.append(alphabet[b2 and 63]) else if (pad) out.append('=')
            i += 3
        }
        return out.toString()
    }
}
