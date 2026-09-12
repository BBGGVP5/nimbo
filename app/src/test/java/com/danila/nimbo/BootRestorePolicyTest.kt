package com.danila.nimbo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRestorePolicyTest {

    @Test
    fun `boot follows auto connect setting`() {
        assertTrue(
            shouldRestoreVpnAfterSystemEvent(
                VpnRestoreTrigger.BOOT,
                autoConnect = true,
                connectionDesired = false
            )
        )
        assertFalse(
            shouldRestoreVpnAfterSystemEvent(
                VpnRestoreTrigger.BOOT,
                autoConnect = false,
                connectionDesired = true
            )
        )
    }

    @Test
    fun `package replacement restores only an intended connection`() {
        assertTrue(
            shouldRestoreVpnAfterSystemEvent(
                VpnRestoreTrigger.PACKAGE_REPLACED,
                autoConnect = false,
                connectionDesired = true
            )
        )
        assertFalse(
            shouldRestoreVpnAfterSystemEvent(
                VpnRestoreTrigger.PACKAGE_REPLACED,
                autoConnect = true,
                connectionDesired = false
            )
        )
    }
}
