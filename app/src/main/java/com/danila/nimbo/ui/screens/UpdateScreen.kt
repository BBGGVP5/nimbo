package com.danila.nimbo.ui.screens

import com.danila.nimbo.ui.components.NimboOperation
import com.danila.nimbo.ui.components.contrastingLabel
import com.danila.nimbo.ui.components.NimboOperationPhrase

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danila.nimbo.utils.BackgroundHealth
import com.danila.nimbo.utils.BackgroundHealthChecker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.network.UpdateWorkScheduler
import com.danila.nimbo.network.UpdateDownloadProgress
import com.danila.nimbo.network.UpdateDownloadStage
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.*
import kotlinx.coroutines.launch

import android.app.Application
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.ui.components.SettingsSwitch
import java.time.Instant

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
    val installedAtIso = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            Instant.ofEpochMilli(packageInfo.lastUpdateTime).toString()
        }.getOrNull()
    }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var currentInfo by remember {
        mutableStateOf(
            UpdateInfo(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = preferencesManager.lastInstalledUpdateVersion
                    ?: BuildConfig.VERSION_NAME,
                changelog = preferencesManager.lastInstalledUpdateChangelog.orEmpty(),
                downloadUrl = "",
                publishDate = installedAtIso,
                channel = if (BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true)) {
                    UpdateChannel.BETA
                } else {
                    UpdateChannel.STABLE
                },
                releaseUrl = preferencesManager.lastInstalledUpdateReleaseUrl.orEmpty()
            )
        )
    }
    var isChecking by remember { mutableStateOf(false) }
    var hasChecked by remember { mutableStateOf(false) }
    var updateCheckError by remember { mutableStateOf<String?>(null) }

    // Новая настройка автопроверки
    var showUpdateDialog by remember { mutableStateOf(preferencesManager.showUpdateDialog) }
    var updateChannel by remember { mutableStateOf(preferencesManager.updateChannel) }
    var updateWifiOnly by remember { mutableStateOf(preferencesManager.updateWifiOnly) }

    val downloadStatus by UpdateManager.downloadStatus.collectAsState()
    val isDownloading by UpdateManager.isDownloading.collectAsState()
    val downloadError by UpdateManager.downloadError.collectAsState()
    val backgroundRetryMessage = t(
        "Не удалось проверить обновления. Повторим в фоне, когда появится сеть.",
        "Couldn't check updates. We'll retry in the background when a network is available."
    )

    // Функция обновления данных
    val refreshData = suspend {
        isChecking = true
        updateCheckError = null
        // Параллельно проверяем обнову и историю
        val updateJob = scope.launch {
            val result = runCatching { UpdateManager.checkUpdateInBackground(context) }
            updateInfo = result.getOrNull()
            if (result.isFailure) {
                updateCheckError = backgroundRetryMessage
                UpdateWorkScheduler.enqueueImmediate(context)
            }
        }
        val historyJob = scope.launch {
            UpdateManager.getReleaseInfoForTag("v${BuildConfig.VERSION_NAME}")
                ?.let { currentInfo = it }
        }
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

    // Состояние фоновых ограничений перечитываем при каждом входе на экран:
    // пользователь мог только что снять оптимизацию батареи в настройках.
    var backgroundHealth by remember { mutableStateOf(BackgroundHealthChecker.inspect(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                backgroundHealth = BackgroundHealthChecker.inspect(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SubPageSectionHeader(t("Состояние", "Status"), icon = Icons.Default.Info)
        Spacer(Modifier.height(8.dp))
        UpdateStatusCard(
            isChecking = isChecking,
            hasUpdate = updateInfo != null,
            checkError = updateCheckError,
            currentVersion = "v" + BuildConfig.VERSION_NAME
                .replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "")
                .trim(),
            isDownloading = isDownloading,
            downloadStatus = downloadStatus,
            downloadError = downloadError,
            updateInfo = updateInfo,
            onCheck = { scope.launch { refreshData() } },
            onInstall = {
                // Загрузку ведёт UpdateManager: она переживает уход с экрана и
                // может быть приостановлена/продолжена из окна обновления.
                UpdateManager.startDownload(context, updateInfo!!)
            }
        )

        // Если система душит фон, «обновление не пришло» — не баг проверки,
        // а корзина ожидания или гибернация. Показываем это прямо здесь.
        if (backgroundHealth.hasIssues) {
            Spacer(Modifier.height(12.dp))
            BackgroundThrottleCard(health = backgroundHealth)
        }

        Spacer(Modifier.height(24.dp))

        SubPageSectionHeader(t("История изменений", "Changelog"), icon = Icons.Default.History)
        Spacer(Modifier.height(8.dp))
        UpdateSection {
            UpdateHistoryCard(currentInfo = currentInfo)
        }
        Spacer(Modifier.height(24.dp))

        SubPageSectionHeader(t("Настройки", "Settings"), icon = Icons.Default.Settings)
        Spacer(Modifier.height(8.dp))
        UpdateSection {
            Column {
                SettingsSwitch(
                    icon = Icons.Default.NotificationsActive,
                    title = t("Автопроверка обновлений", "Auto-check for updates"),
                    subtitle = t("Показывать диалог при запуске", "Show dialog on launch"),
                    checked = showUpdateDialog,
                    onCheckedChange = { showUpdateDialog = it }
                )
                HorizontalDivider(color = nebulaColors.textPrimary.copy(alpha = 0.08f))
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = nebulaColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                    Spacer(Modifier.height(12.dp))
                    UpdateChannelPicker(
                        value = updateChannel,
                        onValueChange = { channel ->
                            if (updateChannel != channel) {
                                updateChannel = channel
                                preferencesManager.updateChannel = channel
                                preferencesManager.lastUpdateCheckTime = 0L
                                scope.launch { refreshData() }
                            }
                        }
                    )
                }
                HorizontalDivider(color = nebulaColors.textPrimary.copy(alpha = 0.08f))
                SettingsSwitch(
                    icon = Icons.Default.Wifi,
                    title = t("Скачивать только по Wi‑Fi", "Download over Wi-Fi only"),
                    subtitle = t(
                        "Не начинать загрузку через мобильную сеть",
                        "Do not start downloads over mobile data"
                    ),
                    checked = updateWifiOnly,
                    onCheckedChange = {
                        updateWifiOnly = it
                        preferencesManager.updateWifiOnly = it
                    }
                )
                HorizontalDivider(color = nebulaColors.textPrimary.copy(alpha = 0.08f))
                Text(
                    t(
                        "Файл проверяется по SHA-256 и сертификату приложения. При ошибке текущая версия останется установленной.",
                        "The file is checked by SHA-256 and the app certificate. Your current version stays installed if anything fails."
                    ),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SubPageSectionHeader(t("Система", "System"), icon = Icons.Default.Memory)
        Spacer(Modifier.height(8.dp))
        UpdateSection { SystemInfoBlock() }

        Spacer(Modifier.height(24.dp))
}

@Composable
private fun UpdateChannelPicker(
    value: UpdateChannel,
    onValueChange: (UpdateChannel) -> Unit
) {
    val colors = LocalNebulaColors.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    val label: @Composable (UpdateChannel) -> String = { channel ->
        when (channel) {
            UpdateChannel.STABLE -> t("Стабильный", "Stable")
            UpdateChannel.BETA -> t("Бета", "Beta")
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(
                    if (expanded) colors.accent.copy(alpha = 0.14f)
                    else colors.controlFill
                )
                .border(
                    width = if (expanded) 1.5.dp else 1.dp,
                    color = if (expanded) colors.accent else colors.textPrimary.copy(alpha = 0.15f),
                    shape = shape
                )
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label(value),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = t("Выбрать канал", "Choose channel"),
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(19.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            UpdateChannel.entries.forEach { channel ->
                val selected = channel == value
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label(channel),
                            color = if (selected) colors.accent else colors.textPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    onClick = {
                        expanded = false
                        onValueChange(channel)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                )
            }
        }
    }
}

@Composable
private fun UpdateHistoryCard(currentInfo: UpdateInfo) {
    val colors = LocalNebulaColors.current
    val language = LocalConfiguration.current.locales[0].language
    val date = UpdateUiText.releaseDate(
        currentInfo.publishDate ?: currentInfo.assetUpdatedAt,
        language
    ) ?: t("Дата обновления недоступна", "Update date unavailable")
    val channel = when (currentInfo.channel) {
        UpdateChannel.STABLE -> t("Стабильный канал", "Stable channel")
        UpdateChannel.BETA -> t("Бета-канал", "Beta channel")
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            UpdateUiText.versionLabel(currentInfo.versionName, language),
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(date, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text("Android · $channel", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        UpdateReleaseNotes(
            content = currentInfo.changelog?.takeIf { it.isNotBlank() }
                ?: t(
                    "Для этой установки подробный список изменений не сохранён.",
                    "Detailed release notes were not saved for this installation."
                )
        )
    }
}

/** Shared only by update surfaces; the complete Markdown and its links stay available. */
@Composable
internal fun UpdateReleaseNotes(content: String) {
    val colors = LocalNebulaColors.current
    var expanded by rememberSaveable(content) { mutableStateOf(false) }
    val stateLabel = if (expanded) t("Развёрнуто", "Expanded") else t("Свёрнуто", "Collapsed")
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = stateLabel },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                t("Что изменилось", "What is new"),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.labelLarge
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            HorizontalDivider(color = colors.panelBorder)
            Spacer(Modifier.height(12.dp))
            MarkdownChangelog(content = content, color = colors.textSecondary, itemAlignment = Alignment.Start)
        }
    }
}

@Composable
private fun UpdateSection(content: @Composable () -> Unit) {
    com.danila.nimbo.ui.components.NimboPanel(Modifier.fillMaxWidth(), content)
}

/**
 * Предупреждение о том, что система ограничивает фоновые проверки.
 * Кнопка ведёт в системные настройки приложения — снять оптимизацию батареи
 * и запрет на работу в фоне можно только там.
 */
@Composable
private fun BackgroundThrottleCard(health: BackgroundHealth) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val reasons = buildList {
        if (health.notificationsBlocked) {
            add(t("уведомления выключены", "notifications are turned off"))
        }
        if (health.batteryOptimized) {
            add(t("включена оптимизация батареи", "battery optimisation is on"))
        }
        if (health.throttled) {
            add(
                t(
                    "система перевела приложение в режим редкого запуска",
                    "the system moved the app to a rare standby bucket"
                )
            )
        }
        if (health.hibernationEnabled) {
            add(
                t(
                    "включено усыпление неиспользуемых приложений",
                    "hibernation of unused apps is on"
                )
            )
        }
    }

    UpdateSection {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(nebulaColors.statusError.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = nebulaColors.statusError,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t(
                            "Фоновые проверки ограничены",
                            "Background checks are limited"
                        ),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t(
                            "Пока приложение не открывают, уведомление об обновлении может приходить с задержкой или не приходить вовсе.",
                            "While the app is not opened, the update notification can be delayed or never arrive."
                        ),
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (reasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                reasons.forEach { reason ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = reason,
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            NimboUpdateButton(
                label = t("Открыть настройки приложения", "Open app settings"),
                icon = Icons.Default.Settings,
                primary = false,
                enabled = true,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun UpdateStatusCard(
    isChecking: Boolean,
    hasUpdate: Boolean,
    checkError: String?,
    currentVersion: String,
    isDownloading: Boolean,
    downloadStatus: UpdateDownloadProgress?,
    downloadError: String?,
    updateInfo: UpdateInfo?,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val language = LocalConfiguration.current.locales[0].language
    UpdateSection {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isChecking) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = colors.textSecondary, strokeWidth = 2.dp)
                } else {
                    Icon(
                        when {
                            !checkError.isNullOrBlank() -> Icons.Default.CloudOff
                            hasUpdate -> Icons.Default.SystemUpdateAlt
                            else -> Icons.Default.Verified
                        },
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when {
                            isChecking -> t("Проверяем обновления", "Checking for updates")
                            !checkError.isNullOrBlank() -> t("Проверка отложена", "Check postponed")
                            updateInfo?.kind == UpdateKind.REPAIR -> t("Дополнительное обновление", "Additional update")
                            hasUpdate -> t("Доступно обновление", "Update available")
                            else -> t("У вас последняя версия", "You're up to date")
                        },
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        t("Установлена $currentVersion", "Installed $currentVersion"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (hasUpdate && updateInfo != null) {
                        Text(
                            UpdateUiText.versionLabel(updateInfo.versionName, language),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
            if (!checkError.isNullOrBlank()) {
                Text(checkError, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (!downloadError.isNullOrBlank()) {
                Text(
                    UpdateUiText.error(downloadError, language),
                    color = colors.statusError,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isDownloading) {
                NimboOperationPhrase(
                    if (downloadStatus?.stage == UpdateDownloadStage.DOWNLOADING) NimboOperation.Download else NimboOperation.Update,
                    active = downloadError.isNullOrBlank() && downloadStatus?.stage != UpdateDownloadStage.READY
                )
                val fraction = (downloadStatus?.fraction ?: 0f).coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()
                Text(
                    when (downloadStatus?.stage) {
                        UpdateDownloadStage.VERIFYING -> t("Проверяем файл", "Verifying file")
                        UpdateDownloadStage.READY -> t("Готово к установке", "Ready to install")
                        else -> t("Загружаем обновление", "Downloading update")
                    },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = colors.accent,
                    trackColor = colors.controlFill
                )
                Text(
                    "${UpdateUiText.fileSize(downloadStatus?.downloadedBytes ?: 0L, language)} / " +
                        "${UpdateUiText.fileSize(downloadStatus?.totalBytes ?: updateInfo?.fileSize ?: 0L, language)} · $percent%",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    t("Загрузку можно продолжить после обрыва", "Download resumes after an interruption"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            NimboUpdateButton(
                label = when {
                    isDownloading -> t("Загрузка…", "Downloading…")
                    isChecking -> t("Проверка…", "Checking…")
                    hasUpdate -> t("Скачать и установить", "Download and install")
                    else -> t("Проверить снова", "Check again")
                },
                icon = if (hasUpdate) Icons.Default.Download else Icons.Default.Refresh,
                primary = true,
                enabled = !isChecking && !isDownloading,
                onClick = if (hasUpdate) onInstall else onCheck
            )

            if (hasUpdate && updateInfo != null) {
                HorizontalDivider(color = colors.panelBorder)
                UpdateReleaseNotes(
                    content = updateInfo.changelog?.takeIf { it.isNotBlank() }
                        ?: t(
                            "Улучшения производительности и исправление ошибок.",
                            "Performance improvements and bug fixes."
                        )
                )
                if (updateInfo.fileSize > 0) {
                    UpdateMetadataRow(Icons.Default.Storage, t("Размер", "Size"),
                        UpdateUiText.fileSize(updateInfo.fileSize, language, decimals = 2))
                }
                if (updateInfo.assetName.isNotBlank()) {
                    UpdateMetadataRow(Icons.Default.Android, t("Файл", "File"), updateInfo.assetName)
                }
                val updatedAt = UpdateUiText.releaseDate(updateInfo.assetUpdatedAt ?: updateInfo.publishDate, language)
                val channelLabel = when (updateInfo.channel) {
                    UpdateChannel.STABLE -> t("Стабильный", "Stable")
                    UpdateChannel.BETA -> t("Бета", "Beta")
                }
                UpdateMetadataRow(
                    Icons.Default.Update, t("Канал", "Channel"),
                    buildString {
                        append(channelLabel)
                        if (updatedAt != null) append(t(" · обновлён $updatedAt", " · updated $updatedAt"))
                    }
                )
                UpdateMetadataRow(
                    Icons.Default.VerifiedUser, t("Защита", "Security"),
                    if (updateInfo.sha256 != null) t("SHA-256 + сертификат APK", "SHA-256 + APK certificate")
                    else t("Сертификат APK", "APK certificate")
                )
            }
        }
    }
}

@Composable
private fun UpdateMetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(nebulaColors.controlFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun NimboUpdateButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    val fill = if (primary) colors.accent else colors.controlFill
    Button(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = fill,
            contentColor = if (primary) contrastingLabel(fill) else colors.textPrimary)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
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

    Column(modifier = Modifier.padding(16.dp)) {
        SystemInfoRow(
            icon = Icons.Default.Memory,
            label = t("Архитектура", "Architecture"),
            value = primaryAbi,
            valueColor = nebulaColors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        SystemInfoRow(
            icon = Icons.Default.PhoneAndroid,
            label = t("Система", "System"),
            value = androidVersion,
            valueColor = nebulaColors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        SystemInfoRow(
            icon = Icons.Default.Smartphone,
            label = t("Устройство", "Device"),
            value = deviceName.ifBlank { "—" },
            valueColor = nebulaColors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        SystemInfoRow(
            icon = Icons.Default.Apps,
            label = t("Версия приложения", "App version"),
            value = appVersion,
            valueColor = nebulaColors.textPrimary
        )
        if (abis.size > 1) {
            Spacer(Modifier.height(14.dp))
            SystemInfoRow(
                icon = Icons.Default.Layers,
                label = t("Поддерживаемые ABI", "Supported ABIs"),
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
                .background(nebulaColors.controlFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(18.dp))
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
    // Also clean notes saved by an older version, without requiring another update.
    val lines = remember(content) {
        com.danila.nimbo.shared.updates.ReleaseNotesText.withoutPlatformHeading(content).lines()
    }
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
