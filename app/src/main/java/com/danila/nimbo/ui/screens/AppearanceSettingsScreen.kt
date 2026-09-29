package com.danila.nimbo.ui.screens

import androidx.compose.runtime.Composable
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.ui.i18n.t

/** Both navigation entry points render the same production appearance controls. */
@Composable
fun AppearanceSettingsScreen(onNavigateBack: () -> Unit, onNavigateToAppIconSettings: () -> Unit) {
    NimboSubPageScaffold(t("Внешний вид", "Appearance"), onBack = onNavigateBack) {
        UniversalAppearanceContent(LocalPreferencesManager.current, onNavigateToAppIconSettings)
    }
}
