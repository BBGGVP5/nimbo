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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NimboProfilesScreen(state: NimboUiState, actions: NimboUiActions) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var favoritesOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(state.activeProfileName) { mutableStateOf(false) }
    val visibleServers = remember(state.servers, query, favoritesOnly, state.favoriteServerIds, state.serverSort, state.favoritesFirst) {
        filterAndSortServers(state, query, favoritesOnly)
    }
    val showServers = expanded || query.isNotBlank() || favoritesOnly
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().nimboScreenPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NimboPageHeading("Профили", if (state.profileCount == 0) "Добавьте первую подписку" else serverCountLabel(state.serverCount)) {
                NimboIconButton(NimboIconName.FAVORITE, Modifier.size(44.dp), selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly })
                Spacer(Modifier.width(8.dp))
                NimboIconButton(NimboIconName.ADD, Modifier.size(44.dp), onClick = actions.onAddProfile)
            }
        }
        item {
            NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NimboIcon(NimboIconName.SEARCH, Modifier.size(20.dp), NimboPalette.TextTertiary)
                    BasicTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f)
                        .heightIn(min = 24.dp).semantics { contentDescription = "Поиск серверов" }, singleLine = true,
                        textStyle = NimboBodyStyle.copy(color = NimboPalette.Text), cursorBrush = SolidColor(NimboPalette.Accent),
                        decorationBox = { inner -> if (query.isBlank()) BasicText("Поиск серверов", style = NimboBodyStyle); inner() })
                }
            }
        }
        if (state.profileCount == 0) {
            item { NimboAddProfileCard(actions) }
        } else {
            item { NimboSubscriptionHeader(state, actions, showServers) { expanded = !showServers; if (!expanded) { query = ""; favoritesOnly = false } } }
            if (showServers) {
                item {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NimboServerSort.entries.forEach { sort -> NimboPill(sort.title, selected = state.serverSort == sort.key,
                            onClick = { actions.onSetAppearance("serverSort", sort.key) }) }
                    }
                }
                if (query.isBlank() && !favoritesOnly) item { AutoFastestCard(state.servers, state.pingInProgress,
                    actions.onConnectFastest, autoSelected = state.activeServerId == "nimbo:auto") }
                if (visibleServers.isEmpty()) item {
                    NimboSurface(Modifier.fillMaxWidth()) { BasicText(if (favoritesOnly) "В избранном пока пусто" else "По запросу ничего не найдено", style = NimboBodyStyle) }
                }
                items(count = visibleServers.size, key = { visibleServers[it].id }) { index ->
                    val server = visibleServers[index]
                    ProfileServerCard(server, server.id in state.favoriteServerIds, actions.onSelectServer, actions.onToggleFavorite, actions.onPingServer)
                }
            }
        }
    }
}

internal fun filterAndSortServers(state: NimboUiState, query: String, favoritesOnly: Boolean): List<NimboServerUi> {
    val value = query.trim()
    return sortServers(state.servers.filter { !favoritesOnly || it.id in state.favoriteServerIds }.filter {
        value.isEmpty() || it.name.contains(value, true) || it.description.contains(value, true) || it.connectionLabel.contains(value, true)
    }, state.serverSort, state.favoriteServerIds, state.favoritesFirst)
}

/**
 * Подключение к лучшему узлу одним нажатием.
 *
 * Стоит там же, где человек выбирает сервер: «авто» — это ещё один вариант
 * выбора, а не настройка страницей глубже.
 */
@Composable
internal fun AutoFastestCard(
    servers: List<NimboServerUi>,
    searching: Boolean,
    onConnect: () -> Unit,
    autoSelected: Boolean = false
) {
    val selected = servers.firstOrNull { it.selected }
    val selectedPing = selected?.ping?.takeIf { it >= 0 && !selected.pingInProgress }
    val subtitle = when {
        searching -> "Замеряю узлы…"
        autoSelected -> "Авто выбрано · ядро меняет маршрут в фоне"
        selected != null && selectedPing != null ->
            "Сейчас: ${withoutFlagEmoji(selected.name)} · ${pingDisplayLabel(selectedPing, false, LocalNimboPingProtocol.current)}"
        else -> "Замерит все серверы и подключится к лучшему"
    }
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        onClick = onConnect,
        enabled = !searching && servers.isNotEmpty()
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
                    "Авто — лучший доступный",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = NimboTypography.body, 
                        color = NimboPalette.Accent,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                BasicText(
                    subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = NimboBodyStyle.copy(fontSize = 12.sp)
                )
            }
            BasicText(
                if (searching) "…" else if (autoSelected) "✓" else "›",
                style = TextStyle(fontFamily = NimboTypography.body, color = NimboPalette.Accent, fontSize = 20.sp)
            )
        }
    }
}

@Composable
internal fun ProfileServerCard(
    server: NimboServerUi,
    favorite: Boolean,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPing: (String) -> Unit
) {
    val shape = nimboStyledShape(14.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(shape)
        .background(if (server.selected) NimboPalette.Soft else NimboPalette.Surface)
        .border(1.dp, if (server.selected) NimboPalette.TextSecondary else NimboPalette.Border, shape)
        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).heightIn(min = 60.dp)
            .clickable(enabled = !server.selected, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(server.id) }
            .semantics { selected = server.selected },
            verticalArrangement = Arrangement.Center) {
            BasicText(withoutFlagEmoji(server.name), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = NimboBodyStyle.copy(color = NimboPalette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            BasicText(server.description.ifBlank { server.connectionLabel }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = NimboBodyStyle.copy(fontSize = 11.sp, lineHeight = 15.sp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NimboPingBadge(server, selected = server.selected)
            Row {
                NimboIconButton(NimboIconName.PING, Modifier.size(44.dp), enabled = !server.pingInProgress, onClick = { onPing(server.id) })
                NimboIconButton(if (favorite) NimboIconName.FAVORITE else NimboIconName.FAVORITE_OFF,
                    Modifier.size(44.dp), selected = favorite, onClick = { onToggleFavorite(server.id) })
            }
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
