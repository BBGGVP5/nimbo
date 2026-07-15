package com.danila.nimbo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.LinkParser
import com.danila.nimbo.network.PingManager
import com.danila.nimbo.network.PingConfig
import com.danila.nimbo.network.PingProtocol
import com.danila.nimbo.network.RemnawaveApiClient
import com.danila.nimbo.network.RemnawaveSubscriptionTemplate
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.ui.screens.SubscriptionTemplateCache
import com.danila.nimbo.ui.screens.SubscriptionProfileMetadata
import com.danila.nimbo.ui.screens.toMetadata
import com.danila.nimbo.ui.i18n.loc
import com.danila.nimbo.ui.i18n.serverCountEn
import com.danila.nimbo.ui.i18n.serverCountRu
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.danila.nimbo.utils.ActiveNetworkType
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.SubscriptionLogoCache
import com.danila.nimbo.utils.detectActiveNetworkType
import com.danila.nimbo.utils.isInternetAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import com.danila.nimbo.vpn.XrayManager
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val preferencesManager = PreferencesManager(application)

    private val _profilesState = MutableStateFlow<List<SubscriptionProfile>>(emptyList())
    val profilesState: StateFlow<List<SubscriptionProfile>> = _profilesState.asStateFlow()

    val profilesMetadataState: StateFlow<List<SubscriptionProfileMetadata>> = _profilesState
        .map { list -> list.map { it.toMetadata() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val _serversState = MutableStateFlow<List<Server>>(emptyList())
    val serversState: StateFlow<List<Server>> = _serversState.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _activePingKeys = MutableStateFlow<Set<String>>(emptySet())
    val activePingKeys: StateFlow<Set<String>> = _activePingKeys.asStateFlow()

    private val _topNotification = MutableStateFlow<com.danila.nimbo.ui.components.NotificationData?>(null)
    val topNotification: StateFlow<com.danila.nimbo.ui.components.NotificationData?> = _topNotification.asStateFlow()

    private val _isRefreshingSubscriptions = MutableStateFlow(false)
    val isRefreshingSubscriptions = _isRefreshingSubscriptions.asStateFlow()

    // --- State for IP and Country ---
    private val _currentIpAddress = MutableStateFlow<String?>(null)
    val currentIpAddress: StateFlow<String?> = _currentIpAddress.asStateFlow()

    private val _ipCountry = MutableStateFlow<String?>(null)
    val ipCountry: StateFlow<String?> = _ipCountry.asStateFlow()

    private val _ipCountryFlag = MutableStateFlow<String?>(null)
    val ipCountryFlag: StateFlow<String?> = _ipCountryFlag.asStateFlow()

    private val _serverIpAddress = MutableStateFlow<String?>(null)
    val serverIpAddress: StateFlow<String?> = _serverIpAddress.asStateFlow()

    private val _devicesState = MutableStateFlow<List<com.danila.nimbo.network.RemnawaveDevice>>(emptyList())
    val devicesState: StateFlow<List<com.danila.nimbo.network.RemnawaveDevice>> = _devicesState.asStateFlow()

    private val _isRefreshingDevices = MutableStateFlow(false)
    val isRefreshingDevices: StateFlow<Boolean> = _isRefreshingDevices.asStateFlow()

    private var pingJob: Job? = null
    private var notificationDismissJob: Job? = null
    @Volatile
    private var pingRunSerial: Long = 0L
    // Результаты пинга копятся здесь и применяются к UI пачками (~5 раз/сек),
    // иначе каждый отдельный результат пересобирает весь список → лаги при скролле.
    private val pendingPings = HashMap<String, Int>()
    private val pendingPingsLock = Any()
    private val subscriptionJobs = ConcurrentHashMap<String, Job>()
    private var connectionInfoJob: Job? = null
    @Volatile
    private var lastPingCachePersistMs: Long = 0L

    private fun isUuidLike(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            .matches(value.trim())
    }

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

    private fun hostDescriptionKey(protocol: String?, host: String?, port: Int): String {
        return "${canonicalProtocol(protocol)}|${host?.trim()?.lowercase().orEmpty()}|$port"
    }

    private fun hostPortKey(host: String?, port: Int): String {
        return "${host?.trim()?.lowercase().orEmpty()}|$port"
    }

    private fun hostNameKey(name: String?): String {
        return name
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.lowercase()
            .orEmpty()
    }

    private fun isXrayJsonTemplateType(templateType: String): Boolean {
        val type = templateType.trim().lowercase()
        return type == "xray_json" || type.contains("xray") || type.contains("json")
    }

    private data class PingTarget(val host: String, val port: Int)

    private fun extractPingTargetFromXrayJson(config: String?): PingTarget? {
        val text = config
            ?.trim()
            ?.takeIf { it.startsWith("{") && it.endsWith("}") }
            ?: return null

        return runCatching {
            val json = JSONObject(text)
            json.optJSONArray("outbounds")?.let { outbounds ->
                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.optJSONObject(i) ?: continue
                    val protocol = outbound.optString("protocol").trim().lowercase()
                    if (protocol in setOf("freedom", "blackhole", "dns", "wireguard")) continue
                    extractPingTargetFromOutbound(outbound)?.let { return@runCatching it }
                }
            }
            json.optJSONArray("inbounds")?.let { inbounds ->
                for (i in 0 until inbounds.length()) {
                    val inbound = inbounds.optJSONObject(i) ?: continue
                    val protocol = inbound.optString("protocol").trim().lowercase()
                    if (protocol in setOf("socks", "http", "tun", "dokodemo-door")) continue
                    inbound.toPingTarget()?.let { return@runCatching it }
                }
            }
            null
        }.getOrNull()
    }

    private fun extractPingTargetFromOutbound(outbound: JSONObject): PingTarget? {
        val settings = outbound.optJSONObject("settings")
        return pingTargetFromArray(settings?.optJSONArray("vnext"))
            ?: pingTargetFromArray(settings?.optJSONArray("servers"))
            ?: settings?.toPingTarget()
            ?: outbound.toPingTarget()
    }

    private fun pingTargetFromArray(array: JSONArray?): PingTarget? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val target = array.optJSONObject(i)?.toPingTarget()
            if (target != null) return target
        }
        return null
    }

    private fun JSONObject.toPingTarget(): PingTarget? {
        val host = listOf("address", "server", "host", "domain", "listen")
            .firstNotNullOfOrNull { key ->
                optString(key)
                    .trim()
                    .takeIf { isValidPingHost(it) }
            }
            ?: return null
        val port = listOf("port", "serverPort", "server_port")
            .firstNotNullOfOrNull { key ->
                optString(key)
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it > 0 }
            }
            ?: return null
        return PingTarget(host, port)
    }

    private fun isValidPingHost(host: String): Boolean {
        val value = host.trim().lowercase()
        return value.isNotBlank() &&
            value != "api" &&
            value != "localhost" &&
            value != "0.0.0.0" &&
            value != "::" &&
            value != "::1"
    }

    private fun remoteConfigForPing(profile: SubscriptionProfile, server: Server): String? {
        val templateConfig = profile.templates.firstOrNull { template ->
            (!server.templateUuid.isNullOrBlank() && template.uuid.equals(server.templateUuid, ignoreCase = true)) ||
                (!server.templateName.isNullOrBlank() && template.name.equals(server.templateName, ignoreCase = true))
        }?.config?.takeIf { it.isNotBlank() }
        return templateConfig ?: profile.rawConfig?.takeIf { it.isNotBlank() }
    }

    private fun resolveTemplateUuidValue(
        rawValue: String?,
        defaultTemplate: RemnawaveSubscriptionTemplate?
    ): String? {
        val value = rawValue
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: return null

        return if (value.equals(RemnawaveApiClient.DEFAULT_XRAY_TEMPLATE_MARKER, ignoreCase = true)) {
            defaultTemplate?.uuid
        } else {
            value
        }
    }

    private fun uniqueTemplateMapByLookupKey(
        entries: Set<Map.Entry<String, String>>,
        prefix: String,
        defaultTemplate: RemnawaveSubscriptionTemplate?
    ): Map<String, String> {
        return entries
            .mapNotNull { entry ->
                if (!entry.key.startsWith(prefix, ignoreCase = true)) return@mapNotNull null
                val lookupKey = entry.key
                    .removePrefix(prefix)
                    .substringBeforeLast("|")
                    .trim()
                if (lookupKey.isBlank()) return@mapNotNull null
                lookupKey to entry.value
            }
            .groupBy({ it.first }, { it.second.trim() })
            .mapNotNull { (key, values) ->
                val uniqueValues = values
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                if (uniqueValues.size != 1) return@mapNotNull null
                resolveTemplateUuidValue(uniqueValues.first(), defaultTemplate)?.let { key to it }
            }
            .toMap()
    }

    private fun uniqueIntMapByLookupKey(
        entries: Set<Map.Entry<String, String>>,
        prefix: String
    ): Map<String, Int> {
        return entries
            .mapNotNull { entry ->
                if (!entry.key.startsWith(prefix, ignoreCase = true)) return@mapNotNull null
                val lookupKey = entry.key
                    .removePrefix(prefix)
                    .substringBeforeLast("|")
                    .trim()
                val value = entry.value.trim().toIntOrNull() ?: return@mapNotNull null
                if (lookupKey.isBlank()) null else lookupKey to value
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, values) ->
                val uniqueValues = values.distinct()
                if (uniqueValues.size == 1) key to uniqueValues.first() else null
            }
            .toMap()
    }

    private suspend fun fetchRenderedTemplateConfigs(
        subscriptionUrl: String,
        templates: List<RemnawaveSubscriptionTemplate>,
        existingTemplates: List<SubscriptionTemplateCache>,
        refreshRenderedConfigs: Boolean
    ): List<RemnawaveSubscriptionTemplate> {
        if (templates.isEmpty()) return templates

        val existingByUuid = existingTemplates
            .associateBy { it.uuid.trim().lowercase() }

        return templates.map { template ->
            val previous = existingByUuid[template.uuid.trim().lowercase()]

            template.copy(
                config = previous?.config ?: template.config,
                fetchedAtMs = previous?.fetchedAtMs ?: template.fetchedAtMs
            )
        }
    }

    private fun sanitizeServerDescriptionForServer(server: Server, description: String?): String? {
        val desc = normalizeMaybeBase64Description(description)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: return null
        // We used to drop the description when it matched server.name to avoid
        // showing duplicate info. But that meant Remnawave subscriptions where the
        // template name and node description are the same (very common) lost their
        // description entirely. Keep it — the UI can dedupe per-row if it wants.

        val lower = desc.lowercase()
        val network = server.network?.trim()?.lowercase().orEmpty()
        val protocol = server.protocol.trim().lowercase()

        if (lower.contains("xhttp") && network != "xhttp" && network != "splithttp") return null
        if (lower.contains("grpc") && network != "grpc") return null
        if (lower.contains("shadowsocks") && protocol != "ss" && protocol != "shadowsocks") return null
        if (lower.contains("vless") && protocol.isNotBlank() && protocol != "vless") return null
        if ((lower.contains("hysteria") || lower.contains("hy2")) &&
            protocol != "hy2" &&
            !protocol.contains("hysteria")
        ) return null

        return desc
    }

    private fun isHysteriaServer(server: Server): Boolean {
        val protocol = server.protocol.trim().lowercase()
        return protocol == "hy" ||
            protocol == "hy2" ||
            protocol.contains("hysteria") ||
            server.network?.contains("hysteria", ignoreCase = true) == true
    }

    private fun protocolMarkerForServer(server: Server): String {
        return when {
            isHysteriaServer(server) -> "HYSTERIA"
            server.protocol.isBlank() -> ""
            else -> server.protocol.trim().uppercase()
        }
    }

    private fun genericServerNameCandidate(server: Server, description: String?): String? {
        val descriptionName = normalizeMaybeBase64Description(description)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.takeIf { !SubscriptionManager.isGenericServerName(it) }

        val baseName = descriptionName ?: countryDisplayNameFromHost(server.host)
        if (baseName.isNullOrBlank()) return null

        val marker = protocolMarkerForServer(server)
        if (marker.isBlank()) return baseName
        if (
            baseName.contains(marker, ignoreCase = true) ||
            (marker == "HYSTERIA" && (baseName.contains("hysteria", ignoreCase = true) || baseName.contains("hy2", ignoreCase = true)))
        ) {
            return baseName
        }
        return "$baseName | $marker"
    }

    /**
     * Ведущий «локационный» сегмент имени до первого разделителя (· или |):
     * "🇫🇮 Финляндия · Shadowsocks ✈️" -> "🇫🇮 Финляндия". Нужен, чтобы у соседних
     * инбаундов на одном хосте (ss/trojan со страной vs VLESS «Server») извлечь общее
     * название страны: полные имена различаются хвостом транспорта, а сегмент — общий.
     */
    private fun locationSegment(name: String): String {
        return name.split('·', '|').firstOrNull()?.trim().orEmpty()
    }

    private fun countryDisplayNameFromHost(host: String?): String? {
        val normalized = host
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val countries = mapOf(
            "ru" to "🇷🇺 Россия",
            "pl" to "🇵🇱 Польша",
            "se" to "🇸🇪 Швеция",
            "fi" to "🇫🇮 Финляндия",
            "de" to "🇩🇪 Германия",
            "nl" to "🇳🇱 Нидерланды",
            "fr" to "🇫🇷 Франция",
            "gb" to "🇬🇧 Великобритания",
            "uk" to "🇬🇧 Великобритания",
            "us" to "🇺🇸 США",
            "ca" to "🇨🇦 Канада",
            "tr" to "🇹🇷 Турция",
            "jp" to "🇯🇵 Япония",
            "kr" to "🇰🇷 Корея",
            "sg" to "🇸🇬 Сингапур",
            "hk" to "🇭🇰 Гонконг",
            "ch" to "🇨🇭 Швейцария",
            "it" to "🇮🇹 Италия",
            "es" to "🇪🇸 Испания",
            "cz" to "🇨🇿 Чехия",
            "at" to "🇦🇹 Австрия"
        )

        val tokens = normalized
            .split(Regex("[^a-z]+"))
            .filter { it.length in 2..3 }
        val tld = normalized.substringAfterLast('.', "").takeIf { it.length == 2 }
        return (listOfNotNull(tld) + tokens).firstNotNullOfOrNull { token ->
            countries[token]
        }
    }

    private fun normalizeMaybeBase64Description(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank() || normalized.equals("null", ignoreCase = true)) return null
        if (!Regex("^[A-Za-z0-9+/=_-]{8,}$").matches(normalized)) return normalized

        val candidate = normalized.replace('-', '+').replace('_', '/')
        if (candidate.length % 4 != 0) return normalized

        val decoded = runCatching {
            val bytes = java.util.Base64.getDecoder().decode(candidate)
            String(bytes, Charsets.UTF_8).trim()
        }.getOrNull()

        val looksReadable = decoded?.let {
            it.isNotBlank() &&
                it.any { ch -> ch.isLetterOrDigit() } &&
                it.none { ch -> ch.code in 0..8 || ch.code in 14..31 || ch.code == 127 }
        } == true

        return if (looksReadable) decoded else normalized
    }

    /**
     * Прерывает все фоновые сетевые задачи (пинг, обновление подписок).
     * Вызывается перед подключением к VPN, чтобы избежать конфликтов ресурсов.
     */
    fun cancelAllSystemJobs() {
        Log.d("MainViewModel", "=== cancelAllSystemJobs called ===")
        // Отменяем пинг
        pingJob?.cancel()
        pingJob = null
        _isPinging.value = false

        // Отменяем все активные загрузки подписок
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
        _isRefreshingSubscriptions.value = false

        // Сбрасываем статус загрузки у всех профилей
        _profilesState.value = _profilesState.value.map { it.copy(isLoading = false) }
    }

    fun dismissNotification() {
        notificationDismissJob?.cancel()
        notificationDismissJob = null
        _topNotification.value = null
    }

    fun showTopNotification(message: String, type: com.danila.nimbo.ui.components.NotificationType = com.danila.nimbo.ui.components.NotificationType.NORMAL) {
        notificationDismissJob?.cancel()

        val notification = com.danila.nimbo.ui.components.NotificationData(message, type)
        _topNotification.value = notification

        val item = com.danila.nimbo.model.NotificationItem(
            id = java.util.UUID.randomUUID().toString(),
            title = when(type) {
                com.danila.nimbo.ui.components.NotificationType.SUCCESS -> "Успешно"
                com.danila.nimbo.ui.components.NotificationType.ERROR -> "Ошибка"
                com.danila.nimbo.ui.components.NotificationType.UPDATE -> "Обновление"
                com.danila.nimbo.ui.components.NotificationType.PING -> "Пинг"
                else -> "Уведомление"
            },
            message = message,
            timestamp = System.currentTimeMillis(),
            type = type
        )
        preferencesManager.addNotificationToHistory(item)

        val displayDurationMs = when (type) {
            com.danila.nimbo.ui.components.NotificationType.UPDATE,
            com.danila.nimbo.ui.components.NotificationType.PING -> 8_000L
            com.danila.nimbo.ui.components.NotificationType.ERROR -> 5_000L
            else -> 3_600L
        }
        notificationDismissJob = viewModelScope.launch {
            delay(displayDurationMs)
            if (_topNotification.value?.id == notification.id) {
                _topNotification.value = null
            }
        }
    }

    fun sendManualNotification(title: String, message: String) {
        val item = com.danila.nimbo.model.NotificationItem(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            type = com.danila.nimbo.ui.components.NotificationType.NORMAL
        )
        preferencesManager.addNotificationToHistory(item)
        showTopNotification(message)
    }

    fun pingAllServers(
        customStartMessage: String? = null,
        type: com.danila.nimbo.ui.components.NotificationType = com.danila.nimbo.ui.components.NotificationType.PING,
        silent: Boolean = false
    ) {
        launchPing(targetServers = null, customStartMessage = customStartMessage, type = type, silent = silent)
    }

    /**
     * Пингует конкретный список серверов (например, все сервера одной подписки —
     * по кнопке "пинг всех" на карточке подписки).
     */
    fun pingServers(
        servers: List<Server>,
        silent: Boolean = false
    ) {
        if (servers.isEmpty()) return
        launchPing(
            targetServers = servers,
            customStartMessage = null,
            type = com.danila.nimbo.ui.components.NotificationType.PING,
            silent = silent
        )
    }

    /**
     * Единая реализация пинга. [targetServers] == null означает "пинговать все
     * сервера всех подписок". Сервера группируются по реальной цели (host:port),
     * поэтому одинаковые инбаунды пингуются один раз, а результат применяется ко
     * всем серверам с этим адресом (раньше пинг дублировался). Несколько локаций
     * пингуются пачками параллельно, результат каждой сразу летит в UI и в кеш.
     */
    private fun launchPing(
        targetServers: List<Server>?,
        customStartMessage: String?,
        type: com.danila.nimbo.ui.components.NotificationType,
        silent: Boolean
    ) {
        pingJob?.cancel()
        synchronized(pendingPingsLock) { pendingPings.clear() }
        val runId = nextPingRunId()
        pingJob = viewModelScope.launch {
            _isPinging.value = true
            // Применяем накопленные результаты пачками, чтобы не пересобирать список на каждый пинг.
            val flusher = launch {
                while (true) {
                    delay(220)
                    flushPendingPings(runId)
                }
            }
            try {
                if (!silent) showTopNotification(customStartMessage ?: "Пинг серверов...", type)

                val profiles = _profilesState.value
                // (профиль, сервер) — для запрошенных серверов или для всех сразу.
                val ownedServers: List<Pair<SubscriptionProfile, Server>> = if (targetServers == null) {
                    profiles.flatMap { p -> p.servers.map { p to it } }
                } else {
                    targetServers.mapNotNull { s ->
                        val owner = profiles.firstOrNull { p -> p.servers.any { it.pingKey() == s.pingKey() } }
                            ?: profiles.firstOrNull { it.url == s.profileUrl }
                        owner?.let { it to s }
                    }
                }

                if (ownedServers.isEmpty()) return@launch

                val pingConfig = buildPingConfig()
                val batchSize = 5
                // Адрес -> уже измеренный пинг: не пингуем один и тот же инбаунд дважды.
                val measuredTargets = ConcurrentHashMap<Pair<String, Int>, Int>()

                // Сразу помечаем ВСЕ сервера как "пингуются" — анимация идёт у всех,
                // а реально пингуем волной по 5 сверху вниз: значение появляется, как
                // только волна дошла до сервера, и анимация на нём гаснет.
                if (isCurrentPingRun(runId)) {
                    _activePingKeys.update { keys -> keys + ownedServers.map { (_, s) -> s.pingKey() } }
                }

                for (chunk in ownedServers.chunked(batchSize)) {
                    if (!isCurrentPingRun(runId)) break

                    // Резолвим адреса лениво, по ходу пачки (не готовим весь список сразу).
                    val newByTarget = LinkedHashMap<Pair<String, Int>, MutableList<Server>>()
                    val unresolved = mutableListOf<String>()
                    for ((profile, server) in chunk) {
                        val target = resolvePingTarget(profile, server)
                        if (target == null) {
                            unresolved.add(server.pingKey())
                        } else {
                            val cached = measuredTargets[target]
                            if (cached != null) {
                                enqueuePingResults(listOf(server.pingKey()), cached)
                            } else {
                                newByTarget.getOrPut(target) { mutableListOf() }.add(server)
                            }
                        }
                    }
                    // С серверов без цели снимаем анимацию — их не пингуем.
                    if (unresolved.isNotEmpty()) _activePingKeys.update { it - unresolved.toSet() }
                    if (newByTarget.isEmpty()) continue

                    coroutineScope {
                        newByTarget.entries.map { (target, servers) ->
                            launch {
                                val pingValue = withContext(Dispatchers.IO) {
                                    runCatching {
                                        withTimeoutOrNull(pingConfig.timeoutMs.toLong() + 100L) {
                                            PingManager.ping(target.first, target.second, pingConfig)
                                        } ?: -1
                                    }.getOrDefault(-1)
                                }
                                measuredTargets[target] = pingValue
                                if (isCurrentPingRun(runId)) {
                                    // Кладём результат в очередь — флашер применит его пачкой.
                                    enqueuePingResults(servers.map { it.pingKey() }, pingValue)
                                }
                            }
                        }.joinAll()
                    }
                }

                // Останавливаем периодический флашер и применяем остатки один раз.
                flusher.cancel()
                flushPendingPings(runId)
                preferencesManager.saveProfiles(_profilesState.value)
                Log.d("MainViewModel", "All pings completed and saved to persistent storage")
                if (!silent) {
                    showTopNotification("Пинг завершен", com.danila.nimbo.ui.components.NotificationType.SUCCESS)
                }
            } finally {
                flusher.cancel()
                flushPendingPings(runId)
                maybePersistPingCache(force = true)
                if (isCurrentPingRun(runId)) {
                    _activePingKeys.value = emptySet()
                    _isPinging.value = false
                }
            }
        }
    }

    /** Кладёт результат пинга в очередь (потокобезопасно). */
    private fun enqueuePingResults(keys: List<String>, pingValue: Int) {
        synchronized(pendingPingsLock) {
            keys.forEach { pendingPings[it] = pingValue }
        }
    }

    /**
     * Применяет накопленные результаты пинга к UI разом: одно обновление состояния
     * вместо десятков (иначе список дёргается и лагает при скролле во время пинга).
     * Заодно снимает анимацию с этих серверов и сбрасывает кеш.
     */
    private fun flushPendingPings(runId: Long) {
        val snapshot: Map<String, Int>? = synchronized(pendingPingsLock) {
            if (pendingPings.isEmpty()) null
            else HashMap(pendingPings).also { pendingPings.clear() }
        }
        if (snapshot == null || !isCurrentPingRun(runId)) return
        updateServersPings(snapshot)
        _activePingKeys.update { it - snapshot.keys }
        maybePersistPingCache()
    }

    fun pingSingleServer(server: Server, silent: Boolean = true) {
        val ownerProfile = _profilesState.value.firstOrNull { p ->
            p.servers.any { it.pingKey() == server.pingKey() }
        } ?: _profilesState.value.firstOrNull { it.url == server.profileUrl }
        val pingTarget = (ownerProfile?.let { resolvePingTarget(it, server) })
            ?: server.pingTargetOrNull()
            ?: return

        pingJob?.cancel()
        val runId = nextPingRunId()
        pingJob = viewModelScope.launch {
            _isPinging.value = true
            if (isCurrentPingRun(runId)) {
                _activePingKeys.value = setOf(server.pingKey())
            }
            try {
                if (!silent) {
                    showTopNotification("Пинг ${server.name}...", com.danila.nimbo.ui.components.NotificationType.PING)
                }
                val pingValue = withContext(Dispatchers.IO) {
                    runCatching {
                        val pingConfig = buildPingConfig()
                        withTimeoutOrNull(pingConfig.timeoutMs.toLong() + 100L) {
                            PingManager.ping(pingTarget.first, pingTarget.second, pingConfig)
                        } ?: -1
                    }.getOrDefault(-1)
                }
                if (isCurrentPingRun(runId)) {
                    // Применяем результат ко всем серверам с тем же адресом (host:port),
                    // чтобы у одинаковых инбаундов не расходился пинг.
                    val sharedKeys = _profilesState.value
                        .flatMap { it.servers }
                        .filter { it.pingTargetOrNull() == pingTarget }
                        .map { it.pingKey() }
                        .toMutableSet()
                        .apply { add(server.pingKey()) }
                    updateServersPings(sharedKeys.associateWith { pingValue })
                    maybePersistPingCache()
                }
                preferencesManager.saveProfiles(_profilesState.value)
                if (!silent) {
                    val message = if (pingValue >= 0) "Пинг ${server.name}: ${pingValue}мс" else "Пинг ${server.name}: ошибка"
                    showTopNotification(message, com.danila.nimbo.ui.components.NotificationType.SUCCESS)
                }
            } finally {
                maybePersistPingCache(force = true)
                if (isCurrentPingRun(runId)) {
                    _activePingKeys.value = emptySet()
                    _isPinging.value = false
                }
            }
        }
    }

    private fun Server.pingTargetOrNull(): Pair<String, Int>? {
        val explicitHost = pingHost
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("API", ignoreCase = true) }
        val explicitPort = pingPort?.takeIf { it > 0 }
        if (explicitHost != null && explicitPort != null) return explicitHost to explicitPort

        val directHost = host
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("API", ignoreCase = true) }
        val directPort = port.takeIf { it > 0 }
        return if (directHost != null && directPort != null && uuid != "remote") {
            directHost to directPort
        } else {
            null
        }
    }

    /**
     * Резолвит цель для пинга для ЛЮБОГО сервера. Сначала пробуем прямой
     * host:port / явный pingHost, а если их нет (типичный remote-template
     * сервер, у которого реальный адрес лежит в xray-конфиге) — достаём
     * адрес из конфига на лету. Это гарантирует, что пинг считается для всех
     * серверов, а не только для тех, у кого pingHost был сохранён при загрузке.
     */
    private fun resolvePingTarget(profile: SubscriptionProfile, server: Server): Pair<String, Int>? {
        server.pingTargetOrNull()?.let { return it }
        val target = extractPingTargetFromXrayJson(remoteConfigForPing(profile, server)) ?: return null
        return target.host to target.port
    }

    private fun buildPingConfig(): PingConfig {
        val pingThroughProxy = preferencesManager.pingThroughProxy && XrayManager.isConnected
        return PingConfig(
            protocol = when (preferencesManager.pingProtocol) {
                1 -> PingProtocol.HTTP_GET
                2 -> PingProtocol.HTTP_HEAD
                3 -> PingProtocol.HTTPS_STRICT
                4 -> PingProtocol.ICMP
                else -> PingProtocol.TCP
            },
            testUrl = preferencesManager.pingUrl,
            timeoutMs = preferencesManager.pingTimeout * 1000,
            useProxy = pingThroughProxy,
            proxyPort = 2080
        )
    }

    /**
     * Чистый расчёт нового списка профилей/серверов с применёнными пингами.
     * Не трогает состояние — можно гонять на фоновом потоке.
     */
    private fun computeUpdatedProfiles(
        current: List<SubscriptionProfile>,
        newPings: Map<String, Int>,
        now: Long
    ): Pair<List<SubscriptionProfile>, List<Server>> {
        val updated = current.map { profile ->
            val hasAnyTarget = profile.servers.any { s -> newPings.containsKey(s.pingKey()) }
            if (!hasAnyTarget) {
                profile
            } else {
                val updatedServers = profile.servers.map { s ->
                    val key = s.pingKey()
                    if (newPings.containsKey(key)) {
                        val newPing = newPings[key] ?: -1
                        // Если новый пинг не удался, не затираем предыдущий успешный результат.
                        if (newPing >= 0) {
                            s.copy(ping = newPing, pingTimestamp = now)
                        } else if ((s.ping ?: -1) >= 0) {
                            s
                        } else {
                            s.copy(ping = -1, pingTimestamp = s.pingTimestamp ?: now)
                        }
                    } else s
                }
                profile.copy(servers = updatedServers)
            }
        }
        return updated to updated.flatMap { it.servers }
    }

    private fun updateServersPings(newPings: Map<String, Int>) {
        val now = System.currentTimeMillis()
        _profilesState.update { current ->
            val (updated, flat) = computeUpdatedProfiles(current, newPings, now)
            _serversState.value = flat
            updated
        }
        refreshSelectedServerPing(newPings.keys)
    }

    private fun updateServerPing(targetServer: Server, pingValue: Int) {
        updateServersPings(mapOf(targetServer.pingKey() to pingValue))
        maybePersistPingCache()
    }

    private fun refreshSelectedServerPing(changedKeys: Set<String>) {
        val selected = VpnManager.selectedServer ?: return
        if (selected.pingKey() !in changedKeys) return
        val refreshed = _profilesState.value.asSequence()
            .flatMap { it.servers.asSequence() }
            .firstOrNull { it.matchesSelection(selected) }
            ?: return
        VpnManager.selectedServer = refreshed
    }

    private fun maybePersistPingCache(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPingCachePersistMs < 1500L) return
        lastPingCachePersistMs = now

        viewModelScope.launch(Dispatchers.IO) {
            preferencesManager.saveProfiles(_profilesState.value)
        }
    }

    fun cancelActivePing() {
        pingRunSerial += 1
        pingJob?.cancel()
        pingJob = null
        _activePingKeys.value = emptySet()
        _isPinging.value = false
        Log.d("MainViewModel", "Active ping cancelled")
    }

    private fun nextPingRunId(): Long {
        pingRunSerial += 1
        return pingRunSerial
    }

    private fun isCurrentPingRun(runId: Long): Boolean = pingRunSerial == runId

    /**
     * Обновляет информацию о текущем IP адресе и стране
     */
    fun refreshIPInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = com.danila.nimbo.utils.getExternalIpAddress()
            _currentIpAddress.value = ip

            val (country, flag) = com.danila.nimbo.utils.getCountryFromIp(ip)
            _ipCountry.value = country
            _ipCountryFlag.value = flag

            Log.d("MainViewModel", "IP Info refreshed: $ip ($country $flag)")
        }
    }

    fun pingServersOnColdStart() {
        if (VpnManager.state.value != VpnState.DISCONNECTED) {
            Log.d("MainViewModel", "Skip cold-start ping: VPN state is ${VpnManager.state.value}")
            return
        }
        if (_profilesState.value.isEmpty()) {
            Log.d("MainViewModel", "Skip cold-start ping: no profiles")
            return
        }
        pingAllServers("Пингуем сервера...", com.danila.nimbo.ui.components.NotificationType.PING)
    }

    private val _isBypassOnlyMode = MutableStateFlow(false)
    val isBypassOnlyMode: StateFlow<Boolean> = _isBypassOnlyMode.asStateFlow()

    private val _isAutoBypassControlReachable = MutableStateFlow(false)
    val isAutoBypassControlReachable: StateFlow<Boolean> = _isAutoBypassControlReachable.asStateFlow()

    private val _activeNetworkType = MutableStateFlow(ActiveNetworkType.NONE)
    val activeNetworkType: StateFlow<ActiveNetworkType> = _activeNetworkType.asStateFlow()

    private var reachabilityJob: Job? = null

    private fun startAutoBypassReachabilityMonitoring() {
        reachabilityJob?.cancel()
        reachabilityJob = viewModelScope.launch {
            while (true) {
                val networkType = detectActiveNetworkType(getApplication())
                _activeNetworkType.value = networkType

                var hasInternet = isInternetAvailable(getApplication())

                if (!hasInternet && networkType != ActiveNetworkType.NONE) {
                    delay(1500)
                    hasInternet = isInternetAvailable(getApplication())
                }

                _isBypassOnlyMode.value = false
                _isAutoBypassControlReachable.value = false
                if (!hasInternet) {
                    Log.d("MainViewModel", "No internet detected, auto-bypass monitor is idle")
                }

                delay(15_000L)
            }
        }
    }

    init {
        Log.d("MainViewModel", "=== ViewModel created ===")
        Log.d("MainViewModel", "Application: ${application.packageName}")
        loadProfiles()
        refreshIPInfo()

        // Слушаем сигнал об отмене всех системных задач (из VPN сервиса)
        viewModelScope.launch {
            VpnManager.cancelSystemJobsSignal.collect {
                cancelAllSystemJobs()
            }
        }

        // Оптимизация обновлений и пингов
        val currentTime = System.currentTimeMillis()
        checkAndAutoUpdateSubscriptions()

        Log.d("MainViewModel", "Launch ping is controlled by MainActivity cold-start flow")

        preferencesManager.lastAppStartTime = currentTime

        // Запуск мониторинга доступности для автообхода
        startAutoBypassReachabilityMonitoring()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("MainViewModel", "=== ViewModel cleared ===")
        Log.d("MainViewModel", "Saving ${_profilesState.value.size} profiles...")
        // Сохраняем профили при очистке ViewModel
        preferencesManager.saveProfiles(_profilesState.value)
        reachabilityJob?.cancel()
        Log.d("MainViewModel", "=== ViewModel cleared (done) ===")
    }

    private fun loadProfiles() {
        val loadedProfiles = preferencesManager.loadProfiles()
        loadedProfiles
            .firstNotNullOfOrNull { it.themeSpec?.takeIf { spec -> spec.isNotBlank() } }
            ?.let { preferencesManager.subscriptionThemeSpec = it }

        _profilesState.value = loadedProfiles
        _serversState.value = loadedProfiles.flatMap { it.servers }
        val totalServers = loadedProfiles.sumOf { it.servers.size }
        Logger.d("MainViewModel", "Загружено ${loadedProfiles.size} профилей с $totalServers серверами")
        Log.d("MainViewModel", "Loaded ${loadedProfiles.size} profiles with $totalServers servers")

        // Восстанавливаем только явно поврежденные/неполные профили, чтобы не требовалось ручное обновление подписки.
        loadedProfiles.forEach { profile ->
            val hasRemotePlaceholder = profile.servers.any { it.uuid == "remote" || it.host == "API" }
            val hasCachedTemplateConfig = profile.templates.any {
                isXrayJsonTemplateType(it.templateType) && !it.config.isNullOrBlank()
            }
            val needsReload =
                (profile.isLoading && profile.servers.isEmpty()) ||
                profile.servers.isEmpty() ||
                (hasRemotePlaceholder && profile.rawConfig.isNullOrBlank() && !hasCachedTemplateConfig)

            if (needsReload) {
                Logger.d("MainViewModel", "Подписка ${profile.url} требует обновления при запуске")
                loadSubscription(profile.url)
            }
        }
    }

    private fun checkAndAutoUpdateSubscriptions() {
        if (!preferencesManager.subscriptionAutoUpdate) return

        val profiles = _profilesState.value
        val currentTime = System.currentTimeMillis()

        profiles.forEach { profile ->
            val lastUpdate = preferencesManager.getLastSubscriptionUpdateTime(profile.url)
            val intervalHours = preferencesManager.getSubscriptionUpdateInterval(profile.url)
                ?: preferencesManager.subscriptionUpdateInterval

            val intervalMillis = intervalHours * 60L * 60L * 1000L

            if (currentTime - lastUpdate >= intervalMillis) {
                Log.d("MainViewModel", "Auto-updating scheduled subscription on launch")
                refreshSubscription(profile.url)
            }
        }
    }

    // Парсит акцентный цвет из заголовка nimbo-theme: "<filter>,<accentHex>,<orb1>,<orb2>,<blur>".
    private fun parseSubscriptionAccentColor(spec: String?): Int? {
        val parts = spec?.split(",")?.map { it.trim() } ?: return null
        val hex = parts.firstOrNull { it.startsWith("#") } ?: return null
        return runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
    }

    // Применяет акцент из подписки через механизм кастомного акцента (единичный цвет).
    fun applySubscriptionAccent(spec: String?) {
        val accent = parseSubscriptionAccentColor(spec) ?: return
        preferencesManager.useDynamicColor = false
        preferencesManager.isCustomAccent = true
        preferencesManager.customGradientColor1 = accent
        preferencesManager.customGradientCount = 1
    }

    fun activeSubscriptionThemeSpec(): String? =
        _profilesState.value.firstNotNullOfOrNull { profile ->
            profile.themeSpec?.takeIf { it.isNotBlank() }
        }

    fun addSubscription(url: String) {
        Log.d("MainViewModel", "=== addSubscription ===")
        Log.d("MainViewModel", "Loading subscription")
        Log.d("MainViewModel", "Current profiles count: ${_profilesState.value.size}")

        if (_profilesState.value.any { it.url.equals(url, ignoreCase = true) }) {
            showTopNotification(
                "Такая подписка уже существует",
                com.danila.nimbo.ui.components.NotificationType.ERROR
            )
            return
        }

        val newProfile = SubscriptionProfile(
            url = url,
            name = "Загрузка...",
            servers = emptyList(),
            isLoading = true
        )

        _profilesState.value = _profilesState.value + newProfile
        Log.d("MainViewModel", "Added profile to state, new count: ${_profilesState.value.size}")

        // «Показывать на главной» включено по умолчанию: закрепляем новую подписку,
        // пока есть свободные слоты (максимум 3 закреплённых профиля).
        if (preferencesManager.getPinnedProfileUrls().size < 3) {
            preferencesManager.pinProfile(url)
        }

        // Сохраняем сразу после добавления
        preferencesManager.saveProfiles(_profilesState.value)
        Log.d("MainViewModel", "Saved profiles after adding")
        Log.d("MainViewModel", "=== addSubscription (done) ===")

        loadSubscription(url, showAddResultNotification = true)
    }

    fun removeSubscription(url: String) {
        // Создаём новый список без удаляемого профиля
        val updatedProfiles = _profilesState.value.filter { it.url != url }

        // Обновляем состояние явно через MutableStateFlow
        _profilesState.value = updatedProfiles
        _serversState.value = updatedProfiles.flatMap { it.servers }

        // Сохраняем обновлённый список
        preferencesManager.saveProfiles(_profilesState.value)

        Log.d("MainViewModel", "Removed subscription, remaining: ${_profilesState.value.size}")
    }

    fun refreshSubscription(url: String) {
        if (subscriptionJobs[url]?.isActive == true) {
            showTopNotification("Подписка уже обновляется...", com.danila.nimbo.ui.components.NotificationType.UPDATE)
            return
        }

        showTopNotification("Обновление подписки...", com.danila.nimbo.ui.components.NotificationType.UPDATE)
        _isRefreshingSubscriptions.value = true
        val index = _profilesState.value.indexOfFirst { it.url == url }
        if (index != -1) {
            val profile = _profilesState.value[index]
            _profilesState.value = _profilesState.value.toMutableList().apply {
                set(index, profile.copy(isLoading = true, error = null))
            }
        }
        loadSubscription(url, isRefresh = true, forceNetwork = true)
    }

    fun refreshAllSubscriptions() {
        val urls = _profilesState.value.map { it.url }
        if (urls.isEmpty()) return

        showTopNotification("Обновление всех подписок...", com.danila.nimbo.ui.components.NotificationType.UPDATE)
        _isRefreshingSubscriptions.value = true
        val jobs = urls.map { url ->
            val index = _profilesState.value.indexOfFirst { it.url == url }
            if (index != -1) {
                val profile = _profilesState.value[index]
                _profilesState.value = _profilesState.value.toMutableList().apply {
                    set(index, profile.copy(isLoading = true, error = null))
                }
            }
            loadSubscription(url, isRefresh = false, forceNetwork = true)
        }

        viewModelScope.launch {
            jobs.joinAll()

            val refreshedProfiles = _profilesState.value.filter { it.url in urls }
            val failedCount = refreshedProfiles.count { !it.error.isNullOrBlank() }
            val updatedCount = refreshedProfiles.size - failedCount
            when {
                failedCount == 0 -> showTopNotification(
                    "Все подписки обновлены · $updatedCount",
                    com.danila.nimbo.ui.components.NotificationType.SUCCESS
                )
                updatedCount > 0 -> showTopNotification(
                    "Обновлено: $updatedCount · с ошибкой: $failedCount",
                    com.danila.nimbo.ui.components.NotificationType.ERROR
                )
                else -> showTopNotification(
                    "Не удалось обновить подписки",
                    com.danila.nimbo.ui.components.NotificationType.ERROR
                )
            }

            if (updatedCount > 0 && VpnManager.state.value == VpnState.DISCONNECTED && preferencesManager.pingOnUpdate) {
                pingAllServers(silent = true)
            } else if (preferencesManager.pingOnUpdate) {
                Log.d("MainViewModel", "Skip auto ping after refresh: VPN is not disconnected or refresh failed")
            }
        }
    }

    private fun loadSubscription(
        url: String,
        isRefresh: Boolean = false,
        showAddResultNotification: Boolean = false,
        showRefreshResultNotification: Boolean = isRefresh,
        allowPostRefreshPing: Boolean = isRefresh,
        forceNetwork: Boolean = false
    ): Job {
        subscriptionJobs[url]?.let { existing ->
            if (existing.isActive) return existing
            subscriptionJobs.remove(url, existing)
        }

        val job = viewModelScope.launch {
            try {
                val index = _profilesState.value.indexOfFirst { it.url == url }
                val oldProfile = if (index != -1) _profilesState.value[index] else null

                val result = withContext(Dispatchers.IO) {
                    SubscriptionManager.load(url, forceRefresh = forceNetwork || isRefresh)
                }
                val remoteTemplates = emptyList<RemnawaveSubscriptionTemplate>()

                val hostDescriptionsByKey = emptyMap<String, String>()
                val hostDescriptionsByHostPort = hostDescriptionsByKey.entries
                    .mapNotNull { entry ->
                        val parts = entry.key.split("|")
                        if (parts.size == 3 && !entry.key.startsWith("template", ignoreCase = true)) {
                            val host = parts[1]
                            val port = parts[2].toIntOrNull() ?: return@mapNotNull null
                            hostPortKey(host, port) to entry.value
                        } else {
                            null
                        }
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapNotNull { (key, descriptions) ->
                        // Несколько host'ов Remnawave могут иметь один и тот же host:port
                        // (например plain VLESS и XHTTP на 443). В таком случае host:port
                        // не является безопасным ключом и не должен перетирать описание.
                        val uniqueDescriptions = descriptions.distinct()
                        if (uniqueDescriptions.size == 1) key to uniqueDescriptions.first() else null
                    }
                    .toMap()
                val hostDescriptionsByUuid = hostDescriptionsByKey.entries
                    .mapNotNull { entry ->
                        val parts = entry.key.split("|")
                        if (parts.size == 2 && parts[0].equals("uuid", ignoreCase = true)) {
                            val uuid = parts[1].trim().lowercase()
                            if (uuid.isNotBlank()) uuid to entry.value else null
                        } else {
                            null
                        }
                    }
                    .toMap()
                val hostDescriptionsByName = hostDescriptionsByKey.entries
                    .mapNotNull { entry ->
                        if (entry.key.startsWith("name|", ignoreCase = true)) {
                            val nameKey = entry.key.substringAfter("|").trim()
                            if (nameKey.isNotBlank()) nameKey to entry.value else null
                        } else {
                            null
                        }
                    }
                    .toMap()
                val oldDescriptionByPingKey = oldProfile?.servers
                    ?.associate { it.pingKey() to it.serverDescription }
                    ?: emptyMap()
                val oldDescriptionByHostPort = oldProfile?.servers
                    ?.mapNotNull { server ->
                        val desc = server.serverDescription
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        if (desc != null) {
                            hostPortKey(server.host, server.port) to desc
                        } else {
                            null
                        }
                    }
                    ?.groupBy({ it.first }, { it.second })
                    ?.mapNotNull { (key, descriptions) ->
                        val uniqueDescriptions = descriptions.distinctBy { it.lowercase() }
                        if (uniqueDescriptions.size == 1) key to uniqueDescriptions.first() else null
                    }
                    ?.toMap()
                    ?: emptyMap()

                // Сохраняем URL подписки для XrayManager
                try {
                    getApplication<Application>().getSharedPreferences("nebulaguard_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_subscription_url", url)
                        .apply()
                    Log.d("MainViewModel", "Saved last subscription reference")
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Failed to save subscription URL", e)
                }

                // Сохраняем время обновления и интервал
                preferencesManager.setLastSubscriptionUpdateTime(url, System.currentTimeMillis())
                preferencesManager.setSubscriptionUpdateInterval(url, result.autoUpdateInterval)

                val fallbackTemplates = oldProfile?.templates.orEmpty().map {
                    RemnawaveSubscriptionTemplate(
                        uuid = it.uuid,
                        name = it.name,
                        templateType = it.templateType,
                        viewPosition = it.viewPosition,
                        isDefault = it.isDefault,
                        config = it.config,
                        fetchedAtMs = it.fetchedAtMs
                    )
                }
                val knownTemplates = if (remoteTemplates.isNotEmpty()) remoteTemplates else fallbackTemplates
                val hasPanelXrayTemplates = knownTemplates.any {
                    isXrayJsonTemplateType(it.templateType)
                }
                val defaultPanelXrayTemplate = knownTemplates
                    .filter { isXrayJsonTemplateType(it.templateType) }
                    .sortedBy { it.viewPosition }
                    .firstOrNull { it.isDefault || it.name.equals("Default", ignoreCase = true) }
                    ?: knownTemplates
                        .filter { isXrayJsonTemplateType(it.templateType) }
                        .sortedBy { it.viewPosition }
                        .firstOrNull()
                val templateNameByUuid = knownTemplates
                    .filter { it.uuid.isNotBlank() && it.name.isNotBlank() }
                    .associateBy({ it.uuid.trim().lowercase() }, { it.name.trim() })
                val hostTemplateUuidByHostUuid = hostDescriptionsByKey.entries
                    .mapNotNull { entry ->
                        if (entry.key.startsWith("templateuuid|", ignoreCase = true)) {
                            val hostUuid = entry.key.substringAfter("|").trim().lowercase()
                            val uuid = resolveTemplateUuidValue(entry.value, defaultPanelXrayTemplate)
                            if (hostUuid.isNotBlank() && !uuid.isNullOrBlank()) hostUuid to uuid else null
                        } else {
                            null
                        }
                    }
                    .toMap()
                val hostTemplateNameByHostUuid = hostDescriptionsByKey.entries
                    .mapNotNull { entry ->
                        if (entry.key.startsWith("templatename|", ignoreCase = true)) {
                            val hostUuid = entry.key.substringAfter("|").trim().lowercase()
                            if (hostUuid.isNotBlank() && entry.value.isNotBlank()) hostUuid to entry.value else null
                        } else {
                            null
                        }
                    }
                    .toMap()
                val hostTemplateUuidByName = uniqueTemplateMapByLookupKey(
                    hostDescriptionsByKey.entries,
                    "templateuuidname|",
                    defaultPanelXrayTemplate
                )
                val hostTemplateUuidByHostPort = uniqueTemplateMapByLookupKey(
                    hostDescriptionsByKey.entries,
                    "templateuuidhostport|",
                    defaultPanelXrayTemplate
                )
                val hostOrderByHostUuid = hostDescriptionsByKey.entries
                    .mapNotNull { entry ->
                        if (entry.key.startsWith("viewposition|", ignoreCase = true)) {
                            val uuid = entry.key.substringAfter("|").trim().lowercase()
                            val position = entry.value.trim().toIntOrNull()
                            if (uuid.isNotBlank() && position != null) uuid to position else null
                        } else {
                            null
                        }
                    }
                    .toMap()
                val hostOrderByName = uniqueIntMapByLookupKey(
                    hostDescriptionsByKey.entries,
                    "viewpositionname|"
                )
                val hostOrderByHostPort = uniqueIntMapByLookupKey(
                    hostDescriptionsByKey.entries,
                    "viewpositionhostport|"
                )

                val hasDirectSubscriptionServers = result.servers.any { it.isNotBlank() }

                // Парсим ссылки и/или используем готовые серверные XRAY_JSON шаблоны (включая автобалансеры).
                // Мы хотим показывать шаблоны ВСЕГДА, когда они есть в панели, даже если есть и direct servers,
                // чтобы пользователь мог выбрать нужный шаблон (например, автобалансер или обход).
                val parsedFromResponse = mutableListOf<Server>()

                // Ссылки из аварийного пула (заголовок nimbo-fallback) — помечаем такие
                // серверы isFallback, чтобы они уходили в backup-группу балансера.
                val fallbackLinkSet = result.fallbackServers.map { it.trim() }.toHashSet()

                // 1. Сначала парсим direct servers, если они присутствуют
                if (hasDirectSubscriptionServers) {
                    data class ParsedDirectServer(
                        val server: Server,
                        val sourceIndex: Int,
                        val viewPosition: Int?
                    )

                    val parsedDirectItems = result.servers
                        .asSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .withIndex()
                        .mapNotNull { indexedLine ->
                            val line = indexedLine.value
                            try {
                                val parsed = LinkParser.parse(line)
                                    .copy(profileUrl = url, isFallback = line in fallbackLinkSet)
                                val hostUuidFromLink = runCatching {
                                    android.net.Uri.parse(line).getQueryParameter("hostUuid")
                                }.getOrNull()?.trim()?.lowercase()
                                // Panel metadata is only a fallback; if the subscription
                                // line carries a custom description, keep it as source of truth.
                                val hostDescription = (hostUuidFromLink?.let { hostDescriptionsByUuid[it] })
                                    ?: hostDescriptionsByName[hostNameKey(parsed.name)]
                                    ?: hostDescriptionsByHostPort[hostPortKey(parsed.host, parsed.port)]
                                val hostTemplateUuid = hostUuidFromLink?.let { hostTemplateUuidByHostUuid[it] }
                                    ?: hostTemplateUuidByName[hostNameKey(parsed.name)]
                                    ?: hostTemplateUuidByHostPort[hostPortKey(parsed.host, parsed.port)]
                                val hostTemplateName = hostUuidFromLink?.let { hostTemplateNameByHostUuid[it] }
                                val effectiveTemplateUuid = hostTemplateUuid
                                    ?: parsed.templateUuid
                                    ?: defaultPanelXrayTemplate?.uuid
                                val effectiveTemplateName = hostTemplateName
                                    ?: effectiveTemplateUuid
                                        ?.trim()
                                        ?.lowercase()
                                        ?.let { templateNameByUuid[it] }
                                    ?: parsed.templateName
                                    ?: defaultPanelXrayTemplate?.name?.takeIf { it.isNotBlank() }
                                val oldDescription = oldDescriptionByPingKey[parsed.pingKey()]
                                    ?: oldDescriptionByHostPort[hostPortKey(parsed.host, parsed.port)]
                                val preferredDescription = parsed.serverDescription
                                    ?: oldDescription
                                    ?: hostDescription
                                val enrichedDescription = sanitizeServerDescriptionForServer(
                                    parsed,
                                    preferredDescription
                                )
                                val server = parsed.copy(
                                    serverDescription = enrichedDescription ?: parsed.serverDescription ?: oldDescription,
                                    templateUuid = effectiveTemplateUuid,
                                    templateName = effectiveTemplateName
                                )
                                val namedServer = if (SubscriptionManager.isGenericServerName(server.name)) {
                                    val inferredName = genericServerNameCandidate(
                                        server,
                                        enrichedDescription ?: preferredDescription
                                    )
                                    if (inferredName.isNullOrBlank()) {
                                        server
                                    } else {
                                        Log.d(
                                            "MainViewModel",
                                            "Named generic server (${server.protocol} @ ${server.host}) as '$inferredName'"
                                        )
                                        server.copy(name = inferredName)
                                    }
                                } else {
                                    server
                                }
                                val viewPosition = hostUuidFromLink?.let { hostOrderByHostUuid[it] }
                                    ?: hostOrderByName[hostNameKey(parsed.name)]
                                    ?: hostOrderByHostPort[hostPortKey(parsed.host, parsed.port)]
                                Log.d("MainViewModel", "Parsed server: ${namedServer.name}, protocol=${namedServer.protocol}, security=${namedServer.security}, hasPbk=${!namedServer.publicKey.isNullOrBlank()}")
                                ParsedDirectServer(namedServer, indexedLine.index, viewPosition)
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Parse error for line: $line", e)
                                null
                            }
                        }.toList()

                    val parsedDirect = if (parsedDirectItems.any { it.viewPosition != null }) {
                        parsedDirectItems
                            .sortedWith(
                                compareBy<ParsedDirectServer> { it.viewPosition ?: Int.MAX_VALUE }
                                    .thenBy { it.sourceIndex }
                            )
                            .map { it.server }
                    } else {
                        parsedDirectItems.map { it.server }
                    }

                    // Hysteria-серверы часто приходят без имени (прямая ссылка без
                    // #fragment, напр. hysteria2://key@pl.host:443/?sni=...) или с
                    // техническим тегом ("proxy"/"proxy-1") из JSON-конфига. Реального
                    // названия страны в самой ссылке нет, а API панели в этой сборке не
                    // настроен. Берём имя у соседнего сервера на ТОМ ЖЕ хосте (например,
                    // VLESS «Польша» на pl.whyk1lled.fun) — это та же нода, другой инбаунд.
                    val realNameByHost = parsedDirect
                        .asSequence()
                        .filter { it.host.isNotBlank() && !SubscriptionManager.isGenericServerName(it.name) }
                        .groupBy { it.host.trim().lowercase() }
                        .mapNotNull { (host, group) ->
                            // Берём общий локационный сегмент (страну) соседних серверов на
                            // хосте. Полные имена различаются хвостом транспорта
                            // ("· Shadowsocks" vs "· Trojan"), но страна у них одна.
                            group.map { locationSegment(it.name) }
                                .filter { it.isNotBlank() && !SubscriptionManager.isGenericServerName(it) }
                                .distinct()
                                .singleOrNull()
                                ?.let { host to it }
                        }
                        .toMap()

                    val namedDirect = if (realNameByHost.isEmpty()) {
                        parsedDirect
                    } else {
                        parsedDirect.map { server ->
                            if (!SubscriptionManager.isGenericServerName(server.name)) return@map server
                            val borrowed = realNameByHost[server.host.trim().lowercase()]
                            if (borrowed.isNullOrBlank()) {
                                server
                            } else {
                                // Помечаем протоколом, чтобы заимствованное имя не сливалось
                                // с соседним VLESS на том же хосте: «🇩🇪 Германия | HYSTERIA».
                                val marker = when {
                                    server.protocol.contains("hysteria", ignoreCase = true) ||
                                        server.protocol.equals("hy2", ignoreCase = true) -> "HYSTERIA"
                                    else -> server.protocol.trim().uppercase()
                                }
                                val newName = if (marker.isBlank()) borrowed else "$borrowed | $marker"
                                if (newName == server.name) {
                                    server
                                } else {
                                    Log.d(
                                        "MainViewModel",
                                        "Named generic server (${server.protocol} @ ${server.host}) as '$newName' from co-located server"
                                    )
                                    server.copy(name = newName)
                                }
                            }
                        }
                    }
                    parsedFromResponse.addAll(namedDirect)
                }

                // 2. Добавляем серверные XRAY_JSON шаблоны как selectable "серверы"
                val xrayTemplates = knownTemplates.filter { isXrayJsonTemplateType(it.templateType) }
                if (xrayTemplates.isNotEmpty()) {
                    val templateServers = xrayTemplates.map { template ->
                        val templateConfig = template.config ?: result.rawConfig
                        val detectedProtocol = SubscriptionManager.detectPrimaryProxyProtocolFromJsonConfig(templateConfig)
                            ?: "xray"
                        val pingTarget = extractPingTargetFromXrayJson(templateConfig)
                        val templateDisplayName = template.name
                            .takeIf { it.isNotBlank() && !SubscriptionManager.isGenericServerName(it) }
                            ?: result.username
                            ?: "Remote Config"
                        Server(
                            name = "🌐 $templateDisplayName",
                            host = "API",
                            port = 0,
                            uuid = "remote",
                            protocol = detectedProtocol,
                            serverDescription = template.name.takeIf { it.isNotBlank() && !SubscriptionManager.isGenericServerName(it) },
                            profileUrl = url,
                            templateUuid = template.uuid,
                            templateName = template.name.takeIf { it.isNotBlank() },
                            pingHost = pingTarget?.host,
                            pingPort = pingTarget?.port
                        )
                    }
                    parsedFromResponse.addAll(templateServers)
                } else if (!hasDirectSubscriptionServers) {
                    // Фолбэк: если шаблонов нет и direct серверов тоже нет, используем дефолтный /json.
                    val detectedProtocol = SubscriptionManager.detectPrimaryProxyProtocolFromJsonConfig(result.rawConfig)
                        ?: result.configType
                        ?: "xray"
                    val pingTarget = extractPingTargetFromXrayJson(result.rawConfig)
                    parsedFromResponse.add(
                        Server(
                            name = "🌐 ${result.username ?: "Remote Config"}",
                            host = "API",
                            port = 0,
                            uuid = "remote",
                            protocol = detectedProtocol,
                            profileUrl = url,
                            pingHost = pingTarget?.host,
                            pingPort = pingTarget?.port
                        )
                    )
                }
                val parsed = if (parsedFromResponse.isNotEmpty()) {
                    parsedFromResponse
                } else {
                    // Не затираем рабочий список серверов, если пришел пустой/битый ответ от API
                    oldProfile?.servers ?: emptyList()
                }
                Log.d("MainViewModel", "Total parsed servers: ${parsed.size} (response=${parsedFromResponse.size}, fallback=${oldProfile?.servers?.size ?: 0})")

                // Имя профиля - username из подписки (Remnawave передаёт в profile_title)
                val profileName = result.username ?: "Подписка"
                Log.d("MainViewModel", "Profile name: $profileName")
                Log.d("MainViewModel", "result.username from SubscriptionManager: ${result.username}")
                Log.d("MainViewModel", "result.announce from SubscriptionManager: ${result.announce}")

                val currentIndex = _profilesState.value.indexOfFirst { it.url == url }
                if (currentIndex != -1) {
                    val existingProfile = _profilesState.value[currentIndex]

                    // Сохраняем кэшированный пинг из старых серверов по стабильному ключу.
                    val oldServerPings = existingProfile.servers.associateBy { it.pingKey() }

                    val updatedServers = parsed.map { server ->
                        val uniqueKey = server.pingKey()
                        val oldServer = oldServerPings[uniqueKey]
                        // Сохраняем последний известный пинг до следующей проверки.
                        if (oldServer?.ping != null && oldServer.ping >= 0) {
                            server.copy(ping = oldServer.ping, pingTimestamp = oldServer.pingTimestamp)
                        } else {
                            server
                        }
                    }

                    val effectiveExpireTime = result.expireTime

                    // Вычисляем дни до истечения
                    val adjustedDaysUntilExpiry = if (effectiveExpireTime > 0) {
                        val now = System.currentTimeMillis() / 1000
                        (effectiveExpireTime - now) / (24 * 60 * 60)
                    } else {
                        -1L
                    }

                    // Имя профиля - username из подписки (Remnawave передаёт в profile_title)
                    val profileName = result.username ?: "Подписка"

                    // Сохраняем оригинальное имя, если оно пришло новым
                    val originalName = if (profileName != "Подписка") profileName else existingProfile.originalName
                    val existingTemplatesByUuid = existingProfile.templates
                        .associateBy { it.uuid.trim().lowercase() }
                    val mergedTemplates = if (remoteTemplates.isNotEmpty()) {
                        remoteTemplates.map {
                            val previous = existingTemplatesByUuid[it.uuid.trim().lowercase()]
                            SubscriptionTemplateCache(
                                uuid = it.uuid,
                                name = it.name,
                                templateType = it.templateType,
                                viewPosition = it.viewPosition,
                                isDefault = it.isDefault,
                                config = it.config ?: previous?.config,
                                fetchedAtMs = it.fetchedAtMs ?: previous?.fetchedAtMs
                            )
                        }
                    } else {
                        existingProfile.templates
                    }
                    val renderedTemplateConfig = mergedTemplates
                        .firstOrNull { isXrayJsonTemplateType(it.templateType) && !it.config.isNullOrBlank() }
                        ?.config
                    val supportsRemoteJson = result.supportsJsonResponse == true || hasPanelXrayTemplates || renderedTemplateConfig != null
                    val updatedBrandLogo = result.brandLogo ?: existingProfile.brandLogo
                    val updatedBrandLogoCache = SubscriptionLogoCache.prepareCachedLogo(
                        logo = updatedBrandLogo,
                        previousLogo = existingProfile.brandLogo,
                        previousCache = existingProfile.brandLogoCache
                    )
                    val updatedThemeSpec = result.themeSpec ?: existingProfile.themeSpec

                    val updated = existingProfile.copy(
                        servers = updatedServers,
                        isLoading = false,
                        name = profileName,
                        username = result.username,
                        uploadTotal = result.uploadTotal,
                        downloadTotal = result.downloadTotal,
                        totalTraffic = result.totalTraffic,
                        expireTime = effectiveExpireTime,
                        deviceCount = result.deviceLimit, // Используем deviceLimit как основной счетчик
                        deviceLimit = result.deviceLimit,
                        onlineDevices = result.onlineDevices,
                        announce = result.announce,
                        daysUntilExpiry = adjustedDaysUntilExpiry,
                        websiteUrl = result.websiteUrl,
                        supportUrl = result.supportUrl,
                        brandLogo = updatedBrandLogo,
                        brandLogoCache = updatedBrandLogoCache,
                        themeSpec = updatedThemeSpec,
                        bonusDaysApplied = false,
                        autoUpdateInterval = result.autoUpdateInterval,
                        originalName = originalName,
                        accountId = result.accountId,
                        numericId = result.numericId,
                        telegramId = result.telegramId,
                        supportsJsonResponse = result.supportsJsonResponse ?: if (supportsRemoteJson) true else existingProfile.supportsJsonResponse,
                        // Никогда не теряем рабочий сырой конфиг из-за временного "пустого" ответа.
                        rawConfig = result.rawConfig ?: existingProfile.rawConfig,
                        configType = result.configType ?: if (supportsRemoteJson) "xray" else existingProfile.configType,
                        templates = mergedTemplates
                    )
                    _profilesState.value = _profilesState.value.toMutableList().apply {
                        set(currentIndex, updated)
                    }

                    // Тема из подписки (nimbo-theme): запоминаем спецификацию и применяем
                    // акцент, если пользователь включил «Тема из подписки».
                    updated.themeSpec?.takeIf { it.isNotBlank() }?.let {
                        preferencesManager.subscriptionThemeSpec = it
                    }
                    if (preferencesManager.useSubscriptionTheme) {
                        applySubscriptionAccent(updated.themeSpec)
                    }

                    // Обновляем общий список серверов
                    _serversState.value = _profilesState.value.flatMap { it.servers }

                    val totalServersCount = _profilesState.value.sumOf { it.servers.size }
                    Log.d("MainViewModel", "Loaded subscription $profileName: ${parsed.size} servers (total: $totalServersCount)")

                    // Сохраняем URL из подписки в PreferencesManager (для кнопок в настройках)
                    result.websiteUrl?.let { preferencesManager.websiteUrl = it }
                    result.supportUrl?.let { preferencesManager.supportUrl = it }

                    // Сохраняем интервал авто-обновления из подписки (Remnawave)
                    result.autoUpdateInterval?.let { preferencesManager.setSubscriptionUpdateInterval(url, it) }

                    // Сохраняем обновленный список профилей
                    preferencesManager.saveProfiles(_profilesState.value)

                    if (showAddResultNotification) {
                        showTopNotification(
                            "Подписка успешно добавлена",
                            com.danila.nimbo.ui.components.NotificationType.SUCCESS
                        )
                    }

                    if (showRefreshResultNotification) {
                        showTopNotification(
                            loc(
                                "${updated.name} обновлена · ${serverCountRu(updated.servers.size)}",
                                "${updated.name} updated · ${serverCountEn(updated.servers.size)}"
                            ),
                            com.danila.nimbo.ui.components.NotificationType.SUCCESS
                        )
                    }

                    if (
                        allowPostRefreshPing &&
                        preferencesManager.pingOnUpdate &&
                        VpnManager.state.value == VpnState.DISCONNECTED
                    ) {
                        pingAllServers(silent = true)
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Subscription error", e)
                val safeError = sanitizeSubscriptionError(e.message)
                updateProfileError(url, safeError)
                if (showAddResultNotification) {
                    val reason = safeError.take(120).ifBlank { "неизвестная ошибка" }
                    showTopNotification(
                        "Не удалось добавить подписку: $reason",
                        com.danila.nimbo.ui.components.NotificationType.ERROR
                    )
                }
                if (showRefreshResultNotification) {
                    showTopNotification(
                        "Не удалось обновить подписку: ${safeError.take(120)}",
                        com.danila.nimbo.ui.components.NotificationType.ERROR
                    )
                }
            } finally {
                coroutineContext[Job]?.let { currentJob ->
                    subscriptionJobs.remove(url, currentJob)
                }
                // Проверяем, остались ли еще загружающиеся профили
                val stillLoading = _profilesState.value.any { it.isLoading }
                if (!stillLoading) {
                    _isRefreshingSubscriptions.value = false
                }
            }
        }
        subscriptionJobs[url] = job
        return job
    }

    private fun sanitizeSubscriptionError(rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) return "Не удалось подключиться к серверу подписки"
        val message = rawMessage.trim()
        val lower = message.lowercase()

        return when {
            lower.contains("timed out") || lower.contains("timeout") ->
                "Таймаут подключения к серверу подписки"

            lower.contains("unable to resolve host") || lower.contains("unknownhost") || lower.contains("nodename") ->
                "Не удалось определить адрес сервера подписки"

            lower.contains("ssl") || lower.contains("handshake") || lower.contains("certificate") ->
                "Ошибка защищенного соединения с сервером подписки"

            lower.contains("failed to connect") || lower.contains("connect to") || lower.contains("connection refused") ->
                "Не удалось подключиться к серверу подписки"

            else -> {
                val redacted = message
                    .replace(Regex("""https?://\S+"""), "[url]")
                    .replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b"""), "[ip]")
                    .replace(Regex("""(?i)\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b"""), "[host]")
                    .replace(Regex("""(?i)\bport\s*\d+\b"""), "порт")
                    .take(120)
                "Ошибка подключения: $redacted"
            }
        }
    }

    private fun updateProfileError(url: String, error: String) {
        val index = _profilesState.value.indexOfFirst { it.url == url }
        if (index != -1) {
            val updated = _profilesState.value[index].copy(
                isLoading = false,
                error = error
            )
            _profilesState.value = _profilesState.value.toMutableList().apply {
                set(index, updated)
            }
            _serversState.value = _profilesState.value.flatMap { it.servers }
            preferencesManager.saveProfiles(_profilesState.value)
        }
    }

    private fun extractProfileNameFromUrl(url: String): String {
        return try {
            val host = URL(url).host ?: "Подписка"
            host.replace("www.", "").split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Подписка"
        } catch (e: Exception) {
            "Подписка"
        }
    }

    private fun extractNameFromServers(servers: List<Server>): String? {
        if (servers.isEmpty()) return null
        return servers.first().name.takeIf { it.isNotBlank() && it != "Server" }
    }

    fun renameProfile(url: String, newName: String) {
        val index = _profilesState.value.indexOfFirst { it.url == url }
        if (index != -1) {
            val profile = _profilesState.value[index]
            val updated = profile.copy(
                customName = if (newName.isBlank()) null else newName,
                originalName = profile.originalName ?: profile.name
            )
            _profilesState.value = _profilesState.value.toMutableList().apply {
                set(index, updated)
            }
            preferencesManager.saveProfiles(_profilesState.value)
        }
    }

    /**
     * Получение информации о профиле для отправки в Telegram
     */
    fun getProfileInfo(url: String): com.danila.nimbo.network.SubscriptionInfo? {
        val profile = _profilesState.value.find { it.url == url } ?: return null

        return com.danila.nimbo.network.SubscriptionInfo(
            username = profile.username,
            uploadTotal = profile.uploadTotal,
            downloadTotal = profile.downloadTotal,
            totalTraffic = profile.totalTraffic,
            expireTime = profile.expireTime,
            deviceCount = profile.deviceLimit,
            deviceLimit = profile.deviceLimit,
            onlineDevices = profile.onlineDevices,
            announce = profile.announce,
            daysUntilExpiry = profile.daysUntilExpiry
        )
    }

    /**
     * Загрузка списка устройств для конкретной подписки
     */
    fun loadDevices(subscriptionUrl: String) {
        _isRefreshingDevices.value = false
        _devicesState.value = emptyList()
    }

    /**
     * Переименование устройства
     */
    fun renameDevice(subscriptionUrl: String, deviceId: String, hwid: String?, newName: String) {
        // 1. Сохраняем локально сразу
        preferencesManager.saveCustomDeviceName(deviceId, newName)

        // Если это текущее устройство, также обновляем глобальное имя для заголовков
        val currentHwid = com.danila.nimbo.utils.AppVersionManager.getHWID(getApplication())
        if (hwid == currentHwid) {
            preferencesManager.customDeviceName = newName
            // Сразу перезагружаем информацию о подписке чтобы обновить имя в UI
            loadSubscription(
                subscriptionUrl,
                isRefresh = true,
                showRefreshResultNotification = false,
                allowPostRefreshPing = false
            )
        }

        // Обновляем состояние UI немедленно
        _devicesState.value = _devicesState.value.map {
            if (it.id == deviceId) it.copy(name = newName) else it
        }

        // 2. Уведомляем об успехе
        showTopNotification("Устройство переименовано", com.danila.nimbo.ui.components.NotificationType.SUCCESS)

    }

    /**
     * Удаление устройства
     */
    fun deleteDevice(subscriptionUrl: String, device: com.danila.nimbo.network.RemnawaveDevice) {
        showTopNotification("Управление устройствами через панель отключено", com.danila.nimbo.ui.components.NotificationType.ERROR)
    }

    /**
     * Пакетное удаление нескольких устройств
     */
    fun deleteDevices(subscriptionUrl: String, devices: List<com.danila.nimbo.network.RemnawaveDevice>) {
        _isRefreshingDevices.value = false
        showTopNotification("Управление устройствами через панель отключено", com.danila.nimbo.ui.components.NotificationType.ERROR)
    }

    /**
     * Изменение порядка устройств (локально)
     */
    fun reorderDevices(from: Int, to: Int) {
        val current = _devicesState.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return

        val item = current.removeAt(from)
        current.add(to, item)

        _devicesState.value = current

        // Сохраняем новый порядок ID
        preferencesManager.saveDeviceOrder(current.map { it.id })
    }
}
