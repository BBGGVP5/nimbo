package com.danila.nimbo.vpn

internal data class TunnelProbeTarget(
    val name: String,
    val url: String,
    val weight: Int = 1
)

/** Pure policy and endpoint catalog for checks that must traverse the Xray outbound. */
internal object TunnelHealthPolicy {
    const val REQUEST_TIMEOUT_MS = 1_500

    val healthTargets = listOf(
        TunnelProbeTarget("Google CDN", "https://www.gstatic.com/generate_204"),
        TunnelProbeTarget("Cloudflare", "https://cp.cloudflare.com/generate_204")
    )

    val serviceTargets = listOf(
        TunnelProbeTarget("Google", "https://www.google.com/generate_204", 4),
        TunnelProbeTarget("Google CDN", "https://www.gstatic.com/generate_204", 3),
        TunnelProbeTarget("Telegram", "https://telegram.org/", 4),
        TunnelProbeTarget("YouTube", "https://www.youtube.com/", 4),
        TunnelProbeTarget("Cloudflare", "https://cp.cloudflare.com/generate_204", 2)
    )

    fun isHealthy(latenciesMs: List<Int>): Boolean = latenciesMs.any { it >= 0 }

    fun successCount(latenciesMs: List<Int>): Int = latenciesMs.count { it >= 0 }
}
