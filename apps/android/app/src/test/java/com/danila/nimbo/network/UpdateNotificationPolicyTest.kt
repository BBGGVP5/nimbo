package com.danila.nimbo.network

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
}
