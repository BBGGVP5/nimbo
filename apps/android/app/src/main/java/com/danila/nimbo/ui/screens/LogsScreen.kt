package com.danila.nimbo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.model.LogEntry
import com.danila.nimbo.model.LogLevel
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.NebulaColors
import com.danila.nimbo.utils.Logger
import androidx.core.content.FileProvider
import java.io.File

private fun NebulaColors.isLightBackground(): Boolean = background.luminance() > 0.5f

private fun logsPanelFill(colors: NebulaColors): Color = colors.panelFill

private fun logsPanelBorder(colors: NebulaColors): Color = colors.panelBorder

private fun logsDivider(colors: NebulaColors): Color = colors.divider

private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> Color(0xFFFF5252)
    LogLevel.WARNING -> Color(0xFFFFAB40)
    LogLevel.INFO -> Color(0xFF59D98E)
    LogLevel.DEBUG -> Color(0xFF8A8A9E)
}

private fun levelShort(level: LogLevel): String = when (level) {
    LogLevel.ERROR -> "ERR"
    LogLevel.WARNING -> "WARN"
    LogLevel.INFO -> "INFO"
    LogLevel.DEBUG -> "DBG"
}

@Composable
fun LogsScreen(onNavigateBack: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val liveLogs by Logger.logEntries.collectAsState()
    val context = LocalContext.current
    val copiedMessage = t("Логи скопированы", "Logs copied")
    val exportedMessage = t("Диагностика сохранена", "Diagnostics saved")
    val exportErrorMessage = t("Не удалось сохранить диагностику", "Could not save diagnostics")
    val shareTitle = t("Поделиться диагностикой", "Share diagnostics")
    val shareErrorMessage = t("Не удалось подготовить файл", "Could not prepare the file")
    val scrollState = rememberLazyListState()

    var query by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<LogEntry>?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val report = pendingExport ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(report)
            } ?: error("Output stream is unavailable")
        }.onSuccess {
            Toast.makeText(context, exportedMessage, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(
                context,
                exportErrorMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingExport = null
    }

    // While paused, freeze the displayed list so it doesn't jump as new logs arrive.
    LaunchedEffect(paused) { frozen = if (paused) Logger.logEntries.value else null }
    val source = frozen ?: liveLogs

    val q = query.trim().lowercase()
    val filtered = source.filter { e ->
        (levelFilter == null || e.level == levelFilter) &&
            (q.isBlank() || e.message.lowercase().contains(q) || e.tag.lowercase().contains(q))
    }
    // Бейджи должны описывать текущий список на экране. Раньше они считались
    // от полного журнала и могли показывать, например, INFO 12 при «Показано 0».
    val visibleInfoCount = filtered.count { it.level == LogLevel.INFO }
    val visibleWarnCount = filtered.count { it.level == LogLevel.WARNING }
    val visibleErrorCount = filtered.count { it.level == LogLevel.ERROR }

    LaunchedEffect(filtered.size, autoScroll, paused) {
        if (autoScroll && !paused && filtered.isNotEmpty()) {
            scrollState.animateScrollToItem(filtered.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NimboBackButton(onBack = onNavigateBack)
            Spacer(Modifier.width(10.dp))
            Text(
                text = t("Логи", "Logs"),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
        // Counts + per-level badges
        Row(
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = t(
                    "${source.size} записей · показано ${filtered.size}",
                    "${source.size} entries · showing ${filtered.size}"
                ),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (visibleInfoCount > 0) LogLevelCountBadge("INFO", visibleInfoCount, logLevelColor(LogLevel.INFO))
            if (visibleWarnCount > 0) LogLevelCountBadge("WARN", visibleWarnCount, logLevelColor(LogLevel.WARNING))
            if (visibleErrorCount > 0) LogLevelCountBadge("ERR", visibleErrorCount, logLevelColor(LogLevel.ERROR))
        }

        if (source.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = nebulaColors.textTertiary.copy(alpha = 0.35f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = t("Логов пока нет", "No logs yet"),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            return@Column
        }

        // Search and severity filter.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogsSearchField(
                query = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f)
            )
            LogsLevelFilter(selected = levelFilter, onSelect = { levelFilter = it })
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogsActionTile(
                icon = Icons.Default.ContentCopy,
                label = t("Копировать", "Copy"),
                modifier = Modifier.weight(1f)
            ) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Nimbo diagnostics", Logger.getLogsAsText(filtered))
                )
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            }
            LogsActionTile(
                icon = Icons.Default.SaveAlt,
                label = t("Сохранить", "Save"),
                modifier = Modifier.weight(1f)
            ) {
                pendingExport = Logger.buildDiagnosticReport(context, filtered)
                exportLauncher.launch("Nimbo_diagnostics_${System.currentTimeMillis()}.txt")
            }
            LogsActionTile(
                icon = Icons.Default.Share,
                label = t("Поделиться", "Share"),
                modifier = Modifier.weight(1f)
            ) {
                shareDiagnosticReport(
                    context = context,
                    report = Logger.buildDiagnosticReport(context, filtered),
                    chooserTitle = shareTitle,
                    errorMessage = shareErrorMessage
                )
            }
            LogsActionTile(
                icon = Icons.Default.DeleteSweep,
                label = t("Очистить", "Clear"),
                destructive = true,
                modifier = Modifier.weight(1f)
            ) {
                showClearConfirmation = true
            }
        }
        Spacer(Modifier.height(12.dp))

        // Log panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(logsPanelFill(nebulaColors))
                .border(BorderStroke(1.dp, logsPanelBorder(nebulaColors)), RoundedCornerShape(16.dp))
        ) {
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = t("По этому фильтру ничего нет", "Nothing matches this filter"),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("Измените запрос или уровень событий", "Change the query or event level"),
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState,
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(filtered, key = { _, it -> it.id }) { index, log ->
                        LogRow(log, showDivider = index < filtered.lastIndex)
                    }
                }
            }
        }

        // Footer: autoscroll / pause / count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LogsCheck(
                label = t("Автопрокрутка", "Auto-scroll"),
                checked = autoScroll,
                onToggle = { autoScroll = !autoScroll }
            )
            LogsCheck(
                label = t("Пауза", "Pause"),
                checked = paused,
                onToggle = { paused = !paused }
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = t("${filtered.size} записей", "${filtered.size} entries"),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        }
    }

    if (showClearConfirmation) {
        NebulaMorphicDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = t("Очистить журнал?", "Clear diagnostics?"),
            description = t(
                "Все сохранённые события будут удалены с устройства.",
                "All saved events will be removed from this device."
            ),
            confirmButtonText = t("Очистить", "Clear"),
            cancelButtonText = t("Отмена", "Cancel"),
            confirmButtonColor = Color(0xFFFF5252),
            onConfirm = {
                Logger.clearLogs()
                showClearConfirmation = false
            }
        )
    }
}

@Composable
private fun LogLevelCountBadge(label: String, count: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label $count",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun LogsSearchField(query: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val nebulaColors = LocalNebulaColors.current
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(Icons.Default.Search, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(21.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(20.dp))
                }
            }
        },
        placeholder = {
            Text(
                text = t("Поиск по логам...", "Search logs..."),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = nebulaColors.textPrimary,
            fontWeight = FontWeight.SemiBold
        ),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = nebulaColors.accent.copy(alpha = 0.55f),
            unfocusedBorderColor = logsPanelBorder(nebulaColors),
            focusedContainerColor = logsPanelFill(nebulaColors),
            unfocusedContainerColor = logsPanelFill(nebulaColors),
            cursorColor = nebulaColors.accent
        )
    )
}

@Composable
private fun LogsActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val tint = if (destructive) Color(0xFFFF5252) else nebulaColors.textPrimary
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (destructive) Color(0xFFFF5252).copy(alpha = 0.10f)
                else logsPanelFill(nebulaColors)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (destructive) Color(0xFFFF5252).copy(alpha = 0.25f)
                    else logsPanelBorder(nebulaColors)
                ),
                RoundedCornerShape(15.dp)
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun LogsLevelFilter(selected: LogLevel?, onSelect: (LogLevel?) -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    var expanded by remember { mutableStateOf(false) }
    val active = selected != null
    Box {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) nebulaColors.accent.copy(alpha = 0.16f) else logsPanelFill(nebulaColors))
                .border(
                    BorderStroke(1.dp, if (active) nebulaColors.accent.copy(alpha = 0.55f) else logsPanelBorder(nebulaColors)),
                    RoundedCornerShape(16.dp)
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = t("Фильтр", "Filter"),
                tint = if (active) nebulaColors.accent else nebulaColors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options: List<Pair<LogLevel?, String>> = listOf(
                null to t("Все", "All"),
                LogLevel.INFO to "INFO",
                LogLevel.WARNING to "WARN",
                LogLevel.ERROR to "ERR",
                LogLevel.DEBUG to "DBG"
            )
            options.forEach { (level, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            color = if (level == null) nebulaColors.textPrimary else logLevelColor(level),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        onSelect(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LogsCheck(label: String, checked: Boolean, onToggle: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) nebulaColors.accent else Color.Transparent)
                .border(
                    1.5.dp,
                    if (checked) nebulaColors.accent else nebulaColors.textSecondary.copy(alpha = 0.55f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LogRow(log: LogEntry, showDivider: Boolean) {
    val nebulaColors = LocalNebulaColors.current
    val levelColor = logLevelColor(log.level)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Column(modifier = Modifier.width(62.dp)) {
                Text(
                    text = levelShort(log.level),
                    color = levelColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = log.formattedTime,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.tag,
                    color = levelColor.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = log.message,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(logsDivider(nebulaColors))
            )
        }
    }
}

private fun shareDiagnosticReport(
    context: Context,
    report: String,
    chooserTitle: String,
    errorMessage: String
) {
    runCatching {
        val shareDirectory = File(context.cacheDir, "shared").apply { mkdirs() }
        shareDirectory.listFiles()
            ?.filter { it.name.startsWith("Nimbo_diagnostics_") }
            ?.forEach(File::delete)
        val file = File(shareDirectory, "Nimbo_diagnostics_${System.currentTimeMillis()}.txt")
        file.writeText(report, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nimbo diagnostics")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Nimbo diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, chooserTitle)
        )
    }.onFailure {
        Toast.makeText(
            context,
            errorMessage,
            Toast.LENGTH_SHORT
        ).show()
    }
}
