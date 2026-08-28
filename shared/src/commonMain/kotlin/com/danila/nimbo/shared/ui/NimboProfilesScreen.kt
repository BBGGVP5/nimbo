package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboProfilesScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 58.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText("Профили", style = NimboTitleStyle)
                BasicText(
                    if (state.profileCount == 0) "Подписок пока нет" else "${state.serverCount} серверов · ${state.profileCount} подписка",
                    style = NimboBodyStyle
                )
            }
            NimboPill("★")
            Spacer(Modifier.width(8.dp))
            NimboPill("＋", onClick = actions.onAddProfile)
        }

        NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("⌕", style = TextStyle(color = NimboPalette.TextTertiary, fontSize = 28.sp))
                Spacer(Modifier.width(12.dp))
                BasicText("Поиск серверов", style = NimboBodyStyle.copy(fontSize = 17.sp))
            }
        }

        if (state.profileCount == 0) {
            NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText("☁", style = TextStyle(fontSize = 46.sp))
                    BasicText("Добавьте первую подписку", style = NimboSectionTitleStyle)
                    BasicText(
                        "Поддерживаются URL подписок и отдельные конфигурации. Импорт откроется в защищённом окне Nimbo.",
                        style = NimboBodyStyle,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NimboPill("＋ Добавить профиль", selected = true, onClick = actions.onAddProfile)
                }
            }
        } else {
            ProfileSubscriptionCard(state, actions)
            BasicText("${state.serverCount} СЕРВЕРОВ", style = NimboBodyStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
            ProfileServerCard(state.activeServerName, "VLESS · Reality", "— ms")
            ProfileServerCard("Резервный сервер", "VLESS · TLS", "— ms")
        }
    }
}

@Composable
private fun ProfileSubscriptionCard(state: NimboUiState, actions: NimboUiActions) {
    NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(NimboPalette.Control),
                    contentAlignment = Alignment.Center
                ) { BasicText("☁", style = TextStyle(fontSize = 25.sp)) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        state.activeProfileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = NimboSectionTitleStyle.copy(fontSize = 19.sp)
                    )
                    BasicText("${state.serverCount} серверов", style = NimboBodyStyle)
                }
                NimboPill("↻", onClick = actions.onRefreshProfile)
                Spacer(Modifier.width(6.dp))
                NimboPill("⋮", onClick = actions.onOpenProfileSettings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric("ТРАФИК", "∞", Modifier.weight(1f))
                ProfileMetric("ИСТЕКАЕТ", "∞", Modifier.weight(1f))
                ProfileMetric("ОБНОВЛЕНО", "сейчас", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NimboPill("◉ Поддержка")
                NimboPill("◎ Сайт")
            }
        }
    }
}

@Composable
private fun ProfileMetric(title: String, value: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(15.dp)).background(NimboPalette.Control).padding(11.dp)) {
        Column {
            BasicText(title, style = NimboBodyStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold))
            BasicText(value, style = TextStyle(color = NimboPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun ProfileServerCard(name: String, transport: String, ping: String) {
    NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(NimboPalette.Control),
                contentAlignment = Alignment.Center
            ) { BasicText("🌐", style = TextStyle(fontSize = 22.sp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NimboSectionTitleStyle.copy(fontSize = 16.sp))
                BasicText(transport, style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
            NimboPill(ping)
            Spacer(Modifier.width(6.dp))
            NimboPill("⋮")
        }
    }
}
