package com.danila.nimbo.network

import com.danila.nimbo.model.UpdateKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationPolicyTest {

    @Test
    fun `notification is recorded only when every Android gate is open`() {
        assertTrue(
            UpdateNotificationPolicy.canPost(
                permissionGranted = true,
                appNotificationsEnabled = true,
                channelEnabled = true
            )
        )
        assertFalse(UpdateNotificationPolicy.canPost(false, true, true))
        assertFalse(UpdateNotificationPolicy.canPost(true, false, true))
        assertFalse(UpdateNotificationPolicy.canPost(true, true, false))
    }

    @Test
    fun `new artifact is delivered immediately and repair reminders are bounded`() {
        assertTrue(
            UpdateNotificationPolicy.shouldPost(
                identity = "new-artifact",
                lastIdentity = "old-artifact",
                kind = UpdateKind.REPAIR,
                lastNotifiedAt = 100L,
                now = 101L
            )
        )
        assertFalse(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.REPAIR,
                lastNotifiedAt = 100L,
                now = 101L
            )
        )
        assertTrue(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.REPAIR,
                lastNotifiedAt = 1L,
                now = 1L + UpdateNotificationPolicy.REPAIR_REMINDER_INTERVAL_MS
            )
        )
        assertFalse(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = UpdateNotificationPolicy.REPAIR_REMINDER_INTERVAL_MS * 2
            )
        )
    }

    @Test
    fun `clock rollback does not create a repair reminder loop`() {
        assertFalse(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.REPAIR,
                lastNotifiedAt = 10_000L,
                now = 5_000L
            )
        )
    }
}
