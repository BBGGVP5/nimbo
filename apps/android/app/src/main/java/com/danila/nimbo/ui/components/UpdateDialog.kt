package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.model.UpdateChannel
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.network.UpdateDownloadStage
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.screens.MarkdownChangelog
import com.danila.nimbo.ui.screens.UpdateUiText
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Шаги всплывающего окна: чейнджлог сворачивается в компактную карточку загрузки. */
private enum class UpdatePopupPhase { DETAILS, ACTIVE, PAUSED, FAILED, READY }

/**
 * Окно обновления на языке Material You: заголовок, одна тональная карточка релиза,
 * сворачиваемый чейнджлог и вертикальные кнопки. «Скачать» запускает загрузку прямо
 * в окне и анимацией убирает чейнджлог, оставляя компактную карточку прогресса
 * с паузой и возобновлением.
 */
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
            enter = fadeIn() + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .then(
                        colors.borderBrush?.let { brush ->
                            Modifier.border(BorderStroke(1.dp, brush), RoundedCornerShape(32.dp))
                        } ?: Modifier
                    ),
                shape = RoundedCornerShape(32.dp),
                color = colors.surface,
                tonalElevation = 0.dp,
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        // The dialog height is animated here and only here:
                        // when the download starts, the details block used to
                        // shrink on its own spring at the same time, so two
                        // height animations of different speed fought over the
                        // same pixels and the collapse looked slow and jerky.
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                ) {
                    UpdatePopupHeadline(
                        phase = phase,
                        kind = updateInfo.kind,
                        stage = downloadStatus?.stage,
                        color = colors.title
                    )

                    Spacer(Modifier.height(20.dp))

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
                                    icon = { Icon(Icons.Default.Android, null, Modifier.size(15.dp)) },
                                    text = "Android"
                                )
                                DialogChip(
                                    text = when (updateInfo.channel) {
                                        UpdateChannel.STABLE -> t("Стабильный", "Stable")
                                        UpdateChannel.BETA -> t("Бета", "Beta")
                                    }
                                )
                                if (updateInfo.fileSize > 0L) {
                                    DialogChip(text = UpdateUiText.fileSize(updateInfo.fileSize, language))
                                }
                                DialogChip(
                                    text = if (updateInfo.sha256 != null) {
                                        "SHA-256"
                                    } else {
                                        t("Подпись APK", "APK signature")
                                    }
                                )
                            }

                            Spacer(Modifier.height(18.dp))
                            Text(
                                t("Что изменилось", "What is new"),
                                color = colors.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.softFill)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                MarkdownChangelog(
                                    content = updateInfo.changelog?.takeIf(String::isNotBlank)
                                        ?: t(
                                            "Исправления ошибок и улучшения стабильности.",
                                            "Bug fixes and stability improvements."
                                        ),
                                    color = colors.body,
                                    itemAlignment = Alignment.Start
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(colors.accent.copy(alpha = 0.08f))
                                    .padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    null,
                                    tint = colors.accent,
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

                    Spacer(Modifier.height(20.dp))

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
            style = if (text.length > 22) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineSmall
            },
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
            .clip(RoundedCornerShape(26.dp))
            .background(colors.cardFill)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.badgeFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    badgeIcon,
                    contentDescription = null,
                    tint = colors.badgeContent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayVersion,
                    color = colors.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.badgeFill)
                        .clickable(onClick = onPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = t("Пауза", "Pause"),
                        tint = colors.badgeContent,
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
                    trackColor = colors.track,
                    materialYou = colors.materialYou
                )
            }
        }
    }
}

/** Линейный индикатор: в Material You волнистый M3 Expressive, иначе свой трек. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateProgressTrack(
    fraction: Float,
    activeColor: Color,
    trackColor: Color,
    materialYou: Boolean
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 380),
        label = "update-progress"
    )
    if (materialYou) {
        // M3 Expressive: волна на заполненной части, разрыв перед дорожкой и
        // точка-ограничитель — всё рисует сам индикатор.
        LinearWavyProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth(),
            color = activeColor,
            trackColor = trackColor
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(activeColor)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
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
                        modifier = Modifier.fillMaxWidth()
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
            .height(56.dp),
        shape = RoundedCornerShape(20.dp),
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
            .height(56.dp),
        shape = RoundedCornerShape(20.dp),
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

/**
 * Палитра окна. В Material You все роли берутся из динамической схемы, стеклянные
 * стили сохраняют свой акцент на тёмном фоне.
 */
private data class UpdatePopupColors(
    val materialYou: Boolean,
    val surface: Color,
    val cardFill: Color,
    val softFill: Color,
    val badgeFill: Color,
    val badgeContent: Color,
    val accent: Color,
    val onAccent: Color,
    val title: Color,
    val body: Color,
    val muted: Color,
    val track: Color,
    val outline: Color,
    val error: Color,
    val borderBrush: Brush?
)

@Composable
private fun rememberUpdatePopupColors(): UpdatePopupColors {
    val nebula = LocalNebulaColors.current
    val scheme = MaterialTheme.colorScheme
    return if (nebula.isMaterialYou) {
        UpdatePopupColors(
            materialYou = true,
            surface = scheme.surfaceContainerHigh,
            cardFill = scheme.surfaceContainerHighest,
            softFill = scheme.surfaceContainer,
            badgeFill = scheme.primaryContainer,
            badgeContent = scheme.onPrimaryContainer,
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            title = scheme.onSurface,
            body = scheme.onSurfaceVariant,
            muted = scheme.onSurfaceVariant.copy(alpha = 0.75f),
            track = scheme.surfaceContainerLow,
            outline = scheme.outlineVariant,
            error = scheme.error,
            borderBrush = null
        )
    } else {
        UpdatePopupColors(
            materialYou = false,
            surface = nebula.surface.copy(alpha = 0.97f),
            cardFill = nebula.accent.copy(alpha = 0.10f),
            softFill = nebula.textPrimary.copy(alpha = 0.045f),
            badgeFill = nebula.accent.copy(alpha = 0.18f),
            badgeContent = nebula.accent,
            accent = nebula.accent,
            onAccent = Color.White,
            title = nebula.textPrimary,
            body = nebula.textSecondary,
            muted = nebula.textTertiary,
            track = nebula.textPrimary.copy(alpha = 0.10f),
            outline = nebula.textPrimary.copy(alpha = 0.16f),
            error = nebula.statusError,
            borderBrush = Brush.linearGradient(
                listOf(
                    nebula.accent.copy(alpha = 0.42f),
                    nebula.textPrimary.copy(alpha = 0.10f)
                )
            )
        )
    }
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
            .background(colors.accent.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Иконку красим через LocalContentColor, а не в месте вызова: Surface
        // диалога не входит в цветовую схему Material, поэтому contentColorFor
        // не срабатывает и значок без tint рисуется чёрным.
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides colors.accent) {
                icon()
            }
        }
        Text(text, color = colors.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
