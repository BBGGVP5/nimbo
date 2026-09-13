package com.danila.nimbo.vpn

import com.danila.nimbo.network.ActiveProxyPing
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Startup probes use the verified running core before the UI becomes CONNECTED. */
internal object AutoSelectionProbe {
    suspend fun check(): List<Int> = coroutineScope {
        val session = HealthProxySessions.active ?: return@coroutineScope List(AutoSelectionPolicy.targets.size) { -1 }
        AutoSelectionPolicy.targets.map { target ->
            async {
                ActiveProxyPing.measure(
                    target.url, AutoSelectionPolicy.PROBE_TIMEOUT_MS, session,
                    { HealthProxySessions.active === session },
                    acceptsStatus = { AutoSelectionPolicy.acceptsResponse(target, it) }
                )
            }
        }.awaitAll()
    }
}
