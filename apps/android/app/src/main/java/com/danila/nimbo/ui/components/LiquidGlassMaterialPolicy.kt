package com.danila.nimbo.ui.components

/**
 * Converts the theme panel alpha into the alpha used by the actual glass
 * surface. This keeps the appearance slider effective instead of letting the
 * component replace it with a fixed value.
 */
internal object LiquidGlassMaterialPolicy {

    fun baseAlpha(
        depth: LiquidGlassDepth,
        isDark: Boolean,
        reducedTransparency: Boolean,
        panelAlpha: Float
    ): Float {
        if (reducedTransparency) return if (isDark) 0.90f else 0.94f

        val defaultAlpha = when (depth) {
            LiquidGlassDepth.CONTROL -> if (isDark) 0.12f else 0.32f
            LiquidGlassDepth.PANEL -> if (isDark) 0.18f else 0.40f
            LiquidGlassDepth.FLOATING -> if (isDark) 0.24f else 0.46f
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
