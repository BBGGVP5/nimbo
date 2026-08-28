package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboAppsScreen(state: NimboUiState, actions: NimboUiActions) {
    var mode by remember { mutableStateOf("vpn") }
    var bundleId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 58.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText("Маршрутизация по приложениям", style = NimboTitleStyle.copy(fontSize = 32.sp))
        BasicText("Выберите, какие приложения должны использовать туннель.", style = NimboBodyStyle)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NimboPill("Через VPN", modifier = Modifier.weight(1f), selected = mode == "vpn", onClick = { mode = "vpn" })
            NimboPill("В обход", modifier = Modifier.weight(1f), selected = mode == "bypass", onClick = { mode = "bypass" })
        }

        NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Правила iOS", style = NimboSectionTitleStyle)
                BasicText(
                    "iOS не разрешает VPN-клиенту читать список установленных программ. Добавьте Bundle ID вручную или перенесите правила с другого устройства.",
                    style = NimboBodyStyle
                )
                BasicTextField(
                    value = bundleId,
                    onValueChange = { bundleId = it.trim() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = NimboPalette.Text, fontSize = 17.sp),
                    cursorBrush = SolidColor(NimboPalette.Accent),
                    decorationBox = { inner ->
                        if (bundleId.isBlank()) BasicText("com.example.app", style = NimboBodyStyle.copy(fontSize = 17.sp))
                        inner()
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NimboPill("Открыть настройки iOS", onClick = actions.onOpenSystemSettings)
                    Spacer(Modifier.weight(1f))
                NimboPill(
                        "Добавить",
                        selected = bundleId.isNotBlank(),
                        onClick = if (bundleId.isNotBlank()) {
                            { actions.onSaveAppRule(bundleId); bundleId = "" }
                        } else null
                    )
                }
            }
        }

        NimboSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("Синхронизированные правила", style = NimboSectionTitleStyle)
                val rules = state.appBundleIds.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
                if (rules.isEmpty()) {
                    BasicText("Пока нет правил для приложений", style = NimboBodyStyle)
                } else {
                    rules.forEach { NimboPill(it) }
                }
                NimboPill("Получить с другого устройства")
            }
        }
    }
}
