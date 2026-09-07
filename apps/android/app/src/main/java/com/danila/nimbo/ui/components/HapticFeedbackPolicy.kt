package com.danila.nimbo.ui.components

import kotlin.math.floor
import kotlin.math.roundToInt

internal object HapticFeedbackPolicy {
    const val CONTINUOUS_SLIDER_INTERVALS = 20

    fun sliderBucket(
        value: Float,
        rangeStart: Float,
        rangeEnd: Float,
        steps: Int
    ): Int {
        val span = rangeEnd - rangeStart
        if (!span.isFinite() || span <= 0f) return 0

        val progress = ((value - rangeStart) / span).coerceIn(0f, 1f)
        val intervals = if (steps > 0) steps + 1 else CONTINUOUS_SLIDER_INTERVALS
        return if (steps > 0) {
            (progress * intervals).roundToInt()
        } else {
            floor(progress * intervals).toInt()
        }.coerceIn(0, intervals)
    }

    fun shouldEmitSliderTick(previousBucket: Int?, currentBucket: Int): Boolean =
        previousBucket != null && previousBucket != currentBucket
}
