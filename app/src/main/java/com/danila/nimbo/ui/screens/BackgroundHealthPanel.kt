package com.danila.nimbo.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.dottedOutline
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.utils.BackgroundHealthChecker
import com.danila.nimbo.utils.BackgroundWorkHistory
import com.danila.nimbo.QuickControlsActivity
import java.text.DateFormat
import java.util.Date

@Composable
fun BackgroundHealthPanel() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = LocalNebulaColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val health = remember(revision, expanded) { BackgroundHealthChecker.inspect(context) }
    val history = remember(revision, expanded) { BackgroundWorkHistory.read(context) }
    fun openSettings(action: String, appDetails: Boolean = false) {
        val intent = Intent(action).apply {
            if (appDetails) data = Uri.parse("package:${context.packageName}")
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"))) }
        }
    }
    val needsAttention = health.notificationsBlocked || health.throttled
    val expandedDescription = if (expanded) t("Развёрнуто", "Expanded") else t("Свёрнуто", "Collapsed")
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "background_health_chevron")
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .semantics { stateDescription = expandedDescription }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackgroundHealthIcon(Icons.Default.BatteryChargingFull, large = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Работа в фоне", "Background activity"), color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(if (needsAttention) t("Проверьте ограничения", "Review restrictions")
                        else t("Состояние и быстрые действия", "Status and quick controls"),
                        color = colors.accent, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ExpandMore, null, tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp).rotate(chevronRotation))
            }
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackgroundHealthStatus(Icons.Default.Notifications, t("Уведомления", "Notifications"),
                        if (health.notificationsBlocked) t("Выключены в Android", "Disabled in Android") else t("Разрешены", "Allowed"))
                    BackgroundHealthStatus(Icons.Default.Schedule, t("Фоновые задачи", "Background tasks"),
                        if (health.throttled) t("Ограничены системой", "Restricted by the system")
                        else t("Сильных ограничений нет", "No severe restrictions detected"))
                    BackgroundHealthStatus(Icons.Default.BatteryStd, t("Батарея", "Battery"),
                        if (health.batteryOptimized) t("Обычная оптимизация Android", "Standard Android optimization")
                        else t("Без оптимизации батареи", "Battery optimization exempt"))
                    if (health.notificationsBlocked) BackgroundHealthAction(Icons.Default.Notifications,
                        t("Настроить уведомления", "Notification settings")) { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }
                    if (health.batteryOptimized || health.throttled) BackgroundHealthAction(Icons.Default.BatteryStd,
                        t("Настройки батареи", "Battery settings")) { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
                    if (health.hibernationEnabled) BackgroundHealthAction(Icons.Default.AdminPanelSettings,
                        t("Автоотзыв разрешений", "Unused-app permissions")) { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                    Text(t("Последние запуски", "Recent runs"), color = colors.textPrimary,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    history.forEach { entry ->
                        val name = when (entry.task) { "updates" -> t("Обновления", "Updates"); "sync" -> t("Синхронизация", "Sync"); else -> t("Подписки", "Subscriptions") }
                        val icon = when (entry.task) { "updates" -> Icons.Default.SystemUpdate; "sync" -> Icons.Default.Sync; else -> Icons.Default.CloudDownload }
                        val time = if (entry.started == 0L) t("Пока нет записей", "No runs recorded") else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.finished.takeIf { it > 0 } ?: entry.started))
                        val outcome = when (entry.outcome) { "cancelled" -> t("Отменено", "Cancelled"); "failed" -> t("Ошибка", "Failed"); "running" -> t("Завершение не записано", "Completion not recorded"); "finished" -> t("Завершено или отложено", "Completed or deferred"); else -> "" }
                        BackgroundHealthStatus(icon, name, listOf(time, outcome).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                    Text(t("Время запуска выбирает Android. При активном VPN обновление подписок откладывается. Меняйте оптимизацию батареи только при пропусках задач.",
                        "Android schedules these tasks. Subscription refresh is deferred while VPN is active. Change battery optimization only if tasks are missed."),
                        color = colors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    BackgroundHealthAction(Icons.Default.Refresh, t("Проверить снова", "Check again")) { revision++ }
                    BackgroundHealthAction(Icons.Default.Tune, t("Быстрое управление", "Quick controls")) {
                        context.startActivity(Intent(context, QuickControlsActivity::class.java))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundHealthIcon(icon: ImageVector, large: Boolean = false) {
    val colors = LocalNebulaColors.current
    val style = LocalElementStyleMode.current
    val radius = when (style) {
        ElementStyleMode.MANGA -> 3.dp
        ElementStyleMode.NOTHING_DOTS -> 8.dp
        else -> 13.dp
    }
    Box(Modifier.size(if (large) 42.dp else 32.dp).clip(RoundedCornerShape(radius))
        .background(if (large) colors.accent.copy(alpha = 0.16f) else colors.softFill),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(if (large) 23.dp else 18.dp))
    }
}

@Composable
private fun BackgroundHealthStatus(icon: ImageVector, title: String, detail: String) {
    val colors = LocalNebulaColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackgroundHealthIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BackgroundHealthAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    val style = LocalElementStyleMode.current
    val radius = when (style) {
        ElementStyleMode.MANGA -> 3.dp
        ElementStyleMode.NOTHING_DOTS -> 8.dp
        else -> 12.dp
    }
    val shape = RoundedCornerShape(radius)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(colors.controlFill)
        .then(if (style == ElementStyleMode.NOTHING_DOTS) {
            Modifier.dottedOutline(colors.accent, cornerRadius = radius, alpha = 0.5f)
        } else Modifier.border(if (style == ElementStyleMode.MANGA) 1.5.dp else 1.dp, colors.panelBorder, shape))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), color = colors.accent,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}
