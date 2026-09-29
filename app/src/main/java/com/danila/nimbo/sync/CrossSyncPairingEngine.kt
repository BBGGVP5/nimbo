package com.danila.nimbo.sync

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CrossSyncPairingStage {
    IDLE,
    CONNECTING,
    WAITING_DESKTOP,
    CHOOSE_DIRECTION,
    READY_TO_IMPORT,
    WAITING_IMPORT_CONFIRMATION,
    COMPLETED
}

/**
 * Сопряжение и передача живут в синглтоне с собственным scope,
 * поэтому выход со страницы (или из приложения) не прерывает поток:
 * пара сохраняется сразу после commit, а весь перенос продолжается в фоне.
 */
object CrossSyncPairingEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = CrossSyncClient()

    var stage by mutableStateOf(CrossSyncPairingStage.IDLE)
    var qr by mutableStateOf<CrossSyncQr?>(null)
    var response by mutableStateOf<SyncWireResponse?>(null)
    var localBundle by mutableStateOf<CrossSyncBundle?>(null)
    var pendingDesktopBundle by mutableStateOf<CrossSyncBundle?>(null)
    var error by mutableStateOf<String?>(null)
    var offline by mutableStateOf(false)
    var addedSubscriptions by mutableIntStateOf(0)
    var sessionLifetimeMs by mutableLongStateOf(60_000L)
    var scanHandled by mutableStateOf(false)
    var nowMs by mutableLongStateOf(System.currentTimeMillis())
    var pairedDevices by mutableStateOf<List<PairedDesktopDevice>>(emptyList())

    private val ticker = scope.launch {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(250L)
        }
    }

    fun failSync(cause: Throwable) {
        error = mobileSyncError(cause)
        offline = isMobileSyncOffline(cause)
    }

    fun syncPairedDevice(preferences: PreferencesManager) {
        if (pairedDevices.isEmpty()) pairedDevices = preferences.crossSyncPairedDevices
    }

    fun reset() {
        error = null
        offline = false
        response = null
        qr = null
        localBundle = null
        pendingDesktopBundle = null
        addedSubscriptions = 0
        sessionLifetimeMs = 60_000L
        nowMs = System.currentTimeMillis()
        stage = CrossSyncPairingStage.IDLE
        scanHandled = false
    }

    private fun applyResponse(next: SyncWireResponse) {
        response = next
        error = next.message.takeIf { next.state == "rejected" }
        stage = when (next.state) {
            "awaiting_approval" -> CrossSyncPairingStage.WAITING_DESKTOP
            "paired" -> CrossSyncPairingStage.CHOOSE_DIRECTION
            "awaiting_import_confirmation" -> CrossSyncPairingStage.WAITING_IMPORT_CONFIRMATION
            "completed" -> CrossSyncPairingStage.COMPLETED
            "rejected", "cancelled", "expired" -> CrossSyncPairingStage.IDLE
            else -> stage
        }
    }

    private suspend fun pollUntilDecision(
        sessionQr: CrossSyncQr,
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        viewModel: MainViewModel
    ): SyncWireResponse {
        while (scope.isActive && System.currentTimeMillis() < sessionQr.expiresAtMs) {
            delay(800)
            val next = client.exchange(
                sessionQr,
                SyncWireRequest(action = "status"),
                preferredTransport = preferences.crossSyncTransportMode
            )
            applyResponse(next)
            when (next.state) {
                "paired" -> {
                    val direction = next.direction
                    if (direction != null) {
                        // ПК уже выбрал направление при подтверждении — продолжаем сами.
                        commit(direction, preferences, profiles, viewModel)
                    }
                    return next
                }
                "completed", "rejected", "cancelled", "expired" -> return next
            }
        }
        throw IllegalStateException("Сеанс истёк. Обновите QR на компьютере")
    }

    fun handleScanned(
        raw: String,
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        viewModel: MainViewModel
    ) {
        if (scanHandled) return
        scanHandled = true
        error = null
        stage = CrossSyncPairingStage.CONNECTING
        scope.launch {
            try {
                val parsed = CrossSyncProtocol.parseQr(raw)
                val scannedAt = System.currentTimeMillis()
                sessionLifetimeMs = (parsed.expiresAtMs - scannedAt).coerceAtLeast(1_000L)
                nowMs = scannedAt
                val exported = AndroidCrossSyncBundleMapper.export(preferences, profiles)
                qr = parsed
                localBundle = exported
                val hello = client.exchange(
                    parsed,
                    SyncWireRequest(
                        action = "hello",
                        deviceId = preferences.getOrCreateCrossSyncDeviceId(),
                        deviceName = exported.deviceName,
                        bundle = exported.filtered(currentCategories(preferences))
                    ),
                    preferredTransport = preferences.crossSyncTransportMode
                )
                applyResponse(hello)
                if (hello.state == "awaiting_approval") {
                    pollUntilDecision(parsed, preferences, profiles, viewModel)
                }
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                offline = isMobileSyncOffline(cause)
                stage = CrossSyncPairingStage.IDLE
            }
        }
    }

    fun pairWithDiscoveredPeer(
        peer: DiscoveredPeerDevice,
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        viewModel: MainViewModel
    ) {
        if (scanHandled) return
        scanHandled = true
        error = null
        stage = CrossSyncPairingStage.CONNECTING
        scope.launch {
            try {
                val keyBytes = (if (!peer.keyBase64.isNullOrBlank()) {
                    runCatching { java.util.Base64.getUrlDecoder().decode(peer.keyBase64) }.getOrNull()
                } else null)?.takeIf { it.size == 32 }
                    ?: throw IllegalArgumentException(
                        "Устройство не передало действительный одноразовый ключ. Обновите QR-код и попробуйте снова."
                    )

                val expiresAt = if (peer.expiresAtMs > System.currentTimeMillis()) {
                    peer.expiresAtMs
                } else {
                    System.currentTimeMillis() + 180_000L
                }

                val directQr = CrossSyncQr(
                    host = peer.host,
                    port = peer.port,
                    sessionId = peer.sessionId ?: "disc_${peer.deviceId}",
                    key = keyBytes,
                    expiresAtMs = expiresAt,
                    comparisonCode = peer.comparisonCode,
                    bluetoothMac = peer.bluetoothMac,
                    preferredTransport = peer.transport
                )

                val scannedAt = System.currentTimeMillis()
                sessionLifetimeMs = (directQr.expiresAtMs - scannedAt).coerceAtLeast(1_000L)
                nowMs = scannedAt
                val exported = AndroidCrossSyncBundleMapper.export(preferences, profiles)
                qr = directQr
                localBundle = exported
                val hello = client.exchange(
                    directQr,
                    SyncWireRequest(
                        action = "hello",
                        deviceId = preferences.getOrCreateCrossSyncDeviceId(),
                        deviceName = exported.deviceName,
                        bundle = exported.filtered(currentCategories(preferences))
                    ),
                    preferredTransport = preferences.crossSyncTransportMode
                )
                applyResponse(hello)
                if (hello.state == "awaiting_approval") {
                    pollUntilDecision(directQr, preferences, profiles, viewModel)
                }
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                offline = isMobileSyncOffline(cause)
                stage = CrossSyncPairingStage.IDLE
            }
        }
    }

    fun commit(
        direction: SyncDirection,
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        viewModel: MainViewModel
    ) {
        val sessionQr = qr ?: return
        val exported = localBundle ?: return
        error = null
        stage = if (direction == SyncDirection.DESKTOP_TO_ANDROID) {
            CrossSyncPairingStage.CONNECTING
        } else {
            CrossSyncPairingStage.WAITING_IMPORT_CONFIRMATION
        }
        scope.launch {
            try {
                val categories = currentCategories(preferences)
                val next = client.exchange(
                    sessionQr,
                    SyncWireRequest(
                        action = "commit",
                        deviceId = preferences.getOrCreateCrossSyncDeviceId(),
                        deviceName = exported.deviceName,
                        bundle = exported.filtered(categories),
                        direction = direction,
                        categories = categories
                    ),
                    preferredTransport = preferences.crossSyncTransportMode
                )
                applyResponse(next)
                persistPairingFrom(sessionQr, next, preferences)
                when (direction) {
                    SyncDirection.DESKTOP_TO_ANDROID -> {
                        pendingDesktopBundle = next.desktopBundle
                            ?: throw IllegalStateException("Компьютер не передал данные")
                        stage = CrossSyncPairingStage.READY_TO_IMPORT
                    }
                    SyncDirection.ANDROID_TO_DESKTOP -> {
                        val final = if (next.state == "completed") {
                            next
                        } else {
                            pollUntilDecision(sessionQr, preferences, profiles, viewModel)
                        }
                        if (final.state == "completed") {
                            preferences.crossSyncLastAt = System.currentTimeMillis()
                            preferences.crossSyncLastDevice = "Nimbo Desktop"
                            runCatching {
                                client.exchange(
                                    sessionQr,
                                    SyncWireRequest(
                                        action = "receipt",
                                        deviceId = preferences.getOrCreateCrossSyncDeviceId()
                                    ),
                                    preferredTransport = preferences.crossSyncTransportMode
                                )
                            }.onSuccess { receipt ->
                                persistPairingFrom(sessionQr, receipt, preferences)
                            }
                            finishCompleted()
                        } else if (final.state == "rejected") {
                            throw IllegalStateException(final.message ?: "Импорт отклонён на компьютере")
                        }
                    }
                }
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                offline = isMobileSyncOffline(cause)
                stage = if (response?.state in setOf("rejected", "cancelled", "expired")) {
                    CrossSyncPairingStage.IDLE
                } else {
                    CrossSyncPairingStage.CHOOSE_DIRECTION
                }
            }
        }
    }

    fun applyDesktopData(
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        viewModel: MainViewModel,
        context: Context
    ) {
        val sessionQr = qr ?: return
        val incoming = pendingDesktopBundle ?: return
        error = null
        stage = CrossSyncPairingStage.CONNECTING
        scope.launch {
            try {
                val languageBefore = preferences.appLanguage
                val categories = currentCategories(preferences)
                AndroidCrossSyncBundleMapper.applySettings(preferences, incoming, categories)
                val missing = AndroidCrossSyncBundleMapper.missingSubscriptions(
                    profiles,
                    incoming,
                    categories
                )
                missing.forEach { subscription ->
                    viewModel.addSubscription(subscription.url)
                    subscription.name?.takeIf { it.isNotBlank() }?.let { name ->
                        viewModel.renameProfile(subscription.url, name)
                    }
                }
                if (categories.subscriptions && incoming.subscriptions.size >= 2) {
                    viewModel.reorderProfiles(incoming.subscriptions.map { it.url })
                }
                addedSubscriptions = missing.size
                preferences.crossSyncLastAt = System.currentTimeMillis()
                preferences.crossSyncLastDevice = incoming.deviceName
                val receipt = client.exchange(
                    sessionQr,
                    SyncWireRequest(
                        action = "receipt",
                        deviceId = preferences.getOrCreateCrossSyncDeviceId()
                    ),
                    preferredTransport = preferences.crossSyncTransportMode
                )
                persistPairingFrom(sessionQr, receipt, preferences)
                finishCompleted()
                viewModel.showTopNotification(
                    if (missing.isEmpty()) {
                        "Настройки синхронизированы"
                    } else {
                        "Синхронизация завершена: добавлено ${missing.size}"
                    }
                )
                if (preferences.appLanguage != languageBefore) {
                    kotlinx.coroutines.delay(900)
                    (context as? android.app.Activity)?.recreate()
                }
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                offline = isMobileSyncOffline(cause)
                stage = CrossSyncPairingStage.READY_TO_IMPORT
            }
        }
    }

    fun applyIncomingBundle(
        incoming: CrossSyncBundle,
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        viewModel: MainViewModel,
        context: Context
    ) {
        scope.launch {
            try {
                val languageBefore = preferences.appLanguage
                val categories = currentCategories(preferences)
                AndroidCrossSyncBundleMapper.applySettings(preferences, incoming, categories)
                val missing = AndroidCrossSyncBundleMapper.missingSubscriptions(
                    profiles,
                    incoming,
                    categories
                )
                missing.forEach { subscription ->
                    viewModel.addSubscription(subscription.url)
                    subscription.name?.takeIf { it.isNotBlank() }?.let { name ->
                        viewModel.renameProfile(subscription.url, name)
                    }
                }
                if (categories.subscriptions && incoming.subscriptions.size >= 2) {
                    viewModel.reorderProfiles(incoming.subscriptions.map { it.url })
                }
                preferences.crossSyncLastAt = System.currentTimeMillis()
                preferences.crossSyncLastDevice = incoming.deviceName
                viewModel.showTopNotification(
                    if (missing.isEmpty()) {
                        "Настройки синхронизированы"
                    } else {
                        "Синхронизация завершена: добавлено ${missing.size}"
                    }
                )
                if (preferences.appLanguage != languageBefore) {
                    kotlinx.coroutines.delay(900)
                    (context as? android.app.Activity)?.recreate()
                }
            } catch (e: Exception) {
                Logger.e("CrossSync", "applyIncomingBundle failed: ${e.message}")
            }
        }
    }

    private fun persistPairingFrom(
        sessionQr: CrossSyncQr,
        receipt: SyncWireResponse,
        preferences: PreferencesManager
    ) {
        val key = receipt.pairedKey ?: return
        val deviceId = receipt.deviceId ?: return
        val info = receipt.desktopDeviceInfo
        val existing = preferences.crossSyncPairedDevices.firstOrNull { it.deviceId == deviceId }
        val prevLastSync = existing?.lastSyncMs ?: 0L
        val updated = PairedDesktopDevice(
            deviceId = deviceId,
            name = info?.name ?: existing?.name ?: "Nimbo Desktop",
            key = key,
            host = sessionQr.host,
            port = receipt.serverPort ?: sessionQr.port,
            pairedAtMs = existing?.pairedAtMs ?: System.currentTimeMillis(),
            lastSyncMs = if (existing != null) prevLastSync else 0L,
            lastSeenRemoteSig = existing?.lastSeenRemoteSig,
            lastExportedSig = existing?.lastExportedSig,
            platform = info?.platform ?: existing?.platform.orEmpty(),
            osName = info?.osName ?: existing?.osName,
            osVersion = info?.osVersion ?: existing?.osVersion,
            appVersion = info?.appVersion ?: existing?.appVersion,
            architecture = info?.architecture ?: existing?.architecture,
            autoSync = existing?.autoSync ?: preferences.crossSyncAutoSync,
            lastSubscriptionCount = receipt.desktopInventory?.subscriptions ?: existing?.lastSubscriptionCount ?: 0,
            lastSubscriptionNames = receipt.desktopSubscriptions ?: existing?.lastSubscriptionNames.orEmpty(),
            bluetoothMac = sessionQr.bluetoothMac ?: existing?.bluetoothMac
        )
        preferences.crossSyncPairedDevices = preferences.crossSyncPairedDevices.let { list ->
            // Desktop builds before the Beta 5 mesh fix returned this phone's
            // own id. Remove that corrupt local record when a real remote id is
            // received so existing subscriptions/settings stay untouched.
            val withoutLegacySelf = list.filterNot {
                it.deviceId == preferences.getOrCreateCrossSyncDeviceId() && it.deviceId != deviceId
            }
            if (withoutLegacySelf.any { it.deviceId == deviceId }) {
                withoutLegacySelf.map { if (it.deviceId == deviceId) updated else it }
            } else {
                withoutLegacySelf + updated
            }
        }
        pairedDevices = preferences.crossSyncPairedDevices
        Logger.i("CrossSync", "Desktop paired for persistent sync: $deviceId @ ${updated.host}:${updated.port}")
    }

    /** После завершения синхронизации стираем код проверки и сеанс. */
    private fun finishCompleted() {
        stage = CrossSyncPairingStage.COMPLETED
        qr = null
        response = null
    }

    private fun currentCategories(preferences: PreferencesManager) = SyncCategories(
        subscriptions = preferences.crossSyncSubscriptions,
        appearance = preferences.crossSyncAppearance,
        connection = preferences.crossSyncConnection,
        automation = preferences.crossSyncAutomation
    )
}

private fun mobileSyncError(cause: Throwable): String {
    val raw = cause.message.orEmpty()
    return when {
        raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
            "Компьютер не в сети. Проверьте, что устройства в одной Wi‑Fi сети и что Nimbo Desktop запущен"
        raw.contains("refused", ignoreCase = true) ->
            "Устройство не в сети. Откройте Nimbo Desktop на компьютере, чтобы продолжить синхронизацию"
        raw.isNotBlank() -> raw
        else -> "Не удалось выполнить синхронизацию"
    }
}

private fun isMobileSyncOffline(cause: Throwable): Boolean {
    val raw = cause.message.orEmpty()
    return raw.contains("refused", ignoreCase = true) ||
        raw.contains("timed out", ignoreCase = true) ||
        raw.contains("timeout", ignoreCase = true)
}
