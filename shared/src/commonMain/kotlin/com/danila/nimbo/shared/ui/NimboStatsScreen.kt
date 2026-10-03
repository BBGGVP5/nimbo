package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A line requires two measured points; never fabricate a ramp from one sample. */
internal fun hasMeasuredHistory(sampleCount: Int): Boolean = sampleCount >= 2

internal data class NimboTrafficSummary(val title: String, val download: Long, val upload: Long)

/** Disconnected interface counters are not a recorded session. History is newest-first. */
internal fun trafficSummary(state: NimboUiState): NimboTrafficSummary? =
    if (state.vpnState == "connected") NimboTrafficSummary("Текущая сессия", state.downloadTotal, state.uploadTotal)
    else state.sessions.firstOrNull()?.let { NimboTrafficSummary("Последняя сессия", it.download, it.upload) }

@Composable
internal fun NimboStatsScreen(state: NimboUiState, actions: NimboUiActions) {
    val connected = state.vpnState == "connected"
    val summary = trafficSummary(state)
    LazyColumn(
        modifier = Modifier.fillMaxSize().nimboScreenPadding(),
        contentPadding = PaddingValues(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("heading") { NimboPageHeading("Статистика", "Трафик и история подключений") }
        item("summary") {
            NimboSurface(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BasicText(summary?.title ?: "Пока нет подключений", style = NimboSectionTitleStyle)
                    if (summary == null) {
                        BasicText("Здесь появится трафик VPN. История сохраняется после отключения.", style = NimboBodyStyle)
                    } else {
                        TrafficTotalCards(state, summary)
                        if (connected && state.sessionAvailable != false) {
                            if (hasMeasuredHistory(state.speedSamples.size)) {
                                SpeedChartCanvas(state.speedSamples, Modifier.fillMaxWidth().height(88.dp))
                                BasicText("История скорости · приём и передача", style = NimboBodyStyle.copy(fontSize = 12.sp))
                            } else {
                                BasicText("Собираем историю скорости…", style = NimboBodyStyle.copy(fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
        }
        item("routes") { RouteTrafficCard(state) }
        item("protocols") { ActiveProtocolCards(state) }
        item("ad-blocking") { AdBlockingCard(state, actions) }
        item("server") {
            NimboSurface(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Выбранный сервер", style = NimboSectionTitleStyle)
                    BasicText(withoutFlagEmoji(state.activeServerName).ifBlank { "Не выбран" }, style = NimboBodyStyle.copy(color = NimboPalette.Text))
                    val selected = state.servers.firstOrNull { it.selected || it.id == state.activeServerId }
                    StatLine("Задержка", pingDisplayLabel(selected?.ping, selected?.pingInProgress == true, state.pingProtocol))
                    StatLine("В подписке", serverCountLabel(state.serverCount))
                    if (state.profileTrafficLabel.isNotBlank()) StatLine("Трафик подписки", state.profileTrafficLabel)
                }
            }
        }
        item("history-heading") { BasicText("История сессий", Modifier.padding(top = 8.dp), style = NimboSectionTitleStyle) }
        if (state.sessions.isEmpty()) {
            item("history-empty") {
                BasicText("Завершённых сессий пока нет.", Modifier.padding(bottom = 12.dp), style = NimboBodyStyle)
            }
        } else {
            // Each session is lazy: importing a long history must not compose every row.
            itemsIndexed(state.sessions, key = { index, _ -> "session-$index" }, contentType = { _, _ -> "session" }) { _, session ->
                NimboSurface(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(session.startedAt, style = NimboBodyStyle.copy(color = NimboPalette.Text, fontWeight = FontWeight.Medium))
                        BasicText(session.duration, style = NimboBodyStyle.copy(fontSize = 12.sp))
                        MetricPair("↓ Скачано", formatTraffic(session.download), "↑ Отдано", formatTraffic(session.upload), compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPair(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String, compact: Boolean = false) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (260 * fontScale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatValue(firstLabel, firstValue, compact)
                StatValue(secondLabel, secondValue, compact)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { StatValue(firstLabel, firstValue, compact) }
                Box(Modifier.weight(1f)) { StatValue(secondLabel, secondValue, compact) }
            }
        }
    }
}

@Composable
private fun StatValue(label: String, value: String, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = NimboBodyStyle.copy(fontSize = 12.sp))
        BasicText(value, style = NimboSectionTitleStyle.copy(fontSize = if (compact) 17.sp else 24.sp))
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (280 * fontScale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(label, style = NimboBodyStyle)
                BasicText(value, style = NimboBodyStyle.copy(color = NimboPalette.Text))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                BasicText(label, Modifier.weight(1f), style = NimboBodyStyle)
                BasicText(value, Modifier.weight(1f), style = NimboBodyStyle.copy(color = NimboPalette.Text))
            }
        }
    }
}

internal fun formatTraffic(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    val rounded = if (value >= 100.0 || unit == 0) {
        value.toLong().toString()
    } else {
        val scaled = (value * 10).toLong()
        "${scaled / 10}.${scaled % 10}"
    }
    return "$rounded ${units[unit]}"
}


