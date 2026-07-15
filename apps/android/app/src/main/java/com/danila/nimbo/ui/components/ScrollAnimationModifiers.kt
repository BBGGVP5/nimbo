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
import kotlinx.coroutines.launch

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
            // НЕ перехватываем onPreScroll / onPostScroll — это убирает FPS падение
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y != 0f) {
                    coroutineScope.launch {
                        // Лёгкое сжатие
                        scale.animateTo(
                            targetValue = 0.985f,
                            animationSpec = tween(80, easing = FastOutLinearInEasing)
                        )
                        // Отпружинивание
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
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
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y != 0f) {
                    coroutineScope.launch {
                        scale.animateTo(
                            targetValue = 0.99f,
                            animationSpec = tween(80, easing = FastOutLinearInEasing)
                        )
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
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
