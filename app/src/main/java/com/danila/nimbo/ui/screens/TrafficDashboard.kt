package com.danila.nimbo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.TrafficMeasurementScope
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import java.util.Locale

internal fun trafficBytes(bytes: Double): String = when {
    bytes >= 1024.0 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024.0 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "${bytes.toLong()} B"
}

@Composable
internal fun ColumnScope.TrafficDashboard(preferences: PreferencesManager) {
    val colors = LocalNebulaColors.current
    val connected = VpnManager.state.value == VpnState.CONNECTED
    val telemetry = VpnManager.nativeTrafficTelemetry.value.takeIf { connected }
    Text(text = when (VpnManager.trafficMeasurementScope.value) {
        TrafficMeasurementScope.MIHOMO -> t("Источник: счётчики ядра Mihomo · текущая сессия", "Source: Mihomo core counters · current session")
        TrafficMeasurementScope.APP_UID -> t("Источник: UID Nimbo в Android; включает служебный трафик, без разделения маршрутов", "Source: Android Nimbo UID; includes control traffic, no route breakdown")
        TrafficMeasurementScope.DEVICE -> t("Источник: весь трафик устройства; включает другие приложения и возможный двойной учёт VPN", "Source: device-wide traffic; includes other apps and possible VPN double counting")
        TrafficMeasurementScope.UNAVAILABLE -> t("Измерения появятся после подключения", "Measurements appear once connected")
    }, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TransferCard(t("Получено", "Downloaded"), Icons.Default.ArrowDownward,
            VpnManager.totalBytesDownloaded.value, VpnManager.downloadSpeed.value, Modifier.weight(1f))
        TransferCard(t("Отправлено", "Uploaded"), Icons.Default.ArrowUpward,
            VpnManager.totalBytesUploaded.value, VpnManager.uploadSpeed.value, Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    val route = telemetry?.route
    val proxyShare = route?.proxyShare
    val description = when {
        route == null -> t("Распределение по маршрутам недоступно", "Route breakdown unavailable")
        proxyShare == null -> t("Пока нет измеренного трафика", "No measured traffic yet")
        else -> t("Через прокси", "Proxy") + " ${String.format(Locale.US, "%.1f%%", proxyShare * 100)}"
    }
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(t("Распределение трафика", "Traffic routes"), color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = description }) {
                    val stroke = Stroke(width = 16.dp.toPx())
                    drawArc(colors.textSecondary.copy(alpha = 0.15f), -90f, 360f, false, style = stroke)
                    if (proxyShare != null) {
                        drawArc(colors.statusConnected, -90f, 360f, false, style = stroke)
                        drawArc(colors.accent, -90f, (proxyShare * 360).toFloat(), false, style = stroke)
                    }
                }
                Text(if (proxyShare == null) "—" else String.format(Locale.US, "%.1f%%", proxyShare * 100),
                    color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall)
            }
            Text(description, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            RouteLegend(t("Прокси", "Proxy"), route?.let { trafficBytes(it.proxyBytes) }, true)
            RouteLegend(t("Напрямую", "Direct"), route?.let { trafficBytes(it.directBytes) }, false)
            if (route == null) Text(t(
                "Ядро не предоставляет измеренные байты маршрутов для этого подключения.",
                "This connection's core does not provide measured route bytes."
            ), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProtocolCard("TCP", telemetry?.tcpConnections, Modifier.weight(1f))
        ProtocolCard("UDP", telemetry?.udpConnections, Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    AdBlockingSettingsCard(preferences)
}

@Composable
private fun TransferCard(title: String, icon: ImageVector, total: Long, speed: Long, modifier: Modifier) {
    val colors = LocalNebulaColors.current
    WindowsFlatPanel(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Text(title, color = colors.textSecondary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 10.dp))
            Text(trafficBytes(total.toDouble()), color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(vertical = 8.dp))
            Text(if (VpnManager.liveSpeedAvailable.value) trafficBytes(speed.toDouble()) + "/s"
                else t("Скорость недоступна", "Speed unavailable"), color = colors.accent,
                style = MaterialTheme.typography.bodyMedium)
            Text(t("За сессию · текущая скорость", "Session total · current speed"),
                color = colors.textSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RouteLegend(title: String, value: String?, proxy: Boolean) {
    val colors = LocalNebulaColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(if (proxy) colors.accent else colors.statusConnected) }
        Text(title, modifier = Modifier.weight(1f).padding(start = 8.dp), color = colors.textSecondary)
        Text(value ?: t("Недоступно", "Unavailable"), color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProtocolCard(protocol: String, count: Long?, modifier: Modifier) {
    val colors = LocalNebulaColors.current
    WindowsFlatPanel(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(protocol, color = colors.accent, style = MaterialTheme.typography.titleMedium)
            Text(count?.toString() ?: "—", color = colors.textPrimary,
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))
            Text(if (count == null) t("Недоступно", "Unavailable") else t("Активные соединения", "Active connections"),
                color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
