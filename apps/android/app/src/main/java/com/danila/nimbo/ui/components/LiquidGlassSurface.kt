package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalLiquidGlassTilt
import com.danila.nimbo.ui.theme.LocalLiquidRefractionEnabled
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled

@Stable
enum class LiquidGlassDepth {
    CONTROL,
    PANEL,
    FLOATING
}

/**
 * A shared, content-safe Liquid Glass material. Compose does not expose a
 * portable backdrop-filter, so the material is built from a translucent tint,
 * directional refraction, a bright inner rim and the animated app background
 * that remains visible through the surface.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape,
    depth: LiquidGlassDepth = LiquidGlassDepth.PANEL,
    interactive: Boolean = true,
    showOutline: Boolean = true
): Modifier = composed {
    val colors = LocalNebulaColors.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    val refractionEnabled = LocalLiquidRefractionEnabled.current
    val tilt = LocalLiquidGlassTilt.current
    val isDark = colors.background.luminance() < 0.5f

    val baseAlpha = LiquidGlassMaterialPolicy.baseAlpha(
        depth = depth,
        isDark = isDark,
        reducedTransparency = reducedTransparency,
        panelAlpha = colors.panelFill.alpha
    )
    val effectStrength = LiquidGlassMaterialPolicy.effectStrength(
        isDark = isDark,
        reducedTransparency = reducedTransparency,
        panelAlpha = colors.panelFill.alpha
    )
    val base = if (isDark) Color(0xFF05070C) else Color.White
    val materialTint = lerp(
        base,
        colors.accent,
        if (refractionEnabled) 0.025f * effectStrength else 0f
    ).copy(alpha = baseAlpha)
    val neutralRimAlpha = if (isDark) 0.09f else 0.28f
    val floatingRim = depth == LiquidGlassDepth.FLOATING
    val completeRimColor = if (refractionEnabled) {
        lerp(Color.White, colors.accent, 0.16f).copy(
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
            tiltX = tilt.x,
            tiltY = tilt.y,
            isDark = isDark,
            effectStrength = effectStrength
        ).map { sample ->
            val spectrum = lerp(coolRim, warmRim, sample.spectrumMix)
            val accentedSpectrum = lerp(spectrum, colors.accent, 0.34f)
            val rimColor = lerp(accentedSpectrum, Color.White, sample.whiteMix)
                .copy(
                    alpha = (
                        sample.alpha * if (floatingRim) 1.28f else 1f
                    ).coerceAtMost(if (isDark) 0.46f else 0.88f)
                )
            sample.fraction to rimColor
        }.toTypedArray()
        // A sweep-gradient border is backed by a square GPU texture. Several
        // Android 17 launchers/drivers blend that texture into the surface,
        // exposing a pale rectangle in the centre of every glass card. A
        // linear spectrum still colors the complete outline and reacts to the
        // tilt-driven samples, but stays inside the shape-safe render path.
        Brush.linearGradient(*rimStops)
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = neutralRimAlpha),
                Color.White.copy(alpha = neutralRimAlpha * 0.55f)
            )
        )
    }

    this
        .liquidTouchDeformation(depth = depth, interactive = interactive)
        // Никакой .shadow(): она держит постоянный прямоугольный render layer на
        // каждой стеклянной панели, и на части устройств этот слой просвечивает
        // сквозь полупрозрачное стекло бледным квадратом внутри скруглённой
        // карточки. Глубину дают только тонировка и рамки ниже — они
        // рисуются строго по форме.
        .clip(shape)
        // Keep the body deliberately shader-free. Several Android 17 devices
        // expose the square backing texture of translucent Compose gradients.
        // The solid translucent tint preserves the glass depth, while the two
        // shape-safe borders below carry the moving/refraction colour.
        .background(materialTint, shape)
        .then(
            if (showOutline) {
                // The neutral outline keeps long capsules readable through their quiet sweep
                // sectors; the moving colored rim then flows over the complete perimeter.
                Modifier
                    .border(if (floatingRim) 1.15.dp else 0.8.dp, completeRimColor, shape)
                    .border(if (floatingRim) 1.55.dp else 1.dp, rim, shape)
            } else {
                Modifier
            }
        )
}
