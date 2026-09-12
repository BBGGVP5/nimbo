package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.isAutoBalancerServer
import com.danila.nimbo.utils.isBypassServer

data class AutoSelectionProgress(
    val running: Boolean = false,
    val checked: Int = 0,
    val total: Int = 0,
    val serverName: String? = null,
    val confirmed: Boolean = false,
    val failed: Boolean = false
)

internal object AutoSelectionPolicy {
    const val PROBE_TIMEOUT_MS = 3_000
    const val CANDIDATE_BUDGET_MS = 12_000L

    // Independent services: a reachable CDN alone does not prove usable access.
    val targets = listOf(
        TunnelProbeTarget("Google", "https://www.gstatic.com/generate_204"),
        TunnelProbeTarget("Telegram", "https://telegram.org/"),
        TunnelProbeTarget("Cloudflare", "https://cp.cloudflare.com/generate_204")
    )

    fun isUsable(latencies: List<Int>): Boolean = latencies.count { it >= 0 } >= 2

    /**
     * Одна цифра на узел из нескольких проб: медиана успешных. Среднее здесь
     * врёт — одна залипшая цель утягивает результат, а нас интересует типичная
     * задержка сквозь узел. Если не ответил никто, узла считай что нет.
     */
    fun representativeLatency(latencies: List<Int>): Int {
        val ok = latencies.filter { it >= 0 }.sorted()
        if (ok.isEmpty()) return -1
        val middle = ok.size / 2
        return if (ok.size % 2 == 1) ok[middle] else (ok[middle - 1] + ok[middle]) / 2
    }

    fun acceptsResponse(target: TunnelProbeTarget, code: Int): Boolean =
        if (target.url.endsWith("generate_204")) code == 204 else code in 200..299

    // Compact request identifiers avoid putting long route credentials into Intent extras.
    fun candidateKey(server: Server): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(server.pingMeasurementKey().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /**
     * Порядок перебора кандидатов.
     *
     * Ключевое: узлы, уже проверенные сквозным запросом, идут впереди, а
     * проверенные и не поднявшиеся — в самый конец. Полагаться на [Server.ping]
     * как на единственный признак нельзя: у обходов с подменным адресом
     * (`google.com:456`) TCP-соединение принимает кто угодно на пути, цифра
     * выходит отличной, а туннель не встаёт — именно поэтому автоподбор раз за
     * разом предлагал мёртвые «Обход 1» и «Обход 3».
     */
    fun order(
        servers: List<Server>,
        restricted: Boolean,
        verified: Map<String, VerifiedLatency> = VerifiedLatencyStore.entries.value,
        nowMs: Long = System.currentTimeMillis()
    ): List<Server> {
        fun verdict(server: Server): VerifiedLatency? = verified[server.pingMeasurementKey()]
            ?.takeIf { nowMs - it.checkedAtMs <= VerifiedLatencyStore.FRESH_WINDOW_MS }

        // 0 — проверен и работает, 1 — не проверялся, 2 — проверен и не поднялся.
        fun rank(server: Server): Int = when (verdict(server)?.ok) {
            true -> 0
            null -> 1
            false -> 2
        }

        fun latency(server: Server): Int {
            verdict(server)?.takeIf { it.ok }?.let { return it.latencyMs }
            return server.ping?.takeIf { value -> value > 0 } ?: Int.MAX_VALUE
        }

        return servers
            .filterNot(::isAutoBalancerServer)
            .distinctBy { it.pingMeasurementKey() }
            .sortedWith(compareBy<Server>(
                { if (restricted && isBypassCandidate(it)) 0 else 1 },
                { rank(it) },
                { latency(it) }
            ))
    }

    private fun isBypassCandidate(server: Server): Boolean {
        val label = "${server.name} ${server.templateName.orEmpty()}".lowercase()
        return server.isFallback || isBypassServer(server) ||
            listOf("обход", "lte", "whitelist", "белые списки").any(label::contains)
    }
}
