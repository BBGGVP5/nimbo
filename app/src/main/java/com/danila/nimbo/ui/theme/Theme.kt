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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.danila.nimbo.ui.components.LiquidGlassTilt
import com.danila.nimbo.ui.components.rememberLiquidGlassTilt



/**
 * Индексы палитры сохраняются как есть; неизвестное значение падает в `THEME`,
 * чтобы фон никогда не оставался без цвета.
 */
/**
 * Форма и палитра фона переехали в общий модуль: iOS обязан рисовать ровно тот
 * же фон, что и Android, а не свою копию. Псевдонимы оставлены, чтобы весь
 * существующий код продолжал ссылаться на прежние имена.
 */
typealias BackgroundStyleMode = com.danila.nimbo.shared.ui.BackgroundStyleMode
typealias BackgroundPaletteMode = com.danila.nimbo.shared.ui.BackgroundPaletteMode

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

/**
 * Палитра, которую по умолчанию подставляем пользователям, обновившимся со
 * старой сборки: там цвет фона был «зашит» в стиль, и его нужно сохранить,
 * иначе после обновления фон внезапно поменяет цвет.
 */
fun legacyBackgroundPaletteForStyle(styleIndex: Int): Int = when (styleIndex) {
    3 -> 1   // Aurora
    8 -> 2   // Cyberpunk
    9 -> 3   // Deep space
    10 -> 4  // Fire
    11 -> 5  // Lava
    12 -> 6  // Neon
    13 -> 7  // Nordic
    14 -> 8  // Blossom
    else -> 0
}

/**
 * Keeps the persisted background selector compatible while giving the UI a
 * single, testable conversion point. New styles are appended so existing
 * installations retain their selected background after an update.
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

val LocalBackgroundStyleMode = staticCompositionLocalOf { BackgroundStyleMode.MORPHISM }
val LocalBackgroundPaletteMode = staticCompositionLocalOf { BackgroundPaletteMode.THEME }
val LocalElementStyleMode = staticCompositionLocalOf { ElementStyleMode.LIQUID_GLASS }
val LocalBackgroundAnimationEnabled = staticCompositionLocalOf { true }
val LocalReducedTransparencyEnabled = staticCompositionLocalOf { false }
val LocalGlobalBlurRadius = compositionLocalOf { 25.0f }
val LocalGlobalCornerRadius = compositionLocalOf { 1.0f }
val LocalLiquidRefractionEnabled = staticCompositionLocalOf { true }
val LocalLiquidGlassTilt = compositionLocalOf { LiquidGlassTilt.Zero }


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
    8 -> universalColorScheme(true)
    9 -> PurpleLightColorScheme
    10 -> BlueLightColorScheme
    11 -> GreenLightColorScheme
    12 -> RedLightColorScheme
    13 -> OrangeLightColorScheme
    14 -> PinkLightColorScheme
    15 -> CyanLightColorScheme
    16 -> LimeLightColorScheme
    17 -> universalColorScheme(false)
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
    gradientEffectsEnabled: Boolean = false,
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
    // Universal layout never supplies an accent of its own. Legacy styles, gradients and
    // brightness controls must not silently replace the user's saved single accent.
    val finalAccent = if (useDynamicColor) resolvedColorScheme.primary else resolveUniversalAccent(
        themeIndex = themeIndex,
        isCustomAccent = isCustomAccent,
        customAccentColor = customAccentColor
    )
    val neutral = universalColorScheme(isDark)
    val ink = neutral.onSurface
    return NebulaColors(
        accent = finalAccent, background = if (pureBlackMode && isDark) Color.Black else neutral.background,
        surface = neutral.surface, cardBackground = neutral.surface,
        onBackground = ink, onSurface = ink, textPrimary = ink,
        textSecondary = if (highContrastUi) ink else neutral.onSurfaceVariant,
        textTertiary = if (highContrastUi) ink else neutral.onSurfaceVariant,
        glow = Color.Transparent, statusConnected = if (isDark) Color(0xFF79C9A3) else Color(0xFF26714D),
        statusError = neutral.error, statusConnecting = finalAccent,
        primaryGradientStart = Color.Transparent, primaryGradientMiddle = Color.Transparent,
        primaryGradientEnd = Color.Transparent, isLiquidGlass = false, isMaterialYou = false, isManga = false,
        panelFill = neutral.surface, controlFill = neutral.surfaceContainerHigh,
        softFill = neutral.surfaceContainerHigh, panelBorder = neutral.outlineVariant, divider = neutral.outlineVariant
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

/** Reactive, neutral layout with one resolved accent for Material and Nimbo controls. */
@Composable
fun NebulaGuardTheme(
    themeIndex: Int = DEFAULT_COLOR_THEME_INDEX,
    isCustomAccent: Boolean = false,
    customAccentColor: Color = Color(0xFF7C5DFA),
    gradientEffectsEnabled: Boolean = false,
    customGradientColor1: Color = Color(0xFF7C5DFA),
    customGradientColor2: Color = Color(0xFF00E5B0),
    customGradientColor3: Color = Color(0xFF00D2FF),
    customGradientCount: Int = 1,
    useDynamicColor: Boolean = false,
    backgroundStyle: Int = 15,
    backgroundPalette: Int = 0,
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
    liquidRefractionEnabled: Boolean = true,
    useSubscriptionTheme: Boolean = false,
    subscriptionThemeSpec: String? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColorAvailable = useDynamicColor && AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.S
    val effectiveThemeIndex = themeIndex
    val darkTheme = isDarkTheme(effectiveThemeIndex)
    val dynamicAccent = if (dynamicColorAvailable) {
        (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else null
    val accent = resolveUniversalAccent(
        themeIndex = effectiveThemeIndex,
        isCustomAccent = isCustomAccent,
        customAccentColor = customAccentColor,
        dynamicAccent = dynamicAccent,
        useSubscriptionTheme = useSubscriptionTheme,
        subscriptionThemeSpec = subscriptionThemeSpec
    )
    val colorScheme = universalColorScheme(darkTheme, accent).let {
        if (pureBlackMode && darkTheme) it.copy(background = Color.Black) else it
    }

    val nebulaColors = getNebulaColors(
        themeIndex = effectiveThemeIndex,
        isCustomAccent = true,
        customAccentColor = accent,
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
    val backgroundMode = BackgroundStyleMode.NONE
    val backgroundPaletteMode = backgroundPaletteModeForIndex(backgroundPalette)
    val elementMode = ElementStyleMode.SIGNAL
    val refractionEffectsEnabled =
        elementMode == ElementStyleMode.LIQUID_GLASS &&
            liquidRefractionEnabled &&
            !reducedTransparency
    val liquidGlassTilt = rememberLiquidGlassTilt(
        enabled = false
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
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
        LocalBackgroundPaletteMode provides backgroundPaletteMode,
        LocalElementStyleMode provides elementMode,
        LocalBackgroundAnimationEnabled provides backgroundAnimationEnabled,
        LocalReducedTransparencyEnabled provides reducedTransparency,
        LocalGlobalBlurRadius provides 0f,
        // Manga входит в сам множитель: половина экранов считает скругления
        // прямым умножением на него, минуя scaleRoundedCornerShape, и там
        // стиль до углов не доходил.
        LocalGlobalCornerRadius provides
            1f,
        LocalLiquidRefractionEnabled provides refractionEffectsEnabled,
        LocalLiquidGlassTilt provides liquidGlassTilt
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = universalTypography(textScale),
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(20.dp)),
            content = content
        )
    }
}
