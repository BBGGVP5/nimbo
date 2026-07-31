package com.danila.nimbo.ui.components

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal data class LiquidGlassRimSample(
    val fraction: Float,
    val alpha: Float,
    val whiteMix: Float,
    val spectrumMix: Float
)

internal object LiquidGlassRimPolicy {

    fun samples(
        tiltX: Float,
        tiltY: Float,
        isDark: Boolean,
        effectStrength: Float,
        steps: Int = 64
    ): List<LiquidGlassRimSample> {
        require(steps >= 8)
        // Bias the light toward the upper-right at rest. Without this stable
        // vector, atan2 around (0, 0) can rotate the whole rim from tiny sensor noise.
        val lightVectorX = 0.35f + tiltX.coerceIn(-1f, 1f)
        val lightVectorY = -0.35f + tiltY.coerceIn(-1f, 1f)
        val lightAngle = atan2(lightVectorY, lightVectorX)
        val safeStrength = effectStrength.coerceIn(0f, 1f)
        val baseAlpha = if (isDark) 0.052f else 0.16f

        return (0..steps).map { index ->
            val fraction = index.toFloat() / steps
            val angle = (fraction * PI * 2.0).toFloat()
            val delta = shortestAngle(angle - lightAngle)
            val whitePeak = cos(delta.toDouble()).coerceAtLeast(0.0).pow(5.0).toFloat()
            val oppositeGlow = cos((delta - PI).toDouble())
                .coerceAtLeast(0.0)
                .pow(3.0)
                .toFloat()
            val spectrum = ((sin((angle * 2f - lightAngle * 0.45f).toDouble()) + 1.0) / 2.0)
                .toFloat()

            LiquidGlassRimSample(
                fraction = fraction,
                alpha = (
                    baseAlpha +
                        safeStrength * (0.030f + whitePeak * 0.17f + oppositeGlow * 0.045f)
                    ).coerceIn(0f, 0.88f),
                whiteMix = (0.14f + whitePeak * 0.86f).coerceIn(0f, 1f),
                spectrumMix = spectrum.coerceIn(0f, 1f)
            )
        }
    }

    private fun shortestAngle(value: Float): Float {
        val fullTurn = (PI * 2.0).toFloat()
        var result = value % fullTurn
        if (result > PI) result -= fullTurn
        if (result < -PI) result += fullTurn
        return result
    }
}
