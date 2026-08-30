package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboSettingsScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 58.dp, bottom = 140.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText("Настройки", style = NimboTitleStyle)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsTile(NimboIconName.ROUTE, "Маршруты", Modifier.weight(1f), actions.onOpenSystemSettings)
            SettingsTile(NimboIconName.CONNECTION, "Соединения", Modifier.weight(1f), actions.onOpenSystemSettings)
            SettingsTile(NimboIconName.NOTIFICATIONS, "Уведомления", Modifier.weight(1f), actions.onOpenSystemSettings)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsTile(NimboIconName.STATS, "Статистика", Modifier.weight(1f), actions.onOpenDiagnostics)
            SettingsTile(NimboIconName.SYNC, "Синхронизация", Modifier.weight(1f), actions.onOpenSystemSettings)
            SettingsTile(NimboIconName.LOGS, "Логи", Modifier.weight(1f), actions.onOpenDiagnostics)
        }

        BasicText("Оформление", style = NimboSectionTitleStyle)
        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column {
                SettingsRow(NimboIconName.APPS, "Nimbo Glass", "Жидкое стекло и системная плавность", showDivider = true)
                SettingsRow(NimboIconName.FAVORITE, "Системный акцент", "Цвет интерфейса следует настройкам устройства", showDivider = true)
                SettingsRow(NimboIconName.NOTIFICATIONS, "Тактильный отклик", "Используется системный Taptic Engine")
            }
        }

        BasicText("Система", style = NimboSectionTitleStyle)
        NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SystemValue("Устройство", state.deviceName)
                SystemValue("Система", state.systemName)
                SystemValue("Версия Nimbo", state.appVersion)
            }
        }

        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column {
                SettingsRow(
                    NimboIconName.LOGS,
                    "Диагностика iOS",
                    "Логи приложения и расширения без секретов",
                    showDivider = true,
                    onClick = actions.onOpenDiagnostics
                )
                SettingsRow(
                    NimboIconName.INFO,
                    "О приложении",
                    "Версия, система, устройство и лицензии",
                    onClick = actions.onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun SettingsTile(icon: NimboIconName, title: String, modifier: Modifier, onClick: () -> Unit) {
    NimboSurface(modifier = modifier, cornerRadius = 22.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NimboIcon(icon, tint = NimboPalette.Accent, modifier = Modifier.size(28.dp))
            BasicText(title, maxLines = 1, style = TextStyle(color = NimboPalette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: NimboIconName,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 44.dp else 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NimboIcon(icon, tint = NimboPalette.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal
                )
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(
                        color = NimboPalette.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
    if (showDivider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}

@Composable
private fun SystemValue(label: String, value: String) {
    Row {
        BasicText(label, style = NimboBodyStyle)
        Spacer(Modifier.weight(1f))
        BasicText(value, style = TextStyle(color = NimboPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
