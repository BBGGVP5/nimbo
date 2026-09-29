package com.danila.nimbo.vpn

import org.junit.Assert.*
import org.junit.Test

class ConnectionOperationGuardTest {
    @Test fun manualPickInvalidatesPendingConsentAndAutoHealthResults() {
        val guard = ConnectionOperationGuard()
        val pendingConsent = guard.invalidate()
        val healthProbe = guard.current
        guard.invalidate() // explicit manual selection
        assertFalse(guard.isCurrent(pendingConsent))
        assertFalse(guard.isCurrent(healthProbe))
    }

    @Test fun disconnectPreventsQueuedSwitchFromReconnecting() {
        val guard = ConnectionOperationGuard()
        val queuedSwitch = guard.invalidate()
        guard.invalidate() // disconnect while cancelAndJoin is suspended
        assertFalse(guard.isCurrent(queuedSwitch))
        val reconnect = guard.invalidate()
        assertTrue(guard.isCurrent(reconnect))
        assertFalse(guard.isCurrent(queuedSwitch))
    }

    @Test fun laterConnectWinsOverEarlierQueuedServiceIntent() {
        val guard = ConnectionOperationGuard()
        val first = guard.invalidate()
        val second = guard.invalidate()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
    }
}
