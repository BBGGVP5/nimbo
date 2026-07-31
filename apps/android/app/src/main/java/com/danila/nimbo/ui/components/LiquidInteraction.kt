package com.danila.nimbo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled

fun Modifier.liquidTouchDeformation(
    depth: LiquidGlassDepth = LiquidGlassDepth.CONTROL,
    interactive: Boolean = true
): Modifier = composed {
    val motionEnabled = LocalBackgroundAnimationEnabled.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    val interactionsEnabled = interactive && motionEnabled && !reducedTransparency
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(interactionsEnabled) {
        if (!interactionsEnabled) pressed = false
    }

    val pressProgress by animateFloatAsState(
        targetValue = if (pressed && interactionsEnabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid_press_progress"
    )
    val trackedTouchX by animateFloatAsState(
        targetValue = touchPosition.x,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_touch_x"
    )
    val trackedTouchY by animateFloatAsState(
        targetValue = touchPosition.y,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_touch_y"
    )
    val depthIntensity = when (depth) {
        LiquidGlassDepth.CONTROL -> 1f
        LiquidGlassDepth.FLOATING -> 0.78f
        LiquidGlassDepth.PANEL -> 0.52f
    }
    val transform = LiquidInteractionPolicy.pressTransform(
        pressed = pressProgress > 0.001f,
        width = measuredSize.width.toFloat(),
        height = measuredSize.height.toFloat(),
        touchX = trackedTouchX,
        touchY = trackedTouchY,
        intensity = depthIntensity * pressProgress
    )

    this
        .onSizeChanged { size ->
            measuredSize = size
            if (touchPosition == Offset.Zero && size.width > 0 && size.height > 0) {
                touchPosition = Offset(size.width / 2f, size.height / 2f)
            }
        }
        .pointerInput(interactionsEnabled) {
            if (!interactionsEnabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                val pointerId = down.id
                val start = down.position
                touchPosition = start
                pressed = true
                var gestureClassified = false
                var cancelledForScroll = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    touchPosition = change.position
                    val delta = change.position - start
                    if (!gestureClassified && delta.getDistance() > viewConfiguration.touchSlop) {
                        gestureClassified = true
                        cancelledForScroll = LiquidInteractionPolicy.shouldCancelForScroll(
                            deltaX = delta.x,
                            deltaY = delta.y,
                            touchSlop = viewConfiguration.touchSlop
                        )
                    }
                    if (cancelledForScroll) {
                        // A vertical scroll must stay completely free. Horizontal travel
                        // remains attached to the glass and keeps pulling it until release.
                        pressed = false
                    }
                }
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = transform.scaleX
            scaleY = transform.scaleY
            translationX = with(density) { transform.translationX.dp.toPx() }
            translationY = with(density) { transform.translationY.dp.toPx() }
            transformOrigin = TransformOrigin(transform.pivotX, transform.pivotY)
        }
}
