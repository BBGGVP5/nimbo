package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun GlassHeader(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val shape = when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape(22.dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape(16.dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(14.dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape(12.dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape(20.dp)
    }
    val headerBackground = when (elementStyle) {
        ElementStyleMode.MORPHISM -> Brush.linearGradient(
            listOf(
                nebulaColors.onSurface.copy(alpha = 0.12f),
                nebulaColors.onSurface.copy(alpha = 0.04f)
            )
        )

        ElementStyleMode.MATERIAL3 -> Brush.verticalGradient(
            listOf(
                nebulaColors.surface.copy(alpha = 0.95f),
                nebulaColors.surface.copy(alpha = 0.82f)
            )
        )

        ElementStyleMode.NOTHING_DOTS -> Brush.linearGradient(
            listOf(
                nebulaColors.surface.copy(alpha = 0.92f),
                nebulaColors.surface.copy(alpha = 0.78f)
            )
        )

        ElementStyleMode.OUTLINED -> Brush.verticalGradient(
            listOf(
                nebulaColors.surface.copy(alpha = 0.62f),
                nebulaColors.surface.copy(alpha = 0.5f)
            )
        )

        ElementStyleMode.SOFT_NEO -> Brush.linearGradient(
            listOf(
                nebulaColors.onSurface.copy(alpha = 0.11f),
                nebulaColors.surface.copy(alpha = 0.82f),
                nebulaColors.onSurface.copy(alpha = 0.07f)
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 6.dp)
            .statusBarsPadding()
            .clip(shape)
            .background(headerBackground)
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                    Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 11.dp, radius = 0.8.dp, alpha = 0.14f)
                } else Modifier
            ),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // glow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            listOf(iconColor.copy(alpha = 0.10f), Color.Transparent),
                            radius = 800f
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // ← назад
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (elementStyle == ElementStyleMode.MATERIAL3) {
                                    Brush.linearGradient(
                                        listOf(
                                            nebulaColors.accent.copy(alpha = 0.14f),
                                            nebulaColors.accent.copy(alpha = 0.05f)
                                        )
                                    )
                                } else {
                                    Brush.radialGradient(
                                        listOf(nebulaColors.textPrimary.copy(0.15f), Color.Transparent)
                                    )
                                }
                            )
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                            tint = nebulaColors.textPrimary
                        )
                    }

                    Spacer(Modifier.width(12.dp))
                }

                // 🧠 контент
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(iconColor.copy(alpha = 0.25f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = nebulaColors.textPrimary,
                            maxLines = 1
                        )

                        subtitle?.let {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = nebulaColors.accent.copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, nebulaColors.accent.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = nebulaColors.accent,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

