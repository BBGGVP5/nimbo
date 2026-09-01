package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboHomeScreen(
    state: NimboUiState,
    actions: NimboUiActions,
    onOpenProfiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeProfileCard(state, actions)
        HomeConnectionButton(state, actions)
        HomeSelectedServer(state, onOpenProfiles)
        HomeMonitoring(state)
    }
}

@Composable
private fun HomeProfileCard(state: NimboUiState, actions: NimboUiActions) {
    // Вёрстка повторяет SubscriptionOverviewPanel: заголовок со щитом,
    // строка ссылок, разделитель и счётчик трафика внизу.
    NimboSurface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        cornerRadius = 22.dp,
        padding = PaddingValues(16.dp),
        onClick = actions.onRefreshProfile
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NimboIcon(
                        NimboIconName.SECURITY,
                        tint = NimboPalette.Text,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = state.activeProfileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = NimboPalette.Text,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                NimboIconButton(
                    name = NimboIconName.REFRESH,
                    modifier = Modifier.size(38.dp),
                    enabled = state.profileCount > 0,
                    onClick = actions.onRefreshProfile
                )
            }

            val description = when {
                state.profileCount == 0 -> "Добавьте подписку или конфигурацию"
                state.profileAnnounce.isNotBlank() -> state.profileAnnounce
                state.profileExpiryLabel.isNotBlank() -> state.profileExpiryLabel
                else -> "${state.serverCount} серверов"
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = description,
                // Объявления бывают на десяток строк — карточка не должна
                // превращаться в стену текста.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = NimboPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )

            val support = state.supportUrl?.takeIf { it.isNotBlank() }
            val website = state.websiteUrl?.takeIf { it.isNotBlank() }
            if (support != null || website != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (support != null) {
                        NimboLinkButton(NimboIconName.SUPPORT, "Поддержка") { actions.onOpenUrl(support) }
                    }
                    if (website != null) {
                        NimboLinkButton(NimboIconName.SITE, "Сайт") { actions.onOpenUrl(website) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Hairline))
            Spacer(Modifier.height(9.dp))

            BasicText(
                // Внизу карточки — потраченный трафик, как на Android.
                // «Серверов: N» остаётся запасным вариантом, пока панель не
                // прислала свои цифры.
                text = when {
                    state.profileCount == 0 -> "Готово к импорту"
                    state.profileTrafficLabel.isNotBlank() -> state.profileTrafficLabel
                    else -> "${state.serverCount} серверов"
                },
                maxLines = 1,
                style = TextStyle(
                    color = NimboPalette.Text.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }
    }
}

@Composable
private fun HomeConnectionButton(state: NimboUiState, actions: NimboUiActions) {
    val connected = state.vpnState == "connected"
    val connecting = state.vpnState in setOf("preparing", "connecting", "disconnecting")
    val failed = state.vpnState == "failed"
    val accent = NimboPalette.Accent
    val interaction = remember { MutableInteractionSource() }
    val style = LocalNimboElementStyle.current
    val isManga = style == NimboElementStyle.MANGA

    // Геометрия и цвета повторяют WindowsConnectionButton на Android в стиле
    // Liquid Glass: два кольца, свечение под ними и круг-кнопка внутри.
    val ringColor = if (connected || connecting) accent else Color.White
    val outerAlpha = when {
        connected -> 0.42f
        connecting -> 0.34f
        else -> 0.10f
    }
    val innerAlpha = when {
        connected -> 0.28f
        connecting -> 0.42f
        else -> 0.14f
    }
    val centerFill = if (connected) accent else NimboPalette.Surface.copy(alpha = 0.54f)
    val centerBorder = when {
        connected -> accent.copy(alpha = 0.55f)
        connecting -> accent.copy(alpha = 0.24f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val iconTint = if (connected) Color.White else Color.White.copy(alpha = 0.72f)

    val rotation = if (connecting) {
        val infinite = rememberInfiniteTransition(label = "nimbo-connect")
        val animated by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "nimbo-connect-rotation"
        )
        animated
    } else {
        0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            if (!isManga) Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = ringColor.copy(alpha = outerAlpha),
                    radius = radius - 2.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = ringColor.copy(alpha = innerAlpha),
                    radius = radius * 0.82f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                if (connected || connecting) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (connected) 0.16f else 0.12f),
                                accent.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 0.98f
                        ),
                        radius = radius * 0.98f,
                        center = center
                    )
                }
            }

            val centerShape = if (isManga) RoundedCornerShape(4.dp) else CircleShape
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(centerShape)
                    .background(
                        if (isManga) {
                            if (connected) NimboPalette.Accent else NimboMangaPalette.Paper
                        } else centerFill
                    )
                    .border(
                        if (isManga) 2.dp else 1.dp,
                        if (isManga) {
                            if (connected || connecting) NimboPalette.Accent else NimboMangaPalette.Ink
                        } else centerBorder,
                        centerShape
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = actions.onToggleVpn
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (connecting) {
                    Canvas(modifier = Modifier.size(58.dp)) {
                        val strokeWidth = 7.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        drawArc(
                            color = NimboPalette.Text.copy(alpha = 0.08f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = accent,
                            startAngle = rotation - 92f,
                            sweepAngle = 112f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                } else {
                    NimboIcon(
                        name = if (connected) NimboIconName.SECURITY else NimboIconName.POWER,
                        tint = iconTint,
                        modifier = Modifier.size(if (connected) 54.dp else 58.dp)
                    )
                }
            }
        }

        BasicText(
            text = when (state.vpnState) {
                "connected" -> "Защищено"
                "preparing" -> "Подготавливаем VPN…"
                "connecting" -> "Подключение…"
                "disconnecting" -> "Отключение…"
                "failed" -> "Не удалось подключиться"
                else -> "Нажмите для подключения"
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = if (connected || connecting || failed) accent else NimboPalette.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        )
        if (failed && (!state.errorCode.isNullOrBlank() || !state.errorMessage.isNullOrBlank())) {
            NimboSurface(cornerRadius = 20.dp, padding = PaddingValues(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    state.errorCode?.let {
                        BasicText(it, style = TextStyle(color = NimboPalette.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                    state.errorMessage?.let { BasicText(it, style = NimboBodyStyle.copy(textAlign = TextAlign.Center)) }
                }
            }
        }
    }
}

@Composable
private fun HomeSelectedServer(state: NimboUiState, onOpenProfiles: () -> Unit) {
    // Повторяет WindowsSelectedServerBar: полоса 56 dp, флаг в мягком квадрате,
    // название и пилюля пинга, справа — кнопка списка.
    val selected = state.servers.firstOrNull { it.id == state.activeServerId }
        ?: state.servers.firstOrNull()
    val hasServer = selected != null
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(16.dp, 3.dp)
    val fill = if (hasServer) {
        NimboPalette.Accent.copy(alpha = 0.08f).compositeOver(NimboPalette.Control)
    } else {
        NimboPalette.Control
    }
    val border = if (hasServer) NimboPalette.Accent.copy(alpha = 0.34f) else NimboPalette.Border

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(shape)
                .background(nimboStyledContainer(fill, selected = hasServer))
                .border(
                    if (style == NimboElementStyle.MANGA) 2.dp else 1.dp,
                    nimboStyledBorder(border, selected = hasServer),
                    shape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenProfiles
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(nimboStyledShape(11.dp, 2.dp))
                    .background(nimboStyledContainer(NimboPalette.Soft)),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(
                    NimboIconName.SITE,
                    tint = NimboPalette.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = withoutFlagEmoji(selected?.name.orEmpty()).ifBlank { "Выберите сервер" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (selected != null) {
                Spacer(Modifier.width(8.dp))
                NimboPill(selected.pingLabel)
            }
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(shape)
                .background(nimboStyledContainer(fill))
                .border(
                    if (style == NimboElementStyle.MANGA) 1.5.dp else 1.dp,
                    nimboStyledBorder(border),
                    shape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenProfiles
                ),
            contentAlignment = Alignment.Center
        ) {
            NimboIcon(
                NimboIconName.LIST,
                tint = NimboPalette.TextSecondary,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}



@Composable
private fun HomeMonitoring(state: NimboUiState) {
    // Мониторинг показывается только на живом подключении — как на Android,
    // где виджеты привязаны к состоянию CONNECTED.
    if (state.vpnState != "connected") return
    if (!state.showSpeedWidget && !state.showMemoryWidget) return
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                "Мониторинг",
                style = TextStyle(
                    color = NimboPalette.TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                if (expanded) "\u2303" else "\u2304",
                style = TextStyle(color = NimboPalette.TextTertiary, fontSize = 16.sp)
            )
        }
        if (!expanded) return@Column

        if (state.showSpeedWidget) {
            Spacer(Modifier.height(10.dp))
            NetworkSpeedChartCard(
                samples = state.speedSamples,
                uploadSpeed = state.uploadSpeed,
                downloadSpeed = state.downloadSpeed
            )
            Spacer(Modifier.height(8.dp))
            SessionTrafficBlocks(upload = state.uploadTotal, download = state.downloadTotal)
        }
        if (state.showMemoryWidget && state.memoryMb > 0) {
            Spacer(Modifier.height(10.dp))
            MemoryUsageCard(memoryMb = state.memoryMb, samples = state.memorySamples)
        }
    }
}

@Composable
private fun NetworkSpeedChartCard(
    samples: List<NimboSpeedSample>,
    uploadSpeed: Long,
    downloadSpeed: Long
) {
    MonitorPanel(modifier = Modifier.fillMaxWidth().height(112.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NimboIcon(
                        NimboIconName.STATS,
                        tint = NimboPalette.TextTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    BasicText(
                        "Скорость",
                        maxLines = 1,
                        style = TextStyle(
                            color = NimboPalette.TextTertiary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                SpeedValue("\u2191", formatSpeed(uploadSpeed), NimboPalette.Green)
                Spacer(Modifier.width(10.dp))
                SpeedValue("\u2193", formatSpeed(downloadSpeed), NimboPalette.Accent)
            }
            Spacer(Modifier.height(8.dp))
            SpeedChartCanvas(
                samples = samples,
                modifier = Modifier.fillMaxWidth().height(62.dp)
            )
        }
    }
}

@Composable
private fun SpeedValue(arrow: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(arrow, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(3.dp))
        BasicText(
            text,
            maxLines = 1,
            style = TextStyle(color = color, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun SpeedChartCanvas(samples: List<NimboSpeedSample>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val peak = samples
            .flatMap { listOf(it.upload, it.download) }
            .maxOrNull()
            ?.coerceAtLeast(1L)
            ?.toFloat() ?: 1f

        fun buildPath(selector: (NimboSpeedSample) -> Long): Path {
            val path = Path()
            val count = samples.size.coerceAtLeast(2)
            samples.forEachIndexed { index, sample ->
                val x = if (count <= 1) 0f else size.width * index / (count - 1)
                val y = size.height - (selector(sample).toFloat() / peak).coerceIn(0f, 1f) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        fun buildArea(line: Path): Path = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        val downPath = buildPath { it.download }
        val upPath = buildPath { it.upload }
        drawPath(
            path = buildArea(downPath),
            brush = Brush.verticalGradient(
                listOf(NimboPalette.Accent.copy(alpha = 0.26f), Color.Transparent)
            )
        )
        drawPath(
            path = downPath,
            color = NimboPalette.Accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = buildArea(upPath),
            brush = Brush.verticalGradient(
                listOf(NimboPalette.Green.copy(alpha = 0.18f), Color.Transparent)
            )
        )
        drawPath(
            path = upPath,
            color = NimboPalette.Green,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun SessionTrafficBlocks(upload: Long, download: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SessionTrafficBlock("\u2191", "Отдано", formatBytes(upload), NimboPalette.Green, Modifier.weight(1f))
        SessionTrafficBlock("\u2193", "Скачано", formatBytes(download), NimboPalette.Accent, Modifier.weight(1f))
    }
}

@Composable
private fun SessionTrafficBlock(
    arrow: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    MonitorPanel(modifier = modifier.height(68.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(arrow, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(6.dp))
                BasicText(
                    label,
                    style = TextStyle(
                        color = NimboPalette.TextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            BasicText(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
                style = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun MemoryUsageCard(memoryMb: Int, samples: List<Int>) {
    MonitorPanel(modifier = Modifier.fillMaxWidth().height(88.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Память",
                    style = TextStyle(
                        color = NimboPalette.TextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    "$memoryMb МБ",
                    style = TextStyle(
                        color = NimboPalette.Text,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(38.dp)) {
                if (samples.isEmpty()) return@Canvas
                // Потолок берём с запасом и от нуля: без этого ровный ряд
                // одинаковых значений рисовался прямой по самому верху.
                val peak = ((samples.maxOrNull() ?: 1) * 1.35f).coerceAtLeast(1f)
                val count = samples.size.coerceAtLeast(2)
                val path = Path()
                samples.forEachIndexed { index, value ->
                    val x = if (count <= 1) 0f else size.width * index / (count - 1)
                    val y = size.height - (value / peak).coerceIn(0f, 1f) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = NimboPalette.Accent.copy(alpha = 0.85f),
                    style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/** Панель мониторинга: то же стекло, но скругление 16 dp и рамка White 10%. */
@Composable
private fun MonitorPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .nimboGlassSurface(
                shape = shape,
                depth = LiquidGlassDepth.PANEL,
                accent = NimboPalette.Accent,
                isDark = true,
                panelAlpha = 1f
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    ) {
        content()
    }
}

private fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/с"

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    val rounded = if (value >= 100.0 || unit == 0) {
        value.toLong().toString()
    } else {
        val scaled = (value * 10).toLong()
        "${scaled / 10}.${scaled % 10}"
    }
    return "$rounded ${units[unit]}"
}
