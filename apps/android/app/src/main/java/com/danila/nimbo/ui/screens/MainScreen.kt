package com.danila.nimbo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.components.TopNotification

@Composable
fun MainScreen(
    onConnect: (Server) -> Unit,
    onDisconnect: () -> Unit,
    onSubscriptionAdded: (String) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    viewModel: MainViewModel = viewModel(),
    initialScreen: String? = null
) {
    val profiles by viewModel.profilesState.collectAsState()
    val topNotification by viewModel.topNotification.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NimboMiniApp(
            mainViewModel = viewModel,
            profiles = profiles,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onSubscriptionAdded = onSubscriptionAdded,
            onProfileDeleted = onProfileDeleted,
            onProfileRefresh = onProfileRefresh,
            initialScreen = initialScreen
        )

        TopNotification(
            data = topNotification,
            onDismiss = viewModel::dismissNotification,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

