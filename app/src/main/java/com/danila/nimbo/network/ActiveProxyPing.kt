package com.danila.nimbo.network

import com.danila.nimbo.vpn.HealthProxySession
import com.danila.nimbo.vpn.LocalProxyConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** No system proxy selector, connection reuse, redirects, cache, or direct retry. */
internal object ActiveProxyPing {
    fun validUrl(value: String): Boolean {
        val trimmed = value.trim()
        if (!(trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true)) ||
            trimmed.any { it.isISOControl() }) return false
        return trimmed.toHttpUrlOrNull()?.let {
            it.username.isEmpty() && it.password.isEmpty() && it.fragment == null &&
                !value.contains('{') && !value.contains('}')
        } == true
    }

    suspend fun measure(
        url: String,
        timeoutMs: Int,
        session: HealthProxySession,
        isCurrent: () -> Boolean,
        method: String = "GET",
        proxyPort: Int = LocalProxyConfig.PORT,
        acceptsStatus: (Int) -> Boolean = { it in 200..299 }
    ): Int {
        if (!isCurrent() || !validUrl(url)) return -1
        val authorization = Credentials.basic(session.username, session.password)
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LocalProxyConfig.HOST, proxyPort)))
            .proxyAuthenticator { _, response ->
                if (!isCurrent() || response.request.header("Proxy-Authorization") != null) null
                else response.request.newBuilder().header("Proxy-Authorization", authorization).build()
            }
            .callTimeout(timeoutMs.coerceIn(250, 10_000).toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        try {
            return suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(url.trim()).method(method, null)
                    .header("User-Agent", "Nimbo Ping/Android")
                    .header("Cache-Control", "no-cache, no-store")
                    .apply {
                        // HTTPS authenticates CONNECT only; never send proxy credentials to the origin.
                        if (url.trim().startsWith("http://", ignoreCase = true)) {
                            header("Proxy-Authorization", authorization)
                        }
                    }.build()
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                if (!isCurrent()) {
                    continuation.resume(-1)
                    return@suspendCancellableCoroutine
                }
                val started = System.nanoTime()
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(-1)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = response.use {
                            if (isCurrent() && acceptsStatus(it.code)) {
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                                    .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                            } else -1
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                })
            }
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
