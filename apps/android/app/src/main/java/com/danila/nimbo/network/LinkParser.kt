package com.danila.nimbo.network

import android.net.Uri
import android.util.Base64
import com.danila.nimbo.model.Server
import org.json.JSONObject

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
        val uri = try { Uri.parse(link) } catch (e: Exception) { null }
        val protocol = link.substringBefore("://", "vless").lowercase()
        
        return when (protocol) {
            "vless" -> parseVless(link, uri)
            "vmess" -> parseVmess(uri, link)
            "trojan" -> parseTrojan(link, uri)
            "ss" -> parseShadowsocks(link, uri)
            "hy", "hy2", "hysteria2", "hysteria" -> parseHysteria2(link, uri)
            else -> parseVless(link, uri)
        }
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
