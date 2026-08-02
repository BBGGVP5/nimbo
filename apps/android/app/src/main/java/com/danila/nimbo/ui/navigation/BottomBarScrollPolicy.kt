package com.danila.nimbo.ui.navigation

internal object BottomBarScrollPolicy {
    private const val DIRECTION_THRESHOLD_PX = 2.5f

    fun visibleAfterScroll(currentlyVisible: Boolean, availableY: Float): Boolean = when {
        availableY <= -DIRECTION_THRESHOLD_PX -> false
        availableY >= DIRECTION_THRESHOLD_PX -> true
        else -> currentlyVisible
    }
}
