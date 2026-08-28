package com.danila.nimbo.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NimboUiState(
    val vpnState: String = "idle",
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val activeProfileName: String = "Подписка не добавлена",
    val activeServerName: String = "Выберите сервер",
    val serverCount: Int = 0,
    val profileCount: Int = 0,
    val deviceName: String = "iPhone",
    val systemName: String = "iOS",
    val appVersion: String = "1.2.0 Beta",
    val appBundleIds: String = "",
    val activeServerId: String? = null,
    val servers: List<NimboServerUi> = emptyList()
)

data class NimboServerUi(
    val id: String,
    val name: String,
    val protocol: String,
    val transport: String = "",
    val security: String = "",
    val pingLabel: String = "— ms",
    val selected: Boolean = false
) {
    val connectionLabel: String
        get() = listOf(protocol.uppercase(), transport.uppercase(), security.replaceFirstChar { it.uppercase() })
            .filter(String::isNotBlank)
            .joinToString(" · ")
}

data class NimboUiActions(
    val onToggleVpn: () -> Unit = {},
    val onAddProfile: () -> Unit = {},
    val onRefreshProfile: () -> Unit = {},
    val onOpenProfileSettings: () -> Unit = {},
    val onSelectServer: (String) -> Unit = {},
    val onSaveAppRule: (String) -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onOpenSystemSettings: () -> Unit = {}
)

@Composable
fun NimboAppShell(
    initialScreen: NimboScreen,
    state: NimboUiState,
    actions: NimboUiActions
) {
    var selectedScreen by remember(initialScreen) { mutableStateOf(initialScreen) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NimboPalette.Accent,
            secondary = NimboPalette.AccentStrong,
            background = NimboPalette.BackgroundDeep,
            surface = NimboPalette.Surface,
            onPrimary = NimboPalette.BackgroundDeep,
            onBackground = NimboPalette.Text,
            onSurface = NimboPalette.Text
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(NimboPalette.Background, NimboPalette.BackgroundDeep),
                        start = Offset.Zero,
                        end = Offset(1000f, 1900f)
                    )
                )
                .drawBehind {
                    drawCircle(
                        color = NimboPalette.Accent.copy(alpha = 0.12f),
                        radius = size.minDimension * 0.58f,
                        center = Offset(size.width * -0.04f, size.height * 0.17f)
                    )
                    drawCircle(
                        color = Color(0xFF6E4CFF).copy(alpha = 0.09f),
                        radius = size.minDimension * 0.50f,
                        center = Offset(size.width * 1.04f, size.height * 0.54f)
                    )
                }
        ) {
            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
                label = "nimbo-primary-screen"
            ) { screen ->
                when (screen) {
                    NimboScreen.HOME -> NimboHomeScreen(
                        state = state,
                        actions = actions,
                        onOpenProfiles = { selectedScreen = NimboScreen.PROFILES }
                    )
                    NimboScreen.PROFILES -> NimboProfilesScreen(state, actions)
                    NimboScreen.APPS -> NimboAppsScreen(state, actions)
                    NimboScreen.SETTINGS -> NimboSettingsScreen(state, actions)
                }
            }

            NimboBottomNavigation(
                selected = selectedScreen,
                onSelected = { selectedScreen = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun NimboBottomNavigation(
    selected: NimboScreen,
    onSelected: (NimboScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    NimboSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        cornerRadius = 32.dp,
        strong = true,
        padding = androidx.compose.foundation.layout.PaddingValues(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NimboScreen.entries.forEach { screen ->
                val isSelected = screen == selected
                val shape = RoundedCornerShape(25.dp)
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .clip(shape)
                        .background(if (isSelected) NimboPalette.Accent.copy(alpha = 0.18f) else Color.Transparent)
                        .then(
                            if (isSelected) Modifier.border(
                                1.dp,
                                NimboPalette.Accent.copy(alpha = 0.42f),
                                shape
                            ) else Modifier
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = !isSelected,
                            onClick = { onSelected(screen) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NimboIcon(
                        name = screen.iconName,
                        selected = isSelected,
                        tint = if (isSelected) NimboPalette.Accent else NimboPalette.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.size(2.dp))
                    BasicText(
                        text = screen.shortTitle,
                        maxLines = 1,
                        style = TextStyle(
                            color = if (isSelected) NimboPalette.Text else NimboPalette.TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

private val NimboScreen.iconName: NimboIconName
    get() = when (this) {
        NimboScreen.HOME -> NimboIconName.HOME
        NimboScreen.PROFILES -> NimboIconName.PROFILES
        NimboScreen.APPS -> NimboIconName.APPS
        NimboScreen.SETTINGS -> NimboIconName.SETTINGS
    }
