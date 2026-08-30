package com.danila.nimbo.shared.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Статистика соединений.
 *
 * На месте бывшего экрана приложений: выбор программ iOS не даёт, а вот
 * показать, сколько прошло через туннель, — вполне. Цифры настоящие: их
 * приносит счётчик utun-интерфейса, тот же, что кормит виджеты на главной.
 */
@Composable
internal fun NimboStatsScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText("Статистика", style = NimboTitleStyle)

        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(
                    if (state.vpnState == "connected") "Текущая сессия" else "Последняя сессия",
                    style = NimboSectionTitleStyle
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatValue("Скачано", formatTraffic(state.downloadTotal), NimboPalette.Accent, Modifier.weight(1f))
                    StatValue("Отдано", formatTraffic(state.uploadTotal), NimboPalette.Green, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatValue(
                        "Приём",
                        formatTraffic(state.downloadSpeed) + "/с",
                        NimboPalette.Accent,
                        Modifier.weight(1f)
                    )
                    StatValue(
                        "Передача",
                        formatTraffic(state.uploadSpeed) + "/с",
                        NimboPalette.Green,
                        Modifier.weight(1f)
                    )
                }
                if (state.speedSamples.isNotEmpty()) {
                    SpeedHistoryChart(state.speedSamples)
                }
            }
        }

        BasicText("Сессии", style = NimboSectionTitleStyle)
        if (state.sessions.isEmpty()) {
            NimboSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                padding = PaddingValues(16.dp)
            ) {
                BasicText(
                    "Пока пусто. Сессия записывается после отключения.",
                    style = NimboBodyStyle
                )
            }
        } else {
            NimboSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column {
                    state.sessions.forEachIndexed { index, session ->
                        SessionRow(session)
                        if (index != state.sessions.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                        }
                    }
                }
            }
        }

        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Сервер", style = NimboSectionTitleStyle)
                StatLine("Выбран", withoutFlagEmoji(state.activeServerName))
                StatLine("Задержка", state.servers.firstOrNull { it.selected }?.pingLabel ?: "— ms")
                StatLine("Всего серверов", state.serverCount.toString())
                if (state.profileTrafficLabel.isNotBlank()) {
                    StatLine("Трафик подписки", state.profileTrafficLabel)
                }
            }
        }
    }
}

@Composable
private fun StatValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(NimboPalette.Control, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = NimboPalette.TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = NimboBodyStyle)
        Spacer(Modifier.weight(1f))
        BasicText(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = NimboPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun SessionRow(session: NimboSessionUi) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                session.startedAt,
                maxLines = 1,
                style = TextStyle(color = NimboPalette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
            BasicText(
                session.duration,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(color = NimboPalette.TextSecondary, fontSize = 12.sp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            BasicText(
                "↓ " + formatTraffic(session.download),
                style = TextStyle(color = NimboPalette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
            BasicText(
                "↑ " + formatTraffic(session.upload),
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(color = NimboPalette.Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun SpeedHistoryChart(samples: List<NimboSpeedSample>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        val peak = samples
            .flatMap { listOf(it.upload, it.download) }
            .maxOrNull()
            ?.coerceAtLeast(1L)
            ?.toFloat() ?: 1f
        val count = samples.size.coerceAtLeast(2)
        samples.forEachIndexed { index, sample ->
            val x = size.width * index / (count - 1)
            val down = (sample.download.toFloat() / peak).coerceIn(0f, 1f) * size.height
            val up = (sample.upload.toFloat() / peak).coerceIn(0f, 1f) * size.height
            drawLine(
                color = NimboPalette.Accent.copy(alpha = 0.75f),
                start = androidx.compose.ui.geometry.Offset(x, size.height),
                end = androidx.compose.ui.geometry.Offset(x, size.height - down),
                strokeWidth = 2f
            )
            drawLine(
                color = NimboPalette.Green.copy(alpha = 0.55f),
                start = androidx.compose.ui.geometry.Offset(x, size.height),
                end = androidx.compose.ui.geometry.Offset(x, size.height - up),
                strokeWidth = 1.2f
            )
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
