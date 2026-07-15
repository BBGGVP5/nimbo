package com.danila.nimbo.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.danila.nimbo.model.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class ActiveNetworkType {
    WIFI,
    MOBILE,
    OTHER,
    NONE
}

fun detectActiveNetworkType(context: Context): ActiveNetworkType {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return ActiveNetworkType.NONE
    val network = cm.activeNetwork ?: return ActiveNetworkType.NONE
    val caps = cm.getNetworkCapabilities(network) ?: return ActiveNetworkType.NONE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ActiveNetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ActiveNetworkType.MOBILE
        else -> ActiveNetworkType.OTHER
    }
}

fun isBypassServer(server: Server): Boolean {
    if (isAutoBalancerServer(server)) return false

    val host = server.host.trim().lowercase()
    if (host.isBlank()) return false
    if (host == "ya.nebulaguard.ru" || host == "yan.nebulaguard.ru") return true
    return host.endsWith(".ru")
}

fun isAutoBalancerServer(server: Server): Boolean {
    fun hasAutoBalancerMarker(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return false
        return normalized.contains("autobalancer") ||
            normalized.contains("balancer") ||
            normalized.contains("балансер") ||
            normalized.contains("loadbalance")
    }

    val nameLower = server.name.trim().lowercase()
    val hasTemplateHints = !server.templateName.isNullOrBlank() || !server.templateUuid.isNullOrBlank()
    val remoteTemplateCandidate = (server.uuid == "remote" || server.host.equals("api", ignoreCase = true)) &&
        hasTemplateHints &&
        (nameLower.contains("автобалансер") || nameLower.contains("balancer") || nameLower.contains("балансер"))
    val namedBypassAutoBalancer = (nameLower.contains("автобалансер") || nameLower.contains("balancer") || nameLower.contains("балансер")) &&
        nameLower.contains("|") &&
        nameLower.contains("обход")

    return hasAutoBalancerMarker(server.templateName) ||
        hasAutoBalancerMarker(server.templateUuid) ||
        hasAutoBalancerMarker(server.serverDescription) ||
        hasAutoBalancerMarker(server.name) ||
        remoteTemplateCandidate ||
        namedBypassAutoBalancer
}

fun isRuDomainServer(server: Server): Boolean {
    val host = server.host.trim().lowercase()
    if (host.isBlank()) return false
    if (host == "ya.nebulaguard.ru" || host == "yan.nebulaguard.ru") return true
    return host.endsWith(".ru")
}

fun filterServersForPolicies(
    servers: List<Server>,
    autoBypassByNetwork: Boolean,
    networkType: ActiveNetworkType,
    shouldUseBypassOnly: Boolean,
    blockBypassWhenDomainReachable: Boolean = false
): List<Server> {
    val baseServers = if (blockBypassWhenDomainReachable) {
        val withoutBypass = servers.filter { server ->
            !isBypassServer(server) || isAutoBalancerServer(server)
        }
        if (withoutBypass.isNotEmpty()) withoutBypass else servers
    } else servers

    if (!autoBypassByNetwork) return baseServers

    val nonBypassServers = baseServers.filterNot(::isBypassServer)

    return when {
        // В BS-режиме показываем только серверы с .ru-доменом и автобалансеры.
        shouldUseBypassOnly -> servers
            .filter { isRuDomainServer(it) || isAutoBalancerServer(it) }
            .distinctBy { it.pingKey() }
        blockBypassWhenDomainReachable && nonBypassServers.isNotEmpty() -> nonBypassServers
        else -> baseServers
    }
}

fun extractHostFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching { java.net.URI(url).host }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

suspend fun canReachDomain(domain: String, port: Int = 443, timeoutMs: Int = 3000): Boolean =
    withContext(Dispatchers.IO) {
        val normalizedDomain = domain.trim()
        if (normalizedDomain.isBlank()) return@withContext false

        val addresses = runCatching { InetAddress.getAllByName(normalizedDomain).toList() }
            .getOrDefault(emptyList())
        if (addresses.isEmpty()) return@withContext false

        val socketTimeout = (timeoutMs * 0.65).toInt().coerceAtLeast(900)
        val tcpReachable = addresses.take(3).any { address ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), socketTimeout)
                }
                true
            }.getOrDefault(false)
        }
        if (tcpReachable) return@withContext true

        val protocol = if (port == 443) "https" else "http"
        val probePaths = listOf("/", "/generate_204")
        val methods = listOf("HEAD", "GET")
        methods.any { method ->
            probePaths.any { path ->
                runCatching {
                    val conn = java.net.URL("$protocol://$normalizedDomain$path").openConnection() as HttpURLConnection
                    conn.connectTimeout = timeoutMs
                    conn.readTimeout = timeoutMs
                    conn.instanceFollowRedirects = true
                    conn.requestMethod = method
                    if (method == "GET") {
                        conn.setRequestProperty("Range", "bytes=0-0")
                    }
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    code in 100..599
                }.getOrDefault(false)
            }
        }
    }

suspend fun shouldUseBypassForNetwork(controlDomain: String, timeoutMs: Int = 2600): Boolean {
    val domain = controlDomain.trim()
    if (domain.isBlank()) return false
    // BS-режим определяется ТОЛЬКО по одному контрольному домену.
    // Если домен недоступен — включаем обходы.
    return !canReachControlDomain(domain, timeoutMs = timeoutMs)
}

suspend fun canReachControlDomain(controlDomain: String, timeoutMs: Int = 2600): Boolean {
    val domain = controlDomain.trim()
    if (domain.isBlank()) return false
    return canReachDomainStable(domain, timeoutMs = timeoutMs)
}

private suspend fun canReachDomainStable(
    domain: String,
    port: Int = 443,
    timeoutMs: Int = 2600,
    attempts: Int = 3
): Boolean {
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        if (canReachDomain(domain, port = port, timeoutMs = timeoutMs)) return true
        if (attempt < attempts - 1) delay(250L + (attempt * 150L))
    }
    return false
}
