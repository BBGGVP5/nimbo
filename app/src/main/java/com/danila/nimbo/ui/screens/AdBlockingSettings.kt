package com.danila.nimbo.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.AdBlockingPreference
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState

@Composable
internal fun AdBlockingSettingsCard(preferences: PreferencesManager) {
    val colors = LocalNebulaColors.current
    var enabled by remember(preferences) { mutableStateOf(preferences.adBlockingEnabled) }
    var showInfo by remember { mutableStateOf(false) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AdBlockingPreference.KEY) enabled = preferences.adBlockingEnabled
        }
        preferences.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth()) {
            SettingsSwitch(Icons.Default.Block, t("Блокировка рекламы", "Ad blocking"),
                t("Со следующего подключения", "Applies on next connection"),
                checked = enabled) {
                enabled = it
                preferences.adBlockingEnabled = it
            }
            val active = VpnManager.activeAdBlockingEnabled.value
            if (VpnManager.state.value == VpnState.CONNECTED) Text(
                text = when (active) {
                    true -> t("Текущее подключение: включено", "Current connection: on")
                    false -> t("Текущее подключение: выключено", "Current connection: off")
                    null -> t("Текущее ядро: блокировка недоступна", "Current core: ad blocking unavailable")
                }, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(t("Фильтрует рекламные домены. Не убирает всю рекламу.",
                    "Filters ad domains. Does not remove all ads."),
                    color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Default.Info, t("Подробнее о блокировке рекламы", "Ad blocking details"),
                        tint = colors.textSecondary)
                }
            }
        }
    }
    if (showInfo) AlertDialog(
        onDismissRequest = { showInfo = false },
        title = { Text(t("Блокировка рекламы", "Ad blocking")) },
        text = { Text(t(
            "Xray и Mihomo фильтруют домены в трафике Nimbo. Mihomo требует режим rule. Встроенная реклама, обход Nimbo и собственный зашифрованный DNS приложений могут остаться. Правила подписки и ваши правила сохраняются и при выключенной опции.",
            "Xray and Mihomo filter domains in Nimbo traffic. Mihomo requires rule mode. In-app/video ads, bypass traffic and encrypted DNS may remain. Provider and custom rules are preserved, even when this option is off."
        )) },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text(t("Понятно", "Got it")) } }
    )
}
