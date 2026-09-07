package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSchedulePolicyTest {

    @Test
    fun `periodic update cadence is the WorkManager minimum without resetting legacy work`() {
        assertEquals(15L, UpdateSchedulePolicy.PERIODIC_INTERVAL_MINUTES)
        assertEquals(5L, UpdateSchedulePolicy.PERIODIC_FLEX_MINUTES)
        assertNotEquals(
            UpdateSchedulePolicy.LEGACY_PERIODIC_WORK_NAME,
            UpdateSchedulePolicy.PERIODIC_WORK_NAME
        )
        assertTrue(UpdateSchedulePolicy.PERIODIC_WORK_NAME.contains("15m"))
    }

    @Test
    fun `foreground exit and system immediate checks cannot replace each other`() {
        assertNotEquals(
            UpdateSchedulePolicy.IMMEDIATE_WORK_NAME,
            UpdateSchedulePolicy.BACKGROUND_CATCH_UP_WORK_NAME
        )
    }
}
