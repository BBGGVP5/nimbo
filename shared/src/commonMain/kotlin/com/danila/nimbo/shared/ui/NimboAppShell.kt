package com.danila.nimbo.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

data class NimboUiState(
    val vpnState: String = "idle",
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val activeProfileName: String = "Подписка не добавлена",
    val activeServerName: String = "Выберите сервер",
    val serverCount: Int = 0,
    val profileCount: Int = 0,
    val deviceName: String = "iPhone",
    val systemName: String = "iOS",
    val appVersion: String = "1.2.0 Beta",
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
    val backgroundMotion: Boolean = true,
    /** Прыжок значков нижней панели при переходе между вкладками. */
    val navIconMotion: Boolean = true,
    val showSpeedWidget: Boolean = true,
    val showMemoryWidget: Boolean = true,
    /** Стиль элементов: glass / material / dotted / signal. */
    val elementStyle: String = "glass",
    /** Порядок серверов: subscription / ping / name. */
    val serverSort: String = "subscription",
    /** Избранные всегда сверху, независимо от выбранного порядка. */
    val favoritesFirst: Boolean = true,
    /** Версия доступного обновления; пусто — обновлений нет. */
    val updateVersion: String = "",
    val updateNotes: String = ""
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
    /** Настройка маршрутизации: ключ и новое значение строкой. */
    val onSetRouting: (String, String) -> Unit = { _, _ -> },
    /** Переход на вкладку: на iOS её показывает системная панель. */
    val onOpenScreen: (String) -> Unit = {},
    /** Настройка оформления: ключ и новое значение строкой. */
    val onSetAppearance: (String, String) -> Unit = { _, _ -> },
    /** Открыть страницу релиза: установить обновление сама iOS не даст. */
    val onOpenUpdate: () -> Unit = {},
    /** Сохранить копию настроек и подписки в файл. */
    val onExportBackup: () -> Unit = {},
    /** Восстановить копию из файла. */
    val onImportBackup: () -> Unit = {},
    /** Открыть перенос данных с другого устройства. */
    val onOpenSync: () -> Unit = {}
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

    CompositionLocalProvider(
        LocalNimboElementStyle provides NimboElementStyle.fromKey(state.elementStyle)
    ) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
            // Ровно тот же фон, что и на Android: общий код, а не похожая копия.
            NimboBackdrop(
                accent = NimboPalette.Accent,
                background = NimboPalette.Background,
                styleMode = backgroundStyleModeForIndex(state.backgroundStyle),
                paletteMode = backgroundPaletteModeForIndex(state.backgroundPalette),
                motionEnabled = state.backgroundMotion
            )

            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
                label = "nimbo-primary-screen"
            ) { screen ->
                when (screen) {
                    NimboScreen.HOME -> NimboHomeScreen(
                        state = state,
                        actions = actions,
                        onOpenProfiles = { actions.onOpenScreen(NimboScreen.PROFILES.wireName) }
                    )
                    NimboScreen.PROFILES -> NimboProfilesScreen(state, actions)
                    NimboScreen.STATS -> NimboStatsScreen(state, actions)
                    NimboScreen.ROUTING -> NimboRoutingScreen(state, actions)
                    NimboScreen.SETTINGS -> NimboSettingsScreen(state, actions)
                }
            }

            if (showBottomBar) {
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

@Composable
private fun NimboBottomNavigation(
    selected: NimboScreen,
    onSelected: (NimboScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    // На Android эта панель размывает фон настоящим блюром; на iOS его нет,
    // поэтому под стеклом лежит плотная подложка — иначе сквозь панель
    // читается прокручивающийся список.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(NimboPalette.Background.copy(alpha = 0.94f))
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
                    val isSelected = screen == selected
                    val shape = RoundedCornerShape(25.dp)
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(shape)
                            .background(
                                if (isSelected) NimboPalette.Accent.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    1.dp,
                                    NimboPalette.Accent.copy(alpha = 0.42f),
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
                            maxLines = 1,
                            style = TextStyle(
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
        NimboScreen.SETTINGS -> NimboIconName.SETTINGS
    }
