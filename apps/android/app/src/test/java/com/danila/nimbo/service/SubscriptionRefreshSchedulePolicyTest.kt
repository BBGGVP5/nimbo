package com.danila.nimbo.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionRefreshSchedulePolicyTest {
    @Test
    fun firstRefreshIsDue() {
        assertTrue(SubscriptionRefreshSchedulePolicy.isDue(1_000_000L, 0L, 3_600))
    }

    @Test
    fun refreshWaitsForConfiguredInterval() {
        val last = 1_000_000L
        assertFalse(SubscriptionRefreshSchedulePolicy.isDue(last + 3_599_000L, last, 3_600))
        assertTrue(SubscriptionRefreshSchedulePolicy.isDue(last + 3_600_000L, last, 3_600))
    }

    @Test
    fun delaySupportsFiveMinuteUserInterval() {
        assertEquals(300L, SubscriptionRefreshSchedulePolicy.delaySeconds(300))
        assertEquals(300L, SubscriptionRefreshSchedulePolicy.delaySeconds(10))
    }

    @Test
    fun notificationRequiresChangeAndBackground() {
        assertTrue(SubscriptionRefreshSchedulePolicy.shouldShowSystemNotification(true, false, 1))
        assertFalse(SubscriptionRefreshSchedulePolicy.shouldShowSystemNotification(true, true, 1))
        assertFalse(SubscriptionRefreshSchedulePolicy.shouldShowSystemNotification(true, false, 0))
        assertFalse(SubscriptionRefreshSchedulePolicy.shouldShowSystemNotification(false, false, 1))
    }
}
