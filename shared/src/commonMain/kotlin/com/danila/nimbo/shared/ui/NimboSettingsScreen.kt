package com.danila.nimbo.shared.ui

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
import androidx.compose.ui.text.TextStyle
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
/** Разделы настроек. Каждый открывается своей страницей, как на Android. */
private enum class SettingsPage(val title: String) {
    ROOT("Настройки"),
    APPEARANCE("Внешний вид"),
    LATENCY("Задержка"),
    CONNECTION("Соединение"),
    SUBSCRIPTION("Подписка"),
    UPDATES("Обновления"),
    SYNC("Синхронизация"),
    BACKUP("Резервная копия"),
    SYSTEM("Система")
}

@Composable
internal fun NimboSettingsScreen(state: NimboUiState, actions: NimboUiActions) {
    // Раздел живёт внутри вкладки: системная панель снизу остаётся на месте,
    // а «назад» возвращает к списку разделов.
    var page by remember { mutableStateOf(SettingsPage.ROOT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (page == SettingsPage.ROOT) {
            BasicText("Настройки", style = NimboTitleStyle)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NimboIconButton(
                    NimboIconName.BACK,
                    modifier = Modifier.size(40.dp),
                    onClick = { page = SettingsPage.ROOT }
                )
                Spacer(Modifier.width(10.dp))
                BasicText(page.title, style = NimboTitleStyle.copy(fontSize = 26.sp))
            }
        }

        when (page) {
            SettingsPage.ROOT -> SettingsRootPage(state, actions) { page = it }
            SettingsPage.APPEARANCE -> AppearancePage(state, actions)
            SettingsPage.LATENCY -> LatencyPage(state, actions)
            SettingsPage.CONNECTION -> ConnectionPage(actions)
            SettingsPage.SUBSCRIPTION -> SubscriptionPage(actions)
            SettingsPage.UPDATES -> UpdatesPage(state, actions)
            SettingsPage.SYNC -> SyncPage(actions)
            SettingsPage.BACKUP -> BackupPage(actions)
            SettingsPage.SYSTEM -> SystemPage(state, actions)
        }
    }
}

@Composable
private fun SettingsRootPage(
    state: NimboUiState,
    actions: NimboUiActions,
    onOpen: (SettingsPage) -> Unit
) {
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

    SettingsSection("Оформление") {
        SettingsRow(
            NimboIconName.SETTINGS,
            SettingsPage.APPEARANCE.title,
            "Стиль элементов, фон и виджеты мониторинга",
            onClick = { onOpen(SettingsPage.APPEARANCE) }
        )
    }

    SettingsSection("Сеть") {
        SettingsRow(
            NimboIconName.PING,
            SettingsPage.LATENCY.title,
            latencySummary(state),
            showDivider = true,
            onClick = { onOpen(SettingsPage.LATENCY) }
        )
        SettingsRow(
            NimboIconName.ROUTE,
            SettingsPage.CONNECTION.title,
            "Маршрутизация и профиль VPN в системе",
            onClick = { onOpen(SettingsPage.CONNECTION) }
        )
    }

    SettingsSection("Данные") {
        SettingsRow(
            NimboIconName.CLOUD,
            SettingsPage.SUBSCRIPTION.title,
            "Обновление списка серверов и настройки источника",
            showDivider = true,
            onClick = { onOpen(SettingsPage.SUBSCRIPTION) }
        )
        SettingsRow(
            NimboIconName.SYNC,
            SettingsPage.SYNC.title,
            "Перенос с компьютера или Android",
            showDivider = true,
            onClick = { onOpen(SettingsPage.SYNC) }
        )
        SettingsRow(
            NimboIconName.DOWNLOAD,
            SettingsPage.BACKUP.title,
            "Сохранить или восстановить файлом",
            onClick = { onOpen(SettingsPage.BACKUP) }
        )
    }

    SettingsSection("Приложение") {
        SettingsRow(
            NimboIconName.DOWNLOAD,
            SettingsPage.UPDATES.title,
            if (state.updateVersion.isBlank()) "Проверка новой сборки" else "Доступна ${state.updateVersion}",
            showDivider = true,
            onClick = { onOpen(SettingsPage.UPDATES) }
        )
        SettingsRow(
            NimboIconName.INFO,
            SettingsPage.SYSTEM.title,
            "${state.appVersion} · диагностика и сведения",
            onClick = { onOpen(SettingsPage.SYSTEM) }
        )
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

/** Короткая подпись раздела: видно настройку, не открывая её. */
private fun latencySummary(state: NimboUiState): String {
    val method = if (state.pingProtocol == "http") "HTTP через туннель" else "TCP до узла"
    return "$method · ${state.pingTimeoutMs} мс"
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
        PingUrlChoice.entries.forEachIndexed { index, choice ->
            SettingsChoiceRow(
                title = choice.title,
                subtitle = choice.url,
                selected = state.pingUrl == choice.url,
                onClick = { actions.onSetPing("url", choice.url) }
            )
            if (index != PingUrlChoice.entries.lastIndex) SettingsDivider()
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
private fun ConnectionPage(actions: NimboUiActions) {
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
private fun SyncPage(actions: NimboUiActions) {
    SettingsSection("Синхронизация") {
        SettingsRow(
            NimboIconName.SYNC,
            "Перенос с другого устройства",
            "QR с компьютера или Android — подписки и настройки",
            onClick = actions.onOpenSync
        )
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
        BasicText(title, style = NimboSectionTitleStyle)
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
