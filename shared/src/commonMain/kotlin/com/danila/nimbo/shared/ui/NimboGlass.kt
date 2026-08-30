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
    showOutline: Boolean = true
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
    val base = if (isDark) Color(0xFF05070C) else Color.White
    val materialTint = lerp(
        base,
        accent,
        if (refractionEnabled) 0.025f * effectStrength else 0f
    ).copy(alpha = baseAlpha)
    val neutralRimAlpha = if (isDark) 0.09f else 0.28f
    val floatingRim = depth == LiquidGlassDepth.FLOATING
    val completeRimColor = if (refractionEnabled) {
        lerp(Color.White, accent, 0.16f).copy(
            alpha = when {
                floatingRim && isDark -> 0.12f
                floatingRim -> 0.32f
                isDark -> 0.065f
                else -> 0.20f
            }
        )
    } else {
        Color.Transparent
    }
    val rim = if (refractionEnabled) {
        val coolRim = Color(0xFF79D7FF)
        val warmRim = Color(0xFFFF8DDA)
        val rimStops = LiquidGlassRimPolicy.samples(
            tiltX = tiltX,
            tiltY = tiltY,
            isDark = isDark,
            effectStrength = effectStrength
        ).map { sample ->
            val spectrum = lerp(coolRim, warmRim, sample.spectrumMix)
            val accentedSpectrum = lerp(spectrum, accent, 0.34f)
            val rimColor = lerp(accentedSpectrum, Color.White, sample.whiteMix)
                .copy(
                    alpha = (
                        sample.alpha * if (floatingRim) 1.28f else 1f
                    ).coerceAtMost(if (isDark) 0.46f else 0.88f)
                )
            sample.fraction to rimColor
        }.toTypedArray()
        Brush.linearGradient(*rimStops)
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = neutralRimAlpha),
                Color.White.copy(alpha = neutralRimAlpha * 0.55f)
            )
        )
    }

    return this
        .clip(shape)
        .background(materialTint, shape)
        .then(
            if (showOutline) {
                Modifier
                    .border(if (floatingRim) 1.15.dp else 0.8.dp, completeRimColor, shape)
                    .border(if (floatingRim) 1.55.dp else 1.dp, rim, shape)
            } else {
                Modifier
            }
        )
}
