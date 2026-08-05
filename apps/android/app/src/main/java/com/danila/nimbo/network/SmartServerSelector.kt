package com.danila.nimbo.network

import com.danila.nimbo.model.Server
import com.danila.nimbo.model.SmartServerGroup
import com.danila.nimbo.model.SmartServerHealth
import com.danila.nimbo.model.SmartServerStrategy

object SmartServerSelector {
    data class Selection(val server: Server, val score: Double, val explanation: String)

    fun select(
        group: SmartServerGroup,
        servers: List<Server>,
        health: Map<String, SmartServerHealth>,
        currentServerKey: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Selection? {
        val order = group.serverKeys.withIndex().associate { it.value to it.index }
        val candidates = servers.mapNotNull { server ->
            val key = server.selectionKey()
            val position = order[key] ?: return@mapNotNull null
            val snapshot = health[key]
            val latency = snapshot?.latencyMs ?: server.ping
            if (group.maxPingMs != null && latency != null && latency > group.maxPingMs) return@mapNotNull null
            val coolingDown = snapshot?.lastFailureAtMs?.let {
                snapshot.consecutiveFailures > 0 && nowMs - it < group.failureCooldownMs
            } == true
            if (coolingDown) return@mapNotNull null

            val successRate = snapshot?.successRate?.coerceIn(0.0, 1.0) ?: 1.0
            val latencyScore = (latency ?: 1_500).coerceAtLeast(1).toDouble()
            val score = when (group.strategy) {
                SmartServerStrategy.LOWEST_LATENCY -> latencyScore + (1.0 - successRate) * 4_000
                SmartServerStrategy.MOST_STABLE -> (1.0 - successRate) * 10_000 + latencyScore * 0.25
                SmartServerStrategy.FAILOVER_ORDER -> position * 10_000.0 + latencyScore * 0.01
                SmartServerStrategy.BALANCED -> latencyScore * 0.65 + (1.0 - successRate) * 5_000
            }
            Triple(server, key, score)
        }
        if (candidates.isEmpty()) return null

        val best = candidates.minBy { it.third }
        val current = currentServerKey?.let { key -> candidates.firstOrNull { it.second == key } }
        val threshold = group.switchThresholdPercent.coerceIn(0, 100) / 100.0
        val chosen = if (current != null && current.third <= best.third * (1.0 + threshold)) current else best
        return Selection(
            server = chosen.first,
            score = chosen.third,
            explanation = if (chosen === current) "kept-current-within-threshold" else group.strategy.name.lowercase()
        )
    }
}
