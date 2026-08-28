@file:Suppress("FunctionName", "unused")

package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.danila.nimbo.shared.subscription.NormalizedSubscription
import kotlinx.serialization.json.Json
import platform.Foundation.NSNotificationCenter
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
            selected = server.id == activeServerId
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
        servers = servers
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
                onOpenSystemSettings = { postIosAction(SystemSettingsAction) }
            )
        )
    }

private fun postIosAction(name: String, payload: String? = null) {
    NSNotificationCenter.defaultCenter.postNotificationName(name, payload)
}
