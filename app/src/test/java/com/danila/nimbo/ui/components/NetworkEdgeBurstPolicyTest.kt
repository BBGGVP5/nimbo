package com.danila.nimbo.ui.components

import androidx.compose.ui.geometry.Offset
import com.danila.nimbo.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class NetworkEdgeBurstPolicyTest {

    @Test
    fun initialSnapshotIsSilent() {
        assertNull(
            NetworkEdgeBurstPolicy.trigger(
                previous = null,
                current = EdgeBurstSnapshot(VpnState.DISCONNECTED, isPinging = false, isRefreshing = false)
            )
        )
    }

    @Test
    fun vpnTransitionsUseSemanticTriggers() {
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, isPinging = false, isRefreshing = false)
        val connecting = idle.copy(vpnState = VpnState.CONNECTING)
        val connected = idle.copy(vpnState = VpnState.CONNECTED)

        assertEquals(EdgeBurstTrigger.CONNECTING, NetworkEdgeBurstPolicy.trigger(idle, connecting))
        assertEquals(EdgeBurstTrigger.CONNECTED, NetworkEdgeBurstPolicy.trigger(connecting, connected))
        assertEquals(EdgeBurstTrigger.DISCONNECTED, NetworkEdgeBurstPolicy.trigger(connected, idle))
    }

    @Test
    fun pingStartsOnlyOnRisingEdge() {
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, isPinging = false, isRefreshing = false)
        val pinging = idle.copy(isPinging = true)

        assertEquals(EdgeBurstTrigger.PING, NetworkEdgeBurstPolicy.trigger(idle, pinging))
        assertNull(NetworkEdgeBurstPolicy.trigger(pinging, pinging))
        assertNull(NetworkEdgeBurstPolicy.trigger(pinging, idle))
    }

    @Test
    fun refreshStartsOnlyOnRisingEdge() {
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, isPinging = false, isRefreshing = false)
        val refreshing = idle.copy(isRefreshing = true)

        assertEquals(EdgeBurstTrigger.REFRESH, NetworkEdgeBurstPolicy.trigger(idle, refreshing))
        assertNull(NetworkEdgeBurstPolicy.trigger(refreshing, refreshing))
        assertNull(NetworkEdgeBurstPolicy.trigger(refreshing, idle))
    }

    @Test
    fun vpnTransitionHasPriorityWhenEventsStartTogether() {
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, isPinging = false, isRefreshing = false)
        val connectedAndPinging = EdgeBurstSnapshot(VpnState.CONNECTED, isPinging = true, isRefreshing = false)

        assertEquals(
            EdgeBurstTrigger.CONNECTED,
            NetworkEdgeBurstPolicy.trigger(idle, connectedAndPinging)
        )
    }

    @Test
    fun connectedResultReusesConnectionButtonOrigin() {
        val controller = NetworkEdgeBurstController(clockMillis = { 1_000L })
        val origin = Offset(120f, 640f)
        val source = EdgeBurstSource(origin, 80f, 80f, EdgeBurstSourceShape.CIRCLE, 1.5f)

        controller.observe(EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false))
        controller.emit(EdgeBurstTrigger.CONNECTING, source)
        controller.observe(EdgeBurstSnapshot(VpnState.CONNECTING, false, false))
        controller.observe(EdgeBurstSnapshot(VpnState.CONNECTED, false, false))

        assertEquals(EdgeBurstTrigger.CONNECTED, controller.event?.trigger)
        assertEquals(origin, controller.event?.source?.center)
    }

    @Test
    fun directPingIsNotDuplicatedByImmediateStateEcho() {
        var now = 1_000L
        val controller = NetworkEdgeBurstController(clockMillis = { now })
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false)

        controller.observe(idle)
        controller.emit(
            EdgeBurstTrigger.PING,
            EdgeBurstSource(Offset(300f, 80f), 24f, 24f, EdgeBurstSourceShape.ROUNDED_RECT)
        )
        val directId = controller.event?.id
        now += 50L
        controller.observe(idle.copy(isPinging = true))

        assertEquals(directId, controller.event?.id)
    }

    @Test
    fun staleDirectEventDoesNotSuppressLaterBackgroundPing() {
        var now = 1_000L
        val controller = NetworkEdgeBurstController(clockMillis = { now })
        val idle = EdgeBurstSnapshot(VpnState.DISCONNECTED, false, false)

        controller.observe(idle)
        controller.emit(
            EdgeBurstTrigger.PING,
            EdgeBurstSource(Offset(300f, 80f), 24f, 24f, EdgeBurstSourceShape.ROUNDED_RECT)
        )
        val directId = controller.event?.id
        now += 1_000L
        controller.observe(idle.copy(isPinging = true))

        assertEquals((directId ?: 0L) + 1L, controller.event?.id)
        assertNull(controller.event?.source)
    }

    @Test
    fun particleSpecIsSlowAndDense() {
        assertTrue(NetworkEdgeBurstVisualSpec.durationMillis >= 1_800)
        assertTrue(NetworkEdgeBurstVisualSpec.particleCount >= 26)
        assertTrue(NetworkEdgeBurstVisualSpec.trailCount >= 3)
    }

    @Test
    fun circularSourceStartsOnButtonPerimeter() {
        val source = EdgeBurstSource(
            center = Offset(100f, 200f),
            halfWidth = 80f,
            halfHeight = 80f,
            shape = EdgeBurstSourceShape.CIRCLE,
            densityMultiplier = 1.5f
        )

        assertEquals(Offset(180f, 200f), NetworkEdgeBurstGeometry.startPoint(source, angleRadians = 0f))
    }

    @Test
    fun roundedSourceStartsOutsideButtonBounds() {
        val source = EdgeBurstSource(
            center = Offset(100f, 200f),
            halfWidth = 24f,
            halfHeight = 24f,
            shape = EdgeBurstSourceShape.ROUNDED_RECT
        )

        val start = NetworkEdgeBurstGeometry.startPoint(source, angleRadians = 0f)
        assertTrue(start.x > source.center.x + source.halfWidth)
    }

    @Test
    fun largeConnectionSourceUsesMoreParticles() {
        assertTrue(
            NetworkEdgeBurstGeometry.particleCount(1.5f) >
                NetworkEdgeBurstVisualSpec.particleCount
        )
    }

    @Test
    fun radialParticleMovesAwayFromSourceImmediately() {
        val source = EdgeBurstSource(
            center = Offset(100f, 200f),
            halfWidth = 80f,
            halfHeight = 80f,
            shape = EdgeBurstSourceShape.CIRCLE
        )
        val start = NetworkEdgeBurstGeometry.startPoint(source, angleRadians = 0f)
        val point = NetworkEdgeBurstGeometry.scatterPoint(
            source = source,
            angleRadians = 0f,
            distance = 100f,
            progress = 0.1f,
            bend = 0f
        )

        assertTrue(point.x > start.x)
        assertEquals(start.y, point.y, 0.001f)
    }

    @Test
    fun oppositeRadialParticlesDivergeInsteadOfConverging() {
        val source = EdgeBurstSource(
            center = Offset(100f, 200f),
            halfWidth = 40f,
            halfHeight = 40f,
            shape = EdgeBurstSourceShape.CIRCLE
        )
        val right = NetworkEdgeBurstGeometry.scatterPoint(source, 0f, 100f, 0.7f, 0f)
        val left = NetworkEdgeBurstGeometry.scatterPoint(source, PI.toFloat(), 100f, 0.7f, 0f)

        assertTrue(right.x > source.center.x)
        assertTrue(left.x < source.center.x)
    }
}
