package com.danila.nimbo.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TrafficTotalCards(state: NimboUiState, summary: NimboTrafficSummary) {
    val live = state.vpnState == "connected" && state.sessionAvailable != false
    // Keep the two directions adjacent even inside a padded phone card.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { TrafficTotalCard("↓ Получено", summary.download, state.downloadSpeed, live, state.sessionAvailable != false || state.vpnState != "connected", "traffic-download-card") }
        Box(Modifier.weight(1f)) { TrafficTotalCard("↑ Отправлено", summary.upload, state.uploadSpeed, live, state.sessionAvailable != false || state.vpnState != "connected", "traffic-upload-card") }
    }
}

@Composable
private fun TrafficTotalCard(title: String, bytes: Long, speed: Long, live: Boolean, available: Boolean, tag: String) {
    NimboSurface(Modifier.fillMaxWidth().testTag(tag), padding = PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text, fontSize = 13.sp), maxLines = 2)
            BasicText(if (available) formatTraffic(bytes) else "Недоступно", style = NimboSectionTitleStyle.copy(fontSize = if (available) 24.sp else 14.sp), maxLines = 2)
            BasicText(if (live) "${formatTraffic(speed)}/с" else "Нет скорости", style = NimboBodyStyle.copy(fontSize = 12.sp), maxLines = 2)
        }
    }
}

@Composable
private fun ResponsiveMetricCards(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (300 * scale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { first(); second() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
internal fun RouteTrafficCard(state: NimboUiState) {
    val routes = measuredRoutes(state)
    NimboSurface(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("Распределение трафика", style = NimboSectionTitleStyle)
            val scale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < (360 * scale).dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RouteDonut(routes)
                        RouteLegend(routes)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        RouteDonut(routes)
                        Box(Modifier.weight(1f)) { RouteLegend(routes) }
                    }
                }
            }
            BasicText(if (routes == null) "Ядро не предоставляет измерения маршрутов для этой сессии." else "Накопленные байты маршрутов по счётчикам ядра.", style = NimboBodyStyle.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun RouteDonut(routes: NimboRouteTraffic?) {
    val proxyColor = NimboPalette.Accent
    val directColor = NimboPalette.TextSecondary
    val emptyColor = NimboPalette.Border
    val fraction = routes?.proxyFraction
    val center = when {
        routes == null -> "Нет данных"
        fraction == null -> "0 Б"
        else -> "${(fraction * 100).toInt()}% VPN"
    }
    Box(Modifier.size(144.dp).semantics { contentDescription = "Маршруты: $center" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val bounds = Size(size.width - stroke, size.height - stroke)
            drawArc(emptyColor, -90f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
            if (fraction != null) {
                drawArc(directColor, -90f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
                if (fraction > 0f) drawArc(proxyColor, -90f, 360f * fraction, false, Offset(inset, inset), bounds, style = Stroke(stroke))
            }
        }
        BasicText(center, style = NimboBodyStyle.copy(color = NimboPalette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun RouteLegend(routes: NimboRouteTraffic?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RouteLegendEntry("Через VPN", NimboPalette.Accent, routes?.proxyUpload, routes?.proxyDownload)
        RouteLegendEntry("Напрямую", NimboPalette.TextSecondary, routes?.directUpload, routes?.directDownload)
    }
}

@Composable
private fun RouteLegendEntry(title: String, color: Color, upload: Long?, download: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text))
        }
        BasicText(if (upload == null || download == null) "Недоступно" else "↑ ${formatTraffic(upload)} · ↓ ${formatTraffic(download)}", style = NimboBodyStyle.copy(fontSize = 12.sp))
    }
}

@Composable
internal fun ActiveProtocolCards(state: NimboUiState) {
    ResponsiveMetricCards(
        first = { ActiveProtocolCard("TCP", activeConnectionLabel(state, state.tcpConnections)) },
        second = { ActiveProtocolCard("UDP", activeConnectionLabel(state, state.udpConnections)) }
    )
}

@Composable
private fun ActiveProtocolCard(protocol: String, count: String) {
    NimboSurface(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(protocol, style = NimboSectionTitleStyle)
            BasicText(count, style = NimboSectionTitleStyle.copy(fontSize = if (count == "Недоступно") 16.sp else 26.sp))
            BasicText("Активных соединений", style = NimboBodyStyle.copy(fontSize = 12.sp))
        }
    }
}
