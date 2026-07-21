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
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.NebulaInputField
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

                // Section: Protocol
                GlassSection(title = "Протокол пинга", icon = Icons.AutoMirrored.Filled.CompareArrows) {
                    ProtocolItem(
                        title = "TCP до ноды",
                        subtitle = "Прямой Socket.connect к host:port; это не задержка интернета через VPN",
                        selected = pingProtocol == 0,
                        onClick = { preferencesManager.pingProtocol = 0 }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    ProtocolItem(
                        title = "HTTP GET",
                        subtitle = "Запрос GET (более точный для веб)",
                        selected = pingProtocol == 1,
                        onClick = { preferencesManager.pingProtocol = 1 }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    ProtocolItem(
                        title = "HTTP HEAD",
                        subtitle = "Запрос HEAD (быстрее чем GET)",
                        selected = pingProtocol == 2,
                        onClick = { preferencesManager.pingProtocol = 2 }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    ProtocolItem(
                        title = "HTTPS Strict",
                        subtitle = "TLS + HTTP ответ, лучше ловит блокировки CDN",
                        selected = pingProtocol == 3,
                        onClick = { preferencesManager.pingProtocol = 3 }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )

                    ProtocolItem(
                        title = "ICMP Ping",
                        subtitle = "Системный Ping (требует прав)",
                        selected = pingProtocol == 4,
                        onClick = { preferencesManager.pingProtocol = 4 }
                    )
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
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetButton("Google", pingUrl == "https://www.gstatic.com/generate_204", Modifier.weight(1f)) {
                                preferencesManager.pingUrl = "https://www.gstatic.com/generate_204"
                            }
                            PresetButton("Cloudflare", pingUrl == "https://cp.cloudflare.com/generate_204", Modifier.weight(1f)) {
                                preferencesManager.pingUrl = "https://cp.cloudflare.com/generate_204"
                            }
                            PresetButton("Apple", pingUrl == "https://captive.apple.com/hotspot-detect.html", Modifier.weight(1f)) {
                                preferencesManager.pingUrl = "https://captive.apple.com/hotspot-detect.html"
                            }
                        }
                        
                        Spacer(Modifier.height(20.dp))
                        
                        // Timeout Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Таймаут ожидания", color = nebulaColors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                                Text("Максимум 10 секунд", style = MaterialTheme.typography.bodySmall, color = nebulaColors.textTertiary)
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(nebulaColors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (pingTimeout > 1) preferencesManager.pingTimeout -= 1 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(16.dp))
                                }
                                
                                Text(
                                    "$pingTimeout с",
                                    color = nebulaColors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                IconButton(
                                    onClick = { if (pingTimeout < 10) preferencesManager.pingTimeout += 1 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                    
                    SettingsSwitch(
                        icon = Icons.Default.VpnLock,
                        title = "Через VPN",
                        subtitle = "End-to-end HTTP до контрольного URL через выбранный outbound; требуется активный VPN",
                        checked = pingThroughProxy,
                        onCheckedChange = { preferencesManager.pingThroughProxy = it }
                    )
                }

                // Section: Display Mode
                GlassSection(title = "Визуализация", icon = Icons.Default.Visibility) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                preferencesManager.pingDisplayMode = (pingDisplayMode + 1) % 3
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Способ отображения", color = nebulaColors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (pingDisplayMode) {
                                    0 -> "Показывать время отклика в мс"
                                    1 -> "Показывать статус доступности"
                                    else -> "Показывать индикатор уровня"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = nebulaColors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Surface(
                            color = nebulaColors.accent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when (pingDisplayMode) {
                                        0 -> "Время"
                                        1 -> "Статус"
                                        else -> "Индикатор"
                                    },
                                    color = nebulaColors.accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Icon(Icons.Default.SyncAlt, null, tint = nebulaColors.accent, modifier = Modifier.size(14.dp).padding(start = 4.dp))
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = nebulaColors.textTertiary)
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

