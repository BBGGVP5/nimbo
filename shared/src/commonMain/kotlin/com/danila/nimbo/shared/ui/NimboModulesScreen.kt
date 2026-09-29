package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.danila.nimbo.shared.routing.NimboModule
import com.danila.nimbo.shared.routing.NimboModuleParser

@Composable
internal fun NimboModulesScreen(state: NimboUiState, actions: NimboUiActions) {
    var editing by remember { mutableStateOf<NimboModule?>(null) }
    val current = editing
    if (current != null) {
        ModuleEditor(current, actions, onCancel = { editing = null }, onSave = {
            actions.onSaveModule(it.id, it.name, it.text)
            editing = null
        })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimboSettingsBack("Маршрутизация") { actions.onOpenScreen(NimboScreen.ROUTING.wireName) }
        NimboPageHeading("Модули") {
            NimboSettingsInfo("Модули", "Свои правила поверх профиля: домены и адреса напрямую, через VPN или в блок. Правила модуля применяются раньше правил профиля.")
        }
        BasicText("${state.modules.count { it.enabled }} включено из ${state.modules.size} · " +
            "${state.modules.sumOf { NimboModuleParser.parse(it.text).rules.size }} правил", style = NimboBodyStyle)
        NimboPrimaryAction("Новый модуль", {
            editing = NimboModule(id = "module-" + nimboRandomId(), name = "Новый модуль", enabled = true, text = NewModuleTemplate)
        }, Modifier.fillMaxWidth())
        if (state.modules.isEmpty()) {
            NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(16.dp)) {
                BasicText("Модулей пока нет", style = NimboBodyStyle)
            }
        }
        state.modules.forEach { module ->
            key(module.id) {
                ModuleCard(module, onToggle = { actions.onToggleModule(module.id) }, onEdit = { editing = module })
            }
        }
    }
}

@Composable
private fun ModuleCard(module: NimboModule, onToggle: () -> Unit, onEdit: () -> Unit) {
    val parsed = remember(module.text) { NimboModuleParser.parse(module.text) }
    val title = module.name.ifBlank { parsed.name?.takeIf { it.isNotBlank() } ?: "Без названия" }
    NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(title, Modifier.fillMaxWidth(), style = NimboSectionTitleStyle)
            BasicText("${parsed.rules.size} правил", style = NimboBodyStyle)
            if (parsed.skippedLines > 0) BasicText("${parsed.skippedLines} строк не распознано",
                style = NimboBodyStyle.copy(color = NimboPalette.Amber))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NimboSettingsAction("Редактировать", Modifier.weight(1f).semantics { contentDescription = "Редактировать: $title" }, onClick = onEdit)
                parsed.description?.takeIf { it.isNotBlank() }?.let { NimboSettingsInfo(title, it) }
            }
            AppearanceToggle("Модуль включён", module.enabled) { onToggle() }
        }
    }
}

@Composable
private fun ModuleEditor(module: NimboModule, actions: NimboUiActions, onCancel: () -> Unit, onSave: (NimboModule) -> Unit) {
    var name by remember(module.id) { mutableStateOf(module.name) }
    var text by remember(module.id) { mutableStateOf(module.text) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = name != module.name || text != module.text
    val parsed = remember(text) { NimboModuleParser.parse(text) }
    val focusManager = LocalFocusManager.current
    val resolvedName = name.trim().ifBlank { parsed.name?.trim().orEmpty() }.ifBlank { "Без названия" }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimboSettingsBack("Модули") {
            focusManager.clearFocus()
            if (dirty) confirmDiscard = true else onCancel()
        }
        NimboPageHeading("Редактор модуля") {
            NimboSettingsInfo("Формат правил", "Поддерживаются DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP и GEOSITE с политиками DIRECT, PROXY и REJECT. Секция [General] пропускается: её настройки относятся к другому движку.")
        }
        // Actions have their own row; the back label remains readable at large text sizes.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NimboIconButton(NimboIconName.COPY, Modifier.size(44.dp).semantics { contentDescription = "Копировать правила" }) {
                focusManager.clearFocus(); actions.onCopyText(text)
            }
            NimboIconButton(NimboIconName.SHARE, Modifier.size(44.dp).semantics { contentDescription = "Экспортировать модуль" }) {
                focusManager.clearFocus(); actions.onExportModule(resolvedName, text)
            }
            NimboIconButton(NimboIconName.DELETE, Modifier.size(44.dp).semantics { contentDescription = "Удалить модуль" }) {
                focusManager.clearFocus(); confirmDelete = true
            }
        }
        BasicText("Название", style = NimboBodyStyle)
        BasicTextField(name, { name = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = "Название модуля" }
                .nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
            textStyle = NimboBodyStyle.copy(color = NimboPalette.Text), cursorBrush = SolidColor(NimboPalette.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
        BasicText("${parsed.rules.size} правил разобрано", style = NimboBodyStyle)
        if (parsed.skippedLines > 0) BasicText("${parsed.skippedLines} строк не распознано",
            style = NimboBodyStyle.copy(color = NimboPalette.Amber))
        BasicTextField(text, { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp)
                .semantics { contentDescription = "Правила маршрутизации модуля" }
                .nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
            textStyle = NimboBodyStyle.copy(color = NimboPalette.Text, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(NimboPalette.Accent))
        NimboSettingsAction("Скрыть клавиатуру", Modifier.fillMaxWidth()) { focusManager.clearFocus() }
        NimboPrimaryAction("Сохранить модуль", {
            focusManager.clearFocus(); onSave(module.copy(name = resolvedName, text = text))
        }, Modifier.fillMaxWidth())
    }
    if (confirmDelete) NimboSettingsConfirmation("Удалить модуль?", resolvedName, "Удалить",
        onDismiss = { confirmDelete = false }, onConfirm = {
            actions.onDeleteModule(module.id)
            confirmDelete = false
            onCancel()
        })
    if (confirmDiscard) NimboSettingsConfirmation("Отменить изменения?", "Несохранённые правки модуля будут потеряны.", "Не сохранять",
        onDismiss = { confirmDiscard = false }, onConfirm = { confirmDiscard = false; onCancel() })
}

/** Заготовка нового модуля: формат виден сразу, искать пример не нужно. */
private val NewModuleTemplate = """
#!name=Мой модуль
#!desc=Свои правила маршрутизации

[Rule]
DOMAIN-SUFFIX,example.com,DIRECT
DOMAIN-KEYWORD,analytics,REJECT
GEOIP,ru,DIRECT
""".trimIndent()
