package com.danila.nimbo.network

import android.content.Context
import android.util.Log
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.utils.AppVersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeParseException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Locale

data class RemnawaveUserInfo(
    val accountId: String?, // UUID
    val username: String?,
    val description: String?,
    val uploadTotal: Long?,
    val downloadTotal: Long?,
    val totalTraffic: Long?,
    val expireTime: Long?,
    val deviceCount: Int?,
    val telegramId: String? = null,
    val shortUuid: String? = null,
    val numericId: String? = null,
    val supportsJsonResponse: Boolean? = null
)

data class RemnawaveDevice(
    val id: String,
    val name: String,
    val hwid: String?,
    val lastSeenAt: Long?,
    val createdAt: Long?,
    val isCurrent: Boolean = false,
    val numericId: String? = null,
    val userUuid: String? = null
)

data class RemnawaveAddDaysResult(
    val success: Boolean,
    val newExpireTime: Long?,
    val addedDays: Int,
    val message: String?
)

data class RemnawaveSubscriptionTemplate(
    val uuid: String,
    val name: String,
    val templateType: String,
    val viewPosition: Int,
    val isDefault: Boolean = false,
    val config: String? = null,
    val fetchedAtMs: Long? = null
)

object RemnawaveApiClient {

    const val DEFAULT_XRAY_TEMPLATE_MARKER = "__default_xray_template__"

    private var userAgent: String = "Nimbo/${BuildConfig.VERSION_NAME}/Android"
    private var deviceId: String = ""
    private val base64LikeRegex = Regex("^[A-Za-z0-9+/=_-]{8,}$")

    /**
     * Инициализация User-Agent и Device ID
     */
    fun init(context: Context) {
        userAgent = AppVersionManager.getUserAgent(context)
        deviceId = AppVersionManager.getHWID(context)
        Log.d("RemnawaveApi", "Initialized with deviceId: $deviceId")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val context = NebulaGuardApplication.instance
            val osVersion = AppVersionManager.getOSVersion()
            val deviceModel = AppVersionManager.getDeviceModel()
            val appVersion = AppVersionManager.getVersionName(context)

            // 🔥 Logging for debug
            Log.d("RemnawaveApi", "Sending Headers: x-device-os=Android, x-ver-os=$osVersion, HWID=$deviceId")

            val requestUserAgent = AppVersionManager.getUserAgent(context)

            chain.proceed(
                original.newBuilder()
                    .header("User-Agent", requestUserAgent)
                    .header("x-device-id", deviceId)
                    .header("x-hwid", deviceId)
                    .header("x-device-os", "Android")
                    .header("x-ver-os", osVersion)
                    .header("x-device-model", deviceModel)
                    .header("x-app-version", appVersion)
                    .build()
            )
        }
        .build()

    internal fun selectTemplateByHints(
        templates: List<RemnawaveSubscriptionTemplate>,
        templateUuidHint: String?,
        templateNameHint: String?
    ): RemnawaveSubscriptionTemplate? {
        val uuidHint = templateUuidHint?.trim()?.takeIf { it.isNotBlank() }
        val nameHint = templateNameHint?.trim()?.takeIf { it.isNotBlank() }

        val byUuid = uuidHint?.let { uuid ->
            templates.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }
        }
        if (byUuid != null) return byUuid

        val byName = nameHint?.let { name ->
            templates.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        if (byName != null) return byName

        return null
    }

    private val nonProxyXrayOutboundProtocols = setOf(
        "freedom",
        "blackhole",
        "dns",
        "loopback"
    )

    internal fun hasUsableXrayClientOutboundProtocol(protocols: Iterable<String>): Boolean {
        return protocols.any { protocolRaw ->
            val protocol = protocolRaw.trim().lowercase(Locale.ROOT)
            protocol.isNotBlank() && protocol !in nonProxyXrayOutboundProtocols
        }
    }

    internal fun isUsableXrayClientConfig(json: JSONObject): Boolean {
        val outbounds = json.optJSONArray("outbounds") ?: return false
        val protocols = buildList {
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                add(outbound.optString("protocol"))
            }
        }
        if (hasUsableXrayClientOutboundProtocol(protocols)) return true
        // Remnawave auto-balancer templates ship only direct/block/loopback outbounds
        // plus a routing.balancers entry and a remnawave.injectHosts directive; the real
        // proxy outbounds are injected client-side before launch (standard xray-core does
        // not expand injectHosts). Treat such templates as usable so we don't discard them.
        return hasBalancerOrInjectHosts(json)
    }

    internal fun hasBalancerOrInjectHosts(json: JSONObject): Boolean {
        val balancers = json.optJSONObject("routing")?.optJSONArray("balancers")
        if (balancers != null && balancers.length() > 0) return true
        val injectHosts = json.optJSONObject("remnawave")?.optJSONArray("injectHosts")
        return injectHosts != null && injectHosts.length() > 0
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun canonicalProtocol(protocolRaw: String?): String {
        val p = protocolRaw?.trim()?.lowercase().orEmpty()
        return when {
            p.contains("vless") -> "vless"
            p.contains("vmess") -> "vmess"
            p.contains("trojan") -> "trojan"
            p.contains("shadow") || p == "ss" -> "shadowsocks"
            p.contains("hysteria") || p == "hy2" -> "hysteria"
            p.contains("tuic") -> "tuic"
            else -> p
        }
    }

    private fun normalizeMaybeBase64Text(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank() || normalized.equals("null", ignoreCase = true)) return null
        if (!base64LikeRegex.matches(normalized)) return normalized

        val candidate = normalized.replace('-', '+').replace('_', '/')
        if (candidate.length % 4 != 0) return normalized

        val decoded = runCatching {
            String(Base64.getDecoder().decode(candidate), StandardCharsets.UTF_8).trim()
        }.getOrNull()

        val looksReadable = decoded?.let {
            it.isNotBlank() &&
                it.any { ch -> ch.isLetterOrDigit() } &&
                it.none { ch -> ch.code in 0..8 || ch.code in 14..31 || ch.code == 127 }
        } == true

        return if (looksReadable) decoded else normalized
    }

    private fun hostDescriptionKey(protocol: String, host: String, port: Int): String {
        return "${canonicalProtocol(protocol)}|${host.trim().lowercase()}|$port"
    }

    private fun hostNameDescriptionKey(name: String): String {
        return "name|${name.trim().replace(Regex("\\s+"), " ").lowercase()}"
    }

    private fun recursiveFindString(node: Any?, keyAliases: Set<String>): String? {
        return when (node) {
            is JSONObject -> {
                val keys = node.keys()
                val deferred = mutableListOf<Any?>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    if (key.lowercase() in keyAliases) {
                        val normalized = value?.toString()?.trim()
                        if (!normalized.isNullOrBlank() && !normalized.equals("null", ignoreCase = true)) {
                            return normalized
                        }
                    }
                    if (value is JSONObject || value is JSONArray) {
                        deferred.add(value)
                    }
                }
                deferred.firstNotNullOfOrNull { recursiveFindString(it, keyAliases) }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = recursiveFindString(node.opt(i), keyAliases)
                    if (!found.isNullOrBlank()) return found
                }
                null
            }
            else -> null
        }
    }

    private fun recursiveFindInt(node: Any?, keyAliases: Set<String>): Int? {
        return when (node) {
            is JSONObject -> {
                val keys = node.keys()
                val deferred = mutableListOf<Any?>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    if (key.lowercase() in keyAliases) {
                        val parsed = when (value) {
                            is Number -> value.toInt()
                            else -> value?.toString()?.trim()?.toIntOrNull()
                        }
                        if (parsed != null && parsed > 0) return parsed
                    }
                    if (value is JSONObject || value is JSONArray) {
                        deferred.add(value)
                    }
                }
                deferred.firstNotNullOfOrNull { recursiveFindInt(it, keyAliases) }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = recursiveFindInt(node.opt(i), keyAliases)
                    if (found != null && found > 0) return found
                }
                null
            }
            else -> null
        }
    }

    private fun unwrapArrayContainers(root: JSONObject): List<JSONArray> {
        val arrays = mutableListOf<JSONArray>()
        listOf(root, root.optJSONObject("response"), root.optJSONObject("data"), root.optJSONObject("result"))
            .filterNotNull()
            .forEach { container ->
                container.optJSONArray("hosts")?.let { arrays.add(it) }
                container.optJSONArray("resolvedProxyConfigs")?.let { arrays.add(it) }
                container.optJSONArray("resolved_proxy_configs")?.let { arrays.add(it) }
                container.optJSONArray("items")?.let { arrays.add(it) }
                container.optJSONArray("list")?.let { arrays.add(it) }
                container.optJSONArray("rows")?.let { arrays.add(it) }
                if (container == root && root.optJSONArray("response") != null) {
                    root.optJSONArray("response")?.let { arrays.add(it) }
                }
                if (container == root && root.optJSONArray("data") != null) {
                    root.optJSONArray("data")?.let { arrays.add(it) }
                }
                if (container == root && root.optJSONArray("result") != null) {
                    root.optJSONArray("result")?.let { arrays.add(it) }
                }
            }
        return arrays.distinct()
    }

    private fun parseJsonRoot(rawBody: String): Any? {
        val body = rawBody.trim()
        if (body.isBlank()) return null
        return runCatching { JSONTokener(body).nextValue() }.getOrNull()
    }

    private fun unwrapArrayContainers(root: Any?): List<JSONArray> {
        return when (root) {
            is JSONArray -> listOf(root)
            is JSONObject -> unwrapArrayContainers(root)
            else -> emptyList()
        }
    }

    /**
     * Возвращает карту serverDescription и host-level XRAY_JSON шаблонов для узлов Remnawave.
     * Ключи:
     * - uuid|hostUuid
     * - name|remark
     * - protocol|host|port
     * - templateuuid|hostUuid
     * - templatename|hostUuid
     * - templateuuidname|remark|hostUuid
     * - templateuuidhostport|host|port|hostUuid
     */
    suspend fun getHostDescriptions(
        subscriptionUrl: String,
        panelUrl: String,
        apiToken: String
    ): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val base = panelUrl.trim().ifBlank {
                    runCatching {
                        val parsed = java.net.URI(subscriptionUrl)
                        "${parsed.scheme}://${parsed.host}"
                    }.getOrDefault("")
                }.trimEnd('/')
                if (base.isBlank()) return@withContext emptyMap()

                fun JSONObject.stringFrom(vararg keys: String): String? {
                    return keys.firstNotNullOfOrNull { key ->
                        optString(key, "")
                            .trim()
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    }
                }

                fun JSONObject.intFrom(vararg keys: String): Int? {
                    return keys.firstNotNullOfOrNull { key ->
                        when (val value = opt(key)) {
                            is Number -> value.toInt().takeIf { it > 0 }
                            is String -> value.trim().toIntOrNull()?.takeIf { it > 0 }
                            else -> null
                        }
                    }
                }

                fun JSONObject.intFromAllowZero(vararg keys: String): Int? {
                    return keys.firstNotNullOfOrNull { key ->
                        when (val value = opt(key)) {
                            is Number -> value.toInt().takeIf { it >= 0 }
                            is String -> value.trim().toIntOrNull()?.takeIf { it >= 0 }
                            else -> null
                        }
                    }
                }

                fun recursiveFindIntAllowZero(node: Any?, keyAliases: Set<String>): Int? {
                    return when (node) {
                        is JSONObject -> {
                            val keys = node.keys()
                            val deferred = mutableListOf<Any?>()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = node.opt(key)
                                if (key.lowercase() in keyAliases) {
                                    val parsed = when (value) {
                                        is Number -> value.toInt()
                                        else -> value?.toString()?.trim()?.toIntOrNull()
                                    }
                                    if (parsed != null && parsed >= 0) return parsed
                                }
                                if (value is JSONObject || value is JSONArray) {
                                    deferred.add(value)
                                }
                            }
                            deferred.firstNotNullOfOrNull { recursiveFindIntAllowZero(it, keyAliases) }
                        }
                        is JSONArray -> {
                            for (i in 0 until node.length()) {
                                val found = recursiveFindIntAllowZero(node.opt(i), keyAliases)
                                if (found != null && found >= 0) return found
                            }
                            null
                        }
                        else -> null
                    }
                }

                fun hostUuidFrom(obj: JSONObject): String? {
                    val metadata = obj.optJSONObject("metadata")
                    return listOf(
                        obj.stringFrom("uuid", "hostUuid", "host_uuid"),
                        metadata?.stringFrom("uuid", "hostUuid", "host_uuid"),
                        recursiveFindString(obj, setOf("hostuuid", "host_uuid"))
                    )
                        .firstOrNull { isUuid(it) }
                        ?.trim()
                }

                fun hostRemarkFrom(obj: JSONObject): String? {
                    val metadata = obj.optJSONObject("metadata")
                    return obj.stringFrom("remark", "name", "title", "label")
                        ?: metadata?.stringFrom("remark", "name", "title", "label")
                        ?: recursiveFindString(obj, setOf("remark", "name", "title", "label"))?.trim()
                }

                fun hostAddressFrom(obj: JSONObject): String? {
                    val metadata = obj.optJSONObject("metadata")
                    val protocolOptions = obj.optJSONObject("protocolOptions")
                    return obj.stringFrom("address", "server", "host", "hostname", "domain", "ip")
                        ?: protocolOptions?.stringFrom("address", "server", "host", "hostname", "domain", "ip")
                        ?: metadata?.stringFrom("address", "server", "host", "hostname", "domain", "ip")
                        ?: recursiveFindString(obj, setOf("address", "server", "host", "hostname", "domain", "ip"))?.trim()
                }

                fun hostPortFrom(obj: JSONObject): Int {
                    val metadata = obj.optJSONObject("metadata")
                    val protocolOptions = obj.optJSONObject("protocolOptions")
                    return obj.intFrom("port", "serverPort", "server_port", "targetPort", "target_port", "listenPort", "listen_port")
                        ?: protocolOptions?.intFrom("port", "serverPort", "server_port", "targetPort", "target_port", "listenPort", "listen_port")
                        ?: metadata?.intFrom("port", "serverPort", "server_port", "targetPort", "target_port", "listenPort", "listen_port")
                        ?: recursiveFindInt(obj, setOf("port", "serverport", "server_port", "targetport", "target_port", "listenport", "listen_port"))
                        ?: 0
                }

                fun hostPositionFrom(obj: JSONObject): Int? {
                    val metadata = obj.optJSONObject("metadata")
                    return obj.intFromAllowZero("viewPosition", "view_position", "position", "sortOrder", "sort_order", "order")
                        ?: metadata?.intFromAllowZero("viewPosition", "view_position", "position", "sortOrder", "sort_order", "order")
                        ?: recursiveFindIntAllowZero(
                            obj,
                            setOf("viewposition", "view_position", "position", "sortorder", "sort_order")
                        )
                }

                fun hostProtocolFrom(obj: JSONObject): String {
                    val protocolOptions = obj.optJSONObject("protocolOptions")
                    return obj.stringFrom("protocol", "type", "securityLayer", "security_layer", "network", "configType", "config_type")
                        ?: protocolOptions?.stringFrom("protocol", "type", "network")
                        ?: recursiveFindString(obj, setOf("protocol", "type", "securitylayer", "security_layer", "network", "configtype", "config_type"))?.trim()
                        ?: ""
                }

                fun xrayTemplateObjectFrom(obj: JSONObject): JSONObject? {
                    val direct = obj.optJSONObject("xrayJsonTemplate")
                        ?: obj.optJSONObject("xray_json_template")
                        ?: obj.optJSONObject("subscriptionTemplate")
                        ?: obj.optJSONObject("subscription_template")
                    if (direct != null) return direct

                    val clientOverrides = obj.optJSONObject("clientOverrides")
                        ?: obj.optJSONObject("client_overrides")
                    return clientOverrides?.optJSONObject("xrayJsonTemplate")
                        ?: clientOverrides?.optJSONObject("xray_json_template")
                        ?: clientOverrides?.optJSONObject("subscriptionTemplate")
                        ?: clientOverrides?.optJSONObject("subscription_template")
                }

                fun xrayTemplateUuidFrom(obj: JSONObject): String? {
                    val direct = recursiveFindString(
                        obj,
                        setOf(
                            "xrayjsontemplateuuid",
                            "xray_json_template_uuid",
                            "templateuuid",
                            "template_uuid"
                        )
                    )?.trim()
                    if (isUuid(direct)) return direct

                    val templateObj = xrayTemplateObjectFrom(obj)
                    return templateObj
                        ?.stringFrom("uuid", "id", "templateUuid", "template_uuid")
                        ?.takeIf { isUuid(it) }
                }

                fun xrayTemplateNameFrom(obj: JSONObject): String? {
                    val direct = recursiveFindString(
                        obj,
                        setOf(
                            "xrayjsontemplatename",
                            "xray_json_template_name",
                            "templatename",
                            "template_name"
                        )
                    )?.trim()
                    if (!direct.isNullOrBlank()) return direct

                    return xrayTemplateObjectFrom(obj)
                        ?.stringFrom("name", "templateName", "template_name", "remark")
                }

                fun templateValueOrDefault(uuid: String?): String {
                    return uuid?.trim()?.takeIf { isUuid(it) } ?: DEFAULT_XRAY_TEMPLATE_MARKER
                }

                val shortUuid = extractShortUuid(subscriptionUrl)
                val candidates = buildList {
                    if (shortUuid.isNotBlank()) {
                        add("$base/api/subscriptions/by-short-uuid/$shortUuid/raw")
                    }
                    add("$base/api/hosts")
                    add("$base/api/v1/hosts")
                    add("$base/api/hosts/list")
                }.distinct()

                val result = mutableMapOf<String, String>()
                for (url in candidates) {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiToken")
                        .addHeader("Accept", "application/json")
                        .build()

                    val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: continue
                    response.use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string().orEmpty().trim()
                        if (body.isBlank()) return@use

                        val root = parseJsonRoot(body) ?: return@use
                        val arrays = unwrapArrayContainers(root)
                        if (arrays.isEmpty()) return@use

                        arrays.forEach { array ->
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i) ?: continue
                                val description = normalizeMaybeBase64Text(
                                    recursiveFindString(
                                    obj,
                                    setOf("serverdescription", "server_description", "description")
                                )
                                )

                                val hostUuid = hostUuidFrom(obj)
                                val hostRemark = hostRemarkFrom(obj)
                                val hostPosition = hostPositionFrom(obj)
                                val xrayTemplateUuid = xrayTemplateUuidFrom(obj)
                                val xrayTemplateName = normalizeMaybeBase64Text(xrayTemplateNameFrom(obj))
                                val templateValue = templateValueOrDefault(xrayTemplateUuid)

                                if (!hostUuid.isNullOrBlank() && isUuid(hostUuid)) {
                                    val normalizedHostUuid = hostUuid.lowercase()
                                    if (!description.isNullOrBlank()) {
                                        result["uuid|$normalizedHostUuid"] = description
                                    }
                                    result["templateuuid|$normalizedHostUuid"] = templateValue
                                    if (!xrayTemplateName.isNullOrBlank()) {
                                        result["templatename|$normalizedHostUuid"] = xrayTemplateName
                                    }
                                    if (hostPosition != null) {
                                        result["viewposition|$normalizedHostUuid"] = hostPosition.toString()
                                    }
                                }

                                if (!hostRemark.isNullOrBlank()) {
                                    val nameKey = hostNameDescriptionKey(hostRemark).substringAfter("|")
                                    if (!description.isNullOrBlank()) {
                                        result[hostNameDescriptionKey(hostRemark)] = description
                                    }
                                    result["templateuuidname|$nameKey|${hostUuid ?: i}"] = templateValue
                                    if (!xrayTemplateName.isNullOrBlank()) {
                                        result["templatenamebyname|$nameKey|${hostUuid ?: i}"] = xrayTemplateName
                                    }
                                    if (hostPosition != null) {
                                        result["viewpositionname|$nameKey|${hostUuid ?: i}"] = hostPosition.toString()
                                    }
                                }

                                val embeddedLink = recursiveFindString(
                                    obj,
                                    setOf("link", "url", "uri", "subscriptionurl", "suburl", "configurl", "serverurl")
                                )

                                if (!embeddedLink.isNullOrBlank() && embeddedLink.contains("://")) {
                                    runCatching { LinkParser.parse(embeddedLink) }.getOrNull()?.let { parsed ->
                                        if (parsed.name.isNotBlank()) {
                                            val nameKey = hostNameDescriptionKey(parsed.name).substringAfter("|")
                                            if (!description.isNullOrBlank()) {
                                                result[hostNameDescriptionKey(parsed.name)] = description
                                            }
                                            result["templateuuidname|$nameKey|${hostUuid ?: i}"] = templateValue
                                            if (!xrayTemplateName.isNullOrBlank()) {
                                                result["templatenamebyname|$nameKey|${hostUuid ?: i}"] = xrayTemplateName
                                            }
                                            if (hostPosition != null) {
                                                result["viewpositionname|$nameKey|${hostUuid ?: i}"] = hostPosition.toString()
                                            }
                                        }
                                        if (parsed.port > 0 && parsed.host.isNotBlank()) {
                                            if (!description.isNullOrBlank()) {
                                                result[hostDescriptionKey(parsed.protocol, parsed.host, parsed.port)] = description
                                            }
                                            result["templateuuidhostport|${parsed.host.trim().lowercase()}|${parsed.port}|${hostUuid ?: i}"] = templateValue
                                            if (hostPosition != null) {
                                                result["viewpositionhostport|${parsed.host.trim().lowercase()}|${parsed.port}|${hostUuid ?: i}"] = hostPosition.toString()
                                            }
                                        }
                                    }
                                }

                                val protocol = hostProtocolFrom(obj)
                                val host = hostAddressFrom(obj).orEmpty()
                                val port = hostPortFrom(obj)

                                if (protocol.isNotBlank() && host.isNotBlank() && port > 0) {
                                    if (!description.isNullOrBlank()) {
                                        result[hostDescriptionKey(protocol, host, port)] = description
                                    }
                                    result["templateuuidhostport|${host.trim().lowercase()}|$port|${hostUuid ?: i}"] = templateValue
                                    if (hostPosition != null) {
                                        result["viewpositionhostport|${host.trim().lowercase()}|$port|${hostUuid ?: i}"] = hostPosition.toString()
                                    }
                                }
                            }
                        }
                    }
                    if (result.isNotEmpty()) break
                }

                result
            } catch (e: Exception) {
                Log.w("RemnawaveApi", "Failed to fetch host descriptions: ${e.message}")
                emptyMap()
            }
        }
    }

    /**
     * Получение информации о пользователе по API Remnawave
     * @param subscriptionUrl URL подписки (например, https://sub.domain.com/abc123)
     * @param panelUrl URL панели Remnawave (например, https://panel.domain.com)
     * @param apiToken Токен API панели
     */
    suspend fun getUserInfo(
        subscriptionUrl: String,
        panelUrl: String,
        apiToken: String,
        usernameHint: String? = null
    ): RemnawaveUserInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Извлекаем shortUuid из URL подписки
                val shortUuid = extractShortUuid(subscriptionUrl)
                if (shortUuid.isEmpty()) {
                    Log.w("RemnawaveApi", "Invalid subscription URL identifier")
                    return@withContext null
                }

                val normalizedUsername = usernameHint?.trim().orEmpty()
                val encodedUsername = if (normalizedUsername.isNotBlank()) {
                    URLEncoder.encode(normalizedUsername, StandardCharsets.UTF_8.toString())
                } else {
                    ""
                }

                val candidateUrls = buildList {
                    add("${panelUrl.trimEnd('/')}/api/users/by-short-uuid/$shortUuid")
                    if (encodedUsername.isNotBlank()) {
                        add("${panelUrl.trimEnd('/')}/api/users/by-username/$encodedUsername")
                    }
                    add("${panelUrl.trimEnd('/')}/api/v1/users/by-sub-link/$shortUuid")
                }.distinct()

                var globalAnnounce: String? = null
                var supportsJsonResponse: Boolean? = null

                // 1. Сначала пробуем получить глобальное объявление из настроек подписки
                try {
                    val settingsUrl = "${panelUrl.trimEnd('/')}/api/subscription-settings"
                    val request = Request.Builder()
                        .url(settingsUrl)
                        .addHeader("Authorization", "Bearer $apiToken")
                        .addHeader("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (response.isSuccessful && body.isNotBlank()) {
                            val json = JSONObject(body)
                            val data = unwrapData(json)
                            globalAnnounce = json.optNullableString("happAnnounce")
                                ?: data.optNullableString("happAnnounce")
                                ?: json.optNullableString("announce")
                                ?: data.optNullableString("announce")

                            val serveJsonAtBase = if (data.has("serveJsonAtBaseSubscription")) {
                                data.optBoolean("serveJsonAtBaseSubscription")
                            } else false
                            val hasJsonResponseRule = data.optJSONObject("responseRules")
                                ?.optJSONArray("rules")
                                ?.let { rules ->
                                    (0 until rules.length()).any { i ->
                                        rules.optJSONObject(i)
                                            ?.optString("responseType", "")
                                            ?.contains("JSON", ignoreCase = true) == true
                                    }
                                } ?: false
                            if (data.has("serveJsonAtBaseSubscription") || data.has("responseRules")) {
                                supportsJsonResponse = serveJsonAtBase || hasJsonResponseRule
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("RemnawaveApi", "Failed to fetch global announcement header: ${e.message}")
                }

                var finalUserInfo: RemnawaveUserInfo? = null

                // 2. Затем получаем данные пользователя для трафика и лимитов
                for (apiUrl in candidateUrls) {
                    Log.d("RemnawaveApi", "Requesting subscription metadata")

                    val request = Request.Builder()
                        .url(apiUrl)
                        .addHeader("Authorization", "Bearer $apiToken")
                        .addHeader("Accept", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string().orEmpty()
                    Log.d("RemnawaveApi", "getUserInfo response: code=${response.code}, body=$responseBody")

                    if (!response.isSuccessful) continue
                    if (responseBody.isBlank()) continue

                    val json = JSONObject(responseBody)
                    val data = unwrapData(json)

                    // Поиск объявления в данных пользователя как запасной вариант
                    val userSpecificAnnounce = json.optNullableString("happAnnounce")
                        ?: data.optNullableString("happAnnounce")
                        ?: json.optNullableString("announce")
                        ?: data.optNullableString("announce")
                        ?: data.optNullableString("description")
                        ?: data.optNullableString("note")
                        ?: data.optNullableString("memo")

                    val expireSeconds = data.optLong("expireTime", 0L).takeIf { it > 0L }
                        ?: parseIsoToUnixMilli(data.optNullableString("expireAt"))?.div(1000L)

                    val infoAccountId = extractAccountId(data, shortUuid)
                    finalUserInfo = RemnawaveUserInfo(
                        accountId = infoAccountId,
                        username = data.optNullableString("username"),
                        description = globalAnnounce ?: userSpecificAnnounce,
                        uploadTotal = data.optLong("uploadTotal", 0).takeIf { it > 0 }
                            ?: data.optLong("upload", 0).takeIf { it > 0 },
                        downloadTotal = data.optLong("downloadTotal", 0).takeIf { it > 0 }
                            ?: data.optLong("download", 0).takeIf { it > 0 },
                        totalTraffic = data.optLong("totalTraffic", 0).takeIf { it > 0 }
                            ?: data.optLong("trafficLimitBytes", 0).takeIf { it > 0 },
                        expireTime = expireSeconds,
                        deviceCount = data.optInt("deviceCount", 0).takeIf { it > 0 }
                            ?: data.optInt("hwidDeviceLimit", 0).takeIf { it > 0 },
                        telegramId = data.optString("telegramId",
                                     data.optString("telegram_id",
                                     data.optString("tgId", ""))).takeIf { it.isNotBlank() },
                        shortUuid = data.optNullableString("shortUuid") ?: data.optNullableString("shortId") ?: shortUuid,
                        numericId = data.optString("id").takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } },
                        supportsJsonResponse = supportsJsonResponse
                    )
                    break
                }

                finalUserInfo

            } catch (e: Exception) {
                Log.e("RemnawaveApi", "API request error", e)
                null
            }
        }
    }

    /**
     * Добавление дней к подписке через API Remnawave
     * @param subscriptionUrl URL подписки
     * @param panelUrl URL панели Remnawave
     * @param apiToken Токен API панели
     * @param daysToAdd Количество дней для добавления
     */
    suspend fun addDaysToSubscription(
        subscriptionUrl: String,
        panelUrl: String,
        apiToken: String,
        accountId: String? = null,
        usernameHint: String? = null,
        daysToAdd: Int
    ): RemnawaveAddDaysResult {
        return withContext(Dispatchers.IO) {
            try {
                // Извлекаем shortUuid из URL подписки
                val shortUuid = extractShortUuid(subscriptionUrl)
                if (shortUuid.isEmpty()) {
                    Log.w("RemnawaveApi", "Invalid subscription URL identifier")
                    return@withContext RemnawaveAddDaysResult(
                        success = false,
                        newExpireTime = null,
                        addedDays = 0,
                        message = "Invalid subscription URL"
                    )
                }

                val base = panelUrl.trimEnd('/')
                val info = getUserInfo(
                    subscriptionUrl = subscriptionUrl,
                    panelUrl = panelUrl,
                    apiToken = apiToken,
                    usernameHint = usernameHint
                )
                val resolvedAccountId = accountId ?: info?.accountId
                val beforeExpireTimeSec = info?.expireTime ?: 0L
                val nowSec = System.currentTimeMillis() / 1000L
                val expectedExpireLowerBound = maxOf(beforeExpireTimeSec, nowSec) +
                    (daysToAdd.toLong() * 24L * 60L * 60L) - (12L * 60L * 60L)

                suspend fun verifyExpireWasExtended(): Long? {
                    val refreshed = getUserInfo(
                        subscriptionUrl = subscriptionUrl,
                        panelUrl = panelUrl,
                        apiToken = apiToken,
                        usernameHint = info?.username ?: usernameHint
                    )
                    val refreshedExpire = refreshed?.expireTime ?: 0L
                    if (refreshedExpire <= 0L) return null
                    if (refreshedExpire >= expectedExpireLowerBound) return refreshedExpire
                    if (beforeExpireTimeSec > 0L && refreshedExpire > beforeExpireTimeSec + (6L * 60L * 60L)) {
                        return refreshedExpire
                    }
                    return null
                }

                val patchedByUserEndpoint = runCatching {
                    val currentExpireMilli = info?.expireTime?.times(1000L) ?: 0L
                    val baseExpireMilli = maxOf(currentExpireMilli, System.currentTimeMillis())
                    val newExpireMilli = baseExpireMilli + (daysToAdd.toLong() * 24L * 60L * 60L * 1000L)
                    val newExpireAtIso = Instant.ofEpochMilli(newExpireMilli).toString()

                    val updatePayload = JSONObject().apply {
                        if (isUuid(resolvedAccountId)) {
                            put("uuid", resolvedAccountId)
                        } else {
                            put("username", info?.username ?: usernameHint ?: shortUuid)
                        }
                        put("expireAt", newExpireAtIso)
                    }.toString().toRequestBody(jsonMediaType)

                    val patchRequest = Request.Builder()
                        .url("$base/api/users")
                        .patch(updatePayload)
                        .addHeader("Authorization", "Bearer $apiToken")
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val patchResponse = client.newCall(patchRequest).execute()
                    val patchBody = patchResponse.body?.string().orEmpty()
                    Log.d("RemnawaveApi", "PATCH /api/users response: ${patchResponse.code}, body: $patchBody")
                    if (!patchResponse.isSuccessful) return@runCatching null

                    val verifiedExpire = verifyExpireWasExtended()
                    if (verifiedExpire == null) {
                        Log.w("RemnawaveApi", "PATCH /api/users returned success but expireTime did not increase")
                        return@runCatching null
                    }

                    RemnawaveAddDaysResult(
                        success = true,
                        newExpireTime = verifiedExpire,
                        addedDays = daysToAdd,
                        message = "Successfully added $daysToAdd days"
                    )
                }.getOrElse {
                    Log.w("RemnawaveApi", "PATCH /api/users add days failed: ${it.message}")
                    null
                }
                if (patchedByUserEndpoint != null) return@withContext patchedByUserEndpoint

                val bulkUrls = listOf(
                    "$base/api/users/bulk/extend-expiration-date",
                    "$base/api/users/bulk/all/extend-expiration-date"
                )
                val bulkPayloads = buildList {
                    if (!resolvedAccountId.isNullOrBlank()) {
                        add(JSONObject().apply {
                            put("userUUIDs", org.json.JSONArray().put(resolvedAccountId))
                            put("days", daysToAdd)
                        }.toString())
                        add(JSONObject().apply {
                            put("userUuids", org.json.JSONArray().put(resolvedAccountId))
                            put("daysToAdd", daysToAdd)
                        }.toString())
                        add(JSONObject().apply {
                            put("userIds", org.json.JSONArray().put(resolvedAccountId))
                            put("days", daysToAdd)
                        }.toString())
                    }
                    add(JSONObject().apply {
                        put("shortUuids", org.json.JSONArray().put(shortUuid))
                        put("days", daysToAdd)
                    }.toString())
                }.distinct()

                for (bulkUrl in bulkUrls) {
                    for (bulkPayload in bulkPayloads) {
                        for (method in listOf("POST", "PATCH", "PUT")) {
                            val reqBody = bulkPayload.toRequestBody(jsonMediaType)
                            val requestBuilder = Request.Builder()
                                .url(bulkUrl)
                                .addHeader("Authorization", "Bearer $apiToken")
                                .addHeader("Accept", "application/json")
                                .addHeader("Content-Type", "application/json")
                            val request = when (method) {
                                "POST" -> requestBuilder.post(reqBody).build()
                                "PATCH" -> requestBuilder.patch(reqBody).build()
                                else -> requestBuilder.put(reqBody).build()
                            }

                            val response = client.newCall(request).execute()
                            val responseBody = response.body?.string().orEmpty()
                            Log.d(
                                "RemnawaveApi",
                                "Bulk extend response: method=$method, code=${response.code}, url=$bulkUrl, body=$responseBody"
                            )
                            if (!response.isSuccessful) continue

                            val verifiedExpire = verifyExpireWasExtended()
                            if (verifiedExpire != null) {
                                return@withContext RemnawaveAddDaysResult(
                                    success = true,
                                    newExpireTime = verifiedExpire,
                                    addedDays = daysToAdd,
                                    message = "Successfully added $daysToAdd days"
                                )
                            }
                        }
                    }
                }

                val candidateUrls = buildList {
                    add("$base/api/v1/users/$shortUuid/add-days")
                    add("$base/api/v1/users/$shortUuid/add-days?days=$daysToAdd")
                    add("$base/api/v1/users/by-sub-link/$shortUuid/add-days")
                    add("$base/api/v1/users/by-sub-link/$shortUuid/add-days?days=$daysToAdd")
                    if (!resolvedAccountId.isNullOrBlank()) {
                        add("$base/api/v1/users/$resolvedAccountId/add-days")
                        add("$base/api/v1/users/$resolvedAccountId/add-days?days=$daysToAdd")
                    }
                }.distinct()

                val payload = JSONObject().apply {
                    put("days", daysToAdd)
                    put("addDays", daysToAdd)
                    put("daysToAdd", daysToAdd)
                }.toString().toRequestBody(jsonMediaType)

                val methods = listOf("PUT", "POST", "PATCH")
                var lastError: String? = null

                for (apiUrl in candidateUrls) {
                    for (method in methods) {
                        val requestBuilder = Request.Builder()
                            .url(apiUrl)
                            .addHeader("Authorization", "Bearer $apiToken")
                            .addHeader("Accept", "application/json")
                            .addHeader("Content-Type", "application/json")

                        val request = when (method) {
                            "PUT" -> requestBuilder.put(payload).build()
                            "POST" -> requestBuilder.post(payload).build()
                            "PATCH" -> requestBuilder.patch(payload).build()
                            else -> requestBuilder.put(payload).build()
                        }

                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string().orEmpty()
                        val allowHeader = response.header("Allow").orEmpty()
                        Log.d(
                            "RemnawaveApi",
                            "Add days response: method=$method, code=${response.code}, url=$apiUrl, allow=$allowHeader, body=$responseBody"
                        )

                        if (response.code == 404 || response.code == 405) {
                            lastError = "HTTP ${response.code}"
                            continue
                        }

                        if (!response.isSuccessful) {
                            val parsedMessage = runCatching {
                                val root = JSONObject(responseBody)
                                val data = root.optJSONObject("data") ?: root.optJSONObject("result") ?: root
                                data.optNullableString("message")
                                    ?: data.optNullableString("error")
                                    ?: root.optNullableString("message")
                            }.getOrNull()

                            return@withContext RemnawaveAddDaysResult(
                                success = false,
                                newExpireTime = null,
                                addedDays = 0,
                                message = parsedMessage ?: "Сервер отклонил выдачу бонуса (${response.code})"
                            )
                        }

                        val json = JSONObject(responseBody)
                        val data = json.optJSONObject("data") ?: json.optJSONObject("result") ?: json
                        val responseExpireTime = readTimestamp(data, "expireTime", "newExpireTime", "expiresAt")?.div(1000L)
                        val message = data.optNullableString("message")
                            ?: json.optNullableString("message")
                        val verifiedExpire = verifyExpireWasExtended()
                            ?: responseExpireTime?.takeIf {
                                it >= expectedExpireLowerBound ||
                                    (beforeExpireTimeSec > 0L && it > beforeExpireTimeSec + (6L * 60L * 60L))
                            }
                        if (verifiedExpire == null) {
                            lastError = "No expiration change detected"
                            continue
                        }

                        return@withContext RemnawaveAddDaysResult(
                            success = true,
                            newExpireTime = verifiedExpire,
                            addedDays = daysToAdd,
                            message = message ?: "Successfully added $daysToAdd days"
                        )
                    }
                }

                RemnawaveAddDaysResult(
                    success = false,
                    newExpireTime = null,
                    addedDays = 0,
                    message = "Remnawave did not confirm expiration update (${lastError ?: "no supported method"})"
                )

            } catch (e: Exception) {
                Log.e("RemnawaveApi", "API request error", e)
                RemnawaveAddDaysResult(
                    success = false,
                    newExpireTime = null,
                    addedDays = 0,
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Упрощённый метод - только username из API
     */
    suspend fun getUsername(subscriptionUrl: String, panelUrl: String, apiToken: String): String? {
        return getUserInfo(subscriptionUrl, panelUrl, apiToken)?.username
    }

    /**
     * Извлекает уникальный идентификатор пользователя (shortUuid или токен) из URL подписки.
     * Поддерживает форматы:
     * - https://panel.com/api/v1/client/subscribe?token=ABC-123
     * - https://panel.com/sub/ABC-123
     * - https://panel.com/api/v1/client/subscribe/ABC-123
     */
    private fun extractShortUuid(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val query = uri.query

            // 1. Ищем в параметрах запроса (?token=... или ?id=...)
            if (!query.isNullOrBlank()) {
                val params = query.split("&").associate {
                    val pair = it.split("=")
                    if (pair.size == 2) pair[0].lowercase() to pair[1] else "" to ""
                }
                params["token"]?.let { return it }
                params["id"]?.let { return it }
                params["shortuuid"]?.let { return it }
                params["uuid"]?.let { return it }
            }

            // 2. Ищем в пути (последний сегмент)
            val path = uri.path.trimEnd('/')
            val segments = path.split("/")
            val lastSegment = segments.lastOrNull()?.substringBefore("?")?.substringBefore("#")

            if (!lastSegment.isNullOrBlank() && lastSegment != "subscribe" && lastSegment != "sub") {
                return lastSegment
            }

            ""
        } catch (e: Exception) {
            // Fallback на старую логику, если URI не распарсился
            url.substringAfterLast("/")
                .substringBefore("?")
                .substringBefore("#")
                .trim()
                .takeIf { it != "subscribe" && it != "sub" && it != url }
                .orEmpty()
        }
    }

    private fun extractAccountId(data: JSONObject, fallbackShortUuid: String): String {
        val uuidCandidates = listOf(
            data.optString("uuid", ""),
            data.optString("_id", ""),
            data.optString("userId", ""),
            data.optString("userUuid", ""),
            data.optString("userUUID", ""),
            data.optString("user_uuid", "")
        ).map { it.trim() }

        val shortCandidates = listOf(
            data.optString("shortUuid", "")
        ).map { it.trim() }

        // Для операций с устройствами нужен именно UUID пользователя.
        val resolvedUuid = uuidCandidates.firstOrNull { it.isNotBlank() && isUuid(it) }
        if (resolvedUuid != null) return resolvedUuid

        // Fallback: shortUuid из профиля, если UUID не пришел.
        return shortCandidates.firstOrNull { it.isNotBlank() } ?: fallbackShortUuid
    }

    private fun readTimestamp(json: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            if (!json.has(key)) continue

            // Если значение - строка (например, ISO), пытаемся распарсить её
            val strValue = json.optString(key, "")
            if (strValue.isNotBlank() && (strValue.contains("T") || strValue.contains("-"))) {
                parseIsoToUnixMilli(strValue)?.let { return it }
            }

            // Если числовое значение
            val raw = json.optLong(key, 0L)
            if (raw <= 0L) continue
            return if (raw < 1_000_000_000_000L) raw * 1000L else raw
        }
        return null
    }

    private fun JSONObject.optNullableString(key: String): String? {
        val value = optString(key, "")
        return value.takeIf { it.isNotBlank() }
    }

    private fun unwrapData(json: JSONObject): JSONObject {
        return json.optJSONObject("response") ?: json.optJSONObject("data") ?: json.optJSONObject("result") ?: json
    }

    private fun parseIsoToUnixMilli(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Instant.parse(value).toEpochMilli()
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(value)?.time
            }
        } catch (_: Exception) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Instant.parse(value.substringBefore(".") + "Z").toEpochMilli()
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            .matches(value)
    }

    private fun normalizeJsonSubscriptionUrl(subscriptionUrl: String): String {
        val trimmed = subscriptionUrl.trim()
        if (trimmed.isBlank()) return trimmed
        val queryPart = trimmed.substringAfter("?", "")
        val base = if (queryPart.isNotBlank()) trimmed.substringBefore("?") else trimmed
        val normalizedBase = if (base.endsWith("/json")) base else "$base/json"
        return if (queryPart.isNotBlank()) "$normalizedBase?$queryPart" else normalizedBase
    }

    private fun appendTemplateParam(url: String, key: String, value: String?): String {
        if (value.isNullOrBlank()) return url
        val separator = if (url.contains("?")) "&" else "?"
        val encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
        return "$url${separator}${key}=$encoded"
    }

    private fun xraySubscriptionUserAgents(): List<String> {
        val context = NebulaGuardApplication.instance
        return listOf(AppVersionManager.getUserAgent(context))
    }

    private fun buildTemplateParameterizedUrls(
        baseJsonUrl: String,
        templateUuid: String?,
        templateName: String?
    ): List<String> {
        val urls = mutableListOf<String>()
        val uuid = templateUuid?.trim()?.takeIf { it.isNotBlank() }
        val name = templateName?.trim()?.takeIf { it.isNotBlank() }

        if (uuid != null) {
            val uuidKeys = listOf(
                "templateUuid",
                "templateId",
                "templateUUID",
                "template_uuid",
                "xrayJsonTemplateUuid",
                "xray_json_template_uuid",
                "subscriptionTemplateUuid",
                "subscription_template_uuid"
            )
            uuidKeys.forEach { key -> urls += appendTemplateParam(baseJsonUrl, key, uuid) }
        }
        if (name != null) {
            val nameKeys = listOf(
                "template",
                "templateName",
                "template_name",
                "xrayJsonTemplateName",
                "xray_json_template_name",
                "subscriptionTemplate",
                "subscription_template"
            )
            nameKeys.forEach { key -> urls += appendTemplateParam(baseJsonUrl, key, name) }
        }
        return urls.distinct()
    }

    suspend fun getSubscriptionTemplates(
        panelUrl: String,
        apiToken: String
    ): List<RemnawaveSubscriptionTemplate> = withContext(Dispatchers.IO) {
        val base = panelUrl.trim().trimEnd('/')
        if (base.isBlank() || apiToken.isBlank()) return@withContext emptyList()

        return@withContext try {
            val candidates = listOf(
                "$base/api/subscription-templates?_t=${System.currentTimeMillis()}",
                "$base/api/subscription-templates",
                "$base/api/v1/subscription-templates"
            )

            fun resolveTemplateArray(root: Any?): JSONArray {
                when (root) {
                    is JSONArray -> return root
                    is JSONObject -> {
                        val container = root.optJSONObject("response")
                            ?: root.optJSONObject("data")
                            ?: root.optJSONObject("result")
                            ?: root
                        container.optJSONArray("templates")?.let { return it }
                        container.optJSONArray("items")?.let { return it }
                        container.optJSONArray("list")?.let { return it }
                        root.optJSONArray("templates")?.let { return it }
                        root.optJSONArray("items")?.let { return it }
                        root.optJSONArray("data")?.let { return it }
                        root.optJSONArray("result")?.let { return it }
                        if (root.opt("response") is JSONArray) return root.getJSONArray("response")
                        if (root.opt("data") is JSONArray) return root.getJSONArray("data")
                        if (root.opt("result") is JSONArray) return root.getJSONArray("result")
                    }
                }
                return JSONArray()
            }

            for (url in candidates) {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiToken")
                    .addHeader("Accept", "application/json")
                    .addHeader("Cache-Control", "no-cache")
                    .addHeader("Pragma", "no-cache")
                    .build()

                val templates = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string().orEmpty()
                    val root = parseJsonRoot(body) ?: return@use emptyList()
                    val array = resolveTemplateArray(root)
                    buildList {
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val uuid = item.optString("uuid", "")
                                .ifBlank { item.optString("id", "") }
                                .ifBlank { item.optString("templateUuid", "") }
                                .ifBlank { item.optString("template_uuid", "") }
                                .trim()
                            if (uuid.isBlank()) continue
                            val templateType = item.optString("templateType")
                                .ifBlank { item.optString("template_type") }
                                .ifBlank { item.optString("type") }
                                .trim()
                            val templateName = item.optString("name", "")
                                .ifBlank { item.optString("templateName", "") }
                                .ifBlank { item.optString("template_name", "") }
                                .ifBlank { item.optString("remark", "") }
                                .trim()
                            val viewPosition = when {
                                item.has("viewPosition") -> item.optInt("viewPosition", Int.MAX_VALUE)
                                item.has("view_position") -> item.optInt("view_position", Int.MAX_VALUE)
                                item.has("position") -> item.optInt("position", Int.MAX_VALUE)
                                else -> Int.MAX_VALUE
                            }
                            add(
                                RemnawaveSubscriptionTemplate(
                                    uuid = uuid,
                                    name = templateName,
                                    templateType = templateType,
                                    viewPosition = viewPosition,
                                    isDefault = item.optBoolean("isDefault")
                                        || item.optBoolean("is_default")
                                        || item.optBoolean("default")
                                        || item.optBoolean("isDefaultTemplate")
                                        || item.optBoolean("is_default_template")
                                        || item.optBoolean("defaultTemplate")
                                        || ((templateType.equals("XRAY_JSON", ignoreCase = true) || templateType.equals("XRAY", ignoreCase = true)) &&
                                            templateName.equals("Default", ignoreCase = true))
                                )
                            )
                        }
                    }
                }

                if (templates.isNotEmpty()) return@withContext templates
            }
            emptyList()
        } catch (e: Exception) {
            Log.w("RemnawaveApi", "Failed to load subscription templates: ${e.message}")
            emptyList()
        }
    }

    suspend fun getXrayConfigByTemplate(
        subscriptionUrl: String,
        panelUrl: String,
        apiToken: String,
        templateUuidHint: String? = null,
        templateNameHint: String? = null,
        serverNameHint: String? = null,
        serverHostHint: String? = null,
        serverPortHint: Int? = null,
        templatesOverride: List<RemnawaveSubscriptionTemplate>? = null
    ): String? = withContext(Dispatchers.IO) {
        val templates = (templatesOverride ?: getSubscriptionTemplates(panelUrl, apiToken))
            .filter {
                val t = it.templateType.trim().lowercase()
                t == "xray_json" || t.contains("xray") || t.contains("json")
            }
            .sortedBy { it.viewPosition }

        val selected = selectTemplateByHints(
            templates = templates,
            templateUuidHint = templateUuidHint,
            templateNameHint = templateNameHint
        )
        val defaultTemplate = templates.firstOrNull { it.isDefault }
            ?: templates.firstOrNull { it.name.equals("Default", ignoreCase = true) }
            ?: templates.firstOrNull { it.name.trim().lowercase() == "default" }
            ?: templates.firstOrNull()
        Log.d(
            "RemnawaveApi",
            "XRAY template resolve: selectedUuid=${selected?.uuid ?: "-"}, selectedName=${selected?.name ?: "-"}, " +
                "defaultUuid=${defaultTemplate?.uuid ?: "-"}, defaultName=${defaultTemplate?.name ?: "-"}"
        )

        val baseJsonUrl = normalizeJsonSubscriptionUrl(subscriptionUrl)
        val candidates = buildList {
            // Если шаблон явно не выбран — берём именно Default из панели.
            // Голый /json не используем как fallback, чтобы не терять panel-template.
            if (selected == null) {
                if (defaultTemplate != null) {
                    addAll(buildTemplateParameterizedUrls(baseJsonUrl, defaultTemplate.uuid, defaultTemplate.name))
                }
            }
            if (selected != null) {
                addAll(buildTemplateParameterizedUrls(baseJsonUrl, selected.uuid, selected.name))
            }
            // Remnawave host-level Xray JSON templates are resolved by the subscription endpoint itself.
            // Query params above are kept for compatibility, but /json is the canonical fallback.
            add(baseJsonUrl)
        }.distinct()

        fun normalizedText(value: String?): String {
            return value
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        }

        fun looksLikeXrayConfig(json: JSONObject): Boolean {
            return isUsableXrayClientConfig(json)
        }

        fun configRemarks(json: JSONObject): String {
            return json.optString("remarks")
                .ifBlank { json.optString("remark") }
                .ifBlank { json.optString("name") }
                .trim()
        }

        fun pickConfigFromArray(array: JSONArray): JSONObject? {
            if (array.length() == 0) return null

            val objects = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    if (looksLikeXrayConfig(obj)) add(obj)
                }
            }
            if (objects.isEmpty()) return null
            if (objects.size == 1) return objects.first()

            val serverName = normalizedText(serverNameHint)
            if (serverName.isNotBlank()) {
                objects.firstOrNull { normalizedText(configRemarks(it)) == serverName }?.let { return it }
                objects.firstOrNull {
                    val remarks = normalizedText(configRemarks(it))
                    remarks.isNotBlank() && (remarks.contains(serverName) || serverName.contains(remarks))
                }?.let { return it }
            }

            val templateName = normalizedText(templateNameHint)
            if (templateName.isNotBlank()) {
                objects.firstOrNull { normalizedText(configRemarks(it)) == templateName }?.let { return it }
            }

            val hostHint = normalizedText(serverHostHint)
            if (hostHint.isNotBlank()) {
                objects.firstOrNull { obj ->
                    obj.optJSONArray("outbounds")?.let { outbounds ->
                        (0 until outbounds.length()).any { index ->
                            val outbound = outbounds.optJSONObject(index) ?: return@any false
                            val serialized = outbound.toString().lowercase(Locale.ROOT)
                            serialized.contains(hostHint) &&
                                (serverPortHint == null || serialized.contains(serverPortHint.toString()))
                        }
                    } == true
                }?.let { return it }
            }

            Log.w(
                "RemnawaveApi",
                "XRAY JSON array has ${objects.size} configs, but no config matched serverName=${serverNameHint ?: "-"}, " +
                    "templateName=${templateNameHint ?: "-"}, host=${serverHostHint ?: "-"}"
            )
            return null
        }

        for (url in candidates) {
            for (userAgent in xraySubscriptionUserAgents()) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", userAgent)
                        .addHeader("Accept", "application/json")
                        .addHeader("Cache-Control", "no-cache")
                        .addHeader("Pragma", "no-cache")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body?.string()?.trim().orEmpty()
                        when {
                            body.startsWith("{") -> {
                                val json = JSONObject(body)
                                if (looksLikeXrayConfig(json)) {
                                    Log.d(
                                        "RemnawaveApi",
                                        "XRAY JSON config loaded from $url using UA=${userAgent.substringBefore(' ')}"
                                    )
                                    return@withContext body
                                }
                            }
                            body.startsWith("[") -> {
                                val selectedConfig = pickConfigFromArray(JSONArray(body))
                                if (selectedConfig != null) {
                                    Log.d(
                                        "RemnawaveApi",
                                        "XRAY JSON config selected from array at $url using UA=${userAgent.substringBefore(' ')}"
                                    )
                                    return@withContext selectedConfig.toString()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("RemnawaveApi", "Failed to load XRAY JSON: ${e::class.java.simpleName}")
                }
            }
        }
        null
    }

    /**
     * Получение готовой конфигурации sing-box из Remnawave
     * @param subscriptionUrl URL подписки
     * @param panelUrl URL панели Remnawave
     * @param apiToken Токен API панели
     * @return JSON строка с конфигурацией sing-box или null
     */
    suspend fun getSingBoxConfig(
        subscriptionUrl: String,
        panelUrl: String,
        apiToken: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Извлекаем identifier из URL подписки
                val identifier = extractShortUuid(subscriptionUrl)
                if (identifier.isEmpty()) {
                    Log.w("RemnawaveApi", "Invalid subscription URL identifier")
                    return@withContext null
                }

                // Request the optional sing-box config with the same Nimbo identity
                // used by every other subscription request.
                val configClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

                val context = NebulaGuardApplication.instance
                val osVersion = AppVersionManager.getOSVersion()
                val deviceModel = AppVersionManager.getDeviceModel()
                val appVersion = AppVersionManager.getVersionName(context)

                val request = Request.Builder()
                    .url(subscriptionUrl)
                    .addHeader("User-Agent", AppVersionManager.getUserAgent(context))
                    .addHeader("x-device-id", deviceId)
                    .addHeader("x-hwid", deviceId)
                    .addHeader("x-device-os", "Android")
                    .addHeader("x-ver-os", osVersion)
                    .addHeader("x-device-model", deviceModel)
                    .addHeader("x-app-version", appVersion)
                    .build()

                Log.d("RemnawaveApi", "Requesting sing-box config")
                val response = configClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w("RemnawaveApi", "Failed to get config: ${response.code}")
                    return@withContext null
                }

                val config = response.body?.string()
                Log.d("RemnawaveApi", "Received response, length: ${config?.length}")
                if (config != null) {
                    Log.d("RemnawaveApi", "Received non-empty sing-box response")
                }

                // Проверяем, что это JSON (sing-box конфиг)
                if (config != null && config.trim().startsWith("{")) {
                    Log.d("RemnawaveApi", "Got valid sing-box JSON config")
                    return@withContext config
                }

                // Если не JSON, значит это base64 со списком ссылок
                Log.d("RemnawaveApi", "Response is not JSON, falling back to link parser")
                null

            } catch (e: Exception) {
                Log.e("RemnawaveApi", "Error getting sing-box config", e)
                null
            }
        }
    }

    /**
     * Получение списка устройств (HWID) пользователя
     */
    suspend fun getDevices(
        subscriptionUrl: String,
        panelUrl: String,
        apiToken: String,
        userUuid: String? = null
    ): List<RemnawaveDevice> {
        return withContext(Dispatchers.IO) {
            try {
                val shortUuid = extractShortUuid(subscriptionUrl)
                val base = panelUrl.trimEnd('/')

                // 🔥 Registration hit: Force the panel to register the current device
                try {
                    Log.d("RemnawaveApi", "Performing subscription registration request")
                    val registrationRequest = Request.Builder()
                        .url(subscriptionUrl)
                        .addHeader("User-Agent", userAgent)
                        .build()
                    client.newCall(registrationRequest).execute().use { _ -> }
                } catch (e: Exception) {
                    Log.w("RemnawaveApi", "Registration hit failed: ${e.message}")
                }

                val normalizedUserUuid = userUuid?.trim().orEmpty()
                val userUuidIsValid = isUuid(normalizedUserUuid)

                val candidateUrls = buildList {
                    // Try the official user-scoped endpoint first
                    if (userUuidIsValid) {
                        add("$base/api/hwid/devices/$normalizedUserUuid")
                    }

                    // Try global endpoint as fallback
                    add("$base/api/hwid/devices")

                    // Other fallbacks
                    if (userUuidIsValid) {
                        add("$base/api/v1/users/$normalizedUserUuid/devices")
                    }
                    if (shortUuid.isNotBlank()) {
                        add("$base/api/v1/users/by-short-uuid/$shortUuid/devices")
                        add("$base/api/v1/users/by-sub-link/$shortUuid/devices")
                    }
                }.distinct()

                Log.d("RemnawaveApi", "Device endpoint candidates: ${candidateUrls.size}")

                for (apiUrl in candidateUrls) {
                    Log.d("RemnawaveApi", "Requesting devices")
                    val isScopedByUserEndpoint = userUuidIsValid && (
                        apiUrl.contains("/api/hwid/devices/$normalizedUserUuid") ||
                        apiUrl.contains("/api/v1/users/$normalizedUserUuid/devices") ||
                        apiUrl.contains("/api/v1/users/by-short-uuid/") ||
                        apiUrl.contains("/api/v1/users/by-sub-link/")
                    )
                    val request = Request.Builder()
                        .url(apiUrl)
                        .addHeader("Authorization", "Bearer $apiToken")
                        .addHeader("Accept", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        Log.w("RemnawaveApi", "getDevices failed: [${response.code}]")
                        continue
                    }

                    Log.d("RemnawaveApi", "getDevices body: $body")

                    val finalArray = try {
                        val trimmed = body.trim()
                        if (trimmed.startsWith("[")) {
                            org.json.JSONArray(trimmed)
                        } else {
                            val json = JSONObject(trimmed)
                            val data = unwrapData(json)
                            if (data.has("devices")) {
                                data.optJSONArray("devices")
                            } else if (data.has("items")) {
                                data.optJSONArray("items")
                            } else if (json.has("data") && json.opt("data") is org.json.JSONArray) {
                                json.getJSONArray("data")
                            } else if (json.has("result") && json.opt("result") is org.json.JSONArray) {
                                json.getJSONArray("result")
                            } else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("RemnawaveApi", "Failed to parse devices json: ${e.message}")
                        null
                    }

                    if (finalArray == null) {
                        Log.w("RemnawaveApi", "Could not find devices array in response")
                        continue
                    }

                    val devices = mutableListOf<RemnawaveDevice>()
                    for (i in 0 until finalArray.length()) {
                        val obj = finalArray.getJSONObject(i)

                        // Filter by userUuid if provided
                        val deviceUserUuid = listOf(
                            obj.optString("userUuid", ""),
                            obj.optString("userUUID", ""),
                            obj.optString("user_uuid", "")
                        )
                            .map { it.trim() }
                            .firstOrNull { isUuid(it) }
                        if (userUuidIsValid) {
                            if (deviceUserUuid != null && !deviceUserUuid.equals(normalizedUserUuid, ignoreCase = true)) {
                                continue
                            }
                            if (deviceUserUuid == null && !isScopedByUserEndpoint) {
                                continue
                            }
                        }

                        val hwid = obj.optString("hwid", obj.optString("deviceId", ""))

                        // Построение имени устройства: пробуем разные ключи для бренда и модели
                        var brand = obj.optString("brand",
                                    obj.optString("manufacturer",
                                    obj.optString("vendor", ""))).trim()

                        var model = obj.optString("model",
                                    obj.optString("deviceModel",
                                    obj.optString("device_model",
                                    obj.optString("product", "")))).trim()

                        var platform = obj.optString("platform", obj.optString("os", "")).trim()

                        // Поиск в метаданных/экстра
                        if (brand.isBlank() && model.isBlank()) {
                            val meta = obj.optJSONObject("metadata") ?: obj.optJSONObject("extra")
                            if (meta != null) {
                                brand = meta.optString("brand",
                                        meta.optString("manufacturer",
                                        meta.optString("vendor", ""))).trim()
                                model = meta.optString("model",
                                        meta.optString("deviceModel",
                                        meta.optString("device_model",
                                        meta.optString("product", "")))).trim()
                                if (platform.isBlank()) {
                                    platform = meta.optString("platform", meta.optString("os", "")).trim()
                                }
                            }
                        }

                        val rawName = obj.optString("name", "")
                        val deviceName = when {
                            rawName.isNotBlank() && rawName != "Устройство" && !rawName.startsWith("#") -> rawName
                            brand.isNotBlank() || model.isNotBlank() -> "$brand $model".trim()
                            platform.isNotBlank() -> platform
                            else -> "Устройство"
                        }

                        if (i == 0) {
                            Log.d("RemnawaveApi", "Parsed first device object")
                        }

                        devices.add(RemnawaveDevice(
                            id = obj.optString("uuid", obj.optString("id", "")).ifBlank {
                                hwid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                            },
                            name = deviceName,
                            hwid = hwid,
                            lastSeenAt = readTimestamp(obj, "lastSeenAt", "last_seen_at", "updatedAt"),
                            createdAt = readTimestamp(obj, "createdAt", "created_at"),
                            isCurrent = hwid.equals(deviceId, ignoreCase = true),
                            numericId = obj.optString("id").takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } },
                            userUuid = deviceUserUuid ?: normalizedUserUuid.takeIf { isScopedByUserEndpoint }
                        ))
                    }
                    Log.d("RemnawaveApi", "Fetched ${devices.size} devices")
                    if (devices.isNotEmpty()) {
                        return@withContext devices.sortedByDescending { it.lastSeenAt ?: 0L }
                    }

                    // Если endpoint вернул 200, но devices пустой, пробуем следующие fallback URL.
                    Log.w("RemnawaveApi", "Devices list is empty, trying next fallback")
                }

                Log.w("RemnawaveApi", "All device candidate URLs failed")
                emptyList()

            } catch (e: Exception) {
                Log.e("RemnawaveApi", "Error getting devices", e)
                emptyList()
            }
        }
    }

    /**
     * Переименование устройства
     */
    suspend fun renameDevice(
        panelUrl: String,
        apiToken: String,
        subscriptionUrl: String,
        deviceId: String,
        hwid: String?,
        newName: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            // Пытаемся обновить через "регистрацию" по прямой ссылке с HWID
            // Это обходной путь для Remnawave, если нет API метода rename
            if (!hwid.isNullOrBlank()) {
                try {
                    Log.d("RemnawaveApi", "Attempting device rename via subscription sync for HWID: $hwid")
                    val subRequest = Request.Builder()
                        .url(subscriptionUrl)
                        .get()
                        .addHeader("User-Agent", userAgent)
                        .addHeader("x-hwid", hwid)
                        .addHeader("x-device-os", "Android")
                        .addHeader("x-device-model", newName) // Новое название отправляем как модель
                        .addHeader("Cache-Control", "no-cache")
                        .build()

                    val subResponse = client.newCall(subRequest).execute()
                    Log.d("RemnawaveApi", "Rename sync response for $hwid: ${subResponse.code}")
                    if (subResponse.isSuccessful) return@withContext true
                } catch (e: Exception) {
                    Log.w("RemnawaveApi", "Failed to rename via sub sync, falling back to REST API", e)
                }
            }

            // Fallback: Стандартный PATCH (может не работать в новых версиях)
            try {
                val apiUrl = "${panelUrl.trimEnd('/')}/api/v1/devices/$deviceId"
                val payload = JSONObject().apply { put("name", newName) }
                    .toString()
                    .toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(apiUrl)
                    .patch(payload)
                    .addHeader("Authorization", "Bearer $apiToken")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                Log.d("RemnawaveApi", "Rename REST fallback response: ${response.code}")
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("RemnawaveApi", "Error renaming device via REST", e)
                false
            }
        }
    }

    /**
     * Удаление (отвязка) устройства
     * Пробует несколько эндпоинтов и методов (DELETE, POST) для совместимости с разными версиями Remnawave.
     * Основной приоритет - POST /api/hwid/devices/delete c userUuid и hwid.
     */
    suspend fun deleteDevice(
        panelUrl: String,
        apiToken: String,
        userUuid: String? = null,
        hwid: String? = null,
        deviceId: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val base = panelUrl.trimEnd('/')

            // Список кандидатов (URL и метод/тип тела)
            val candidates = mutableListOf<Triple<String, String, String?>>()

            // 1. Приоритет согласно примеру пользователя (POST {userUuid, hwid})
            if (!userUuid.isNullOrBlank() && !hwid.isNullOrBlank()) {
                candidates.add(Triple("$base/api/hwid/devices/delete", "POST_USER_HWID", null))
            }

            // 2. Стандартные REST кандидаты
            if (!deviceId.isNullOrBlank()) {
                candidates.add(Triple("$base/api/v1/devices/$deviceId", "DELETE", null))
                candidates.add(Triple("$base/api/hwid/devices/$deviceId", "DELETE", null))
                candidates.add(Triple("$base/api/hwid/devices/delete", "POST", "id"))
                candidates.add(Triple("$base/api/hwid/devices/delete", "POST", "uuid"))
                candidates.add(Triple("$base/api/v1/devices/delete", "POST", "id"))
                candidates.add(Triple("$base/api/v1/devices/delete", "POST", "uuid"))
            }

            var lastResponseInfo: String? = null

            for ((url, method, bodyKey) in candidates) {
                try {
                    val requestBuilder = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiToken")
                        .addHeader("Accept", "application/json")

                    val request = when (method) {
                        "POST_USER_HWID" -> {
                            val payload = JSONObject().apply {
                                put("userUuid", userUuid)
                                put("hwid", hwid)
                            }.toString().toRequestBody(jsonMediaType)
                            requestBuilder.post(payload).build()
                        }
                        "DELETE" -> requestBuilder.delete().build()
                        "POST" -> {
                            val payload = JSONObject().apply { put(bodyKey!!, deviceId) }
                                .toString().toRequestBody(jsonMediaType)
                            requestBuilder.post(payload).build()
                        }
                        else -> requestBuilder.delete().build()
                    }

                    val response = client.newCall(request).execute()
                    val code = response.code
                    val body = response.body?.string().orEmpty()
                    lastResponseInfo = "Method=$method Code=$code Body=$body"

                    Log.d("RemnawaveApi", "deleteDevice candidate: method=$method, key=$bodyKey, code=$code")

                    if (response.isSuccessful) {
                        Log.i("RemnawaveApi", "deleteDevice succeeded with method=$method")
                        return@withContext true
                    }

                    if (code == 401 || code == 403) {
                        Log.e("RemnawaveApi", "deleteDevice AUTH ERROR: $code")
                        break
                    }

                } catch (e: Exception) {
                    Log.w("RemnawaveApi", "deleteDevice candidate failed: ${e::class.java.simpleName}")
                }
            }

            Log.e("RemnawaveApi", "All deleteDevice candidates failed. Info: $lastResponseInfo")
            false
        }
    }
}
