package com.danila.nimbo.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danila.nimbo.R
import com.danila.nimbo.ui.components.SettingsNavigationItem
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import com.danila.nimbo.utils.*
import kotlinx.coroutines.delay

@Composable
internal fun VpnLiveUpdateSettings() {
    val context = LocalContext.current
    val preferences = com.danila.nimbo.ui.LocalPreferencesManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(preferences.vpnLiveUpdateEnabled) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var snapshot by remember { mutableStateOf(VpnLiveUpdatePlatform.snapshot(context)) }
    var settingsFailed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumed = true
                enabled = preferences.vpnLiveUpdateEnabled
                snapshot = VpnLiveUpdatePlatform.snapshot(context)
            } else if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column {
        SettingsSwitch(Icons.Default.Cloud, t("Динамическая пилюля", "Live status pill"),
            if (enabled) t("Статус рядом с часами, затем — облачко. Выключите для обычного уведомления.",
                "Brief status beside the clock, then the cloud. Turn off for a regular notification.")
            else t("Обычное уведомление NIMBO. VPN продолжает работать.",
                "Regular NIMBO notification. VPN keeps running."), enabled) {
            enabled = it
            preferences.vpnLiveUpdateEnabled = it
            snapshot = VpnLiveUpdatePlatform.snapshot(context)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(t("Предпросмотр", "Preview"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (enabled) {
                    VpnPillPreview(enabled = true, resumed = resumed, english = preferences.appLanguage == "en")
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.nimbo_cloud), null, Modifier.size(24.dp))
                        Column {
                            Text("NIMBO", style = MaterialTheme.typography.labelLarge)
                            Text(t("Обычное VPN-уведомление", "Regular VPN notification"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        SettingsNavigationItem(Icons.Default.Settings, t("Пилюля в Android", "Android Live Updates"),
            pillAvailabilityLabel(snapshot)) {
            settingsFailed = !VpnLiveUpdatePlatform.openSettings(context, snapshot.availability)
        }
        if (settingsFailed) Text(t("Настройки Android недоступны. Откройте их из меню телефона.",
            "Android settings are unavailable. Open them from your phone's Settings app."),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun pillAvailabilityLabel(snapshot: VpnPillSnapshot): String = when (snapshot.availability) {
    VpnPillAvailability.UNSUPPORTED -> t("Нужен Android 16 или новее", "Requires Android 16 or newer")
    VpnPillAvailability.APP_DISABLED -> t("Обычное VPN-уведомление", "Regular VPN notification")
    VpnPillAvailability.NOTIFICATIONS_BLOCKED -> t("Разрешите уведомления Nimbo", "Allow Nimbo notifications")
    VpnPillAvailability.CHANNEL_BLOCKED -> t("Канал VPN отключён в Android", "VPN channel is disabled in Android")
    VpnPillAvailability.CHANNEL_MINIMIZED -> t("Канал VPN свёрнут — измените в Android", "VPN channel is minimized — change it in Android")
    VpnPillAvailability.SYSTEM_DISABLED -> t("Разрешите Live Updates в Android", "Allow Live Updates in Android")
    VpnPillAvailability.UNKNOWN -> t("Не удалось проверить разрешение Android", "Couldn't check Android permission")
    VpnPillAvailability.AVAILABLE -> when {
        snapshot.postedPromoted == true -> t("Android повысил уведомление VPN", "Android promoted the VPN notification")
        snapshot.postedCompatible == false -> t("Android не принял формат уведомления", "Android rejected the notification format")
        snapshot.postedPromoted == false -> t("Запрошена — отображение выбирает Android", "Requested — Android controls the display")
        else -> t("Разрешена — появится при подключении VPN", "Allowed — requested when VPN connects")
    }
}

/** A labelled sample, not an overlay. One short sequence per appearance/replay, no polling. */
@Composable
private fun VpnPillPreview(enabled: Boolean, resumed: Boolean, english: Boolean) {
    val motionAllowed = LocalBackgroundAnimationEnabled.current && !LocalReducedTransparencyEnabled.current &&
        ValueAnimator.areAnimatorsEnabled()
    var replay by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(VpnPillState.CONNECTED) }
    var seconds by remember { mutableIntStateOf(6) }
    LaunchedEffect(replay, motionAllowed, resumed, enabled) {
        state = VpnPillState.CONNECTED; seconds = 6
        if (!motionAllowed || !resumed || !enabled) return@LaunchedEffect
        state = VpnPillState.CONNECTING; seconds = 0
        delay(1200)
        state = VpnPillState.CONNECTED
        delay(1800)
        seconds = 6
    }
    val text = if (enabled) vpnPillText(state, seconds, english) else null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(40.dp).clip(RoundedCornerShape(50))
            .background(Color(0xFF101114)).border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.nimbo_cloud), t("Облачко Nimbo", "Nimbo cloud"),
                Modifier.size(20.dp), tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            AnimatedContent(targetState = text, label = "vpn-pill-preview", transitionSpec = {
                ((fadeIn(tween(if (motionAllowed) 200 else 0)) +
                    slideInVertically(tween(if (motionAllowed) 220 else 0)) { if (motionAllowed) it / 3 else 0 }) togetherWith
                    (fadeOut(tween(if (motionAllowed) 140 else 0)) +
                    slideOutVertically(tween(if (motionAllowed) 180 else 0)) { if (motionAllowed) -it / 3 else 0 })).using(
                    SizeTransform { _, _ -> tween(if (motionAllowed) 220 else 0) })
            }) { value ->
                if (value != null) Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp))
                else Spacer(Modifier.size(0.dp))
            }
        }
        TextButton(onClick = { replay++ }, enabled = enabled && motionAllowed) {
            Text(t("Повторить", "Replay"))
        }
    }
}
