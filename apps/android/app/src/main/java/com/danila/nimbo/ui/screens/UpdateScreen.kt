package com.danila.nimbo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.BuildConfig
import androidx.compose.material3.pulltorefresh.*
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.*
import kotlinx.coroutines.launch

import android.app.Application
import androidx.compose.animation.core.tween
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.ui.components.SettingsSwitch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    NimboSubPageScaffold(title = t("Обновления", "Updates"), onBack = onBack) {
        UpdatesSettingsContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.UpdatesSettingsContent() {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var currentInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var hasChecked by remember { mutableStateOf(false) }

    // Новая настройка автопроверки
    var showUpdateDialog by remember { mutableStateOf(preferencesManager.showUpdateDialog) }
    var updateChannel by remember { mutableStateOf(preferencesManager.updateChannel) }

    val downloadProgress by UpdateManager.downloadProgress.collectAsState()
    val isDownloading by UpdateManager.isDownloading.collectAsState()
    val downloadError by UpdateManager.downloadError.collectAsState()

    // Функция обновления данных
    val refreshData = suspend {
        isChecking = true
        // Параллельно проверяем обнову и историю
        val updateJob = scope.launch { updateInfo = UpdateManager.checkUpdate(context) }
        val historyJob = scope.launch { currentInfo = UpdateManager.getReleaseInfoForTag("v${BuildConfig.VERSION_NAME}") }
        updateJob.join()
        historyJob.join()
        isChecking = false
        hasChecked = true
    }

    // Сохраняем настройку
    LaunchedEffect(showUpdateDialog) {
        preferencesManager.showUpdateDialog = showUpdateDialog
    }

    // Загрузка данных при входе
    LaunchedEffect(Unit) {
        refreshData()
    }

    SubPageSectionHeader(t("Состояние", "Status"), icon = Icons.Default.Info)
        Spacer(Modifier.height(8.dp))
        UpdateStatusCard(
            isChecking = isChecking,
            hasUpdate = updateInfo != null,
            currentVersion = "v" + BuildConfig.VERSION_NAME
                .replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "")
                .trim(),
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            updateInfo = updateInfo,
            onCheck = { scope.launch { refreshData() } },
            onInstall = {
                scope.launch {
                    UpdateManager.downloadAndInstall(context, updateInfo!!)
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        SubPageSectionHeader(t("Настройки", "Settings"), icon = Icons.Default.Settings)
        Spacer(Modifier.height(8.dp))
        NimboGlassSection {
            Column {
                SettingsSwitch(
                    icon = Icons.Default.NotificationsActive,
                    title = t("Автопроверка обновлений", "Auto-check for updates"),
                    subtitle = t("Показывать диалог при запуске", "Show dialog on launch"),
                    checked = showUpdateDialog,
                    onCheckedChange = { showUpdateDialog = it }
                )
                HorizontalDivider(color = nebulaColors.textPrimary.copy(alpha = 0.08f))
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t("Канал обновлений", "Update channel"),
                                color = nebulaColors.textPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                t(
                                    "Бета включает предварительные сборки",
                                    "Beta includes prerelease builds"
                                ),
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        UpdateChannel.entries.forEachIndexed { index, channel ->
                            SegmentedButton(
                                selected = updateChannel == channel,
                                onClick = {
                                    if (updateChannel != channel) {
                                        updateChannel = channel
                                        preferencesManager.updateChannel = channel
                                        preferencesManager.lastUpdateCheckTime = 0L
                                        scope.launch { refreshData() }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, UpdateChannel.entries.size),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    when (channel) {
                                        UpdateChannel.STABLE -> t("Стабильный", "Stable")
                                        UpdateChannel.BETA -> t("Бета", "Beta")
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        t(
                            "APK проверяется по SHA-256 и сертификату. Если загрузка или установка завершится с ошибкой, Android сохранит текущую версию.",
                            "The APK is checked by SHA-256 and signing certificate. If download or installation fails, Android keeps the current version."
                        ),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        SubPageSectionHeader(t("Система", "System"), icon = Icons.Default.Memory)
        Spacer(Modifier.height(8.dp))
        NimboGlassSection { SystemInfoBlock() }

        if (currentInfo?.changelog?.isNotBlank() == true) {
            Spacer(Modifier.height(24.dp))
            SubPageSectionHeader(t("История изменений", "Changelog"), icon = Icons.Default.History)
            Spacer(Modifier.height(8.dp))
            NimboGlassSection {
                Column(modifier = Modifier.padding(20.dp)) {
                    MarkdownChangelog(
                        content = currentInfo?.changelog ?: "",
                        color = nebulaColors.textSecondary,
                        itemAlignment = Alignment.Start
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
}

@Composable
private fun NimboGlassSection(content: @Composable () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    val glassBlur = if (reducedTransparency) 0.dp else LocalGlobalBlurRadius.current
        .coerceIn(0f, 80f)
        .dp
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(nebulaColors.surface)
        )
        if (!reducedTransparency) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(nebulaColors.accent.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                    .blur(glassBlur)
            )
        }
        content()
    }
}

@Composable
private fun UpdateStatusCard(
    isChecking: Boolean,
    hasUpdate: Boolean,
    currentVersion: String,
    isDownloading: Boolean,
    downloadProgress: Float?,
    downloadError: String?,
    updateInfo: UpdateInfo?,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    NimboGlassSection {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.14f))
                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val statusIcon = when {
                        isChecking -> null
                        hasUpdate -> Icons.Default.NewReleases
                        else -> Icons.Default.Verified
                    }
                    if (statusIcon != null) {
                        Icon(statusIcon, null, tint = nebulaColors.accent, modifier = Modifier.size(26.dp))
                    } else {
                        ExpressiveCircularLoader(
                            modifier = Modifier.size(26.dp),
                            color = nebulaColors.accent,
                            strokeWidth = 3.dp
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isChecking -> t("Проверяем обновления", "Checking for updates")
                            updateInfo?.kind == UpdateKind.REPAIR ->
                                t("Исправление текущей версии", "Repair for current version")
                            hasUpdate -> t("Доступно обновление", "Update available")
                            else -> t("У вас последняя версия", "You're up to date")
                        },
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    val subtitleText = when {
                        hasUpdate -> "Nimbo " + (updateInfo?.versionName
                            ?.replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "")
                            ?.let { "v$it" }
                            ?: "")
                        else -> t("Nimbo $currentVersion", "Nimbo $currentVersion")
                    }
                    Text(
                        text = subtitleText,
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1
                    )
                }
            }

            if (hasUpdate && updateInfo != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = nebulaColors.textPrimary.copy(alpha = 0.08f))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = t("ЧТО НОВОГО", "WHAT'S NEW"),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                MarkdownChangelog(
                    content = updateInfo.changelog?.takeIf { it.isNotBlank() }
                        ?: t(
                            "Улучшения производительности и исправление ошибок.",
                            "Performance improvements and bug fixes."
                        ),
                    color = nebulaColors.textSecondary,
                    itemAlignment = Alignment.Start
                )

                if (updateInfo.fileSize > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = t(
                            "Размер: ${"%.2f".format(updateInfo.fileSize / 1024f / 1024f)} МБ",
                            "Size: ${"%.2f".format(updateInfo.fileSize / 1024f / 1024f)} MB"
                        ),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(Modifier.height(10.dp))
                val updatedAt = formatReleaseDate(updateInfo.assetUpdatedAt)
                val channelLabel = when (updateInfo.channel) {
                    UpdateChannel.STABLE -> t("Стабильный", "Stable")
                    UpdateChannel.BETA -> t("Бета", "Beta")
                }
                Text(
                    text = buildString {
                        append(t("Канал: $channelLabel", "Channel: $channelLabel"))
                        if (updatedAt != null) append(t(" • Файл обновлён: $updatedAt", " • File updated: $updatedAt"))
                    },
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = if (updateInfo.sha256 != null) {
                        t("Проверка: SHA-256 + сертификат APK", "Verification: SHA-256 + APK certificate")
                    } else {
                        t("Проверка: сертификат APK", "Verification: APK certificate")
                    },
                    color = nebulaColors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!downloadError.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = downloadError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(18.dp))

            if (isDownloading) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(nebulaColors.textPrimary.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(downloadProgress ?: 0f)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(nebulaColors.accent, nebulaColors.accent.copy(alpha = 0.6f))
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = t(
                            "Загрузка: ${(downloadProgress?.let { (it * 100).toInt() } ?: 0)}%",
                            "Downloading: ${(downloadProgress?.let { (it * 100).toInt() } ?: 0)}%"
                        ),
                        color = nebulaColors.accent,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                val (label, icon) = when {
                    hasUpdate -> t("Установить", "Install") to Icons.Default.Download
                    isChecking -> t("Проверка…", "Checking…") to Icons.Default.Refresh
                    else -> t("Проверить снова", "Check again") to Icons.Default.Refresh
                }
                NimboUpdateButton(
                    label = label,
                    icon = icon,
                    primary = hasUpdate,
                    enabled = !isChecking,
                    onClick = if (hasUpdate) onInstall else onCheck
                )
            }
        }
    }
}

private fun formatReleaseDate(value: String?): String? = runCatching {
    value?.takeIf(String::isNotBlank)?.let {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(it))
    }
}.getOrNull()

@Composable
private fun NimboUpdateButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(18.dp)
    val containerAlpha = if (primary) 0.32f else 0.08f
    val borderAlpha = if (primary) 0.55f else 0.22f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(nebulaColors.accent.copy(alpha = containerAlpha))
            .border(1.dp, nebulaColors.accent.copy(alpha = borderAlpha), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SystemInfoBlock() {
    val nebulaColors = LocalNebulaColors.current
    val abis = remember { android.os.Build.SUPPORTED_ABIS.toList() }
    val primaryAbi = abis.firstOrNull() ?: "—"
    val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
    val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
    val appVersion = "v" + BuildConfig.VERSION_NAME.replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "").trim() +
        " (${BuildConfig.VERSION_CODE})"

    Column(modifier = Modifier.padding(20.dp)) {
        SystemInfoRow(
            icon = Icons.Default.Memory,
            label = "Архитектура",
            value = primaryAbi,
            valueColor = nebulaColors.accent
        )
        Spacer(Modifier.height(14.dp))
        SystemInfoRow(
            icon = Icons.Default.PhoneAndroid,
            label = "Система",
            value = androidVersion,
            valueColor = nebulaColors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        SystemInfoRow(
            icon = Icons.Default.Smartphone,
            label = "Устройство",
            value = deviceName.ifBlank { "—" },
            valueColor = nebulaColors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        SystemInfoRow(
            icon = Icons.Default.Apps,
            label = "Версия приложения",
            value = appVersion,
            valueColor = nebulaColors.textPrimary
        )
        if (abis.size > 1) {
            Spacer(Modifier.height(14.dp))
            SystemInfoRow(
                icon = Icons.Default.Layers,
                label = "Поддерживаемые ABI",
                value = abis.joinToString(", "),
                valueColor = nebulaColors.textSecondary
            )
        }
    }
}

@Composable
private fun SystemInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    val nebulaColors = LocalNebulaColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(nebulaColors.accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                value,
                color = valueColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        }
    }
}

@Composable
fun MarkdownChangelog(
    content: String,
    color: Color,
    itemAlignment: Alignment.Horizontal = Alignment.Start
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = LocalNebulaColors.current.accent
    val lines = content.lines()
    var inCodeBlock = false
    val codeBuffer = mutableListOf<String>()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = itemAlignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            if (trimmedLine.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true
                    codeBuffer.clear()
                } else {
                    inCodeBlock = false
                    CodeBlock(
                        text = codeBuffer.joinToString(separator = "\n"),
                        itemAlignment = itemAlignment
                    )
                    codeBuffer.clear()
                }
                return@forEach
            }
            if (inCodeBlock) {
                codeBuffer.add(line)
                return@forEach
            }

            // A line wrapped entirely in **…** is used in our release notes as a
            // section title — strip the asterisks and render as a styled header
            // instead of letting them slip through as literal stars or get
            // misread as a bullet.
            val boldHeaderMatch = Regex("^\\*\\*(.+?)\\*\\*[:：]?$").matchEntire(trimmedLine)
            when {
                trimmedLine.startsWith("#") -> {
                    // Header (Removing ALL # from start)
                    val headerText = trimmedLine.replaceFirst(Regex("^#+\\s*"), "")
                    Text(
                        text = headerText,
                        color = LocalNebulaColors.current.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                boldHeaderMatch != null -> {
                    Text(
                        text = boldHeaderMatch.groupValues[1],
                        color = LocalNebulaColors.current.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                // Require whitespace after the bullet character so we don't accidentally
                // match "**bold**" as a list item starting with "*".
                Regex("^[-*]\\s+").containsMatchIn(trimmedLine) -> {
                    // List Item (Replacing - or * with •)
                    val listText = trimmedLine.replaceFirst(Regex("^[-*]\\s*"), "")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = if (itemAlignment == Alignment.CenterHorizontally) 0.dp else 12.dp),
                        horizontalArrangement = if (itemAlignment == Alignment.CenterHorizontally) Arrangement.Center else Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", color = LocalNebulaColors.current.accent, fontWeight = FontWeight.Bold)
                        MarkdownInlineText(
                            text = listText,
                            color = color,
                            linkColor = linkColor,
                            uriHandler = uriHandler
                        )
                    }
                }
                Regex("^\\d+[.)]\\s+").containsMatchIn(trimmedLine) -> {
                    val numberPrefix = Regex("^\\d+[.)]\\s+").find(trimmedLine)?.value ?: ""
                    val listText = trimmedLine.removePrefix(numberPrefix)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = if (itemAlignment == Alignment.CenterHorizontally) 0.dp else 12.dp),
                        horizontalArrangement = if (itemAlignment == Alignment.CenterHorizontally) Arrangement.Center else Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(numberPrefix, color = LocalNebulaColors.current.accent, fontWeight = FontWeight.Bold)
                        MarkdownInlineText(
                            text = listText,
                            color = color,
                            linkColor = linkColor,
                            uriHandler = uriHandler
                        )
                    }
                }
                else -> {
                    // Normal Text
                    MarkdownInlineText(
                        text = trimmedLine,
                        color = color,
                        linkColor = linkColor,
                        uriHandler = uriHandler,
                        textAlign = if (itemAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
                    )
                }
            }
        }
        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            CodeBlock(
                text = codeBuffer.joinToString(separator = "\n"),
                itemAlignment = itemAlignment
            )
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    color: Color,
    linkColor: Color,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    textAlign: TextAlign = TextAlign.Start
) {
    val annotated = remember(text, color, linkColor) { parseInlineMarkdown(text, color, linkColor) }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = color,
            lineHeight = 20.sp,
            textAlign = textAlign
        )
    ) { offset ->
        annotated
            .getStringAnnotations(tag = "URL", start = offset, end = offset)
            .firstOrNull()
            ?.let { uriHandler.openUri(it.item) }
    }
}

@Composable
private fun CodeBlock(
    text: String,
    itemAlignment: Alignment.Horizontal
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(nebulaColors.textPrimary.copy(alpha = 0.06f))
            .border(0.5.dp, nebulaColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(
            text = text,
            color = nebulaColors.textSecondary,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            textAlign = if (itemAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
        )
    }
}

private fun parseInlineMarkdown(text: String, color: Color, linkColor: Color): AnnotatedString {
    val markdownLinkRegex = Regex("""\[(.+?)]\((https?://[^\s)]+)\)""")
    val bareUrlRegex = Regex("""https?://[^\s)]+""")

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val remaining = text.substring(i)
            val markdownLink = markdownLinkRegex.find(remaining)?.takeIf { it.range.first == 0 }
            if (markdownLink != null) {
                val label = markdownLink.groupValues[1]
                val url = markdownLink.groupValues[2]
                val start = length
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(label)
                pop()
                addStringAnnotation(tag = "URL", annotation = url, start = start, end = length)
                i += markdownLink.value.length
                continue
            }

            if (remaining.startsWith("**")) {
                val end = remaining.indexOf("**", startIndex = 2)
                if (end > 1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color))
                    append(remaining.substring(2, end))
                    pop()
                    i += end + 2
                    continue
                }
            }

            if (remaining.startsWith("`")) {
                val end = remaining.indexOf('`', startIndex = 1)
                if (end > 0) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = color.copy(alpha = 0.16f)
                        )
                    )
                    append(remaining.substring(1, end))
                    pop()
                    i += end + 1
                    continue
                }
            }

            val bareUrl = bareUrlRegex.find(remaining)?.takeIf { it.range.first == 0 }
            if (bareUrl != null) {
                val url = bareUrl.value.trimEnd('.', ',', ';', ':', '!')
                val start = length
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(url)
                pop()
                addStringAnnotation(tag = "URL", annotation = url, start = start, end = length)
                i += url.length
                continue
            }

            append(text[i])
            i += 1
        }
    }
}
