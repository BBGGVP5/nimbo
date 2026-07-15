package com.danila.nimbo.ui.navigation

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.components.BottomBar
import com.danila.nimbo.ui.screens.*
import com.danila.nimbo.ui.screens.AboutScreen
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

private const val NAV_ANIMATION_MS = 320

private fun routeDepth(route: String?): Int {
    val normalized = route?.substringBefore("/") ?: return 0
    return when (normalized) {
        "home" -> 0
        "profiles" -> 0
        "settings" -> 0
        "profile_servers" -> 1
        "updates",
        "about",
        "backup",
        "logs",
        "routing",
        "subscription_settings",
        "app_proxy_settings",
        "network_settings",
        "network_presets",
        "appearance_settings",
        "notification_history",
        "device_management" -> 1
        "network_connection_settings",
        "network_tunnel_settings",
        "network_socks_settings",
        "ping_settings",
        "connectivity_diagnostics",
        "app_icon_settings" -> 2
        "ping_tool",
        "speed_test",
        "connectivity_diagnostics_history",
        "traffic_monitor" -> 3
        else -> 1
    }
}

private fun routeMorphOrigin(route: String?): TransformOrigin {
    val normalized = route?.substringBefore("/") ?: return TransformOrigin(0.5f, 0.5f)
    return when (normalized) {
        "home" -> TransformOrigin(0.18f, 0.92f)
        "profiles" -> TransformOrigin(0.5f, 0.92f)
        "settings" -> TransformOrigin(0.82f, 0.92f)
        "profile_servers" -> TransformOrigin(0.5f, 0.36f)
        "device_management" -> TransformOrigin(0.78f, 0.30f)
        "network_connection_settings" -> TransformOrigin(0.5f, 0.28f)
        "network_tunnel_settings" -> TransformOrigin(0.5f, 0.38f)
        "network_socks_settings" -> TransformOrigin(0.5f, 0.48f)
        "ping_settings" -> TransformOrigin(0.5f, 0.58f)
        "connectivity_diagnostics" -> TransformOrigin(0.5f, 0.68f)
        "app_icon_settings" -> TransformOrigin(0.5f, 0.78f)
        else -> TransformOrigin(0.5f, 0.46f)
    }
}

@Composable
fun NavGraph(
    mainViewModel: com.danila.nimbo.MainViewModel,
    servers: List<Server>,
    profiles: List<SubscriptionProfile>,
    onConnect: (Server) -> Unit,
    onSubscriptionAdded: (String) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    showAddWidgetPanel: Boolean,
    onShowAddWidgetPanel: (Boolean) -> Unit,
    initialScreen: String? = null
) {
    val navController = rememberNavController()
    // Переходим на начальный экран, если он задан
    LaunchedEffect(initialScreen) {
        if (!initialScreen.isNullOrEmpty() && initialScreen != "home") {
            navController.navigate(initialScreen)
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isAddSheetVisible by remember { mutableStateOf(false) }
    val topLevelRoutes = remember { listOf("home", "profiles", "settings") }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    val canNavigateBack = navController.previousBackStackEntry != null
    val systemBackEnabled = !showAddWidgetPanel && !isAddSheetVisible

    fun navigateToTopLevel(route: String) {
        if (currentRoute == route) return
        val restoredFromBackStack = navController.popBackStack(route, inclusive = false)
        if (!restoredFromBackStack) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateBySwipe(dragAmount: Float) {
        val currentIndex = topLevelRoutes.indexOf(currentRoute)
        if (currentIndex == -1 || abs(dragAmount) < 80f) return

        val targetIndex = if (dragAmount < 0) {
            currentIndex + 1
        } else {
            currentIndex - 1
        }

        topLevelRoutes.getOrNull(targetIndex)?.let(::navigateToTopLevel)
    }

    fun navigateBackOrHome() {
        val navigatedBack = navController.popBackStack()
        if (!navigatedBack && currentRoute != "home") {
            navigateToTopLevel("home")
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = systemBackEnabled) { progress ->
            try {
                progress.collect { event ->
                    if (canNavigateBack || currentRoute != "home") {
                        predictiveBackProgress = event.progress.coerceIn(0f, 1f)
                    }
                }
                predictiveBackProgress = 0f
                navigateBackOrHome()
            } catch (_: CancellationException) {
                predictiveBackProgress = 0f
            }
        }
    } else {
        BackHandler(enabled = systemBackEnabled) {
            navigateBackOrHome()
        }
    }

    // BottomBar поверх контента - всегда отображается
    Box(modifier = Modifier.fillMaxSize()) {
        var swipeDragAmount = 0f
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = {
                fadeIn(animationSpec = tween(190, delayMillis = 50)) +
                    scaleIn(
                        initialScale = 0.58f,
                        transformOrigin = routeMorphOrigin(targetState.destination.route),
                        animationSpec = tween(NAV_ANIMATION_MS, easing = FastOutSlowInEasing)
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(130)) +
                    scaleOut(
                        targetScale = 1.04f,
                        transformOrigin = routeMorphOrigin(targetState.destination.route),
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(180, delayMillis = 60)) +
                    scaleIn(
                        initialScale = 0.96f,
                        transformOrigin = routeMorphOrigin(targetState.destination.route),
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(180)) +
                    scaleOut(
                        targetScale = 0.58f,
                        transformOrigin = routeMorphOrigin(initialState.destination.route),
                        animationSpec = tween(NAV_ANIMATION_MS, easing = FastOutSlowInEasing)
                    )
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (predictiveBackProgress > 0f) {
                        translationX = size.width * 0.92f * predictiveBackProgress
                        scaleX = 1f - 0.035f * predictiveBackProgress
                        scaleY = 1f - 0.035f * predictiveBackProgress
                        alpha = 1f - 0.08f * predictiveBackProgress
                    }
                }
                .pointerInput(currentRoute, showAddWidgetPanel, isAddSheetVisible) {
                    if (showAddWidgetPanel || isAddSheetVisible) return@pointerInput
                    if (currentRoute !in topLevelRoutes) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { swipeDragAmount = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeDragAmount += dragAmount
                        },
                        onDragEnd = {
                            navigateBySwipe(swipeDragAmount)
                            swipeDragAmount = 0f
                        },
                        onDragCancel = {
                            swipeDragAmount = 0f
                        }
                    )
                }
        ) {
            composable(route = "home") {
                HomeScreen(
                    mainViewModel = mainViewModel,
                    navController = navController,
                    servers = servers,
                    profiles = profiles,
                    onConnect = onConnect,
                    onSubscriptionAdded = onSubscriptionAdded,
                    showAddWidgetPanel = showAddWidgetPanel,
                    onShowAddWidgetPanel = onShowAddWidgetPanel,
                    onAddSheetVisibilityChange = { isAddSheetVisible = it }
                )
            }

            composable(route = "profiles") {
                val profilesMetadata by mainViewModel.profilesMetadataState.collectAsState()
                ProfilesScreen(
                    mainViewModel = mainViewModel,
                    profiles = profilesMetadata,
                    onSubscriptionAdded = onSubscriptionAdded,
                    onProfileDeleted = onProfileDeleted,
                    onProfileRefresh = onProfileRefresh,
                    onOpenServers = { profileMetadata ->
                        val encodedUrl = java.net.URLEncoder.encode(profileMetadata.url, "UTF-8")
                        navController.navigate("profile_servers/$encodedUrl")
                    },
                    onAddSheetVisibilityChange = { isAddSheetVisible = it }
                )
            }

            composable(route = "profile_servers/{profileUrl}") { backStackEntry ->
                val profileList by mainViewModel.profilesState.collectAsState()
                val encodedUrl = backStackEntry.arguments?.getString("profileUrl") ?: ""
                val profileUrl = try {
                    java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                } catch (e: Exception) {
                    encodedUrl
                }
                val profile = profileList.find { it.url == profileUrl }

                if (profile != null) {
                    ProfileServersScreen(
                        mainViewModel = mainViewModel,
                        navController = navController,
                        profile = profile,
                        onServerSelected = { server ->
                            onConnect(server)
                        },
                        onProfileRefresh = onProfileRefresh
                    )
                }
            }

            composable(route = "settings") {                                SettingsScreen(
                    onNavigateToLogs = {
                        navController.navigate("logs")
                    },
                    onNavigateToSubscriptionSettings = {
                        navController.navigate("subscription_settings")
                    },
                    onNavigateToNetworkSettings = {
                        navController.navigate("network_settings")
                    },
                    onNavigateToNetworkPresets = {
                        navController.navigate("network_presets")
                    },
                    onNavigateToRouting = {
                        navController.navigate("routing")
                    },
                    onNavigateToAppProxySettings = {
                        navController.navigate("app_proxy_settings")
                    },
                    onNavigateToAppearanceSettings = {
                        navController.navigate("appearance_settings")
                    },
                    onNavigateToBackup = {
                        navController.navigate("backup")
                    },
                    onNavigateToAbout = {
                        navController.navigate("about")
                    },
                    onNavigateToUpdates = {
                        navController.navigate("updates")
                    },
                    onNavigateToNotificationHistory = {
                        navController.navigate("notification_history")
                    }
                )
            }

            composable(route = "updates") {
                                UpdateScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(route = "about") {
                                AboutScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "backup") {
                                BackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "logs") {
                                LogsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "routing") {
                                RoutingScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "subscription_settings") {
                                SubscriptionSettingsScreen(
                    onNavigateToRouting = { navController.navigate("routing") },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "app_proxy_settings") {
                                AppProxySettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "network_settings") {
                                NetworkSettingsScreen(
                    onNavigateToConnectionSettings = {
                        navController.navigate("network_connection_settings")
                    },
                    onNavigateToTunnelSettings = {
                        navController.navigate("network_tunnel_settings")
                    },
                    onNavigateToSocksSettings = {
                        navController.navigate("network_socks_settings")
                    },
                    onNavigateToPingSettings = {
                        navController.navigate("ping_settings")
                    },
                    onNavigateToConnectivityDiagnostics = {
                        navController.navigate("connectivity_diagnostics")
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "network_connection_settings") {
                                ConnectionSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "network_tunnel_settings") {
                                TunnelSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "network_socks_settings") {
                                SocksSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "network_presets") {
                                NetworkPresetsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "ping_settings") {
                                PingSettingsScreen(
                    navController = navController,
                    preferencesManager = com.danila.nimbo.NebulaGuardApplication.instance.preferencesManager,
                    mainViewModel = mainViewModel
                )
            }

            composable(route = "ping_tool") {
                                PingToolScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "speed_test") {
                                SpeedTestScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "connectivity_diagnostics") {
                                ConnectivityDiagnosticsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHistory = { navController.navigate("connectivity_diagnostics_history") },
                    onNavigateToPingTool = { navController.navigate("ping_tool") },
                    onNavigateToSpeedTest = { navController.navigate("speed_test") },
                    onNavigateToTrafficMonitor = { navController.navigate("traffic_monitor") }
                )
            }

            composable(route = "connectivity_diagnostics_history") {
                                ConnectivityDiagnosticsHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "traffic_monitor") {
                                TrafficMonitorScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "appearance_settings") {
                                AppearanceSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAppIconSettings = {
                        navController.navigate("app_icon_settings")
                    }
                )
            }

            composable(route = "app_icon_settings") {
                                AppIconSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "notification_history") {
                                NotificationHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = "device_management/{profileUrl}") { backStackEntry ->
                                val encodedUrl = backStackEntry.arguments?.getString("profileUrl") ?: ""
                val profileUrl = try {
                    java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                } catch (e: Exception) {
                    encodedUrl
                }

                DeviceManagementScreen(
                    mainViewModel = mainViewModel,
                    subscriptionUrl = profileUrl,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (!showAddWidgetPanel && !isAddSheetVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(120.dp)
                        .blur(14.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.16f)
                                )
                            )
                        )
                )
            }
            AnimatedVisibility(
                visible = !showAddWidgetPanel && !isAddSheetVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                BottomBar(navController)
            }
        }
    }
}
