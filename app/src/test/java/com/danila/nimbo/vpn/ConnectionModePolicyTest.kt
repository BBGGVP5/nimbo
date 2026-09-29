package com.danila.nimbo.vpn

import org.junit.Assert.*
import org.junit.Test

class ConnectionModePolicyTest {
    @Test fun selectingAutoWhileDisconnectedNeverRequestsConnection() {
        assertFalse(ConnectionModePolicy.shouldApplyImmediately(false, false))
        assertFalse(ConnectionModePolicy.shouldApplyImmediately(false, true))
        assertFalse(ConnectionModePolicy.shouldApplyImmediately(true, false))
        assertTrue(ConnectionModePolicy.shouldApplyImmediately(true, true))
    }

    @Test fun autoUsesOnlyItsSavedProfileRegardlessOfLastConnectedProfile() {
        val profiles = mapOf("chosen" to listOf("a", "b", "a"), "last-connected" to listOf("other"))
        assertEquals(listOf("a", "b"), ConnectionModePolicy.candidates("chosen", profiles))
        assertTrue(ConnectionModePolicy.candidates("deleted", profiles).isEmpty())
        assertTrue(ConnectionModePolicy.candidates(null, profiles).isEmpty())
    }

    @Test fun automaticModeRecoversEvenWhenManualAutoReconnectIsDisabled() {
        assertFalse(ConnectionModePolicy.recoveryEnabled(false, false))
        assertTrue(ConnectionModePolicy.recoveryEnabled(true, false))
        assertTrue(ConnectionModePolicy.recoveryEnabled(false, true))
        val connecting = VpnRecoveryPolicy.reduce(VpnRecoveryPolicy.State(),
            VpnRecoveryPolicy.Event.ManualConnect(true)).state
        val failure = VpnRecoveryPolicy.reduce(connecting, VpnRecoveryPolicy.Event.ConnectFailed(
            retryable = true, autoRecoveryEnabled = ConnectionModePolicy.recoveryEnabled(true, false),
            hasServer = true))
        assertTrue(failure.commands.any { it is VpnRecoveryPolicy.Command.ScheduleRetry })
        val stopped = VpnRecoveryPolicy.reduce(failure.state, VpnRecoveryPolicy.Event.ManualDisconnect)
        val lateRetry = VpnRecoveryPolicy.reduce(stopped.state, VpnRecoveryPolicy.Event.RetryElapsed)
        assertFalse(lateRetry.state.desiredConnected)
        assertFalse(lateRetry.commands.contains(VpnRecoveryPolicy.Command.StartConnection))
    }
}
