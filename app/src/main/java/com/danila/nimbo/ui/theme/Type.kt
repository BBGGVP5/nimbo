package com.danila.nimbo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import com.danila.nimbo.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private fun TextStyle.scaledBy(scale: Float): TextStyle {
    val safeScale = scale.coerceIn(0.85f, 1.25f)
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * safeScale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * safeScale else lineHeight
    )
}

/**
 * Набор Signal: подписи и мелкие ярлыки идут моноширинным шрифтом с
 * разрядкой — так же, как кикеры в десктопной приборной панели. Основной
 * текст остаётся прежним, иначе экраны станут нечитаемыми.
 */
fun signalTypography(scale: Float): Typography {
    val base = scaledTypography(scale)
    fun TextStyle.asKicker(extra: Float) = copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (letterSpacing.takeIf { it.isSpecified }?.value ?: 0f).plus(extra).sp
    )
    return base.copy(
        labelSmall = base.labelSmall.asKicker(0.9f),
        labelMedium = base.labelMedium.asKicker(0.7f),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    )
}

fun scaledTypography(scale: Float): Typography = Typography(
    displayLarge = Typography.displayLarge.scaledBy(scale),
    displayMedium = Typography.displayMedium.scaledBy(scale),
    displaySmall = Typography.displaySmall.scaledBy(scale),
    headlineLarge = Typography.headlineLarge.scaledBy(scale),
    headlineMedium = Typography.headlineMedium.scaledBy(scale),
    headlineSmall = Typography.headlineSmall.scaledBy(scale),
    titleLarge = Typography.titleLarge.scaledBy(scale),
    titleMedium = Typography.titleMedium.scaledBy(scale),
    titleSmall = Typography.titleSmall.scaledBy(scale),
    bodyLarge = Typography.bodyLarge.scaledBy(scale),
    bodyMedium = Typography.bodyMedium.scaledBy(scale),
    bodySmall = Typography.bodySmall.scaledBy(scale),
    labelLarge = Typography.labelLarge.scaledBy(scale),
    labelMedium = Typography.labelMedium.scaledBy(scale),
    labelSmall = Typography.labelSmall.scaledBy(scale)
)


// Bundled with OFL licenses. No runtime font download and no locale-dependent fallback for Cyrillic.
val NimboBodyFont = FontFamily(
    Font(R.font.golostext_normal, FontWeight.Normal),
    Font(R.font.golostext_medium, FontWeight.Medium),
    Font(R.font.golostext_semibold, FontWeight.SemiBold),
    Font(R.font.golostext_bold, FontWeight.Bold)
)
val NimboHeadingFont = FontFamily(
    Font(R.font.manrope_normal, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

/** Preview hierarchy, scaled by the app preference and then by Android's font scale. */
fun universalTypography(scale: Float): Typography {
    fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = NimboBodyFont, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = 0.sp).scaledBy(scale)
    fun heading(size: Int, line: Int) = TextStyle(fontFamily = NimboHeadingFont,
        fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = (-0.7).sp).scaledBy(scale)
    return Typography(
        displayLarge = heading(40, 48), displayMedium = heading(34, 42), displaySmall = heading(30, 38),
        headlineLarge = heading(28, 36), headlineMedium = heading(25, 33), headlineSmall = heading(22, 30),
        titleLarge = heading(20, 28), titleMedium = body(14, 21, FontWeight.Medium),
        titleSmall = body(13, 19, FontWeight.Medium), bodyLarge = body(14, 21),
        bodyMedium = body(12, 19), bodySmall = body(11, 17), labelLarge = body(12, 18, FontWeight.Medium),
        labelMedium = body(11, 16), labelSmall = body(10, 15)
    )
}
