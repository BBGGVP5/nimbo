package com.danila.nimbo.network

import android.util.Log
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.vpn.LocalProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

private const val TAG = "PingManager"
private const val TCP_SAMPLE_COUNT = 2
private const val TCP_SAMPLE_DELAY_MS = 25L
private const val TCP_CONNECT_TIMEOUT_CAP_MS = 3000

/**
 * Конфигурация пинга
 */
data class PingConfig(
    val protocol: PingProtocol = PingProtocol.TCP,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val timeoutMs: Int = 3000,
    val useProxy: Boolean = false,
    val proxyPort: Int = LocalProxyConfig.PORT
)

enum class PingProtocol {
    TCP, HTTP_GET, HTTP_HEAD, HTTPS_STRICT, ICMP
}

/**
 * Расширенный менеджер пинга для NebulaGuard.
 * Использует статические функции для надежности.
 */
object PingManager {
    /**
     * Основной метод пинга, учитывающий настройки пользователя.
     */
    suspend fun ping(
        host: String,
        port: Int = 443,
        config: PingConfig = PingConfig()
    ): Int = pingNode(host, port, serverProtocol = null, network = null, config = config)

    suspend fun pingNode(
        host: String,
        port: Int,
        serverProtocol: String?,
        network: String?,
        config: PingConfig
    ): Int {
        for (protocol in protocolAttempts(config, serverProtocol, network)) {
            val result = when (protocol) {
                PingProtocol.TCP -> pingTcp(host, port, config.timeoutMs)
                PingProtocol.HTTP_GET -> pingHttp(resolveHttpUrl(host, port, config.testUrl), "GET", config.timeoutMs, config.useProxy, config.proxyPort)
                PingProtocol.HTTP_HEAD -> pingHttp(resolveHttpUrl(host, port, config.testUrl), "HEAD", config.timeoutMs, config.useProxy, config.proxyPort)
                PingProtocol.HTTPS_STRICT -> pingHttpsStrict(host, port, config.timeoutMs, config.useProxy, config.proxyPort)
                PingProtocol.ICMP -> pingIcmp(host, config.timeoutMs)
            }
            if (result >= 0) return result
        }
        return -1
    }

    /**
     * A loopback HTTP proxy cannot carry raw TCP-connect or ICMP measurements to a
     * node. Proxy mode therefore always measures the configured HTTP health endpoint
     * through the active Xray outbound.
     */
    internal fun effectiveProtocol(config: PingConfig): PingProtocol {
        if (!config.useProxy) return config.protocol
        return if (config.protocol == PingProtocol.HTTP_GET) {
            PingProtocol.HTTP_GET
        } else {
            PingProtocol.HTTP_HEAD
        }
    }

    internal fun protocolAttempts(
        config: PingConfig,
        serverProtocol: String?,
        network: String?
    ): List<PingProtocol> {
        val primary = effectiveProtocol(config)
        if (config.useProxy) return listOf(primary)

        val protocolName = serverProtocol?.trim()?.lowercase().orEmpty()
        val networkName = network?.trim()?.lowercase().orEmpty()
        val udpOnlyTransport = listOf(protocolName, networkName).any { value ->
            value.contains("hysteria") ||
                value == "hy2" ||
                value.contains("tuic") ||
                value.contains("quic")
        }

        return when {
            primary == PingProtocol.TCP && udpOnlyTransport -> listOf(PingProtocol.ICMP, PingProtocol.TCP)
            primary == PingProtocol.TCP -> listOf(PingProtocol.TCP, PingProtocol.ICMP)
            primary == PingProtocol.ICMP -> listOf(PingProtocol.ICMP, PingProtocol.TCP)
            else -> listOf(primary)
        }
    }

    internal fun timeoutBudgetMs(
        config: PingConfig,
        serverProtocol: String?,
        network: String?
    ): Long = config.timeoutMs.coerceAtLeast(250).toLong() *
        protocolAttempts(config, serverProtocol, network).size + 250L

    internal fun resolveHttpUrl(host: String, port: Int, templateUrl: String): String {
        val trimmed = templateUrl.trim()
        if (trimmed.isEmpty()) return "https://$host:$port/"

        if (trimmed.contains("{host}") || trimmed.contains("{port}")) {
            return trimmed
                .replace("{host}", host)
                .replace("{port}", port.toString())
        }

        // A configured URL is a health endpoint. Proxy transports such as
        // VLESS/XHTTP intentionally do not expose a regular HTTP origin on
        // their server port, so rewriting this URL would make HTTP ping hang.
        return trimmed
    }

    /**
     * TCP Пинг: проверка времени установки соединения по сокету
     */
    suspend fun pingTcp(host: String, port: Int, timeoutMs: Int): Int {
        return withContext(Dispatchers.IO) {
            val normalizedHost = host.trim()
            if (normalizedHost.isBlank() || port <= 0) return@withContext -1

            val connectTimeoutMs = timeoutMs.coerceIn(250, 10_000)
            val addresses = runCatching {
                InetAddress.getAllByName(normalizedHost)
                    .distinctBy { it.hostAddress }
                    .sortedWith(compareBy<InetAddress> { it !is Inet4Address }.thenBy { it.hostAddress })
            }.getOrElse { error ->
                Log.w(TAG, "TCP Ping DNS failed for $normalizedHost: ${error.message}")
                emptyList()
            }

            if (addresses.isEmpty()) return@withContext -1

            val samples = mutableListOf<Long>()
            val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(connectTimeoutMs.toLong())
            repeat(TCP_SAMPLE_COUNT) { index ->
                val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).toInt()
                if (remainingMs <= 0) return@repeat
                measureTcpConnect(addresses, port, remainingMs)?.let(samples::add)
                if (index < TCP_SAMPLE_COUNT - 1) {
                    val afterSampleMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime())
                    val pauseMs = TCP_SAMPLE_DELAY_MS.coerceAtMost(afterSampleMs)
                    if (pauseMs > 0) delay(pauseMs)
                }
            }

            aggregateTcpSamples(samples)
        }
    }

    private fun measureTcpConnect(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int
    ): Long? {
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        val perAddressTimeoutMs = timeoutMs.coerceAtMost(TCP_CONNECT_TIMEOUT_CAP_MS)
        for (address in addresses) {
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).toInt()
            if (remainingMs <= 0) return null
            val startNs = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(address, port), perAddressTimeoutMs.coerceAtMost(remainingMs))
                }
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                    .coerceAtLeast(1L)
            } catch (_: Exception) {
                // Try the next resolved address, if any.
            }
        }
        return null
    }

    internal fun aggregateTcpSamples(samples: List<Long>): Int {
        if (samples.isEmpty()) return -1

        val sorted = samples
            .filter { it >= 0L }
            .map { it.coerceAtLeast(1L) }
            .sorted()
        if (sorted.isEmpty()) return -1

        val stable = if (sorted.size >= 4) sorted.dropLast(1) else sorted
        val middle = stable.size / 2
        val value = if (stable.size % 2 == 1) {
            stable[middle]
        } else {
            (stable[middle - 1] + stable[middle]) / 2
        }
        return value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * HTTP Пинг: проверка времени до получения HTTP-ответа
     */
    suspend fun pingHttp(
        urlString: String,
        method: String,
        timeoutMs: Int,
        useProxy: Boolean = false,
        proxyPort: Int = LocalProxyConfig.PORT
    ): Int {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val start = System.currentTimeMillis()
                val url = URL(urlString)
                
                val proxy = if (useProxy) {
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(LocalProxyConfig.HOST, proxyPort))
                } else null

                val httpConnection =
                    (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                connection = httpConnection
                httpConnection.requestMethod = method
                httpConnection.connectTimeout = timeoutMs
                httpConnection.readTimeout = timeoutMs
                httpConnection.instanceFollowRedirects = false
                
                // Чтобы избежать полной загрузки тела при GET
                if (method == "GET") {
                    httpConnection.setRequestProperty("Range", "bytes=0-0")
                }

                httpConnection.connect()
                val code = httpConnection.responseCode
                val duration = (System.currentTimeMillis() - start).toInt()
                
                if (code in 200..399) duration else -1
            } catch (e: Exception) {
                Log.w(TAG, "HTTP $method Ping failed: ${e.message}")
                -1
            } finally {
                runCatching { connection?.inputStream?.close() }
                runCatching { connection?.errorStream?.close() }
                connection?.disconnect()
            }
        }
    }

    /**
     * Strict HTTPS check: TCP connect alone is not enough for blocked CDN/server probes.
     */
    suspend fun pingHttpsStrict(
        host: String,
        port: Int,
        timeoutMs: Int,
        useProxy: Boolean = false,
        proxyPort: Int = LocalProxyConfig.PORT
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                val safeHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
                val url = URL(if (port == 443) "https://$safeHost/" else "https://$safeHost:$port/")
                val proxy = if (useProxy) {
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(LocalProxyConfig.HOST, proxyPort))
                } else null

                val connection = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Range", "bytes=0-0")
                connection.setRequestProperty("User-Agent", "Nimbo/${BuildConfig.VERSION_NAME}/Android")
                val code = connection.responseCode
                runCatching { connection.inputStream.close() }
                runCatching { connection.errorStream?.close() }
                connection.disconnect()
                val duration = (System.currentTimeMillis() - start).toInt()
                if (code in 100..599) duration else -1
            } catch (e: Exception) {
                Log.w(TAG, "HTTPS strict Ping failed: ${e.message}")
                -1
            }
        }
    }

    /**
     * ICMP Пинг: системный ping или isReachable
     */
    suspend fun pingIcmp(host: String, timeoutMs: Int): Int {
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                // На Android Process(ping) обычно стабильнее чем isReachable
                val timeoutSeconds = (timeoutMs / 1000).coerceAtLeast(1)
                val process = Runtime.getRuntime().exec("ping -c 1 -W $timeoutSeconds $host")
                val result = process.waitFor()
                val duration = (System.currentTimeMillis() - start).toInt()
                
                if (result == 0) duration else {
                    // Fallback to isReachable
                    if (InetAddress.getByName(host).isReachable(timeoutMs)) {
                        (System.currentTimeMillis() - start).toInt()
                    } else -1
                }
            } catch (e: Exception) {
                Log.w(TAG, "ICMP Ping failed: ${e.message}")
                -1
            }
        }
    }
}
