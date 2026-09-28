package com.danila.nimbo.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
internal actual fun nimboPlatformFontFamily(heading: Boolean): FontFamily =
    if (heading) NimboResourceTypography.heading else NimboResourceTypography.body
