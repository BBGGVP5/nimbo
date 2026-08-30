package com.danila.nimbo.shared.subscription

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SubscriptionPayloadParser {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val supportedSchemes = setOf(
        "vless", "vmess", "trojan", "ss", "ssr", "hysteria2", "hy2", "tuic",
        "naive", "naive+https", "naive+quic", "wg", "wireguard", "awg", "amneziawg"
    )

    fun parse(payload: String, source: String? = null): NormalizedSubscription {
        val clean = payload.trim().removePrefix("\uFEFF")
        if (clean.isEmpty()) return emptyResult(source)

        parseJson(clean, source)?.let { return it }

        val plainLinks = extractShareLinks(clean)
        if (plainLinks.isNotEmpty()) {
            return resultFromLinks(plainLinks, source, SubscriptionPayloadFormat.PLAIN_LINKS)
        }

        val decoded = decodeBase64(clean)
        if (decoded != null) {
            parseJson(decoded, source)?.let { parsed ->
                return if (parsed.format == SubscriptionPayloadFormat.JSON_LINKS) parsed else parsed
            }
            val decodedLinks = extractShareLinks(decoded)
            if (decodedLinks.isNotEmpty()) {
                return resultFromLinks(decodedLinks, source, SubscriptionPayloadFormat.BASE64_LINKS)
            }
        }

        return emptyResult(source)
    }

    fun parseToJson(payload: String, source: String? = null): String = json.encodeToString(parse(payload, source))

    internal fun encodeBase64ForTest(value: String, urlSafe: Boolean): String {
        val alphabet = if (urlSafe) URL_SAFE_ALPHABET else STANDARD_ALPHABET
        val bytes = value.encodeToByteArray()
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var index = 0
        while (index < bytes.size) {
            val b0 = bytes[index].toInt() and 0xff
            val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else -1
            val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else -1
            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 3) shl 4) or if (b1 >= 0) (b1 ushr 4) else 0])
            out.append(if (b1 >= 0) alphabet[((b1 and 15) shl 2) or if (b2 >= 0) (b2 ushr 6) else 0] else '=')
            out.append(if (b2 >= 0) alphabet[b2 and 63] else '=')
            index += 3
        }
        return out.toString()
    }

    private fun parseJson(raw: String, source: String?): NormalizedSubscription? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        if (root !is JsonObject && root !is JsonArray) return null

        if (root is JsonObject && root["outbounds"] is JsonArray) {
            val outbound = (root["outbounds"] as JsonArray)
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("protocol").orEmpty() !in setOf("direct", "freedom", "block", "blackhole", "dns") }
                ?: (root["outbounds"] as JsonArray).firstOrNull() as? JsonObject
            val protocol = outbound?.string("protocol").orEmpty().ifBlank { "xray" }
            val endpoint = findXrayEndpoint(outbound)
            val title = root.string("remarks") ?: root.string("name") ?: sourceTitle(source)
            return NormalizedSubscription(
                title = title,
                source = source,
                format = SubscriptionPayloadFormat.XRAY_JSON,
                servers = listOf(
                    NormalizedSubscriptionServer(
                        id = stableId("xray|$protocol|${endpoint.first}|${endpoint.second}|${raw.length}"),
                        name = title,
                        protocol = protocol,
                        host = endpoint.first,
                        port = endpoint.second,
                        rawConfiguration = raw,
                        isNativeXrayJson = true
                    )
                )
            )
        }

        val links = linkedSetOf<String>()
        collectLinks(root, links)
        if (links.isEmpty()) return null
        val title = (root as? JsonObject)?.let { it.string("name") ?: it.string("remarks") } ?: sourceTitle(source)
        return resultFromLinks(links.toList(), source, SubscriptionPayloadFormat.JSON_LINKS, title)
    }

    private fun collectLinks(element: JsonElement, output: MutableSet<String>) {
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.let { value ->
                extractShareLinks(value).forEach(output::add)
            }
            is JsonArray -> element.forEach { collectLinks(it, output) }
            is JsonObject -> element.values.forEach { collectLinks(it, output) }
        }
    }

    private fun resultFromLinks(
        links: List<String>,
        source: String?,
        format: SubscriptionPayloadFormat,
        explicitTitle: String? = null
    ): NormalizedSubscription {
        val servers = links
            .asSequence()
            .mapNotNull(::parseShareLink)
            .distinctBy { it.id }
            .toList()
        return NormalizedSubscription(
            title = explicitTitle?.takeIf(String::isNotBlank) ?: sourceTitle(source),
            source = source,
            format = format,
            servers = servers,
            diagnosticCode = if (servers.isEmpty()) "SUBSCRIPTION_NO_SUPPORTED_NODES" else null
        )
    }

    private fun parseShareLink(raw: String): NormalizedSubscriptionServer? {
        val link = raw.trim().trimEnd(',', ';')
        val scheme = link.substringBefore("://", "").lowercase()
        if (scheme !in supportedSchemes) return null

        if (scheme == "vmess") return parseVmess(link)

        val withoutScheme = link.substringAfter("://")
        val fragmentRaw = withoutScheme.substringAfterLast('#', "")
        val beforeFragment = if (fragmentRaw.isNotEmpty()) withoutScheme.substringBeforeLast('#') else withoutScheme
        val queryRaw = beforeFragment.substringAfter('?', "")
        val authority = beforeFragment.substringBefore('?')
        val hostPort = authority.substringAfterLast('@', authority)
        val host = parseHost(hostPort)
        val port = parsePort(hostPort)
        val params = parseQuery(queryRaw)
        val canonicalProtocol = when (scheme) {
            "hy2" -> "hysteria2"
            "naive+https", "naive+quic" -> "naive"
            "wg" -> "wireguard"
            "amneziawg" -> "awg"
            else -> scheme
        }
        val fallbackName = host.ifBlank { canonicalProtocol.uppercase() }
        val fragment = splitFragment(fragmentRaw)
        val name = fragment.first.ifBlank { fallbackName }
        val transport = when {
            scheme.startsWith("naive+") -> scheme.substringAfter('+')
            else -> params["type"] ?: params["network"] ?: params["net"] ?: ""
        }
        val security = params["security"] ?: params["tls"] ?: when {
            params.containsKey("pbk") || params.containsKey("publickey") -> "reality"
            else -> ""
        }

        return NormalizedSubscriptionServer(
            id = stableId(normalizeForIdentity(link)),
            name = name,
            protocol = canonicalProtocol,
            host = percentDecode(host),
            port = port,
            transport = transport,
            security = security,
            rawConfiguration = link,
            description = fragment.second
        )
    }

    /**
     * Панели дописывают в #fragment хвост вида `?serverDescription=<base64>`.
     * Имя — всё до вопросительного знака, описание достаётся из хвоста и при
     * необходимости декодируется из base64.
     */
    private fun splitFragment(fragmentRaw: String): Pair<String, String> {
        val decoded = percentDecode(fragmentRaw)
        val separator = decoded.indexOf('?')
        if (separator < 0) return decoded.trim() to ""
        val name = decoded.substring(0, separator).trim()
        val params = parseQuery(decoded.substring(separator + 1))
        val raw = params["serverdescription"]
            ?: params["server_description"]
            ?: params["server-description"]
            ?: params["description"]
            ?: ""
        val description = raw.trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let { value -> decodeBase64Text(value) ?: value }
            .orEmpty()
        return (name.ifBlank { decoded.trim() }) to description
    }

    private fun parseVmess(link: String): NormalizedSubscriptionServer? {
        val encoded = link.substringAfter("://").substringBefore('#').trim()
        val decoded = decodeBase64(encoded) ?: return null
        val root = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val host = root.string("add").orEmpty()
        val port = root["port"]?.jsonPrimitive?.intOrNull
            ?: root.string("port")?.toIntOrNull()
            ?: 0
        val fragment = splitFragment(link.substringAfterLast('#', ""))
        val name = root.string("ps")?.let(::percentDecode)?.ifBlank { null }
            ?: fragment.first.ifBlank { host.ifBlank { "VMess" } }
        val transport = root.string("net").orEmpty()
        val security = root.string("tls").orEmpty()
        return NormalizedSubscriptionServer(
            id = stableId(normalizeForIdentity(link)),
            name = name,
            protocol = "vmess",
            host = host,
            port = port,
            transport = transport,
            security = security,
            rawConfiguration = link,
            description = fragment.second
        )
    }

    private fun extractShareLinks(raw: String): List<String> {
        val pattern = Regex("(?i)(?:vless|vmess|trojan|ss|ssr|hysteria2|hy2|tuic|naive(?:\\+https|\\+quic)?|wg|wireguard|awg|amneziawg)://[^\\s<>\\\"]+")
        return pattern.findAll(raw)
            .map { it.value.trim().trimEnd(',', ';', '\'', ')', ']') }
            .distinct()
            .toList()
    }

    /**
     * Описание сервера — обычный текст, в нём нет ни двоеточий, ни скобок,
     * по которым [decodeBase64] узнаёт закодированную подписку. Поэтому у него
     * своя проверка: результат должен быть читаемой строкой без управляющих
     * символов, иначе считаем, что base64 тут и не было.
     */
    private fun decodeBase64Text(raw: String): String? {
        val decoded = decodeBase64(raw, requirePayloadMarkers = false) ?: return null
        val trimmed = decoded.trim()
        if (trimmed.isBlank()) return null
        return trimmed.takeIf { text -> text.none { it.isISOControl() } }
    }

    private fun decodeBase64(raw: String, requirePayloadMarkers: Boolean = true): String? {
        val compact = raw.filterNot(Char::isWhitespace).trim()
        if (compact.length < 8 || compact.any { it !in BASE64_CHARS }) return null
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val bytes = ArrayList<Byte>((padded.length / 4) * 3)
        var index = 0
        while (index < padded.length) {
            val c0 = base64Value(padded[index])
            val c1 = base64Value(padded[index + 1])
            val c2 = base64Value(padded[index + 2])
            val c3 = base64Value(padded[index + 3])
            if (c0 < 0 || c1 < 0 || c2 < -1 || c3 < -1) return null
            bytes += ((c0 shl 2) or (c1 ushr 4)).toByte()
            if (c2 >= 0) bytes += (((c1 and 15) shl 4) or (c2 ushr 2)).toByte()
            if (c3 >= 0 && c2 >= 0) bytes += (((c2 and 3) shl 6) or c3).toByte()
            index += 4
        }
        val decoded = runCatching { bytes.toByteArray().decodeToString() }.getOrNull() ?: return null
        if (!requirePayloadMarkers) return decoded
        return decoded.takeIf { text -> text.any { it == ':' || it == '{' || it == '[' } }
    }

    private fun base64Value(char: Char): Int = when (char) {
        '=' -> -1
        in 'A'..'Z' -> char.code - 'A'.code
        in 'a'..'z' -> char.code - 'a'.code + 26
        in '0'..'9' -> char.code - '0'.code + 52
        '+', '-' -> 62
        '/', '_' -> 63
        else -> -2
    }

    private fun parseQuery(raw: String): Map<String, String> = raw
        .split('&')
        .mapNotNull { part ->
            if (part.isBlank()) null else {
                val key = percentDecode(part.substringBefore('=')).lowercase()
                key to percentDecode(part.substringAfter('=', ""))
            }
        }
        .toMap()

    private fun parseHost(hostPort: String): String {
        val clean = hostPort.substringAfterLast('@')
        return if (clean.startsWith('[')) clean.substringAfter('[').substringBefore(']')
        else clean.substringBeforeLast(':', clean)
    }

    private fun parsePort(hostPort: String): Int {
        val clean = hostPort.substringAfterLast('@')
        return if (clean.startsWith('[')) clean.substringAfter("]:", "0").toIntOrNull() ?: 0
        else clean.substringAfterLast(':', "0").toIntOrNull() ?: 0
    }

    private fun percentDecode(raw: String): String {
        val bytes = ArrayList<Byte>(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '%' && index + 2 < raw.length -> {
                    val value = raw.substring(index + 1, index + 3).toIntOrNull(16)
                    if (value != null) {
                        bytes += value.toByte()
                        index += 3
                    } else {
                        bytes += char.code.toByte()
                        index++
                    }
                }
                char == '+' -> {
                    bytes += ' '.code.toByte()
                    index++
                }
                else -> {
                    bytes += char.toString().encodeToByteArray().toList()
                    index++
                }
            }
        }
        return runCatching { bytes.toByteArray().decodeToString() }.getOrDefault(raw)
    }

    private fun findXrayEndpoint(outbound: JsonObject?): Pair<String, Int> {
        if (outbound == null) return "" to 0
        fun search(element: JsonElement): Pair<String, Int>? {
            when (element) {
                is JsonObject -> {
                    val address = element.string("address") ?: element.string("server")
                    val port = element["port"]?.jsonPrimitive?.intOrNull
                        ?: element.string("port")?.toIntOrNull()
                    if (!address.isNullOrBlank()) return address to (port ?: 0)
                    element.values.forEach { search(it)?.let { found -> return found } }
                }
                is JsonArray -> element.forEach { search(it)?.let { found -> return found } }
                else -> Unit
            }
            return null
        }
        return search(outbound) ?: ("" to 0)
    }

    private fun JsonObject.string(key: String): String? = this[key]
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull

    private fun sourceTitle(source: String?): String {
        if (source.isNullOrBlank()) return "Подписка"
        val withoutScheme = source.substringAfter("://", source)
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return if (host.isBlank()) "Подписка" else "Подписка · $host"
    }

    private fun emptyResult(source: String?) = NormalizedSubscription(
        title = sourceTitle(source),
        source = source,
        diagnosticCode = "SUBSCRIPTION_NO_SUPPORTED_NODES"
    )

    private fun normalizeForIdentity(link: String): String = link.trim().substringBefore('#').lowercase()

    private fun stableId(value: String): String {
        var hash = 0xcbf29ce484222325UL
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
        }
        return hash.toString(16)
    }

    private const val STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_SAFE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val BASE64_CHARS = (STANDARD_ALPHABET + "-_=\r\n\t ").toSet()
}
