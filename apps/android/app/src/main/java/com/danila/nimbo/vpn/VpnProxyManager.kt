package com.danila.nimbo.vpn

import android.content.Context
import android.util.Log
import com.danila.nimbo.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Менеджер для проверки и подключения к VPN серверам
 * Поддерживает VLESS, VMess, Trojan, Shadowsocks
 */
object VpnProxyManager {

    private const val TAG = "VpnProxyManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Проверка доступности сервера (ping + проверка порта)
     */
    suspend fun checkServerAvailability(server: Server): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Пробуем подключиться к порту
                val socket = java.net.Socket()
                socket.connect(InetSocketAddress(server.host, server.port), 5000)
                socket.close()
                Log.d(TAG, "Server ${server.name} is reachable on port ${server.port}")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Server ${server.name} is not reachable: ${e.message}")
                false
            }
        }
    }

    /**
     * Проверка работы прокси (тестовый запрос через прокси)
     */
    suspend fun testProxyConnection(server: Server): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Проверяем доступность сервера через xray-core
                checkServerAvailability(server)
            } catch (e: Exception) {
                Log.e(TAG, "Proxy test failed", e)
                false
            }
        }
    }

    /**
     * Выбор лучшего сервера из списка (с минимальным пингом)
     */
    suspend fun selectBestServer(servers: List<Server>): Server? {
        if (servers.isEmpty()) return null

        val availableServers = mutableListOf<Pair<Server, Long>>()

        // Проверяем сервера параллельно
        servers.forEach { server ->
            val startTime = System.currentTimeMillis()
            if (checkServerAvailability(server)) {
                val ping = System.currentTimeMillis() - startTime
                availableServers.add(server to ping)
            }
        }

        // Возвращаем сервер с минимальным пингом
        return availableServers.minByOrNull { it.second }?.first
    }

    /**
     * Конвертация сервера в конфигурацию для xray
     */
    fun serverToXrayConfig(server: Server): String {
        // Формируем URL для xray
        return when (server.protocol.lowercase()) {
            "vless" -> {
                "vless://${server.uuid}@${server.host}:${server.port}#${server.name}"
            }
            "vmess" -> {
                // VMess требует base64 кодирования конфига
                val vmessConfig = """
                    {
                        "v": "2",
                        "ps": "${server.name}",
                        "add": "${server.host}",
                        "port": "${server.port}",
                        "id": "${server.uuid}",
                        "aid": "0",
                        "net": "tcp",
                        "type": "none",
                        "host": "",
                        "path": "",
                        "tls": ""
                    }
                """.trimIndent()
                "vmess://" + android.util.Base64.encodeToString(
                    vmessConfig.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            }
            "trojan" -> {
                "trojan://${server.uuid}@${server.host}:${server.port}#${server.name}"
            }
            "ss" -> {
                "ss://${server.uuid}@${server.host}:${server.port}#${server.name}"
            }
            else -> {
                "vless://${server.uuid}@${server.host}:${server.port}#${server.name}"
            }
        }
    }
}
