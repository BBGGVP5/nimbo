package com.danila.nimbo.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.utils.AppVersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class SubscriptionInfo(
    val servers: List<String> = emptyList(),
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
    val totalTraffic: Long = 0L,
    val expireTime: Long = 0L,
    val deviceCount: Int = 0,
    val deviceLimit: Int = 0,
    val onlineDevices: Int = 0,
    val announce: String? = null,
    val username: String? = null,
    val daysUntilExpiry: Long = -1L,
    val websiteUrl: String? = null,
    val supportUrl: String? = null,
    val autoUpdateInterval: Int? = null,
    val rawConfig: String? = null, // Полный конфиг (JSON) из API
    val configType: String? = null, // тип конфига: "sing-box" или "xray"
    val supportsJsonResponse: Boolean? = null, // поддерживает ли подписка JSON-ответ
    val accountId: String? = null, // уникальный ID пользователя (UUID)
    val shortUuid: String? = null, // короткий ID пользователя (Short UUID)
    val telegramId: String? = null, // ID Telegram
    val numericId: String? = null, // Цифровой ID
    val brandLogo: String? = null, // логотип бренда из заголовка nimbo-logo (URL или base64)
    val themeSpec: String? = null, // тема из заголовка nimbo-theme ("filter,accentHex,orb1,orb2,blur")
    val fallbackUrl: String? = null, // URL аварийного пула из заголовка nimbo-fallback
    val fallbackServers: List<String> = emptyList() // ссылки аварийного пула, подмешанные в servers
)

object SubscriptionManager {
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

    private data class CachedSubscription(
        val info: SubscriptionInfo,
        val fetchedAtMs: Long
    )

    private const val DEFAULT_CACHE_TTL_MS = 45_000L
    private const val PRIMARY_LOAD_BUDGET_MS = 30_000L
    private const val MIN_REMAINING_ATTEMPT_MS = 2_000L
    private const val ENRICHMENT_MAX_CANDIDATES = 3

    private var userAgent: String = "Nimbo/${BuildConfig.VERSION_NAME}/Android"
    private var deviceId: String = ""
    private var appContext: Context? = null
    private val responseCache = ConcurrentHashMap<String, CachedSubscription>()
    private val requestLocks = ConcurrentHashMap<String, Any>()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val osVersion = AppVersionManager.getOSVersion()
                val deviceModel = AppVersionManager.getDeviceModel()
                val appVersion = AppVersionManager.getVersionName(NebulaGuardApplication.instance)

                val prefs = appContext?.let { com.danila.nimbo.utils.PreferencesManager(it) }
                val cleanUA = original.header("User-Agent")
                    ?.takeIf { it.isNotBlank() }
                    ?: prefs?.subscriptionUserAgent?.takeIf { it.isNotBlank() }
                    ?: userAgent.takeIf { it.isNotBlank() }
                    ?: AppVersionManager.getUserAgent(NebulaGuardApplication.instance)

                val customName = prefs?.customDeviceName
                val finalDeviceModel = if (!customName.isNullOrBlank()) customName else deviceModel

                Log.d("SubscriptionManager", "Sending Headers: x-device-os=Android, x-ver-os=$osVersion, HWID=$deviceId, model=$finalDeviceModel")

                chain.proceed(
                    original.newBuilder()
                        .header("User-Agent", cleanUA)
                        .header("x-device-id", deviceId)
                        .header("x-hwid", deviceId)
                        .header("X-HWID", deviceId)
                        .header("HWID", deviceId)
                        .header("x-device-os", "Android")
                        .header("x-ver-os", osVersion)
                        .header("x-device-model", finalDeviceModel)
                        .header("x-app-version", appVersion)
                        .build()
                )
            }
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(14, TimeUnit.SECONDS)
            .build()
    }
    private val bestEffortHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Инициализация User-Agent и Device ID
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        userAgent = com.danila.nimbo.utils.PreferencesManager(context).subscriptionUserAgent
        deviceId = AppVersionManager.getHWID(context)
        Log.d("SubscriptionManager", "Initialized with deviceId: $deviceId")
    }

    /**
     * Получение уникального ID устройства (HWID)
     */
    private fun getDeviceId(context: Context): String {
        return AppVersionManager.getHWID(context)
    }

    fun load(url: String): SubscriptionInfo = load(url = url, forceRefresh = false)

    fun load(
        url: String,
        forceRefresh: Boolean = false,
        maxCacheAgeMs: Long = DEFAULT_CACHE_TTL_MS
    ): SubscriptionInfo {
        val normalizedUrl = url.trim()

        if (!forceRefresh) {
            getCachedIfFresh(normalizedUrl, maxCacheAgeMs)?.let {
                Log.d("SubscriptionManager", "Returning fresh cached subscription")
                return it
            }
        }

        val lock = requestLocks.computeIfAbsent(normalizedUrl) { Any() }
        synchronized(lock) {
            if (!forceRefresh) {
                getCachedIfFresh(normalizedUrl, maxCacheAgeMs)?.let {
                    Log.d("SubscriptionManager", "Returning cached subscription after lock")
                    return it
                }
            }

            return try {
                val loaded = loadInternal(normalizedUrl)
                responseCache[normalizedUrl] = CachedSubscription(
                    info = loaded,
                    fetchedAtMs = System.currentTimeMillis()
                )
                loaded
            } catch (e: Exception) {
                if (!forceRefresh) {
                    responseCache[normalizedUrl]?.let { stale ->
                        Log.w("SubscriptionManager", "Using stale cached subscription after load error: ${e.message}")
                        return stale.info
                    }
                }
                throw e
            }
        }
    }

    fun invalidateCache(url: String? = null) {
        if (url.isNullOrBlank()) {
            responseCache.clear()
            return
        }
        responseCache.remove(url.trim())
    }

    private fun getCachedIfFresh(url: String, maxAgeMs: Long): SubscriptionInfo? {
        val cached = responseCache[url] ?: return null
        val age = System.currentTimeMillis() - cached.fetchedAtMs
        if (age <= maxAgeMs) return cached.info
        return null
    }

    private fun loadInternal(url: String): SubscriptionInfo {
        val attempts = subscriptionUserAgentAttempts()
        val deadlineMs = System.currentTimeMillis() + PRIMARY_LOAD_BUDGET_MS
        var lastInfo: SubscriptionInfo? = null
        var lastError: Exception? = null

        for ((index, attemptUserAgent) in attempts.withIndex()) {
            if (System.currentTimeMillis() + MIN_REMAINING_ATTEMPT_MS >= deadlineMs) {
                break
            }

            try {
                val info = loadInternalOnce(url, attemptUserAgent)
                if (info.hasLoadableSubscriptionContent()) {
                    if (index > 0) {
                        Log.w(
                            "SubscriptionManager",
                            "Loaded subscription after User-Agent fallback: ${attemptUserAgent.substringBefore(' ')}"
                        )
                    }
                    val alternateResponses = loadAlternateClientResponses(
                        url = url,
                        baseInfo = info,
                        currentUserAgent = attemptUserAgent
                    )
                    val mergedHysteria = mergeMissingHysteriaFromAlternateClients(
                        baseInfo = info,
                        alternateResponses = alternateResponses
                    )
                    val enriched = enrichNamesAndDescriptionsFromAlternateClients(
                        baseInfo = mergedHysteria,
                        alternateResponses = alternateResponses
                    )
                    return mergeFallbackPool(
                        baseInfo = enriched,
                        mainUrl = url,
                        requestUserAgent = attemptUserAgent
                    )
                }

                lastInfo = info
                Log.w(
                    "SubscriptionManager",
                    "Subscription response had no server links/config with UA=${attemptUserAgent.substringBefore(' ')}, trying fallback"
                )
            } catch (e: Exception) {
                lastError = e
                Log.w(
                    "SubscriptionManager",
                    "Subscription load failed with UA=${attemptUserAgent.substringBefore(' ')}: ${e.message}"
                )
            }
        }

        if (lastInfo != null) {
            throw IllegalStateException("Ответ подписки не содержит серверов или Xray JSON-конфиг")
        }
        if (lastError != null && System.currentTimeMillis() >= deadlineMs) {
            throw java.net.SocketTimeoutException("Таймаут обновления подписки")
        }
        throw lastError ?: IllegalStateException("Ответ подписки не содержит серверов или Xray JSON-конфиг")
    }

    private fun subscriptionUserAgentAttempts(): List<String> {
        val context = appContext ?: NebulaGuardApplication.instance
        val prefs = runCatching { com.danila.nimbo.utils.PreferencesManager(context) }.getOrNull()
        val version = AppVersionManager.getVersionName(context)
        val nimboUserAgent = AppVersionManager.getUserAgent(context)
        return listOfNotNull(
            nimboUserAgent,
            prefs?.subscriptionUserAgent,
            userAgent.takeIf { it.isNotBlank() },
            "Happ/$version",
            "Happ/Nimbo/$version/Android",
            "Incy/$version",
            "SFA/1.8.0 (Sing-box for Android)",
            AppVersionManager.getUserAgent(context)
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private fun SubscriptionInfo.hasLoadableSubscriptionContent(): Boolean {
        return servers.any { isProtocolLink(it) } ||
            !rawConfig.isNullOrBlank() ||
            supportsJsonResponse == true
    }

    /**
     * Дополнительные форматы подписки независимы друг от друга, поэтому загружаем
     * короткий приоритетный набор параллельно. Один и тот же ответ затем используется
     * и для поиска Hysteria, и для обогащения имён/описаний.
     */
    private fun loadAlternateClientResponses(
        url: String,
        baseInfo: SubscriptionInfo,
        currentUserAgent: String
    ): List<Pair<String, SubscriptionInfo>> {
        val protocolLinks = baseInfo.servers.filter { isProtocolLink(it) }
        val needsHysteria = protocolLinks.none { isHysteriaProtocolLink(it) }
        val needsMetadata = protocolLinks.any { shareLinkNameNeedsImprovement(it) } ||
            (protocolLinks.isNotEmpty() && protocolLinks.none { !linkServerDescription(it).isNullOrBlank() })
        if (!needsHysteria && !needsMetadata) return emptyList()

        val context = appContext ?: NebulaGuardApplication.instance
        val version = AppVersionManager.getVersionName(context)
        val candidates = buildList {
            if (needsHysteria) {
                add("Incy/$version")
                add("Happ/$version")
                add("SFA/1.8.0 (Sing-box for Android)")
                add("NekoBoxForAndroid/1.3.0")
                add("sing-box/1.10.0")
            }
            if (needsMetadata) {
                add("Happ/$version")
                add("Happ")
                add("Happ/Nimbo/$version/Android")
                add("Incy/$version")
            }
        }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(currentUserAgent, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(ENRICHMENT_MAX_CANDIDATES)

        if (candidates.isEmpty()) return emptyList()

        return runBlocking {
            candidates.map { userAgent ->
                async(Dispatchers.IO) {
                    val result = runCatching { loadInternalOnce(url, userAgent, bestEffort = true) }
                        .onFailure {
                            Log.d(
                                "SubscriptionManager",
                                "No enrichment response from ${userAgent.substringBefore(' ')}: ${it.message}"
                            )
                        }
                        .getOrNull()
                    result?.let { userAgent to it }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun isSingBoxClientConfig(json: JSONObject): Boolean {
        val outbounds = json.optJSONArray("outbounds") ?: return false
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val type = outbound.cleanString("type") ?: continue
            if (type.isNotBlank()) return true
        }
        return false
    }

    private fun mergeMissingHysteriaFromAlternateClients(
        baseInfo: SubscriptionInfo,
        alternateResponses: List<Pair<String, SubscriptionInfo>>
    ): SubscriptionInfo {
        if (baseInfo.servers.any { isHysteriaProtocolLink(it) }) return baseInfo

        // Среди альтернативных клиентов предпочитаем тот, чьи hysteria-ссылки несут
        // нормальные имена. Иначе первым ответившим часто оказывается JSON-конфиг с
        // техническими тегами outbound'ов ("proxy-1", "proxy-2"), и реальные названия
        // стран (которые есть в прямых ссылках другого клиента) теряются.
        var genericFallback: Pair<List<String>, SubscriptionInfo>? = null

        for ((userAgent, extraInfo) in alternateResponses) {
            val hysteriaServers = extraInfo.servers.filter { isHysteriaProtocolLink(it) }
            if (hysteriaServers.isEmpty()) continue

            val hasRealNames = hysteriaServers.any { !isGenericShareLinkName(it) }
            if (hasRealNames) {
                Log.i(
                    "SubscriptionManager",
                    "Merged ${hysteriaServers.size} named Hysteria servers from ${userAgent.substringBefore(' ')} response"
                )
                return baseInfo.copy(
                    servers = dedupeServerLinks(baseInfo.servers + hysteriaServers),
                    rawConfig = baseInfo.rawConfig ?: extraInfo.rawConfig,
                    configType = baseInfo.configType ?: extraInfo.configType,
                    supportsJsonResponse = baseInfo.supportsJsonResponse ?: extraInfo.supportsJsonResponse
                )
            }

            // Только технические имена (proxy-1 и т.п.) — запоминаем как запасной
            // вариант, но продолжаем искать клиента с настоящими названиями.
            if (genericFallback == null) {
                genericFallback = hysteriaServers to extraInfo
                Log.d(
                    "SubscriptionManager",
                    "Hysteria from ${userAgent.substringBefore(' ')} has only generic names; keep searching for a named source"
                )
            }
        }

        // Клиента с нормальными именами не нашли — берём то, что есть (как раньше).
        genericFallback?.let { (hysteriaServers, extraInfo) ->
            Log.i(
                "SubscriptionManager",
                "No named Hysteria source found; merging ${hysteriaServers.size} generic-named Hysteria servers"
            )
            return baseInfo.copy(
                servers = dedupeServerLinks(baseInfo.servers + hysteriaServers),
                rawConfig = baseInfo.rawConfig ?: extraInfo.rawConfig,
                configType = baseInfo.configType ?: extraInfo.configType,
                supportsJsonResponse = baseInfo.supportsJsonResponse ?: extraInfo.supportsJsonResponse
            )
        }

        return baseInfo
    }

    /**
     * Remnawave отдаёт человекочитаемые имена и serverDescription только в JSON-формате
     * подписки (его получают «богатые» клиенты вроде Happ). При обычном UA панель возвращает
     * base64-список ссылок: у VLESS там есть только #fragment, а serverDescription отсутствует
     * вовсе. Если в основном ответе у серверов имена-заглушки ("Сервер", proxy-1, голый домен,
     * имя == host) или нет описаний — дотягиваем JSON-вариант альтернативным клиентом и аккуратно
     * переносим оттуда имя и serverDescription по ключу scheme://host:port/type. Только дополняем:
     * настоящие имена и описания из основного ответа никогда не перетираем.
     */
    private fun enrichNamesAndDescriptionsFromAlternateClients(
        baseInfo: SubscriptionInfo,
        alternateResponses: List<Pair<String, SubscriptionInfo>>
    ): SubscriptionInfo {
        val baseLinks = baseInfo.servers
        val anyNameNeedsImprovement = baseLinks.any {
            isProtocolLink(it) && shareLinkNameNeedsImprovement(it)
        }
        val noDescriptionsAtAll = baseLinks
            .filter { isProtocolLink(it) }
            .let { proto -> proto.isNotEmpty() && proto.none { !linkServerDescription(it).isNullOrBlank() } }
        if (!anyNameNeedsImprovement && !noDescriptionsAtAll) return baseInfo

        for ((userAgent, richInfo) in alternateResponses) {
            val richLinks = richInfo.servers.filter { isProtocolLink(it) }
            // Полезен только ответ, где есть хотя бы одно настоящее имя или описание.
            val hasRicherData = richLinks.any { !shareLinkNameNeedsImprovement(it) } ||
                richLinks.any { !linkServerDescription(it).isNullOrBlank() }
            if (!hasRicherData) continue

            // Первый встреченный сервер на scheme://host:port/type — источник истины.
            val richByKey = HashMap<String, String>()
            richLinks.forEach { rich ->
                val key = linkHostPortKey(rich) ?: return@forEach
                if (!richByKey.containsKey(key)) richByKey[key] = rich
            }

            var enrichedCount = 0
            val enrichedLinks = baseLinks.map { base ->
                if (!isProtocolLink(base)) return@map base
                val needsName = shareLinkNameNeedsImprovement(base)
                val needsDesc = linkServerDescription(base).isNullOrBlank()
                if (!needsName && !needsDesc) return@map base
                val key = linkHostPortKey(base) ?: return@map base
                val rich = richByKey[key] ?: return@map base

                var out = base
                if (needsName) {
                    val richName = shareLinkFragmentName(rich).trim()
                    if (richName.isNotBlank() &&
                        !isGenericServerName(richName) &&
                        !looksLikeBareDomain(richName)
                    ) {
                        out = setShareLinkFragmentName(out, richName)
                    }
                }
                if (needsDesc) {
                    val richDesc = linkServerDescription(rich)
                    if (!richDesc.isNullOrBlank()) {
                        out = appendServerDescriptionToLink(out, richDesc)
                    }
                }
                if (out != base) enrichedCount++
                out
            }

            if (enrichedCount > 0) {
                Log.i(
                    "SubscriptionManager",
                    "Enriched $enrichedCount server(s) with names/descriptions from " +
                        "${userAgent.substringBefore(' ')} JSON response"
                )
                return baseInfo.copy(servers = enrichedLinks)
            }
        }

        return baseInfo
    }

    /**
     * Аварийный пул серверов из заголовка подписки nimbo-fallback.
     *
     * Если основной ответ принёс заголовок nimbo-fallback с URL, best-effort скачиваем
     * этот отдельный пул и подмешиваем его узлы в конец списка servers как fallback-группу
     * (самый низкий приоритет — основные серверы идут первыми). Дубликаты основных серверов
     * отбрасываем по тому же ключу, что и dedupeServerLinks. Строго best-effort: любая ошибка
     * сети/парсинга тихо проглатывается и НЕ ломает обновление основной подписки.
     */
    private fun mergeFallbackPool(
        baseInfo: SubscriptionInfo,
        mainUrl: String,
        requestUserAgent: String
    ): SubscriptionInfo {
        val fallbackUrl = baseInfo.fallbackUrl?.trim()?.takeIf { it.isNotBlank() } ?: return baseInfo
        if (fallbackUrl.equals(mainUrl.trim(), ignoreCase = true)) return baseInfo

        return runCatching {
            // Одиночный запрос без записи last_subscription_url и без рекурсии в собственный
            // nimbo-fallback пула — берём только готовые ссылки.
            val poolInfo = loadInternalOnce(
                fallbackUrl,
                requestUserAgent,
                persistAsLastUrl = false,
                bestEffort = true
            )
            val fallbackLinks = dedupeServerLinks(
                poolInfo.servers.map { it.trim() }.filter { it.isNotBlank() && isProtocolLink(it) }
            )
            if (fallbackLinks.isEmpty()) return@runCatching baseInfo

            val mainKeys = baseInfo.servers.map { dedupeKeyForLink(it) }.toHashSet()
            val newFallback = fallbackLinks.filter { dedupeKeyForLink(it) !in mainKeys }
            if (newFallback.isEmpty()) return@runCatching baseInfo

            Log.i(
                "SubscriptionManager",
                "Merged ${newFallback.size} fallback server(s) from emergency pool ${fallbackUrl.take(80)}"
            )
            baseInfo.copy(
                servers = baseInfo.servers + newFallback,
                fallbackServers = newFallback
            )
        }.getOrElse {
            Log.d("SubscriptionManager", "Fallback pool merge skipped: ${it.message}")
            baseInfo
        }
    }

    /** Ключ дедупликации ссылки — тот же, что использует dedupeServerLinks. */
    private fun dedupeKeyForLink(link: String): String {
        val trimmed = link.trim()
        return if (isHysteriaProtocolLink(trimmed)) {
            "hysteria|${canonicalHysteriaLinkKey(trimmed)}"
        } else {
            "link|$trimmed"
        }
    }

    /**
     * Имя из #fragment ссылки бесполезно для показа, если оно пустое, техническое
     * (proxy-1, node3, "Сервер"…), выглядит как голый домен или совпадает с host.
     * Это зеркалит логику serverTitle() в UI, которая такие имена тоже отбрасывает.
     */
    private fun shareLinkNameNeedsImprovement(link: String): Boolean {
        val name = shareLinkFragmentName(link).trim()
        if (name.isBlank()) return true
        if (isGenericServerName(name)) return true
        if (looksLikeBareDomain(name)) return true
        val host = runCatching { Uri.parse(link).host }.getOrNull()?.trim()
        return !host.isNullOrBlank() && name.equals(host, ignoreCase = true)
    }

    private fun looksLikeBareDomain(value: String): Boolean {
        val compact = value.trim()
        if (compact.isEmpty() || compact.contains(" ")) return false
        return Regex("""^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(:\d{1,5})?$""").matches(compact)
    }

    private fun linkHostPortKey(link: String): String? {
        val uri = runCatching { Uri.parse(link.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val type = (uri.getQueryParameter("type") ?: uri.getQueryParameter("network"))
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: "tcp"
        return "$scheme://$host:$port/$type"
    }

    private fun linkServerDescription(link: String): String? {
        val uri = runCatching { Uri.parse(link.trim()) }.getOrNull() ?: return null
        return (uri.getQueryParameter("serverDescription")
            ?: uri.getQueryParameter("server_description")
            ?: uri.getQueryParameter("server-description")
            ?: uri.getQueryParameter("description"))
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun setShareLinkFragmentName(link: String, name: String): String {
        if (name.isBlank() || !link.contains("://")) return link
        val hashIndex = link.indexOf('#')
        val base = if (hashIndex >= 0) link.substring(0, hashIndex) else link
        return "$base#${Uri.encode(name)}"
    }

    /**
     * Технические/заглушечные имена, которые xray/sing-box ставят outbound'ам, когда в
     * конфиге нет человекочитаемого remark: "proxy", "proxy-1", "outbound-2", "node3",
     * "Hysteria2", "Hysteria2 <host>" и т.п. Это не настоящее название сервера.
     */
    internal fun isGenericServerName(name: String?): Boolean {
        val n = name?.trim()?.lowercase().orEmpty()
        if (n.isBlank()) return true
        if (n == "hysteria" || n == "hysteria2") return true
        if (n.startsWith("hysteria2 ") || n.startsWith("hysteria ")) return true
        return Regex("^(proxy|out|outbound|node|server|tunnel|vpn)[\\s_-]?\\d*$").matches(n)
    }

    private fun shareLinkFragmentName(link: String): String {
        val raw = link.substringAfterLast('#', "")
        if (raw.isBlank()) return ""
        return runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
    }

    private fun isGenericShareLinkName(link: String): Boolean =
        isGenericServerName(shareLinkFragmentName(link))

    private fun loadInternalOnce(
        url: String,
        requestUserAgent: String,
        persistAsLastUrl: Boolean = true,
        bestEffort: Boolean = false
    ): SubscriptionInfo {
        // Сохраняем URL подписки в SharedPreferences для использования при подключении VPN.
        // Для аварийного пула (nimbo-fallback) это пропускаем — иначе перетрём основной URL.
        if (persistAsLastUrl) appContext?.let { ctx ->
            try {
                ctx.getSharedPreferences("nebulaguard_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_subscription_url", url)
                    .apply()
                Log.d("SubscriptionManager", "Saved last subscription reference")
            } catch (e: Exception) {
                Log.w("SubscriptionManager", "Failed to save subscription URL", e)
            }
        }

        // Добавляем timestamp для обхода кэша (cache busting)
        val cacheBusterUrl = if (url.contains("?")) {
            "$url&_t=${System.currentTimeMillis()}"
        } else {
            "$url?_t=${System.currentTimeMillis()}"
        }

        val requestBuilder = Request.Builder()
            .url(cacheBusterUrl)
            .header("User-Agent", requestUserAgent)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")

        val request = requestBuilder.build()

        val response = (if (bestEffort) bestEffortHttpClient else httpClient)
            .newCall(request)
            .execute()

        // Логируем все заголовки для отладки
        Log.d("SubscriptionManager", "Response headers received")
        for (i in 0 until response.headers.size) {
            Log.d("SubscriptionManager", "  ${response.headers.name(i)}: ${response.headers.value(i)}")
        }

        // Получаем заголовки с информацией о подписке (Remnawave и другие)
        var uploadTotal = response.header("Subscription-Upload")?.toLongOrNull()
            ?: response.header("X-Subscription-Upload")?.toLongOrNull() ?: 0L
        var downloadTotal = response.header("Subscription-Download")?.toLongOrNull()
            ?: response.header("X-Subscription-Download")?.toLongOrNull() ?: 0L
        var totalTraffic = response.header("Subscription-Total")?.toLongOrNull()
            ?: response.header("X-Subscription-Total")?.toLongOrNull() ?: 0L
        var expireTime = response.header("Subscription-Expire")?.toLongOrNull()
            ?: response.header("X-Subscription-Expire")?.toLongOrNull() ?: 0L
        var deviceCount = response.header("Subscription-Device")?.toIntOrNull()
            ?: response.header("X-Subscription-Device")?.toIntOrNull() ?: 0

        // Remnawave HWID device limit headers (docs.rw/docs/features/hwid-device-limit)
        // x-hwid-limit — лимит устройств для подписки
        var deviceLimit = response.header("x-hwid-limit")?.toIntOrNull()
            ?: response.header("X-Hwid-Limit")?.toIntOrNull()
            ?: response.header("Subscription-Device-Limit")?.toIntOrNull()
            ?: response.header("X-Subscription-Device-Limit")?.toIntOrNull() ?: 0
        // onlineDevices пока не передаётся отдельным заголовком — будет получен через API
        var onlineDevices = response.header("x-hwid-devices")?.toIntOrNull()
            ?: response.header("Subscription-Online-Devices")?.toIntOrNull()
            ?: response.header("X-Subscription-Online-Devices")?.toIntOrNull() ?: 0

        // Логируем HWID заголовки для отладки
        val hwidActive = response.header("x-hwid-active")
        val hwidMaxReached = response.header("x-hwid-max-devices-reached")
        Log.d("SubscriptionManager", "HWID headers: x-hwid-limit=$deviceLimit, x-hwid-active=$hwidActive, x-hwid-max-reached=$hwidMaxReached")

        // Если deviceCount из старого заголовка был лимитом, используем его как fallback

        // Announce - описание подписки (может быть base64)
        val announceRaw = response.headers["announce"]
            ?: response.headers["subscription_description"]
            ?: response.headers["Subscription-Announce"]
            ?: response.headers["Subscription-Description"]
        var announce = decodeBase64Header(announceRaw)

        // profile_title - название профиля (Remnawave, может быть base64)
        val profileTitleRaw = response.headers["profile-title"]
            ?: response.headers["profile_title"]
        val profileTitle = decodeBase64Header(profileTitleRaw)

        val userInfo = response.header("subscription-userinfo")
            ?: response.header("Subscription-Userinfo")
            ?: response.header("profile_userinfo")
            ?: response.header("X-Subscription-Userinfo")

        if (userInfo != null) {
            val parsedInfo = parseUserInfoHeader(userInfo)
            if (uploadTotal == 0L) uploadTotal = parsedInfo.upload
            if (downloadTotal == 0L) downloadTotal = parsedInfo.download
            if (totalTraffic == 0L) totalTraffic = parsedInfo.total
            if (expireTime == 0L) expireTime = parsedInfo.expire
        }

        val daysUntilExpiry = response.header("Subscription-Days")?.toLongOrNull()
            ?: response.header("Subscription-Expire-Days")?.toLongOrNull()
            ?: response.header("X-Subscription-Days")?.toLongOrNull()
            ?: -1L

        val autoUpdateInterval = response.header("Subscription-Update-Interval")?.toIntOrNull()
            ?: response.header("X-Subscription-Update-Interval")?.toIntOrNull()
            ?: response.header("subscription_update_interval")?.toIntOrNull()

        val websiteUrl = response.headers["website-url"]
            ?: response.headers["Website-Url"]
            ?: response.headers["X-Website-Url"]
        val supportUrl = response.headers["support-url"]
            ?: response.headers["Support-Url"]
            ?: response.headers["X-Support-Url"]

        // Brand logo + theme delivered by the panel via custom headers.
        // Supports the nimbo-* names and the upstream dropweb-* names as a fallback.
        val brandLogo = (response.headers["nimbo-logo"]
            ?: response.headers["Nimbo-Logo"]
            ?: response.headers["x-nimbo-logo"]
            ?: response.headers["dropweb-logo"])
            ?.trim()?.takeIf { it.isNotBlank() }
        val themeSpec = (response.headers["nimbo-theme"]
            ?: response.headers["Nimbo-Theme"]
            ?: response.headers["x-nimbo-theme"]
            ?: response.headers["dropweb-theme"])
            ?.trim()?.takeIf { it.isNotBlank() }

        // Аварийный пул серверов (nimbo-fallback): URL отдельной подписки/пула, который
        // клиент best-effort подмешивает как fallback-группу. decodeBase64Header не тронет
        // обычный https-URL (двоеточие/слэш не входят в base64-алфавит), но поддержит
        // явный "base64:"-префикс.
        val fallbackUrl = (response.headers["nimbo-fallback"]
            ?: response.headers["Nimbo-Fallback"]
            ?: response.headers["x-nimbo-fallback"]
            ?: response.headers["dropweb-fallback"])
            ?.let { decodeBase64Header(it) ?: it }
            ?.trim()?.takeIf { it.isNotBlank() }

        // App-routing rules optionally provided by the subscription via HTTP headers
        // (process-direct / app-direct → bypass, process-proxy / app-proxy → through VPN).
        runCatching { extractAndStoreSubscriptionAppRules(response) }

        var username = extractUsernameFromAnnounce(announce)
            ?: extractUsernameFromProfileTitle(profileTitle)

        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw Exception(buildSubscriptionHttpError(response.code, body, hwidActive, hwidMaxReached))
        }
        if (body.isBlank()) return SubscriptionInfo()
        val trimmedBody = body.trim()
        var supportsJsonResponse: Boolean? = null

        if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
            try {
                val json = JSONObject(trimmedBody)
                val data = json.optJSONObject("response")
                    ?: json.optJSONObject("data")
                    ?: json.optJSONObject("result")
                    ?: json
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
            } catch (_: Exception) { }
        }

        // 1. Проверяем, не является ли это сразу JSON-конфигом
        if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
            try {
                val json = JSONObject(trimmedBody)
                val isXrayClientConfig = RemnawaveApiClient.isUsableXrayClientConfig(json)
                val isSingBoxClientConfig = isSingBoxClientConfig(json)
                val detectedProxyProtocol = detectPrimaryProxyProtocolFromJsonConfig(trimmedBody)
                if (isXrayClientConfig || isSingBoxClientConfig || !detectedProxyProtocol.isNullOrBlank()) {
                    val hasXrayProtocolKeys = listOfNotNull(json.optJSONArray("outbounds"), json.optJSONArray("inbounds"))
                        .any { array ->
                            (0 until array.length()).any { index ->
                                array.optJSONObject(index)?.has("protocol") == true
                            }
                        }
                    val type = if (
                        isSingBoxClientConfig &&
                        !isXrayClientConfig &&
                        !hasXrayProtocolKeys &&
                        !json.has("routing") &&
                        !json.has("policy")
                    ) {
                        "sing-box"
                    } else {
                        "xray"
                    }
                    val clientConfigServers = parseServerLinksFromClientJsonConfig(trimmedBody)
                    if (clientConfigServers.isNotEmpty()) {
                        Log.d(
                            "SubscriptionManager",
                            "Extracted ${clientConfigServers.size} server links from $type JSON config"
                        )
                    }
                    val resolvedInfo = SubscriptionInfo(
                        servers = clientConfigServers,
                        rawConfig = trimmedBody,
                        configType = type,
                        supportsJsonResponse = true,
                        uploadTotal = uploadTotal,
                        downloadTotal = downloadTotal,
                        totalTraffic = totalTraffic,
                        expireTime = expireTime,
                        deviceCount = deviceLimit,
                        deviceLimit = deviceLimit,
                        onlineDevices = onlineDevices,
                        announce = announce,
                        username = username,
                        daysUntilExpiry = daysUntilExpiry,
                        websiteUrl = websiteUrl,
                        supportUrl = supportUrl,
                        brandLogo = brandLogo,
                        themeSpec = themeSpec,
                        fallbackUrl = fallbackUrl,
                        autoUpdateInterval = autoUpdateInterval,
                        shortUuid = url.substringAfterLast("/").substringBefore("?"),
                        numericId = null,
                        telegramId = null
                    )
                    return resolvedInfo.copy(
                        announce = resolveAnnounceTemplate(resolvedInfo, url)
                    )
                }
            } catch (e: Exception) { }
        }

        val serversFromApiJson = parseServerLinksFromApiJson(trimmedBody)
        if (serversFromApiJson != null) {
            Log.d("SubscriptionManager", "Parsed ${serversFromApiJson.size} server links from JSON API payload")
        }

        // 2. Декодируем как Base64 (если это не JSON-массив серверов)
        val decoded = if (serversFromApiJson == null) {
            try {
                val decodedBytes = Base64.getDecoder().decode(trimmedBody)
                String(decodedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                body
            }
        } else {
            ""
        }

        val structuredTextServers = if (serversFromApiJson == null) {
            parseServerLinksFromSubscriptionText(decoded)
        } else {
            emptyList()
        }

        val servers = dedupeServerLinks(
            serversFromApiJson
                ?: structuredTextServers.ifEmpty {
                    decoded.split("\n", "\r").filter { it.isNotBlank() }.map { it.trim() }
                }
        )
        if (servers.isNotEmpty()) {
            Log.d("SubscriptionManager", "Parsed server protocols: ${serverProtocolSummary(servers)}")
        }
        val baselineInfo = SubscriptionInfo(
            servers = servers,
            uploadTotal = uploadTotal,
            downloadTotal = downloadTotal,
            totalTraffic = totalTraffic,
            expireTime = expireTime,
            deviceCount = deviceLimit,
            deviceLimit = deviceLimit,
            onlineDevices = onlineDevices,
            announce = announce,
            username = username,
            daysUntilExpiry = daysUntilExpiry,
            websiteUrl = websiteUrl,
            supportUrl = supportUrl,
            brandLogo = brandLogo,
            themeSpec = themeSpec,
            fallbackUrl = fallbackUrl,
            autoUpdateInterval = autoUpdateInterval,
            supportsJsonResponse = supportsJsonResponse,
            shortUuid = url.substringAfterLast("/").substringBefore("?")
        )

        return baselineInfo.copy(
            announce = resolveAnnounceTemplate(baselineInfo, url)
        )
    }

    private fun extractUsernameFromUrl(url: String): String? {
        return try {
            val path = url.substringAfterLast("/", "")
            if (path.isNotEmpty() && path != url) URLDecoder.decode(path, "UTF-8") else null
        } catch (e: Exception) { null }
    }

    private fun extractUsernameFromHeader(userInfo: String?): String? {
        if (userInfo.isNullOrBlank()) return null
        val usernameMatch = Regex("username=([^;]+)").find(userInfo)
        return usernameMatch?.groupValues?.get(1)?.trim()
    }

    private fun buildSubscriptionHttpError(
        code: Int,
        body: String,
        hwidActive: String?,
        hwidMaxReached: String?
    ): String {
        val lowerBody = body.lowercase()
        val hwidDenied = hwidActive.equals("false", ignoreCase = true) ||
            hwidMaxReached.equals("true", ignoreCase = true) ||
            lowerBody.contains("hwid") ||
            lowerBody.contains("device limit") ||
            lowerBody.contains("max devices")

        if (hwidDenied) {
            return "Подписка отклонила это устройство (HWID/лимит устройств). Откройте управление устройствами в панели или удалите старое устройство и обновите подписку."
        }

        return "Не удалось загрузить подписку: HTTP $code"
    }

    private data class ParsedUserInfo(val upload: Long = 0L, val download: Long = 0L, val total: Long = 0L, val expire: Long = 0L)

    private fun parseUserInfoHeader(userInfo: String): ParsedUserInfo {
        val parts = userInfo.split(";").associate {
            val pair = it.trim().split("=")
            if (pair.size == 2) pair[0].trim().lowercase() to pair[1].trim() else "" to ""
        }
        return ParsedUserInfo(
            upload = parts["upload"]?.toLongOrNull() ?: 0L,
            download = parts["download"]?.toLongOrNull() ?: 0L,
            total = parts["total"]?.toLongOrNull() ?: 0L,
            expire = parts["expire"]?.toLongOrNull() ?: 0L
        )
    }

    private fun resolveAnnounceTemplate(info: SubscriptionInfo, url: String): String? {
        val template = info.announce ?: return null
        if (!template.contains("{{")) return template

        val used = info.uploadTotal + info.downloadTotal
        val left = if (info.totalTraffic > 0) maxOf(0L, info.totalTraffic - used) else 0L

        // Пересчитываем дни, если у нас есть актуальный expireTime
        val currentDays = if (info.expireTime > 0) {
            val diff = info.expireTime - System.currentTimeMillis() / 1000
            if (diff > 0) diff / (24 * 3600) else 0L
        } else {
            info.daysUntilExpiry
        }

        val replacements = mapOf(
            "{{DAYS_LEFT}}" to when {
                currentDays < 0 -> "Бессрочно"
                currentDays == 0L -> "Истекает сегодня"
                else -> "$currentDays"
            },
            "{{TRAFFIC_USED}}" to formatTraffic(used),
            "{{TRAFFIC_LEFT}}" to (if (info.totalTraffic > 0) formatTraffic(left) else "∞"),
            "{{TOTAL_TRAFFIC}}" to (if (info.totalTraffic > 0) formatTraffic(info.totalTraffic) else "∞"),
            "{{STATUS}}" to if (currentDays == 0L && info.expireTime > 0 && info.expireTime < System.currentTimeMillis()/1000 || (info.totalTraffic > 0 && used >= info.totalTraffic)) "Истекла" else "Активна",
            "{{USERNAME}}" to (info.username ?: "Пользователь"),
            "{{ID}}" to (info.numericId ?: info.shortUuid ?: info.accountId ?: "N/A"),
            "{{SHORT_UUID}}" to (info.shortUuid ?: url.substringAfterLast("/").substringBefore("?")),
            "{{SUBSCRIPTION_URL}}" to url,
            "{{SS_SUPPORT_LINK}}" to (info.supportUrl ?: "N/A"),
            "{{SS_HWID_LIMIT}}" to "${info.deviceLimit}",
            "{{SS_PROFILE_UPDATE_INTERVAL}}" to "${info.autoUpdateInterval ?: 30}",
            "{{TELEGRAM_ID}}" to (info.telegramId ?: "N/A"),
            "{{EXPIRE_UNIX}}" to "${info.expireTime}",
            "{{TRAFFIC_USED_BYTES}}" to "$used",
            "{{TRAFFIC_LEFT_BYTES}}" to "$left",
            "{{TOTAL_TRAFFIC_BYTES}}" to "${info.totalTraffic}",
            "{{EMAIL}}" to "",
            "{{TAG}}" to ""
        )

        var result = template
        replacements.forEach { (key, value) ->
            result = result.replace(key, value, ignoreCase = true)
        }
        return result
    }

    private fun formatTraffic(bytes: Long): String {
        if (bytes <= 0) return "0 Б"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        val digitGroup = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return try {
            "%.1f %s".format(java.util.Locale.US, bytes / Math.pow(1024.0, digitGroup.toDouble()), units[digitGroup])
        } catch (e: Exception) {
            "${bytes / (1024 * 1024)} МБ"
        }
    }

    private fun extractUsernameFromAnnounce(announce: String?): String? {
        if (announce.isNullOrBlank()) return null
        val usernameMatch = Regex("👤\\s*([A-Za-z0-9_]+)\\s*·").find(announce)
        return usernameMatch?.groupValues?.get(1)?.trim()
    }

    private fun extractUsernameFromProfileTitle(profileTitle: String?): String? {
        if (profileTitle.isNullOrBlank()) return null
        val parts = profileTitle.split(" · ")
        return parts.lastOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private val DIRECT_RULE_HEADERS = listOf(
        "process-direct", "process-direct-list", "process-bypass", "app-direct", "apps-direct",
        "nimbo-process-direct", "x-nimbo-process-direct", "x-process-direct", "x-app-direct"
    )
    private val PROXY_RULE_HEADERS = listOf(
        "process-proxy", "process-proxy-list", "app-proxy", "apps-proxy",
        "nimbo-process-proxy", "x-nimbo-process-proxy", "x-process-proxy", "x-app-proxy"
    )

    private fun extractAndStoreSubscriptionAppRules(response: okhttp3.Response) {
        val ctx = appContext ?: return
        val direct = collectAppRuleEntries(response, DIRECT_RULE_HEADERS)
        val proxy = collectAppRuleEntries(response, PROXY_RULE_HEADERS)
        if (direct.isEmpty() && proxy.isEmpty()) return
        val prefs = com.danila.nimbo.utils.PreferencesManager(ctx)
        if (direct.isNotEmpty()) prefs.setSubscriptionAppDirectList(direct)
        if (proxy.isNotEmpty()) prefs.setSubscriptionAppProxyList(proxy)
        Log.d("SubscriptionManager", "Subscription app rules: direct=${direct.size}, proxy=${proxy.size}")
    }

    private fun collectAppRuleEntries(response: okhttp3.Response, headerNames: List<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (name in headerNames) {
            for (raw in response.headers.values(name)) {
                val decoded = decodeBase64Header(raw) ?: raw
                out.addAll(parseAppRuleTokens(decoded))
            }
        }
        return out
    }

    private fun parseAppRuleTokens(value: String): List<String> {
        return value.split(',', ';', '\n')
            .map { it.trim().trim('"', '\'', '[', ']', ' ').trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .map { token ->
                val m = Regex("^process-name\\s+(.+)$", RegexOption.IGNORE_CASE).find(token)
                (m?.groupValues?.get(1) ?: token).trim()
            }
            .filter {
                it.isNotBlank() &&
                    !it.startsWith("http://", ignoreCase = true) &&
                    !it.startsWith("https://", ignoreCase = true)
            }
    }

    private fun decodeBase64Header(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (value.startsWith("base64:", ignoreCase = true)) {
            try {
                val base64Part = value.substringAfter("base64:", "")
                return String(Base64.getDecoder().decode(base64Part.trim()), Charsets.UTF_8)
            } catch (e: Exception) { return value }
        }
        if (value.matches(Regex("^[A-Za-z0-9+/=]+$")) && value.length > 10) {
            try {
                return String(Base64.getDecoder().decode(value.trim()), Charsets.UTF_8)
            } catch (e: Exception) { }
        }
        return value
    }

    private fun dedupeServerLinks(links: List<String>): List<String> {
        val byKey = linkedMapOf<String, String>()
        links
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { link ->
                val key = dedupeKeyForLink(link)
                val existing = byKey[key]
                if (existing == null || shouldReplaceDuplicateServerLink(existing, link)) {
                    byKey[key] = link
                }
            }
        return byKey.values.toList()
    }

    private fun shouldReplaceDuplicateServerLink(existing: String, candidate: String): Boolean {
        if (!isHysteriaProtocolLink(existing) || !isHysteriaProtocolLink(candidate)) return false
        val existingGeneric = isGenericShareLinkName(existing)
        val candidateGeneric = isGenericShareLinkName(candidate)
        return existingGeneric && !candidateGeneric
    }

    private fun canonicalHysteriaLinkKey(link: String): String {
        val withoutFragment = link.substringBefore('#')
        val scheme = withoutFragment.substringBefore("://", "").lowercase()
        val rest = withoutFragment.substringAfter("://", withoutFragment)
        val endpoint = rest.substringBefore('?')
        val stableQuery = rest
            .substringAfter('?', "")
            .split('&')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it.substringBefore('=').lowercase()
                key == "serverdescription" || key == "hostuuid"
            }
            .sorted()
            .joinToString("&")

        return if (stableQuery.isBlank()) {
            "$scheme://$endpoint"
        } else {
            "$scheme://$endpoint?$stableQuery"
        }
    }

    internal fun parseServerLinksFromClientJsonConfig(jsonText: String): List<String> {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) return emptyList()

        return runCatching {
            val root = parseJsonRoot(trimmed) ?: return@runCatching emptyList()
            val outbounds = collectOutboundsArrays(root)
            val inbounds = collectInboundsArrays(root)

            dedupeServerLinks(
                outbounds.flatMap { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            val outbound = array.optJSONObject(i) ?: continue
                            val link = xrayHysteriaLinkFromOutbound(outbound)
                                ?: singBoxHysteriaLinkFromOutbound(outbound)
                                ?: continue
                            add(link)
                        }
                    }
                } +
                    inbounds.flatMap { array ->
                        buildList {
                            for (i in 0 until array.length()) {
                                val inbound = array.optJSONObject(i) ?: continue
                                xrayHysteriaLinkFromInbound(inbound)?.let { add(it) }
                            }
                        }
                    }
            )
        }.getOrElse {
            emptyList()
        }
    }

    internal fun detectPrimaryProxyProtocolFromJsonConfig(jsonText: String?): String? {
        val trimmed = jsonText?.trim()?.takeIf { it.isNotBlank() } ?: return null

        return runCatching {
            val root = parseJsonRoot(trimmed) ?: return@runCatching null
            collectOutboundsArrays(root).firstNotNullOfOrNull { array ->
                for (i in 0 until array.length()) {
                    val protocol = detectProxyProtocolFromOutbound(array.optJSONObject(i) ?: continue)
                    if (!protocol.isNullOrBlank()) return@firstNotNullOfOrNull protocol
                }
                null
            } ?: collectInboundsArrays(root).firstNotNullOfOrNull { array ->
                for (i in 0 until array.length()) {
                    val protocol = detectProxyProtocolFromInbound(array.optJSONObject(i) ?: continue)
                    if (!protocol.isNullOrBlank()) return@firstNotNullOfOrNull protocol
                }
                null
            }
        }.getOrNull()
    }

    private fun xrayHysteriaLinkFromOutbound(outbound: JSONObject): String? {
        val protocol = outbound.cleanString("protocol")?.lowercase().orEmpty()
        if (!isHysteriaProtocolName(protocol)) return null

        val settings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings")
        val hysteriaSettings = stream?.optJSONObject("hysteriaSettings")
        val finalMask = stream?.optJSONObject("finalmask")
        val quicParams = finalMask?.optJSONObject("quicParams")
        val udpHop = quicParams?.optJSONObject("udpHop")
        val tlsSettings = stream?.optJSONObject("tlsSettings")

        val host = settings.cleanString("address", "server", "host", "domain")
            ?: outbound.cleanString("address", "server", "host", "domain")
            ?: return null
        val port = settings.cleanInt("port", "serverPort", "server_port")
            ?: outbound.cleanInt("port", "serverPort", "server_port")
            ?: return null
        val password = hysteriaSettings?.cleanString("auth", "password")
            ?: settings.cleanString("auth", "password")

        val obfs = extractXrayHysteriaObfs(finalMask)
        val params = linkedMapOf<String, String?>(
            "sni" to tlsSettings?.cleanString("serverName", "server_name", "sni"),
            "alpn" to tlsSettings?.cleanStringArray("alpn"),
            "insecure" to tlsSettings?.cleanBoolean("allowInsecure", "insecure")?.let { if (it) "1" else "0" },
            "obfs" to obfs?.first,
            "obfs-password" to obfs?.second,
            "mport" to udpHop?.cleanString("ports"),
            "hopInterval" to udpHop?.cleanString("interval"),
            "upmbps" to quicParams?.cleanString("brutalUp", "up", "upMbps", "up_mbps"),
            "downmbps" to quicParams?.cleanString("brutalDown", "down", "downMbps", "down_mbps"),
            "congestion" to quicParams?.cleanString("congestion")
        )
        val name = outbound.cleanString("remarks", "remark", "name", "tag")
            ?: "Hysteria2 $host"

        return buildHysteriaShareLink(host, port, password, name, params)
    }

    private fun singBoxHysteriaLinkFromOutbound(outbound: JSONObject): String? {
        val type = outbound.cleanString("type")?.lowercase().orEmpty()
        if (!isHysteriaProtocolName(type)) return null

        val host = outbound.cleanString("server", "address", "host", "domain") ?: return null
        val serverPorts = outbound.cleanStringList("server_ports", "serverPorts", "mport", "ports")
        val port = outbound.cleanInt("server_port", "serverPort", "port")
            ?: serverPorts?.let { firstPortFromRange(it) }
            ?: return null
        val tls = outbound.optJSONObject("tls")
        val obfsObject = outbound.optJSONObject("obfs")

        val obfsType = obfsObject?.cleanString("type")
            ?: outbound.cleanString("obfs", "obfs_type", "obfsType", "obfs-type")
        val obfsPassword = obfsObject?.cleanString("password")
            ?: outbound.cleanString(
                "obfs_password",
                "obfsPassword",
                "obfs-password",
                "obfs_param",
                "obfsParam",
                "obfs-param"
            )

        val params = linkedMapOf<String, String?>(
            "sni" to (tls?.cleanString("server_name", "serverName", "sni", "peer")
                ?: outbound.cleanString("server_name", "serverName", "sni", "peer")),
            "alpn" to tls?.cleanStringArray("alpn"),
            "insecure" to (tls?.cleanBoolean("insecure", "allowInsecure")
                ?: outbound.cleanBoolean("tls_insecure", "allowInsecure"))?.let { if (it) "1" else "0" },
            "obfs" to obfsType,
            "obfs-password" to obfsPassword,
            "mport" to serverPorts,
            "hopInterval" to outbound.cleanString("hop_interval", "hopInterval", "hop-interval", "interval"),
            "upmbps" to outbound.cleanString("up_mbps", "upMbps", "up", "upload"),
            "downmbps" to outbound.cleanString("down_mbps", "downMbps", "down", "download"),
            "congestion" to outbound.cleanString("congestion", "cc")
        )
        val name = outbound.cleanString("remarks", "remark", "name", "tag")
            ?: "Hysteria2 $host"
        val password = outbound.cleanString("password", "auth", "auth_str", "authStr", "auth-str")

        return buildHysteriaShareLink(host, port, password, name, params)
    }

    private fun xrayHysteriaLinkFromInbound(inbound: JSONObject): String? {
        val protocol = inbound.cleanString("protocol", "type")?.lowercase().orEmpty()
        val stream = inbound.optJSONObject("streamSettings")
        val network = stream?.cleanString("network")?.lowercase().orEmpty()
        if (!isHysteriaProtocolName(protocol) && !isHysteriaProtocolName(network)) return null

        val settings = inbound.optJSONObject("settings") ?: JSONObject()
        val hysteriaSettings = stream?.optJSONObject("hysteriaSettings")
        val finalMask = stream?.optJSONObject("finalmask")
        val quicParams = finalMask?.optJSONObject("quicParams")
        val udpHop = quicParams?.optJSONObject("udpHop")
        val tlsSettings = stream?.optJSONObject("tlsSettings")

        val host = listOfNotNull(
            inbound.cleanString(
                "address",
                "server",
                "host",
                "domain",
                "externalAddress",
                "external_address",
                "publicAddress",
                "public_address"
            ),
            settings.cleanString(
                "address",
                "server",
                "host",
                "domain",
                "externalAddress",
                "external_address",
                "publicAddress",
                "public_address"
            ),
            inbound.cleanString("listen").takeIf { isUsableServerHost(it) }
        ).firstOrNull { isUsableServerHost(it) } ?: return null

        val port = inbound.cleanInt("port", "listenPort", "listen_port", "serverPort", "server_port")
            ?: settings.cleanInt("port", "serverPort", "server_port")
            ?: return null
        val password = hysteriaSettings?.cleanString("auth", "password", "auth_str", "authStr", "auth-str")
            ?: settings.cleanString("auth", "password", "auth_str", "authStr", "auth-str")
            ?: extractHysteriaPasswordFromClients(settings.optJSONArray("clients"))

        val obfs = extractXrayHysteriaObfs(finalMask)
        val params = linkedMapOf<String, String?>(
            "sni" to tlsSettings?.cleanString("serverName", "server_name", "sni"),
            "alpn" to tlsSettings?.cleanStringArray("alpn"),
            "insecure" to tlsSettings?.cleanBoolean("allowInsecure", "insecure")?.let { if (it) "1" else "0" },
            "obfs" to obfs?.first,
            "obfs-password" to obfs?.second,
            "mport" to udpHop?.cleanString("ports"),
            "hopInterval" to udpHop?.cleanString("interval"),
            "upmbps" to quicParams?.cleanString("brutalUp", "up", "upMbps", "up_mbps"),
            "downmbps" to quicParams?.cleanString("brutalDown", "down", "downMbps", "down_mbps"),
            "congestion" to quicParams?.cleanString("congestion")
        )
        val name = inbound.cleanString("remarks", "remark", "name", "tag")
            ?: "Hysteria2 $host"

        return buildHysteriaShareLink(host, port, password, name, params)
    }

    private fun extractHysteriaPasswordFromClients(clients: JSONArray?): String? {
        if (clients == null) return null
        for (i in 0 until clients.length()) {
            val client = clients.optJSONObject(i) ?: continue
            val password = client.cleanString(
                "auth",
                "password",
                "auth_str",
                "authStr",
                "auth-str"
            )
            if (!password.isNullOrBlank()) return password
        }
        return null
    }

    private fun extractXrayHysteriaObfs(finalMask: JSONObject?): Pair<String, String?>? {
        val udpLayers = finalMask?.optJSONArray("udp") ?: return null
        for (i in 0 until udpLayers.length()) {
            val layer = udpLayers.optJSONObject(i) ?: continue
            val type = layer.cleanString("type") ?: continue
            if (!type.equals("salamander", ignoreCase = true)) continue
            val password = layer.optJSONObject("settings")?.cleanString("password")
            return type to password
        }
        return null
    }

    private fun buildHysteriaShareLink(
        host: String,
        port: Int,
        password: String?,
        name: String,
        params: Map<String, String?>
    ): String {
        val hostPart = if (host.contains(":") && !host.startsWith("[") && !host.endsWith("]")) {
            "[$host]"
        } else {
            host
        }
        val userInfo = password
            ?.takeIf { it.isNotBlank() }
            ?.let { "${encodeUriComponent(it)}@" }
            .orEmpty()
        val query = params
            .mapNotNull { (key, value) ->
                val normalized = value?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?: return@mapNotNull null
                "$key=${encodeUriComponent(normalized)}"
            }
            .joinToString("&")
            .let { if (it.isBlank()) "" else "?$it" }
        val fragment = name
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { "#${encodeUriComponent(it)}" }
            .orEmpty()

        return "hy2://$userInfo$hostPart:$port$query$fragment"
    }

    private fun parseServerLinksFromSubscriptionText(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        return dedupeServerLinks(
            findProtocolLinksInText(trimmed) +
                parseServerLinksFromClientJsonConfig(trimmed) +
                parseHysteriaLinksFromClashYaml(trimmed)
        )
    }

    private fun parseJsonRoot(text: String): Any? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()
    }

    private fun collectOutboundsArrays(node: Any?, depth: Int = 0): List<JSONArray> {
        if (node == null || node == JSONObject.NULL || depth > 16) return emptyList()
        return when (node) {
            is JSONObject -> buildList {
                node.optJSONArray("outbounds")?.let { add(it) }
                node.optJSONArray("outbound")?.let { add(it) }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key) ?: continue
                    if (value is String) {
                        parseJsonRoot(value)?.let { addAll(collectOutboundsArrays(it, depth + 1)) }
                    } else if (value is JSONObject || value is JSONArray) {
                        addAll(collectOutboundsArrays(value, depth + 1))
                    }
                }
            }
            is JSONArray -> buildList {
                for (i in 0 until node.length()) {
                    addAll(collectOutboundsArrays(node.opt(i), depth + 1))
                }
            }
            else -> emptyList()
        }
    }

    private fun collectInboundsArrays(node: Any?, depth: Int = 0): List<JSONArray> {
        if (node == null || node == JSONObject.NULL || depth > 16) return emptyList()
        return when (node) {
            is JSONObject -> buildList {
                node.optJSONArray("inbounds")?.let { add(it) }
                node.optJSONArray("inbound")?.let { add(it) }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key) ?: continue
                    if (value is String) {
                        parseJsonRoot(value)?.let { addAll(collectInboundsArrays(it, depth + 1)) }
                    } else if (value is JSONObject || value is JSONArray) {
                        addAll(collectInboundsArrays(value, depth + 1))
                    }
                }
            }
            is JSONArray -> buildList {
                for (i in 0 until node.length()) {
                    addAll(collectInboundsArrays(node.opt(i), depth + 1))
                }
            }
            else -> emptyList()
        }
    }

    private fun detectProxyProtocolFromOutbound(outbound: JSONObject): String? {
        return normalizeDetectedProxyProtocol(
            outbound.cleanString("type", "protocol") ?: return null,
            outbound
        )
    }

    private fun detectProxyProtocolFromInbound(inbound: JSONObject): String? {
        val stream = inbound.optJSONObject("streamSettings")
        return normalizeDetectedProxyProtocol(
            inbound.cleanString("type", "protocol")
                ?: stream?.cleanString("network")
                ?: return null,
            inbound
        )
    }

    private fun normalizeDetectedProxyProtocol(rawProtocol: String, node: JSONObject): String? {
        val protocol = rawProtocol.trim().lowercase()
        return when {
            protocol.isBlank() || isNonProxyProtocolName(protocol) -> null
            protocol.contains("vless") -> "vless"
            protocol.contains("vmess") -> "vmess"
            protocol.contains("trojan") -> "trojan"
            protocol == "ss" || protocol.contains("shadowsocks") || protocol.contains("shadow") -> "shadowsocks"
            isHysteriaProtocolName(protocol) -> if (isHysteriaVersion2(node)) "hysteria2" else "hysteria"
            protocol.contains("tuic") -> "tuic"
            else -> protocol
        }
    }

    private fun isHysteriaVersion2(node: JSONObject): Boolean {
        val settings = node.optJSONObject("settings")
        val stream = node.optJSONObject("streamSettings")
        val hysteriaSettings = stream?.optJSONObject("hysteriaSettings")
        return node.cleanInt("version") == 2 ||
            settings?.cleanInt("version") == 2 ||
            hysteriaSettings?.cleanInt("version") == 2 ||
            node.cleanString("type", "protocol").equals("hysteria2", ignoreCase = true) ||
            stream?.cleanString("network").equals("hysteria", ignoreCase = true)
    }

    private fun isNonProxyProtocolName(value: String): Boolean {
        return value.trim().lowercase() in setOf(
            "freedom",
            "blackhole",
            "dns",
            "loopback",
            "direct",
            "block",
            "selector",
            "urltest",
            "url-test",
            "wireguard"
        )
    }

    private fun findProtocolLinksInText(text: String): List<String> {
        val regex = Regex("""(?i)\b(?:vless|vmess|trojan|ss|hysteria2|hysteria|hy2|hy|tuic)://[^\s"'<>]+""")
        return regex.findAll(text)
            .map { match ->
                match.value.trim().trimEnd(',', ';', ']', '}', ')')
            }
            .filter { isProtocolLink(it) }
            .toList()
    }

    private fun parseHysteriaLinksFromClashYaml(text: String): List<String> {
        if (!text.contains("type:", ignoreCase = true) || !text.contains("server:", ignoreCase = true)) {
            return emptyList()
        }

        val blocks = mutableListOf<Map<String, String>>()
        var current: LinkedHashMap<String, String>? = null
        var nestedPrefix: String? = null
        var nestedIndent = -1

        fun flushCurrent() {
            current?.takeIf { it.isNotEmpty() }?.let { blocks.add(it.toMap()) }
            current = null
            nestedPrefix = null
            nestedIndent = -1
        }

        text.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

            if (trimmed.startsWith("- ")) {
                flushCurrent()
                current = linkedMapOf()
                nestedPrefix = null
                nestedIndent = -1
                parseYamlPayloadInto(current!!, trimmed.removePrefix("- ").trim(), null)
                return@forEach
            }

            val target = current ?: return@forEach
            val indent = rawLine.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            if (nestedPrefix != null && indent <= nestedIndent) {
                nestedPrefix = null
                nestedIndent = -1
            }
            val pair = parseYamlKeyValue(trimmed) ?: return@forEach
            val key = normalizeConfigKey(pair.first)
            val value = cleanYamlScalar(pair.second)
            if (value.isBlank()) {
                nestedPrefix = key
                nestedIndent = indent
            } else {
                val finalKey = nestedPrefix?.let { "$it$key" } ?: key
                target[finalKey] = value
                if (key != "type" && key != "password") {
                    nestedPrefix = null
                }
            }
        }
        flushCurrent()

        return dedupeServerLinks(blocks.mapNotNull(::hysteriaShareLinkFromConfigMap))
    }

    private fun parseYamlPayloadInto(
        target: MutableMap<String, String>,
        payload: String,
        nestedPrefix: String?
    ) {
        if (payload.isBlank()) return
        if (payload.startsWith("{") && payload.endsWith("}")) {
            payload.trim('{', '}')
                .split(",")
                .mapNotNull { parseYamlKeyValue(it.trim()) }
                .forEach { (key, value) ->
                    val normalizedKey = nestedPrefix?.let { "$it${normalizeConfigKey(key)}" } ?: normalizeConfigKey(key)
                    target[normalizedKey] = cleanYamlScalar(value)
                }
            return
        }

        parseYamlKeyValue(payload)?.let { (key, value) ->
            val normalizedKey = nestedPrefix?.let { "$it${normalizeConfigKey(key)}" } ?: normalizeConfigKey(key)
            target[normalizedKey] = cleanYamlScalar(value)
        }
    }

    private fun parseYamlKeyValue(line: String): Pair<String, String>? {
        val index = line.indexOf(':')
        if (index <= 0) return null
        return line.substring(0, index).trim() to line.substring(index + 1).trim()
    }

    private fun hysteriaShareLinkFromConfigMap(config: Map<String, String>): String? {
        val type = config.valueFor("type")?.lowercase().orEmpty()
        if (!isHysteriaProtocolName(type)) return null

        val host = config.valueFor("server", "address", "host") ?: return null
        val ports = config.valueFor("serverports", "ports", "mport")
        val port = config.intFor("serverport", "port")
            ?: ports?.let { firstPortFromRange(it) }
            ?: return null
        val password = config.valueFor("password", "auth", "authstr")
        val obfsType = config.valueFor("obfstype", "obfs")
            ?.takeIf { it.lowercase() != "true" && it.lowercase() != "false" }
        val obfsPassword = config.valueFor("obfspassword", "obfsparam", "obfspass", "obfspasswd")
        val insecure = config.valueFor("skipcertverify", "skipcert", "insecure", "allowinsecure")
            ?.let { parseBooleanText(it) }
        val params = linkedMapOf<String, String?>(
            "sni" to config.valueFor("sni", "servername", "peer"),
            "alpn" to config.valueFor("alpn"),
            "insecure" to insecure?.let { if (it) "1" else "0" },
            "obfs" to obfsType,
            "obfs-password" to obfsPassword,
            "mport" to ports,
            "hopInterval" to config.valueFor("hopinterval", "interval"),
            "upmbps" to config.valueFor("upmbps", "up", "upspeed", "upload"),
            "downmbps" to config.valueFor("downmbps", "down", "downspeed", "download")
        )
        val name = config.valueFor("name", "tag", "remarks", "remark") ?: "Hysteria2 $host"
        return buildHysteriaShareLink(host, port, password, name, params)
    }

    private fun normalizeConfigKey(key: String): String {
        return key.trim().lowercase().filter { it.isLetterOrDigit() }
    }

    private fun cleanYamlScalar(value: String): String {
        return value
            .trim()
            .trim(',', '[', ']')
            .trim()
            .trim('"', '\'')
            .trim()
    }

    private fun Map<String, String>.valueFor(vararg keys: String): String? {
        return keys
            .map(::normalizeConfigKey)
            .firstNotNullOfOrNull { key ->
                this[key]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }
    }

    private fun Map<String, String>.intFor(vararg keys: String): Int? {
        return valueFor(*keys)?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun firstPortFromRange(value: String): Int? {
        return Regex("""\d+""").find(value)?.value?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun parseBooleanText(value: String): Boolean? {
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }

    private fun encodeUriComponent(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun JSONObject.cleanString(vararg keys: String): String? {
        for (key in keys) {
            val raw = opt(key)
            val text = when (raw) {
                null, JSONObject.NULL -> null
                is String -> raw
                is Number, is Boolean -> raw.toString()
                else -> null
            }
            val normalized = text
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            if (normalized != null) return normalized
        }
        return null
    }

    private fun JSONObject.cleanInt(vararg keys: String): Int? {
        for (key in keys) {
            val raw = opt(key)
            val parsed = when (raw) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }
            if (parsed != null && parsed > 0) return parsed
        }
        return null
    }

    private fun JSONObject.cleanBoolean(vararg keys: String): Boolean? {
        for (key in keys) {
            val raw = opt(key)
            val parsed = when (raw) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "1", "true", "yes", "y" -> true
                    "0", "false", "no", "n" -> false
                    else -> null
                }
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun JSONObject.cleanStringArray(key: String): String? {
        val array = optJSONArray(key) ?: return cleanString(key)
        val values = buildList {
            for (i in 0 until array.length()) {
                array.optString(i)
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?.let { add(it) }
            }
        }
        return values.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    private fun JSONObject.cleanStringList(vararg keys: String): String? {
        for (key in keys) {
            val raw = opt(key)
            val values = when (raw) {
                is JSONArray -> buildList {
                    for (i in 0 until raw.length()) {
                        raw.optString(i)
                            .trim()
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                            ?.let { add(it) }
                    }
                }
                is String -> listOf(raw.trim())
                is Number -> listOf(raw.toString())
                else -> emptyList()
            }
            val normalized = values
                .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                .joinToString(",")
                .takeIf { it.isNotBlank() }
            if (normalized != null) return normalized
        }
        return null
    }

    private fun serverProtocolSummary(links: List<String>): String {
        return links
            .mapNotNull { link ->
                link.substringBefore("://", "")
                    .takeIf { it.isNotBlank() }
                    ?.lowercase()
            }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
            .entries
            .joinToString(", ") { "${it.key}=${it.value}" }
    }

    internal fun parseServerLinksFromApiJson(jsonText: String): List<String>? {
        if (!jsonText.startsWith("{") || !jsonText.endsWith("}")) return null
        return runCatching {
            val root = JSONObject(jsonText)
            val collectedLinks = findProtocolLinksInText(jsonText) +
                parseServerLinksFromClientJsonConfig(jsonText) +
                collectStringPayloads(root).flatMap(::parseServerLinksFromSubscriptionText)
            val parsedFromNamedArrays = mutableListOf<String>()
            val candidates = listOfNotNull(
                root,
                root.optJSONObject("response"),
                root.optJSONObject("data"),
                root.optJSONObject("result")
            )

            candidates.forEach { container ->
                val serversArray = container.optJSONArray("servers")
                    ?: container.optJSONArray("serverList")
                    ?: container.optJSONArray("links")
                    ?: container.optJSONArray("items")
                    ?: return@forEach

                val parsed = parseServersArray(serversArray)
                if (parsed.isNotEmpty()) parsedFromNamedArrays.addAll(parsed)
            }

            dedupeServerLinks(parsedFromNamedArrays + collectedLinks).takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun collectStringPayloads(node: Any?, depth: Int = 0): List<String> {
        if (node == null || node == JSONObject.NULL || depth > 12) return emptyList()
        return when (node) {
            is JSONObject -> buildList {
                val keys = node.keys()
                while (keys.hasNext()) {
                    when (val value = node.opt(keys.next())) {
                        is String -> value.trim().takeIf { it.isNotBlank() }?.let { add(it) }
                        is JSONObject, is JSONArray -> addAll(collectStringPayloads(value, depth + 1))
                    }
                }
            }
            is JSONArray -> buildList {
                for (i in 0 until node.length()) {
                    addAll(collectStringPayloads(node.opt(i), depth + 1))
                }
            }
            else -> emptyList()
        }
    }

    private fun parseServersArray(array: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            val normalized = normalizeServerItem(item)
            if (!normalized.isNullOrBlank()) {
                result.add(normalized)
            }
        }
        return result
    }

    private fun normalizeServerItem(item: Any?): String? {
        return when (item) {
            is String -> item.trim().takeIf { it.isNotBlank() }
            is JSONObject -> {
                val link = item.optString("link")
                    .ifBlank { item.optString("url") }
                    .ifBlank { item.optString("uri") }
                    .ifBlank { item.optString("subscriptionUrl") }
                    .ifBlank { item.optString("subUrl") }
                    .ifBlank { findFirstProtocolLink(item).orEmpty() }
                    .trim()
                if (link.isBlank()) return null

                val serverDescription = item.optString("serverDescription")
                    .ifBlank { item.optString("server_description") }
                    .ifBlank { item.optString("server-description") }
                    .ifBlank { item.optString("description") }
                    .ifBlank { serverDescriptionFromMeta(item.opt("meta")).orEmpty() }
                    .ifBlank {
                        findFirstStringByKeys(
                            item,
                            setOf(
                                "serverdescription",
                                "server_description",
                                "server-description",
                                "description"
                            )
                        ).orEmpty()
                    }
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                val hostUuid = listOf(
                    item.optString("uuid"),
                    item.optString("hostUuid"),
                    item.optString("host_uuid")
                ).map { it.trim() }
                    .firstOrNull { isUuid(it) }

                // Имя сервера в JSON-подписке часто лежит в отдельном поле, а не в #fragment
                // ссылки. Переносим его во fragment, если у самой ссылки имя — заглушка.
                val displayName = item.optString("name")
                    .ifBlank { item.optString("remark") }
                    .ifBlank { item.optString("remarks") }
                    .ifBlank { item.optString("ps") }
                    .ifBlank { item.optString("tag") }
                    .ifBlank { item.optString("title") }
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                var normalized = appendServerDescriptionToLink(link, serverDescription)
                normalized = appendHostUuidToLink(normalized, hostUuid)
                if (displayName != null &&
                    !isGenericServerName(displayName) &&
                    shareLinkNameNeedsImprovement(normalized)
                ) {
                    normalized = setShareLinkFragmentName(normalized, displayName)
                }
                normalized
            }
            else -> null
        }
    }

    private fun serverDescriptionFromMeta(meta: Any?): String? {
        return when (meta) {
            is JSONObject -> findFirstStringByKeys(
                meta,
                setOf("serverdescription", "server_description", "server-description", "description")
            )
            is String -> {
                val trimmed = meta.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?: return null
                val decoded = decodeBase64Header(trimmed) ?: trimmed
                val jsonText = decoded.trim()
                if (jsonText.startsWith("{") && jsonText.endsWith("}")) {
                    runCatching {
                        findFirstStringByKeys(
                            JSONObject(jsonText),
                            setOf("serverdescription", "server_description", "server-description", "description")
                        )
                    }.getOrNull()
                } else {
                    null
                }
            }
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun findFirstStringByKeys(node: Any?, normalizedKeys: Set<String>): String? {
        return when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key) ?: continue
                    val normalizedKey = key.trim().lowercase()
                    if (normalizedKey in normalizedKeys) {
                        val direct = when (value) {
                            is String -> value.trim()
                            is Number, is Boolean -> value.toString().trim()
                            else -> ""
                        }.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        if (!direct.isNullOrBlank()) return direct
                    }
                    val nested = findFirstStringByKeys(value, normalizedKeys)
                    if (!nested.isNullOrBlank()) return nested
                }
                null
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val nested = findFirstStringByKeys(node.opt(i), normalizedKeys)
                    if (!nested.isNullOrBlank()) return nested
                }
                null
            }
            else -> null
        }
    }

    private fun appendServerDescriptionToLink(link: String, serverDescription: String?): String {
        if (serverDescription.isNullOrBlank()) return link
        if (!link.contains("://")) return link
        if (Regex("""([?&])serverDescription=""", RegexOption.IGNORE_CASE).containsMatchIn(link)) return link

        val hashIndex = link.indexOf('#')
        val base = if (hashIndex >= 0) link.substring(0, hashIndex) else link
        val fragment = if (hashIndex >= 0) link.substring(hashIndex) else ""
        val separator = if (base.contains("?")) "&" else "?"
        val encoded = Uri.encode(serverDescription)
        return "$base${separator}serverDescription=$encoded$fragment"
    }

    private fun appendHostUuidToLink(link: String, hostUuid: String?): String {
        if (hostUuid.isNullOrBlank()) return link
        if (!link.contains("://")) return link
        if (Regex("""([?&])hostUuid=""", RegexOption.IGNORE_CASE).containsMatchIn(link)) return link

        val hashIndex = link.indexOf('#')
        val base = if (hashIndex >= 0) link.substring(0, hashIndex) else link
        val fragment = if (hashIndex >= 0) link.substring(hashIndex) else ""
        val separator = if (base.contains("?")) "&" else "?"
        val encoded = Uri.encode(hostUuid)
        return "$base${separator}hostUuid=$encoded$fragment"
    }

    private fun isUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return UUID_REGEX.matches(value.trim())
    }

    private fun findFirstProtocolLink(node: Any?): String? {
        return when (node) {
            is String -> node.trim().takeIf { isProtocolLink(it) }
            is JSONObject -> {
                // Сначала пытаемся стандартные ключи верхнего уровня.
                val direct = listOf(
                    "link",
                    "url",
                    "uri",
                    "subscriptionUrl",
                    "subUrl",
                    "configUrl",
                    "serverUrl"
                ).firstNotNullOfOrNull { key ->
                    node.optString(key).trim().takeIf { isProtocolLink(it) }
                }
                if (!direct.isNullOrBlank()) return direct

                // Затем просматриваем все поля рекурсивно.
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key) ?: continue
                    val nested = findFirstProtocolLink(value)
                    if (!nested.isNullOrBlank()) return nested
                }
                null
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val nested = findFirstProtocolLink(node.opt(i))
                    if (!nested.isNullOrBlank()) return nested
                }
                null
            }
            else -> null
        }
    }

    private fun isProtocolLink(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("vless://") ||
            normalized.startsWith("vmess://") ||
            normalized.startsWith("trojan://") ||
            normalized.startsWith("ss://") ||
            normalized.startsWith("hysteria://") ||
            normalized.startsWith("hysteria2://") ||
            normalized.startsWith("hy2://") ||
            normalized.startsWith("hy://") ||
            normalized.startsWith("tuic://")
    }

    private fun isHysteriaProtocolLink(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("hysteria://") ||
            normalized.startsWith("hysteria2://") ||
            normalized.startsWith("hy2://") ||
            normalized.startsWith("hy://")
    }

    private fun isHysteriaProtocolName(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized == "hy" ||
            normalized == "hy2" ||
            normalized == "hysteria" ||
            normalized == "hysteria2" ||
            normalized.contains("hysteria")
    }

    private fun isUsableServerHost(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return normalized.isNotBlank() &&
            normalized != "0.0.0.0" &&
            normalized != "::" &&
            normalized != "::1" &&
            normalized != "localhost" &&
            normalized != "127.0.0.1"
    }
}
