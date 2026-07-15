package com.danila.nimbo.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(nebulaColors.accent.copy(alpha = 0.25f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = nebulaColors.textSecondary
            )
        }

        Spacer(Modifier.height(6.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = when (elementStyle) {
                ElementStyleMode.MORPHISM -> RoundedCornerShape(18.dp)
                ElementStyleMode.MATERIAL3 -> RoundedCornerShape(14.dp)
                ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(12.dp)
                ElementStyleMode.OUTLINED -> RoundedCornerShape(10.dp)
                ElementStyleMode.SOFT_NEO -> RoundedCornerShape(20.dp)
            },
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(settingsIconBrush(nebulaColors, elementStyle))
                    .border(1.dp, nebulaColors.accent.copy(alpha = 0.24f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) nebulaColors.textPrimary else nebulaColors.textTertiary,
                    maxLines = 2,  // Разрешаем 2 строки для длинных заголовков
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))  // Небольшой отступ между заголовком и подзаголовком
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) nebulaColors.textTertiary else nebulaColors.textTertiary.copy(alpha = 0.8f),
                    maxLines = 2,  // Разрешаем 2 строки для длинных подзаголовков
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = nebulaColors.accent,
                checkedTrackColor = when (elementStyle) {
                    ElementStyleMode.MATERIAL3 -> nebulaColors.accent.copy(alpha = 0.45f)
                    ElementStyleMode.NOTHING_DOTS -> nebulaColors.accent.copy(alpha = 0.25f)
                    ElementStyleMode.OUTLINED -> Color.Transparent
                    ElementStyleMode.SOFT_NEO -> nebulaColors.accent.copy(alpha = 0.34f)
                    else -> nebulaColors.accent.copy(alpha = 0.3f)
                },
                uncheckedThumbColor = if (elementStyle == ElementStyleMode.OUTLINED) nebulaColors.onSurface.copy(alpha = 0.55f) else nebulaColors.textTertiary,
                uncheckedTrackColor = when (elementStyle) {
                    ElementStyleMode.OUTLINED -> Color.Transparent
                    ElementStyleMode.SOFT_NEO -> nebulaColors.onSurface.copy(alpha = 0.14f)
                    else -> nebulaColors.textTertiary.copy(alpha = 0.2f)
                },
                checkedBorderColor = if (elementStyle == ElementStyleMode.OUTLINED) nebulaColors.accent.copy(alpha = 0.5f) else Color.Transparent,
                uncheckedBorderColor = if (elementStyle == ElementStyleMode.OUTLINED) nebulaColors.onSurface.copy(alpha = 0.3f) else Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(settingsIconBrush(nebulaColors, elementStyle))
                .border(1.dp, nebulaColors.accent.copy(alpha = 0.24f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier.size(21.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = nebulaColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = nebulaColors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(settingsIconBrush(nebulaColors, elementStyle))
                    .border(1.dp, nebulaColors.accent.copy(alpha = 0.24f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = nebulaColors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Перейти",
            tint = nebulaColors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsLinkItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(settingsIconBrush(nebulaColors, elementStyle))
                    .border(1.dp, nebulaColors.accent.copy(alpha = 0.24f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = nebulaColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Открыть",
            tint = nebulaColors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun NebulaInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    singleLine: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val shape: Shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(settingsRowBackground(nebulaColors, elementStyle))
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                    Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.8.dp, alpha = 0.11f)
                } else Modifier
            )
            .padding(2.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = singleLine,
            label = { Text(label) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = nebulaColors.accent.copy(alpha = 0.55f),
                unfocusedBorderColor = nebulaColors.textTertiary.copy(alpha = 0.25f),
                disabledBorderColor = nebulaColors.textTertiary.copy(alpha = 0.2f),
                focusedTextColor = nebulaColors.textPrimary,
                unfocusedTextColor = nebulaColors.textPrimary,
                disabledTextColor = nebulaColors.textSecondary,
                focusedLabelColor = nebulaColors.accent,
                unfocusedLabelColor = nebulaColors.textSecondary,
                focusedLeadingIconColor = nebulaColors.accent,
                unfocusedLeadingIconColor = nebulaColors.textSecondary,
                focusedTrailingIconColor = nebulaColors.accent,
                unfocusedTrailingIconColor = nebulaColors.textSecondary,
                cursorColor = nebulaColors.accent
            )
        )
    }
}

private fun settingsIconBrush(
    nebulaColors: com.danila.nimbo.ui.theme.NebulaColors,
    style: ElementStyleMode
): Brush = when (style) {
    ElementStyleMode.MORPHISM -> Brush.radialGradient(
        colors = listOf(nebulaColors.accent.copy(alpha = 0.15f), Color.Transparent)
    )

    ElementStyleMode.MATERIAL3 -> Brush.linearGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.22f),
            nebulaColors.accent.copy(alpha = 0.08f)
        )
    )

    ElementStyleMode.NOTHING_DOTS -> Brush.linearGradient(
        colors = listOf(
            nebulaColors.onSurface.copy(alpha = 0.18f),
            nebulaColors.accent.copy(alpha = 0.12f)
        )
    )

    ElementStyleMode.OUTLINED -> Brush.linearGradient(
        colors = listOf(
            nebulaColors.onSurface.copy(alpha = 0.12f),
            Color.Transparent
        )
    )

    ElementStyleMode.SOFT_NEO -> Brush.radialGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.18f),
            Color.Transparent
        )
    )
}

private fun settingsRowBackground(
    nebulaColors: com.danila.nimbo.ui.theme.NebulaColors,
    style: ElementStyleMode
): Brush = when (style) {
    ElementStyleMode.MORPHISM -> Brush.linearGradient(
        listOf(Color.Transparent, Color.Transparent)
    )

    ElementStyleMode.MATERIAL3 -> Brush.linearGradient(
        listOf(
            nebulaColors.surface.copy(alpha = 0.72f),
            nebulaColors.surface.copy(alpha = 0.58f)
        )
    )

    ElementStyleMode.NOTHING_DOTS -> Brush.linearGradient(
        listOf(
            nebulaColors.surface.copy(alpha = 0.66f),
            nebulaColors.surface.copy(alpha = 0.52f)
        )
    )

    ElementStyleMode.OUTLINED -> Brush.linearGradient(
        listOf(
            Color.Transparent,
            Color.Transparent
        )
    )

    ElementStyleMode.SOFT_NEO -> Brush.linearGradient(
        listOf(
            nebulaColors.onSurface.copy(alpha = 0.1f),
            nebulaColors.surface.copy(alpha = 0.7f),
            nebulaColors.onSurface.copy(alpha = 0.06f)
        )
    )
}
