package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Routing preference only; changing it never reconnects the active session. */
@Composable
internal fun AdBlockingSettingsCard(state: NimboUiState, actions: NimboUiActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Фильтрация", Modifier.padding(start = 16.dp, top = 4.dp),
            style = NimboBodyStyle.copy(color = NimboPalette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        NimboSurface(Modifier.fillMaxWidth().testTag("ad-blocking-settings"),
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            Column {
                AppearanceToggle("Блокировка рекламы", state.adBlockingEnabled,
                    info = "Xray и Mihomo фильтруют домены в трафике Nimbo. Mihomo требует режим rule. Встроенная реклама, обход Nimbo и собственный зашифрованный DNS приложений могут остаться. Правила подписки и ваши правила сохраняются и при выключенной опции.",
                    subtitle = "Рекламные домены · не вся реклама",
                    onChange = actions.onSetAdBlocking)
                val status = when {
                    state.vpnState != "connected" -> "Со следующего подключения"
                    state.activeAdBlockingEnabled == null -> "Со следующего подключения · текущая сессия неизвестна"
                    state.activeAdBlockingEnabled != state.adBlockingEnabled -> "Изменится при следующем подключении"
                    state.activeAdBlockingEnabled -> "Текущее подключение: включено"
                    else -> "Текущее подключение: выключено"
                }
                BasicText(status, Modifier.padding(top = 2.dp, bottom = 10.dp), style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
        }
    }
}
