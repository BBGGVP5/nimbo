package com.danila.nimbo.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.NetworkPreset
import com.danila.nimbo.model.Server
import com.danila.nimbo.model.SmartServerGroup
import com.danila.nimbo.model.SmartServerStrategy
import com.danila.nimbo.model.TrafficBudget
import com.danila.nimbo.model.TrafficBudgetAction
import com.danila.nimbo.model.TrafficBudgetPeriod
import com.danila.nimbo.network.NetworkContextSnapshot
import com.danila.nimbo.network.NetworkEventJournal
import com.danila.nimbo.network.SmartServerGroupStore
import com.danila.nimbo.network.TrafficBudgetStore
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.NetworkProfileManager
import com.danila.nimbo.utils.PreferencesManager
import java.text.DateFormat
import java.util.Date

/**
 * Advanced network controls are deliberately part of the existing Connections
 * page. This keeps tunnel status, active rules and automation in one place.
 */
@Composable
fun NetworkIntelligenceScreen(
    preferencesManager: PreferencesManager,
    servers: List<Server>,
    onOpenFirewall: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val en = remember(preferencesManager.appLanguage) { preferencesManager.appLanguage == "en" }
    fun loc(ru: String, english: String) = if (en) english else ru

    var refreshKey by remember { mutableStateOf(0) }
    var contextSnapshot by remember(refreshKey) {
        mutableStateOf(NetworkProfileManager.captureNetworkContext(context))
    }
    var presets by remember(refreshKey) { mutableStateOf(NetworkProfileManager.getPresets(context)) }
    var activePresetId by remember(refreshKey) { mutableStateOf(NetworkProfileManager.getActivePresetId(context)) }
    var autoApply by remember(refreshKey) { mutableStateOf(NetworkProfileManager.isAutoApplyEnabled(context)) }
    var groups by remember(refreshKey) { mutableStateOf(SmartServerGroupStore.groups(context)) }
    var budgets by remember(refreshKey) { mutableStateOf(TrafficBudgetStore.budgets(context)) }
    var usage by remember(refreshKey) { mutableStateOf(TrafficBudgetStore.usage(context)) }
    var events by remember(refreshKey) { mutableStateOf(NetworkEventJournal.list(context).asReversed()) }
    var profileName by remember { mutableStateOf("") }
    var budgetGb by remember { mutableStateOf("10") }

    fun reload() {
        refreshKey++
        contextSnapshot = NetworkProfileManager.captureNetworkContext(context)
        presets = NetworkProfileManager.getPresets(context)
        activePresetId = NetworkProfileManager.getActivePresetId(context)
        autoApply = NetworkProfileManager.isAutoApplyEnabled(context)
        groups = SmartServerGroupStore.groups(context)
        budgets = TrafficBudgetStore.budgets(context)
        usage = TrafficBudgetStore.usage(context)
        events = NetworkEventJournal.list(context).asReversed()
    }

    NimboSubPageScaffold(
        title = loc("Соединения", "Connections"),
        subtitle = loc(
            "Туннель, правила и автоматизация сети",
            "Tunnel, rules and network automation"
        ),
        onBack = onBack
    ) {
        ConnectionsSettingsSection(
            preferencesManager = preferencesManager,
            onOpenFirewall = onOpenFirewall
        )

        SubPageSectionHeader(loc("Автоматизация сети", "Network automation"), Icons.Default.Route)
        NetworkFeatureCard(Icons.Default.NetworkWifi, loc("Текущая сеть", "Current network")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        networkSummary(contextSnapshot, en),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        validationSummary(contextSnapshot, en),
                        color = if (contextSnapshot.validated) {
                            colors.accent
                        } else {
                            colors.statusError
                        }
                    )
                }
                IconButton(onClick = ::reload) {
                    Icon(
                        Icons.Default.Refresh,
                        loc("Обновить", "Refresh"),
                        tint = colors.textPrimary
                    )
                }
            }
            if (contextSnapshot.captivePortal) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openCaptivePortal(context) },
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.WifiFind, null)
                    Spacer(Modifier.size(8.dp))
                    Text(loc("Открыть страницу входа", "Open sign-in page"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        NetworkFeatureCard(Icons.Default.Route, loc("Профили сети", "Network profiles")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        loc("Выбирать профиль автоматически", "Select profile automatically"),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    SecondaryText(
                        loc(
                            "По Wi‑Fi, оператору, роумингу, зарядке и типу сети",
                            "By Wi-Fi, carrier, roaming, charging and transport"
                        )
                    )
                }
                Switch(
                    checked = autoApply,
                    onCheckedChange = {
                        autoApply = it
                        NetworkProfileManager.setAutoApplyEnabled(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.softFill,
                        uncheckedBorderColor = colors.textTertiary
                    )
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = colors.divider
            )
            presets.forEach { preset ->
                PresetRow(preset, preset.id == activePresetId) {
                    NetworkProfileManager.setActivePresetId(context, preset.id)
                    activePresetId = preset.id
                }
            }
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(loc("Название профиля для этой сети", "Profile name for this network")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.controlFill,
                    unfocusedContainerColor = colors.controlFill,
                    focusedBorderColor = colors.accent.copy(alpha = 0.62f),
                    unfocusedBorderColor = colors.textPrimary.copy(alpha = 0.22f),
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.accent,
                    unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.accent
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val base = presets.firstOrNull { it.id == activePresetId } ?: presets.first()
                    val exact = NetworkProfileManager.createCustomPresetFrom(
                        context,
                        base.copy(
                            matchSsid = contextSnapshot.ssid,
                            matchCarrierName = contextSnapshot.carrierName.takeIf { contextSnapshot.ssid == null },
                            matchTransport = contextSnapshot.transport.name,
                            matchMetered = contextSnapshot.metered,
                            matchRoaming = contextSnapshot.roaming,
                            matchCaptivePortal = contextSnapshot.captivePortal,
                            priority = 10
                        ),
                        profileName.ifBlank {
                            contextSnapshot.ssid
                                ?: contextSnapshot.carrierName
                                ?: loc("Моя сеть", "My network")
                        }
                    )
                    NetworkProfileManager.setActivePresetId(context, exact.id)
                    profileName = ""
                    reload()
                },
                enabled = presets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.28f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary,
                    disabledContentColor = colors.textTertiary
                )
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.size(8.dp))
                Text(loc("Запомнить текущую сеть", "Remember current network"))
            }
        }
        Spacer(Modifier.height(12.dp))

        NetworkFeatureCard(Icons.Default.Speed, loc("Умная группа серверов", "Smart server group")) {
            SecondaryText(
                loc(
                    "Выбирает узел по пингу и стабильности, не переключаясь из-за небольшой разницы.",
                    "Chooses a node by latency and reliability without switching for tiny differences."
                )
            )
            groups.forEach { group ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        SecondaryText("${group.serverKeys.size} · ${group.strategy.name.lowercase()}")
                    }
                    IconButton(onClick = { SmartServerGroupStore.deleteGroup(context, group.id); reload() }) {
                        Icon(
                            Icons.Default.Delete,
                            loc("Удалить", "Delete"),
                            tint = colors.statusError
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    val uniqueServers = servers.distinctBy(Server::selectionKey)
                    val group = SmartServerGroup(
                        id = "smart_${System.currentTimeMillis()}",
                        name = loc("Автовыбор", "Auto select"),
                        serverKeys = uniqueServers.map(Server::selectionKey),
                        strategy = SmartServerStrategy.BALANCED
                    )
                    SmartServerGroupStore.saveGroup(context, group)
                    presets.firstOrNull { it.id == activePresetId }?.let {
                        NetworkProfileManager.savePreset(context, it.copy(smartGroupId = group.id))
                    }
                    reload()
                },
                enabled = servers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.28f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary,
                    disabledContentColor = colors.textTertiary
                )
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.size(8.dp))
                Text(loc("Создать из доступных серверов", "Create from available servers"))
            }
        }
        Spacer(Modifier.height(12.dp))

        NetworkFeatureCard(Icons.Default.Speed, loc("Лимиты трафика", "Traffic budgets")) {
            budgets.forEach { budget ->
                val used = usage[budget.id]?.usedBytes ?: 0L
                val percent = if (budget.limitBytes > 0) {
                    (used * 100 / budget.limitBytes).coerceAtMost(100)
                } else {
                    100
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(budget.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        SecondaryText(
                            "${formatBudgetBytes(used)} / ${formatBudgetBytes(budget.limitBytes)} · $percent%"
                        )
                    }
                    IconButton(onClick = { TrafficBudgetStore.deleteBudget(context, budget.id); reload() }) {
                        Icon(
                            Icons.Default.Delete,
                            loc("Удалить", "Delete"),
                            tint = colors.statusError
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(colors.softFill, RoundedCornerShape(5.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(percent / 100f)
                            .height(5.dp)
                            .background(colors.accent, RoundedCornerShape(5.dp))
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = budgetGb,
                onValueChange = { budgetGb = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(loc("Лимит в ГБ на месяц", "Monthly limit in GB")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.controlFill,
                    unfocusedContainerColor = colors.controlFill,
                    focusedBorderColor = colors.accent.copy(alpha = 0.62f),
                    unfocusedBorderColor = colors.textPrimary.copy(alpha = 0.22f),
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.accent,
                    unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.accent
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val gigabytes = budgetGb.toDoubleOrNull()?.coerceAtLeast(0.1) ?: return@OutlinedButton
                    TrafficBudgetStore.saveBudget(
                        context,
                        TrafficBudget(
                            id = "device_${System.currentTimeMillis()}",
                            name = loc("Месячный лимит", "Monthly budget"),
                            limitBytes = (gigabytes * 1024 * 1024 * 1024).toLong(),
                            period = TrafficBudgetPeriod.MONTH,
                            action = TrafficBudgetAction.NOTIFY
                        )
                    )
                    reload()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.28f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.size(8.dp))
                Text(loc("Добавить лимит", "Add budget"))
            }
        }
        Spacer(Modifier.height(12.dp))

        NetworkFeatureCard(Icons.Default.History, loc("Почему отключилось?", "Why did it disconnect?")) {
            if (events.isEmpty()) {
                SecondaryText(
                    loc(
                        "Событий пока нет. Здесь появится понятная цепочка изменений сети и восстановления.",
                        "No events yet. Network changes and recovery steps will appear here."
                    )
                )
            } else {
                events.take(30).forEachIndexed { index, event ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = colors.divider
                        )
                    }
                    Row {
                        Text(
                            event.title,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(event.timestampMs)),
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    event.detail?.let {
                        Text(
                            it,
                            color = colors.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = { NetworkEventJournal.clear(context); reload() },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Text(loc("Очистить историю", "Clear history"))
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun NetworkFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNebulaColors.current
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.accent)
                Spacer(Modifier.size(10.dp))
                Text(
                    title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text = text, color = LocalNebulaColors.current.textSecondary)
}

@Composable
private fun PresetRow(preset: NetworkPreset, active: Boolean, onSelect: () -> Unit) {
    val colors = LocalNebulaColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            preset.iconGlyph ?: "◆",
            color = colors.textPrimary,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(preset.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            val rules = listOfNotNull(
                preset.matchSsid,
                preset.matchCarrierName,
                preset.matchTransport
            ).joinToString(" · ")
            if (rules.isNotBlank()) {
                Text(rules, color = colors.textSecondary, maxLines = 1)
            }
        }
        if (active) Text("●", color = colors.accent)
    }
}

private fun networkSummary(snapshot: NetworkContextSnapshot, en: Boolean): String {
    val name = snapshot.ssid ?: snapshot.carrierName ?: if (en) "Unknown network" else "Неизвестная сеть"
    return "$name · ${snapshot.transport.name.lowercase()}${
        if (snapshot.metered) {
            if (en) " · metered" else " · лимитная"
        } else {
            ""
        }
    }"
}

private fun validationSummary(snapshot: NetworkContextSnapshot, en: Boolean): String = when {
    snapshot.captivePortal -> if (en) "Sign-in is required before VPN starts" else "Нужна авторизация до запуска VPN"
    snapshot.validated -> if (en) "Internet access confirmed" else "Доступ в интернет подтверждён"
    else -> if (en) "Internet access has not been confirmed yet" else "Доступ в интернет пока не подтверждён"
}

private fun openCaptivePortal(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun formatBudgetBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
