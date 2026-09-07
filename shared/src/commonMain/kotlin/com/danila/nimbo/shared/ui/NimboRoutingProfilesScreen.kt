package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.shared.routing.NimboRoutingProfile

/**
 * Профили маршрутизации: что идёт через VPN, а что напрямую.
 *
 * Экран повторяет андроидный: сначала готовые наборы, потом — правка любого из
 * них. Правки встроенного профиля хранятся отдельно от самого набора, поэтому
 * «Вернуть» всегда возвращает исходные правила, а не стирает выбор.
 */
@Composable
internal fun NimboRoutingProfilesScreen(state: NimboUiState, actions: NimboUiActions) {
    var editing by remember { mutableStateOf<NimboRoutingProfile?>(null) }

    val current = editing
    if (current != null) {
        RoutingProfileEditor(
            profile = current,
            onCancel = { editing = null },
            onSave = {
                actions.onSaveRoutingProfile(it)
                editing = null
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(
            "‹ Маршрутизация",
            modifier = Modifier.nimboRowClickable {
                actions.onOpenScreen(NimboScreen.ROUTING.wireName)
            },
            style = TextStyle(
                color = NimboPalette.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        BasicText("Профили", style = NimboTitleStyle)
        BasicText(
            "Готовые наборы правил. Модули добавляются поверх выбранного профиля.",
            style = NimboBodyStyle
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "ГОТОВЫЕ ПРОФИЛИ",
                modifier = Modifier.weight(1f),
                style = NimboBodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            )
            NimboIconPill(
                NimboIconName.REFRESH,
                "Вернуть",
                onClick = actions.onResetRoutingProfiles
            )
        }

        state.routingProfiles.forEach { profile ->
            RoutingProfileCard(
                profile = profile,
                active = profile.id == state.routingProfileId,
                onSelect = { actions.onSelectRoutingProfile(profile.id) },
                onEdit = { editing = profile }
            )
        }
    }
}

@Composable
private fun RoutingProfileCard(
    profile: NimboRoutingProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        padding = PaddingValues(14.dp),
        onClick = onSelect
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(nimboStyledShape(13.dp, 2.dp))
                    .background(
                        nimboStyledContainer(
                            NimboPalette.Accent.copy(alpha = if (active) 0.28f else 0.14f),
                            active
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(NimboIconName.ROUTE, tint = NimboPalette.Accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    profile.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = NimboSectionTitleStyle.copy(fontSize = 16.sp)
                )
                BasicText(
                    profile.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = NimboBodyStyle.copy(fontSize = 12.sp)
                )
                BasicText(
                    buildString {
                        append("${profile.ruleCount} правил · ")
                        append(if (profile.globalProxy) "остальное через VPN" else "остальное напрямую")
                    },
                    style = NimboBodyStyle.copy(
                        fontSize = 11.sp,
                        color = if (active) NimboPalette.Accent else NimboPalette.TextSecondary
                    )
                )
            }
            if (active) {
                BasicText(
                    "✓",
                    style = TextStyle(
                        color = NimboPalette.Accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.width(6.dp))
            }
            NimboIconButton(
                NimboIconName.SETTINGS,
                modifier = Modifier.size(36.dp),
                onClick = onEdit
            )
        }
    }
}

@Composable
private fun RoutingProfileEditor(
    profile: NimboRoutingProfile,
    onCancel: () -> Unit,
    onSave: (NimboRoutingProfile) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var description by remember(profile.id) { mutableStateOf(profile.description) }
    var globalProxy by remember(profile.id) { mutableStateOf(profile.globalProxy) }
    var bypassLocalIp by remember(profile.id) { mutableStateOf(profile.bypassLocalIp) }
    var strategy by remember(profile.id) { mutableStateOf(profile.domainStrategy) }
    var directSites by remember(profile.id) { mutableStateOf(profile.directSites.joinToString("\n")) }
    var directIp by remember(profile.id) { mutableStateOf(profile.directIp.joinToString("\n")) }
    var proxySites by remember(profile.id) { mutableStateOf(profile.proxySites.joinToString("\n")) }
    var proxyIp by remember(profile.id) { mutableStateOf(profile.proxyIp.joinToString("\n")) }
    var blockSites by remember(profile.id) { mutableStateOf(profile.blockSites.joinToString("\n")) }
    var blockIp by remember(profile.id) { mutableStateOf(profile.blockIp.joinToString("\n")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "‹ Профили",
                modifier = Modifier.weight(1f).nimboRowClickable(onCancel),
                style = TextStyle(
                    color = NimboPalette.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            NimboIconPill(
                NimboIconName.SUPPORT,
                "Сохранить",
                onClick = {
                    onSave(
                        profile.copy(
                            name = name.trim().ifEmpty { profile.name },
                            description = description.trim(),
                            globalProxy = globalProxy,
                            bypassLocalIp = bypassLocalIp,
                            domainStrategy = strategy,
                            directSites = lines(directSites),
                            directIp = lines(directIp),
                            proxySites = lines(proxySites),
                            proxyIp = lines(proxyIp),
                            blockSites = lines(blockSites),
                            blockIp = lines(blockIp)
                        )
                    )
                }
            )
        }
        BasicText(profile.name, style = NimboTitleStyle.copy(fontSize = 26.sp))

        EditorField("Название", name, singleLine = true) { name = it }
        EditorField("Описание", description, singleLine = true) { description = it }

        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column {
                EditorToggle(
                    title = "Весь прочий трафик через VPN",
                    subtitle = "Выключите, чтобы через VPN шли только правила из списков",
                    checked = globalProxy,
                    showDivider = true
                ) { globalProxy = it }
                EditorToggle(
                    title = "Локальные адреса напрямую",
                    subtitle = "Принтеры, NAS и роутер остаются доступны",
                    checked = bypassLocalIp,
                    showDivider = false
                ) { bypassLocalIp = it }
            }
        }

        NimboSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            padding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            NimboDropdownRow(
                title = "Стратегия доменов",
                subtitle = "Как ядро сопоставляет имя сайта с правилами",
                options = listOf(
                    NimboDropdownOption("AsIs", "AsIs", "Только по имени, без обращения к DNS"),
                    NimboDropdownOption(
                        "IPIfNonMatch",
                        "IPIfNonMatch",
                        "Если имя не совпало — сверить по адресу"
                    ),
                    NimboDropdownOption(
                        "IPOnDemand",
                        "IPOnDemand",
                        "Сразу разрешать имя в адрес при проверке правил"
                    )
                ),
                selectedKey = strategy,
                onSelect = { strategy = it }
            )
        }

        BasicText(
            "ПРАВИЛА",
            style = NimboBodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )
        BasicText(
            "По одному значению на строку: domain:example.com, geosite:ru, geoip:ru, IP или подсеть.",
            style = NimboBodyStyle.copy(fontSize = 12.sp)
        )

        EditorField("Сайты напрямую", directSites, monospace = true) { directSites = it }
        EditorField("IP напрямую", directIp, monospace = true) { directIp = it }
        EditorField("Сайты через VPN", proxySites, monospace = true) { proxySites = it }
        EditorField("IP через VPN", proxyIp, monospace = true) { proxyIp = it }
        EditorField("Блокируемые сайты", blockSites, monospace = true) { blockSites = it }
        EditorField("Блокируемые IP", blockIp, monospace = true) { blockIp = it }
    }
}

private fun lines(value: String): List<String> =
    value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun EditorField(
    label: String,
    value: String,
    singleLine: Boolean = false,
    monospace: Boolean = false,
    onChange: (String) -> Unit
) {
    Column {
        BasicText(
            label,
            style = NimboBodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .nimboControlSurface(nimboStyledShape(16.dp, 2.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier else Modifier.heightIn(min = 92.dp)),
                textStyle = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = if (monospace) 13.sp else 15.sp,
                    lineHeight = if (monospace) 19.sp else 20.sp,
                    // Правила читаются столбцами: пропорциональный шрифт
                    // превращает их в кашу.
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
                ),
                cursorBrush = SolidColor(NimboPalette.Accent)
            )
        }
    }
}

@Composable
private fun EditorToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 12.sp))
        }
        Spacer(Modifier.width(10.dp))
        NimboToggle(checked = checked, onChange = onChange)
    }
    if (showDivider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(NimboPalette.Hairline))
    }
}
