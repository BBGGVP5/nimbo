package com.danila.nimbo.shared.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val LocalNimboEdgeBackEnabled = staticCompositionLocalOf { false }
internal val LocalNimboBackActions = staticCompositionLocalOf<NimboBackActions?> { null }

/** Same actions as visible back buttons, including unsaved-edit confirmation. */
internal class NimboBackActions {
    private val actions = mutableListOf<Pair<Any, () -> Unit>>()
    fun add(owner: Any, action: () -> Unit) { actions.add(owner to action) }
    fun remove(owner: Any) { actions.removeAll { it.first === owner } }
    fun back(fallback: () -> Unit) { (actions.lastOrNull()?.second ?: fallback)() }
}

@Composable
internal fun NimboRegisterBack(onBack: () -> Unit) {
    val actions = LocalNimboBackActions.current ?: return
    val latest by rememberUpdatedState(onBack)
    DisposableEffect(actions) {
        val owner = Any()
        actions.add(owner) { latest() }
        onDispose { actions.remove(owner) }
    }
}

/** Edge-only: vertical scrolling, cancelled/multi-touch and short drags do not navigate. */
internal fun nimboBackSwipeCommits(x: Float, y: Float, threshold: Float): Boolean =
    x >= threshold && abs(x) > abs(y) * 1.5f

internal fun Modifier.nimboEdgeBack(onBack: () -> Unit): Modifier = composed {
    val enabled = LocalNimboEdgeBackEnabled.current
    val latest by rememberUpdatedState(onBack)
    if (!enabled) this else pointerInput(Unit) {
        val edge = 28.dp.toPx()
        val distance = 80.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > edge) return@awaitEachGesture
            var captured = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size != 1) break
                val change = event.changes.first()
                if (change.id != down.id || change.isConsumed) break
                val delta = change.position - down.position
                if (!captured && abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) >= abs(delta.x)) break
                if (!captured && delta.x < -viewConfiguration.touchSlop) break
                if (delta.x > viewConfiguration.touchSlop && delta.x > abs(delta.y) * 1.5f) captured = true
                if (captured) change.consume()
                if (!change.pressed) {
                    if (captured && nimboBackSwipeCommits(delta.x, delta.y, distance)) latest()
                    break
                }
            }
        }
    }
}
