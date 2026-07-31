package com.danila.nimbo.vpn

import kotlin.math.min

/** Time and retry rules shared by initial connection and recovery cycles. */
internal object ConnectionAttemptPolicy {
    const val TOTAL_CYCLE_BUDGET_MS = 60_000L
    const val PER_ATTEMPT_TIMEOUT_MS = 15_000L
    const val FINAL_CONNECT_RESERVE_MS = 20_000L
    const val MIN_USEFUL_ATTEMPT_MS = 3_000L
    const val CANDIDATE_COOLDOWN_MS = 120_000L
    const val NORMAL_MAX_ATTEMPTS = 2
    const val PROBE_MAX_ATTEMPTS = 1
    const val MAX_RANKING_CANDIDATES = 3
    const val DIRECT_REACHABILITY_TIMEOUT_MS = 2_000

    private val deterministicErrorMarkers = listOf(
        "invalid config",
        "failed to parse",
        "parse config",
        "unsupported protocol",
        "unknown protocol",
        "missing required",
        "template config is empty"
    )

    val baselineTargets = listOf(
        TunnelProbeTarget("Google CDN", "https://www.gstatic.com/generate_204"),
        TunnelProbeTarget("Cloudflare", "https://cp.cloudflare.com/generate_204")
    )

    val restrictedServiceTargets = listOf(
        TunnelProbeTarget("Telegram", "https://telegram.org/"),
        TunnelProbeTarget("YouTube", "https://www.youtube.com/")
    )

    fun attemptTimeoutMs(remainingCycleMs: Long): Long =
        min(PER_ATTEMPT_TIMEOUT_MS, remainingCycleMs.coerceAtLeast(0L))

    fun isCoolingDown(
        failedAtMs: Long?,
        nowMs: Long,
        explicitSelection: Boolean
    ): Boolean {
        if (explicitSelection || failedAtMs == null || nowMs < failedAtMs) return false
        return nowMs - failedAtMs < CANDIDATE_COOLDOWN_MS
    }

    fun isRestricted(
        baselineReachability: List<Boolean>,
        targetReachability: List<Boolean>
    ): Boolean = baselineReachability.any { it } &&
        targetReachability.isNotEmpty() &&
        targetReachability.none { it }

    fun shouldRetry(error: String?, attempt: Int, maxAttempts: Int): Boolean {
        if (attempt + 1 >= maxAttempts) return false
        val normalized = error?.lowercase().orEmpty()
        return deterministicErrorMarkers.none(normalized::contains)
    }

    fun canStartRankingProbe(remainingCycleMs: Long): Boolean =
        remainingCycleMs >= FINAL_CONNECT_RESERVE_MS + MIN_USEFUL_ATTEMPT_MS

    fun shouldDetectRestrictedNetwork(
        autoRotationEnabled: Boolean,
        selectedIsAutoBalancer: Boolean
    ): Boolean = autoRotationEnabled && !selectedIsAutoBalancer
}
