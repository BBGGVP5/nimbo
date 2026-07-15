package com.danila.nimbo.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ClipboardManager
import android.net.VpnService
import android.util.Log
import android.os.Debug
import com.danila.nimbo.ui.i18n.serverCountEn
import com.danila.nimbo.ui.i18n.serverCountRu
import com.danila.nimbo.ui.i18n.t
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.danila.nimbo.model.HomeWidget
import com.danila.nimbo.model.Server
import com.danila.nimbo.model.WidgetRegistry
import com.danila.nimbo.model.WidgetType
import com.danila.nimbo.model.WidgetConfig
import com.danila.nimbo.network.PingManager
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.DeleteProfileDialog
import com.danila.nimbo.ui.components.EditableWidgetCard
import com.danila.nimbo.ui.components.MorphismCircularGauge
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.QrCodeDisplayBottomSheet
import com.danila.nimbo.ui.components.QrScannerScreen
import com.danila.nimbo.ui.components.SubscriptionInfoCard
import com.danila.nimbo.ui.components.TopBarMorphIcon
import com.danila.nimbo.ui.components.cleanServerName
import com.danila.nimbo.ui.components.extractFlagEmoji
import com.danila.nimbo.ui.theme.*
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.viewmodel.HomeWidgetViewModel
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.filterServersForPolicies
import com.danila.nimbo.ui.components.ServerControlButtons
import com.danila.nimbo.vpn.MyVpnService
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import com.danila.nimbo.utils.NetworkProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ── Утилиты ───────────────────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} МБ"
    else -> "${bytes / (1024 * 1024 * 1024)} ГБ"
}

fun formatTime(sec: Int): String = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)

private fun isValidIpv4(ip: String?): Boolean {
    if (ip.isNullOrBlank()) return false
    if (!ip.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return false
    return ip.split(".").all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
}

private fun extractIpv4FromText(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val match = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""").find(text)?.value
    return match?.takeIf { isValidIpv4(it) }
}

private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
            conn.inputStream.bufferedReader().readText().trim()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

suspend fun getExternalIpAddress(): String {
    val candidates = listOf(
        "https://api64.ipify.org?format=json",
        "https://ifconfig.me/ip",
        "https://api.ip.sb/ip"
    )
    for (url in candidates) {
        val body = httpGet(url) ?: continue
        val fromJson = Regex(""""ip"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1)
        val ip = extractIpv4FromText(fromJson) ?: extractIpv4FromText(body)
        if (isValidIpv4(ip)) return ip!!
    }
    return "Не удалось определить"
}

suspend fun resolveHostToIp(host: String): String? = try {
    withContext(Dispatchers.IO) {
        val addrs = java.net.InetAddress.getAllByName(host)
        addrs.firstOrNull { it.address.size == 4 }?.hostAddress ?: addrs.firstOrNull()?.hostAddress
    }
} catch (e: Exception) { Log.w("HomeScreen", "DNS fail: ${e.message}"); null }

suspend fun getCountryFromIp(ip: String?): Pair<String?, String?> {
    if (ip == null || !ip.contains(".")) return Pair(null, null)
    val countryApiUrls = listOf(
        "https://ipwho.is/$ip",
        "https://ipapi.co/$ip/json/"
    )
    for (url in countryApiUrls) {
        val response = httpGet(url)
        if (response.isNullOrBlank()) continue
        val country = Regex(""""country"\s*:\s*"([^"]+)"""").find(response)?.groupValues?.getOrNull(1)
        val code = Regex(""""country_code"\s*:\s*"([^"]+)"""").find(response)?.groupValues?.getOrNull(1)
            ?: Regex(""""countryCode"\s*:\s*"([^"]+)"""").find(response)?.groupValues?.getOrNull(1)
        if (!country.isNullOrBlank() && !code.isNullOrBlank()) {
            return Pair(translateCountryToRussian(country), countryCodeToFlag(code))
        }
    }
    return Pair(null, null)
}

fun getCountryFromServerName(serverName: String?): Pair<String?, String?> {
    if (serverName == null || serverName.isEmpty()) return Pair("Неизвестно", "🌐")
    val flag = extractFlagEmoji(serverName)
    if (flag.isEmpty()) {
        return when {
            serverName.contains("финлянд", true) || serverName.contains("finland", true) || serverName.contains("helsinki", true) -> Pair("Финляндия", "🇫🇮")
            serverName.contains("нидерланд", true) || serverName.contains("netherlands", true) || serverName.contains("amsterdam", true) -> Pair("Нидерланды", "🇳🇱")
            serverName.contains("германи", true) || serverName.contains("germany", true) || serverName.contains("frankfurt", true) || serverName.contains("berlin", true) -> Pair("Германия", "🇩🇪")
            serverName.contains("сша", true) || serverName.contains("usa", true) || serverName.contains("united states", true) || serverName.contains("new york", true) -> Pair("США", "🇺🇸")
            serverName.contains("росси", true) || serverName.contains("russia", true) || serverName.contains("moscow", true) -> Pair("Россия", "🇷🇺")
            serverName.contains("турци", true) || serverName.contains("turkey", true) || serverName.contains("istanbul", true) -> Pair("Турция", "🇹🇷")
            serverName.contains("латви", true) || serverName.contains("latvia", true) || serverName.contains("riga", true) -> Pair("Латвия", "🇱🇻")
            serverName.contains("казахстан", true) || serverName.contains("kazakhstan", true) || serverName.contains("almaty", true) -> Pair("Казахстан", "🇰🇿")
            serverName.contains("хорвати", true) || serverName.contains("croatia", true) || serverName.contains("zagreb", true) -> Pair("Хорватия", "🇭🇷")
            serverName.contains("обход", true) -> Pair("Обход", "😎")
            serverName.contains("автобаланс", true) || serverName.contains("autobalance", true) -> Pair(null, null)
            else -> Pair("Неизвестно", "🌐")
        }
    }
    return when (flag) {
        "🇫🇮" -> Pair("Финляндия", "🇫🇮"); "🇳🇱" -> Pair("Нидерланды", "🇳🇱"); "🇩🇪" -> Pair("Германия", "🇩🇪")
        "🇺🇸" -> Pair("США", "🇺🇸"); "🇷🇺" -> Pair("Россия", "🇷🇺"); "🇹🇷" -> Pair("Турция", "🇹🇷")
        "🇱🇻" -> Pair("Латвия", "🇱🇻"); "🇰🇿" -> Pair("Казахстан", "🇰🇿"); "🇭🇷" -> Pair("Хорватия", "🇭🇷")
        "🇬🇧" -> Pair("Великобритания", "🇬🇧"); "🇫🇷" -> Pair("Франция", "🇫🇷"); "🇪🇪" -> Pair("Эстония", "🇪🇪")
        "🇵🇱" -> Pair("Польша", "🇵🇱"); "🇮🇹" -> Pair("Италия", "🇮🇹"); "🇪🇸" -> Pair("Испания", "🇪🇸")
        "🇵🇹" -> Pair("Португалия", "🇵🇹"); "🇸🇪" -> Pair("Швеция", "🇸🇪"); "🇳🇴" -> Pair("Норвегия", "🇳🇴")
        "🇩🇰" -> Pair("Дания", "🇩🇰"); "🇯🇵" -> Pair("Япония", "🇯🇵"); "🇰🇷" -> Pair("Южная Корея", "🇰🇷")
        "🇸🇬" -> Pair("Сингапур", "🇸🇬"); "🇦🇺" -> Pair("Австралия", "🇦🇺"); "🇨🇦" -> Pair("Канада", "🇨🇦")
        "🇧🇷" -> Pair("Бразилия", "🇧🇷"); "🇮🇳" -> Pair("Индия", "🇮🇳"); "🇦🇪" -> Pair("ОАЭ", "🇦🇪")
        "🇮🇱" -> Pair("Израиль", "🇮🇱"); "🇿🇦" -> Pair("ЮАР", "🇿🇦"); "🇦🇷" -> Pair("Аргентина", "🇦🇷")
        "🇲🇽" -> Pair("Мексика", "🇲🇽"); "🇨🇭" -> Pair("Швейцария", "🇨🇭"); "🇦🇹" -> Pair("Австрия", "🇦🇹")
        "🇧🇪" -> Pair("Бельгия", "🇧🇪"); "🇨🇿" -> Pair("Чехия", "🇨🇿"); "🇬🇷" -> Pair("Греция", "🇬🇷")
        "🇹🇭" -> Pair("Таиланд", "🇹🇭"); "🇻🇳" -> Pair("Вьетнам", "🇻🇳"); "🇮🇩" -> Pair("Индонезия", "🇮🇩")
        "🇲🇾" -> Pair("Малайзия", "🇲🇾"); "🇵🇭" -> Pair("Филиппины", "🇵🇭"); "🇭🇰" -> Pair("Гонконг", "🇭🇰")
        "🇹🇼" -> Pair("Тайвань", "🇹🇼")
        else -> Pair("Неизвестно", "🌐")
    }
}

fun translateCountryToRussian(country: String): String = when (country) {
    "Finland" -> "Финляндия"; "Netherlands" -> "Нидерланды"; "Germany" -> "Германия"
    "United States" -> "США"; "Russia" -> "Россия"; "Turkey" -> "Турция"
    "Latvia" -> "Латвия"; "Kazakhstan" -> "Казахстан"; "Croatia" -> "Хорватия"
    "United Kingdom" -> "Великобритания"; "France" -> "Франция"; "Estonia" -> "Эстония"
    "Poland" -> "Польша"; "Italy" -> "Италия"; "Spain" -> "Испания"
    "Portugal" -> "Португалия"; "Sweden" -> "Швеция"; "Norway" -> "Норвегия"
    "Denmark" -> "Дания"; "Japan" -> "Япония"; "South Korea" -> "Южная Корея"
    "Singapore" -> "Сингапур"; "Australia" -> "Австралия"; "Canada" -> "Канада"
    "Brazil" -> "Бразилия"; "India" -> "Индия"; "United Arab Emirates" -> "ОАЭ"
    "Israel" -> "Израиль"; "South Africa" -> "ЮАР"; "Argentina" -> "Аргентина"
    "Mexico" -> "Мексика"; "Switzerland" -> "Швейцария"; "Austria" -> "Австрия"
    "Belgium" -> "Бельгия"; "Czechia" -> "Чехия"; "Greece" -> "Греция"
    "Thailand" -> "Таиланд"; "Vietnam" -> "Вьетнам"; "Indonesia" -> "Индонезия"
    "Malaysia" -> "Малайзия"; "Philippines" -> "Филиппины"; "Hong Kong" -> "Гонконг"
    "Taiwan" -> "Тайвань"; "China" -> "Китай"; "Ukraine" -> "Украина"; "Belarus" -> "Беларусь"
    else -> country
}

fun countryCodeToFlag(code: String): String =
    code.uppercase().map { 0x1F1E6 + (it - 'A') }.map { String(Character.toChars(it)) }.joinToString("")

private fun protocolBadge(server: Server): String {
    val protocol = server.protocol.lowercase()
    val network = server.network?.lowercase().orEmpty()

    if (protocol.contains("vless")) {
        return when (network) {
            "xhttp" -> "XHTTP"
            "grpc" -> "gRPC"
            "ws" -> "WebSocket"
            "h2" -> "HTTP/2"
            else -> "VLESS"
        }
    }

    return when {
        protocol.contains("vmess") -> "VMess"
        protocol.contains("trojan") -> "Trojan"
        protocol.contains("shadowsocks") || protocol == "ss" -> "Shadowsocks"
        protocol.contains("hysteria") -> "Hysteria"
        protocol.contains("tuic") -> "TUIC"
        protocol.isNotBlank() -> protocol.replaceFirstChar { it.uppercase() }
        else -> "VLESS"
    }
}

private fun descriptionBadge(server: Server): String? {
    val raw = server.serverDescription
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: return null
    return raw
}

private fun primaryServerBadge(server: Server): String {
    return descriptionBadge(server) ?: protocolBadge(server)
}

// ── HomeScreen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: com.danila.nimbo.MainViewModel,
    navController: NavController,
    servers: List<Server>,
    profiles: List<SubscriptionProfile>,
    onConnect: (Server) -> Unit,
    onSubscriptionAdded: (String) -> Unit,
    viewModel: HomeWidgetViewModel = viewModel(),
    showAddWidgetPanel: Boolean,
    onShowAddWidgetPanel: (Boolean) -> Unit,
    onAddSheetVisibilityChange: (Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf<Boolean>(false) }
    var showManualDialog by remember { mutableStateOf<Boolean>(false) }
    var manualUrl by remember { mutableStateOf("") }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = remember { PreferencesManager(context) }
    val showVersionInHeader by preferencesManager.showVersionInHeaderState
    val glowEffectsEnabled by preferencesManager.glowEffectsEnabledState
    val activity = context as Activity
    val vpnState = VpnManager.state.value
    val connectedServerState = VpnManager.connectedServer.value
    val scope = rememberCoroutineScope()

    val isVpnConnected = vpnState == VpnState.CONNECTED

    // 🔥 Получаем версию приложения
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "1.0"

    val isEditMode: Boolean by viewModel.isEditMode.collectAsState(initial = false)
    val hasChanges: Boolean by viewModel.hasUnsavedChanges.collectAsState(initial = false)
    val activeWidgets: List<HomeWidget> by viewModel.activeWidgets.collectAsState(initial = emptyList())
    val hiddenWidgets: List<HomeWidget> by viewModel.hiddenWidgets.collectAsState(initial = emptyList())

    BackHandler(enabled = isEditMode) {
        if (hasChanges) viewModel.cancelChanges() else viewModel.toggleEditMode()
    }

    var selectedServer by remember { mutableStateOf<Server?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var selectedProfileUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showProfileDialog by remember { mutableStateOf<Boolean>(false) }
    var showNoProfileError by remember { mutableStateOf<Boolean>(false) }
    var showQrScanner by remember { mutableStateOf<Boolean>(false) }

    // 🔥 Новые состояния для SubscriptionInfoCard
    var subscriptionExpanded by remember { mutableStateOf(preferencesManager.homeSubscriptionExpanded) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var showQrBottomSheetFor by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    val isRefreshingSubs: Boolean by mainViewModel.isRefreshingSubscriptions.collectAsState(initial = false)

    val currentActiveProfile: SubscriptionProfile? = profiles.find { it.url == selectedProfileUrl }
    val autoBypassByNetwork = preferencesManager.autoBypassByNetwork
    val isPinging by mainViewModel.isPinging.collectAsState()
    val allowServerSwitchWhileConnected by preferencesManager.allowServerSwitchWhileConnectedState
    val showConnectionWidgetsOnlyWhenConnected by preferencesManager.showConnectionWidgetsOnlyWhenConnectedState
    val showProtectedStatusCard by preferencesManager.showProtectedStatusCardState
    val showConnectionTimeCard by preferencesManager.showConnectionTimeCardState
    val connectButtonSizeScale by preferencesManager.connectButtonSizeScaleState
    val animatedConnectButtonMinHeight by animateDpAsState(
        targetValue = (260f * connectButtonSizeScale).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "connect_button_min_height"
    )
    val activeNetworkType by mainViewModel.activeNetworkType.collectAsState()
    val domainReachableForBypass by mainViewModel.isAutoBypassControlReachable.collectAsState()
    val shouldUseBypassOnlyForNetwork by mainViewModel.isBypassOnlyMode.collectAsState()
    var showPresetQuickSwitcher by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(NetworkProfileManager.getPresets(context)) }
    var activePresetId by remember { mutableStateOf(NetworkProfileManager.getActivePresetId(context)) }

    val effectiveServers = remember(
        servers,
        autoBypassByNetwork,
        activeNetworkType,
        domainReachableForBypass,
        shouldUseBypassOnlyForNetwork
    ) {
        val bypassOnlyModeActive = autoBypassByNetwork && shouldUseBypassOnlyForNetwork
        filterServersForPolicies(
            servers = servers,
            autoBypassByNetwork = autoBypassByNetwork,
            networkType = activeNetworkType,
            shouldUseBypassOnly = bypassOnlyModeActive,
            blockBypassWhenDomainReachable = autoBypassByNetwork &&
                domainReachableForBypass &&
                !shouldUseBypassOnlyForNetwork
        )
    }

    val serverPings = remember(effectiveServers) {
        effectiveServers
            .groupBy { it.pingKey() }
            .mapValues { (_, variants) ->
                val bestFresh = variants
                    .filter { it.isPingValid() }
                    .minByOrNull { it.ping ?: Int.MAX_VALUE }
                val bestAny = variants
                    .filter { (it.ping ?: -1) >= 0 }
                    .minByOrNull { it.ping ?: Int.MAX_VALUE }
                (bestFresh ?: bestAny)?.ping ?: -1
            }
    }
    val jsonSupportByProfileUrl = remember(profiles) {
        profiles.associate { it.url to (it.supportsJsonResponse == true) }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    val currentDownloadSpeed = formatBytes(VpnManager.downloadSpeed.value) + "/с"
    val currentUploadSpeed = formatBytes(VpnManager.uploadSpeed.value) + "/с"

    var currentIpAddress by rememberSaveable {
        mutableStateOf(preferencesManager.getString("cached_external_ip"))
    }
    var serverIpAddress by remember { mutableStateOf<String?>(null) }
    var ipCountry by rememberSaveable {
        mutableStateOf(preferencesManager.getString("cached_ip_country"))
    }
    var ipCountryFlag by rememberSaveable {
        mutableStateOf(preferencesManager.getString("cached_ip_country_flag"))
    }

    fun applyCountry(info: Pair<String?, String?>) {
        ipCountry = info.first ?: "Неизвестно"
        ipCountryFlag = info.second ?: "🌐"
    }

    fun persistIpAndCountry(ip: String?) {
        if (!isValidIpv4(ip)) return
        preferencesManager.setString("cached_external_ip", ip)
        preferencesManager.setString("cached_ip_country", ipCountry)
        preferencesManager.setString("cached_ip_country_flag", ipCountryFlag)
        preferencesManager.setLong("cached_external_ip_time", System.currentTimeMillis())
    }

    fun handleServerSelection(newServer: Server) {
        val currentConnected = connectedServerState ?: VpnManager.connectedServer.value
        val isSameServer = currentConnected?.host == newServer.host &&
            currentConnected.port == newServer.port &&
            currentConnected.uuid == newServer.uuid

        selectedServer = newServer
        VpnManager.selectedServer = newServer
        preferencesManager.saveLastSelectedServer(newServer)
        newServer.profileUrl?.let { preferencesManager.saveLastSelectedProfileUrl(it) }

        if (vpnState == VpnState.CONNECTED && !isSameServer) {
            // Обновляем верхний блок сразу и переключаем туннель на выбранный сервер.
            VpnManager.connectedServer.value = newServer
            scope.launch {
                activity.startService(
                    android.content.Intent(activity, MyVpnService::class.java).apply {
                        action = MyVpnService.ACTION_DISCONNECT
                    }
                )
                kotlinx.coroutines.delay(450)
                val reconnectIntent = MyVpnService.createConnectIntent(activity, newServer)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    activity.startForegroundService(reconnectIntent)
                } else {
                    activity.startService(reconnectIntent)
                }
            }
        }
    }

    // 🔥 Логика буфера обмена
    LaunchedEffect(showAddDialog, showManualDialog) {
        if (showAddDialog || showManualDialog) {
            try {
                val cm =
                    context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val primaryData = cm.primaryClip
                if (primaryData != null && primaryData.itemCount > 0) {
                    val text = primaryData.getItemAt(0).text?.toString()
                    if (text != null && (text.startsWith("http") || text.startsWith("vless://") || text.startsWith(
                            "vmess://"
                        ) || text.startsWith("ss://") || text.startsWith("vmess://"))
                    ) {
                        clipboardUrl = text
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeScreen", "Clipboard access failed", e)
            }
        }
    }

    val qrCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showQrScanner = true
        else scope.launch { snackbarHostState.showSnackbar("Требуется разрешение камеры") }
    }

    LaunchedEffect(showAddDialog, showManualDialog, showQrScanner) {
        onAddSheetVisibilityChange(
            showAddDialog || showManualDialog || showQrScanner
        )
    }

    LaunchedEffect(isVpnConnected, connectedServerState, selectedServer) {
        if (isVpnConnected) {
            val activeServer = connectedServerState ?: selectedServer ?: VpnManager.selectedServer
            val serverHost = activeServer?.host
            if (serverHost != null) {
                // Сначала устанавливаем страну из имени сервера (быстрый fallback)
                val serverCountryInfo = getCountryFromServerName(activeServer?.name)
                if (serverCountryInfo.first != null && serverCountryInfo.first != "Неизвестно") {
                    applyCountry(serverCountryInfo)
                }

                // Пытаемся определить IP сервера для отображения информации о нем (страна/флаг)
                val resolvedServerIp =
                    if (serverHost.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""))) {
                        serverHost
                    } else {
                        resolveHostToIp(serverHost)
                    }
                serverIpAddress = resolvedServerIp

                // Для точной страны/флага используем именно внешний IP (exit node), а не имя сервера.
                currentIpAddress = null

                // Пробуем несколько раз с задержкой, так как туннелю нужно время на прогрев
                repeat(4) { attempt ->
                    kotlinx.coroutines.delay(1000L + attempt * 1000L)
                    val newIp = getExternalIpAddress()
                    if (isValidIpv4(newIp) && newIp != "Не удалось определить") {
                        currentIpAddress = newIp
                        val info = getCountryFromIp(newIp)
                        applyCountry(info)
                        persistIpAndCountry(newIp)
                        return@LaunchedEffect
                    }
                }
                if (currentIpAddress == null) {
                    val cachedIp = preferencesManager.getString("cached_external_ip")
                    if (isValidIpv4(cachedIp)) {
                        currentIpAddress = cachedIp
                        ipCountry = preferencesManager.getString("cached_ip_country") ?: ipCountry
                        ipCountryFlag = preferencesManager.getString("cached_ip_country_flag") ?: ipCountryFlag
                    } else {
                        currentIpAddress = "Не удалось определить"
                    }
                }
            }
        } else {
            serverIpAddress = null
            // Когда отключаемся, сначала пробуем получить актуальный внешний IP, иначе используем кеш.
            val actualIp = getExternalIpAddress()
            if (isValidIpv4(actualIp) && actualIp != "Не удалось определить") {
                currentIpAddress = actualIp
                val info = getCountryFromIp(actualIp)
                applyCountry(info)
                persistIpAndCountry(actualIp)
            } else {
                val cached = preferencesManager.getString("cached_external_ip")
                if (isValidIpv4(cached)) {
                    currentIpAddress = cached
                    ipCountry = preferencesManager.getString("cached_ip_country") ?: ipCountry
                    ipCountryFlag = preferencesManager.getString("cached_ip_country_flag") ?: ipCountryFlag
                }
            }
        }
    }

    LaunchedEffect(profiles) {
        if (profiles.isNotEmpty() && selectedProfileUrl == null) {
            selectedProfileUrl = profiles.first().url
        }
    }

    LaunchedEffect(Unit) {
        val prefs = PreferencesManager(context)
        val lastUrl = prefs.loadLastSelectedProfileUrl()
        if (lastUrl != null && profiles.any { it.url == lastUrl }) selectedProfileUrl = lastUrl
        val lastServer = prefs.loadLastSelectedServer()
        if (lastServer != null) {
            val matched = effectiveServers.firstOrNull { it.matchesSelection(lastServer) }
            if (matched != null) {
                selectedServer = matched
                VpnManager.selectedServer = matched
            }
        }
        presets = NetworkProfileManager.getPresets(context)
        activePresetId = NetworkProfileManager.getActivePresetId(context)
    }

    LaunchedEffect(effectiveServers) {
        if (effectiveServers.isEmpty()) {
            selectedServer = null
            VpnManager.selectedServer = null
            return@LaunchedEffect
        }

        val current = selectedServer
        if (current == null) {
            selectedServer = effectiveServers[0]
            VpnManager.selectedServer = effectiveServers[0]
        } else {
            // 🔥 СИНХРОНИЗАЦИЯ: Обновляем объект сервера, сохранив выбор, но получив новые данные (пинг)
            val updated = effectiveServers.find { it.matchesSelection(current) }
            if (updated != null) {
                selectedServer = updated
                // Обновляем VpnManager только если мы хотим, чтобы текущее соединение знало о новом пинге
                // Но обычно это нужно только для UI
            } else {
                // Если старый сервер исчез из списка, выбираем первый доступный
                selectedServer = effectiveServers[0]
                VpnManager.selectedServer = effectiveServers[0]
            }
        }
    }

    LaunchedEffect(selectedServer) { VpnManager.selectedServer = selectedServer }

    LaunchedEffect(selectedProfileUrl) {
        if (selectedProfileUrl != null)
            PreferencesManager(context).saveLastSelectedProfileUrl(selectedProfileUrl!!)
    }

    LaunchedEffect(activeNetworkType, servers) {
        val result = NetworkProfileManager.applyPresetForCurrentNetworkIfEnabled(
            context = context,
            availableServers = servers
        )
        if (result != null) {
            presets = NetworkProfileManager.getPresets(context)
            activePresetId = result.preset.id
            val server = result.selectedServer
            if (server != null) {
                val matched = effectiveServers.firstOrNull { it.matchesSelection(server) } ?: server
                selectedServer = matched
                VpnManager.selectedServer = matched
                preferencesManager.saveLastSelectedServer(matched)
                matched.profileUrl?.let { preferencesManager.saveLastSelectedProfileUrl(it) }
            }
        }
    }

    LaunchedEffect(vpnState) {
        if (vpnState != VpnState.CONNECTED) VpnManager.connectedSeconds.value = 0
    }

    val lazyListState = rememberLazyListState()
    val HEADER_COUNT = 1

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index - HEADER_COUNT
        val toIndex = to.index - HEADER_COUNT

        viewModel.moveWidget(fromIndex, toIndex)
    }

    val showSpeed by remember(vpnState) { mutableStateOf(preferencesManager.showSpeed) }

    val filteredWidgets = remember(activeWidgets, showSpeed, vpnState, showConnectionWidgetsOnlyWhenConnected) {
        val hideConnectionWidgets = showConnectionWidgetsOnlyWhenConnected && vpnState != VpnState.CONNECTED

        activeWidgets.filter { widget ->
            val type = widget.type
            if (type == WidgetType.IP_INFO) return@filter false
            if (hideConnectionWidgets && (type == WidgetType.SPEED_DOWNLOAD || type == WidgetType.SPEED_UPLOAD || type == WidgetType.SPEED_STATS || type == WidgetType.VPN_STATUS)) {
                return@filter false
            }

            if (showSpeed) {
                true
            } else {
                type != WidgetType.SPEED_DOWNLOAD &&
                        type != WidgetType.SPEED_UPLOAD &&
                        type != WidgetType.SPEED_STATS
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    mainViewModel.refreshAllSubscriptions()
                    kotlinx.coroutines.delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 100.dp,
                    top = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "home_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        TopAppBar(
                            title = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            text = "NebulaGuard",
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = nebulaColors.textPrimary,
                                            maxLines = 1
                                        )
                                        if (showVersionInHeader) {
                                            Text(
                                                text = "v$versionName",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = nebulaColors.textPrimary.copy(alpha = 0.5f),
                                                modifier = Modifier.padding(
                                                    start = 4.dp,
                                                    top = 2.dp
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Твой надёжный VPN клиент",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = nebulaColors.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            },
                            navigationIcon = { Box(modifier = Modifier.width(32.dp)) },
                            actions = {
                                AnimatedContent(
                                    targetState = isEditMode,
                                    transitionSpec = {
                                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                    },
                                    label = "editButtons"
                                ) { edit ->
                                    if (edit) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val infiniteTransition =
                                                rememberInfiniteTransition(label = "addPulse")
                                            val addPulse by infiniteTransition.animateFloat(
                                                initialValue = 0.85f,
                                                targetValue = 1.15f,
                                                animationSpec = infiniteRepeatable(
                                                    tween(1000), RepeatMode.Reverse
                                                ),
                                                label = "top_add_pulse"
                                            )
                                            TopBarMorphIcon(
                                                icon = Icons.Default.Add,
                                                color = nebulaColors.accent,
                                                iconModifier = Modifier.scale(addPulse)
                                            ) {
                                                onShowAddWidgetPanel(true)
                                            }
                                            TopBarMorphIcon(
                                                icon = Icons.Default.Check,
                                                color = nebulaColors.accent
                                            ) {
                                                viewModel.saveChanges()
                                            }
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TopBarMorphIcon(
                                                icon = Icons.Default.Tune,
                                                color = if (showPresetQuickSwitcher) nebulaColors.accent else nebulaColors.textSecondary
                                            ) {
                                                showPresetQuickSwitcher = !showPresetQuickSwitcher
                                            }
                                            TopBarMorphIcon(
                                                icon = Icons.Default.Edit,
                                                color = nebulaColors.accent
                                            ) {
                                                viewModel.toggleEditMode()
                                            }
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )

                        AnimatedVisibility(
                            visible = showPresetQuickSwitcher,
                            enter = fadeIn(tween(220)) + slideInVertically(
                                initialOffsetY = { -it / 3 },
                                animationSpec = tween(220)
                            ),
                            exit = fadeOut(tween(150)) + slideOutVertically(
                                targetOffsetY = { -it / 4 },
                                animationSpec = tween(150)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presets.forEach { preset ->
                                    FilterChip(
                                        selected = activePresetId == preset.id,
                                        onClick = {
                                            val applyResult = NetworkProfileManager.applyPresetById(
                                                context = context,
                                                presetId = preset.id,
                                                availableServers = effectiveServers
                                            )
                                            if (applyResult != null) {
                                                presets = NetworkProfileManager.getPresets(context)
                                                activePresetId = applyResult.preset.id
                                                applyResult.selectedServer?.let { presetServer ->
                                                    handleServerSelection(presetServer)
                                                }
                                                mainViewModel.showTopNotification(
                                                    message = "Пресет \"${applyResult.preset.name}\" применён",
                                                    type = com.danila.nimbo.ui.components.NotificationType.SUCCESS
                                                )
                                            }
                                        },
                                        label = { Text(preset.name) }
                                    )
                                }
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        navController.navigate("network_presets")
                                    },
                                    label = { Text("Создать") }
                                )
                            }
                        }

                        if (preferencesManager.memoryMonitoringState.value && isVpnConnected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MemoryMonitor()
                            }
                        }
                    }
                }

                items(
                    filteredWidgets,
                    key = { widget -> widget.id }
                ) { widget ->

                    ReorderableItem(reorderableState, key = widget.id) { isDragging ->
                        val elevation = if (isDragging) 10.dp else 0.dp
                        val itemAlpha = if (isDragging) 0.9f else 1f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation,
                                    if (widget.type == WidgetType.VPN_BUTTON) CircleShape else RoundedCornerShape(
                                        20.dp
                                    )
                                )
                                .graphicsLayer { this.alpha = itemAlpha }
                        ) {
                            // ── Контент виджета ──────────────────────────
                            when (widget.type) {

                                WidgetType.VPN_BUTTON -> Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = animatedConnectButtonMinHeight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        VpnConnectionButtonNoText(
                                            state = vpnState,
                                            isRefreshing = isRefreshingSubs,
                                            sizeScale = connectButtonSizeScale,
                                            onClick = {
                                                when (vpnState) {
                                                    VpnState.DISCONNECTED -> {
                                                        if (profiles.isEmpty()) {
                                                            showNoProfileError =
                                                                true; return@VpnConnectionButtonNoText
                                                        }
                                                        if (selectedProfileUrl == null && profiles.isNotEmpty()) {
                                                            selectedProfileUrl =
                                                                profiles.first().url
                                                        }
                                                        val serverForConnect = selectedServer
                                                            ?: effectiveServers.firstOrNull()
                                                            ?: servers.firstOrNull()
                                                        if (serverForConnect == null) {
                                                            showNoProfileError = true
                                                            return@VpnConnectionButtonNoText
                                                        }
                                                        if (selectedProfileUrl == null) {
                                                            selectedProfileUrl =
                                                                serverForConnect.profileUrl
                                                                    ?: profiles.first().url
                                                        }
                                                        mainViewModel.cancelAllSystemJobs()
                                                        currentIpAddress =
                                                            null // Сбрасываем IP для обновления
                                                        handleServerSelection(serverForConnect)
                                                        val intent =
                                                            VpnService.prepare(activity)
                                                        if (intent != null) {
                                                            activity.startActivityForResult(
                                                                intent,
                                                                100
                                                            )
                                                        } else {
                                                            val vpnIntent =
                                                                MyVpnService.createConnectIntent(
                                                                    activity,
                                                                    serverForConnect
                                                                )
                                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                                                                activity.startForegroundService(
                                                                    vpnIntent
                                                                )
                                                            else activity.startService(vpnIntent)
                                                        }
                                                    }

                                                    VpnState.CONNECTED -> activity.startService(
                                                        android.content.Intent(
                                                            activity,
                                                            MyVpnService::class.java
                                                        )
                                                            .apply {
                                                                action =
                                                                    MyVpnService.ACTION_DISCONNECT
                                                            }
                                                    )

                                                    else -> {}
                                                }
                                            })

                                        val isConnected = vpnState == VpnState.CONNECTED
                                        val connectedServer = connectedServerState ?: selectedServer
                                        ?: VpnManager.selectedServer

                                        if (isConnected && connectedServer != null) {
                                            Spacer(Modifier.height(16.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                nebulaColors.accent.copy(alpha = 0.10f),
                                                                nebulaColors.textPrimary.copy(alpha = 0.03f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        0.8.dp,
                                                        nebulaColors.accent.copy(alpha = 0.35f),
                                                        RoundedCornerShape(20.dp)
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .background(
                                                                nebulaColors.accent.copy(alpha = 0.18f),
                                                                CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Dns,
                                                            contentDescription = null,
                                                            tint = nebulaColors.accent,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = cleanServerName(connectedServer.name),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = nebulaColors.textPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        AnimatedContent(
                                            targetState = isConnected,
                                            transitionSpec = {
                                                fadeIn(tween(400)) togetherWith fadeOut(tween(200))
                                            },
                                            label = "status_near_button"
                                        ) { connected ->
                                            if (connected) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(top = 14.dp)
                                                ) {
                                                    val visibleStatusCards = listOf(showProtectedStatusCard, showConnectionTimeCard).count { it }
                                                    if (visibleStatusCards > 0) {
                                                        val statusCardHeight = 126.dp
                                                        val cardWeight = if (visibleStatusCards == 1) 1f else 0.5f

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            AnimatedVisibility(
                                                                visible = showProtectedStatusCard,
                                                                modifier = Modifier.weight(cardWeight),
                                                                enter = fadeIn(tween(400)) + slideInVertically(
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.7f,
                                                                        stiffness = Spring.StiffnessMediumLow
                                                                    ),
                                                                    initialOffsetY = { it / 2 }
                                                                )
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(statusCardHeight)
                                                                        .clip(RoundedCornerShape(20.dp))
                                                                        .background(
                                                                            Brush.verticalGradient(
                                                                                listOf(
                                                                                    nebulaColors.accent.copy(alpha = 0.12f),
                                                                                    nebulaColors.textPrimary.copy(alpha = 0.03f)
                                                                                )
                                                                            )
                                                                        )
                                                                        .border(
                                                                            0.5.dp,
                                                                            nebulaColors.accent.copy(alpha = 0.4f),
                                                                            RoundedCornerShape(20.dp)
                                                                        )
                                                                        .padding(vertical = 14.dp, horizontal = 12.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                        Box(
                                                                            contentAlignment = Alignment.Center,
                                                                            modifier = Modifier.size(32.dp)
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Outlined.Shield,
                                                                                contentDescription = null,
                                                                                tint = nebulaColors.accent,
                                                                                modifier = Modifier.size(24.dp)
                                                                            )
                                                                            Icon(
                                                                                imageVector = Icons.Default.ElectricBolt,
                                                                                contentDescription = null,
                                                                                tint = nebulaColors.accent,
                                                                                modifier = Modifier.size(11.dp)
                                                                            )
                                                                        }
                                                                        Spacer(Modifier.height(4.dp))
                                                                        Text(
                                                                            "Защищено",
                                                                            color = nebulaColors.accent,
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            maxLines = 1,
                                                                            softWrap = false,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                        Spacer(Modifier.height(2.dp))
                                                                        Text(
                                                                            "Трафик зашифрован",
                                                                            color = nebulaColors.textSecondary,
                                                                            style = MaterialTheme.typography.titleSmall,
                                                                            fontWeight = FontWeight.Bold,
                                                                            maxLines = 1,
                                                                            softWrap = false,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            AnimatedVisibility(
                                                                visible = showConnectionTimeCard,
                                                                modifier = Modifier.weight(cardWeight),
                                                                enter = fadeIn(tween(400)) + slideInVertically(
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.7f,
                                                                        stiffness = Spring.StiffnessMediumLow
                                                                    ),
                                                                    initialOffsetY = { it / 2 }
                                                                )
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(statusCardHeight)
                                                                        .clip(RoundedCornerShape(20.dp))
                                                                        .background(
                                                                            Brush.verticalGradient(
                                                                                listOf(
                                                                                    nebulaColors.accent.copy(alpha = 0.12f),
                                                                                    nebulaColors.textPrimary.copy(alpha = 0.03f)
                                                                                )
                                                                            )
                                                                        )
                                                                        .border(
                                                                            0.5.dp,
                                                                            nebulaColors.accent.copy(alpha = 0.4f),
                                                                            RoundedCornerShape(20.dp)
                                                                        )
                                                                        .padding(vertical = 14.dp, horizontal = 12.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                        Box(
                                                                            contentAlignment = Alignment.Center,
                                                                            modifier = Modifier.size(32.dp)
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.AccessTime,
                                                                                contentDescription = null,
                                                                                tint = nebulaColors.accent,
                                                                                modifier = Modifier.size(24.dp)
                                                                            )
                                                                        }
                                                                        Spacer(Modifier.height(4.dp))
                                                                        Text(
                                                                            text = "Время подключения",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = nebulaColors.accent,
                                                                            maxLines = 1,
                                                                            softWrap = false,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                        Spacer(Modifier.height(2.dp))
                                                                        Text(
                                                                            text = formatTime(VpnManager.connectedSeconds.value),
                                                                            color = nebulaColors.textPrimary,
                                                                            style = MaterialTheme.typography.titleSmall,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                WidgetType.VPN_CONNECT_HINT -> {
                                    val isConnected = vpnState == VpnState.CONNECTED
                                    val isConnecting = vpnState == VpnState.CONNECTING

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !isConnected && !isConnecting,
                                        enter = fadeIn(tween(400)) + scaleIn(
                                            initialScale = 0.95f,
                                            animationSpec = spring(
                                                dampingRatio = 0.8f,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        ),
                                        exit = fadeOut(tween(250)) + scaleOut(
                                            targetScale = 0.95f
                                        )
                                    ) {
                                        ConnectHintWidget()
                                    }
                                }

                                WidgetType.SERVER_SELECTOR -> {
                                    val isConnected = vpnState == VpnState.CONNECTED
                                    val canShowSelector = !isConnected || allowServerSwitchWhileConnected

                                    AnimatedContent(
                                        targetState = canShowSelector,
                                        transitionSpec = {
                                            fadeIn(tween(300)) + scaleIn(
                                                initialScale = 0.95f
                                            ) togetherWith
                                                    fadeOut(tween(200)) + scaleOut(
                                                targetScale = 0.95f
                                            )
                                        },
                                        label = "serverSelector"
                                    ) { isVisible ->

                                        if (isVisible) {
                                            if (effectiveServers.isNotEmpty()) {
                                                ServerSelectorWidget(
                                                    selectedServer = selectedServer,
                                                    servers = effectiveServers,
                                                    serverPings = serverPings,
                                                    isPinging = isPinging,
                                                    pingDisplayMode = preferencesManager.pingDisplayModeState.value,
                                                    showJsonForServer = { server ->
                                                        server.profileUrl?.let { jsonSupportByProfileUrl[it] == true } == true
                                                    },
                                                    expanded = expanded,
                                                    onExpandedChange = { expanded = it },
                                                    onServerSelected = { handleServerSelection(it) },
                                                    autoUpdateInterval = currentActiveProfile?.autoUpdateInterval
                                                        ?: 12
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.height(1.dp))
                                        }
                                    }
                                }

                                WidgetType.SERVER_ACTIONS -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ServerControlButtons(
                                            onRefresh = {
                                                currentActiveProfile?.url?.let {
                                                    mainViewModel.refreshSubscription(
                                                        it
                                                    )
                                                } ?: mainViewModel.refreshAllSubscriptions()
                                            },
                                            onPingAll = { mainViewModel.pingAllServers() },
                                            onOpenServers = {
                                                currentActiveProfile?.let { prof ->
                                                    val encodedUrl =
                                                        java.net.URLEncoder.encode(prof.url, "UTF-8")
                                                    navController.navigate("profile_servers/$encodedUrl")
                                                } ?: navController.navigate("profiles")
                                            },
                                            onAdd = { showAddDialog = true },
                                            isPinging = isPinging,
                                            isUpdating = isRefreshingSubs
                                        )
                                        AnimatedVisibility(visible = profiles.isEmpty()) {
                                            EmptySubscriptionAddServerCard(
                                                onAdd = { showAddDialog = true }
                                            )
                                        }
                                    }
                                }

                                WidgetType.SUBSCRIPTION_INFO -> if (currentActiveProfile != null) {
                                    SubscriptionInfoCard(
                                        profileName = currentActiveProfile.displayName,
                                        originalName = currentActiveProfile.originalName,
                                        subscriptionUrl = currentActiveProfile.url,
                                        announce = currentActiveProfile.announce,
                                        daysUntilExpiry = currentActiveProfile.daysUntilExpiry,
                                        usedTraffic = currentActiveProfile.usedTraffic,
                                        totalTraffic = currentActiveProfile.totalTraffic,
                                        deviceLimit = currentActiveProfile.deviceLimit,
                                        onlineDevices = currentActiveProfile.onlineDevices,
                                        websiteUrl = currentActiveProfile.websiteUrl,
                                        supportUrl = currentActiveProfile.supportUrl,
                                        numericId = currentActiveProfile.numericId,
                                        onDeviceClick = {
                                            val encodedUrl = java.net.URLEncoder.encode(
                                                currentActiveProfile.url,
                                                "UTF-8"
                                            )
                                            navController.navigate("device_management/$encodedUrl")
                                        },
                                        onRename = {
                                            renameValue = currentActiveProfile.displayName
                                            showRenameDialog = true
                                        },
                                        onDelete = {
                                            showDeleteConfirmDialog = currentActiveProfile.url
                                        },
                                        onShowQr = {
                                            showQrBottomSheetFor = currentActiveProfile.url
                                        },
                                        onRefresh = {
                                            currentActiveProfile.url.let { url ->
                                                mainViewModel.refreshSubscription(url)
                                            }
                                        },
                                        onPingAll = { mainViewModel.pingAllServers() },
                                        isPinging = isPinging,
                                        isUpdating = currentActiveProfile.isLoading,
                                        showActions = false,
                                        isExpanded = subscriptionExpanded,
                                        onUpdateExpanded = {
                                            subscriptionExpanded = it
                                            preferencesManager.homeSubscriptionExpanded = it
                                        }
                                    )
                                }

                                WidgetType.IP_INFO -> IPInfoWidget(
                                    ipCountry = ipCountry,
                                    ipCountryFlag = ipCountryFlag,
                                    currentIpAddress = currentIpAddress,
                                    serverIpAddress = serverIpAddress,
                                    isVpnConnected = isVpnConnected,
                                    onRefresh = {
                                        scope.launch {
                                            val refreshedIp = getExternalIpAddress()
                                            if (isValidIpv4(refreshedIp) && refreshedIp != "Не удалось определить") {
                                                currentIpAddress = refreshedIp
                                                applyCountry(getCountryFromIp(refreshedIp))
                                                persistIpAndCountry(refreshedIp)
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                WidgetType.SPEED_DOWNLOAD -> {
                                    if (showConnectionWidgetsOnlyWhenConnected && !isVpnConnected) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    } else {
                                    SpeedCard(
                                        icon = Icons.Default.ArrowDownward,
                                        label = "Загрузка",
                                        value = currentDownloadSpeed,
                                        color = LocalNebulaColors.current.accent
                                    )
                                    }
                                }

                                WidgetType.SPEED_UPLOAD -> {
                                    if (showConnectionWidgetsOnlyWhenConnected && !isVpnConnected) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    } else {
                                    SpeedCard(
                                        icon = Icons.Default.ArrowUpward,
                                        label = "Отдача",
                                        value = currentUploadSpeed,
                                        color = LocalNebulaColors.current.accent
                                    )
                                    }
                                }

                                WidgetType.DEVICE_STATS -> if (currentActiveProfile != null) {
                                    val deviceCount = currentActiveProfile.deviceCount
                                    val limit = currentActiveProfile.deviceLimit
                                    val isUnlimited = limit <= 0
                                    MorphismCircularGauge(
                                        progress = if (isUnlimited) 0.5f else deviceCount.toFloat() / limit.coerceAtLeast(
                                            1
                                        ),
                                        centerText = if (isUnlimited) "$deviceCount/∞" else "$deviceCount/$limit",
                                        subText = "Устройств",
                                        icon = Icons.Default.Devices,
                                        color = LocalNebulaColors.current.accent
                                    )
                                }

                                WidgetType.EXPIRY_STATS -> if (currentActiveProfile != null) {
                                    val days = currentActiveProfile.daysUntilExpiry
                                    val totalDays = 30f
                                    val progress =
                                        if (days < 0) 1f else (days.toFloat() / totalDays).coerceIn(
                                            0f,
                                            1f
                                        )
                                    val daysText = if (days < 0) "∞" else "$days"
                                    MorphismCircularGauge(
                                        progress = progress,
                                        centerText = daysText,
                                        subText = if (days < 0) "Бессрочно" else "Дней осталось",
                                        icon = Icons.Default.History,
                                        color = Color(0xFF00E676)
                                    )
                                }

                                else -> {}
                            }

                            if (isEditMode && widget.type != WidgetType.VPN_BUTTON) {
                                val config = WidgetRegistry.getConfigForType(widget.type)
                                Box(
                                    modifier = with(this@ReorderableItem) {
                                        Modifier
                                            .matchParentSize()
                                            .heightIn(min = 60.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        nebulaColors.accent.copy(alpha = 0.65f),
                                                        nebulaColors.accent.copy(alpha = 0.8f)
                                                    )
                                                )
                                            )
                                            .longPressDraggableHandle()
                                    }
                                ) {
                                    Box(
                                        modifier = with(this@ReorderableItem) {
                                            Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 10.dp)
                                                .size(32.dp)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(
                                                            LocalNebulaColors.current.accent.copy(
                                                                alpha = 0.30f
                                                            ), Color.Transparent
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    LocalNebulaColors.current.accent.copy(
                                                        alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .draggableHandle()
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            "Перетащить",
                                            tint = LocalNebulaColors.current.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 50.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = config.icon,
                                            contentDescription = null,
                                            tint = nebulaColors.textPrimary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = config.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = nebulaColors.textPrimary.copy(alpha = 0.95f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!config.isSystem) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 10.dp)
                                                .size(32.dp)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(
                                                            nebulaColors.statusDisconnected.copy(
                                                                alpha = 0.35f
                                                            ), Color.Transparent
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    nebulaColors.statusDisconnected.copy(
                                                        alpha = 0.6f
                                                    ),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) { viewModel.hideWidget(widget.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Удалить",
                                                tint = nebulaColors.statusDisconnected,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 10.dp)
                                                .size(32.dp)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(
                                                            nebulaColors.textPrimary.copy(
                                                                alpha = 0.06f
                                                            ), Color.Transparent
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Lock,
                                                null,
                                                tint = nebulaColors.textTertiary.copy(alpha = 0.35f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && currentActiveProfile != null) {
        NebulaMorphicDialog(
            onDismissRequest = { showRenameDialog = false },
            title = "Переименовать профиль",
            description = "Введите новое название для вашей подписки",
            confirmButtonText = "Сохранить",
            onConfirm = {
                mainViewModel.renameProfile(currentActiveProfile.url, renameValue)
                showRenameDialog = false
            }
        ) {
            OutlinedTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Новое имя", color = nebulaColors.textTertiary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = nebulaColors.accent,
                    unfocusedBorderColor = nebulaColors.accent.copy(alpha = 0.2f),
                    cursorColor = nebulaColors.accent,
                    focusedTextColor = nebulaColors.textPrimary,
                    unfocusedTextColor = nebulaColors.textPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
    showDeleteConfirmDialog?.let { url ->
        val profile = currentActiveProfile?.takeIf { it.url == url }
        DeleteProfileDialog(
            profileName = profile?.displayName.orEmpty(),
            serverCount = profile?.servers?.size,
            onDismissRequest = { showDeleteConfirmDialog = null },
            onConfirm = {
                mainViewModel.removeSubscription(url)
                showDeleteConfirmDialog = null
            }
        )
    }
    showQrBottomSheetFor?.let { url ->
        QrCodeDisplayBottomSheet(
            url = url,
            onDismiss = { showQrBottomSheetFor = null }
        )
    }

    // --- NEW DIALOGS (Fixed & Modernized) ---

    if (showAddDialog) {
        NebulaMorphicDialog(
            onDismissRequest = { showAddDialog = false },
            title = "Добавить подписку",
            description = "Выберите удобный способ добавления подписки",
            confirmButtonText = null, // Custom content buttons
            onConfirm = {}
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Option 1: Scan QR
                Surface(
                    onClick = {
                        showAddDialog = false
                        qrCodeLauncher.launch(Manifest.permission.CAMERA)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = nebulaColors.accent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(nebulaColors.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = nebulaColors.accent)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Сканировать QR-код",
                                style = MaterialTheme.typography.titleMedium,
                                color = nebulaColors.textPrimary
                            )
                            Text(
                                "Мгновенный импорт через камеру",
                                style = MaterialTheme.typography.bodySmall,
                                color = nebulaColors.textSecondary
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = nebulaColors.textTertiary)
                    }
                }

                // Option 2: Manual URL
                Surface(
                    onClick = {
                        showAddDialog = false
                        showManualDialog = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Link, null, tint = Color.White.copy(alpha = 0.8f))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Ввести ссылку вручную",
                                style = MaterialTheme.typography.titleMedium,
                                color = nebulaColors.textPrimary
                            )
                            Text(
                                "Вставьте URL вашей подписки",
                                style = MaterialTheme.typography.bodySmall,
                                color = nebulaColors.textSecondary
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = nebulaColors.textTertiary)
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        NebulaMorphicDialog(
            onDismissRequest = {
                showManualDialog = false
                manualUrl = ""
            },
            title = "Ввод ссылки",
            description = "Вставьте URL подписки из буфера обмена или введите вручную",
            confirmButtonText = "Добавить",
            onConfirm = {
                if (manualUrl.isNotBlank()) {
                    onSubscriptionAdded(manualUrl)
                    showManualDialog = false
                    manualUrl = ""
                }
            }
        ) {
            OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "https://example.com/sub",
                        color = nebulaColors.textTertiary
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = nebulaColors.accent,
                    unfocusedBorderColor = nebulaColors.accent.copy(alpha = 0.2f),
                    cursorColor = nebulaColors.accent,
                    focusedTextColor = nebulaColors.textPrimary,
                    unfocusedTextColor = nebulaColors.textPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        try {
                            val cm =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val data = cm.primaryClip
                            if (data != null && data.itemCount > 0) {
                                manualUrl = data.getItemAt(0).text?.toString() ?: ""
                            }
                        } catch (e: Exception) {
                            Log.w("HomeScreen", "Paste failed", e)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, "Вставить", tint = nebulaColors.accent)
                    }
                }
            )
        }
    }

    if (showAddWidgetPanel) {
        AvailableWidgetsPanelHome(
            activeWidgets = activeWidgets,
            allWidgetConfigs = WidgetRegistry.availableWidgets,
            preferencesManager = preferencesManager,
            onAddWidget = { type ->
                viewModel.addWidget(type)
                onShowAddWidgetPanel(false)
            },
            onDismiss = { onShowAddWidgetPanel(false) }
        )
    }

    // QR Overlay
    AnimatedVisibility(
        visible = showQrScanner,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        QrScannerScreen(
            onResult = { result ->
                showQrScanner = false
                onSubscriptionAdded(result)
            },
            onBack = { showQrScanner = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 100.dp)
        )
    }
}

@Composable
private fun EmptySubscriptionAddServerCard(onAdd: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(252.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        nebulaColors.accent.copy(alpha = 0.13f),
                        nebulaColors.textPrimary.copy(alpha = 0.035f),
                        Color.Transparent
                    ),
                    radius = 520f
                )
            )
            .border(
                1.dp,
                nebulaColors.accent.copy(alpha = 0.16f),
                RoundedCornerShape(24.dp)
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(nebulaColors.accent.copy(alpha = 0.10f))
                    .border(1.dp, nebulaColors.accent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Добавить сервер",
                style = MaterialTheme.typography.titleMedium,
                color = nebulaColors.textPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Вставьте ссылку подписки или конфигурацию сервера",
                style = MaterialTheme.typography.bodySmall,
                color = nebulaColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = nebulaColors.accent,
                    contentColor = nebulaColors.background
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Добавить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Панель добавления виджетов с анимацией ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableWidgetsPanelHome(
    activeWidgets: List<HomeWidget>,
    allWidgetConfigs: List<WidgetConfig>,
    preferencesManager: PreferencesManager,
    onAddWidget: (WidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val availableConfigs = allWidgetConfigs.filter { config ->
        activeWidgets.none { it.type == config.type }
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val allowServerSwitchWhileConnected by preferencesManager.allowServerSwitchWhileConnectedState
    val connectButtonStyle by preferencesManager.connectButtonStyleState
    val connectButtonSizeScale by preferencesManager.connectButtonSizeScaleState
    val showConnectionWidgetsOnlyWhenConnected by preferencesManager.showConnectionWidgetsOnlyWhenConnectedState
    val showProtectedStatusCard by preferencesManager.showProtectedStatusCardState
    val showConnectionTimeCard by preferencesManager.showConnectionTimeCardState
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(400),
        label = "scrimAlpha"
    )

    LaunchedEffect(Unit) { expanded = true }

    fun closeSheet() {
        expanded = false
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = nebulaColors.background.copy(alpha = 0.98f),
        contentColor = nebulaColors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.65f * scrimAlpha),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(nebulaColors.textPrimary.copy(alpha = 0.15f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Добавить виджет",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = nebulaColors.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Выберите виджет для быстрого доступа",
                        style = MaterialTheme.typography.bodyMedium,
                        color = nebulaColors.textTertiary
                    )
                }

                Surface(
                    onClick = { onDismiss() },
                    shape = CircleShape,
                    color = nebulaColors.textPrimary.copy(alpha = 0.06f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            val tabTitles = listOf("Виджеты", "Подключение")
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = nebulaColors.accent,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) nebulaColors.accent else nebulaColors.textTertiary
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selectedTab == 0) {
                if (availableConfigs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = nebulaColors.accent.copy(alpha = 0.1f),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CheckCircle, null, tint = nebulaColors.accent, modifier = Modifier.size(40.dp))
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Все виджеты добавлены",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = nebulaColors.textPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Вы используете все доступные элементы",
                                style = MaterialTheme.typography.bodySmall,
                                color = nebulaColors.textTertiary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(availableConfigs, key = { it.type.name }) { config ->
                            WidgetPreviewItem(
                                config = config,
                                onClick = {
                                    onAddWidget(config.type)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsToggleCard(
                        title = "Выбор сервера при подключении",
                        subtitle = "Разрешить менять сервер, пока VPN уже подключен",
                        checked = allowServerSwitchWhileConnected,
                        onCheckedChange = { preferencesManager.allowServerSwitchWhileConnected = it }
                    )

                    SettingsToggleCard(
                        title = "Виджеты только при подключении",
                        subtitle = "Скрывать скорость до момента подключения",
                        checked = showConnectionWidgetsOnlyWhenConnected,
                        onCheckedChange = { preferencesManager.showConnectionWidgetsOnlyWhenConnected = it }
                    )

                    SettingsToggleCard(
                        title = "Показывать «Защищено»",
                        subtitle = "Карточка статуса шифрования рядом с кнопкой",
                        checked = showProtectedStatusCard,
                        onCheckedChange = { preferencesManager.showProtectedStatusCard = it }
                    )

                    SettingsToggleCard(
                        title = "Показывать «Время подключения»",
                        subtitle = "Карточка таймера рядом с кнопкой",
                        checked = showConnectionTimeCard,
                        onCheckedChange = { preferencesManager.showConnectionTimeCard = it }
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = nebulaColors.textPrimary.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val defaultScale = 1f
                        val isScaleChanged = kotlin.math.abs(connectButtonSizeScale - defaultScale) > 0.001f
                        val resetAlpha by animateFloatAsState(
                            targetValue = if (isScaleChanged) 1f else 0f,
                            animationSpec = tween(180),
                            label = "reset_icon_alpha"
                        )
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Размер кнопки подключения",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = nebulaColors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(connectButtonSizeScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = nebulaColors.accent,
                                        modifier = Modifier.clickable { preferencesManager.connectButtonSizeScale = defaultScale }
                                    )
                                    Surface(
                                        onClick = { if (isScaleChanged) preferencesManager.connectButtonSizeScale = defaultScale },
                                        shape = RoundedCornerShape(10.dp),
                                        color = nebulaColors.accent.copy(alpha = 0.14f * resetAlpha),
                                        border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.35f * resetAlpha)),
                                        modifier = Modifier
                                            .alpha(resetAlpha)
                                            .size(width = 34.dp, height = 28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = "Сбросить размер",
                                                tint = nebulaColors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Slider(
                                value = connectButtonSizeScale,
                                onValueChange = { preferencesManager.connectButtonSizeScale = it },
                                valueRange = 0.50f..2.00f
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = nebulaColors.textPrimary.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Стиль кнопки подключения",
                                style = MaterialTheme.typography.titleSmall,
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))
                            Spacer(Modifier.height(6.dp))
                            val styleOptions = listOf(
                                "Classic" to 0,
                                "Power" to 1,
                                "Mini" to 2,
                                "Shield" to 3,
                                "Pulse" to 4,
                                "Neo" to 5,
                                "Bolt" to 6,
                                "Ring" to 7,
                                "Ghost" to 8
                            )
                            styleOptions.chunked(4).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { (label, value) ->
                                        val isSelected = connectButtonStyle == value
                                        val animatedScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.04f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            label = "style_chip_scale"
                                        )
                                        val animatedAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.24f else 0.06f,
                                            animationSpec = tween(220),
                                            label = "style_chip_alpha"
                                        )
                                        Surface(
                                            onClick = { preferencesManager.connectButtonStyle = value },
                                            modifier = Modifier.weight(1f).scale(animatedScale),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) nebulaColors.accent.copy(alpha = animatedAlpha) else nebulaColors.textPrimary.copy(alpha = 0.04f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) nebulaColors.accent.copy(alpha = 0.55f) else nebulaColors.textPrimary.copy(alpha = 0.08f)
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isSelected) nebulaColors.accent else nebulaColors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                    repeat(4 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetPreviewItem(
    config: WidgetConfig,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = nebulaColors.textPrimary.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon in morphic container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(nebulaColors.accent.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        config.icon,
                        null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        config.title,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        config.description,
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Add button
                Surface(
                    shape = CircleShape,
                    color = nebulaColors.accent.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Widget Preview
            WidgetTypePreview(type = config.type)
        }
    }
}

@Composable
fun WidgetTypePreview(type: WidgetType) {
    val nebulaColors = LocalNebulaColors.current

    fun previewCardModifier() = Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(
            Brush.verticalGradient(
                listOf(
                    nebulaColors.textPrimary.copy(alpha = 0.08f),
                    nebulaColors.textPrimary.copy(alpha = 0.03f)
                )
            )
        )
        .border(
            0.8.dp,
            nebulaColors.textPrimary.copy(alpha = 0.12f),
            RoundedCornerShape(14.dp)
        )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(nebulaColors.background.copy(alpha = 0.74f))
            .border(
                0.8.dp,
                nebulaColors.textPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        when (type) {
            WidgetType.DEVICE_STATS -> {
                MorphismCircularGauge(
                    progress = 0.4f,
                    centerText = "4/10",
                    subText = "Устройств",
                    icon = Icons.Default.Devices,
                    color = nebulaColors.accent,
                    size = 100.dp
                )
            }
            WidgetType.EXPIRY_STATS -> {
                MorphismCircularGauge(
                    progress = 0.7f,
                    centerText = "21",
                    subText = "Дней осталось",
                    icon = Icons.Default.History,
                    color = Color(0xFF00E676),
                    size = 100.dp
                )
            }
            WidgetType.VPN_BUTTON -> {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    nebulaColors.accent.copy(alpha = 0.35f),
                                    nebulaColors.accent.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            1.dp,
                            nebulaColors.textPrimary.copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            WidgetType.VPN_STATUS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(previewCardModifier())
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Shield, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
                                Icon(Icons.Default.ElectricBolt, null, tint = nebulaColors.accent, modifier = Modifier.size(9.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Защищено", color = nebulaColors.accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text("Трафик зашифрован", color = nebulaColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(previewCardModifier())
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccessTime, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Время подключения", color = nebulaColors.accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text("00:12:34", color = nebulaColors.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            WidgetType.VPN_CONNECT_HINT -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .then(previewCardModifier())
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(nebulaColors.accent.copy(alpha = 0.17f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, null, tint = nebulaColors.accent, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Нажмите кнопку для подключения", color = nebulaColors.textPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text("Выберите сервер и подключитесь", color = nebulaColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }

            WidgetType.SERVER_SELECTOR -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .then(previewCardModifier())
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(nebulaColors.accent.copy(alpha = 0.17f), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Dns, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Автобалансер · EU #1 🌍", color = nebulaColors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }

            WidgetType.SERVER_ACTIONS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Icons.Default.Refresh, Icons.Default.Speed, Icons.Default.Dns, Icons.Default.Add).forEach { icon ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .then(previewCardModifier()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            WidgetType.SUBSCRIPTION_INFO -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .then(previewCardModifier())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(nebulaColors.accent.copy(alpha = 0.16f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Work, null, tint = nebulaColors.accent, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(7.dp))
                        Text("BBGGVP5", color = nebulaColors.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .background(nebulaColors.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Активен", color = nebulaColors.accent, style = MaterialTheme.typography.labelSmall)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .background(nebulaColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("27253 дня", color = nebulaColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .background(nebulaColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .background(nebulaColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                        )
                    }
                }
            }

            WidgetType.IP_INFO -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .then(previewCardModifier())
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🇷🇺", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Россия", style = MaterialTheme.typography.titleSmall, color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("84.252.101.179", style = MaterialTheme.typography.bodySmall, color = nebulaColors.textSecondary)
                        }
                    }
                }
            }

            WidgetType.SPEED_DOWNLOAD, WidgetType.SPEED_UPLOAD -> {
                val isDownload = type == WidgetType.SPEED_DOWNLOAD
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .then(previewCardModifier())
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(nebulaColors.accent.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isDownload) Icons.Default.Download else Icons.Default.Upload,
                                null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(if (isDownload) "Download" else "Upload", style = MaterialTheme.typography.labelSmall, color = nebulaColors.textTertiary)
                            Text("4.8 МБ/с", style = MaterialTheme.typography.titleSmall, color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            WidgetType.SPEED_STATS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Download" to Icons.Default.Download, "Upload" to Icons.Default.Upload).forEach { (label, icon) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(previewCardModifier())
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = nebulaColors.textTertiary)
                                    Text("4.8 МБ/с", style = MaterialTheme.typography.labelMedium, color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .then(previewCardModifier())
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            WidgetRegistry.getConfigForType(type).icon,
                            null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            WidgetRegistry.getConfigForType(type).title,
                            style = MaterialTheme.typography.labelMedium,
                            color = nebulaColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

// ── EditableWidgetsList ────────────────────────────────────────────────────────

@Composable
fun EditableWidgetsList(
    widgets: List<HomeWidget>,
    isEditMode: Boolean,
    onWidgetHide: (String) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    getWidgetConfig: (WidgetType) -> WidgetConfig,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onMoveWidget(from.index, to.index)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(widgets, key = { it.id }) { widget ->
            val config = getWidgetConfig(widget.type)

            ReorderableItem(reorderableState, key = widget.id) { isDragging ->
                val elevation by animateDpAsState(
                    if (isDragging) 12.dp else 0.dp,
                    label = "elev"
                )
                val bgAlpha by animateFloatAsState(
                    if (isDragging) 0.88f else 1f,
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation,
                            if (widget.type == WidgetType.VPN_BUTTON) CircleShape else RoundedCornerShape(
                                20.dp
                            )
                        )
                        .graphicsLayer { alpha = bgAlpha }
                ) {
                    EditableWidgetCard(
                        widget = widget, config = config, isEditMode = isEditMode,
                        canRemove = !config.isSystem, onRemove = { onWidgetHide(widget.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isEditMode) {
                        Box(
                            modifier = with(this@ReorderableItem) {
                                Modifier
                                    .matchParentSize()
                                    .heightIn(min = 72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(nebulaColors.surface.copy(alpha = 0.50f))
                                    .longPressDraggableHandle()
                            }
                        ) {
                            Box(
                                modifier = with(this@ReorderableItem) {
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 14.dp)
                                        .size(44.dp)
                                        .background(
                                            LocalNebulaColors.current.accent.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            LocalNebulaColors.current.accent.copy(alpha = 0.4f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .draggableHandle()
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    "Перетащить",
                                    tint = LocalNebulaColors.current.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.align(Alignment.Center)
                                    .padding(horizontal = 68.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    config.icon,
                                    null,
                                    tint = nebulaColors.textPrimary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    config.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = nebulaColors.textPrimary.copy(alpha = 0.95f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!config.isSystem) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd).padding(end = 14.dp)
                                        .size(44.dp)
                                        .background(
                                            nebulaColors.statusDisconnected.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            nebulaColors.statusDisconnected.copy(alpha = 0.45f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }) {
                                            onWidgetHide(
                                                widget.id
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Удалить",
                                        tint = nebulaColors.statusDisconnected,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd).padding(end = 14.dp)
                                        .size(44.dp)
                                        .background(
                                            nebulaColors.textPrimary.copy(alpha = 0.07f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        null,
                                        tint = nebulaColors.textTertiary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Composable виджеты ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectorWidget(
    selectedServer: Server?,
    servers: List<Server>,
    serverPings: Map<String, Int>,
    isPinging: Boolean,
    pingDisplayMode: Int,
    showJsonForServer: (Server) -> Boolean = { false },
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onServerSelected: (Server) -> Unit,
    autoUpdateInterval: Int = 12
) {
    val nebulaColors = LocalNebulaColors.current

    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "corner_radius"
    )

    var componentSize by remember { mutableStateOf<androidx.compose.ui.unit.IntSize>(androidx.compose.ui.unit.IntSize.Zero) }
    var componentTopInWindowPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                componentSize = coordinates.size
                componentTopInWindowPx = coordinates.positionInWindow().y
            }
            .clip(
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = cornerRadius,
                    bottomEnd = cornerRadius
                )
            )
            .background(nebulaColors.textPrimary.copy(alpha = 0.04f))
            .border(
                0.5.dp,
                nebulaColors.accent.copy(alpha = 0.2f),
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = cornerRadius,
                    bottomEnd = cornerRadius
                )
            )
            .clickable { onExpandedChange(!expanded) }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            nebulaColors.accent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 400f
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf<Color>(
                                    AccentPink.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val flag = extractFlagEmoji(selectedServer?.name ?: "")
                    if (flag.isNotEmpty()) {
                        Text(text = flag, fontSize = 18.sp)
                    } else {
                        Icon(
                            Icons.Default.Dns,
                            null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        "Сервер",
                        style = MaterialTheme.typography.labelSmall,
                        color = nebulaColors.textTertiary
                    )
                    AnimatedContent(
                        targetState = selectedServer?.name ?: "Выберите сервер",
                        transitionSpec = {
                            (fadeIn(
                                tween(
                                    220,
                                    delayMillis = 90
                                )
                            ) + slideInVertically { height -> height / 2 })
                                .togetherWith(fadeOut(tween(90)) + slideOutVertically { height -> -height / 2 })
                        },
                        label = "server_name_anim"
                    ) { name ->
                        Text(
                            text = cleanServerName(name),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedServer != null) nebulaColors.accent else nebulaColors.textPrimary,
                            fontWeight = if (selectedServer != null) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

                // Пинг
                val selectedServerPing = selectedServer?.pingKey()?.let { serverPings[it] } ?: -1
                val showLoading = isPinging && selectedServerPing == -1

                if (showLoading || selectedServerPing != -1) {
                    val pingColor = when {
                        showLoading -> nebulaColors.textTertiary.copy(alpha = 0.5f)
                        selectedServerPing == -1 -> nebulaColors.statusDisconnected
                        selectedServerPing <= 70 -> nebulaColors.statusConnected
                        selectedServerPing <= 120 -> Color(0xFFCDDC39) // Vibrant Lime/Yellow
                        selectedServerPing <= 220 -> Color(0xFFFF9800) // Vibrant Orange
                        else -> nebulaColors.statusDisconnected
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "ping_loading")
                    val alpha by if (showLoading) {
                        infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "ping_alpha"
                        )
                    } else remember { mutableStateOf(1f) }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(alpha)
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            null,
                            tint = pingColor,
                            modifier = Modifier.size(14.dp)
                        )
                        if (pingDisplayMode == 2) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pingColor)
                                    .border(1.dp, pingColor.copy(alpha = 0.4f), CircleShape)
                            )
                        } else {
                            Text(
                                text = if (showLoading) "..."
                                       else if (pingDisplayMode == 1) {
                                           if (selectedServerPing == -1) "Ошибка" else "Доступен"
                                       } else {
                                           if (selectedServerPing == -1) "н/д" else "${selectedServerPing}мс"
                                       },
                                style = MaterialTheme.typography.labelSmall,
                                color = pingColor
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Анимированная стрелочка
                val arrowRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "arrow_rotation"
                )

                Icon(
                    Icons.Default.KeyboardArrowDown,
                    null,
                    tint = nebulaColors.textTertiary,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
        }

        // FULLY CUSTOM POPUP
        if (expanded) {
            val popupWidth = with(density) { componentSize.width.toDp() }
            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
            val sidePaddingPx = with(density) { 12.dp.toPx() }
            val preferredThresholdPx = with(density) { 220.dp.toPx() }
            val maxPopupHeightLimitPx = with(density) { 450.dp.toPx() }
            val availableBelowPx = (screenHeightPx - (componentTopInWindowPx + componentSize.height) - sidePaddingPx)
                .coerceAtLeast(0f)
            val availableAbovePx = (componentTopInWindowPx - sidePaddingPx).coerceAtLeast(0f)
            val openUpwards = availableBelowPx < preferredThresholdPx && availableAbovePx > availableBelowPx
            val chosenAvailablePx = if (openUpwards) availableAbovePx else availableBelowPx
            val popupMaxHeightPx = chosenAvailablePx
                .coerceAtMost(maxPopupHeightLimitPx)
                .coerceAtLeast(1f)
            val popupMaxHeight = with(density) { popupMaxHeightPx.toDp() }
            val popupOffsetY = if (openUpwards) -popupMaxHeightPx.toInt() else componentSize.height
            val popupShape = if (openUpwards) {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            } else {
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
            }

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, popupOffsetY),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true,
                    clippingEnabled = true
                )
            ) {
                val animationState =
                    remember { MutableTransitionState(false).apply { targetState = true } }

                AnimatedVisibility(
                    visibleState = animationState,
                    enter = slideInVertically(initialOffsetY = { -20 }) + fadeIn() + scaleIn(
                        initialScale = 0.95f,
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    ),
                    exit = slideOutVertically(targetOffsetY = { -20 }) + fadeOut() + scaleOut(
                        targetScale = 0.95f,
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .width(popupWidth)
                            .heightIn(max = popupMaxHeight)
                            .shadow(
                                24.dp,
                                popupShape,
                                spotColor = nebulaColors.accent.copy(alpha = 0.35f)
                            )
                            .clip(popupShape)
                            .background(nebulaColors.surface.copy(alpha = 0.97f))
                            .background(
                                Brush.linearGradient(
                                    listOf<Color>(
                                        nebulaColors.textPrimary.copy(alpha = 0.14f),
                                        nebulaColors.textPrimary.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                nebulaColors.textPrimary.copy(alpha = 0.22f),
                                popupShape
                            )
                    ) {
                        // Glow effect internal
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf<Color>(
                                            LocalNebulaColors.current.accent.copy(
                                                alpha = 0.20f
                                            ), Color.Transparent
                                        ),
                                        radius = 600f
                                    )
                                )
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = popupMaxHeight)
                                .padding(vertical = 12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "ВЫБОР СЕРВЕРА",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = nebulaColors.textTertiary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )

                                    val interval = autoUpdateInterval
                                    Text(
                                        t(
                                            "${serverCountRu(servers.size)} • ${interval}ч",
                                            "${serverCountEn(servers.size)} • ${interval}h"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = nebulaColors.textTertiary.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            itemsIndexed(
                                items = servers,
                                key = { index, server ->
                                    "${server.host}:${server.port}:${server.uuid}:${server.templateUuid.orEmpty()}:${server.templateName.orEmpty()}_$index"
                                }
                            ) { _, server ->
                                val flag = extractFlagEmoji(server.name)
                                val name = cleanServerName(server.name)
                                val isSelected = server == selectedServer
                                val protocol = primaryServerBadge(server)
                                val showJsonBadge = showJsonForServer(server)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onServerSelected(server)
                                            onExpandedChange(false)
                                        }
                                        .background(
                                            if (isSelected) nebulaColors.textPrimary.copy(
                                                alpha = 0.07f
                                            ) else Color.Transparent
                                        )
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = flag.ifEmpty { "🌐" },
                                        fontSize = 19.sp,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) nebulaColors.accent else nebulaColors.textPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ServerTagChip(
                                                text = protocol,
                                                containerColor = nebulaColors.textPrimary.copy(alpha = 0.08f),
                                                borderColor = nebulaColors.textPrimary.copy(alpha = 0.15f),
                                                textColor = nebulaColors.textSecondary
                                            )
                                            if (showJsonBadge) {
                                                ServerTagChip(
                                                    text = "JSON",
                                                    containerColor = Color(0x33FFC107),
                                                    borderColor = Color(0x66FFC107),
                                                    textColor = Color(0xFFFFD54F)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val ping = serverPings[server.pingKey()] ?: -1
                                        val showLoading = isPinging && ping == -1

                                        val pingColor = when {
                                            showLoading -> nebulaColors.textTertiary.copy(alpha = 0.5f)
                                            ping == -1 -> nebulaColors.statusDisconnected
                                            ping <= 70 -> nebulaColors.statusConnected
                                            ping <= 120 -> Color(0xFFCDDC39)
                                            ping <= 220 -> Color(0xFFFF9800)
                                            else -> nebulaColors.statusDisconnected
                                        }

                                        val infiniteTransition = rememberInfiniteTransition(label = "ping_loading_list")
                                        val alpha by if (showLoading) {
                                            infiniteTransition.animateFloat(
                                                initialValue = 0.4f,
                                                targetValue = 1.0f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(800, easing = LinearEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "ping_alpha"
                                            )
                                        } else remember { mutableStateOf(1f) }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 8.dp).alpha(alpha)
                                        ) {
                                            Icon(
                                                Icons.Default.Speed,
                                                null,
                                                tint = pingColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            if (pingDisplayMode == 2) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(pingColor)
                                                )
                                            } else {
                                                Text(
                                                    text = if (showLoading) "..."
                                                           else if (pingDisplayMode == 1) {
                                                               if (ping == -1) "Ошибка" else "Доступен"
                                                           } else {
                                                               if (ping == -1) "н/д" else "${ping}мс"
                                                           },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = pingColor
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint = nebulaColors.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun ServerTagChip(
    text: String,
    containerColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .widthIn(min = if (text == "JSON") 44.dp else 0.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun IPInfoWidget(
    ipCountry: String?,
    ipCountryFlag: String?,
    currentIpAddress: String?,
    serverIpAddress: String?,
    isVpnConnected: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier // 👈 ВАЖНО
) {
    val nebulaColors = LocalNebulaColors.current
    val displayIp = currentIpAddress ?: serverIpAddress
    val ipLabel = if (isVpnConnected) "Текущий VPN IP" else "Текущий IP"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        nebulaColors.accent.copy(alpha = 0.08f),
                        nebulaColors.textPrimary.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                0.5.dp,
                nebulaColors.accent.copy(alpha = 0.22f),
                RoundedCornerShape(18.dp)
            )
            .clickable {
                // Копируем IP или просто даем фидбек
                onRefresh()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = ipCountryFlag ?: "🌐", fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ipCountry ?: "Определение...",
                    style = MaterialTheme.typography.titleSmall,
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ipLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = nebulaColors.textTertiary
                )
                Text(
                    text = displayIp ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = nebulaColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Виджет скорости — отдельная карточка
@Composable
fun CategoryHeader(title: String, icon: ImageVector) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = nebulaColors.accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = nebulaColors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SpeedCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        color.copy(alpha = 0.10f),
                        nebulaColors.textPrimary.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                0.5.dp,
                color.copy(alpha = 0.25f),
                RoundedCornerShape(18.dp)
            )
            .clickable { /* Просто для анимации блока */ }
    ) {
        // Glow слой
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf<Color>(color.copy(alpha = 0.08f), Color.Transparent),
                        radius = 400f
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = 0.25f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = nebulaColors.textTertiary
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Виджет действий с сервером — 4 кнопки в ряд
@Composable
fun ServerActionsCard(
    servers: List<Server>,
    serverPings: Map<String, Int>,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onServers: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        nebulaColors.textPrimary.copy(alpha = 0.08f),
                        nebulaColors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.dp,
                nebulaColors.textPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(18.dp)
            )
    ) {
        val nebulaColors = LocalNebulaColors.current
        // Glow слой
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            nebulaColors.accent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),

                        radius = 400f
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обновить — голубой
            ActionButton(
                icon = Icons.Default.Refresh,
                label = "Обновить",
                color = nebulaColors.accent
            ) { onRefresh() }

            // Пинг — фиолетовый
            ActionButton(
                icon = Icons.Default.Speed,
                label = "Пинг",
                color = nebulaColors.accent
            ) { onPing() }

            // Серверы — розовый
            ActionButton(
                icon = Icons.Default.Dns,
                label = "Серверы",
                color = nebulaColors.accent
            ) { onServers() }

            // Добавить — зелёный
            ActionButton(
                icon = Icons.Default.Add,
                label = "Добавить",
                color = nebulaColors.statusConnected
            ) { onAdd() }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = nebulaColors.textTertiary,
            maxLines = 1
        )
    }
}

@Composable
private fun StyledShieldStatusIcon(
    style: Int,
    color: Color,
    glowEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        when (style) {
            1 -> {
                Icon(Icons.Default.PowerSettingsNew, null, tint = color, modifier = Modifier.size(24.dp))
            }
            2 -> {
                Icon(Icons.Outlined.Shield, null, tint = color, modifier = Modifier.size(24.dp))
            }
            3 -> {
                Icon(Icons.Outlined.Shield, null, tint = color, modifier = Modifier.size(24.dp))
            }
            4 -> {
                Icon(Icons.Outlined.Shield, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                Icon(Icons.Default.ElectricBolt, null, tint = color, modifier = Modifier.size(14.dp))
            }
            5 -> {
                Icon(Icons.Default.RadioButtonChecked, null, tint = color.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                Icon(Icons.Default.PowerSettingsNew, null, tint = color, modifier = Modifier.size(12.dp))
            }
            6 -> {
                Icon(Icons.Outlined.Shield, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                Icon(Icons.Default.FlashOn, null, tint = color, modifier = Modifier.size(14.dp))
            }
            7 -> {
                Icon(Icons.Default.RadioButtonUnchecked, null, tint = color.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
                Icon(Icons.Default.Circle, null, tint = color.copy(alpha = 0.55f), modifier = Modifier.size(10.dp))
            }
            8 -> {
                Icon(Icons.Outlined.Shield, null, tint = color.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
                Icon(Icons.Default.RadioButtonUnchecked, null, tint = color.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
                Icon(Icons.Default.ElectricBolt, null, tint = color.copy(alpha = 0.9f), modifier = Modifier.size(10.dp))
            }
            else -> {
                Icon(Icons.Outlined.Shield, null, tint = color.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
                if (glowEnabled) {
                    Icon(Icons.Default.ElectricBolt, null, tint = color.copy(alpha = 0.35f), modifier = Modifier.size(14.dp).offset(y = 1.dp))
                }
                Icon(Icons.Default.ElectricBolt, null, tint = color, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun VpnConnectionButtonNoText(
    state: VpnState,
    isRefreshing: Boolean = false,
    sizeScale: Float = 1f,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val pm_internal = remember { com.danila.nimbo.utils.PreferencesManager(context) }
    val glowEnabled_internal by pm_internal.glowEffectsEnabledState
    val connectButtonStyle by pm_internal.connectButtonStyleState
    val safeScale = sizeScale.coerceIn(0.50f, 2.00f)
    val animatedSizeScale by animateFloatAsState(
        targetValue = safeScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "connect_button_size_scale"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    val buttonColor = when (state) {
        VpnState.CONNECTED -> nebulaColors.statusConnected
        VpnState.CONNECTING -> nebulaColors.statusConnecting
        else -> nebulaColors.accent
    }


    // 🔥 Анимация пульсации при подключении (сглаженная)
    val pulseTargetScale = if (state == VpnState.CONNECTED) 1.08f else 1f
    val animatedPulseTargetScale by animateFloatAsState(
        targetValue = pulseTargetScale,
        animationSpec = tween(500),
        label = "smooth_pulse_target"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = animatedPulseTargetScale,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (state == VpnState.CONNECTED) 0.22f else 0.30f,
        targetValue = if (state == VpnState.CONNECTED) 0.05f else 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val connectedBreathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VpnState.CONNECTED) 1.035f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connected_breath_scale"
    )

    // Вращение линий обновления
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation"
    )
    val styleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "style_rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .scale(animatedSizeScale)
            .graphicsLayer { clip = false }
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = interactionSource
            )
    ) {
        // 🔥 Линии обновления (крутящиеся линии вокруг кнопки)
        if (isRefreshing) {
            repeat(3) { index ->
                Canvas(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                    drawArc(
                        color = nebulaColors.accent.copy(alpha = 0.8f),

                        startAngle = refreshRotation + (index * 120f),
                        sweepAngle = 60f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            4.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
        }
        // 🔥 Внешнее пульсирующее свечение (исправлено - без обрезки)
        if (state == VpnState.CONNECTED) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .background(
                        buttonColor.copy(alpha = pulseAlpha * 0.4f),
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .size(172.dp)
                    .scale(connectedBreathScale)
                    .border(
                        width = 1.5.dp,
                        color = buttonColor.copy(alpha = 0.28f),
                        shape = CircleShape
                    )
            )
        }

        // Главное свечение (фон) — Canvas (работает через RenderNode, не перекомпозицию)
        Canvas(modifier = Modifier.size(240.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(buttonColor.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2
                )
            )
        }

        // Кольцо и иконка (с объемом)
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(connectedBreathScale)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf<Color>(
                            nebulaColors.textPrimary.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
                .border(2.dp, nebulaColors.textPrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Внутренний легкий блик сверху
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf<Color>(
                                nebulaColors.textPrimary.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 100f
                        ),
                        shape = CircleShape
                    )
            )

            val connected = state == VpnState.CONNECTED
            AnimatedContent(
                targetState = connectButtonStyle to connected,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "connect_button_style_anim"
            ) { (style, isConnected) ->
                if (isConnected) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                        when (style) {
                            1 -> {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    null,
                                    tint = buttonColor,
                                    modifier = Modifier.size(74.dp).rotate(styleRotation)
                                )
                            }
                            2 -> {
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor, modifier = Modifier.size(72.dp))
                            }
                            3 -> {
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor.copy(alpha = 0.3f), modifier = Modifier.size(82.dp))
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor, modifier = Modifier.size(74.dp))
                            }
                            4 -> {
                                Icon(Icons.Default.PowerSettingsNew, null, tint = buttonColor.copy(alpha = 0.85f), modifier = Modifier.size(70.dp))
                                Icon(Icons.Default.ElectricBolt, null, tint = nebulaColors.accent, modifier = Modifier.size(34.dp).scale(pulseScale))
                            }
                            5 -> {
                                Icon(Icons.Default.RadioButtonUnchecked, null, tint = buttonColor.copy(alpha = 0.7f), modifier = Modifier.size(78.dp).rotate(-styleRotation))
                                Icon(Icons.Default.PowerSettingsNew, null, tint = buttonColor, modifier = Modifier.size(52.dp))
                            }
                            6 -> {
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor.copy(alpha = 0.85f), modifier = Modifier.size(76.dp))
                                Icon(Icons.Default.FlashOn, null, tint = nebulaColors.accent, modifier = Modifier.size(32.dp).offset(x = (-8).dp, y = 2.dp))
                                Icon(Icons.Default.FlashOn, null, tint = nebulaColors.accent.copy(alpha = 0.85f), modifier = Modifier.size(26.dp).offset(x = 8.dp, y = (-3).dp))
                            }
                            7 -> {
                                Icon(Icons.Default.RadioButtonUnchecked, null, tint = buttonColor.copy(alpha = 0.45f), modifier = Modifier.size(84.dp).rotate(styleRotation))
                                Icon(Icons.Default.RadioButtonUnchecked, null, tint = buttonColor.copy(alpha = 0.7f), modifier = Modifier.size(62.dp).rotate(-styleRotation))
                                Icon(Icons.Default.Circle, null, tint = buttonColor, modifier = Modifier.size(20.dp))
                            }
                            8 -> {
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor.copy(alpha = 0.35f), modifier = Modifier.size(82.dp))
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor.copy(alpha = 0.75f), modifier = Modifier.size(64.dp))
                                Icon(Icons.Default.ElectricBolt, null, tint = buttonColor.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                            }
                            else -> {
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor.copy(alpha = 0.15f), modifier = Modifier.size(82.dp))
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor, modifier = Modifier.size(76.dp))
                                if (glowEnabled_internal) {
                                    Icon(
                                        Icons.Default.ElectricBolt,
                                        null,
                                        tint = Color.Black.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp).offset(x = 1.dp, y = 3.dp)
                                    )
                                }
                                Icon(Icons.Default.ElectricBolt, null, tint = nebulaColors.accent, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                } else {
                    when (style) {
                        2, 3 -> Icon(Icons.Outlined.Shield, null, tint = buttonColor, modifier = Modifier.size(70.dp))
                        5 -> Icon(Icons.Default.RadioButtonUnchecked, null, tint = buttonColor, modifier = Modifier.size(70.dp))
                        6 -> Icon(Icons.Default.FlashOn, null, tint = buttonColor, modifier = Modifier.size(70.dp))
                        7 -> {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.RadioButtonUnchecked, null, tint = buttonColor.copy(alpha = 0.85f), modifier = Modifier.size(76.dp))
                                Icon(Icons.Default.Circle, null, tint = buttonColor.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                            }
                        }
                        8 -> {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Shield, null, tint = buttonColor.copy(alpha = 0.65f), modifier = Modifier.size(72.dp))
                                Icon(Icons.Default.Circle, null, tint = buttonColor.copy(alpha = 0.45f), modifier = Modifier.size(10.dp))
                            }
                        }
                        4 -> Icon(Icons.Default.ElectricBolt, null, tint = buttonColor, modifier = Modifier.size(70.dp))
                        else -> Icon(Icons.Default.PowerSettingsNew, null, tint = buttonColor, modifier = Modifier.size(76.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectHintWidget() {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf<Color>(
                        nebulaColors.textPrimary.copy(alpha = 0.06f),
                        nebulaColors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.dp,
                nebulaColors.textPrimary.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Нажмите кнопку для подключения",
            style = MaterialTheme.typography.bodyMedium,
            color = nebulaColors.textTertiary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(18.dp))
            .background(nebulaColors.textPrimary.copy(alpha = 0.04f))
            .border(
                0.5.dp,
                nebulaColors.accent.copy(alpha = 0.15f),
                RoundedCornerShape(18.dp)
            ).padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(
                    Brush.radialGradient(
                        colors = listOf<Color>(
                            color.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ), shape = RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = nebulaColors.textSecondary
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.textPrimary
                )
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = nebulaColors.textSecondary
                )
            }
        }
    }
}

@Composable
fun ComboStatsWidget(profile: SubscriptionProfile) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        nebulaColors.textPrimary.copy(alpha = 0.08f),
                        nebulaColors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.dp,
                nebulaColors.textPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SettingsInputComponent,
                    null,
                    tint = LocalNebulaColors.current.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Статус аккаунта",
                    style = MaterialTheme.typography.titleSmall,
                    color = nebulaColors.textPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Дни
                ComboStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Event,
                    label = "Осталось",
                    value = if (profile.daysUntilExpiry == -1L) "∞" else "${profile.daysUntilExpiry}",
                    unit = "дн.",
                    color = LocalNebulaColors.current.accent
                )
                // Устройства
                ComboStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Devices,
                    label = "Устройства",
                    value = "${profile.onlineDevices} / ${profile.deviceLimit}",
                    unit = "шт.",
                    color = LocalNebulaColors.current.accent
                )
            }

            // Трафик
            val used = formatBytes(profile.downloadTotal + profile.uploadTotal)
            val isUnlimited = profile.totalTraffic <= 0

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDownload,
                            null,
                            tint = nebulaColors.textTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Использовано трафика",
                            style = MaterialTheme.typography.labelSmall,
                            color = nebulaColors.textTertiary
                        )
                    }
                    if (isUnlimited) {
                        Text(
                            "$used / ∞",
                            style = MaterialTheme.typography.labelSmall,
                            color = nebulaColors.textSecondary
                        )
                    } else {
                        val total = formatBytes(profile.totalTraffic)
                        Text(
                            "$used / $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = nebulaColors.textSecondary
                        )
                    }
                }

                if (!isUnlimited && profile.totalTraffic > 0) {
                    val progress =
                        ((profile.downloadTotal + profile.uploadTotal).toFloat() / profile.totalTraffic.toFloat()).coerceIn(
                            0f,
                            1f
                        )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = LocalNebulaColors.current.accent,
                        trackColor = nebulaColors.textPrimary.copy(alpha = 0.05f)
                    )
                } else {
                    // Декоративная полоса для безлимита
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf<Color>(
                                        LocalNebulaColors.current.accent.copy(
                                            alpha = 0.3f
                                        ), LocalNebulaColors.current.accent.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )
                }
            }

        }
    }
}

@Composable
fun ComboStatItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Column {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = nebulaColors.textTertiary
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.textPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = nebulaColors.textTertiary
                )
            }
        }
    }
}

@Composable
fun SpeedStatsWidget(downSpeed: String, upSpeed: String) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        nebulaColors.textPrimary.copy(alpha = 0.08f),
                        nebulaColors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.dp,
                nebulaColors.textPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SpeedStatItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Download,
                label = "Download",
                value = downSpeed,
                color = LocalNebulaColors.current.accent
            )
            SpeedStatItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Upload,
                label = "Upload",
                value = upSpeed,
                color = LocalNebulaColors.current.accent
            )
        }
    }
}

@Composable
fun SpeedStatItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    val nebulaColors = LocalNebulaColors.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = nebulaColors.textTertiary
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        color = nebulaColors.textPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "МБ/с",
                        style = MaterialTheme.typography.bodySmall,
                        color = nebulaColors.textTertiary
                    )
                }
            }
        }

        // 🔥 Имитация мини-графика
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.03f))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight().background(
                    Brush.horizontalGradient(
                        colors = listOf<Color>(
                            color.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
            )
        }
    }
}

@Composable
fun VpnStatusPreview() {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(156.dp)
                                                                        .clip(RoundedCornerShape(20.dp))
            .background(nebulaColors.textPrimary.copy(alpha = 0.04f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(StatusConnected.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Shield,
                null,
                tint = StatusConnected,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "Защищено",
                color = StatusConnected,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "00:42:15",
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MemoryMonitor() {
    val nebulaColors = LocalNebulaColors.current
    var memoryUsage by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
            val nativeMemInMB = Debug.getNativeHeapAllocatedSize() / 1048576L
            memoryUsage = usedMemInMB + nativeMemInMB
            kotlinx.coroutines.delay(2000)
        }
    }

    val status = when {
        memoryUsage < 150 -> "Норма"
        memoryUsage < 300 -> "Повышено"
        else -> "Высокое"
    }

    val isHigh = memoryUsage >= 300
    val statusColor = if (isHigh) nebulaColors.statusError else nebulaColors.accent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(nebulaColors.textPrimary.copy(alpha = 0.03f))
            .border(
                1.dp,
                statusColor.copy(alpha = 0.12f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        statusColor.copy(alpha = 0.28f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Память приложения",
                    style = MaterialTheme.typography.labelSmall,
                    color = nebulaColors.textSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = statusColor,
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${memoryUsage} MB  •  ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = nebulaColors.textPrimary
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = nebulaColors.textPrimary.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = nebulaColors.textTertiary
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = nebulaColors.accent,
                    checkedTrackColor = nebulaColors.accent.copy(alpha = 0.4f)
                )
            )
        }
    }
}
