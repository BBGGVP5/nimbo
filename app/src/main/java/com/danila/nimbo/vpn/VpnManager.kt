package com.danila.nimbo.vpn

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.TrafficHistory

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
    val autoSelection = mutableStateOf(AutoSelectionProgress())
    val autoProfileUrl = mutableStateOf<String?>(null)
    internal val connectionOperations = ConnectionOperationGuard()

    fun selectManualServer(context: Context, server: Server) {
        connectionOperations.invalidate()
        val preferences = PreferencesManager(context)
        preferences.saveConnectionMode(null)
        autoProfileUrl.value = null
        autoSelection.value = AutoSelectionProgress()
        selectedServer = server
        server.profileUrl?.let(preferences::saveLastSelectedProfileUrl)
        MyVpnService.notifySelectionChanged(context)
    }

    fun restoreConnectionMode(context: Context) {
        val preferences = PreferencesManager(context)
        autoProfileUrl.value = preferences.autoServerSelectionProfileUrl
        if (state.value == VpnState.DISCONNECTED) {
            autoSelection.value = AutoSelectionProgress(selected = preferences.autoServerSelectionDesired)
        }
    }
    // Поток для сигнала об отмене всех фоновых задач (пинг, загрузка подписок)
    val cancelSystemJobsSignal = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state = mutableStateOf(VpnState.DISCONNECTED)
    val connectedServer = mutableStateOf<Server?>(null)
    val recoveryStatus = mutableStateOf(VpnRecoveryStatus.IDLE)
    val recoveryAttempt = mutableStateOf(0)

    /**
     * Последняя ошибка подключения, из-за которой сервис сдался. UI показывает
     * её диалогом с кнопкой «Скопировать логи»; очищается при новой попытке.
     */
    val lastConnectionError = mutableStateOf<ConnectionFailure?>(null)

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
    val liveSpeedAvailable = mutableStateOf(false)

    val trafficMeasurementScope = mutableStateOf(TrafficMeasurementScope.UNAVAILABLE)
    val nativeTrafficTelemetry = mutableStateOf<NativeTrafficTelemetry?>(null)
    val activeAdBlockingEnabled = mutableStateOf<Boolean?>(null)

    fun clearLiveTelemetry() {
        clearLiveSpeeds()
        nativeTrafficTelemetry.value = null
        activeAdBlockingEnabled.value = null
    }

    fun clearLiveSpeeds() {
        liveSpeedAvailable.value = false
        uploadSpeed.value = 0L
        downloadSpeed.value = 0L
    }

    private val selectedServerValue = mutableStateOf<Server?>(null)
    var selectedServer: Server?
        get() = selectedServerValue.value
        set(value) {
            selectedServerValue.value = value
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
        clearLiveSpeeds()
        nativeTrafficTelemetry.value = null
        trafficMeasurementScope.value = TrafficMeasurementScope.UNAVAILABLE
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
            liveSpeedAvailable.value = true
            uploadSpeed.value = uploadedDelta / timeDelta
            downloadSpeed.value = downloadedDelta / timeDelta
            totalBytesUploaded.value += uploadedDelta
            totalBytesDownloaded.value += downloadedDelta
            // История для графиков: точка скорости на каждый тик и расход по дням.
            TrafficHistory.recordSpeed(uploadSpeed.value, downloadSpeed.value)
            if (uploadedDelta > 0 || downloadedDelta > 0) {
                TrafficHistory.recordTraffic(uploadedDelta, downloadedDelta)
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
        restoreConnectionMode(context)
        val preferencesManager = PreferencesManager(context)
        selectedServerValue.value = preferencesManager.loadLastSelectedServer()
        return selectedServerValue.value
    }
}

