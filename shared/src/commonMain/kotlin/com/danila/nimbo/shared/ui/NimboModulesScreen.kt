package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                    "${state.modules.size} · ${state.modules.sumOf { NimboModuleParser.parse(it.text).rules.size }} правил"
                },
                modifier = Modifier.weight(1f),
                style = NimboBodyStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            NimboIconPill(
                NimboIconName.ADD,
                "Новый модуль",
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
                        "Вставьте набор правил вида DOMAIN-SUFFIX,ozon.ru,DIRECT — подойдёт готовый список из другого приложения. " +
                            "Правила модуля применяются раньше правил профиля.",
                        style = NimboBodyStyle
                    )
                }
            }
        }

        state.modules.forEach { module ->
            ModuleCard(
                module = module,
                onToggle = { actions.onToggleModule(module.id) },
                onEdit = { editing = module },
                onDelete = { actions.onDeleteModule(module.id) }
            )
        }
    }
}

@Composable
private fun ModuleCard(
    module: NimboModule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                    .background(nimboStyledContainer(NimboPalette.Accent.copy(alpha = 0.16f))),
                contentAlignment = Alignment.Center
            ) {
                NimboIcon(NimboIconName.ROUTE, tint = NimboPalette.Accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    parsed.name?.takeIf { it.isNotBlank() } ?: module.name,
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
                        if (!module.enabled) append(" · выключен")
                    },
                    style = NimboBodyStyle.copy(
                        fontSize = 12.sp,
                        color = if (parsed.skippedLines > 0) NimboPalette.Accent else NimboPalette.TextSecondary
                    )
                )
            }
            NimboPill(
                if (module.enabled) "Вкл" else "Выкл",
                selected = module.enabled,
                onClick = onToggle
            )
            Spacer(Modifier.width(6.dp))
            NimboIconButton(
                NimboIconName.DELETE,
                modifier = Modifier.size(36.dp),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun ModuleEditor(
    module: NimboModule,
    onCancel: () -> Unit,
    onSave: (NimboModule) -> Unit
) {
    var text by remember(module.id) { mutableStateOf(module.text) }
    val parsed = remember(text) { NimboModuleParser.parse(text) }

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
                        module.copy(
                            name = parsed.name?.takeIf { it.isNotBlank() } ?: module.name,
                            text = text
                        )
                    )
                }
            )
        }
        BasicText(
            parsed.name?.takeIf { it.isNotBlank() } ?: module.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = NimboTitleStyle.copy(fontSize = 26.sp)
        )
        BasicText(
            "${parsed.rules.size} правил разобрано" +
                if (parsed.skippedLines > 0) " · ${parsed.skippedLines} строк не понято" else "",
            style = NimboBodyStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (parsed.skippedLines > 0) NimboPalette.Accent else NimboPalette.TextSecondary
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(nimboStyledShape(18.dp, 2.dp))
                .background(nimboStyledContainer(NimboPalette.Control))
                .border(
                    if (LocalNimboElementStyle.current == NimboElementStyle.MANGA) 1.5.dp else 1.dp,
                    nimboStyledBorder(NimboPalette.Hairline),
                    nimboStyledShape(18.dp, 2.dp)
                )
                .padding(14.dp)
        ) {
            // Моноширинный шрифт: правила читаются столбцами, пропорциональный
            // превращает их в кашу.
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                textStyle = TextStyle(
                    color = NimboPalette.Text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(NimboPalette.Accent)
            )
        }

        BasicText(
            "Поддерживаются DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP и GEOSITE с политиками DIRECT, PROXY и REJECT. " +
                "Секция [General] пропускается: её настройки относятся к другому движку.",
            style = NimboBodyStyle.copy(fontSize = 12.sp)
        )
    }
}

/** Заготовка нового модуля: формат виден сразу, искать пример не нужно. */
private val NewModuleTemplate = """
#!name=Мой модуль
#!desc=Свои правила маршрутизации

[Rule]
DOMAIN-SUFFIX,ozon.ru,DIRECT
DOMAIN-KEYWORD,analytics,REJECT
GEOIP,ru,DIRECT
""".trimIndent()
