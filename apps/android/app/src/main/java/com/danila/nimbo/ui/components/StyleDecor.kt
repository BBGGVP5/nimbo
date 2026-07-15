package com.danila.nimbo.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dotPatternOverlay(
    color: Color,
    spacing: Dp = 12.dp,
    radius: Dp = 1.dp,
    alpha: Float = 0.18f
): Modifier = this.drawWithContent {
    drawContent()
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
