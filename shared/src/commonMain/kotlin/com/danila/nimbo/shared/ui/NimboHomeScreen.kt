package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboHomeScreen(
    state: NimboUiState,
    actions: NimboUiActions,
    onOpenProfiles: () -> Unit
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(state.activeProfileName) { mutableStateOf(false) }
    NimboPullRefresh(state.profileRefreshing, state.profileCount > 0, actions.onRefreshProfile,
        Modifier.fillMaxSize()) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().nimboScreenPadding(),
        contentPadding = PaddingValues(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { HomeHeader(state, actions) }
        item { NimboConnectionPanel(state, actions) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Мои подписки", Modifier.weight(1f), style = NimboSectionTitleStyle)
                BasicText("Все профили ↗", Modifier.heightIn(min = 44.dp).nimboClickable(onClick = onOpenProfiles).padding(vertical = 12.dp), style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
        }
        if (state.profileCount == 0) {
            item { NimboAddProfileCard(actions) }
        } else {
            item { NimboSubscriptionHeader(state, actions, expanded) { expanded = !expanded } }
            if (expanded) {
                item { AutoFastestCard(state.servers, state.pingInProgress, actions.onConnectFastest,
                    autoSelected = state.activeServerId == "nimbo:auto") }
                items(count = state.servers.size, key = { "home-server-${state.servers[it].id}" }) { index ->
                    val server = state.servers[index]
                    ProfileServerCard(server, server.id in state.favoriteServerIds,
                        actions.onSelectServer, actions.onToggleFavorite, actions.onPingServer)
                }
            }
        }
        item { HomeMonitoring(state) }
    }
    }
}

@Composable
private fun HomeHeader(state: NimboUiState, actions: NimboUiActions) {
    val dark = LocalNimboDark.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val controls: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, NimboPalette.Border, CircleShape)
                .background(NimboPalette.Surface).semantics { contentDescription = "Переключить тему" }
                .nimboClickable { actions.onSetAppearance("themeMode", if (dark) "light" else "dark") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DarkMode, null, Modifier.size(20.dp), tint = NimboPalette.Text)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, NimboPalette.Border, CircleShape)
                .background(NimboPalette.Surface).semantics { contentDescription = "Добавить" }
                .nimboClickable(onClick = actions.onAddProfile), contentAlignment = Alignment.Center) {
                NimboIcon(NimboIconName.ADD, Modifier.size(22.dp))
            }
        }
    }
    val brand: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText("nimbo", style = NimboTitleStyle.copy(fontWeight = FontWeight.Bold))
            BasicText("v${state.appVersion.removePrefix("v").replace("-beta.", " β")}", style = NimboBodyStyle.copy(fontSize = 11.sp, lineHeight = 14.sp))
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (170 + 25 * fontScale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                brand(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { controls() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                brand(Modifier.weight(1f))
                controls()
            }
        }
    }
}

internal val NimboUiState.showHomeSpeedMonitor: Boolean
    get() = vpnState == "connected" && showSpeedWidget

internal val NimboUiState.showHomeMemoryMonitor: Boolean
    get() = vpnState == "connected" && showMemoryWidget && memoryMb > 0

@Composable
private fun HomeMonitoring(state: NimboUiState) {
    if (!state.showHomeSpeedMonitor && !state.showHomeMemoryMonitor) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.showHomeSpeedMonitor) NetworkSpeedChartCard(state.speedSamples, state.uploadSpeed, state.downloadSpeed)
        if (state.showHomeMemoryMonitor) MemoryUsageCard(state.memoryMb, state.memorySamples)
    }
}

@Composable
private fun ChartMetric(label: String, value: String, modifier: Modifier = Modifier, secondary: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        BasicText(label, style = NimboBodyStyle.copy(fontSize = 12.sp))
        BasicText(value, style = NimboSectionTitleStyle.copy(fontSize = 23.sp, lineHeight = 29.sp,
            fontWeight = FontWeight.Medium, color = if (secondary) NimboPalette.TextSecondary else NimboPalette.Text))
    }
}

@Composable
private fun HomeMetricPair(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (280 * fontScale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartMetric(firstLabel, firstValue)
                ChartMetric(secondLabel, secondValue, secondary = true)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartMetric(firstLabel, firstValue, Modifier.weight(1f))
                ChartMetric(secondLabel, secondValue, Modifier.weight(1f), secondary = true)
            }
        }
    }
}

@Composable
private fun ChartAxis() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText("Начало", style = NimboBodyStyle.copy(fontSize = 10.sp))
        BasicText("Сейчас", style = NimboBodyStyle.copy(fontSize = 10.sp))
    }
}

@Composable
private fun NetworkSpeedChartCard(samples: List<NimboSpeedSample>, uploadSpeed: Long, downloadSpeed: Long) {
    MonitorPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Скорость соединения", style = NimboSectionTitleStyle.copy(fontSize = 15.sp))
                BasicText("Последние измерения", style = NimboBodyStyle.copy(fontSize = 11.sp))
            }
            HomeMetricPair("↓ Загрузка", formatSpeed(downloadSpeed), "↑ Отдача", formatSpeed(uploadSpeed))
            SpeedChartCanvas(samples, Modifier.fillMaxWidth().height(92.dp))
            ChartAxis()
        }
    }
}

@Composable
internal fun SpeedChartCanvas(samples: List<NimboSpeedSample>, modifier: Modifier = Modifier) {
    val colors = LocalNimboColors.current
    Canvas(modifier = modifier) {
        for (fraction in listOf(0f, .5f, 1f)) drawLine(colors.border, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), strokeWidth = 1f)
        if (!hasMeasuredHistory(samples.size)) return@Canvas
        val peak = samples
            .flatMap { listOf(it.upload, it.download) }
            .maxOrNull()
            ?.coerceAtLeast(1L)
            ?.toFloat() ?: 1f

        fun buildPath(selector: (NimboSpeedSample) -> Long): Path {
            val path = Path()
            val count = samples.size.coerceAtLeast(2)
            samples.forEachIndexed { index, sample ->
                val x = if (count <= 1) 0f else size.width * index / (count - 1)
                val y = size.height - (selector(sample).toFloat() / peak).coerceIn(0f, 1f) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        fun buildArea(line: Path): Path = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        val downPath = buildPath { it.download }
        val upPath = buildPath { it.upload }
        drawPath(
            path = buildArea(downPath),
            brush = Brush.verticalGradient(
                listOf(colors.accent.copy(alpha = 0.26f), Color.Transparent)
            )
        )
        drawPath(
            path = downPath,
            color = colors.accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = buildArea(upPath),
            brush = Brush.verticalGradient(
                listOf(colors.text.copy(alpha = 0.08f), Color.Transparent)
            )
        )
        drawPath(
            path = upPath,
            color = colors.text.copy(alpha = .45f),
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun MemoryUsageCard(memoryMb: Int, samples: List<Int>) {
    val chartColor = NimboPalette.Accent
    val grid = NimboPalette.Border
    MonitorPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText("Память приложения", style = NimboSectionTitleStyle.copy(fontSize = 15.sp))
            HomeMetricPair("Сейчас", "$memoryMb МБ", "Пик на графике", "${maxOf(memoryMb, samples.maxOrNull() ?: memoryMb)} МБ")
            Canvas(Modifier.fillMaxWidth().height(76.dp)) {
                for (fraction in listOf(0f, .5f, 1f)) drawLine(grid, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), strokeWidth = 1f)
                if (!hasMeasuredHistory(samples.size)) return@Canvas
                val peak = ((samples.maxOrNull() ?: 1) * 1.35f).coerceAtLeast(1f)
                val count = samples.size.coerceAtLeast(2)
                val path = Path()
                var previousY = 0f
                samples.forEachIndexed { index, value ->
                    val x = size.width * index / (count - 1)
                    val y = size.height - (value / peak).coerceIn(0f, 1f) * size.height
                    if (index == 0) path.moveTo(x, y) else { path.lineTo(x, previousY); path.lineTo(x, y) }
                    previousY = y
                }
                drawPath(path, chartColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            ChartAxis()
        }
    }
}

@Composable
private fun MonitorPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NimboSurface(modifier = modifier, cornerRadius = 16.dp,
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { content() }
}

private fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/с"

private fun formatBytes(bytes: Long): String {
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
