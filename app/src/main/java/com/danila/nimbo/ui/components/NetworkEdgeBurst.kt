package com.danila.nimbo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.vpn.VpnState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class EdgeBurstTrigger {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    PING,
    REFRESH
}

internal object NetworkEdgeBurstVisualSpec {
    const val durationMillis = 1_900
    const val particleCount = 28
    const val trailCount = 3
}

internal enum class EdgeBurstSourceShape {
    CIRCLE,
    ROUNDED_RECT
}

@Immutable
internal data class EdgeBurstSource(
    val center: Offset,
    val halfWidth: Float,
    val halfHeight: Float,
    val shape: EdgeBurstSourceShape,
    val densityMultiplier: Float = 1f,
    val outsetPx: Float = 0f
)

internal object NetworkEdgeBurstGeometry {
    fun startPoint(source: EdgeBurstSource, angleRadians: Float): Offset {
        val directionX = cos(angleRadians)
        val directionY = sin(angleRadians)
        val distance = when (source.shape) {
            EdgeBurstSourceShape.CIRCLE -> min(source.halfWidth, source.halfHeight) + source.outsetPx
            EdgeBurstSourceShape.ROUNDED_RECT -> {
                val horizontal = if (abs(directionX) < 0.0001f) Float.MAX_VALUE
                else source.halfWidth / abs(directionX)
                val vertical = if (abs(directionY) < 0.0001f) Float.MAX_VALUE
                else source.halfHeight / abs(directionY)
                min(horizontal, vertical) + 2f + source.outsetPx
            }
        }
        return Offset(
            x = source.center.x + directionX * distance,
            y = source.center.y + directionY * distance
        )
    }

    fun particleCount(densityMultiplier: Float): Int =
        (NetworkEdgeBurstVisualSpec.particleCount * densityMultiplier)
            .roundToInt()
            .coerceIn(NetworkEdgeBurstVisualSpec.particleCount, 56)

    fun scatterPoint(
        source: EdgeBurstSource,
        angleRadians: Float,
        distance: Float,
        progress: Float,
        bend: Float
    ): Offset {
        val resolvedProgress = progress.coerceIn(0f, 1f)
        val start = startPoint(source, angleRadians)
        val directionX = cos(angleRadians)
        val directionY = sin(angleRadians)
        val radialTravel = distance * resolvedProgress
        val tangentTravel = sin(resolvedProgress * PI).toFloat() * bend
        return Offset(
            x = start.x + directionX * radialTravel - directionY * tangentTravel,
            y = start.y + directionY * radialTravel + directionX * tangentTravel
        )
    }
}

@Immutable
internal data class EdgeBurstSnapshot(
    val vpnState: VpnState,
    val isPinging: Boolean,
    val isRefreshing: Boolean
)

@Immutable
internal data class EdgeBurstEvent(
    val id: Long,
    val trigger: EdgeBurstTrigger,
    val source: EdgeBurstSource?
)

internal object NetworkEdgeBurstPolicy {
    fun trigger(previous: EdgeBurstSnapshot?, current: EdgeBurstSnapshot): EdgeBurstTrigger? {
        if (previous == null) return null

        if (previous.vpnState != current.vpnState) {
            return when (current.vpnState) {
                VpnState.CONNECTING -> EdgeBurstTrigger.CONNECTING
                VpnState.CONNECTED -> EdgeBurstTrigger.CONNECTED
                VpnState.DISCONNECTED -> EdgeBurstTrigger.DISCONNECTED
            }
        }
        if (!previous.isPinging && current.isPinging) return EdgeBurstTrigger.PING
        if (!previous.isRefreshing && current.isRefreshing) return EdgeBurstTrigger.REFRESH
        return null
    }
}

@Stable
internal class NetworkEdgeBurstController(
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    var event by mutableStateOf<EdgeBurstEvent?>(null)
        private set

    private var previous: EdgeBurstSnapshot? = null
    private var sequence = 0L
    private var connectionSource: EdgeBurstSource? = null
    private var lastDirectTrigger: EdgeBurstTrigger? = null
    private var lastDirectAt = Long.MIN_VALUE

    fun emit(trigger: EdgeBurstTrigger, source: EdgeBurstSource?) {
        if (trigger == EdgeBurstTrigger.CONNECTING && source != null) {
            connectionSource = source
        }
        lastDirectTrigger = trigger
        lastDirectAt = clockMillis()
        publish(trigger, source)
        if (trigger == EdgeBurstTrigger.DISCONNECTED) connectionSource = null
    }

    fun observe(snapshot: EdgeBurstSnapshot) {
        val trigger = NetworkEdgeBurstPolicy.trigger(previous, snapshot)
        previous = snapshot
        if (trigger == null) return

        val elapsedSinceDirect = clockMillis() - lastDirectAt
        val isImmediateEcho = trigger == lastDirectTrigger && elapsedSinceDirect in 0L..450L
        if (isImmediateEcho) return

        val source = when (trigger) {
            EdgeBurstTrigger.CONNECTING,
            EdgeBurstTrigger.CONNECTED,
            EdgeBurstTrigger.DISCONNECTED -> connectionSource
            EdgeBurstTrigger.PING,
            EdgeBurstTrigger.REFRESH -> null
        }
        publish(trigger, source)
        if (trigger == EdgeBurstTrigger.DISCONNECTED) connectionSource = null
    }

    private fun publish(trigger: EdgeBurstTrigger, source: EdgeBurstSource?) {
        sequence += 1L
        event = EdgeBurstEvent(id = sequence, trigger = trigger, source = source)
    }
}

internal val LocalNetworkEdgeBurstEmitter = staticCompositionLocalOf<(EdgeBurstTrigger, EdgeBurstSource?) -> Unit> {
    { _, _ -> }
}

@Composable
internal fun rememberNetworkEdgeBurstController(snapshot: EdgeBurstSnapshot): NetworkEdgeBurstController {
    val controller = remember { NetworkEdgeBurstController() }
    LaunchedEffect(snapshot) {
        controller.observe(snapshot)
    }
    return controller
}

/**
 * A cheap, one-shot status accent. Small pixel tiles climb both display edges
 * once; there is no continuous traffic animation and the overlay never handles input.
 */
@Composable
internal fun NetworkEdgeBurstOverlay(
    event: EdgeBurstEvent?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    var handledEventId by remember { mutableLongStateOf(Long.MIN_VALUE) }

    LaunchedEffect(event?.id, enabled) {
        val currentEvent = event
        if (currentEvent == null) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!enabled) {
            handledEventId = currentEvent.id
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (handledEventId == currentEvent.id) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        handledEventId = currentEvent.id
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = NetworkEdgeBurstVisualSpec.durationMillis,
                easing = LinearOutSlowInEasing
            )
        )
    }

    val activeEvent = event
    if (!enabled || activeEvent == null || progress.value >= 1f) return
    val colors = LocalNebulaColors.current
    val eventColor = when (activeEvent.trigger) {
        EdgeBurstTrigger.CONNECTED -> colors.statusConnected
        EdgeBurstTrigger.DISCONNECTED -> colors.statusError
        EdgeBurstTrigger.CONNECTING,
        EdgeBurstTrigger.PING,
        EdgeBurstTrigger.REFRESH -> colors.accent
    }

    Canvas(modifier = modifier) {
        val baseTile = 3.4.dp.toPx()
        val corner = 0.75.dp.toPx()
        val travel = size.height + baseTile * 4f
        val current = progress.value
        val source = activeEvent.source
        val tileCount = NetworkEdgeBurstGeometry.particleCount(source?.densityMultiplier ?: 1f)

        repeat(tileCount) { index ->
            val stagger = if (source != null) (index % 5) * 0.006f else index * 0.017f
            if (current < stagger) return@repeat

            val local = ((current - stagger) / (1f - stagger)).coerceIn(0f, 1f)
            if (local >= 1f) return@repeat

            val envelope = sin(local * PI).toFloat().coerceIn(0f, 1f)
            val lane = index % 3
            val sizeFactor = when (index % 4) {
                0 -> 1.12f
                1 -> 0.72f
                2 -> 0.92f
                else -> 0.56f
            }
            val tileSize = baseTile * sizeFactor
            val sideInset = (2.5.dp + 4.5.dp * lane).toPx()
            val drift = sin((local * 3.5f + index * 0.37f) * PI).toFloat() * 1.4.dp.toPx()
            val y = size.height + baseTile * 2f - local * travel
            val alpha = if (source != null) {
                (0.24f + envelope * 0.68f) * (1f - local)
            } else {
                (0.16f + envelope * 0.72f) * (1f - local * 0.16f)
            }
            val color = eventColor.copy(alpha = alpha.coerceIn(0f, 0.88f))

            if (source != null) {
                val angle = index * (2f * PI.toFloat() / tileCount) + 0.35f
                val baseDistance = when {
                    source.densityMultiplier >= 1.45f -> 176.dp
                    source.densityMultiplier >= 1.15f -> 142.dp
                    else -> 112.dp
                }.toPx()
                val particleDistance = baseDistance * (0.74f + (index % 7) * 0.055f)
                val bendDirection = if (index % 2 == 0) 1f else -1f
                val bend = (8.dp + 4.dp * (index % 4)).toPx() * bendDirection
                val point = NetworkEdgeBurstGeometry.scatterPoint(
                    source = source,
                    angleRadians = angle,
                    distance = particleDistance,
                    progress = local,
                    bend = bend
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(point.x - tileSize / 2f, point.y - tileSize / 2f),
                    size = Size(tileSize, tileSize),
                    cornerRadius = CornerRadius(corner, corner)
                )
                repeat(NetworkEdgeBurstVisualSpec.trailCount) trailLoop@ { trailIndex ->
                    val step = trailIndex + 1
                    val trailProgress = (local - 0.032f * step).coerceAtLeast(0f)
                    if (trailProgress <= 0f) return@trailLoop
                    val trailScale = 1f / (1f + step * 0.42f)
                    val trailSize = tileSize * trailScale
                    val trailAlpha = alpha * (0.34f / step)
                    val trailPoint = NetworkEdgeBurstGeometry.scatterPoint(
                        source = source,
                        angleRadians = angle,
                        distance = particleDistance,
                        progress = trailProgress,
                        bend = bend
                    )
                    drawRoundRect(
                        color = eventColor.copy(alpha = trailAlpha.coerceIn(0f, 0.3f)),
                        topLeft = Offset(trailPoint.x - trailSize / 2f, trailPoint.y - trailSize / 2f),
                        size = Size(trailSize, trailSize),
                        cornerRadius = CornerRadius(corner * 0.7f, corner * 0.7f)
                    )
                }
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(sideInset + drift, y),
                    size = Size(tileSize, tileSize),
                    cornerRadius = CornerRadius(corner, corner)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width - sideInset - tileSize - drift, y),
                    size = Size(tileSize, tileSize),
                    cornerRadius = CornerRadius(corner, corner)
                )

                repeat(NetworkEdgeBurstVisualSpec.trailCount) { trailIndex ->
                    val step = trailIndex + 1
                    val trailScale = 1f / (1f + step * 0.42f)
                    val trailSize = tileSize * trailScale
                    val trailY = y + tileSize * (1.3f + step * 1.05f)
                    val trailAlpha = alpha * (0.34f / step)
                    val trailColor = eventColor.copy(alpha = trailAlpha.coerceIn(0f, 0.3f))
                    drawRoundRect(
                        color = trailColor,
                        topLeft = Offset(sideInset + drift, trailY),
                        size = Size(trailSize, trailSize),
                        cornerRadius = CornerRadius(corner * 0.7f, corner * 0.7f)
                    )
                    drawRoundRect(
                        color = trailColor,
                        topLeft = Offset(size.width - sideInset - trailSize - drift, trailY),
                        size = Size(trailSize, trailSize),
                        cornerRadius = CornerRadius(corner * 0.7f, corner * 0.7f)
                    )
                }
            }
        }
    }
}
