package com.danila.nimbo.network

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateWorkSchedulerTest {

    @Test
    fun `normal boot and package replacement request an immediate check`() {
        assertTrue(UpdateWorkScheduler.shouldEnqueueImmediate(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(UpdateWorkScheduler.shouldEnqueueImmediate(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(UpdateWorkScheduler.shouldEnqueueImmediate(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertFalse(UpdateWorkScheduler.shouldEnqueueImmediate("unknown.action"))
    }
}
