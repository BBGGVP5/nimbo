package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.danila.nimbo.shared.routing.NimboRoutingProfile

@Composable
internal fun NimboRoutingProfilesScreen(state: NimboUiState, actions: NimboUiActions) {
    var editing by remember { mutableStateOf<NimboRoutingProfile?>(null) }
    var reset by remember { mutableStateOf(false) }
    val current = editing
    if (current != null) {
        RoutingProfileEditor(current, onCancel = { editing = null }, onSave = {
            actions.onSaveRoutingProfile(it); editing = null
        })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimboSettingsBack("Маршрутизация") { actions.onOpenScreen(NimboScreen.ROUTING.wireName) }
        NimboPageHeading("Профили") {
            NimboSettingsInfo("Профили маршрутизации", "Готовые наборы правил. Модули добавляются поверх выбранного профиля. Правки сохраняются отдельно, чтобы встроенные наборы можно было восстановить.")
        }
        NimboSettingsAction("Вернуть исходные профили") { reset = true }
        if (state.routingProfiles.isEmpty()) NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            BasicText("Профилей пока нет", style = NimboBodyStyle)
        }
        state.routingProfiles.forEach { profile ->
            key(profile.id) {
                RoutingProfileCard(profile, active = profile.id == state.routingProfileId,
                    onSelect = { actions.onSelectRoutingProfile(profile.id) }, onEdit = { editing = profile })
            }
        }
    }
    if (reset) NimboSettingsConfirmation("Вернуть исходные профили?", "Правки встроенных наборов будут сброшены.", "Восстановить",
        onDismiss = { reset = false }, onConfirm = { actions.onResetRoutingProfiles(); reset = false })
}

@Composable
private fun RoutingProfileCard(profile: NimboRoutingProfile, active: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(active, role = Role.RadioButton, onClick = onSelect).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(profile.name, style = NimboSectionTitleStyle)
                BasicText(if (active) "Выбран · ${profile.ruleCount} правил" else "${profile.ruleCount} правил", style = NimboBodyStyle)
                BasicText(if (profile.globalProxy) "Остальное через VPN" else "Остальное напрямую", style = NimboBodyStyle)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NimboSettingsAction("Редактировать", Modifier.weight(1f).semantics { contentDescription = "Редактировать: ${profile.name}" }, onClick = onEdit)
                if (profile.description.isNotBlank()) NimboSettingsInfo(profile.name, profile.description)
            }
        }
    }
}

@Composable
private fun RoutingProfileEditor(profile: NimboRoutingProfile, onCancel: () -> Unit, onSave: (NimboRoutingProfile) -> Unit) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var description by remember(profile.id) { mutableStateOf(profile.description) }
    var globalProxy by remember(profile.id) { mutableStateOf(profile.globalProxy) }
    var bypassLocalIp by remember(profile.id) { mutableStateOf(profile.bypassLocalIp) }
    var strategy by remember(profile.id) { mutableStateOf(profile.domainStrategy) }
    var ruleOrder by remember(profile.id) { mutableStateOf(profile.ruleOrder) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var directSites by remember(profile.id) { mutableStateOf(profile.directSites.joinToString("\n")) }
    var directIp by remember(profile.id) { mutableStateOf(profile.directIp.joinToString("\n")) }
    var proxySites by remember(profile.id) { mutableStateOf(profile.proxySites.joinToString("\n")) }
    var proxyIp by remember(profile.id) { mutableStateOf(profile.proxyIp.joinToString("\n")) }
    var blockSites by remember(profile.id) { mutableStateOf(profile.blockSites.joinToString("\n")) }
    var blockIp by remember(profile.id) { mutableStateOf(profile.blockIp.joinToString("\n")) }
    val focusManager = LocalFocusManager.current
    val draft = profile.copy(name = name.trim().ifEmpty { profile.name }, description = description.trim(),
            globalProxy = globalProxy, bypassLocalIp = bypassLocalIp, domainStrategy = strategy, ruleOrder = ruleOrder,
            directSites = lines(directSites), directIp = lines(directIp), proxySites = lines(proxySites),
            proxyIp = lines(proxyIp), blockSites = lines(blockSites), blockIp = lines(blockIp))
    val dirty = draft != profile
    val save = { focusManager.clearFocus(); onSave(draft) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimboSettingsBack("Профили") {
            focusManager.clearFocus()
            if (dirty) confirmDiscard = true else onCancel()
        }
        NimboPageHeading("Редактор профиля")
        NimboPrimaryAction("Сохранить", save, Modifier.fillMaxWidth())
        EditorField("Название", name, singleLine = true) { name = it }
        EditorField("Описание", description) { description = it }
        SettingsSection("Прочий трафик") {
            AppearanceToggle("Через VPN", globalProxy, info = "Выключите, чтобы через VPN шли только правила из списков") { globalProxy = it }
            SettingsDivider()
            AppearanceToggle("Локальные адреса напрямую", bypassLocalIp, info = "Принтеры, NAS и роутер остаются доступны") { bypassLocalIp = it }
            SettingsDivider()
            NimboSettingsSelector("Стратегия доменов", listOf(
                NimboDropdownOption("AsIs", "AsIs", "Только по имени, без обращения к DNS"),
                NimboDropdownOption("IPIfNonMatch", "IPIfNonMatch", "Если имя не совпало — сверить по адресу"),
                NimboDropdownOption("IPOnDemand", "IPOnDemand", "Сразу разрешать имя в адрес при проверке правил")
            ), strategy, subtitle = "Как ядро сопоставляет имя сайта с правилами") { strategy = it }
            SettingsDivider()
            NimboSettingsSelector("Порядок правил", listOf(
                NimboDropdownOption("block-proxy-direct", "Блокировка → VPN → напрямую"),
                NimboDropdownOption("block-direct-proxy", "Блокировка → напрямую → VPN")
            ), ruleOrder, subtitle = "При пересечении списков применяется первое совпавшее правило") { ruleOrder = it }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Правила", Modifier.weight(1f), style = NimboSectionTitleStyle)
            NimboSettingsInfo("Формат правил", "По одному значению на строку: domain:example.com, geosite:ru, geoip:ru, IP или подсеть.")
        }
        EditorField("Сайты напрямую", directSites, monospace = true) { directSites = it }
        EditorField("IP напрямую", directIp, monospace = true) { directIp = it }
        EditorField("Сайты через VPN", proxySites, monospace = true) { proxySites = it }
        EditorField("IP через VPN", proxyIp, monospace = true) { proxyIp = it }
        EditorField("Блокируемые сайты", blockSites, monospace = true) { blockSites = it }
        EditorField("Блокируемые IP", blockIp, monospace = true) { blockIp = it }
        NimboSettingsAction("Скрыть клавиатуру", Modifier.fillMaxWidth()) { focusManager.clearFocus() }
        NimboPrimaryAction("Сохранить профиль", save, Modifier.fillMaxWidth())
    }
    if (confirmDiscard) NimboSettingsConfirmation("Отменить изменения?", "Несохранённые правки профиля будут потеряны.", "Не сохранять",
        onDismiss = { confirmDiscard = false }, onConfirm = { confirmDiscard = false; onCancel() })
}

private fun lines(value: String): List<String> = value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun EditorField(label: String, value: String, singleLine: Boolean = false, monospace: Boolean = false, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = NimboBodyStyle)
        BasicTextField(value, onChange, singleLine = singleLine,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 44.dp else 100.dp, max = 200.dp)
                .semantics { contentDescription = label }.nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
            textStyle = NimboBodyStyle.copy(color = NimboPalette.Text, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), cursorBrush = SolidColor(NimboPalette.Accent))
    }
}
