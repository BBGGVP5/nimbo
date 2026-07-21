package com.danila.nimbo.vpn

import com.danila.nimbo.vpn.VpnRecoveryPolicy.Command
import com.danila.nimbo.vpn.VpnRecoveryPolicy.Event
import com.danila.nimbo.vpn.VpnRecoveryPolicy.Phase
import com.danila.nimbo.vpn.VpnRecoveryPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRecoveryPolicyTest {

    @Test
    fun screenCycle_stopsTunnelAndReconnectsOnce() {
        val connected = State(desiredConnected = true, phase = Phase.CONNECTED)

        val screenOff = VpnRecoveryPolicy.reduce(connected, Event.ScreenOff(pauseEnabled = true))
        val firstScreenOn = VpnRecoveryPolicy.reduce(
            screenOff.state,
            Event.ScreenOn(resumeEnabled = true, hasServer = true)
        )
        val duplicateScreenOn = VpnRecoveryPolicy.reduce(
            firstScreenOn.state,
            Event.ScreenOn(resumeEnabled = true, hasServer = true)
        )

        assertEquals(listOf(Command.CancelRetry, Command.StopTunnelForScreen), screenOff.commands)
        assertTrue(screenOff.state.screenPaused)
        assertEquals(listOf(Command.CancelRetry, Command.StartConnection), firstScreenOn.commands)
        assertTrue(duplicateScreenOn.commands.isEmpty())
    }

    @Test
    fun screenOffWhileConnecting_cancelsHiddenConnection() {
        val connecting = State(
            desiredConnected = true,
            phase = Phase.CONNECTING,
            connectPending = true
        )

        val result = VpnRecoveryPolicy.reduce(connecting, Event.ScreenOff(pauseEnabled = true))

        assertEquals(Phase.PAUSED_BY_SCREEN, result.state.phase)
        assertFalse(result.state.connectPending)
        assertTrue(result.commands.contains(Command.StopTunnelForScreen))
    }

    @Test
    fun screenOnWithResumeDisabled_stopsServiceAndClearsIntent() {
        val paused = State(
            desiredConnected = true,
            phase = Phase.PAUSED_BY_SCREEN,
            screenPaused = true
        )

        val result = VpnRecoveryPolicy.reduce(
            paused,
            Event.ScreenOn(resumeEnabled = false, hasServer = true)
        )

        assertFalse(result.state.desiredConnected)
        assertEquals(listOf(Command.CancelRetry, Command.StopService), result.commands)
    }

    @Test
    fun manualDisconnect_beatsScreenAndNetworkRecovery() {
        val active = State(desiredConnected = true, phase = Phase.CONNECTED)
        val stopped = VpnRecoveryPolicy.reduce(active, Event.ManualDisconnect)
        val network = VpnRecoveryPolicy.reduce(
            stopped.state,
            Event.NetworkChanged(available = true, autoRecoveryEnabled = true, hasServer = true)
        )
        val screen = VpnRecoveryPolicy.reduce(
            network.state,
            Event.ScreenOn(resumeEnabled = true, hasServer = true)
        )

        assertFalse(stopped.state.desiredConnected)
        assertTrue(network.commands.isEmpty())
        assertTrue(screen.commands.isEmpty())
    }

    @Test
    fun networkLossAndReturn_reconnectsOnlyOnce() {
        val active = State(desiredConnected = true, phase = Phase.CONNECTED)
        val lost = VpnRecoveryPolicy.reduce(
            active,
            Event.NetworkChanged(available = false, autoRecoveryEnabled = true, hasServer = true)
        )
        val available = VpnRecoveryPolicy.reduce(
            lost.state,
            Event.NetworkChanged(available = true, autoRecoveryEnabled = true, hasServer = true)
        )
        val duplicate = VpnRecoveryPolicy.reduce(
            available.state,
            Event.NetworkChanged(available = true, autoRecoveryEnabled = true, hasServer = true)
        )

        assertTrue(lost.commands.contains(Command.StopTunnelForNetwork))
        assertEquals(listOf(Command.CancelRetry, Command.StartConnection), available.commands)
        assertTrue(duplicate.commands.isEmpty())
    }

    @Test
    fun networkLossWithoutAutoRecovery_stopsService() {
        val active = State(desiredConnected = true, phase = Phase.CONNECTED)

        val result = VpnRecoveryPolicy.reduce(
            active,
            Event.NetworkChanged(available = false, autoRecoveryEnabled = false, hasServer = true)
        )

        assertFalse(result.state.desiredConnected)
        assertTrue(result.commands.contains(Command.StopService))
    }

    @Test
    fun networkHandoff_rebuildsAnIntentionalConnectionWithoutRetrySetting() {
        val active = State(desiredConnected = true, phase = Phase.CONNECTED)

        val result = VpnRecoveryPolicy.reduce(active, Event.NetworkHandoff(hasServer = true))

        assertEquals(Phase.WAITING_FOR_NETWORK, result.state.phase)
        assertEquals(
            listOf(Command.CancelRetry, Command.RebuildTunnelForNetwork),
            result.commands
        )
    }

    @Test
    fun networkHandoff_afterManualDisconnectDoesNothing() {
        val stopped = VpnRecoveryPolicy.reduce(
            State(desiredConnected = true, phase = Phase.CONNECTED),
            Event.ManualDisconnect
        )

        val result = VpnRecoveryPolicy.reduce(stopped.state, Event.NetworkHandoff(hasServer = true))

        assertTrue(result.commands.isEmpty())
        assertFalse(result.state.desiredConnected)
    }

    @Test
    fun stickyRestore_doesNotResumeWhileScreenIsStillOff() {
        val paused = State(
            desiredConnected = true,
            phase = Phase.PAUSED_BY_SCREEN,
            screenPaused = true
        )

        val result = VpnRecoveryPolicy.reduce(
            paused,
            Event.StickyRestore(screenInteractive = false, hasServer = true)
        )

        assertEquals(Phase.PAUSED_BY_SCREEN, result.state.phase)
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun missingServer_isDiagnosedWithoutConnectCommand() {
        val result = VpnRecoveryPolicy.reduce(State(), Event.ManualConnect(hasServer = false))

        assertFalse(result.state.desiredConnected)
        assertTrue(result.commands.single() is Command.Diagnostic)
    }

    @Test
    fun retryDelay_isExponentialAndBounded() {
        assertEquals(1_000L, VpnRecoveryPolicy.retryDelayMs(1))
        assertEquals(2_000L, VpnRecoveryPolicy.retryDelayMs(2))
        assertEquals(30_000L, VpnRecoveryPolicy.retryDelayMs(6))
        assertEquals(30_000L, VpnRecoveryPolicy.retryDelayMs(99))
    }

    @Test
    fun successfulConnection_resetsRetryState() {
        val waiting = State(
            desiredConnected = true,
            phase = Phase.WAITING_FOR_NETWORK,
            retryAttempt = 4
        )

        val result = VpnRecoveryPolicy.reduce(waiting, Event.ConnectSucceeded)

        assertEquals(Phase.CONNECTED, result.state.phase)
        assertEquals(0, result.state.retryAttempt)
        assertEquals(listOf(Command.CancelRetry), result.commands)
    }

    @Test
    fun stickyRestore_afterProcessRestart_startsOnlyOnce() {
        val restored = VpnRecoveryPolicy.reduce(
            State(
                desiredConnected = true,
                phase = Phase.DISCONNECTED,
                networkAvailable = true
            ),
            Event.StickyRestore(screenInteractive = true, hasServer = true)
        )
        val duplicate = VpnRecoveryPolicy.reduce(
            restored.state,
            Event.StickyRestore(screenInteractive = true, hasServer = true)
        )

        assertEquals(listOf(Command.StartConnection), restored.commands)
        assertTrue(restored.state.connectPending)
        assertTrue(duplicate.commands.isEmpty())
    }

    @Test
    fun retryElapsed_doesNotStartWithoutNetwork() {
        val result = VpnRecoveryPolicy.reduce(
            State(
                desiredConnected = true,
                phase = Phase.WAITING_FOR_NETWORK,
                networkAvailable = false,
                retryAttempt = 2
            ),
            Event.RetryElapsed
        )

        assertTrue(result.commands.isEmpty())
        assertFalse(result.state.connectPending)
    }
}
