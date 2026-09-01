package com.danila.nimbo.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dotPatternOverlay(
    color: Color,
    spacing: Dp = 12.dp,
    radius: Dp = 1.dp,
    alpha: Float = 0.18f
): Modifier = this.drawBehind {
    val step = spacing.toPx().coerceAtLeast(6f)
    val r = radius.toPx().coerceAtLeast(0.6f)
    val dotColor = color.copy(alpha = alpha)

    var y = step * 0.5f
    while (y < size.height) {
        var x = step * 0.5f
        while (x < size.width) {
            drawCircle(color = dotColor, radius = r, center = androidx.compose.ui.geometry.Offset(x, y))
            x += step
        }
        y += step
    }
}

/**
 * Rounded dotted perimeter used by the Dotted style.  A round capped short dash makes
 * the contour read as individual LEDs rather than a conventional dashed border.
 */
fun Modifier.dottedOutline(
    color: Color,
    cornerRadius: Dp = 14.dp,
    thickness: Dp = 1.dp,
    dotLength: Dp = 1.2.dp,
    gap: Dp = 3.6.dp,
    alpha: Float = 0.78f
): Modifier = this.drawBehind {
    val strokeWidth = thickness.toPx().coerceAtLeast(0.75f)
    val inset = strokeWidth / 2f
    val width = (size.width - strokeWidth).coerceAtLeast(0f)
    val height = (size.height - strokeWidth).coerceAtLeast(0f)
    val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    drawRoundRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = Offset(inset, inset),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dotLength.toPx().coerceAtLeast(0.8f), gap.toPx().coerceAtLeast(2f)),
                phase = 0f
            )
        )
    )
}

