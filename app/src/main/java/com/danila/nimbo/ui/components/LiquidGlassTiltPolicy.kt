package com.danila.nimbo.ui.components

import androidx.compose.runtime.Immutable

@Immutable
data class LiquidGlassTilt(
    val x: Float,
    val y: Float
) {
    companion object {
        val Zero = LiquidGlassTilt(0f, 0f)
    }
}

internal object LiquidGlassTiltPolicy {
    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    private const val ACTIVE_GRAVITY_RANGE = 6f

    fun fromGravity(
        gravityX: Float,
        gravityY: Float,
        displayRotation: Int
    ): LiquidGlassTilt {
        val (screenX, screenY) = when (displayRotation) {
            ROTATION_90 -> -gravityY to -gravityX
            ROTATION_180 -> -gravityX to gravityY
            ROTATION_270 -> gravityY to gravityX
            else -> gravityX to -gravityY
        }
        return LiquidGlassTilt(
            x = (screenX / ACTIVE_GRAVITY_RANGE).coerceIn(-1f, 1f),
            y = (screenY / ACTIVE_GRAVITY_RANGE).coerceIn(-1f, 1f)
        )
    }
}
