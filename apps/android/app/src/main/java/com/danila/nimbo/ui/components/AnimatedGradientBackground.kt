package com.danila.nimbo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    styleOverride: BackgroundStyleMode? = null
) {
    val nebulaColors = LocalNebulaColors.current
    val styleMode = styleOverride ?: LocalBackgroundStyleMode.current
    val animationEnabled = LocalBackgroundAnimationEnabled.current
    val reducedTransparencyEnabled = LocalReducedTransparencyEnabled.current
    val accent = nebulaColors.accent
    val background = nebulaColors.background
    val glow = nebulaColors.glow
    val gradStart = nebulaColors.primaryGradientStart
    val gradMid = nebulaColors.primaryGradientMiddle
    val gradEnd = nebulaColors.primaryGradientEnd

    // Reduced transparency is an accessibility setting for opacity/blur, not
    // a request to freeze the scene. Keep motion alive at a quieter amplitude.
    val animateBackground = animationEnabled
    // Single phase 0..1 that wraps seamlessly — Restart + LinearEasing means
    // there is no visible jump at the cycle boundary because every animated
    // value below is expressed through sin/cos of phase*2π (or as an offset
    // into a repeating pattern whose period exactly matches phase).
    val phase: Float = if (animateBackground) {
        val transition = rememberInfiniteTransition(label = "bg")
        val animated by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(38000, easing = LinearEasing), RepeatMode.Restart),
            label = "bg-phase"
        )
        animated
    } else {
        0f
    }

    val twoPi = (PI * 2.0).toFloat()
    val t1 = 0.5f + 0.5f * sin(phase * twoPi)
    val t2 = 0.5f + 0.5f * cos(phase * twoPi)
    val patternAlpha = if (reducedTransparencyEnabled) 0.38f else 1f

    val starSpecs = remember {
        val random = java.util.Random(42L)
        List(32) { index ->
            BackgroundStar(
                xFraction = random.nextFloat(),
                yFraction = random.nextFloat(),
                radiusDp = 0.5f + random.nextFloat() * 1.5f,
                phase = index.toFloat()
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Reduced transparency tones down the pattern but does not remove
            // it. The selector should still feel alive when accessibility
            // settings are enabled; only the opacity and glows are reduced.
            // Базовый цвет задаётся палитрой приложения. Выбор анимации ниже
            // добавляет только самостоятельный прозрачный слой и никогда не
            // перекрашивает сам фон.

            when (styleMode) {
                BackgroundStyleMode.MORPHISM -> {
                    // Calm floating circles: each orbit is independent so the
                    // screen never looks like one large blob sliding around.
                    val circles = listOf(
                        Triple(0.16f, 0.18f, 0.34f),
                        Triple(0.82f, 0.27f, 0.28f),
                        Triple(0.30f, 0.60f, 0.42f),
                        Triple(0.78f, 0.78f, 0.38f),
                        Triple(0.12f, 0.88f, 0.24f)
                    )
                    circles.forEachIndexed { index, (x, y, radius) ->
                        val local = phase * twoPi + index * 1.31f
                        val cx = (x + sin(local.toDouble()).toFloat() * 0.035f) * w
                        val cy = (y + cos(local.toDouble()).toFloat() * 0.025f) * h
                        drawRadialBlob(
                            (if (index % 2 == 0) accent else glow).copy(alpha = 0.18f * patternAlpha),
                            cx,
                            cy,
                            w * radius
                        )
                    }
                }

                BackgroundStyleMode.MATERIAL3 -> {
                    // Material You's quiet ring field. The rings breathe and
                    // drift slightly instead of being a static decoration.
                    val rings = listOf(
                        Triple(0.18f, 0.20f, 0.12f),
                        Triple(0.78f, 0.30f, 0.16f),
                        Triple(0.26f, 0.62f, 0.19f),
                        Triple(0.78f, 0.82f, 0.14f)
                    )
                    rings.forEachIndexed { index, (x, y, radius) ->
                        val local = phase * twoPi + index * 0.92f
                        val breathe = 1f + sin(local.toDouble()).toFloat() * 0.09f
                        val center = Offset(
                            (x + cos(local.toDouble()).toFloat() * 0.025f) * w,
                            (y + sin(local.toDouble()).toFloat() * 0.018f) * h
                        )
                        drawCircle(
                            color = accent.copy(alpha = 0.15f * patternAlpha),
                            radius = w * radius * breathe,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                        drawCircle(
                            color = gradMid.copy(alpha = 0.07f * patternAlpha),
                            radius = w * radius * breathe * 1.28f,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                BackgroundStyleMode.NOTHING_DOTS -> {
                    // Dot pattern that slowly drifts. The offset uses phase * step
                    // directly (no mod), so visually 0 == step (positions coincide
                    // because the pattern repeats every `step`), and Restart never
                    // causes a visible jump.
                    val step = 22f
                    val radius = 1.4f
                    val dotColor = accent.copy(alpha = 0.26f * patternAlpha)
                    val driftX = phase * step
                    val driftY = phase * step * 0.5f
                    var y = -step + (driftY % step)
                    while (y < h + step) {
                        var x = -step + (driftX % step)
                        while (x < w + step) {
                            drawCircle(
                                color = dotColor,
                                radius = radius,
                                center = Offset(x, y)
                            )
                            x += step
                        }
                        y += step
                    }
                }

                BackgroundStyleMode.AURORA -> {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                gradStart.copy(alpha = 0.28f),
                                gradMid.copy(alpha = 0.22f),
                                gradEnd.copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            start = Offset(0f, h * t2),
                            end = Offset(w, h * t1)
                        )
                    )
                    drawRadialBlob(accent.copy(alpha = 0.14f), w * 0.15f + w * t1 * 0.3f, h * 0.2f, w * 0.55f)
                    drawRadialBlob(gradMid.copy(alpha = 0.12f), w * 0.75f, h * 0.75f - h * t2 * 0.2f, w * 0.5f)
                }

                BackgroundStyleMode.GRID -> {
                    val grid = 42f
                    val shift = (phase * grid * 1.4f) % grid
                    val gridColor = nebulaColors.onSurface.copy(alpha = 0.075f * patternAlpha)
                    var offset = -h - grid + shift
                    while (offset < w + h + grid) {
                        drawLine(
                            color = gridColor,
                            start = Offset(offset, 0f),
                            end = Offset(offset + h, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        offset += grid
                    }
                    offset = -h - grid + shift * 0.73f
                    while (offset < w + h + grid) {
                        drawLine(
                            color = gridColor.copy(alpha = gridColor.alpha * 0.62f),
                            start = Offset(offset, h),
                            end = Offset(offset + h, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                        offset += grid * 1.55f
                    }
                }

                BackgroundStyleMode.MESH -> {
                    val forms = listOf(
                        Triple(0.18f, 0.20f, 0.14f),
                        Triple(0.80f, 0.26f, 0.17f),
                        Triple(0.27f, 0.68f, 0.21f),
                        Triple(0.82f, 0.80f, 0.15f)
                    )
                    forms.forEachIndexed { index, (x, y, radius) ->
                        val local = phase * twoPi + index * 1.17f
                        val cx = (x + sin(local.toDouble()).toFloat() * 0.025f) * w
                        val cy = (y + cos(local.toDouble()).toFloat() * 0.024f) * h
                        val path = polygonPath(
                            center = Offset(cx, cy),
                            radius = w * radius,
                            sides = 5 + index % 3,
                            rotation = local * 0.10f
                        )
                        drawPath(
                            path = path,
                            color = (if (index % 2 == 0) gradStart else gradEnd).copy(alpha = 0.09f * patternAlpha),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                BackgroundStyleMode.WAVES -> {
                    // Three broad, rounded ribbons are spread across the
                    // screen instead of emerging from one corner.
                    val ribbons = listOf(
                        Triple(0.18f, 0.20f, 0.48f),
                        Triple(0.72f, 0.48f, 0.36f),
                        Triple(0.30f, 0.78f, 0.56f)
                    )
                    ribbons.forEachIndexed { index, (x, y, lengthFraction) ->
                        val local = phase * twoPi + index * 1.4f
                        val center = Offset(
                            (x + sin(local.toDouble()).toFloat() * 0.04f) * w,
                            (y + cos(local.toDouble()).toFloat() * 0.035f) * h
                        )
                        val angle = (-10f - index * 3f) / 180f * PI
                        val length = w * lengthFraction
                        val dx = cos(angle).toFloat() * length / 2f
                        val dy = sin(angle).toFloat() * length / 2f
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.17f * patternAlpha),
                                    gradMid.copy(alpha = 0.10f * patternAlpha),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(center.x - dx, center.y - dy),
                            end = Offset(center.x + dx, center.y + dy),
                            strokeWidth = (7f + index * 1.5f).dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                BackgroundStyleMode.STARFIELD -> {
                    // Sparse snow-like flakes, each on its own slow trajectory.
                    starSpecs.forEachIndexed { index, star ->
                        val sx = (star.xFraction * w + sin((phase * twoPi + index).toDouble()).toFloat() * 14.dp.toPx()) % w
                        val sy = (star.yFraction * h + phase * h * (0.08f + (index % 4) * 0.02f)) % h
                        val flicker = (sin((phase * twoPi * 2f + star.phase).toDouble()) * 0.5 + 0.5).toFloat()
                        val radius = (star.radiusDp + flicker * 0.8f).dp.toPx()
                        val snowColor = nebulaColors.textPrimary.copy(alpha = (0.16f + flicker * 0.20f) * patternAlpha)
                        drawLine(
                            color = snowColor,
                            start = Offset(sx - radius, sy),
                            end = Offset(sx + radius, sy),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = snowColor,
                            start = Offset(sx, sy - radius),
                            end = Offset(sx, sy + radius),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                BackgroundStyleMode.CYBERPUNK,
                BackgroundStyleMode.DEEP_SPACE,
                BackgroundStyleMode.FIRE,
                BackgroundStyleMode.LAVA,
                BackgroundStyleMode.NEON,
                BackgroundStyleMode.NORDIC,
                BackgroundStyleMode.BLOSSOM,
                BackgroundStyleMode.RAIN,
                BackgroundStyleMode.ORBIT -> {
                    // Разные траектории и плотность есть у каждого режима,
                    // а цвета всегда берутся из выбранной пользователем палитры.
                    val effectColors = listOf(accent, gradStart, gradMid, gradEnd, glow)
                    val motionScale = when (styleMode) {
                        BackgroundStyleMode.FIRE, BackgroundStyleMode.LAVA -> 1.35f
                        BackgroundStyleMode.NEON, BackgroundStyleMode.CYBERPUNK -> 1.15f
                        BackgroundStyleMode.DEEP_SPACE -> 0.72f
                        else -> 1f
                    }
                    effectColors.forEachIndexed { index, color ->
                        val local = (phase + index * 0.23f) % 1f
                        val x = w * (0.14f + (index % 2) * 0.72f + sin(local * twoPi) * 0.10f * motionScale)
                        val y = h * (0.15f + (index / 2) * 0.42f + cos(local * twoPi) * 0.08f * motionScale)
                        drawRadialBlob(color.copy(alpha = (0.16f - index * 0.018f) * patternAlpha), x, y, w * (0.52f - index * 0.035f))
                    }
                }

                BackgroundStyleMode.SIGNAL_FLOW -> {
                    // Several independent, sparse signal tracks flow across the
                    // screen. They stay separated instead of forming one dense
                    // staircase, while their starting points shift over time.
                    val tracks = listOf(
                        Triple(0.12f, 0.10f, 0.38f),
                        Triple(0.30f, 0.25f, 0.56f),
                        Triple(0.08f, 0.44f, 0.31f),
                        Triple(0.50f, 0.61f, 0.47f),
                        Triple(0.20f, 0.80f, 0.42f)
                    )
                    tracks.forEachIndexed { index, (x, y, length) ->
                        val travel = ((phase * 0.24f) + index * 0.19f) % 1f
                        val startX = (x + travel * 1.30f - 0.34f) * w
                        val startY = (y + sin((phase * twoPi + index * 0.8f).toDouble()).toFloat() * 0.025f) * h
                        val endX = startX + length * w
                        val endY = startY - length * w * 0.18f
                        val color = if (index % 2 == 0) accent else glow
                        drawLine(
                            color = color.copy(alpha = 0.18f * patternAlpha),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 9.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        val gleam = (travel * 1.18f).coerceIn(0f, 1f)
                        val gx = startX + (endX - startX) * gleam
                        val gy = startY + (endY - startY) * gleam
                        drawCircle(
                            color = Color.White.copy(alpha = 0.26f * patternAlpha),
                            radius = 4.dp.toPx(),
                            center = Offset(gx, gy)
                        )
                    }
                }

                BackgroundStyleMode.NONE -> Unit
            }
        }

        if (LocalElementStyleMode.current == ElementStyleMode.MATERIAL_EXPRESSIVE &&
            styleMode != BackgroundStyleMode.NONE
        ) {
            MaterialYouEmojiOverlay(
                phase = phase,
                animated = animateBackground,
                alphaScale = patternAlpha
            )
        }
    }
}

@Composable
private fun MaterialYouEmojiOverlay(
    phase: Float,
    animated: Boolean,
    alphaScale: Float
) {
    val emojis = remember {
        listOf("✨", "🌐", "⚡", "🛡️", "☁️", "🔒", "🚀", "🔄").shuffled().take(5)
    }
    val positions = remember {
        listOf(
            0.08f to 0.18f,
            0.78f to 0.29f,
            0.16f to 0.56f,
            0.82f to 0.72f,
            0.42f to 0.88f
        )
    }
    val twoPi = (PI * 2.0).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        emojis.forEachIndexed { index, emoji ->
            val (xFraction, yFraction) = positions[index]
            val local = phase * twoPi + index * 0.91f
            val driftX = if (animated) sin(local.toDouble()).toFloat() * 11f else 0f
            val driftY = if (animated) cos(local.toDouble()).toFloat() * 15f else 0f
            Text(
                text = emoji,
                modifier = Modifier
                    .offset(x = maxWidth * xFraction, y = maxHeight * yFraction)
                    .graphicsLayer {
                        alpha = (0.055f + (index % 3) * 0.012f) * alphaScale
                        translationX = driftX.dp.toPx()
                        translationY = driftY.dp.toPx()
                        rotationZ = if (animated) sin(local.toDouble()).toFloat() * 4f else 0f
                    },
                fontSize = (18 + (index % 3) * 4).sp
            )
        }
    }
}

private data class BackgroundStar(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val phase: Float
)

private fun polygonPath(
    center: Offset,
    radius: Float,
    sides: Int,
    rotation: Float
): Path = Path().apply {
    val safeSides = sides.coerceAtLeast(3)
    repeat(safeSides) { index ->
        val angle = rotation + (PI * 2.0 * index / safeSides) - PI / 2.0
        val point = Offset(
            center.x + cos(angle).toFloat() * radius,
            center.y + sin(angle).toFloat() * radius
        )
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun DrawScope.drawRadialBlob(color: Color, cx: Float, cy: Float, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(cx, cy)
    )
}
