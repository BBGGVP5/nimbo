package com.danila.nimbo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Layout decisions only: no network policy, preference mapping or persistence. */
internal fun networkRowStacks(widthDp: Float, fontScale: Float): Boolean =
    widthDp / fontScale.coerceAtLeast(1f) < 320f

internal fun networkChoicesStack(widthDp: Float, fontScale: Float, count: Int): Boolean =
    count > 0 && widthDp / fontScale.coerceAtLeast(1f) / count < 88f

@Composable
internal fun NetworkSettingsLabel(title: String, subtitle: String?, icon: ImageVector?, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = colors.textSecondary)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun NetworkSettingsRow(title: String, subtitle: String?, icon: ImageVector? = null,
    control: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (networkRowStacks(maxWidth.value, LocalDensity.current.fontScale)) {
            Column(Modifier.fillMaxWidth().testTag("network-row-stacked")) {
                NetworkSettingsLabel(title, subtitle, icon, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.align(Alignment.End)) { control() }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("network-row-inline"),
                verticalAlignment = Alignment.CenterVertically) {
                NetworkSettingsLabel(title, subtitle, icon, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                control()
            }
        }
    }
}

@Composable
internal fun NetworkSettingsToggleRow(title: String, subtitle: String?, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, showDivider: Boolean = true, icon: ImageVector? = null,
    enabled: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    NetworkSettingsRow(title, subtitle, icon) {
        NimboStyleSwitch(checked = checked, enabled = enabled,
            // Keep the Switch semantics present even when disabled, without dispatching work.
            onCheckedChange = { if (enabled) { haptic.tick(); onCheckedChange(it) } })
    }
    if (showDivider) HorizontalDivider(color = LocalNebulaColors.current.divider)
}

@Composable
internal fun NetworkSettingsChoice(title: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = LocalNebulaColors.current
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics {
        this.selected = selected
        role = Role.RadioButton
    }, color = if (selected) colors.controlFill else colors.panelFill,
        contentColor = colors.textPrimary, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.panelBorder)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Content-fitting timing chips, with a full 48dp touch target and no fixed-width empty tile. */
@Composable
internal fun NetworkSettingsCompactChoice(title: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics {
        this.selected = selected; role = Role.RadioButton
    }, color = if (selected) colors.controlFill else colors.panelFill,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.panelBorder)) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

@Composable
internal fun NetworkSettingsChoices(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.selectableGroup()) {
        val stacked = networkChoicesStack(maxWidth.value, LocalDensity.current.fontScale, items.size)
        val select: (Int) -> Unit = { index -> if (index != selectedIndex) { haptic.tick(); onSelect(index) } }
        if (stacked) Column(Modifier.fillMaxWidth().testTag("network-choices-stacked"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, label ->
                NetworkSettingsChoice(label, index == selectedIndex, { select(index) }, Modifier.fillMaxWidth())
            }
        } else {
            val colors = LocalNebulaColors.current
            Surface(Modifier.fillMaxWidth().testTag("network-choices-inline"), color = colors.controlFill,
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.panelBorder)) {
                Row(Modifier.padding(3.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    items.forEachIndexed { index, label ->
                        val selected = index == selectedIndex
                        Surface(onClick = { select(index) },
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).semantics {
                                this.selected = selected; role = Role.RadioButton
                            }, shape = RoundedCornerShape(9.dp),
                            color = if (selected) colors.panelFill else androidx.compose.ui.graphics.Color.Transparent,
                            border = if (selected) BorderStroke(1.dp, colors.accent) else null) {
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) colors.textPrimary else colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NetworkSettingsTextField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, singleLine: Boolean = true) {
    val colors = LocalNebulaColors.current
    OutlinedTextField(value, onValueChange, modifier, singleLine = singleLine,
        label = { Text(label) }, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.controlFill, unfocusedContainerColor = colors.controlFill,
            focusedBorderColor = colors.accent, unfocusedBorderColor = colors.panelBorder,
            focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
            focusedLabelColor = colors.textSecondary, unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.accent))
}
