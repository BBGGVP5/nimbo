package com.danila.nimbo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Selection only. Starting/stopping a tunnel belongs to the connection control. */
@Composable
fun NimboAutoChoice(selected: Boolean, subtitle: String, onSelect: () -> Unit,
    modifier: Modifier = Modifier, busy: Boolean = false) {
    val colors = LocalNebulaColors.current
    Surface(onClick = onSelect, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)
        .testTag("auto-mode-choice").semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.accent.copy(alpha = .08f) else colors.panelFill,
        border = BorderStroke(1.dp, if (selected) colors.accent.copy(alpha = .55f) else colors.panelBorder)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
            else Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = colors.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Авто", "Auto"), style = MaterialTheme.typography.titleSmall,
                    color = if (selected) colors.accent else colors.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            RadioButton(selected, onClick = null)
        }
    }
}
