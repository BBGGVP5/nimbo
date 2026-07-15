package com.danila.nimbo.ui.screens

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.TopBarMorphIcon
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager

@Composable
fun SettingsScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToSubscriptionSettings: () -> Unit,
    onNavigateToNetworkSettings: () -> Unit,
    onNavigateToNetworkPresets: () -> Unit,
    onNavigateToRouting: () -> Unit,
    onNavigateToAppProxySettings: () -> Unit,
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToNotificationHistory: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val uriHandler = LocalUriHandler.current
    val nebulaColors = LocalNebulaColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var killSwitch by remember { mutableStateOf(preferencesManager.killSwitch) }
    var autoConnect by remember { mutableStateOf(preferencesManager.autoConnect) }
    var disconnectOnLock by remember { mutableStateOf(preferencesManager.disconnectOnLock) }
    var connectOnUnlock by remember { mutableStateOf(preferencesManager.connectOnUnlock) }
    var memoryMonitoring by remember { mutableStateOf(preferencesManager.memoryMonitoring) }
    var updateSubOnStartup by remember { mutableStateOf(preferencesManager.updateSubOnStartup) }
    var memoryLimitDisabled by remember { mutableStateOf(preferencesManager.memoryLimitDisabled) }
    var memoryLimitMb by remember { mutableIntStateOf(preferencesManager.memoryLimitMb) }

    var hasBatteryOptimizationIssue by remember { mutableStateOf(false) }
    var hasNotificationIssue by remember { mutableStateOf(false) }

    fun refreshPermissionWarnings() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        hasBatteryOptimizationIssue =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !powerManager.isIgnoringBatteryOptimizations(context.packageName)

        val notificationsEnabledByChannel = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val hasPostNotificationsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasNotificationIssue = !(notificationsEnabledByChannel && hasPostNotificationsPermission)
    }

    LaunchedEffect(killSwitch) { preferencesManager.killSwitch = killSwitch }
    LaunchedEffect(autoConnect) { preferencesManager.autoConnect = autoConnect }
    LaunchedEffect(disconnectOnLock) { preferencesManager.disconnectOnLock = disconnectOnLock }
    LaunchedEffect(connectOnUnlock) { preferencesManager.connectOnUnlock = connectOnUnlock }
    LaunchedEffect(memoryMonitoring) { preferencesManager.memoryMonitoring = memoryMonitoring }
    LaunchedEffect(updateSubOnStartup) { preferencesManager.updateSubOnStartup = updateSubOnStartup }
    LaunchedEffect(memoryLimitDisabled) { preferencesManager.memoryLimitDisabled = memoryLimitDisabled }
    LaunchedEffect(memoryLimitMb) { preferencesManager.memoryLimitMb = memoryLimitMb }
    LaunchedEffect(Unit) { refreshPermissionWarnings() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionWarnings()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GlassHeader(
                title = "Настройки",
                icon = Icons.Default.Settings,
                iconColor = nebulaColors.accent,
                actions = {
                    TopBarMorphIcon(
                        icon = Icons.Default.Notifications,
                        color = nebulaColors.accent,
                        onClick = onNavigateToNotificationHistory
                    )
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 132.dp)
            ) {
                if (hasBatteryOptimizationIssue || hasNotificationIssue) {
                    WarningCard(
                        hasBatteryOptimizationIssue = hasBatteryOptimizationIssue,
                        hasNotificationIssue = hasNotificationIssue
                    )
                    Spacer(Modifier.height(10.dp))
                }

                GlassSection(title = "Быстрые Переключатели", icon = Icons.Default.Bolt) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickToggleTileLarge(
                            modifier = Modifier.weight(1f),
                            title = "Kill Switch",
                            subtitle = "Блокировать трафик при сбое VPN",
                            icon = Icons.Default.Lock,
                            checked = killSwitch,
                            onCheckedChange = { killSwitch = it }
                        )
                        QuickToggleTileLarge(
                            modifier = Modifier.weight(1f),
                            title = "Автостарт VPN",
                            subtitle = "Старт VPN при запуске",
                            icon = Icons.Default.AutoAwesome,
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickToggleTileLarge(
                            modifier = Modifier.weight(1f),
                            title = "Блокировка экрана",
                            subtitle = "Отключать VPN при блокировке",
                            icon = Icons.Default.ScreenLockLandscape,
                            checked = disconnectOnLock,
                            onCheckedChange = {
                                disconnectOnLock = it
                                if (!it) connectOnUnlock = false
                            }
                        )
                        QuickToggleTileLarge(
                            modifier = Modifier.weight(1f),
                            title = "Разблокировка",
                            subtitle = "Включать VPN после unlock",
                            icon = Icons.Default.LockOpen,
                            checked = disconnectOnLock && connectOnUnlock,
                            onCheckedChange = { if (disconnectOnLock) connectOnUnlock = it },
                            enabled = disconnectOnLock
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickToggleTileLarge(
                            modifier = Modifier.weight(1f),
                            title = "Мониторинг памяти",
                            subtitle = "Показывать данные на главном экране",
                            icon = Icons.Default.Memory,
                            checked = memoryMonitoring,
                            onCheckedChange = { memoryMonitoring = it }
                        )
                        QuickToggleTileLarge(
                            modifier = Modifier.weight(1f),
                            title = "Обновлять подписки",
                            subtitle = "Проверять обновления при старте",
                            icon = Icons.Default.CloudDownload,
                            checked = updateSubOnStartup,
                            onCheckedChange = { updateSubOnStartup = it }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                GlassSection(title = "Память", icon = Icons.Default.Memory) {
                    MemoryLimitCard(
                        memoryLimitDisabled = memoryLimitDisabled,
                        memoryLimitMb = memoryLimitMb,
                        onUnlimitedChange = { memoryLimitDisabled = it },
                        onLimitChange = { memoryLimitMb = it }
                    )
                }

                Spacer(Modifier.height(8.dp))

                GlassSection(title = "Сеть И Конфиг", icon = Icons.Default.SettingsEthernet) {
                    ModernNavigationRow(
                        icon = Icons.Default.SettingsEthernet,
                        title = "Настройки сети",
                        subtitle = "Соединение и скорость",
                        onClick = onNavigateToNetworkSettings
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.Tune,
                        title = "Профили сети",
                        subtitle = "Дом / Wi-Fi / Роуминг и пресеты",
                        onClick = onNavigateToNetworkPresets
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.Subscriptions,
                        title = "Подписки",
                        subtitle = "Интервал обновления и параметры",
                        onClick = onNavigateToSubscriptionSettings
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.Route,
                        title = "Маршрутизация",
                        subtitle = "Маршруты трафика и DNS",
                        onClick = onNavigateToRouting
                    )
                }

                Spacer(Modifier.height(8.dp))

                GlassSection(title = "Интерфейс И Данные", icon = Icons.Default.Palette) {
                    ModernNavigationRow(
                        icon = Icons.Default.SettingsApplications,
                        title = "Прокси по приложениям",
                        subtitle = "Режимы: обход VPN или только через VPN",
                        onClick = onNavigateToAppProxySettings
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.Palette,
                        title = "Внешний вид",
                        subtitle = "Тема, цвета, анимации",
                        onClick = onNavigateToAppearanceSettings
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.Backup,
                        title = "Резервные копии",
                        subtitle = "Экспорт и восстановление",
                        onClick = onNavigateToBackup
                    )
                }

                Spacer(Modifier.height(8.dp))

                GlassSection(title = "Поддержка И Обновления", icon = Icons.Default.Info) {
                    ModernNavigationRow(
                        icon = Icons.Default.Info,
                        title = "О приложении",
                        subtitle = "Версия, разработчик, лицензия",
                        onClick = onNavigateToAbout
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.BugReport,
                        title = "Логи",
                        subtitle = "Просмотр и экспорт логов",
                        onClick = onNavigateToLogs
                    )
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernNavigationRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Обновление",
                        subtitle = "Проверить новую версию",
                        onClick = onNavigateToUpdates
                    )
                }

                Spacer(Modifier.height(8.dp))

                GlassSection(title = "Ссылки", icon = Icons.Default.Link) {
                    ModernLinkRow(icon = Icons.Default.Send, title = "Telegram канал") {
                        uriHandler.openUri("https://t.me/nebulaguard_channel")
                    }
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernLinkRow(icon = Icons.AutoMirrored.Filled.Chat, title = "Чат") {
                        uriHandler.openUri("https://t.me/+uGciYHbWgrgyODIy")
                    }
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernLinkRow(icon = Icons.Default.SmartToy, title = "VPN Бот") {
                        uriHandler.openUri("https://t.me/nebulaguardd_bot")
                    }
                    HorizontalDivider(color = nebulaColors.textTertiary.copy(alpha = 0.08f))
                    ModernLinkRow(icon = Icons.Default.CloudDownload, title = "Канал обновлений") {
                        uriHandler.openUri("https://t.me/nebulaguard_update")
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    hasBatteryOptimizationIssue: Boolean,
    hasNotificationIssue: Boolean
) {
    val nebulaColors = LocalNebulaColors.current
    val warningText = when {
        hasBatteryOptimizationIssue && hasNotificationIssue ->
            "Включена оптимизация батареи и выключены уведомления. Это может приводить к разрывам VPN."
        hasBatteryOptimizationIssue ->
            "Включена оптимизация батареи. Это может приводить к разрывам VPN."
        else ->
            "Уведомления выключены. Это может приводить к разрывам VPN."
    }
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Внимание",
                    style = MaterialTheme.typography.titleSmall,
                    color = nebulaColors.textPrimary
                )
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = nebulaColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun QuickToggleTileLarge(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = modifier
            .height(178.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(nebulaColors.textPrimary.copy(alpha = if (enabled) 0.05f else 0.025f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = if (enabled) onCheckedChange else null,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = nebulaColors.accent,
                        checkedTrackColor = nebulaColors.accent.copy(alpha = 0.35f)
                    )
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = nebulaColors.textPrimary,
                maxLines = 2
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = nebulaColors.textSecondary,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun MemoryLimitCard(
    memoryLimitDisabled: Boolean,
    memoryLimitMb: Int,
    onUnlimitedChange: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val sliderValue = memoryLimitMb.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Снять ограничение",
                        style = MaterialTheme.typography.titleSmall,
                        color = nebulaColors.textPrimary
                    )
                    Text(
                        text = "Для мощных устройств. Больше памяти повышает стабильность под нагрузкой.",
                        style = MaterialTheme.typography.bodySmall,
                        color = nebulaColors.textSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = memoryLimitDisabled,
                    onCheckedChange = onUnlimitedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = nebulaColors.accent,
                        checkedTrackColor = nebulaColors.accent.copy(alpha = 0.35f)
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (memoryLimitDisabled) {
                    "Лимит памяти: без ограничений"
                } else {
                    "Лимит памяти: ${memoryLimitMb} MB"
                },
                style = MaterialTheme.typography.labelLarge,
                color = nebulaColors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (memoryLimitDisabled) {
                    "Режим для мощных устройств и максимальной стабильности."
                } else {
                    "Ниже лимит — ниже расход памяти, выше лимит — стабильнее при высокой нагрузке."
                },
                style = MaterialTheme.typography.bodySmall,
                color = nebulaColors.textSecondary
            )
            Spacer(Modifier.height(6.dp))
            Slider(
                value = sliderValue,
                onValueChange = { onLimitChange(it.toInt().coerceIn(40, 300)) },
                valueRange = 40f..300f,
                enabled = !memoryLimitDisabled,
                steps = 25,
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.accent,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.3f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("40 MB", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                Text("300 MB", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ModernNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(nebulaColors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = nebulaColors.textPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = nebulaColors.textSecondary, maxLines = 2)
        }
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = nebulaColors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ModernLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(nebulaColors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = nebulaColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = nebulaColors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}
