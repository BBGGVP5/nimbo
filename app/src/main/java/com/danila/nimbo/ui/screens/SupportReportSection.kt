package com.danila.nimbo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.SupportDiagnosticStore
import com.danila.nimbo.utils.SupportReportPreview
import com.danila.nimbo.utils.prepareSupportReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SupportReportSection() {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()
    val shareTitle = t("Отправить диагностику Nimbo", "Share Nimbo diagnostics")
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SupportReportPreview?>(null) }
    var selected by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t(
            "Отчёт содержит версии приложения и ядра, состояние VPN и обезличенные снимки конфигурации и заголовков подписки. Пароли, ключи, адреса серверов и ссылки исключены. Снимки могут быть устаревшими.",
            "The report includes app and core versions, VPN state and redacted configuration and subscription header snapshots. Credentials, server addresses and links are omitted. Snapshots may be outdated."
        ), color = colors.textPrimary)
        Text(t(
            "Сначала проверьте содержимое каждого файла (до 32 КиБ). Подготовка не подключает VPN и ничего не отправляет. Поделиться можно отдельно через выбранное приложение.",
            "Review each file first (up to 32 KiB). Preparing does not connect the VPN or send anything. Share separately through an app you choose."
        ), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy, onClick = {
            busy = true
            error = false
            preview = null
            selected = 0
            scope.launch {
                try {
                    preview = prepareSupportReport { SupportDiagnosticStore.export(context) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = true
                } finally {
                    busy = false
                }
            }
        }) {
            Text(if (busy) t("Подготовка…", "Preparing…") else t("Подготовить отчёт", "Prepare report"))
        }
        preview?.let { prepared ->
            Text(t("Предпросмотр файлов", "File previews"), color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium)
            prepared.entries.forEachIndexed { index, entry ->
                FilterChip(selected = selected == index, onClick = { selected = index },
                    label = { Text(entry.file.name) })
            }
            key(prepared, selected) {
                Surface(color = colors.panelFill, border = BorderStroke(1.dp, colors.panelBorder)) {
                    SelectionContainer {
                        Text(prepared.entries[selected].text,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                            color = colors.textPrimary, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(t("Будут отправлены все три файла с показанным содержимым.",
                "All three files will be shared with the contents shown here."),
                color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                error = false
                try {
                    SupportDiagnosticStore.share(context, prepared.entries.map { it.file }, shareTitle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = true
                }
            }) { Text(t("Поделиться отчётом", "Share report")) }
        }
        if (error) Text(t("Не удалось подготовить или открыть отчёт. Попробуйте ещё раз.",
            "Could not prepare or open the report. Please try again."), color = colors.textPrimary)
    }
}
