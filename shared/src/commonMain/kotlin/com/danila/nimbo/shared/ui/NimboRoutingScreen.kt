package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Раздельного туннелирования по приложениям на iOS не существует: система не
 * даёт обычному VPN выбирать процессы (это умеет только управляемый профиль
 * MDM). Поэтому вместо экрана приложений здесь настоящая маршрутизация — то,
 * чем iOS действительно управляет: обход локальных сетей, DNS туннеля и
 * определение доменов.
 */
@Composable
internal fun NimboRoutingScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "‹ Настройки",
                modifier = Modifier.nimboRowClickable {
                    actions.onOpenScreen(NimboScreen.SETTINGS.wireName)
                },
                style = TextStyle(
                    color = NimboPalette.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        BasicText("Маршрутизация", style = NimboTitleStyle)
        BasicText(
            "Куда и как направлять трафик. Изменения применяются при следующем подключении.",
            style = NimboBodyStyle
        )

        // Модули — соседний способ управлять маршрутом, поэтому кнопка стоит
        // здесь, а не прячется в настройках.
        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            onClick = { actions.onOpenScreen(NimboScreen.MODULES.wireName) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NimboIcon(NimboIconName.LIST, tint = NimboPalette.Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText("Модули", style = NimboSectionTitleStyle.copy(fontSize = 16.sp))
                    BasicText(
                        if (state.modules.isEmpty()) {
                            "Свои правила: домены напрямую, через VPN или в блок"
                        } else {
                            "${state.modules.count { it.enabled }} включено из ${state.modules.size}"
                        },
                        style = NimboBodyStyle.copy(fontSize = 12.sp)
                    )
                }
                BasicText("›", style = TextStyle(color = NimboPalette.Accent, fontSize = 20.sp))
            }
        }

        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column {
                RoutingToggleRow(
                    title = "Обход локальных сетей",
                    subtitle = "Принтеры, NAS и роутер остаются доступны напрямую",
                    checked = state.routingBypassLocal,
                    showDivider = true,
                    onChange = { actions.onSetRouting("bypassLocal", it.toString()) }
                )
                RoutingToggleRow(
                    title = "Определение доменов",
                    subtitle = "Ядро читает имя сайта из соединения — нужно для правил по доменам",
                    checked = state.routingSniffing,
                    onChange = { actions.onSetRouting("sniffing", it.toString()) }
                )
            }
        }

        BasicText("DNS в туннеле", style = NimboSectionTitleStyle)
        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column {
                NimboDnsPreset.entries.forEachIndexed { index, preset ->
                    RoutingChoiceRow(
                        title = preset.title,
                        subtitle = preset.subtitle,
                        selected = state.routingDns == preset.key,
                        showDivider = index != NimboDnsPreset.entries.lastIndex,
                        onClick = { actions.onSetRouting("dns", preset.key) }
                    )
                }
            }
        }
    }
}

/** Наборы DNS: значения уходят в системные настройки туннеля. */
internal enum class NimboDnsPreset(
    val key: String,
    val title: String,
    val subtitle: String
) {
    CLOUDFLARE("cloudflare", "Cloudflare", "1.1.1.1 — по умолчанию"),
    GOOGLE("google", "Google", "8.8.8.8"),
    ADGUARD("adguard", "AdGuard", "94.140.14.14 — с фильтрацией рекламы"),
    SYSTEM("system", "Системный", "DNS оператора или Wi-Fi сети")
}

@Composable
private fun RoutingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal
                )
            )
            BasicText(
                subtitle,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(
                    color = NimboPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
        Spacer(Modifier.width(12.dp))
        NimboToggle(checked = checked, onChange = onChange)
    }
    if (showDivider) {
        val style = LocalNimboElementStyle.current
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (style == NimboElementStyle.MANGA) 1.5.dp else 1.dp)
                .background(
                    if (style == NimboElementStyle.MANGA) NimboMangaPalette.Ink.copy(alpha = 0.34f)
                    else Color.White.copy(alpha = 0.06f)
                )
        )
    }
}

@Composable
private fun RoutingChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .nimboRowClickable(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal
                )
            )
            BasicText(
                subtitle,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(
                    color = NimboPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
        if (selected) {
            NimboIcon(
                NimboIconName.SECURITY,
                tint = NimboPalette.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (showDivider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}
