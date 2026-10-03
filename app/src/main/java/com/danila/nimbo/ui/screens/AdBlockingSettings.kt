package com.danila.nimbo.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                t("Сохранённая настройка · со следующего подключения", "Saved preference · applies on next connection"),
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
            Text(t(
                "Xray и Mihomo блокируют известные рекламные и трекинговые домены в трафике Nimbo. Для Mihomo нужен режим rule. Не вся реклама в приложениях и видео, обход VPN и собственный зашифрованный DNS поддаются фильтрации. Правила провайдера действуют независимо от переключателя.",
                "Xray and Mihomo block known advertising and tracking domains in Nimbo-routed traffic. Mihomo requires rule mode. Some in-app/video ads, VPN bypass traffic and app-specific encrypted DNS cannot be filtered. Provider rules apply independently of this switch."
            ), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
        }
    }
}
