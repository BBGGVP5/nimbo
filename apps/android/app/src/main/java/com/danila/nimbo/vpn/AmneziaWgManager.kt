package com.danila.nimbo.vpn

import android.net.Network
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.danila.nimbo.awg.AmneziaWgLibrary
import com.danila.nimbo.awg.AwgConfigBuilder
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Запускает и останавливает туннель на нативном движке AmneziaWG
 * (libwg-go.so). Вызовы идут на фоновом диспетчере: создание TUN и
 * инициализация ядра — блокирующие операции.
 */
object AmneziaWgManager {

    private const val TAG = "AmneziaWgManager"

    var isConnected = false
        private set

    var connectionError: String? = null
        private set

    private var tunnelHandle = -1
    private var tunInterface: ParcelFileDescriptor? = null

    suspend fun connect(
        vpnService: VpnService,
        server: Server,
        underlyingNetwork: Network? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val settings = AwgConfigBuilder.buildSettings(server)
            if (settings == null) {
                connectionError = "Недостаточно данных для AmneziaWG: нужны PrivateKey и PublicKey"
                Logger.e(TAG, connectionError ?: "")
                return@withContext false
            }

            AmneziaWgLibrary.ensureLoaded()
            val tun = VpnInterfaceBuilder.establish(vpnService, underlyingNetwork)
            tunInterface = tun

            val handle = AmneziaWgLibrary.awgTurnOn("nimbo0", tun.fd, settings)
            if (handle < 0) {
                connectionError = "AmneziaWG не запустился (код $handle)"
                Logger.e(TAG, "awgTurnOn failed with code $handle")
                disconnect()
                return@withContext false
            }
            tunnelHandle = handle

            // Сокет ядра не должен уходить в собственный VPN.
            listOf(
                AmneziaWgLibrary.awgGetSocketV4(handle),
                AmneziaWgLibrary.awgGetSocketV6(handle)
            ).filter { it >= 0 }.forEach { fd ->
                if (!VpnInterfaceBuilder.protectSocket(vpnService, underlyingNetwork, fd)) {
                    Logger.w(TAG, "Could not protect AmneziaWG socket (fd=$fd)")
                }
            }

            isConnected = true
            connectionError = null
            Logger.i(TAG, "AmneziaWG tunnel started (handle=$handle, ${server.name})")
            true
        } catch (e: CancellationException) {
            disconnect()
            throw e
        } catch (e: Exception) {
            connectionError = e.message ?: e.toString()
            Logger.e(TAG, "AmneziaWG connection error", e)
            disconnect()
            false
        }
    }

    fun disconnect() {
        if (tunnelHandle >= 0) {
            runCatching { AmneziaWgLibrary.awgTurnOff(tunnelHandle) }
                .onFailure { Logger.w(TAG, "awgTurnOff failed: ${it.message}") }
            tunnelHandle = -1
        }
        runCatching { tunInterface?.close() }
        tunInterface = null
        isConnected = false
    }

    fun recordConnectionFailure(message: String) {
        connectionError = message
    }

    /**
     * Время с последнего handshake в секундах, либо -1, если туннель не
     * запущен или handshake ещё не было (UAPI-поле last_handshake_time_sec).
     */
    fun lastHandshakeSeconds(): Long {
        val handle = tunnelHandle
        if (handle < 0) return -1
        val config = runCatching { AmneziaWgLibrary.awgGetConfig(handle) }.getOrNull() ?: return -1
        return config.lineSequence()
            .firstOrNull { it.startsWith("last_handshake_time_sec=") }
            ?.substringAfter('=')
            ?.trim()
            ?.toLongOrNull()
            ?: 0L
    }
}
