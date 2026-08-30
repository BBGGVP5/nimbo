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
            .padding(top = 58.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

            val description = if (state.profileCount > 0) {
                "${state.serverCount} серверов"
            } else {
                "Добавьте подписку или конфигурацию"
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = description,
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
                text = if (state.profileCount > 0) "${state.serverCount} серверов / \u221E" else "Готово к импорту",
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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

            Box(
                modifier = Modifier
                    .size(174.dp)
                    .clip(CircleShape)
                    .background(centerFill)
                    .border(1.dp, centerBorder, CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = actions.onToggleVpn
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (connecting) {
                    Canvas(modifier = Modifier.size(62.dp)) {
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
                        modifier = Modifier.size(if (connected) 58.dp else 64.dp)
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
                fontSize = 24.sp,
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
    val shape = RoundedCornerShape(16.dp)
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
                .height(56.dp)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenProfiles
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val flag = flagEmoji(selected?.name.orEmpty())
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(NimboPalette.Soft),
                contentAlignment = Alignment.Center
            ) {
                if (flag.isNotBlank()) {
                    BasicText(
                        flag,
                        style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = NimboPalette.Text)
                    )
                } else {
                    NimboIcon(
                        NimboIconName.SITE,
                        tint = NimboPalette.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = selected?.name?.takeIf { it.isNotBlank() } ?: "Выберите сервер",
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
                .size(56.dp)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
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

/** Флаг из названия сервера: панели ставят его первым символом. */
private fun flagEmoji(name: String): String {
    val trimmed = name.trimStart()
    if (trimmed.length < 4) return ""
    val first = trimmed.substring(0, 2)
    val second = trimmed.substring(2, 4)
    val isRegional = { pair: String ->
        pair.length == 2 && pair[0] == '\uD83C' && pair[1] in '\uDDE6'..'\uDDFF'
    }
    return if (isRegional(first) && isRegional(second)) first + second else ""
}

@Composable
private fun HomeMonitoring(state: NimboUiState) {
    NimboSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Мониторинг", style = NimboSectionTitleStyle.copy(fontSize = 16.sp))
                Spacer(Modifier.weight(1f))
                BasicText("⌃", style = TextStyle(color = NimboPalette.TextSecondary, fontSize = 20.sp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Hairline))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Скорость", if (state.vpnState == "connected") "↑ 0 КБ/с" else "Нет трафика", Modifier.weight(1f))
                MetricCard("Память", "— МБ", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(NimboPalette.Control).padding(14.dp)) {
        Column {
            BasicText(title, style = NimboBodyStyle.copy(fontSize = 12.sp))
            BasicText(value, style = TextStyle(color = NimboPalette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}
