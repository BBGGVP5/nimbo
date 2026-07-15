package com.danila.nimbo.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.NebulaInputField
import com.danila.nimbo.ui.components.SettingsNavigationItem
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import java.util.UUID

private fun generateSocksCredential(prefix: String): String {
    return "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
}

@Composable
fun NetworkSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConnectionSettings: () -> Unit,
    onNavigateToTunnelSettings: () -> Unit,
    onNavigateToSocksSettings: () -> Unit,
    onNavigateToConnectivityDiagnostics: () -> Unit,
    onNavigateToPingSettings: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current

    val pingProtocol by preferencesManager.pingProtocolState
    var pingOnStartup by remember { mutableStateOf(preferencesManager.pingOnStartup) }
    var updateSubOnStartup by remember { mutableStateOf(preferencesManager.updateSubOnStartup) }
    var pingOnUpdate by remember { mutableStateOf(preferencesManager.pingOnUpdate) }
    var autoRotationEnabled by remember { mutableStateOf(preferencesManager.autoRotationEnabled) }
    val autoBypassAlwaysOn = preferencesManager.autoBypassByNetwork
    val scrollState = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    LaunchedEffect(pingOnStartup) { preferencesManager.pingOnStartup = pingOnStartup }
    LaunchedEffect(updateSubOnStartup) { preferencesManager.updateSubOnStartup = updateSubOnStartup }
    LaunchedEffect(pingOnUpdate) { preferencesManager.pingOnUpdate = pingOnUpdate }
    LaunchedEffect(autoRotationEnabled) { preferencesManager.autoRotationEnabled = autoRotationEnabled }
    LaunchedEffect(Unit) { preferencesManager.autoBypassByNetwork = true }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            GlassHeader(
                title = "Сетевые настройки",
                icon = Icons.Default.Public,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                GlassSection(
                    title = "Разделы",
                    icon = Icons.Default.Tune
                ) {
                    SettingsNavigationItem(
                        icon = Icons.Default.NetworkCheck,
                        title = "Соединение",
                        subtitle = "Автоподключение, Kill Switch и LAN",
                        onClick = onNavigateToConnectionSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsNavigationItem(
                        icon = Icons.Default.VpnKey,
                        title = "Туннель",
                        subtitle = "IP/DNS, фрагментация, Mux, UDP и уведомления",
                        onClick = onNavigateToTunnelSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsNavigationItem(
                        icon = Icons.Default.Security,
                        title = "SOCKS5 авторизация",
                        subtitle = "Логин, пароль, порт и защита локального прокси",
                        onClick = onNavigateToSocksSettings
                    )
                }

                Spacer(Modifier.height(16.dp))

                GlassSection(
                    title = "Проверки и обновления",
                    icon = Icons.Default.Speed
                ) {
                    SettingsSwitch(
                        icon = Icons.Default.SyncAlt,
                        title = "Резервный режим",
                        subtitle = "Всегда включен для сетей с ограничениями",
                        checked = autoBypassAlwaysOn,
                        enabled = false,
                        onCheckedChange = {}
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsNavigationItem(
                        icon = Icons.Default.CellTower,
                        title = "Настройки пинга",
                        subtitle = when (pingProtocol) {
                            1 -> "HTTP GET"
                            2 -> "HTTP HEAD"
                            3 -> "ICMP"
                            else -> "TCP Connect"
                        },
                        onClick = onNavigateToPingSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsNavigationItem(
                        icon = Icons.Default.NetworkCheck,
                        title = "Проверка БС",
                        subtitle = "Проверить, есть ли ограничения связи",
                        onClick = onNavigateToConnectivityDiagnostics
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.Bolt,
                        title = "Пинг при запуске",
                        subtitle = "Автоматически пинговать серверы при открытии приложения",
                        checked = pingOnStartup,
                        onCheckedChange = { pingOnStartup = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.Sync,
                        title = "Пинг при обновлении",
                        subtitle = "Выполнять пинг после обновления подписки",
                        checked = pingOnUpdate,
                        onCheckedChange = { pingOnUpdate = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.CloudDownload,
                        title = "Обновлять подписки",
                        subtitle = "Автоматическое обновление подписок при старте",
                        checked = updateSubOnStartup,
                        onCheckedChange = { updateSubOnStartup = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.SyncAlt,
                        title = "Авто-ротация сервера",
                        subtitle = "Переключать на рабочий узел при блокировке/ошибке",
                        checked = autoRotationEnabled,
                        onCheckedChange = { autoRotationEnabled = it }
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Изменения применяются при следующем подключении VPN",
                    style = MaterialTheme.typography.bodySmall,
                    color = nebulaColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ConnectionSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scrollState = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    var autoConnect by remember { mutableStateOf(preferencesManager.autoConnect) }
    var killSwitch by remember { mutableStateOf(preferencesManager.killSwitch) }
    var keepDeviceActive by remember { mutableStateOf(preferencesManager.keepDeviceActive) }
    var allowLanConnections by remember { mutableStateOf(preferencesManager.allowLanConnections) }
    var allowHotspotAccess by remember { mutableStateOf(preferencesManager.allowHotspotAccess) }
    var lanThroughProxy by remember { mutableStateOf(preferencesManager.lanThroughProxy) }

    LaunchedEffect(autoConnect) { preferencesManager.autoConnect = autoConnect }
    LaunchedEffect(killSwitch) { preferencesManager.killSwitch = killSwitch }
    LaunchedEffect(keepDeviceActive) { preferencesManager.keepDeviceActive = keepDeviceActive }
    LaunchedEffect(allowLanConnections) { preferencesManager.allowLanConnections = allowLanConnections }
    LaunchedEffect(allowHotspotAccess) { preferencesManager.allowHotspotAccess = allowHotspotAccess }
    LaunchedEffect(lanThroughProxy) { preferencesManager.lanThroughProxy = lanThroughProxy }
    LaunchedEffect(Unit) { preferencesManager.autoBypassByNetwork = true }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            GlassHeader(
                title = "Соединение",
                icon = Icons.Default.NetworkCheck,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                GlassSection(title = "Подключение", icon = Icons.Default.SettingsEthernet) {
                    SettingsSwitch(
                        icon = Icons.Default.ElectricalServices,
                        title = "Автоподключение",
                        subtitle = "Подключаться при запуске приложения",
                        checked = autoConnect,
                        onCheckedChange = { autoConnect = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.Shield,
                        title = "Kill Switch",
                        subtitle = "Блокировать интернет без VPN",
                        checked = killSwitch,
                        onCheckedChange = { killSwitch = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Держать устройство активным",
                        subtitle = "Удерживать wakelock во время работы VPN",
                        checked = keepDeviceActive,
                        onCheckedChange = { keepDeviceActive = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.SyncAlt,
                        title = "Резервный режим",
                        subtitle = "Всегда включен и недоступен для отключения",
                        checked = true,
                        enabled = false,
                        onCheckedChange = {}
                    )
                }

                Spacer(Modifier.height(16.dp))
                GlassSection(title = "Локальная сеть", icon = Icons.Default.Link) {
                    SettingsSwitch(
                        icon = Icons.Default.Link,
                        title = "Разрешить LAN подключения",
                        subtitle = "Исключить локальную сеть из VPN-туннеля",
                        checked = allowLanConnections,
                        onCheckedChange = { allowLanConnections = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.PortableWifiOff,
                        title = "Доступ через хотспот",
                        subtitle = "Разрешить доступ к локальным сервисам через точку доступа",
                        checked = allowHotspotAccess,
                        onCheckedChange = { allowHotspotAccess = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.Route,
                        title = "LAN через прокси",
                        subtitle = "Направлять локальный трафик через VPN-прокси",
                        checked = lanThroughProxy,
                        onCheckedChange = { lanThroughProxy = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scrollState = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    var tunnelMode by remember { mutableStateOf(preferencesManager.tunnelMode) }
    var showNotificationSpeed by remember { mutableStateOf(preferencesManager.showNotificationSpeed) }
    var packetFragmentationEnabled by remember { mutableStateOf(preferencesManager.packetFragmentationEnabled) }
    var muxEnabled by remember { mutableStateOf(preferencesManager.muxEnabled) }
    var blockUdp by remember { mutableStateOf(preferencesManager.blockUdp) }
    var vpnIpType by remember { mutableStateOf(preferencesManager.vpnIpType) }
    var vpnDnsMode by remember { mutableStateOf(preferencesManager.vpnDnsMode) }

    LaunchedEffect(tunnelMode) { preferencesManager.tunnelMode = tunnelMode }
    LaunchedEffect(showNotificationSpeed) { preferencesManager.showNotificationSpeed = showNotificationSpeed }
    LaunchedEffect(packetFragmentationEnabled) { preferencesManager.packetFragmentationEnabled = packetFragmentationEnabled }
    LaunchedEffect(muxEnabled) { preferencesManager.muxEnabled = muxEnabled }
    LaunchedEffect(blockUdp) { preferencesManager.blockUdp = blockUdp }
    LaunchedEffect(vpnIpType) { preferencesManager.vpnIpType = vpnIpType }
    LaunchedEffect(vpnDnsMode) { preferencesManager.vpnDnsMode = vpnDnsMode }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            GlassHeader(
                title = "Туннель",
                icon = Icons.Default.VpnKey,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                GlassSection(title = "Маршрутизация", icon = Icons.Default.Tune) {
                    SettingsSwitch(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        title = "Режим туннеля (Tun+Proxy)",
                        subtitle = "Использовать оба режима вместо только Tun",
                        checked = tunnelMode == 0 || tunnelMode == 2,
                        onCheckedChange = { tunnelMode = if (it) 2 else 1 }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))

                    var ipExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = ipExpanded,
                        onExpandedChange = { ipExpanded = !ipExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        NebulaInputField(
                            value = if (vpnIpType == "dual") "IPv4 + IPv6" else "IPv4",
                            onValueChange = {},
                            readOnly = true,
                            label = "Тип IP",
                            leadingIcon = { Icon(Icons.Default.SettingsEthernet, null, tint = nebulaColors.accent) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ipExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = ipExpanded,
                            onDismissRequest = { ipExpanded = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = nebulaColors.surface.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, nebulaColors.textTertiary.copy(alpha = 0.3f))
                        ) {
                            DropdownMenuItem(text = { Text("IPv4", color = nebulaColors.textPrimary) }, onClick = {
                                vpnIpType = "ipv4"
                                ipExpanded = false
                            })
                            DropdownMenuItem(text = { Text("IPv4 + IPv6", color = nebulaColors.textPrimary) }, onClick = {
                                vpnIpType = "dual"
                                ipExpanded = false
                            })
                        }
                    }

                    var dnsExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = dnsExpanded,
                        onExpandedChange = { dnsExpanded = !dnsExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        NebulaInputField(
                            value = when (vpnDnsMode) {
                                "local" -> "Локальный (direct)"
                                "hybrid" -> "Гибридный"
                                else -> "Внутренний (скрытый)"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = "VPN DNS",
                            leadingIcon = { Icon(Icons.Default.Dns, null, tint = nebulaColors.accent) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dnsExpanded,
                            onDismissRequest = { dnsExpanded = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = nebulaColors.surface.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, nebulaColors.textTertiary.copy(alpha = 0.3f))
                        ) {
                            DropdownMenuItem(text = { Text("Внутренний (скрытый)", color = nebulaColors.textPrimary) }, onClick = {
                                vpnDnsMode = "remote"
                                dnsExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Локальный (direct)", color = nebulaColors.textPrimary) }, onClick = {
                                vpnDnsMode = "local"
                                dnsExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Гибридный", color = nebulaColors.textPrimary) }, onClick = {
                                vpnDnsMode = "hybrid"
                                dnsExpanded = false
                            })
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.FilterAlt,
                        title = "Фрагментирование",
                        subtitle = "Обход DPI через адаптивную фрагментацию пакетов",
                        checked = packetFragmentationEnabled,
                        onCheckedChange = { packetFragmentationEnabled = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.SyncAlt,
                        title = "Мультиплексирование (Mux)",
                        subtitle = "Объединение соединений в один канал",
                        checked = muxEnabled,
                        onCheckedChange = { muxEnabled = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.PortableWifiOff,
                        title = "Блокировать UDP",
                        subtitle = "Сломает DNS-over-UDP, QUIC, игры и звонки",
                        checked = blockUdp,
                        onCheckedChange = { blockUdp = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                    SettingsSwitch(
                        icon = Icons.Default.Notifications,
                        title = "Скорость в уведомлении",
                        subtitle = "Показывать upload/download скорость в VPN-уведомлении",
                        checked = showNotificationSpeed,
                        onCheckedChange = { showNotificationSpeed = it }
                    )
                }
            }
        }
    }
}

@Composable
fun SocksSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scrollState = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    var socks5AuthEnabled by remember { mutableStateOf(preferencesManager.socks5AuthEnabled) }
    var socks5AuthLogin by remember { mutableStateOf(preferencesManager.socks5AuthLogin) }
    var socks5AuthPassword by remember { mutableStateOf(preferencesManager.socks5AuthPassword) }
    var socks5AuthPortText by remember { mutableStateOf(preferencesManager.socks5AuthPort.toString()) }

    LaunchedEffect(Unit) {
        if (socks5AuthLogin.isBlank() || !socks5AuthLogin.startsWith("nebula", ignoreCase = true)) {
            socks5AuthLogin = generateSocksCredential("nebula")
        }
        if (socks5AuthPassword.isBlank()) {
            socks5AuthPassword = generateSocksCredential("nebula_pw")
        }
    }

    LaunchedEffect(socks5AuthEnabled) { preferencesManager.socks5AuthEnabled = socks5AuthEnabled }
    LaunchedEffect(socks5AuthLogin) { preferencesManager.socks5AuthLogin = socks5AuthLogin.trim() }
    LaunchedEffect(socks5AuthPassword) { preferencesManager.socks5AuthPassword = socks5AuthPassword.trim() }
    LaunchedEffect(socks5AuthPortText) {
        socks5AuthPortText.toIntOrNull()?.let { preferencesManager.socks5AuthPort = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            GlassHeader(
                title = "SOCKS5 авторизация",
                icon = Icons.Default.Security,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                GlassSection(title = "Доступ", icon = Icons.Default.Key) {
                    SettingsSwitch(
                        icon = Icons.Default.Lock,
                        title = "SOCKS5 авторизация",
                        subtitle = "Защищает локальный прокси от сканирования портов приложениями",
                        checked = socks5AuthEnabled,
                        onCheckedChange = { socks5AuthEnabled = it }
                    )

                    if (socks5AuthEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))
                        NebulaInputField(
                            value = socks5AuthLogin,
                            onValueChange = { socks5AuthLogin = it },
                            label = "Логин",
                            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Lock, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        NebulaInputField(
                            value = socks5AuthPassword,
                            onValueChange = { socks5AuthPassword = it },
                            label = "Пароль",
                            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Password, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        NebulaInputField(
                            value = socks5AuthPortText,
                            onValueChange = { input -> socks5AuthPortText = input.filter { it.isDigit() }.take(5) },
                            label = "Port",
                            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.DataObject, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        SettingsNavigationItem(
                            icon = Icons.Default.Sync,
                            title = "Сбросить логин/пароль",
                            subtitle = "Сгенерировать новую пару доступа SOCKS5",
                            onClick = {
                                socks5AuthLogin = generateSocksCredential("nebula")
                                socks5AuthPassword = generateSocksCredential("nebula_pw")
                            }
                        )
                        Text(
                            text = "Логин начинается с nebula по умолчанию",
                            style = MaterialTheme.typography.bodySmall,
                            color = nebulaColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
