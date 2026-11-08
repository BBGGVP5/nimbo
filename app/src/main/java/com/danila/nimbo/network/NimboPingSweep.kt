package com.danila.nimbo.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * [probe] replaces the Xray diagnostic for protocols whose transport lives in
 * a sidecar (TrustTunnel's authenticated health check).
 */
internal data class NimboPingTarget(
    val key: String,
    val config: String?,
    val probe: (suspend () -> Int)? = null
)

/** Immutable node/config pairing; equal endpoints never merge different row results. */
internal object NimboPingSweep {
    suspend fun run(
        targets: List<NimboPingTarget>,
        measure: suspend (String) -> Int,
        publish: (String, Int) -> Unit
    ) {
        for (target in targets.toList()) {
            currentCoroutineContext().ensureActive()
            val value = target.probe?.invoke() ?: target.config?.let { measure(it) } ?: -1
            currentCoroutineContext().ensureActive()
            publish(target.key, value)
        }
    }
}
