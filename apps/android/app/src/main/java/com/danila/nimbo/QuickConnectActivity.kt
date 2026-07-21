package com.danila.nimbo

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.MyVpnService
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import com.danila.nimbo.utils.Logger

/**
 * Активность для быстрого подключения VPN из ярлыка или центра управления
 * Работает в фоновом режиме без показа UI
 */
class QuickConnectActivity : Activity() {

    private lateinit var preferencesManager: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private var pendingServer: Server? = null

    companion object {
        private const val REQUEST_VPN_PERMISSION = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        handleQuickConnectIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickConnectIntent(intent)
    }

    private fun handleQuickConnectIntent(intent: Intent) {
        Logger.d("QuickConnect", "=== QuickConnectActivity started ===")
        Logger.d("QuickConnect", "Intent action: ${intent?.action}")
        Logger.d("QuickConnect", "Intent extras: ${intent?.extras}")

        // Пытаемся получить сервер несколькими способами
        var selectedServer: Server? = null

        // 1. Сначала пробуем получить из Intent (от TileService)
        selectedServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(MyVpnService.EXTRA_SERVER, Server::class.java)
        } else {
            @Suppress("DEPRECATION")
            (intent.getSerializableExtra(MyVpnService.EXTRA_SERVER) as? Server)
        }
        
        if (selectedServer != null) {
            Logger.d("QuickConnect", "✓ Loaded full server from EXTRA_SERVER: ${selectedServer.name}")
        } else {
            // Пробуем старый способ получения параметров по частям
            val host = intent.getStringExtra("server_host")
            val port = intent.getIntExtra("server_port", 0)
            val uuid = intent.getStringExtra("server_uuid")
            val protocol = intent.getStringExtra("server_protocol")
            val name = intent.getStringExtra("server_name")

            Logger.d("QuickConnect", "Intent server params: host=$host, port=$port, uuid=$uuid")

            if (!host.isNullOrBlank() && port > 0 && !uuid.isNullOrBlank()) {
                selectedServer = Server(
                    uuid = uuid,
                    host = host,
                    port = port,
                    protocol = protocol ?: "vless",
                    name = name ?: "Server",
                    flow = intent.getStringExtra("server_flow") ?: "",
                    security = intent.getStringExtra("server_security") ?: "",
                    network = intent.getStringExtra("server_network") ?: "",
                    path = intent.getStringExtra("server_path") ?: "",
                    sni = intent.getStringExtra("server_sni") ?: "",
                    fingerprint = intent.getStringExtra("server_fingerprint") ?: "",
                    alpn = intent.getStringExtra("server_alpn") ?: "",
                    publicKey = intent.getStringExtra("server_public_key") ?: "",
                    shortId = intent.getStringExtra("server_short_id") ?: "",
                    spiderX = intent.getStringExtra("server_spider_x") ?: ""
                )
                Logger.d("QuickConnect", "✓ Loaded server from partial Intent: ${selectedServer?.name ?: "null"}")
            }
        }

        // 2. Если не нашли в Intent, пробуем загрузить из PreferencesManager
        if (selectedServer == null) {
            selectedServer = preferencesManager.loadLastSelectedServer()
            Logger.d("QuickConnect", "✓ Loaded server from PreferencesManager: ${selectedServer?.name ?: "null"}")
        }

        // 3. Если не нашли, пробуем загрузить из VpnManager
        if (selectedServer == null) {
            selectedServer = VpnManager.selectedServer
            Logger.d("QuickConnect", "✓ Loaded server from VpnManager: ${selectedServer?.name ?: "null"}")
        }

        // 4. Если не нашли, загружаем из профилей
        if (selectedServer == null) {
            val profiles = preferencesManager.loadProfiles()
            Logger.d("QuickConnect", "Loaded ${profiles.size} profiles")
            // Пытаемся найти последний выбранный профиль
            val lastProfileUrl = preferencesManager.loadLastSelectedProfileUrl()
            Logger.d("QuickConnect", "Last profile URL: $lastProfileUrl")
            val profile = profiles.find { it.url == lastProfileUrl } ?: profiles.firstOrNull()
            selectedServer = profile?.servers?.firstOrNull()
            Logger.d("QuickConnect", "✓ Loaded server from profiles: ${selectedServer?.name ?: "null"}")
        }

        if (selectedServer != null) {
            Logger.d("QuickConnect", "Final server: ${selectedServer!!.name} (${selectedServer!!.host}:${selectedServer!!.port})")
            
            // Запрашиваем разрешение на VPN
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                Logger.d("QuickConnect", "VPN permission required, requesting permission")
                pendingServer = selectedServer
                VpnManager.selectedServer = selectedServer
                selectedServer.profileUrl?.let(preferencesManager::saveLastSelectedProfileUrl)

                @Suppress("DEPRECATION")
                startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION)
                return
            } else {
                connectSelectedServer(selectedServer)
            }
        } else {
            Logger.d("QuickConnect", "✗ No server configured, opening MainActivity")
            // Нет сервера - открываем приложение
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
        }

        finishSoon()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VPN_PERMISSION) return

        val server = pendingServer
        pendingServer = null

        if (resultCode == RESULT_OK && server != null) {
            Logger.d("QuickConnect", "VPN permission granted from system dialog")
            connectSelectedServer(server)
        } else {
            Logger.d("QuickConnect", "VPN permission denied or server missing")
            VpnManager.state.value = VpnState.DISCONNECTED
        }

        finishSoon()
    }

    private fun connectSelectedServer(server: Server) {
        Logger.d("QuickConnect", "VPN permission granted, connecting...")

        VpnManager.selectedServer = server
        server.profileUrl?.let(preferencesManager::saveLastSelectedProfileUrl)
        val serviceIntent = MyVpnService.createConnectIntent(this, server)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Logger.d("QuickConnect", "VPN service started successfully")
        } catch (e: Exception) {
            Logger.e("QuickConnect", "Failed to start VPN service", e)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }
    }

    private fun finishSoon() {
        handler.postDelayed({
            finish()
            Logger.d("QuickConnect", "=== QuickConnectActivity finished ===")
        }, 500)
    }
}

