package com.danila.nimbo.network

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Measurement deadlines must not retroactively invalidate a result while cleanup joins. */
@androidx.annotation.Keep
internal object NimboPingDeadline {
    suspend fun withWorker(worker: Mutex, deadline: Long, now: () -> Long, probe: suspend () -> Int): Int {
        var acquired = false
        try {
            val remaining = deadline - now()
            if (remaining <= 0) return -1
            val entered = withTimeoutOrNull(remaining) {
                worker.lock()
                // Outside the timeout's result: even cancellation at handoff must release this lock.
                acquired = true
                true
            } ?: false
            if (!entered || now() >= deadline) return -1
            currentCoroutineContext().ensureActive()
            return probe()
        } finally {
            if (acquired) worker.unlock()
        }
    }

    suspend fun measureThenCleanup(
        deadline: Long, now: () -> Long,
        measurement: suspend () -> Int, cleanup: suspend () -> Unit
    ): Int {
        val result = try {
            val remaining = deadline - now()
            if (remaining <= 0) -1 else withTimeoutOrNull(remaining) { measurement() } ?: -1
        } finally {
            // Caller retains worker ownership until this joins. Cleanup has its own bounded wait.
            withContext(NonCancellable) { cleanup() }
        }
        // External cancellation is different from the completed measurement's own deadline.
        currentCoroutineContext().ensureActive()
        return result
    }
}
