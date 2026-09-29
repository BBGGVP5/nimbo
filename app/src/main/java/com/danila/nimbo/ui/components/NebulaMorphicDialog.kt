@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.i18n.t

/** Opaque, IME-safe dialog. Title, explanation and actions all remain reachable at large text sizes. */
@Composable
fun NebulaMorphicDialog(
    onDismissRequest: () -> Unit,
    title: String,
    description: String? = null,
    confirmButtonText: String? = "ОК",
    cancelButtonText: String? = "Отмена",
    onConfirm: () -> Unit,
    confirmButtonColor: Color? = null,
    headerIcon: ImageVector? = null,
    headerIconTint: Color? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = LocalNebulaColors.current
    val accent = confirmButtonColor ?: colors.accent
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest, properties = properties) {
        Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight)
            .navigationBarsPadding().imePadding(), shape = RoundedCornerShape(18.dp),
            color = colors.panelFill, contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, colors.panelBorder), tonalElevation = 0.dp) {
            Column(Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        if (headerIcon != null) Icon(headerIcon, null, Modifier.padding(bottom = 8.dp).size(24.dp),
                            tint = headerIconTint ?: colors.textSecondary)
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() })
                    }
                    IconButton(onDismissRequest, Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, t("Закрыть", "Close"), tint = colors.textSecondary)
                    }
                }
                HorizontalDivider(color = colors.divider)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!description.isNullOrBlank()) Text(description, color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium)
                content()
                if (cancelButtonText != null || confirmButtonText != null) {
                    HorizontalDivider(color = colors.divider)
                    NimboToolActions {
                        if (cancelButtonText != null) OutlinedButton(onDismissRequest,
                            Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.panelBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)) {
                            Text(cancelButtonText)
                        }
                        if (confirmButtonText != null) Button(onConfirm,
                            Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contrastingLabel(accent))) {
                            Text(confirmButtonText)
                        }
                    }
                }
                }
            }
        }
    }
}
