@file:Suppress("FunctionName", "unused")

package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.danila.nimbo.shared.subscription.NormalizedSubscription
import com.danila.nimbo.shared.routing.NimboModule
import com.danila.nimbo.shared.routing.NimboModuleParser
import com.danila.nimbo.shared.routing.NimboBuiltinRoutingProfiles
import com.danila.nimbo.shared.routing.NimboRoutingProfile
import com.danila.nimbo.shared.routing.NimboRoutingProfileRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.encodeToString
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

private const val ToggleVpnAction = "com.nimbo.action.toggle-vpn"
private const val AddProfileAction = "com.nimbo.action.add-profile"
private const val RefreshProfileAction = "com.nimbo.action.refresh-profile"
private const val ProfileSettingsAction = "com.nimbo.action.profile-settings"
private const val SaveAppRuleAction = "com.nimbo.action.save-app-rule"
private const val DiagnosticsAction = "com.nimbo.action.diagnostics"
private const val AboutAction = "com.nimbo.action.about"
private const val SystemSettingsAction = "com.nimbo.action.system-settings"
private const val SelectServerAction = "com.nimbo.action.select-server"
private const val OpenUrlAction = "com.nimbo.action.open-url"
private const val RoutingAction = "com.nimbo.action.routing"
private const val OpenScreenAction = "com.nimbo.action.open-screen"
private const val OpenUpdateAction = "com.nimbo.action.open-update"
private const val ExportBackupAction = "com.nimbo.action.export-backup"
private const val ImportBackupAction = "com.nimbo.action.import-backup"
private const val OpenSyncAction = "com.nimbo.action.open-sync"
private const val PingServerAction = "com.nimbo.action.ping-server"
private const val PingAllAction = "com.nimbo.action.ping-all"
private const val ConnectFastestAction = "com.nimbo.action.connect-fastest"
private const val CopyTextAction = "com.nimbo.action.copy-text"
private const val ExportModuleAction = "com.nimbo.action.export-module"
private const val ImportSubscriptionAction = "com.nimbo.action.import-subscription"
private const val ImportClipboardAction = "com.nimbo.action.import-clipboard"
private const val ImportFileAction = "com.nimbo.action.import-file"
private const val ScanQrAction = "com.nimbo.action.scan-qr"
private const val AppearanceChangedAction = "com.nimbo.action.appearance-changed"

/** Оформление хранится там же, где настройки маршрутизации. */
private const val AppearanceDefaultsPrefix = "com.nimbo.appearance."

private fun appearanceInt(key: String, default: Int): Int {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.objectForKey(AppearanceDefaultsPrefix + key) == null) return default
    return defaults.integerForKey(AppearanceDefaultsPrefix + key).toInt()
}

private fun appearanceFlag(key: String, default: Boolean): Boolean {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.objectForKey(AppearanceDefaultsPrefix + key) == null) return default
    return defaults.boolForKey(AppearanceDefaultsPrefix + key)
}

private fun appearanceText(key: String, default: String): String =
    NSUserDefaults.standardUserDefaults.stringForKey(AppearanceDefaultsPrefix + key) ?: default

/** Сведения о доступном обновлении приносит Swift: в сеть ходит он. */
fun NimboUpdateIosRelease(version: String, notes: String) {
    iosUiState.value = iosUiState.value.copy(updateVersion = version, updateNotes = notes)
}

private fun applyAppearanceChange(key: String, value: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    when (key) {
        "backgroundStyle", "backgroundPalette" ->
            defaults.setInteger(value.toLongOrNull() ?: 0L, AppearanceDefaultsPrefix + key)
        "elementStyle", "serverSort", "connectStyle" ->
            defaults.setObject(value, AppearanceDefaultsPrefix + key)
        else -> defaults.setBool(value == "true", AppearanceDefaultsPrefix + key)
    }
    iosUiState.value = iosUiState.value.copy(
        backgroundStyle = appearanceInt("backgroundStyle", 0),
        backgroundPalette = appearanceInt("backgroundPalette", 0),
        backgroundMotion = appearanceFlag("backgroundMotion", true),
        navIconMotion = appearanceFlag("navIconMotion", true),
        showSpeedWidget = appearanceFlag("showSpeedWidget", true),
        showMemoryWidget = appearanceFlag("showMemoryWidget", true),
        elementStyle = appearanceText("elementStyle", "glass"),
        serverSort = appearanceText("serverSort", "subscription"),
        favoritesFirst = appearanceFlag("favoritesFirst", true),
        connectStyle = appearanceText("connectStyle", "classic"),
        statusParticles = appearanceFlag("statusParticles", true)
    )
    // Нативная нижняя панель живёт вне Compose. Сообщаем Swift о смене
    // оформления, чтобы Manga/Glass применялись сразу, без перезапуска экрана.
    postIosAction(AppearanceChangedAction, appearanceText("elementStyle", "glass"))
}

/**
 * Настройки замера задержки.
 *
 * Тот же приём, что и с оформлением: значения лежат в NSUserDefaults, поэтому
 * их читает и служба замера на стороне Swift — отдельный мост не нужен.
 */
private const val PingDefaultsPrefix = "com.nimbo.ping."
private const val DefaultPingUrl = "https://www.gstatic.com/generate_204"

private fun pingText(key: String, default: String): String =
    NSUserDefaults.standardUserDefaults.stringForKey(PingDefaultsPrefix + key) ?: default

private fun pingInt(key: String, default: Int): Int {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.objectForKey(PingDefaultsPrefix + key) == null) return default
    return defaults.integerForKey(PingDefaultsPrefix + key).toInt()
}

private fun applyPingChange(key: String, value: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    when (key) {
        "timeoutMs" -> defaults.setInteger(value.toLongOrNull() ?: 3000L, PingDefaultsPrefix + key)
        else -> defaults.setObject(value, PingDefaultsPrefix + key)
    }
    iosUiState.value = iosUiState.value.copy(
        pingProtocol = pingText("protocol", "tcp"),
        pingTimeoutMs = pingInt("timeoutMs", 3000),
        pingUrl = pingText("url", DefaultPingUrl)
    )
}

/**
 * Модули маршрутизации.
 *
 * Хранятся текстом в NSUserDefaults: оттуда же их читает сборка конфигурации,
 * поэтому туннелю не нужен отдельный мост, а набор переживает перезапуск.
 */
private const val ModulesDefaultsKey = "com.nimbo.routing.modules"

@kotlinx.serialization.Serializable
private data class StoredModule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val text: String
)

private fun loadModules(): List<NimboModule> {
    val raw = NSUserDefaults.standardUserDefaults.stringForKey(ModulesDefaultsKey) ?: return emptyList()
    return runCatching { iosJson.decodeFromString<List<StoredModule>>(raw) }
        .getOrDefault(emptyList())
        .map { NimboModule(id = it.id, name = it.name, enabled = it.enabled, text = it.text) }
}

private fun storeModules(modules: List<NimboModule>) {
    val payload = modules.map { StoredModule(it.id, it.name, it.enabled, it.text) }
    NSUserDefaults.standardUserDefaults.setObject(iosJson.encodeToString(payload), ModulesDefaultsKey)
    iosUiState.value = iosUiState.value.copy(modules = modules)
}

private fun saveModule(id: String, name: String, text: String) {
    val current = loadModules()
    val next = if (current.any { it.id == id }) {
        current.map { if (it.id == id) it.copy(name = name, text = text) else it }
    } else {
        current + NimboModule(id = id, name = name, enabled = true, text = text)
    }
    storeModules(next)
}

private fun toggleModule(id: String) {
    storeModules(loadModules().map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
}

private fun deleteModule(id: String) {
    storeModules(loadModules().filterNot { it.id == id })
}

/**
 * Правила включённых модулей в виде JSON для Xray.
 *
 * Swift берёт готовый массив и вставляет его в конфигурацию: разбор текста
 * обязан быть один на обе платформы, поэтому он остаётся здесь.
 */
fun NimboIosModuleRulesJson(): String {
    val rules = NimboModuleParser.rulesOf(loadModules()).mapNotNull { rule ->
        if (rule.domains.isEmpty() && rule.ips.isEmpty()) return@mapNotNull null
        buildJsonObject {
            put("type", JsonPrimitive("field"))
            if (rule.domains.isNotEmpty()) {
                put("domain", JsonArray(rule.domains.map { JsonPrimitive(it) }))
            }
            if (rule.ips.isNotEmpty()) {
                put("ip", JsonArray(rule.ips.map { JsonPrimitive(it) }))
            }
            put("outboundTag", JsonPrimitive(rule.policy.outboundTag))
        }
    }
    return iosJson.encodeToString(JsonArray(rules))
}

/**
 * Профили маршрутизации.
 *
 * Правки хранятся отдельно от встроенных наборов: обновление приложения
 * приносит новые списки доменов, и перезаписывать ими то, что человек написал
 * сам, нельзя. «Вернуть» просто убирает правки.
 */
private const val RoutingProfilesKey = "com.nimbo.routing.profiles"
private const val RoutingProfileIdKey = "com.nimbo.routing.profile-id"

@kotlinx.serialization.Serializable
private data class StoredRoutingProfile(
    val id: String,
    val name: String,
    val description: String,
    val ruleOrder: String,
    val globalProxy: Boolean,
    val bypassLocalIp: Boolean,
    val domainStrategy: String,
    val directSites: List<String>,
    val directIp: List<String>,
    val proxySites: List<String>,
    val proxyIp: List<String>,
    val blockSites: List<String>,
    val blockIp: List<String>
)

private fun StoredRoutingProfile.toProfile(builtin: Boolean) = NimboRoutingProfile(
    id = id,
    name = name,
    description = description,
    builtin = builtin,
    ruleOrder = ruleOrder,
    globalProxy = globalProxy,
    bypassLocalIp = bypassLocalIp,
    domainStrategy = domainStrategy,
    directSites = directSites,
    directIp = directIp,
    proxySites = proxySites,
    proxyIp = proxyIp,
    blockSites = blockSites,
    blockIp = blockIp
)

private fun NimboRoutingProfile.toStored() = StoredRoutingProfile(
    id = id,
    name = name,
    description = description,
    ruleOrder = ruleOrder,
    globalProxy = globalProxy,
    bypassLocalIp = bypassLocalIp,
    domainStrategy = domainStrategy,
    directSites = directSites,
    directIp = directIp,
    proxySites = proxySites,
    proxyIp = proxyIp,
    blockSites = blockSites,
    blockIp = blockIp
)

private fun loadRoutingOverrides(): Map<String, StoredRoutingProfile> {
    val raw = NSUserDefaults.standardUserDefaults.stringForKey(RoutingProfilesKey) ?: return emptyMap()
    return runCatching { iosJson.decodeFromString<List<StoredRoutingProfile>>(raw) }
        .getOrDefault(emptyList())
        .associateBy { it.id }
}

private fun loadRoutingProfiles(): List<NimboRoutingProfile> {
    val overrides = loadRoutingOverrides()
    return NimboBuiltinRoutingProfiles.defaults().map { default ->
        overrides[default.id]?.toProfile(builtin = true) ?: default
    }
}

private fun activeRoutingProfileId(): String =
    NSUserDefaults.standardUserDefaults.stringForKey(RoutingProfileIdKey)
        ?: NimboBuiltinRoutingProfiles.GLOBAL

private fun publishRoutingProfiles() {
    iosUiState.value = iosUiState.value.copy(
        routingProfiles = loadRoutingProfiles(),
        routingProfileId = activeRoutingProfileId()
    )
    // Правила уходят в туннель при сборке конфигурации — её пересобирает Swift.
    postIosAction(RoutingAction, "profile")
}

private fun selectRoutingProfile(id: String) {
    NSUserDefaults.standardUserDefaults.setObject(id, RoutingProfileIdKey)
    publishRoutingProfiles()
}

private fun saveRoutingProfile(profile: NimboRoutingProfile) {
    val next = loadRoutingOverrides().toMutableMap()
    next[profile.id] = profile.toStored()
    NSUserDefaults.standardUserDefaults.setObject(
        iosJson.encodeToString(next.values.toList()),
        RoutingProfilesKey
    )
    publishRoutingProfiles()
}

private fun resetRoutingProfiles() {
    NSUserDefaults.standardUserDefaults.removeObjectForKey(RoutingProfilesKey)
    publishRoutingProfiles()
}

/**
 * Правила выбранного профиля для Xray.
 *
 * Возвращается объект со стратегией доменов и правилами: стратегия относится ко
 * всей маршрутизации, а не к отдельному правилу, поэтому её нельзя положить в
 * массив.
 */
fun NimboIosRoutingProfileJson(): String {
    val profile = loadRoutingProfiles().firstOrNull { it.id == activeRoutingProfileId() }
        ?: NimboBuiltinRoutingProfiles.defaults().first()
    val rules = NimboRoutingProfileRules.rules(profile).map { rule ->
        buildJsonObject {
            put("type", JsonPrimitive("field"))
            if (rule.domains.isNotEmpty()) {
                put("domain", JsonArray(rule.domains.map { JsonPrimitive(it) }))
            }
            if (rule.ips.isNotEmpty()) {
                put("ip", JsonArray(rule.ips.map { JsonPrimitive(it) }))
            }
            if (rule.catchAll) {
                // Правилу нужно хоть одно поле совпадения, иначе Xray его не
                // примет: сеть подходит — под неё попадает весь трафик.
                put("network", JsonPrimitive("tcp,udp"))
            }
            put("outboundTag", JsonPrimitive(rule.outboundTag))
        }
    }
    return iosJson.encodeToString(
        buildJsonObject {
            put("domainStrategy", JsonPrimitive(profile.domainStrategy))
            put("rules", JsonArray(rules))
        }
    )
}

/**
 * Уведомления внутри приложения.
 *
 * История хранится на устройстве: всплывающая полоса живёт секунды, а понять
 * задним числом, почему не обновилась подписка, без записи невозможно.
 */
private const val NotificationsDefaultsKey = "com.nimbo.notifications"
private const val NotificationsLimit = 100

@kotlinx.serialization.Serializable
private data class StoredNotification(
    val id: String,
    val title: String,
    val message: String,
    val kind: String,
    val timestampSeconds: Long,
    val timeLabel: String
)

private fun loadNotifications(): List<NimboNotification> {
    val raw = NSUserDefaults.standardUserDefaults.stringForKey(NotificationsDefaultsKey) ?: return emptyList()
    return runCatching { iosJson.decodeFromString<List<StoredNotification>>(raw) }
        .getOrDefault(emptyList())
        .map {
            NimboNotification(
                id = it.id,
                title = it.title,
                message = it.message,
                kind = NimboNotificationKind.fromWireName(it.kind),
                timestampSeconds = it.timestampSeconds,
                timeLabel = it.timeLabel
            )
        }
}

private fun storeNotifications(items: List<NimboNotification>) {
    // Сотни записей никто не читает, а место и время разбора они занимают.
    val trimmed = items.take(NotificationsLimit)
    val payload = trimmed.map {
        StoredNotification(
            id = it.id,
            title = it.title,
            message = it.message,
            kind = it.kind.wireName,
            timestampSeconds = it.timestampSeconds,
            timeLabel = it.timeLabel
        )
    }
    NSUserDefaults.standardUserDefaults.setObject(
        iosJson.encodeToString(payload),
        NotificationsDefaultsKey
    )
    iosUiState.value = iosUiState.value.copy(notifications = trimmed)
}

/**
 * Показать сообщение и записать его в историю.
 *
 * Время форматирует Swift: у него есть локаль устройства, а общий код о ней
 * ничего не знает.
 */
fun NimboPushIosNotification(
    kind: String,
    message: String,
    timeLabel: String,
    timestampSeconds: Long
) {
    val notification = NimboNotification(
        id = "n-" + timestampSeconds.toString() + "-" + message.hashCode().toString(36),
        title = NimboNotificationKind.fromWireName(kind).title,
        message = message,
        kind = NimboNotificationKind.fromWireName(kind),
        timestampSeconds = timestampSeconds,
        timeLabel = timeLabel
    )
    storeNotifications(listOf(notification) + loadNotifications())
    iosUiState.value = iosUiState.value.copy(toast = notification)
}

/**
 * Запустить частицы события.
 *
 * Номер события растёт: одинаковые подряд идущие события иначе не отличить, и
 * вторая анимация не запускалась бы.
 */
fun NimboPushIosBurst(trigger: String) {
    val current = iosUiState.value
    iosUiState.value = current.copy(
        burstEventId = current.burstEventId + 1,
        burstTrigger = trigger
    )
}

/** Скрыть всплывающую полосу: историю это не трогает. */
fun NimboDismissIosToast() {
    iosUiState.value = iosUiState.value.copy(toast = null)
}

private fun deleteNotification(id: String) {
    storeNotifications(loadNotifications().filterNot { it.id == id })
}

private fun clearNotifications() {
    storeNotifications(emptyList())
}

/** Настройки маршрутизации живут в NSUserDefaults и переживают перезапуск. */
private const val RoutingDefaultsPrefix = "com.nimbo.routing."

private fun loadRoutingFlag(key: String, default: Boolean): Boolean {
    val defaults = NSUserDefaults.standardUserDefaults
    val stored = defaults.objectForKey(RoutingDefaultsPrefix + key) ?: return default
    return (stored as? Boolean) ?: default
}

private fun loadRoutingValue(key: String, default: String): String =
    NSUserDefaults.standardUserDefaults.stringForKey(RoutingDefaultsPrefix + key) ?: default

private fun applyRoutingChange(key: String, value: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    when (key) {
        "bypassLocal", "sniffing" -> defaults.setBool(value == "true", RoutingDefaultsPrefix + key)
        else -> defaults.setObject(value, RoutingDefaultsPrefix + key)
    }
    iosUiState.value = iosUiState.value.copy(
        routingBypassLocal = loadRoutingFlag("bypassLocal", true),
        routingSniffing = loadRoutingFlag("sniffing", true),
        routingDns = loadRoutingValue("dns", "cloudflare")
    )
    // Пересобрать конфигурацию должен Swift: у него доступ к профилю и туннелю.
    postIosAction(RoutingAction, key)
}

/** Избранные серверы переживают перезапуск: держим их в NSUserDefaults. */
private const val FavoritesDefaultsKey = "com.nimbo.favorite-server-ids"

private fun loadFavoriteServerIds(): Set<String> {
    val stored = NSUserDefaults.standardUserDefaults.stringArrayForKey(FavoritesDefaultsKey)
    return stored.orEmpty().mapNotNull { it as? String }.toSet()
}

private fun storeFavoriteServerIds(value: Set<String>) {
    NSUserDefaults.standardUserDefaults.setObject(value.toList(), FavoritesDefaultsKey)
}

private val iosFavorites = mutableStateOf(loadFavoriteServerIds())

private fun toggleFavoriteServer(serverId: String) {
    if (serverId.isBlank()) return
    val current = iosFavorites.value
    val next = if (serverId in current) current - serverId else current + serverId
    iosFavorites.value = next
    storeFavoriteServerIds(next)
    iosUiState.value = iosUiState.value.copy(favoriteServerIds = next)
}

/**
 * Пока подписка на iOS скачивается без разбора заголовков панели, сайт берём из
 * адреса самой подписки — ровно так же ведёт себя десктоп, когда провайдер не
 * прислал profile-web-page-url.
 */
private fun websiteFromSource(source: String?): String? {
    val value = source?.trim().orEmpty()
    if (!value.startsWith("http://") && !value.startsWith("https://")) return null
    val schemeEnd = value.indexOf("://") + 3
    val hostEnd = value.indexOf('/', schemeEnd)
    val origin = if (hostEnd > 0) value.substring(0, hostEnd) else value
    return origin.takeIf { it.length > schemeEnd }
}

/**
 * Сведения о подписке (имя владельца, трафик, срок) приходят из заголовков
 * ответа панели — разбор ссылок их не содержит.
 */
fun NimboUpdateIosProfileMeta(
    title: String?,
    trafficLabel: String,
    expiryLabel: String,
    updatedLabel: String,
    announce: String
) {
    iosUiState.value = iosUiState.value.copy(
        activeProfileName = title?.takeIf { it.isNotBlank() } ?: iosUiState.value.activeProfileName,
        profileTrafficLabel = trafficLabel,
        profileExpiryLabel = expiryLabel,
        profileUpdatedLabel = updatedLabel,
        profileAnnounce = announce
    )
}

/**
 * Показания туннеля приходят отдельной функцией: подпись
 * [NimboUpdateIosUiState] трогать нельзя, иначе ломается вызов из Swift.
 */
fun NimboUpdateIosMetrics(
    uploadSpeed: Long,
    downloadSpeed: Long,
    uploadTotal: Long,
    downloadTotal: Long,
    uploadSamples: List<Long>,
    downloadSamples: List<Long>,
    memoryMb: Int,
    memorySamples: List<Int>
) {
    val count = minOf(uploadSamples.size, downloadSamples.size)
    val samples = (0 until count).map { index ->
        NimboSpeedSample(upload = uploadSamples[index], download = downloadSamples[index])
    }
    iosUiState.value = iosUiState.value.copy(
        uploadSpeed = uploadSpeed,
        downloadSpeed = downloadSpeed,
        uploadTotal = uploadTotal,
        downloadTotal = downloadTotal,
        speedSamples = samples,
        memoryMb = memoryMb,
        memorySamples = memorySamples
    )
}

private const val NimboSupportUrl = "https://t.me/nebulaguard_channel"

private val iosUiState = mutableStateOf(NimboUiState())
private val iosJson = Json { ignoreUnknownKeys = true }

fun NimboUpdateIosUiState(
    vpnState: String,
    errorCode: String?,
    errorMessage: String?,
    activeProfileName: String,
    activeServerName: String,
    serverCount: Int,
    profileCount: Int,
    deviceName: String,
    systemName: String,
    appVersion: String,
    profileJson: String?,
    activeServerId: String?
) {
    val normalizedProfile = profileJson
        ?.let { runCatching { iosJson.decodeFromString<NormalizedSubscription>(it) }.getOrNull() }
    val servers = normalizedProfile?.servers.orEmpty().map { server ->
        NimboServerUi(
            id = server.id,
            name = server.name,
            protocol = server.protocol,
            transport = server.transport,
            security = server.security,
            selected = server.id == activeServerId,
            ping = iosPings.value[server.id],
            pingInProgress = iosPingInProgress.value,
            description = server.description
        )
    }
    val selectedServer = servers.firstOrNull { it.selected } ?: servers.firstOrNull()
    iosUiState.value = NimboUiState(
        vpnState = vpnState,
        errorCode = errorCode,
        errorMessage = errorMessage,
        activeProfileName = normalizedProfile?.title ?: activeProfileName,
        activeServerName = selectedServer?.name ?: activeServerName,
        serverCount = normalizedProfile?.servers?.size ?: serverCount,
        profileCount = if (normalizedProfile != null) 1 else profileCount,
        deviceName = deviceName,
        systemName = systemName,
        appVersion = appVersion,
        activeServerId = selectedServer?.id ?: activeServerId,
        servers = servers,
        supportUrl = NimboSupportUrl,
        websiteUrl = websiteFromSource(normalizedProfile?.source),
        favoriteServerIds = iosFavorites.value,
        pings = iosPings.value,
        pingInProgress = iosPingInProgress.value,
        sessions = iosSessions.value,
        routingBypassLocal = loadRoutingFlag("bypassLocal", true),
        routingSniffing = loadRoutingFlag("sniffing", true),
        routingDns = loadRoutingValue("dns", "cloudflare"),
        backgroundStyle = appearanceInt("backgroundStyle", 0),
        backgroundPalette = appearanceInt("backgroundPalette", 0),
        backgroundMotion = appearanceFlag("backgroundMotion", true),
        navIconMotion = appearanceFlag("navIconMotion", true),
        showSpeedWidget = appearanceFlag("showSpeedWidget", true),
        showMemoryWidget = appearanceFlag("showMemoryWidget", true),
        elementStyle = appearanceText("elementStyle", "glass"),
        serverSort = appearanceText("serverSort", "subscription"),
        favoritesFirst = appearanceFlag("favoritesFirst", true),
        connectStyle = appearanceText("connectStyle", "classic"),
        statusParticles = appearanceFlag("statusParticles", true),
        pingProtocol = pingText("protocol", "tcp"),
        pingTimeoutMs = pingInt("timeoutMs", 3000),
        pingUrl = pingText("url", DefaultPingUrl),
        modules = loadModules(),
        routingProfiles = loadRoutingProfiles(),
        routingProfileId = activeRoutingProfileId(),
        notifications = loadNotifications()
    )
}

/** Замеры задержки: приходят из Swift, там их считает NimboPingService. */
private val iosPings = mutableStateOf<Map<String, Int>>(emptyMap())
private val iosPingInProgress = mutableStateOf(false)

fun NimboUpdateIosPings(serverIds: List<String>, values: List<Int>, inProgress: Boolean) {
    val count = minOf(serverIds.size, values.size)
    if (count > 0) {
        val merged = iosPings.value.toMutableMap()
        for (index in 0 until count) {
            merged[serverIds[index]] = values[index]
        }
        iosPings.value = merged
    }
    iosPingInProgress.value = inProgress
    val current = iosUiState.value
    iosUiState.value = current.copy(
        pings = iosPings.value,
        pingInProgress = inProgress,
        servers = current.servers.map { server ->
            server.copy(
                ping = iosPings.value[server.id],
                pingInProgress = inProgress
            )
        }
    )
}

/** Завершённые сессии: считает их приложение, ядро статистики не отдаёт. */
private val iosSessions = mutableStateOf<List<NimboSessionUi>>(emptyList())

fun NimboUpdateIosSessions(
    startedAt: List<String>,
    durations: List<String>,
    downloads: List<Long>,
    uploads: List<Long>
) {
    val count = minOf(startedAt.size, durations.size, downloads.size, uploads.size)
    iosSessions.value = (0 until count).map { index ->
        NimboSessionUi(
            startedAt = startedAt[index],
            duration = durations[index],
            download = downloads[index],
            upload = uploads[index]
        )
    }
    iosUiState.value = iosUiState.value.copy(sessions = iosSessions.value)
}

/** Текущая вкладка: её задаёт системная панель из SwiftUI. */
private val iosScreen = mutableStateOf(NimboScreen.HOME)

fun NimboSetIosScreen(wireName: String) {
    iosScreen.value = NimboScreen.fromWireName(wireName)
}

fun NimboCurrentIosScreen(): String = iosScreen.value.wireName

fun NimboComposeViewController(screenName: String): UIViewController =
    ComposeUIViewController {
        NimboSharedScreen(
            screen = NimboScreen.fromWireName(screenName),
            showBottomBar = false,
            externalScreen = iosScreen.value,
            state = iosUiState.value,
            actions = NimboUiActions(
                onToggleVpn = { postIosAction(ToggleVpnAction) },
                onAddProfile = { postIosAction(AddProfileAction) },
                onRefreshProfile = { postIosAction(RefreshProfileAction) },
                onOpenProfileSettings = { postIosAction(ProfileSettingsAction) },
                onSelectServer = { postIosAction(SelectServerAction, it) },
                onSaveAppRule = { postIosAction(SaveAppRuleAction, it) },
                onOpenDiagnostics = { postIosAction(DiagnosticsAction) },
                onOpenAbout = { postIosAction(AboutAction) },
                onOpenSystemSettings = { postIosAction(SystemSettingsAction) },
                onOpenUrl = { postIosAction(OpenUrlAction, it) },
                onToggleFavorite = { toggleFavoriteServer(it) },
                onPingServer = { postIosAction(PingServerAction, it) },
                onPingAll = { postIosAction(PingAllAction) },
                onConnectFastest = { postIosAction(ConnectFastestAction) },
                onImportSubscription = { postIosAction(ImportSubscriptionAction, it) },
                onImportClipboard = { postIosAction(ImportClipboardAction) },
                onImportFile = { postIosAction(ImportFileAction) },
                onScanQr = { postIosAction(ScanQrAction) },
                onSetRouting = { key, value -> applyRoutingChange(key, value) },
                onSetAppearance = { key, value -> applyAppearanceChange(key, value) },
                onSetPing = { key, value -> applyPingChange(key, value) },
                onSaveModule = { id, name, text -> saveModule(id, name, text) },
                onToggleModule = { toggleModule(it) },
                onDeleteModule = { deleteModule(it) },
                onCopyText = { postIosAction(CopyTextAction, it) },
                // Имя и текст уходят одной строкой: у уведомления один объект,
                // а разделитель переводом строки в имени встретиться не может.
                onExportModule = { name, text -> postIosAction(ExportModuleAction, name + "\n" + text) },
                onSelectRoutingProfile = { selectRoutingProfile(it) },
                onSaveRoutingProfile = { saveRoutingProfile(it) },
                onResetRoutingProfiles = { resetRoutingProfiles() },
                onDismissToast = { NimboDismissIosToast() },
                onDeleteNotification = { deleteNotification(it) },
                onClearNotifications = { clearNotifications() },
                onOpenUpdate = { postIosAction(OpenUpdateAction) },
                onExportBackup = { postIosAction(ExportBackupAction) },
                onImportBackup = { postIosAction(ImportBackupAction) },
                onOpenSync = { postIosAction(OpenSyncAction) },
                onOpenScreen = { wireName ->
                    iosScreen.value = NimboScreen.fromWireName(wireName)
                    postIosAction(OpenScreenAction, wireName)
                }
            )
        )
    }

private fun postIosAction(name: String, payload: String? = null) {
    NSNotificationCenter.defaultCenter.postNotificationName(name, payload)
}
