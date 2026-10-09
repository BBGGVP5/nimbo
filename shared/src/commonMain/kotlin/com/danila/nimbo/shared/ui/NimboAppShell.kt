package com.danila.nimbo.shared.ui

import com.danila.nimbo.shared.updates.ReleaseDefaults

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

data class NimboUiState(
    val appearance: NimboAppearance = NimboAppearance(),
    val vpnState: String = "idle",
    /** Measured native overlay height in points; content scrolls clear of the floating bar. */
    val nativeBottomClearance: Float = 0f,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val activeProfileName: String = "Подписка не добавлена",
    val activeServerName: String = "Выберите сервер",
    val serverCount: Int = 0,
    val profileCount: Int = 0,
    /** Host subscription operation, shared by the refresh button and pull gesture. */
    val profileRefreshing: Boolean = false,
    val deviceName: String = "iPhone",
    val systemName: String = "iOS",
    val appVersion: String = ReleaseDefaults.VERSION,
    val appBundleIds: String = "",
    val activeServerId: String? = null,
    val servers: List<NimboServerUi> = emptyList(),
    /** Ссылка поддержки провайдера; пусто — кнопка не показывается. */
    val supportUrl: String? = null,
    /** Сайт подписки; пусто — кнопка не показывается. */
    val websiteUrl: String? = null,
    val favoriteServerIds: Set<String> = emptySet(),
    /** Байты в секунду по туннелю; считаются по счётчикам utun-интерфейса. */
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    /** Накоплено за текущую сессию подключения. */
    val uploadTotal: Long = 0,
    val downloadTotal: Long = 0,
    /** Cumulative core route bytes; null means this core cannot report them. */
    val routeTraffic: NimboRouteTraffic? = null,
    val tcpConnections: Int? = null,
    val udpConnections: Int? = null,
    val sessionAvailable: Boolean? = null,
    /** Saved preference applies at the next connection. */
    val adBlockingEnabled: Boolean = false,
    val activeAdBlockingEnabled: Boolean? = null,
    val speedSamples: List<NimboSpeedSample> = emptyList(),
    /** Память процесса приложения, МБ. */
    val memoryMb: Int = 0,
    val memorySamples: List<Int> = emptyList(),
    /** Локальные сети идут мимо туннеля. */
    val routingBypassLocal: Boolean = true,
    /** Ядро читает имя сайта из соединения (нужно для правил по доменам). */
    val routingSniffing: Boolean = true,
    /** Ключ набора DNS: cloudflare / google / adguard / system. */
    val routingDns: String = "cloudflare",
    /** «Использовано / всего» из заголовка subscription-userinfo. */
    val profileTrafficLabel: String = "",
    /** Quota is shown only when the provider supplied a positive limit. */
    val profileTrafficUsed: Long = 0,
    val profileTrafficTotal: Long = 0,
    val connectionDuration: String = "",
    /** Срок действия подписки оттуда же. */
    val profileExpiryLabel: String = "",
    /** Когда подписка обновлялась последний раз. */
    val profileUpdatedLabel: String = "",
    /** Объявление провайдера из заголовка announce. */
    val profileAnnounce: String = "",
    /** Задержка до сервера, мс; -1 — не ответил. Ключ — идентификатор. */
    val pings: Map<String, Int> = emptyMap(),
    /** Идёт замер: пилюли показывают многоточие вместо старых цифр. */
    val pingInProgress: Boolean = false,
    /** Завершённые сессии, самые свежие первыми. */
    val sessions: List<NimboSessionUi> = emptyList(),
    /** Движение фона: индекс стиля из backgroundStyleModeForIndex. */
    val backgroundStyle: Int = 0,
    /** Палитра фона: индекс из backgroundPaletteModeForIndex. */
    val backgroundPalette: Int = 0,
    val backgroundMotion: Boolean = false,
    /** Прыжок значков нижней панели при переходе между вкладками. */
    val navIconMotion: Boolean = true,
    val showSpeedWidget: Boolean = true,
    val showMemoryWidget: Boolean = true,
    val pingOnLaunch: Boolean = true,
    val pingAfterRefresh: Boolean = true,
    val refreshOnLaunch: Boolean = false,
    /** Стиль элементов: glass / material / dotted / signal. */
    val elementStyle: String = "glass",
    /** Порядок серверов: subscription / ping / name. */
    val serverSort: String = "subscription",
    /** Избранные всегда сверху, независимо от выбранного порядка. */
    val favoritesFirst: Boolean = true,
    /** Версия доступного обновления; пусто — обновлений нет. */
    /** Как мерить задержку: «tcp» до узла или «http» через туннель. */
    /** Событие для частиц: номер растёт с каждым новым, вид задаёт цвет. */
    val burstEventId: Long = 0,
    val burstTrigger: String = "activity",
    /** Статусные частицы можно выключить, как на Android. */
    val statusParticles: Boolean = false,
    /** Стиль главной кнопки: «classic» — кольцо, «compact» — полоса. */
    val connectStyle: String = "classic",
    /** История уведомлений и сообщение, которое показывается сейчас. */
    val notifications: List<NimboNotification> = emptyList(),
    val toast: NimboNotification? = null,
    /** Пользовательские наборы правил. */
    val modules: List<com.danila.nimbo.shared.routing.NimboModule> = emptyList(),
    /** Профили маршрутизации и тот, что выбран сейчас. */
    val routingProfiles: List<com.danila.nimbo.shared.routing.NimboRoutingProfile> = emptyList(),
    val routingProfileId: String = "global",
    val pingProtocol: String = "nimbo",
    val pingDisplay: String = "numeric",
    val pingTimeoutMs: Int = 3000,
    val pingUrl: String = "https://www.gstatic.com/generate_204",
    val updateVersion: String = "",
    val updateNotes: String = "",
    /** Канал обновлений: `beta` — вместе с предварительными сборками. */
    val updateChannel: String = ReleaseDefaults.UPDATE_CHANNEL,
    /** Сообщать ли о новой сборке уведомлением. */
    val updateNotify: Boolean = true,
    /** Итог последней проверки для подписи под кнопкой. */
    val updateStatus: String = "",
    /** Ход загрузки файла сборки: проценты или итог. */
    val updateDownloadStatus: String = ""
)

/** Завершённая сессия подключения для экрана статистики. */
data class NimboSessionUi(
    val startedAt: String,
    val duration: String,
    val download: Long,
    val upload: Long
)

/** Одно измерение скорости: показания за секунду. */
data class NimboSpeedSample(
    val upload: Long,
    val download: Long
)

data class NimboServerUi(
    val id: String,
    val name: String,
    val protocol: String,
    val transport: String = "",
    val security: String = "",

    val selected: Boolean = false,
    /** Задержка до сервера, мс: -1 — не ответил, null — ещё не мерили. */
    val ping: Int? = null,
    val pingInProgress: Boolean = false,
    /** Описание из подписки; пусто — показываем протокол и транспорт. */
    val description: String = ""
) {
    /** Подпись пилюли: «— ms», пока не мерили, и «×», если узел молчит. */
    val pingLabel: String
        get() = when {
            pingInProgress -> "…"
            ping == null -> "— ms"
            ping < 0 -> "×"
            else -> "$ping ms"
        }

    val connectionLabel: String
        get() = listOf(protocol.uppercase(), transport.uppercase(), security.replaceFirstChar { it.uppercase() })
            .filter(String::isNotBlank)
            .joinToString(" · ")
}

data class NimboUiActions(
    val onSetAdBlocking: (Boolean) -> Unit = {},
    val onToggleVpn: () -> Unit = {},
    val onAddProfile: () -> Unit = {},
    val onRefreshProfile: () -> Unit = {},
    val onOpenProfileSettings: () -> Unit = {},
    val onSelectServer: (String) -> Unit = {},
    val onSaveAppRule: (String) -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onOpenSystemSettings: () -> Unit = {},
    /** Открыть ссылку во внешнем браузере. */
    val onOpenUrl: (String) -> Unit = {},
    val onToggleFavorite: (String) -> Unit = {},
    /** Измерить задержку до одного сервера. */
    val onPingServer: (String) -> Unit = {},
    /** Перемерить весь список сразу. */
    val onPingAll: () -> Unit = {},
    /** Замерить узлы и подключиться к самому быстрому. */
    val onConnectFastest: () -> Unit = {},
    /** Настройка маршрутизации: ключ и новое значение строкой. */
    val onSetRouting: (String, String) -> Unit = { _, _ -> },
    /** Переход на вкладку: на iOS её показывает системная панель. */
    val onOpenScreen: (String) -> Unit = {},
    /** Настройка оформления: ключ и новое значение строкой. */
    val onSetAppearance: (String, String) -> Unit = { _, _ -> },
    /** Настройка замера задержки: protocol, timeoutMs или url. */
    val onSetPing: (String, String) -> Unit = { _, _ -> },
    /** Сохранить модуль: идентификатор, имя и текст правил. */
    val onSaveModule: (String, String, String) -> Unit = { _, _, _ -> },
    val onToggleModule: (String) -> Unit = {},
    val onDeleteModule: (String) -> Unit = {},
    /** Положить текст в буфер обмена. */
    val onCopyText: (String) -> Unit = {},
    /** Отдать модуль файлом: имя набора и текст правил. */
    val onExportModule: (String, String) -> Unit = { _, _ -> },
    /** Профили маршрутизации: выбор, правка и возврат к исходным наборам. */
    val onSelectRoutingProfile: (String) -> Unit = {},
    val onSaveRoutingProfile: (com.danila.nimbo.shared.routing.NimboRoutingProfile) -> Unit = {},
    val onResetRoutingProfiles: () -> Unit = {},
    /** Скрыть всплывающее сообщение. */
    val onDismissToast: () -> Unit = {},
    val onDeleteNotification: (String) -> Unit = {},
    val onClearNotifications: () -> Unit = {},
    /** Открыть страницу релиза: установить обновление сама iOS не даст. */
    val onOpenUpdate: () -> Unit = {},
    /** Спросить у GitHub, есть ли сборка новее установленной. */
    val onCheckUpdate: () -> Unit = {},
    /** Скачать файл сборки и отдать его системному окну сохранения. */
    val onDownloadUpdate: () -> Unit = {},
    /** Настройки обновлений: `channel`, `notify`. */
    val onSetUpdate: (String, String) -> Unit = { _, _ -> },
    /** Импорт подписки из введённой ссылки или конфигурации. */
    val onImportSubscription: (String) -> Unit = {},
    /** Импорт из буфера обмена. */
    val onImportClipboard: () -> Unit = {},
    /** Импорт из файла профиля. */
    val onImportFile: () -> Unit = {},
    /** Сканировать QR-код с подпиской. */
    val onScanQr: () -> Unit = {},
    /** Сохранить копию настроек и подписки в файл. */
    val onExportBackup: () -> Unit = {},
    /** Восстановить копию из файла. */
    val onImportBackup: () -> Unit = {},
    /** Открыть перенос данных с другого устройства. */
    val onOpenSync: () -> Unit = {},
    /** Native on-demand settings; absent when the platform has no such sheet. */
    val onOpenOnDemandSettings: (() -> Unit)? = null,
    /** Native core selector; absent on clients that do not provide this sheet. */
    val onOpenCoreSettings: (() -> Unit)? = null
)

@Composable
fun NimboAppShell(
    initialScreen: NimboScreen,
    state: NimboUiState,
    actions: NimboUiActions,
    /**
     * На iOS панель рисует система своим материалом — единственный способ
     * получить настоящее размытие фона, поэтому здесь её отключают.
     */
    showBottomBar: Boolean = true,
    /** Вкладка снаружи: когда панель системная, выбор приходит от неё. */
    externalScreen: NimboScreen? = null
) {
    var internalScreen by remember(initialScreen) { mutableStateOf(initialScreen) }
    val selectedScreen = externalScreen ?: internalScreen
    val routedActions = actions.copy(onOpenScreen = { wireName ->
        internalScreen = NimboScreen.fromWireName(wireName)
        actions.onOpenScreen(wireName)
    })
    val appearance = state.appearance.normalized().copy(brightness = 1f, transparency = 0f, corners = 1f)
    val systemDark = isSystemInDarkTheme()
    val dark = appearance.isDark(systemDark)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wideNavigation = showBottomBar && maxWidth >= 1280.dp
    CompositionLocalProvider(
        LocalNimboContentBottom provides if (showBottomBar && !wideNavigation) 116.dp else maxOf(16f, state.nativeBottomClearance).dp,
        LocalNimboContentTop provides if (showBottomBar) 24.dp else 16.dp,
        LocalNimboPingDisplay provides normalizePingDisplay(state.pingDisplay),
        LocalNimboPingProtocol provides normalizePingProtocol(state.pingProtocol),
        LocalNimboElementStyle provides NimboElementStyle.NIMBO_GLASS,
        LocalNimboAppearance provides appearance,
        LocalNimboDark provides dark,
        LocalNimboColors provides nimboColors(appearance, systemDark),
        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)
    ) {
    MaterialTheme(
        colorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = NimboPalette.Accent,
            secondary = NimboPalette.AccentStrong,
            background = NimboPalette.BackgroundDeep,
            surface = NimboPalette.Surface,
            onPrimary = NimboPalette.BackgroundDeep,
            onBackground = NimboPalette.Text,
            onSurface = NimboPalette.Text
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Universal-v2 has one matte canvas; persisted legacy styles cannot replace it.
            Box(Modifier.fillMaxSize().background(NimboPalette.Background))

            Row(Modifier.align(Alignment.TopCenter)
                .widthIn(max = if (wideNavigation) 1080.dp else 840.dp).fillMaxSize()) {
            if (wideNavigation) {
                NimboDesktopNavigation(selectedScreen, { internalScreen = it },
                    Modifier.width(248.dp).fillMaxHeight())
            }
            AnimatedContent(
                targetState = selectedScreen,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
                label = "nimbo-primary-screen"
            ) { screen ->
                when (screen) {
                    NimboScreen.HOME -> NimboHomeScreen(
                        state = state,
                        actions = routedActions,
                        onOpenProfiles = { routedActions.onOpenScreen(NimboScreen.PROFILES.wireName) }
                    )
                    NimboScreen.PROFILES -> NimboProfilesScreen(state, routedActions)
                    NimboScreen.STATS -> NimboStatsScreen(state, routedActions)
                    NimboScreen.ROUTING -> NimboRoutingScreen(state, routedActions)
                    NimboScreen.MODULES -> NimboModulesScreen(state, routedActions)
                    NimboScreen.ROUTING_PROFILES -> NimboRoutingProfilesScreen(state, routedActions)
                    NimboScreen.NOTIFICATIONS -> NimboNotificationsScreen(state, routedActions)
                    NimboScreen.SETTINGS -> NimboSettingsScreen(state, routedActions)
                }
            }
            }

            // Сообщение висит поверх содержимого и над панелью: на Android оно
            // тоже перекрывает экран, иначе его не замечают.
            state.toast?.let { toast ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp)
                        .nimboScreenPadding()
                ) {
                    NimboToast(toast, onDismiss = actions.onDismissToast)
                }
            }

            if (showBottomBar && !wideNavigation) {
                NimboBottomNavigation(
                    selected = selectedScreen,
                    onSelected = { internalScreen = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
    }
    }
}

private val DesktopNavigationSections = listOf(
    "Основное" to listOf(NimboScreen.HOME, NimboScreen.PROFILES, NimboScreen.STATS),
    "Инструменты" to listOf(NimboScreen.ROUTING, NimboScreen.MODULES,
        NimboScreen.ROUTING_PROFILES, NimboScreen.NOTIFICATIONS),
    "Приложение" to listOf(NimboScreen.SETTINGS)
)

@Composable
private fun NimboDesktopNavigation(selected: NimboScreen, onSelected: (NimboScreen) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 16.dp, top = 24.dp, end = 12.dp, bottom = 24.dp)
        .clip(RoundedCornerShape(24.dp)).background(NimboPalette.Surface)
        .border(1.dp, NimboPalette.Border, RoundedCornerShape(24.dp))
        .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText("nimbo", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 20.dp),
            style = NimboSectionTitleStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
        DesktopNavigationSections.forEachIndexed { index, (heading, screens) ->
            if (index > 0) Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(NimboPalette.Border))
            BasicText(heading.uppercase(), Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp),
                style = NimboBodyStyle.copy(color = NimboPalette.TextTertiary, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold))
            screens.forEach { screen ->
                val active = screen == selected
                val shape = RoundedCornerShape(14.dp)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
                    .background(if (active) NimboPalette.Soft else Color.Transparent)
                    .semantics {
                        contentDescription = "Навигация: ${screen.title}"
                        if (active) stateDescription = "Текущая страница"
                    }
                    .clickable(enabled = !active, role = Role.Button) { onSelected(screen) }
                    .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NimboIcon(screen.iconName, Modifier.size(20.dp),
                        if (active) NimboPalette.Accent else NimboPalette.TextSecondary)
                    BasicText(screen.title, style = NimboBodyStyle.copy(
                        color = if (active) NimboPalette.Text else NimboPalette.TextSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium))
                }
            }
        }
    }
}

@Composable
private fun NimboBottomNavigation(
    selected: NimboScreen,
    onSelected: (NimboScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalNimboElementStyle.current
    val outerShape = nimboStyledShape(32.dp, 3.dp)
    // На Android эта панель размывает фон настоящим блюром; на iOS его нет,
    // поэтому под стеклом лежит плотная подложка — иначе сквозь панель
    // читается прокручивающийся список.
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .clip(outerShape)
            .background(
                if (style == NimboElementStyle.MANGA) NimboMangaPalette.PaperDeep
                else NimboPalette.Background.copy(alpha = 0.94f)
            )
    ) {
        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 32.dp,
            strong = true,
            padding = androidx.compose.foundation.layout.PaddingValues(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NimboScreen.entries.filter { it.inTabBar }.forEach { screen ->
                    val isSelected = screen == (if (selected.inTabBar) selected else NimboScreen.SETTINGS)
                    val shape = nimboStyledShape(25.dp, 2.dp)
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp).padding(vertical = 8.dp)
                            .clip(shape)
                            .background(
                                nimboStyledContainer(
                                    if (isSelected) NimboPalette.Soft else Color.Transparent,
                                    selected = isSelected
                                )
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    if (style == NimboElementStyle.MANGA) 2.dp else 1.dp,
                                    nimboStyledBorder(NimboPalette.Border, selected = true),
                                    shape
                                ) else Modifier
                            )
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                enabled = !isSelected,
                                onClick = { onSelected(screen) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        NimboIcon(
                            name = screen.iconName,
                            selected = isSelected,
                            tint = if (isSelected) NimboPalette.Accent else NimboPalette.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.size(2.dp))
                        BasicText(
                            text = screen.shortTitle,
                            style = TextStyle(fontFamily = NimboTypography.body, 
                                color = if (isSelected) NimboPalette.Text else NimboPalette.TextSecondary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

private val NimboScreen.iconName: NimboIconName
    get() = when (this) {
        NimboScreen.HOME -> NimboIconName.HOME
        NimboScreen.PROFILES -> NimboIconName.PROFILES
        NimboScreen.STATS -> NimboIconName.STATS
        NimboScreen.ROUTING -> NimboIconName.ROUTE
        NimboScreen.MODULES -> NimboIconName.LIST
        NimboScreen.ROUTING_PROFILES -> NimboIconName.ROUTE
        NimboScreen.NOTIFICATIONS -> NimboIconName.NOTIFICATIONS
        NimboScreen.SETTINGS -> NimboIconName.SETTINGS
    }
