package com.danila.nimbo.vpn

import android.app.Application
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import com.danila.nimbo.network.ActiveProxyPing
import com.danila.nimbo.network.NodePingConfig
import com.danila.nimbo.network.NimboPingReadiness
import com.danila.nimbo.network.NimboProbePorts
import com.danila.nimbo.network.NimboPingPhase
import com.danila.nimbo.network.NimboPingStageSender
import kotlinx.coroutines.*
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject

/** The manifest MUST place this private bound service in :nimbo_ping, never the VPN process. */
internal class NimboPingService : Service() {
    companion object {
        const val PROCESS_SUFFIX = ":nimbo_ping"
        const val RUN = 1
        const val STOP = 2
        const val RESULT = 3
        const val STAGE = 4
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var busy = false
    private val handler = Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            STOP -> terminateDiagnosticProcess()
            RUN -> {
                if (busy || Application.getProcessName() != packageName + PROCESS_SUFFIX) {
                    message.replyTo?.send(Message.obtain(null, RESULT, -1, 0))
                } else {
                    busy = true
                    val request = Bundle(message.data)
                    val response = message.replyTo
                    val remaining = request.getLong("deadline") - SystemClock.elapsedRealtime()
                    handlerWatchdog.postDelayed({ terminateDiagnosticProcess() }, remaining.coerceIn(1, 15_000))
                    scope.launch {
                        val diagnostics = NimboPingStageSender { phase ->
                            response?.send(Message.obtain(null, STAGE, phase, 0))
                        }
                        diagnostics.stage(NimboPingPhase.RUN_RECEIVED)
                        val value = try { runProbe(request, diagnostics) } catch (e: CancellationException) { throw e } catch (_: Exception) { -1 }
                        // Deliver before teardown; synchronous stop/drain can exceed the GET deadline.
                        // Parent STOP/unbind owns teardown; watchdog still handles parent loss.
                        runCatching { response?.send(Message.obtain(null, RESULT, value, 0)) }
                    }
                }
            }
        }
        true
    }
    private val handlerWatchdog = Handler(Looper.getMainLooper())
    private val messenger = Messenger(handler)
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean { terminateDiagnosticProcess(); return false }

    private suspend fun runProbe(request: Bundle, diagnostics: NimboPingStageSender): Int {
        diagnostics.stage(NimboPingPhase.CONFIG_READ_STARTED)
        val configFd = request.getParcelable<ParcelFileDescriptor>("config") ?: return -1
        val source = ParcelFileDescriptor.AutoCloseInputStream(configFd).use { stream ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > NodePingConfig.MAX_CONFIG_BYTES) return -1
            }
            val bytes = output.toByteArray()
            if (bytes.size > NodePingConfig.MAX_CONFIG_BYTES) return -1
            bytes.toString(Charsets.UTF_8)
        }
        val url = request.getString("url") ?: return -1
        if (!ActiveProxyPing.validUrl(url)) return -1
        val bridge = request.getBinder("protect") ?: return -1
        diagnostics.stage(NimboPingPhase.CONFIG_READ)
        diagnostics.stage(NimboPingPhase.NETWORK_SELECT_STARTED)
        val cm = getSystemService(ConnectivityManager::class.java)
        val networks = listOfNotNull(cm.activeNetwork) + cm.allNetworks.toList()
        val network = networks.distinct().firstOrNull {
            val caps = cm.getNetworkCapabilities(it)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } ?: return -1
        diagnostics.stage(NimboPingPhase.PHYSICAL_NETWORK_SELECTED)
        if (!cm.bindProcessToNetwork(network)) return -1
        diagnostics.stage(NimboPingPhase.PROCESS_NETWORK_BOUND)
        diagnostics.stage(NimboPingPhase.ROUTE_VERIFY_STARTED)
        val session = HealthProxySession("diagnostic")
        val candidates = NodePingConfig.leastPingCandidates(source)
        val (port, readinessPort) = NimboProbePorts.allocate(candidates.isNotEmpty())
        val config = NodePingConfig.prepare(source, port, session, readinessPort) ?: return -1
        diagnostics.stage(NimboPingPhase.ROUTE_VERIFIED)
        // Separate assets directory avoids racing the VPN's runtime files during first start.
        diagnostics.stage(NimboPingPhase.ASSETS_PREPARE_STARTED)
        val assets = filesDir.resolve("nimbo-ping-data").apply { mkdirs() }
        XrayManager.ensureXrayDatAssets(this, assets)
        diagnostics.stage(NimboPingPhase.ASSETS_READY)
        val runtime = JSONObject(config).put("env", JSONObject().put("xray.location.asset", assets.absolutePath)).toString()
        LibXray.registerDialerController(object : DialerController {
            override fun protectFd(fd: Long): Boolean = runCatching {
                if (!DiagnosticSocketProtection.protect(bridge, fd.toInt())) return false
                if (cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) != true) return false
                ParcelFileDescriptor.fromFd(fd.toInt()).use { network.bindSocket(it.fileDescriptor) }
                true
            }.getOrDefault(false)
        })
        LibXray.registerListenerController(object : DialerController {
            override fun protectFd(fd: Long): Boolean = DiagnosticSocketProtection.protect(bridge, fd.toInt())
        })
        diagnostics.stage(NimboPingPhase.CORE_START_REQUESTED)
        val started = JSONObject(LibXray.invoke(XrayCoreProtocol.runXrayFromJson(runtime)))
        if (!started.optBoolean("success")) return -1
        diagnostics.stage(NimboPingPhase.CORE_STARTED)
        val deadline = request.getLong("deadline")
        return NimboPingReadiness.measureOnce(awaitReady = {
            if (candidates.isNotEmpty()) diagnostics.stage(NimboPingPhase.BALANCER_WAIT)
            NimboPingReadiness.await(candidates, deadline, SystemClock::elapsedRealtime,
                read = { timeout -> ActiveProxyPing.readinessSnapshot(requireNotNull(readinessPort), session, timeout) })
        }, measure = {
            val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtMost(request.getInt("timeout").toLong())
            if (remaining < 250) -1 else {
                diagnostics.stage(NimboPingPhase.TARGET_GET_STARTED)
                ActiveProxyPing.measure(url, remaining.toInt(), session,
                    { scope.isActive && SystemClock.elapsedRealtime() < deadline }, proxyPort = port)
            }
        })
    }

    private fun terminateDiagnosticProcess() {
        scope.cancel()
        // Never kill the main/VPN process even if the manifest is accidentally changed.
        if (Application.getProcessName() == packageName + PROCESS_SUFFIX) Process.killProcess(Process.myPid())
    }
    override fun onDestroy() { handlerWatchdog.removeCallbacksAndMessages(null); terminateDiagnosticProcess(); super.onDestroy() }
}
