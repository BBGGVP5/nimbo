package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current

    val shape = when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape(20.dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape(16.dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(14.dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape(12.dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape(22.dp)
    }

    val backgroundBrush = when (elementStyle) {
        ElementStyleMode.MORPHISM -> Brush.linearGradient(
            listOf(
                nebulaColors.textPrimary.copy(alpha = 0.08f),
                nebulaColors.textPrimary.copy(alpha = 0.02f)
            )
        )

        ElementStyleMode.MATERIAL3 -> Brush.verticalGradient(
            listOf(
                nebulaColors.surface.copy(alpha = 0.95f),
                nebulaColors.surface.copy(alpha = 0.86f)
            )
        )

        ElementStyleMode.NOTHING_DOTS -> Brush.linearGradient(
            listOf(
                nebulaColors.surface.copy(alpha = 0.93f),
                nebulaColors.surface.copy(alpha = 0.85f)
            )
        )

        ElementStyleMode.OUTLINED -> Brush.verticalGradient(
            listOf(
                nebulaColors.surface.copy(alpha = 0.6f),
                nebulaColors.surface.copy(alpha = 0.5f)
            )
        )

        ElementStyleMode.SOFT_NEO -> Brush.linearGradient(
            listOf(
                nebulaColors.onSurface.copy(alpha = 0.11f),
                nebulaColors.surface.copy(alpha = 0.8f),
                nebulaColors.onSurface.copy(alpha = 0.06f)
            )
        )
    }

    val borderColor = when (elementStyle) {
        ElementStyleMode.MORPHISM -> nebulaColors.textPrimary.copy(alpha = 0.12f)
        ElementStyleMode.MATERIAL3 -> nebulaColors.onSurface.copy(alpha = 0.16f)
        ElementStyleMode.NOTHING_DOTS -> nebulaColors.accent.copy(alpha = 0.18f)
        ElementStyleMode.OUTLINED -> nebulaColors.onSurface.copy(alpha = 0.22f)
        ElementStyleMode.SOFT_NEO -> nebulaColors.accent.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundBrush)
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                    Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.9.dp, alpha = 0.13f)
                } else Modifier
            )
            .border(
                1.dp,
                borderColor,
                shape
            ),
        content = content
    )
}
