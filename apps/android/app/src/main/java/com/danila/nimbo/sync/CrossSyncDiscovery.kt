package com.danila.nimbo.sync

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private const val PLACEHOLDER_BLUETOOTH_MAC = "02:00:00:00:00:00"

/**
 * Начиная с Android 12 обращения к спаренным устройствам и RFCOMM требуют
 * BLUETOOTH_CONNECT. Без явной проверки система бросает SecurityException,
 * и обнаружение молча умирает вместе с корутиной.
 */
private fun hasBluetoothConnectPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        NebulaGuardApplication.instance,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
}

const val DISCOVERY_UDP_PORT = 42002
const val DEFAULT_SYNC_TCP_PORT = 42000

data class DiscoveredPeerDevice(
    val deviceId: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int = DEFAULT_SYNC_TCP_PORT,
    val bluetoothMac: String? = null,
    val transport: String = "wifi",
    val lastSeenMs: Long = System.currentTimeMillis(),
    val keyBase64: String? = null,
    val sessionId: String? = null,
    val comparisonCode: String? = null,
    val expiresAtMs: Long = 0L
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        (expiresAtMs > 0 && nowMs > expiresAtMs) || (nowMs - lastSeenMs > 12_000L)
}

data class DiscoveryBeaconPacket(
    @SerializedName("type") val type: String = "NIMBO_DISCOVERY_V1",
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("port") val port: Int = DEFAULT_SYNC_TCP_PORT,
    @SerializedName("bt_mac") val btMac: String? = null,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("key") val key: String,
    @SerializedName("code") val comparisonCode: String,
    @SerializedName("exp") val expiresAtMs: Long
)

data class IncomingPairingRequest(
    val requestId: String,
    val peerName: String,
    val peerDeviceId: String,
    val platform: String,
    val comparisonCode: String,
    val incomingBundle: CrossSyncBundle?,
    val onAccept: () -> Unit,
    val onReject: () -> Unit
)

object CrossSyncDiscoveryEngine {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private var bluetoothScanJob: Job? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeerDevice>> = _discoveredPeers.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var currentSessionKey: ByteArray = ByteArray(32)
    private var currentSessionId: String = ""
    private var currentComparisonCode: String = ""
    private var currentExpiresAtMs: Long = 0L

    @Synchronized
    fun generateNewBeaconSession(): DiscoveryBeaconPacket {
        val random = SecureRandom()
        val keyBytes = ByteArray(32).also { random.nextBytes(it) }
        currentSessionKey = keyBytes
        currentSessionId = "disc_${System.currentTimeMillis()}_${random.nextInt(100000)}"
        currentComparisonCode = String.format(java.util.Locale.US, "%06d", random.nextInt(1_000_000))
        currentExpiresAtMs = System.currentTimeMillis() + 180_000L // 3 minutes validity

        return DiscoveryBeaconPacket(
            deviceId = "",
            deviceName = "",
            platform = "android",
            port = DEFAULT_SYNC_TCP_PORT,
            btMac = getBluetoothMac(),
            sessionId = currentSessionId,
            key = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes),
            comparisonCode = currentComparisonCode,
            expiresAtMs = currentExpiresAtMs
        )
    }

    @Synchronized
    fun activeBeaconSession(forceNew: Boolean = false): DiscoveryBeaconPacket {
        val now = System.currentTimeMillis()
        if (forceNew || currentSessionId.isBlank() || currentExpiresAtMs <= now + 5_000L) {
            return generateNewBeaconSession()
        }
        return DiscoveryBeaconPacket(
            deviceId = "",
            deviceName = "",
            platform = "android",
            port = DEFAULT_SYNC_TCP_PORT,
            btMac = getBluetoothMac(),
            sessionId = currentSessionId,
            key = Base64.getUrlEncoder().withoutPadding().encodeToString(currentSessionKey),
            comparisonCode = currentComparisonCode,
            expiresAtMs = currentExpiresAtMs
        )
    }

    @Synchronized
    fun sessionKeyFor(sessionId: String, nowMs: Long = System.currentTimeMillis()): ByteArray? =
        currentSessionKey.copyOf().takeIf {
            sessionId == currentSessionId && nowMs < currentExpiresAtMs && it.size == 32
        }

    fun createLocalQrPayload(preferences: PreferencesManager, forceNew: Boolean = false): String {
        val host = localPrivateIpv4()
            ?: throw IllegalStateException("Подключите оба устройства к одной локальной Wi-Fi сети")
        val beacon = activeBeaconSession(forceNew)
        return CrossSyncProtocol.buildQrPayload(
            host = host,
            port = DEFAULT_SYNC_TCP_PORT,
            sessionId = beacon.sessionId,
            key = Base64.getUrlDecoder().decode(beacon.key),
            expiresAtMs = beacon.expiresAtMs,
            comparisonCode = beacon.comparisonCode,
            bluetoothMac = beacon.btMac,
            preferredTransport = preferences.crossSyncTransportMode
        )
    }

    fun initBackground(context: Context) {
        initBackground(PreferencesManager(context.applicationContext))
    }

    fun initBackground(preferences: PreferencesManager) {
        val myDeviceId = preferences.getOrCreateCrossSyncDeviceId()
        val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                    broadcast = true
                    soTimeout = 4000
                }
            }.onSuccess { socket ->
                socket.use { datagramSocket ->
                    val buffer = ByteArray(4096)
                    while (isActive) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            datagramSocket.receive(packet)
                            val senderHost = packet.address.hostAddress ?: continue
                            val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            handleIncomingBeacon(json, senderHost, myDeviceId, preferences, datagramSocket, packet.address)
                        } catch (_: Exception) {
                            // socket timeout or read error, continue loop
                        }
                    }
                }
            }.onFailure { err ->
                Logger.e("CrossSyncDiscovery", "Background UDP listen failed: ${err.message}")
            }
        }
    }

    fun startDiscovery(preferences: PreferencesManager) {
        _isSearching.value = true
        _discoveredPeers.value = emptyList()

        val myDeviceId = preferences.getOrCreateCrossSyncDeviceId()
        val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val beaconTemplate = activeBeaconSession()

        // 1. Ensure UDP Listener on 42002 is active
        if (listenJob == null || listenJob?.isActive == false) {
            initBackground(preferences)
        }

        // 2. Start UDP Broadcast emitter
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            while (isActive) {
                try {
                    val myBtMac = getBluetoothMac()
                    val beacon = beaconTemplate.copy(
                        deviceId = myDeviceId,
                        deviceName = myDeviceName,
                        btMac = myBtMac
                    )
                    val json = gson.toJson(beacon).toByteArray(Charsets.UTF_8)
                    val broadcastAddrs = getBroadcastAddresses()
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        for (addr in broadcastAddrs) {
                            runCatching {
                                val packet = DatagramPacket(json, json.size, addr, DISCOVERY_UDP_PORT)
                                socket.send(packet)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.d("CrossSyncDiscovery", "Broadcast tick error: ${e.message}")
                }
                delay(1500)
            }
        }

        // 3. Scan Bluetooth bonded and nearby devices
        bluetoothScanJob?.cancel()
        bluetoothScanJob = scope.launch {
            scanBluetoothDevices(preferences)
        }

        // 4. Stale peer cleanup loop
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isActive) {
                delay(3000)
                val now = System.currentTimeMillis()
                _discoveredPeers.value = _discoveredPeers.value.filterNot { it.isExpired(now) }
            }
        }
    }

    fun stopDiscovery() {
        _isSearching.value = false
        broadcastJob?.cancel()
        broadcastJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        bluetoothScanJob?.cancel()
        bluetoothScanJob = null
        // Note: listenJob stays alive to respond to discovery requests from other devices in background
    }

    private fun handleIncomingBeacon(
        rawJson: String,
        senderHost: String,
        myDeviceId: String,
        preferences: PreferencesManager,
        socket: DatagramSocket,
        senderAddr: InetAddress
    ) {
        val beacon = runCatching { gson.fromJson(rawJson, DiscoveryBeaconPacket::class.java) }.getOrNull() ?: return
        if (beacon.deviceId == myDeviceId || beacon.deviceId.isBlank()) return

        // If the packet is a broadcast/ping and we are in background or searching, reply with direct unicast pong
        if (beacon.type == "NIMBO_DISCOVERY_V1" || beacon.type == "NIMBO_DISCOVERY_PING") {
            runCatching {
                // A discovery reply must advertise the same one-time key that the
                // embedded server will use. Rotating it here made every QR/pong stale.
                val myBeacon = activeBeaconSession().copy(
                    type = "NIMBO_DISCOVERY_PONG",
                    deviceId = myDeviceId,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    btMac = getBluetoothMac()
                )
                val replyBytes = gson.toJson(myBeacon).toByteArray(Charsets.UTF_8)
                val replyPacket = DatagramPacket(replyBytes, replyBytes.size, senderAddr, DISCOVERY_UDP_PORT)
                socket.send(replyPacket)
            }
        }

        // If this device is actively searching on screen, add/update the peer in discoveredPeers
        if (_isSearching.value) {
            val peer = DiscoveredPeerDevice(
                deviceId = beacon.deviceId,
                name = beacon.deviceName.ifBlank { "Nimbo Peer" },
                platform = beacon.platform.ifBlank { "unknown" },
                host = senderHost,
                port = if (beacon.port in 1024..65535) beacon.port else DEFAULT_SYNC_TCP_PORT,
                bluetoothMac = beacon.btMac?.takeIf { it.isNotBlank() },
                transport = if (!beacon.btMac.isNullOrBlank()) "both" else "wifi",
                lastSeenMs = System.currentTimeMillis(),
                keyBase64 = beacon.key,
                sessionId = beacon.sessionId,
                comparisonCode = beacon.comparisonCode,
                expiresAtMs = beacon.expiresAtMs
            )

            val current = _discoveredPeers.value
            val updated = if (current.any { it.deviceId == peer.deviceId }) {
                current.map { if (it.deviceId == peer.deviceId) peer else it }
            } else {
                current + peer
            }
            _discoveredPeers.value = updated
        }
    }

    // Проверка разрешения живёт в hasBluetoothConnectPermission(), а lint видит
    // только inline-checkSelfPermission, поэтому подавляем предупреждение здесь.
    @SuppressLint("MissingPermission")
    private fun scanBluetoothDevices(preferences: PreferencesManager) {
        if (!hasBluetoothConnectPermission()) return
        runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return

            val bonded = adapter.bondedDevices ?: emptySet()
            for (dev in bonded) {
                val mac = dev.address ?: continue
                val name = dev.name ?: "Bluetooth Device"
                val isNimboPeer = name.contains("Nimbo", ignoreCase = true) ||
                    preferences.crossSyncPairedDevices.any { it.bluetoothMac == mac }

                if (isNimboPeer) {
                    val peer = DiscoveredPeerDevice(
                        deviceId = "bt_${mac.replace(":", "")}",
                        name = name,
                        platform = "desktop",
                        host = "127.0.0.1",
                        port = DEFAULT_SYNC_TCP_PORT,
                        bluetoothMac = mac,
                        transport = "bluetooth",
                        lastSeenMs = System.currentTimeMillis()
                    )
                    val current = _discoveredPeers.value
                    if (!current.any { it.bluetoothMac == mac || it.deviceId == peer.deviceId }) {
                        _discoveredPeers.value = current + peer
                    }
                }
            }
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getBluetoothMac(): String? {
        if (!hasBluetoothConnectPermission()) return null
        return runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            if (adapter.isEnabled) adapter.address else null
        }.getOrNull()
            // Обычным приложениям система с Android 6 отдаёт вместо настоящего
            // адреса заглушку 02:00:00:00:00:00. Раздавать её в маяке нельзя:
            // все устройства выглядели бы как один и тот же bluetooth-пир.
            ?.takeIf { it.isNotBlank() && !it.equals(PLACEHOLDER_BLUETOOTH_MAC, ignoreCase = true) }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return list
            for (iface in interfaces.asSequence()) {
                if (iface.isLoopback || !iface.isUp) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) list.add(broadcast)
                }
            }
        } catch (_: Exception) {}
        return list.distinct()
    }

    private fun localPrivateIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.mapNotNull { address -> address.hostAddress?.substringBefore('%') }
            ?.firstOrNull(CrossSyncProtocol::isPrivateIpv4)
    }.getOrNull()
}

object CrossSyncEmbeddedServer {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tcpServerJob: Job? = null
    private var btServerJob: Job? = null
    private var isRunning = false
    private val pairingSessions = ConcurrentHashMap<String, ServerPairingSession>()

    private data class ServerPairingSession(
        val id: String,
        val key: ByteArray,
        val comparisonCode: String,
        val expiresAtMs: Long,
        var peerHost: String,
        var peerDeviceId: String? = null,
        var peerName: String = "Nimbo",
        var peerBundle: CrossSyncBundle? = null,
        var approved: Boolean = false,
        var rejected: Boolean = false,
        var state: String = "awaiting_approval",
        var pairedKey: String? = null
    )

    private val _incomingRequest = MutableStateFlow<IncomingPairingRequest?>(null)
    val incomingRequest: StateFlow<IncomingPairingRequest?> = _incomingRequest.asStateFlow()

    fun initBackground(context: Context) {
        val preferences = PreferencesManager(context.applicationContext)
        val profiles = preferences.loadProfiles()
        start(preferences, profiles) { receivedBundle ->
            runCatching {
                val categories = PairedSyncEngine.currentCategories(preferences)
                AndroidCrossSyncBundleMapper.applySettings(preferences, receivedBundle, categories)
                val missing = AndroidCrossSyncBundleMapper.missingSubscriptions(profiles, receivedBundle, categories)
                if (missing.isNotEmpty()) {
                    val current = preferences.loadProfiles()
                    val newProfiles = missing.map { sub ->
                        com.danila.nimbo.ui.screens.SubscriptionProfile(
                            url = sub.url.trim(),
                            name = sub.name?.trim().orEmpty(),
                            customName = sub.name?.trim()?.takeIf { it.isNotBlank() }
                        )
                    }
                    preferences.saveProfiles(current + newProfiles)
                }
                preferences.crossSyncLastAt = System.currentTimeMillis()
                preferences.crossSyncLastDevice = receivedBundle.deviceName
            }
        }
    }

    // RFCOMM-сервер поднимается только после hasBluetoothConnectPermission().
    @SuppressLint("MissingPermission")
    fun start(preferences: PreferencesManager, profiles: List<com.danila.nimbo.ui.screens.SubscriptionProfile>, onDataReceived: (CrossSyncBundle) -> Unit) {
        if (isRunning) return
        isRunning = true

        // 1. TCP Server
        tcpServerJob = scope.launch {
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DEFAULT_SYNC_TCP_PORT))
                }
            }.onSuccess { serverSocket ->
                serverSocket.use { server ->
                    while (isActive) {
                        try {
                            val clientSocket = server.accept()
                            scope.launch {
                                handleClientConnection(clientSocket, preferences, profiles, onDataReceived)
                            }
                        } catch (_: Exception) {
                            if (!isActive) break
                        }
                    }
                }
            }.onFailure { e ->
                Logger.d("CrossSyncServer", "TCP server start error: ${e.message}")
            }
        }

        // 2. Bluetooth RFCOMM Server
        btServerJob = scope.launch {
            if (!hasBluetoothConnectPermission()) return@launch
            runCatching {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                if (!adapter.isEnabled) return@launch
                adapter.listenUsingRfcommWithServiceRecord(
                    "NimboSync",
                    CrossSyncClient.NIMBO_BLUETOOTH_UUID
                )
            }.onSuccess { btServer ->
                btServer.use { server ->
                    while (isActive) {
                        try {
                            val client = server.accept()
                            scope.launch {
                                handleBluetoothConnection(client, preferences, profiles, onDataReceived)
                            }
                        } catch (_: Exception) {
                            if (!isActive) break
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        tcpServerJob?.cancel()
        tcpServerJob = null
        btServerJob?.cancel()
        btServerJob = null
        _incomingRequest.value = null
        pairingSessions.clear()
    }

    fun dismissIncomingRequest() {
        _incomingRequest.value?.onReject?.invoke()
        _incomingRequest.value = null
    }

    fun clearIncomingRequest() {
        _incomingRequest.value = null
    }

    private suspend fun handleClientConnection(
        socket: Socket,
        preferences: PreferencesManager,
        profiles: List<com.danila.nimbo.ui.screens.SubscriptionProfile>,
        onDataReceived: (CrossSyncBundle) -> Unit
    ) = withContext(Dispatchers.IO) {
        socket.use { s ->
            s.soTimeout = 10_000
            runCatching {
                val input = DataInputStream(s.getInputStream().buffered())
                val output = DataOutputStream(s.getOutputStream().buffered())
                processWireStream(input, output, preferences, profiles, onDataReceived, s.inetAddress.hostAddress.orEmpty())
            }.onFailure { err ->
                Logger.d("CrossSyncServer", "Client connection failed: ${err.message}")
            }
        }
    }

    private suspend fun handleBluetoothConnection(
        socket: BluetoothSocket,
        preferences: PreferencesManager,
        profiles: List<com.danila.nimbo.ui.screens.SubscriptionProfile>,
        onDataReceived: (CrossSyncBundle) -> Unit
    ) = withContext(Dispatchers.IO) {
        socket.use { s ->
            runCatching {
                val input = DataInputStream(s.inputStream.buffered())
                val output = DataOutputStream(s.outputStream.buffered())
                processWireStream(input, output, preferences, profiles, onDataReceived, "127.0.0.1")
            }.onFailure { err ->
                Logger.d("CrossSyncServer", "Bluetooth connection failed: ${err.message}")
            }
        }
    }

    private suspend fun processWireStream(
        input: DataInputStream,
        output: DataOutputStream,
        preferences: PreferencesManager,
        profiles: List<com.danila.nimbo.ui.screens.SubscriptionProfile>,
        onDataReceived: (CrossSyncBundle) -> Unit,
        peerHost: String
    ) {
        val length = input.readInt()
        if (length !in 1..2 * 1024 * 1024) return
        val frameBytes = ByteArray(length)
        input.readFully(frameBytes)

        val envelope = gson.fromJson(frameBytes.decodeToString(), EncryptedSyncEnvelope::class.java)
        val resumeDeviceId = envelope.sessionId.removePrefix("resume:")
            .takeIf { envelope.sessionId.startsWith("resume:") && it.isNotBlank() }
        val pairedDevice = resumeDeviceId?.let { id ->
            preferences.crossSyncPairedDevices.firstOrNull { it.deviceId == id }
        }
        val sessionKey = if (pairedDevice != null) {
            runCatching { Base64.getUrlDecoder().decode(pairedDevice.key) }.getOrNull()
        } else {
            CrossSyncDiscoveryEngine.sessionKeyFor(envelope.sessionId)
        } ?: return
        val requestJson = runCatching {
            CrossSyncCrypto.decrypt(sessionKey, envelope).decodeToString()
        }.getOrNull() ?: return

        val request = gson.fromJson(requestJson, SyncWireRequest::class.java)
        val localBundle = AndroidCrossSyncBundleMapper.export(preferences, preferences.loadProfiles())
        val response = if (pairedDevice != null) {
            processResumeRequest(
                preferences = preferences,
                device = pairedDevice,
                request = request,
                localBundle = localBundle,
                onDataReceived = onDataReceived
            )
        } else {
            processPairingRequest(
                preferences = preferences,
                request = request,
                localBundle = localBundle,
                envelope = envelope,
                sessionKey = sessionKey,
                peerHost = peerHost,
                onDataReceived = onDataReceived
            )
        }

        val respPlaintext = gson.toJson(response).encodeToByteArray()
        val respEnvelope = CrossSyncCrypto.encrypt(sessionKey, envelope.sessionId, respPlaintext)
        val respFrame = gson.toJson(respEnvelope).encodeToByteArray()

        output.writeInt(respFrame.size)
        output.write(respFrame)
        output.flush()
    }

    private fun processPairingRequest(
        preferences: PreferencesManager,
        request: SyncWireRequest,
        localBundle: CrossSyncBundle,
        envelope: EncryptedSyncEnvelope,
        sessionKey: ByteArray,
        peerHost: String,
        onDataReceived: (CrossSyncBundle) -> Unit
    ): SyncWireResponse {
        val beacon = CrossSyncDiscoveryEngine.activeBeaconSession()
        val session = pairingSessions.computeIfAbsent(envelope.sessionId) {
            ServerPairingSession(
                id = envelope.sessionId,
                key = sessionKey.copyOf(),
                comparisonCode = beacon.comparisonCode,
                expiresAtMs = beacon.expiresAtMs,
                peerHost = peerHost
            )
        }
        synchronized(session) {
            if (System.currentTimeMillis() >= session.expiresAtMs) {
                session.state = "expired"
            }
            when (request.action) {
                "hello" -> {
                    if (session.state == "expired") return pairingResponse(session, preferences, localBundle)
                    val incoming = request.bundle ?: return SyncWireResponse(
                        state = "rejected",
                        message = "Устройство не передало данные для синхронизации"
                    )
                    session.peerDeviceId = request.deviceId
                    session.peerName = request.deviceName?.takeIf { it.isNotBlank() }
                        ?: incoming.deviceName.ifBlank { "Nimbo" }
                    session.peerBundle = incoming
                    session.peerHost = peerHost.ifBlank { session.peerHost }
                    if (!session.approved && !session.rejected) {
                        session.state = "awaiting_approval"
                        _incomingRequest.value = IncomingPairingRequest(
                            requestId = session.id,
                            peerName = session.peerName,
                            peerDeviceId = session.peerDeviceId.orEmpty(),
                            platform = incoming.platform,
                            comparisonCode = session.comparisonCode,
                            incomingBundle = incoming,
                            onAccept = {
                                synchronized(session) {
                                    if (!session.rejected) {
                                        session.approved = true
                                        session.state = "paired"
                                        if (session.pairedKey == null) {
                                            val persistent = ByteArray(32).also { SecureRandom().nextBytes(it) }
                                            session.pairedKey = Base64.getUrlEncoder().withoutPadding()
                                                .encodeToString(persistent)
                                        }
                                    }
                                }
                            },
                            onReject = {
                                synchronized(session) {
                                    session.rejected = true
                                    session.state = "rejected"
                                }
                            }
                        )
                    }
                }
                "status" -> Unit
                "commit" -> {
                    if (!session.approved || session.rejected) {
                        return pairingResponse(session, preferences, localBundle)
                    }
                    val direction = request.direction
                        ?: return SyncWireResponse(state = "rejected", message = "Не выбрано направление передачи")
                    ensurePairedDevice(preferences, session, request, localBundle)
                    if (direction == SyncDirection.ANDROID_TO_DESKTOP) {
                        request.bundle?.let(onDataReceived)
                        session.state = "completed"
                    } else {
                        session.state = "export_authorized"
                    }
                }
                "receipt" -> {
                    if (session.approved) session.state = "completed"
                }
                else -> return SyncWireResponse(state = "rejected", message = "Неизвестная команда синхронизации")
            }
            return pairingResponse(session, preferences, localBundle)
        }
    }

    private fun pairingResponse(
        session: ServerPairingSession,
        preferences: PreferencesManager,
        localBundle: CrossSyncBundle
    ) = SyncWireResponse(
        state = session.state,
        comparisonCode = session.comparisonCode,
        desktopBundle = localBundle.takeIf { session.approved },
        desktopInventory = localBundle.inventory(),
        desktopDeviceInfo = localBundle.deviceInfo,
        desktopSubscriptions = localBundle.subscriptions.map { it.name ?: it.url },
        expiresAtMs = session.expiresAtMs,
        message = "Сопряжение отклонено на втором устройстве".takeIf { session.rejected },
        paired = session.approved,
        deviceId = preferences.getOrCreateCrossSyncDeviceId(),
        pairedKey = session.pairedKey,
        serverPort = DEFAULT_SYNC_TCP_PORT,
        applied = session.state == "completed"
    )

    private fun ensurePairedDevice(
        preferences: PreferencesManager,
        session: ServerPairingSession,
        request: SyncWireRequest,
        localBundle: CrossSyncBundle
    ) {
        val remoteId = session.peerDeviceId ?: request.deviceId ?: return
        val key = session.pairedKey ?: return
        val info = session.peerBundle?.deviceInfo
        val old = preferences.crossSyncPairedDevices.firstOrNull { it.deviceId == remoteId }
        val updated = PairedDesktopDevice(
            deviceId = remoteId,
            name = session.peerName,
            key = key,
            host = session.peerHost,
            port = DEFAULT_SYNC_TCP_PORT,
            pairedAtMs = old?.pairedAtMs ?: System.currentTimeMillis(),
            lastSyncMs = System.currentTimeMillis(),
            lastSeenRemoteSig = old?.lastSeenRemoteSig,
            lastExportedSig = old?.lastExportedSig,
            platform = info?.platform ?: session.peerBundle?.platform.orEmpty(),
            osName = info?.osName,
            osVersion = info?.osVersion,
            appVersion = info?.appVersion,
            architecture = info?.architecture,
            autoSync = old?.autoSync ?: preferences.crossSyncAutoSync,
            lastSubscriptionCount = session.peerBundle?.subscriptions?.size ?: 0,
            lastSubscriptionNames = session.peerBundle?.subscriptions?.map { it.name ?: it.url }.orEmpty(),
            bluetoothMac = old?.bluetoothMac
        )
        preferences.crossSyncPairedDevices = preferences.crossSyncPairedDevices.let { devices ->
            if (devices.any { it.deviceId == remoteId }) {
                devices.map { if (it.deviceId == remoteId) updated else it }
            } else devices + updated
        }
    }

    private fun processResumeRequest(
        preferences: PreferencesManager,
        device: PairedDesktopDevice,
        request: SyncWireRequest,
        localBundle: CrossSyncBundle,
        onDataReceived: (CrossSyncBundle) -> Unit
    ): SyncWireResponse {
        if (request.action == "unpair") {
            preferences.crossSyncPairedDevices = preferences.crossSyncPairedDevices
                .filterNot { it.deviceId == device.deviceId }
            return SyncWireResponse(state = "unpaired", deviceId = preferences.getOrCreateCrossSyncDeviceId())
        }
        if (request.action == "hello" && device.autoSync) request.bundle?.let(onDataReceived)
        if (request.action !in setOf("hello", "status")) {
            return SyncWireResponse(state = "rejected", message = "Неизвестная команда синхронизации")
        }
        val updated = device.copy(
            lastSyncMs = System.currentTimeMillis(),
            lastSubscriptionCount = request.bundle?.subscriptions?.size ?: device.lastSubscriptionCount,
            lastSubscriptionNames = request.bundle?.subscriptions?.map { it.name ?: it.url }
                ?: device.lastSubscriptionNames
        )
        preferences.crossSyncPairedDevices = preferences.crossSyncPairedDevices.map {
            if (it.deviceId == device.deviceId) updated else it
        }
        return SyncWireResponse(
            state = "active",
            paired = true,
            deviceId = preferences.getOrCreateCrossSyncDeviceId(),
            desktopBundle = localBundle.takeIf { device.autoSync },
            desktopInventory = localBundle.inventory().takeIf { device.autoSync },
            desktopDeviceInfo = localBundle.deviceInfo.takeIf { device.autoSync },
            desktopSubscriptions = localBundle.subscriptions.map { it.name ?: it.url }.takeIf { device.autoSync },
            serverPort = DEFAULT_SYNC_TCP_PORT,
            applied = request.action == "hello" && request.bundle != null
        )
    }
}
