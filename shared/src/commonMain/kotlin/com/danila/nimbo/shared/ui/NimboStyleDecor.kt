package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * Точечная сетка стиля Dotted — перенесена из `ui/components/StyleDecor.kt`
 * приложения, чтобы обе платформы рисовали одинаково.
 */
fun Modifier.nimboDotPattern(
    color: Color,
    spacing: Dp = 12.dp,
    radius: Dp = 1.dp,
    alpha: Float = 0.18f
): Modifier = this.drawBehind {
    val step = spacing.toPx().coerceAtLeast(6f)
    val dotRadius = radius.toPx().coerceAtLeast(0.6f)
    val dotColor = color.copy(alpha = alpha)

    var y = step * 0.5f
    while (y < size.height) {
        var x = step * 0.5f
        while (x < size.width) {
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

/**
 * Контур из отдельных точек: короткий штрих с круглым завершением читается как
 * ряд светодиодов, а не как обычная пунктирная рамка.
 */
fun Modifier.nimboDottedOutline(
    color: Color,
    cornerRadius: Dp = 14.dp,
    thickness: Dp = 1.dp,
    dotLength: Dp = 1.2.dp,
    gap: Dp = 3.6.dp,
    alpha: Float = 1f
): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = thickness.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dotLength.toPx(), gap.toPx()),
            0f
        )
    )
    val inset = thickness.toPx() / 2f
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}

/** Страница в клетку — подложка стиля Manga. */
@androidx.compose.runtime.Composable
fun NimboMangaBackdrop() {
    // Волокна считаются один раз: случайность с фиксированным зерном, иначе
    // крапины плясали бы на каждом кадре.
    val grain = androidx.compose.runtime.remember {
        val random = kotlin.random.Random(20260901)
        List(1200) { Offset(random.nextFloat(), random.nextFloat()) }
    }
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(NimboMangaPalette.PaperDeep)
    ) {
        val step = 26.dp.toPx()
        val line = NimboMangaPalette.Ink.copy(alpha = 0.025f)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }

        val speckSize = androidx.compose.ui.geometry.Size(1.2f.dp.toPx(), 1.2f.dp.toPx())
        val speck = NimboMangaPalette.Ink.copy(alpha = 0.045f)
        grain.forEach { point ->
            drawRect(
                color = speck,
                topLeft = Offset(point.x * size.width, point.y * size.height),
                size = speckSize
            )
        }

        // Лист лежит неровно: к краям он уходит в тень.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.30f)
                ),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = kotlin.math.max(size.width, size.height) * 0.78f
            )
        )
    }
}
