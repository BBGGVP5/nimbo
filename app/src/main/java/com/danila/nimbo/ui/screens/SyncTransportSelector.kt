package com.danila.nimbo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.components.nimboControlShape

/** Existing preference keys, one radio target per choice, no changes to pairing/discovery. */
@Composable
internal fun SyncTransportSelector(mode: String, onSelect: (String) -> Unit) {
    val colors = LocalNebulaColors.current
    val options = listOf(
        Triple("wifi", Icons.Default.Wifi, "Wi-Fi"),
        Triple("bluetooth", Icons.Default.Bluetooth, "Bluetooth"),
        Triple("both", Icons.Default.Sync, t("Авто", "Auto"))
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, icon, label) ->
            val selected = mode == value
            Surface(
                onClick = { if (!selected) onSelect(value) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 88.dp)
                    .testTag("sync-transport-$value").semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                shape = nimboControlShape(12.dp, 2.dp),
                color = if (selected) colors.controlFill else colors.panelFill,
                contentColor = colors.textPrimary,
                border = BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) colors.accent else colors.divider)
            ) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    Icon(icon, null, Modifier.size(24.dp), tint = if (selected) colors.accent else colors.textSecondary)
                    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}
