package com.danila.nimbo.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class NimboPingTarget(val key: String, val config: String?)

/** Immutable node/config pairing; equal endpoints never merge different row results. */
internal object NimboPingSweep {
    suspend fun run(
        targets: List<NimboPingTarget>,
        measure: suspend (String) -> Int,
        publish: (String, Int) -> Unit
    ) {
        for (target in targets.toList()) {
            currentCoroutineContext().ensureActive()
            val value = target.config?.let { measure(it) } ?: -1
            currentCoroutineContext().ensureActive()
            publish(target.key, value)
        }
    }
}
