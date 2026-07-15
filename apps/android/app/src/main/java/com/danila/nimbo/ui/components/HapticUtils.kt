package com.danila.nimbo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Indication

@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(onClick) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
}

@Composable
fun rememberHapticToggle(onToggle: (Boolean) -> Unit): (Boolean) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(onToggle) {
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
