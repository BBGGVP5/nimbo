package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Стеклянный материал Nimbo, общий для Android и iOS.
 *
 * Раньше на iOS рисовались собственные карточки с градиентом и рамкой, из-за
 * чего интерфейс лишь «походил» на андроидный. Здесь лежит та самая математика,
 * которой пользуется приложение: тонировка по глубине, ободок по направлению
 * света и никаких теней — их прямоугольный слой просвечивал сквозь стекло.
 *
 * Наклон устройства (`tiltX`/`tiltY`) остаётся параметром: на Android его даёт
 * акселерометр, на iOS пока передаётся покой.
 */

@Stable
enum class LiquidGlassDepth {
    CONTROL,
    PANEL,
    FLOATING
}

data class LiquidGlassRimSample(
    val fraction: Float,
    val alpha: Float,
    val whiteMix: Float,
    val spectrumMix: Float
)

object LiquidGlassRimPolicy {

    fun samples(
        tiltX: Float,
        tiltY: Float,
        isDark: Boolean,
        effectStrength: Float,
        steps: Int = 64
    ): List<LiquidGlassRimSample> {
        require(steps >= 8)
        // Bias the light toward the upper-right at rest. Without this stable
        // vector, atan2 around (0, 0) can rotate the whole rim from tiny sensor noise.
        val lightVectorX = 0.35f + tiltX.coerceIn(-1f, 1f)
        val lightVectorY = -0.35f + tiltY.coerceIn(-1f, 1f)
        val lightAngle = atan2(lightVectorY, lightVectorX)
        val safeStrength = effectStrength.coerceIn(0f, 1f)
        val baseAlpha = if (isDark) 0.052f else 0.16f

        return (0..steps).map { index ->
            val fraction = index.toFloat() / steps
            val angle = (fraction * PI * 2.0).toFloat()
            val delta = shortestAngle(angle - lightAngle)
            val whitePeak = cos(delta.toDouble()).coerceAtLeast(0.0).pow(5.0).toFloat()
            val oppositeGlow = cos((delta - PI).toDouble())
                .coerceAtLeast(0.0)
                .pow(3.0)
                .toFloat()
            val spectrum = ((sin((angle * 2f - lightAngle * 0.45f).toDouble()) + 1.0) / 2.0)
                .toFloat()

            LiquidGlassRimSample(
                fraction = fraction,
                alpha = (
                    baseAlpha +
                        safeStrength * (0.030f + whitePeak * 0.17f + oppositeGlow * 0.045f)
                    ).coerceIn(0f, 0.88f),
                whiteMix = (0.14f + whitePeak * 0.86f).coerceIn(0f, 1f),
                spectrumMix = spectrum.coerceIn(0f, 1f)
            )
        }
    }

    private fun shortestAngle(value: Float): Float {
        val fullTurn = (PI * 2.0).toFloat()
        var result = value % fullTurn
        if (result > PI) result -= fullTurn
        if (result < -PI) result += fullTurn
        return result
    }
}

/**
 * Converts the theme panel alpha into the alpha used by the actual glass
 * surface. This keeps the appearance slider effective instead of letting the
 * component replace it with a fixed value.
 */
object LiquidGlassMaterialPolicy {

    // Quiet content surfaces; floating navigation keeps the stronger glass edge.
    fun chromeStrength(depth: LiquidGlassDepth): Float = when (depth) {
        LiquidGlassDepth.CONTROL -> 0.38f
        LiquidGlassDepth.PANEL -> 0.55f
        LiquidGlassDepth.FLOATING -> 1f
    }

    fun sheenAlpha(depth: LiquidGlassDepth, isDark: Boolean, effectStrength: Float): Float =
        (if (isDark) 0.07f else 0.16f) * chromeStrength(depth) * effectStrength.coerceIn(0f, 1f)

    fun baseAlpha(
        depth: LiquidGlassDepth,
        isDark: Boolean,
        reducedTransparency: Boolean,
        panelAlpha: Float
    ): Float {
        if (reducedTransparency) return if (isDark) 0.90f else 0.94f

        // Тёмная тема: стекло держим заметно плотнее. На прозрачных значениях
        // фон бил сквозь панели, и карточки выглядели как выцветшие пятна.
        val defaultAlpha = when (depth) {
            LiquidGlassDepth.CONTROL -> if (isDark) 0.26f else 0.32f
            LiquidGlassDepth.PANEL -> if (isDark) 0.34f else 0.40f
            LiquidGlassDepth.FLOATING -> if (isDark) 0.42f else 0.46f
        }
        return (defaultAlpha * effectStrength(isDark, reducedTransparency, panelAlpha))
            .coerceIn(0.002f, 0.94f)
    }

    fun effectStrength(
        isDark: Boolean,
        reducedTransparency: Boolean,
        panelAlpha: Float
    ): Float {
        if (reducedTransparency) return 0f
        val referencePanelAlpha = if (isDark) 0.42f else 0.62f
        return (panelAlpha / referencePanelAlpha).coerceIn(0.02f, 1f)
    }
}


/**
 * Заливка и ободок стекла. Осознанно без шейдеров: полноразмерный render layer
 * под скруглённой карточкой на части устройств проступает бледным квадратом.
 */
fun Modifier.nimboGlassSurface(
    shape: Shape,
    depth: LiquidGlassDepth = LiquidGlassDepth.PANEL,
    accent: Color,
    isDark: Boolean,
    panelAlpha: Float,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    refractionEnabled: Boolean = true,
    reducedTransparency: Boolean = false,
    showOutline: Boolean = true,
    brightness: Float = 1f
): Modifier {
    val baseAlpha = LiquidGlassMaterialPolicy.baseAlpha(
        depth = depth,
        isDark = isDark,
        reducedTransparency = reducedTransparency,
        panelAlpha = panelAlpha
    )
    val effectStrength = LiquidGlassMaterialPolicy.effectStrength(
        isDark = isDark,
        reducedTransparency = reducedTransparency,
        panelAlpha = panelAlpha
    )
    // На iOS системные материалы заметно светлее: чистая тень делала панель
    // плоской заплаткой на фоне. Подмешиваем белый, сохраняя глубину.
    val rawBase = if (isDark) lerp(Color(0xFF05070C), Color(0xFF8FA8CE), 0.22f) else Color.White
    val level = if (brightness.isFinite()) brightness.coerceIn(0.5f, 2f) else 1f
    val base = Color(
        (rawBase.red * level).coerceIn(0f, 1f),
        (rawBase.green * level).coerceIn(0f, 1f),
        (rawBase.blue * level).coerceIn(0f, 1f)
    )
    val materialTint = lerp(
        base,
        accent,
        if (refractionEnabled) 0.025f * effectStrength else 0f
    ).copy(alpha = baseAlpha)
    val neutralRimAlpha = if (isDark) 0.09f else 0.28f
    val floatingRim = depth == LiquidGlassDepth.FLOATING
    val chromeStrength = LiquidGlassMaterialPolicy.chromeStrength(depth)
    val rim = if (refractionEnabled) {
        val rimStops = LiquidGlassRimPolicy.samples(
            tiltX = tiltX,
            tiltY = tiltY,
            isDark = isDark,
            effectStrength = effectStrength
        ).map { sample ->
            val rimColor = lerp(accent, Color.White, 0.82f + sample.whiteMix * 0.18f)
                .copy(
                    alpha = (
                        sample.alpha * chromeStrength
                    ).coerceAtMost(if (isDark) 0.46f else 0.88f)
                )
            sample.fraction to rimColor
        }.toTypedArray()
        Brush.linearGradient(*rimStops)
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = neutralRimAlpha * chromeStrength),
                Color.White.copy(alpha = neutralRimAlpha * chromeStrength * 0.55f)
            )
        )
    }

    // Блик по верхней кромке — то, чем стекло отличается от заливки.
    val sheen = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = LiquidGlassMaterialPolicy.sheenAlpha(depth, isDark, effectStrength)),
            Color.Transparent
        )
    )

    return this
        .clip(shape)
        .background(materialTint, shape)
        .background(sheen, shape)
        .then(
            if (showOutline) {
                Modifier
                    .border(if (floatingRim) 1.dp else 0.75.dp, rim, shape)
            } else {
                Modifier
            }
        )
}
