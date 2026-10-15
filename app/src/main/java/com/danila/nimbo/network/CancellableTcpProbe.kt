package com.danila.nimbo.network

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Closing the owned socket unblocks connect immediately, rather than waiting for its timeout. */
internal suspend fun cancellableTcpProbe(
    address: InetSocketAddress,
    timeoutMs: Int,
    socketFactory: () -> Socket = ::Socket
): Long? = coroutineScope {
    ensureActive()
    val socket = socketFactory()
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { socket.close() } }
        launch(Dispatchers.IO) {
            val started = System.nanoTime()
            val result = try {
                socket.use {
                    if (!continuation.isActive) return@launch
                    it.tcpNoDelay = true
                    it.connect(address, timeoutMs)
                }
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).coerceAtLeast(1)
            } catch (_: Exception) { null }
            if (continuation.isActive) continuation.resume(result)
        }
    }
}
