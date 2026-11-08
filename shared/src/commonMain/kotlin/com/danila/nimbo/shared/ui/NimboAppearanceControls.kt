package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

@Composable
internal fun NimboAppearanceDetails(state: NimboUiState, actions: NimboUiActions) {
    val settings = state.appearance
    SettingsSection("Тема") {
        NimboSettingsSelector("Режим", listOf(
            NimboDropdownOption("system", "Системная"), NimboDropdownOption("light", "Светлая"),
            NimboDropdownOption("dark", "Тёмная"), NimboDropdownOption("oled", "OLED")
        ), settings.themeMode) { actions.onSetAppearance("themeMode", it) }
    }
    SettingsSection("Акцентный цвет") {
        val presets = listOf("E8E8E8" to "Чёрно-белая", "7298EE" to "Кобальт",
            "55B8B0" to "Лагуна", "C77E67" to "Терракота")
        val options = presets.map { NimboDropdownOption(it.first, it.second) }.let {
            if (presets.any { it.first == settings.accentHex }) it else it + NimboDropdownOption(settings.accentHex, "#${settings.accentHex}")
        }
        NimboSettingsSelector("Цвет", options, settings.accentHex) { actions.onSetAppearance("accentHex", it) }
        SettingsDivider()
        var hex by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
        val focus = LocalFocusManager.current
        val valid = hex.length == 6 && hex.toLongOrNull(16) != null
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Свой цвет · HEX", style = NimboBodyStyle)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(hex, { hex = it.removePrefix("#").take(6).uppercase() },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { contentDescription = "Акцентный цвет HEX" }
                        .nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
                    singleLine = true, textStyle = NimboBodyStyle.copy(color = NimboPalette.Text), cursorBrush = SolidColor(NimboPalette.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (valid) actions.onSetAppearance("accentHex", hex)
                        focus.clearFocus()
                    }))
                NimboIconButton(NimboIconName.SAVE, Modifier.size(44.dp).semantics { contentDescription = "Сохранить цвет" }, enabled = valid) {
                    actions.onSetAppearance("accentHex", hex)
                    focus.clearFocus()
                }
                NimboIconButton(NimboIconName.REFRESH, Modifier.size(44.dp).semantics { contentDescription = "Сбросить цвет" }) {
                    actions.onSetAppearance("accentHex", "E8E8E8")
                }
            }
        }
    }
    SettingsSection("Текст") {
        AppearanceSlider("Масштаб текста", "textScale", settings.textScale, 0.85f..1.25f, 1f, actions)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Размер текста", Modifier.weight(1f), style = NimboBodyStyle)
            NimboSettingsInfo("Размер текста", "Дополняет системный размер текста")
        }
    }
}

@Composable
internal fun AppearanceToggle(title: String, checked: Boolean, info: String? = null, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (subtitle.isNullOrBlank()) {
                BasicText(title, Modifier.weight(1f), style = NimboBodyStyle.copy(color = NimboPalette.Text))
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 12.sp))
                }
            }
            // One named accessibility/keyboard target for the entire setting, not an anonymous switch.
            Box(Modifier.focusProperties { canFocus = false }.clearAndSetSemantics { }) {
                NimboToggle(checked = checked, onChange = onChange)
            }
        }
        if (!info.isNullOrBlank()) NimboSettingsInfo(title, info)
    }
}

@Composable
private fun AppearanceSlider(title: String, key: String, value: Float, range: ClosedFloatingPointRange<Float>, default: Float, actions: NimboUiActions) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(title, Modifier.weight(1f), style = NimboBodyStyle.copy(color = NimboPalette.Text))
            BasicText("${(value * 100).roundToInt()}%", style = NimboBodyStyle)
            NimboIconButton(NimboIconName.REFRESH, Modifier.size(44.dp).semantics { contentDescription = "Сбросить: $title" }, enabled = value != default) {
                actions.onSetAppearance(key, default.toString())
            }
        }
        Slider(value = value.coerceIn(range), onValueChange = { actions.onSetAppearance(key, it.toString()) }, valueRange = range,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = title },
            colors = SliderDefaults.colors(thumbColor = NimboPalette.Accent, activeTrackColor = NimboPalette.Accent,
                inactiveTrackColor = NimboPalette.Border))
    }
}
