package com.danila.nimbo.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Частицы событий — те же, что на Android.
 *
 * Квадратные крупицы поднимаются от нижнего края вверх: зелёные при
 * подключении, красные при отключении, акцентные при пинге и обновлении
 * подписки. Событие видно боковым зрением, и не нужно читать подпись, чтобы
 * понять, что произошло.
 */
enum class NimboBurstTrigger(val wireName: String) {
    CONNECTED("connected"),
    DISCONNECTED("disconnected"),
    ACTIVITY("activity");

    companion object {
        fun fromWireName(value: String?): NimboBurstTrigger =
            entries.firstOrNull { it.wireName == value } ?: ACTIVITY
    }
}

/** Длительность и плотность повторяют NetworkEdgeBurstVisualSpec на Android. */
private const val BurstDurationMillis = 1_900
private const val BurstParticleCount = 28

@Composable
internal fun NimboEdgeBurstOverlay(
    eventId: Long,
    trigger: NimboBurstTrigger,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    var handledEvent by remember { mutableStateOf(0L) }

    LaunchedEffect(eventId, enabled) {
        if (eventId == 0L || !enabled || handledEvent == eventId) {
            handledEvent = eventId
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        handledEvent = eventId
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(BurstDurationMillis, easing = LinearOutSlowInEasing)
        )
    }

    if (!enabled || progress.value >= 1f) return

    val color = when (trigger) {
        NimboBurstTrigger.CONNECTED -> Color(0xFF4ADE80)
        NimboBurstTrigger.DISCONNECTED -> Color(0xFFFF6B6B)
        NimboBurstTrigger.ACTIVITY -> NimboPalette.Accent
    }

    Canvas(modifier = modifier) {
        val baseTile = 3.4.dp.toPx()
        val corner = 0.75.dp.toPx()
        val travel = size.height + baseTile * 4f
        val current = progress.value

        repeat(BurstParticleCount) { index ->
            // Крупицы уходят вверх не строем: у каждой своя задержка.
            val stagger = index * 0.017f
            if (current < stagger) return@repeat
            val local = ((current - stagger) / (1f - stagger)).coerceIn(0f, 1f)
            if (local >= 1f) return@repeat

            val envelope = sin(local * PI).toFloat().coerceIn(0f, 1f)
            val lane = index % 3
            val sizeFactor = when (index % 4) {
                0 -> 1.12f
                1 -> 0.72f
                2 -> 0.92f
                else -> 0.56f
            }
            val tileSize = baseTile * sizeFactor
            val sideInset = (2.5.dp + 4.5.dp * lane).toPx()
            val drift = sin((local * 3.5f + index * 0.37f) * PI).toFloat() * 1.4.dp.toPx()
            val y = size.height + baseTile * 2f - local * travel
            val alpha = ((0.16f + envelope * 0.72f) * (1f - local * 0.16f)).coerceIn(0f, 0.88f)
            val tint = color.copy(alpha = alpha)

            // По обеим сторонам экрана: одна колонка выглядела бы полосой
            // помех, а не событием.
            drawRoundRect(
                color = tint,
                topLeft = Offset(sideInset + drift, y),
                size = Size(tileSize, tileSize),
                cornerRadius = CornerRadius(corner, corner)
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width - sideInset - tileSize - drift, y),
                size = Size(tileSize, tileSize),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
    }
}
