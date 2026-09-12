package com.danila.nimbo

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.service.VpnQuickSettingsTileService
import com.danila.nimbo.vpn.MyVpnService
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** User-facing chooser; opening this activity never starts VPN by itself. */
class QuickControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferencesManager(this)
        setContent {
            val base = prefs.colorTheme.mod(9)
            val dark = when (prefs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            NebulaGuardTheme(
                themeIndex = base + if (dark) 0 else 9, elementStyle = prefs.elementStyle,
                isCustomAccent = prefs.isCustomAccent, customAccentColor = Color(prefs.customAccentColor),
                useDynamicColor = prefs.useDynamicColor, highContrastUi = prefs.highContrastUi,
                reducedTransparency = prefs.reducedTransparency, pureBlackMode = prefs.pureBlackMode,
                textScale = prefs.textScale, globalCorners = prefs.globalCorners,
                globalBrightness = prefs.globalBrightness, globalTransparency = prefs.globalTransparency,
                globalBlur = prefs.globalBlur, gradientEffectsEnabled = prefs.gradientEffectsEnabled,
                customGradientColor1 = Color(prefs.customGradientColor1),
                customGradientColor2 = Color(prefs.customGradientColor2),
                customGradientColor3 = Color(prefs.customGradientColor3), customGradientCount = prefs.customGradientCount
            ) {
                val en = prefs.appLanguage == "en"
                var favoritesOnly by remember { mutableStateOf(true) }
                var notice by remember { mutableStateOf<String?>(null) }
                val servers by produceState(emptyList<com.danila.nimbo.model.Server>()) {
                    value = withContext(Dispatchers.IO) { prefs.loadProfiles().flatMap { it.servers }.distinctBy { it.pingKey() } }
                }
                val favorites = remember { prefs.getPinnedServerKeys() }
                val visible = servers.filter { !favoritesOnly || it.pingKey() in favorites }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (en) "Quick controls" else "Быстрое управление", style = MaterialTheme.typography.headlineSmall)
                        Text(if (VpnManager.state.value == VpnState.CONNECTED) (VpnManager.connectedServer.value?.name ?: "Nimbo") else if(en) "Select a server to connect" else "Выберите сервер для подключения")
                        VpnManager.lastConnectionError.value?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
                        Row {
                            TextButton(onClick = { favoritesOnly = !favoritesOnly }) { Text(if (favoritesOnly) { if(en) "★ Favorites" else "★ Избранные" } else { if(en) "All servers" else "Все серверы" }) }
                            TextButton(onClick = { finish() }) { Text(if(en) "Close" else "Закрыть") }
                        }
                        if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = {
                            runCatching {
                                getSystemService(StatusBarManager::class.java).requestAddTileService(
                                    ComponentName(this@QuickControlsActivity, VpnQuickSettingsTileService::class.java), "Nimbo",
                                    Icon.createWithResource(this@QuickControlsActivity, R.drawable.icon_quick_settings), mainExecutor
                                ) { result -> notice = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                    if(en) "Tile is available in Quick Settings" else "Плитка доступна в шторке"
                                } else { if(en) "Add the tile manually in Quick Settings" else "Добавьте плитку вручную в редакторе шторки" } }
                            }.onFailure { notice = if(en) "Open the Quick Settings editor" else "Откройте редактор шторки" }
                        }) { Text(if(en) "Add VPN tile" else "Добавить плитку VPN") }
                        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (VpnManager.state.value != VpnState.DISCONNECTED) TextButton(onClick = {
                            startService(Intent(this@QuickControlsActivity, MyVpnService::class.java).setAction(MyVpnService.ACTION_DISCONNECT))
                        }) { Text(if(en) "Disconnect" else "Отключить") }
                        if (visible.isEmpty()) {
                            Text(if (favoritesOnly) { if(en) "No favorites yet. Switch to all servers." else "Избранных пока нет. Переключитесь на все серверы." }
                                else { if(en) "No servers. Add a subscription in Nimbo." else "Нет серверов. Добавьте подписку в Nimbo." })
                            TextButton(onClick = { startActivity(Intent(this@QuickControlsActivity, MainActivity::class.java)); finish() }) {
                                Text(if(en) "Open Nimbo" else "Открыть Nimbo")
                            }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visible, key = { it.pingKey() }) { server ->
                                GlassCard(Modifier.fillMaxWidth().clickable(enabled = VpnManager.state.value != VpnState.CONNECTING) {
                                    startActivity(Intent(this@QuickControlsActivity, QuickConnectActivity::class.java)
                                        .putExtras(MyVpnService.createConnectIntent(this@QuickControlsActivity, server)))
                                    finish()
                                }) { Text(server.name, Modifier.padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}
