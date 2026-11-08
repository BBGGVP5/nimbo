package com.danila.nimbo.vpn

import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.model.Server
import com.danila.nimbo.shared.subscription.TrustTunnelEndpoint
import com.danila.nimbo.shared.subscription.TrustTunnelLink
import com.danila.nimbo.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress
import java.util.UUID

/**
 * TrustTunnel (AdGuard) on Android: Mihomo's protocol client inside the same Go
 * runtime as Xray (`nimbo/trusttunnelcore`), exposed as an authenticated
 * loopback SOCKS5 endpoint that Xray's socks outbound uses. Unlike the official
 * client it has no anti-DPI ClientHello splitting and no client_random; such
 * links still connect when the endpoint does not require them.
 */
object TrustTunnelManager {
    private const val TAG = "TrustTunnelManager"
    private const val START_TIMEOUT_MS = 10_000

    /** Starts the bridge and returns [server] pointed at its local SOCKS port. */
    suspend fun start(server: Server, protect: (Int) -> Boolean): Server = withContext(Dispatchers.IO) {
        require(server.isTrustTunnel()) { "Server is not a TrustTunnel endpoint" }
        stop()
        NebulaGuardApplication.ensureXrayCoreLoaded()
        val endpoint = decode(server)
        val username = UUID.randomUUID().toString().replace("-", "")
        val password = UUID.randomUUID().toString().replace("-", "")
        val request = TrustTunnelLink.bridgeRequest(
            endpoint, resolveDialAddress(endpoint), username, password, START_TIMEOUT_MS
        )
        val response = JSONObject(LibXray.nimboTrustTunnelStart(request, controller(protect)))
        if (!response.optBoolean("ok")) {
            val code = response.optString("error")
            Logger.w(TAG, "TrustTunnel bridge refused to start: $code")
            throw IllegalStateException(TrustTunnelLink.bridgeErrorMessage(code, endpoint))
        }
        val port = response.optInt("port")
        check(port in 1..65535) { "TrustTunnel не открыл локальный порт" }
        Logger.i(TAG, "TrustTunnel bridge is ready on loopback port $port")
        server.copy(
            trustTunnelSocksPort = port,
            trustTunnelSocksUsername = username,
            trustTunnelSocksPassword = password
        )
    }

    fun stop() {
        runCatching { LibXray.nimboTrustTunnelStop() }
    }

    fun isRunning(): Boolean = runCatching {
        JSONObject(LibXray.nimboTrustTunnelStatus()).optBoolean("running")
    }.getOrDefault(false)

    fun networkChanged() {
        runCatching { LibXray.nimboTrustTunnelNetworkChanged() }
    }

    /**
     * Honest latency: one authenticated health check through an independent
     * client. A TCP connect proves neither credentials nor HTTP/3 reachability.
     * Returns -1 on failure. [protect] is required while the device VPN is up.
     */
    suspend fun probe(server: Server, timeoutMs: Int, protect: ((Int) -> Boolean)?): Int = withContext(Dispatchers.IO) {
        runCatching {
            NebulaGuardApplication.ensureXrayCoreLoaded()
            val endpoint = decode(server)
            val request = TrustTunnelLink.bridgeRequest(
                endpoint, resolveDialAddress(endpoint), timeoutMs = timeoutMs.coerceIn(1_000, 30_000)
            )
            val response = JSONObject(LibXray.nimboTrustTunnelProbe(request, protect?.let(::controller)))
            if (response.optBoolean("ok")) response.optInt("latencyMs", -1) else -1
        }.getOrDefault(-1)
    }

    private fun decode(server: Server): TrustTunnelEndpoint {
        val link = server.trustTunnelLink?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("У сервера TrustTunnel нет ссылки tt://")
        return TrustTunnelLink.decode(link)
    }

    private fun controller(protect: (Int) -> Boolean) = object : DialerController {
        override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
    }

    /**
     * The Go client takes an IP: the app is excluded from its own VPN, so this
     * lookup never loops through the tunnel. Addresses are tried in link order.
     */
    private fun resolveDialAddress(endpoint: TrustTunnelEndpoint): String {
        var lastError: Throwable? = null
        for (address in endpoint.addresses) {
            val (host, port) = TrustTunnelLink.splitHostPort(address)
            val resolved = runCatching { InetAddress.getAllByName(host).toList() }
                .onFailure { lastError = it }
                .getOrNull()
                ?.sortedBy { if (it is Inet6Address) 1 else 0 }
                ?.firstOrNull()
                ?: continue
            val ip = resolved.hostAddress ?: continue
            return if (resolved is Inet6Address) "[$ip]:$port" else "$ip:$port"
        }
        throw IllegalStateException("Не удалось разрешить адрес сервера TrustTunnel", lastError)
    }
}
