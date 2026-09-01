package com.danila.nimbo.network

import android.net.Uri
import android.util.Base64
import com.danila.nimbo.model.Server
import org.json.JSONObject
import java.net.URI

object LinkParser {
    private val base64LikeRegex = Regex("^[A-Za-z0-9+/=_-]{8,}$")

    private fun maybeDecodeBase64Text(value: String): String? {
        val candidate = value.trim()
        if (candidate.length < 8) return null
        if (!base64LikeRegex.matches(candidate)) return null
        val normalized = candidate.replace('-', '+').replace('_', '/')
        if (normalized.length % 4 != 0) return null

        return runCatching {
            val decoded = Base64.decode(normalized, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8).trim()
        }.getOrNull()?.takeIf { decoded ->
            decoded.isNotBlank() &&
                decoded.any { it.isLetterOrDigit() } &&
                decoded.none { it.code in 0..8 || it.code in 14..31 || it.code == 127 }
        }
    }

    private fun normalizeServerDescription(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return null
        if (normalized.equals("null", ignoreCase = true)) return null
        return maybeDecodeBase64Text(normalized) ?: normalized
    }

    private fun extractServerDescription(
        params: Map<String, String>,
        uri: Uri?,
        fallback: String? = null
    ): String? {
        return normalizeServerDescription(
            params["serverdescription"]
                ?: params["server_description"]
                ?: params["server-description"]
                ?: params["description"]
                ?: extractDescriptionFromMeta(params["meta"] ?: uri?.getQueryParameter("meta"))
                ?: uri?.getQueryParameter("serverDescription")
                ?: uri?.getQueryParameter("server_description")
                ?: uri?.getQueryParameter("server-description")
                ?: uri?.getQueryParameter("description")
                ?: fallback
        )
    }

    private fun extractDescriptionFromMeta(rawMeta: String?): String? {
        val normalized = rawMeta?.trim().orEmpty()
        if (normalized.isBlank() || normalized.equals("null", ignoreCase = true)) return null
        val decoded = normalizeServerDescription(normalized) ?: normalized
        val jsonText = decoded.trim()
        if (!jsonText.startsWith("{") || !jsonText.endsWith("}")) return null
        return runCatching {
            val json = JSONObject(jsonText)
            json.optString("serverDescription")
                .ifBlank { json.optString("server_description") }
                .ifBlank { json.optString("server-description") }
                .ifBlank { json.optString("description") }
                .trim()
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }.getOrNull()
    }

    private fun extractTemplateHints(params: Map<String, String>, uri: Uri?): Pair<String?, String?> {
        val uuid = params["templateuuid"]
            ?: params["template_uuid"]
            ?: params["templateid"]
            ?: params["template_id"]
            ?: uri?.getQueryParameter("templateUuid")
            ?: uri?.getQueryParameter("template_uuid")
            ?: uri?.getQueryParameter("templateId")
            ?: uri?.getQueryParameter("template_id")
        val name = params["templatename"]
            ?: params["template_name"]
            ?: params["template"]
            ?: uri?.getQueryParameter("templateName")
            ?: uri?.getQueryParameter("template_name")
            ?: uri?.getQueryParameter("template")
        return normalizeServerDescription(uuid) to normalizeServerDescription(name)
    }

    private fun sanitizeDescriptionByNetwork(description: String?, network: String?): String? {
        val desc = normalizeServerDescription(description) ?: return null
        val net = network?.trim()?.lowercase().orEmpty()
        val d = desc.lowercase()

        // Защита от "прилипания" serverDescription к несовместимому типу транспорта.
        // Частый кейс: у TCP узла внезапно приходит "XHTTP 🚀" из внешнего JSON.
        if (net == "tcp") {
            if (d.contains("xhttp") || d.contains("grpc") || d.contains("ws") || d.contains("h2")) {
                return null
            }
        }
        if (net == "xhttp" && d.contains("grpc")) return null
        if (net == "grpc" && d.contains("xhttp")) return null

        return desc
    }

    private fun decodeQueryValue(value: String): String {
        // Важно: не превращаем '+' в пробел, иначе ломаются ключи (например pbk в Reality).
        val androidDecoded = runCatching { Uri.decode(value) }.getOrNull()
        if (androidDecoded != null && (androidDecoded != value || !value.contains('%'))) {
            return androidDecoded
        }
        return runCatching {
            java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun parseQueryParams(queryPart: String): Map<String, String> {
        return queryPart
            .split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].lowercase() to decodeQueryValue(parts[1])
                else it.lowercase() to ""
            }
    }

    private fun decodeName(rawName: String, fallback: String = "Server"): String {
        val candidate = rawName.takeIf { it.isNotBlank() && !it.contains("://") } ?: fallback
        val decoded = decodeQueryValue(candidate)
        return if (decoded != candidate || candidate.contains('%')) {
            decoded
        } else {
            maybeDecodeBase64Text(candidate) ?: candidate
        }
    }

    /**
     * Remnawave (Happ-формат) дописывает к #fragment ссылки хвост вида
     * "?serverDescription=<base64>" (иногда и другие query-параметры). Раньше у VLESS это
     * обнуляло имя до "Server" (см. проверку `contains("?")`), а у ss/trojan весь хвост,
     * включая base64, попадал прямо в название сервера. Делим fragment на
     * (чистое имя, описание из хвоста или null). Описание прогоняется через
     * extractServerDescription, который умеет декодировать base64.
     */
    private fun splitFragmentNameAndDescription(rawFragment: String): Pair<String, String?> {
        val decoded = decodeName(rawFragment, fallback = "")
        val qIndex = decoded.indexOf('?')
        if (qIndex < 0) return decoded to null
        val namePart = decoded.substring(0, qIndex).trim()
        val tailParams = parseQueryParams(decoded.substring(qIndex + 1))
        val desc = extractServerDescription(tailParams, uri = null)
        return (namePart.ifBlank { decoded }) to desc
    }

    private fun parseBooleanParam(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "1", "true", "yes", "y" -> true
            "0", "false", "no", "n" -> false
            else -> null
        }
    }

    private fun parseHostPort(rawHostPort: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = rawHostPort.trim()
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOf(']')
            if (end > 0) {
                val host = trimmed.substring(1, end)
                val port = trimmed.substring(end + 1)
                    .removePrefix(":")
                    .substringBefore("/")
                    .substringBefore("?")
                    .toIntOrNull()
                    ?: defaultPort
                return host to port
            }
        }

        val withoutPath = trimmed.substringBefore("/").substringBefore("?")
        val host = if (withoutPath.count { it == ':' } == 1) {
            withoutPath.substringBeforeLast(":")
        } else {
            withoutPath
        }
        val port = if (withoutPath.count { it == ':' } == 1) {
            withoutPath.substringAfterLast(":", defaultPort.toString()).toIntOrNull() ?: defaultPort
        } else {
            defaultPort
        }
        return host to port
    }

    fun parse(link: String): Server {
        val trimmed = link.trim()
        val uri = try { Uri.parse(trimmed) } catch (e: Exception) { null }
        // Сырые wg/awg-конфиги (без схемы) парсим как WireGuard.
        if (looksLikeWgIni(trimmed)) return parseWireGuardLink(trimmed, uri)
        val protocol = trimmed.substringBefore("://", "vless").lowercase()
        
        return when (protocol) {
            "vless" -> parseVless(link, uri)
            "vmess" -> parseVmess(uri, link)
            "trojan" -> parseTrojan(link, uri)
            "ss" -> parseShadowsocks(link, uri)
            "hy", "hy2", "hysteria2", "hysteria" -> parseHysteria2(link, uri)
            "naive", "naive+https", "naive+quic" -> parseNaiveProxy(trimmed)
            "awg", "amneziawg" -> parseAwgLink(link, uri)
            "wireguard", "wg" -> parseWireGuardLink(link, uri)
            else -> parseVless(link, uri)
        }
    }

    private fun parseNaiveProxy(link: String): Server {
        val parsed = runCatching { URI(link) }
            .getOrElse { throw IllegalArgumentException("Некорректная ссылка NaiveProxy", it) }
        val transport = when (parsed.scheme?.lowercase()) {
            "naive+quic" -> "quic"
            "naive", "naive+https" -> "https"
            else -> throw IllegalArgumentException("Поддерживаются naive+https:// и naive+quic://")
        }
        val host = parsed.host?.trim().orEmpty()
        if (host.isBlank()) throw IllegalArgumentException("В ссылке NaiveProxy не указан сервер")

        val rawUserInfo = parsed.rawUserInfo.orEmpty()
        val separator = rawUserInfo.indexOf(':')
        if (separator <= 0 || separator == rawUserInfo.lastIndex) {
            throw IllegalArgumentException("В ссылке NaiveProxy нужны имя пользователя и пароль")
        }
        val username = Uri.decode(rawUserInfo.substring(0, separator)).trim()
        val password = Uri.decode(rawUserInfo.substring(separator + 1))
        if (username.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("В ссылке NaiveProxy нужны имя пользователя и пароль")
        }

        val port = if (parsed.port > 0) parsed.port else 443
        val displayName = Uri.decode(parsed.rawFragment.orEmpty()).trim().ifBlank { "NaiveProxy" }
        return Server(
            name = displayName,
            host = host,
            port = port,
            uuid = username,
            protocol = "naive",
            security = "tls",
            network = transport,
            sni = host,
            naiveUsername = username,
            naivePassword = password,
            naiveTransport = transport
        )
    }

    // ---------- WireGuard / AmneziaWG ----------

    private fun parseIntParam(params: Map<String, String>, vararg keys: String): Int? {
        for (key in keys) {
            val value = params[key]?.trim()?.takeIf { it.isNotBlank() } ?: continue
            return value.toIntOrNull()
        }
        return null
    }

    private fun firstParam(params: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = params[key]?.trim()?.takeIf { it.isNotBlank() } ?: continue
            return value
        }
        return null
    }

    private fun parseWgEndpoint(raw: String?, uriHost: String?, uriPort: Int?): Pair<String, Int> {
        val candidate = raw?.trim().orEmpty().ifEmpty { uriHost.orEmpty() }
        if (candidate.isBlank()) return "" to 0
        val (host, port) = parseHostPort(candidate, 51820)
        val effectivePort = port.takeIf { it > 0 } ?: uriPort ?: 51820
        return host to effectivePort
    }

    private fun parseAwgLink(link: String, uri: Uri?): Server {
        val rawRest = link.substringAfter("://", "").substringBeforeLast("#", "").trim()
        val rawFragment = link.substringAfterLast("#", "")
        val fragmentName = if (rawFragment.isNotBlank() && !rawFragment.contains("://")) {
            splitFragmentNameAndDescription(rawFragment).first.ifBlank { null }
        } else null

        // awg://<base64 INI> — формат официального клиента Amnezia.
        val decodedIni = maybeDecodeBase64Text(rawRest.substringBefore("?"))
        val params = parseQueryParams(rawRest.substringAfter("?", ""))

        if (!decodedIni.isNullOrBlank() && looksLikeWgIni(decodedIni)) {
            return parseWgIniConfig(decodedIni, nameOverride = fragmentName, params = params, protocol = "awg")
        }

        // awg://key=value&... — параметры в query (панели/скрипты).
        if (params.isNotEmpty()) {
            return parseWgQueryConfig(params, uri, fragmentName, protocol = "awg")
        }

        // Сырой INI прямо в ссылке.
        if (looksLikeWgIni(rawRest)) {
            return parseWgIniConfig(rawRest, nameOverride = fragmentName, params = emptyMap(), protocol = "awg")
        }

        // Безнадёжный случай: только host:port без ключей.
        val (host, port) = parseWgEndpoint(rawRest, uri?.host, uri?.port)
        return Server(
            name = fragmentName ?: "AWG",
            host = host,
            port = port,
            uuid = "",
            protocol = "awg",
            serverDescription = null
        )
    }

    private fun parseWireGuardLink(link: String, uri: Uri?): Server {
        // Сырой INI-конфиг целиком.
        if (looksLikeWgIni(link)) {
            return parseWgIniConfig(link, nameOverride = null, params = emptyMap(), protocol = "wireguard")
        }
        val rawRest = link.substringAfter("://", "").trim()
        val rawFragment = link.substringAfterLast("#", "")

        // wireguard://<имя>?key=value...
        val qIndex = rawRest.indexOf('?')
        val namePart = if (qIndex >= 0) rawRest.substring(0, qIndex) else ""
        val queryPart = if (qIndex >= 0) rawRest.substring(qIndex + 1) else ""
        val params = parseQueryParams(queryPart)

        val fragmentName = if (rawFragment.isNotBlank() && !rawFragment.contains("://")) {
            splitFragmentNameAndDescription(rawFragment).first.ifBlank { null }
        } else null

        val nameOverride = fragmentName
            ?: decodeName(namePart, fallback = "").ifBlank { null }
            ?: firstParam(params, "name", "hostname", "title", "remark", "comment", "ps")

        if (params.isNotEmpty()) {
            return parseWgQueryConfig(params, uri, nameOverride, protocol = "wireguard")
        }

        val rawBody = if (namePart.isBlank()) rawRest else namePart
        if (looksLikeWgIni(rawBody)) {
            return parseWgIniConfig(rawBody, nameOverride = nameOverride, params = emptyMap(), protocol = "wireguard")
        }

        val decodedIni = maybeDecodeBase64Text(rawBody)
        if (!decodedIni.isNullOrBlank() && looksLikeWgIni(decodedIni)) {
            return parseWgIniConfig(decodedIni, nameOverride = nameOverride, params = emptyMap(), protocol = "wireguard")
        }

        val (host, port) = parseWgEndpoint(rawBody, uri?.host, uri?.port)
        return Server(
            name = nameOverride ?: "WireGuard",
            host = host,
            port = port,
            uuid = "",
            protocol = "wireguard",
            serverDescription = null
        )
    }

    private fun looksLikeWgIni(text: String?): Boolean {
        val lower = text?.lowercase().orEmpty()
        return lower.contains("[interface]") || lower.contains("[peer]")
    }

    private fun looksLikeEndpointHost(host: String): Boolean {
        if (host.isBlank() || host.contains(' ')) return false
        if (host.contains('.')) return true
        return host.count { it == ':' } >= 2
    }

    private fun parseWgIniConfig(
        iniText: String,
        nameOverride: String?,
        params: Map<String, String>,
        protocol: String
    ): Server {
        val interfaceParams = mutableMapOf<String, String>()
        val peerParams = mutableMapOf<String, String>()
        var currentSection: String? = null

        iniText.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isBlank()) return@forEach
            when {
                line.startsWith("[") && line.endsWith("]") -> {
                    currentSection = line.removePrefix("[").removeSuffix("]").lowercase()
                }

                line.contains("=") -> {
                    val key = line.substringBefore("=").trim().lowercase()
                    val value = line.substringAfter("=").trim()
                    when (currentSection) {
                        "interface" -> interfaceParams[key] = value
                        "peer" -> peerParams[key] = value
                    }
                }
            }
        }

        val merged = interfaceParams.toMutableMap()
        // Параметры из query ссылки приоритетнее INI (имя, endpoint и т.п.).
        merged.putAll(params)
        val peerMerged = peerParams.toMutableMap()
        return buildWgServer(interfaceParams = merged, peerParams = peerMerged, nameOverride = nameOverride, protocol = protocol)
    }

    private fun parseWgQueryConfig(
        params: Map<String, String>,
        uri: Uri?,
        nameOverride: String?,
        protocol: String
    ): Server {
        val interfaceParams = HashMap(params)
        val peerParams = HashMap<String, String>()

        // В query-формате peer-ключи лежат рядом с interface-ключами.
        val peerKeys = mapOf(
            "publickey" to "publickey",
            "pubkey" to "publickey",
            "public_key" to "publickey",
            "peer_public_key" to "publickey",
            "server_public_key" to "publickey",
            "presharedkey" to "presharedkey",
            "psk" to "presharedkey",
            "preshared_key" to "presharedkey",
            "peer_preshared_key" to "presharedkey",
            "endpoint" to "endpoint",
            "server" to "endpoint",
            "allowedips" to "allowedips",
            "allowed_ips" to "allowedips",
            "ip_range" to "allowedips",
            "routes" to "allowedips",
            "keepalive" to "persistent_keepalive",
            "persistent_keepalive" to "persistent_keepalive",
            "persistentkeepalive" to "persistent_keepalive",
            "persistent_keepalive_interval" to "persistent_keepalive"
        )
        peerKeys.forEach { (rawKey, canonicalKey) ->
            params[rawKey]?.let { peerParams[canonicalKey] = it }
        }
        peerParams.keys.forEach { key -> interfaceParams.remove(key) }

        // Endpoint в query может лежать отдельным ключом; достаём host:port из uri как fallback.
        // uri.host тут доверять нельзя напрямую: в wireguard://<имя>?.. host-часть — это имя.
        val endpoint = peerParams["endpoint"]
        val uriHost = uri?.host
        val uriPort = uri?.port
        if (endpoint.isNullOrBlank() && !uriHost.isNullOrBlank() && looksLikeEndpointHost(uriHost)) {
            peerParams["endpoint"] = if (uriPort != null && uriPort > 0) "$uriHost:$uriPort" else uriHost
        }

        return buildWgServer(
            interfaceParams = interfaceParams,
            peerParams = peerParams,
            nameOverride = nameOverride,
            protocol = protocol
        )
    }

    private fun buildWgServer(
        interfaceParams: Map<String, String>,
        peerParams: Map<String, String>,
        nameOverride: String?,
        protocol: String
    ): Server {
        val ip = interfaceParams
        val pp = peerParams

        val privateKey = firstParam(ip, "privatekey", "private_key", "privkey", "key", "client_private_key")
        val publicKey = firstParam(pp, "publickey", "public_key", "pubkey", "peer_public_key", "server_public_key")
        val presharedKey = firstParam(pp, "presharedkey", "preshared_key", "psk")
            ?: firstParam(ip, "presharedkey", "preshared_key", "psk")
        val address = firstParam(ip, "address", "addresses", "ip", "ips", "interface_address")
        val allowedIps = firstParam(pp, "allowedips", "allowed_ips", "ip_range", "routes")
        val dns = firstParam(ip, "dns", "dns1", "dns2", "dns_server", "dns_servers")
        val mtu = parseIntParam(ip, "mtu", "mtu_size")
        val keepAlive = parseIntParam(pp, "persistent_keepalive", "persistentkeepalive", "keepalive")

        val (host, port) = parseWgEndpoint(pp["endpoint"], null, null)

        val name = nameOverride?.takeIf { it.isNotBlank() }
            ?: firstParam(ip, "name", "hostname", "title", "remark", "comment", "ps")
            ?: host.ifBlank { protocol }

        return Server(
            name = name,
            host = host,
            port = port,
            uuid = "",
            protocol = protocol,
            serverDescription = firstParam(ip, "serverdescription", "server_description", "description"),
            wgPrivateKey = privateKey,
            wgPublicKey = publicKey,
            wgPresharedKey = presharedKey,
            wgAddress = address,
            wgAllowedIps = allowedIps,
            wgDns = dns,
            wgMtu = mtu,
            wgKeepAlive = keepAlive,
            awgJc = parseIntParam(ip, "jc", "junkpacketcount", "junk"),
            awgJmin = parseIntParam(ip, "jmin", "junkpacketminsize"),
            awgJmax = parseIntParam(ip, "jmax", "junkpacketmaxsize"),
            awgS1 = parseIntParam(ip, "s1", "junkpacketcollectionsize1"),
            awgS2 = parseIntParam(ip, "s2", "junkpacketcollectionsize2"),
            awgS3 = parseIntParam(ip, "s3", "junkpacketcollectionsize3"),
            awgS4 = parseIntParam(ip, "s4", "junkpacketcollectionsize4"),
            awgH1 = firstParam(ip, "h1", "header1"),
            awgH2 = firstParam(ip, "h2", "header2"),
            awgH3 = firstParam(ip, "h3", "header3"),
            awgH4 = firstParam(ip, "h4", "header4"),
            awgI1 = firstParam(ip, "i1", "init1"),
            awgI2 = firstParam(ip, "i2", "init2"),
            awgI3 = firstParam(ip, "i3", "init3"),
            awgI4 = firstParam(ip, "i4", "init4"),
            awgI5 = firstParam(ip, "i5", "init5")
        )
    }

    private fun parseVless(link: String, uri: Uri?): Server {
        // Извлекаем базу: vless://uuid@host:port
        val mainPart = link.substringAfter("://").substringBefore("#").substringBefore("?")
        val userInfo = mainPart.substringBefore("@", "")
        val hostPort = mainPart.substringAfter("@", "")
        val host = hostPort.substringBefore(":", hostPort)
        val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
        
        // Извлекаем фрагмент (имя) - ищем ПОСЛЕДНИЙ #. Remnawave может дописать во fragment
        // хвост "?serverDescription=<base64>" — отделяем его, иначе имя обнулялось до "Server".
        val rawFragment = link.substringAfterLast("#", "")
        val (fragName, fragDescription) = if (rawFragment.isBlank() || rawFragment.contains("://")) {
            "Server" to null
        } else {
            splitFragmentNameAndDescription(rawFragment)
        }
        val name = fragName.ifBlank { "Server" }

        // Извлекаем параметры запроса - они могут быть после ? или после # (в некоторых форматах)
        val queryPart = if (link.contains("?")) {
            link.substringAfter("?").substringBefore("#")
        } else ""
        
        val params = parseQueryParams(queryPart)

        val security = params["security"] ?: uri?.getQueryParameter("security")
        val isReality = security?.lowercase() == "reality"
        val isTls = isReality || security?.lowercase() == "tls" || (uri?.getQueryParameter("security")?.lowercase() == "tls")
        val (templateUuid, templateName) = extractTemplateHints(params, uri)

        val network = params["type"] ?: params["network"] ?: uri?.getQueryParameter("type") ?: "tcp"
        val description = sanitizeDescriptionByNetwork(
            extractServerDescription(params, uri) ?: fragDescription,
            network
        )

        return Server(
            name = name,
            host = host,
            port = port,
            uuid = userInfo,
            protocol = "vless",
            serverDescription = description,
            flow = params["flow"] ?: uri?.getQueryParameter("flow"),
            security = security,
            network = network,
            path = params["path"] ?: uri?.getQueryParameter("path"),
            hostHeader = params["host"] ?: params["h"] ?: uri?.getQueryParameter("host"),
            serviceName = params["servicename"] ?: uri?.getQueryParameter("serviceName"),
            sni = params["sni"]
                ?: params["servername"]
                ?: params["server_name"]
                ?: uri?.getQueryParameter("sni")
                ?: uri?.getQueryParameter("serverName"),
            fingerprint = params["fp"] ?: uri?.getQueryParameter("fp"),
            alpn = params["alpn"] ?: uri?.getQueryParameter("alpn"),
            allowInsecure = (params["allowinsecure"] ?: uri?.getQueryParameter("allowInsecure"))?.toBooleanStrictOrNull(),
            tls = isTls,
            publicKey = params["pbk"]
                ?: params["publickey"]
                ?: params["public_key"]
                ?: uri?.getQueryParameter("pbk")
                ?: uri?.getQueryParameter("publicKey"),
            shortId = params["sid"]
                ?: params["shortid"]
                ?: params["short_id"]
                ?: uri?.getQueryParameter("sid")
                ?: uri?.getQueryParameter("shortId"),
            spiderX = params["spx"] ?: uri?.getQueryParameter("spx"),
            templateUuid = templateUuid,
            templateName = templateName
        )
    }

    private fun parseVmess(uri: Uri?, link: String): Server {
        try {
            val base64Part = link.removePrefix("vmess://")
            val decoded = Base64.decode(base64Part.trim(), Base64.DEFAULT)
            val jsonStr = String(decoded)
            val json = JSONObject(jsonStr)

            return Server(
                name = json.optString("ps", "Server"),
                host = json.optString("add", ""),
                port = json.optString("port", "443").toIntOrNull() ?: 443,
                uuid = json.optString("id", ""),
                protocol = "vmess",
                serverDescription = extractServerDescription(
                    params = emptyMap(),
                    uri = uri,
                    fallback = json.optString("serverDescription")
                ),
                alterId = json.optInt("aid", 0),
                security = json.optString("scy", "auto"),
                network = json.optString("net", "tcp"),
                path = if (json.has("path")) json.optString("path") else null,
                hostHeader = if (json.has("host")) json.optString("host") else null,
                sni = if (json.has("sni")) json.optString("sni") else null,
                tls = json.optString("tls", "").equals("tls", ignoreCase = true),
                templateUuid = normalizeServerDescription(json.optString("templateUuid")),
                templateName = normalizeServerDescription(json.optString("templateName"))
            )
        } catch (e: Exception) {
            val rawFragment = uri?.fragment ?: ""
            val (fragName, fragDescription) = if (rawFragment.isBlank() || rawFragment.contains("://")) {
                "Server" to null
            } else {
                splitFragmentNameAndDescription(rawFragment)
            }
            val params = uri?.queryParameterNames?.associateWith { uri.getQueryParameter(it).orEmpty() } ?: emptyMap()
            val (templateUuid, templateName) = extractTemplateHints(params.mapKeys { it.key.lowercase() }, uri)
            return Server(
                name = fragName.ifBlank { "Server" },
                host = uri?.host ?: "",
                port = uri?.port ?: 443,
                uuid = uri?.userInfo ?: "",
                protocol = "vmess",
                serverDescription = extractServerDescription(emptyMap(), uri) ?: fragDescription,
                templateUuid = templateUuid,
                templateName = templateName
            )
        }
    }
    private fun parseTrojan(link: String, uri: Uri?): Server {
        val queryPart = link.substringAfter("?", "").substringBefore("#")
        val params = parseQueryParams(queryPart)

        val rawFragment = link.substringAfterLast("#", "")
        val (fragName, fragDescription) = if (rawFragment.isBlank() || rawFragment.contains("://")) {
            "Server" to null
        } else {
            splitFragmentNameAndDescription(rawFragment)
        }
        val name = fragName.ifBlank { "Server" }
        val (templateUuid, templateName) = extractTemplateHints(params, uri)

        val network = params["type"] ?: "tcp"
        // Honour an explicit security param. When it's missing, classic Trojan over
        // tcp/raw implies TLS, but Trojan tunnelled through plain ws/httpupgrade is
        // normally fronted (plaintext to the node) — forcing TLS there breaks the
        // handshake. grpc/xhttp keep the TLS default since they're usually wrapped in it.
        val security = (params["security"] ?: uri?.getQueryParameter("security"))
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: when (network.trim().lowercase()) {
                "ws", "httpupgrade", "http-upgrade" -> "none"
                else -> "tls"
            }
        val description = sanitizeDescriptionByNetwork(
            extractServerDescription(params, uri) ?: fragDescription,
            network
        )

        return Server(
            name = name,
            host = uri?.host ?: "",
            port = uri?.port ?: 443,
            uuid = decodeQueryValue(uri?.userInfo.orEmpty()),
            protocol = "trojan",
            serverDescription = description,
            security = security,
            tls = security == "tls",
            sni = params["sni"] ?: uri?.getQueryParameter("sni"),
            alpn = params["alpn"] ?: uri?.getQueryParameter("alpn"),
            fingerprint = params["fp"] ?: params["fingerprint"] ?: uri?.getQueryParameter("fp"),
            allowInsecure = parseBooleanParam(params["allowinsecure"] ?: uri?.getQueryParameter("allowInsecure")),
            network = network,
            path = params["path"] ?: uri?.getQueryParameter("path"),
            hostHeader = params["host"] ?: uri?.getQueryParameter("host"),
            serviceName = params["servicename"] ?: params["serviceName"] ?: uri?.getQueryParameter("serviceName"),
            templateUuid = templateUuid,
            templateName = templateName
        )
    }

    private fun parseShadowsocks(link: String, uri: Uri?): Server {
        // shadowsocks: ss://base64(method:password)@host:port#name
        val mainPart = link.substringAfter("ss://").substringBefore("#")
        val userInfo = mainPart.substringBefore("@", "")
        val hostPort = mainPart.substringAfter("@", "")
        
        val decodedUserInfo = try {
            val decoded = Base64.decode(userInfo, Base64.DEFAULT)
            String(decoded)
        } catch (e: Exception) {
            userInfo
        }
        
        val method = decodedUserInfo.substringBefore(":", "chacha20-poly1305")
        val password = decodedUserInfo.substringAfter(":", "")
        val rawFragment = link.substringAfterLast("#", "")
        val (fragName, fragDescription) = if (rawFragment.isBlank() || rawFragment.contains("://")) {
            "Server" to null
        } else {
            splitFragmentNameAndDescription(rawFragment)
        }
        val name = fragName.ifBlank { "Server" }
        val params = uri?.queryParameterNames?.associateWith { uri.getQueryParameter(it).orEmpty() }
            ?.mapKeys { it.key.lowercase() }
            ?: emptyMap()
        val (templateUuid, templateName) = extractTemplateHints(params, uri)

        return Server(
            name = name,
            host = hostPort.substringBefore(":"),
            port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443,
            uuid = password,
            protocol = "ss",
            serverDescription = extractServerDescription(emptyMap(), uri) ?: fragDescription,
            method = method,
            templateUuid = templateUuid,
            templateName = templateName
        )
    }

    private fun parseHysteria2(link: String, uri: Uri?): Server {
        val queryPart = link.substringAfter("?", "").substringBefore("#")
        val params = parseQueryParams(queryPart)
        val mainPart = link.substringAfter("://").substringBefore("#").substringBefore("?")
        val hasUserInfo = mainPart.contains("@")
        val rawUserInfo = if (hasUserInfo) mainPart.substringBefore("@", "") else ""
        val rawHostPort = if (hasUserInfo) mainPart.substringAfter("@", "") else mainPart
        val (parsedHost, parsedPort) = parseHostPort(rawHostPort, 443)

        val rawFragment = link.substringAfterLast("#", "")
        val (fragName, fragDescription) = if (rawFragment.isBlank() || rawFragment.contains("://")) {
            "Hysteria2" to null
        } else {
            splitFragmentNameAndDescription(rawFragment)
        }
        val name = fragName.ifBlank { "Hysteria2" }
        val (templateUuid, templateName) = extractTemplateHints(params, uri)
        val password = decodeQueryValue(rawUserInfo)
            .ifBlank {
                params["auth"]
                    ?: params["auth-str"]
                    ?: params["auth_str"]
                    ?: params["authstring"]
                    ?: params["password"]
                    ?: uri?.userInfo
                    ?: ""
            }

        val insecure = parseBooleanParam(
            params["insecure"]
                ?: params["allowinsecure"]
                ?: params["allow_insecure"]
                ?: uri?.getQueryParameter("insecure")
        )
        val obfs = params["obfs"] ?: params["obfs-type"] ?: params["obfstype"]
        val obfsPassword = params["obfs-password"]
            ?: params["obfspassword"]
            ?: params["obfs_password"]
            ?: params["obfs-param"]
            ?: params["obfsparam"]
            ?: params["obfs_param"]
            ?: params["salamander-password"]
            ?: params["salamander_password"]
        val portHopping = params["mport"]
            ?: params["ports"]
            ?: params["porthopping"]
            ?: params["port_hopping"]
        val hopInterval = params["hopinterval"]
            ?: params["hop_interval"]
            ?: params["hop-interval"]
            ?: params["interval"]
        val up = params["upmbps"] ?: params["up"] ?: params["upload"]
        val down = params["downmbps"] ?: params["down"] ?: params["download"]
        val congestion = params["congestion"] ?: params["cc"]

        return Server(
            name = name,
            host = uri?.host ?: parsedHost,
            port = (uri?.port ?: parsedPort).takeIf { it > 0 } ?: 443,
            uuid = password,
            protocol = "hysteria",
            serverDescription = extractServerDescription(params, uri) ?: fragDescription,
            security = "tls",
            network = "hysteria",
            sni = params["sni"]
                ?: params["peer"]
                ?: params["servername"]
                ?: params["server_name"]
                ?: uri?.getQueryParameter("sni"),
            alpn = params["alpn"] ?: "h3",
            allowInsecure = insecure,
            tls = true,
            hysteriaObfs = obfs,
            hysteriaObfsPassword = obfsPassword,
            hysteriaPorts = portHopping,
            hysteriaHopInterval = hopInterval,
            hysteriaUp = up,
            hysteriaDown = down,
            hysteriaCongestion = congestion,
            templateUuid = templateUuid,
            templateName = templateName
        )
    }
}
