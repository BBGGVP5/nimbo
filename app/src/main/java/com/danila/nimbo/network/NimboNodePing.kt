package com.danila.nimbo.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.danila.nimbo.vpn.DiagnosticSocketProtection
import com.danila.nimbo.vpn.NimboPingService
import com.danila.nimbo.mihomo.MihomoProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference

/** One isolated core at a time, including across ViewModel and diagnostics requests. */
internal object NimboNodePing {
    private val worker = Mutex()
    @Volatile private var retiringProcess: IBinder? = null
    const val STARTUP_ALLOWANCE_MS = 3_000L

    suspend fun measure(context: Context, config: String?, url: String, timeoutMs: Int): Int {
        return measureIsolated(context, config, url, timeoutMs, false, null, null)
    }

    /** Same isolated diagnostic process and physical route as ordinary Nimbo Ping. */
    suspend fun measureMihomo(context: Context, yaml: String, node: String, url: String, timeoutMs: Int): MihomoPingResult {
        if (node.isBlank() || node.length > 512) return MihomoPingResult(-1, MihomoPingFailure.UNSUPPORTED_NODE)
        val reason = AtomicReference<MihomoPingFailure?>(null)
        val delay = measureIsolated(context, yaml, url, timeoutMs, true, node, reason)
        return MihomoPingResult(delay, reason.get())
    }

    private suspend fun measureIsolated(context: Context, config: String?, url: String, timeoutMs: Int,
                                        mihomo: Boolean, node: String?, reason: AtomicReference<MihomoPingFailure?>?): Int {
        val timeout = timeoutMs.coerceIn(1000, 10_000)
        // Deadline begins before queueing: another caller cannot make waiting unbounded.
        val deadline = SystemClock.elapsedRealtime() + timeout + STARTUP_ALLOWANCE_MS
        val trace = NimboPingDiagnostics.begin(deadline, SystemClock::elapsedRealtime,
            if (mihomo) NimboPingCore.MIHOMO else NimboPingCore.XRAY)
        try {
            val sourceLimit = if (mihomo) MihomoProtocol.MAX_SOURCE_BYTES else NodePingConfig.MAX_CONFIG_BYTES
            if (config == null || config.toByteArray().size > sourceLimit || !ActiveProxyPing.validUrl(url)) {
                if (mihomo) { reason?.set(MihomoPingFailure.INVALID_CONFIG); trace.recordFailure(MihomoPingFailure.INVALID_CONFIG) }
                trace.finish(-1)
                return -1
            }
            trace.record(NimboPingPhase.QUEUE_WAIT)
            val value = NimboPingDeadline.withWorker(worker, deadline, SystemClock::elapsedRealtime) {
                trace.record(NimboPingPhase.QUEUE_ACQUIRED)
                // The previous isolated core can outlive unbind by a moment. A
                // sweep must wait for its death instead of marking the next node
                // offline merely because Android has not reaped the process yet.
                if (retiringProcess?.isBinderAlive == true) {
                    trace.record(NimboPingPhase.PROCESS_RETIRING)
                }
                if (!NimboPingDeadline.awaitRetirement(deadline, SystemClock::elapsedRealtime,
                        { retiringProcess?.isBinderAlive == true })) -1 else {
                    retiringProcess = null
                    probe(context.applicationContext, config, url, timeout, deadline, trace, mihomo, node, reason)
                }
            }
            trace.measurementEnded()
            trace.finish(value)
            return value
        } catch (error: CancellationException) {
            trace.finish(-1, cancelled = true)
            throw error
        } catch (error: Exception) {
            trace.finish(-1)
            throw error
        }
    }

    private suspend fun probe(context: Context, config: String, url: String, timeout: Int, deadline: Long,
                              trace: NimboPingTrace, mihomo: Boolean, node: String?, reason: AtomicReference<MihomoPingFailure?>?): Int = coroutineScope {
        val connected = CompletableDeferred<IBinder>()
        val result = CompletableDeferred<Int>()
        val died = CompletableDeferred<Unit>()
        var remote: Messenger? = null
        val death = IBinder.DeathRecipient {
            trace.record(NimboPingPhase.PROCESS_LOST)
            died.complete(Unit); result.complete(-1)
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                trace.record(NimboPingPhase.BIND_CONNECTED)
                runCatching { binder.linkToDeath(death, 0) }.onFailure { died.complete(Unit) }
                connected.complete(binder)
            }
            override fun onServiceDisconnected(name: ComponentName) { trace.record(NimboPingPhase.PROCESS_LOST); died.complete(Unit); result.complete(-1) }
            override fun onBindingDied(name: ComponentName) { trace.record(NimboPingPhase.PROCESS_LOST); died.complete(Unit); connected.completeExceptionally(IllegalStateException("Diagnostic process unavailable")); result.complete(-1) }
            override fun onNullBinding(name: ComponentName) { connected.completeExceptionally(IllegalStateException("Diagnostic binding unavailable")) }
        }
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                NimboPingService.RESULT -> {
                    val failure = MihomoPingFailure.fromWire(message.data.getString("reason"))
                    trace.recordResult(failure)
                    reason?.set(failure)
                    result.complete(message.arg1)
                }
                NimboPingService.STAGE -> trace.child(message.arg1)
            }
            true
        })
        var bound = false
        var writer: Job? = null
        var pipe: Array<ParcelFileDescriptor>? = null
        NimboPingDeadline.measureThenCleanup(deadline, SystemClock::elapsedRealtime, measurement = measurement@ {
            try {
                trace.record(NimboPingPhase.BIND_REQUEST)
                bound = context.bindService(Intent(context, NimboPingService::class.java), connection, Context.BIND_AUTO_CREATE)
                if (!bound) return@measurement -1
                remote = Messenger(connected.await())
                val descriptors = ParcelFileDescriptor.createPipe()
                pipe = descriptors
                val request = Message.obtain(null, NimboPingService.RUN).apply {
                    replyTo = receiver
                    data = Bundle().apply {
                        putParcelable("config", descriptors[0]); putString("url", url)
                        putInt("timeout", timeout); putLong("deadline", deadline)
                        if (mihomo) { putString("mode", "mihomo"); putString("node", node) }
                        putBinder("protect", DiagnosticSocketProtection.bridge(context))
                    }
                }
                remote.send(request)
                trace.record(NimboPingPhase.PIPE_SENT)
                descriptors[0].close()
                writer = launch(Dispatchers.IO) {
                    runCatching { ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write(config.toByteArray()) } }
                }
                trace.record(NimboPingPhase.WAIT_RESULT)
                result.await()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { -1 }
        }, cleanup = {
            trace.measurementEnded()
            trace.record(NimboPingPhase.CLEANUP_STARTED)
            // Close pipes before waiting, to unblock both the reader and writer on cancellation.
            pipe?.forEach { runCatching { it.close() } }
            writer?.cancel()
            retiringProcess = remote?.binder
            runCatching { remote?.send(Message.obtain(null, NimboPingService.STOP)) }
            if (bound) runCatching { context.unbindService(connection) }
            if (remote != null) withTimeoutOrNull(1500) { died.await() }
            if (died.isCompleted) retiringProcess = null
            trace.record(NimboPingPhase.CLEANUP_JOINED)
        })
    }
}
