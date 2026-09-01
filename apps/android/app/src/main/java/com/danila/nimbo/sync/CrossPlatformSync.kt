package com.danila.nimbo.sync

import com.danila.nimbo.ui.theme.ElementStyleMode

import android.os.Build
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val CROSS_SYNC_SCHEMA = "nimbo-cross-sync-v1"
private const val CROSS_SYNC_AAD_PREFIX = "nimbo-sync-v1:"
private const val MAX_SYNC_FRAME_BYTES = 2 * 1024 * 1024

enum class SyncDirection {
    @SerializedName("desktop_to_android")
    DESKTOP_TO_ANDROID,

    @SerializedName("android_to_desktop")
    ANDROID_TO_DESKTOP
}

data class SyncCategories(
    @SerializedName("subscriptions") val subscriptions: Boolean = true,
    @SerializedName("appearance") val appearance: Boolean = true,
    @SerializedName("connection") val connection: Boolean = true,
    @SerializedName("automation") val automation: Boolean = true
)

data class SyncSubscription(
    @SerializedName("url") val url: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("order") val order: Int = -1
)

data class SyncDeviceInfo(
    @SerializedName("name") val name: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("os_name") val osName: String,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("app_version") val appVersion: String? = null,
    @SerializedName("architecture") val architecture: String? = null
)

data class SyncAppearance(
    @SerializedName("theme_mode") val themeMode: String = "system",
    @SerializedName("ui_style") val uiStyle: String = "nimbo",
    @SerializedName("accent_color") val accentColor: String = "#75a7ff",
    @SerializedName("panel_brightness") val panelBrightness: Int = 100,
    @SerializedName("transparency") val transparency: Int = 0,
    @SerializedName("blur") val blur: Int = 25,
    @SerializedName("rounding") val rounding: Int = 100,
    @SerializedName("provider_theme") val providerTheme: Boolean = true,
    @SerializedName("show_subscription_logo") val showSubscriptionLogo: Boolean = true
)

data class SyncConnection(
    @SerializedName("kill_switch") val killSwitch: Boolean = false,
    @SerializedName("tls_fragmentation") val tlsFragmentation: Boolean = false,
    @SerializedName("show_speed_chart") val showSpeedChart: Boolean = true
)

data class SyncAutomation(
    @SerializedName("language") val language: String = "system",
    @SerializedName("ping_on_launch") val pingOnLaunch: Boolean = true,
    @SerializedName("update_channel") val updateChannel: String = "stable",
    @SerializedName("update_wifi_only") val updateWifiOnly: Boolean = false,
    @SerializedName("subscriptions_auto_update") val subscriptionsAutoUpdate: Boolean = true,
    @SerializedName("subscriptions_update_interval_hours") val subscriptionsUpdateIntervalHours: Int = 6,
    @SerializedName("subscriptions_update_on_launch") val subscriptionsUpdateOnLaunch: Boolean = false,
    @SerializedName("subscriptions_ping_after_update") val subscriptionsPingAfterUpdate: Boolean = false
)

data class CrossSyncBundle(
    @SerializedName("schema") val schema: String = CROSS_SYNC_SCHEMA,
    @SerializedName("platform") val platform: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("device_info") val deviceInfo: SyncDeviceInfo? = null,
    @SerializedName("subscriptions") val subscriptions: List<SyncSubscription> = emptyList(),
    @SerializedName("appearance") val appearance: SyncAppearance? = null,
    @SerializedName("connection") val connection: SyncConnection? = null,
    @SerializedName("automation") val automation: SyncAutomation? = null
) {
    fun inventory(): SyncInventory = SyncInventory(
        subscriptions = subscriptions.size,
        hasAppearance = appearance != null,
        hasConnection = connection != null,
        hasAutomation = automation != null
    )

    fun filtered(categories: SyncCategories): CrossSyncBundle = copy(
        subscriptions = if (categories.subscriptions) subscriptions else emptyList(),
        appearance = appearance.takeIf { categories.appearance },
        connection = connection.takeIf { categories.connection },
        automation = automation.takeIf { categories.automation }
    )
}

data class SyncInventory(
    @SerializedName("subscriptions") val subscriptions: Int,
    @SerializedName("has_appearance") val hasAppearance: Boolean,
    @SerializedName("has_connection") val hasConnection: Boolean,
    @SerializedName("has_automation") val hasAutomation: Boolean
) {
    fun isEmpty(): Boolean = subscriptions == 0 && !hasAppearance && !hasConnection && !hasAutomation
}

data class CrossSyncQr(
    val host: String,
    val port: Int,
    val sessionId: String,
    val key: ByteArray,
    val expiresAtMs: Long,
    val comparisonCode: String?,
    val bluetoothMac: String? = null,
    val preferredTransport: String? = null
)

data class EncryptedSyncEnvelope(
    @SerializedName("v") val version: Int = 1,
    @SerializedName("sid") val sessionId: String,
    @SerializedName("nonce") val nonce: String,
    @SerializedName("ciphertext") val ciphertext: String
)

data class SyncWireRequest(
    @SerializedName("action") val action: String,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("bundle") val bundle: CrossSyncBundle? = null,
    @SerializedName("direction") val direction: SyncDirection? = null,
    @SerializedName("categories") val categories: SyncCategories? = null
)

data class SyncWireResponse(
    @SerializedName("state") val state: String,
    @SerializedName("comparison_code") val comparisonCode: String? = null,
    @SerializedName("desktop_bundle") val desktopBundle: CrossSyncBundle? = null,
    @SerializedName("desktop_inventory") val desktopInventory: SyncInventory? = null,
    @SerializedName("desktop_device_info") val desktopDeviceInfo: SyncDeviceInfo? = null,
    @SerializedName("desktop_subscriptions") val desktopSubscriptions: List<String>? = null,
    @SerializedName("expires_at_ms") val expiresAtMs: Long? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("paired") val paired: Boolean = false,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("paired_key") val pairedKey: String? = null,
    @SerializedName("server_port") val serverPort: Int? = null,
    @SerializedName("applied") val applied: Boolean = false,
    @SerializedName("applied_categories") val appliedCategories: List<String>? = null,
    @SerializedName("added_subscriptions") val addedSubscriptions: List<String>? = null,
    @SerializedName("direction") val direction: SyncDirection? = null
)

data class PairedDesktopDevice(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("name") val name: String,
    @SerializedName("key") val key: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("paired_at_ms") val pairedAtMs: Long = 0,
    @SerializedName("last_sync_ms") val lastSyncMs: Long = 0,
    @SerializedName("last_seen_remote_sig") val lastSeenRemoteSig: String? = null,
    @SerializedName("last_exported_sig") val lastExportedSig: String? = null,
    @SerializedName("platform") val platform: String = "",
    @SerializedName("os_name") val osName: String? = null,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("app_version") val appVersion: String? = null,
    @SerializedName("architecture") val architecture: String? = null,
    @SerializedName("auto_sync") val autoSync: Boolean = true,
    @SerializedName("subscription_count") val lastSubscriptionCount: Int = 0,
    @SerializedName("subscription_names") val lastSubscriptionNames: List<String> = emptyList(),
    @SerializedName("bluetooth_mac") val bluetoothMac: String? = null
)

object CrossSyncProtocol {
    fun buildQrPayload(
        host: String,
        port: Int,
        sessionId: String,
        key: ByteArray,
        expiresAtMs: Long,
        comparisonCode: String? = null,
        bluetoothMac: String? = null,
        preferredTransport: String? = null
    ): String {
        require(isPrivateIpv4(host)) { "Для QR нужен локальный IPv4-адрес" }
        require(port in 1024..65535) { "Некорректный порт синхронизации" }
        require(key.size == 32) { "AES-256 key required" }
        fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        val query = buildList {
            add("v=1")
            add("host=${enc(host)}")
            add("port=$port")
            add("sid=${enc(sessionId)}")
            add("key=${enc(Base64.getUrlEncoder().withoutPadding().encodeToString(key))}")
            add("exp=$expiresAtMs")
            comparisonCode?.takeIf { it.isNotBlank() }?.let { add("code=${enc(it)}") }
            bluetoothMac?.takeIf { it.isNotBlank() }?.let { add("bt_mac=${enc(it)}") }
            preferredTransport?.takeIf { it.isNotBlank() }?.let { add("transport=${enc(it)}") }
        }.joinToString("&")
        return "nimbo-sync://pair?$query"
    }

    fun parseQr(raw: String, nowMs: Long = System.currentTimeMillis()): CrossSyncQr {
        val uri = runCatching { URI(raw.trim()) }
            .getOrElse { throw IllegalArgumentException("Некорректный QR синхронизации") }
        require(uri.scheme.equals("nimbo-sync", ignoreCase = true) && uri.host == "pair") {
            "Это не QR синхронизации Nimbo"
        }
        val query = parseQuery(uri.rawQuery.orEmpty())
        require(query["v"] == "1") { "Версия протокола не поддерживается" }
        val host = query["host"]?.trim().orEmpty()
        require(isPrivateIpv4(host)) { "QR указывает не на локальный адрес" }
        val port = query["port"]?.toIntOrNull()
            ?.takeIf { it in 1024..65535 }
            ?: throw IllegalArgumentException("Некорректный порт синхронизации")
        val sessionId = query["sid"]?.trim()?.takeIf { it.length in 1..128 }
            ?: throw IllegalArgumentException("В QR отсутствует сеанс")
        val key = runCatching { Base64.getUrlDecoder().decode(query["key"].orEmpty()) }
            .getOrElse { throw IllegalArgumentException("Повреждён ключ синхронизации") }
        require(key.size == 32) { "Повреждён ключ синхронизации" }
        val expiresAt = query["exp"]?.toLongOrNull()
            ?: throw IllegalArgumentException("В QR отсутствует срок действия")
        require(expiresAt > nowMs) { "QR уже устарел. Обновите его на втором устройстве" }
        require(expiresAt - nowMs <= 5 * 60_000L) { "Некорректный срок действия QR" }
        val bluetoothMac = query["bt_mac"]?.trim()?.takeIf { it.isNotBlank() }
        val transport = query["transport"]?.trim()?.takeIf { it.isNotBlank() }
        return CrossSyncQr(
            host = host,
            port = port,
            sessionId = sessionId,
            key = key,
            expiresAtMs = expiresAt,
            comparisonCode = query["code"]?.takeIf { it.length in 4..12 },
            bluetoothMac = bluetoothMac,
            preferredTransport = transport
        )
    }

    fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 169 && parts[1] == 254)
    }

    fun recommendDirection(android: SyncInventory, desktop: SyncInventory): SyncDirection? = when {
        android.subscriptions == 0 && desktop.subscriptions > 0 -> SyncDirection.DESKTOP_TO_ANDROID
        desktop.subscriptions == 0 && android.subscriptions > 0 -> SyncDirection.ANDROID_TO_DESKTOP
        android.isEmpty() && !desktop.isEmpty() -> SyncDirection.DESKTOP_TO_ANDROID
        desktop.isEmpty() && !android.isEmpty() -> SyncDirection.ANDROID_TO_DESKTOP
        else -> null
    }

    fun canonicalSubscriptionUrl(raw: String): String {
        val trimmed = raw.trim()
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching trimmed.lowercase()
            val host = uri.host?.lowercase() ?: return@runCatching trimmed.lowercase()
            val normalizedPath = (uri.rawPath ?: "").trimEnd('/')
            URI(
                scheme,
                uri.rawUserInfo,
                host,
                uri.port,
                normalizedPath,
                uri.rawQuery,
                null
            ).toASCIIString()
        }.getOrDefault(trimmed.lowercase())
    }

    fun mergeSubscriptions(
        local: List<SyncSubscription>,
        incoming: List<SyncSubscription>
    ): List<SyncSubscription> {
        val merged = LinkedHashMap<String, SyncSubscription>()
        local.filter { it.url.isNotBlank() }.forEach { item ->
            merged[canonicalSubscriptionUrl(item.url)] = item.copy(url = item.url.trim())
        }
        incoming.filter { it.url.isNotBlank() }.forEach { item ->
            val key = canonicalSubscriptionUrl(item.url)
            val existing = merged[key]
            if (existing == null) {
                merged[key] = item.copy(url = item.url.trim())
            } else if (existing.name.isNullOrBlank() && !item.name.isNullOrBlank()) {
                merged[key] = existing.copy(name = item.name?.trim())
            }
        }
        return merged.values.toList()
    }

    fun subscriptionPreviewNames(
        subscriptions: List<SyncSubscription>,
        limit: Int = 8
    ): List<String> = subscriptions
        .take(limit.coerceIn(0, 20))
        .mapIndexed { index, item ->
            item.name
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("://") && it != item.url }
                ?.take(80)
                ?: "Подписка ${index + 1}"
        }

    private fun parseQuery(raw: String): Map<String, String> = raw
        .split('&')
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8.name())
            key to value
        }
        .toMap()
}

object CrossSyncCrypto {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encrypt(
        key: ByteArray,
        sessionId: String,
        plaintext: ByteArray,
        random: SecureRandom = SecureRandom()
    ): EncryptedSyncEnvelope {
        require(key.size == 32) { "AES-256 key required" }
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD((CROSS_SYNC_AAD_PREFIX + sessionId).encodeToByteArray())
        val encrypted = cipher.doFinal(plaintext)
        return EncryptedSyncEnvelope(
            sessionId = sessionId,
            nonce = encoder.encodeToString(nonce),
            ciphertext = encoder.encodeToString(encrypted)
        )
    }

    fun decrypt(key: ByteArray, envelope: EncryptedSyncEnvelope): ByteArray {
        require(key.size == 32 && envelope.version == 1) { "Unsupported encrypted frame" }
        val nonce = decoder.decode(envelope.nonce)
        require(nonce.size == 12) { "Invalid AES-GCM nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD((CROSS_SYNC_AAD_PREFIX + envelope.sessionId).encodeToByteArray())
        return cipher.doFinal(decoder.decode(envelope.ciphertext))
    }
}

class CrossSyncClient(
    private val gson: Gson = Gson()
) {
    companion object {
        val NIMBO_BLUETOOTH_UUID: java.util.UUID =
            java.util.UUID.fromString("0a23e590-7d6f-4428-9d89-9a74288b835e")
    }

    suspend fun exchange(
        qr: CrossSyncQr,
        request: SyncWireRequest,
        preferredTransport: String = "both"
    ): SyncWireResponse = withContext(Dispatchers.IO) {
        require(System.currentTimeMillis() < qr.expiresAtMs) { "Сеанс синхронизации истёк" }
        val mode = preferredTransport.lowercase()
        val mac = qr.bluetoothMac
        when {
            mode == "bluetooth" && !mac.isNullOrBlank() -> exchangeFrameBluetooth(mac, qr.key, qr.sessionId, request)
            mode == "wifi" -> exchangeFrame(qr.host, qr.port, qr.key, qr.sessionId, request)
            else -> {
                // "both" or default mode: try Wi-Fi first, fallback to Bluetooth if available
                runCatching { exchangeFrame(qr.host, qr.port, qr.key, qr.sessionId, request) }
                    .getOrElse { wifiErr ->
                        if (!mac.isNullOrBlank()) {
                            runCatching { exchangeFrameBluetooth(mac, qr.key, qr.sessionId, request) }
                                .getOrElse { throw wifiErr }
                        } else {
                            throw wifiErr
                        }
                    }
            }
        }
    }

    suspend fun exchangePaired(
        device: PairedDesktopDevice,
        request: SyncWireRequest,
        preferredTransport: String = "both"
    ): SyncWireResponse {
        val key = runCatching { Base64.getUrlDecoder().decode(device.key) }
            .getOrElse { throw IllegalArgumentException("Повреждён ключ устройства") }
        require(key.size == 32) { "Повреждён ключ устройства" }
        return withContext(Dispatchers.IO) {
            val mode = preferredTransport.lowercase()
            val mac = device.bluetoothMac
            // The resume id identifies the caller. Older builds used the remote id,
            // which happened to work for the first PC but collided with multiple peers.
            val callerId = request.deviceId?.takeIf { it.isNotBlank() } ?: device.deviceId
            val sessionId = "resume:$callerId"
            when {
                mode == "bluetooth" && !mac.isNullOrBlank() -> exchangeFrameBluetooth(mac, key, sessionId, request)
                mode == "wifi" -> exchangeFrame(device.host, device.port, key, sessionId, request)
                else -> {
                    runCatching { exchangeFrame(device.host, device.port, key, sessionId, request) }
                        .getOrElse { wifiErr ->
                            if (!mac.isNullOrBlank()) {
                                runCatching { exchangeFrameBluetooth(mac, key, sessionId, request) }
                                    .getOrElse { throw wifiErr }
                            } else {
                                throw wifiErr
                            }
                        }
                }
            }
        }
    }

    private suspend fun exchangeFrameBluetooth(
        macAddress: String,
        key: ByteArray,
        sessionId: String,
        request: SyncWireRequest
    ): SyncWireResponse = withContext(Dispatchers.IO) {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("Bluetooth недоступен на устройстве")
        if (!adapter.isEnabled) {
            throw IllegalStateException("Bluetooth выключен. Включите Bluetooth для подключения")
        }
        val device = runCatching { adapter.getRemoteDevice(macAddress) }
            .getOrElse { throw IllegalArgumentException("Некорректный Bluetooth-адрес компьютера") }

        val plaintext = gson.toJson(request).encodeToByteArray()
        require(plaintext.size <= MAX_SYNC_FRAME_BYTES) { "Слишком большой пакет синхронизации" }
        val envelope = CrossSyncCrypto.encrypt(key, sessionId, plaintext)
        val frame = gson.toJson(envelope).encodeToByteArray()
        require(frame.size <= MAX_SYNC_FRAME_BYTES) { "Слишком большой пакет синхронизации" }

        val socket = device.createRfcommSocketToServiceRecord(NIMBO_BLUETOOTH_UUID)
        socket.use { btSocket ->
            btSocket.connect()
            DataOutputStream(btSocket.outputStream.buffered()).use { output ->
                output.writeInt(frame.size)
                output.write(frame)
                output.flush()

                val input = DataInputStream(btSocket.inputStream.buffered())
                val length = input.readInt()
                require(length in 1..MAX_SYNC_FRAME_BYTES) { "Некорректный ответ синхронизации по Bluetooth" }
                val responseFrame = ByteArray(length)
                input.readFully(responseFrame)
                val responseEnvelope = gson.fromJson(
                    responseFrame.decodeToString(),
                    EncryptedSyncEnvelope::class.java
                )
                require(responseEnvelope.sessionId == sessionId) { "Ответ другого сеанса" }
                val responseJson = CrossSyncCrypto.decrypt(key, responseEnvelope).decodeToString()
                gson.fromJson(responseJson, SyncWireResponse::class.java)
            }
        }
    }

    private suspend fun exchangeFrame(
        host: String,
        port: Int,
        key: ByteArray,
        sessionId: String,
        request: SyncWireRequest
    ): SyncWireResponse = withContext(Dispatchers.IO) {
        val plaintext = gson.toJson(request).encodeToByteArray()
        require(plaintext.size <= MAX_SYNC_FRAME_BYTES) { "Слишком большой пакет синхронизации" }
        val envelope = CrossSyncCrypto.encrypt(key, sessionId, plaintext)
        val frame = gson.toJson(envelope).encodeToByteArray()
        require(frame.size <= MAX_SYNC_FRAME_BYTES) { "Слишком большой пакет синхронизации" }

        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5_000)
            socket.soTimeout = 8_000
            DataOutputStream(socket.getOutputStream().buffered()).use { output ->
                output.writeInt(frame.size)
                output.write(frame)
                output.flush()

                val input = DataInputStream(socket.getInputStream().buffered())
                val length = input.readInt()
                require(length in 1..MAX_SYNC_FRAME_BYTES) { "Некорректный ответ синхронизации" }
                val responseFrame = ByteArray(length)
                input.readFully(responseFrame)
                val responseEnvelope = gson.fromJson(
                    responseFrame.decodeToString(),
                    EncryptedSyncEnvelope::class.java
                )
                require(responseEnvelope.sessionId == sessionId) { "Ответ другого сеанса" }
                val responseJson = CrossSyncCrypto.decrypt(key, responseEnvelope).decodeToString()
                gson.fromJson(responseJson, SyncWireResponse::class.java)
            }
        }
    }
}

object AndroidCrossSyncBundleMapper {
    fun export(
        preferences: PreferencesManager,
        profiles: List<SubscriptionProfile>,
        deviceName: String = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" },
        nowMs: Long = System.currentTimeMillis()
    ): CrossSyncBundle {
        val themeMode = when {
            preferences.themeMode == 2 && preferences.pureBlackMode -> "black"
            preferences.themeMode == 1 -> "light"
            preferences.themeMode == 2 -> "dark"
            else -> "system"
        }
        // Акцент экспортируем только если пользователь реально задал кастомный цвет.
        // Иначе desktop применит "#000000"/дефолтный цвет как кастомный и сломает тему.
        val accent = if (!preferences.useDynamicColor && preferences.isCustomAccent && preferences.customAccentColor != 0) {
            String.format(
                Locale.ROOT,
                "#%06x",
                preferences.customAccentColor and 0x00FFFFFF
            )
        } else {
            ""
        }
        return CrossSyncBundle(
            platform = "android",
            deviceName = deviceName,
            createdAtMs = nowMs,
            deviceInfo = SyncDeviceInfo(
                name = deviceName,
                platform = "android",
                osName = "Android",
                osVersion = "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                appVersion = BuildConfig.VERSION_NAME,
                architecture = Build.SUPPORTED_ABIS.firstOrNull()
            ),
            subscriptions = profiles
                .filter { it.url.isNotBlank() }
                .mapIndexed { index, profile ->
                    SyncSubscription(
                        url = profile.url.trim(),
                        name = profile.customName ?: profile.name.takeIf(String::isNotBlank),
                        order = index
                    )
                },
            appearance = SyncAppearance(
                themeMode = themeMode,
                // Стили десктопа и телефона совпадают по названиям, поэтому
                // Signal и Nothing Dots переносятся как есть, а не сваливаются
                // в material_you.
                uiStyle = when (preferences.elementStyle) {
                    ElementStyleMode.MATERIAL_EXPRESSIVE.persistedValue -> "material_you"
                    ElementStyleMode.NOTHING_DOTS.persistedValue -> "dotted"
                    ElementStyleMode.SIGNAL.persistedValue -> "signal"
                    ElementStyleMode.MANGA.persistedValue -> "manga"
                    else -> "nimbo"
                },
                accentColor = accent,
                panelBrightness = (preferences.globalBrightness * 100f).toInt().coerceIn(50, 200),
                transparency = (preferences.globalTransparency * 100f).toInt().coerceIn(0, 100),
                blur = preferences.globalBlur.toInt().coerceIn(0, 80),
                rounding = (preferences.globalCorners * 100f).toInt().coerceIn(25, 200),
                providerTheme = preferences.useSubscriptionTheme,
                showSubscriptionLogo = preferences.showSubscriptionLogo
            ),
            connection = SyncConnection(
                killSwitch = preferences.killSwitch,
                tlsFragmentation = preferences.tlsFragment,
                showSpeedChart = preferences.showSpeed
            ),
            automation = SyncAutomation(
                language = preferences.appLanguage.ifBlank { "system" },
                pingOnLaunch = preferences.pingOnStartup,
                updateChannel = preferences.updateChannel.preferenceValue,
                updateWifiOnly = preferences.updateWifiOnly,
                subscriptionsAutoUpdate = preferences.subscriptionAutoUpdate,
                subscriptionsUpdateIntervalHours =
                    (preferences.subscriptionUpdateInterval / 3600).coerceIn(1, 168),
                subscriptionsUpdateOnLaunch = preferences.updateSubOnStartup,
                subscriptionsPingAfterUpdate = preferences.pingOnUpdate
            )
        )
    }

    fun applySettings(
        preferences: PreferencesManager,
        incoming: CrossSyncBundle,
        categories: SyncCategories
    ) {
        require(incoming.schema == CROSS_SYNC_SCHEMA) { "Несовместимый формат синхронизации" }
        if (categories.appearance) {
            incoming.appearance?.let { appearance ->
                preferences.themeMode = when (appearance.themeMode.lowercase()) {
                    "light" -> 1
                    "dark", "black" -> 2
                    else -> 0
                }
                preferences.pureBlackMode = appearance.themeMode.equals("black", ignoreCase = true)
                preferences.elementStyle = when (appearance.uiStyle) {
                    "material_you" -> ElementStyleMode.MATERIAL_EXPRESSIVE.persistedValue
                    "dotted" -> ElementStyleMode.NOTHING_DOTS.persistedValue
                    "signal" -> ElementStyleMode.SIGNAL.persistedValue
                    "manga" -> ElementStyleMode.MANGA.persistedValue
                    else -> ElementStyleMode.LIQUID_GLASS.persistedValue
                }
                parseAccent(appearance.accentColor)?.let { accent ->
                    preferences.useDynamicColor = false
                    preferences.isCustomAccent = true
                    preferences.customAccentColor = accent
                    preferences.customGradientColor1 = accent
                    preferences.customGradientCount = 1
                }
                preferences.globalBrightness = (appearance.panelBrightness / 100f).coerceIn(0.5f, 2f)
                preferences.globalTransparency = (appearance.transparency / 100f).coerceIn(0f, 1f)
                preferences.globalBlur = appearance.blur.toFloat().coerceIn(0f, 80f)
                preferences.globalCorners = (appearance.rounding / 100f).coerceIn(0.25f, 2f)
                preferences.useSubscriptionTheme = appearance.providerTheme
                preferences.showSubscriptionLogo = appearance.showSubscriptionLogo
            }
        }
        if (categories.connection) {
            incoming.connection?.let { connection ->
                preferences.killSwitch = connection.killSwitch
                preferences.tlsFragment = connection.tlsFragmentation
                preferences.showSpeed = connection.showSpeedChart
            }
        }
        if (categories.automation) {
            incoming.automation?.let { automation ->
                preferences.appLanguage = automation.language.takeIf { it in setOf("ru", "en") }
                    ?: ""
                preferences.pingOnStartup = automation.pingOnLaunch
                preferences.updateChannel = UpdateChannel.fromPreference(automation.updateChannel)
                preferences.updateWifiOnly = automation.updateWifiOnly
                preferences.subscriptionAutoUpdate = automation.subscriptionsAutoUpdate
                preferences.subscriptionUpdateInterval =
                    automation.subscriptionsUpdateIntervalHours.coerceIn(1, 168) * 3600
                preferences.updateSubOnStartup = automation.subscriptionsUpdateOnLaunch
                preferences.pingOnUpdate = automation.subscriptionsPingAfterUpdate
            }
        }
        if (categories.subscriptions && incoming.subscriptions.size >= 2) {
            reorderProfiles(preferences, incoming.subscriptions)
        }
    }

    private fun reorderProfiles(
        preferences: PreferencesManager,
        incoming: List<SyncSubscription>
    ) {
        val profiles = preferences.loadProfiles()
        if (profiles.size < 2) return
        val order = incoming
            .filter { it.order >= 0 && it.url.isNotBlank() }
            .associate { CrossSyncProtocol.canonicalSubscriptionUrl(it.url) to it.order }
        if (order.isEmpty()) return
        val sorted = profiles.sortedWith(
            compareBy { profile ->
                order[CrossSyncProtocol.canonicalSubscriptionUrl(profile.url)] ?: Int.MAX_VALUE
            }
        )
        if (sorted != profiles) preferences.saveProfiles(sorted)
    }

    fun missingSubscriptions(
        localProfiles: List<SubscriptionProfile>,
        incoming: CrossSyncBundle,
        categories: SyncCategories
    ): List<SyncSubscription> {
        if (!categories.subscriptions) return emptyList()
        val localKeys = localProfiles
            .map { CrossSyncProtocol.canonicalSubscriptionUrl(it.url) }
            .toHashSet()
        return incoming.subscriptions
            .filter { it.url.isNotBlank() }
            .distinctBy { CrossSyncProtocol.canonicalSubscriptionUrl(it.url) }
            .filter { CrossSyncProtocol.canonicalSubscriptionUrl(it.url) !in localKeys }
    }

    fun bundleSignature(bundle: CrossSyncBundle, categories: SyncCategories): String {
        val json = Gson().toJson(bundle.filtered(categories))
        val digest = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(json.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun parseAccent(raw: String): Int? {
        val normalized = raw.trim().removePrefix("#")
        if (!normalized.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return runCatching { (0xFF000000L or normalized.toLong(16)).toInt() }.getOrNull()
    }
}

data class PairedSyncResult(
    val unpaired: Boolean = false,
    val deviceName: String = "",
    val addedSubscriptions: Int = 0,
    val appliedCategories: List<String> = emptyList()
)

object PairedSyncEngine {
    private val client = CrossSyncClient()

    fun currentCategories(preferences: PreferencesManager) = SyncCategories(
        subscriptions = preferences.crossSyncSubscriptions,
        appearance = preferences.crossSyncAppearance,
        connection = preferences.crossSyncConnection,
        automation = preferences.crossSyncAutomation
    )

    /**
     * Односторонний цикл постоянной синхронизации: телефон шлёт свой бандл на ПК,
     * получает бандл ПК и применяет его, если он изменился с прошлого раза.
     * Возвращает результат; при [PairedSyncResult.unpaired] запись о ПК надо удалить.
     */
    suspend fun syncOnce(
        preferences: PreferencesManager,
        device: PairedDesktopDevice,
        addSubscription: suspend (url: String, name: String?) -> Unit
    ): PairedSyncResult {
        val profiles = preferences.loadProfiles()
        val exported = AndroidCrossSyncBundleMapper.export(preferences, profiles)
        val categories = currentCategories(preferences)
        val response = client.exchangePaired(
            device,
            SyncWireRequest(
                action = "hello",
                deviceId = preferences.getOrCreateCrossSyncDeviceId(),
                deviceName = exported.deviceName,
                bundle = exported.filtered(categories),
                categories = categories
            ),
            preferredTransport = preferences.crossSyncTransportMode
        )
        if (!response.paired || response.state == "unpaired") {
            return PairedSyncResult(unpaired = true, deviceName = device.name)
        }

        var lastSeenRemoteSig = device.lastSeenRemoteSig
        var added = 0
        var appliedCategories = emptyList<String>()
        val incoming = response.desktopBundle
        if (incoming != null) {
            val exportedSig = AndroidCrossSyncBundleMapper.bundleSignature(exported, categories)
            val localUnchanged = device.lastExportedSig == null ||
                device.lastExportedSig == exportedSig
            if (localUnchanged) {
                val incomingSig = AndroidCrossSyncBundleMapper.bundleSignature(incoming, categories)
                if (lastSeenRemoteSig != incomingSig) {
                    AndroidCrossSyncBundleMapper.applySettings(preferences, incoming, categories)
                    val missing =
                        AndroidCrossSyncBundleMapper.missingSubscriptions(profiles, incoming, categories)
                    missing.forEach { addSubscription(it.url, it.name) }
                    added = missing.size
                    appliedCategories = listOfNotNull(
                        "subscriptions".takeIf { categories.subscriptions },
                        "appearance".takeIf { categories.appearance },
                        "connection".takeIf { categories.connection },
                        "automation".takeIf { categories.automation }
                    )
                    lastSeenRemoteSig = incomingSig
                }
            }
        }

        val updated = device.copy(
            lastSyncMs = System.currentTimeMillis(),
            lastSeenRemoteSig = lastSeenRemoteSig,
            lastExportedSig = AndroidCrossSyncBundleMapper.bundleSignature(exported, categories),
            lastSubscriptionCount = response.desktopInventory?.subscriptions
                ?: incoming?.inventory()?.subscriptions ?: device.lastSubscriptionCount,
            lastSubscriptionNames = if (!response.desktopSubscriptions.isNullOrEmpty()) {
                response.desktopSubscriptions
            } else {
                incoming?.subscriptions?.map { it.name ?: it.url } ?: device.lastSubscriptionNames
            }
        )
        preferences.crossSyncPairedDevices =
            preferences.crossSyncPairedDevices.map { if (it.deviceId == device.deviceId) updated else it }
        preferences.crossSyncLastAt = updated.lastSyncMs
        preferences.crossSyncLastDevice = device.name
        return PairedSyncResult(
            unpaired = false,
            deviceName = device.name,
            addedSubscriptions = added,
            appliedCategories = appliedCategories
        )
    }

    suspend fun unpair(preferences: PreferencesManager, device: PairedDesktopDevice) {
        runCatching {
            client.exchangePaired(
                device,
                SyncWireRequest(
                    action = "unpair",
                    deviceId = preferences.getOrCreateCrossSyncDeviceId()
                )
            )
        }
        preferences.crossSyncPairedDevices =
            preferences.crossSyncPairedDevices.filterNot { it.deviceId == device.deviceId }
    }
}
