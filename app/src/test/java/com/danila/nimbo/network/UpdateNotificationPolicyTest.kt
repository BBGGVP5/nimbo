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
        // Раньше здесь ожидался false: об одной и той же версии напоминали ровно
        // один раз. Именно из-за этого пропущенное уведомление уже не повторялось.
        assertTrue(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = UpdateNotificationPolicy.VERSION_REMINDER_INTERVAL_MS
            )
        )
    }

    @Test
    fun `version reminder waits a day and stops after the limit`() {
        assertFalse(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = UpdateNotificationPolicy.VERSION_REMINDER_INTERVAL_MS - 1,
                notifiedCount = 1
            )
        )
        assertTrue(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = UpdateNotificationPolicy.VERSION_REMINDER_INTERVAL_MS,
                notifiedCount = UpdateNotificationPolicy.MAX_VERSION_REMINDERS - 1
            )
        )
        assertFalse(
            UpdateNotificationPolicy.shouldPost(
                identity = "same-artifact",
                lastIdentity = "same-artifact",
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = UpdateNotificationPolicy.VERSION_REMINDER_INTERVAL_MS * 10,
                notifiedCount = UpdateNotificationPolicy.MAX_VERSION_REMINDERS
            )
        )
    }

    @Test
    fun `explicitly skipped build is never reminded about`() {
        assertFalse(
            UpdateNotificationPolicy.shouldPost(
                identity = "skipped-artifact",
                lastIdentity = null,
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = UpdateNotificationPolicy.VERSION_REMINDER_INTERVAL_MS * 5,
                skippedIdentity = "skipped-artifact"
            )
        )
        assertTrue(
            UpdateNotificationPolicy.shouldPost(
                identity = "fresh-artifact",
                lastIdentity = null,
                kind = UpdateKind.VERSION,
                lastNotifiedAt = 0L,
                now = 1L,
                skippedIdentity = "skipped-artifact"
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
