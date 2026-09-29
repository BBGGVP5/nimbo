package com.danila.nimbo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.danila.nimbo.network.PingDisplay
import com.danila.nimbo.network.ActiveProxyPing
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.NebulaInputField
import com.danila.nimbo.ui.components.PingTimeoutControl
import com.danila.nimbo.ui.components.PingValueContent
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingSettingsScreen(
    navController: NavController,
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel
) {
    val nebulaColors = LocalNebulaColors.current
    
    val pingProtocol by preferencesManager.pingProtocolState
    val pingUrl by preferencesManager.pingUrlState
    val pingTimeout by preferencesManager.pingTimeoutState
    val pingDisplayMode by preferencesManager.pingDisplayModeState
    val pingThroughProxy by preferencesManager.pingThroughProxyState

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GlassHeader(
                title = "Настройки пинга",
                icon = Icons.Default.AccessTime,
                iconColor = nebulaColors.accent,
                onBack = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    onClick = { navController.navigate("ping_tool") },
                    shape = RoundedCornerShape(16.dp),
                    color = nebulaColors.accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.32f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Speed, null, tint = nebulaColors.accent)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Проверить пинг", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Открыть отдельный инструмент диагностики", color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = nebulaColors.accent)
                    }
                }

                com.danila.nimbo.ui.components.PingDiagnosticSummary()

                // Section: Protocol
                GlassSection(title = "Протокол пинга", icon = Icons.AutoMirrored.Filled.CompareArrows) {
                    ProtocolItem(
                        title = "Nimbo Ping",
                        subtitle = "GET через маршрут сервера без включения VPN",
                        selected = pingProtocol == 5,
                        onClick = { preferencesManager.pingProtocol = 5 }
                    )
                    ProtocolItem(
                        title = "TCP",
                        subtitle = "Проверка доступности адреса и порта",
                        selected = pingProtocol == 0,
                        onClick = { preferencesManager.pingProtocol = 0 }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    ProtocolItem(
                        title = "HTTP GET",
                        subtitle = "Время ответа страницы целиком",
                        selected = pingProtocol == 1,
                        onClick = { preferencesManager.pingProtocol = 1 }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    ProtocolItem(
                        title = "HTTP HEAD",
                        subtitle = "Время ответа без загрузки страницы",
                        selected = pingProtocol == 2,
                        onClick = { preferencesManager.pingProtocol = 2 }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    ProtocolItem(
                        title = "HTTPS Strict",
                        subtitle = "Проверка защищённого соединения",
                        selected = pingProtocol == 3,
                        onClick = { preferencesManager.pingProtocol = 3 }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )

                    ProtocolItem(
                        title = "ICMP Ping",
                        subtitle = "Системная проверка адреса",
                        selected = pingProtocol == 4,
                        onClick = { preferencesManager.pingProtocol = 4 }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = nebulaColors.textTertiary.copy(alpha = 0.1f))

                }

                // Section: Test URL
                GlassSection(title = "Параметры теста", icon = Icons.Default.Language) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "URL для проверки",
                            style = MaterialTheme.typography.labelSmall,
                            color = nebulaColors.textTertiary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        NebulaInputField(
                            value = pingUrl,
                            onValueChange = { preferencesManager.pingUrl = it },
                            label = "URL",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Language, null, tint = nebulaColors.accent) }
                        )
                        
                        if (!ActiveProxyPing.validUrl(pingUrl)) {
                            Text("Введите HTTP/HTTPS URL без логина, пароля и фрагмента.", color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetButton("Google", pingUrl == "https://www.gstatic.com/generate_204", Modifier.weight(1f)) {
                                preferencesManager.pingUrl = "https://www.gstatic.com/generate_204"
                            }
                            PresetButton("Cloud", pingUrl == "https://cp.cloudflare.com/generate_204", Modifier.weight(1f)) {
                                preferencesManager.pingUrl = "https://cp.cloudflare.com/generate_204"
                            }
                            PresetButton("Apple", pingUrl == "https://captive.apple.com/hotspot-detect.html", Modifier.weight(1f)) {
                                preferencesManager.pingUrl = "https://captive.apple.com/hotspot-detect.html"
                            }
                        }
                        
                        Spacer(Modifier.height(20.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Таймаут ожидания", color = nebulaColors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text("1–10 секунд. Пинг сервера отображается в мс.", style = MaterialTheme.typography.bodySmall, color = nebulaColors.textTertiary)
                            PingTimeoutControl(pingTimeout, { preferencesManager.pingTimeout = it })
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    SettingsSwitch(
                        icon = Icons.Default.VpnLock,
                        title = "Через VPN",
                        subtitle = "Другие методы используют активный VPN; Nimbo Ping — маршрут сервера",
                        checked = pingThroughProxy || pingProtocol == 5,
                        enabled = pingProtocol != 5,
                        onCheckedChange = { preferencesManager.pingThroughProxy = it }
                    )
                }

                // Section: Display Mode
                GlassSection(title = "Визуализация", icon = Icons.Default.Visibility) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PingDisplay.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { display ->
                                    val selected = pingDisplayMode == display.id
                                    Surface(
                                        onClick = { preferencesManager.pingDisplayMode = display.id },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) nebulaColors.accent.copy(alpha = 0.12f) else nebulaColors.textPrimary.copy(alpha = 0.04f),
                                        border = BorderStroke(1.dp, if (selected) nebulaColors.accent else nebulaColors.textPrimary.copy(alpha = 0.10f))
                                    ) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                when (display) {
                                                    PingDisplay.NUMERIC -> "Числа"
                                                    PingDisplay.BARS -> "Полоски"
                                                    PingDisplay.BOTH -> "Вместе"
                                                    PingDisplay.DOTS -> "Точки"
                                                },
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = nebulaColors.textPrimary,
                                                maxLines = 1
                                            )
                                            PingValueContent(87, display.id, nebulaColors.accent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ProtocolItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = nebulaColors.textTertiary)
        }
        
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (selected) nebulaColors.accent.copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .border(
                    1.dp,
                    if (selected) nebulaColors.accent else nebulaColors.textPrimary.copy(alpha = 0.1f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = nebulaColors.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PresetButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) nebulaColors.accent.copy(alpha = 0.16f) else nebulaColors.textPrimary.copy(alpha = 0.05f),
        modifier = modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 9.dp),
            color = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

