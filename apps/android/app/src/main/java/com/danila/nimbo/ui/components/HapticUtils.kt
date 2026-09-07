package com.danila.nimbo.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Indication

@Composable
fun rememberPreferenceAwareHapticFeedback(
    enabled: Boolean,
    strength: HapticStrength,
    style: HapticStyle
): HapticFeedback {
    val context = LocalContext.current.applicationContext
    val platformHaptic = LocalHapticFeedback.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    return remember(platformHaptic, vibrator, enabled, strength, style) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (!enabled) return

                val pattern = if (hapticFeedbackType == HapticFeedbackType.LongPress) {
                    HapticPatternPolicy.confirmation(strength, style)
                } else {
                    HapticPatternPolicy.tick(strength, style)
                }
                val customHapticSucceeded = vibrate(vibrator, pattern)

                if (!customHapticSucceeded) {
                    platformHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
}

fun performConnectionSuccessHaptic(
    context: Context,
    enabled: Boolean,
    strength: Int,
    style: Int
) {
    if (!enabled) return
    val pattern = HapticPatternPolicy.confirmation(
        strength = HapticStrength.fromPersistedValue(strength),
        style = HapticStyle.fromPersistedValue(style)
    )
    vibrate(resolveVibrator(context.applicationContext), pattern)
}

private fun resolveVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun vibrate(vibrator: Vibrator?, pattern: HapticPattern): Boolean = runCatching {
    val target = vibrator ?: return@runCatching false
    if (!target.hasVibrator()) return@runCatching false
    val amplitudes = if (target.hasAmplitudeControl()) {
        pattern.amplitudes
    } else {
        IntArray(pattern.amplitudes.size) { index ->
            if (pattern.amplitudes[index] == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
        }
    }
    target.vibrate(VibrationEffect.createWaveform(pattern.timings, amplitudes, -1))
    true
}.getOrDefault(false)

@Composable
fun rememberHapticSliderValueChange(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
): (Float) -> Unit {
    val haptic = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val initialBucket = HapticFeedbackPolicy.sliderBucket(
        value = value,
        rangeStart = valueRange.start,
        rangeEnd = valueRange.endInclusive,
        steps = steps
    )
    var previousBucket by remember(valueRange.start, valueRange.endInclusive, steps) {
        mutableIntStateOf(initialBucket)
    }

    // Keep programmatic value changes in sync without treating them as user gestures.
    SideEffect {
        previousBucket = initialBucket
    }

    return remember(haptic, valueRange.start, valueRange.endInclusive, steps) {
        { newValue ->
            val currentBucket = HapticFeedbackPolicy.sliderBucket(
                value = newValue,
                rangeStart = valueRange.start,
                rangeEnd = valueRange.endInclusive,
                steps = steps
            )
            if (HapticFeedbackPolicy.shouldEmitSliderTick(previousBucket, currentBucket)) {
                haptic.tick()
            }
            previousBucket = currentBucket
            currentOnValueChange(newValue)
        }
    }
}

@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, onClick) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
}

@Composable
fun rememberHapticToggle(onToggle: (Boolean) -> Unit): (Boolean) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, onToggle) {
        { value: Boolean ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle(value)
        }
    }
}

fun HapticFeedback.tick() {
    performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

fun HapticFeedback.confirm() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    feedbackType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    clickable(
        enabled = enabled,
        interactionSource = source,
        indication = indication,
        onClick = {
            haptic.performHapticFeedback(feedbackType)
            onClick()
        }
    )
}
