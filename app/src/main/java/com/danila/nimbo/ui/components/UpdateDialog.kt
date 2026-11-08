package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.network.UpdateDownloadStage
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.screens.UpdateReleaseNotes
import com.danila.nimbo.ui.screens.UpdateUiText
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Шаги всплывающего окна: чейнджлог сворачивается в компактную карточку загрузки. */
private enum class UpdatePopupPhase { DETAILS, ACTIVE, PAUSED, FAILED, READY }

/** Compact update summary with persistent download controls and expandable notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val colors = rememberUpdatePopupColors()
    val language = LocalConfiguration.current.locales[0].language
    val displayVersion = remember(updateInfo.versionName, language) {
        UpdateUiText.versionLabel(updateInfo.versionName, language)
    }
    val releaseDate = remember(updateInfo.assetUpdatedAt, updateInfo.publishDate, language) {
        UpdateUiText.releaseDate(updateInfo.assetUpdatedAt ?: updateInfo.publishDate, language)
    }

    val isDownloading by UpdateManager.isDownloading.collectAsState()
    val isPaused by UpdateManager.isPaused.collectAsState()
    val downloadStatus by UpdateManager.downloadStatus.collectAsState()
    val downloadError by UpdateManager.downloadError.collectAsState()

    // Загрузка, начатая раньше (на странице обновлений или до закрытия окна),
    // сохраняет своё состояние: окно сразу открывается компактной карточкой.
    var started by remember { mutableStateOf(isDownloading || isPaused) }
    var resumableBytes by remember(updateInfo.artifactId) {
        mutableStateOf(UpdateManager.resumableBytes(context, updateInfo))
    }
    LaunchedEffect(isDownloading, isPaused, downloadError) {
        resumableBytes = UpdateManager.resumableBytes(context, updateInfo)
    }

    val phase = when {
        isDownloading -> UpdatePopupPhase.ACTIVE
        !started -> UpdatePopupPhase.DETAILS
        !downloadError.isNullOrBlank() -> UpdatePopupPhase.FAILED
        downloadStatus?.stage == UpdateDownloadStage.READY -> UpdatePopupPhase.READY
        isPaused -> UpdatePopupPhase.PAUSED
        else -> UpdatePopupPhase.ACTIVE
    }
    val showDetails = phase == UpdatePopupPhase.DETAILS

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate,
            dismissOnClickOutside = !updateInfo.forceUpdate,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 620.dp).fillMaxWidth(0.94f)
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).navigationBarsPadding().imePadding()
                    .border(1.dp, colors.outline, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()).padding(20.dp)
                ) {
                    UpdatePopupHeadline(
                        phase = phase,
                        kind = updateInfo.kind,
                        stage = downloadStatus?.stage,
                        color = colors.title
                    )

                    Spacer(Modifier.height(16.dp))

                    UpdateReleaseCard(
                        phase = phase,
                        colors = colors,
                        displayVersion = displayVersion,
                        releaseDate = releaseDate,
                        resumableBytes = resumableBytes,
                        totalBytes = downloadStatus?.totalBytes?.takeIf { it > 0L } ?: updateInfo.fileSize,
                        downloadedBytes = downloadStatus?.downloadedBytes ?: 0L,
                        fraction = downloadStatus?.fraction ?: 0f,
                        errorText = downloadError?.takeIf { it.isNotBlank() }
                            ?.let { UpdateUiText.error(it, language) },
                        language = language,
                        // На этапе проверки файла останавливать уже нечего:
                        // сеть закрыта, идёт хеш и разбор APK.
                        canPause = phase == UpdatePopupPhase.ACTIVE &&
                            downloadStatus?.stage == UpdateDownloadStage.DOWNLOADING,
                        onPause = { UpdateManager.pauseDownload() }
                    )

                    NimboOperationPhrase(
                        if (downloadStatus?.stage == UpdateDownloadStage.DOWNLOADING) NimboOperation.Download else NimboOperation.Update,
                        active = isDownloading && downloadError.isNullOrBlank() && downloadStatus?.stage != UpdateDownloadStage.READY,
                        modifier = Modifier.padding(top = 8.dp))

                    Spacer(Modifier.height(16.dp))

                    UpdatePopupActions(
                        phase = phase,
                        colors = colors,
                        forceUpdate = updateInfo.forceUpdate,
                        kind = updateInfo.kind,
                        resumableBytes = resumableBytes,
                        onDownload = {
                            started = true
                            UpdateManager.clearDownloadError()
                            UpdateManager.startDownload(context, updateInfo)
                        },
                        onInstall = {
                            UpdateManager.verifiedApkFile(context, updateInfo)?.let { file ->
                                UpdateManager.installApk(context, file)
                            }
                        },
                        onOpenHistory = onOpenHistory,
                        onDismiss = onDismiss
                    )

                    AnimatedVisibility(
                        visible = showDetails,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(110))
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DialogChip(
                                    text = when (updateInfo.channel) {
                                        UpdateChannel.STABLE -> t("Стабильный", "Stable")
                                        UpdateChannel.BETA -> t("Бета", "Beta")
                                    }
                                )
                                if (updateInfo.fileSize > 0L) {
                                    DialogChip(text = UpdateUiText.fileSize(updateInfo.fileSize, language))
                                }

                            }

                            Spacer(Modifier.height(12.dp))
                            UpdateReleaseNotes(
                                maxHeight = (LocalConfiguration.current.screenHeightDp * 0.3f).dp,
                                content = updateInfo.changelog?.takeIf(String::isNotBlank)
                                    ?: t(
                                        "Исправления ошибок и улучшения стабильности.",
                                        "Bug fixes and stability improvements."
                                    )
                            )

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.cardFill)
                                    .padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    null,
                                    tint = colors.muted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    t(
                                        "Перед установкой Nimbo проверит файл и подпись приложения.",
                                        "Nimbo verifies the file and app signature before installation."
                                    ),
                                    color = colors.body,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun UpdatePopupHeadline(
    phase: UpdatePopupPhase,
    kind: UpdateKind,
    stage: UpdateDownloadStage?,
    color: Color
) {
    val headline = when (phase) {
        UpdatePopupPhase.DETAILS -> if (kind == UpdateKind.REPAIR) {
            t("Дополнительное обновление", "Additional update")
        } else {
            t("Доступно обновление", "Update available")
        }

        UpdatePopupPhase.ACTIVE -> if (stage == UpdateDownloadStage.VERIFYING) {
            t("Проверяем файл…", "Verifying file…")
        } else {
            t("Загрузка обновления…", "Downloading update…")
        }

        UpdatePopupPhase.PAUSED -> t("Загрузка приостановлена", "Download paused")
        UpdatePopupPhase.FAILED -> t("Не удалось загрузить", "Download failed")
        UpdatePopupPhase.READY -> t("Готово к установке", "Ready to install")
    }
    AnimatedContent(
        targetState = headline,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically { height -> height / 3 }) togetherWith
                (fadeOut(tween(140)) + slideOutVertically { height -> -height / 3 })
        },
        label = "update-popup-headline"
    ) { text ->
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UpdateReleaseCard(
    phase: UpdatePopupPhase,
    colors: UpdatePopupColors,
    displayVersion: String,
    releaseDate: String?,
    resumableBytes: Long,
    totalBytes: Long,
    downloadedBytes: Long,
    fraction: Float,
    errorText: String?,
    language: String,
    canPause: Boolean,
    onPause: () -> Unit
) {
    val showProgress = phase == UpdatePopupPhase.ACTIVE ||
        phase == UpdatePopupPhase.PAUSED ||
        phase == UpdatePopupPhase.READY
    val percent = (fraction * 100f).toInt().coerceIn(0, 100)
    val badgeIcon: ImageVector = when (phase) {
        UpdatePopupPhase.DETAILS -> Icons.Default.SystemUpdateAlt
        UpdatePopupPhase.ACTIVE -> Icons.Default.Download
        UpdatePopupPhase.PAUSED -> Icons.Default.Pause
        UpdatePopupPhase.FAILED -> Icons.Default.ErrorOutline
        UpdatePopupPhase.READY -> Icons.Default.CheckCircle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardFill)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                badgeIcon,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayVersion,
                    color = colors.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                when (phase) {
                    UpdatePopupPhase.DETAILS -> {
                        if (resumableBytes > 0L) {
                            Text(
                                t(
                                    "Загружено ${UpdateUiText.fileSize(resumableBytes, language)} — можно продолжить",
                                    "${UpdateUiText.fileSize(resumableBytes, language)} downloaded — can be resumed"
                                ),
                                color = colors.body,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else if (releaseDate != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    null,
                                    tint = colors.muted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    releaseDate,
                                    color = colors.muted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    UpdatePopupPhase.FAILED -> Text(
                        errorText ?: t("Проверьте соединение", "Check your connection"),
                        color = colors.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> Text(
                        text = UpdateUiText.fileSize(downloadedBytes, language) +
                            " / " + UpdateUiText.fileSize(totalBytes, language) +
                            " ($percent%)",
                        color = colors.body,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            AnimatedVisibility(
                visible = canPause,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120))
            ) {
                IconButton(
                    onClick = onPause,
                    modifier = Modifier.padding(start = 8.dp).size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = t("Пауза", "Pause"),
                        tint = colors.title,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showProgress,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(110))
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                UpdateProgressTrack(
                    fraction = if (phase == UpdatePopupPhase.READY) 1f else fraction,
                    activeColor = if (phase == UpdatePopupPhase.PAUSED) colors.muted else colors.accent,
                    trackColor = colors.track
                )
            }
        }
    }
}

@Composable
private fun UpdateProgressTrack(
    fraction: Float,
    activeColor: Color,
    trackColor: Color
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 380),
        label = "update-progress"
    )
    LinearProgressIndicator(
        progress = { animatedFraction },
        modifier = Modifier.fillMaxWidth().height(4.dp),
        color = activeColor,
        trackColor = trackColor
    )
}

@Composable
private fun UpdatePopupActions(
    phase: UpdatePopupPhase,
    colors: UpdatePopupColors,
    forceUpdate: Boolean,
    kind: UpdateKind,
    resumableBytes: Long,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val resumeLabel = t("Возобновить загрузку", "Resume download")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (phase) {
            UpdatePopupPhase.DETAILS -> {
                UpdateFilledButton(
                    label = when {
                        resumableBytes > 0L -> resumeLabel
                        kind == UpdateKind.REPAIR -> t("Скачать и установить", "Download and install")
                        else -> t("Скачать", "Download")
                    },
                    icon = if (resumableBytes > 0L) Icons.Default.PlayArrow else Icons.Default.Download,
                    colors = colors,
                    onClick = onDownload
                )
                UpdateOutlinedButton(
                    label = t("Просмотр истории изменений", "View changelog"),
                    icon = Icons.Default.History,
                    colors = colors,
                    onClick = onOpenHistory
                )
                if (!forceUpdate) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            t("Позже", "Later"),
                            color = colors.muted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            UpdatePopupPhase.ACTIVE -> {
                if (!forceUpdate) {
                    UpdateOutlinedButton(
                        label = t("Закрыть", "Close"),
                        icon = null,
                        colors = colors,
                        onClick = onDismiss
                    )
                }
            }

            UpdatePopupPhase.PAUSED, UpdatePopupPhase.FAILED -> {
                UpdateFilledButton(
                    label = resumeLabel,
                    icon = Icons.Default.PlayArrow,
                    colors = colors,
                    onClick = onDownload
                )
                if (!forceUpdate) {
                    UpdateOutlinedButton(
                        label = t("Закрыть", "Close"),
                        icon = null,
                        colors = colors,
                        onClick = onDismiss
                    )
                }
            }

            UpdatePopupPhase.READY -> {
                UpdateFilledButton(
                    label = t("Установить", "Install"),
                    icon = Icons.Default.Download,
                    colors = colors,
                    onClick = onInstall
                )
                if (!forceUpdate) {
                    UpdateOutlinedButton(
                        label = t("Закрыть", "Close"),
                        icon = null,
                        colors = colors,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateFilledButton(
    label: String,
    icon: ImageVector?,
    colors: UpdatePopupColors,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent
        )
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun UpdateOutlinedButton(
    label: String,
    icon: ImageVector?,
    colors: UpdatePopupColors,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.title)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** One neutral palette; only the main action and progress use the selected accent. */
private data class UpdatePopupColors(
    val surface: Color,
    val cardFill: Color,
    val accent: Color,
    val onAccent: Color,
    val title: Color,
    val body: Color,
    val muted: Color,
    val track: Color,
    val outline: Color,
    val error: Color
)

@Composable
private fun rememberUpdatePopupColors(): UpdatePopupColors {
    val n = LocalNebulaColors.current
    return UpdatePopupColors(
        surface = n.panelFill,
        cardFill = n.controlFill,
        accent = n.accent,
        onAccent = contrastingLabel(n.accent),
        title = n.textPrimary,
        body = n.textSecondary,
        muted = n.textSecondary,
        track = n.panelBorder,
        outline = n.panelBorder,
        error = n.statusError
    )
}

@Composable
private fun DialogChip(
    text: String,
    icon: (@Composable () -> Unit)? = null
) {
    val colors = rememberUpdatePopupColors()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardFill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Иконку красим через LocalContentColor, а не в месте вызова: Surface
        // диалога не входит в цветовую схему Material, поэтому contentColorFor
        // не срабатывает и значок без tint рисуется чёрным.
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides colors.body) {
                icon()
            }
        }
        Text(text, color = colors.body, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
