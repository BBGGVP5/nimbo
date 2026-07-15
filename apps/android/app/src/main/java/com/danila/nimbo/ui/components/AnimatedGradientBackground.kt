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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedGradientBackground() {
    val nebulaColors = LocalNebulaColors.current
    val styleMode = LocalBackgroundStyleMode.current
    val animationEnabled = LocalBackgroundAnimationEnabled.current
    val reducedTransparencyEnabled = LocalReducedTransparencyEnabled.current
    val accent = nebulaColors.accent
    val background = nebulaColors.background
    val glow = nebulaColors.glow
    val gradStart = nebulaColors.primaryGradientStart
    val gradMid = nebulaColors.primaryGradientMiddle
    val gradEnd = nebulaColors.primaryGradientEnd

    val animateBackground = animationEnabled && !reducedTransparencyEnabled
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

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (reducedTransparencyEnabled) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            background,
                            accent.copy(alpha = 0.08f),
                            background
                        )
                    )
                )
                return@Canvas
            }

            when (styleMode) {
                BackgroundStyleMode.MORPHISM -> {
                    drawRadialBlob(accent.copy(alpha = 0.22f), t1 * w, t2 * h * 0.55f, w * 0.75f)
                    drawRadialBlob(glow.copy(alpha = 0.18f), t2 * w, t1 * h * 0.45f, w * 0.9f)
                    drawRadialBlob(gradEnd.copy(alpha = 0.16f), t1 * w * 0.8f, t1 * h * 0.2f, w * 0.6f)
                }

                BackgroundStyleMode.MATERIAL3 -> {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                gradStart.copy(alpha = 0.16f),
                                gradMid.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    drawRadialBlob(accent.copy(alpha = 0.12f), w * 0.85f, h * 0.2f, w * 0.45f)
                }

                BackgroundStyleMode.NOTHING_DOTS -> {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                background,
                                gradEnd.copy(alpha = 0.08f),
                                background
                            )
                        )
                    )
                    // Dot pattern that slowly drifts. The offset uses phase * step
                    // directly (no mod), so visually 0 == step (positions coincide
                    // because the pattern repeats every `step`), and Restart never
                    // causes a visible jump.
                    val step = 22f
                    val radius = 1.4f
                    val dotColor = accent.copy(alpha = 0.26f)
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
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                background,
                                gradMid.copy(alpha = 0.1f),
                                background
                            )
                        )
                    )
                    val grid = 34f
                    val shiftX = (phase * grid) % grid
                    val shiftY = (phase * grid * 0.5f) % grid
                    var x = -grid + shiftX
                    while (x < w + grid) {
                        drawLine(
                            color = nebulaColors.onSurface.copy(alpha = 0.08f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        x += grid
                    }
                    var y = -grid + shiftY
                    while (y < h + grid) {
                        drawLine(
                            color = nebulaColors.onSurface.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        y += grid
                    }
                    drawRadialBlob(accent.copy(alpha = 0.1f), w * 0.5f, h * 0.4f, w * 0.5f)
                }

                BackgroundStyleMode.MESH -> {
                    drawRadialBlob(accent.copy(alpha = 0.2f), t1 * w, t2 * h, w * 0.8f)
                    drawRadialBlob(gradStart.copy(alpha = 0.15f), (1 - t2) * w, t1 * h, w * 0.7f)
                    drawRadialBlob(gradMid.copy(alpha = 0.18f), t2 * w * 0.5f, (1 - t1) * h * 0.8f, w * 0.9f)
                    drawRadialBlob(gradEnd.copy(alpha = 0.12f), (1 - t1) * w * 0.3f, t2 * h * 1.2f, w * 0.6f)
                }

                BackgroundStyleMode.WAVES -> {
                    val waveCount = 4
                    val baseAlpha = 0.08f
                    for (i in 0 until waveCount) {
                        val local = (phase + i.toFloat() / waveCount) % 1.0f
                        val waveY = (0.5f + 0.45f * sin(local * twoPi)) * h
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, accent.copy(alpha = baseAlpha - i * 0.01f), Color.Transparent)
                            ),
                            start = Offset(0f, waveY),
                            end = Offset(w, waveY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    drawRadialBlob(accent.copy(alpha = 0.15f), w * 0.5f, h * 0.3f, w * 0.6f)
                }

                BackgroundStyleMode.STARFIELD -> {
                    starSpecs.forEach { star ->
                        val sx = star.xFraction * w
                        val sy = (star.yFraction * h + phase * h * 0.2f) % h
                        val flicker = (sin((phase * twoPi * 3f + star.phase).toDouble()) * 0.5 + 0.5).toFloat()
                        drawCircle(
                            color = accent.copy(alpha = 0.15f + flicker * 0.25f),
                            radius = star.radiusDp.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                    drawRadialBlob(accent.copy(alpha = 0.1f), w * 0.8f, h * 0.2f, w * 0.4f)
                }

                BackgroundStyleMode.CYBERPUNK,
                BackgroundStyleMode.DEEP_SPACE,
                BackgroundStyleMode.FIRE,
                BackgroundStyleMode.LAVA,
                BackgroundStyleMode.NEON,
                BackgroundStyleMode.NORDIC,
                BackgroundStyleMode.BLOSSOM -> {
                    val preset = when (styleMode) {
                        BackgroundStyleMode.CYBERPUNK -> listOf(Color(0xFF00F0FF), Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF22FFAA))
                        BackgroundStyleMode.DEEP_SPACE -> listOf(Color(0xFF3432A8), Color(0xFF7C5DFA), Color(0xFFEAF3FF), Color(0xFF1B2356))
                        BackgroundStyleMode.FIRE -> listOf(Color(0xFFFF3D00), Color(0xFFFFA000), Color(0xFFFFD166), Color(0xFFB31312))
                        BackgroundStyleMode.LAVA -> listOf(Color(0xFFFF2E2E), Color(0xFFFF7A00), Color(0xFF7C1FFF), Color(0xFFFFC857))
                        BackgroundStyleMode.NEON -> listOf(Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF00D2FF), Color(0xFF9BFF6A))
                        BackgroundStyleMode.NORDIC -> listOf(Color(0xFF8FFFE8), Color(0xFF89C2FF), Color(0xFF2C4A72), Color(0xFFE7F8FF))
                        BackgroundStyleMode.BLOSSOM -> listOf(Color(0xFFFF9BC4), Color(0xFFFFC29B), Color(0xFFC7A8FF), Color(0xFFFFE3EF))
                        else -> listOf(accent, gradMid, gradEnd)
                    }
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                preset.first().copy(alpha = 0.16f),
                                background,
                                preset.last().copy(alpha = 0.10f)
                            )
                        )
                    )
                    preset.forEachIndexed { index, color ->
                        val local = (phase + index * 0.23f) % 1f
                        val x = w * (0.14f + (index % 2) * 0.72f + sin(local * twoPi) * 0.10f)
                        val y = h * (0.15f + (index / 2) * 0.62f + cos(local * twoPi) * 0.08f)
                        drawRadialBlob(color.copy(alpha = 0.18f - index * 0.02f), x, y, w * (0.58f - index * 0.04f))
                    }
                }
            }
        }
    }
}

private data class BackgroundStar(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val phase: Float
)

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
