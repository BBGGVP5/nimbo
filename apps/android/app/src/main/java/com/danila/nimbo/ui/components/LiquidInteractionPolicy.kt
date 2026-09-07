package com.danila.nimbo.ui.components

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

internal data class LiquidPressTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val pivotX: Float,
    val pivotY: Float
) {
    companion object {
        val Identity = LiquidPressTransform(
            scaleX = 1f,
            scaleY = 1f,
            translationX = 0f,
            translationY = 0f,
            pivotX = 0.5f,
            pivotY = 0.5f
        )
    }
}

internal object LiquidInteractionPolicy {

    fun pressTransform(
        pressed: Boolean,
        width: Float,
        height: Float,
        touchX: Float,
        touchY: Float,
        intensity: Float
    ): LiquidPressTransform {
        if (!pressed || width <= 0f || height <= 0f || intensity <= 0f) {
            return LiquidPressTransform.Identity
        }

        val safeIntensity = intensity.coerceIn(0f, 1.25f)
        val rawX = (touchX / width - 0.5f) * 2f
        val rawY = (touchY / height - 0.5f) * 2f
        // A saturating curve behaves like gel under tension: it follows the finger
        // outside the surface but progressively resists additional deformation.
        fun elasticPull(value: Float): Float =
            (tanh((value * 0.78f).toDouble()).toFloat() * 1.55f)

        val normalizedX = elasticPull(rawX)
        val normalizedY = elasticPull(rawY)
        val absoluteX = abs(normalizedX)
        val absoluteY = abs(normalizedY)
        val horizontalDominance = (absoluteX - absoluteY).coerceAtLeast(0f)
        val verticalDominance = (absoluteY - absoluteX).coerceAtLeast(0f)
        val baseExpansion = 0.014f * safeIntensity
        val directionalExpansion = 0.055f * safeIntensity
        val perpendicularSqueeze = 0.010f * safeIntensity

        return LiquidPressTransform(
            scaleX = 1f + baseExpansion +
                absoluteX * directionalExpansion -
                verticalDominance * perpendicularSqueeze,
            scaleY = 1f + baseExpansion +
                absoluteY * directionalExpansion -
                horizontalDominance * perpendicularSqueeze,
            translationX = normalizedX * 8.5f * safeIntensity,
            translationY = normalizedY * 7.5f * safeIntensity,
            pivotX = (0.5f - normalizedX * 0.17f * safeIntensity).coerceIn(0.16f, 0.84f),
            pivotY = (0.5f - normalizedY * 0.17f * safeIntensity).coerceIn(0.16f, 0.84f)
        )
    }

    fun shouldCancelForScroll(
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float
    ): Boolean {
        if (touchSlop <= 0f) return abs(deltaY) > abs(deltaX)
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (distanceSquared <= touchSlop * touchSlop) return false
        return abs(deltaY) > abs(deltaX)
    }

    fun continuousTabIndex(x: Float, width: Float, itemCount: Int): Float {
        if (width <= 0f || itemCount <= 1) return 0f
        val itemWidth = width / itemCount
        return (x / itemWidth - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())
    }

    fun nearestTabIndex(continuousIndex: Float, itemCount: Int): Int {
        if (itemCount <= 1) return 0
        return continuousIndex.roundToInt().coerceIn(0, itemCount - 1)
    }

    fun bubbleStretch(continuousIndex: Float): Float =
        (abs(continuousIndex - continuousIndex.roundToInt()) * 2f).coerceIn(0f, 1f)

    fun landingTargetIndex(
        continuousIndex: Float,
        @Suppress("UNUSED_PARAMETER") velocityIndexPerSecond: Float,
        itemCount: Int
    ): Int = nearestTabIndex(continuousIndex, itemCount)

    fun landingVelocity(velocityPxPerSecond: Float, itemWidthPx: Float): Float {
        if (itemWidthPx <= 0f || !velocityPxPerSecond.isFinite()) return 0f
        return (velocityPxPerSecond / itemWidthPx).coerceIn(-3.2f, 3.2f)
    }

    fun landingImpact(velocityIndexPerSecond: Float, remainingDistance: Float): Float {
        val velocityContribution = (abs(velocityIndexPerSecond) / 3.2f).coerceIn(0f, 1f) * 0.27f
        val distanceContribution = remainingDistance.coerceIn(0f, 1f) * 0.18f
        return (0.55f + velocityContribution + distanceContribution).coerceIn(0.55f, 1f)
    }

    fun landingDelayMillis(remainingDistance: Float): Long =
        (70f + remainingDistance.coerceIn(0f, 1f) * 120f).toLong()
}
