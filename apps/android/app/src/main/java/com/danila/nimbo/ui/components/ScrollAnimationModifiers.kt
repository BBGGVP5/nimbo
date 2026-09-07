package com.danila.nimbo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Оптимизированный модификатор «желе» при достижении границ прокрутки.
 * Анимация применяется ТОЛЬКО к alpha/scale через graphicsLayer
 * (не вызывает перемеривание layout).
 * Реагирует ТОЛЬКО на onPostFling (не на каждый пиксель scroll),
 * что устраняет главную причину FPS-дропов.
 */
fun Modifier.jellyScrollAnimation(): Modifier = composed(
    inspectorInfo = debugInspectorInfo { name = "jellyScrollAnimation" }
) {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private var animationJob: Job? = null
            private var lastTriggerNanos = 0L

            // НЕ перехватываем onPreScroll / onPostScroll — это убирает FPS падение
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val now = System.nanoTime()
                val enoughVelocity = abs(available.y) >= 320f
                val outsideDebounceWindow = now - lastTriggerNanos >= 220_000_000L
                if (enoughVelocity && outsideDebounceWindow) {
                    lastTriggerNanos = now
                    animationJob?.cancel()
                    animationJob = coroutineScope.launch {
                        scale.stop()
                        scale.snapTo(1f)
                        // Один короткий отклик без повторного «дребезга» пружины.
                        scale.animateTo(
                            targetValue = 0.996f,
                            animationSpec = tween(55, easing = FastOutLinearInEasing)
                        )
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(nestedScrollConnection)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/**
 * Упрощённая версия — только bounce при конце быстрого свайпа.
 */
fun Modifier.bounceScrollAnimation(): Modifier = composed(
    inspectorInfo = debugInspectorInfo { name = "bounceScrollAnimation" }
) {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private var animationJob: Job? = null
            private var lastTriggerNanos = 0L

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val now = System.nanoTime()
                val enoughVelocity = abs(available.y) >= 320f
                val outsideDebounceWindow = now - lastTriggerNanos >= 220_000_000L
                if (enoughVelocity && outsideDebounceWindow) {
                    lastTriggerNanos = now
                    animationJob?.cancel()
                    animationJob = coroutineScope.launch {
                        scale.stop()
                        scale.snapTo(1f)
                        scale.animateTo(
                            targetValue = 0.997f,
                            animationSpec = tween(55, easing = FastOutLinearInEasing)
                        )
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(nestedScrollConnection)
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

