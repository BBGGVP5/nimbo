package com.danila.nimbo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Provider is an explicit overlay; disabling it restores the untouched personal choice. */
internal fun resolveUniversalAccent(
    themeIndex: Int,
    isCustomAccent: Boolean = false,
    customAccentColor: Color = Color(0xFF7C5DFA),
    dynamicAccent: Color? = null,
    useSubscriptionTheme: Boolean = false,
    subscriptionThemeSpec: String? = null
): Color {
    val provider = if (useSubscriptionTheme) subscriptionAccent(subscriptionThemeSpec) else null
    return (provider ?: dynamicAccent ?: if (isCustomAccent) customAccentColor else when (themeIndex.mod(9)) {
        0 -> AccentPurple
        1 -> AccentBlue
        2 -> AccentGreen
        3 -> AccentRed
        4 -> AccentOrange
        5 -> AccentPink
        6 -> AccentCyan
        7 -> AccentLime
        else -> if (isDarkTheme(themeIndex)) Color(0xFFE8E8E8) else Color(0xFF202020)
    }).copy(alpha = 1f)
}

/** Only the first accent token belongs to the provider's single-color theme. */
internal fun subscriptionAccent(spec: String?): Color? {
    val hex = spec?.split(',')?.map(String::trim)?.firstOrNull { it.startsWith('#') }
        ?.removePrefix("#") ?: return null
    val rgb = when {
        hex.matches(Regex("[0-9a-fA-F]{6}")) -> hex.toLong(16)
        hex.matches(Regex("[0-9a-fA-F]{8}")) -> hex.toLong(16) and 0xFFFFFF
        else -> return null
    }
    return Color((0xFF000000L or rgb).toInt())
}

/** Universal-v2 neutral roles, including the container roles used by native dialogs. */
internal fun universalColorScheme(dark: Boolean, selectedAccent: Color? = null): ColorScheme {
    val canvas = Color(if (dark) 0xFF121212 else 0xFFF5F4F1)
    val panel = Color(if (dark) 0xFF1C1C1C else 0xFFFFFFFF)
    val raised = Color(if (dark) 0xFF262626 else 0xFFEFEFEC)
    val ink = Color(if (dark) 0xFFF1F1F1 else 0xFF202020)
    val secondary = Color(if (dark) 0xFFA0A0A0 else 0xFF676767)
    val border = Color(if (dark) 0xFF303030 else 0xFFDEDEDB)
    val accent = selectedAccent?.copy(alpha = 1f) ?: Color(if (dark) 0xFFE8E8E8 else 0xFF202020)
    val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent, onPrimary = onAccent, primaryContainer = raised, onPrimaryContainer = ink,
        secondary = ink, onSecondary = canvas, secondaryContainer = raised, onSecondaryContainer = ink,
        tertiary = secondary, onTertiary = canvas, tertiaryContainer = raised, onTertiaryContainer = ink,
        background = canvas, onBackground = ink, surface = panel, onSurface = ink,
        surfaceVariant = raised, onSurfaceVariant = secondary, outline = secondary, outlineVariant = border,
        surfaceTint = Color.Transparent, surfaceDim = canvas, surfaceBright = raised,
        surfaceContainerLowest = canvas, surfaceContainerLow = panel, surfaceContainer = panel,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
        error = Color(if (dark) 0xFFFF9292 else 0xFFAF2424),
        onError = Color(if (dark) 0xFF350606 else 0xFFFFFFFF),
        errorContainer = Color(if (dark) 0xFF402323 else 0xFFFFE8E8),
        onErrorContainer = Color(if (dark) 0xFFFFCACA else 0xFF711616)
    )
}
