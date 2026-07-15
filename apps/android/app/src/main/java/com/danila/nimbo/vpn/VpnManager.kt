package com.danila.nimbo.vpn

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.PreferencesManager

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class VpnRecoveryStatus {
    IDLE,
    PAUSED_BY_SCREEN,
    WAITING_FOR_NETWORK,
    RETRYING
}

object VpnManager {
    // Поток для сигнала об отмене всех фоновых задач (пинг, загрузка подписок)
    val cancelSystemJobsSignal = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state = mutableStateOf(VpnState.DISCONNECTED)
    val connectedServer = mutableStateOf<Server?>(null)
    val recoveryStatus = mutableStateOf(VpnRecoveryStatus.IDLE)
    val recoveryAttempt = mutableStateOf(0)

    val connectedSeconds = mutableStateOf(0)

    // Подсчёт трафика (байты) - общие за сессию
    val totalBytesUploaded = mutableStateOf(0L)
    val totalBytesDownloaded = mutableStateOf(0L)

    // Подсчёт пакетов - общие за сессию
    val totalPacketsUploaded = mutableStateOf(0L)
    val totalPacketsDownloaded = mutableStateOf(0L)

    // Время начала текущей сессии (epoch ms), 0 = нет активной сессии
    val sessionStartMs = mutableStateOf(0L)

    // Скорость (байт/сек) - текущая
    val uploadSpeed = mutableStateOf(0L)
    val downloadSpeed = mutableStateOf(0L)

    private var _selectedServer: Server? = null
    var selectedServer: Server?
        get() = _selectedServer
        set(value) {
            _selectedServer = value
            // Сохраняем последний выбранный сервер целиком (с TLS/Reality полями)
            value?.let { server ->
                try {
                    val context = NebulaGuardApplication.instance
                    val preferencesManager = PreferencesManager(context)
                    preferencesManager.saveLastSelectedServer(server)
                } catch (e: Exception) {
                    Log.e("VpnManager", "Error saving server: ${e.message}")
                }
            }
        }

    // Сброс статистики
    fun resetStats() {
        connectedSeconds.value = 0
        totalBytesUploaded.value = 0L
        totalBytesDownloaded.value = 0L
        totalPacketsUploaded.value = 0L
        totalPacketsDownloaded.value = 0L
        sessionStartMs.value = System.currentTimeMillis()
        uploadSpeed.value = 0L
        downloadSpeed.value = 0L
    }

    // Обновление скорости + постоянный учёт трафика
    fun updateSpeeds(uploadedDelta: Long, downloadedDelta: Long, timeDelta: Long) {
        if (timeDelta > 0) {
            uploadSpeed.value = uploadedDelta / timeDelta
            downloadSpeed.value = downloadedDelta / timeDelta
            totalBytesUploaded.value += uploadedDelta
            totalBytesDownloaded.value += downloadedDelta
            if (uploadedDelta > 0 || downloadedDelta > 0) {
                try {
                    PreferencesManager(NebulaGuardApplication.instance)
                        .addTraffic(uploadedDelta, downloadedDelta)
                } catch (e: Exception) {
                    Log.e("VpnManager", "Error accumulating traffic: ${e.message}")
                }
            }
        }
    }

    // Обновление счётчиков пакетов за сессию
    fun updatePackets(uploadedPacketsDelta: Long, downloadedPacketsDelta: Long) {
        if (uploadedPacketsDelta > 0) totalPacketsUploaded.value += uploadedPacketsDelta
        if (downloadedPacketsDelta > 0) totalPacketsDownloaded.value += downloadedPacketsDelta
    }

    /**
     * Загрузка последнего выбранного сервера из SharedPreferences
     */
    fun loadLastSelectedServer(context: Context): Server? {
        val preferencesManager = PreferencesManager(context)
        _selectedServer = preferencesManager.loadLastSelectedServer()
        return _selectedServer
    }
}
