package com.danila.nimbo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.*
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.subscriptionAccent
import com.danila.nimbo.utils.PreferencesManager

/** Theme choices change color, never the production layout or its geometry. */
@Composable
internal fun ColumnScope.UniversalAppearanceContent(
    preferences: PreferencesManager,
    onAppIconClick: () -> Unit
) {
    val mode by preferences.themeModeState
    val oled by preferences.pureBlackModeState
    val dynamic by preferences.useDynamicColorState
    val custom by preferences.isCustomAccentState
    val accent by preferences.customAccentColorState
    val palette by preferences.colorThemeState
    val providerSpec by preferences.subscriptionThemeSpecState
    val subscriptionTheme by preferences.useSubscriptionThemeState
    val showLogo by preferences.showSubscriptionLogoState
    val textScale by preferences.textScaleState
    val contrast by preferences.highContrastUiState
    val motion by preferences.backgroundAnimationEnabledState
    val compactConnectButton by preferences.compactConnectButtonState
    val colors = LocalNebulaColors.current
    val systemColorActive = dynamic && android.os.Build.VERSION.SDK_INT >= 31
    val providerAccent = subscriptionAccent(providerSpec)
    val providerActive = subscriptionTheme && providerAccent != null
    var customDialog by rememberSaveable { mutableStateOf(false) }

    SubPageSectionHeader(t("ТЕМА", "THEME"))
    NimboThemePreviewGrid(
        selectedIndex = if (oled) 3 else mode.coerceIn(0, 2),
        accent = if (!providerActive && !systemColorActive && !custom && palette.mod(9) == 8) null else colors.accent,
        onSelect = { index ->
            preferences.pureBlackMode = index == 3
            preferences.themeMode = if (index == 3) 2 else index
        }
    )
    Spacer(Modifier.height(20.dp))
    SubPageSectionHeader(t("АКЦЕНТНЫЙ ЦВЕТ", "ACCENT COLOR"))
    Text(
        if (providerActive) t("Сейчас используется цвет провайдера. Выбор ниже переключит на ваш акцент.",
            "Provider color is active. Choosing a color below switches to your accent.")
        else t("Выбор применяется сразу и сохраняется для следующего запуска.",
            "Your choice applies immediately and is saved for the next launch."),
        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
    AccentLivePreview(colors.accent)
    Spacer(Modifier.height(12.dp))
    NimboPanel(Modifier.fillMaxWidth()) {
        Column {
            NimboChoiceRow(t("Нейтральный", "Neutral"), selected = !providerActive && !systemColorActive && !custom && palette.mod(9) == 8,
                onClick = {
                    preferences.useSubscriptionTheme = false
                    preferences.useDynamicColor = false
                    preferences.isCustomAccent = false
                    preferences.colorTheme = if (colors.background.luminance() > 0.5f) 17 else 8
                })
            if (android.os.Build.VERSION.SDK_INT >= 31) NimboChoiceRow(t("Системный цвет", "System color"),
                selected = !providerActive && systemColorActive, onClick = {
                    preferences.useSubscriptionTheme = false
                    preferences.useDynamicColor = true
                })
            listOf(
                t("Кобальт", "Cobalt") to Color(0xFF7298EE),
                t("Лагуна", "Lagoon") to Color(0xFF55B8B0),
                t("Терракота", "Terracotta") to Color(0xFFC77E67)
            ).forEach { (label, color) ->
                NimboChoiceRow(label, selected = !providerActive && custom && !systemColorActive && accent == color.toArgb(),
                    onClick = {
                        preferences.applyUniversalAccent(color.toArgb())
                    }, leading = { Box(Modifier.size(22.dp).background(color, CircleShape)) })
            }
            SettingsNavigationItem(Icons.Default.ColorLens, t("Свой цвет", "Custom color"),
                if (!providerActive && custom && !systemColorActive)
                    t("Выбран", "Selected") + " · #%06X".format(java.util.Locale.ROOT, accent and 0xFFFFFF)
                else t("Один цвет акцента", "A single accent color")) { customDialog = true }
        }
    }
    Spacer(Modifier.height(20.dp))
    SettingsSection(t("Главная", "Home"), Icons.Default.PowerSettingsNew) {
        SettingsSwitch(Icons.Default.PowerSettingsNew,
            t("Компактная кнопка подключения", "Compact connect button"),
            t("Небольшая кнопка вместо большого круга", "Use a smaller button instead of the large circle"),
            compactConnectButton) { preferences.compactConnectButton = it }
    }
    Spacer(Modifier.height(20.dp))
    SettingsSection(t("Подписка", "Subscription"), Icons.Default.Layers) {
        SettingsSwitch(Icons.Default.Palette, t("Цвет из подписки", "Subscription color"),
            when {
                providerActive -> t("Включён вместо личного акцента. Отключите, чтобы вернуть свой цвет.",
                    "Active instead of your personal accent. Turn off to restore your color.")
                subscriptionTheme -> t("Провайдер не передал подходящий цвет — пока используется ваш акцент.",
                    "No usable provider color yet; your personal accent is used for now.")
                else -> t("Использовать цвет провайдера, сохранив личный акцент", "Use your provider’s color and keep your personal accent saved")
            }, subscriptionTheme) { preferences.useSubscriptionTheme = it }
        SettingsSwitch(Icons.Default.Image, t("Логотип подписки", "Subscription logo"),
            t("Показывать логотип провайдера", "Show the provider’s logo"), showLogo) { preferences.showSubscriptionLogo = it }
    }
    Spacer(Modifier.height(20.dp))
    SettingsSection(t("Доступность", "Accessibility"), Icons.Default.Accessibility) {
        SettingsSwitch(Icons.Default.Contrast, t("Высокий контраст", "High contrast"),
            t("Более чёткие подписи", "Stronger text contrast"), contrast) { preferences.highContrastUi = it }
        SettingsSwitch(Icons.Default.Animation, t("Уменьшить движение", "Reduce motion"),
            t("Без анимации переходов", "Disable animated transitions"), !motion) { preferences.backgroundAnimationEnabled = !it }
        Column(Modifier.padding(14.dp)) {
            Text(t("Размер текста", "Text size") + " · ${(textScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Text(t("Учитывает системный размер шрифта", "Also follows the system font size"),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Slider(value = textScale, onValueChange = { preferences.textScale = it }, valueRange = 0.85f..1.25f, steps = 7)
            TextButton(onClick = { preferences.textScale = 1f }) { Text(t("Сбросить размер", "Reset size")) }
        }
    }
    Spacer(Modifier.height(20.dp))
    NimboPanel(Modifier.fillMaxWidth()) {
        SettingsNavigationItem(Icons.Default.Apps, t("Значок приложения", "App icon"),
            t("Иконка на домашнем экране устройства", "Icon on your device’s home screen"), onAppIconClick)
    }
    if (customDialog) UniversalAccentColorDialog(preferences, onDismiss = { customDialog = false })
}

/** A small live sample of the applied accent, independent of icon or background presets. */
@Composable
private fun AccentLivePreview(accent: Color) {
    val colors = LocalNebulaColors.current
    Surface(color = colors.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.48f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(21.dp),
                        tint = if (accent.luminance() > 0.55f) Color.Black else Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Превью акцента", "Accent preview"), color = colors.textPrimary,
                        style = MaterialTheme.typography.titleSmall)
                    Text(t("Кнопка, выбор и задержка", "Button, selection and latency"),
                        color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = accent.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Text(t("Выбрано", "Selected"), color = accent, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Surface(color = accent.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Text("87 ms", color = accent, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
    }
}

/** A single opaque accent; editing is local until Apply, with no legacy gradient controls. */
@Composable
private fun UniversalAccentColorDialog(preferences: PreferencesManager, onDismiss: () -> Unit) {
    val colors = LocalNebulaColors.current
    var hex by rememberSaveable { mutableStateOf("#%06X".format(java.util.Locale.ROOT, colors.accent.toArgb() and 0xFFFFFF)) }
    val normalized = hex.trim().removePrefix("#")
    val argb = if (normalized.matches(Regex("[0-9a-fA-F]{6}")))
        (0xFF000000L or normalized.toLong(16)).toInt() else null
    NebulaMorphicDialog(onDismissRequest = onDismiss, title = t("Свой цвет", "Custom color"),
        confirmButtonText = null, cancelButtonText = null, onConfirm = {}) {
        AccentLivePreview(argb?.let(::Color) ?: colors.accent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(argb?.let { Color(it) } ?: colors.accent, CircleShape))
            Text(t("Акцент интерфейса", "Interface accent"), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(value = hex, onValueChange = { hex = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(t("Цвет HEX", "HEX color")) },
            placeholder = { Text("#7298EE") }, isError = argb == null,
            supportingText = { Text(t("Шесть символов: 0–9 и A–F", "Six characters: 0–9 and A–F")) })
        Button(onClick = {
            argb?.let { value ->
                preferences.applyUniversalAccent(value)
                onDismiss()
            }
        }, enabled = argb != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)) {
            Text(t("Применить", "Apply"))
        }
    }
}

/** Explicit personal selection supersedes provider mode, while keeping its spec for later. */
private fun PreferencesManager.applyUniversalAccent(value: Int) {
    useSubscriptionTheme = false
    useDynamicColor = false
    customAccentColor = value
    // Keep older clients/sync readers consistent, even though the root now uses the single accent.
    customGradientColor1 = value
    customGradientColor2 = value
    customGradientColor3 = value
    customGradientCount = 1
    isCustomAccent = true
}
