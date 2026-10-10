package com.danila.nimbo.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Frame values are read by layers and Canvas, without re-composing the home page. */
class NimboConnectionMotion internal constructor(
    val scale: State<Float>,
    val iconScale: State<Float>,
    val cloudProgress: State<Float>,
    internal val cycle: State<Float>,
    internal val confirmation: State<Float>,
    internal val busy: Boolean
)

@Composable
fun rememberNimboConnectionMotion(
    connected: Boolean,
    busy: Boolean,
    pressed: Boolean,
    enabled: Boolean = true
): NimboConnectionMotion {
    val cloud = animateFloatAsState(
        targetValue = if (connected && !busy) 1f else 0f,
        animationSpec = if (enabled) tween(420, easing = FastOutSlowInEasing) else snap(),
        label = "connection-cloud-transform"
    )
    val scale = animateFloatAsState(
        targetValue = if (!enabled) 1f else if (pressed) .91f else if (busy) .975f else 1f,
        animationSpec = if (!enabled) snap() else if (pressed) tween(85, easing = FastOutSlowInEasing)
            else spring(dampingRatio = .62f, stiffness = Spring.StiffnessMedium),
        label = "connection-press"
    )
    val icon = remember { Animatable(1f) }
    val confirmation = remember { Animatable(1f) }
    val cycle = remember { Animatable(.25f) }
    val previousConnected = remember { booleanArrayOf(connected) }
    LaunchedEffect(connected, busy, enabled) {
        val justConnected = connected && !previousConnected[0] && !busy
        previousConnected[0] = connected
        icon.snapTo(1f)
        confirmation.snapTo(1f)
        if (justConnected && enabled && coroutineContext[MotionDurationScale]?.scaleFactor != 0f) {
            icon.snapTo(.8f)
            confirmation.snapTo(0f)
            coroutineScope {
                launch { icon.animateTo(1f, spring(dampingRatio = .6f, stiffness = Spring.StiffnessMedium)) }
                confirmation.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            }
        }
    }
    LaunchedEffect(busy, enabled) {
        cycle.snapTo(.25f)
        if (busy && enabled) {
            // Respect the system's animation scale; do not spin a zero-duration loop.
            while (isActive && coroutineContext[MotionDurationScale]?.scaleFactor != 0f) {
                cycle.snapTo(0f)
                cycle.animateTo(1f, tween(1600, easing = LinearEasing))
                // Some system animation overrides make animateTo complete
                // immediately. Keep the loop cooperative even in that case.
                kotlinx.coroutines.delay(16)
            }
        }
    }
    return remember(scale, busy) {
        NimboConnectionMotion(scale, icon.asState(), cloud, cycle.asState(), confirmation.asState(), busy)
    }
}

/** Decoration only: the icon and primary label continue to describe the actual VPN state. */
@Composable
fun NimboConnectionHalo(motion: NimboConnectionMotion, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val stroke = 2.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        if (motion.busy) {
            drawArc(color.copy(alpha = .72f), startAngle = motion.cycle.value * 360f - 90f,
                sweepAngle = 82f, useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val progress = motion.confirmation.value
        if (progress < 1f) {
            drawCircle(color.copy(alpha = (1f - progress) * .45f),
                radius = radius * (.93f + .07f * progress), style = Stroke(stroke))
        }
    }
}
