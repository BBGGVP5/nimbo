package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NimboHomeMonitoringTest {
    @Test fun staleSamplesNeverShowWithoutAnEstablishedConnection() {
        for (vpnState in listOf("idle", "disconnected", "preparing", "connecting", "disconnecting", "error")) {
            val state = NimboUiState(vpnState = vpnState, memoryMb = 120, memorySamples = listOf(110, 120))
            assertFalse(state.showHomeSpeedMonitor, vpnState)
            assertFalse(state.showHomeMemoryMonitor, vpnState)
        }
    }

    @Test fun connectedWidgetsRespectTheirOwnSettings() {
        val state = NimboUiState(vpnState = "connected", memoryMb = 120)
        assertTrue(state.showHomeSpeedMonitor)
        assertTrue(state.showHomeMemoryMonitor)
        assertFalse(state.copy(showSpeedWidget = false).showHomeSpeedMonitor)
        assertTrue(state.copy(showSpeedWidget = false).showHomeMemoryMonitor)
        assertFalse(state.copy(showMemoryWidget = false).showHomeMemoryMonitor)
        assertTrue(state.copy(showMemoryWidget = false).showHomeSpeedMonitor)
    }

    @Test fun memoryOnlyMonitoringWaitsForARealMeasurement() {
        val state = NimboUiState(vpnState = "connected", showSpeedWidget = false)
        assertFalse(state.showHomeSpeedMonitor)
        assertFalse(state.showHomeMemoryMonitor)
        assertFalse(state.copy(memoryMb = -1).showHomeMemoryMonitor)
        assertTrue(state.copy(memoryMb = 1).showHomeMemoryMonitor)
    }
}
