package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Routing preference only; changing it never reconnects the active session. */
@Composable
internal fun AdBlockingSettingsCard(state: NimboUiState, actions: NimboUiActions) {
    NimboSurface(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppearanceToggle("Блокировка рекламы", state.adBlockingEnabled,
                info = "Xray и Mihomo фильтруют домены в трафике Nimbo. Mihomo требует режим rule. Встроенная реклама, обход Nimbo и собственный зашифрованный DNS приложений могут остаться. Правила подписки и ваши правила сохраняются и при выключенной опции.",
                onChange = actions.onSetAdBlocking)
            val status = when {
                state.vpnState != "connected" -> "Со следующего подключения"
                state.activeAdBlockingEnabled == null -> "Со следующего подключения · текущая сессия неизвестна"
                state.activeAdBlockingEnabled != state.adBlockingEnabled -> "Изменится при следующем подключении"
                state.activeAdBlockingEnabled -> "Текущее подключение: включено"
                else -> "Текущее подключение: выключено"
            }
            BasicText(status, style = NimboBodyStyle.copy(color = NimboPalette.Text, fontSize = 12.sp))
            BasicText("Фильтрует рекламные домены. Не убирает всю рекламу.", style = NimboBodyStyle.copy(fontSize = 12.sp))
        }
    }
}
