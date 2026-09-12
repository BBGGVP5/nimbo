package com.danila.nimbo.ui.navigation

import kotlin.math.abs

internal object BottomBarScrollPolicy {
    private const val DIRECTION_THRESHOLD_PX = 2.5f

    fun visibleAfterScroll(currentlyVisible: Boolean, availableY: Float): Boolean = when {
        availableY <= -DIRECTION_THRESHOLD_PX -> false
        availableY >= DIRECTION_THRESHOLD_PX -> true
        else -> currentlyVisible
    }
}

/**
 * Собирает небольшие scroll-delta одного направления, прежде чем менять состояние панели.
 * Это не даёт панели мигать при дрожании пальца и при коротком обратном delta от overscroll.
 */
internal class BottomBarScrollTracker(
    private val activationDistancePx: Float = 22f
) {
    private var accumulatedY = 0f
    private var direction = 0

    fun visibleAfterScroll(currentlyVisible: Boolean, availableY: Float): Boolean {
        if (availableY == 0f) return currentlyVisible

        val newDirection = if (availableY > 0f) 1 else -1
        if (direction != 0 && direction != newDirection) {
            accumulatedY = 0f
        }
        direction = newDirection
        accumulatedY += availableY

        if (abs(accumulatedY) < activationDistancePx) return currentlyVisible

        val result = accumulatedY > 0f
        accumulatedY = 0f
        return result
    }

    fun reset() {
        accumulatedY = 0f
        direction = 0
    }
}
