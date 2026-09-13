package com.danila.nimbo.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Only polls local core observations; never retries the user's target GET. */
@androidx.annotation.Keep // Stable DEX marker for distinguishing pre-correction APKs.
internal object NimboPingReadiness {
    const val MAX_BODY_BYTES = 1024 * 1024
    fun ready(payload: String?, candidates: Set<String>): Boolean = runCatching {
        if (payload == null || payload.toByteArray().size > MAX_BODY_BYTES) return false
        val observations = JSONObject(payload).optJSONObject("observatory") ?: return false
        candidates.any { tag ->
            val status = observations.optJSONObject(tag)
            // Native protobuf's JSON omits a zero delay; leastPing treats it as zero too.
            status?.optBoolean("alive") == true && status.optLong("delay", 0) in 0 until 99_999_999L
        }
    }.getOrDefault(false)

    suspend fun await(
        candidates: Set<String>, deadlineMs: Long, now: () -> Long,
        read: suspend (Int) -> String?, pause: suspend (Long) -> Unit = { delay(it) }
    ): Boolean {
        if (candidates.isEmpty()) return true
        repeat(130) {
            currentCoroutineContext().ensureActive()
            val remaining = deadlineMs - now()
            if (remaining < 250) return false
            val payload = read(minOf(remaining, 500).toInt())
            currentCoroutineContext().ensureActive()
            if (now() >= deadlineMs) return false
            if (ready(payload, candidates)) return true
            pause(minOf(100, (deadlineMs - now()).coerceAtLeast(0)))
        }
        return false
    }

    suspend fun measureOnce(awaitReady: suspend () -> Boolean, measure: suspend () -> Int): Int {
        if (!awaitReady()) return -1
        currentCoroutineContext().ensureActive()
        return measure() // Including HTTP failure: never repeat this call.
    }
}
