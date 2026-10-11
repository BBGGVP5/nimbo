package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NimboNotificationPolicyTest {
    private fun note(id: String) = NimboNotification(id, "Активность", "Сообщение", NimboNotificationKind.ACTIVITY, 1, "12:00")

    @Test fun oldTimerCannotDismissANewerToast() {
        assertTrue(shouldDismissNotification(note("old"), "old"))
        assertFalse(shouldDismissNotification(note("new"), "old"))
        assertFalse(shouldDismissNotification(null, "old"))
    }

    @Test fun legacyDuplicateIdsAreRepairedDeterministicallyWithoutDroppingMessages() {
        val original = listOf(note("same"), note("same"), note(""), note("same"))
        val repaired = normalizeNotificationIds(original)
        assertEquals(4, repaired.size)
        assertEquals(4, repaired.map { it.id }.toSet().size)
        assertTrue(repaired.all { it.id.isNotBlank() })
        assertEquals(repaired, normalizeNotificationIds(original))
        assertEquals(repaired, normalizeNotificationIds(repaired))
        assertEquals(original.map { it.message }, repaired.map { it.message })
    }

    @Test fun finiteTimeoutsMatchAndroidAndRespectAccessibility() {
        assertEquals(4_000L, notificationTimeoutMillis(NimboNotificationKind.ACTIVITY))
        assertEquals(4_000L, notificationTimeoutMillis(NimboNotificationKind.SUCCESS))
        assertEquals(6_000L, notificationTimeoutMillis(NimboNotificationKind.ERROR))
        assertEquals(8_000L, notificationTimeoutMillis(NimboNotificationKind.UPDATE))
        assertEquals(12_000L, notificationTimeoutMillis(NimboNotificationKind.ERROR, 12_000L))
    }
}
