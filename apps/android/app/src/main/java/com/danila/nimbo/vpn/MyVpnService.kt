package com.danila.nimbo.vpn

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.danila.nimbo.model.Server
import com.danila.nimbo.model.NetworkEventSeverity
import com.danila.nimbo.model.NetworkEventType
import com.danila.nimbo.model.TrafficBudgetAction
import com.danila.nimbo.network.CaptivePortalPolicy
import com.danila.nimbo.network.NetworkContextSnapshot
import com.danila.nimbo.network.NetworkEventJournal
import com.danila.nimbo.network.NetworkTransport
import com.danila.nimbo.network.SmartServerGroupStore
import com.danila.nimbo.network.TrafficBudgetPolicy
import com.danila.nimbo.network.TrafficBudgetStore
import com.danila.nimbo.network.TemporaryAppRuleManager
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.network.RemnawaveApiClient
import com.danila.nimbo.network.PingConfig
import com.danila.nimbo.network.PingManager
import com.danila.nimbo.network.PingProtocol
import com.danila.nimbo.service.DeviceStatsQuickSettingsTileService
import com.danila.nimbo.service.ProfileStatsQuickSettingsTileService
import com.danila.nimbo.service.TrafficStatsQuickSettingsTileService
import com.danila.nimbo.service.VpnQuickSettingsTileService
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.ui.components.performConnectionSuccessHaptic
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.NetworkProfileManager
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.TrafficHistory
import com.danila.nimbo.utils.isAutoBalancerServer
import com.danila.nimbo.utils.isBypassServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.danila.nimbo.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.danila.nimbo.vpn.DISCONNECT"
        const val ACTION_PAUSE = "com.danila.nimbo.vpn.PAUSE"
        const val ACTION_RELOAD_CONFIGURATION = "com.danila.nimbo.vpn.RELOAD_CONFIGURATION"
        const val EXTRA_SERVER = "server"

        const val EXTRA_SERVER_HOST = "server_host"
        const val EXTRA_SERVER_PORT = "server_port"
        const val EXTRA_SERVER_UUID = "server_uuid"
        const val EXTRA_SERVER_PROTOCOL = "server_protocol"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_FLOW = "server_flow"
        const val EXTRA_SERVER_SECURITY = "server_security"
        const val EXTRA_SERVER_NETWORK = "server_network"
        const val EXTRA_SERVER_PATH = "server_path"
        const val EXTRA_SERVER_SNI = "server_sni"
        const val EXTRA_SERVER_FINGERPRINT = "server_fingerprint"
        const val EXTRA_SERVER_ALPN = "server_alpn"
        const val EXTRA_SERVER_PUBLIC_KEY = "server_public_key"
        const val EXTRA_SERVER_SHORT_ID = "server_short_id"
        const val EXTRA_SERVER_SPIDER_X = "server_spider_x"
        const val EXTRA_SERVER_TLS = "server_tls"
        const val EXTRA_SERVER_PROFILE_URL = "server_profile_url"
        const val EXTRA_SERVER_TEMPLATE_UUID = "server_template_uuid"
        const val EXTRA_SERVER_TEMPLATE_NAME = "server_template_name"
        const val EXTRA_SERVER_HYSTERIA_OBFS = "server_hysteria_obfs"
        const val EXTRA_SERVER_HYSTERIA_OBFS_PASSWORD = "server_hysteria_obfs_password"
        const val EXTRA_SERVER_HYSTERIA_PORTS = "server_hysteria_ports"
        const val EXTRA_SERVER_HYSTERIA_HOP_INTERVAL = "server_hysteria_hop_interval"
        const val EXTRA_SERVER_HYSTERIA_UP = "server_hysteria_up"
        const val EXTRA_SERVER_HYSTERIA_DOWN = "server_hysteria_down"
        const val EXTRA_SERVER_HYSTERIA_CONGESTION = "server_hysteria_congestion"
        const val EXTRA_SERVER_NAIVE_USERNAME = "server_naive_username"
        const val EXTRA_SERVER_NAIVE_PASSWORD = "server_naive_password"
        const val EXTRA_SERVER_NAIVE_TRANSPORT = "server_naive_transport"
        const val EXTRA_SERVER_WG_PRIVATE_KEY = "server_wg_private_key"
        const val EXTRA_SERVER_WG_PUBLIC_KEY = "server_wg_public_key"
        const val EXTRA_SERVER_WG_PRESHARED_KEY = "server_wg_preshared_key"
        const val EXTRA_SERVER_WG_ADDRESS = "server_wg_address"
        const val EXTRA_SERVER_WG_ALLOWED_IPS = "server_wg_allowed_ips"
        const val EXTRA_SERVER_WG_DNS = "server_wg_dns"
        const val EXTRA_SERVER_WG_MTU = "server_wg_mtu"
        const val EXTRA_SERVER_WG_KEEPALIVE = "server_wg_keepalive"
        const val EXTRA_SERVER_AWG_JC = "server_awg_jc"
        const val EXTRA_SERVER_AWG_JMIN = "server_awg_jmin"
        const val EXTRA_SERVER_AWG_JMAX = "server_awg_jmax"
        const val EXTRA_SERVER_AWG_S1 = "server_awg_s1"
        const val EXTRA_SERVER_AWG_S2 = "server_awg_s2"
        const val EXTRA_SERVER_AWG_S3 = "server_awg_s3"
        const val EXTRA_SERVER_AWG_S4 = "server_awg_s4"
        const val EXTRA_SERVER_AWG_H1 = "server_awg_h1"
        const val EXTRA_SERVER_AWG_H2 = "server_awg_h2"
        const val EXTRA_SERVER_AWG_H3 = "server_awg_h3"
        const val EXTRA_SERVER_AWG_H4 = "server_awg_h4"
        const val EXTRA_SERVER_AWG_I1 = "server_awg_i1"
        const val EXTRA_SERVER_AWG_I2 = "server_awg_i2"
        const val EXTRA_SERVER_AWG_I3 = "server_awg_i3"
        const val EXTRA_SERVER_AWG_I4 = "server_awg_i4"
        const val EXTRA_SERVER_AWG_I5 = "server_awg_i5"

        private const val TAG = "MyVpnService"
        private const val FORCE_LOCAL_MANUAL_ROUTING_TEST = false
        private const val AUTO_BALANCER_RETRY_DELAY_MS = 350L
        private const val POST_CONNECT_STABILIZATION_MS = 250L
        private const val RECOVERY_POST_CONNECT_STABILIZATION_MS = 100L
        private const val UNDERLYING_NETWORK_DEBOUNCE_MS = 350L
        private const val UNDERLYING_NETWORK_SETTLE_MS = 350L
        private const val INTERNET_WAIT_POLL_MS = 300L
        private const val MANUAL_INTERNET_WAIT_ATTEMPTS = 12
        private const val RECOVERY_INTERNET_WAIT_ATTEMPTS = 4
        private const val AWG_HANDSHAKE_WAIT_MS = 8_000L
        private const val AWG_HANDSHAKE_POLL_MS = 500L
        private const val BACKGROUND_MAINTENANCE_INTERVAL_TICKS = 30
        private const val NOTIFICATION_UPDATE_INTERVAL_TICKS = 1

        fun createConnectIntent(context: android.content.Context, server: Server): Intent {
            return Intent(context, MyVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER, server)
                putExtra(EXTRA_SERVER_HOST, server.host)
                putExtra(EXTRA_SERVER_PORT, server.port)
                putExtra(EXTRA_SERVER_UUID, server.uuid)
                putExtra(EXTRA_SERVER_PROTOCOL, server.protocol)
                putExtra(EXTRA_SERVER_NAME, server.name)
                putExtra(EXTRA_SERVER_FLOW, server.flow ?: "")
                putExtra(EXTRA_SERVER_SECURITY, server.security ?: "")
                putExtra(EXTRA_SERVER_NETWORK, server.network ?: "")
                putExtra(EXTRA_SERVER_PATH, server.path ?: "")
                putExtra(EXTRA_SERVER_SNI, server.sni ?: "")
                putExtra(EXTRA_SERVER_FINGERPRINT, server.fingerprint ?: "")
                putExtra(EXTRA_SERVER_ALPN, server.alpn ?: "")
                putExtra(EXTRA_SERVER_PUBLIC_KEY, server.publicKey ?: "")
                putExtra(EXTRA_SERVER_SHORT_ID, server.shortId ?: "")
                putExtra(EXTRA_SERVER_SPIDER_X, server.spiderX ?: "")
                putExtra(EXTRA_SERVER_TLS, server.tls ?: false)
                putExtra(EXTRA_SERVER_PROFILE_URL, server.profileUrl ?: "")
                putExtra(EXTRA_SERVER_TEMPLATE_UUID, server.templateUuid ?: "")
                putExtra(EXTRA_SERVER_TEMPLATE_NAME, server.templateName ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_OBFS, server.hysteriaObfs ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_OBFS_PASSWORD, server.hysteriaObfsPassword ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_PORTS, server.hysteriaPorts ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_HOP_INTERVAL, server.hysteriaHopInterval ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_UP, server.hysteriaUp ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_DOWN, server.hysteriaDown ?: "")
                putExtra(EXTRA_SERVER_HYSTERIA_CONGESTION, server.hysteriaCongestion ?: "")
                putExtra(EXTRA_SERVER_NAIVE_USERNAME, server.naiveUsername ?: "")
                putExtra(EXTRA_SERVER_NAIVE_PASSWORD, server.naivePassword ?: "")
                putExtra(EXTRA_SERVER_NAIVE_TRANSPORT, server.naiveTransport ?: "")
                putExtra(EXTRA_SERVER_WG_PRIVATE_KEY, server.wgPrivateKey ?: "")
                putExtra(EXTRA_SERVER_WG_PUBLIC_KEY, server.wgPublicKey ?: "")
                putExtra(EXTRA_SERVER_WG_PRESHARED_KEY, server.wgPresharedKey ?: "")
                putExtra(EXTRA_SERVER_WG_ADDRESS, server.wgAddress ?: "")
                putExtra(EXTRA_SERVER_WG_ALLOWED_IPS, server.wgAllowedIps ?: "")
                putExtra(EXTRA_SERVER_WG_DNS, server.wgDns ?: "")
                putExtra(EXTRA_SERVER_WG_MTU, server.wgMtu ?: 0)
                putExtra(EXTRA_SERVER_WG_KEEPALIVE, server.wgKeepAlive ?: 0)
                putExtra(EXTRA_SERVER_AWG_JC, server.awgJc ?: 0)
                putExtra(EXTRA_SERVER_AWG_JMIN, server.awgJmin ?: 0)
                putExtra(EXTRA_SERVER_AWG_JMAX, server.awgJmax ?: 0)
                putExtra(EXTRA_SERVER_AWG_S1, server.awgS1 ?: 0)
                putExtra(EXTRA_SERVER_AWG_S2, server.awgS2 ?: 0)
                putExtra(EXTRA_SERVER_AWG_S3, server.awgS3 ?: 0)
                putExtra(EXTRA_SERVER_AWG_S4, server.awgS4 ?: 0)
                putExtra(EXTRA_SERVER_AWG_H1, server.awgH1 ?: "")
                putExtra(EXTRA_SERVER_AWG_H2, server.awgH2 ?: "")
                putExtra(EXTRA_SERVER_AWG_H3, server.awgH3 ?: "")
                putExtra(EXTRA_SERVER_AWG_H4, server.awgH4 ?: "")
                putExtra(EXTRA_SERVER_AWG_I1, server.awgI1 ?: "")
                putExtra(EXTRA_SERVER_AWG_I2, server.awgI2 ?: "")
                putExtra(EXTRA_SERVER_AWG_I3, server.awgI3 ?: "")
                putExtra(EXTRA_SERVER_AWG_I4, server.awgI4 ?: "")
                putExtra(EXTRA_SERVER_AWG_I5, server.awgI5 ?: "")
            }
        }

        fun startConnectService(context: Context, server: Server) {
            val intent = createConnectIntent(context, server)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // On Android 12+ a plain background context (e.g. a process spun up just for the
                // restore) may not be allowed to start a foreground service. Route through
                // QuickConnectActivity, which can (re)request VPN consent and start the FGS from an
                // activity context — so background/cold-start connects don't silently fail.
                Logger.e(TAG, "Failed to start VPN connect service, falling back to QuickConnectActivity", e)
                runCatching {
                    val activityIntent = Intent(context, com.danila.nimbo.QuickConnectActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtras(intent)
                    }
                    context.startActivity(activityIntent)
                }.onFailure { Logger.e(TAG, "Fallback QuickConnect launch failed", it) }
            }
        }

        /** Rebuilds the native core and TUN only when a VPN session is already active. */
        fun requestConfigurationReload(context: Context): Boolean {
            if (!RoutingRuntimePolicy.shouldReloadTunnel(VpnManager.state.value)) return false

            val intent = Intent(context, MyVpnService::class.java).apply {
                action = ACTION_RELOAD_CONFIGURATION
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.onFailure { error ->
                Logger.e(TAG, "Не удалось применить новые правила маршрутизации", error)
            }.getOrDefault(false)
        }
    }

    private var isConnected = false
    private var isConnecting = false
    private var currentServerHost: String = ""
    private var currentServerName: String = ""
    private var currentServer: Server? = null
    private var currentProfileName: String? = null
    private var currentProfileLogoBitmap: android.graphics.Bitmap? = null
    private var connectionStatusOverride: String? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var connectionJob: Job? = null
    private var timerJob: Job? = null
    private var recoveryJob: Job? = null
    private var networkDebounceJob: Job? = null
    private var networkHandoffJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val underlyingNetworkLock = Any()
    private var selectedUnderlyingNetwork: Network? = null
    private var selectedUnderlyingCapabilities: NetworkCapabilities? = null
    private var lastUnderlyingNetworkHandle: Long? = null
    private var lastUnderlyingTransportName: String = "none"
    private var recoveryState = VpnRecoveryPolicy.State()
    private var captivePortalState = CaptivePortalPolicy.State()
    private var serviceStopping = false

    private val handler = Handler(Looper.getMainLooper())
    private var connectionTimeoutRunnable: Runnable? = null
    
    private lateinit var customNotificationManager: com.danila.nimbo.utils.NotificationManager
    private lateinit var preferencesManager: PreferencesManager
    private var lastConnectedServer: Server? = null
    private val candidateFailedAtMs = mutableMapOf<String, Long>()

    // WakeLock для работы в фоне
    private var wakeLock: WakeLock? = null

    // BroadcastReceiver для блокировки/разблокировки экрана
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        preferencesManager = PreferencesManager(this)
        val networkAvailable = hasUsableUnderlyingNetwork()
        recoveryState = VpnRecoveryPolicy.State(
            desiredConnected = preferencesManager.vpnConnectionDesired,
            phase = when {
                preferencesManager.vpnPausedByScreen -> VpnRecoveryPolicy.Phase.PAUSED_BY_SCREEN
                preferencesManager.vpnConnectionDesired && !networkAvailable ->
                    VpnRecoveryPolicy.Phase.WAITING_FOR_NETWORK
                else -> VpnRecoveryPolicy.Phase.DISCONNECTED
            },
            screenPaused = preferencesManager.vpnPausedByScreen,
            networkAvailable = networkAvailable
        )
        com.danila.nimbo.utils.NotificationManager.createNotificationChannels(this)
        registerScreenReceiver()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Start command with action: $action")

        when (action) {
            ACTION_CONNECT -> {
                val previousServer = currentServer
                val wasActive = isConnected || isConnecting
                currentServer = extractServer(intent)
                    ?: VpnManager.selectedServer
                    ?: preferencesManager.loadLastSelectedServer()
                val target = currentServer
                if (target == null) {
                    Logger.e(TAG, "Connect request ignored: no server is available")
                    requestManualStop("no server")
                    return START_NOT_STICKY
                }

                // В Android allow-list VPN включается только после первого
                // addAllowedApplication(). Пустой (либо состоящий из удалённых
                // приложений) список иначе означает «через VPN идут все», что
                // перехватывает DNS у программ, которые должны остаться direct.
                if (!hasSelectedVpnOnlyApplication()) {
                    Logger.w(TAG, "VPN-only mode has no installed selected apps; tunnel was not started to keep direct DNS working")
                    requestManualStop("vpn-only app list is empty")
                    return START_NOT_STICKY
                }

                preferencesManager.vpnConnectionDesired = true
                preferencesManager.vpnPausedByScreen = false
                recoveryState = VpnRecoveryPolicy.reduce(
                    recoveryState,
                    VpnRecoveryPolicy.Event.ManualConnect(hasServer = true)
                ).state
                VpnManager.recoveryStatus.value = VpnRecoveryStatus.IDLE
                VpnManager.recoveryAttempt.value = 0
                cancelRecoveryJob()

                currentServerHost = currentServer?.host ?: currentServer?.name.orEmpty()
                currentServerName = currentServer?.name ?: currentServer?.host.orEmpty()

                Logger.i(TAG, "VPN connect request received")

                // Кешируем имя профиля и логотип один раз при подключении.
                // Профиль ищем устойчиво: по profileUrl сервера, иначе по принадлежности сервера к
                // профилю (uuid / host:port), иначе по последнему выбранному профилю. Это важно для
                // уведомления, где он отображается отдельной иконкой справа.
                val profiles = preferencesManager.loadProfiles()
                val srv = currentServer
                val matchedProfile = srv?.profileUrl?.takeIf { it.isNotBlank() }
                        ?.let { u -> profiles.find { it.url == u } }
                    ?: profiles.find { p ->
                        p.servers.any { s ->
                            (srv?.uuid != null && srv.uuid.isNotBlank() && s.uuid == srv.uuid) ||
                                (srv != null && s.host == srv.host && s.port == srv.port)
                        }
                    }
                    ?: preferencesManager.loadLastSelectedProfileUrl()
                        ?.let { u -> profiles.find { it.url == u } }
                currentProfileName = matchedProfile?.let { it.name ?: it.username }

                currentProfileLogoBitmap = null
                val logoStr = if (matchedProfile != null && preferencesManager.showSubscriptionLogo) {
                    com.danila.nimbo.utils.SubscriptionLogoCache.displayLogo(
                        matchedProfile.brandLogo,
                        matchedProfile.brandLogoCache
                    )
                } else {
                    null
                }
                if (logoStr != null) {
                    serviceScope.launch {
                        val bitmap = com.danila.nimbo.utils.SubscriptionLogoCache.loadLogoBitmap(logoStr)
                        if (bitmap != null) {
                            currentProfileLogoBitmap = bitmap
                            refreshForegroundNotification()
                        } else {
                            Logger.w(TAG, "Subscription logo present but could not be decoded")
                        }
                    }
                }

                Log.d(TAG, "Connection profile metadata prepared (logo=${logoStr != null})")

                when {
                    // Обычное подключение из отключённого состояния.
                    !wasActive -> connect(target)
                    // Тот же сервер уже активен — переподключаться незачем.
                    previousServer != null &&
                        connectionCandidateKey(previousServer) == connectionCandidateKey(target) ->
                        Log.d(TAG, "Switch ignored: ${target.name} is already the active server")
                    // Уже подключены/подключаемся к другому серверу → смена «на лету».
                    else -> switchServer(target)
                }
            }
            ACTION_DISCONNECT -> {
                requestManualStop("disconnected")
            }
            ACTION_PAUSE -> {
                requestManualStop("paused")
            }
            ACTION_RELOAD_CONFIGURATION -> {
                val target = currentServer
                    ?: VpnManager.selectedServer
                    ?: preferencesManager.loadLastSelectedServer()
                if (target == null || (!isConnected && !isConnecting)) {
                    Logger.i(TAG, "Правила маршрутизации сохранены и будут применены при следующем подключении")
                } else {
                    Logger.i(TAG, "Применяем новые правила маршрутизации")
                    switchServer(target)
                }
            }
            else -> {
                restoreStickyService()
            }
        }

        return START_STICKY
    }

    /**
     * Возвращает false для пустых, вручную введённых и уже удалённых пакетов.
     * Такие записи нельзя передавать в Builder.addAllowedApplication(): Android
     * не активирует allow-list, если ни один пакет фактически не был добавлен.
     */
    private fun hasSelectedVpnOnlyApplication(): Boolean {
        return VpnTunPolicy.hasUsableVpnOnlySelection(
            proxyByApp = preferencesManager.proxyByApp,
            ownPackage = packageName,
            selectedPackages = preferencesManager.getAppVpnOnlyList(),
            isInstalled = { candidate ->
                runCatching {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(candidate, 0)
                }.isSuccess
            }
        )
    }

    /**
     * Подключение к серверу
     */
    private fun connect(currentServer: Server?, recoveryMode: Boolean = false) {
        if (isConnecting) {
            Log.d(TAG, "Already connecting")
            return
        }

        serviceStopping = false
        isConnecting = true
        clearLastConnectionError()
        VpnManager.state.value = VpnState.CONNECTING
        VpnManager.recoveryStatus.value = VpnRecoveryStatus.IDLE

        Log.d(TAG, "Starting VPN connection")
        Logger.i(TAG, "Начало подключения к VPN")

        // Создаём уведомление
        startForeground(com.danila.nimbo.utils.NotificationManager.NOTIFICATION_ID_VPN, createNotification())

        connectionJob = serviceScope.launch {
            try {
                // Проверяем интернет
                if (!waitForInternet(recoveryMode)) {
                    Log.e(TAG, "No internet connection")
                    Logger.e(TAG, "Отсутствие подключения к интернету")
                    handleConnectionFailure("No internet connection", retryable = true)
                    return@launch
                }

                // Получаем сервер
                val server = currentServer ?: run {
                    Log.e(TAG, "No server specified")
                    Logger.e(TAG, "Сервер не указан")
                    requestManualStop("no server")
                    return@launch
                }

                val effectiveServer = resolveServerForConnect(server)
                val cycleDeadlineMs = SystemClock.elapsedRealtime() +
                    ConnectionAttemptPolicy.TOTAL_CYCLE_BUDGET_MS
                val selectedIsAutoBalancer = isAutoBalancerServer(effectiveServer)
                val autoRotationPreferenceEnabled = preferencesManager.autoRotationEnabled
                val autoRotationEnabled = autoRotationPreferenceEnabled && !selectedIsAutoBalancer
                val networkRestricted = if (
                    ConnectionAttemptPolicy.shouldDetectRestrictedNetwork(
                        autoRotationEnabled = autoRotationPreferenceEnabled,
                        selectedIsAutoBalancer = selectedIsAutoBalancer
                    )
                ) {
                    isNetworkRestrictedForBypass()
                } else {
                    false
                }
                val candidates = buildConnectionCandidates(
                    baseServer = effectiveServer,
                    autoRotationEnabled = autoRotationEnabled,
                    networkRestricted = networkRestricted
                )
                // Do not start and tear down several tunnels merely to rank bypass
                // locations. Apart from making the first connection noticeably slower,
                // that pre-flight race could leave a freshly selected core in a stale
                // state. The stored ping/order is enough; real failures still move us
                // to the next candidate inside the bounded connection cycle below.
                val orderedCandidates = candidates
                if (selectedIsAutoBalancer) {
                    Log.i(
                        TAG,
                        "Auto-balancer selected: skip bypass pre-probe and server rotation for faster connect"
                    )
                }
                var connected = false
                var connectedServer: Server? = null
                val explicitSelectionKey = connectionCandidateKey(effectiveServer)

                for ((index, candidate) in orderedCandidates.withIndex()) {
                    val nowMs = SystemClock.elapsedRealtime()
                    val remainingMs = cycleDeadlineMs - nowMs
                    if (remainingMs <= 0L) {
                        Logger.w(TAG, "Общий лимит подключения исчерпан")
                        break
                    }
                    val candidateKey = connectionCandidateKey(candidate)
                    val explicitSelection = candidateKey == explicitSelectionKey
                    if (ConnectionAttemptPolicy.isCoolingDown(
                            failedAtMs = candidateFailedAtMs[candidateKey],
                            nowMs = nowMs,
                            explicitSelection = explicitSelection
                        )
                    ) {
                        Logger.i(TAG, "Пропускаем ${candidate.name}: сервер временно в cooldown после ошибки")
                        continue
                    }
                    Log.d(
                        TAG,
                        "Connecting candidate ${index + 1}/${orderedCandidates.size}: ${candidate.name} (${candidate.host}:${candidate.port})"
                    )
                    Logger.i(
                        TAG,
                        if (index == 0) {
                            "Подключение к серверу: ${candidate.name}"
                        } else {
                            "Переходим на резервный сервер: ${candidate.name}"
                        }
                    )
                    currentServerName = candidate.name
                    val isEnglish = preferencesManager.appLanguage == "en"
                    connectionStatusOverride = if (index == 0) {
                        if (isEnglish) "Checking server: ${candidate.name}" else "Проверяем сервер: ${candidate.name}"
                    } else {
                        if (isEnglish) "Switching to fallback: ${candidate.name}" else "Переход на резервный сервер: ${candidate.name}"
                    }
                    refreshForegroundNotification()
                    connected = connectCandidate(
                        candidate,
                        cycleDeadlineMs = cycleDeadlineMs,
                        recoveryMode = recoveryMode
                    )
                    if (connected) {
                        connectedServer = candidate
                        candidateFailedAtMs.remove(candidateKey)
                    } else {
                        candidateFailedAtMs[candidateKey] = SystemClock.elapsedRealtime()
                    }

                    if (connected) break
                }

                if (!connected) {
                    connectionStatusOverride = null
                    val rawError = AmneziaWgManager.connectionError ?: XrayManager.connectionError
                    val userFacingError = buildUserFacingConnectionError(rawError)
                    Logger.e(TAG, userFacingError)
                    handleConnectionFailure(rawError ?: "Connection failed", retryable = true)
                    return@launch
                }
                val finalServer = connectedServer ?: effectiveServer

                // Успех
                isConnected = true
                isConnecting = false
                this@MyVpnService.currentServer = finalServer
                currentServerName = finalServer.name
                currentServerHost = finalServer.host
                connectionStatusOverride = null
                VpnManager.state.value = VpnState.CONNECTED
                // Журнал сессий: с этого момента считаем трафик текущего подключения.
                TrafficHistory.startSession(finalServer.name)
                performConnectionSuccessHaptic(
                    context = this@MyVpnService,
                    enabled = preferencesManager.hapticFeedbackEnabled,
                    strength = preferencesManager.hapticFeedbackStrength,
                    style = preferencesManager.hapticFeedbackStyle
                )
                VpnManager.connectedServer.value = finalServer
                VpnManager.selectedServer = finalServer
                lastConnectedServer = finalServer // Store the successfully connected server
                val connectAtMs = System.currentTimeMillis()
                val connectAtText = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(connectAtMs))
                preferencesManager.lastConnectAtMs = connectAtMs
                preferencesManager.lastConnectAtText = connectAtText
                preferencesManager.lastConnectedProfileUrl = finalServer.profileUrl ?: ""
                preferencesManager.lastConnectedServerName = finalServer.name
                preferencesManager.vpnConnectionDesired = true
                preferencesManager.vpnPausedByScreen = false
                preferencesManager.saveLastSelectedServer(finalServer)
                finalServer.profileUrl?.let { preferencesManager.saveLastSelectedProfileUrl(it) }
                notifyQuickSettingsTiles()
                recoveryState = VpnRecoveryPolicy.reduce(
                    recoveryState.copy(
                        desiredConnected = true,
                        networkAvailable = true
                    ),
                    VpnRecoveryPolicy.Event.ConnectSucceeded
                ).state
                VpnManager.recoveryStatus.value = VpnRecoveryStatus.IDLE
                VpnManager.recoveryAttempt.value = 0
                cancelRecoveryJob()

                Logger.i(TAG, "Успешное подключение к ${finalServer.name}")
                SmartServerGroupStore.recordResult(
                    this@MyVpnService,
                    finalServer.selectionKey(),
                    finalServer.ping,
                    success = true
                )
                NetworkEventJournal.record(
                    this@MyVpnService,
                    NetworkEventType.VPN_CONNECTED,
                    "VPN подключён",
                    "Туннель запущен и готов к работе",
                    serverName = finalServer.name
                )

                handler.post {
                    startForeground(com.danila.nimbo.utils.NotificationManager.NOTIFICATION_ID_VPN, createNotification())
                }

                // Запускаем таймер
                startTimer()

                Log.d(TAG, "Connected successfully")

            } catch (e: CancellationException) {
                // Job отменён (например, пользователь переключил сервер на лету).
                // Сервис НЕ трогаем — новый connect() уже на подходе из switchServer().
                Log.d(TAG, "Connection job cancelled, skipping teardown")
                throw e
            } catch (e: Exception) {
                connectionStatusOverride = null
                Logger.e(TAG, buildUserFacingConnectionError(e.message))
                handleConnectionFailure(e.message ?: e::class.java.simpleName, retryable = true)
            }
        }
    }

    /**
     * Смена сервера «на лету» без полной остановки сервиса.
     *
     * Старый путь смены был disconnect() + connect(): disconnect() вызывает stopSelf()
     * и убивает foreground-сервис, после чего идёт холодное переподключение (новый
     * сервис, waitForInternet, перебор кандидатов, новый establishTun → система заново
     * поднимает VPN-интерфейс). Это и медленно, и нестабильно — ACTION_CONNECT,
     * прилетевший пока старый сервис ещё не остановился, отбрасывался по isConnected.
     *
     * Здесь сервис и уведомление остаются живыми: гасим только текущий пайплайн и
     * запускаем connect() заново. XrayManager.connect() сам останавливает старое ядро
     * и закрывает старый tun, а старый job мы дожидаемся через cancelAndJoin, чтобы не
     * было гонки за singleton XrayManager / tun fd.
     */
    private fun switchServer(newServer: Server) {
        Logger.i(TAG, "Переключение на сервер: ${newServer.name}")

        // Сразу отражаем переключение в UI.
        VpnManager.state.value = VpnState.CONNECTING

        // Гасим текущий пайплайн, НЕ трогая сам сервис (без stopSelf/stopForeground).
        connectionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        connectionTimeoutRunnable = null
        timerJob?.cancel()
        timerJob = null

        val previousJob = connectionJob
        connectionJob = null
        isConnected = false
        isConnecting = false

        serviceScope.launch {
            // Дожидаемся завершения предыдущего подключения, чтобы новый connect()
            // не пересёкся с ним на нативном ядре / tun.
            runCatching { previousJob?.cancelAndJoin() }
            withContext(Dispatchers.Main) {
                connect(newServer)
            }
        }
    }

    private fun buildUserFacingConnectionError(raw: String?): String {
        val safe = sanitizeSensitiveError(raw)
        val tunFailure = safe.contains("Failed to establish TUN", ignoreCase = true) ||
            safe.contains("configure tun interface", ignoreCase = true) ||
            safe.contains("tun-in", ignoreCase = true)

        if (tunFailure) {
            val hints = detectTunConflictHints()
            return if (hints.isBlank()) {
                "Ошибка подключения: не удалось создать VPN-интерфейс (TUN). Закройте другие VPN/сетевые модули и попробуйте снова."
            } else {
                "Ошибка подключения: не удалось создать VPN-интерфейс (TUN). $hints"
            }
        }

        return "Ошибка подключения: ${safe.ifBlank { "неизвестная ошибка" }}"
    }

    private fun sanitizeSensitiveError(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("""https?://\S+"""), "[url]")
            .replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b"""), "[ip]")
            .replace(Regex("""(?i)\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b"""), "[host]")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(220)
    }

    private fun detectTunConflictHints(): String {
        val hints = mutableListOf<String>()
        if (isAnotherVpnTransportActive()) {
            hints += "Обнаружен активный VPN-профиль."
        }
        if (isRootOrKernelModuleLikely()) {
            hints += "Обнаружена модифицированная система (root/ядро с сетевыми модулями)."
        }
        if (hints.isNotEmpty()) {
            hints += "Отключите конфликтующие VPN/модули и повторите подключение."
        }
        return hints.joinToString(" ")
    }

    private fun isAnotherVpnTransportActive(): Boolean {
        return runCatching {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetwork
                ?.let(cm::getNetworkCapabilities)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
    }

    private fun isRootOrKernelModuleLikely(): Boolean {
        val markers = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/ksu",
            "/data/adb/modules",
            "/system/bin/ksud",
            "/system/xbin/ksud"
        )
        val hasMarker = markers.any { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
        val testKeys = Build.TAGS?.contains("test-keys", ignoreCase = true) == true
        return hasMarker || testKeys
    }

    private fun requestManualStop(stopReason: String) {
        if (serviceStopping) return
        serviceStopping = true
        Logger.i(TAG, "VPN stopped by user: $stopReason")
        NetworkEventJournal.record(
            this,
            NetworkEventType.VPN_DISCONNECTED,
            if (stopReason == "traffic budget reached") "VPN остановлен по лимиту трафика" else "VPN отключён",
            stopReason,
            severity = if (stopReason == "traffic budget reached") NetworkEventSeverity.WARNING else NetworkEventSeverity.INFO,
            serverName = recoveryServer()?.name
        )
        recoveryState = VpnRecoveryPolicy.reduce(
            recoveryState,
            VpnRecoveryPolicy.Event.ManualDisconnect
        ).state
        preferencesManager.vpnConnectionDesired = false
        preferencesManager.vpnPausedByScreen = false
        VpnManager.recoveryStatus.value = VpnRecoveryStatus.IDLE
        VpnManager.recoveryAttempt.value = 0
        cancelRecoveryJob()
        networkDebounceJob?.cancel()
        networkDebounceJob = null
        cancelNetworkHandoffJob()
        teardownTunnel(cancelConnectionJob = true)
        unregisterNetworkCallback()

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping foreground service", e)
        }
    }

    private fun pauseTunnelForScreen() {
        if (!preferencesManager.vpnConnectionDesired) return
        preferencesManager.vpnPausedByScreen = true
        VpnManager.recoveryStatus.value = VpnRecoveryStatus.PAUSED_BY_SCREEN
        VpnManager.recoveryAttempt.value = 0
        cancelRecoveryJob()
        cancelNetworkHandoffJob()
        teardownTunnel(cancelConnectionJob = true)
        Logger.i(TAG, "VPN tunnel paused because the screen turned off")
        refreshForegroundNotification()
    }

    private fun pauseTunnelForNetwork() {
        if (!preferencesManager.vpnConnectionDesired || preferencesManager.vpnPausedByScreen) return
        VpnManager.recoveryStatus.value = VpnRecoveryStatus.WAITING_FOR_NETWORK
        cancelRecoveryJob()
        cancelNetworkHandoffJob()
        teardownTunnel(cancelConnectionJob = true)
        Logger.w(TAG, "Underlying network is unavailable; VPN is waiting")
        refreshForegroundNotification()
    }

    private fun rebuildTunnelForNetwork() {
        if (!preferencesManager.vpnConnectionDesired || preferencesManager.vpnPausedByScreen) return
        VpnManager.recoveryStatus.value = VpnRecoveryStatus.WAITING_FOR_NETWORK
        cancelRecoveryJob()
        cancelNetworkHandoffJob()
        Logger.i(TAG, "Underlying network changed; rebuilding the complete VPN tunnel")
        refreshForegroundNotification()
        networkHandoffJob = serviceScope.launch {
            val previousConnectionJob = connectionJob
            connectionJob = null
            previousConnectionJob?.cancelAndJoin()
            teardownTunnel(cancelConnectionJob = false)
            delay(UNDERLYING_NETWORK_SETTLE_MS)
            networkHandoffJob = null
            if (
                preferencesManager.vpnConnectionDesired &&
                !preferencesManager.vpnPausedByScreen &&
                hasUsableUnderlyingNetwork()
            ) {
                startRecoveryConnection()
            }
        }
    }

    private fun cancelNetworkHandoffJob() {
        networkHandoffJob?.cancel()
        networkHandoffJob = null
    }

    private fun teardownTunnel(cancelConnectionJob: Boolean) {
        // Сессия закрывается здесь, а не в onDestroy: сервис может пережить
        // разрыв туннеля, и тогда две сессии слиплись бы в одну строку журнала.
        TrafficHistory.finishSession()
        connectionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        connectionTimeoutRunnable = null

        if (cancelConnectionJob) {
            connectionJob?.cancel()
        }
        connectionJob = null
        disconnectCurrentEngine()
        timerJob?.cancel()
        timerJob = null

        releaseWakeLock()

        isConnected = false
        isConnecting = false
        VpnManager.state.value = VpnState.DISCONNECTED
        VpnManager.connectedServer.value = null
        VpnManager.connectedSeconds.value = 0
        notifyQuickSettingsTiles()
    }

    private fun clearLastConnectionError() {
        VpnManager.lastConnectionError.value = null
    }

    private fun handleConnectionFailure(message: String, retryable: Boolean) {
        if (serviceStopping || !preferencesManager.vpnConnectionDesired) return
        recoveryServer()?.let { failedServer ->
            SmartServerGroupStore.recordResult(
                this,
                failedServer.selectionKey(),
                failedServer.ping,
                success = false
            )
        }
        NetworkEventJournal.record(
            this,
            NetworkEventType.HEALTH_CHECK_FAILED,
            "Соединение не прошло проверку",
            message,
            severity = NetworkEventSeverity.WARNING,
            serverName = recoveryServer()?.name
        )
        teardownTunnel(cancelConnectionJob = false)

        val networkAvailable = hasUsableUnderlyingNetwork()
        recoveryState = recoveryState.copy(networkAvailable = networkAvailable)
        if (!networkAvailable && preferencesManager.autoReconnect) {
            recoveryState = recoveryState.copy(
                phase = VpnRecoveryPolicy.Phase.WAITING_FOR_NETWORK,
                connectPending = false
            )
            VpnManager.recoveryStatus.value = VpnRecoveryStatus.WAITING_FOR_NETWORK
            Logger.w(TAG, "Connection paused until the underlying network returns")
            refreshForegroundNotification()
            return
        }

        val result = VpnRecoveryPolicy.reduce(
            recoveryState,
            VpnRecoveryPolicy.Event.ConnectFailed(
                retryable = retryable,
                autoRecoveryEnabled = preferencesManager.autoReconnect,
                hasServer = recoveryServer() != null
            )
        )
        recoveryState = result.state
        val failure = ConnectionFailureClassifier.classify(
            raw = message,
            tunConflictHint = detectTunConflictHints().takeIf { it.isNotBlank() },
            hasNetwork = networkAvailable
        )
        // В журнал уходит и причина, и техническая строка: по одному только
        // «не удалось запустить» разобрать обращение в поддержку невозможно.
        Logger.w(TAG, "VPN connection failed: ${failure.reason} | ${failure.technical}")
        executeRecoveryCommands(result.commands)

        val willRetry = result.commands.any { it is VpnRecoveryPolicy.Command.ScheduleRetry }
        if (!willRetry) {
            // Показываем диалог только когда автоповтор уже не спасёт.
            VpnManager.lastConnectionError.value = failure
            requestManualStop("connection failed")
        }
    }

    private fun restoreStickyService() {
        if (!preferencesManager.vpnConnectionDesired) {
            stopSelf()
            return
        }

        val server = recoveryServer()
        currentServer = server
        currentServerName = server?.name.orEmpty()
        currentServerHost = server?.host.orEmpty()
        refreshForegroundNotification()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val result = VpnRecoveryPolicy.reduce(
            recoveryState.copy(
                desiredConnected = true,
                screenPaused = preferencesManager.vpnPausedByScreen,
                networkAvailable = hasUsableUnderlyingNetwork()
            ),
            VpnRecoveryPolicy.Event.StickyRestore(
                screenInteractive = powerManager.isInteractive,
                hasServer = server != null
            )
        )
        applyRecoveryResult(result)
    }

    private fun recoveryServer(): Server? {
        return currentServer
            ?: lastConnectedServer
            ?: VpnManager.selectedServer
            ?: preferencesManager.loadLastSelectedServer()
    }

    private fun applyRecoveryResult(result: VpnRecoveryPolicy.Result) {
        recoveryState = result.state
        preferencesManager.vpnConnectionDesired = result.state.desiredConnected
        preferencesManager.vpnPausedByScreen = result.state.screenPaused
        executeRecoveryCommands(result.commands)
    }

    private fun executeRecoveryCommands(commands: List<VpnRecoveryPolicy.Command>) {
        commands.forEach { command ->
            when (command) {
                VpnRecoveryPolicy.Command.StartConnection -> startRecoveryConnection()
                VpnRecoveryPolicy.Command.StopTunnelForScreen -> pauseTunnelForScreen()
                VpnRecoveryPolicy.Command.StopTunnelForNetwork -> pauseTunnelForNetwork()
                VpnRecoveryPolicy.Command.RebuildTunnelForNetwork -> rebuildTunnelForNetwork()
                VpnRecoveryPolicy.Command.StopService -> requestManualStop("policy stop")
                VpnRecoveryPolicy.Command.CancelRetry -> cancelRecoveryJob()
                is VpnRecoveryPolicy.Command.ScheduleRetry -> scheduleRecovery(command.delayMs)
                is VpnRecoveryPolicy.Command.Diagnostic -> Logger.w(TAG, command.message)
            }
        }
    }

    private fun startRecoveryConnection() {
        if (
            serviceStopping ||
            isConnected ||
            isConnecting ||
            networkHandoffJob?.isActive == true ||
            !preferencesManager.vpnConnectionDesired ||
            preferencesManager.vpnPausedByScreen
        ) {
            return
        }
        if (!hasUsableUnderlyingNetwork()) {
            recoveryState = recoveryState.copy(
                phase = VpnRecoveryPolicy.Phase.WAITING_FOR_NETWORK,
                networkAvailable = false,
                connectPending = false
            )
            VpnManager.recoveryStatus.value = VpnRecoveryStatus.WAITING_FOR_NETWORK
            refreshForegroundNotification()
            return
        }
        val server = recoveryServer()
        if (server == null) {
            Logger.e(TAG, "Automatic recovery skipped: no saved server")
            requestManualStop("missing recovery server")
            return
        }
        currentServer = server
        currentServerName = server.name
        currentServerHost = server.host
        Logger.i(TAG, "Starting automatic VPN recovery")
        connect(server, recoveryMode = true)
    }

    private fun scheduleRecovery(delayMs: Long) {
        cancelRecoveryJob()
        VpnManager.recoveryStatus.value = VpnRecoveryStatus.RETRYING
        VpnManager.recoveryAttempt.value = recoveryState.retryAttempt
        Logger.w(TAG, "Scheduling VPN recovery attempt ${recoveryState.retryAttempt} in ${delayMs}ms")
        NetworkEventJournal.record(
            this,
            NetworkEventType.RECOVERY_SCHEDULED,
            "Запланировано восстановление VPN",
            "Попытка ${recoveryState.retryAttempt}, ожидание ${delayMs / 1000} с",
            severity = NetworkEventSeverity.WARNING,
            serverName = recoveryServer()?.name
        )
        refreshForegroundNotification()
        recoveryJob = serviceScope.launch {
            delay(delayMs)
            if (!preferencesManager.autoReconnect) {
                requestManualStop("automatic recovery disabled")
                return@launch
            }
            val result = VpnRecoveryPolicy.reduce(
                recoveryState,
                VpnRecoveryPolicy.Event.RetryElapsed
            )
            applyRecoveryResult(result)
        }
    }

    private fun cancelRecoveryJob() {
        recoveryJob?.cancel()
        recoveryJob = null
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val initialNetwork = connectivityManager.activeNetwork
            ?.takeIf { network -> isUsableUnderlyingNetwork(connectivityManager, network) }
        val initialCapabilities = initialNetwork?.let(connectivityManager::getNetworkCapabilities)
        synchronized(underlyingNetworkLock) {
            selectedUnderlyingNetwork = initialNetwork
            selectedUnderlyingCapabilities = initialCapabilities
            lastUnderlyingNetworkHandle = initialNetwork?.networkHandle
            lastUnderlyingTransportName = underlyingTransportName(initialCapabilities)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // onCapabilitiesChanged follows onAvailable and carries an ordered,
                // race-free snapshot. Do not synchronously query capabilities here.
                synchronized(underlyingNetworkLock) {
                    selectedUnderlyingNetwork = network
                    selectedUnderlyingCapabilities = null
                }
                scheduleUnderlyingNetworkEvaluation()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                synchronized(underlyingNetworkLock) {
                    if (selectedUnderlyingNetwork == network) {
                        selectedUnderlyingCapabilities = capabilities
                    }
                }
                scheduleUnderlyingNetworkEvaluation()
            }

            override fun onLost(network: Network) {
                val lostSelectedNetwork = synchronized(underlyingNetworkLock) {
                    if (selectedUnderlyingNetwork == network) {
                        selectedUnderlyingNetwork = null
                        selectedUnderlyingCapabilities = null
                        true
                    } else {
                        false
                    }
                }
                if (lostSelectedNetwork) scheduleUnderlyingNetworkEvaluation()
            }

            override fun onUnavailable() {
                synchronized(underlyingNetworkLock) {
                    selectedUnderlyingNetwork = null
                    selectedUnderlyingCapabilities = null
                }
                scheduleUnderlyingNetworkEvaluation()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                connectivityManager.registerBestMatchingNetworkCallback(request, callback, handler)
            } else {
                // MinSdk 29 fallback: a single request callback follows the best
                // matching network instead of reporting every simultaneous transport.
                connectivityManager.requestNetwork(request, callback)
            }
            networkCallback = callback
        }.onFailure {
            Logger.e(TAG, "Unable to register underlying-network callback", it)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback
        networkCallback = null
        if (callback != null) {
            runCatching {
                val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            }.onFailure {
                Logger.w(TAG, "Failed to unregister network callback: ${it.message}")
            }
        }
        synchronized(underlyingNetworkLock) {
            selectedUnderlyingNetwork = null
            selectedUnderlyingCapabilities = null
            lastUnderlyingNetworkHandle = null
            lastUnderlyingTransportName = "none"
        }
    }

    private fun scheduleUnderlyingNetworkEvaluation() {
        networkDebounceJob?.cancel()
        networkDebounceJob = serviceScope.launch {
            delay(UNDERLYING_NETWORK_DEBOUNCE_MS)
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val tracked = synchronized(underlyingNetworkLock) {
                selectedUnderlyingNetwork to selectedUnderlyingCapabilities
            }
            val trackedCapabilities = tracked.second
                ?: tracked.first?.let(connectivityManager::getNetworkCapabilities)
            val selectedNetwork = tracked.first
                ?.takeIf {
                    trackedCapabilities != null &&
                        isUsableUnderlyingCapabilities(trackedCapabilities)
                }
                ?: connectivityManager.activeNetwork
                    ?.takeIf { network -> isUsableUnderlyingNetwork(connectivityManager, network) }
            val selectedCapabilities = when (selectedNetwork) {
                tracked.first -> trackedCapabilities
                null -> null
                else -> connectivityManager.getNetworkCapabilities(selectedNetwork)
            }

            synchronized(underlyingNetworkLock) {
                selectedUnderlyingNetwork = selectedNetwork
                selectedUnderlyingCapabilities = selectedCapabilities
            }

            val available = selectedNetwork != null
            val handle = selectedNetwork?.networkHandle
            val transportName = underlyingTransportName(selectedCapabilities)
            val previous = synchronized(underlyingNetworkLock) {
                val oldHandle = lastUnderlyingNetworkHandle
                val oldTransport = lastUnderlyingTransportName
                lastUnderlyingNetworkHandle = handle
                lastUnderlyingTransportName = transportName
                oldHandle to oldTransport
            }
            val previousHandle = previous.first
            val transportChanged = available &&
                previousHandle != null &&
                handle != null &&
                previousHandle != handle

            val availableProfileServers = buildList {
                preferencesManager.loadProfiles().forEach { profile -> addAll(profile.servers) }
                currentServer?.let(::add)
                lastConnectedServer?.let(::add)
            }.distinctBy(::connectionCandidateKey)
            val profileResult = NetworkProfileManager.applyPresetForNetworkIfEnabled(
                context = this@MyVpnService,
                snapshot = NetworkProfileManager.captureNetworkContext(
                    context = this@MyVpnService,
                    preferredCapabilities = selectedCapabilities
                ),
                availableServers = availableProfileServers
            )
            val profileServer = profileResult?.selectedServer
            val profileServerChanged = profileServer != null &&
                recoveryServer()?.let(::connectionCandidateKey) != connectionCandidateKey(profileServer)
            if (profileServerChanged) {
                val selectedProfileServer = requireNotNull(profileServer)
                currentServer = selectedProfileServer
                VpnManager.selectedServer = selectedProfileServer
                preferencesManager.saveLastSelectedServer(selectedProfileServer)
            }
            val profileRequiresRebuild = profileResult?.settingsChanged == true || profileServerChanged
            if (profileRequiresRebuild) {
                NetworkEventJournal.record(
                    this@MyVpnService,
                    NetworkEventType.NETWORK_CHANGED,
                    "Применён профиль сети «${profileResult?.preset?.name.orEmpty()}»",
                    profileServer?.let { "Выбран сервер ${it.name}" }
                        ?: "Обновлены правила подключения",
                    transport = networkSnapshot(selectedCapabilities).transport,
                    serverName = profileServer?.name
                )
            }

            val portalDecision = CaptivePortalPolicy.evaluate(
                captivePortalState,
                networkSnapshot(selectedCapabilities),
                preferencesManager.vpnConnectionDesired
            )
            captivePortalState = portalDecision.state
            when (portalDecision.action) {
                CaptivePortalPolicy.Action.PAUSE_AND_SHOW_LOGIN -> {
                    NetworkEventJournal.record(
                        this@MyVpnService,
                        NetworkEventType.CAPTIVE_PORTAL,
                        "Wi‑Fi требует авторизацию",
                        "VPN временно остановлен до завершения входа",
                        severity = NetworkEventSeverity.WARNING,
                        transport = NetworkTransport.WIFI
                    )
                    teardownTunnel(cancelConnectionJob = true)
                    recoveryState = recoveryState.copy(
                        phase = VpnRecoveryPolicy.Phase.WAITING_FOR_NETWORK,
                        networkAvailable = false,
                        connectPending = false
                    )
                    VpnManager.recoveryStatus.value = VpnRecoveryStatus.WAITING_FOR_NETWORK
                    openCaptivePortalLogin()
                    refreshForegroundNotification()
                    return@launch
                }
                CaptivePortalPolicy.Action.WAIT_FOR_VALIDATION -> {
                    VpnManager.recoveryStatus.value = VpnRecoveryStatus.WAITING_FOR_NETWORK
                    return@launch
                }
                CaptivePortalPolicy.Action.RECOVER_TUNNEL -> {
                    NetworkEventJournal.record(
                        this@MyVpnService,
                        NetworkEventType.PORTAL_AUTHORIZED,
                        "Авторизация Wi‑Fi завершена",
                        "Восстанавливаем VPN",
                        transport = NetworkTransport.WIFI
                    )
                    recoveryState = recoveryState.copy(networkAvailable = true)
                    startRecoveryConnection()
                    return@launch
                }
                CaptivePortalPolicy.Action.NONE -> Unit
            }

            if (transportChanged) {
                Logger.i(
                    TAG,
                    "Best underlying network changed: ${previous.second} -> $transportName"
                )
                NetworkEventJournal.record(
                    this@MyVpnService,
                    NetworkEventType.NETWORK_CHANGED,
                    "Сеть изменилась",
                    "${previous.second} → $transportName",
                    transport = networkSnapshot(selectedCapabilities).transport
                )
                selectedNetwork?.let { network ->
                    runCatching { setUnderlyingNetworks(arrayOf(network)) }
                        .onFailure { error ->
                            Logger.w(TAG, "Unable to publish new underlying network: ${error.message}")
                        }
                }
            }

            if (
                (transportChanged || profileRequiresRebuild) &&
                (isConnected || isConnecting) &&
                preferencesManager.vpnConnectionDesired &&
                !preferencesManager.vpnPausedByScreen
            ) {
                applyRecoveryResult(
                    VpnRecoveryPolicy.reduce(
                        recoveryState,
                        VpnRecoveryPolicy.Event.NetworkHandoff(recoveryServer() != null)
                    )
                )
                return@launch
            }

            val result = VpnRecoveryPolicy.reduce(
                recoveryState,
                VpnRecoveryPolicy.Event.NetworkChanged(
                    available = available,
                    autoRecoveryEnabled = preferencesManager.autoReconnect,
                    hasServer = recoveryServer() != null
                )
            )
            applyRecoveryResult(result)
        }
    }

    private fun hasUsableUnderlyingNetwork(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return findUsableUnderlyingNetwork(connectivityManager) != null
    }

    private fun findUsableUnderlyingNetwork(connectivityManager: ConnectivityManager): Network? {
        val tracked = synchronized(underlyingNetworkLock) {
            selectedUnderlyingNetwork to selectedUnderlyingCapabilities
        }
        tracked.first?.let { network ->
            val capabilities = tracked.second ?: connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && isUsableUnderlyingCapabilities(capabilities)) {
                return network
            }
        }
        connectivityManager.activeNetwork
            ?.takeIf { network -> isUsableUnderlyingNetwork(connectivityManager, network) }
            ?.let { return it }
        return null
    }

    private fun isUsableUnderlyingNetwork(
        connectivityManager: ConnectivityManager,
        network: Network
    ): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return isUsableUnderlyingCapabilities(capabilities)
    }

    private fun isUsableUnderlyingCapabilities(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun underlyingTransportName(capabilities: NetworkCapabilities?): String = when {
        capabilities == null -> "none"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
        else -> "other"
    }

    private fun networkSnapshot(capabilities: NetworkCapabilities?): NetworkContextSnapshot {
        val transport = when {
            capabilities == null -> NetworkTransport.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
        return NetworkContextSnapshot(
            transport = transport,
            metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            captivePortal = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        )
    }

    private fun openCaptivePortalLogin() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Logger.w(TAG, "Unable to open Wi-Fi sign-in settings: ${it.message}") }
    }

    private fun refreshForegroundNotification() {
        handler.post {
            runCatching {
                startForeground(
                    com.danila.nimbo.utils.NotificationManager.NOTIFICATION_ID_VPN,
                    createNotification()
                )
            }.onFailure {
                Logger.e(TAG, "Unable to refresh VPN foreground notification", it)
            }
        }
    }

    /**
     * Проверка интернета
     */
    private suspend fun waitForInternet(recoveryMode: Boolean): Boolean {
        Log.d(TAG, "waitForInternet starting...")
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val maxAttempts = if (recoveryMode) {
            RECOVERY_INTERNET_WAIT_ATTEMPTS
        } else {
            MANUAL_INTERNET_WAIT_ATTEMPTS
        }

        repeat(maxAttempts) { attempt ->
            val network = findUsableUnderlyingNetwork(connectivityManager)
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

            if (capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {

                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                    if (attempt == 0) {
                        openCaptivePortalLogin()
                    }
                    Log.d(TAG, "Captive portal detected; waiting for authentication")
                    delay(INTERNET_WAIT_POLL_MS)
                    return@repeat
                }

                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (isValidated) {
                    Log.d(TAG, "Internet validated by system")
                    return true
                }

                // Recovery follows a ConnectivityManager callback, so an INTERNET-capable
                // physical network is enough to rebuild the tunnel immediately. A manual
                // connection gets one short grace poll for Android validation.
                if (recoveryMode || attempt >= 1) {
                    Log.d(TAG, "Internet capability detected (validated=false), proceeding")
                    return true
                }
            }
            Log.d(TAG, "Waiting for internet... attempt ${attempt + 1}/$maxAttempts")
            delay(INTERNET_WAIT_POLL_MS)
        }

        Log.w(TAG, "Internet not available after waiting")
        return false
    }

    /**
     * Запуск таймера и мониторинга трафика
     */
    private fun startTimer() {
        VpnManager.resetStats()

        // Удерживаем устройство активным только если это включено в настройках.
        if (preferencesManager.keepDeviceActive) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        timerJob = serviceScope.launch {
            // Use our own UID's counters, not device-wide ones. The xray core runs
            // in-process and its protected sockets (the real encrypted egress to the VPN
            // server) are owned by our UID, so getUidRx/Tx ≈ the actual tunnel throughput.
            // Device-wide TrafficStats double-counts every byte (once on the tun interface,
            // once on the underlying socket) and folds in other apps' traffic — that's why
            // the speed graph read roughly 2x inflated and "fake". Fall back to device-wide
            // only if the OEM reports UNSUPPORTED for per-UID stats.
            val myUid = android.os.Process.myUid()
            val useUidStats = TrafficStats.getUidRxBytes(myUid) >= 0L &&
                TrafficStats.getUidTxBytes(myUid) >= 0L
            fun rxBytes(): Long = if (useUidStats) TrafficStats.getUidRxBytes(myUid) else TrafficStats.getTotalRxBytes()
            fun txBytes(): Long = if (useUidStats) TrafficStats.getUidTxBytes(myUid) else TrafficStats.getTotalTxBytes()
            fun rxPackets(): Long = if (useUidStats) TrafficStats.getUidRxPackets(myUid) else TrafficStats.getTotalRxPackets()
            fun txPackets(): Long = if (useUidStats) TrafficStats.getUidTxPackets(myUid) else TrafficStats.getTotalTxPackets()
            Log.i(TAG, "Traffic source: ${if (useUidStats) "per-UID ($myUid)" else "device-wide (UID stats unsupported)"}")

            var lastTime = System.currentTimeMillis()
            var lastRxBytes = rxBytes()
            var lastTxBytes = txBytes()
            var lastRxPackets = rxPackets()
            var lastTxPackets = txPackets()
            var memoryCheckTick = 0
            var backgroundMaintenanceTick = 0
            var notificationUpdateTick = 0

            while (isConnected) {
                delay(1000)
                VpnManager.connectedSeconds.value++
                memoryCheckTick++
                backgroundMaintenanceTick++
                notificationUpdateTick++

                if (memoryCheckTick >= 5) {
                    memoryCheckTick = 0
                    enforceSoftMemoryLimitIfNeeded()
                }

                if (backgroundMaintenanceTick >= BACKGROUND_MAINTENANCE_INTERVAL_TICKS) {
                    backgroundMaintenanceTick = 0
                    TemporaryAppRuleManager.expire(this@MyVpnService)
                    if (preferencesManager.keepDeviceActive) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                }


                val currentTime = System.currentTimeMillis()
                val timeDelta = (currentTime - lastTime) / 1000L

                if (timeDelta > 0) {
                    lastTime = currentTime
                    val currentRxBytes = rxBytes()
                    val currentTxBytes = txBytes()

                    val rxDelta = if (lastRxBytes >= 0 && currentRxBytes >= 0) {
                        (currentRxBytes - lastRxBytes).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    val txDelta = if (lastTxBytes >= 0 && currentTxBytes >= 0) {
                        (currentTxBytes - lastTxBytes).coerceAtLeast(0L)
                    } else {
                        0L
                    }

                    lastRxBytes = currentRxBytes
                    lastTxBytes = currentTxBytes

                    val currentRxPackets = rxPackets()
                    val currentTxPackets = txPackets()
                    val rxPacketDelta = if (lastRxPackets >= 0 && currentRxPackets >= 0) {
                        (currentRxPackets - lastRxPackets).coerceAtLeast(0L)
                    } else 0L
                    val txPacketDelta = if (lastTxPackets >= 0 && currentTxPackets >= 0) {
                        (currentTxPackets - lastTxPackets).coerceAtLeast(0L)
                    } else 0L
                    lastRxPackets = currentRxPackets
                    lastTxPackets = currentTxPackets

                    VpnManager.updateSpeeds(txDelta, rxDelta, timeDelta)
                    VpnManager.updatePackets(txPacketDelta, rxPacketDelta)
                    evaluateTrafficBudgets(txDelta + rxDelta, currentTime)

                    if (notificationUpdateTick >= NOTIFICATION_UPDATE_INTERVAL_TICKS) {
                        notificationUpdateTick = 0
                        Log.d(
                            TAG,
                            "Traffic(real) - Up: ${formatBytes(txDelta)}/s, Down: ${formatBytes(rxDelta)}/s"
                        )
                        handler.post {
                            startForeground(
                                com.danila.nimbo.utils.NotificationManager.NOTIFICATION_ID_VPN,
                                createNotification()
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Форматирование байт в человекочитаемый формат
     */
    private fun evaluateTrafficBudgets(addedBytes: Long, nowMs: Long) {
        if (addedBytes <= 0L) return
        val budgets = TrafficBudgetStore.budgets(this)
            .filter { it.enabled && it.packageName.isNullOrBlank() }
        if (budgets.isEmpty()) return

        val usage = TrafficBudgetStore.usage(this).toMutableMap()
        var shouldDisconnect = false
        budgets.forEach { budget ->
            val evaluation = TrafficBudgetPolicy.evaluate(
                budget = budget,
                previous = usage[budget.id],
                addedBytes = addedBytes,
                nowMs = nowMs
            )
            usage[budget.id] = evaluation.usage
            when (evaluation.event) {
                TrafficBudgetPolicy.Event.WARNING -> {
                    val percent = (evaluation.fraction * 100).toInt().coerceAtMost(100)
                    NetworkEventJournal.record(
                        this,
                        NetworkEventType.TRAFFIC_LIMIT_REACHED,
                        "Лимит трафика скоро закончится",
                        "${budget.name}: использовано $percent%",
                        severity = NetworkEventSeverity.WARNING
                    )
                    connectionStatusOverride = "Трафик: $percent% лимита"
                    refreshForegroundNotification()
                }
                TrafficBudgetPolicy.Event.LIMIT_REACHED -> {
                    NetworkEventJournal.record(
                        this,
                        NetworkEventType.TRAFFIC_LIMIT_REACHED,
                        "Лимит трафика достигнут",
                        budget.name,
                        severity = NetworkEventSeverity.ERROR
                    )
                    connectionStatusOverride = "Лимит трафика достигнут"
                    refreshForegroundNotification()
                    shouldDisconnect = evaluation.action == TrafficBudgetAction.DISCONNECT ||
                        evaluation.action == TrafficBudgetAction.BLOCK_UNTIL_RESET
                }
                TrafficBudgetPolicy.Event.NONE -> Unit
            }
        }
        TrafficBudgetStore.saveUsage(this, usage)
        if (shouldDisconnect) requestManualStop("traffic budget reached")
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes Б"
            bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} МБ"
            else -> "${bytes / (1024 * 1024 * 1024)} ГБ"
        }
    }

    /**
     * Приобретение WakeLock для работы в фоне
     */
    private fun acquireWakeLock() {
        val lock = wakeLock ?: run {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NebulaGuard::VpnService"
            ).apply {
                setReferenceCounted(false)
            }
        }.also { wakeLock = it }

        if (!lock.isHeld) {
            // The user explicitly enabled "Keep device active". A timed lock can
            // expire while the device is idle, after which the service may never
            // get CPU time to renew it. The tunnel teardown always releases it.
            lock.acquire()
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun enforceSoftMemoryLimitIfNeeded() {
        if (preferencesManager.memoryLimitDisabled) return

        val limitMb = preferencesManager.memoryLimitMb
        val usedMb = currentProcessMemoryMb()
        if (usedMb <= limitMb) return

        Log.w(TAG, "Soft memory limit exceeded: used=${usedMb}MB, limit=${limitMb}MB. Triggering GC.")
        Runtime.getRuntime().gc()
        Runtime.getRuntime().runFinalization()
        val afterGcMb = currentProcessMemoryMb()

        if (afterGcMb > limitMb + 30) {
            Log.w(TAG, "Memory still high after GC: ${afterGcMb}MB (limit=${limitMb}MB)")
        }
    }

    private fun currentProcessMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        val managedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1_048_576L
        return managedMb + nativeMb
    }

    /**
     * Освобождение WakeLock
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    /**
     * Создание уведомления
     */
    private fun createNotification(): android.app.Notification {
        val isEn = preferencesManager.appLanguage == "en"
        val recoveryStatus = VpnManager.recoveryStatus.value
        val statusOverride = connectionStatusOverride ?: when (recoveryStatus) {
            VpnRecoveryStatus.PAUSED_BY_SCREEN ->
                if (isEn) "Paused until screen turns on" else "Пауза до включения экрана"
            VpnRecoveryStatus.WAITING_FOR_NETWORK ->
                if (isEn) "Waiting for network" else "Ожидание сети"
            VpnRecoveryStatus.RETRYING -> {
                val attempt = VpnManager.recoveryAttempt.value
                if (isEn) "Reconnecting · attempt $attempt" else "Переподключение · попытка $attempt"
            }
            VpnRecoveryStatus.IDLE -> null
        }
        return com.danila.nimbo.utils.NotificationManager.createVpnNotification(
            context = this,
            serverName = currentServerName,
            isConnected = isConnected,
            connectionTimeSeconds = VpnManager.connectedSeconds.value,
            profileName = currentProfileName,
            downSpeedBytes = VpnManager.downloadSpeed.value,
            upSpeedBytes = VpnManager.uploadSpeed.value,
            statusOverride = statusOverride,
            showPauseAction = recoveryStatus == VpnRecoveryStatus.IDLE,
            subscriptionLogoBitmap = currentProfileLogoBitmap
                ?.takeIf { preferencesManager.showSubscriptionLogo }
        )
    }

    private data class BypassProbeReport(
        val server: Server,
        val successCount: Int,
        val averageLatencyMs: Int,
        val score: Int
    )

    private fun bypassLocationPriority(server: Server): Int {
        val source = "${server.name} ${server.serverDescription.orEmpty()} ${server.host}"
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
        val tokens = source.split(" ").filter { it.isNotBlank() }.toSet()

        val priorities = listOf(
            "fi" to 1, "finland" to 1,
            "lv" to 2, "latvia" to 2,
            "ee" to 3, "estonia" to 3,
            "lt" to 4, "lithuania" to 4,
            "pl" to 5, "poland" to 5,
            "de" to 6, "germany" to 6,
            "nl" to 7, "netherlands" to 7,
            "tr" to 8, "turkey" to 8,
            "kz" to 9, "kazakhstan" to 9,
            "us" to 10, "usa" to 10, "unitedstates" to 10
        )
        return priorities.firstOrNull { (token, _) -> token in tokens }?.second ?: 50
    }

    private fun shouldRunBypassLocationProbing(
        candidates: List<Server>,
        networkRestricted: Boolean
    ): Boolean {
        if (!networkRestricted || !preferencesManager.autoBypassByNetwork) return false
        val bypass = candidates.filter(::isBypassServer)
        val regular = candidates.filterNot(::isBypassServer)
        return bypass.size >= 2 && regular.isEmpty()
    }

    private suspend fun connectCandidate(
        candidate: Server,
        cycleDeadlineMs: Long,
        maxAttemptsOverride: Int? = null,
        probeMode: Boolean = false,
        recoveryMode: Boolean = false
    ): Boolean {
        // AmneziaWG/WireGuard работают на нативном движке libwg-go, минуя Xray,
        // удалённые шаблоны и локальный прокси.
        if (candidate.usesAwgEngine()) {
            return connectAwgCandidate(candidate, cycleDeadlineMs, maxAttemptsOverride, probeMode)
        }
        val autoBalancerCandidate = isAutoBalancerServer(candidate)
        // For auto-balancer templates, gather the profile's concrete servers so the
        // balancer (which ships without real proxy outbounds) can be populated client-side.
        val balancerProxyServers = if (autoBalancerCandidate) {
            collectBalancerProxyServers(candidate)
        } else {
            emptyList()
        }
        val useRemoteConfig = !FORCE_LOCAL_MANUAL_ROUTING_TEST && shouldUseRemoteTemplateConfig(candidate)

        var remoteConfigError: Throwable? = null
        val config = if (useRemoteConfig) {
            Log.i(
                TAG,
                "Using template/remote config for server: ${candidate.name} (templateUuid=${candidate.templateUuid ?: "-"}, templateName=${candidate.templateName ?: "-"})"
            )
            val resolveBudgetMs = ConnectionAttemptPolicy.attemptTimeoutMs(
                cycleDeadlineMs - SystemClock.elapsedRealtime()
            )
            if (resolveBudgetMs <= 0L) return false
            val resolvedConfig = withTimeoutOrNull(resolveBudgetMs) {
                runCatching { resolveRemoteConfig(candidate) }
                    .onFailure {
                        remoteConfigError = it
                        Log.w(TAG, "Remote/template config is unavailable: ${it.message}")
                    }
                    .getOrNull()
            }
            if (resolvedConfig == null && remoteConfigError == null) {
                remoteConfigError = IllegalStateException("template config request timed out")
            }
            resolvedConfig
        } else {
            Log.i(
                TAG,
                if (FORCE_LOCAL_MANUAL_ROUTING_TEST) {
                    "FORCED TEST MODE: using local manual routing template for ${candidate.name}"
                } else {
                    "Using local config generation for protocol: ${candidate.protocol}"
                }
            )
            null
        }

        if (useRemoteConfig &&
            config.isNullOrBlank() &&
            (!candidate.templateUuid.isNullOrBlank() || !candidate.templateName.isNullOrBlank())
        ) {
            val message = remoteConfigError?.message ?: "template config is empty"
            val strictTemplateRequired = candidate.uuid.equals("remote", ignoreCase = true) ||
                candidate.host.equals("API", ignoreCase = true)

            if (strictTemplateRequired) {
                Log.e(TAG, "Template config is required for ${candidate.name}, local fallback is disabled")
                Logger.e(TAG, "Не удалось получить XRAY-шаблон из подписки: $message")
                return false
            } else {
                Log.w(
                    TAG,
                    "Template config unavailable for regular server ${candidate.name}; fallback to local config generation"
                )
                Logger.w(
                    TAG,
                    "Шаблон из подписки временно недоступен, используем локальную генерацию для ${candidate.name}"
                )
            }
        }

        var lastTriedConfig: String? = config
        val defaultAttempts = if (probeMode) {
            ConnectionAttemptPolicy.PROBE_MAX_ATTEMPTS
        } else {
            ConnectionAttemptPolicy.NORMAL_MAX_ATTEMPTS
        }
        val maxAttempts = (maxAttemptsOverride ?: defaultAttempts).coerceAtLeast(1)
        for (attempt in 0 until maxAttempts) {
            var remainingCycleMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
            if (remainingCycleMs <= 0L) break

            if (attempt > 0) {
                Log.w(
                    TAG,
                    "Connection retry ${attempt + 1}/$maxAttempts for ${candidate.name}${if (probeMode) " [probe]" else ""}..."
                )
                XrayManager.disconnect()
                NaiveProxyManager.stop()
                val retryDelayMs = if (autoBalancerCandidate) {
                    AUTO_BALANCER_RETRY_DELAY_MS
                } else {
                    1000L * (attempt + 1)
                }
                if (remainingCycleMs <= retryDelayMs) break
                delay(retryDelayMs)
            }

            if (useRemoteConfig && attempt > 0 && (!autoBalancerCandidate || lastTriedConfig.isNullOrBlank())) {
                val refreshBudgetMs = ConnectionAttemptPolicy.attemptTimeoutMs(
                    cycleDeadlineMs - SystemClock.elapsedRealtime()
                )
                if (refreshBudgetMs <= 0L) break
                withTimeoutOrNull(refreshBudgetMs) {
                    runCatching {
                        lastTriedConfig = resolveRemoteConfig(candidate)
                    }.onFailure {
                        Log.w(TAG, "Failed to refresh remote config on retry ${attempt + 1}: ${it.message}")
                    }
                }
            }

            remainingCycleMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
            val attemptTimeoutMs = ConnectionAttemptPolicy.attemptTimeoutMs(remainingCycleMs)
            if (attemptTimeoutMs <= 0L) break
            val connected = withTimeoutOrNull(attemptTimeoutMs) {
                runCatching {
                    val runtimeCandidate = if (candidate.isNaiveProxy()) {
                        NaiveProxyManager.start(this@MyVpnService, candidate)
                    } else {
                        candidate
                    }
                    XrayManager.connect(
                        context = this@MyVpnService,
                        server = runtimeCandidate,
                        vpnService = this@MyVpnService,
                        underlyingNetwork = findUsableUnderlyingNetwork(
                            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                        ),
                        overrideConfig = lastTriedConfig,
                        proxyServers = balancerProxyServers
                    )
                }.onFailure {
                    Logger.e(TAG, "Не удалось запустить ${candidate.protocol} для ${candidate.name}", it)
                    NaiveProxyManager.stop()
                }.getOrDefault(false)
            } ?: run {
                Logger.w(TAG, "Connection attempt timed out for ${candidate.name}")
                XrayManager.disconnect()
                NaiveProxyManager.stop()
                false
            }
            if (connected) {
                val verificationBudgetMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
                val verified = if (verificationBudgetMs > 0L) {
                    withTimeoutOrNull(verificationBudgetMs) {
                        verifyStartedTunnel(
                            candidate,
                            verifyTraffic = !probeMode,
                            recoveryMode = recoveryMode
                        )
                    } ?: false
                } else {
                    false
                }
                if (verified) return true
                XrayManager.disconnect()
                NaiveProxyManager.stop()
            }
            if (!ConnectionAttemptPolicy.shouldRetry(XrayManager.connectionError, attempt, maxAttempts)) {
                break
            }
        }
        if (candidate.isNaiveProxy()) NaiveProxyManager.stop()
        return false
    }

    private suspend fun connectAwgCandidate(
        candidate: Server,
        cycleDeadlineMs: Long,
        maxAttemptsOverride: Int? = null,
        probeMode: Boolean = false
    ): Boolean {
        val defaultAttempts = if (probeMode) {
            ConnectionAttemptPolicy.PROBE_MAX_ATTEMPTS
        } else {
            ConnectionAttemptPolicy.NORMAL_MAX_ATTEMPTS
        }
        val maxAttempts = (maxAttemptsOverride ?: defaultAttempts).coerceAtLeast(1)
        for (attempt in 0 until maxAttempts) {
            var remainingCycleMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
            if (remainingCycleMs <= 0L) break

            if (attempt > 0) {
                Log.w(TAG, "AmneziaWG retry ${attempt + 1}/$maxAttempts for ${candidate.name}...")
                AmneziaWgManager.disconnect()
                val retryDelayMs = 1000L * (attempt + 1)
                if (remainingCycleMs <= retryDelayMs) break
                delay(retryDelayMs)
            }

            remainingCycleMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
            val attemptTimeoutMs = ConnectionAttemptPolicy.attemptTimeoutMs(remainingCycleMs)
            if (attemptTimeoutMs <= 0L) break
            val connected = withTimeoutOrNull(attemptTimeoutMs) {
                AmneziaWgManager.connect(
                    vpnService = this@MyVpnService,
                    server = candidate,
                    underlyingNetwork = findUsableUnderlyingNetwork(
                        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    )
                )
            } ?: run {
                Logger.w(TAG, "AmneziaWG connection attempt timed out for ${candidate.name}")
                AmneziaWgManager.disconnect()
                false
            }
            if (connected) {
                val verificationBudgetMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
                val verified = if (verificationBudgetMs > 0L) {
                    withTimeoutOrNull(verificationBudgetMs) {
                        verifyAwgTunnel(candidate, verifyHandshake = !probeMode)
                    } ?: false
                } else {
                    false
                }
                if (verified) return true
                AmneziaWgManager.disconnect()
            }
            if (!ConnectionAttemptPolicy.shouldRetry(AmneziaWgManager.connectionError, attempt, maxAttempts)) {
                break
            }
        }
        return false
    }

    private suspend fun verifyAwgTunnel(candidate: Server, verifyHandshake: Boolean): Boolean {
        delay(POST_CONNECT_STABILIZATION_MS)
        if (!AmneziaWgManager.isConnected) {
            Logger.w(TAG, "AmneziaWG engine stopped right after start for ${candidate.name}")
            AmneziaWgManager.recordConnectionFailure("AmneziaWG engine stopped immediately after start")
            return false
        }
        if (!hasUsableUnderlyingNetwork()) {
            Logger.w(TAG, "Underlying network disappeared after VPN start for ${candidate.name}")
            AmneziaWgManager.recordConnectionFailure("Underlying network disappeared after VPN start")
            AmneziaWgManager.disconnect()
            return false
        }
        if (!verifyHandshake) return true

        // Ждём первый handshake: для UDP-туннеля это единственная объективная
        // проверка, что ключи/endpoint верны и сервер отвечает.
        val deadline = SystemClock.elapsedRealtime() + AWG_HANDSHAKE_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!AmneziaWgManager.isConnected) {
                AmneziaWgManager.recordConnectionFailure("AmneziaWG engine stopped during handshake wait")
                return false
            }
            if (AmneziaWgManager.lastHandshakeSeconds() > 0L) {
                Logger.i(TAG, "AmneziaWG handshake established for ${candidate.name}")
                return true
            }
            delay(AWG_HANDSHAKE_POLL_MS)
        }
        Logger.w(TAG, "AmneziaWG handshake timeout for ${candidate.name}")
        AmneziaWgManager.recordConnectionFailure("AmneziaWG handshake timeout (нет ответа от сервера)")
        return false
    }

    private fun disconnectCurrentEngine() {
        AmneziaWgManager.disconnect()
        XrayManager.disconnect()
        NaiveProxyManager.stop()
    }

    private suspend fun verifyStartedTunnel(
        candidate: Server,
        verifyTraffic: Boolean,
        recoveryMode: Boolean
    ): Boolean {
        delay(
            if (recoveryMode) RECOVERY_POST_CONNECT_STABILIZATION_MS
            else POST_CONNECT_STABILIZATION_MS
        )
        if (!XrayManager.isConnected) {
            Logger.w(TAG, "Xray core stopped right after start for ${candidate.name}")
            XrayManager.recordConnectionFailure("Xray core stopped immediately after start")
            return false
        }
        if (!hasUsableUnderlyingNetwork()) {
            Logger.w(TAG, "Underlying network disappeared after VPN start for ${candidate.name}")
            XrayManager.recordConnectionFailure("Underlying network disappeared after VPN start")
            XrayManager.disconnect()
            return false
        }
        when (TunnelHealthPolicy.verificationMode(verifyTraffic, recoveryMode)) {
            TunnelHealthPolicy.VerificationMode.SKIP -> return true
            TunnelHealthPolicy.VerificationMode.BACKGROUND -> {
                launchRecoveryTunnelProbe(candidate)
                return true
            }
            TunnelHealthPolicy.VerificationMode.BLOCKING -> Unit
        }

        val latencies = probeThroughSelectedOutbound(TunnelHealthPolicy.healthTargets)
        val acceptance = TunnelHealthPolicy.startupAcceptance(
            coreRunning = XrayManager.isConnected,
            underlyingNetworkAvailable = hasUsableUnderlyingNetwork(),
            latenciesMs = latencies
        )
        return when (acceptance) {
            TunnelHealthPolicy.StartupAcceptance.CONFIRMED -> {
                Logger.i(TAG, "Туннель ${candidate.name} подтверждён end-to-end запросом через VPN")
                true
            }

            TunnelHealthPolicy.StartupAcceptance.PROVISIONAL -> {
                Logger.w(
                    TAG,
                    "Контрольные HTTPS-сайты через ${candidate.name} не ответили; " +
                        "Xray и физическая сеть активны, поэтому оставляем туннель подключённым"
                )
                true
            }

            TunnelHealthPolicy.StartupAcceptance.REJECTED -> {
                val reason = if (!XrayManager.isConnected) {
                    "Xray core stopped during tunnel verification"
                } else {
                    "Underlying network disappeared during tunnel verification"
                }
                Logger.w(TAG, "$reason for ${candidate.name}")
                XrayManager.recordConnectionFailure(reason)
                XrayManager.disconnect()
                false
            }
        }
    }

    private fun launchRecoveryTunnelProbe(candidate: Server) {
        serviceScope.launch {
            val latencies = probeThroughSelectedOutbound(TunnelHealthPolicy.healthTargets)
            if (!XrayManager.isConnected || !preferencesManager.vpnConnectionDesired) return@launch

            if (TunnelHealthPolicy.isHealthy(latencies)) {
                Logger.i(TAG, "Фоновая проверка восстановленного туннеля ${candidate.name} прошла")
            } else {
                Logger.w(
                    TAG,
                    "Восстановленный туннель ${candidate.name} запущен, но контрольные HTTPS-сайты пока не ответили"
                )
            }
        }
    }

    private suspend fun runBypassServiceProbeSuite(server: Server): BypassProbeReport {
        val targets = TunnelHealthPolicy.serviceTargets
        val latencies = probeThroughSelectedOutbound(targets)

        var score = 0
        var successCount = 0
        var latencySum = 0

        targets.forEachIndexed { index, target ->
            val latency = latencies.getOrElse(index) { -1 }
            if (latency >= 0) {
                successCount++
                latencySum += latency
                score += target.weight * 10_000 - (latency * target.weight * 2)
            } else {
                score -= target.weight * 3_000
            }
        }

        val avg = if (successCount > 0) latencySum / successCount else Int.MAX_VALUE
        return BypassProbeReport(
            server = server,
            successCount = successCount,
            averageLatencyMs = avg,
            score = score
        )
    }

    private suspend fun probeThroughSelectedOutbound(
        targets: List<TunnelProbeTarget>
    ): List<Int> = coroutineScope {
        targets.map { target ->
            async { probeTargetThroughSelectedOutbound(target) }
        }.awaitAll()
    }

    private suspend fun probeTargetThroughSelectedOutbound(target: TunnelProbeTarget): Int =
        try {
            PingManager.pingHttp(
                urlString = target.url,
                method = "GET",
                timeoutMs = TunnelHealthPolicy.REQUEST_TIMEOUT_MS,
                useProxy = true,
                proxyPort = LocalProxyConfig.PORT
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            -1
        }

    private suspend fun rankBypassCandidatesByServiceReachability(
        candidates: List<Server>,
        cycleDeadlineMs: Long
    ): List<Server> {
        val bypassCandidates = candidates.filter(::isBypassServer)
            .distinctBy { "${it.host}:${it.port}:${it.uuid}" }
        if (bypassCandidates.size < 2) return candidates

        Logger.i(TAG, "Проверяем обходные локации по сервисам (Google/Telegram/YouTube)...")
        val reports = mutableListOf<BypassProbeReport>()

        for (candidate in bypassCandidates.take(ConnectionAttemptPolicy.MAX_RANKING_CANDIDATES)) {
            val remainingCycleMs = cycleDeadlineMs - SystemClock.elapsedRealtime()
            if (!ConnectionAttemptPolicy.canStartRankingProbe(remainingCycleMs)) {
                Logger.i(TAG, "Останавливаем предварительную проверку: резервируем время на подключение")
                break
            }
            val candidateKey = connectionCandidateKey(candidate)
            if (ConnectionAttemptPolicy.isCoolingDown(
                    failedAtMs = candidateFailedAtMs[candidateKey],
                    nowMs = SystemClock.elapsedRealtime(),
                    explicitSelection = false
                )
            ) {
                continue
            }
            Log.i(TAG, "Bypass probe start: ${candidate.name} (${candidate.host}:${candidate.port})")

            val connected = connectCandidate(
                candidate = candidate,
                cycleDeadlineMs = cycleDeadlineMs,
                maxAttemptsOverride = ConnectionAttemptPolicy.PROBE_MAX_ATTEMPTS,
                probeMode = true
            )
            if (!connected) {
                candidateFailedAtMs[candidateKey] = SystemClock.elapsedRealtime()
                reports += BypassProbeReport(
                    server = candidate,
                    successCount = 0,
                    averageLatencyMs = Int.MAX_VALUE,
                    score = Int.MIN_VALUE / 2
                )
                continue
            }

            val report = runBypassServiceProbeSuite(candidate)
            reports += report
            if (report.successCount == 0) {
                candidateFailedAtMs[candidateKey] = SystemClock.elapsedRealtime()
            } else {
                candidateFailedAtMs.remove(candidateKey)
            }
            Log.i(
                TAG,
                "AmneziaWG probe result: ${candidate.name}, success=${report.successCount}/5, avg=${if (report.averageLatencyMs == Int.MAX_VALUE) "-1" else report.averageLatencyMs}ms, score=${report.score}"
            )
            disconnectCurrentEngine()
            delay(250)
        }

        if (reports.isEmpty()) return candidates

        val rankedBypass = reports
            .sortedWith(
                compareByDescending<BypassProbeReport> { it.score }
                    .thenByDescending { it.successCount }
                    .thenBy { it.averageLatencyMs }
            )
            .map { it.server }

        val rankedKeys = rankedBypass.map { "${it.host}:${it.port}:${it.uuid}" }.toSet()
        val remainder = bypassCandidates.filter { "${it.host}:${it.port}:${it.uuid}" !in rankedKeys }
        return rankedBypass + remainder
    }

    private fun buildConnectionCandidates(
        baseServer: Server,
        autoRotationEnabled: Boolean,
        networkRestricted: Boolean
    ): List<Server> {
        if (!autoRotationEnabled) return listOf(baseServer)
        val profileUrl = baseServer.profileUrl ?: preferencesManager.loadLastSelectedProfileUrl()
        if (profileUrl.isNullOrBlank()) return listOf(baseServer)

        val profiles = preferencesManager.loadProfiles()
        val profile = profiles.find { it.url == profileUrl } ?: return listOf(baseServer)
        if (profile.servers.isEmpty()) return listOf(baseServer)

        val baseKey = connectionCandidateKey(baseServer)
        val alternatives = profile.servers
            .filterNot { candidate -> connectionCandidateKey(candidate) == baseKey }
            .sortedBy { it.ping ?: Int.MAX_VALUE }
            .take(10)

        val ordered = if (networkRestricted && preferencesManager.autoBypassByNetwork) {
            val prioritized = (listOf(baseServer) + alternatives).distinctBy(::connectionCandidateKey)
            val bypass = prioritized
                .filter(::isBypassServer)
                .sortedWith(compareBy<Server>({ bypassLocationPriority(it) }, { it.ping ?: Int.MAX_VALUE }))
            if (bypass.isNotEmpty()) bypass else prioritized
        } else {
            val prioritized = (listOf(baseServer) + alternatives).distinctBy(::connectionCandidateKey)
            val regular = prioritized.filterNot(::isBypassServer)
            val bypass = prioritized.filter(::isBypassServer)
            regular + bypass
        }
        return ordered
    }

    private fun connectionCandidateKey(server: Server): String {
        val host = server.host.trim().lowercase()
        val protocol = server.protocol.trim().lowercase()
        val uuid = server.uuid.trim().lowercase()
        val network = server.network?.trim()?.lowercase().orEmpty()
        val templateUuid = server.templateUuid?.trim()?.lowercase().orEmpty()
        val templateName = server.templateName?.trim()?.lowercase().orEmpty()
        val remoteBalancerTag = server.remoteBalancerTag?.trim()?.lowercase().orEmpty()
        val remoteOutboundTag = server.remoteOutboundTag?.trim()?.lowercase().orEmpty()
        val isRemote = uuid == "remote" || host == "api"

        return if (isRemote) {
            "remote|$uuid|$host|${server.port}|$protocol|$network|$templateUuid|$templateName|$remoteBalancerTag|$remoteOutboundTag|${server.name.trim().lowercase()}"
        } else {
            "regular|$uuid|$host|${server.port}|$protocol|$network"
        }
    }

    private suspend fun isNetworkRestrictedForBypass(): Boolean {
        if (!preferencesManager.autoBypassByNetwork) return false

        val (baseline, targetServices) = coroutineScope {
            val baselineProbe = async {
                probeDirectReachability(ConnectionAttemptPolicy.baselineTargets)
            }
            val serviceProbe = async {
                probeDirectReachability(ConnectionAttemptPolicy.restrictedServiceTargets)
            }
            baselineProbe.await() to serviceProbe.await()
        }
        val restricted = ConnectionAttemptPolicy.isRestricted(baseline, targetServices)
        Log.i(
            TAG,
            "Restricted-network check: baseline=$baseline, targetServices=$targetServices, restricted=$restricted"
        )
        if (restricted) {
            Logger.i(TAG, "Обнаружены сетевые ограничения; приоритет отдан обходным локациям")
        }
        return restricted
    }

    private suspend fun probeDirectReachability(
        targets: List<TunnelProbeTarget>
    ): List<Boolean> = coroutineScope {
        targets.map { target ->
            async {
                runCatching {
                    PingManager.pingHttp(
                        urlString = target.url,
                        method = "GET",
                        timeoutMs = ConnectionAttemptPolicy.DIRECT_REACHABILITY_TIMEOUT_MS,
                        useProxy = false
                    ) >= 0
                }.getOrDefault(false)
            }
        }.awaitAll()
    }

    private fun resolveServerForConnect(server: Server): Server {
        val profileUrl = server.profileUrl ?: preferencesManager.loadLastSelectedProfileUrl()
        val baseServer = if (!profileUrl.isNullOrBlank()) {
            server.copy(profileUrl = profileUrl)
        } else {
            server
        }
        if (profileUrl.isNullOrBlank()) return baseServer

        val profiles = preferencesManager.loadProfiles()
        val profile = profiles.find { it.url == profileUrl } ?: return baseServer
        if (profile.servers.isEmpty()) return baseServer

        val remoteTemplateRequested = baseServer.uuid == "remote" &&
            (!baseServer.templateUuid.isNullOrBlank() || !baseServer.templateName.isNullOrBlank())

        val exactRemoteTemplate = if (remoteTemplateRequested) {
            profile.servers.firstOrNull { candidate ->
                candidate.uuid == "remote" &&
                    (
                        (!baseServer.templateUuid.isNullOrBlank() &&
                            !candidate.templateUuid.isNullOrBlank() &&
                            candidate.templateUuid.equals(baseServer.templateUuid, ignoreCase = true)) ||
                        (!baseServer.templateName.isNullOrBlank() &&
                            !candidate.templateName.isNullOrBlank() &&
                            candidate.templateName.equals(baseServer.templateName, ignoreCase = true))
                        )
            }
        } else {
            null
        }
        if (exactRemoteTemplate != null) {
            return exactRemoteTemplate.copy(profileUrl = profileUrl)
        }

        val exactByKey = profile.servers.find { candidate ->
            candidate.host == baseServer.host &&
            candidate.port == baseServer.port &&
            candidate.protocol.equals(baseServer.protocol, ignoreCase = true) &&
            candidate.uuid == baseServer.uuid &&
            (
                candidate.uuid != "remote" ||
                    (
                        candidate.templateUuid.equals(baseServer.templateUuid, ignoreCase = true) &&
                            candidate.templateName.equals(baseServer.templateName, ignoreCase = true) &&
                            candidate.remoteBalancerTag.orEmpty().equals(baseServer.remoteBalancerTag.orEmpty(), ignoreCase = true) &&
                            candidate.remoteOutboundTag.orEmpty().equals(baseServer.remoteOutboundTag.orEmpty(), ignoreCase = true)
                        )
                )
        }
        if (exactByKey != null) return exactByKey.copy(profileUrl = profileUrl)

        if (baseServer.uuid != "remote") {
            // Для обычных серверов не подменяем выбор агрессивным fallback'ом:
            // это может увести на другой узел и дать connection refused.
            return baseServer
        }

        if (remoteTemplateRequested) {
            Log.w(
                TAG,
                "Remote template server not found in cached profile, keep selected template hints: " +
                    "templateUuid=${baseServer.templateUuid ?: "-"}, templateName=${baseServer.templateName ?: "-"}"
            )
            return baseServer
        }

        val fallback = if (baseServer.uuid == "remote") {
            profile.servers.firstOrNull { it.uuid == "remote" || it.host.equals("API", ignoreCase = true) }
                ?: profile.servers.first()
        } else {
            profile.servers.firstOrNull { it.protocol.equals(baseServer.protocol, ignoreCase = true) }
                ?: profile.servers.first()
        }

        Log.w(
            TAG,
            "Using fallback server from cached profile for stability: ${fallback.name} (${fallback.host}:${fallback.port})"
        )
        return fallback.copy(profileUrl = profileUrl)
    }

    /**
     * Concrete proxy servers from the same profile used to populate an auto-balancer
     * template (its real outbounds normally come from remnawave.injectHosts, which the
     * bundled standard libXray does not expand). Excludes remote-template markers and
     * other auto-balancer entries — only real, connectable nodes go into the balancer.
     */
    private fun collectBalancerProxyServers(balancer: Server): List<Server> {
        val profileUrl = balancer.profileUrl ?: preferencesManager.loadLastSelectedProfileUrl()
        if (profileUrl.isNullOrBlank()) return emptyList()
        val profile = preferencesManager.loadProfiles().find { it.url == profileUrl } ?: return emptyList()
        val proxyServers = profile.servers
            .filterNot { it.isRemoteTemplateServer() }
            .filterNot { isAutoBalancerServer(it) }
            .filter { it.host.isNotBlank() && it.uuid.isNotBlank() }
            .distinctBy { it.selectionKey() }
        Log.i(TAG, "Collected ${proxyServers.size} proxy servers for auto-balancer ${balancer.name}")
        return proxyServers
    }

    private suspend fun resolveRemoteConfig(server: Server): String {
        val profileUrl = server.profileUrl
            ?: preferencesManager.loadLastSelectedProfileUrl()
            ?: throw Exception("Не найден URL профиля для дистанционного конфига")

        Logger.i(TAG, "Resolving remote configuration for selected server")

        val profiles = preferencesManager.loadProfiles()
        val cachedProfile = profiles.find { it.url == profileUrl }
        val cachedConfig = cachedProfile?.rawConfig
        val cachedTemplateConfig = cachedProfile?.templates
            ?.firstOrNull { cachedTemplate ->
                (!server.templateUuid.isNullOrBlank() && cachedTemplate.uuid.equals(server.templateUuid, ignoreCase = true)) ||
                    (!server.templateName.isNullOrBlank() && cachedTemplate.name.equals(server.templateName, ignoreCase = true))
            }
            ?.config
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { looksLikeXrayJsonConfig(it) }
        val strictTemplateRequested = !server.templateUuid.isNullOrBlank() || !server.templateName.isNullOrBlank()

        Log.d(TAG, "Cached config status: ${if (cachedConfig.isNullOrBlank()) "MISSING/EMPTY" else "EXISTS (len=${cachedConfig.length})"}")

        if (strictTemplateRequested && !cachedTemplateConfig.isNullOrBlank()) {
            Log.i(
                TAG,
                "Using CACHED XRAY template config for server ${server.name} " +
                    "(templateUuid=${server.templateUuid ?: "-"}, templateName=${server.templateName ?: "-"})"
            )
            return cachedTemplateConfig
        }

        if (!cachedTemplateConfig.isNullOrBlank()) {
            Log.w(TAG, "WARNING: Remote template refresh failed. Falling back to CACHED TEMPLATE config (len=${cachedTemplateConfig.length})")
            return cachedTemplateConfig
        }

        if (strictTemplateRequested) {
            if (!cachedConfig.isNullOrBlank() && looksLikeXrayJsonConfig(cachedConfig)) {
                val json = runCatching { org.json.JSONObject(cachedConfig) }.getOrNull()
                if (json != null && (json.has("remnawave") || json.optJSONObject("routing")?.has("balancers") == true)) {
                    Log.i(TAG, "SUCCESS: Falling back to cached rawConfig which contains balancer/template configuration")
                    return cachedConfig
                }
            }
            Log.e(
                TAG,
                "STRICT TEMPLATE MODE: template was requested (uuid=${server.templateUuid ?: "-"}, name=${server.templateName ?: "-"}) but rendered template JSON was not received."
            )
            throw Exception("Не удалось получить выбранный шаблон из панели. Обновите шаблоны/хосты в панели и повторите.")
        }

        // 1. Пытаемся получить свежий конфиг (2 попытки)
        Log.d(TAG, "Attempting to refresh config from network...")
        repeat(2) { attempt ->
            try {
                val loaded = SubscriptionManager.load(profileUrl)
                val freshConfig = loaded.rawConfig
                if (!freshConfig.isNullOrBlank() && looksLikeXrayJsonConfig(freshConfig)) {
                    val currentProfiles = preferencesManager.loadProfiles()
                    val updatedProfiles = currentProfiles.map { profile ->
                        if (profile.url == profileUrl) {
                            profile.copy(
                                rawConfig = freshConfig,
                                configType = loaded.configType ?: profile.configType
                            )
                        } else {
                            profile
                        }
                    }
                    preferencesManager.saveProfiles(updatedProfiles)
                    Log.i(TAG, "SUCCESS: Fresh remote config loaded (attempt ${attempt + 1}), len=${freshConfig.length}")
                    return freshConfig
                } else {
                    Log.w(TAG, "Fresh config is empty or not a usable Xray client config on attempt ${attempt + 1}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fresh config load FAILED (attempt ${attempt + 1}): ${e.message}")
            }
            if (attempt == 0) delay(1000) // Пауза перед второй попыткой
        }

        // 2. Если сеть подвела, используем кэш
        if (!cachedConfig.isNullOrBlank() && looksLikeXrayJsonConfig(cachedConfig)) {
            Log.w(TAG, "WARNING: Network refresh failed. Falling back to CACHED config (len=${cachedConfig.length})")
            return cachedConfig
        }

        Log.e(TAG, "CRITICAL: No rawConfig available (Network failed & Cache empty/missing)")
        throw Exception("Конфигурация не найдена. Пожалуйста, обновите подписку вручную.")
    }

    private fun shouldUseRemoteTemplateConfig(server: Server): Boolean {
        // NaiveProxy is handled by its own native sidecar; an Xray template cannot
        // replace that process and would silently route the node as another protocol.
        if (server.isNaiveProxy()) return false
        if (server.uuid == "remote") return true
        if (!server.templateUuid.isNullOrBlank() || !server.templateName.isNullOrBlank()) return true

        val profileUrl = server.profileUrl ?: preferencesManager.loadLastSelectedProfileUrl()
        val profile = profileUrl
            ?.let { url -> preferencesManager.loadProfiles().find { it.url == url } }

        if (profile?.hasXrayTemplate() == true) return true
        if (looksLikeXrayJsonConfig(profile?.rawConfig)) return true

        return false
    }

    private fun SubscriptionProfile.hasXrayTemplate(): Boolean {
        return templates.any { template ->
            val type = template.templateType.trim().lowercase(Locale.ROOT)
            type == "xray_json" || (type.contains("xray") && type.contains("json"))
        }
    }

    private fun looksLikeXrayJsonConfig(config: String?): Boolean {
        if (config.isNullOrBlank()) return false
        val trimmed = config.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            RemnawaveApiClient.isUsableXrayClientConfig(org.json.JSONObject(trimmed))
        }.getOrDefault(false)
    }

    private fun extractServer(intent: Intent?): Server? {
        if (intent == null) return null

        val serializedServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_SERVER, Server::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_SERVER) as? Server
        }
        if (serializedServer != null) return serializedServer

        val host = intent.getStringExtra(EXTRA_SERVER_HOST)?.takeIf { it.isNotBlank() } ?: return null
        val port = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
        val uuid = intent.getStringExtra(EXTRA_SERVER_UUID).orEmpty()
        val protocol = intent.getStringExtra(EXTRA_SERVER_PROTOCOL).orEmpty().ifBlank { "vless" }
        val name = intent.getStringExtra(EXTRA_SERVER_NAME).orEmpty().ifBlank { host }

        return Server(
            name = name,
            host = host,
            port = port,
            uuid = uuid,
            protocol = protocol,
            flow = intent.getStringExtra(EXTRA_SERVER_FLOW)?.takeIf { it.isNotBlank() },
            security = intent.getStringExtra(EXTRA_SERVER_SECURITY)?.takeIf { it.isNotBlank() },
            network = intent.getStringExtra(EXTRA_SERVER_NETWORK)?.takeIf { it.isNotBlank() },
            path = intent.getStringExtra(EXTRA_SERVER_PATH)?.takeIf { it.isNotBlank() },
            sni = intent.getStringExtra(EXTRA_SERVER_SNI)?.takeIf { it.isNotBlank() },
            fingerprint = intent.getStringExtra(EXTRA_SERVER_FINGERPRINT)?.takeIf { it.isNotBlank() },
            alpn = intent.getStringExtra(EXTRA_SERVER_ALPN)?.takeIf { it.isNotBlank() },
            publicKey = intent.getStringExtra(EXTRA_SERVER_PUBLIC_KEY)?.takeIf { it.isNotBlank() },
            shortId = intent.getStringExtra(EXTRA_SERVER_SHORT_ID)?.takeIf { it.isNotBlank() },
            spiderX = intent.getStringExtra(EXTRA_SERVER_SPIDER_X)?.takeIf { it.isNotBlank() },
            tls = intent.getBooleanExtra(EXTRA_SERVER_TLS, false).takeIf { intent.hasExtra(EXTRA_SERVER_TLS) } ?: (intent.getStringExtra(EXTRA_SERVER_SECURITY)?.lowercase() in listOf("reality", "tls")),
            profileUrl = intent.getStringExtra(EXTRA_SERVER_PROFILE_URL)?.takeIf { it.isNotBlank() },
            templateUuid = intent.getStringExtra(EXTRA_SERVER_TEMPLATE_UUID)?.takeIf { it.isNotBlank() },
            templateName = intent.getStringExtra(EXTRA_SERVER_TEMPLATE_NAME)?.takeIf { it.isNotBlank() },
            hysteriaObfs = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_OBFS)?.takeIf { it.isNotBlank() },
            hysteriaObfsPassword = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_OBFS_PASSWORD)?.takeIf { it.isNotBlank() },
            hysteriaPorts = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_PORTS)?.takeIf { it.isNotBlank() },
            hysteriaHopInterval = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_HOP_INTERVAL)?.takeIf { it.isNotBlank() },
            hysteriaUp = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_UP)?.takeIf { it.isNotBlank() },
            hysteriaDown = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_DOWN)?.takeIf { it.isNotBlank() },
            hysteriaCongestion = intent.getStringExtra(EXTRA_SERVER_HYSTERIA_CONGESTION)?.takeIf { it.isNotBlank() },
            naiveUsername = intent.getStringExtra(EXTRA_SERVER_NAIVE_USERNAME)?.takeIf { it.isNotBlank() },
            naivePassword = intent.getStringExtra(EXTRA_SERVER_NAIVE_PASSWORD)?.takeIf { it.isNotBlank() },
            naiveTransport = intent.getStringExtra(EXTRA_SERVER_NAIVE_TRANSPORT)?.takeIf { it.isNotBlank() },
            wgPrivateKey = intent.getStringExtra(EXTRA_SERVER_WG_PRIVATE_KEY)?.takeIf { it.isNotBlank() },
            wgPublicKey = intent.getStringExtra(EXTRA_SERVER_WG_PUBLIC_KEY)?.takeIf { it.isNotBlank() },
            wgPresharedKey = intent.getStringExtra(EXTRA_SERVER_WG_PRESHARED_KEY)?.takeIf { it.isNotBlank() },
            wgAddress = intent.getStringExtra(EXTRA_SERVER_WG_ADDRESS)?.takeIf { it.isNotBlank() },
            wgAllowedIps = intent.getStringExtra(EXTRA_SERVER_WG_ALLOWED_IPS)?.takeIf { it.isNotBlank() },
            wgDns = intent.getStringExtra(EXTRA_SERVER_WG_DNS)?.takeIf { it.isNotBlank() },
            wgMtu = intent.getIntExtra(EXTRA_SERVER_WG_MTU, 0).takeIf { it > 0 },
            wgKeepAlive = intent.getIntExtra(EXTRA_SERVER_WG_KEEPALIVE, 0).takeIf { it > 0 },
            awgJc = intent.getIntExtra(EXTRA_SERVER_AWG_JC, 0).takeIf { it > 0 },
            awgJmin = intent.getIntExtra(EXTRA_SERVER_AWG_JMIN, 0).takeIf { it > 0 },
            awgJmax = intent.getIntExtra(EXTRA_SERVER_AWG_JMAX, 0).takeIf { it > 0 },
            awgS1 = intent.getIntExtra(EXTRA_SERVER_AWG_S1, 0).takeIf { it > 0 },
            awgS2 = intent.getIntExtra(EXTRA_SERVER_AWG_S2, 0).takeIf { it > 0 },
            awgS3 = intent.getIntExtra(EXTRA_SERVER_AWG_S3, 0).takeIf { it > 0 },
            awgS4 = intent.getIntExtra(EXTRA_SERVER_AWG_S4, 0).takeIf { it > 0 },
            awgH1 = intent.getStringExtra(EXTRA_SERVER_AWG_H1)?.takeIf { it.isNotBlank() },
            awgH2 = intent.getStringExtra(EXTRA_SERVER_AWG_H2)?.takeIf { it.isNotBlank() },
            awgH3 = intent.getStringExtra(EXTRA_SERVER_AWG_H3)?.takeIf { it.isNotBlank() },
            awgH4 = intent.getStringExtra(EXTRA_SERVER_AWG_H4)?.takeIf { it.isNotBlank() },
            awgI1 = intent.getStringExtra(EXTRA_SERVER_AWG_I1)?.takeIf { it.isNotBlank() },
            awgI2 = intent.getStringExtra(EXTRA_SERVER_AWG_I2)?.takeIf { it.isNotBlank() },
            awgI3 = intent.getStringExtra(EXTRA_SERVER_AWG_I3)?.takeIf { it.isNotBlank() },
            awgI4 = intent.getStringExtra(EXTRA_SERVER_AWG_I4)?.takeIf { it.isNotBlank() },
            awgI5 = intent.getStringExtra(EXTRA_SERVER_AWG_I5)?.takeIf { it.isNotBlank() }
        )
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        unregisterScreenReceiver()
        unregisterNetworkCallback()
        cancelRecoveryJob()
        networkDebounceJob?.cancel()
        networkDebounceJob = null
        cancelNetworkHandoffJob()
        teardownTunnel(cancelConnectionJob = true)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            recoveryState = recoveryState.copy(
                                desiredConnected = preferencesManager.vpnConnectionDesired,
                                phase = when {
                                    isConnected -> VpnRecoveryPolicy.Phase.CONNECTED
                                    isConnecting -> VpnRecoveryPolicy.Phase.CONNECTING
                                    else -> recoveryState.phase
                                },
                                screenPaused = preferencesManager.vpnPausedByScreen,
                                networkAvailable = hasUsableUnderlyingNetwork(),
                                connectPending = isConnecting
                            )
                            val result = VpnRecoveryPolicy.reduce(
                                recoveryState,
                                VpnRecoveryPolicy.Event.ScreenOff(
                                    pauseEnabled = preferencesManager.disconnectOnLock
                                )
                            )
                            if (result.commands.isNotEmpty()) {
                                Logger.i(TAG, "Screen turned off; applying VPN pause policy")
                            }
                            applyRecoveryResult(result)
                        }
                        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                            val result = VpnRecoveryPolicy.reduce(
                                recoveryState.copy(
                                    desiredConnected = preferencesManager.vpnConnectionDesired,
                                    screenPaused = preferencesManager.vpnPausedByScreen,
                                    networkAvailable = hasUsableUnderlyingNetwork()
                                ),
                                VpnRecoveryPolicy.Event.ScreenOn(
                                    resumeEnabled = preferencesManager.connectOnUnlock,
                                    hasServer = recoveryServer() != null
                                )
                            )
                            if (result.commands.any { it == VpnRecoveryPolicy.Command.StartConnection }) {
                                Logger.i(TAG, "Screen turned on; resuming VPN")
                            }
                            applyRecoveryResult(result)
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            unregisterReceiver(it)
            screenReceiver = null
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "Trim memory: $level")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (preferencesManager.vpnConnectionDesired) {
            Logger.i(TAG, "App task removed; sticky VPN service remains responsible for recovery")
        }
    }

    override fun onRevoke() {
        Logger.w(TAG, "VPN permission was revoked by the system")
        requestManualStop("permission revoked")
        super.onRevoke()
    }

    private fun notifyQuickSettingsTiles() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val tileServices = listOf(
            VpnQuickSettingsTileService::class.java,
            DeviceStatsQuickSettingsTileService::class.java,
            TrafficStatsQuickSettingsTileService::class.java,
            ProfileStatsQuickSettingsTileService::class.java
        )

        tileServices.forEach { serviceClass ->
            kotlin.runCatching {
                android.service.quicksettings.TileService.requestListeningState(
                    this,
                    ComponentName(this, serviceClass)
                )
            }.onFailure {
                Log.w(TAG, "Failed to refresh tile ${serviceClass.simpleName}: ${it.message}")
            }
        }
    }
}
