package com.danila.nimbo.shared.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Фон разделён на два независимых слоя:
 *  - [BackgroundPaletteMode] отвечает ТОЛЬКО за цвет (палитра пятен и подложки);
 *  - [BackgroundStyleMode] отвечает ТОЛЬКО за форму движения (что именно рисуется).
 *
 * Благодаря этому любой эффект можно покрасить в любую палитру, а смена палитры
 * не ломает выбранную анимацию.
 */

/**
 * Форма фоновой анимации. Отвечает ТОЛЬКО за движение/геометрию —
 * цвет задаётся отдельно через [BackgroundPaletteMode].
 */
enum class BackgroundStyleMode {
    MORPHISM,
    MATERIAL3,
    NOTHING_DOTS,
    AURORA,
    GRID,
    MESH,
    WAVES,
    STARFIELD,
    CYBERPUNK,
    DEEP_SPACE,
    FIRE,
    LAVA,
    NEON,
    NORDIC,
    BLOSSOM,
    NONE,
    RAIN,
    ORBIT,
    /**
     * Calm, separated signal capsules. Kept last so persisted background
     * indices selected in older builds never change their meaning.
     */
    SIGNAL_FLOW
}

/**
 * Палитра фона. Отвечает ТОЛЬКО за цвет — не влияет на то, какой эффект
 * рисуется. `THEME` наследует акцент активной темы.
 */
enum class BackgroundPaletteMode {
    THEME,
    AURORA,
    CYBER,
    SPACE,
    FIRE,
    LAVA,
    NEON,
    NORDIC,
    BLOSSOM,
    OCEAN,
    SUNSET,
    FOREST
}

/**
 * Индексы сохраняются как есть; неизвестное значение падает в первый вариант,
 * чтобы фон никогда не оставался без движения или без цвета. Порядок совпадает
 * с андроидным (`Theme.kt`), поэтому настройки читаются одинаково.
 */
fun backgroundStyleModeForIndex(index: Int): BackgroundStyleMode = when (index) {
    1 -> BackgroundStyleMode.MATERIAL3
    2 -> BackgroundStyleMode.NOTHING_DOTS
    3 -> BackgroundStyleMode.AURORA
    4 -> BackgroundStyleMode.GRID
    5 -> BackgroundStyleMode.MESH
    6 -> BackgroundStyleMode.WAVES
    7 -> BackgroundStyleMode.STARFIELD
    8 -> BackgroundStyleMode.CYBERPUNK
    9 -> BackgroundStyleMode.DEEP_SPACE
    10 -> BackgroundStyleMode.FIRE
    11 -> BackgroundStyleMode.LAVA
    12 -> BackgroundStyleMode.NEON
    13 -> BackgroundStyleMode.NORDIC
    14 -> BackgroundStyleMode.BLOSSOM
    15 -> BackgroundStyleMode.NONE
    16 -> BackgroundStyleMode.RAIN
    17 -> BackgroundStyleMode.ORBIT
    18 -> BackgroundStyleMode.SIGNAL_FLOW
    else -> BackgroundStyleMode.MORPHISM
}

fun backgroundPaletteModeForIndex(index: Int): BackgroundPaletteMode = when (index) {
    1 -> BackgroundPaletteMode.AURORA
    2 -> BackgroundPaletteMode.CYBER
    3 -> BackgroundPaletteMode.SPACE
    4 -> BackgroundPaletteMode.FIRE
    5 -> BackgroundPaletteMode.LAVA
    6 -> BackgroundPaletteMode.NEON
    7 -> BackgroundPaletteMode.NORDIC
    8 -> BackgroundPaletteMode.BLOSSOM
    9 -> BackgroundPaletteMode.OCEAN
    10 -> BackgroundPaletteMode.SUNSET
    11 -> BackgroundPaletteMode.FOREST
    else -> BackgroundPaletteMode.THEME
}

private const val TWO_PI = (PI * 2.0).toFloat()

/** Детерминированный псевдослучайный 0..1 — одинаковый на каждом кадре. */
private fun rnd(index: Int, salt: Int): Float {
    val x = sin(index * 12.9898f + salt * 78.233f) * 43758.547f
    return x - floor(x)
}

private fun wrap01(value: Float): Float {
    val v = value % 1f
    return if (v < 0f) v + 1f else v
}

/**
 * Палитра фона. Возвращает минимум 3 цвета, отсортированных от «главного» к
 * дополнительным. [accent] используется, когда выбран режим «Как тема».
 */
fun backgroundPaletteColors(
    mode: BackgroundPaletteMode,
    accent: Color,
    isLight: Boolean
): List<Color> {
    val palette = when (mode) {
        BackgroundPaletteMode.THEME -> listOf(
            accent,
            lerp(accent, Color(0xFF00D2FF), 0.42f),
            lerp(accent, Color.White, 0.34f),
            lerp(accent, Color(0xFF7C5DFA), 0.35f)
        )
        BackgroundPaletteMode.AURORA -> listOf(
            Color(0xFF6BE88E), Color(0xFF63B3FF), Color(0xFF7C5DFA), Color(0xFF9BFFD8)
        )
        BackgroundPaletteMode.CYBER -> listOf(
            Color(0xFF00F0FF), Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF22FFAA)
        )
        BackgroundPaletteMode.SPACE -> listOf(
            Color(0xFF5B5BE0), Color(0xFF7C5DFA), Color(0xFFEAF3FF), Color(0xFF2A2F7A)
        )
        BackgroundPaletteMode.FIRE -> listOf(
            Color(0xFFFF6B00), Color(0xFFFFA000), Color(0xFFFFD166), Color(0xFFFF3D00)
        )
        BackgroundPaletteMode.LAVA -> listOf(
            Color(0xFFFF2E2E), Color(0xFFFF7A00), Color(0xFF7C1FFF), Color(0xFFFFC857)
        )
        BackgroundPaletteMode.NEON -> listOf(
            Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF00D2FF), Color(0xFF9BFF6A)
        )
        BackgroundPaletteMode.NORDIC -> listOf(
            Color(0xFF8FFFE8), Color(0xFF89C2FF), Color(0xFFE7F8FF), Color(0xFF5D8FD6)
        )
        BackgroundPaletteMode.BLOSSOM -> listOf(
            Color(0xFFFF9BC4), Color(0xFFFFC29B), Color(0xFFC7A8FF), Color(0xFFFFE3EF)
        )
        BackgroundPaletteMode.OCEAN -> listOf(
            Color(0xFF00C2A8), Color(0xFF2E8BFF), Color(0xFF7FE3FF), Color(0xFF0B5FA5)
        )
        BackgroundPaletteMode.SUNSET -> listOf(
            Color(0xFFFF8A5B), Color(0xFFFF5F87), Color(0xFF9B5DE5), Color(0xFFFFC46B)
        )
        BackgroundPaletteMode.FOREST -> listOf(
            Color(0xFF4CC98A), Color(0xFF9ED75B), Color(0xFF2F8F6B), Color(0xFFD9F5A8)
        )
    }
    // В светлой теме чистые неоновые тона выжигают текст — слегка приглушаем.
    return if (isLight) palette.map { lerp(it, Color(0xFF2A2E3A), 0.18f) } else palette
}

/**
 * Рисует выбранный эффект.
 *
 * @param phase монотонно растущее время в «оборотах» эффекта.
 * @param intensity множитель непрозрачности — маленькой превью-плитке нужно больше.
 * @param detail доля частиц/линий. Плитки настроек рисуются десятками штук
 *   одновременно, поэтому им хватает облегчённой версии.
 */
fun DrawScope.drawNimboBackgroundMotion(
    mode: BackgroundStyleMode,
    phase: Float,
    colors: List<Color>,
    isLight: Boolean,
    intensity: Float = 1f,
    detail: Float = 1f
) {
    if (mode == BackgroundStyleMode.NONE || colors.isEmpty()) return

    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val minDim = size.minDimension
    val a = (if (isLight) 0.15f else 0.24f) * intensity
    fun c(i: Int): Color = colors[((i % colors.size) + colors.size) % colors.size]
    fun count(full: Int, min: Int = 4): Int = maxOf(min, (full * detail).toInt())

    when (mode) {
        // ---------- Круги: медленно плавающие мягкие пятна ----------
        BackgroundStyleMode.MORPHISM -> {
            repeat(5) { i ->
                val local = phase + i * 0.19f
                val radius = w * (0.30f + 0.24f * rnd(i, 5))
                val center = Offset(
                    x = w * (0.12f + 0.76f * rnd(i, 1)) + w * 0.14f * sin((local + rnd(i, 2)) * TWO_PI),
                    y = h * (0.10f + 0.80f * rnd(i, 3)) + h * 0.09f * cos((local + rnd(i, 4)) * TWO_PI)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c(i).copy(alpha = a * 0.95f),
                            c(i).copy(alpha = a * 0.28f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }

        // ---------- Material You: мягкие тональные формы ----------
        BackgroundStyleMode.MATERIAL3 -> {
            // Material You feels warmer when its motion is based on broad tonal
            // surfaces, not on an always-expanding target-like set of circles.
            // The three shapes drift independently and stay far behind content.
            repeat(3) { i ->
                val local = phase * (0.32f + i * 0.06f) + i * 0.27f
                val width = w * (0.56f - i * 0.07f)
                val height = minDim * (0.30f + i * 0.05f)
                val center = Offset(
                    x = w * (0.15f + i * 0.34f) + w * 0.09f * sin(local * TWO_PI),
                    y = h * (0.18f + i * 0.28f) + h * 0.07f * cos(local * TWO_PI)
                )
                withTransform({
                    rotate(
                        degrees = -18f + i * 13f + sin(local * TWO_PI) * 7f,
                        pivot = center
                    )
                }) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c(i).copy(alpha = a * 0.95f),
                                c(i + 1).copy(alpha = a * 0.28f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = width * 0.72f
                        ),
                        topLeft = Offset(center.x - width * 0.5f, center.y - height * 0.5f),
                        size = Size(width, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * 0.50f)
                    )
                }
            }
        }

        // ---------- Dotted: спокойная световая матрица ----------
        BackgroundStyleMode.NOTHING_DOTS -> {
            val cols = count(13, min = 7)
            val rows = count(24, min = 10)
            val dx = w / cols
            val dy = h / rows
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val scanner = wrap01(phase * 0.20f) * (cols + rows)
                    val distance = kotlin.math.abs(col + row * 0.78f - scanner)
                    val scanGlow = (1f - distance / 3.6f).coerceIn(0f, 1f)
                    val breath = 0.5f + 0.5f * sin(phase * TWO_PI * 0.42f + col * 0.42f + row * 0.28f)
                    drawCircle(
                        color = c(col + row).copy(alpha = a * (0.16f + breath * 0.20f + scanGlow * 0.76f)),
                        radius = dx * (0.043f + scanGlow * 0.040f),
                        center = Offset(dx * (col + 0.5f), dy * (row + 0.5f))
                    )
                }
            }
        }

        // ---------- Аврора: полярные ленты ----------
        BackgroundStyleMode.AURORA -> {
            repeat(3) { band ->
                val yBase = h * (0.18f + band * 0.20f)
                val amp = h * (0.10f - band * 0.015f)
                val path = Path()
                path.moveTo(0f, yBase)
                var x = 0f
                val stepX = w / 28f
                while (x <= w) {
                    val k = x / w
                    val y = yBase +
                        sin((k * 2.1f + phase + band * 0.27f) * TWO_PI) * amp +
                        cos((k * 3.6f - phase * 1.35f) * TWO_PI) * amp * 0.38f
                    path.lineTo(x, y)
                    x += stepX
                }
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(c(band).copy(alpha = a * 1.05f), Color.Transparent),
                        startY = yBase - amp,
                        endY = yBase + h * 0.34f
                    )
                )
            }
        }

        // ---------- Сетка: диагональные линии ----------
        BackgroundStyleMode.GRID -> {
            val step = minDim / maxOf(4f, 8f * detail)
            val shift = phase * step
            val stroke = minDim * 0.0035f
            var d = -h
            while (d < w + h) {
                drawLine(
                    color = c(0).copy(alpha = a * 0.55f),
                    start = Offset(d + shift, 0f),
                    end = Offset(d + shift + h, h),
                    strokeWidth = stroke
                )
                drawLine(
                    color = c(1).copy(alpha = a * 0.40f),
                    start = Offset(d - shift, 0f),
                    end = Offset(d - shift - h, h),
                    strokeWidth = stroke
                )
                d += step
            }
        }

        // ---------- Фигуры: вращающиеся многоугольники ----------
        BackgroundStyleMode.MESH -> {
            repeat(5) { i ->
                val sides = 3 + (i % 4)
                val cx = w * (0.15f + 0.70f * rnd(i, 11)) + w * 0.05f * sin((phase + rnd(i, 15)) * TWO_PI)
                val cy = h * (0.12f + 0.74f * rnd(i, 12)) + h * 0.04f * cos((phase + rnd(i, 16)) * TWO_PI)
                val r = minDim * (0.12f + 0.11f * rnd(i, 13))
                val rot = (phase + rnd(i, 14)) * TWO_PI * (if (i % 2 == 0) 1f else -1f)
                val path = Path()
                for (k in 0 until sides) {
                    val ang = rot + k * TWO_PI / sides
                    val px = cx + cos(ang) * r
                    val py = cy + sin(ang) * r
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, color = c(i).copy(alpha = a * 0.32f))
                drawPath(
                    path = path,
                    color = c(i + 1).copy(alpha = a * 1.05f),
                    style = Stroke(width = minDim * 0.004f)
                )
            }
        }

        // ---------- Волны ----------
        BackgroundStyleMode.WAVES -> {
            repeat(6) { i ->
                val yBase = h * (0.38f + i * 0.11f)
                val amp = h * (0.055f - i * 0.005f)
                val path = Path()
                path.moveTo(0f, yBase)
                var x = 0f
                val stepX = w / 36f
                while (x <= w) {
                    val y = yBase + sin((x / w * 2f + phase * (1f + i * 0.14f) + i * 0.22f) * TWO_PI) * amp
                    path.lineTo(x, y)
                    x += stepX
                }
                drawPath(
                    path = path,
                    color = c(i).copy(alpha = a * (1.0f - i * 0.10f)),
                    style = Stroke(width = minDim * 0.009f, cap = StrokeCap.Round)
                )
            }
        }

        // ---------- Снег ----------
        BackgroundStyleMode.STARFIELD -> {
            repeat(count(90, min = 18)) { i ->
                val speed = 0.35f + rnd(i, 21) * 0.85f
                val y = wrap01(rnd(i, 22) + phase * speed) * h
                val x = wrap01(
                    rnd(i, 23) + sin((phase * speed * 2f + rnd(i, 24)) * TWO_PI) * 0.05f
                ) * w
                drawCircle(
                    color = c(i).copy(alpha = a * (0.55f + 0.85f * rnd(i, 26))),
                    radius = minDim * (0.006f + 0.014f * rnd(i, 25)),
                    center = Offset(x, y)
                )
            }
        }

        // ---------- Импульс: сканирующие полосы ----------
        BackgroundStyleMode.CYBERPUNK -> {
            repeat(3) { i ->
                val t = wrap01(phase * (1f + i * 0.28f) + i * 0.33f)
                val y = h * t
                val band = h * 0.07f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, c(i).copy(alpha = a * 1.1f), Color.Transparent),
                        startY = y - band,
                        endY = y + band
                    ),
                    topLeft = Offset(0f, y - band),
                    size = Size(w, band * 2f)
                )
            }
            repeat(7) { i ->
                val x = w * rnd(i, 31)
                val flicker = abs(sin((phase * 2f + rnd(i, 32)) * TWO_PI))
                drawLine(
                    color = c(i + 1).copy(alpha = a * 0.30f * (0.35f + 0.65f * flicker)),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = minDim * 0.004f
                )
            }
        }

        // ---------- Звёзды ----------
        BackgroundStyleMode.DEEP_SPACE -> {
            repeat(2) { i ->
                val radius = w * (0.45f + 0.15f * i)
                val center = Offset(
                    x = w * (0.25f + 0.5f * i) + w * 0.06f * sin((phase + i * 0.4f) * TWO_PI),
                    y = h * (0.25f + 0.4f * i)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c(i + 2).copy(alpha = a * 0.5f), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
            repeat(count(80, min = 24)) { i ->
                val twinkle = 0.5f + 0.5f * sin((phase * (1f + rnd(i, 34)) + rnd(i, 35)) * TWO_PI)
                val drift = w * 0.02f * sin((phase + rnd(i, 36)) * TWO_PI)
                drawCircle(
                    color = c(i).copy(alpha = a * (0.25f + 1.1f * twinkle * rnd(i, 37))),
                    radius = minDim * (0.0025f + 0.005f * rnd(i, 38)),
                    center = Offset(w * rnd(i, 32) + drift, h * rnd(i, 33))
                )
            }
        }

        // ---------- Искры ----------
        BackgroundStyleMode.FIRE -> {
            repeat(count(90, min = 18)) { i ->
                val t = wrap01(rnd(i, 41) + phase * (0.5f + rnd(i, 42) * 0.8f))
                val y = h * (1.05f - t * 1.15f)
                val x = w * rnd(i, 43) + sin((t * 3f + rnd(i, 44)) * TWO_PI) * w * 0.04f
                drawCircle(
                    color = c(i).copy(alpha = a * (1f - t) * 1.5f),
                    radius = minDim * (0.005f + 0.013f * rnd(i, 45)) * (1f - t * 0.5f),
                    center = Offset(x, y)
                )
            }
        }

        // ---------- Пузыри: лава-лампа ----------
        BackgroundStyleMode.LAVA -> {
            repeat(6) { i ->
                val t = wrap01(rnd(i, 51) + phase * (0.4f + rnd(i, 52) * 0.5f))
                val radius = minDim * (0.16f + 0.14f * rnd(i, 53))
                val center = Offset(
                    x = w * (0.10f + 0.80f * rnd(i, 54)) + w * 0.07f * sin((t + rnd(i, 55)) * TWO_PI),
                    y = h * (1.15f - t * 1.30f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c(i).copy(alpha = a * 1.0f),
                            c(i + 1).copy(alpha = a * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }

        // ---------- Неон: светящиеся ломаные ----------
        BackgroundStyleMode.NEON -> {
            repeat(3) { band ->
                val yBase = h * (0.24f + band * 0.24f)
                val segs = 7
                val path = Path()
                path.moveTo(0f, yBase)
                for (k in 1..segs) {
                    val x = w * k / segs
                    val y = yBase + sin((k * 1.3f + band) + phase * TWO_PI) * h * 0.065f
                    path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = c(band).copy(alpha = a * 0.30f),
                    style = Stroke(width = minDim * 0.030f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = c(band).copy(alpha = a * 1.5f),
                    style = Stroke(width = minDim * 0.007f, cap = StrokeCap.Round)
                )
            }
        }

        // ---------- Лучи ----------
        BackgroundStyleMode.NORDIC -> {
            repeat(5) { i ->
                val t = wrap01(phase * 0.7f + i / 5f)
                val x = w * (-0.35f + 1.7f * t)
                val bandW = w * (0.10f + 0.06f * rnd(i, 61))
                // Наклон считаем от ширины: на вытянутом экране «от высоты»
                // луч уезжал далеко за левый край и превращался в полосу.
                val skew = w * 0.45f
                val path = Path()
                path.moveTo(x, 0f)
                path.lineTo(x + bandW, 0f)
                path.lineTo(x + bandW - skew, h)
                path.lineTo(x - skew, h)
                path.close()
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(c(i).copy(alpha = a * 0.95f), Color.Transparent),
                        startY = 0f,
                        endY = h * 0.92f
                    )
                )
            }
        }

        // ---------- Лепестки ----------
        BackgroundStyleMode.BLOSSOM -> {
            repeat(count(60, min = 14)) { i ->
                val t = wrap01(rnd(i, 71) + phase * (0.30f + rnd(i, 72) * 0.45f))
                val y = t * h * 1.1f - h * 0.05f
                val x = w * rnd(i, 73) + sin((t * 2f + rnd(i, 74)) * TWO_PI) * w * 0.08f
                val rr = minDim * (0.014f + 0.018f * rnd(i, 75))
                withTransform({
                    rotate(degrees = t * 360f * (1f + rnd(i, 76)), pivot = Offset(x, y))
                }) {
                    drawOval(
                        color = c(i).copy(alpha = a * (0.55f + 0.6f * rnd(i, 77))),
                        topLeft = Offset(x - rr, y - rr * 0.5f),
                        size = Size(rr * 2f, rr)
                    )
                }
            }
        }

        // ---------- Дождь ----------
        BackgroundStyleMode.RAIN -> {
            repeat(count(110, min = 22)) { i ->
                val speed = 0.8f + rnd(i, 81) * 1.2f
                val t = wrap01(rnd(i, 82) + phase * speed)
                val y = t * h * 1.2f - h * 0.1f
                val x = w * rnd(i, 83)
                val len = h * (0.05f + 0.06f * rnd(i, 84))
                drawLine(
                    color = c(i).copy(alpha = a * (0.45f + 0.7f * rnd(i, 85))),
                    start = Offset(x, y),
                    end = Offset(x - len * 0.28f, y + len),
                    strokeWidth = minDim * 0.0055f,
                    cap = StrokeCap.Round
                )
            }
        }

        // ---------- Орбиты ----------
        BackgroundStyleMode.ORBIT -> {
            val center = Offset(w * 0.5f, h * 0.42f)
            repeat(4) { i ->
                val rx = minDim * (0.28f + i * 0.20f)
                val ry = rx * (0.42f + 0.10f * i)
                drawOval(
                    color = c(i).copy(alpha = a * 0.45f),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = minDim * 0.005f)
                )
                repeat(2) { k ->
                    val ang = (phase * (1f + i * 0.22f) + k * 0.5f + i * 0.17f) * TWO_PI *
                        (if (i % 2 == 0) 1f else -1f)
                    val px = center.x + cos(ang) * rx
                    val py = center.y + sin(ang) * ry
                    val dotR = minDim * (0.016f - i * 0.002f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(c(i + k).copy(alpha = a * 1.6f), Color.Transparent),
                            center = Offset(px, py),
                            radius = dotR * 3.2f
                        ),
                        radius = dotR * 3.2f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = c(i + k).copy(alpha = a * 2.0f),
                        radius = dotR,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // ---------- Поток: редкие капсулы, идущие по независимым дорожкам ----------
        BackgroundStyleMode.SIGNAL_FLOW -> {
            // This deliberately avoids the old "staircase" look: every lane owns
            // a different Y position, length, speed and entry offset. Together
            // they read as a calm technical signal field rather than a loading UI.
            val lanes = count(7, min = 4)
            repeat(lanes) { i ->
                val travel = wrap01(phase * (0.14f + rnd(i, 121) * 0.075f) + rnd(i, 122))
                val length = w * (0.17f + rnd(i, 123) * 0.20f)
                val x = -length + travel * (w + length * 2f)
                val y = h * (0.10f + rnd(i, 124) * 0.80f) +
                    sin((phase * 0.38f + rnd(i, 125)) * TWO_PI) * h * 0.018f
                val angle = -12f + rnd(i, 126) * 24f
                val radians = angle / 180f * PI.toFloat()
                val end = Offset(x + cos(radians) * length, y + sin(radians) * length)
                val stroke = minDim * (0.010f + rnd(i, 127) * 0.007f)
                val color = c(i)
                drawLine(
                    color = color.copy(alpha = a * 0.20f),
                    start = Offset(x, y),
                    end = end,
                    strokeWidth = stroke * 2.4f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0f),
                            color.copy(alpha = a * 0.95f),
                            color.copy(alpha = a * 0.30f)
                        ),
                        start = Offset(x, y),
                        end = end
                    ),
                    start = Offset(x, y),
                    end = end,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }

        BackgroundStyleMode.NONE -> Unit
    }
}


/** Кадр, который показываем при выключенном движении фона. */
private const val BackgroundStaticPhase = 0.12f

/**
 * Фаза берётся из кадровых часов, а не из бесконечной анимации: так эффект не
 * рвётся при смене экрана и одинаково тикает на обеих платформах.
 */
@Composable
fun rememberNimboBackgroundPhase(
    enabled: Boolean = true,
    periodSeconds: Float = 24f
): State<Float> {
    val phase = remember { mutableFloatStateOf(BackgroundStaticPhase) }
    LaunchedEffect(enabled, periodSeconds) {
        if (!enabled) {
            phase.floatValue = BackgroundStaticPhase
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                phase.floatValue =
                    BackgroundStaticPhase + (now - startNanos) / 1_000_000_000f / periodSeconds
            }
        }
    }
    return phase
}

/**
 * Полноэкранная подложка Nimbo — та же, что на Android: тонированный градиент,
 * нижняя виньетка и поверх них выбранный эффект.
 */
@Composable
fun NimboBackdrop(
    accent: Color,
    background: Color,
    styleMode: BackgroundStyleMode,
    paletteMode: BackgroundPaletteMode,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    isLight: Boolean = false
) {
    val paletteColors = remember(paletteMode, accent, isLight) {
        backgroundPaletteColors(paletteMode, accent, isLight)
    }
    val phase = rememberNimboBackgroundPhase(enabled = motionEnabled)

    // Подложка тонируется палитрой сдержанно: сильная заливка делала бы весь
    // экран светлее темы. Низ остаётся чистым фоном, чтобы нижняя панель с ним
    // сливалась.
    val deepTop = lerp(background, Color.Black, if (isLight) 0f else 0.14f)
    val tintTop = lerp(deepTop, paletteColors[0], if (isLight) 0.07f else 0.13f)
    val tintMid = lerp(
        background,
        paletteColors.getOrElse(1) { paletteColors[0] },
        if (isLight) 0.035f else 0.06f
    )
    val backgroundBrush = Brush.verticalGradient(listOf(tintTop, tintMid, background))
    val bottomVignette = if (isLight) Color.Transparent else Color.Black.copy(alpha = 0.42f)

    Box(modifier = modifier.fillMaxSize().background(backgroundBrush)) {
        if (bottomVignette != Color.Transparent) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, bottomVignette, Color.Transparent),
                        startY = size.height * 0.42f,
                        endY = size.height
                    )
                )
            }
        }
        if (styleMode != BackgroundStyleMode.NONE) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawNimboBackgroundMotion(
                    mode = styleMode,
                    phase = phase.value,
                    colors = paletteColors,
                    isLight = isLight
                )
            }
        }
    }
}
