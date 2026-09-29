package com.danila.nimbo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.mihomo.MihomoBridge
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.VpnCoreChoice
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NimboCoreSettings(
    preferences: PreferencesManager,
    onConnect: ((com.danila.nimbo.model.Server) -> Unit)? = null,
    onOpenProfiles: () -> Unit = {}
) {
    val mihomoAvailable by produceState(false) {
        value = withContext(Dispatchers.IO) { MihomoBridge.available() }
    }
    val active = if (VpnManager.state.value == VpnState.CONNECTED)
        VpnManager.connectedServer.value?.let {
            when {
                it.protocol.equals("mihomo", ignoreCase = true) -> "Mihomo"
                it.usesAwgEngine() -> "AmneziaWG"
                else -> "Xray"
            }
        } else null
    NimboCoreSettingsContent(preferences.vpnCoreState.value, {
        preferences.vpnCore = it
    }, active,
        mihomoAvailable = mihomoAvailable, onOpenMihomoProfiles = onOpenProfiles)
}

@Composable
internal fun NimboCoreSettingsContent(
    coreId: String,
    onCoreChange: (String) -> Unit,
    active: String? = null,
    mihomoAvailable: Boolean = false,
    onOpenMihomoProfiles: () -> Unit = {},
) {
    val colors = LocalNebulaColors.current
    val chosen = VpnCoreChoice.fromId(coreId)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (active != null) {
                Text(t("Текущее подключение: $active", "Current connection: $active"),
                    style = MaterialTheme.typography.titleSmall, color = colors.textPrimary,
                    modifier = Modifier.testTag("vpn-core-active"))
        }
        Text(t("Выбор применяется при следующем подключении. Текущий туннель продолжит работать на своём ядре.",
            "Applies on the next connection. The current tunnel keeps its core."),
            style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VpnCoreChoice.entries.forEach { choice ->
                val enabled = choice != VpnCoreChoice.MIHOMO || mihomoAvailable
                val isSelected = chosen == choice
                val title = if (choice == VpnCoreChoice.AUTO) t("Авто", "Auto") else choice.title
                val detail = when (choice) {
                    VpnCoreChoice.AUTO -> t("Ядро по типу профиля: Xray, AmneziaWG или Mihomo", "Use the profile's engine: Xray, AmneziaWG or Mihomo")
                    VpnCoreChoice.XRAY -> t("Только совместимые с Xray серверы и конфигурации", "Only Xray-compatible servers and configurations")
                    VpnCoreChoice.AWG -> t("AmneziaWG и WireGuard", "AmneziaWG and WireGuard")
                    VpnCoreChoice.MIHOMO -> if (mihomoAvailable)
                        t("Подписки, группы прокси, правила и DNS сервиса", "Subscriptions, proxy groups, provider rules and DNS")
                    else t("Android bridge отсутствует в этой сборке", "Android bridge is not included in this build")
                }
                Surface(onClick = { if (chosen != choice) onCoreChange(choice.id) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("vpn-core-${choice.id}")
                        .semantics { selected = isSelected; role = Role.RadioButton },
                    color = colors.panelFill, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.panelBorder)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = isSelected, onClick = null, enabled = enabled,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium,
                                color = if (enabled) colors.textPrimary else colors.textSecondary)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onOpenMihomoProfiles, modifier = Modifier.fillMaxWidth()
            .testTag("mihomo-open-profiles"), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)) {
            Text(t("Профили и группы Mihomo", "Mihomo profiles and groups"))
        }
        if (chosen == null) Text(t("Сохранённое ядро не распознано. Выберите один из доступных вариантов.",
            "The saved core is unknown. Select an available option."), color = colors.textPrimary)
        Text(t("«Авто» здесь выбирает ядро, а не локацию. Автовыбор рабочего сервера включается в профилях. При ручном выборе ядра автоподбор использует только совместимые серверы.",
            "Auto here selects an engine, not a location. Automatic server selection is in Profiles. With a manual core selection, only compatible servers are considered."),
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}
