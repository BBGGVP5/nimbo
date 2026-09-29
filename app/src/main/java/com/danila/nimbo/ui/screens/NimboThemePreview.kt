package com.danila.nimbo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.R
import com.danila.nimbo.ui.components.contrastingLabel
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.universalColorScheme

/** Color choices preview the same Home composition, not separate interface styles. */
@Composable
internal fun NimboThemePreviewGrid(selectedIndex: Int, accent: Color?, onSelect: (Int) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val labels = listOf(t("Системная", "System"), t("Светлая", "Light"), t("Тёмная", "Dark"), "OLED")
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= 640.dp) 4 else if (maxWidth >= 280.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..3).toList().chunked(columns).forEach { options ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { index ->
                        NimboThemePreviewOption(labels[index], index == selectedIndex,
                            dark = if (index == 0) systemDark else index != 1,
                            oled = index == 3, accent = accent, onClick = { onSelect(index) },
                            modifier = Modifier.weight(1f).testTag("theme-preview-$index"))
                    }
                }
            }
        }
    }
}

@Composable
private fun NimboThemePreviewOption(label: String, selected: Boolean, dark: Boolean,
    oled: Boolean, accent: Color?, onClick: () -> Unit, modifier: Modifier) {
    val outer = LocalNebulaColors.current
    val preview = universalColorScheme(dark, accent)
    val canvas = if (oled) Color.Black else preview.background
    Surface(onClick, modifier.semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp), color = outer.panelFill,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) outer.accent else outer.panelBorder)) {
        Column(Modifier.padding(10.dp)) {
            Surface(color = canvas, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 124.dp).clearAndSetSemantics {}) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("nimbo", fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = preview.onBackground)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth(0.8f).height(5.dp).background(preview.onBackground, CircleShape))
                            Box(Modifier.fillMaxWidth(0.55f).height(4.dp).background(preview.onSurfaceVariant, CircleShape))
                        }
                        Box(Modifier.size(30.dp).background(preview.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.nimbo_cloud), null, Modifier.size(18.dp), tint = contrastingLabel(preview.primary))
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(preview.surface, RoundedCornerShape(6.dp)).padding(7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(preview.surfaceVariant, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f).height(4.dp).background(preview.onSurfaceVariant, CircleShape))
                    }
                    Row(Modifier.fillMaxWidth().background(preview.surfaceVariant, CircleShape).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceAround) {
                        repeat(4) { index -> Box(Modifier.size(5.dp).background(if (index == 0) preview.primary else preview.onSurfaceVariant, CircleShape)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = outer.textPrimary)
                if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = outer.accent)
            }
        }
    }
}
