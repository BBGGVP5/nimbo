package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

private enum class SettingsTab(val title: String, val icon: NimboIconName) {
    GENERAL("Общие", NimboIconName.SETTINGS),
    APPEARANCE("Внешний вид", NimboIconName.PALETTE),
    SUBSCRIPTION("Подписка", NimboIconName.CLOUD),
    LATENCY("Пинг серверов", NimboIconName.PING),
    BACKUP("Резервная копия", NimboIconName.DOWNLOAD),
    UPDATES("Обновления", NimboIconName.SYNC),
    ABOUT("О приложении", NimboIconName.INFO)
}

@Composable
internal fun NimboSettingsScreen(state: NimboUiState, actions: NimboUiActions) {
    var tab by remember { mutableStateOf<SettingsTab?>(null) }
    // Each page starts at the top; a long appearance page must not offset the next page.
    key(tab) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val selected = tab
            if (selected == null) {
                BasicText("Настройки", style = NimboTitleStyle)
                if (state.updateVersion.isNotBlank()) {
                    SettingsSection("Обновление") {
                        SettingsRow(NimboIconName.DOWNLOAD, "Доступна ${state.updateVersion}",
                            value = state.updateStatus.takeIf { it.isNotBlank() }, onClick = { tab = SettingsTab.UPDATES })
                    }
                }
                SettingsSection("Подключение") {
                    actions.onOpenCoreSettings?.let { openCoreSettings ->
                        SettingsRow(NimboIconName.CONNECTION, "Ядро VPN",
                            "Выбор для следующего подключения", showDivider = true,
                            onClick = openCoreSettings)
                    }
                    SettingsRow(NimboIconName.PING, "Пинг серверов",
                        value = "${pingMethodTitle(state.pingProtocol)} · ${state.pingTimeoutMs / 1000.0} с",
                        showDivider = true, onClick = { tab = SettingsTab.LATENCY })
                    actions.onOpenOnDemandSettings?.let {
                        SettingsRow(NimboIconName.CONNECTION, "Автоподключение",
                            "Wi-Fi и сотовая сеть", showDivider = true, onClick = it)
                    }
                    SettingsRow(NimboIconName.ROUTE, "Маршрутизация", showDivider = true,
                        onClick = { actions.onOpenScreen(NimboScreen.ROUTING.wireName) })
                    SettingsRow(NimboIconName.CLOUD, "Подписка", onClick = { tab = SettingsTab.SUBSCRIPTION })
                }
                SettingsSection("Приложение") {
                    listOf(SettingsTab.GENERAL, SettingsTab.APPEARANCE, SettingsTab.BACKUP,
                        SettingsTab.UPDATES, SettingsTab.ABOUT).forEachIndexed { index, entry ->
                        SettingsRow(entry.icon, entry.title, showDivider = index < 4, onClick = { tab = entry })
                    }
                }
            } else {
                NimboSettingsBack("Настройки") { tab = null }
                BasicText(selected.title, style = NimboTitleStyle)
                when (selected) {
                    SettingsTab.GENERAL -> GeneralPage(state, actions)
                    SettingsTab.APPEARANCE -> AppearancePage(state, actions)
                    SettingsTab.SUBSCRIPTION -> SubscriptionPage(state, actions)
                    SettingsTab.LATENCY -> LatencyPage(state, actions)
                    SettingsTab.BACKUP -> BackupPage(actions)
                    SettingsTab.UPDATES -> UpdatesPage(state, actions)
                    SettingsTab.ABOUT -> SystemPage(state, actions)
                }
            }
        }
    }
}

@Composable
private fun GeneralPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Отклик") {
        AppearanceToggle("Виброотклик", state.appearance.haptics) { actions.onSetAppearance("haptics", it.toString()) }
    }
    SettingsSection("Соединение") {
        SettingsRow(NimboIconName.CONNECTION, "Системные настройки VPN", "Профиль Nimbo в настройках iOS",
            onClick = actions.onOpenSystemSettings)
    }
    SettingsSection("Синхронизация") {
        SettingsRow(NimboIconName.SYNC, "Перенос с другого устройства", "QR с компьютера или Android — подписки и настройки",
            onClick = actions.onOpenSync)
    }
    SettingsSection("Мониторинг") {
        AppearanceToggle("График скорости", state.showSpeedWidget, info = "Скорость и трафик текущей сессии") {
            actions.onSetAppearance("showSpeedWidget", it.toString())
        }
        SettingsDivider()
        AppearanceToggle("Память", state.showMemoryWidget, info = "Сколько занимает приложение") {
            actions.onSetAppearance("showMemoryWidget", it.toString())
        }
    }
}

@Composable
private fun AppearancePage(state: NimboUiState, actions: NimboUiActions) {
    NimboAppearanceDetails(state, actions)
    SettingsSection("Главная") {
        AppearanceToggle("Компактная кнопка подключения", state.connectStyle == "compact",
            info = "Широкая кнопка вместо круглой на главном экране") {
            actions.onSetAppearance("connectStyle", if (it) "compact" else "classic")
        }
    }
    SettingsSection("Движение") {
        AppearanceToggle("Анимация значков", state.navIconMotion) { actions.onSetAppearance("navIconMotion", it.toString()) }
    }
}

private fun pingMethodTitle(key: String): String = when (normalizePingProtocol(key)) {
    "tcp" -> "TCP"
    "http_get" -> "HTTP GET"
    "http_head" -> "HTTP HEAD"
    "icmp" -> "ICMP"
    else -> "Nimbo Ping"
}

@Composable
private fun LatencyPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Автоматический замер") {
        AppearanceToggle("Пинг при запуске", state.pingOnLaunch) { actions.onSetAppearance("pingOnLaunch", it.toString()) }
        AppearanceToggle("После обновления подписки", state.pingAfterRefresh) { actions.onSetAppearance("pingAfterRefresh", it.toString()) }
    }
    SettingsSection("Проверка") {
        NimboSettingsSelector("Метод", listOf(
            NimboDropdownOption("nimbo", "Nimbo Ping", "Проверяет каждый сервер отдельно без переключения VPN. Значение — оценка HTTP GET ÷ 3,3, а не точное RTT."),
            NimboDropdownOption("tcp", "TCP до узла", "Время установления соединения с портом сервера"),
            NimboDropdownOption("http_get", "HTTP GET", "Полное время GET к контрольному URL через активный VPN. Прямое соединение не подставляется."),
            NimboDropdownOption("http_head", "HTTP HEAD", "Полное время получения заголовков через активный VPN"),
            NimboDropdownOption("icmp", "ICMP Ping", "Echo-запрос к узлу; сервер может не отвечать")
        ), normalizePingProtocol(state.pingProtocol)) { actions.onSetPing("protocol", it) }
        SettingsDivider()
        val timeouts = listOf(1000, 2000, 3000, 5000, 10000).let {
            if (state.pingTimeoutMs in it) it else (it + state.pingTimeoutMs).sorted()
        }
        NimboSettingsSelector("Таймаут", timeouts.map {
            NimboDropdownOption(it.toString(), "${it / 1000.0} с")
        }, state.pingTimeoutMs.toString(), subtitle = "Время ожидания в секундах. Результат пинга отображается в миллисекундах.") {
            actions.onSetPing("timeoutMs", it)
        }
        SettingsDivider()
        PingTimeoutInput(state.pingTimeoutMs, actions)
        SettingsDivider()
        NimboSettingsSelector("Отображение", listOf(
            NimboDropdownOption("numeric", "Цифры (мс)"), NimboDropdownOption("bars", "Шкала"),
            NimboDropdownOption("both", "Шкала и цифры"), NimboDropdownOption("dots", "Точки")
        ), normalizePingDisplay(state.pingDisplay)) { actions.onSetPing("display", it) }
    }
    SettingsSection("Адрес проверки") {
        val presets = PingUrlChoice.entries.map { NimboDropdownOption(it.url, it.title, it.url) }
        NimboSettingsSelector("Контрольный URL", if (presets.none { it.key == state.pingUrl }) {
            presets + NimboDropdownOption(state.pingUrl, "Свой адрес", state.pingUrl)
        } else presets, state.pingUrl,
            subtitle = "Используется при HTTP-проверке. Адрес должен отвечать быстро и без переадресаций.") {
            actions.onSetPing("url", it)
        }
        SettingsDivider()
        CustomPingUrlRow(state, actions)
    }
}

/** UI seconds are converted once at the storage/service boundary. */
internal fun pingTimeoutMillisFromSeconds(value: String): Int? {
    val seconds = value.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!seconds.isFinite() || seconds !in 1.0..10.0) return null
    return (seconds * 1000).roundToInt()
}

@Composable
private fun PingTimeoutInput(timeoutMs: Int, actions: NimboUiActions) {
    var seconds by remember(timeoutMs) { mutableStateOf((timeoutMs / 1000.0).toString().removeSuffix(".0")) }
    val millis = pingTimeoutMillisFromSeconds(seconds)
    val focus = LocalFocusManager.current
    val save = {
        millis?.let { actions.onSetPing("timeoutMs", it.toString()) }
        focus.clearFocus()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Свой таймаут · секунды", style = NimboBodyStyle.copy(color = NimboPalette.Text))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(seconds, { seconds = it.take(8) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = "Таймаут в секундах" }
                    .nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
                singleLine = true, textStyle = NimboBodyStyle.copy(color = NimboPalette.Text),
                cursorBrush = SolidColor(NimboPalette.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (millis != null) save() }))
            NimboIconButton(NimboIconName.SAVE, Modifier.size(44.dp).semantics { contentDescription = "Сохранить таймаут" },
                enabled = millis != null, onClick = save)
        }
        BasicText(if (millis == null) "Введите от 1 до 10 секунд" else "От 1 до 10 с. Пинг отображается в мс.", style = NimboBodyStyle)
    }
}

@Composable
private fun CustomPingUrlRow(state: NimboUiState, actions: NimboUiActions) {
    val focusManager = LocalFocusManager.current
    var draft by remember(state.pingUrl) { mutableStateOf(state.pingUrl) }
    val trimmed = draft.trim()
    val ready = (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
        trimmed.substringAfter("://").substringBefore('/').isNotBlank() && trimmed.none { it.isWhitespace() }
    val save = { if (ready) actions.onSetPing("url", trimmed); focusManager.clearFocus() }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Свой адрес", style = NimboBodyStyle)
        BasicTextField(draft, { draft = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .semantics { contentDescription = "URL проверки пинга" }
                .nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
            textStyle = NimboBodyStyle.copy(color = NimboPalette.Text), cursorBrush = SolidColor(NimboPalette.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }))
        NimboSettingsAction("Сохранить URL", enabled = ready, onClick = save)
    }
}

private enum class PingUrlChoice(val title: String, val url: String) {
    GSTATIC("Google", "https://www.gstatic.com/generate_204"),
    CLOUDFLARE("Cloudflare", "https://cp.cloudflare.com/generate_204"),
    APPLE("Apple", "https://captive.apple.com/hotspot-detect.html")
}

@Composable
private fun SubscriptionPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Подписка") {
        AppearanceToggle("Обновлять при запуске", state.refreshOnLaunch, info = "Только при отключённом VPN") {
            actions.onSetAppearance("refreshOnLaunch", it.toString())
        }
        SettingsDivider()
        SettingsRow(NimboIconName.CLOUD, "Настройки подписки", "Обновление, описание и адрес источника",
            showDivider = true, onClick = actions.onOpenProfileSettings)
        SettingsRow(NimboIconName.REFRESH, "Обновить сейчас", "Перечитать список серверов у панели", onClick = actions.onRefreshProfile)
    }
}

@Composable
private fun UpdatesPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Версия") {
        SystemValue("Установлена", state.appVersion)
        if (state.updateVersion.isNotBlank()) SystemValue("Доступна", state.updateVersion)
        if (state.updateStatus.isNotBlank()) UpdateStatus(state.updateStatus)
        NimboSettingsAction("Проверить обновление", onClick = actions.onCheckUpdate)
        if (state.updateVersion.isNotBlank()) {
            if (state.updateDownloadStatus.isNotBlank()) UpdateStatus(state.updateDownloadStatus)
            NimboSettingsAction("Скачать файл сборки", onClick = actions.onDownloadUpdate)
            SettingsRow(NimboIconName.SITE, "Страница релиза", "Описание и все файлы сборки. Файл .ipa устанавливается тем же способом, которым установлена текущая версия.", onClick = actions.onOpenUpdate)
        }
    }
    SettingsSection("Канал") {
        NimboSettingsSelector("Сборки", listOf(
            NimboDropdownOption("beta", "Бета", "Предварительные сборки с новыми возможностями"),
            NimboDropdownOption("stable", "Стабильный", "Только готовые стабильные сборки")
        ), if (state.updateChannel == "stable") "stable" else "beta") { actions.onSetUpdate("channel", it) }
        SettingsDivider()
        AppearanceToggle("Сообщать о новых сборках", state.updateNotify) { actions.onSetUpdate("notify", it.toString()) }
    }
    val notes = com.danila.nimbo.shared.updates.ReleaseNotesText.forApp(state.updateNotes)
    if (notes.isNotBlank()) SettingsSection("Что изменилось") {
        var expanded by remember(notes) { mutableStateOf(false) }
        SelectionContainer { BasicText(notes, Modifier.padding(vertical = 12.dp), style = NimboBodyStyle,
            maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis) }
        NimboSettingsAction(if (expanded) "Свернуть" else "Все изменения", onClick = { expanded = !expanded })
    }
}

/** The bridge supplies status text, not a numeric progress model. Never simulate progress. */
@Composable
private fun UpdateStatus(status: String) {
    val error = status.contains("не удалось", ignoreCase = true) || status.contains("ошиб", ignoreCase = true)
    val percentage = Regex("(\\d{1,3})\\s*%").find(status)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer { BasicText(status, style = NimboBodyStyle.copy(color = if (error) NimboPalette.Red else NimboPalette.Text)) }
        if (percentage != null) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(NimboPalette.Border)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(percentage / 100f, 0f..1f) }) {
                if (percentage > 0) Box(Modifier.fillMaxWidth(percentage / 100f).height(4.dp).background(NimboPalette.Accent))
            }
        }
    }
}

@Composable
private fun BackupPage(actions: NimboUiActions) {
    SettingsSection("Резервная копия") {
        SettingsRow(NimboIconName.DOWNLOAD, "Сохранить копию", "Подписка и настройки одним файлом", showDivider = true, onClick = actions.onExportBackup)
        SettingsRow(NimboIconName.SYNC, "Восстановить из файла", "Заменит текущие настройки и подписку", onClick = actions.onImportBackup)
    }
}

@Composable
private fun SystemPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Nimbo") {
        SystemValue("Версия", state.appVersion)
        SystemValue("Устройство", state.deviceName)
        SystemValue("Система", state.systemName)
        SettingsRow(NimboIconName.INFO, "О приложении", onClick = actions.onOpenAbout)
    }
    SettingsSection("Система") {
        SettingsRow(NimboIconName.NOTIFICATIONS, "Уведомления", "История сообщений приложения", showDivider = true,
            onClick = { actions.onOpenScreen(NimboScreen.NOTIFICATIONS.wireName) })
        SettingsRow(NimboIconName.LOGS, "Диагностика", "Логи приложения и туннеля без секретов", showDivider = true, onClick = actions.onOpenDiagnostics)
        SettingsRow(NimboIconName.SETTINGS, "Язык и уведомления", "Задаются в настройках iOS", onClick = actions.onOpenSystemSettings)
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(title, Modifier.padding(start = 16.dp, top = 4.dp),
            style = NimboBodyStyle.copy(color = NimboPalette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Column { content() } }
    }
}

@Composable
internal fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Hairline))
}

@Composable
private fun SettingsRow(icon: NimboIconName, title: String, subtitle: String? = null,
    showDivider: Boolean = false, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.nimboClickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NimboIcon(icon, tint = NimboPalette.TextSecondary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text))
                if (!value.isNullOrBlank()) BasicText(value, style = NimboBodyStyle)
                if (!subtitle.isNullOrBlank()) BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
            if (onClick != null) BasicText("›", style = NimboBodyStyle)
        }
    }
    if (showDivider) SettingsDivider()
}

@Composable
private fun SystemValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = NimboBodyStyle)
        SelectionContainer { BasicText(value.ifBlank { "—" }, style = NimboBodyStyle.copy(color = NimboPalette.Text)) }
    }
}

@Composable
internal fun NimboSettingsBack(title: String, onClick: () -> Unit) {
    NimboSettingsAction("‹ $title", onClick = onClick)
}

@Composable
internal fun NimboSettingsAction(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .nimboClickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        BasicText(title, style = NimboBodyStyle.copy(color = if (enabled) NimboPalette.Text else NimboPalette.TextTertiary))
    }
}

@Composable
internal fun NimboSettingsInfo(title: String, message: String) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(44.dp).clip(CircleShape).nimboClickable { open = true }
        .semantics { contentDescription = "Информация: $title" }, contentAlignment = Alignment.Center) {
        NimboIcon(NimboIconName.INFO, tint = NimboPalette.TextSecondary, modifier = Modifier.size(20.dp))
    }
    if (open) NimboSettingsDialog(title, onDismiss = { open = false }) {
        SelectionContainer { BasicText(message, style = NimboBodyStyle.copy(color = NimboPalette.Text)) }
    }
}

@Composable
internal fun NimboSettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String = "Закрыть",
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
            NimboSurface(Modifier.fillMaxWidth().heightIn(max = maxHeight * .9f),
                cornerRadius = 20.dp, padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }, style = NimboSectionTitleStyle)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    footer?.invoke(this)
                    NimboSettingsAction(dismissLabel, Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            }
        }
    }
}

/** Confirmation actions stay reachable even when a name or explanation fills the body. */
@Composable
internal fun NimboSettingsConfirmation(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    NimboSettingsDialog(title, onDismiss, dismissLabel = "Отмена", footer = {
        NimboPrimaryAction(confirmLabel, onConfirm, Modifier.fillMaxWidth())
    }) {
        SelectionContainer { BasicText(message, style = NimboBodyStyle.copy(color = NimboPalette.Text)) }
    }
}

/** Custom bounded selector with focus, arrow keys, Escape and a distinct info action. */
@Composable
internal fun NimboSettingsSelector(title: String, options: List<NimboDropdownOption>, selectedKey: String,
    modifier: Modifier = Modifier, subtitle: String? = null, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val triggerFocus = remember { FocusRequester() }
    val selected = options.firstOrNull { it.key == selectedKey }
    val close: () -> Unit = { expanded = false; triggerFocus.requestFocus() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).focusRequester(triggerFocus)
            .clickable(role = Role.Button) { expanded = true }
            .semantics { stateDescription = selected?.title ?: selectedKey }
            .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text))
                BasicText(selected?.title ?: selectedKey.ifBlank { "Не выбрано" }, style = NimboBodyStyle)
            }
            BasicText("⌄", Modifier.padding(horizontal = 8.dp), style = NimboBodyStyle)
        }
        val info = listOfNotNull(subtitle, selected?.subtitle).filter { it.isNotBlank() }.joinToString("\n\n")
        if (info.isNotBlank()) NimboSettingsInfo(title, info)
    }
    if (expanded) {
        NimboSettingsDialog(title, onDismiss = close) {
            val focusManager = LocalFocusManager.current
            val choiceFocus = remember { FocusRequester() }
            Column(Modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) when (it.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
                    Key.Escape -> { close(); true }
                    else -> false
                } else false
            }) {
                options.forEachIndexed { index, option ->
                    val active = option.key == selectedKey
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 48.dp)
                            .then(if (active || (selected == null && index == 0)) Modifier.focusRequester(choiceFocus) else Modifier)
                            .selectable(active, role = Role.RadioButton) { onSelect(option.key); close() }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(option.title, Modifier.weight(1f), style = NimboBodyStyle.copy(color = NimboPalette.Text))
                            if (active) BasicText("✓", Modifier.padding(horizontal = 8.dp), style = NimboBodyStyle.copy(color = NimboPalette.Text))
                        }
                        if (!option.subtitle.isNullOrBlank()) NimboSettingsInfo(option.title, option.subtitle)
                    }
                    if (index < options.lastIndex) SettingsDivider()
                }
            }
            LaunchedEffect(Unit) { if (options.isNotEmpty()) choiceFocus.requestFocus() }
        }
    }
}

/** Названия эффектов в порядке индексов `backgroundStyleModeForIndex`. */
private enum class BackgroundStyleChoice(val title: String) {
    MORPHISM("Морфизм"),
    MATERIAL3("Material"),
    DOTS("Точки"),
    AURORA("Аврора"),
    GRID("Сетка"),
    MESH("Меш"),
    WAVES("Волны"),
    STARFIELD("Звёзды"),
    CYBERPUNK("Киберпанк"),
    DEEP_SPACE("Космос"),
    FIRE("Огонь"),
    LAVA("Лава"),
    NEON("Неон"),
    NORDIC("Север"),
    BLOSSOM("Цветение"),
    NONE("Без движения"),
    RAIN("Дождь"),
    ORBIT("Орбиты"),
    SIGNAL_FLOW("Сигнал")
}

/** Названия палитр в порядке индексов `backgroundPaletteModeForIndex`. */
private enum class BackgroundPaletteChoice(val title: String) {
    THEME("Как тема"),
    AURORA("Аврора"),
    CYBER("Кибер"),
    SPACE("Космос"),
    FIRE("Огонь"),
    LAVA("Лава"),
    NEON("Неон"),
    NORDIC("Север"),
    BLOSSOM("Цветение"),
    OCEAN("Океан"),
    SUNSET("Закат"),
    FOREST("Лес")
}

