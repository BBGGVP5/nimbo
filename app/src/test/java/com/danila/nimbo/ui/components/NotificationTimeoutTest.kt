package com.danila.nimbo.ui.components

import org.junit.Assert.*
import org.junit.Test

class NotificationTimeoutTest {
    @Test fun everyBannerIncludingWorkInProgressHasABoundedDefaultLifetime() {
        NotificationType.entries.forEach {
            assertTrue(notificationDurationMillis(it) in 4_000L..8_000L)
            assertEquals(notificationDurationMillis(it), notificationTimeoutMillis(it, null))
        }
    }

    @Test fun accessibilityReadingTimeIsNeverShortenedByTheDefault() {
        NotificationType.entries.forEach {
            assertEquals(30_000L, notificationTimeoutMillis(it, 30_000L))
            assertEquals(notificationDurationMillis(it), notificationTimeoutMillis(it, 1_000L))
            assertEquals(notificationDurationMillis(it), notificationTimeoutMillis(it, -1L))
        }
    }

    @Test fun assistiveTechnologyCanKeepControlsAvailableUntilExplicitDismissal() {
        assertEquals(Long.MAX_VALUE, notificationTimeoutMillis(NotificationType.ERROR, Long.MAX_VALUE))
    }
}
