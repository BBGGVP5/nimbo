package com.danila.nimbo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danila.nimbo.network.ServiceAvailability
import com.danila.nimbo.network.ServiceCheckResult
import com.danila.nimbo.network.ServiceStatusCatalog
import com.danila.nimbo.network.ServiceStatusProbe
import com.danila.nimbo.network.ServiceStatusStore
import com.danila.nimbo.network.ServiceTarget
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.NimboPanel
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusGreen = Color(0xFF34C759)
private val StatusRed = Color(0xFFE5484D)
private val StatusOrange = Color(0xFFF5A524)

private fun availabilityColor(value: ServiceAvailability): Color = when (value) {
    ServiceAvailability.AVAILABLE -> StatusGreen
    ServiceAvailability.REGION_BLOCKED -> StatusOrange
    ServiceAvailability.ERROR -> StatusRed
}

@Composable
private fun availabilityLabel(value: ServiceAvailability): String = when (value) {
    ServiceAvailability.AVAILABLE -> t("Доступен", "Available")
    ServiceAvailability.REGION_BLOCKED -> t("Регион не поддерживается", "Region not supported")
    ServiceAvailability.ERROR -> t("Ошибка проверки", "Check failed")
}

private fun serviceIcon(id: String): ImageVector? = when (id) {
    "github" -> Icons.Default.Code
    "youtube" -> Icons.Default.SmartDisplay
    "chatgpt" -> Icons.Default.Hub
    "claude" -> Icons.Default.Flare
    "gemini" -> Icons.Default.AutoAwesome
    "spotify" -> Icons.Default.GraphicEq
    "tiktok" -> Icons.Default.MusicNote
    "discord" -> Icons.Default.SportsEsports
    "bilibili" -> Icons.Default.LiveTv
    "telegram" -> Icons.AutoMirrored.Filled.Send
    "instagram" -> Icons.Default.PhotoCamera
    else -> null
}

private fun serviceMonogram(id: String): String = when (id) {
    "google" -> "G"
    "netflix" -> "N"
    "disney" -> "D+"
    "prime" -> "pv"
    else -> "•"
}

internal fun countryFlagEmoji(code: String?): String? {
    val normalized = code?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } } ?: return null
    return normalized.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/** Короткая строка на главной: открывает лист «Состояние сервисов». */
@Composable
internal fun ServiceStatusEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    val allResults by ServiceStatusStore.results.collectAsStateWithLifecycle()
    val owner by ServiceStatusStore.owner.collectAsStateWithLifecycle()
    val running by ServiceStatusStore.running.collectAsStateWithLifecycle()
    // Результаты прошлого сервера не выдаём за текущие.
    val results = if (owner == VpnManager.connectedServer.value?.pingMeasurementKey()) allResults else emptyMap()
    val available = results.values.count { it.availability == ServiceAvailability.AVAILABLE }
    val summary = when {
        running.isNotEmpty() -> t("Проверяем…", "Checking…")
        results.isEmpty() -> t("Проверить Google, YouTube, ChatGPT и другие", "Check Google, YouTube, ChatGPT and more")
        else -> t("Доступно $available из ${results.size}", "$available of ${results.size} available")
    }
    NimboPanel(modifier) {
        Row(Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.softFill),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Public, null, Modifier.size(20.dp), tint = colors.textPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Состояние сервисов", "Service status"), style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary)
                Crossfade(summary, animationSpec = tween(200), label = "service-status-summary") { text ->
                    Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.textTertiary)
        }
    }
}

/**
 * Остаток прокрутки и инерции списка не отдаём листу вверх: иначе на дне списка
 * ModalBottomSheet пытается «доехать» сам и прокрутка пружинит дважды. Тянуть
 * лист вниз за верх списка (закрытие жестом) по-прежнему можно.
 */
private val KeepFlingInsideList = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) available.copy(x = 0f) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available.copy(x = 0f) else Velocity.Zero
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceStatusSheet(onDismiss: () -> Unit) {
    val colors = LocalNebulaColors.current
    val context = LocalContext.current
    val allResults by ServiceStatusStore.results.collectAsStateWithLifecycle()
    val owner by ServiceStatusStore.owner.collectAsStateWithLifecycle()
    val running by ServiceStatusStore.running.collectAsStateWithLifecycle()
    var disabled by remember { mutableStateOf(ServiceStatusStore.disabledIds(context)) }
    var editing by remember { mutableStateOf(false) }
    val vpnState = VpnManager.state.value
    val connectedKey = VpnManager.connectedServer.value?.pingMeasurementKey()
    // Перечитывается при смене состояния VPN: сессия появляется после старта ядра.
    val session = remember(vpnState, connectedKey) { ServiceStatusProbe.session() }
    val visible = ServiceStatusCatalog.targets.filter { it.id !in disabled }
    val results = if (owner == connectedKey) allResults else emptyMap()

    LaunchedEffect(session) {
        ServiceStatusStore.resetIfRouteChanged(session?.ownerKey)
        // Открыли лист — проверяем сразу, если результатов нет или они старше пяти минут.
        if (session != null && ServiceStatusStore.needsRefresh(visible)) ServiceStatusStore.launchCheck(visible)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SheetRoundButton(Icons.Default.Close, t("Закрыть", "Close"), onClick = onDismiss)
                Crossfade(editing, Modifier.weight(1f), animationSpec = tween(180), label = "service-status-title") { edit ->
                    Text(if (edit) t("Сервисы для проверки", "Services to check") else t("Состояние сервисов", "Service status"),
                        Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                SheetRoundButton(Icons.Default.SignalCellularAlt, t("Проверить все", "Check all"),
                    enabled = !editing && session != null && running.isEmpty()) {
                    ServiceStatusStore.launchCheck(visible)
                }
                Spacer(Modifier.width(6.dp))
                SheetRoundButton(if (editing) Icons.Default.Check else Icons.Default.Tune,
                    if (editing) t("Готово", "Done") else t("Настроить список", "Edit list")) { editing = !editing }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(session == null && !editing, enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()) {
                Text(unavailableReason(vpnState), style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp))
            }
            val rows = if (editing) ServiceStatusCatalog.targets else visible
            val shape = RoundedCornerShape(15.dp)
            // weight(fill = false): список занимает ровно остаток высоты листа и прокручивается
            // внутри него, а не обрезается и не растягивает лист.
            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth().clip(shape).background(colors.panelFill)
                    .border(1.dp, colors.panelBorder, shape).nestedScroll(KeepFlingInsideList),
                overscrollEffect = null
            ) {
                itemsIndexed(rows, key = { _, target -> target.id }, contentType = { _, _ -> if (editing) "toggle" else "status" }) { index, target ->
                    Column(Modifier.animateItem()) {
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 14.dp), color = colors.divider)
                        if (editing) {
                            ServiceToggleRow(target, target.id !in disabled) { enabled ->
                                disabled = if (enabled) disabled - target.id else disabled + target.id
                                ServiceStatusStore.setDisabledIds(context, disabled)
                            }
                        } else {
                            ServiceStatusRow(target, results[target.id], target.id in running,
                                canCheck = session != null) { ServiceStatusStore.launchCheck(listOf(target)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun unavailableReason(state: VpnState): String = when {
    state != VpnState.CONNECTED -> t("Подключите VPN — сервисы проверяются через активный туннель.",
        "Connect VPN — services are checked through the active tunnel.")
    else -> t("Проверка доступна только для подключений через ядро Xray (Mihomo, AmneziaWG и NaiveProxy пока не поддерживаются).",
        "Only available for Xray connections (Mihomo, AmneziaWG and NaiveProxy are not supported yet).")
}

@Composable
private fun SheetRoundButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.softFill)) {
        Icon(icon, description, tint = if (enabled) colors.textPrimary else colors.textTertiary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ServiceGlyph(target: ServiceTarget, dot: Color?) {
    val colors = LocalNebulaColors.current
    Box(Modifier.size(44.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(colors.softFill),
            contentAlignment = Alignment.Center) {
            val icon = serviceIcon(target.id)
            if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = colors.textPrimary)
            else Text(serviceMonogram(target.id), color = colors.textPrimary, fontWeight = FontWeight.Black,
                fontSize = if (serviceMonogram(target.id).length > 1) 14.sp else 20.sp)
        }
        if (dot != null) {
            Box(Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape).background(dot)
                .border(2.dp, colors.panelFill, CircleShape))
        }
    }
}

@Composable
private fun StatusBadge(availability: ServiceAvailability, modifier: Modifier = Modifier) {
    val color = availabilityColor(availability)
    val dark = LocalNebulaColors.current.surface.luminance() < 0.4f
    Text(availabilityLabel(availability), modifier.clip(RoundedCornerShape(50))
        .background(color.copy(alpha = if (dark) 0.22f else 0.16f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (dark) color else color.copy(red = color.red * 0.7f, green = color.green * 0.7f, blue = color.blue * 0.7f),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ServiceStatusRow(
    target: ServiceTarget,
    result: ServiceCheckResult?,
    checking: Boolean,
    canCheck: Boolean,
    onRefresh: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val statusColor by animateColorAsState(result?.let { availabilityColor(it.availability) } ?: Color.Transparent,
        tween(250), label = "service-status-dot")
    Row(Modifier.fillMaxWidth().heightIn(min = ServiceRowHeight).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ServiceGlyph(target, statusColor.takeIf { result != null })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(target.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // Бейдж проявляется, а не выскакивает: смена статуса не дёргает строку.
                AnimatedContent(result?.availability, Modifier.weight(1f, fill = false),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "service-status-badge") { availability ->
                    if (availability != null) StatusBadge(availability, Modifier.padding(start = 8.dp))
                }
            }
            val ipLine = when {
                result == null -> if (checking) t("Проверяем…", "Checking…") else t("Не проверено", "Not checked")
                result.exitIp != null -> listOfNotNull(countryFlagEmoji(result.countryCode), result.exitIp).joinToString(" ")
                result.detail != null -> "— · ${result.detail}"
                else -> "—"
            }
            Crossfade(ipLine, animationSpec = tween(200), label = "service-status-ip") { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium,
                    color = if (result?.exitIp != null) statusColor else colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Crossfade(result, animationSpec = tween(200), label = "service-status-latency") { shown ->
            Column(horizontalAlignment = Alignment.End) {
                if (shown != null && shown.latencyMs >= 0) {
                    Text("${shown.latencyMs} ms", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
                if (shown != null) {
                    Text(timeFormat.format(Date(shown.checkedAtMs)), style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary)
                }
            }
        }
        Crossfade(checking, Modifier.size(44.dp), animationSpec = tween(180), label = "service-status-refresh") { busy ->
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    ExpressiveCircularLoader(Modifier.size(20.dp), color = colors.textSecondary, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh, enabled = canCheck) {
                        Icon(Icons.Default.Refresh, t("Проверить ${target.name}", "Check ${target.name}"),
                            tint = if (canCheck) colors.textSecondary else colors.textTertiary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Одна высота у строк статуса и настройки: переключение режима не прыгает. */
private val ServiceRowHeight = 64.dp

@Composable
private fun ServiceToggleRow(target: ServiceTarget, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalNebulaColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = ServiceRowHeight).clickable(role = Role.Switch) { onChange(!enabled) }
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ServiceGlyph(target, null)
        Spacer(Modifier.width(12.dp))
        Text(target.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Switch(checked = enabled, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
    }
}
