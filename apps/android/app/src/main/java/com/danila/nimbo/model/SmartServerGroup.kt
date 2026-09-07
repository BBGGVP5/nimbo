package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName

enum class SmartServerStrategy {
    BALANCED,
    LOWEST_LATENCY,
    MOST_STABLE,
    FAILOVER_ORDER
}

data class SmartServerGroup(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("serverKeys") val serverKeys: List<String>,
    @SerializedName("strategy") val strategy: SmartServerStrategy = SmartServerStrategy.BALANCED,
    @SerializedName("maxPingMs") val maxPingMs: Int? = null,
    @SerializedName("failureCooldownMs") val failureCooldownMs: Long = 120_000L,
    @SerializedName("switchThresholdPercent") val switchThresholdPercent: Int = 20,
    @SerializedName("updatedAtMs") val updatedAtMs: Long = System.currentTimeMillis()
)

data class SmartServerHealth(
    @SerializedName("serverKey") val serverKey: String,
    @SerializedName("latencyMs") val latencyMs: Int? = null,
    @SerializedName("successRate") val successRate: Double = 1.0,
    @SerializedName("consecutiveFailures") val consecutiveFailures: Int = 0,
    @SerializedName("lastFailureAtMs") val lastFailureAtMs: Long? = null,
    @SerializedName("lastSuccessAtMs") val lastSuccessAtMs: Long? = null
)
