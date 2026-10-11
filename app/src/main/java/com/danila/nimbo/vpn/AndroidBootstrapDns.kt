package com.danila.nimbo.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CancellationSignal
import android.os.HandlerThread
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** System DNS on the physical network, including its Private DNS policy and failover. */
internal class AndroidBootstrapDns(context: Context, private val network: Network) : BootstrapDnsExchange {
    private val closed = AtomicBoolean()
    private val pending = ConcurrentHashMap<CancellationSignal, CompletableFuture<ByteArray?>>()
    private val resolverThread = if (Build.VERSION.SDK_INT >= 37) {
        HandlerThread("nimbo-platform-dns").apply { start() }
    } else null
    @Suppress("DEPRECATION")
    private val resolver = if (Build.VERSION.SDK_INT >= 37) {
        DnsResolver(context.applicationContext, requireNotNull(resolverThread).looper)
    } else DnsResolver.getInstance()

    init {
        val capabilities = context.getSystemService(ConnectivityManager::class.java)
            .getNetworkCapabilities(network)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) != true) {
            close()
            error("Physical network unavailable for Xray bootstrap DNS")
        }
    }

    override fun resolve(query: ByteArray): ByteArray? {
        if (closed.get()) return null
        val cancellation = CancellationSignal()
        val result = CompletableFuture<ByteArray?>()
        pending[cancellation] = result
        try {
            if (closed.get()) return null
            resolver.rawQuery(
                network, query, DnsResolver.FLAG_EMPTY, Executor { it.run() }, cancellation,
                object : DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer: ByteArray, rcode: Int) { result.complete(answer) }
                    override fun onError(error: DnsResolver.DnsException) { result.complete(null) }
                }
            )
            return result.get(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (_: Exception) {
            return null
        } finally {
            pending.remove(cancellation)
            cancellation.cancel()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pending.forEach { (cancellation, result) -> cancellation.cancel(); result.complete(null) }
        pending.clear()
        resolverThread?.quitSafely()
    }
}
