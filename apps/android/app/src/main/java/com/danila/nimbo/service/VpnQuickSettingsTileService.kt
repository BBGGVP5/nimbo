package com.danila.nimbo.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.danila.nimbo.QuickConnectActivity
import com.danila.nimbo.R
import com.danila.nimbo.model.Server
import com.danila.nimbo.vpn.MyVpnService
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.*

class VpnQuickSettingsTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var preferencesManager: PreferencesManager

    companion object {
        private const val REQUEST_OPEN_APP = 1001
        private const val REQUEST_QUICK_CONNECT = 1002
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
        Log.d("VpnTile", "TileService created")
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d("VpnTile", "Tile onStartListening")

        startStateObserver()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        scope.coroutineContext.cancelChildren()
    }

    // 🔥 ПУЛЛИНГ состояния (вместо collect)
    private fun startStateObserver() {
        scope.launch {
            while (isActive) {
                updateTileState()
                delay(500) // обновление каждые 0.5 сек
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        Log.d("VpnTile", "Tile onClick")

        val currentState = VpnManager.state.value
        if (currentState == VpnState.CONNECTING) {
            updateTileState()
            return
        }

        val isVpnRunning = currentState == VpnState.CONNECTED

        if (!isVpnRunning) {
            val selectedServer = loadLastSelectedServer()

            if (selectedServer == null) {
                Log.d("VpnTile", "No server found → opening app")
                openApp()
                return
            }

            Log.d(
                "VpnTile",
                "Connecting to: ${selectedServer.name} (${selectedServer.host}:${selectedServer.port})"
            )

            VpnManager.selectedServer = selectedServer

            preferencesManager.saveLastSelectedServer(selectedServer)

            try {
                val prepareIntent = VpnService.prepare(this)

                if (prepareIntent != null) {
                    startActivityAndCollapseCompat(
                        createQuickConnectIntent(selectedServer),
                        REQUEST_QUICK_CONNECT
                    )

                } else {
                    val intent = MyVpnService.createConnectIntent(this, selectedServer)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }

            } catch (e: Exception) {
                Log.e("VpnTile", "Failed to connect", e)
                openApp()
            }

        } else {
            Log.d("VpnTile", "Disconnecting")

            val intent = Intent(this, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_DISCONNECT
            }

            startService(intent)
        }
    }

    private fun loadLastSelectedServer(): Server? {
        var server = preferencesManager.loadLastSelectedServer()
        if (server != null) return server

        server = VpnManager.selectedServer
        if (server != null) return server

        val profiles = preferencesManager.loadProfiles()
        if (profiles.isEmpty()) return null

        val lastProfileUrl = preferencesManager.loadLastSelectedProfileUrl()
        val profile = profiles.find { it.url == lastProfileUrl } ?: profiles.firstOrNull()

        server = profile?.servers?.firstOrNull()

        server?.let {
            preferencesManager.saveLastSelectedServer(it)
        }

        return server
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openApp() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                          Intent.FLAG_ACTIVITY_SINGLE_TOP or
                          Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                it.addCategory(Intent.CATEGORY_LAUNCHER)
                startActivityAndCollapseCompat(it, REQUEST_OPEN_APP)
            }
        } catch (e: Exception) {
            Log.e("VpnTile", "Error opening app", e)
        }
    }

    private fun createQuickConnectIntent(server: Server): Intent {
        return Intent(this, QuickConnectActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(MyVpnService.createConnectIntent(this@VpnQuickSettingsTileService, server))
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val state = VpnManager.state.value

        tile.state = when (state) {
            VpnState.CONNECTED -> Tile.STATE_ACTIVE
            VpnState.DISCONNECTED -> Tile.STATE_INACTIVE
            VpnState.CONNECTING -> Tile.STATE_UNAVAILABLE
        }

        val selectedServerName = VpnManager.connectedServer.value?.name
            ?: preferencesManager.lastConnectedServerName.takeIf { it.isNotBlank() }

        tile.label = when (state) {
            VpnState.CONNECTED -> selectedServerName ?: "Nimbo"
            VpnState.CONNECTING -> "Подключение..."
            VpnState.DISCONNECTED -> "Nimbo"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                VpnState.CONNECTED -> "Подключено"
                VpnState.CONNECTING -> "Ожидание"
                VpnState.DISCONNECTED -> "Отключено"
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.icon = android.graphics.drawable.Icon.createWithResource(
                this,
                R.drawable.icon_quick_settings
            )
        }

        tile.updateTile()

        Log.d("VpnTile", "Tile updated: $state")
    }
}
