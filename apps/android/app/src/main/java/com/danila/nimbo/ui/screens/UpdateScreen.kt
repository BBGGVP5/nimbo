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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.pulltorefresh.*
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.network.UpdateDownloadProgress
import com.danila.nimbo.network.UpdateDownloadStage
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.LiquidGlassDepth
import com.danila.nimbo.ui.components.liquidGlassSurface
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.*
import kotlinx.coroutines.launch

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import kotlin.math.cos
import kotlin.math.sin
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

    // Новая настройка автопроверки
    var showUpdateDialog by remember { mutableStateOf(preferencesManager.showUpdateDialog) }
    var updateChannel by remember { mutableStateOf(preferencesManager.updateChannel) }
    var updateWifiOnly by remember { mutableStateOf(preferencesManager.updateWifiOnly) }

    val downloadStatus by UpdateManager.downloadStatus.collectAsState()
    val isDownloading by UpdateManager.isDownloading.collectAsState()
    val downloadError by UpdateManager.downloadError.collectAsState()

    // Функция обновления данных
    val refreshData = suspend {
        isChecking = true
        // Параллельно проверяем обнову и историю
        val updateJob = scope.launch { updateInfo = UpdateManager.checkUpdate(context) }
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

    SubPageSectionHeader(t("Состояние", "Status"), icon = Icons.Default.Info)
        Spacer(Modifier.height(8.dp))
        UpdateStatusCard(
            isChecking = isChecking,
            hasUpdate = updateInfo != null,
            currentVersion = "v" + BuildConfig.VERSION_NAME
                .replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "")
                .trim(),
            isDownloading = isDownloading,
            downloadStatus = downloadStatus,
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

        SubPageSectionHeader(t("История изменений", "Changelog"), icon = Icons.Default.History)
        Spacer(Modifier.height(8.dp))
        NimboGlassSection {
            UpdateHistoryCard(currentInfo = currentInfo)
        }
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
                        Spacer(Modifier.width(12.dp))
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
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SubPageSectionHeader(t("Система", "System"), icon = Icons.Default.Memory)
        Spacer(Modifier.height(8.dp))
        NimboGlassSection { SystemInfoBlock() }

        Spacer(Modifier.height(24.dp))
}

@Composable
private fun UpdateChannelPicker(
    value: UpdateChannel,
    onValueChange: (UpdateChannel) -> Unit
) {
    val colors = LocalNebulaColors.current
    var expanded by remember { mutableStateOf(false) }
    val width = 154.dp
    val shape = RoundedCornerShape(15.dp)
    val label: @Composable (UpdateChannel) -> String = { channel ->
        when (channel) {
            UpdateChannel.STABLE -> t("Стабильный", "Stable")
            UpdateChannel.BETA -> t("Бета", "Beta")
        }
    }

    Box(modifier = Modifier.width(width)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (expanded) colors.accent.copy(alpha = 0.14f)
                    else colors.textPrimary.copy(alpha = 0.045f)
                )
                .border(
                    width = if (expanded) 1.5.dp else 1.dp,
                    color = if (expanded) colors.accent else colors.textPrimary.copy(alpha = 0.15f),
                    shape = shape
                )
                .clickable { expanded = !expanded }
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
            modifier = Modifier.width(width)
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

    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.copy(alpha = 0.14f))
                    .border(1.dp, colors.accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.History, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = UpdateUiText.versionLabel(currentInfo.versionName, language),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = date,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateInfoChip(t("Android", "Android"))
            UpdateInfoChip(channel)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = colors.textPrimary.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        MarkdownChangelog(
            content = currentInfo.changelog?.takeIf { it.isNotBlank() }
                ?: t(
                    "Для этой установки подробный список изменений не сохранён.",
                    "Detailed release notes were not saved for this installation."
                ),
            color = colors.textSecondary,
            itemAlignment = Alignment.Start
        )
    }
}

@Composable
private fun UpdateInfoChip(text: String) {
    val colors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.accent.copy(alpha = 0.11f))
            .border(1.dp, colors.accent.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NimboGlassSection(content: @Composable () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    val shape = RoundedCornerShape(18.dp)
    val surfaceModifier = if (nebulaColors.isLiquidGlass) {
        Modifier.liquidGlassSurface(
            shape = shape,
            depth = LiquidGlassDepth.PANEL,
            interactive = false
        )
    } else {
        Modifier
            .clip(shape)
            .background(
                if (reducedTransparency) nebulaColors.surface
                else nebulaColors.surface.copy(alpha = 0.94f)
            )
            .border(1.dp, nebulaColors.textPrimary.copy(alpha = 0.10f), shape)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(surfaceModifier)
    ) {
        content()
    }
}

@Composable
private fun UpdateStatusCard(
    isChecking: Boolean,
    hasUpdate: Boolean,
    currentVersion: String,
    isDownloading: Boolean,
    downloadStatus: UpdateDownloadProgress?,
    downloadError: String?,
    updateInfo: UpdateInfo?,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val language = LocalConfiguration.current.locales[0].language
    NimboGlassSection {
        Box {
            UpdateStatusBackdrop(isChecking = isChecking, hasUpdate = hasUpdate)
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
                                t("Дополнительное обновление", "Additional update")
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
                    UpdateMetadataRow(
                        icon = Icons.Default.Storage,
                        label = t("Размер", "Size"),
                        value = UpdateUiText.fileSize(updateInfo.fileSize, language, decimals = 2)
                    )
                }
                if (updateInfo.assetName.isNotBlank()) {
                    UpdateMetadataRow(
                        icon = Icons.Default.Android,
                        label = t("Файл", "File"),
                        value = updateInfo.assetName
                    )
                }

                val updatedAt = UpdateUiText.releaseDate(
                    updateInfo.assetUpdatedAt ?: updateInfo.publishDate,
                    language
                )
                val channelLabel = when (updateInfo.channel) {
                    UpdateChannel.STABLE -> t("Стабильный", "Stable")
                    UpdateChannel.BETA -> t("Бета", "Beta")
                }
                UpdateMetadataRow(
                    icon = Icons.Default.Update,
                    label = t("Канал", "Channel"),
                    value = buildString {
                        append(channelLabel)
                        if (updatedAt != null) append(t(" · обновлён $updatedAt", " · updated $updatedAt"))
                    }
                )
                UpdateMetadataRow(
                    icon = Icons.Default.VerifiedUser,
                    label = t("Защита", "Security"),
                    value = if (updateInfo.sha256 != null) {
                        t("SHA-256 + сертификат APK", "SHA-256 + APK certificate")
                    } else {
                        t("Сертификат APK", "APK certificate")
                    },
                    accent = true
                )
            }

            if (!downloadError.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = UpdateUiText.error(downloadError, language),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(18.dp))

            if (isDownloading) {
                val progress = downloadStatus
                val fraction = progress?.fraction ?: 0f
                val percent = (fraction * 100).toInt().coerceIn(0, 100)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.075f))
                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when (progress?.stage) {
                                    UpdateDownloadStage.VERIFYING -> t("Проверяем файл", "Verifying file")
                                    UpdateDownloadStage.READY -> t("Готово к установке", "Ready to install")
                                    else -> t("Загружаем обновление", "Downloading update")
                                },
                                color = nebulaColors.textPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = t("Загрузку можно продолжить после обрыва", "Download resumes after an interruption"),
                                color = nebulaColors.textTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(nebulaColors.accent.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "$percent%",
                                color = nebulaColors.accent,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(nebulaColors.textPrimary.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(nebulaColors.accent.copy(alpha = 0.72f), nebulaColors.accent)
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            UpdateUiText.fileSize(progress?.downloadedBytes ?: 0L, language),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            t(
                                "из ${UpdateUiText.fileSize(progress?.totalBytes ?: updateInfo?.fileSize ?: 0L, language)}",
                                "of ${UpdateUiText.fileSize(progress?.totalBytes ?: updateInfo?.fileSize ?: 0L, language)}"
                            ),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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
}

/**
 * Живой фон карточки статуса обновления.
 *
 * Прежняя версия рисовала четыре линии с alpha 0.035–0.06 — на тёмной панели
 * их не было видно вообще. Теперь это три слоя: дышащие пятна акцента,
 * бегущие световые полосы и мягкое свечение сверху; плотность и скорость
 * зависят от состояния, поэтому «Проверяем» ощущается быстрее, чем
 * «У вас последняя версия».
 *
 * Фаза монотонно растёт от кадровых часов: у `infiniteRepeatable` на стыке
 * 1 -> 0 полосы прыгали обратно заметным рывком.
 */
@Composable
private fun UpdateStatusBackdrop(
    isChecking: Boolean,
    hasUpdate: Boolean
) {
    val colors = LocalNebulaColors.current
    val animate = LocalBackgroundAnimationEnabled.current && !LocalReducedTransparencyEnabled.current
    // Проверка — быстрое сканирование, найденное обновление — бодрее покоя.
    val speed = when {
        isChecking -> 2.6f
        hasUpdate -> 1.5f
        else -> 1f
    }
    val phaseState = remember { mutableFloatStateOf(0.38f) }
    LaunchedEffect(animate, speed) {
        if (!animate) {
            phaseState.floatValue = 0.38f
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                phaseState.floatValue = 0.38f + (now - startNanos) / 1_000_000_000f / 6.4f * speed
            }
        }
    }

    val accent = colors.accent
    val highlight = if (colors.background.luminance() > 0.5f) Color.Black else Color.White
    // Найденное обновление подсвечиваем ярче: карточка должна тянуть взгляд.
    val boost = if (hasUpdate) 1.35f else 1f

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val phase = phaseState.floatValue
        val twoPi = (Math.PI * 2.0).toFloat()

        // 1. Дышащие пятна акцента.
        repeat(3) { index ->
            val local = phase * 0.55f + index * 0.31f
            val radius = h * (0.85f + 0.25f * index)
            val center = Offset(
                x = w * (0.18f + 0.34f * index) + w * 0.10f * sin((local * twoPi).toDouble()).toFloat(),
                y = h * (0.30f + 0.22f * (index % 2)) + h * 0.16f * cos((local * twoPi).toDouble()).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.13f * boost),
                        accent.copy(alpha = 0.04f * boost),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // 2. Бегущие полосы.
        val segment = w * 0.34f
        val travel = w + segment * 2f
        repeat(4) { index ->
            val local = ((phase + index * 0.27f) % 1f + 1f) % 1f
            val startX = local * travel - segment
            val y = h * (0.16f + index * 0.20f)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = (0.16f + index * 0.03f) * boost),
                        Color.Transparent
                    ),
                    start = Offset(startX, y),
                    end = Offset(startX + segment, y + h * 0.06f)
                ),
                start = Offset(startX, y),
                end = Offset(startX + segment, y + h * 0.06f),
                strokeWidth = (1.6f + index * 0.35f).dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 3. Мягкое свечение по верхней кромке — даёт карточке объём без тени.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(highlight.copy(alpha = 0.05f), Color.Transparent),
                startY = 0f,
                endY = h * 0.55f
            )
        )
    }
}

@Composable
private fun UpdateMetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Boolean = false
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
                .clip(RoundedCornerShape(9.dp))
                .background(nebulaColors.accent.copy(alpha = if (accent) 0.17f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) nebulaColors.accent else nebulaColors.textSecondary,
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
                color = if (accent) nebulaColors.accent else nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

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
