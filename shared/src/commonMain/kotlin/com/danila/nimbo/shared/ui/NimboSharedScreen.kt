package com.danila.nimbo.shared.ui

import androidx.compose.runtime.Composable

@Composable
fun NimboSharedScreen(
    screen: NimboScreen,
    state: NimboUiState = NimboUiState(),
    actions: NimboUiActions = NimboUiActions(),
    showBottomBar: Boolean = true,
    externalScreen: NimboScreen? = null
) {
    NimboAppShell(
        initialScreen = screen,
        state = state,
        actions = actions,
        showBottomBar = showBottomBar,
        externalScreen = externalScreen
    )
}
