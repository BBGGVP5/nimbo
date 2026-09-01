package com.danila.nimbo.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Настройки iOS повторяют андроидные разделами, но содержат только то, что на
 * этой платформе работает: оформление фона (его рисует общий модуль), состав
 * мониторинга и системные пункты. Раздельного туннеля, языка интерфейса и
 * резервных копий здесь нет — им на iOS не на что опереться.
 */
/** Разделы настроек: те же вкладки, что и в ленте на Android. */
private enum class SettingsTab(val title: String, val icon: NimboIconName) {
    GENERAL("Общие", NimboIconName.SETTINGS),
    APPEARANCE("Внешний вид", NimboIconName.PALETTE),
    SUBSCRIPTION("Подписка", NimboIconName.CLOUD),
    LATENCY("Задержка", NimboIconName.PING),
    BACKUP("Резервная копия", NimboIconName.DOWNLOAD),
    UPDATES("Обновления", NimboIconName.SYNC),
    ABOUT("О приложении", NimboIconName.INFO)
}

@Composable
internal fun NimboSettingsScreen(state: NimboUiState, actions: NimboUiActions) {
    var tab by remember { mutableStateOf(SettingsTab.GENERAL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText("Настройки", style = NimboTitleStyle)

        if (state.updateVersion.isNotBlank()) {
            NimboSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                padding = PaddingValues(16.dp),
                onClick = actions.onOpenUpdate
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            "Доступна версия ${state.updateVersion}",
                            style = TextStyle(
                                color = NimboPalette.Accent,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        // Установить обновление из приложения iOS не даёт:
                        // сборку подписывают снаружи. Ведём на страницу релиза.
                        BasicText(
                            "Открыть страницу релиза и скачать сборку",
                            modifier = Modifier.padding(top = 2.dp),
                            style = TextStyle(
                                color = NimboPalette.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                    BasicText("›", style = TextStyle(color = NimboPalette.Accent, fontSize = 20.sp))
                }
            }
        }

        SettingsTabStrip(selected = tab, onSelect = { tab = it })

        when (tab) {
            SettingsTab.GENERAL -> GeneralPage(state, actions)
            SettingsTab.APPEARANCE -> AppearancePage(state, actions)
            SettingsTab.SUBSCRIPTION -> SubscriptionPage(actions)
            SettingsTab.LATENCY -> LatencyPage(state, actions)
            SettingsTab.BACKUP -> BackupPage(actions)
            SettingsTab.UPDATES -> UpdatesPage(state, actions)
            SettingsTab.ABOUT -> SystemPage(state, actions)
        }
    }
}

/**
 * Лента вкладок. Выбранная раскрывается подписью — так на узком экране
 * помещается вдвое больше разделов, чем со всеми подписями сразу.
 */
@Composable
private fun SettingsTabStrip(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        padding = PaddingValues(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTab.entries.forEach { entry ->
                SettingsTabItem(
                    tab = entry,
                    selected = entry == selected,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}

@Composable
private fun SettingsTabItem(tab: SettingsTab, selected: Boolean, onClick: () -> Unit) {
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(12.dp, 2.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(
                nimboStyledContainer(
                    if (selected) NimboPalette.Accent.copy(alpha = 0.16f) else Color.Transparent,
                    selected = selected
                )
            )
            .then(
                if (style == NimboElementStyle.MANGA && selected) {
                    Modifier.border(2.dp, NimboPalette.Accent, shape)
                } else Modifier
            )
            .nimboRowClickable(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NimboIcon(
            tab.icon,
            tint = if (selected) NimboPalette.Text else NimboPalette.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        // Подпись выезжает из-под значка, а не появляется рывком: лента при
        // переключении заметно перестраивается, и резкая смена сбивает глаз.
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = shrinkHorizontally(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                BasicText(
                    tab.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = NimboPalette.Text,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }
    }
}

@Composable
private fun GeneralPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Соединение") {
        SettingsRow(
            NimboIconName.ROUTE,
            "Маршрутизация",
            "Обход локальных сетей, DNS и определение доменов",
            showDivider = true,
            onClick = { actions.onOpenScreen(NimboScreen.ROUTING.wireName) }
        )
        SettingsRow(
            NimboIconName.CONNECTION,
            "Системные настройки VPN",
            "Профиль Nimbo в настройках iOS",
            onClick = actions.onOpenSystemSettings
        )
    }

    SettingsSection("Синхронизация") {
        SettingsRow(
            NimboIconName.SYNC,
            "Перенос с другого устройства",
            "QR с компьютера или Android — подписки и настройки",
            onClick = actions.onOpenSync
        )
    }

    SettingsSection("Мониторинг") {
        SettingsRowFrame(height = 52.dp) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsTitle("График скорости")
                SettingsSubtitle("Скорость и трафик текущей сессии")
            }
            NimboSwitch(state.showSpeedWidget) {
                actions.onSetAppearance("showSpeedWidget", it.toString())
            }
        }
        SettingsDivider()
        SettingsRowFrame(height = 52.dp) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsTitle("Память")
                SettingsSubtitle("Сколько занимает приложение")
            }
            NimboSwitch(state.showMemoryWidget) {
                actions.onSetAppearance("showMemoryWidget", it.toString())
            }
        }
    }
}

@Composable
private fun AppearancePage(state: NimboUiState, actions: NimboUiActions) {
    BasicText("Стиль интерфейса", style = NimboSectionTitleStyle)
    BasicText(
        "Переключает визуальный слой: поверхности, кнопки, поля",
        style = NimboBodyStyle
    )
    NimboElementStyle.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { style ->
                NimboStylePreviewCard(
                    style = style,
                    selected = state.elementStyle == style.key,
                    onClick = { actions.onSetAppearance("elementStyle", style.key) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Нечётный последний стиль занимает ряд целиком, иначе половина
            // ряда остаётся пустой и выглядит обрывом.
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    BasicText("Стиль подключения", style = NimboSectionTitleStyle)
    BasicText("Форма главной кнопки на домашнем экране", style = NimboBodyStyle)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ConnectStylePreviewCard(
            title = "Классический",
            subtitle = "Кольцо во весь экран",
            compact = false,
            selected = state.connectStyle != "compact",
            onClick = { actions.onSetAppearance("connectStyle", "classic") },
            modifier = Modifier.weight(1f)
        )
        ConnectStylePreviewCard(
            title = "Компактный",
            subtitle = "Полоса вместо кольца",
            compact = true,
            selected = state.connectStyle == "compact",
            onClick = { actions.onSetAppearance("connectStyle", "compact") },
            modifier = Modifier.weight(1f)
        )
    }

    SettingsSection("Фон") {
        SettingsRowFrame(height = 52.dp) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsTitle("Движение фона")
                SettingsSubtitle("Выключите, чтобы фон замер и экономил батарею")
            }
            NimboSwitch(state.backgroundMotion) {
                actions.onSetAppearance("backgroundMotion", it.toString())
            }
        }
        SettingsDivider()
        SettingsRowFrame(height = 52.dp) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsTitle("Статусные частицы")
                SettingsSubtitle("Зелёные при подключении, красные при отключении")
            }
            NimboSwitch(state.statusParticles) {
                actions.onSetAppearance("statusParticles", it.toString())
            }
        }
        SettingsDivider()
        SettingsRowFrame(height = 52.dp) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsTitle("Анимация значков")
                SettingsSubtitle("Значки панели подпрыгивают при переходе")
            }
            NimboSwitch(state.navIconMotion) {
                actions.onSetAppearance("navIconMotion", it.toString())
            }
        }
        SettingsDivider()
        BackgroundPicker(
            title = "Эффект",
            items = BackgroundStyleChoice.entries.map { it.title },
            selectedIndex = state.backgroundStyle,
            paletteIndex = state.backgroundPalette,
            previewStyleFor = { index -> backgroundStyleModeForIndex(index) },
            previewPaletteFor = { backgroundPaletteModeForIndex(state.backgroundPalette) },
            onSelect = { actions.onSetAppearance("backgroundStyle", it.toString()) }
        )
        SettingsDivider()
        BackgroundPicker(
            title = "Палитра",
            items = BackgroundPaletteChoice.entries.map { it.title },
            selectedIndex = state.backgroundPalette,
            paletteIndex = state.backgroundPalette,
            previewStyleFor = { backgroundStyleModeForIndex(state.backgroundStyle) },
            previewPaletteFor = { index -> backgroundPaletteModeForIndex(index) },
            onSelect = { actions.onSetAppearance("backgroundPalette", it.toString()) }
        )
    }

}

@Composable
private fun LatencyPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Способ замера") {
        // ICMP на iOS недоступен обычному приложению — нужны raw-сокеты,
        // которых система не даёт. Поэтому выбор из двух, а не из трёх.
        SettingsChoiceRow(
            title = "TCP до узла",
            subtitle = "Время установления соединения с портом сервера",
            selected = state.pingProtocol != "http",
            onClick = { actions.onSetPing("protocol", "tcp") }
        )
        SettingsDivider()
        SettingsChoiceRow(
            title = "HTTP через туннель",
            subtitle = "Запрос к адресу проверки: показывает задержку рабочего маршрута",
            selected = state.pingProtocol == "http",
            onClick = { actions.onSetPing("protocol", "http") }
        )
    }

    SettingsSection("Таймаут") {
        BasicText(
            "Сколько ждать ответа, прежде чем считать узел молчащим",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = NimboBodyStyle
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1000, 2000, 3000, 5000).forEach { value ->
                NimboPill(
                    "${value / 1000} с",
                    modifier = Modifier.weight(1f),
                    selected = state.pingTimeoutMs == value,
                    onClick = { actions.onSetPing("timeoutMs", value.toString()) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    SettingsSection("Адрес проверки") {
        BasicText(
            "Используется при замере по HTTP. Подходит любой адрес, отвечающий быстро и без переадресаций.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = NimboBodyStyle
        )
        PingUrlChoice.entries.forEach { choice ->
            SettingsChoiceRow(
                title = choice.title,
                subtitle = choice.url,
                selected = state.pingUrl == choice.url,
                onClick = { actions.onSetPing("url", choice.url) }
            )
            SettingsDivider()
        }
        CustomPingUrlRow(state, actions)
    }
}

/**
 * Поле для своего адреса.
 *
 * Значение применяется по кнопке, а не по каждому нажатию клавиши: иначе
 * недописанный адрес успевал уйти в настройки и первый же замер уходил в
 * никуда.
 */
@Composable
private fun CustomPingUrlRow(state: NimboUiState, actions: NimboUiActions) {
    val isPreset = PingUrlChoice.entries.any { it.url == state.pingUrl }
    var draft by remember(state.pingUrl) {
        mutableStateOf(if (isPreset) "" else state.pingUrl)
    }
    val trimmed = draft.trim()
    // Без схемы запрос не уйдёт, поэтому кнопка ждёт полный адрес.
    val ready = trimmed.startsWith("http://") || trimmed.startsWith("https://")

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsTitle("Свой адрес")
            Spacer(Modifier.width(8.dp))
            if (!isPreset && state.pingUrl.isNotBlank()) {
                BasicText(
                    "✓",
                    style = TextStyle(
                        color = NimboPalette.Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        SettingsSubtitle("Например, страница отклика вашего сервера")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .nimboControlSurface(nimboStyledShape(14.dp, 2.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(color = NimboPalette.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(NimboPalette.Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (ready) actions.onSetPing("url", trimmed) }
                    ),
                    decorationBox = { inner ->
                        if (draft.isBlank()) {
                            BasicText(
                                "https://example.com/health",
                                style = NimboBodyStyle.copy(fontSize = 15.sp)
                            )
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            NimboPill(
                "Готово",
                selected = ready,
                onClick = { if (ready) actions.onSetPing("url", trimmed) }
            )
        }
    }
}

/** Проверенные адреса: отвечают пустым 204 и не тянут содержимое. */
private enum class PingUrlChoice(val title: String, val url: String) {
    GSTATIC("Google", "https://www.gstatic.com/generate_204"),
    CLOUDFLARE("Cloudflare", "https://cloudflare.com/cdn-cgi/trace"),
    APPLE("Apple", "https://captive.apple.com/hotspot-detect.html")
}

@Composable
private fun SubscriptionPage(actions: NimboUiActions) {
    SettingsSection("Подписка") {
        SettingsRow(
            NimboIconName.CLOUD,
            "Настройки подписки",
            "Обновление, описание и адрес источника",
            showDivider = true,
            onClick = actions.onOpenProfileSettings
        )
        SettingsRow(
            NimboIconName.REFRESH,
            "Обновить сейчас",
            "Перечитать список серверов у панели",
            onClick = actions.onRefreshProfile
        )
    }
}

@Composable
private fun UpdatesPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Обновления") {
        SettingsRow(
            NimboIconName.DOWNLOAD,
            if (state.updateVersion.isBlank()) "Проверить обновление" else "Доступна ${state.updateVersion}",
            // Ставить обновление сама iOS не даст: только открыть страницу
            // релиза, где лежит файл для переподписи.
            "Открыть страницу релиза",
            onClick = actions.onOpenUpdate
        )
    }
    if (state.updateNotes.isNotBlank()) {
        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(16.dp)
        ) {
            Column {
                BasicText("Что изменилось", style = NimboSectionTitleStyle)
                Spacer(Modifier.height(6.dp))
                BasicText(state.updateNotes, style = NimboBodyStyle)
            }
        }
    }
}

@Composable
private fun BackupPage(actions: NimboUiActions) {
    SettingsSection("Резервная копия") {
        SettingsRow(
            NimboIconName.DOWNLOAD,
            "Сохранить копию",
            "Подписка и настройки одним файлом",
            showDivider = true,
            onClick = actions.onExportBackup
        )
        SettingsRow(
            NimboIconName.SYNC,
            "Восстановить из файла",
            "Заменит текущие настройки и подписку",
            onClick = actions.onImportBackup
        )
    }
}

@Composable
private fun SystemPage(state: NimboUiState, actions: NimboUiActions) {
    SettingsSection("Система") {
        SettingsRow(
            NimboIconName.NOTIFICATIONS,
            "Уведомления",
            "История сообщений приложения",
            showDivider = true,
            onClick = { actions.onOpenScreen(NimboScreen.NOTIFICATIONS.wireName) }
        )
        SettingsRow(
            NimboIconName.LOGS,
            "Диагностика",
            "Логи приложения и туннеля без секретов",
            showDivider = true,
            onClick = actions.onOpenDiagnostics
        )
        SettingsRow(
            NimboIconName.INFO,
            "О приложении",
            "${state.appVersion} · ${state.systemName}",
            showDivider = true,
            onClick = actions.onOpenAbout
        )
        SettingsRow(
            NimboIconName.NOTIFICATIONS,
            "Язык и уведомления",
            // Своего переключателя языка на iOS нет: система задаёт язык
            // приложения сама, и честнее отвести туда, чем показывать
            // настройку, которая ничего не меняет.
            "Задаются в настройках iOS",
            onClick = actions.onOpenSystemSettings
        )
    }

    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemValue("Устройство", state.deviceName)
            SystemValue("Система", state.systemName)
            SystemValue("Версия", state.appVersion)
        }
    }
}

/**
 * Плитка выбора формы кнопки подключения.
 *
 * Слова «классический» и «компактный» сами по себе ничего не показывают —
 * человек не видит разницы, пока не переключит. Миниатюра показывает форму
 * сразу, ровно как карточки стилей интерфейса.
 */
@Composable
private fun ConnectStylePreviewCard(
    title: String,
    subtitle: String,
    compact: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalNimboElementStyle.current
    val manga = style == NimboElementStyle.MANGA
    val shape = nimboStyledShape(18.dp, 3.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    NimboPalette.Accent.copy(alpha = 0.13f)
                } else if (manga) {
                    NimboMangaPalette.Paper
                } else {
                    NimboPalette.Surface
                }
            )
            .border(
                if (manga) {
                    if (selected) 2.5.dp else 1.5.dp
                } else 1.dp,
                if (selected) NimboPalette.Accent.copy(alpha = 0.74f) else nimboStyledBorder(NimboPalette.Border),
                shape
            )
            .nimboRowClickable(onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(nimboStyledShape(14.dp, 2.dp))
                .background(if (manga) NimboMangaPalette.PaperDeep else NimboPalette.Background),
            contentAlignment = Alignment.Center
        ) {
            if (compact) {
                // Полоса: та же геометрия, что у настоящей кнопки.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(26.dp)
                        .clip(nimboStyledShape(9.dp, 2.dp))
                        .background(NimboPalette.Accent)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(nimboStyledShape(27.dp, 3.dp))
                        .border(
                            3.dp,
                            NimboPalette.Accent,
                            nimboStyledShape(27.dp, 3.dp)
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            title,
            style = TextStyle(
                color = if (selected) NimboPalette.Text else NimboPalette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 11.sp))
    }
}

/** Строка выбора одного варианта из нескольких. */
@Composable
private fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nimboRowClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SettingsTitle(title)
            SettingsSubtitle(subtitle)
        }
        Spacer(Modifier.width(10.dp))
        BasicText(
            if (selected) "✓" else "",
            style = TextStyle(
                color = NimboPalette.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
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

/**
 * Плитки-превью рисуются тем же кодом, что и настоящий фон, — иначе выбор
 * вслепую: названия «Меш» и «Морфизм» сами по себе ничего не говорят.
 */
@Composable
private fun BackgroundPicker(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    paletteIndex: Int,
    previewStyleFor: (Int) -> BackgroundStyleMode,
    previewPaletteFor: (Int) -> BackgroundPaletteMode,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        SettingsTitle(title)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val shape = nimboStyledShape(14.dp, 2.dp)
                val colors = backgroundPaletteColors(
                    previewPaletteFor(index),
                    NimboPalette.Accent,
                    isLight = false
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clip(shape)
                        .nimboRowClickable { onSelect(index) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(shape)
                            .background(NimboPalette.Background)
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) NimboPalette.Accent else NimboPalette.Hairline,
                                shape
                            )
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawNimboBackgroundMotion(
                                mode = previewStyleFor(index),
                                phase = 0.12f,
                                colors = colors,
                                isLight = false,
                                intensity = 1.7f,
                                detail = 0.5f
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (selected) NimboPalette.Text else NimboPalette.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // В Manga заголовок начинается с косой красной засечки: без неё
            // раздел не отличить от обычной подписи.
            if (LocalNimboElementStyle.current == NimboElementStyle.MANGA) NimboMangaSlash()
            BasicText(title, style = NimboSectionTitleStyle)
        }
        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRowFrame(height: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun SettingsTitle(text: String) {
    BasicText(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = NimboPalette.Text,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal
        )
    )
}

@Composable
private fun SettingsSubtitle(text: String) {
    BasicText(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
        style = TextStyle(
            color = NimboPalette.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    )
}

@Composable
private fun SettingsDivider() {
    val style = LocalNimboElementStyle.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (style == NimboElementStyle.MANGA) 1.5.dp else 1.dp)
            .background(
                if (style == NimboElementStyle.MANGA) NimboMangaPalette.Ink.copy(alpha = 0.34f)
                else Color.White.copy(alpha = 0.06f)
            )
    )
}

@Composable
private fun NimboSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    NimboToggle(checked = checked, onChange = onChange)
}

@Composable
private fun SettingsRow(
    icon: NimboIconName,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 40.dp else 52.dp)
            .then(
                if (onClick != null) {
                    Modifier.nimboRowClickable(onClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NimboIcon(icon, tint = NimboPalette.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SettingsTitle(title)
            if (subtitle != null) SettingsSubtitle(subtitle)
        }
        BasicText(
            "›",
            style = TextStyle(color = NimboPalette.TextTertiary, fontSize = 18.sp)
        )
    }
    if (showDivider) SettingsDivider()
}

@Composable
private fun SystemValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = NimboBodyStyle)
        Spacer(Modifier.weight(1f))
        BasicText(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = NimboPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
    }
}
