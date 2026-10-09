package com.danila.nimbo.ui.navigation

/** Keep labels readable instead of giving one tab a taller, wrapped caption. */
internal fun navigationColumnCount(availableWidthDp: Float, fontScale: Float): Int =
    if (availableWidthDp < 300f * fontScale.coerceAtLeast(1f)) 2 else 4
