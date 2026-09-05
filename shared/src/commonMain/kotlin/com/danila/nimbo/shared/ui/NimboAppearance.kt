package com.danila.nimbo.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Same ranges/defaults as Android's appearance controls. */
data class NimboAppearance(
    val themeMode: String = "system",
    val accentHex: String = "75A7FF",
    val brightness: Float = 1f,
    val transparency: Float = 0f,
    val corners: Float = 1f,
    val textScale: Float = 1f,
    val refraction: Boolean = true,
    val haptics: Boolean = true
) {
    fun normalized(): NimboAppearance = copy(
        themeMode = themeMode.takeIf { it in listOf("system", "light", "dark", "oled") } ?: "system",
        accentHex = accentHex.removePrefix("#").uppercase().takeIf {
            it.length == 6 && it.toLongOrNull(16) != null
        } ?: "75A7FF",
        brightness = brightness.bounded(0.5f, 2f, 1f),
        transparency = transparency.bounded(0f, 1f, 0f),
        corners = corners.bounded(0.25f, 2f, 1f),
        textScale = textScale.bounded(0.85f, 1.25f, 1f)
    )

    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        "light" -> false
        "dark", "oled" -> true
        else -> systemDark
    }
}

private fun Float.bounded(min: Float, max: Float, default: Float) =
    if (isFinite()) coerceIn(min, max) else default

internal val LocalNimboAppearance = staticCompositionLocalOf { NimboAppearance() }
internal val LocalNimboDark = staticCompositionLocalOf { true }

internal data class NimboColors(
    val background: Color, val backgroundDeep: Color,
    val surface: Color, val surfaceStrong: Color,
    val control: Color, val soft: Color, val border: Color,
    val accent: Color, val text: Color, val green: Color, val amber: Color, val red: Color,
    val paper: Color, val paperDeep: Color, val ink: Color
)

internal fun nimboColors(appearance: NimboAppearance, systemDark: Boolean): NimboColors {
    val settings = appearance.normalized()
    val dark = settings.isDark(systemDark)
    fun panel(color: Color): Color = Color(
        (color.red * settings.brightness).coerceIn(0f, 1f),
        (color.green * settings.brightness).coerceIn(0f, 1f),
        (color.blue * settings.brightness).coerceIn(0f, 1f), color.alpha
    )
    val accent = Color(0xFF000000 or settings.accentHex.toLong(16))
    return NimboColors(
        background = if (settings.themeMode == "oled") Color.Black else if (dark) Color(0xFF091321) else Color(0xFFF2F5FC),
        backgroundDeep = if (settings.themeMode == "oled") Color.Black else if (dark) Color(0xFF080F1C) else Color(0xFFE6ECF7),
        surface = panel(if (dark) Color(0xFF101D31) else Color(0xFFF8FAFF)),
        surfaceStrong = panel(if (dark) Color(0xFF14243A) else Color(0xFFE5ECF8)),
        control = if (dark) Color(0x09FFFFFF) else Color(0x0F10213C),
        soft = if (dark) Color(0x14FFFFFF) else Color(0x1410213C),
        border = if (dark) Color(0x24FFFFFF) else Color(0x2610213C),
        accent = accent,
        text = if (dark) Color(0xFFEAEBF2) else Color(0xFF17243A),
        green = if (dark) Color(0xFF5DD9A1) else Color(0xFF12714F),
        amber = if (dark) Color(0xFFE2A75F) else Color(0xFF925406),
        red = if (dark) Color(0xFFFF7B7B) else Color(0xFFB92E40),
        paper = panel(if (dark) Color(0xFF1B1814) else Color(0xFFFBF7EC)),
        paperDeep = if (dark) Color(0xFF15130F) else Color(0xFFF3EDDD),
        ink = if (dark) Color(0xFFF4EEDF) else Color(0xFF14120E)
    )
}

internal val LocalNimboColors = staticCompositionLocalOf { nimboColors(NimboAppearance(), true) }
