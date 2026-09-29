package com.danila.nimbo.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNebulaColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
        }
        NimboPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 4.dp), content = content) }
    }

}

@Composable
private fun SettingsEntry(icon: ImageVector, title: String, subtitle: String?,
    onClick: (() -> Unit)? = null, enabled: Boolean = true, trailing: @Composable () -> Unit = {}) {
    val colors = LocalNebulaColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(colors.controlFill, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.textPrimary else colors.textSecondary)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
fun SettingsSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean,
    enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    SettingsEntry(icon, title, subtitle, enabled = enabled) {
        Switch(checked, onCheckedChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    SettingsEntry(icon, title, subtitle)
}

@Composable
fun SettingsNavigationItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SettingsEntry(icon, title, subtitle, onClick) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = LocalNebulaColors.current.textSecondary)
    }
}

@Composable
fun SettingsLinkItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    SettingsEntry(icon, title, null, onClick) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp), tint = LocalNebulaColors.current.textSecondary)
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
    val shape: Shape = nimboControlShape(16.dp, 3.dp)

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
                unfocusedBorderColor = nimboControlBorderColor(nebulaColors.textTertiary.copy(alpha = 0.25f)),
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
    ElementStyleMode.LIQUID_GLASS -> Brush.linearGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.13f),
            nebulaColors.accent.copy(alpha = 0.035f)
        )
    )

    ElementStyleMode.MATERIAL_EXPRESSIVE -> Brush.linearGradient(
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

    ElementStyleMode.SOFT_NEO -> Brush.linearGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.18f),
            Color.Transparent
        )
    )

    // Signal: подложка иконки ровная и акцентная, без градиента.
    // Manga: ровная бумага без градиента — цвет задаёт тема.
    ElementStyleMode.MANGA -> Brush.linearGradient(
        listOf(nebulaColors.panelFill, nebulaColors.panelFill)
    )

    ElementStyleMode.SIGNAL -> Brush.linearGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.1f),
            nebulaColors.accent.copy(alpha = 0.1f)
        )
    )
}

private fun settingsRowBackground(
    nebulaColors: com.danila.nimbo.ui.theme.NebulaColors,
    style: ElementStyleMode
): Brush = when (style) {
    ElementStyleMode.LIQUID_GLASS -> Brush.linearGradient(
        listOf(Color.Transparent, Color.Transparent)
    )

    ElementStyleMode.MATERIAL_EXPRESSIVE -> Brush.linearGradient(
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

    // Ряды настроек идут сплошным списком, поэтому фон у них прозрачный —
    // разделяют не подложки, а границы карточки-секции.
    // Manga: ровная бумага без градиента — цвет задаёт тема.
    ElementStyleMode.MANGA -> Brush.linearGradient(
        listOf(nebulaColors.controlFill, nebulaColors.controlFill)
    )

    ElementStyleMode.SIGNAL -> Brush.linearGradient(
        listOf(Color.Transparent, Color.Transparent)
    )
}


