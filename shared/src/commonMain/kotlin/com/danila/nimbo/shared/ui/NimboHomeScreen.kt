package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboHomeScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 58.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeProfileCard(state, actions)
        HomeConnectionButton(state, actions)
        HomeSelectedServer(state)
        HomeMonitoring(state)
    }
}

@Composable
private fun HomeProfileCard(state: NimboUiState, actions: NimboUiActions) {
    NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(NimboPalette.Control)
                        .border(1.dp, NimboPalette.Border, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { BasicText("☁", style = TextStyle(fontSize = 26.sp)) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = state.activeProfileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = NimboPalette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                    BasicText(
                        text = if (state.profileCount > 0) "${state.serverCount} серверов · без срока" else "Добавьте подписку или конфигурацию",
                        style = NimboBodyStyle
                    )
                }
                NimboPill(text = "↻", onClick = actions.onRefreshProfile)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NimboPill(text = "◉ Поддержка")
                NimboPill(text = "◎ Сайт")
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Hairline))
            BasicText(
                text = if (state.profileCount > 0) "${state.serverCount} серверов / ∞" else "Готово к импорту",
                style = TextStyle(color = NimboPalette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun HomeConnectionButton(state: NimboUiState, actions: NimboUiActions) {
    val connected = state.vpnState == "connected"
    val busy = state.vpnState in setOf("preparing", "connecting", "disconnecting")
    val failed = state.vpnState == "failed"
    val accent = when {
        connected -> NimboPalette.Green
        failed -> NimboPalette.Amber
        else -> NimboPalette.Accent
    }
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .drawBehind {
                    drawCircle(accent.copy(alpha = 0.08f), radius = size.minDimension * 0.50f)
                    drawCircle(accent.copy(alpha = 0.15f), radius = size.minDimension * 0.42f)
                }
                .clip(CircleShape)
                .background(NimboPalette.BackgroundDeep.copy(alpha = 0.76f))
                .border(1.dp, accent.copy(alpha = 0.46f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, enabled = !busy, onClick = actions.onToggleVpn),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = if (connected) "◆" else if (busy) "···" else "⏻",
                style = TextStyle(color = accent, fontSize = if (connected) 56.sp else 66.sp, fontWeight = FontWeight.Light)
            )
        }
        BasicText(
            text = when (state.vpnState) {
                "connected" -> "Защищено"
                "preparing" -> "Подготавливаем VPN…"
                "connecting" -> "Подключение…"
                "disconnecting" -> "Отключение…"
                "failed" -> "Не удалось подключиться"
                else -> "Нажмите для подключения"
            },
            style = TextStyle(color = accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        )
        if (failed && (!state.errorCode.isNullOrBlank() || !state.errorMessage.isNullOrBlank())) {
            NimboSurface(cornerRadius = 20.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    state.errorCode?.let {
                        BasicText(it, style = TextStyle(color = NimboPalette.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                    state.errorMessage?.let { BasicText(it, style = NimboBodyStyle.copy(textAlign = TextAlign.Center)) }
                }
            }
        }
    }
}

@Composable
private fun HomeSelectedServer(state: NimboUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NimboSurface(
            modifier = Modifier.weight(1f).height(76.dp),
            cornerRadius = 22.dp,
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(NimboPalette.Control),
                    contentAlignment = Alignment.Center
                ) { BasicText("🌐", style = TextStyle(fontSize = 22.sp)) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        state.activeServerName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = NimboPalette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    BasicText("VLESS · Reality", style = NimboBodyStyle.copy(fontSize = 12.sp))
                }
                NimboPill("— ms")
            }
        }
        NimboSurface(
            modifier = Modifier.size(76.dp),
            cornerRadius = 22.dp,
            padding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText("☷", style = TextStyle(color = NimboPalette.Text, fontSize = 28.sp))
            }
        }
    }
}

@Composable
private fun HomeMonitoring(state: NimboUiState) {
    NimboSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Мониторинг", style = NimboSectionTitleStyle.copy(fontSize = 16.sp))
                Spacer(Modifier.weight(1f))
                BasicText("⌃", style = TextStyle(color = NimboPalette.TextSecondary, fontSize = 20.sp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Hairline))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Скорость", if (state.vpnState == "connected") "↑ 0 КБ/с" else "Нет трафика", Modifier.weight(1f))
                MetricCard("Память", "— МБ", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(NimboPalette.Control).padding(14.dp)) {
        Column {
            BasicText(title, style = NimboBodyStyle.copy(fontSize = 12.sp))
            BasicText(value, style = TextStyle(color = NimboPalette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}
