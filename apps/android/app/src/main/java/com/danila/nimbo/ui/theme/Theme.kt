package com.danila.nimbo.ui.theme

import android.app.Activity
import android.os.Build as AndroidBuild
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

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
    BLOSSOM
}

enum class ElementStyleMode {
    MORPHISM,
    MATERIAL3,
    NOTHING_DOTS,
    OUTLINED,
    SOFT_NEO
}

val LocalBackgroundStyleMode = staticCompositionLocalOf { BackgroundStyleMode.MORPHISM }
val LocalElementStyleMode = staticCompositionLocalOf { ElementStyleMode.MORPHISM }
val LocalBackgroundAnimationEnabled = staticCompositionLocalOf { true }
val LocalReducedTransparencyEnabled = staticCompositionLocalOf { false }
val LocalGlobalBlurRadius = compositionLocalOf { 25.0f }
val LocalGlobalCornerRadius = compositionLocalOf { 1.0f }


// ==================== ТЁМНЫЕ ТЕМЫ ====================

// Purple dark color scheme
private val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C5DFA),
    secondary = Color(0xFF9B8DF5),
    tertiary = Color(0xFFC7B8FF),
    background = Color(0xFF0F0F1A),
    surface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF5F5FA),
    onSurface = Color(0xFFF5F5FA),
    outline = Color(0x20FFFFFF)
)

// Blue dark color scheme
private val BlueDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentBlueBright,
    tertiary = AccentBlueSoft,
    background = Color(0xFF0A0F1A),
    surface = Color(0xFF121A2E),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color(0xFF07101F),
    onSecondary = Color(0xFF07101F),
    onTertiary = Color.Black,
    onBackground = Color(0xFFF5F5FA),
    onSurface = Color(0xFFF5F5FA),
    outline = Color(0x20FFFFFF)
)

// Green dark color scheme
private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00C853),
    secondary = Color(0xFF00E676),
    tertiary = Color(0xFF69F0AE),
    background = Color(0xFF0A1A0F),
    surface = Color(0xFF122E1A),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF5F5FA),
    onSurface = Color(0xFFF5F5FA),
    outline = Color(0x20FFFFFF)
)

// Red dark color scheme
private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF5252),
    secondary = Color(0xFFFF8A80),
    tertiary = Color(0xFFFFCDD2),
    background = Color(0xFF1A0A0A),
    surface = Color(0xFF2E1212),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF5F5FA),
    onSurface = Color(0xFFF5F5FA),
    outline = Color(0x20FFFFFF)
)

// Orange dark color scheme
private val OrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF9800),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFFFFE0B2),
    background = Color(0xFF1A120A),
    surface = Color(0xFF2E1F12),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF5F5FA),
    onSurface = Color(0xFFF5F5FA),
    outline = Color(0x20FFFFFF)
)

private val PinkDarkColorScheme = darkColorScheme(
    primary = AccentPink,
    secondary = Color(0xFFF9A8D4),
    tertiary = Color(0xFFFBCFE8),
    background = Color(0xFF1A0A14),
    surface = Color(0xFF2E1224),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFFDF2F8),
    onSurface = Color(0xFFFDF2F8),
    outline = Color(0x20FFFFFF)
)

private val CyanDarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = Color(0xFF67E8F9),
    tertiary = Color(0xFFA5F3FC),
    background = Color(0xFF06161A),
    surface = Color(0xFF0F2A30),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFE6FBFF),
    onSurface = Color(0xFFE6FBFF),
    outline = Color(0x20FFFFFF)
)

private val LimeDarkColorScheme = darkColorScheme(
    primary = AccentLime,
    secondary = Color(0xFFBEF264),
    tertiary = Color(0xFFD9F99D),
    background = Color(0xFF121A06),
    surface = Color(0xFF24300F),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF7FEE7),
    onSurface = Color(0xFFF7FEE7),
    outline = Color(0x20FFFFFF)
)

private val SilverDarkColorScheme = darkColorScheme(
    primary = AccentSilver,
    secondary = Color(0xFFCBD5E1),
    tertiary = Color(0xFFE2E8F0),
    background = Color(0xFF0F1218),
    surface = Color(0xFF1E2430),
    surfaceVariant = Color(0x18FFFFFF),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    outline = Color(0x20FFFFFF)
)

// ==================== СВЕТЛЫЕ ТЕМЫ ====================

// Purple light color scheme
private val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF7C5DFA),
    secondary = Color(0xFF9B8DF5),
    tertiary = Color(0xFFC7B8FF),
    background = Color(0xFFF5F5FA),
    surface = Color(0xFFFAFAFF),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF0F0F1A),
    onSurface = Color(0xFF0F0F1A),
    outline = Color(0x20000000)
)

// Blue light color scheme
private val BlueLightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = AccentBlueBright,
    tertiary = AccentBlueSoft,
    background = Color(0xFFF0F4F8),
    surface = Color(0xFFFAFBFF),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color(0xFF07101F),
    onSecondary = Color(0xFF07101F),
    onTertiary = Color.Black,
    onBackground = Color(0xFF0A0F1A),
    onSurface = Color(0xFF0A0F1A),
    outline = Color(0x20000000)
)

// Green light color scheme
private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF00C853),
    secondary = Color(0xFF00E676),
    tertiary = Color(0xFF69F0AE),
    background = Color(0xFFF0F8F0),
    surface = Color(0xFFFAFFFA),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF0A1A0F),
    onSurface = Color(0xFF0A1A0F),
    outline = Color(0x20000000)
)

// Red light color scheme
private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFFF5252),
    secondary = Color(0xFFFF8A80),
    tertiary = Color(0xFFFFCDD2),
    background = Color(0xFFF8F0F0),
    surface = Color(0xFFFFFAFA),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFF1A0A0A),
    onSurface = Color(0xFF1A0A0A),
    outline = Color(0x20000000)
)

// Orange light color scheme
private val OrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFFFF9800),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFFFFE0B2),
    background = Color(0xFFF8F4F0),
    surface = Color(0xFFFFFAF5),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFF1A120A),
    onSurface = Color(0xFF1A120A),
    outline = Color(0x20000000)
)

private val PinkLightColorScheme = lightColorScheme(
    primary = AccentPink,
    secondary = Color(0xFFDB2777),
    tertiary = Color(0xFFBE185D),
    background = Color(0xFFFFF1F8),
    surface = Color(0xFFFFFAFC),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A0A14),
    onSurface = Color(0xFF1A0A14),
    outline = Color(0x20000000)
)

private val CyanLightColorScheme = lightColorScheme(
    primary = AccentCyan,
    secondary = Color(0xFF0891B2),
    tertiary = Color(0xFF0E7490),
    background = Color(0xFFEFFBFF),
    surface = Color(0xFFFAFEFF),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF06161A),
    onSurface = Color(0xFF06161A),
    outline = Color(0x20000000)
)

private val LimeLightColorScheme = lightColorScheme(
    primary = AccentLime,
    secondary = Color(0xFF65A30D),
    tertiary = Color(0xFF4D7C0F),
    background = Color(0xFFF7FCEB),
    surface = Color(0xFFFDFFF7),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121A06),
    onSurface = Color(0xFF121A06),
    outline = Color(0x20000000)
)

private val SilverLightColorScheme = lightColorScheme(
    primary = AccentSilver,
    secondary = Color(0xFF64748B),
    tertiary = Color(0xFF475569),
    background = Color(0xFFF4F7FA),
    surface = Color(0xFFFCFEFF),
    surfaceVariant = Color(0x18000000),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F1218),
    onSurface = Color(0xFF0F1218),
    outline = Color(0x20000000)
)

// ==================== HELPERS ====================

private const val THEME_COLOR_COUNT = 9

fun isDarkTheme(themeIndex: Int): Boolean = themeIndex < THEME_COLOR_COUNT

private fun getColorScheme(themeIndex: Int): ColorScheme = when (themeIndex) {
    0 -> PurpleDarkColorScheme
    1 -> BlueDarkColorScheme
    2 -> GreenDarkColorScheme
    3 -> RedDarkColorScheme
    4 -> OrangeDarkColorScheme
    5 -> PinkDarkColorScheme
    6 -> CyanDarkColorScheme
    7 -> LimeDarkColorScheme
    8 -> SilverDarkColorScheme
    9 -> PurpleLightColorScheme
    10 -> BlueLightColorScheme
    11 -> GreenLightColorScheme
    12 -> RedLightColorScheme
    13 -> OrangeLightColorScheme
    14 -> PinkLightColorScheme
    15 -> CyanLightColorScheme
    16 -> LimeLightColorScheme
    17 -> SilverLightColorScheme
    else -> BlueDarkColorScheme
}

/**
 * Получение кастомных цветов Nebula на основе индекса темы или кастомного цвета
 */
/**
 * Utility extensions for modifying color brightness and transparency
 */
fun Color.adjustBrightness(brightness: Float): Color {
    if (brightness == 1.0f) return this
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (this.red * 255f).toInt(),
        (this.green * 255f).toInt(),
        (this.blue * 255f).toInt(),
        hsv
    )
    hsv[2] = (hsv[2] * brightness).coerceIn(0f, 1f)
    if (brightness > 1.0f) {
        hsv[1] = (hsv[1] * (1.0f + (brightness - 1.0f) * 0.2f)).coerceIn(0f, 1f)
    }
    return Color.hsv(hsv[0], hsv[1], hsv[2], this.alpha)
}

fun Color.adjustTransparency(transparency: Float): Color {
    if (transparency <= 0f) return this
    val baseAlpha = if (this.alpha == 1.0f) 0.9f else this.alpha
    val newAlpha = baseAlpha * (1.0f - transparency).coerceIn(0.02f, 1.0f)
    return this.copy(alpha = newAlpha)
}

fun getNebulaColors(
    themeIndex: Int,
    isCustomAccent: Boolean = false,
    customAccentColor: Color = Color(0xFF7C5DFA),
    gradientEffectsEnabled: Boolean = true,
    customGradientColor1: Color = Color(0xFF7C5DFA),
    customGradientColor2: Color = Color(0xFF00E5B0),
    customGradientColor3: Color = Color(0xFF00D2FF),
    customGradientCount: Int = 1,
    useDynamicColor: Boolean = false,
    highContrastUi: Boolean = false,
    reducedTransparency: Boolean = false,
    pureBlackMode: Boolean = false,
    elementStyle: Int = 0,
    colorScheme: ColorScheme? = null,
    globalBrightness: Float = 1.0f,
    globalTransparency: Float = 0.0f
): NebulaColors {
    val isDark = isDarkTheme(themeIndex)
    val resolvedColorScheme = colorScheme ?: getColorScheme(themeIndex)
    val accent = if (useDynamicColor) {
        resolvedColorScheme.primary
    } else if (isCustomAccent) {
        // Блендим акцентный цвет из всех выбранных цветов градиента
        when (customGradientCount) {
            3 -> {
                // Усреднение трех цветов
                val mid = customGradientColor1.lerp(customGradientColor2, 0.5f)
                mid.lerp(customGradientColor3, 0.33f)
            }
            2 -> {
                // Усреднение двух цветов
                customGradientColor1.lerp(customGradientColor2, 0.5f)
            }
            else -> customGradientColor1
        }
    } else {
        val colorType = if (isDark) themeIndex else themeIndex - THEME_COLOR_COUNT
        when (colorType) {
            0 -> AccentPurple
            1 -> AccentBlue
            2 -> AccentGreen
            3 -> AccentRed
            4 -> AccentOrange
            5 -> AccentPink
            6 -> AccentCyan
            7 -> AccentLime
            8 -> AccentSilver
            else -> AccentBlue
        }
    }

    val textSecondaryAlpha = if (highContrastUi) 0.86f else 0.66f
    val textTertiaryAlpha = if (highContrastUi) 0.72f else 0.42f
    val cardAlpha = when {
        reducedTransparency -> if (isDark) 0.06f else 0.18f
        highContrastUi -> if (isDark) 0.05f else 0.14f
        else -> if (isDark) 0.03f else 0.08f
    }

    val windowsBackground = if (pureBlackMode) Color(0xFF000000) else DarkBackground
    val windowsSurface = if (pureBlackMode) Color(0xFF050A12) else DarkSurface
    val windowsText = if (pureBlackMode) Color(0xFFF4F5FA) else Color(0xFFEAEBF2)
    val windowsLightBackground = Color(0xFFF6F5FB)
    val windowsLightSurface = Color(0xFFFFFFFF)
    val windowsLightText = Color(0xFF211D34)
    val resolvedBackground = if (isDark) windowsBackground else windowsLightBackground
    val resolvedSurface = if (isDark) windowsSurface else windowsLightSurface
    val resolvedText = if (isDark) windowsText else windowsLightText

    // Material You ("Expressive"): tonal, accent-tinted surfaces and no glass hairline.
    // surfaceColorAtElevation tints surface toward the (dynamic or accent) primary, so
    // panels visibly lift off the background and the look clearly differs from glass.
    val isMaterialYou = elementStyle == 1
    val m3PanelFill = resolvedColorScheme.surfaceColorAtElevation(6.dp)
    val m3ControlFill = resolvedColorScheme.surfaceColorAtElevation(12.dp)
    val m3SoftFill = resolvedColorScheme.surfaceColorAtElevation(2.dp)
    val m3Divider = resolvedColorScheme.outlineVariant

    // Determine target raw colors based on style
    val resolvedPanelFill = if (isMaterialYou) m3PanelFill else {
        if (isDark) DarkSurface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    }
    val resolvedControlFill = if (isMaterialYou) m3ControlFill else {
        if (isDark) Color.White.copy(alpha = 0.035f) else Color(0xFFF6F5FB)
    }
    val resolvedSoftFill = if (isMaterialYou) m3SoftFill else {
        if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F0F7)
    }
    val resolvedPanelBorder = if (isMaterialYou) Color.Transparent else {
        if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE2E0EA)
    }
    val resolvedDivider = if (isMaterialYou) m3Divider else {
        if (isDark) Color.White.copy(alpha = 0.075f) else Color(0xFFE7E5EE)
    }

    // Apply global brightness and transparency
    val finalPanelFill = resolvedPanelFill.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)
    val finalControlFill = resolvedControlFill.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)
    val finalSoftFill = resolvedSoftFill.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)
    val finalPanelBorder = resolvedPanelBorder.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)
    val finalDivider = resolvedDivider.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)

    val finalSurface = if (isMaterialYou) finalPanelFill else resolvedSurface.adjustBrightness(globalBrightness)
    val finalAccent = accent.adjustBrightness(globalBrightness)
    val finalBackground = resolvedBackground.adjustBrightness(globalBrightness)
    val baseCardBackground = if (isDark) Color.White.copy(alpha = cardAlpha) else Color.Black.copy(alpha = cardAlpha)
    val finalCardBackground = baseCardBackground.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)

    val adjGrad1 = customGradientColor1.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)
    val adjGrad2 = customGradientColor2.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)
    val adjGrad3 = customGradientColor3.adjustBrightness(globalBrightness).adjustTransparency(globalTransparency)

    return NebulaColors(
        accent = finalAccent,
        background = finalBackground,
        surface = finalSurface,
        cardBackground = finalCardBackground,
        isMaterialYou = isMaterialYou,
        panelFill = finalPanelFill,
        controlFill = finalControlFill,
        softFill = finalSoftFill,
        panelBorder = finalPanelBorder,
        divider = finalDivider,
        onBackground = resolvedText,
        onSurface = resolvedText,
        textPrimary = resolvedText,
        textSecondary = resolvedText.copy(alpha = textSecondaryAlpha),
        textTertiary = resolvedText.copy(alpha = textTertiaryAlpha),
        glow = if (gradientEffectsEnabled) {
            when (customGradientCount) {
                3 -> adjGrad2.copy(alpha = 0.25f)
                2 -> adjGrad1.lerp(adjGrad2, 0.5f).copy(alpha = 0.25f)
                else -> finalAccent.copy(alpha = 0.25f)
            }
        } else Color.Transparent,
        statusConnected = Color(0xFF5DD9A1),
        statusDisconnected = Color(0xFF6B7280),
        statusError = Color(0xFFFF7A7A),
        statusConnecting = finalAccent,
        primaryGradientStart = if (gradientEffectsEnabled) {
            if (isCustomAccent) adjGrad1 else finalAccent.copy(alpha = 0.8f)
        } else {
            Color.Transparent
        },
        primaryGradientMiddle = if (gradientEffectsEnabled) {
            if (isCustomAccent) {
                when (customGradientCount) {
                    3 -> adjGrad2
                    2 -> adjGrad1.lerp(adjGrad2, 0.5f)
                    else -> adjGrad1
                }
            } else finalAccent.adjustBrightness(if (isDark) 0.86f else 0.94f)
        } else {
            Color.Transparent
        },
        primaryGradientEnd = if (gradientEffectsEnabled) {
            if (isCustomAccent) {
                when (customGradientCount) {
                    3 -> adjGrad3
                    2 -> adjGrad2
                    else -> adjGrad1
                }
            } else finalAccent.adjustBrightness(if (isDark) 0.72f else 0.88f)
        } else {
            Color.Transparent
        }
    )
}

internal fun Color.lerp(other: Color, fraction: Float): Color {
    return Color(
        red = red + (other.red - red) * fraction,
        green = green + (other.green - green) * fraction,
        blue = blue + (other.blue - blue) * fraction,
        alpha = alpha + (other.alpha - alpha) * fraction
    )
}

// ==================== ACCENT-SEEDED MATERIAL 3 SCHEME ====================
// The static preset ColorSchemes above only override primary/secondary/tertiary, so every
// *container* / *variant* / surfaceTint / surfaceContainer* role silently falls back to the
// Material 3 BASELINE palette — which is purple. In Material You element style the bottom bar
// (compositeOver primaryContainer), the nav pills (secondaryContainer) and the cards
// (primaryContainer / onPrimaryContainer / surfaceVariant / outlineVariant) paint straight from
// those roles, so they ignored the chosen accent and always looked purple. We regenerate the
// full accent role set from the resolved seed colour so the whole interface follows the accent.

private fun Color.relLum(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Hue-preserving tonal step: blend toward white/black to reach [target] perceived lightness (0..1). */
private fun Color.tone(target: Float): Color {
    val l = relLum()
    return if (target >= l) {
        val f = if (l >= 1f) 0f else (target - l) / (1f - l)
        lerp(Color.White, f.coerceIn(0f, 1f))
    } else {
        val f = if (l <= 0f) 0f else (l - target) / l
        lerp(Color.Black, f.coerceIn(0f, 1f))
    }
}

private fun Color.desaturate(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255f).toInt(), (green * 255f).toInt(), (blue * 255f).toInt(), hsv
    )
    hsv[1] = (hsv[1] * (1f - amount)).coerceIn(0f, 1f)
    return Color.hsv(hsv[0], hsv[1], hsv[2], alpha)
}

private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255f).toInt(), (green * 255f).toInt(), (blue * 255f).toInt(), hsv
    )
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color.hsv(hsv[0], hsv[1], hsv[2], alpha)
}

/**
 * Fill in a complete, accent-consistent set of Material 3 roles on top of [base] (which keeps the
 * preset's background/surface/onSurface darkness). [seed] is the resolved accent (preset, custom or
 * dynamic). For preset accents seed == base.primary, so primary is unchanged and Glass mode keeps
 * its look; for a custom accent this also re-seeds primary so it finally matches the picked colour.
 */
private fun buildAccentScheme(
    base: ColorScheme,
    seed: Color,
    isDark: Boolean,
    reseedPrimaries: Boolean
): ColorScheme {
    val accent = seed.copy(alpha = 1f)
    val secondary = accent.desaturate(0.45f)
    val tertiary = accent.shiftHue(40f).desaturate(0.20f)
    val neutralVariant = accent.desaturate(0.84f)

    fun tier(delta: Float): Color {
        val lifted = if (delta >= 0f) base.surface.lerp(Color.White, delta) else base.surface.lerp(Color.Black, -delta)
        return lifted.lerp(accent, 0.05f)
    }

    // For preset accents the curated primary/secondary/tertiary are kept as-is (seed == base.primary
    // anyway), so Glass mode is unchanged; only a custom accent re-seeds the main tones.
    val withContainers = if (isDark) {
        base.copy(
            primaryContainer = accent.tone(0.26f),
            onPrimaryContainer = accent.tone(0.92f),
            inversePrimary = accent.tone(0.40f),
            secondaryContainer = secondary.tone(0.28f),
            onSecondaryContainer = secondary.tone(0.92f),
            tertiaryContainer = tertiary.tone(0.28f),
            onTertiaryContainer = tertiary.tone(0.92f),
            surfaceVariant = neutralVariant.tone(0.22f),
            onSurfaceVariant = neutralVariant.tone(0.80f),
            outline = neutralVariant.tone(0.55f),
            outlineVariant = neutralVariant.tone(0.28f),
            surfaceTint = accent,
            surfaceDim = tier(-0.02f),
            surfaceBright = tier(0.14f),
            surfaceContainerLowest = tier(-0.04f),
            surfaceContainerLow = tier(0.01f),
            surfaceContainer = tier(0.03f),
            surfaceContainerHigh = tier(0.06f),
            surfaceContainerHighest = tier(0.10f)
        )
    } else {
        base.copy(
            primaryContainer = accent.tone(0.88f),
            onPrimaryContainer = accent.tone(0.16f),
            inversePrimary = accent.tone(0.78f),
            secondaryContainer = secondary.tone(0.88f),
            onSecondaryContainer = secondary.tone(0.14f),
            tertiaryContainer = tertiary.tone(0.88f),
            onTertiaryContainer = tertiary.tone(0.14f),
            surfaceVariant = neutralVariant.tone(0.90f),
            onSurfaceVariant = neutralVariant.tone(0.30f),
            outline = neutralVariant.tone(0.50f),
            outlineVariant = neutralVariant.tone(0.80f),
            surfaceTint = accent,
            surfaceDim = tier(-0.06f),
            surfaceBright = tier(0.0f),
            surfaceContainerLowest = tier(0.0f),
            surfaceContainerLow = tier(-0.01f),
            surfaceContainer = tier(-0.02f),
            surfaceContainerHigh = tier(-0.035f),
            surfaceContainerHighest = tier(-0.05f)
        )
    }

    if (!reseedPrimaries) return withContainers

    return if (isDark) {
        withContainers.copy(
            primary = accent,
            onPrimary = accent.tone(0.12f),
            secondary = secondary.tone(0.78f),
            onSecondary = secondary.tone(0.14f),
            tertiary = tertiary.tone(0.78f),
            onTertiary = tertiary.tone(0.14f)
        )
    } else {
        withContainers.copy(
            primary = accent,
            onPrimary = accent.tone(0.99f),
            secondary = secondary.tone(0.40f),
            onSecondary = secondary.tone(0.99f),
            tertiary = tertiary.tone(0.40f),
            onTertiary = tertiary.tone(0.99f)
        )
    }
}

/** Resolve the accent seed the same way getNebulaColors does (preset index / custom gradient blend). */
private fun resolveSeedAccent(
    effectiveThemeIndex: Int,
    isCustomAccent: Boolean,
    customGradientColor1: Color,
    customGradientColor2: Color,
    customGradientColor3: Color,
    customGradientCount: Int
): Color {
    if (isCustomAccent) {
        return when (customGradientCount) {
            3 -> customGradientColor1.lerp(customGradientColor2, 0.5f).lerp(customGradientColor3, 0.33f)
            2 -> customGradientColor1.lerp(customGradientColor2, 0.5f)
            else -> customGradientColor1
        }
    }
    val isDark = isDarkTheme(effectiveThemeIndex)
    val colorType = if (isDark) effectiveThemeIndex else effectiveThemeIndex - THEME_COLOR_COUNT
    return when (colorType) {
        0 -> AccentPurple
        1 -> AccentBlue
        2 -> AccentGreen
        3 -> AccentRed
        4 -> AccentOrange
        5 -> AccentPink
        6 -> AccentCyan
        7 -> AccentLime
        8 -> AccentSilver
        else -> AccentBlue
    }
}

@Composable
fun NebulaGuardTheme(
    themeIndex: Int = DEFAULT_COLOR_THEME_INDEX,
    isCustomAccent: Boolean = false,
    customAccentColor: Color = Color(0xFF7C5DFA),
    gradientEffectsEnabled: Boolean = true,
    customGradientColor1: Color = Color(0xFF7C5DFA),
    customGradientColor2: Color = Color(0xFF00E5B0),
    customGradientColor3: Color = Color(0xFF00D2FF),
    customGradientCount: Int = 1,
    useDynamicColor: Boolean = false,
    backgroundStyle: Int = 0,
    elementStyle: Int = 0,
    backgroundAnimationEnabled: Boolean = true,
    highContrastUi: Boolean = false,
    reducedTransparency: Boolean = false,
    pureBlackMode: Boolean = false,
    textScale: Float = 1f,
    globalBrightness: Float = 1.0f,
    globalTransparency: Float = 0.0f,
    globalBlur: Float = 25.0f,
    globalCorners: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColorAvailable = useDynamicColor && AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.S
    val effectiveThemeIndex = if (useDynamicColor && !dynamicColorAvailable) {
        if (isDarkTheme(themeIndex)) DEFAULT_COLOR_THEME_INDEX else DEFAULT_COLOR_THEME_INDEX + THEME_COLOR_COUNT
    } else {
        themeIndex
    }
    val colorScheme = when {
        dynamicColorAvailable -> {
            // Wallpaper-based scheme is already a complete, correct tonal palette — use as-is.
            if (isDarkTheme(themeIndex)) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            // Re-seed all accent/container/variant roles from the chosen accent so Material You
            // element style stops falling back to the baseline purple palette.
            val seed = resolveSeedAccent(
                effectiveThemeIndex = effectiveThemeIndex,
                isCustomAccent = isCustomAccent,
                customGradientColor1 = customGradientColor1,
                customGradientColor2 = customGradientColor2,
                customGradientColor3 = customGradientColor3,
                customGradientCount = customGradientCount
            )
            buildAccentScheme(
                base = getColorScheme(effectiveThemeIndex),
                seed = seed,
                isDark = isDarkTheme(effectiveThemeIndex),
                reseedPrimaries = isCustomAccent
            )
        }
    }

    val nebulaColors = getNebulaColors(
        themeIndex = effectiveThemeIndex,
        isCustomAccent = isCustomAccent,
        customAccentColor = customAccentColor,
        gradientEffectsEnabled = gradientEffectsEnabled,
        customGradientColor1 = customGradientColor1,
        customGradientColor2 = customGradientColor2,
        customGradientColor3 = customGradientColor3,
        customGradientCount = customGradientCount,
        useDynamicColor = dynamicColorAvailable,
        highContrastUi = highContrastUi,
        reducedTransparency = reducedTransparency,
        pureBlackMode = pureBlackMode,
        elementStyle = elementStyle,
        colorScheme = colorScheme,
        globalBrightness = globalBrightness,
        globalTransparency = globalTransparency
    )
    val darkTheme = isDarkTheme(effectiveThemeIndex)
    val backgroundMode = when (backgroundStyle) {
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
        else -> BackgroundStyleMode.MORPHISM
    }
    val elementMode = when (elementStyle) {
        1 -> ElementStyleMode.MATERIAL3
        2 -> ElementStyleMode.NOTHING_DOTS
        3 -> ElementStyleMode.OUTLINED
        4 -> ElementStyleMode.SOFT_NEO
        else -> ElementStyleMode.MORPHISM
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalNebulaColors provides nebulaColors,
        LocalBackgroundStyleMode provides backgroundMode,
        LocalElementStyleMode provides elementMode,
        LocalBackgroundAnimationEnabled provides backgroundAnimationEnabled,
        LocalReducedTransparencyEnabled provides reducedTransparency,
        LocalGlobalBlurRadius provides globalBlur,
        LocalGlobalCornerRadius provides globalCorners
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography(textScale),
            content = content
        )
    }
}
