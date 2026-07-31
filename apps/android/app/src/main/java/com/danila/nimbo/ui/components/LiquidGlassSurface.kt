package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
    interactive: Boolean = true
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
    val elevation = when (depth) {
        LiquidGlassDepth.CONTROL -> 3.dp
        LiquidGlassDepth.PANEL -> 9.dp
        LiquidGlassDepth.FLOATING -> 16.dp
    }
    val base = if (isDark) Color(0xFF05070C) else Color.White
    val fill = if (refractionEnabled) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to base.copy(alpha = baseAlpha),
                0.30f to Color.White.copy(
                    alpha = (if (isDark) 0.028f else 0.14f) * effectStrength
                ),
                0.52f to colors.accent.copy(
                    alpha = (if (isDark) 0.055f else 0.10f) * effectStrength
                ),
                0.74f to base.copy(alpha = baseAlpha * 0.72f),
                1.0f to base.copy(alpha = baseAlpha * 0.94f)
            ),
            start = Offset(
                x = -100f + tilt.x * 260f,
                y = -80f + tilt.y * 210f
            ),
            end = Offset(
                x = 1100f + tilt.x * 320f,
                y = 850f + tilt.y * 260f
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                base.copy(alpha = baseAlpha),
                base.copy(alpha = baseAlpha * 0.90f)
            )
        )
    }
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
        Brush.sweepGradient(*rimStops)
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
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.accent.copy(alpha = if (isDark) 0.08f else 0.08f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.46f else 0.16f)
        )
        .clip(shape)
        .background(fill)
        .drawWithCache {
            val upperSheen = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(
                        alpha = (if (isDark) 0.085f else 0.18f) * effectStrength
                    ),
                    Color.Transparent
                ),
                center = Offset(
                    x = size.width * (0.50f + tilt.x * 0.42f).coerceIn(0.05f, 0.95f),
                    y = size.height * (0.08f + tilt.y * 0.30f).coerceIn(-0.10f, 0.44f)
                ),
                radius = size.maxDimension * 0.72f
            )
            val refractedAccent = Brush.radialGradient(
                colors = listOf(
                    colors.accent.copy(alpha = (if (isDark) 0.085f else 0.15f) * effectStrength),
                    Color.Transparent
                ),
                center = Offset(
                    x = size.width * (0.72f - tilt.x * 0.28f).coerceIn(0.04f, 0.96f),
                    y = size.height * (0.80f - tilt.y * 0.24f).coerceIn(0.04f, 0.96f)
                ),
                radius = size.maxDimension * 0.72f
            )
            val coolRefraction = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF79D7FF).copy(alpha = (if (isDark) 0.045f else 0.08f) * effectStrength),
                    Color.Transparent
                ),
                center = Offset(
                    x = size.width * (0.12f + tilt.x * 0.22f).coerceIn(0.02f, 0.80f),
                    y = size.height * (0.88f + tilt.y * 0.16f).coerceIn(0.20f, 0.98f)
                ),
                radius = size.maxDimension * 0.56f
            )
            val warmRefraction = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF8DDA).copy(alpha = (if (isDark) 0.035f else 0.065f) * effectStrength),
                    Color.Transparent
                ),
                center = Offset(
                    x = size.width * (0.86f + tilt.x * 0.12f).coerceIn(0.20f, 0.98f),
                    y = size.height * (0.10f + tilt.y * 0.20f).coerceIn(0.02f, 0.80f)
                ),
                radius = size.maxDimension * 0.46f
            )
            onDrawBehind {
                if (refractionEnabled) {
                    drawRect(upperSheen)
                    drawRect(refractedAccent)
                    drawRect(coolRefraction)
                    drawRect(warmRefraction)
                }
            }
        }
        // The neutral outline keeps long capsules readable through their quiet sweep
        // sectors; the moving colored rim then flows over the complete perimeter.
        .border(if (floatingRim) 1.15.dp else 0.8.dp, completeRimColor, shape)
        .border(if (floatingRim) 1.55.dp else 1.dp, rim, shape)
}
