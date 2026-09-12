package com.danila.nimbo.network

data class SubscriptionRefreshSummary(
    val updatedSubscriptions: Int,
    val failedSubscriptions: Int,
    val totalServers: Int
)

/** Successful network refreshes are reported as updates regardless of content equality. */
object SubscriptionRefreshPolicy {
    fun summarize(
        successfulServerCounts: List<Int>,
        failedCount: Int
    ): SubscriptionRefreshSummary = SubscriptionRefreshSummary(
        updatedSubscriptions = successfulServerCounts.size,
        failedSubscriptions = failedCount.coerceAtLeast(0),
        totalServers = successfulServerCounts.sumOf { it.coerceAtLeast(0) }
    )
}
