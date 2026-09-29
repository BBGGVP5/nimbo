package com.danila.nimbo.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import com.danila.nimbo.shared.ui.nimboGlassSurface
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalLiquidGlassTilt
import com.danila.nimbo.ui.theme.LocalLiquidRefractionEnabled
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled

/** Глубина материала переехала в общий модуль вместе с самим стеклом. */
typealias LiquidGlassDepth = com.danila.nimbo.shared.ui.LiquidGlassDepth

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

    // Сам материал живёт в общем модуле — iOS обязан рисовать ровно те же
    // карточки. Здесь остаётся только то, чего на iOS нет: отклик на касание.
    this
        .liquidTouchDeformation(depth = depth, interactive = interactive)
        .nimboGlassSurface(
            shape = shape,
            depth = depth,
            accent = colors.accent,
            isDark = colors.background.luminance() < 0.5f,
            panelAlpha = colors.panelFill.alpha,
            tiltX = tilt.x,
            tiltY = tilt.y,
            refractionEnabled = refractionEnabled,
            reducedTransparency = reducedTransparency,
            showOutline = showOutline
        )
}
