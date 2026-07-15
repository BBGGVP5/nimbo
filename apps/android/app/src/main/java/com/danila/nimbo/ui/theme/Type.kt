package com.danila.nimbo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
