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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
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

/**
 * Где сейчас кнопка подключения.
 *
 * Частицы должны вылетать из неё, а не из края экрана: событие принадлежит
 * кнопке, и разлёт от неё читается как отклик на нажатие. Значение ставит сам
 * экран, потому что положение кнопки знает только он.
 */
internal object NimboBurstSource {
    var bounds: Rect? by mutableStateOf(null)
    /** Круглая кнопка или полоса: от этого зависит точка старта крупицы. */
    var round: Boolean by mutableStateOf(true)
}

/** Точка на контуре источника в направлении угла. */
private fun sourceStartPoint(bounds: Rect, round: Boolean, angle: Float): Offset {
    val directionX = cos(angle)
    val directionY = sin(angle)
    val halfWidth = bounds.width / 2f
    val halfHeight = bounds.height / 2f
    val distance = if (round) {
        min(halfWidth, halfHeight)
    } else {
        val horizontal = if (abs(directionX) < 0.0001f) Float.MAX_VALUE else halfWidth / abs(directionX)
        val vertical = if (abs(directionY) < 0.0001f) Float.MAX_VALUE else halfHeight / abs(directionY)
        min(horizontal, vertical) + 2f
    }
    return Offset(
        x = bounds.center.x + directionX * distance,
        y = bounds.center.y + directionY * distance
    )
}

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

    // Подключение и отключение принадлежат кнопке — крупицы летят от неё.
    // Пинг и обновление подписки ей не принадлежат: они поднимаются от нижнего
    // края, как на Android.
    val source = when (trigger) {
        NimboBurstTrigger.CONNECTED, NimboBurstTrigger.DISCONNECTED -> NimboBurstSource.bounds
        NimboBurstTrigger.ACTIVITY -> null
    }
    val roundSource = NimboBurstSource.round

    Canvas(modifier = modifier) {
        val baseTile = 3.4.dp.toPx()
        val corner = 0.75.dp.toPx()
        val travel = size.height + baseTile * 4f
        val current = progress.value

        if (source != null && source.width > 1f && source.height > 1f) {
            // Разлёт от кнопки: угол по кругу, радиальный ход и поперечный
            // изгиб — та же геометрия, что в NetworkEdgeBurst на Android.
            repeat(BurstParticleCount) { index ->
                val stagger = (index % 5) * 0.006f
                if (current < stagger) return@repeat
                val local = ((current - stagger) / (1f - stagger)).coerceIn(0f, 1f)
                if (local >= 1f) return@repeat

                val envelope = sin(local * PI).toFloat().coerceIn(0f, 1f)
                val sizeFactor = when (index % 4) {
                    0 -> 1.12f
                    1 -> 0.72f
                    2 -> 0.92f
                    else -> 0.56f
                }
                val tileSize = baseTile * sizeFactor
                val angle = index * (2f * PI.toFloat() / BurstParticleCount) + 0.35f
                val distance = 132.dp.toPx() * (0.74f + (index % 7) * 0.055f)
                val bend = (8.dp + 4.dp * (index % 4)).toPx() * (if (index % 2 == 0) 1f else -1f)

                val start = sourceStartPoint(source, roundSource, angle)
                val directionX = cos(angle)
                val directionY = sin(angle)
                val radial = distance * local
                val tangent = sin(local * PI).toFloat() * bend
                val point = Offset(
                    x = start.x + directionX * radial - directionY * tangent,
                    y = start.y + directionY * radial + directionX * tangent
                )
                val alpha = ((0.24f + envelope * 0.68f) * (1f - local)).coerceIn(0f, 0.88f)
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(point.x - tileSize / 2f, point.y - tileSize / 2f),
                    size = Size(tileSize, tileSize),
                    cornerRadius = CornerRadius(corner, corner)
                )
            }
            return@Canvas
        }

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
