package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun NimboAppearanceDetails(state: NimboUiState, actions: NimboUiActions) {
    val settings = state.appearance
    SettingsSection("Тема") {
        BasicText("Как в Android: системная, светлая, тёмная или чёрная OLED", style = NimboBodyStyle,
            modifier = Modifier.padding(vertical = 10.dp))
        listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная", "oled" to "OLED")
            .chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (key, label) ->
                        val shape = nimboStyledShape(18.dp)
                        val selected = settings.themeMode == key
                        Column(Modifier.weight(1f).clip(shape)
                            .background(nimboStyledContainer(NimboPalette.SurfaceStrong, selected))
                            .border(if (selected) 2.dp else 1.dp, nimboStyledBorder(NimboPalette.Border, selected), shape)
                            .nimboRowClickable { actions.onSetAppearance("themeMode", key) }
                            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().height(32.dp).clip(nimboStyledShape(8.dp))) {
                                val first = if (key == "light" || key == "system") Color(0xFFF2F5FC) else Color(0xFF091321)
                                Box(Modifier.weight(1f).fillMaxHeight().background(if (key == "oled") Color.Black else first))
                                Box(Modifier.weight(1f).fillMaxHeight().background(if (key == "system") Color(0xFF091321) else if (key == "oled") Color.Black else first))
                            }
                            BasicText((if (selected) "✓ " else "") + label, style = NimboBodyStyle.copy(color = NimboPalette.Text))
                        }
                    }
                }
            }
        Spacer(Modifier.height(6.dp))
    }

    SettingsSection("Акцентный цвет") {
        val presets = listOf("75A7FF", "8D79FF", "DB73B4", "E63329", "F19A49", "DFC156", "65BC91", "60BDD5", "A0A6B3")
        presets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { hex ->
                    val shape = nimboStyledShape(14.dp)
                    val selected = settings.accentHex == hex
                    Box(Modifier.weight(1f).height(46.dp).clip(shape)
                        .background(Color(0xFF000000 or hex.toLong(16)))
                        .border(if (selected) 3.dp else 1.dp, if (selected) NimboPalette.Text else NimboPalette.Border, shape)
                        .nimboRowClickable { actions.onSetAppearance("accentHex", hex) }, contentAlignment = Alignment.Center) {
                        if (selected) BasicText("✓", style = TextStyle(color = Color.Black, fontSize = 23.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        var hex by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("#", style = NimboBodyStyle)
            BasicTextField(hex, { hex = it.removePrefix("#").take(6).uppercase() },
                modifier = Modifier.weight(1f).nimboControlSurface(nimboStyledShape(12.dp)).padding(12.dp),
                singleLine = true, textStyle = NimboBodyStyle.copy(color = NimboPalette.Text), cursorBrush = SolidColor(NimboPalette.Accent))
            NimboIconButton(NimboIconName.SAVE, Modifier.size(44.dp), enabled = hex.length == 6 && hex.toLongOrNull(16) != null) {
                actions.onSetAppearance("accentHex", hex)
            }
            NimboIconButton(NimboIconName.REFRESH, Modifier.size(44.dp)) { actions.onSetAppearance("accentHex", "75A7FF") }
        }
    }

    SettingsSection("Детали стиля") {
        AppearanceSlider("Яркость панелей", "brightness", settings.brightness, 0.5f..2f, 1f, actions)
        // Бумага Manga остаётся непрозрачной. Радиус системного blur iOS не
        // предоставляет: не показываем ползунок, который ничего не делает.
        if (state.elementStyle == "glass") {
            AppearanceSlider("Прозрачность стекла", "transparency", settings.transparency, 0f..1f, 0f, actions)
            AppearanceToggle("Блики и преломление", settings.refraction) { actions.onSetAppearance("refraction", it.toString()) }
        }
        AppearanceSlider("Скругление элементов", "corners", settings.corners, 0.25f..2f, 1f, actions)
        NimboPill("Сбросить детали стиля", Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            actions.onSetAppearance("brightness", "1")
            actions.onSetAppearance("transparency", "0")
            actions.onSetAppearance("corners", "1")
            actions.onSetAppearance("refraction", "true")
        }
    }
    SettingsSection("Текст") {
        AppearanceSlider("Масштаб текста", "textScale", settings.textScale, 0.85f..1.25f, 1f, actions)
        BasicText("Дополняет системный размер текста", style = NimboBodyStyle, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
internal fun AppearanceToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, modifier = Modifier.weight(1f), style = NimboBodyStyle.copy(color = NimboPalette.Text))
        NimboToggle(checked = checked, onChange = onChange)
    }
}

@Composable
private fun AppearanceSlider(title: String, key: String, value: Float, range: ClosedFloatingPointRange<Float>, default: Float, actions: NimboUiActions) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, Modifier.weight(1f), style = NimboBodyStyle.copy(color = NimboPalette.Text))
            BasicText("${(value * 100).roundToInt()}%", style = NimboBodyStyle)
            NimboIconButton(NimboIconName.REFRESH, Modifier.size(40.dp), enabled = value != default) { actions.onSetAppearance(key, default.toString()) }
        }
        Slider(value = value, onValueChange = { actions.onSetAppearance(key, it.toString()) }, valueRange = range)
    }
}
