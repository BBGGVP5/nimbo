package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionRefreshPolicyTest {
    @Test
    fun everySuccessfulResponseCountsAsUpdatedSubscription() {
        val summary = SubscriptionRefreshPolicy.summarize(
            successfulServerCounts = listOf(5),
            failedCount = 0
        )

        assertEquals(1, summary.updatedSubscriptions)
        assertEquals(5, summary.totalServers)
        assertEquals(0, summary.failedSubscriptions)
    }

    @Test
    fun summaryAddsServersAcrossSuccessfulSubscriptions() {
        val summary = SubscriptionRefreshPolicy.summarize(
            successfulServerCounts = listOf(3, 7),
            failedCount = 2
        )

        assertEquals(2, summary.updatedSubscriptions)
        assertEquals(10, summary.totalServers)
        assertEquals(2, summary.failedSubscriptions)
    }
}
