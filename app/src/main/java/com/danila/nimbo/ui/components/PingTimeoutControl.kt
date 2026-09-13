package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Shared by both Android settings entry points; latency formatting is deliberately untouched. */
@Composable
fun PingTimeoutControl(seconds: Int, onSecondsChange: (Int) -> Unit, english: Boolean = false) {
    val colors = LocalNebulaColors.current
    fun t(ru: String, en: String) = if (english) en else ru
    var editing by rememberSaveable { mutableStateOf(false) }
    val unit = t("с", "s")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { onSecondsChange(seconds - 1) }, enabled = seconds > 1) {
            Icon(Icons.Default.Remove, t("Уменьшить таймаут на секунду", "Decrease timeout by one second"),
                tint = if (seconds > 1) colors.textPrimary else colors.textTertiary)
        }
        OutlinedButton(
            onClick = { editing = true },
            modifier = Modifier.testTag("ping-timeout-edit").semantics {
                contentDescription = t("Таймаут $seconds секунд. Ввести число", "Timeout $seconds seconds. Enter a number")
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
        ) {
            Text("$seconds $unit")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { onSecondsChange(seconds + 1) }, enabled = seconds < 10) {
            Icon(Icons.Default.Add, t("Увеличить таймаут на секунду", "Increase timeout by one second"),
                tint = if (seconds < 10) colors.textPrimary else colors.textTertiary)
        }
    }
    if (editing) {
        var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(seconds.toString(), TextRange(0, seconds.toString().length)))
        }
        val parsed = parsePingTimeoutSeconds(draft.text)
        val focus = remember { FocusRequester() }
        val save = {
            parsed?.let { onSecondsChange(it); editing = false }
            Unit
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(t("Таймаут проверки", "Probe timeout")) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("ping-timeout-input"),
                    label = { Text(t("Секунды", "Seconds")) },
                    suffix = { Text(unit) },
                    supportingText = { Text(t("От 1 до 10 с. Пинг сервера — в мс.", "1–10 s. Server latency is shown in ms.")) },
                    isError = parsed == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.textTertiary,
                        focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textSecondary,
                        cursorColor = colors.accent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = save, enabled = parsed != null, modifier = Modifier.testTag("ping-timeout-save")) {
                    Text(t("Сохранить", "Save"))
                }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(t("Отмена", "Cancel")) } }
        )
    }
}
