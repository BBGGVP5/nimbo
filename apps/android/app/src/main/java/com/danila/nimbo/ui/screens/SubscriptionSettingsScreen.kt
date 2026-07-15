package com.danila.nimbo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.service.SubscriptionUpdateScheduler
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.components.jellyScrollAnimation
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.observeInternetConnection

@Composable
fun SubscriptionSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRouting: () -> Unit
) {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = remember { PreferencesManager(context) }

    var autoUpdate by remember { mutableStateOf<Boolean>(preferencesManager.subscriptionAutoUpdate) }
    var updateInterval by remember { mutableStateOf<Int>(preferencesManager.subscriptionUpdateInterval) }
    var sendHwid by remember { mutableStateOf<Boolean>(preferencesManager.sendHwid) }
    var showNotificationSpeed by remember { mutableStateOf<Boolean>(preferencesManager.showNotificationSpeed) }
    var showNotificationConnectionTime by remember { mutableStateOf<Boolean>(preferencesManager.showNotificationConnectionTime) }

    var showIntervalDropdown by remember { mutableStateOf(false) }

    var isInternetAvailable by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        observeInternetConnection(context).collect { isOnline ->
            isInternetAvailable = isOnline
        }
    }

    LaunchedEffect(autoUpdate, updateInterval) {
        preferencesManager.subscriptionAutoUpdate = autoUpdate
        preferencesManager.subscriptionUpdateInterval = updateInterval
        SubscriptionUpdateScheduler.reschedule(context)
    }
    LaunchedEffect(sendHwid) { preferencesManager.sendHwid = sendHwid }
    LaunchedEffect(showNotificationSpeed) { preferencesManager.showNotificationSpeed = showNotificationSpeed }
    LaunchedEffect(showNotificationConnectionTime) {
        preferencesManager.showNotificationConnectionTime = showNotificationConnectionTime
    }

    val intervals = listOf(
        300 to "5 минут",
        900 to "15 минут",
        1800 to "30 минут",
        3600 to "1 час",
        7200 to "2 часа",
        14400 to "4 часа",
        28800 to "8 часов",
        43200 to "12 часов",
        86400 to "24 часа"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            GlassHeader(
                title = "Подписки",
                icon = Icons.Default.Subscriptions,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .jellyScrollAnimation()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
            ) {
                if (!isInternetAvailable) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x33FF4D67),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF4D67))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF6A7A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(
                                    text = "Нет подключения к интернету",
                                    color = Color(0xFFFF9AA5),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Резервный режим временно недоступен до восстановления сети.",
                                    color = nebulaColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                GlassSection(
                    title = "Автообновление",
                    icon = Icons.Default.Bolt
                ) {
                    SettingsSwitch(
                        icon = Icons.Default.AutoAwesome,
                        title = "Автообновление",
                        subtitle = "Для всех подписок",
                        checked = autoUpdate,
                        onCheckedChange = { autoUpdate = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.08f)
                    )

                    MorphismDropdownField(
                        icon = Icons.Default.History,
                        title = "Интервал обновления",
                        subtitle = intervals.find { it.first == updateInterval }?.second ?: "12 часов",
                        expanded = showIntervalDropdown,
                        enabled = autoUpdate,
                        onToggle = { if (autoUpdate) showIntervalDropdown = !showIntervalDropdown },
                        options = intervals,
                        selectedValue = updateInterval,
                        onSelect = {
                            updateInterval = it
                            showIntervalDropdown = false
                        }
                    )
                }

                Spacer(Modifier.height(18.dp))

                GlassSection(
                    title = "Проверка связи",
                    icon = Icons.Default.NetworkCheck
                ) {
                    SettingsSwitch(
                        icon = Icons.Default.Speed,
                        title = "Скорость в уведомлении",
                        subtitle = "Показывать download/upload в VPN-уведомлении",
                        checked = showNotificationSpeed,
                        onCheckedChange = { showNotificationSpeed = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.08f)
                    )

                    SettingsSwitch(
                        icon = Icons.Default.Timer,
                        title = "Время в уведомлении",
                        subtitle = "Показывать длительность подключения",
                        checked = showNotificationConnectionTime,
                        onCheckedChange = { showNotificationConnectionTime = it }
                    )

                }

                Spacer(Modifier.height(18.dp))

                GlassSection(
                    title = "Идентификация",
                    icon = Icons.Default.Fingerprint
                ) {
                    SettingsSwitch(
                        icon = Icons.Default.VpnKey,
                        title = "Передача HWID",
                        subtitle = "Уникальный ID устройства для сервера",
                        checked = sendHwid,
                        onCheckedChange = { sendHwid = it }
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = nebulaColors.accent.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "HWID помогает серверу идентифицировать ваш аккаунт и предоставлять персональные бонусы",
                                color = nebulaColors.textPrimary.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                GlassSection(
                    title = "Маршрутизация",
                    icon = Icons.AutoMirrored.Filled.AltRoute
                ) {
                    com.danila.nimbo.ui.components.SettingsNavigationItem(
                        icon = Icons.AutoMirrored.Filled.AltRoute,
                        title = "Правила маршрутизации",
                        subtitle = "Управление трафиком (nimbo://routing/add/...)",
                        onClick = onNavigateToRouting
                    )
                }
            }
        }
    }
}

@Composable
private fun MorphismDropdownField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "dropdownArrow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(nebulaColors.textPrimary.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(nebulaColors.accent.copy(alpha = 0.15f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            subtitle,
                            color = if (enabled) nebulaColors.textTertiary else nebulaColors.textTertiary.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = null,
                    tint = if (enabled) nebulaColors.textTertiary else nebulaColors.textTertiary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp).rotate(arrowRotation)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && enabled,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(nebulaColors.surface.copy(alpha = 0.85f))
            ) {
                options.forEachIndexed { index, (value, label) ->
                    val isSelected = value == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) nebulaColors.accent else nebulaColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (index != options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = nebulaColors.textTertiary.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}
