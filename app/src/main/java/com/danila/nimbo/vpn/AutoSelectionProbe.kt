package com.danila.nimbo.vpn

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Always traverses the selected outbound, even when Nimbo itself is excluded from VPN. */
internal object AutoSelectionProbe {
    suspend fun check(): List<Int> = coroutineScope {
        // Never reuse a pooled connection from the previously tested candidate.
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LocalProxyConfig.HOST, LocalProxyConfig.PORT)))
            .callTimeout(AutoSelectionPolicy.PROBE_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        try {
            AutoSelectionPolicy.targets.map { target ->
                async {
                    suspendCancellableCoroutine { continuation ->
                        val started = System.nanoTime()
                        val request = Request.Builder().url(target.url)
                            .header("Cache-Control", "no-cache, no-store").build()
                        val call = client.newCall(request)
                        continuation.invokeOnCancellation { call.cancel() }
                        call.enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                if (continuation.isActive) continuation.resume(-1)
                            }

                            override fun onResponse(call: Call, response: Response) {
                                val latency = response.use {
                                    if (AutoSelectionPolicy.acceptsResponse(target, it.code)) {
                                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).toInt()
                                    } else -1
                                }
                                if (continuation.isActive) continuation.resume(latency)
                            }
                        })
                    }
                }
            }.awaitAll()
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
