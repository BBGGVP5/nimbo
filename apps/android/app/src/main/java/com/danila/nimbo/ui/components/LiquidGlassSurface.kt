package com.danila.nimbo.ui.components

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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
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
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.accent.copy(alpha = if (isDark) 0.08f else 0.08f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.46f else 0.16f)
        )
        .clip(shape)
        .drawWithCache {
            // Render the whole material in one outline-bound draw node. Several
            // Android 17 GPU drivers expose the rectangular backing textures of
            // overlapping radial shaders as pale square tiles, even when Compose
            // clips every individual layer. Keeping the material path-bound and
            // using continuous linear optics avoids those texture seams.
            val glassPath = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
            val diagonalShiftX = tilt.x.coerceIn(-1f, 1f) * size.width * 0.18f
            val diagonalShiftY = tilt.y.coerceIn(-1f, 1f) * size.height * 0.18f
            val glassFill = if (refractionEnabled) {
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to base.copy(alpha = baseAlpha * 0.96f),
                        0.25f to Color.White.copy(
                            alpha = (if (isDark) 0.030f else 0.14f) * effectStrength
                        ),
                        0.48f to colors.accent.copy(
                            alpha = (if (isDark) 0.055f else 0.10f) * effectStrength
                        ),
                        0.70f to base.copy(alpha = baseAlpha * 0.72f),
                        1.00f to base.copy(alpha = baseAlpha * 0.94f)
                    ),
                    start = Offset(
                        x = -size.width * 0.12f + diagonalShiftX,
                        y = -size.height * 0.10f + diagonalShiftY
                    ),
                    end = Offset(
                        x = size.width * 1.12f + diagonalShiftX,
                        y = size.height * 1.10f + diagonalShiftY
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        base.copy(alpha = baseAlpha),
                        base.copy(alpha = baseAlpha * 0.90f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            }
            val opticalFlow = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.22f to Color(0xFF79D7FF).copy(
                        alpha = (if (isDark) 0.024f else 0.055f) * effectStrength
                    ),
                    0.46f to Color.White.copy(
                        alpha = (if (isDark) 0.044f else 0.10f) * effectStrength
                    ),
                    0.68f to colors.accent.copy(
                        alpha = (if (isDark) 0.035f else 0.075f) * effectStrength
                    ),
                    0.84f to Color(0xFFFF8DDA).copy(
                        alpha = (if (isDark) 0.018f else 0.04f) * effectStrength
                    ),
                    1.00f to Color.Transparent
                ),
                start = Offset(
                    x = size.width * (0.05f + tilt.x * 0.12f),
                    y = size.height * (0.96f + tilt.y * 0.10f)
                ),
                end = Offset(
                    x = size.width * (0.95f + tilt.x * 0.12f),
                    y = size.height * (0.04f + tilt.y * 0.10f)
                )
            )
            onDrawBehind {
                drawPath(glassPath, glassFill)
                if (refractionEnabled) {
                    drawPath(glassPath, opticalFlow)
                }
            }
        }
        // The neutral outline keeps long capsules readable through their quiet sweep
        // sectors; the moving colored rim then flows over the complete perimeter.
        .border(if (floatingRim) 1.15.dp else 0.8.dp, completeRimColor, shape)
        .border(if (floatingRim) 1.55.dp else 1.dp, rim, shape)
}
