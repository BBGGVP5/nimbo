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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.shared.routing.NimboModule
import com.danila.nimbo.shared.routing.NimboModuleParser

/**
 * Модули маршрутизации: наборы правил, написанные человеком.
 *
 * Экран намеренно про текст, а не про конструктор правил: наборы приносят
 * готовыми из других приложений и правят целиком, а построчный редактор
 * заставлял бы вбивать сотню правил по одному.
 */
@Composable
internal fun NimboModulesScreen(state: NimboUiState, actions: NimboUiActions) {
    var editing by remember { mutableStateOf<NimboModule?>(null) }

    val current = editing
    if (current != null) {
        ModuleEditor(
            module = current,
            actions = actions,
            onCancel = { editing = null },
            onSave = { saved ->
                actions.onSaveModule(saved.id, saved.name, saved.text)
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
        BasicText("Модули", style = NimboTitleStyle)
        BasicText(
            "Свои правила поверх профиля: домены и адреса, которые всегда идут напрямую, через VPN или в блок.",
            style = NimboBodyStyle
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (state.modules.isEmpty()) {
                    "Модулей пока нет"
                } else {
                    val rules = state.modules.sumOf { NimboModuleParser.parse(it.text).rules.size }
                    "${state.modules.count { it.enabled }} включено из ${state.modules.size} · $rules правил"
                },
                modifier = Modifier.weight(1f),
                style = NimboBodyStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            NimboIconPill(
                NimboIconName.ADD,
                "Новый",
                onClick = {
                    editing = NimboModule(
                        id = "module-" + nimboRandomId(),
                        name = "Новый модуль",
                        enabled = true,
                        text = NewModuleTemplate
                    )
                }
            )
        }

        if (state.modules.isEmpty()) {
            NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column {
                    BasicText("Как это работает", style = NimboSectionTitleStyle)
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        "Нажмите «Новый» и вставьте набор правил вида DOMAIN-SUFFIX,example.com,DIRECT — " +
                            "подойдёт готовый список из другого приложения. Правила модуля применяются раньше правил профиля.",
                        style = NimboBodyStyle
                    )
                }
            }
        }

        state.modules.forEach { module ->
            ModuleCard(
                module = module,
                onToggle = { actions.onToggleModule(module.id) },
                onEdit = { editing = module }
            )
        }
    }
}

@Composable
private fun ModuleCard(
    module: NimboModule,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val parsed = remember(module.text) { NimboModuleParser.parse(module.text) }
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        padding = PaddingValues(14.dp),
        onClick = onEdit
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(nimboStyledShape(13.dp, 2.dp))
                    .background(
                        nimboStyledContainer(
                            if (module.enabled) {
                                NimboPalette.Accent.copy(alpha = 0.18f)
                            } else {
                                NimboPalette.Control
                            },
                            selected = module.enabled
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(
                    NimboIconName.ROUTE,
                    tint = if (module.enabled) NimboPalette.Accent else NimboPalette.TextTertiary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    module.name.ifBlank { parsed.name?.takeIf { it.isNotBlank() } ?: "Без названия" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = NimboSectionTitleStyle.copy(fontSize = 16.sp)
                )
                BasicText(
                    buildString {
                        append("${parsed.rules.size} правил")
                        // Пропущенные строки прячут ошибку: человек ждёт, что
                        // работает весь набор.
                        if (parsed.skippedLines > 0) append(" · ${parsed.skippedLines} строк не понято")
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = NimboBodyStyle.copy(
                        fontSize = 12.sp,
                        color = if (parsed.skippedLines > 0) NimboPalette.Accent else NimboPalette.TextSecondary
                    )
                )
            }
            Spacer(Modifier.width(10.dp))
            // Переключатель вместо пилюли: состояние набора — это «включён»
            // или «нет», и выглядеть оно должно как всякий другой тумблер.
            NimboToggle(checked = module.enabled) { onToggle() }
        }
    }
}

@Composable
private fun ModuleEditor(
    module: NimboModule,
    actions: NimboUiActions,
    onCancel: () -> Unit,
    onSave: (NimboModule) -> Unit
) {
    var name by remember(module.id) { mutableStateOf(module.name) }
    var text by remember(module.id) { mutableStateOf(module.text) }
    var rulesFocused by remember { mutableStateOf(false) }
    val parsed = remember(text) { NimboModuleParser.parse(text) }
    val focusManager = LocalFocusManager.current
    val rulesFocus = remember { FocusRequester() }
    val resolvedName = name.trim().ifBlank { parsed.name?.trim().orEmpty() }.ifBlank { "Без названия" }

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
                "‹ Модули",
                modifier = Modifier.weight(1f).nimboRowClickable {
                    focusManager.clearFocus()
                    onCancel()
                },
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
                    focusManager.clearFocus()
                    onSave(module.copy(name = resolvedName, text = text))
                }
            )
        }

        BasicText(
            "НАЗВАНИЕ",
            style = NimboBodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .nimboControlSurface(nimboStyledShape(16.dp, 2.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            // Имя набора — своё, а не строка из текста правил: списки из других
            // приложений часто приходят вообще без имени.
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(NimboPalette.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { inner ->
                    if (name.isBlank()) {
                        BasicText("Например, «Ozon напрямую»", style = NimboBodyStyle.copy(fontSize = 16.sp))
                    }
                    inner()
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "${parsed.rules.size} правил разобрано" +
                    if (parsed.skippedLines > 0) " · ${parsed.skippedLines} строк не понято" else "",
                modifier = Modifier.weight(1f),
                style = NimboBodyStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (parsed.skippedLines > 0) NimboPalette.Accent else NimboPalette.TextSecondary
                )
            )
            // Клавиатуру на iOS нечем убрать: у цифрового и многострочного поля
            // системной кнопки «свернуть» нет, поэтому она своя.
            if (rulesFocused) {
                NimboIconPill(
                    NimboIconName.BACK,
                    "Скрыть клавиатуру",
                    onClick = { focusManager.clearFocus() }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .nimboControlSurface(nimboStyledShape(18.dp, 2.dp))
                .padding(14.dp)
        ) {
            // Моноширинный шрифт: правила читаются столбцами, пропорциональный
            // превращает их в кашу.
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .focusRequester(rulesFocus)
                    .onFocusChanged { rulesFocused = it.isFocused },
                textStyle = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(NimboPalette.Accent)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModuleAction(
                icon = NimboIconName.LIST,
                title = "Копировать",
                modifier = Modifier.weight(1f)
            ) {
                focusManager.clearFocus()
                actions.onCopyText(text)
            }
            ModuleAction(
                icon = NimboIconName.DOWNLOAD,
                title = "Экспорт",
                modifier = Modifier.weight(1f)
            ) {
                focusManager.clearFocus()
                actions.onExportModule(resolvedName, text)
            }
            ModuleAction(
                icon = NimboIconName.DELETE,
                title = "Удалить",
                modifier = Modifier.weight(1f),
                accent = false
            ) {
                focusManager.clearFocus()
                actions.onDeleteModule(module.id)
                onCancel()
            }
        }

        BasicText(
            "Поддерживаются DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP и GEOSITE с политиками DIRECT, PROXY и REJECT. " +
                "Секция [General] пропускается: её настройки относятся к другому движку.",
            style = NimboBodyStyle.copy(fontSize = 12.sp)
        )
    }
}

/** Кнопка действия под редактором: значок и подпись в один столбец. */
@Composable
private fun ModuleAction(
    icon: NimboIconName,
    title: String,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .nimboControlSurface(nimboStyledShape(16.dp, 2.dp))
            .nimboRowClickable(onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NimboIcon(
            icon,
            tint = if (accent) NimboPalette.Accent else NimboPalette.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(5.dp))
        BasicText(
            title,
            style = TextStyle(
                color = if (accent) NimboPalette.Text else NimboPalette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
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
