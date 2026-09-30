package com.danila.nimbo.vpn

import android.content.Context
import android.net.Network
import android.net.VpnService
import android.os.Build
import com.danila.nimbo.mihomo.MihomoBridge
import com.danila.nimbo.mihomo.MihomoProfiles
import com.danila.nimbo.mihomo.MihomoProtocol
import com.danila.nimbo.mihomo.MihomoException
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.JsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import libXray.DialerController
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** All unsupported user choices are rejected, never silently removed from the YAML or preferences. */
internal data class MihomoOptions(
    val ipType: String = "ipv4",
    val dnsMode: String = "remote",
    val perAppMode: Int = 0,
    val routing: Boolean = false,
    val modules: Boolean = false,
    val blockUdp: Boolean = false,
    val automaticSelection: Boolean = false,
    val directLan: Boolean = false,
    val hotspot: Boolean = false,
    val killSwitch: Boolean = false
) {
    fun conflicts(): List<String> = buildList {
        if (!ipType.equals("ipv4", true) && !ipType.equals("dual", true)) add("unknown IP mode")
        if (!dnsMode.equals("remote", true)) add("local/hybrid DNS")
        // Existing VPN-only policy omits TUN DNS; the native plan requires managed DNS.
        if (perAppMode !in 0..2) add("unknown per-app mode")
        if (routing || modules) add("routing rules/modules")
        if (blockUdp) add("UDP blocking")
        if (automaticSelection) add("automatic server selection")
        if (directLan && Build.VERSION.SDK_INT < 33) add("direct LAN bypass (requires Android 13+ route exclusions)")
        if (hotspot) add("hotspot proxy")
        if (killSwitch) add("kill switch (persistent blocking TUN is unavailable)")
    }
}

/** Never close the platform's original FD unless native stop has joined its duplicate's reader. */
internal class MihomoFdLease(private val fd: Closeable) {
    var generation: Long? = null
    private var closed = false
    fun stopAndClose(stop: (Long?) -> Unit) {
        if (closed) return
        stop(generation) // Throwing deliberately retains the FD and protector for a later retry.
        fd.close()
        closed = true
    }
}

internal fun mihomoReady(status: JsonObject): Boolean = runCatching {
    status.get("state")?.asString == "running" &&
        status.get("networkOwner")?.asString == "android-vpn" &&
        status.get("tunReady")?.asBoolean == true
}.getOrDefault(false)

internal enum class MihomoStartStage {
    PREFLIGHT, NETWORK_SELECT, ENGINE_HANDOFF, PREPARE_SOCKET, DATA_DIR,
    TUN_ESTABLISH, NATIVE_START, VERIFY_READY, RESTORE_SELECTION
}

internal fun mihomoSafeErrorCode(error: Throwable): String =
    (error as? MihomoException)?.code?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,79}")) }
        ?: "START_FAILED"

/** Keep native and subscription error text (which can include endpoints or credentials) out of the UI. */
internal fun mihomoStartFailure(stage: MihomoStartStage, error: Throwable): String {
    return "Mihomo [${stage.name}]: ${mihomoSafeErrorCode(error)}"
}

internal fun mihomoStartupBudgetMs(remainingCycleMs: Long): Long = remainingCycleMs.coerceIn(0L, 25_000L)

/** Process-owned stop barrier. Cancellation/stop scheduling never waits for a blocked JNI call. */
internal class MihomoEngineLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val state = Any()
    private var epoch = 0L
    private var stop: Deferred<Unit>? = null

    fun stop(cancelNative: () -> Unit, close: () -> Unit): Deferred<Unit> {
        cancelNative()
        return synchronized(state) {
            epoch++
            val previous = stop
            scope.async {
                previous?.join() // Retrying stop is allowed after failure; starting is not.
                mutex.withLock { close() }
            }.also { stop = it }
        }
    }

    suspend fun <T> start(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        val (ticket, barrier) = synchronized(state) { epoch to stop }
        barrier?.await()
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (synchronized(state) { ticket != epoch }) throw CancellationException("VPN start superseded by stop")
            block()
        }
    }
}

/** Trusted borrowed-FD owner. No gomobile symbols beyond DialerController escape the reflective bridge. */
object MihomoManager {
    private val lock = Any()
    private val epoch = AtomicLong()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nimbo-mihomo-lifecycle").apply { isDaemon = true }
    }
    private val canceller = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nimbo-mihomo-cancel").apply { isDaemon = true }
    }
    @Volatile private var pendingRequest: String? = null
    private var lease: MihomoFdLease? = null
    // Keep the callback strongly reachable through native stop, including failed startup cleanup.
    private var protector: DialerController? = null
    @Volatile var isConnected = false
        private set
    @Volatile var connectionError: String? = null
        private set

    internal fun options(p: PreferencesManager) = MihomoOptions(
        ipType = p.vpnIpType, dnsMode = p.vpnDnsMode, perAppMode = p.proxyByApp,
        routing = p.isRoutingEnabled, modules = p.routingModules().any { it.enabled },
        blockUdp = p.blockUdp, automaticSelection = p.autoServerSelectionDesired,
        directLan = p.allowLanConnections && !p.lanThroughProxy, hotspot = p.allowHotspotAccess,
        killSwitch = p.killSwitch
    )

    /** For an explicit compatible-settings UI; never changes preferences or the current session. */
    fun compatibilityConflicts(context: Context): List<String> = options(PreferencesManager(context)).conflicts()

    const val CONFIGURATION_SCOPE_NOTICE = "Mihomo uses the original YAML. Xray sniffing, mux, fragmentation, " +
        "connection tuning and bypass/rotation strategies do not apply. Native readiness is not external connectivity."

    /** Pure native policy admission: no TUN, sockets, listeners, or live session mutations. */
    fun prepare(context: Context, server: Server): String {
        check(MihomoBridge.available()) { "Mihomo Android adapter is unavailable" }
        val conflicts = compatibilityConflicts(context)
        check(conflicts.isEmpty()) { "Mihomo: unsupported settings: ${conflicts.joinToString()}. Disable them explicitly before connecting." }
        val prefs = PreferencesManager(context)
        val appRules = when (prefs.proxyByApp) {
            1 -> prefs.getAppBypassList()
            2 -> prefs.getAppVpnOnlyList()
            else -> emptyList()
        }
        if (prefs.proxyByApp == 2) check(appRules.any { it.isNotBlank() && it != context.packageName }) {
            "Mihomo: VPN-only mode requires at least one selected application."
        }
        appRules.map(String::trim).filter { it.isNotBlank() && it != context.packageName }.forEach {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(it, 0)
            }
        return MihomoProfiles.source(context, server).also { MihomoBridge.preflight(it) }
    }

    suspend fun connect(context: Context, yaml: String, service: VpnService, network: Network): Boolean =
        suspendCancellableCoroutine { continuation ->
            val ticket = epoch.get()
            val requestId = UUID.randomUUID().toString()
            continuation.invokeOnCancellation {
                // cancel bypasses the native operation lock, even while StartAndroid blocks in provider IO.
                cancelRequest(requestId)
                worker.execute {
                    synchronized(lock) {
                        if (pendingRequest == requestId) runCatching { stopLocked() }
                    }
                }
            }
            worker.execute {
                val connected = synchronized(lock) {
                    if (!continuation.isActive || ticket != epoch.get()) return@synchronized false
                    var stage = MihomoStartStage.PREPARE_SOCKET
                    try {
                        stopLocked()
                        pendingRequest = requestId
                        check(continuation.isActive && ticket == epoch.get()) { "Mihomo start cancelled" }
                        connectionError = null
                        val callback = object : DialerController {
                            override fun protectFd(fd: Long): Boolean = runCatching {
                                fd in 0..Int.MAX_VALUE.toLong() &&
                                    VpnInterfaceBuilder.protectSocket(service, network, fd.toInt())
                            }.getOrDefault(false)
                        }
                        protector = callback
                        MihomoBridge.setSocketProtector(callback)
                        MihomoBridge.setFlowOwnerResolver { protocol, source, sourcePort, destination, destinationPort ->
                            runCatching {
                                check(Build.VERSION.SDK_INT >= 29)
                                val manager = service.getSystemService(android.net.ConnectivityManager::class.java)
                                val uid = manager.getConnectionOwnerUid(
                                    if (protocol.equals("tcp", true)) android.system.OsConstants.IPPROTO_TCP else android.system.OsConstants.IPPROTO_UDP,
                                    java.net.InetSocketAddress(java.net.InetAddress.getByName(source), sourcePort.toInt()),
                                    java.net.InetSocketAddress(java.net.InetAddress.getByName(destination), destinationPort.toInt()))
                                check(uid >= 0)
                                val name = service.packageManager.getPackagesForUid(uid)?.sorted()?.firstOrNull().orEmpty()
                                check(name.isNotBlank())
                                JsonObject().apply { addProperty("uid", uid); addProperty("package", name) }.toString()
                            }.getOrDefault("{}")
                        }
                        stage = MihomoStartStage.DATA_DIR
                        val dataDir = context.filesDir.resolve("mihomo-data").resolve(MihomoProtocol.sourceHash(yaml))
                        check(dataDir.isDirectory || dataDir.mkdirs()) { "Mihomo data directory unavailable" }
                        ensureMihomoGeoAssets(context, dataDir)
                        val prefs = PreferencesManager(context)
                        val exclusions = MihomoBridge.preflight(yaml).getAsJsonArray("excludedPackages")?.map { it.asString }.orEmpty()
                        stage = MihomoStartStage.TUN_ESTABLISH
                        val tun = VpnInterfaceBuilder.establishMihomo(service, network, exclusions)
                        val owned = MihomoFdLease(tun)
                        lease = owned
                        stage = MihomoStartStage.NATIVE_START
                        val status = MihomoBridge.startAndroid(
                            yaml, dataDir.absolutePath, tun.fd.toLong(), requestId,
                            ipv6 = prefs.vpnIpType.equals("dual", ignoreCase = true),
                            physicalDns = service.getSystemService(android.net.ConnectivityManager::class.java)
                                .getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
                        )
                        // Starts/stops are serialized here. Read an atomic status envelope rather than
                        // the bridge's shared latestGeneration (UI inspect calls can update that value).
                        val running = MihomoBridge.response("status")
                        owned.generation = running.generation
                        stage = MihomoStartStage.VERIFY_READY
                        check(continuation.isActive && ticket == epoch.get()) { "Mihomo start cancelled" }
                        check(mihomoReady(status) && mihomoReady(running.data) &&
                            status.get("sourceSHA256") == running.data.get("sourceSHA256")) {
                            "Mihomo TUN did not become ready"
                        }
                        stage = MihomoStartStage.RESTORE_SELECTION
                        com.danila.nimbo.mihomo.MihomoGroupChoices.restore(context, MihomoProtocol.sourceHash(yaml), running.generation)
                        check(continuation.isActive && ticket == epoch.get()) { "Mihomo start cancelled" }
                        runCatching { com.danila.nimbo.utils.SupportDiagnosticStore.captureConfig(
                            context, "mihomo", MihomoBridge.call("diagnosticConfig").toString()) }
                        isConnected = true
                        true
                    } catch (error: Exception) {
                        connectionError = mihomoStartFailure(stage, error)
                        runCatching { stopLocked() }.onFailure {
                            connectionError = "Mihomo native stop failed; the original TUN is retained until cleanup succeeds."
                        }
                        false
                    }
                }
                continuation.resume(connected)
            }
        }

    /** Cancel before waiting for the Java lifecycle lock; stop always precedes original-FD close. */
    fun disconnect() {
        cancelPending()
        synchronized(lock) { stopLocked() }
    }

    /** Safe on Main: no JNI and no waiting on the lifecycle lock. */
    fun cancelPending() {
        epoch.incrementAndGet()
        pendingRequest?.let(::cancelRequest)
    }

    fun markStartupTimedOut() {
        connectionError = "Mihomo [STARTUP]: START_TIMEOUT"
        cancelPending()
    }

    private fun cancelRequest(requestId: String) {
        canceller.execute { runCatching { MihomoBridge.cancel(requestId) } }
    }

    private fun stopLocked() {
        isConnected = false
        lease?.let { owned ->
            // A failed/invalid start reply may not carry a generation. While holding the owner
            // lock, obtain one from status. If even status fails, retain FD/protector and fail closed.
            if (owned.generation == null) owned.generation = MihomoBridge.response("status").generation
            owned.stopAndClose { generation -> MihomoBridge.stop(generation) }
        }
        lease = null
        if (protector != null) { MihomoBridge.setFlowOwnerResolver(null); MihomoBridge.setSocketProtector(null) }
        protector = null
        pendingRequest = null
    }

    /** The app already ships these compatible V2Ray-format databases for Xray.
     * Mihomo looks for case-sensitive GeoSite.dat/GeoIP.dat in its own home. */
    private fun ensureMihomoGeoAssets(context: Context, dataDir: File) {
        val version = "${BuildConfig.VERSION_CODE}:${BuildConfig.VERSION_NAME}"
        val marker = dataDir.resolve("nimbo-geodata.version")
        val assets = listOf("geosite.dat" to "GeoSite.dat", "geoip.dat" to "GeoIP.dat")
        if (marker.isFile && runCatching { marker.readText() == version }.getOrDefault(false) &&
            assets.all { (_, target) -> dataDir.resolve(target).let { it.isFile && it.length() > 1024L } }) return

        assets.forEach { (source, targetName) ->
            val target = dataDir.resolve(targetName)
            val temporary = dataDir.resolve(".$targetName.nimbo-tmp")
            runCatching { temporary.delete() }
            context.assets.open(source).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            check(temporary.isFile && temporary.length() > 1024L) { "Mihomo geodata asset is incomplete" }
            if (target.exists()) check(target.delete()) { "Cannot replace stale Mihomo geodata" }
            check(temporary.renameTo(target)) { "Cannot install Mihomo geodata" }
        }
        val temporaryMarker = dataDir.resolve(".nimbo-geodata.version.tmp")
        temporaryMarker.writeText(version)
        if (marker.exists()) check(marker.delete()) { "Cannot replace Mihomo geodata marker" }
        check(temporaryMarker.renameTo(marker)) { "Cannot commit Mihomo geodata marker" }
    }

    fun ready(): Boolean = synchronized(lock) {
        val owned = lease ?: return@synchronized false
        if (!isConnected) return@synchronized false
        runCatching {
            val reply = MihomoBridge.response("status", generation = owned.generation)
            reply.generation == owned.generation && mihomoReady(reply.data)
        }
            .getOrDefault(false)
    }
}
