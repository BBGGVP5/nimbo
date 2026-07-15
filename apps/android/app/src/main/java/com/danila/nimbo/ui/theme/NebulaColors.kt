package com.danila.nimbo.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class NebulaColors(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val onBackground: Color,
    val onSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glow: Color,
    val statusConnected: Color = Color(0xFF00E5B0),
    val statusDisconnected: Color = Color(0xFF6B7280),
    val statusError: Color = Color(0xFFF44336),
    val statusConnecting: Color,
    val primaryGradientStart: Color,
    val primaryGradientMiddle: Color,
    val primaryGradientEnd: Color = Color(0xFF5D4A8A),
    // ===== Element style (Nimbo Glass vs Material You) =====
    // When [isMaterialYou] is true, the panel/control/border/divider colors below
    // hold tonal Material 3 values; the windows* helpers in the UI switch to them so
    // the whole interface re-skins. For Nimbo Glass these stay at the glass defaults
    // and the helpers compute their own flat-glass colors.
    val isMaterialYou: Boolean = false,
    val panelFill: Color = DarkSurface,
    val controlFill: Color = Color.White.copy(alpha = 0.035f),
    val softFill: Color = Color.White.copy(alpha = 0.08f),
    val panelBorder: Color = Color.Transparent,
    val divider: Color = Color.White.copy(alpha = 0.075f)
)

val LocalNebulaColors = staticCompositionLocalOf<NebulaColors> {
    error("No NebulaColors provided")
}
