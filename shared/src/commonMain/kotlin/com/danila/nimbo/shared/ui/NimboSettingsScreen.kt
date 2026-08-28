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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboSettingsScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 58.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText("Настройки", style = NimboTitleStyle)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsTile("☷", "Маршрутизация", Modifier.weight(1f), actions.onOpenSystemSettings)
            SettingsTile("⚿", "Соединения", Modifier.weight(1f), actions.onOpenSystemSettings)
            SettingsTile("♧", "Уведомления", Modifier.weight(1f), actions.onOpenSystemSettings)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsTile("▥", "Статистика", Modifier.weight(1f), actions.onOpenDiagnostics)
            SettingsTile("↻", "Синхронизация", Modifier.weight(1f), actions.onOpenSystemSettings)
            SettingsTile("≡", "Логи", Modifier.weight(1f), actions.onOpenDiagnostics)
        }

        BasicText("Оформление", style = NimboSectionTitleStyle)
        NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRow("◌", "Nimbo Glass", "Жидкое стекло и системная плавность")
                SettingsRow("◉", "Системный акцент", "Цвет интерфейса следует настройкам устройства")
                SettingsRow("◌", "Тактильный отклик", "Используется системный Taptic Engine")
            }
        }

        BasicText("Система", style = NimboSectionTitleStyle)
        NimboSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SystemValue("Устройство", state.deviceName)
                SystemValue("Система", state.systemName)
                SystemValue("Версия Nimbo", state.appVersion)
            }
        }

        NimboSurface(modifier = Modifier.fillMaxWidth(), onClick = actions.onOpenDiagnostics) {
            SettingsRow("▤", "Диагностика iOS", "Логи приложения и расширения без секретов")
        }
        NimboSurface(modifier = Modifier.fillMaxWidth(), onClick = actions.onOpenAbout) {
            SettingsRow("ⓘ", "О приложении", "Версия, система, устройство и лицензии")
        }
    }
}

@Composable
private fun SettingsTile(glyph: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    NimboSurface(modifier = modifier, cornerRadius = 22.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText(glyph, style = TextStyle(color = NimboPalette.Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            BasicText(title, maxLines = 1, style = TextStyle(color = NimboPalette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun SettingsRow(glyph: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(NimboPalette.Control),
            contentAlignment = Alignment.Center
        ) { BasicText(glyph, style = TextStyle(color = NimboPalette.Accent, fontSize = 22.sp)) }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = NimboPalette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 12.sp))
        }
        BasicText("›", style = TextStyle(color = NimboPalette.TextSecondary, fontSize = 24.sp))
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
