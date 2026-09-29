@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.ui.components.SubscriptionBrandLogo
import com.danila.nimbo.ui.components.NimboToolActions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.NimboAction
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.formatBytes
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubscriptionQuotaSummary(profile: SubscriptionProfile) {
    val colors = LocalNebulaColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.SignalCellularAlt, null, Modifier.size(14.dp), tint = colors.textSecondary)
                Text(if (profile.totalTraffic > 0)
                    formatBytes((profile.totalTraffic - profile.usedTraffic).coerceAtLeast(0)) + t(" осталось", " remaining")
                    else t("Без лимита", "Unlimited"), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            if (profile.expireTime > 0) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(14.dp), tint = colors.textSecondary)
                Text(t("До ", "Until ") + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.expireTime * 1000)),
                    style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
        if (profile.totalTraffic > 0) LinearProgressIndicator(
            progress = { (profile.usedTraffic.toFloat() / profile.totalTraffic).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = colors.accent, trackColor = colors.controlFill)
        else HorizontalDivider(color = colors.divider)
    }
}

@Composable
internal fun SubscriptionDetailsDialog(profile: SubscriptionProfile, onDismiss: () -> Unit,
    onSupport: () -> Unit, onSite: () -> Unit, serverCountOverride: Int? = null) {
    val colors = LocalNebulaColors.current
    val preferences = LocalPreferencesManager.current
    val lastUpdate = preferences.getLastSubscriptionUpdateTime(profile.mihomoSourceUrl ?: profile.url)
    val updated = if (lastUpdate > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastUpdate)) else t("Нет данных", "No data")
    NebulaMorphicDialog(onDismissRequest = onDismiss, title = t("О подписке", "About subscription"),
        confirmButtonText = null, cancelButtonText = null, onConfirm = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (preferences.showSubscriptionLogo && !profile.brandLogo.isNullOrBlank()) {
                SubscriptionBrandLogo(profile.brandLogo!!, profile.brandLogoCache, size = 51.dp)
            } else Box(Modifier.size(51.dp).background(colors.textPrimary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(profile.displayName.take(2).uppercase(), color = colors.background, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName.removeSuffix(" · Mihomo"), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text(profile.originalName?.takeIf { it.isNotBlank() && it != profile.displayName }
                    ?: t("Подписка", "Subscription"), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        NimboToolActions(minCellDp = 120f, maxColumns = 2) {
            ProviderFact(Icons.Default.SignalCellularAlt,
                if (profile.totalTraffic > 0) formatBytes((profile.totalTraffic - profile.usedTraffic).coerceAtLeast(0)) +
                    t(" из ", " of ") + formatBytes(profile.totalTraffic) else t("Без лимита", "Unlimited"),
                t("Доступный трафик", "Available traffic"), Modifier.weight(1f))
            ProviderFact(Icons.Default.CalendarMonth,
                if (profile.expireTime > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.expireTime * 1000))
                else t("Не указан", "Not specified"), t("Срок действия", "Expiry"), Modifier.weight(1f))
            ProviderFact(Icons.Default.Dns, (serverCountOverride ?: profile.servers.size).toString(),
                t("Серверы", "Servers"), Modifier.weight(1f))
            ProviderFact(Icons.Default.Refresh, updated, t("Последнее обновление", "Last updated"), Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.divider)
        Text(t("Устройства: ", "Devices: ") + "${profile.deviceCount} / " +
            if (profile.deviceLimit > 0) profile.deviceLimit.toString() else "∞",
            style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        profile.username?.takeIf(String::isNotBlank)?.let {
            Text(t("Аккаунт: ", "Account: ") + it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Text(profile.announce?.trim()?.takeIf(String::isNotEmpty) ?: t("Описание отсутствует", "No description"),
            style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.divider)
        NimboToolActions {
            if (!profile.supportUrl.isNullOrBlank()) NimboAction(Icons.Default.SupportAgent, t("Поддержка", "Support"),
                onSupport, Modifier.weight(1f))
            if (!profile.websiteUrl.isNullOrBlank()) NimboAction(Icons.Default.Public, t("Сайт провайдера", "Provider website"),
                onSite, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProviderFact(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String,
    modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = colors.controlFill) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = colors.textSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}
