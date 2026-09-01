package com.danila.nimbo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

/**
 * Общий геометрический контракт Manga. Экранные компоненты не должны
 * самостоятельно оставлять стеклянные радиусы 14–26 dp.
 */
@Composable
fun nimboControlShape(
    defaultRadius: Dp,
    mangaRadius: Dp = 3.dp
): RoundedCornerShape = RoundedCornerShape(
    if (LocalElementStyleMode.current == ElementStyleMode.MANGA) mangaRadius else defaultRadius
)

@Composable
fun nimboControlBorderColor(
    default: Color,
    selected: Boolean = false
): Color {
    val colors = LocalNebulaColors.current
    return if (LocalElementStyleMode.current == ElementStyleMode.MANGA) {
        if (selected) colors.accent else colors.panelBorder
    } else default
}

@Composable
fun nimboControlBorderWidth(
    default: Dp = 1.dp,
    selected: Boolean = false
): Dp = if (LocalElementStyleMode.current == ElementStyleMode.MANGA) {
    if (selected) 2.dp else 1.5.dp
} else default

@Composable
fun nimboControlContainer(
    default: Color,
    selected: Boolean = false
): Color {
    val colors = LocalNebulaColors.current
    return if (LocalElementStyleMode.current == ElementStyleMode.MANGA) {
        if (selected) colors.accent.copy(alpha = 0.14f) else colors.controlFill
    } else default
}

/**
 * Material Switch не меняет форму трека. Для Manga рисуем компактный
 * прямоугольный переключатель с чернильным контуром; остальные темы получают
 * стандартный Material-компонент и сохраняют прежнее поведение.
 */
@Composable
fun NimboStyleSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedTrackColor: Color = LocalNebulaColors.current.accent,
    checkedThumbColor: Color = LocalNebulaColors.current.textPrimary,
    uncheckedTrackColor: Color = LocalNebulaColors.current.controlFill,
    uncheckedThumbColor: Color = LocalNebulaColors.current.textSecondary,
    uncheckedBorderColor: Color = LocalNebulaColors.current.panelBorder
) {
    val style = LocalElementStyleMode.current
    if (style != ElementStyleMode.MANGA) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedTrackColor = checkedTrackColor,
                checkedThumbColor = checkedThumbColor,
                uncheckedTrackColor = uncheckedTrackColor,
                uncheckedThumbColor = uncheckedThumbColor,
                uncheckedBorderColor = uncheckedBorderColor
            )
        )
        return
    }

    val colors = LocalNebulaColors.current
    val trackShape: Shape = RoundedCornerShape(2.dp)
    val thumbShape: Shape = RoundedCornerShape(1.dp)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        label = "manga_switch_thumb"
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(trackShape)
            .background(
                when {
                    !enabled -> colors.controlFill.copy(alpha = 0.55f)
                    checked -> checkedTrackColor.copy(alpha = 0.34f)
                    else -> uncheckedTrackColor
                }
            )
            .border(
                width = 2.dp,
                color = when {
                    !enabled -> colors.panelBorder.copy(alpha = 0.32f)
                    checked -> colors.accent
                    else -> colors.panelBorder
                },
                shape = trackShape
            )
            .semantics { role = Role.Switch }
            .clickable(
                enabled = enabled && onCheckedChange != null,
                interactionSource = interaction,
                indication = null
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(thumbShape)
                .background(
                    when {
                        !enabled -> colors.textTertiary.copy(alpha = 0.45f)
                        checked -> colors.panelBorder
                        else -> uncheckedThumbColor
                    }
                )
                .border(1.dp, colors.panelBorder.copy(alpha = if (enabled) 0.85f else 0.25f), thumbShape)
        )
    }
}
