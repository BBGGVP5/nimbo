package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

internal val NimboIconName.accessibleLabel: String get() = when (this) {
    NimboIconName.ADD -> "Добавить"
    NimboIconName.REFRESH -> "Обновить подписку"
    NimboIconName.PING -> "Проверить пинг"
    NimboIconName.INFO -> "Информация"
    NimboIconName.MORE -> "Другие действия"
    NimboIconName.FAVORITE -> "Избранное"
    NimboIconName.FAVORITE_OFF -> "Добавить в избранное"
    NimboIconName.BACK -> "Назад"
    NimboIconName.DELETE -> "Удалить"
    NimboIconName.SAVE -> "Сохранить"
    NimboIconName.EDIT -> "Изменить"
    NimboIconName.COPY -> "Копировать"
    NimboIconName.DOWNLOAD -> "Скачать"
    NimboIconName.SHARE -> "Поделиться"
    NimboIconName.POWER -> "Подключение VPN"
    NimboIconName.NOTIFICATIONS -> "Уведомления"
    NimboIconName.SEARCH -> "Поиск"
    NimboIconName.SETTINGS -> "Настройки"
    NimboIconName.LIST -> "Список серверов"
    else -> name.lowercase().replace('_', ' ')
}

/** Same code-native cloud used by system icons; no baked square or lock. */
internal val NimboCloudVector: ImageVector by lazy {
    ImageVector.Builder("Nimbo cloud", 32.dp, 32.dp, 1024f, 1024f).addPath(
        pathData = PathParser().parsePathString("M320 704C240 704 184 647 184 574C184 492 245 414 349 414C374 334 441 282 527 282C618 282 681 350 692 438C767 431 839 488 839 570C839 600 835 620 819 633C806 658 644 630 611 576C605 559 600 542 603 512C581 525 581 558 594 585C619 638 681 673 766 689C741 700 713 704 686 704Z").toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

@Composable
internal fun NimboPageHeading(title: String, subtitle: String = "", actions: (@Composable RowScope.() -> Unit)? = null) {
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(title, style = NimboTitleStyle)
            if (subtitle.isNotBlank()) BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 13.sp))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (actions != null && maxWidth < (300 * scale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                heading(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                heading(Modifier.weight(1f))
                actions?.invoke(this)
            }
        }
    }
}

@Composable
internal fun NimboPrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: NimboIconName? = null) {
    val accent = NimboPalette.Accent
    val ink = if (accent.luminance() > .5f) Color(0xFF202020) else Color.White
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .background(accent.copy(alpha = if (enabled) 1f else .45f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) NimboIcon(icon, Modifier.size(if (icon == NimboIconName.CLOUD) 30.dp else 22.dp), ink)
            BasicText(text, style = NimboBodyStyle.copy(color = ink, fontWeight = FontWeight.SemiBold))
        }
    }
}

internal val NimboUiState.connectionBusy: Boolean
    get() = vpnState in setOf("preparing", "connecting", "disconnecting")

/** The brand cloud means an established connection, never a pending attempt. */
internal val NimboUiState.connectionIcon: NimboIconName
    get() = if (vpnState == "connected") NimboIconName.CLOUD else NimboIconName.POWER

internal val NimboUiState.connectionTitle: String get() = when (vpnState) {
    "connected" -> "Вы подключены"
    "preparing", "connecting" -> "Подключаемся…"
    "disconnecting" -> "Отключаемся…"
    "failed" -> "Не удалось подключиться"
    else -> "Готов к подключению"
}

@Composable
internal fun NimboConnectionPanel(state: NimboUiState, actions: NimboUiActions) {
    val connected = state.vpnState == "connected"
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var clickFeedback by remember { mutableStateOf(false) }
    var clickFeedbackKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(clickFeedbackKey) {
        if (clickFeedbackKey > 0) {
            kotlinx.coroutines.delay(130)
            clickFeedback = false
        }
    }
    val motion = rememberNimboConnectionMotion(connected, state.connectionBusy,
        pressed || clickFeedback, enabled = state.navIconMotion)
    val actionLabel = if (connected) "Отключить" else if (state.connectionBusy) "Подождите…" else "Подключить"
    val toggle = {
        clickFeedback = true
        clickFeedbackKey++
        if (state.servers.isEmpty() && !connected) actions.onAddProfile() else actions.onToggleVpn()
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (connected) NimboPalette.Accent else NimboPalette.TextTertiary))
            BasicText(if (connected) "VPN АКТИВЕН" else "ВАШЕ СОЕДИНЕНИЕ",
                style = NimboBodyStyle.copy(fontSize = 10.sp, textAlign = TextAlign.Center))
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val titleStyle = NimboSectionTitleStyle.copy(fontSize = 25.sp, lineHeight = 31.sp, textAlign = TextAlign.Center)
            // Respect large text while keeping whole words intact on a narrow phone.
            val widestWord = remember(state.connectionTitle, titleStyle, measurer) {
                state.connectionTitle.split(' ').maxOf { measurer.measure(it, titleStyle, softWrap = false).size.width }
            }
            val available = with(density) { maxWidth.toPx() }
            val fit = if (widestWord > available) (available / widestWord).coerceAtMost(1f) * .98f else 1f
            BasicText(state.connectionTitle, Modifier.fillMaxWidth(), style = titleStyle.copy(fontSize = 25.sp * fit, lineHeight = 31.sp * fit))
        }
        Spacer(Modifier.height(5.dp))
        BasicText(when {
            connected -> "Ваш трафик идёт через выбранный сервер"
            state.connectionBusy -> "Дождитесь завершения операции"
            state.vpnState == "failed" -> "Проверьте сеть или выберите другой сервер"
            else -> "Один шаг до подключения"
        }, style = NimboBodyStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Center))
        Spacer(Modifier.height(24.dp))
        if (state.connectStyle == "compact") {
            val fill = NimboPalette.Accent
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .graphicsLayer {
                    scaleX = motion.scale.value
                    scaleY = motion.scale.value
                    translationY = (1f - motion.scale.value) * 20.dp.toPx()
                }
                .clip(RoundedCornerShape(20.dp)).background(fill)
                .semantics { contentDescription = actionLabel }
                .clickable(enabled = !state.connectionBusy, role = Role.Button,
                    interactionSource = interactionSource, indication = null, onClick = toggle)
                .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val ink = if (fill.luminance() > .5f) Color(0xFF202020) else Color.White
                NimboIcon(state.connectionIcon, Modifier.size(24.dp), ink)
                BasicText(actionLabel, Modifier.weight(1f), style = NimboBodyStyle.copy(
                    color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
            }
        } else {
            val fill = NimboPalette.Accent
            Box(Modifier.size(132.dp).graphicsLayer {
                scaleX = motion.scale.value
                scaleY = motion.scale.value
                translationY = (1f - motion.scale.value) * 20.dp.toPx()
            }
                .border(1.dp, NimboPalette.Border, CircleShape), contentAlignment = Alignment.Center) {
                NimboConnectionHalo(motion, fill, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().padding(7.dp).clip(CircleShape).background(fill)
                    .semantics { contentDescription = actionLabel }
                    .clickable(enabled = !state.connectionBusy, role = Role.Button,
                        interactionSource = interactionSource, indication = null, onClick = toggle), contentAlignment = Alignment.Center) {
                    NimboIcon(state.connectionIcon, Modifier.size(if (connected) 56.dp else 34.dp)
                        .graphicsLayer { scaleX = motion.iconScale.value; scaleY = motion.iconScale.value },
                        tint = if (fill.luminance() > .5f) Color(0xFF202020) else Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (state.vpnState == "preparing" || state.vpnState == "connecting") {
            NimboBusyCaption(NimboBusyKind.CONNECTION, rotate = state.navIconMotion)
        } else {
            BasicText(when {
                connected -> listOf(state.connectionDuration, "Нажмите, чтобы отключить").filter(String::isNotBlank).joinToString(" · ")
                state.vpnState == "disconnecting" -> "Дождитесь отключения"
                else -> "Нажмите, чтобы подключиться"
            },
                style = NimboBodyStyle.copy(fontSize = 11.sp, textAlign = TextAlign.Center))
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Border))
        val selected = state.servers.firstOrNull { it.id == state.activeServerId || it.selected }
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clickable(role = Role.Button) { actions.onOpenScreen(NimboScreen.PROFILES.wireName) }
            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(NimboPalette.Soft), contentAlignment = Alignment.Center) {
                NimboIcon(NimboIconName.SITE, Modifier.size(19.dp), NimboPalette.TextSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(withoutFlagEmoji(selected?.name ?: state.activeServerName), style = NimboBodyStyle.copy(color = NimboPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
                BasicText(listOf(state.activeProfileName.takeIf { state.profileCount > 0 }, selected?.connectionLabel).filterNotNull().joinToString(" · "),
                    style = NimboBodyStyle.copy(fontSize = 11.sp))
                if (selected != null && LocalDensity.current.fontScale > 1.3f) NimboPingBadge(selected, quiet = true)
            }
            if (selected != null && LocalDensity.current.fontScale <= 1.3f) NimboPingBadge(selected, quiet = true)
            BasicText("›", style = NimboSectionTitleStyle)
        }
        if (state.vpnState == "failed" && !state.errorMessage.isNullOrBlank()) {
            BasicText(state.errorMessage, style = NimboBodyStyle.copy(color = NimboPalette.Red))
            NimboLinkButton(NimboIconName.LOGS, "Открыть диагностику", actions.onOpenDiagnostics)
        }
    }
}

internal fun serverCountLabel(count: Int): String {
    val unit = when { count % 100 in 11..14 -> "серверов"; count % 10 == 1 -> "сервер"; count % 10 in 2..4 -> "сервера"; else -> "серверов" }
    return "$count $unit"
}

@Composable
private fun SubscriptionAction(icon: NimboIconName, text: String, enabled: Boolean = true, description: String = icon.accessibleLabel, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(NimboPalette.Control)
        .semantics { contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        NimboIcon(icon, Modifier.size(17.dp), NimboPalette.TextSecondary)
        BasicText(text, style = NimboBodyStyle.copy(fontSize = 12.sp))
    }
}

/** Expansion is independent of all info/refresh/ping actions. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun NimboSubscriptionHeader(state: NimboUiState, actions: NimboUiActions, expanded: Boolean, onToggle: () -> Unit) {
    var info by remember(state.activeProfileName) { mutableStateOf(false) }
    if (info) NimboSubscriptionInfo(state, actions) { info = false }
    NimboSurface(Modifier.fillMaxWidth().testTag("subscription-card")
        .semantics { stateDescription = if (expanded) "Серверы показаны" else "Серверы скрыты" },
        padding = PaddingValues(14.dp), onClick = onToggle) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("subscription-header"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(NimboPalette.Accent), contentAlignment = Alignment.Center) {
                        BasicText(state.activeProfileName.trim().take(2).uppercase(), style = NimboSectionTitleStyle.copy(color = if (NimboPalette.Accent.luminance() > .5f) Color(0xFF202020) else Color.White))
                    }
                    Column(Modifier.weight(1f)) {
                        BasicText(state.activeProfileName, style = NimboSectionTitleStyle)
                        BasicText(serverCountLabel(state.serverCount), style = NimboBodyStyle.copy(fontSize = 12.sp))
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f), tint = NimboPalette.TextSecondary)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = "Информация" }.clickable(role = Role.Button) { info = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(21.dp), tint = NimboPalette.Text)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (state.profileTrafficLabel.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NimboIcon(NimboIconName.STATS, Modifier.size(15.dp), NimboPalette.TextSecondary)
                    BasicText(state.profileTrafficLabel, style = NimboBodyStyle.copy(fontSize = 12.sp))
                }
                if (state.profileExpiryLabel.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(15.dp), tint = NimboPalette.TextSecondary)
                    BasicText(state.profileExpiryLabel, style = NimboBodyStyle.copy(fontSize = 12.sp))
                }
            }
            state.remainingQuotaFraction?.let { remaining ->
                Box(Modifier.fillMaxWidth().height(2.dp).background(NimboPalette.Border)
                    .semantics {
                        contentDescription = "Остаток трафика"
                        progressBarRangeInfo = ProgressBarRangeInfo(remaining, 0f..1f)
                    }) {
                    if (remaining > 0f) Box(Modifier.fillMaxWidth(remaining).height(2.dp).background(NimboPalette.Accent.copy(alpha = .65f)))
                }
            }
            if (state.profileAnnounce.isNotBlank()) {
                BasicText(state.profileAnnounce.trim(), maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubscriptionAction(NimboIconName.PING, if (state.pingInProgress) "Остановить пинг" else "Пинг", description = if (state.pingInProgress) "Остановить пинг" else NimboIconName.PING.accessibleLabel, onClick = actions.onPingAll)
                SubscriptionAction(NimboIconName.REFRESH, "Обновить", onClick = actions.onRefreshProfile)
            }
            if (state.profileUpdatedLabel.isNotBlank()) BasicText("Обновлено ${state.profileUpdatedLabel}", style = NimboBodyStyle.copy(fontSize = 11.sp))
        }
    }
}

internal val NimboUiState.remainingQuotaFraction: Float?
    get() = if (profileTrafficTotal > 0) {
        ((profileTrafficTotal - profileTrafficUsed.coerceAtLeast(0)).coerceAtLeast(0).toDouble() / profileTrafficTotal).toFloat()
    } else null

@Composable
internal fun NimboSubscriptionInfo(state: NimboUiState, actions: NimboUiActions, onDismiss: () -> Unit) {
    NimboSettingsDialog("О подписке", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(51.dp).clip(RoundedCornerShape(14.dp)).background(NimboPalette.Accent), contentAlignment = Alignment.Center) {
                BasicText(state.activeProfileName.trim().take(2).uppercase(), style = NimboSectionTitleStyle.copy(
                    color = if (NimboPalette.Accent.luminance() > .5f) Color(0xFF202020) else Color.White))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(state.activeProfileName, style = NimboSectionTitleStyle.copy(fontSize = 20.sp))
                BasicText("Информация о подписке", style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
        }
        NimboSubscriptionFacts(state)
        if (state.profileAnnounce.isNotBlank()) {
            SettingsDivider()
            androidx.compose.foundation.text.selection.SelectionContainer {
                BasicText(state.profileAnnounce, style = NimboBodyStyle)
            }
        }
        SettingsDivider()
        state.supportUrl?.takeIf(String::isNotBlank)?.let { url ->
            NimboLinkButton(NimboIconName.SUPPORT, "Поддержка") { actions.onOpenUrl(url) }
        }
        state.websiteUrl?.takeIf(String::isNotBlank)?.let { url ->
            NimboLinkButton(NimboIconName.SITE, "Сайт провайдера") { actions.onOpenUrl(url) }
        }
        NimboLinkButton(NimboIconName.SETTINGS, "Настройки подписки") { onDismiss(); actions.onOpenProfileSettings() }
    }
}

/** Facts are provider-backed; unknown quota and expiry never become demo numbers. */
@Composable
internal fun NimboSubscriptionFacts(state: NimboUiState) {
    val facts = listOf(
        Triple(NimboIconName.STATS, state.profileTrafficLabel.ifBlank { "Нет данных" }, "Трафик подписки"),
        Triple(NimboIconName.INFO, state.profileExpiryLabel.ifBlank { "Не указан" }, "Срок действия"),
        Triple(NimboIconName.LIST, serverCountLabel(state.serverCount), "Доступные серверы"),
        Triple(NimboIconName.REFRESH, state.profileUpdatedLabel.ifBlank { "Ещё не обновлялась" }, "Последнее обновление")
    )
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= (260 * fontScale).dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            facts.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { (icon, value, label) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(NimboPalette.Control).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NimboIcon(icon, Modifier.size(18.dp), NimboPalette.TextSecondary)
                            BasicText(value, style = NimboBodyStyle.copy(color = NimboPalette.Text, fontWeight = FontWeight.SemiBold))
                            BasicText(label, style = NimboBodyStyle.copy(fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}
