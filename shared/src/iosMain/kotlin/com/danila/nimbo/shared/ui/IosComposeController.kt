@file:Suppress("FunctionName", "unused")

package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
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

private val iosUiState = mutableStateOf(NimboUiState())

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
    appVersion: String
) {
    iosUiState.value = NimboUiState(
        vpnState = vpnState,
        errorCode = errorCode,
        errorMessage = errorMessage,
        activeProfileName = activeProfileName,
        activeServerName = activeServerName,
        serverCount = serverCount,
        profileCount = profileCount,
        deviceName = deviceName,
        systemName = systemName,
        appVersion = appVersion
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
