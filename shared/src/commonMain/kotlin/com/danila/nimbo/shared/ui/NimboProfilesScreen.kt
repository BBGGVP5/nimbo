package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboProfilesScreen(state: NimboUiState, actions: NimboUiActions) {
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val visibleServers = remember(state.servers, query, favoritesOnly, state.favoriteServerIds) {
        val value = query.trim()
        state.servers
            .filter { !favoritesOnly || it.id in state.favoriteServerIds }
            .filter {
                value.isEmpty() ||
                    it.name.contains(value, ignoreCase = true) ||
                    it.connectionLabel.contains(value, ignoreCase = true)
            }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 58.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                modifier = Modifier.size(50.dp),
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
            BasicText(
                if (favoritesOnly) "ИЗБРАННОЕ · ${visibleServers.size}" else "${state.serverCount} СЕРВЕРОВ",
                style = NimboBodyStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            if (favoritesOnly && visibleServers.isEmpty()) {
                NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    BasicText(
                        "Избранных серверов пока нет. Отметьте нужные кнопкой «⋯» в строке сервера.",
                        style = NimboBodyStyle
                    )
                }
            }
            visibleServers.forEach { server ->
                ProfileServerCard(
                    server = server,
                    favorite = server.id in state.favoriteServerIds,
                    onSelect = actions.onSelectServer,
                    onToggleFavorite = actions.onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun ProfileSubscriptionCard(state: NimboUiState, actions: NimboUiActions) {
    NimboSurface(modifier = Modifier.fillMaxWidth(), strong = true) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(NimboPalette.Control),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric("ТРАФИК", "∞", Modifier.weight(1f))
                ProfileMetric("ИСТЕКАЕТ", "∞", Modifier.weight(1f))
                ProfileMetric("ОБНОВЛЕНО", "—", Modifier.weight(1f))
            }
            // Пустую ссылку не показываем: кнопка, которая ничего не делает,
            // хуже отсутствующей.
            val support = state.supportUrl?.takeIf { it.isNotBlank() }
            val website = state.websiteUrl?.takeIf { it.isNotBlank() }
            if (support != null || website != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (support != null) {
                        NimboPill("◉ Поддержка", onClick = { actions.onOpenUrl(support) })
                    }
                    if (website != null) {
                        NimboPill("◎ Сайт", onClick = { actions.onOpenUrl(website) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(title: String, value: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(15.dp)).background(NimboPalette.Control).padding(11.dp)) {
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
    onToggleFavorite: (String) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    NimboSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !server.selected,
                onClick = { onSelect(server.id) }
            ),
        cornerRadius = 22.dp,
        strong = server.selected
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(NimboPalette.Control),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(
                    if (server.selected) NimboIconName.SECURITY else NimboIconName.SITE,
                    tint = if (server.selected) NimboPalette.Accent else NimboPalette.Text,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NimboSectionTitleStyle.copy(fontSize = 16.sp))
                BasicText(server.connectionLabel, style = NimboBodyStyle.copy(fontSize = 12.sp))
            }
            NimboPill(server.pingLabel, selected = server.selected)
            Spacer(Modifier.width(6.dp))
            NimboIconButton(
                if (favorite) NimboIconName.FAVORITE else NimboIconName.MORE,
                modifier = Modifier.size(44.dp),
                selected = favorite,
                onClick = { onToggleFavorite(server.id) }
            )
        }
    }
}
