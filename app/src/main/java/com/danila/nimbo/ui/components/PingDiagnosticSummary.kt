package com.danila.nimbo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danila.nimbo.network.NimboPingDiagnostics
import com.danila.nimbo.network.NimboPingOutcome
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun PingDiagnosticSummary(english: Boolean = false) {
    val latest by NimboPingDiagnostics.latest.collectAsState()
    val summary = latest ?: return
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var copied by remember(summary) { mutableStateOf(false) }
    fun t(ru: String, en: String) = if (english) en else ru
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(t("Диагностика последней проверки", "Latest probe diagnostics"), color = colors.textSecondary)
        }
        if (expanded) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val outcome = when (summary.outcome) {
                    NimboPingOutcome.IN_PROGRESS -> t("Проверка идёт", "Checking")
                    NimboPingOutcome.SUCCESS -> t("Ответ получен", "Reply received")
                    NimboPingOutcome.UNAVAILABLE -> t("Ответ не получен", "No reply")
                    NimboPingOutcome.DEADLINE_EXCEEDED -> t("Время ожидания истекло", "Deadline exceeded")
                    NimboPingOutcome.PROCESS_LOST -> t("Процесс проверки завершился", "Probe process exited")
                    NimboPingOutcome.CANCELLED -> t("Проверка отменена", "Probe cancelled")
                }
                Text(outcome, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    t("Последний этап: ", "Last stage: ") + pingDiagnosticLastStage(summary).name,
                    color = colors.textSecondary, style = MaterialTheme.typography.bodySmall
                )
                Text(
                    t("Это последняя проверка, не обязательно выбранного сервера. Для диагностики проверьте один сервер. В отчёте только этапы и время — без адресов и ключей.",
                        "This is the latest probe, not necessarily the selected server. Probe one server to diagnose it. The report contains only stages and timing, no addresses or keys."),
                    color = colors.textTertiary, style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    enabled = summary.outcome != NimboPingOutcome.IN_PROGRESS,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Nimbo Ping diagnostic", pingDiagnosticText(summary)))
                            copied = true
                        }
                    }
                ) { Text(if (copied) t("Скопировано", "Copied") else t("Скопировать диагностику", "Copy diagnostics")) }
            }
        }
    }
}
