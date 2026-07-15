package com.danila.nimbo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.service.SubscriptionUpdateScheduler
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.ui.screens.MainScreen
import com.danila.nimbo.ui.theme.DEFAULT_COLOR_THEME_INDEX
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import com.danila.nimbo.utils.AppIconManager
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.MyVpnService
import com.danila.nimbo.vpn.VpnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private var pendingSubscriptionUrl by mutableStateOf<String?>(null)
    private var pendingVpnServer: Server? = null
    private var pendingPingTrigger by mutableStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        // ComponentActivity doesn't apply AppCompat's per-app language plumbing,
        // so we override the configuration ourselves before the activity inflates.
        val prefs = PreferencesManager(newBase)
        val lang = prefs.appLanguage
        val wrapped = if (lang.isBlank()) {
            // "System": clear any previously forced locale from the process-global
            // default. Otherwise Locale.getDefault()-based helpers (loc()/tNon())
            // stay stuck on the last picked language after the user switches back to
            // System — which looked like "the language doesn't always switch".
            val systemLocale = newBase.resources.configuration.locales[0]
            Locale.setDefault(systemLocale)
            newBase
        } else {
            val locale = Locale.forLanguageTag(lang)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(wrapped)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainActivity", "Notification permission granted=$granted")
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingVpnServer?.let(::startVpnService)
        } else {
            VpnManager.state.value = com.danila.nimbo.vpn.VpnState.DISCONNECTED
        }
        pendingVpnServer = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        preferencesManager = PreferencesManager(this)
        ensureNimboDefaults()
        AppIconManager.ensureValidAliasState(this, preferencesManager.selectedAppIcon)

        VpnManager.loadLastSelectedServer(this)
        requestNotificationPermission()
        handleIntent(intent)

        CoroutineScope(Dispatchers.IO).launch {
            com.danila.nimbo.network.RemnawaveApiClient.init(application)
            SubscriptionManager.init(application)
        }
        SubscriptionUpdateScheduler.schedule(this)

        setContent {
            val themeIndex by preferencesManager.colorThemeState
            val themeMode by preferencesManager.themeModeState
            val textScale by preferencesManager.textScaleState
            val isCustomAccent by preferencesManager.isCustomAccentState
            val customAccentColorInt by preferencesManager.customAccentColorState
            val gradientEffectsEnabled by preferencesManager.gradientEffectsEnabledState
            val customGradientColor1Int by preferencesManager.customGradientColor1State
            val customGradientColor2Int by preferencesManager.customGradientColor2State
            val customGradientColor3Int by preferencesManager.customGradientColor3State
            val customGradientCount by preferencesManager.customGradientCountState
            val useDynamicColor by preferencesManager.useDynamicColorState
            val backgroundStyle by preferencesManager.backgroundStyleState
            val elementStyle by preferencesManager.elementStyleState
            val backgroundAnimationEnabled by preferencesManager.backgroundAnimationEnabledState
            val highContrastUi by preferencesManager.highContrastUiState
            val reducedTransparency by preferencesManager.reducedTransparencyState
            val pureBlackMode by preferencesManager.pureBlackModeState
            val globalBrightness by preferencesManager.globalBrightnessState
            val globalTransparency by preferencesManager.globalTransparencyState
            val globalBlur by preferencesManager.globalBlurState
            val globalCorners by preferencesManager.globalCornersState

            val customAccentColor = remember(customAccentColorInt) { Color(customAccentColorInt) }
            val customGradientColor1 = remember(customGradientColor1Int) { Color(customGradientColor1Int) }
            val customGradientColor2 = remember(customGradientColor2Int) { Color(customGradientColor2Int) }
            val customGradientColor3 = remember(customGradientColor3Int) { Color(customGradientColor3Int) }
            val systemDark = isSystemInDarkTheme()
            val baseThemeIndex = themeIndex.mod(9)
            val effectiveThemeIndex = when (themeMode) {
                1 -> baseThemeIndex + 9
                2 -> baseThemeIndex
                else -> if (systemDark) baseThemeIndex else baseThemeIndex + 9
            }

            CompositionLocalProvider(LocalPreferencesManager provides preferencesManager) {
                NebulaGuardTheme(
                    themeIndex = effectiveThemeIndex,
                    isCustomAccent = isCustomAccent,
                    customAccentColor = customAccentColor,
                    gradientEffectsEnabled = gradientEffectsEnabled,
                    customGradientColor1 = customGradientColor1,
                    customGradientColor2 = customGradientColor2,
                    customGradientColor3 = customGradientColor3,
                    customGradientCount = customGradientCount,
                    useDynamicColor = useDynamicColor,
                    backgroundStyle = backgroundStyle,
                    elementStyle = elementStyle,
                    backgroundAnimationEnabled = backgroundAnimationEnabled,
                    highContrastUi = highContrastUi,
                    reducedTransparency = reducedTransparency,
                    pureBlackMode = pureBlackMode,
                    textScale = textScale,
                    globalBrightness = globalBrightness,
                    globalTransparency = globalTransparency,
                    globalBlur = globalBlur,
                    globalCorners = globalCorners
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val profiles by viewModel.profilesState.collectAsState()

                    LaunchedEffect(pendingSubscriptionUrl, profiles.size) {
                        pendingSubscriptionUrl?.let { url ->
                            pendingSubscriptionUrl = null
                            viewModel.addSubscription(url)
                        }
                    }

                    LaunchedEffect(pendingPingTrigger) {
                        if (pendingPingTrigger > 0) viewModel.pingAllServers()
                    }

                    MainScreen(
                        initialScreen = intent.getStringExtra("OPEN_SCREEN"),
                        onConnect = ::connectWithPermission,
                        onDisconnect = ::disconnectVpn,
                        onSubscriptionAdded = { url ->
                            viewModel.addSubscription(url)
                        },
                        onProfileDeleted = { url ->
                            viewModel.removeSubscription(url)
                        },
                        onProfileRefresh = { url ->
                            viewModel.refreshSubscription(url)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun ensureNimboDefaults() {
        if (!preferencesManager.getBoolean("nimbo_defaults_applied_v1", false)) {
            preferencesManager.colorTheme = DEFAULT_COLOR_THEME_INDEX
            preferencesManager.themeMode = 2
            preferencesManager.useDynamicColor = false
            preferencesManager.backgroundStyle = 0
            preferencesManager.elementStyle = 0
            preferencesManager.gradientEffectsEnabled = true
            preferencesManager.backgroundAnimationEnabled = true
            preferencesManager.splashScreenEnabled = false
            preferencesManager.setOnboardingComplete(true)
            preferencesManager.setBoolean("nimbo_defaults_applied_v1", true)
        }

        if (!preferencesManager.getBoolean("nimbo_motion_defaults_v2", false)) {
            preferencesManager.backgroundAnimationEnabled = false
            preferencesManager.setBoolean("nimbo_motion_defaults_v2", true)
        }

        if (!preferencesManager.getBoolean("nimbo_accent_motion_defaults_v3", false)) {
            preferencesManager.backgroundAnimationEnabled = true
            preferencesManager.setBoolean("nimbo_accent_motion_defaults_v3", true)
        }

        if (!preferencesManager.getBoolean("nimbo_purple_fallback_applied_v1", false)) {
            if (
                preferencesManager.useDynamicColor &&
                !preferencesManager.isCustomAccent &&
                preferencesManager.colorTheme == 2
            ) {
                preferencesManager.colorTheme = DEFAULT_COLOR_THEME_INDEX
            }
            preferencesManager.setBoolean("nimbo_purple_fallback_applied_v1", true)
        }

        if (!preferencesManager.getBoolean("nimbo_windows_theme_defaults_v4", false)) {
            preferencesManager.colorTheme = DEFAULT_COLOR_THEME_INDEX
            preferencesManager.themeMode = 2
            preferencesManager.useDynamicColor = false
            preferencesManager.setBoolean("nimbo_windows_theme_defaults_v4", true)
        }

        if (!preferencesManager.getBoolean("nimbo_site_blue_default_v5", false)) {
            val usesPreviousDefault =
                preferencesManager.colorTheme.mod(9) == 0 &&
                    !preferencesManager.isCustomAccent &&
                    !preferencesManager.useDynamicColor
            if (usesPreviousDefault) {
                preferencesManager.colorTheme = if (preferencesManager.colorTheme >= 9) {
                    DEFAULT_COLOR_THEME_INDEX + 9
                } else {
                    DEFAULT_COLOR_THEME_INDEX
                }
            }
            preferencesManager.setBoolean("nimbo_site_blue_default_v5", true)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun connectWithPermission(server: Server) {
        pendingVpnServer = server
        VpnManager.selectedServer = server
        server.profileUrl?.let { preferencesManager.saveLastSelectedProfileUrl(it) }

        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            vpnPermissionLauncher.launch(permissionIntent)
        } else {
            startVpnService(server)
            pendingVpnServer = null
        }
    }

    private fun startVpnService(server: Server) {
        val intent = MyVpnService.createConnectIntent(this, server)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun disconnectVpn() {
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("TRIGGER_PING", false) == true) {
            // Bump a counter — the LaunchedEffect downstream watches this token
            // so re-tapping the ping action while the activity is already alive
            // keeps firing fresh pings.
            pendingPingTrigger += 1
            intent.removeExtra("TRIGGER_PING")
        }

        val uri = intent?.data ?: return
        val scheme = uri.scheme ?: return
        if (scheme != "nimbo" && scheme != "nebula") return

        val host = uri.host ?: return
        if (host != "add" && host != "subscription") return

        pendingSubscriptionUrl = extractSubscriptionUrl(uri, scheme, host)
    }

    private fun extractSubscriptionUrl(uri: Uri, scheme: String, host: String): String? {
        val fullUri = uri.toString()
        val prefix = "$scheme://$host/"
        val rawCandidate = when {
            fullUri.startsWith(prefix) -> fullUri.substring(prefix.length)
            !uri.getQueryParameter("url").isNullOrBlank() -> uri.getQueryParameter("url")
            !uri.path.isNullOrBlank() -> uri.path?.trim('/')
            else -> null
        } ?: return null

        val candidate = rawCandidate.trim()
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) return candidate

        val decoded = runCatching { URLDecoder.decode(candidate, Charsets.UTF_8.name()) }
            .getOrNull()
            ?.trim()
            ?: return null

        return if (decoded.startsWith("http://") || decoded.startsWith("https://")) decoded else null
    }
}
