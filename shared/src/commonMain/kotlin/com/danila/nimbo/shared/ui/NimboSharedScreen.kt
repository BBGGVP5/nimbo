package com.danila.nimbo.shared.ui

import androidx.compose.runtime.Composable

@Composable
fun NimboSharedScreen(
    screen: NimboScreen,
    state: NimboUiState = NimboUiState(),
    actions: NimboUiActions = NimboUiActions()
) {
    NimboAppShell(
        initialScreen = screen,
        state = state,
        actions = actions
    )
}
