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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboProfilesScreen(state: NimboUiState, actions: NimboUiActions) {
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val visibleServers = remember(
        state.servers,
        query,
        favoritesOnly,
        state.favoriteServerIds,
        state.serverSort,
        state.favoritesFirst
    ) {
        val value = query.trim()
        val filtered = state.servers
            .filter { !favoritesOnly || it.id in state.favoriteServerIds }
            .filter {
                value.isEmpty() ||
                    it.name.contains(value, ignoreCase = true) ||
                    it.description.contains(value, ignoreCase = true) ||
                    it.connectionLabel.contains(value, ignoreCase = true)
            }
        sortServers(filtered, state.serverSort, state.favoriteServerIds, state.favoritesFirst)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText("Профили", style = NimboTitleStyle)
                BasicText(
                    if (state.profileCount == 0) "Подписок пока нет" else "${state.serverCount} серверов · ${state.profileCount} подписка",
                    style = NimboBodyStyle
                )
            }
            // Сердечко фильтрует список по избранному; сами метки ставятся
            // кнопкой «⋯» в строке сервера.
            NimboIconButton(
                NimboIconName.FAVORITE,
                modifier = Modifier.size(46.dp),
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly }
            )
            Spacer(Modifier.width(8.dp))
            NimboIconButton(NimboIconName.ADD, modifier = Modifier.size(50.dp), onClick = actions.onAddProfile)
        }

        NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NimboIcon(NimboIconName.SEARCH, tint = NimboPalette.TextTertiary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = NimboPalette.Text, fontSize = 17.sp),
                    cursorBrush = SolidColor(NimboPalette.Accent),
                    decorationBox = { inner ->
                        if (query.isBlank()) BasicText("Поиск серверов", style = NimboBodyStyle.copy(fontSize = 17.sp))
                        inner()
                    }
                )
            }
        }

        if (state.profileCount == 0) {
            NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NimboIcon(NimboIconName.CLOUD, tint = NimboPalette.Accent, modifier = Modifier.size(52.dp))
                    BasicText("Добавьте первую подписку", style = NimboSectionTitleStyle)
                    BasicText(
                        "Поддерживаются URL подписок и отдельные конфигурации. Импорт откроется в защищённом окне Nimbo.",
                        style = NimboBodyStyle,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NimboPill("Добавить профиль", selected = true, onClick = actions.onAddProfile)
                }
            }
        } else {
            ProfileSubscriptionCard(state, actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NimboServerSort.entries.forEach { sort ->
                    NimboPill(
                        sort.title,
                        modifier = Modifier.weight(1f),
                        selected = state.serverSort == sort.key,
                        onClick = { actions.onSetAppearance("serverSort", sort.key) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    if (favoritesOnly) "ИЗБРАННОЕ · ${visibleServers.size}" else "${state.serverCount} СЕРВЕРОВ",
                    modifier = Modifier.weight(1f),
                    style = NimboBodyStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                // Кнопка на виду: пока замер запускался нажатием на число,
                // догадаться о нём было нельзя.
                // Значок без подписи: рядом стоит счётчик серверов, и текст
                // «Проверить все» отбирал у него половину строки.
                NimboIconButton(
                    NimboIconName.PING,
                    modifier = Modifier.size(40.dp),
                    selected = state.pingInProgress,
                    onClick = { if (!state.pingInProgress) actions.onPingAll() }
                )
            }
            if (favoritesOnly && visibleServers.isEmpty()) {
                NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    BasicText(
                        "Избранных серверов пока нет. Отметьте нужные сердцем в строке сервера.",
                        style = NimboBodyStyle
                    )
                }
            }
            // Первым в списке — не сервер, а способ выбора: разница между
            // узлами это задержка, а не название страны, и читать полсотни
            // строк ради неё не нужно.
            AutoFastestCard(
                servers = state.servers,
                searching = state.pingInProgress,
                onConnect = actions.onConnectFastest
            )
            visibleServers.forEach { server ->
                ProfileServerCard(
                    server = server,
                    favorite = server.id in state.favoriteServerIds,
                    onSelect = actions.onSelectServer,
                    onToggleFavorite = actions.onToggleFavorite,
                    onPing = actions.onPingServer
                )
            }
        }
    }
}

/**
 * Подключение к лучшему узлу одним нажатием.
 *
 * Стоит там же, где человек выбирает сервер: «авто» — это ещё один вариант
 * выбора, а не настройка страницей глубже.
 */
@Composable
private fun AutoFastestCard(
    servers: List<NimboServerUi>,
    searching: Boolean,
    onConnect: () -> Unit
) {
    val selected = servers.firstOrNull { it.selected }
    val selectedPing = selected?.ping?.takeIf { it > 0 }
    val subtitle = when {
        searching -> "Замеряю узлы…"
        selected != null && selectedPing != null ->
            "Сейчас: ${withoutFlagEmoji(selected.name)} · $selectedPing мс"
        else -> "Замерит все серверы и подключится к лучшему"
    }
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        onClick = { if (!searching) onConnect() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(nimboStyledShape(13.dp, 2.dp))
                    .background(nimboStyledContainer(NimboPalette.Accent.copy(alpha = 0.18f))),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(NimboIconName.PING, tint = NimboPalette.Accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    "Авто — самый быстрый",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = NimboPalette.Accent,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                BasicText(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = NimboBodyStyle.copy(fontSize = 12.sp)
                )
            }
            BasicText(
                if (searching) "…" else "›",
                style = TextStyle(color = NimboPalette.Accent, fontSize = 20.sp)
            )
        }
    }
}

@Composable
private fun ProfileSubscriptionCard(state: NimboUiState, actions: NimboUiActions) {
    val iconShape = nimboStyledShape(15.dp, 2.dp)
    NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(iconShape).background(nimboStyledContainer(NimboPalette.Control)),
                    contentAlignment = Alignment.Center
                ) { NimboIcon(NimboIconName.CLOUD, tint = NimboPalette.Accent, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        state.activeProfileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = NimboSectionTitleStyle.copy(fontSize = 19.sp)
                    )
                    BasicText("${state.serverCount} серверов", style = NimboBodyStyle)
                }
                NimboIconButton(NimboIconName.REFRESH, modifier = Modifier.size(46.dp), onClick = actions.onRefreshProfile)
                Spacer(Modifier.width(6.dp))
                NimboIconButton(NimboIconName.MORE, modifier = Modifier.size(46.dp), onClick = actions.onOpenProfileSettings)
            }
            // Описание провайдера целиком: на главной оно обрезано, чтобы
            // карточка не превращалась в стену текста, и посмотреть его было
            // негде.
            if (state.profileAnnounce.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(nimboStyledShape(16.dp, 2.dp))
                        .background(nimboStyledContainer(NimboPalette.Control))
                        .padding(14.dp)
                ) {
                    Column {
                        BasicText(
                            "ОПИСАНИЕ",
                            style = NimboBodyStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            state.profileAnnounce,
                            style = NimboBodyStyle.copy(fontSize = 13.sp, lineHeight = 19.sp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Значения приходят из заголовков подписки; пока их нет —
                // честнее показать прочерк, чем выдуманную бесконечность.
                ProfileMetric("ТРАФИК", state.profileTrafficLabel.ifBlank { "—" }, Modifier.weight(1f))
                ProfileMetric("ИСТЕКАЕТ", state.profileExpiryLabel.ifBlank { "—" }, Modifier.weight(1f))
                ProfileMetric("ОБНОВЛЕНО", state.profileUpdatedLabel.ifBlank { "—" }, Modifier.weight(1f))
            }
            // Пустую ссылку не показываем: кнопка, которая ничего не делает,
            // хуже отсутствующей.
            val support = state.supportUrl?.takeIf { it.isNotBlank() }
            val website = state.websiteUrl?.takeIf { it.isNotBlank() }
            if (support != null || website != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (support != null) {
                        NimboIconPill(
                            NimboIconName.SUPPORT,
                            "Поддержка",
                            onClick = { actions.onOpenUrl(support) }
                        )
                    }
                    if (website != null) {
                        NimboIconPill(
                            NimboIconName.SITE,
                            "Сайт",
                            onClick = { actions.onOpenUrl(website) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(title: String, value: String, modifier: Modifier) {
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(15.dp, 2.dp)
    Box(
        modifier
            .clip(shape)
            .background(nimboStyledContainer(NimboPalette.Control))
            .border(if (style == NimboElementStyle.MANGA) 1.5.dp else 0.dp, nimboStyledBorder(Color.Transparent), shape)
            .padding(11.dp)
    ) {
        Column {
            BasicText(title, style = NimboBodyStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold))
            BasicText(value, style = TextStyle(color = NimboPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun ProfileServerCard(
    server: NimboServerUi,
    favorite: Boolean,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPing: (String) -> Unit
) {
    // Геометрия из ProxyRow: 64 dp, скругление 16 dp, полоска акцента слева
    // у выбранного сервера и флаг в квадратном чипе.
    val interaction = remember { MutableInteractionSource() }
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(16.dp, 3.dp)
    val baseSurface = if (style == NimboElementStyle.MANGA) {
        Modifier
            .clip(shape)
            .background(nimboStyledContainer(NimboMangaPalette.Paper, selected = server.selected))
    } else {
        Modifier.nimboGlassSurface(
            shape = shape,
            depth = if (server.selected) LiquidGlassDepth.CONTROL else LiquidGlassDepth.PANEL,
            accent = NimboPalette.Accent,
            isDark = true,
            panelAlpha = 1f
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(baseSurface)
            .border(
                if (style == NimboElementStyle.MANGA) if (server.selected) 2.dp else 1.5.dp else 1.dp,
                nimboStyledBorder(
                    if (server.selected) NimboPalette.Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                    selected = server.selected
                ),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !server.selected,
                onClick = { onSelect(server.id) }
            )
    ) {
        if (server.selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(NimboPalette.Accent)
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(nimboStyledShape(12.dp, 2.dp))
                    .background(
                        if (server.selected) {
                            NimboPalette.Accent.copy(alpha = 0.16f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(
                    NimboIconName.SITE,
                    tint = NimboPalette.Accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = withoutFlagEmoji(server.name),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = NimboPalette.Text,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                BasicText(
                    text = server.description.ifBlank { server.connectionLabel },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = NimboPalette.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }
            NimboPill(server.pingLabel, selected = server.selected)
            Spacer(Modifier.width(6.dp))
            // Отдельная кнопка со спидометром: нажатие на само число оставили,
            // но полагаться на него нельзя — его никто не находит.
            NimboIconButton(
                NimboIconName.PING,
                modifier = Modifier.size(36.dp),
                onClick = { onPing(server.id) }
            )
            Spacer(Modifier.width(6.dp))
            NimboIconButton(
                if (favorite) NimboIconName.FAVORITE else NimboIconName.FAVORITE_OFF,
                modifier = Modifier.size(36.dp),
                selected = favorite,
                onClick = { onToggleFavorite(server.id) }
            )
        }
    }
}


/** Порядок списка серверов. */
internal enum class NimboServerSort(val key: String, val title: String) {
    SUBSCRIPTION("subscription", "Как в подписке"),
    PING("ping", "По задержке"),
    NAME("name", "По названию")
}

/**
 * Узлы без замера и молчащие уходят в конец: иначе «—» и «×» оказывались бы
 * впереди живых, а сортировка по задержке нужна ровно для обратного.
 */
internal fun sortServers(
    servers: List<NimboServerUi>,
    sort: String,
    favorites: Set<String>,
    favoritesFirst: Boolean
): List<NimboServerUi> {
    val ordered = when (sort) {
        NimboServerSort.PING.key -> servers.sortedWith(
            compareBy(
                { it.ping == null || it.ping < 0 },
                { if (it.ping != null && it.ping >= 0) it.ping else Int.MAX_VALUE },
                { it.name.lowercase() }
            )
        )
        NimboServerSort.NAME.key -> servers.sortedBy { withoutFlagEmoji(it.name).lowercase() }
        else -> servers
    }
    if (!favoritesFirst || favorites.isEmpty()) return ordered
    return ordered.sortedByDescending { it.id in favorites }
}
