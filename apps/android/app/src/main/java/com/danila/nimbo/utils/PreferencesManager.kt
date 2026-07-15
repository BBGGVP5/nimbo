package com.danila.nimbo.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.model.BuiltinRoutingProfiles
import com.danila.nimbo.model.RoutingProfile
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.model.HomeWidget
import com.danila.nimbo.model.WidgetRegistry
import com.danila.nimbo.model.WidgetType
import com.danila.nimbo.ui.theme.DEFAULT_COLOR_THEME_INDEX
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

class PreferencesManager(context: Context) {
    val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("nebulaguard_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val profilesFile = File(context.filesDir, "profiles_v3.json")
    private val profilesBackupFile = File(context.filesDir, "profiles_v3.bak.json")
    private val profilesIoLock = Any()

    init {
        Log.d("PreferencesManager", "Initialized. Profiles file: ${profilesFile.absolutePath}")
    }

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_VPN_CONNECTION_DESIRED = "vpn_connection_desired"
        private const val KEY_VPN_PAUSED_BY_SCREEN = "vpn_paused_by_screen"
        private const val KEY_TLS_FRAGMENT = "tls_fragment"
        private const val KEY_SHOW_SUBSCRIPTION_LOGO = "show_subscription_logo"
        private const val KEY_USE_SUBSCRIPTION_THEME = "use_subscription_theme"
        private const val KEY_SUBSCRIPTION_THEME_SPEC = "subscription_theme_spec"

        private const val KEY_SHOW_SPEED = "show_speed"
        private const val KEY_SHOW_NOTIFICATION_SPEED = "show_notification_speed"
        private const val KEY_SHOW_NOTIFICATION_CONNECTION_TIME = "show_notification_connection_time"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_TUNNEL_MODE = "tunnel_mode"
        private const val KEY_SUBSCRIPTION_AUTO_UPDATE = "subscription_auto_update"
        private const val KEY_SUBSCRIPTION_UPDATE_INTERVAL = "subscription_update_interval"
        private const val KEY_SEND_HWID = "send_hwid"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_PROXY_BY_APP = "proxy_by_app"
        private const val KEY_APP_BYPASS_LIST = "app_bypass_list"
        private const val KEY_APP_VPN_ONLY_LIST = "app_vpn_only_list"
        private const val KEY_SUB_APP_DIRECT_LIST = "sub_app_direct_list"
        private const val KEY_SUB_APP_PROXY_LIST = "sub_app_proxy_list"
        private const val KEY_CUSTOM_RULE_ICONS = "custom_rule_icons"
        private const val KEY_COLLAPSED_SUBSCRIPTIONS = "collapsed_subscriptions"
        private const val KEY_COLLAPSED_THEME_SECTIONS = "collapsed_theme_sections"
        private const val KEY_TELEGRAM_CHANNEL = "telegram_channel"
        private const val KEY_VPN_URL = "vpn_url"
        private const val KEY_WEBSITE_URL = "website_url"
        private const val KEY_SUPPORT_URL = "support_url"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_LAST_SELECTED_SERVER = "last_selected_server"
        private const val KEY_LAST_SELECTED_PROFILE_URL = "last_selected_profile_url"
        private const val KEY_HOME_WIDGETS = "home_widgets"
        private const val KEY_HIDDEN_WIDGETS = "hidden_widgets"
        private const val KEY_SHOW_UPDATE_DIALOG = "show_update_dialog"
        private const val KEY_LAST_UPDATE_NOTIFIED_VERSION = "last_update_notified_version"
        private const val KEY_UPDATE_NOTIFICATION_COUNT = "update_notification_count"
        private const val KEY_LAST_UPDATE_NOTIFICATION_TIME = "last_update_notification_time"
        private const val KEY_UPDATE_DIALOG_SKIPPED_VERSION = "update_dialog_skipped_version"
        private const val KEY_LAST_UPDATE_CHECK_TIME = "last_update_check_time"
        private const val KEY_PING_TYPE = "ping_type"
        private const val KEY_NOTIFICATION_HISTORY = "notification_history"
        private const val KEY_LAST_PING_TIME = "last_ping_time"
        private const val KEY_SERVER_PING_CACHE = "server_ping_cache_v1"
        private const val KEY_LAST_APP_START_TIME = "last_app_start_time"
        private const val KEY_LAST_CONNECT_AT_MS = "last_connect_at_ms"
        private const val KEY_LAST_CONNECT_AT_TEXT = "last_connect_at_text"
        private const val KEY_LAST_CONNECTED_PROFILE_URL = "last_connected_profile_url"
        private const val KEY_LAST_CONNECTED_SERVER_NAME = "last_connected_server_name"
        private const val KEY_AUTO_BYPASS_BY_NETWORK = "auto_bypass_by_network"
        private const val KEY_BS_BYPASS_MODE = "bs_bypass_mode"
        private const val KEY_LIVE_PING_ENABLED = "live_ping_enabled"
        private const val KEY_DISCONNECT_ON_LOCK = "disconnect_on_lock"
        private const val KEY_CONNECT_ON_UNLOCK = "connect_on_unlock"
        private const val KEY_MEMORY_MONITORING = "memory_monitoring"
        private const val KEY_MEMORY_LIMIT_MB = "memory_limit_mb"
        private const val KEY_MEMORY_LIMIT_DISABLED = "memory_limit_disabled"
        private const val KEY_PING_ON_STARTUP = "ping_on_startup"
        private const val KEY_UPDATE_SUB_ON_STARTUP = "update_sub_on_startup"
        private const val KEY_PING_ON_UPDATE = "ping_on_update"
        private const val KEY_SERVER_SORT_ORDER = "server_sort_order"
        private const val KEY_PINNED_PROFILE_URLS = "pinned_profile_urls"
        private const val KEY_PINNED_SERVER_KEYS = "pinned_server_keys"
        private const val KEY_SERVER_NAME_OVERRIDES = "server_name_overrides_v1"
        private const val KEY_HIDDEN_SERVER_KEYS = "hidden_server_keys_v1"
        private const val KEY_ROUTING_ENABLED = "routing_enabled"
        private const val KEY_ROUTING_PROFILE_JSON = "routing_profile_json"
        private const val KEY_ROUTING_BUILTIN_OVERRIDES_JSON = "routing_builtin_overrides_json"
        private const val KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID = "active_builtin_routing_profile_id"
        private const val KEY_HARDWARE_ID = "hardware_id"
        private const val KEY_SHOW_VERSION_IN_HEADER = "show_version_in_header"
        private const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"
        private const val KEY_IS_CUSTOM_ACCENT = "is_custom_accent"
        private const val KEY_GRADIENT_EFFECTS_ENABLED = "gradient_effects_enabled"
        private const val KEY_CUSTOM_GRADIENT_COLOR_1 = "custom_gradient_color_1"
        private const val KEY_CUSTOM_GRADIENT_COLOR_2 = "custom_gradient_color_2"
        private const val KEY_CUSTOM_GRADIENT_COLOR_3 = "custom_gradient_color_3"
        private const val KEY_CUSTOM_GRADIENT_COUNT = "custom_gradient_count"
        private const val KEY_GLOW_EFFECTS_ENABLED = "glow_effects_enabled"
        private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
        private const val KEY_SELECTED_APP_ICON = "selected_app_icon"
        private const val KEY_CUSTOM_APP_ICON_BASE64 = "custom_app_icon_base64"
        private const val KEY_BACKGROUND_STYLE = "background_style"
        private const val KEY_ELEMENT_STYLE = "element_style"
        private const val KEY_BACKGROUND_ANIMATION_ENABLED = "background_animation_enabled"
        private const val KEY_HIGH_CONTRAST_UI = "high_contrast_ui"
        private const val KEY_REDUCED_TRANSPARENCY = "reduced_transparency"
        private const val KEY_PURE_BLACK_MODE = "pure_black_mode"
        private const val KEY_VISUAL_STYLE = "visual_style"
        private const val KEY_SPLASH_SCREEN_ENABLED = "splash_screen_enabled"
        private const val KEY_GLOBAL_BRIGHTNESS = "global_brightness"
        private const val KEY_GLOBAL_TRANSPARENCY = "global_transparency"
        private const val KEY_GLOBAL_BLUR = "global_blur_radius_val"
        private const val KEY_GLOBAL_CORNERS = "global_corner_radius_val"
        private const val KEY_PING_PROTOCOL = "ping_protocol"
        private const val KEY_PING_URL = "ping_url"
        private const val KEY_PING_TIMEOUT = "ping_timeout"
        private const val KEY_PING_DISPLAY_MODE = "ping_display_mode"
        private const val KEY_PING_THROUGH_PROXY = "ping_through_proxy"
        private const val KEY_CUSTOM_DEVICE_NAME = "custom_device_name"
        private const val KEY_CUSTOM_DEVICE_NAMES = "custom_device_names"
        private const val KEY_HOME_SUBSCRIPTION_EXPANDED = "home_subscription_expanded"
        private const val KEY_SERVER_LIST_SUBSCRIPTION_EXPANDED = "server_list_subscription_expanded"
        private const val KEY_DEVICE_ORDER = "device_order"
        private const val KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED = "allow_server_switch_while_connected"
        private const val KEY_CONNECT_BUTTON_STYLE = "connect_button_style"
        private const val KEY_CONNECT_BUTTON_SIZE_SCALE = "connect_button_size_scale"
        private const val KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED = "show_connection_widgets_only_when_connected"
        private const val KEY_SHOW_PROTECTED_STATUS_CARD = "show_protected_status_card"
        private const val KEY_SHOW_CONNECTION_TIME_CARD = "show_connection_time_card"
        private const val KEY_AUTO_ROTATION_ENABLED = "auto_rotation_enabled"
        private const val KEY_KEEP_DEVICE_ACTIVE = "keep_device_active"
        private const val KEY_ALLOW_LAN_CONNECTIONS = "allow_lan_connections"
        private const val KEY_ALLOW_HOTSPOT_ACCESS = "allow_hotspot_access"
        private const val KEY_LAN_THROUGH_PROXY = "lan_through_proxy"
        private const val KEY_SOCKS5_AUTH_ENABLED = "socks5_auth_enabled"
        private const val KEY_SOCKS5_AUTH_LOGIN = "socks5_auth_login"
        private const val KEY_SOCKS5_AUTH_PASSWORD = "socks5_auth_password"
        private const val KEY_SOCKS5_AUTH_PORT = "socks5_auth_port"
        private const val KEY_BLOCK_UDP = "block_udp"
        private const val KEY_MUX_ENABLED = "mux_enabled"
        private const val KEY_PACKET_FRAGMENTATION_ENABLED = "packet_fragmentation_enabled"
        private const val KEY_TRAFFIC_SNIFFING_ENABLED = "traffic_sniffing_enabled"
        private const val KEY_IDLE_TIMEOUT_SECONDS = "idle_timeout_seconds"
        private const val KEY_TCP_CONNECTION_LIMIT = "tcp_connection_limit"
        private const val KEY_UDP_CONNECTION_LIMIT = "udp_connection_limit"
        private const val KEY_DIAGNOSTIC_LOG_RETENTION_HOURS = "diagnostic_log_retention_hours"
        private const val KEY_VPN_IP_TYPE = "vpn_ip_type"
        private const val KEY_VPN_DNS_MODE = "vpn_dns_mode"
        private const val KEY_SUBSCRIPTION_USER_AGENT = "subscription_user_agent"
        private const val KEY_SUBSCRIPTION_USER_AGENT_MODE = "subscription_user_agent_mode"
        private const val KEY_SUBSCRIPTION_USER_AGENT_CUSTOM = "subscription_user_agent_custom"
        private const val KEY_NOTIFY_ON_EXPIRY = "notify_on_expiry"
        private const val KEY_EXPIRY_NOTIFY_DAYS = "expiry_notify_days"
        private const val KEY_NOTIFY_ON_SUBSCRIPTION_UPDATE = "notify_on_subscription_update"
        private const val KEY_TRAFFIC_TOTAL_UP = "traffic_total_up"
        private const val KEY_TRAFFIC_TOTAL_DOWN = "traffic_total_down"
        private const val KEY_TRAFFIC_MONTH_KEY = "traffic_month_key"
        private const val KEY_TRAFFIC_MONTH_UP = "traffic_month_up"
        private const val KEY_TRAFFIC_MONTH_DOWN = "traffic_month_down"
    }

    private data class SavedServerPing(
        val ping: Int = -1,
        val timestamp: Long? = null
    )

    // State для отслеживания изменений темы (инициализация из SP)
    val colorThemeState = mutableStateOf(sharedPreferences.getInt(KEY_COLOR_THEME, DEFAULT_COLOR_THEME_INDEX))
    val themeModeState = mutableStateOf(sharedPreferences.getInt(KEY_THEME_MODE, sharedPreferences.getInt("theme_mode_override", 0)).coerceIn(0, 2))
    val textScaleState = mutableStateOf(sharedPreferences.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.85f, 1.25f))
    val showSpeedState = mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_SPEED, true))
    val tlsFragmentState = mutableStateOf(sharedPreferences.getBoolean(KEY_TLS_FRAGMENT, false))
    val showSubscriptionLogoState = mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_SUBSCRIPTION_LOGO, true))
    val useSubscriptionThemeState = mutableStateOf(sharedPreferences.getBoolean(KEY_USE_SUBSCRIPTION_THEME, false))
    val memoryMonitoringState = mutableStateOf(sharedPreferences.getBoolean(KEY_MEMORY_MONITORING, false))
    val memoryLimitMbState = mutableStateOf(sharedPreferences.getInt(KEY_MEMORY_LIMIT_MB, 160).coerceIn(40, 300))
    val memoryLimitDisabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_MEMORY_LIMIT_DISABLED, true))
    val customAccentColorState = mutableStateOf(sharedPreferences.getInt(KEY_CUSTOM_ACCENT_COLOR, 0xFF7C5DFA.toInt()))
    val isCustomAccentState = mutableStateOf(sharedPreferences.getBoolean(KEY_IS_CUSTOM_ACCENT, false))
    val showVersionInHeaderState = mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_VERSION_IN_HEADER, true))
    val gradientEffectsEnabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_GRADIENT_EFFECTS_ENABLED, true))
    val customGradientColor1State = mutableStateOf(sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_1, 0xFF7C5DFA.toInt()))
    val customGradientColor2State = mutableStateOf(sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_2, 0xFF00E5B0.toInt()))
    val customGradientColor3State = mutableStateOf(sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_3, 0xFF00D2FF.toInt()))
    val customGradientCountState = mutableStateOf(sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COUNT, 1))
    val glowEffectsEnabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_GLOW_EFFECTS_ENABLED, true))
    val useDynamicColorState = mutableStateOf(sharedPreferences.getBoolean(KEY_USE_DYNAMIC_COLOR, false))
    val selectedAppIconState = mutableStateOf(sharedPreferences.getInt(KEY_SELECTED_APP_ICON, 0))
    val customAppIconBase64State = mutableStateOf(sharedPreferences.getString(KEY_CUSTOM_APP_ICON_BASE64, null))
    val backgroundStyleState = mutableStateOf(sharedPreferences.getInt(KEY_BACKGROUND_STYLE, 0))
    val elementStyleState = mutableStateOf(sharedPreferences.getInt(KEY_ELEMENT_STYLE, 0))
    val backgroundAnimationEnabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_BACKGROUND_ANIMATION_ENABLED, true))
    val highContrastUiState = mutableStateOf(sharedPreferences.getBoolean(KEY_HIGH_CONTRAST_UI, false))
    val reducedTransparencyState = mutableStateOf(sharedPreferences.getBoolean(KEY_REDUCED_TRANSPARENCY, false))
    val pureBlackModeState = mutableStateOf(sharedPreferences.getBoolean(KEY_PURE_BLACK_MODE, false))
    val splashScreenEnabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_SPLASH_SCREEN_ENABLED, true))
    val globalBrightnessState = mutableStateOf(sharedPreferences.getFloat(KEY_GLOBAL_BRIGHTNESS, 1.0f).coerceIn(0.5f, 2.0f))
    val globalTransparencyState = mutableStateOf(sharedPreferences.getFloat(KEY_GLOBAL_TRANSPARENCY, 0.0f).coerceIn(0.0f, 1.0f))
    val globalBlurState = mutableStateOf(sharedPreferences.getFloat(KEY_GLOBAL_BLUR, 25.0f).coerceIn(0.0f, 80.0f))
    val globalCornersState = mutableStateOf(sharedPreferences.getFloat(KEY_GLOBAL_CORNERS, 1.0f).coerceIn(0.25f, 4.0f))

    val pingProtocolState = mutableStateOf(sharedPreferences.getInt(KEY_PING_PROTOCOL, 0))
    val pingUrlState = mutableStateOf(sharedPreferences.getString(KEY_PING_URL, "https://www.gstatic.com/generate_204") ?: "https://www.gstatic.com/generate_204")
    val pingTimeoutState = mutableStateOf(sharedPreferences.getInt(KEY_PING_TIMEOUT, 3))
    val pingDisplayModeState = mutableStateOf(sharedPreferences.getInt(KEY_PING_DISPLAY_MODE, 0))
    val pingThroughProxyState = mutableStateOf(sharedPreferences.getBoolean(KEY_PING_THROUGH_PROXY, false))
    val subscriptionUserAgentModeState = mutableStateOf(readSubscriptionUserAgentMode())
    val customSubscriptionUserAgentState = mutableStateOf(readCustomSubscriptionUserAgent())
    val autoBypassByNetworkState = mutableStateOf(sharedPreferences.getBoolean(KEY_AUTO_BYPASS_BY_NETWORK, true))
    val allowServerSwitchWhileConnectedState = mutableStateOf(sharedPreferences.getBoolean(KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED, false))
    val connectButtonStyleState = mutableStateOf(sharedPreferences.getInt(KEY_CONNECT_BUTTON_STYLE, 0))
    val connectButtonSizeScaleState = mutableStateOf(sharedPreferences.getFloat(KEY_CONNECT_BUTTON_SIZE_SCALE, 1f))
    val showConnectionWidgetsOnlyWhenConnectedState = mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED, false))
    val showProtectedStatusCardState = mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_PROTECTED_STATUS_CARD, true))
    val showConnectionTimeCardState = mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_CONNECTION_TIME_CARD, true))
    val autoRotationEnabledState = mutableStateOf(sharedPreferences.getBoolean(KEY_AUTO_ROTATION_ENABLED, false))

    var isRoutingEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ROUTING_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ROUTING_ENABLED, value).apply()

    var routingProfileJson: String?
        get() = sharedPreferences.getString(KEY_ROUTING_PROFILE_JSON, null)
        set(value) = sharedPreferences.edit()
            .putString(KEY_ROUTING_PROFILE_JSON, value)
            .remove(KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID)
            .apply()

    fun saveRoutingProfile(profile: RoutingProfile?) {
        when {
            profile == null -> sharedPreferences.edit()
                .remove(KEY_ROUTING_PROFILE_JSON)
                .remove(KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID)
                .apply()
            profile.id != null && BuiltinRoutingProfiles.byId(profile.id) != null ->
                saveBuiltinRoutingProfile(profile)
            else -> saveImportedRoutingProfile(profile)
        }
    }

    /**
     * Возвращает уникальный ID устройства (HWID).
     * Если он еще не создан, генерирует новый UUID и сохраняет его навсегда.
     */
    var hardwareId: String
        get() {
            var id = sharedPreferences.getString(KEY_HARDWARE_ID, null)
            if (id == null) {
                id = java.util.UUID.randomUUID().toString().uppercase()
                sharedPreferences.edit().putString(KEY_HARDWARE_ID, id).apply()
                Log.d("PreferencesManager", "Generated new stable HWID: $id")
            }
            return id
        }
        set(value) = sharedPreferences.edit().putString(KEY_HARDWARE_ID, value).apply()

    val defaultSubscriptionUserAgent: String
        get() = "Nimbo/${BuildConfig.VERSION_NAME}/Android"

    val happSubscriptionUserAgent: String
        get() = "Happ/${BuildConfig.VERSION_NAME}"

    val incySubscriptionUserAgent: String
        get() = "Incy/${BuildConfig.VERSION_NAME}"

    private fun readSubscriptionUserAgentMode(): Int {
        if (sharedPreferences.contains(KEY_SUBSCRIPTION_USER_AGENT_MODE)) {
            return sharedPreferences.getInt(KEY_SUBSCRIPTION_USER_AGENT_MODE, 0).coerceIn(0, 3)
        }
        val legacy = sharedPreferences.getString(KEY_SUBSCRIPTION_USER_AGENT, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return 0
        return when {
            legacy.equals(defaultSubscriptionUserAgent, ignoreCase = true) -> 0
            legacy.contains("happ", ignoreCase = true) -> 1
            legacy.contains("incy", ignoreCase = true) -> 2
            else -> 3
        }
    }

    private fun readCustomSubscriptionUserAgent(): String {
        val stored = sharedPreferences.getString(KEY_SUBSCRIPTION_USER_AGENT_CUSTOM, null)
            ?.trim()
            .orEmpty()
        if (stored.isNotBlank()) return stored

        if (!sharedPreferences.contains(KEY_SUBSCRIPTION_USER_AGENT_MODE)) {
            val legacy = sharedPreferences.getString(KEY_SUBSCRIPTION_USER_AGENT, null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            if (legacy.isNotBlank() && readSubscriptionUserAgentMode() == 3) return legacy
        }
        return ""
    }

    var subscriptionUserAgentMode: Int
        get() = readSubscriptionUserAgentMode()
        set(value) {
            val safe = value.coerceIn(0, 3)
            sharedPreferences.edit()
                .putInt(KEY_SUBSCRIPTION_USER_AGENT_MODE, safe)
                .putString(KEY_SUBSCRIPTION_USER_AGENT, userAgentForMode(safe))
                .apply()
            subscriptionUserAgentModeState.value = safe
        }

    var customSubscriptionUserAgent: String
        get() = readCustomSubscriptionUserAgent()
        set(value) {
            val normalized = value.trim()
            sharedPreferences.edit()
                .putString(KEY_SUBSCRIPTION_USER_AGENT_CUSTOM, normalized)
                .putString(KEY_SUBSCRIPTION_USER_AGENT, userAgentForMode(subscriptionUserAgentMode, normalized))
                .apply()
            customSubscriptionUserAgentState.value = normalized
        }

    private fun userAgentForMode(mode: Int, customOverride: String? = null): String {
        val custom = customOverride ?: customSubscriptionUserAgent
        return when (mode.coerceIn(0, 3)) {
            1 -> happSubscriptionUserAgent
            2 -> incySubscriptionUserAgent
            3 -> custom.trim().ifBlank { defaultSubscriptionUserAgent }
            else -> defaultSubscriptionUserAgent
        }
    }

    // User-Agent header sent when fetching subscription URLs.
    var subscriptionUserAgent: String
        get() = userAgentForMode(subscriptionUserAgentMode)
        set(value) {
            val normalized = value.trim()
            val nextMode = when {
                normalized.isBlank() || normalized.equals(defaultSubscriptionUserAgent, ignoreCase = true) -> 0
                normalized.equals(happSubscriptionUserAgent, ignoreCase = true) -> 1
                normalized.equals(incySubscriptionUserAgent, ignoreCase = true) -> 2
                normalized.contains("happ", ignoreCase = true) &&
                    !normalized.contains("nimbo", ignoreCase = true) -> 1
                normalized.contains("incy", ignoreCase = true) -> 2
                else -> 3
            }
            if (nextMode == 3) {
                sharedPreferences.edit()
                    .putString(KEY_SUBSCRIPTION_USER_AGENT_CUSTOM, normalized)
                    .putString(KEY_SUBSCRIPTION_USER_AGENT, normalized)
                    .putInt(KEY_SUBSCRIPTION_USER_AGENT_MODE, nextMode)
                    .apply()
                customSubscriptionUserAgentState.value = normalized
            } else {
                sharedPreferences.edit()
                    .putString(KEY_SUBSCRIPTION_USER_AGENT, userAgentForMode(nextMode))
                    .putInt(KEY_SUBSCRIPTION_USER_AGENT_MODE, nextMode)
                    .apply()
            }
            subscriptionUserAgentModeState.value = nextMode
        }

    // Language tag for in-app locale override. Blank = system default.
    // MainActivity.attachBaseContext picks this up before the activity inflates.
    var appLanguage: String
        get() = sharedPreferences.getString("app_language", "") ?: ""
        set(value) = sharedPreferences.edit().putString("app_language", value.trim()).apply()

    /** Built-in routing profiles with saved user edits overlaid on top of app defaults. */
    fun builtinRoutingProfiles(): List<RoutingProfile> =
        BuiltinRoutingProfiles.resolve(readBuiltinRoutingOverrides())

    /**
     * Returns the selected built-in profile. Older app versions saved only its name;
     * that legacy value is migrated lazily the first time it is read.
     */
    fun activeBuiltinRoutingProfileId(): String? {
        sharedPreferences.getString(KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID, null)
            ?.trim()
            ?.takeIf { BuiltinRoutingProfiles.byId(it) != null }
            ?.let { return it }

        val legacyId = routingProfileJson
            ?.let { json -> runCatching { gson.fromJson(json, RoutingProfile::class.java) }.getOrNull() }
            ?.let { profile -> BuiltinRoutingProfiles.idForLegacyName(profile.name) }

        if (legacyId != null) {
            sharedPreferences.edit()
                .putString(KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID, legacyId)
                .apply()
        }
        return legacyId
    }

    fun activateBuiltinRoutingProfile(id: String): RoutingProfile {
        val profile = builtinRoutingProfiles().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown built-in routing profile: $id")
        sharedPreferences.edit()
            .putString(KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID, id)
            .putString(KEY_ROUTING_PROFILE_JSON, gson.toJson(profile))
            .putBoolean(KEY_ROUTING_ENABLED, true)
            .apply()
        return profile
    }

    /** Saves an override only; future updates can safely add new default rules. */
    fun saveBuiltinRoutingProfile(profile: RoutingProfile): RoutingProfile {
        val id = profile.id?.trim().orEmpty()
        val default = BuiltinRoutingProfiles.byId(id)
            ?: throw IllegalArgumentException("Unknown built-in routing profile: $id")
        val normalized = profile.copy(
            id = id,
            builtin = true,
            name = profile.name?.trim().takeUnless { it.isNullOrBlank() } ?: default.name,
            description = profile.description?.trim().takeUnless { it.isNullOrBlank() } ?: default.description
        )
        val overrides = readBuiltinRoutingOverrides().toMutableMap().apply { put(id, normalized) }
        val editor = sharedPreferences.edit()
            .putString(KEY_ROUTING_BUILTIN_OVERRIDES_JSON, gson.toJson(overrides))
        if (activeBuiltinRoutingProfileId() == id) {
            editor.putString(KEY_ROUTING_PROFILE_JSON, gson.toJson(normalized))
        }
        editor.apply()
        return normalized
    }

    fun resetBuiltinRoutingProfile(id: String): RoutingProfile {
        val default = BuiltinRoutingProfiles.byId(id)
            ?: throw IllegalArgumentException("Unknown built-in routing profile: $id")
        val overrides = readBuiltinRoutingOverrides().toMutableMap().apply { remove(id) }
        val editor = sharedPreferences.edit()
            .putString(KEY_ROUTING_BUILTIN_OVERRIDES_JSON, gson.toJson(overrides))
        if (activeBuiltinRoutingProfileId() == id) {
            editor.putString(KEY_ROUTING_PROFILE_JSON, gson.toJson(default))
        }
        editor.apply()
        return default
    }

    fun saveImportedRoutingProfile(profile: RoutingProfile) {
        val imported = profile.copy(id = null, builtin = false)
        sharedPreferences.edit()
            .remove(KEY_ACTIVE_BUILTIN_ROUTING_PROFILE_ID)
            .putString(KEY_ROUTING_PROFILE_JSON, gson.toJson(imported))
            .putBoolean(KEY_ROUTING_ENABLED, true)
            .apply()
    }

    fun loadRoutingProfile(): RoutingProfile? {
        activeBuiltinRoutingProfileId()?.let { id ->
            return builtinRoutingProfiles().firstOrNull { it.id == id }
        }
        val json = routingProfileJson ?: return null
        return runCatching { gson.fromJson(json, RoutingProfile::class.java) }.getOrNull()
    }

    private fun readBuiltinRoutingOverrides(): Map<String, RoutingProfile> {
        val json = sharedPreferences.getString(KEY_ROUTING_BUILTIN_OVERRIDES_JSON, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, RoutingProfile>>() {}.type
        return runCatching { gson.fromJson<Map<String, RoutingProfile>>(json, type) ?: emptyMap() }
            .getOrDefault(emptyMap())
            .filterKeys { BuiltinRoutingProfiles.byId(it) != null }
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_COLOR_THEME -> colorThemeState.value = prefs.getInt(KEY_COLOR_THEME, DEFAULT_COLOR_THEME_INDEX)
            KEY_THEME_MODE -> themeModeState.value = prefs.getInt(KEY_THEME_MODE, 0).coerceIn(0, 2)
            KEY_TEXT_SCALE -> textScaleState.value = prefs.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.85f, 1.25f)
            KEY_SHOW_SPEED -> showSpeedState.value = prefs.getBoolean(KEY_SHOW_SPEED, true)
            KEY_IS_CUSTOM_ACCENT -> isCustomAccentState.value = prefs.getBoolean(KEY_IS_CUSTOM_ACCENT, false)
            KEY_CUSTOM_ACCENT_COLOR -> customAccentColorState.value = prefs.getInt(KEY_CUSTOM_ACCENT_COLOR, 0xFF7C5DFA.toInt())
            KEY_GRADIENT_EFFECTS_ENABLED -> gradientEffectsEnabledState.value = prefs.getBoolean(KEY_GRADIENT_EFFECTS_ENABLED, true)
            KEY_CUSTOM_GRADIENT_COLOR_1 -> customGradientColor1State.value = prefs.getInt(KEY_CUSTOM_GRADIENT_COLOR_1, 0xFF7C5DFA.toInt())
            KEY_CUSTOM_GRADIENT_COLOR_2 -> customGradientColor2State.value = prefs.getInt(KEY_CUSTOM_GRADIENT_COLOR_2, 0xFF00E5B0.toInt())
            KEY_CUSTOM_GRADIENT_COLOR_3 -> customGradientColor3State.value = prefs.getInt(KEY_CUSTOM_GRADIENT_COLOR_3, 0xFF00D2FF.toInt())
            KEY_CUSTOM_GRADIENT_COUNT -> customGradientCountState.value = prefs.getInt(KEY_CUSTOM_GRADIENT_COUNT, 1)
            KEY_SHOW_VERSION_IN_HEADER -> showVersionInHeaderState.value = prefs.getBoolean(KEY_SHOW_VERSION_IN_HEADER, true)
            KEY_MEMORY_MONITORING -> memoryMonitoringState.value = prefs.getBoolean(KEY_MEMORY_MONITORING, false)
            KEY_MEMORY_LIMIT_MB -> memoryLimitMbState.value = prefs.getInt(KEY_MEMORY_LIMIT_MB, 160).coerceIn(40, 300)
            KEY_MEMORY_LIMIT_DISABLED -> memoryLimitDisabledState.value = prefs.getBoolean(KEY_MEMORY_LIMIT_DISABLED, true)
            KEY_GLOW_EFFECTS_ENABLED -> glowEffectsEnabledState.value = prefs.getBoolean(KEY_GLOW_EFFECTS_ENABLED, true)
            KEY_USE_DYNAMIC_COLOR -> useDynamicColorState.value = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, false)
            KEY_SELECTED_APP_ICON -> selectedAppIconState.value = prefs.getInt(KEY_SELECTED_APP_ICON, 0)
            KEY_CUSTOM_APP_ICON_BASE64 -> customAppIconBase64State.value = prefs.getString(KEY_CUSTOM_APP_ICON_BASE64, null)
            KEY_BACKGROUND_STYLE -> backgroundStyleState.value = prefs.getInt(KEY_BACKGROUND_STYLE, prefs.getInt(KEY_VISUAL_STYLE, 0))
            KEY_ELEMENT_STYLE -> elementStyleState.value = prefs.getInt(KEY_ELEMENT_STYLE, prefs.getInt(KEY_VISUAL_STYLE, 0))
            KEY_BACKGROUND_ANIMATION_ENABLED -> backgroundAnimationEnabledState.value = prefs.getBoolean(KEY_BACKGROUND_ANIMATION_ENABLED, true)
            KEY_HIGH_CONTRAST_UI -> highContrastUiState.value = prefs.getBoolean(KEY_HIGH_CONTRAST_UI, false)
            KEY_REDUCED_TRANSPARENCY -> reducedTransparencyState.value = prefs.getBoolean(KEY_REDUCED_TRANSPARENCY, false)
            KEY_PURE_BLACK_MODE -> pureBlackModeState.value = prefs.getBoolean(KEY_PURE_BLACK_MODE, false)
            KEY_SPLASH_SCREEN_ENABLED -> splashScreenEnabledState.value = prefs.getBoolean(KEY_SPLASH_SCREEN_ENABLED, true)
            KEY_GLOBAL_BRIGHTNESS -> globalBrightnessState.value = prefs.getFloat(KEY_GLOBAL_BRIGHTNESS, 1.0f).coerceIn(0.5f, 2.0f)
            KEY_GLOBAL_TRANSPARENCY -> globalTransparencyState.value = prefs.getFloat(KEY_GLOBAL_TRANSPARENCY, 0.0f).coerceIn(0.0f, 1.0f)
            KEY_GLOBAL_BLUR -> globalBlurState.value = prefs.getFloat(KEY_GLOBAL_BLUR, 25.0f).coerceIn(0.0f, 80.0f)
            KEY_GLOBAL_CORNERS -> globalCornersState.value = prefs.getFloat(KEY_GLOBAL_CORNERS, 1.0f).coerceIn(0.25f, 4.0f)
            KEY_PING_PROTOCOL -> pingProtocolState.value = prefs.getInt(KEY_PING_PROTOCOL, 0)
            KEY_PING_URL -> pingUrlState.value = prefs.getString(KEY_PING_URL, "https://www.gstatic.com/generate_204") ?: "https://www.gstatic.com/generate_204"
            KEY_PING_TIMEOUT -> pingTimeoutState.value = prefs.getInt(KEY_PING_TIMEOUT, 3)
            KEY_PING_DISPLAY_MODE -> pingDisplayModeState.value = prefs.getInt(KEY_PING_DISPLAY_MODE, 0)
            KEY_PING_THROUGH_PROXY -> pingThroughProxyState.value = prefs.getBoolean(KEY_PING_THROUGH_PROXY, false)
            KEY_SUBSCRIPTION_USER_AGENT_MODE -> subscriptionUserAgentModeState.value = prefs.getInt(KEY_SUBSCRIPTION_USER_AGENT_MODE, 0).coerceIn(0, 3)
            KEY_SUBSCRIPTION_USER_AGENT_CUSTOM -> customSubscriptionUserAgentState.value = prefs.getString(KEY_SUBSCRIPTION_USER_AGENT_CUSTOM, "") ?: ""
            KEY_AUTO_BYPASS_BY_NETWORK -> autoBypassByNetworkState.value = true
            KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED -> allowServerSwitchWhileConnectedState.value = prefs.getBoolean(KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED, false)
            KEY_CONNECT_BUTTON_STYLE -> connectButtonStyleState.value = prefs.getInt(KEY_CONNECT_BUTTON_STYLE, 0)
            KEY_CONNECT_BUTTON_SIZE_SCALE -> connectButtonSizeScaleState.value = prefs.getFloat(KEY_CONNECT_BUTTON_SIZE_SCALE, 1f)
            KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED -> showConnectionWidgetsOnlyWhenConnectedState.value = prefs.getBoolean(KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED, false)
            KEY_SHOW_PROTECTED_STATUS_CARD -> showProtectedStatusCardState.value = prefs.getBoolean(KEY_SHOW_PROTECTED_STATUS_CARD, true)
            KEY_SHOW_CONNECTION_TIME_CARD -> showConnectionTimeCardState.value = prefs.getBoolean(KEY_SHOW_CONNECTION_TIME_CARD, true)
            KEY_AUTO_ROTATION_ENABLED -> autoRotationEnabledState.value = prefs.getBoolean(KEY_AUTO_ROTATION_ENABLED, false)
            KEY_VISUAL_STYLE -> {
                val legacy = prefs.getInt(KEY_VISUAL_STYLE, 0)
                if (!prefs.contains(KEY_BACKGROUND_STYLE)) backgroundStyleState.value = legacy
                if (!prefs.contains(KEY_ELEMENT_STYLE)) elementStyleState.value = legacy
            }
        }
    }

    init {
        // Инициализируем состояние темы из SharedPreferences
        colorThemeState.value = sharedPreferences.getInt(KEY_COLOR_THEME, DEFAULT_COLOR_THEME_INDEX)
        themeModeState.value = sharedPreferences.getInt(KEY_THEME_MODE, sharedPreferences.getInt("theme_mode_override", 0)).coerceIn(0, 2)
        textScaleState.value = sharedPreferences.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.85f, 1.25f)
        showSpeedState.value = sharedPreferences.getBoolean(KEY_SHOW_SPEED, true)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        memoryMonitoringState.value = sharedPreferences.getBoolean(KEY_MEMORY_MONITORING, false)
        memoryLimitMbState.value = sharedPreferences.getInt(KEY_MEMORY_LIMIT_MB, 160).coerceIn(40, 300)
        memoryLimitDisabledState.value = sharedPreferences.getBoolean(KEY_MEMORY_LIMIT_DISABLED, true)
        customAccentColorState.value = sharedPreferences.getInt(KEY_CUSTOM_ACCENT_COLOR, 0xFF7C5DFA.toInt())
        isCustomAccentState.value = sharedPreferences.getBoolean(KEY_IS_CUSTOM_ACCENT, false)
        showVersionInHeaderState.value = sharedPreferences.getBoolean(KEY_SHOW_VERSION_IN_HEADER, true)
        gradientEffectsEnabledState.value = sharedPreferences.getBoolean(KEY_GRADIENT_EFFECTS_ENABLED, true)
        customGradientColor1State.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_1, 0xFF7C5DFA.toInt())
        customGradientColor2State.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_2, 0xFF00E5B0.toInt())
        customGradientColor3State.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_3, 0xFF00D2FF.toInt())
        customGradientCountState.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COUNT, 1)
        glowEffectsEnabledState.value = sharedPreferences.getBoolean(KEY_GLOW_EFFECTS_ENABLED, true)
        useDynamicColorState.value = sharedPreferences.getBoolean(KEY_USE_DYNAMIC_COLOR, false)
        selectedAppIconState.value = sharedPreferences.getInt(KEY_SELECTED_APP_ICON, 0)
        customAppIconBase64State.value = sharedPreferences.getString(KEY_CUSTOM_APP_ICON_BASE64, null)
        val legacyStyle = sharedPreferences.getInt(KEY_VISUAL_STYLE, 0)
        backgroundStyleState.value = sharedPreferences.getInt(KEY_BACKGROUND_STYLE, legacyStyle)
        elementStyleState.value = sharedPreferences.getInt(KEY_ELEMENT_STYLE, legacyStyle)
        backgroundAnimationEnabledState.value = sharedPreferences.getBoolean(KEY_BACKGROUND_ANIMATION_ENABLED, true)
        highContrastUiState.value = sharedPreferences.getBoolean(KEY_HIGH_CONTRAST_UI, false)
        reducedTransparencyState.value = sharedPreferences.getBoolean(KEY_REDUCED_TRANSPARENCY, false)
        pureBlackModeState.value = sharedPreferences.getBoolean(KEY_PURE_BLACK_MODE, false)
        splashScreenEnabledState.value = sharedPreferences.getBoolean(KEY_SPLASH_SCREEN_ENABLED, true)
        globalBrightnessState.value = sharedPreferences.getFloat(KEY_GLOBAL_BRIGHTNESS, 1.0f).coerceIn(0.5f, 2.0f)
        globalTransparencyState.value = sharedPreferences.getFloat(KEY_GLOBAL_TRANSPARENCY, 0.0f).coerceIn(0.0f, 1.0f)
        globalBlurState.value = sharedPreferences.getFloat(KEY_GLOBAL_BLUR, 25.0f).coerceIn(0.0f, 80.0f)
        globalCornersState.value = sharedPreferences.getFloat(KEY_GLOBAL_CORNERS, 1.0f).coerceIn(0.25f, 4.0f)

        pingProtocolState.value = sharedPreferences.getInt(KEY_PING_PROTOCOL, 0)
        pingUrlState.value = sharedPreferences.getString(KEY_PING_URL, "https://www.gstatic.com/generate_204") ?: "https://www.gstatic.com/generate_204"
        pingTimeoutState.value = sharedPreferences.getInt(KEY_PING_TIMEOUT, 3)
        pingDisplayModeState.value = sharedPreferences.getInt(KEY_PING_DISPLAY_MODE, 0)
        pingThroughProxyState.value = sharedPreferences.getBoolean(KEY_PING_THROUGH_PROXY, false)
        subscriptionUserAgentModeState.value = readSubscriptionUserAgentMode()
        customSubscriptionUserAgentState.value = readCustomSubscriptionUserAgent()
        autoBypassByNetworkState.value = sharedPreferences.getBoolean(KEY_AUTO_BYPASS_BY_NETWORK, true)
        allowServerSwitchWhileConnectedState.value = sharedPreferences.getBoolean(KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED, false)
        connectButtonStyleState.value = sharedPreferences.getInt(KEY_CONNECT_BUTTON_STYLE, 0)
        connectButtonSizeScaleState.value = sharedPreferences.getFloat(KEY_CONNECT_BUTTON_SIZE_SCALE, 1f)
        showConnectionWidgetsOnlyWhenConnectedState.value = sharedPreferences.getBoolean(KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED, false)
        showProtectedStatusCardState.value = sharedPreferences.getBoolean(KEY_SHOW_PROTECTED_STATUS_CARD, true)
        showConnectionTimeCardState.value = sharedPreferences.getBoolean(KEY_SHOW_CONNECTION_TIME_CARD, true)
        autoRotationEnabledState.value = sharedPreferences.getBoolean(KEY_AUTO_ROTATION_ENABLED, false)

        // Обязательные/дефолтные сетевые параметры NebulaGuard
        if (!sharedPreferences.getBoolean(KEY_AUTO_BYPASS_BY_NETWORK, true)) {
            sharedPreferences.edit().putBoolean(KEY_AUTO_BYPASS_BY_NETWORK, true).apply()
            autoBypassByNetworkState.value = true
        }
        if (!sharedPreferences.contains(KEY_SOCKS5_AUTH_ENABLED)) {
            sharedPreferences.edit().putBoolean(KEY_SOCKS5_AUTH_ENABLED, true).apply()
        }
        ensureSocksCredentials()
    }

    var customDeviceName: String?
        get() = sharedPreferences.getString(KEY_CUSTOM_DEVICE_NAME, null)
        set(value) = sharedPreferences.edit().putString(KEY_CUSTOM_DEVICE_NAME, value).apply()

    fun getCustomDeviceNames(): Map<String, String> {
        val json = sharedPreferences.getString(KEY_CUSTOM_DEVICE_NAMES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveCustomDeviceName(deviceId: String, name: String) {
        val currentNames = getCustomDeviceNames().toMutableMap()
        currentNames[deviceId] = name
        sharedPreferences.edit().putString(KEY_CUSTOM_DEVICE_NAMES, gson.toJson(currentNames)).apply()
    }

    fun getDeviceOrder(): List<String> {
        val json = sharedPreferences.getString(KEY_DEVICE_ORDER, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDeviceOrder(order: List<String>) {
        val json = gson.toJson(order)
        sharedPreferences.edit().putString(KEY_DEVICE_ORDER, json).apply()
    }

    var splashScreenEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_SPLASH_SCREEN_ENABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SPLASH_SCREEN_ENABLED, value).apply()
            splashScreenEnabledState.value = value
        }

    var glowEffectsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_GLOW_EFFECTS_ENABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_GLOW_EFFECTS_ENABLED, value).apply()
            glowEffectsEnabledState.value = value
        }

    var useDynamicColor: Boolean
        get() = sharedPreferences.getBoolean(KEY_USE_DYNAMIC_COLOR, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_USE_DYNAMIC_COLOR, value).apply()
            useDynamicColorState.value = value
        }

    var selectedAppIcon: Int
        get() = sharedPreferences.getInt(KEY_SELECTED_APP_ICON, 0)
        set(value) {
            sharedPreferences.edit().putInt(KEY_SELECTED_APP_ICON, value).apply()
            selectedAppIconState.value = value
        }

    var customAppIconBase64: String?
        get() = sharedPreferences.getString(KEY_CUSTOM_APP_ICON_BASE64, null)
        set(value) {
            sharedPreferences.edit().putString(KEY_CUSTOM_APP_ICON_BASE64, value).apply()
            customAppIconBase64State.value = value
        }

    // 0 = Morphism, 1 = Material 3, 2 = Nothing Dots
    var backgroundStyle: Int
        get() = sharedPreferences.getInt(KEY_BACKGROUND_STYLE, sharedPreferences.getInt(KEY_VISUAL_STYLE, 0))
        set(value) {
            val safe = value.coerceIn(0, 14)
            sharedPreferences.edit().putInt(KEY_BACKGROUND_STYLE, safe).apply()
            backgroundStyleState.value = safe
        }

    // 0 = Morphism, 1 = Material 3, 2 = Nothing Dots
    var elementStyle: Int
        get() = sharedPreferences.getInt(KEY_ELEMENT_STYLE, sharedPreferences.getInt(KEY_VISUAL_STYLE, 0))
        set(value) {
            sharedPreferences.edit().putInt(KEY_ELEMENT_STYLE, value.coerceIn(0, 4)).apply()
            elementStyleState.value = value.coerceIn(0, 4)
        }

    var backgroundAnimationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_BACKGROUND_ANIMATION_ENABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_BACKGROUND_ANIMATION_ENABLED, value).apply()
            backgroundAnimationEnabledState.value = value
        }

    var highContrastUi: Boolean
        get() = sharedPreferences.getBoolean(KEY_HIGH_CONTRAST_UI, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_HIGH_CONTRAST_UI, value).apply()
            highContrastUiState.value = value
        }

    var reducedTransparency: Boolean
        get() = sharedPreferences.getBoolean(KEY_REDUCED_TRANSPARENCY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_REDUCED_TRANSPARENCY, value).apply()
            reducedTransparencyState.value = value
        }

    var pureBlackMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_PURE_BLACK_MODE, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_PURE_BLACK_MODE, value).apply()
            pureBlackModeState.value = value
        }

    fun saveProfiles(profiles: List<SubscriptionProfile>) {
        synchronized(profilesIoLock) {
            try {
                saveServerPingCache(profiles)
                val json = gson.toJson(profiles)
                Log.d("PreferencesManager", "=== SAVE PROFILES (FILE) ===")
                Log.d("PreferencesManager", "Profiles count: ${profiles.size}, JSON length: ${json.length}")

                // Санитарная проверка перед сохранением: не теряем ли мы rawConfig?
                profiles.forEach { p ->
                    if (p.rawConfig.isNullOrBlank()) {
                        Log.w("PreferencesManager", "Warning: Saving profile [${p.name}] with NULL/EMPTY rawConfig!")
                    }
                }

                // Атомарная запись через уникальный временный файл.
                // Уникальное имя + lock защищают от гонки при параллельных saveProfiles.
                val tempFile = File(profilesFile.parent, "${profilesFile.name}.${System.nanoTime()}.tmp")
                tempFile.writeText(json)

                // Локальный backup рабочего файла перед заменой.
                if (profilesFile.exists()) {
                    runCatching { profilesFile.copyTo(profilesBackupFile, overwrite = true) }
                        .onFailure { Log.w("PreferencesManager", "Failed to update profiles backup before replace: ${it.message}") }
                }

                // На некоторых Android API renameTo() возвращает false, если целевой файл существует.
                // Принудительно удаляем старый файл перед заменой.
                if (profilesFile.exists()) {
                    Log.d("PreferencesManager", "Removing old profiles file before rename")
                    profilesFile.delete()
                }

                if (tempFile.renameTo(profilesFile)) {
                    Log.d("PreferencesManager", "Profiles saved successfully to file (FINAL)")
                    // Backup всегда хранит последнюю рабочую версию.
                    runCatching { profilesFile.copyTo(profilesBackupFile, overwrite = true) }
                        .onFailure { Log.w("PreferencesManager", "Failed to refresh profiles backup after save: ${it.message}") }
                    // Удаляем из старого хранилища после успешного сохранения в файл
                    sharedPreferences.edit().remove(KEY_PROFILES).apply()
                } else {
                    Log.e("PreferencesManager", "CRITICAL ERROR: Failed to rename temp file to ${profilesFile.name}! Using SP fallback.")
                    // Fallback на SharedPreferences если файл не сработал
                    sharedPreferences.edit().putString(KEY_PROFILES, json).commit()
                    tempFile.delete()
                }

                Log.d("PreferencesManager", "=== END SAVE ===")
            } catch (e: Exception) {
                Log.e("PreferencesManager", "Exception in saveProfiles: ${e.message}", e)
            }
        }
    }

    fun loadProfiles(): List<SubscriptionProfile> {
        synchronized(profilesIoLock) {
            try {
                Log.d("PreferencesManager", "=== LOAD PROFILES ===")

                val type = object : TypeToken<List<SubscriptionProfile>>() {}.type
                fun parseProfiles(sourceName: String, json: String?): List<SubscriptionProfile>? {
                    if (json.isNullOrBlank()) return null
                    return try {
                        val parsed: List<SubscriptionProfile> = gson.fromJson(json, type)
                        Log.d("PreferencesManager", "Loaded ${parsed.size} profiles from $sourceName")
                        parsed
                    } catch (e: Exception) {
                        Log.e("PreferencesManager", "Failed to parse profiles from $sourceName: ${e.message}")
                        null
                    }
                }

                val fileJson = if (profilesFile.exists()) {
                    runCatching { profilesFile.readText() }
                        .onFailure { Log.e("PreferencesManager", "Error reading profiles file: ${it.message}") }
                        .getOrNull()
                } else null
                val fileProfiles = parseProfiles("file", fileJson)
                if (fileProfiles != null) {
                    fileProfiles.forEachIndexed { i, p ->
                        if (p.url.isBlank()) Log.w("PreferencesManager", "Profile #$i has blank URL")
                        if (p.rawConfig.isNullOrBlank()) Log.w("PreferencesManager", "Profile #$i [${p.name}] has NONE/EMPTY rawConfig!")
                    }
                    return applySavedServerPings(fileProfiles)
                }

                // Fallback к локальному backup-файлу
                val backupJson = if (profilesBackupFile.exists()) {
                    runCatching { profilesBackupFile.readText() }
                        .onFailure { Log.e("PreferencesManager", "Error reading profiles backup file: ${it.message}") }
                        .getOrNull()
                } else null
                val backupProfiles = parseProfiles("backup file", backupJson)
                if (backupProfiles != null) {
                    runCatching { saveProfiles(backupProfiles) }
                        .onFailure { Log.w("PreferencesManager", "Failed to restore main profiles file from backup: ${it.message}") }
                    return applySavedServerPings(backupProfiles)
                }

                // Fallback к SharedPreferences, если файл отсутствует/битый
                val spJson = sharedPreferences.getString(KEY_PROFILES, null)
                val spProfiles = parseProfiles("SharedPreferences", spJson)
                if (spProfiles != null) {
                    // Восстанавливаем файл из рабочего fallback источника
                    runCatching { saveProfiles(spProfiles) }
                        .onFailure { Log.w("PreferencesManager", "Failed to restore profiles file from SharedPreferences: ${it.message}") }
                    return applySavedServerPings(spProfiles)
                }

                Log.d("PreferencesManager", "No valid profiles data found")
                return emptyList()
            } catch (e: Exception) {
                Log.e("PreferencesManager", "Exception in loadProfiles: ${e.message}", e)
                return emptyList()
            }
        }
    }

    fun saveServerPingCache(profiles: List<SubscriptionProfile>) {
        synchronized(profilesIoLock) {
            val cache = loadServerPingCache().toMutableMap()
            val now = System.currentTimeMillis()
            var changed = false

            profiles.forEach { profile ->
                profile.servers.forEach { server ->
                    val ping = server.ping ?: return@forEach
                    if (ping < 0) return@forEach

                    val keyServer = if (server.profileUrl.isNullOrBlank()) {
                        server.copy(profileUrl = profile.url)
                    } else {
                        server
                    }
                    val key = keyServer.pingKey()
                    val value = SavedServerPing(
                        ping = ping,
                        timestamp = server.pingTimestamp ?: now
                    )
                    if (cache[key] != value) {
                        cache[key] = value
                        changed = true
                    }
                }
            }

            if (changed) {
                sharedPreferences.edit()
                    .putString(KEY_SERVER_PING_CACHE, gson.toJson(cache))
                    .apply()
                Log.d("PreferencesManager", "Saved server ping cache: ${cache.size} entries")
            }
        }
    }

    private fun applySavedServerPings(profiles: List<SubscriptionProfile>): List<SubscriptionProfile> {
        val cache = loadServerPingCache()
        if (cache.isEmpty()) return profiles

        var changed = false
        val restored = profiles.map { profile ->
            val restoredServers = profile.servers.map { server ->
                if ((server.ping ?: -1) >= 0) return@map server

                val keyServer = if (server.profileUrl.isNullOrBlank()) {
                    server.copy(profileUrl = profile.url)
                } else {
                    server
                }
                val saved = cache[keyServer.pingKey()] ?: cache[server.pingKey()]
                if (saved != null && saved.ping >= 0) {
                    changed = true
                    server.copy(ping = saved.ping, pingTimestamp = saved.timestamp)
                } else {
                    server
                }
            }
            if (restoredServers != profile.servers) profile.copy(servers = restoredServers) else profile
        }

        if (changed) {
            Log.d("PreferencesManager", "Restored saved pings from cache")
        }
        return restored
    }

    private fun loadServerPingCache(): Map<String, SavedServerPing> {
        val json = sharedPreferences.getString(KEY_SERVER_PING_CACHE, null)
            ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, SavedServerPing>>() {}.type
            gson.fromJson<Map<String, SavedServerPing>>(json, type).orEmpty()
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Failed to parse server ping cache: ${e.message}")
            emptyMap()
        }
    }

    // Настройки приложения
    var killSwitch: Boolean
        get() = sharedPreferences.getBoolean(KEY_KILL_SWITCH, false)
        set(value) { sharedPreferences.edit().putBoolean(KEY_KILL_SWITCH, value).commit() }

    var autoConnect: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_CONNECT, false)
        set(value) { sharedPreferences.edit().putBoolean(KEY_AUTO_CONNECT, value).commit() }

    var autoReconnect: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()

    var vpnConnectionDesired: Boolean
        get() = sharedPreferences.getBoolean(KEY_VPN_CONNECTION_DESIRED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_VPN_CONNECTION_DESIRED, value).commit()
        }

    var vpnPausedByScreen: Boolean
        get() = sharedPreferences.getBoolean(KEY_VPN_PAUSED_BY_SCREEN, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_VPN_PAUSED_BY_SCREEN, value).commit()
        }

    var showSpeed: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_SPEED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_SPEED, value).apply()
            showSpeedState.value = value
        }

    // TLS Fragment (opt-in DPI bypass that splits the TLS ClientHello so SNI is not contiguous).
    var tlsFragment: Boolean
        get() = sharedPreferences.getBoolean(KEY_TLS_FRAGMENT, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_TLS_FRAGMENT, value).apply()
            tlsFragmentState.value = value
        }

    // Show the provider/brand logo delivered via the nimbo-logo subscription header.
    var showSubscriptionLogo: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_SUBSCRIPTION_LOGO, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_SUBSCRIPTION_LOGO, value).apply()
            showSubscriptionLogoState.value = value
        }

    // Apply the accent/theme delivered via the nimbo-theme subscription header.
    var useSubscriptionTheme: Boolean
        get() = sharedPreferences.getBoolean(KEY_USE_SUBSCRIPTION_THEME, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_USE_SUBSCRIPTION_THEME, value).apply()
            useSubscriptionThemeState.value = value
        }

    // Last theme spec ("filter,accentHex,...") seen from the nimbo-theme header.
    var subscriptionThemeSpec: String?
        get() = sharedPreferences.getString(KEY_SUBSCRIPTION_THEME_SPEC, null)
        set(value) { sharedPreferences.edit().putString(KEY_SUBSCRIPTION_THEME_SPEC, value).apply() }

    var showNotificationSpeed: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_NOTIFICATION_SPEED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SHOW_NOTIFICATION_SPEED, value).apply()

    var showNotificationConnectionTime: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_NOTIFICATION_CONNECTION_TIME, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SHOW_NOTIFICATION_CONNECTION_TIME, value).apply()

    var autoBypassByNetwork: Boolean
        get() = true
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_AUTO_BYPASS_BY_NETWORK, true).apply()
            autoBypassByNetworkState.value = true
        }

    var bsBypassMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_BS_BYPASS_MODE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_BS_BYPASS_MODE, value).apply()

    var livePingEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_LIVE_PING_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_LIVE_PING_ENABLED, value).apply()

    var disconnectOnLock: Boolean
        get() = sharedPreferences.getBoolean(KEY_DISCONNECT_ON_LOCK, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_DISCONNECT_ON_LOCK, value).apply()

    var connectOnUnlock: Boolean
        get() = sharedPreferences.getBoolean(KEY_CONNECT_ON_UNLOCK, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_CONNECT_ON_UNLOCK, value).apply()

    var memoryMonitoring: Boolean
        get() = sharedPreferences.getBoolean(KEY_MEMORY_MONITORING, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_MEMORY_MONITORING, value).apply()
            memoryMonitoringState.value = value
        }

    var memoryLimitMb: Int
        get() = sharedPreferences.getInt(KEY_MEMORY_LIMIT_MB, 160).coerceIn(40, 300)
        set(value) {
            val safe = value.coerceIn(40, 300)
            sharedPreferences.edit().putInt(KEY_MEMORY_LIMIT_MB, safe).apply()
            memoryLimitMbState.value = safe
        }

    var memoryLimitDisabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_MEMORY_LIMIT_DISABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_MEMORY_LIMIT_DISABLED, value).apply()
            memoryLimitDisabledState.value = value
        }

    var pingOnStartup: Boolean
        get() = sharedPreferences.getBoolean(KEY_PING_ON_STARTUP, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PING_ON_STARTUP, value).apply()

    var updateSubOnStartup: Boolean
        get() = sharedPreferences.getBoolean(KEY_UPDATE_SUB_ON_STARTUP, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_UPDATE_SUB_ON_STARTUP, value).apply()

    var pingOnUpdate: Boolean
        get() = sharedPreferences.getBoolean(KEY_PING_ON_UPDATE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PING_ON_UPDATE, value).apply()

    var serverSortOrder: String
        get() = sharedPreferences.getString(KEY_SERVER_SORT_ORDER, "DEFAULT") ?: "DEFAULT"
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_SORT_ORDER, value).apply()

    var selectedSortProtocols: Set<String>
        get() = sharedPreferences.getStringSet("selected_sort_protocols", emptySet())?.toSet() ?: emptySet()
        set(value) = sharedPreferences.edit().putStringSet("selected_sort_protocols", value).apply()

    fun getPinnedProfileUrls(): Set<String> {
        return sharedPreferences.getStringSet(KEY_PINNED_PROFILE_URLS, emptySet())?.toSet() ?: emptySet()
    }

    fun isProfilePinned(profileUrl: String): Boolean {
        val key = profileUrl.trim().lowercase()
        if (key.isBlank()) return false
        return getPinnedProfileUrls().contains(key)
    }

    fun pinProfile(profileUrl: String) {
        val key = profileUrl.trim().lowercase()
        if (key.isBlank()) return
        val updated = getPinnedProfileUrls().toMutableSet()
        updated.add(key)
        sharedPreferences.edit().putStringSet(KEY_PINNED_PROFILE_URLS, updated).apply()
    }

    fun unpinProfile(profileUrl: String) {
        val key = profileUrl.trim().lowercase()
        if (key.isBlank()) return
        val updated = getPinnedProfileUrls().toMutableSet()
        updated.remove(key)
        sharedPreferences.edit().putStringSet(KEY_PINNED_PROFILE_URLS, updated).apply()
    }

    fun getPinnedServerKeys(): Set<String> {
        return sharedPreferences.getStringSet(KEY_PINNED_SERVER_KEYS, emptySet())?.toSet() ?: emptySet()
    }

    fun isServerPinned(serverKey: String): Boolean {
        val key = serverKey.trim()
        if (key.isBlank()) return false
        return getPinnedServerKeys().contains(key)
    }

    fun pinServer(serverKey: String) {
        val key = serverKey.trim()
        if (key.isBlank()) return
        val updated = getPinnedServerKeys().toMutableSet()
        updated.add(key)
        sharedPreferences.edit().putStringSet(KEY_PINNED_SERVER_KEYS, updated).apply()
    }

    fun unpinServer(serverKey: String) {
        val key = serverKey.trim()
        if (key.isBlank()) return
        val updated = getPinnedServerKeys().toMutableSet()
        updated.remove(key)
        sharedPreferences.edit().putStringSet(KEY_PINNED_SERVER_KEYS, updated).apply()
    }

    fun getServerNameOverrides(): Map<String, String> {
        val json = sharedPreferences.getString(KEY_SERVER_NAME_OVERRIDES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type)
                ?.filterKeys { it.isNotBlank() }
                ?.mapValues { it.value.trim() }
                ?.filterValues { it.isNotBlank() }
                ?: emptyMap()
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Failed to parse server name overrides: ${e.message}")
            emptyMap()
        }
    }

    fun getServerDisplayName(serverKey: String): String? {
        val key = serverKey.trim()
        if (key.isBlank()) return null
        return getServerNameOverrides()[key]
    }

    fun setServerDisplayName(serverKey: String, name: String?) {
        val key = serverKey.trim()
        if (key.isBlank()) return
        val updated = getServerNameOverrides().toMutableMap()
        val normalized = name?.trim().orEmpty()
        if (normalized.isBlank()) {
            updated.remove(key)
        } else {
            updated[key] = normalized
        }
        sharedPreferences.edit().putString(KEY_SERVER_NAME_OVERRIDES, gson.toJson(updated)).apply()
    }

    fun getHiddenServerKeys(): Set<String> {
        return sharedPreferences.getStringSet(KEY_HIDDEN_SERVER_KEYS, emptySet())?.toSet() ?: emptySet()
    }

    fun isServerHidden(serverKey: String): Boolean {
        val key = serverKey.trim()
        if (key.isBlank()) return false
        return getHiddenServerKeys().contains(key)
    }

    fun hideServer(serverKey: String) {
        val key = serverKey.trim()
        if (key.isBlank()) return
        val updated = getHiddenServerKeys().toMutableSet()
        updated.add(key)
        sharedPreferences.edit().putStringSet(KEY_HIDDEN_SERVER_KEYS, updated).apply()
    }

    fun unhideServer(serverKey: String) {
        val key = serverKey.trim()
        if (key.isBlank()) return
        val updated = getHiddenServerKeys().toMutableSet()
        updated.remove(key)
        sharedPreferences.edit().putStringSet(KEY_HIDDEN_SERVER_KEYS, updated).apply()
    }

    var showUpdateDialog: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_UPDATE_DIALOG, true)
        set(value) { sharedPreferences.edit().putBoolean(KEY_SHOW_UPDATE_DIALOG, value).commit() }

    var lastUpdateNotifiedVersion: String?
        get() = sharedPreferences.getString(KEY_LAST_UPDATE_NOTIFIED_VERSION, null)
        set(value) = sharedPreferences.edit().putString(KEY_LAST_UPDATE_NOTIFIED_VERSION, value).apply()

    var updateNotificationCount: Int
        get() = sharedPreferences.getInt(KEY_UPDATE_NOTIFICATION_COUNT, 0)
        set(value) = sharedPreferences.edit().putInt(KEY_UPDATE_NOTIFICATION_COUNT, value).apply()

    var lastUpdateNotificationTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_UPDATE_NOTIFICATION_TIME, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_UPDATE_NOTIFICATION_TIME, value).apply()

    /**
     * Версия, которую пользователь нажал «Позже» в диалоге обновлений.
     * Пока остаётся актуальной, in-app диалог при запуске не показывается
     * (но карточка «Доступно обновление» на отдельной странице всё равно есть).
     */
    var updateDialogSkippedVersion: String?
        get() = sharedPreferences.getString(KEY_UPDATE_DIALOG_SKIPPED_VERSION, null)
        set(value) = sharedPreferences.edit().putString(KEY_UPDATE_DIALOG_SKIPPED_VERSION, value).apply()

    var lastUpdateCheckTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_UPDATE_CHECK_TIME, value).apply()

    fun isOnboardingComplete(): Boolean {
        val result = sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        Log.d("PreferencesManager", "isOnboardingComplete: $result")
        return result
    }

    fun setOnboardingComplete(complete: Boolean) {
        Log.d("PreferencesManager", "setOnboardingComplete: $complete")
        val success = sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).commit()
        Log.d("PreferencesManager", "Onboarding complete saved: $success")
        Log.d("PreferencesManager", "Onboarding complete value: ${sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)}")
    }

    // Режим туннеля: 0 = Тун+Прокси, 1 = Только туннель, 2 = Только прокси (по умолчанию)
    var tunnelMode: Int
        get() = sharedPreferences.getInt(KEY_TUNNEL_MODE, 2)
        set(value) = sharedPreferences.edit().putInt(KEY_TUNNEL_MODE, value).apply()

    // Настройки подписок
    var subscriptionAutoUpdate: Boolean
        get() = sharedPreferences.getBoolean(KEY_SUBSCRIPTION_AUTO_UPDATE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SUBSCRIPTION_AUTO_UPDATE, value).apply()

    var subscriptionUpdateInterval: Int
        get() = sharedPreferences.getInt(KEY_SUBSCRIPTION_UPDATE_INTERVAL, 43200) // по умолчанию 12 часов
        set(value) = sharedPreferences.edit().putInt(KEY_SUBSCRIPTION_UPDATE_INTERVAL, value).apply()

    // Уведомлять об истечении подписки
    var notifyOnExpiry: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTIFY_ON_EXPIRY, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTIFY_ON_EXPIRY, value).apply()

    // За сколько дней до конца предупреждать (1..30)
    var expiryNotifyDays: Int
        get() = sharedPreferences.getInt(KEY_EXPIRY_NOTIFY_DAYS, 3).coerceIn(1, 30)
        set(value) = sharedPreferences.edit().putInt(KEY_EXPIRY_NOTIFY_DAYS, value.coerceIn(1, 30)).apply()

    // Уведомлять об успешном обновлении подписки
    var notifyOnSubscriptionUpdate: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTIFY_ON_SUBSCRIPTION_UPDATE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTIFY_ON_SUBSCRIPTION_UPDATE, value).apply()

    // === Постоянный учёт трафика (за всё время + за текущий месяц) ===
    private val trafficLock = Any()

    private fun currentMonthKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
    }

    val totalTrafficUp: Long
        get() = sharedPreferences.getLong(KEY_TRAFFIC_TOTAL_UP, 0L)

    val totalTrafficDown: Long
        get() = sharedPreferences.getLong(KEY_TRAFFIC_TOTAL_DOWN, 0L)

    // Метка текущего месяца для отображения (MM-YYYY)
    val trafficMonthLabel: String
        get() {
            val cal = java.util.Calendar.getInstance()
            return "%02d-%04d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))
        }

    val monthlyTrafficUp: Long
        get() = if (sharedPreferences.getString(KEY_TRAFFIC_MONTH_KEY, null) == currentMonthKey())
            sharedPreferences.getLong(KEY_TRAFFIC_MONTH_UP, 0L) else 0L

    val monthlyTrafficDown: Long
        get() = if (sharedPreferences.getString(KEY_TRAFFIC_MONTH_KEY, null) == currentMonthKey())
            sharedPreferences.getLong(KEY_TRAFFIC_MONTH_DOWN, 0L) else 0L

    fun addTraffic(uploadedBytes: Long, downloadedBytes: Long) {
        val up = uploadedBytes.coerceAtLeast(0L)
        val down = downloadedBytes.coerceAtLeast(0L)
        if (up == 0L && down == 0L) return
        synchronized(trafficLock) {
            val month = currentMonthKey()
            val rolledOver = sharedPreferences.getString(KEY_TRAFFIC_MONTH_KEY, null) != month
            val monthUp = if (rolledOver) 0L else sharedPreferences.getLong(KEY_TRAFFIC_MONTH_UP, 0L)
            val monthDown = if (rolledOver) 0L else sharedPreferences.getLong(KEY_TRAFFIC_MONTH_DOWN, 0L)
            sharedPreferences.edit()
                .putLong(KEY_TRAFFIC_TOTAL_UP, sharedPreferences.getLong(KEY_TRAFFIC_TOTAL_UP, 0L) + up)
                .putLong(KEY_TRAFFIC_TOTAL_DOWN, sharedPreferences.getLong(KEY_TRAFFIC_TOTAL_DOWN, 0L) + down)
                .putString(KEY_TRAFFIC_MONTH_KEY, month)
                .putLong(KEY_TRAFFIC_MONTH_UP, monthUp + up)
                .putLong(KEY_TRAFFIC_MONTH_DOWN, monthDown + down)
                .apply()
        }
    }

    fun resetTrafficStats() {
        synchronized(trafficLock) {
            sharedPreferences.edit()
                .remove(KEY_TRAFFIC_TOTAL_UP)
                .remove(KEY_TRAFFIC_TOTAL_DOWN)
                .remove(KEY_TRAFFIC_MONTH_KEY)
                .remove(KEY_TRAFFIC_MONTH_UP)
                .remove(KEY_TRAFFIC_MONTH_DOWN)
                .apply()
        }
    }

    // Отправка HWID
    var sendHwid: Boolean
        get() = sharedPreferences.getBoolean(KEY_SEND_HWID, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SEND_HWID, value).apply()

    // Тип пинга: 0 = TCP, 1 = HTTP/GET, 2 = HTTP/HEAD, 3 = HTTPS Strict, 4 = ICMP
    var pingProtocol: Int
        get() = sharedPreferences.getInt(KEY_PING_PROTOCOL, 0)
        set(value) {
            sharedPreferences.edit().putInt(KEY_PING_PROTOCOL, value).apply()
            pingProtocolState.value = value
        }

    var pingUrl: String
        get() = sharedPreferences.getString(KEY_PING_URL, "https://www.gstatic.com/generate_204") ?: "https://www.gstatic.com/generate_204"
        set(value) {
            sharedPreferences.edit().putString(KEY_PING_URL, value).apply()
            pingUrlState.value = value
        }

    var pingTimeout: Int
        get() = sharedPreferences.getInt(KEY_PING_TIMEOUT, 3)
        set(value) {
            sharedPreferences.edit().putInt(KEY_PING_TIMEOUT, value).apply()
            pingTimeoutState.value = value
        }

    var pingDisplayMode: Int
        get() = sharedPreferences.getInt(KEY_PING_DISPLAY_MODE, 0)
        set(value) {
            sharedPreferences.edit().putInt(KEY_PING_DISPLAY_MODE, value).apply()
            pingDisplayModeState.value = value
        }

    var pingThroughProxy: Boolean
        get() = sharedPreferences.getBoolean(KEY_PING_THROUGH_PROXY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_PING_THROUGH_PROXY, value).apply()
            pingThroughProxyState.value = value
        }

    var allowServerSwitchWhileConnected: Boolean
        get() = sharedPreferences.getBoolean(KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_ALLOW_SERVER_SWITCH_WHILE_CONNECTED, value).apply()
            allowServerSwitchWhileConnectedState.value = value
        }

    // 0..8 = стили кнопки подключения
    var connectButtonStyle: Int
        get() = sharedPreferences.getInt(KEY_CONNECT_BUTTON_STYLE, 0)
        set(value) {
            val safe = value.coerceIn(0, 8)
            sharedPreferences.edit().putInt(KEY_CONNECT_BUTTON_STYLE, safe).apply()
            connectButtonStyleState.value = safe
        }

    // 0.50..2.00 - масштаб кнопки подключения
    var connectButtonSizeScale: Float
        get() = sharedPreferences.getFloat(KEY_CONNECT_BUTTON_SIZE_SCALE, 1f)
        set(value) {
            val safe = value.coerceIn(0.50f, 2.00f)
            sharedPreferences.edit().putFloat(KEY_CONNECT_BUTTON_SIZE_SCALE, safe).apply()
            connectButtonSizeScaleState.value = safe
        }

    var showConnectionWidgetsOnlyWhenConnected: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_CONNECTION_WIDGETS_ONLY_WHEN_CONNECTED, value).apply()
            showConnectionWidgetsOnlyWhenConnectedState.value = value
        }

    var showProtectedStatusCard: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_PROTECTED_STATUS_CARD, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_PROTECTED_STATUS_CARD, value).apply()
            showProtectedStatusCardState.value = value
        }

    var showConnectionTimeCard: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_CONNECTION_TIME_CARD, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_CONNECTION_TIME_CARD, value).apply()
            showConnectionTimeCardState.value = value
        }

    var autoRotationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_ROTATION_ENABLED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_AUTO_ROTATION_ENABLED, value).apply()
            autoRotationEnabledState.value = value
        }

    var keepDeviceActive: Boolean
        get() = sharedPreferences.getBoolean(KEY_KEEP_DEVICE_ACTIVE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_KEEP_DEVICE_ACTIVE, value).apply()

    var allowLanConnections: Boolean
        get() = sharedPreferences.getBoolean(KEY_ALLOW_LAN_CONNECTIONS, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ALLOW_LAN_CONNECTIONS, value).apply()

    var allowHotspotAccess: Boolean
        get() = sharedPreferences.getBoolean(KEY_ALLOW_HOTSPOT_ACCESS, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ALLOW_HOTSPOT_ACCESS, value).apply()

    var lanThroughProxy: Boolean
        get() = sharedPreferences.getBoolean(KEY_LAN_THROUGH_PROXY, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_LAN_THROUGH_PROXY, value).apply()

    var socks5AuthEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_SOCKS5_AUTH_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SOCKS5_AUTH_ENABLED, value).apply()

    var socks5AuthLogin: String
        get() {
            val current = sharedPreferences.getString(KEY_SOCKS5_AUTH_LOGIN, "").orEmpty()
            if (current.isNotBlank() && current.startsWith("nebula", ignoreCase = true)) return current
            val generated = "nebula_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            sharedPreferences.edit().putString(KEY_SOCKS5_AUTH_LOGIN, generated).apply()
            return generated
        }
        set(value) = sharedPreferences.edit().putString(KEY_SOCKS5_AUTH_LOGIN, value).apply()

    var socks5AuthPassword: String
        get() {
            val current = sharedPreferences.getString(KEY_SOCKS5_AUTH_PASSWORD, "").orEmpty()
            if (current.isNotBlank()) return current
            val generated = "nebula_pw_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            sharedPreferences.edit().putString(KEY_SOCKS5_AUTH_PASSWORD, generated).apply()
            return generated
        }
        set(value) = sharedPreferences.edit().putString(KEY_SOCKS5_AUTH_PASSWORD, value).apply()

    var socks5AuthPort: Int
        get() = sharedPreferences.getInt(KEY_SOCKS5_AUTH_PORT, 28215)
        set(value) = sharedPreferences.edit().putInt(KEY_SOCKS5_AUTH_PORT, value.coerceIn(1024, 65535)).apply()

    var blockUdp: Boolean
        get() = sharedPreferences.getBoolean(KEY_BLOCK_UDP, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_BLOCK_UDP, value).apply()

    var muxEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUX_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUX_ENABLED, value).apply()

    var packetFragmentationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_PACKET_FRAGMENTATION_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PACKET_FRAGMENTATION_ENABLED, value).apply()

    var trafficSniffingEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_TRAFFIC_SNIFFING_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_TRAFFIC_SNIFFING_ENABLED, value).apply()

    var idleTimeoutSeconds: Int
        get() = sharedPreferences.getInt(KEY_IDLE_TIMEOUT_SECONDS, 300).coerceIn(30, 3600)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_IDLE_TIMEOUT_SECONDS, value.coerceIn(30, 3600))
            .apply()

    var tcpConnectionLimit: Int
        get() = sharedPreferences.getInt(KEY_TCP_CONNECTION_LIMIT, 256).coerceIn(16, 4096)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TCP_CONNECTION_LIMIT, value.coerceIn(16, 4096))
            .apply()

    var udpConnectionLimit: Int
        get() = sharedPreferences.getInt(KEY_UDP_CONNECTION_LIMIT, 128).coerceIn(16, 4096)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_UDP_CONNECTION_LIMIT, value.coerceIn(16, 4096))
            .apply()

    var diagnosticLogRetentionHours: Int
        get() {
            val stored = sharedPreferences.getInt(KEY_DIAGNOSTIC_LOG_RETENTION_HOURS, 24)
            return if (stored <= 0) 0 else stored.coerceIn(1, 24 * 365)
        }
        set(value) = sharedPreferences.edit()
            .putInt(
                KEY_DIAGNOSTIC_LOG_RETENTION_HOURS,
                if (value <= 0) 0 else value.coerceIn(1, 24 * 365)
            )
            .apply()

    var vpnIpType: String
        get() = sharedPreferences.getString(KEY_VPN_IP_TYPE, "ipv4") ?: "ipv4"
        set(value) = sharedPreferences.edit().putString(KEY_VPN_IP_TYPE, value).apply()

    var vpnDnsMode: String
        get() = sharedPreferences.getString(KEY_VPN_DNS_MODE, "remote") ?: "remote"
        set(value) = sharedPreferences.edit().putString(KEY_VPN_DNS_MODE, value).apply()

    // Цветовая тема: 0-8 = тёмные, 9-17 = светлые
    var colorTheme: Int
        get() = sharedPreferences.getInt(KEY_COLOR_THEME, DEFAULT_COLOR_THEME_INDEX)
        set(value) {
            sharedPreferences.edit().putInt(KEY_COLOR_THEME, value).commit()
            colorThemeState.value = value
        }

    // 0 = System, 1 = Light, 2 = Dark.
    var themeMode: Int
        get() = sharedPreferences.getInt(KEY_THEME_MODE, sharedPreferences.getInt("theme_mode_override", 0)).coerceIn(0, 2)
        set(value) {
            val safe = value.coerceIn(0, 2)
            sharedPreferences.edit()
                .putInt(KEY_THEME_MODE, safe)
                .putInt("theme_mode_override", safe)
                .apply()
            themeModeState.value = safe
        }

    var themeModeOverride: Int
        get() = themeMode
        set(value) {
            themeMode = value
        }

    var textScale: Float
        get() = sharedPreferences.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.85f, 1.25f)
        set(value) {
            val safe = value.coerceIn(0.85f, 1.25f)
            sharedPreferences.edit().putFloat(KEY_TEXT_SCALE, safe).apply()
            textScaleState.value = safe
        }

    // Прокси по приложениям:
    // 0 = Все через VPN
    // 1 = Выбранные приложения в обход VPN (напрямую)
    // 2 = Только выбранные приложения через VPN (остальные напрямую)
    var proxyByApp: Int
        get() = sharedPreferences.getInt(KEY_PROXY_BY_APP, 0).coerceIn(0, 2)
        set(value) {
            sharedPreferences.edit()
                .putInt(KEY_PROXY_BY_APP, value.coerceIn(0, 2))
                .commit()
        }

    // Списки приложений (хранятся как JSON)
    fun getAppBypassList(): Set<String> {
        val json = sharedPreferences.getString(KEY_APP_BYPASS_LIST, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setAppBypassList(packages: Set<String>) {
        val json = gson.toJson(packages)
        sharedPreferences.edit().putString(KEY_APP_BYPASS_LIST, json).apply()
    }

    fun getAppVpnOnlyList(): Set<String> {
        val json = sharedPreferences.getString(KEY_APP_VPN_ONLY_LIST, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setAppVpnOnlyList(packages: Set<String>) {
        val json = gson.toJson(packages)
        sharedPreferences.edit().putString(KEY_APP_VPN_ONLY_LIST, json).apply()
    }

    // Optional per-entry icon source for manually-added rules.
    // Values: "app:<package>", "file:<absolutePath>" or "fav:<host>".
    fun getCustomRuleIcons(): Map<String, String> {
        val json = sharedPreferences.getString(KEY_CUSTOM_RULE_ICONS, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setCustomRuleIcons(icons: Map<String, String>) {
        val json = gson.toJson(icons)
        sharedPreferences.edit().putString(KEY_CUSTOM_RULE_ICONS, json).apply()
    }

    // App-routing rules delivered by the subscription provider (HTTP headers).
    // Stored separately so the user can opt-in to load them into the active lists.
    fun getSubscriptionAppDirectList(): Set<String> {
        val json = sharedPreferences.getString(KEY_SUB_APP_DIRECT_LIST, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setSubscriptionAppDirectList(entries: Set<String>) {
        val json = gson.toJson(entries)
        sharedPreferences.edit().putString(KEY_SUB_APP_DIRECT_LIST, json).apply()
    }

    fun getSubscriptionAppProxyList(): Set<String> {
        val json = sharedPreferences.getString(KEY_SUB_APP_PROXY_LIST, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setSubscriptionAppProxyList(entries: Set<String>) {
        val json = gson.toJson(entries)
        sharedPreferences.edit().putString(KEY_SUB_APP_PROXY_LIST, json).apply()
    }

    // Profile cards are expanded by default; we only persist the URLs the user
    // explicitly collapsed so the choice survives app restarts.
    fun getCollapsedSubscriptions(): Set<String> {
        val json = sharedPreferences.getString(KEY_COLLAPSED_SUBSCRIPTIONS, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setSubscriptionCollapsed(url: String, collapsed: Boolean) {
        val current = getCollapsedSubscriptions().toMutableSet()
        if (collapsed) current.add(url) else current.remove(url)
        sharedPreferences.edit().putString(KEY_COLLAPSED_SUBSCRIPTIONS, gson.toJson(current)).apply()
    }

    fun getCollapsedThemeSections(): Set<String> {
        val json = sharedPreferences.getString(KEY_COLLAPSED_THEME_SECTIONS, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setThemeSectionCollapsed(sectionId: String, collapsed: Boolean) {
        val normalizedId = sectionId.trim().lowercase()
        if (normalizedId.isBlank()) return
        val current = getCollapsedThemeSections().toMutableSet()
        if (collapsed) current.add(normalizedId) else current.remove(normalizedId)
        sharedPreferences.edit().putString(KEY_COLLAPSED_THEME_SECTIONS, gson.toJson(current)).apply()
    }

    var telegramChannel: String
        get() = sharedPreferences.getString(KEY_TELEGRAM_CHANNEL, "https://t.me/nebulaguard_channel")
            ?: "https://t.me/nebulaguard_channel"
        set(value) = sharedPreferences.edit().putString(KEY_TELEGRAM_CHANNEL, value).apply()

    var vpnUrl: String
        get() = sharedPreferences.getString(KEY_VPN_URL, "https://t.me/nebulaguardd_bot")
            ?: "https://t.me/nebulaguardd_bot"
        set(value) = sharedPreferences.edit().putString(KEY_VPN_URL, value).apply()

    var websiteUrl: String
        get() = sharedPreferences.getString(KEY_WEBSITE_URL, "https://nimboapp.pw")
            ?: "https://nimboapp.pw"
        set(value) = sharedPreferences.edit().putString(KEY_WEBSITE_URL, value).apply()

    var supportUrl: String
        get() = sharedPreferences.getString(KEY_SUPPORT_URL, "https://t.me/nebulaguardd_bot")
            ?: "https://t.me/nebulaguardd_bot"
        set(value) = sharedPreferences.edit().putString(KEY_SUPPORT_URL, value).apply()

    fun getSubscriptionUpdateInterval(url: String): Int? {
        val key = "subscription_update_interval_${url.hashCode()}"
        val value = sharedPreferences.getInt(key, 0)
        return value.takeIf { it > 0 }
    }

    fun setSubscriptionUpdateInterval(url: String, intervalHours: Int?) {
        val key = "subscription_update_interval_${url.hashCode()}"
        if (intervalHours != null && intervalHours > 0) {
            sharedPreferences.edit().putInt(key, intervalHours).apply()
        } else {
            sharedPreferences.edit().remove(key).apply()
        }
    }

    fun getLastSubscriptionUpdateTime(url: String): Long {
        val key = "subscription_last_update_${url.hashCode()}"
        return sharedPreferences.getLong(key, 0L)
    }

    fun setLastSubscriptionUpdateTime(url: String, timeMillis: Long) {
        val key = "subscription_last_update_${url.hashCode()}"
        sharedPreferences.edit().putLong(key, timeMillis).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun setBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun setLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun setString(key: String, value: String?) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    private fun ensureSocksCredentials() {
        val login = sharedPreferences.getString(KEY_SOCKS5_AUTH_LOGIN, "").orEmpty()
        if (login.isBlank() || !login.startsWith("nebula", ignoreCase = true)) {
            sharedPreferences.edit()
                .putString(KEY_SOCKS5_AUTH_LOGIN, "nebula_${UUID.randomUUID().toString().replace("-", "").take(8)}")
                .apply()
        }
        val password = sharedPreferences.getString(KEY_SOCKS5_AUTH_PASSWORD, "").orEmpty()
        if (password.isBlank()) {
            sharedPreferences.edit()
                .putString(KEY_SOCKS5_AUTH_PASSWORD, "nebula_pw_${UUID.randomUUID().toString().replace("-", "").take(8)}")
                .apply()
        }
    }

    var homeSubscriptionExpanded: Boolean
        get() = sharedPreferences.getBoolean(KEY_HOME_SUBSCRIPTION_EXPANDED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_HOME_SUBSCRIPTION_EXPANDED, value).apply()

    var serverListSubscriptionExpanded: Boolean
        get() = sharedPreferences.getBoolean(KEY_SERVER_LIST_SUBSCRIPTION_EXPANDED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SERVER_LIST_SUBSCRIPTION_EXPANDED, value).apply()

    fun saveLastSelectedServer(server: com.danila.nimbo.model.Server) {
        try {
            val serverJson = gson.toJson(server)
            sharedPreferences.edit().putString(KEY_LAST_SELECTED_SERVER, serverJson).apply()
            Log.d("PreferencesManager", "Saved last selected server: ${server.name}, ping=${server.ping ?: -1}")
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error saving last selected server JSON: ${e.message}")
        }
    }

    fun loadLastSelectedServer(): com.danila.nimbo.model.Server? {
        val serverData = sharedPreferences.getString(KEY_LAST_SELECTED_SERVER, null) ?: return null
        val fallbackProfileUrl = loadLastSelectedProfileUrl()

        try {
            if (serverData.startsWith("{")) {
                return gson.fromJson(serverData, com.danila.nimbo.model.Server::class.java)
                    ?.let { parsed ->
                        if (parsed.profileUrl.isNullOrBlank() && !fallbackProfileUrl.isNullOrBlank()) {
                            parsed.copy(profileUrl = fallbackProfileUrl)
                        } else {
                            parsed
                        }
                    }
                    ?.let(::withLatestSavedPing)
                    ?.also {
                        Log.d("PreferencesManager", "Loaded last selected server from JSON: ${it.name}")
                    }
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error parsing last selected server JSON: ${e.message}")
        }

        val parts = serverData.split("|", limit = 5)
        if (parts.size != 5) return null

        return try {
            com.danila.nimbo.model.Server(
                name = parts[4],
                host = parts[0],
                port = parts[1].toIntOrNull() ?: 443,
                uuid = parts[2],
                protocol = parts[3],
                profileUrl = fallbackProfileUrl
            ).let(::withLatestSavedPing).also {
                Log.d("PreferencesManager", "Loaded last selected server from legacy format: ${it.name}")
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error loading last selected server legacy: ${e.message}")
            null
        }
    }

    private fun withLatestSavedPing(server: com.danila.nimbo.model.Server): com.danila.nimbo.model.Server {
        val profiles = loadProfiles()
        return profiles.asSequence()
            .flatMap { it.servers.asSequence() }
            .firstOrNull { saved -> saved.matchesSelection(server) }
            ?: server
    }

    fun saveLastSelectedProfileUrl(url: String) {
        sharedPreferences.edit().putString(KEY_LAST_SELECTED_PROFILE_URL, url).apply()
        Log.d("PreferencesManager", "Saved last selected profile reference")
    }

    fun loadLastSelectedProfileUrl(): String? {
        val url = sharedPreferences.getString(KEY_LAST_SELECTED_PROFILE_URL, null)
        Log.d("PreferencesManager", "Loaded last selected profile reference")
        return url
    }

    fun clearLastSelectedProfileUrl() {
        sharedPreferences.edit().remove(KEY_LAST_SELECTED_PROFILE_URL).apply()
        Log.d("PreferencesManager", "Cleared last selected profile URL")
    }

    fun clearLastSelectedServer() {
        sharedPreferences.edit().remove(KEY_LAST_SELECTED_SERVER).apply()
        Log.d("PreferencesManager", "Cleared last selected server")
    }

    fun saveHomeWidgets(widgets: List<HomeWidget>) {
        try {
            val json = gson.toJson(widgets)
            sharedPreferences.edit().putString(KEY_HOME_WIDGETS, json).apply()
            Log.d("PreferencesManager", "Saved home widgets: ${widgets.size} items")
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error saving home widgets: ${e.message}")
        }
    }

    fun loadHomeWidgets(): List<HomeWidget> {
        return try {
            val json = sharedPreferences.getString(KEY_HOME_WIDGETS, null)

            val widgets: MutableList<HomeWidget> = if (json != null) {
                val type = object : TypeToken<List<HomeWidget>>() {}.type
                val loaded = gson.fromJson<List<HomeWidget>>(json, type)
                val migrated = WidgetRegistry.migrateWidgets(loaded)
                Log.d("PreferencesManager", "Loaded home widgets: ${migrated.size} items (migrated)")
                migrated.toMutableList()
            } else {
                Log.d("PreferencesManager", "Using default home widgets")
                mutableListOf(
                    HomeWidget("vpn_button", WidgetType.VPN_BUTTON, true, 0),
                    HomeWidget("server_selector", WidgetType.SERVER_SELECTOR, true, 2),
                    HomeWidget("server_actions", WidgetType.SERVER_ACTIONS, true, 3),
                    HomeWidget("vpn_status", WidgetType.VPN_STATUS, true, 4),
                    HomeWidget("subscription_info", WidgetType.SUBSCRIPTION_INFO, true, 4)
                )
            }
            return widgets.mapIndexed { index, w ->
                w.copy(position = index)
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error loading home widgets: ${e.message}")
            emptyList()
        }
    }

    fun saveHiddenWidgets(widgetIds: List<String>) {
        try {
            val json = gson.toJson(widgetIds)
            sharedPreferences.edit().putString(KEY_HIDDEN_WIDGETS, json).apply()
            Log.d("PreferencesManager", "Saved hidden widgets: ${widgetIds.size} items")
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error saving hidden widgets: ${e.message}")
        }
    }

    fun loadHiddenWidgets(): List<String> {
        return try {
            val json = sharedPreferences.getString(KEY_HIDDEN_WIDGETS, null)
            if (json != null) {
                val type = object : TypeToken<List<String>>() {}.type
                val hidden = gson.fromJson<List<String>>(json, type)
                Log.d("PreferencesManager", "Loaded hidden widgets: ${hidden.size} items")
                hidden
            } else {
                Log.d("PreferencesManager", "No hidden widgets saved")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error loading hidden widgets: ${e.message}")
            emptyList()
        }
    }

    fun exportSettings(): String {
        return try {
            val payload = linkedMapOf<String, Any?>(
                "format" to "nebula_settings_v2",
                "exported_at" to System.currentTimeMillis(),
                "prefs" to sharedPreferences.all
            )
            gson.toJson(payload)
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error exporting settings: ${e.message}")
            ""
        }
    }

    fun importSettings(json: String): Boolean {
        return try {
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val prefsNode = if (root.has("prefs") && root.get("prefs").isJsonObject) {
                root.getAsJsonObject("prefs")
            } else {
                root
            }

            val editor = sharedPreferences.edit()

            prefsNode.entrySet().forEach { (key, element) ->
                when {
                    element == null || element.isJsonNull -> editor.remove(key)
                    element.isJsonPrimitive -> {
                        val primitive = element.asJsonPrimitive
                        when {
                            primitive.isBoolean -> editor.putBoolean(key, primitive.asBoolean)
                            primitive.isString -> editor.putString(key, primitive.asString)
                            primitive.isNumber -> {
                                val doubleVal = primitive.asDouble
                                if (doubleVal == doubleVal.toLong().toDouble()) {
                                    if (doubleVal >= Int.MIN_VALUE && doubleVal <= Int.MAX_VALUE) {
                                        editor.putInt(key, doubleVal.toInt())
                                    } else {
                                        editor.putLong(key, doubleVal.toLong())
                                    }
                                } else {
                                    editor.putFloat(key, doubleVal.toFloat())
                                }
                            }
                        }
                    }
                    element.isJsonArray -> {
                        val set = element.asJsonArray
                            .mapNotNull { item -> if (item.isJsonPrimitive && item.asJsonPrimitive.isString) item.asString else null }
                            .toSet()
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.apply()
            colorThemeState.value = sharedPreferences.getInt(KEY_COLOR_THEME, DEFAULT_COLOR_THEME_INDEX)
            val legacyStyle = sharedPreferences.getInt(KEY_VISUAL_STYLE, 0)
            backgroundStyleState.value = sharedPreferences.getInt(KEY_BACKGROUND_STYLE, legacyStyle)
            elementStyleState.value = sharedPreferences.getInt(KEY_ELEMENT_STYLE, legacyStyle)
            backgroundAnimationEnabledState.value = sharedPreferences.getBoolean(KEY_BACKGROUND_ANIMATION_ENABLED, true)
            highContrastUiState.value = sharedPreferences.getBoolean(KEY_HIGH_CONTRAST_UI, false)
            reducedTransparencyState.value = sharedPreferences.getBoolean(KEY_REDUCED_TRANSPARENCY, false)
            splashScreenEnabledState.value = sharedPreferences.getBoolean(KEY_SPLASH_SCREEN_ENABLED, true)
            glowEffectsEnabledState.value = sharedPreferences.getBoolean(KEY_GLOW_EFFECTS_ENABLED, true)
            useDynamicColorState.value = sharedPreferences.getBoolean(KEY_USE_DYNAMIC_COLOR, false)
            selectedAppIconState.value = sharedPreferences.getInt(KEY_SELECTED_APP_ICON, 0)
            showVersionInHeaderState.value = sharedPreferences.getBoolean(KEY_SHOW_VERSION_IN_HEADER, true)
            gradientEffectsEnabledState.value = sharedPreferences.getBoolean(KEY_GRADIENT_EFFECTS_ENABLED, true)
            customAccentColorState.value = sharedPreferences.getInt(KEY_CUSTOM_ACCENT_COLOR, 0xFF7C5DFA.toInt())
            isCustomAccentState.value = sharedPreferences.getBoolean(KEY_IS_CUSTOM_ACCENT, false)
            customGradientCountState.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COUNT, 1)
            customGradientColor1State.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_1, 0xFF7C5DFA.toInt())
            customGradientColor2State.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_2, 0xFF00E5B0.toInt())
            customGradientColor3State.value = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_3, 0xFF00D2FF.toInt())
            true
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error importing settings: ${e.message}")
            false
        }
    }

    fun getNotificationHistory(): List<com.danila.nimbo.model.NotificationItem> {
        val json = sharedPreferences.getString(KEY_NOTIFICATION_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<com.danila.nimbo.model.NotificationItem>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addNotificationToHistory(item: com.danila.nimbo.model.NotificationItem) {
        val history = getNotificationHistory().toMutableList()
        history.add(0, item) // Добавляем в начало
        if (history.size > 50) history.removeAt(history.size - 1) // Лимит 50 записей

        val json = gson.toJson(history)
        sharedPreferences.edit().putString(KEY_NOTIFICATION_HISTORY, json).apply()
    }

    fun removeNotificationFromHistory(id: String) {
        val history = getNotificationHistory().filterNot { it.id == id }
        if (history.isEmpty()) {
            sharedPreferences.edit().remove(KEY_NOTIFICATION_HISTORY).apply()
        } else {
            sharedPreferences.edit().putString(KEY_NOTIFICATION_HISTORY, gson.toJson(history)).apply()
        }
    }

    fun clearNotificationHistory() {
        sharedPreferences.edit().remove(KEY_NOTIFICATION_HISTORY).apply()
    }

    var lastPingTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_PING_TIME, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_PING_TIME, value).apply()

    var lastAppStartTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_APP_START_TIME, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_APP_START_TIME, value).apply()

    var lastConnectAtMs: Long
        get() = sharedPreferences.getLong(KEY_LAST_CONNECT_AT_MS, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_CONNECT_AT_MS, value).apply()

    var lastConnectAtText: String
        get() = sharedPreferences.getString(KEY_LAST_CONNECT_AT_TEXT, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_LAST_CONNECT_AT_TEXT, value).apply()

    var lastConnectedProfileUrl: String
        get() = sharedPreferences.getString(KEY_LAST_CONNECTED_PROFILE_URL, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_LAST_CONNECTED_PROFILE_URL, value).apply()

    var lastConnectedServerName: String
        get() = sharedPreferences.getString(KEY_LAST_CONNECTED_SERVER_NAME, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_LAST_CONNECTED_SERVER_NAME, value).apply()

    var showVersionInHeader: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOW_VERSION_IN_HEADER, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_VERSION_IN_HEADER, value).apply()
            showVersionInHeaderState.value = value
        }

    var customAccentColor: Int
        get() = sharedPreferences.getInt(KEY_CUSTOM_ACCENT_COLOR, 0xFF7C5DFA.toInt())
        set(value) {
            sharedPreferences.edit().putInt(KEY_CUSTOM_ACCENT_COLOR, value).apply()
            customAccentColorState.value = value
        }

    var isCustomAccent: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_CUSTOM_ACCENT, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_IS_CUSTOM_ACCENT, value).apply()
            isCustomAccentState.value = value
        }

    var gradientEffectsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_GRADIENT_EFFECTS_ENABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_GRADIENT_EFFECTS_ENABLED, value).apply()
            gradientEffectsEnabledState.value = value
        }

    var customGradientColor1: Int
        get() = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_1, 0xFF7C5DFA.toInt())
        set(value) {
            sharedPreferences.edit().putInt(KEY_CUSTOM_GRADIENT_COLOR_1, value).apply()
            customGradientColor1State.value = value
        }

    var customGradientColor2: Int
        get() = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_2, 0xFF00E5B0.toInt())
        set(value) {
            sharedPreferences.edit().putInt(KEY_CUSTOM_GRADIENT_COLOR_2, value).apply()
            customGradientColor2State.value = value
        }

    var customGradientColor3: Int
        get() = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COLOR_3, 0xFF00D2FF.toInt())
        set(value) {
            sharedPreferences.edit().putInt(KEY_CUSTOM_GRADIENT_COLOR_3, value).apply()
            customGradientColor3State.value = value
        }

    var customGradientCount: Int
        get() = sharedPreferences.getInt(KEY_CUSTOM_GRADIENT_COUNT, 1)
        set(value) {
            sharedPreferences.edit().putInt(KEY_CUSTOM_GRADIENT_COUNT, value).apply()
            customGradientCountState.value = value
        }

    var globalBrightness: Float
        get() = sharedPreferences.getFloat(KEY_GLOBAL_BRIGHTNESS, 1.0f).coerceIn(0.5f, 2.0f)
        set(value) {
            val safe = value.coerceIn(0.5f, 2.0f)
            sharedPreferences.edit().putFloat(KEY_GLOBAL_BRIGHTNESS, safe).apply()
            globalBrightnessState.value = safe
        }

    var globalTransparency: Float
        get() = sharedPreferences.getFloat(KEY_GLOBAL_TRANSPARENCY, 0.0f).coerceIn(0.0f, 1.0f)
        set(value) {
            val safe = value.coerceIn(0.0f, 1.0f)
            sharedPreferences.edit().putFloat(KEY_GLOBAL_TRANSPARENCY, safe).apply()
            globalTransparencyState.value = safe
        }

    var globalBlur: Float
        get() = sharedPreferences.getFloat(KEY_GLOBAL_BLUR, 25.0f).coerceIn(0.0f, 80.0f)
        set(value) {
            val safe = value.coerceIn(0.0f, 80.0f)
            sharedPreferences.edit().putFloat(KEY_GLOBAL_BLUR, safe).apply()
            globalBlurState.value = safe
        }

    var globalCorners: Float
        get() = sharedPreferences.getFloat(KEY_GLOBAL_CORNERS, 1.0f).coerceIn(0.25f, 4.0f)
        set(value) {
            val safe = value.coerceIn(0.25f, 4.0f)
            sharedPreferences.edit().putFloat(KEY_GLOBAL_CORNERS, safe).apply()
            globalCornersState.value = safe
        }
}
