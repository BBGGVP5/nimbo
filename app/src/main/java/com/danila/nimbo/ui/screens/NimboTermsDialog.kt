package com.danila.nimbo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.ui.components.NimboPanel
import com.danila.nimbo.ui.components.contrastingLabel
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Presentation-only revision: existing legal wording and Agree/Exit callbacks are unchanged. */
@Composable
internal fun NimboTermsDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    val colors = LocalNebulaColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            NimboPanel(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Policy, null, Modifier.size(24.dp), tint = colors.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Text(t("Условия использования", "Terms of use"), Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        IconButton(onDismiss, Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, t("Закрыть", "Close"), tint = colors.textSecondary)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.divider)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TermsParagraph(t("Назначение", "Purpose"), t(
                            "Данное программное обеспечение предназначено исключительно для некоммерческого использования в образовательных и исследовательских целях.",
                            "This software is intended solely for non-commercial use in educational and research contexts."))
                        TermsParagraph(t("Ограничения", "Restrictions"), t(
                            "Коммерческое использование запрещено.", "Commercial use is prohibited."))
                        TermsParagraph(t("Ответственность", "Liability"), t(
                            "Разработчики не несут ответственности за любую коммерческую деятельность с использованием данного ПО.",
                            "The developers accept no liability for any commercial activity carried out with this software."))
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = contrastingLabel(colors.accent))) {
                        Text(t("Согласен", "Agree"))
                    }
                    TextButton(onExit, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(t("Выйти из приложения", "Exit application"), color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsParagraph(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = LocalNebulaColors.current.textPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalNebulaColors.current.textSecondary)
    }
}
