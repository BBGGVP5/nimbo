@file:Suppress("FunctionName", "unused")

package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.danila.nimbo.shared.subscription.NormalizedSubscription
import kotlinx.serialization.json.Json
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
        routingBypassLocal = loadRoutingFlag("bypassLocal", true),
        routingSniffing = loadRoutingFlag("sniffing", true),
        routingDns = loadRoutingValue("dns", "cloudflare")
    )
}

fun NimboComposeViewController(screenName: String): UIViewController =
    ComposeUIViewController {
        NimboSharedScreen(
            screen = NimboScreen.fromWireName(screenName),
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
                onSetRouting = { key, value -> applyRoutingChange(key, value) }
            )
        )
    }

private fun postIosAction(name: String, payload: String? = null) {
    NSNotificationCenter.defaultCenter.postNotificationName(name, payload)
}
