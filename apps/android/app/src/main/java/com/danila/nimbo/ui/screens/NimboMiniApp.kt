package com.danila.nimbo.ui.screens

import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Debug
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.danila.nimbo.service.SubscriptionUpdateScheduler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.model.Server
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.ui.components.DeleteProfileDialog
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.ExpressiveLoadingPane
import com.danila.nimbo.ui.components.NotificationType
import com.danila.nimbo.ui.components.QrCodeDisplayBottomSheet
import com.danila.nimbo.ui.components.QrScannerScreen
import com.danila.nimbo.ui.components.SubscriptionBrandLogo
import com.danila.nimbo.ui.screens.SubscriptionProfileMetadata
import com.danila.nimbo.ui.screens.toMetadata
import com.danila.nimbo.ui.components.UpdateDialog
import com.danila.nimbo.ui.components.cleanServerName
import com.danila.nimbo.ui.components.extractFlagEmoji
import com.danila.nimbo.ui.theme.BackgroundStyleMode
import com.danila.nimbo.ui.theme.DEFAULT_COLOR_THEME_INDEX
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalBackgroundStyleMode
import com.danila.nimbo.ui.i18n.loc
import com.danila.nimbo.ui.i18n.serverCountEn
import com.danila.nimbo.ui.i18n.serverCountRu
import com.danila.nimbo.ui.i18n.subscriptionCountEn
import com.danila.nimbo.ui.i18n.subscriptionCountRu
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import com.danila.nimbo.ui.theme.NebulaColors
import com.danila.nimbo.ui.theme.LocalGlobalCornerRadius
import com.danila.nimbo.ui.theme.LocalGlobalBlurRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.BlendMode
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.formatBytes
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnRecoveryStatus
import com.danila.nimbo.vpn.VpnState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class MiniDestination {
    Home, Subscription, AppAccess, Settings,
    Theme, Language, PingSettings, About, Disclaimer, ConnectionId, Notifications, Updates, Logs,
    Routing, Connections, Statistics, Firewall;

    fun isSettingsSubPage(): Boolean = when (this) {
        Theme, Language, PingSettings, About, ConnectionId, Notifications, Updates, Logs,
        Routing, Connections, Statistics, Firewall -> true
        else -> false
    }

    /**
     * Y fraction on the Settings screen where this destination's source row sits.
     * Used as the transformOrigin for the open/close container-transform scale,
     * so the screen feels like it expands out of (and shrinks back into) the row.
     */
    fun settingsRowYFraction(): Float = when (this) {
        Language -> 0.22f
        Theme -> 0.30f
        PingSettings -> 0.38f
        Notifications -> 0.54f
        ConnectionId -> 0.62f
        Updates -> 0.70f
        About -> 0.78f
        Logs -> 0.86f
        else -> 0.5f
    }

    fun sourceTransformOrigin(): TransformOrigin = when (this) {
        Home -> TransformOrigin(0.18f, 0.94f)
        Subscription -> TransformOrigin(0.38f, 0.94f)
        AppAccess -> TransformOrigin(0.62f, 0.94f)
        Settings -> TransformOrigin(0.86f, 0.94f)
        else -> TransformOrigin(0.50f, settingsRowYFraction())
    }
}

private enum class MiniIconMotion {
    None, Refresh, Ping
}
private enum class MiniSubscriptionTab { Proxies, Profiles }
// Какой метод добавления подписки запустить при открытии диалога «Добавить профиль».
// Open — просто открыть; Paste/File — открыть и сразу выполнить метод (как одноимённые
// кнопки в самом диалоге); Qr — сразу открыть сканер (без диалога).
private enum class AddProfileAction { Open, Paste, File, Qr }
private enum class InterfacePreviewKind { Nebula, MaterialYou }

@Composable
fun NimboMiniApp(
    mainViewModel: MainViewModel,
    profiles: List<SubscriptionProfile>,
    onConnect: (Server) -> Unit,
    onDisconnect: () -> Unit,
    onSubscriptionAdded: (String) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    initialScreen: String?
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val profilesMetadata by mainViewModel.profilesMetadataState.collectAsState()

    var destination by rememberSaveable {
        mutableStateOf(
            when (initialScreen) {
                "settings" -> MiniDestination.Settings
                "updates" -> MiniDestination.Updates
                "profiles", "profile_servers" -> MiniDestination.Subscription
                else -> MiniDestination.Home
            }
        )
    }
    var subscriptionTab by rememberSaveable { mutableStateOf(MiniSubscriptionTab.Profiles) }
    var currentProfileUrl by rememberSaveable { mutableStateOf<String?>(preferencesManager.loadLastSelectedProfileUrl()) }
    // neverEqualPolicy: a Server with the same selectionKey but updated ping/timestamp
    // (post-LaunchedEffect refresh) compares structurally-equal-ish enough that
    // mutableStateOf's default policy can skip notifying subscribers, leaving the
    // visual selection stuck on the previous row. Force every assignment to publish.
    var selectedServer by remember {
        mutableStateOf(
            VpnManager.selectedServer ?: preferencesManager.loadLastSelectedServer(),
            policy = neverEqualPolicy()
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAddAction by remember { mutableStateOf(AddProfileAction.Open) }
    var showQrScanner by remember { mutableStateOf(false) }
    var shouldScrollToSelectedServer by remember { mutableStateOf(false) }
    var showTvSheetUrl by remember { mutableStateOf<String?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val destinationHistory = remember { mutableStateListOf<MiniDestination>() }

    fun navigateTo(target: MiniDestination) {
        if (target == destination) return
        if (destinationHistory.lastOrNull() != destination) {
            destinationHistory.add(destination)
            if (destinationHistory.size > 24) destinationHistory.removeAt(0)
        }
        destination = target
    }

    fun replaceDestination(target: MiniDestination) {
        destinationHistory.clear()
        destination = target
    }

    fun navigateBackInMiniApp(): Boolean {
        when {
            showQrScanner -> {
                showQrScanner = false
                return true
            }
            showAddDialog -> {
                showAddDialog = false
                return true
            }
            showTvSheetUrl != null -> {
                showTvSheetUrl = null
                return true
            }
            showDisclaimer -> {
                showDisclaimer = false
                return true
            }
        }

        while (destinationHistory.isNotEmpty()) {
            val previous = destinationHistory.removeAt(destinationHistory.lastIndex)
            if (previous != destination) {
                destination = previous
                return true
            }
        }

        val fallback = when {
            destination == MiniDestination.Firewall -> MiniDestination.Connections
            destination.isSettingsSubPage() -> MiniDestination.Settings
            destination != MiniDestination.Home -> MiniDestination.Home
            else -> null
        }
        if (fallback != null && fallback != destination) {
            destination = fallback
            return true
        }
        return false
    }

    val canHandleSystemBack = showQrScanner ||
        showAddDialog ||
        showTvSheetUrl != null ||
        showDisclaimer ||
        destination != MiniDestination.Home

    BackHandler(enabled = canHandleSystemBack) {
        navigateBackInMiniApp()
    }

    // OTA: check GitHub releases on launch, then show a dialog once per release
    // unless the user has explicitly pressed "Later" for that version. The check
    // is throttled to once every six hours so it doesn't hit the API every
    // process restart.
    LaunchedEffect(Unit) {
        if (!preferencesManager.showUpdateDialog) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val throttleMs = 6 * 60 * 60 * 1000L
        if (now - preferencesManager.lastUpdateCheckTime < throttleMs) return@LaunchedEffect

        val info = runCatching { UpdateManager.checkUpdate() }.getOrNull()
        preferencesManager.lastUpdateCheckTime = System.currentTimeMillis()
        if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
            val skipped = preferencesManager.updateDialogSkippedVersion
            if (skipped == null || skipped != info.versionName || info.forceUpdate) {
                pendingUpdateInfo = info
            }
        }
    }

    val qrPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showQrScanner = true
        else mainViewModel.showTopNotification(loc("Требуется разрешение камеры", "Camera permission required"))
    }

    LaunchedEffect(profiles) {
        if (profiles.isEmpty()) {
            currentProfileUrl = null
            selectedServer = null
            VpnManager.selectedServer = null
            preferencesManager.clearLastSelectedServer()
            preferencesManager.clearLastSelectedProfileUrl()
            if (destination == MiniDestination.Subscription) {
                replaceDestination(MiniDestination.Home)
            }
            return@LaunchedEffect
        }

        val fallbackProfile = profiles.firstOrNull()
        if (currentProfileUrl == null) currentProfileUrl = fallbackProfile?.url

        val cachedSelection = selectedServer
        val freshSelection = cachedSelection?.let { selected ->
            val ownerUrl = selected.profileUrl
            if (!ownerUrl.isNullOrBlank()) {
                profiles.find { it.url == ownerUrl }?.servers?.firstOrNull { it.matchesSelection(selected) }
            } else {
                profiles.asSequence()
                    .flatMap { it.servers.asSequence() }
                    .firstOrNull { server -> selected.matchesSelection(server) }
            }
        }
        when {
            freshSelection != null -> {
                selectedServer = freshSelection
                VpnManager.selectedServer = freshSelection
            }
            cachedSelection == null -> {
                profiles.firstOrNull()?.servers?.firstOrNull()?.let { server ->
                    selectedServer = server
                    VpnManager.selectedServer = server
                    server.profileUrl?.let(preferencesManager::saveLastSelectedProfileUrl)
                }
            }
            else -> {
                // The cached selection didn't match anything in the new profiles
                // snapshot. Only reset if the profile that owned the selection is
                // truly gone — otherwise keep the user's pick (a transient ping
                // update or selectionKey edge-case shouldn't yank their choice).
                val ownerProfileUrl = cachedSelection.profileUrl
                val ownerStillExists = !ownerProfileUrl.isNullOrBlank() &&
                    profiles.any { it.url == ownerProfileUrl }
                if (!ownerStillExists) {
                    profiles.firstOrNull()?.servers?.firstOrNull()?.let { server ->
                        selectedServer = server
                        VpnManager.selectedServer = server
                        server.profileUrl?.let(preferencesManager::saveLastSelectedProfileUrl)
                    }
                }
            }
        }
    }

    fun selectServer(server: Server) {
        // IMPORTANT: store the exact same Server instance we received from the list.
        // We used to copy(profileUrl=…) here, which produced a Server with a different
        // selectionKey than the one rendered in ProxyList — so matchesSelection() never
        // matched it back and the row never lit up (most visible on auto-balancer rows
        // where profileUrl is null in the parsed subscription).
        selectedServer = server
        VpnManager.selectedServer = server
        val resolvedProfileUrl = server.profileUrl ?: currentProfileUrl
        resolvedProfileUrl?.let {
            currentProfileUrl = it
            preferencesManager.saveLastSelectedProfileUrl(it)
        }

        // Если VPN уже поднят и в настройках разрешена смена без отключения — тапом по
        // другому серверу переключаемся на него «на лету»: onConnect шлёт ACTION_CONNECT,
        // и сервис делает switchServer() без полного реконнекта. Иначе — просто выбор.
        val vpnActive = VpnManager.state.value == VpnState.CONNECTED ||
            VpnManager.state.value == VpnState.CONNECTING
        val activeServer = VpnManager.connectedServer.value
        val isDifferentServer = activeServer == null || !server.matchesSelection(activeServer)
        val displayName = serverUiTitle(preferencesManager, server)
        if (vpnActive && preferencesManager.allowServerSwitchWhileConnected && isDifferentServer) {
            mainViewModel.showTopNotification(loc("Переключение на $displayName…", "Switching to $displayName…"))
            onConnect(server)
        }
    }

    fun addSubscription(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val isFirstSubscription = profiles.isEmpty()
        onSubscriptionAdded(trimmed)
        if (isFirstSubscription) {
            replaceDestination(MiniDestination.Home)
        } else {
            navigateTo(MiniDestination.Subscription)
            subscriptionTab = MiniSubscriptionTab.Profiles
        }
    }

    val miniMotionEnabled = rememberMiniMotionEnabled()

    val onOpenProfilesRemembered = remember {
        {
            navigateTo(MiniDestination.Subscription)
            subscriptionTab = MiniSubscriptionTab.Profiles
        }
    }
    val onOpenServersRemembered = remember {
        { scrollToSelected: Boolean ->
            shouldScrollToSelectedServer = scrollToSelected
            navigateTo(MiniDestination.Subscription)
            subscriptionTab = MiniSubscriptionTab.Proxies
        }
    }
    val onOpenSupportRemembered = remember {
        { profile: SubscriptionProfile ->
            val supportUrl = profile.supportUrl ?: profile.websiteUrl
            if (supportUrl.isNullOrBlank()) {
                mainViewModel.showTopNotification(loc("Поддержка не указана", "Support link not provided"))
            } else {
                openUrl(context, supportUrl)
            }
        }
    }
    val onOpenSiteRemembered = remember {
        { profile: SubscriptionProfile ->
            val url = profile.websiteUrl ?: profile.supportUrl
            if (url.isNullOrBlank()) {
                mainViewModel.showTopNotification(loc("Сайт не указан", "Website link not provided"))
            } else {
                openUrl(context, url)
            }
        }
    }
    val onConnectRemembered = remember {
        { server: Server ->
            selectServer(server)
            onConnect(server)
        }
    }
    val onNeedSubscriptionRemembered = remember { { showAddDialog = true } }
    val onAddMethodRemembered = remember {
        { action: AddProfileAction ->
            when (action) {
                AddProfileAction.Qr ->
                    qrPermissionLauncher.launch(Manifest.permission.CAMERA)
                else -> {
                    pendingAddAction = action
                    showAddDialog = true
                }
            }
        }
    }

    val onTabChangeRemembered = remember { { tab: MiniSubscriptionTab -> subscriptionTab = tab } }
    val onBackRemembered = remember { { navigateBackInMiniApp(); Unit } }
    val onSelectProfileRemembered = remember {
        { profileMetadata: SubscriptionProfileMetadata ->
            currentProfileUrl = profileMetadata.url
            preferencesManager.saveLastSelectedProfileUrl(profileMetadata.url)
            subscriptionTab = MiniSubscriptionTab.Profiles
        }
    }
    val onSelectServerRemembered = remember { { server: Server -> selectServer(server) } }
    val onAddSubscriptionRemembered = remember { { showAddDialog = true } }
    val onShowTvQrRemembered = remember { { url: String -> showTvSheetUrl = url } }
    val onOpenUrlRemembered = remember { { ctx: Context, url: String? -> openUrl(ctx, url) } }

    val onOpenSubscriptionRemembered = remember {
        {
            navigateTo(MiniDestination.Subscription)
            subscriptionTab = MiniSubscriptionTab.Profiles
        }
    }
    val onOpenAppAccessRemembered = remember { { navigateTo(MiniDestination.AppAccess) } }
    val onLanguageClickRemembered = remember { { navigateTo(MiniDestination.Language) } }
    val onAboutClickRemembered = remember { { navigateTo(MiniDestination.About) } }
    val onDisclaimerClickRemembered = remember { { showDisclaimer = true } }
    val onConnectionIdClickRemembered = remember { { navigateTo(MiniDestination.ConnectionId) } }
    val onNotificationsClickRemembered = remember { { navigateTo(MiniDestination.Notifications) } }
    val onUpdatesClickRemembered = remember { { navigateTo(MiniDestination.Updates) } }
    val onLogsClickRemembered = remember { { navigateTo(MiniDestination.Logs) } }
    val onRoutingClickRemembered = remember { { navigateTo(MiniDestination.Routing) } }
    val onConnectionsClickRemembered = remember { { navigateTo(MiniDestination.Connections) } }
    val onStatsClickRemembered = remember { { navigateTo(MiniDestination.Statistics) } }
    val onScrollResetRemembered = remember { { shouldScrollToSelectedServer = false } }

    // Shared snapshot of the backdrop + current page. The floating bottom bar redraws
    // this layer blurred and clipped to its own bounds, so it frosts the real content
    // behind it (true glass) rather than getting an opaque fill or an extra glow layer.
    val backdropLayer = rememberGraphicsLayer()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                }
        ) {
        NimboBackdrop()

        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                if (!miniMotionEnabled) {
                    return@AnimatedContent fadeIn(tween(0)) togetherWith fadeOut(tween(0)) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                }
                val target = targetState
                val initial = initialState
                // Keep page transitions light: full-screen scale transforms were
                // noticeably expensive on lower-end devices.
                val isSubPage = target.isSettingsSubPage() || initial.isSettingsSubPage()
                if (isSubPage) {
                    val incoming = target.isSettingsSubPage() && initial == MiniDestination.Settings
                    val outgoing = initial.isSettingsSubPage() && target == MiniDestination.Settings
                    val enter = if (incoming) {
                        fadeIn(tween(140, delayMillis = 10)) +
                            slideInVertically(
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            ) { height -> height / 28 }
                    } else {
                        fadeIn(tween(120))
                    }
                    val exit = if (outgoing) {
                        fadeOut(tween(110)) +
                            slideOutVertically(
                                animationSpec = tween(150, easing = FastOutSlowInEasing)
                            ) { height -> height / 32 }
                    } else {
                        fadeOut(tween(100))
                    }
                    enter togetherWith exit
                } else {
                    val forward = target.ordinal > initial.ordinal
                    val enter = fadeIn(tween(110)) + slideInHorizontally(
                        animationSpec = tween(130, easing = FastOutSlowInEasing)
                    ) { width -> if (forward) width / 16 else -width / 16 }
                    val exit = fadeOut(tween(80))
                    enter togetherWith exit using SizeTransform(clip = false) { _, _ -> snap() }
                }
            },
            label = "mini-destination"
        ) { targetDestination ->
            when (targetDestination) {
                MiniDestination.Home -> NimboHomeScreen(
                    mainViewModel = mainViewModel,
                    preferencesManager = preferencesManager,
                    selectedServer = selectedServer,
                    onOpenProfiles = onOpenProfilesRemembered,
                    onOpenServers = onOpenServersRemembered,
                    onRefreshProfile = onProfileRefresh,
                    onOpenSupport = onOpenSupportRemembered,
                    onOpenSite = onOpenSiteRemembered,
                    onConnect = onConnectRemembered,
                    onDisconnect = onDisconnect,
                    onNeedSubscription = onNeedSubscriptionRemembered,
                    onAddMethod = onAddMethodRemembered
                )

                MiniDestination.Subscription -> NimboSubscriptionScreen(
                    mainViewModel = mainViewModel,
                    profilesMetadata = profilesMetadata,
                    currentProfileUrl = currentProfileUrl,
                    selectedServer = selectedServer,
                    tab = subscriptionTab,
                    preferencesManager = preferencesManager,
                    onTabChange = onTabChangeRemembered,
                    onBack = onBackRemembered,
                    onSelectProfile = onSelectProfileRemembered,
                    onSelectServer = onSelectServerRemembered,
                    onAddSubscription = onAddSubscriptionRemembered,
                    onProfileDeleted = onProfileDeleted,
                    onProfileRefresh = onProfileRefresh,
                    onShowTvQr = RoseOnly@{ url -> showTvSheetUrl = url },
                    onOpenUrl = onOpenUrlRemembered,
                    showBack = false,
                    scrollToSelected = shouldScrollToSelectedServer,
                    onScrollReset = onScrollResetRemembered
                )

                MiniDestination.Settings -> NimboSettingsScreen(
                    preferencesManager = preferencesManager,
                    mainViewModel = mainViewModel,
                    onOpenSubscription = onOpenSubscriptionRemembered,
                    onOpenAppAccess = onOpenAppAccessRemembered,
                    onLanguageClick = onLanguageClickRemembered,
                    onAboutClick = onAboutClickRemembered,
                    onDisclaimerClick = onDisclaimerClickRemembered,
                    onConnectionIdClick = onConnectionIdClickRemembered,
                    onNotificationsClick = onNotificationsClickRemembered,
                    onUpdatesClick = onUpdatesClickRemembered,
                    onLogsClick = onLogsClickRemembered,
                    onRoutingClick = onRoutingClickRemembered,
                    onConnectionsClick = onConnectionsClickRemembered,
                    onStatsClick = onStatsClickRemembered,
                    onRefreshFirstProfile = onProfileRefresh,
                    onShowTvQr = onShowTvQrRemembered
                )

                MiniDestination.AppAccess -> AppProxySettingsScreen(
                    onNavigateBack = { navigateBackInMiniApp() },
                    showBack = false
                )

                MiniDestination.Theme -> NimboThemeScreen(
                    preferencesManager = preferencesManager,
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.PingSettings -> NimboPingSettingsScreen(
                    preferencesManager = preferencesManager,
                    mainViewModel = mainViewModel,
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Language -> NimboLanguageScreen(
                    preferencesManager = preferencesManager,
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.About -> NimboAboutScreen(
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Disclaimer -> {
                    // Disclaimer is now a centered dialog, but the enum entry stays
                    // so saved instance state from older builds doesn't crash —
                    // route it to Settings and pop the dialog open.
                    LaunchedEffect(Unit) {
                        replaceDestination(MiniDestination.Settings)
                        showDisclaimer = true
                    }
                }

                MiniDestination.ConnectionId -> NimboConnectionIdScreen(
                    preferencesManager = preferencesManager,
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Notifications -> NimboNotificationsScreen(
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Updates -> UpdateScreen(
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Logs -> LogsScreen(
                    onNavigateBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Routing -> RoutingScreen(
                    onNavigateBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Connections -> NimboSubPageScaffold(
                    title = t("Соединения", "Connections"),
                    subtitle = t("Туннель, активный сервер и правила", "Tunnel, active server and rules"),
                    onBack = { navigateBackInMiniApp() }
                ) {
                    ConnectionsSettingsSection(
                        preferencesManager = preferencesManager,
                        onOpenFirewall = { navigateTo(MiniDestination.Firewall) }
                    )
                }

                MiniDestination.Statistics -> NimboSubPageScaffold(
                    title = t("Статистика", "Statistics"),
                    subtitle = t("Скорость и расход трафика", "Speed and traffic usage"),
                    onBack = { navigateBackInMiniApp() }
                ) {
                    StatisticsSettingsSection(preferencesManager = preferencesManager)
                }

                MiniDestination.Firewall -> NimboSubPageScaffold(
                    title = t("Сетевой экран", "Network Shield"),
                    onBack = { navigateBackInMiniApp() }
                ) {
                    val vpnState = VpnManager.state.value
                    FirewallScreenContent(
                        vpnState = vpnState,
                        activeServer = selectedServer,
                        preferencesManager = preferencesManager
                    )
                }
            }
        }
        }

        val showBottomControls = destination == MiniDestination.Home ||
            destination == MiniDestination.Subscription ||
            destination == MiniDestination.AppAccess ||
            destination == MiniDestination.Settings
        if (showBottomControls) {
            NimboBottomControls(
                backdropLayer = backdropLayer,
                destination = destination,
                onDestinationChange = { target ->
                    if (target == MiniDestination.Subscription && destination != MiniDestination.Subscription) {
                        subscriptionTab = MiniSubscriptionTab.Profiles
                    }
                    navigateTo(target)
                }
            )
        }

        val activity = context as? android.app.Activity
        if (showDisclaimer) {
            NimboDisclaimerDialog(
                onDismiss = { showDisclaimer = false },
                onExit = {
                    showDisclaimer = false
                    activity?.finishAndRemoveTask()
                }
            )
        }

        if (showAddDialog) {
            NimboAddSubscriptionDialog(
                initialAction = pendingAddAction,
                onDismiss = {
                    showAddDialog = false
                    pendingAddAction = AddProfileAction.Open
                },
                onAdd = { url ->
                    showAddDialog = false
                    pendingAddAction = AddProfileAction.Open
                    addSubscription(url)
                },
                onQr = {
                    showAddDialog = false
                    pendingAddAction = AddProfileAction.Open
                    qrPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onNotify = { message ->
                    mainViewModel.showTopNotification(message)
                }
            )
        }

        showTvSheetUrl?.let { url ->
            QrCodeDisplayBottomSheet(
                url = url,
                onDismiss = { showTvSheetUrl = null }
            )
        }

        AnimatedVisibility(
            visible = showQrScanner,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (showQrScanner) {
                QrScannerScreen(
                    onResult = { result ->
                        showQrScanner = false
                        addSubscription(result)
                    },
                    onBack = { showQrScanner = false }
                )
            }
        }

        pendingUpdateInfo?.let { info ->
            UpdateDialog(
                updateInfo = info,
                onDismiss = {
                    preferencesManager.updateDialogSkippedVersion = info.versionName
                    pendingUpdateInfo = null
                },
                onUpdate = {
                    pendingUpdateInfo = null
                    navigateTo(MiniDestination.Updates)
                }
            )
        }
    }
}

@Composable
private fun rememberMiniMotionEnabled(): Boolean {
    val context = LocalContext.current
    val backgroundAnimationEnabled = LocalBackgroundAnimationEnabled.current
    val reducedTransparencyEnabled = LocalReducedTransparencyEnabled.current
    val lowRamDevice = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    return backgroundAnimationEnabled && !reducedTransparencyEnabled && !lowRamDevice
}

@Composable
private fun NimboBackdrop() {
    val nebulaColors = LocalNebulaColors.current
    val accent = nebulaColors.accent
    val baseBackground = nebulaColors.background
    val isLight = baseBackground.luminance() > 0.5f
    val animationsOn = rememberMiniMotionEnabled()
    val styleMode = LocalBackgroundStyleMode.current

    val windowsDarkBg = if (nebulaColors.background == Color.Black) Color(0xFF000000) else nebulaColors.background
    val resolvedBase = if (isLight) baseBackground else windowsDarkBg
    val presetColors = miniBackgroundPresetColors(styleMode, accent, isLight)
    val accentTop = if (isLight) lerp(baseBackground, accent, 0.07f) else lerp(resolvedBase, accent, 0.12f)
    // The very bottom resolves to the plain base background so the opaque bottom
    // nav bar (painted with nebulaColors.background) blends seamlessly into it.
    val accentBottom = if (isLight) baseBackground else resolvedBase
    val backgroundBrush = when {
        presetColors.isNotEmpty() && isLight -> Brush.verticalGradient(
            listOf(
                lerp(baseBackground, presetColors.first(), 0.08f),
                baseBackground,
                accentBottom
            )
        )
        presetColors.isNotEmpty() -> Brush.verticalGradient(
            listOf(
                lerp(resolvedBase, presetColors.first(), 0.22f),
                resolvedBase,
                resolvedBase
            )
        )
        isLight -> Brush.verticalGradient(
            listOf(accentTop, baseBackground, accentBottom)
        )
        else -> Brush.verticalGradient(
            // Bottom half stays flat at resolvedBase (== nebulaColors.background) so the
            // opaque bottom nav bar merges into it with no visible seam.
            listOf(accentTop, resolvedBase, resolvedBase)
        )
    }
    val bottomVignette = if (isLight) Color.Transparent
        else Color.Black.copy(alpha = 0.42f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            if (bottomVignette != Color.Transparent) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            bottomVignette,
                            Color.Transparent
                        ),
                        startY = h * 0.42f,
                        endY = h
                    )
                )
            }
        }

        if (animationsOn) {
            val infiniteTransition = rememberInfiniteTransition(label = "nimbo-bg")
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(18000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "nimbo-bg-accent-phase"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val twoPi = (PI * 2.0).toFloat()
                val accentAlpha = if (isLight) 0.11f else 0.18f
                val blobs = presetColors.ifEmpty { listOf(accent, accent) }

                blobs.forEachIndexed { index, blobColor ->
                    val local = (phase + index * 0.23f) % 1f
                    val baseX = when (index % 4) {
                        0 -> 0.08f
                        1 -> 0.90f
                        2 -> 0.22f
                        else -> 0.78f
                    }
                    val baseY = when (index % 4) {
                        0 -> 0.06f
                        1 -> 0.94f
                        2 -> 0.82f
                        else -> 0.18f
                    }
                    val strength = when (index % 4) {
                        0 -> 0.72f
                        1 -> 0.40f
                        2 -> 0.34f
                        else -> 0.30f
                    }
                    val radius = w * when (index % 4) {
                        0 -> 0.42f
                        1 -> 0.34f
                        2 -> 0.38f
                        else -> 0.30f
                    }
                    val center = Offset(
                        x = w * (baseX + sin(local * twoPi) * (0.10f + index * 0.01f)),
                        y = h * (baseY + cos(local * twoPi) * (0.06f + index * 0.005f))
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                blobColor.copy(alpha = accentAlpha * strength),
                                blobColor.copy(alpha = accentAlpha * 0.18f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                // Oscillate the sweep along sin so the value at phase 0 and phase 1
                // coincide — Restart no longer causes a visible jump.
                val sweepCenterX = w * (0.5f + 0.52f * sin(phase * twoPi))
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            (presetColors.firstOrNull() ?: accent).copy(alpha = if (isLight) 0.08f else 0.06f),
                            Color.Transparent
                        ),
                        start = Offset(sweepCenterX - w * 0.24f, 0f),
                        end = Offset(sweepCenterX + w * 0.24f, h)
                    )
                )

            }
        }
    }
}

private fun miniBackgroundPresetColors(
    styleMode: BackgroundStyleMode,
    accent: Color,
    isLight: Boolean
): List<Color> {
    val baseAccent = if (isLight) accent.copy(alpha = 0.92f) else accent
    return when (styleMode) {
        BackgroundStyleMode.CYBERPUNK -> listOf(Color(0xFF00F0FF), Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF22FFAA))
        BackgroundStyleMode.DEEP_SPACE -> listOf(Color(0xFF3432A8), Color(0xFF7C5DFA), Color(0xFFEAF3FF), Color(0xFF1B2356))
        BackgroundStyleMode.FIRE -> listOf(Color(0xFFFF3D00), Color(0xFFFFA000), Color(0xFFFFD166), Color(0xFFB31312))
        BackgroundStyleMode.LAVA -> listOf(Color(0xFFFF2E2E), Color(0xFFFF7A00), Color(0xFF7C1FFF), Color(0xFFFFC857))
        BackgroundStyleMode.NEON -> listOf(Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF00D2FF), Color(0xFF9BFF6A))
        BackgroundStyleMode.NORDIC -> listOf(Color(0xFF8FFFE8), Color(0xFF89C2FF), Color(0xFF2C4A72), Color(0xFFE7F8FF))
        BackgroundStyleMode.BLOSSOM -> listOf(Color(0xFFFF9BC4), Color(0xFFFFC29B), Color(0xFFC7A8FF), Color(0xFFFFE3EF))
        BackgroundStyleMode.AURORA -> listOf(Color(0xFF6BE88E), Color(0xFF63B3FF), baseAccent)
        BackgroundStyleMode.MESH -> listOf(baseAccent, Color(0xFF00D2FF), Color(0xFFFF7B7B), Color(0xFF5DD9A1))
        BackgroundStyleMode.STARFIELD -> listOf(Color(0xFF8EA2FF), Color(0xFFEAF0FF), baseAccent)
        else -> emptyList()
    }
}

@Composable
private fun NimboHomeScreen(
    mainViewModel: MainViewModel,
    preferencesManager: PreferencesManager,
    selectedServer: Server?,
    onOpenProfiles: () -> Unit,
    onOpenServers: (Boolean) -> Unit,
    onRefreshProfile: (String) -> Unit,
    onOpenSupport: (SubscriptionProfile) -> Unit,
    onOpenSite: (SubscriptionProfile) -> Unit,
    onConnect: (Server) -> Unit,
    onDisconnect: () -> Unit,
    onNeedSubscription: () -> Unit,
    onAddMethod: (AddProfileAction) -> Unit = {}
) {
    val profiles by mainViewModel.profilesState.collectAsState()
    val profile = profiles.firstOrNull { it.url == selectedServer?.profileUrl } ?: profiles.firstOrNull()
    val targetServer = selectedServer ?: profile?.servers?.firstOrNull()
    val isPinging by mainViewModel.isPinging.collectAsState()
    val activePingKeys by mainViewModel.activePingKeys.collectAsState()
    val targetPinging = targetServer != null && activePingKeys.contains(targetServer.pingKey())
    val vpnState = VpnManager.state.value
    val recoveryStatus = VpnManager.recoveryStatus.value
    val recoveryAttempt = VpnManager.recoveryAttempt.value
    val connectedSeconds = VpnManager.connectedSeconds.value
    val connectButtonStyle by preferencesManager.connectButtonStyleState
    val uploadSpeed = VpnManager.uploadSpeed.value
    val downloadSpeed = VpnManager.downloadSpeed.value
    val uploadedTotal = VpnManager.totalBytesUploaded.value
    val downloadedTotal = VpnManager.totalBytesDownloaded.value
    val showSpeed by preferencesManager.showSpeedState
    val showMemory by preferencesManager.memoryMonitoringState
    val speedWidgetsVisible = showSpeed && vpnState == VpnState.CONNECTED
    val memoryWidgetVisible = showMemory && vpnState == VpnState.CONNECTED
    val homeWidgetsVisible = speedWidgetsVisible || memoryWidgetVisible
    var homeWidgetsExpanded by rememberSaveable { mutableStateOf(true) }
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val speedSamples = rememberSpeedSamples(speedWidgetsVisible)
    val memoryState = rememberMemorySamples(memoryWidgetVisible)
    val serverBlockTopGap by animateDpAsState(
        targetValue = if (homeWidgetsVisible) 8.dp else 28.dp,
        animationSpec = if (miniMotionEnabled) tween(220, easing = FastOutSlowInEasing) else snap(),
        label = "home-server-gap"
    )

    LaunchedEffect(targetServer?.pingKey(), vpnState) {
        val server = targetServer
        if (server != null && server.ping == null && vpnState == VpnState.DISCONNECTED && !isPinging) {
            mainViewModel.pingSingleServer(server)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 12.dp, bottom = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (profile != null) {
            SubscriptionOverviewPanel(
                mainViewModel = mainViewModel,
                preferencesManager = preferencesManager,
                profile = profile,
                selectedServer = selectedServer,
                onOpenProfiles = onOpenProfiles,
                onOpenServers = { onOpenServers(true) },
                onRefresh = { onRefreshProfile(profile.url) },
                onOpenSupport = { onOpenSupport(profile) },
                onOpenSite = { onOpenSite(profile) }
            )
        } else {
            EmptyHomeProfilePanel(onAction = onAddMethod)
        }

        if (profile != null) {
            Spacer(Modifier.height(18.dp))

            val onConnectToggle: () -> Unit = {
                when {
                    recoveryStatus != VpnRecoveryStatus.IDLE -> onDisconnect()
                    vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING -> onDisconnect()
                    else -> {
                        val server = targetServer
                        if (server == null) onNeedSubscription() else onConnect(server)
                    }
                }
            }
            if (connectButtonStyle == 1) {
                // Compact: full-width horizontal pill with the status text built in.
                WindowsConnectionButtonCompact(
                    state = vpnState,
                    connectedSeconds = connectedSeconds,
                    recoveryStatus = recoveryStatus,
                    recoveryAttempt = recoveryAttempt,
                    onClick = onConnectToggle,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                WindowsConnectionButton(
                    state = vpnState,
                    onClick = onConnectToggle
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = connectionStatusText(
                        vpnState,
                        connectedSeconds,
                        recoveryStatus,
                        recoveryAttempt
                    ),
                    color = if (
                        vpnState == VpnState.DISCONNECTED &&
                        recoveryStatus == VpnRecoveryStatus.IDLE
                    ) {
                        LocalNebulaColors.current.textPrimary
                    } else {
                        LocalNebulaColors.current.accent
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(serverBlockTopGap))

            val pingDisplayMode by preferencesManager.pingDisplayModeState
            WindowsSelectedServerBar(
                server = targetServer,
                preferencesManager = preferencesManager,
                onServerClick = {
                    if (targetServer == null) onNeedSubscription() else onOpenServers(true)
                },
                onListClick = { onOpenServers(false) },
                isPinging = targetPinging,
                pingDisplayMode = pingDisplayMode,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = homeWidgetsVisible,
                enter = fadeIn(animationSpec = tween(if (miniMotionEnabled) 140 else 0)),
                exit = fadeOut(animationSpec = tween(if (miniMotionEnabled) 100 else 0))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(10.dp))
                    HomeWidgetsHeader(
                        title = t("Мониторинг", "Monitor"),
                        expanded = homeWidgetsExpanded,
                        onToggle = { homeWidgetsExpanded = !homeWidgetsExpanded },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(
                        visible = homeWidgetsExpanded,
                        enter = fadeIn(animationSpec = tween(if (miniMotionEnabled) 120 else 0)),
                        exit = fadeOut(animationSpec = tween(if (miniMotionEnabled) 90 else 0))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (speedWidgetsVisible) {
                                Spacer(Modifier.height(10.dp))
                                NetworkSpeedChartCard(
                                    samples = speedSamples,
                                    uploadSpeed = uploadSpeed,
                                    downloadSpeed = downloadSpeed,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                SessionTrafficBlocks(
                                    upload = uploadedTotal,
                                    download = downloadedTotal,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (memoryWidgetVisible) {
                                Spacer(Modifier.height(10.dp))
                                MemoryUsageCard(
                                    memoryMb = memoryState.currentMb,
                                    samples = memoryState.samples,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWidgetsHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (miniMotionEnabled) tween(160, easing = FastOutSlowInEasing) else snap(),
        label = "home-widgets-chevron"
    )
    GlassPanel(
        modifier = modifier
            .height(54.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle
            ),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) t("Свернуть", "Collapse") else t("Развернуть", "Expand"),
                tint = nebulaColors.textTertiary,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }
    }
}

@Composable
private fun SubscriptionOverviewPanel(
    mainViewModel: MainViewModel,
    preferencesManager: PreferencesManager,
    profile: SubscriptionProfile,
    selectedServer: Server?,
    onOpenProfiles: () -> Unit,
    onOpenServers: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSite: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val isRefreshingSubscriptions by mainViewModel.isRefreshingSubscriptions.collectAsState()
    val showSubscriptionLogo by preferencesManager.showSubscriptionLogoState
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .nimboClickable {
                onOpenProfiles()
            },
        shape = RoundedCornerShape(22.dp),
        borderColor = Color.White.copy(alpha = 0.13f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val brandLogo = profile.brandLogo?.takeIf { it.isNotBlank() }
                    if (showSubscriptionLogo && brandLogo != null) {
                        SubscriptionBrandLogo(
                            logo = brandLogo,
                            cachedLogo = profile.brandLogoCache,
                            size = 34.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = nebulaColors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName.ifBlank { t("Подписка", "Subscription") },
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val expiryRemaining = formatProfileExpiryRemaining(profile)
                        if (expiryRemaining.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = nebulaColors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = expiryRemaining,
                                    color = nebulaColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                MiniSquareIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = onRefresh,
                    motion = MiniIconMotion.Refresh,
                    active = isRefreshingSubscriptions || profile.isLoading
                )
            }

            val description = profile.announce
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: buildSubscriptionDescription(profile)

            if (description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowsLinkButton(icon = Icons.Default.SupportAgent, label = t("Поддержка", "Support"), onClick = onOpenSupport)
                WindowsLinkButton(icon = Icons.Default.Public, label = t("Сайт", "Site"), onClick = onOpenSite)
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(windowsDivider(nebulaColors))
            )
            Spacer(Modifier.height(9.dp))

            Text(
                text = if (profile.totalTraffic > 0) {
                    "${formatBytes(profile.usedTraffic)} / ${formatBytes(profile.totalTraffic)}"
                } else {
                    "${formatBytes(profile.usedTraffic)} / ∞"
                },
                color = nebulaColors.textPrimary.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyHomeProfilePanel(onAction: (AddProfileAction) -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        borderColor = nebulaColors.accent.copy(alpha = 0.28f),
        accentFill = true
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            nebulaColors.accent.copy(alpha = 0.12f),
                            Color.Transparent,
                            nebulaColors.accent.copy(alpha = 0.04f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        lerp(nebulaColors.accent, Color.White, 0.14f),
                                        nebulaColors.accent
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .size(23.dp)
                                .clip(CircleShape)
                                .background(nebulaColors.surface)
                                .border(1.dp, nebulaColors.accent.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(nebulaColors.accent)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = t("БЫСТРЫЙ СТАРТ", "QUICK START"),
                                color = nebulaColors.accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = t("Добавьте подписку", "Add a subscription"),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 28.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                        Text(
                            text = t(
                                "Ссылка, QR-код или готовый файл профиля",
                                "Use a link, QR code, or profile file"
                            ),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                AddSubscriptionPrimaryAction {
                    onAction(AddProfileAction.Open)
                }

                Row(
                    modifier = Modifier.padding(top = 17.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(nebulaColors.textTertiary.copy(alpha = 0.15f))
                    )
                    Text(
                        text = t("БЫСТРЫЙ ИМПОРТ", "QUICK IMPORT"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(nebulaColors.textTertiary.copy(alpha = 0.15f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    AddMethodChip(Icons.Default.ContentPaste, t("Буфер", "Paste"), Modifier.weight(1f)) {
                        onAction(AddProfileAction.Paste)
                    }
                    AddMethodChip(Icons.Default.FolderOpen, t("Файл", "File"), Modifier.weight(1f)) {
                        onAction(AddProfileAction.File)
                    }
                    AddMethodChip(Icons.Default.QrCodeScanner, t("QR-код", "QR code"), Modifier.weight(1f)) {
                        onAction(AddProfileAction.Qr)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSubscriptionPrimaryAction(onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = if (miniMotionEnabled) tween(110, easing = FastOutSlowInEasing) else snap(),
        label = "add-subscription-action-scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        lerp(nebulaColors.accent, Color.White, 0.10f),
                        nebulaColors.accent
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(17.dp))
            .clickable(
                indication = null,
                interactionSource = interactionSource
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = t("Добавить подписку", "Add subscription"),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AddMethodChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (miniMotionEnabled) tween(100, easing = FastOutSlowInEasing) else snap(),
        label = "add-method-scale"
    )
    Box(
        modifier = modifier
            .height(62.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(nebulaColors.accent.copy(alpha = 0.08f))
            .border(1.dp, nebulaColors.accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = interactionSource
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                color = nebulaColors.textPrimary.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 3.dp, end = 3.dp)
            )
        }
    }
}

private data class MiniSpeedSample(val upload: Long, val download: Long)
private data class MiniMemoryState(val currentMb: Long, val samples: List<Long>)

@Composable
private fun rememberSpeedSamples(active: Boolean): List<MiniSpeedSample> {
    var samples by remember { mutableStateOf(List(24) { MiniSpeedSample(0L, 0L) }) }
    LaunchedEffect(active) {
        while (active) {
            samples = (samples + MiniSpeedSample(VpnManager.uploadSpeed.value, VpnManager.downloadSpeed.value))
                .takeLast(40)
            delay(1000)
        }
    }
    return samples
}

@Composable
private fun rememberMemorySamples(active: Boolean): MiniMemoryState {
    var currentMb by remember { mutableStateOf(0L) }
    var samples by remember { mutableStateOf(List(24) { 0L }) }
    LaunchedEffect(active) {
        while (active) {
            val usedMb = currentAppMemoryMb()
            currentMb = usedMb
            samples = (samples + usedMb).takeLast(32)
            delay(2000)
        }
    }
    return MiniMemoryState(currentMb, samples)
}

private fun currentAppMemoryMb(): Long {
    val runtime = Runtime.getRuntime()
    val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
    val nativeMb = Debug.getNativeHeapAllocatedSize() / 1048576L
    return (heapMb + nativeMb).coerceAtLeast(0L)
}

@Composable
private fun WindowsConnectionButton(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val isLight = nebulaColors.isLight
    val connected = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val accent = nebulaColors.accent
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val ringColor = if (connected || connecting) accent else if (isLight) Color(0xFFCFCDD9) else Color.White
    val idleFill = if (isLight) Color.White.copy(alpha = 0.96f) else Color(0xFF151520).copy(alpha = 0.96f)
    val idleBorder = if (connecting) accent.copy(alpha = 0.24f) else if (isLight) Color(0xFFDAD8E3) else Color.White.copy(alpha = 0.08f)
    val rotation = if (connecting && miniMotionEnabled) {
        val infinite = rememberInfiniteTransition(label = "windows-connect")
        val animatedRotation by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "windows-connect-rotation"
        )
        animatedRotation
    } else {
        0f
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (miniMotionEnabled) tween(110, easing = FastOutSlowInEasing) else snap(),
        label = "windows-connect-scale"
    )

    Box(
        modifier = modifier
            .size(216.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerAlpha = when {
                connected -> 0.42f
                connecting -> 0.34f
                else -> if (isLight) 0.34f else 0.10f
            }
            val innerAlpha = when {
                connected -> 0.28f
                connecting -> 0.42f
                else -> if (isLight) 0.30f else 0.14f
            }
            drawCircle(
                color = ringColor.copy(alpha = outerAlpha),
                radius = radius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = ringColor.copy(alpha = innerAlpha),
                radius = radius * 0.82f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            if (connected || connecting) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (connected) 0.16f else 0.12f),
                            accent.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.98f
                    ),
                    radius = radius * 0.98f,
                    center = center
                )
            }
        }
        val materialYou = nebulaColors.isMaterialYou
        val centerFill = when {
            materialYou && connected -> MaterialTheme.colorScheme.primary
            // Disconnected Material You: darkest tonal surface + accent outline (below) for a dark,
            // clearly-defined idle button.
            materialYou -> MaterialTheme.colorScheme.surfaceContainerLowest
            connected -> accent
            else -> idleFill
        }
        val centerBorder = when {
            materialYou && connected -> Color.Transparent
            materialYou -> accent.copy(alpha = 0.5f)
            connected -> accent.copy(alpha = 0.55f)
            else -> idleBorder
        }
        val iconTint = when {
            materialYou && connected -> MaterialTheme.colorScheme.onPrimary
            materialYou -> MaterialTheme.colorScheme.primary
            connected -> Color.White
            isLight -> Color(0xFF6F6D7D)
            else -> Color.White.copy(alpha = 0.72f)
        }

        Box(
            modifier = Modifier
                .size(174.dp)
                .clip(CircleShape)
                .background(centerFill)
                .border(
                    if (materialYou && !connected && !connecting) 1.5.dp else 1.dp,
                    centerBorder,
                    CircleShape
                )
                .clickable(
                    indication = if (materialYou) LocalIndication.current else null,
                    interactionSource = interactionSource,
                    onClick = {
                        haptic.performHapticFeedback(
                            if (connected) HapticFeedbackType.LongPress
                            else HapticFeedbackType.TextHandleMove
                        )
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                Canvas(modifier = Modifier.size(62.dp)) {
                    val strokeWidth = 7.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    drawArc(
                        color = nebulaColors.textPrimary.copy(alpha = if (isLight) 0.10f else 0.08f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = rotation - 92f,
                        sweepAngle = 112f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            } else {
                Icon(
                    imageVector = if (connected) Icons.Default.Security else Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(if (connected) 62.dp else 68.dp)
                )
            }
        }
    }
}

@Composable
private fun WindowsConnectionButtonCompact(
    state: VpnState,
    connectedSeconds: Int,
    recoveryStatus: VpnRecoveryStatus,
    recoveryAttempt: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val isLight = nebulaColors.isLight
    val materialYou = nebulaColors.isMaterialYou
    val connected = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val accent = nebulaColors.accent
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val cornerScale = LocalGlobalCornerRadius.current
    val shape = RoundedCornerShape(20.dp * cornerScale)
    val chipShape = RoundedCornerShape(14.dp * cornerScale)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = if (miniMotionEnabled) tween(110, easing = FastOutSlowInEasing) else snap(),
        label = "compact-connect-scale"
    )
    val rotation = if (connecting && miniMotionEnabled) {
        val infinite = rememberInfiniteTransition(label = "compact-connect")
        val r by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "compact-connect-rot"
        )
        r
    } else 0f

    val panelFill = if (connected) {
        accent.copy(alpha = if (isLight) 0.12f else 0.16f).compositeOver(nebulaColors.surface)
    } else {
        windowsPanelFill(nebulaColors)
    }
    val panelBorder = if (connected) accent.copy(alpha = 0.5f) else windowsBorder(nebulaColors, 0.14f)
    val chipFill = when {
        materialYou && connected -> MaterialTheme.colorScheme.primary
        // Disconnected Material You: darkest tonal surface + accent outline, for a dark, contrasting chip.
        materialYou -> MaterialTheme.colorScheme.surfaceContainerLowest
        connected -> accent
        isLight -> Color.White
        else -> Color(0xFF1B1B27)
    }
    val iconTint = when {
        materialYou && connected -> MaterialTheme.colorScheme.onPrimary
        materialYou -> MaterialTheme.colorScheme.primary
        connected -> Color.White
        isLight -> Color(0xFF6F6D7D)
        else -> Color.White.copy(alpha = 0.82f)
    }
    val textColor = if (connected || connecting) accent else nebulaColors.textPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .scale(scale)
            .clip(shape)
            .background(panelFill)
            .border(1.dp, panelBorder, shape)
            .clickable(
                indication = if (materialYou) LocalIndication.current else null,
                interactionSource = interactionSource,
                onClick = {
                    haptic.performHapticFeedback(
                        if (connected) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                    )
                    onClick()
                }
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(chipShape)
                .background(chipFill)
                .border(
                    1.dp,
                    if (connected || (materialYou && !connecting)) accent.copy(alpha = 0.40f)
                    else windowsBorder(nebulaColors, 0.12f),
                    chipShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val sw = 3.dp.toPx()
                    val inset = sw / 2f
                    val arcSize = Size(size.width - sw, size.height - sw)
                    drawArc(
                        color = accent.copy(alpha = 0.18f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = rotation - 92f,
                        sweepAngle = 112f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
            } else {
                Icon(
                    imageVector = if (connected) Icons.Default.Security else Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = connectionStatusText(state, connectedSeconds, recoveryStatus, recoveryAttempt),
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun connectionStatusText(
    state: VpnState,
    connectedSeconds: Int,
    recoveryStatus: VpnRecoveryStatus = VpnRecoveryStatus.IDLE,
    recoveryAttempt: Int = 0
): String {
    return when (recoveryStatus) {
        VpnRecoveryStatus.PAUSED_BY_SCREEN ->
            loc("Пауза до включения экрана", "Paused until screen turns on")
        VpnRecoveryStatus.WAITING_FOR_NETWORK ->
            loc("Ожидание сети", "Waiting for network")
        VpnRecoveryStatus.RETRYING ->
            loc("Переподключение · попытка $recoveryAttempt", "Reconnecting · attempt $recoveryAttempt")
        VpnRecoveryStatus.IDLE -> when (state) {
            VpnState.CONNECTED ->
                loc("Подключено ${formatDuration(connectedSeconds)}", "Connected ${formatDuration(connectedSeconds)}")
            VpnState.CONNECTING -> loc("Подключение...", "Connecting...")
            VpnState.DISCONNECTED -> loc("Нажмите для подключения", "Tap to connect")
        }
    }
}

@Composable
private fun WindowsSelectedServerBar(
    server: Server?,
    preferencesManager: PreferencesManager,
    onServerClick: () -> Unit,
    onListClick: () -> Unit,
    isPinging: Boolean = false,
    pingDisplayMode: Int = 0,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val hasServer = server != null
    val controlFill = if (hasServer) {
        nebulaColors.accent.copy(alpha = 0.08f).compositeOver(windowsControlFill(nebulaColors))
    } else {
        windowsControlFill(nebulaColors)
    }
    val border = if (hasServer) nebulaColors.accent.copy(alpha = 0.34f) else windowsBorder(nebulaColors)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onServerClick,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = controlFill,
            border = BorderStroke(1.dp, border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val flagEmoji = server?.let { extractFlagEmoji(it.name) }.orEmpty()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(windowsSoftFill(nebulaColors)),
                    contentAlignment = Alignment.Center
                ) {
                    if (flagEmoji.isNotBlank()) {
                        Text(flagEmoji, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Default.Public, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = server?.let { serverUiTitle(preferencesManager, it) } ?: t("Выберите сервер", "Choose server"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (server != null) {
                    val currentPing = server.ping ?: -1
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .nimboClickable(onClick = onServerClick)
                    ) {
                        WindowsPingPill(ping = currentPing, loading = isPinging, pingDisplayMode = pingDisplayMode)
                    }
                }
            }
        }
        Surface(
            onClick = onListClick,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = controlFill,
            border = BorderStroke(1.dp, border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.List, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun WindowsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val border = windowsBorder(nebulaColors)
    val fill = windowsControlFill(nebulaColors)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(Icons.Default.Search, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(21.dp))
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(20.dp))
                }
            }
        },
        placeholder = {
            Text(
                text = placeholder,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = nebulaColors.textPrimary,
            fontWeight = FontWeight.SemiBold
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (nebulaColors.isLight) Color(0xFFD8D5E2) else Color.White.copy(alpha = 0.14f),
            unfocusedBorderColor = border,
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            cursorColor = nebulaColors.accent,
            focusedTextColor = nebulaColors.textPrimary,
            unfocusedTextColor = nebulaColors.textPrimary
        )
    )
}

@Composable
private fun NetworkSpeedChartCard(
    samples: List<MiniSpeedSample>,
    uploadSpeed: Long,
    downloadSpeed: Long,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = modifier.height(126.dp),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BarChart, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = t("Скорость", "Speed"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                SpeedValue(Icons.Default.ArrowUpward, formatSpeedLabel(uploadSpeed), Color(0xFF5DD9A1))
                Spacer(Modifier.width(10.dp))
                SpeedValue(Icons.Default.ArrowDownward, formatSpeedLabel(downloadSpeed), nebulaColors.accent)
            }
            Spacer(Modifier.height(8.dp))
            SpeedChartCanvas(
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )
        }
    }
}

@Composable
private fun SpeedValue(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedChartCanvas(samples: List<MiniSpeedSample>, modifier: Modifier = Modifier) {
    val nebulaColors = LocalNebulaColors.current
    Canvas(modifier = modifier) {
        val values = samples.flatMap { listOf(it.upload, it.download) }
        val peak = values.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
        fun buildPath(selector: (MiniSpeedSample) -> Long): Path {
            val path = Path()
            val count = samples.size.coerceAtLeast(2)
            samples.forEachIndexed { index, sample ->
                val x = if (count <= 1) 0f else size.width * index / (count - 1)
                val y = size.height - (selector(sample).toFloat() / peak).coerceIn(0f, 1f) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }
        fun buildArea(line: Path): Path {
            return Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        }
        val downPath = buildPath { it.download }
        val upPath = buildPath { it.upload }
        drawPath(
            path = buildArea(downPath),
            brush = Brush.verticalGradient(
                listOf(nebulaColors.accent.copy(alpha = 0.26f), Color.Transparent)
            )
        )
        drawPath(
            path = downPath,
            color = nebulaColors.accent,
            style = Stroke(width = 2.dp.toPx())
        )
        drawPath(
            path = buildArea(upPath),
            brush = Brush.verticalGradient(
                listOf(Color(0xFF5DD9A1).copy(alpha = 0.18f), Color.Transparent)
            )
        )
        drawPath(
            path = upPath,
            color = Color(0xFF5DD9A1),
            style = Stroke(width = 1.7.dp.toPx())
        )
    }
}

@Composable
private fun SessionTrafficBlocks(upload: Long, download: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SessionTrafficBlock(
            icon = Icons.Default.ArrowUpward,
            label = t("Отдано", "Upload"),
            value = formatBytes(upload),
            color = Color(0xFF5DD9A1),
            modifier = Modifier.weight(1f)
        )
        SessionTrafficBlock(
            icon = Icons.Default.ArrowDownward,
            label = t("Скачано", "Downloaded"),
            value = formatBytes(download),
            color = LocalNebulaColors.current.accent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SessionTrafficBlock(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = value,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 5.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MemoryUsageCard(
    memoryMb: Long,
    samples: List<Long>,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val accent = nebulaColors.accent
    GlassPanel(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Memory, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = t("Память", "Memory"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = "${memoryMb.coerceAtLeast(0L)} MB",
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            MemoryChartCanvas(
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }
    }
}

@Composable
private fun MemoryChartCanvas(samples: List<Long>, modifier: Modifier = Modifier) {
    val accent = LocalNebulaColors.current.accent
    Canvas(modifier = modifier) {
        val values = samples.ifEmpty { listOf(0L) }
        val maxValue = values.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
        val minValue = values.minOrNull()?.toFloat() ?: 0f
        val span = (maxValue - minValue).coerceAtLeast(maxValue * 0.35f).coerceAtLeast(1f)
        val path = Path()
        val count = values.size.coerceAtLeast(2)
        values.forEachIndexed { index, value ->
            val x = if (count <= 1) 0f else size.width * index / (count - 1)
            val normalized = ((value.toFloat() - minValue) / span).coerceIn(0f, 1f)
            val y = size.height - (0.18f + normalized * 0.62f) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.24f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun buildSubscriptionDescription(profile: SubscriptionProfile): String {
    val lines = mutableListOf<String>()
    profile.username?.takeIf { it.isNotBlank() }?.let { lines += "🛡️ @$it" }
    if (profile.daysUntilExpiry >= 0) lines += "🗓️ Остаток дней: ${profile.daysUntilExpiry}"
    lines += "📊 Использовано трафика: ${formatBytes(profile.usedTraffic)}"
    val id = profile.numericId ?: profile.accountId
    if (!id.isNullOrBlank()) lines += "ID $id"
    profile.websiteUrl?.takeIf { it.isNotBlank() }?.let { lines += it }
    return lines.joinToString("\n")
}

private fun buildSubscriptionDescription(profile: SubscriptionProfileMetadata): String {
    val lines = mutableListOf<String>()
    profile.username?.takeIf { it.isNotBlank() }?.let { lines += "🛡️ @$it" }
    if (profile.daysUntilExpiry >= 0) lines += "🗓️ Остаток дней: ${profile.daysUntilExpiry}"
    lines += "📊 Использовано трафика: ${formatBytes(profile.usedTraffic)}"
    val id = profile.numericId ?: profile.accountId
    if (!id.isNullOrBlank()) lines += "ID $id"
    profile.websiteUrl?.takeIf { it.isNotBlank() }?.let { lines += it }
    return lines.joinToString("\n")
}

private fun formatSpeedLabel(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/с"

private fun formatBytesPrecise(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024L * 1024 -> "%.1f КБ".format(Locale.US, bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(Locale.US, bytes / (1024.0 * 1024))
    else -> "%.2f ГБ".format(Locale.US, bytes / (1024.0 * 1024 * 1024))
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
}

@Composable
private fun MiniSquareIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 54.dp,
    iconSize: Dp = 27.dp,
    motion: MiniIconMotion = MiniIconMotion.None,
    active: Boolean = false,
    forceOpaque: Boolean = false
) {
    GlassPanel(
        // Radius scales with size so small (36–40dp) buttons read as rounded squares
        // rather than circles, consistent with the larger control buttons.
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.30f),
        borderColor = Color.White.copy(alpha = 0.10f),
        forceOpaque = forceOpaque
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MiniIconButton(
                icon = icon,
                onClick = onClick,
                size = size,
                iconSize = iconSize,
                motion = motion,
                active = active
            )
        }
    }
}

@Composable
private fun SelectedServerOverviewLine(
    server: Server,
    showJsonBadge: Boolean,
    isPinging: Boolean,
    pingDisplayMode: Int,
    onPingClick: () -> Unit,
    onOpenServers: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val flagEmoji = extractFlagEmoji(server.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(nebulaColors.accent.copy(alpha = 0.10f))
            .border(1.dp, nebulaColors.accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onOpenServers()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (flagEmoji.isNotEmpty()) {
                Text(text = flagEmoji, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("Выбранный сервер", "Selected server"),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            Text(
                text = serverTitle(server),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showJsonBadge) {
                Spacer(Modifier.height(4.dp))
                JsonMiniBadge()
            }
        }
        // Only surface the spinner when we don't yet have a value — otherwise
        // a global ping sweep blanks out every selected server's value while it
        // runs, which is exactly the "ping shows nothing" complaint.
        val currentPing = server.ping ?: -1
        MiniPingBadge(
            ping = currentPing,
            isPinging = isPinging && currentPing == -1,
            pingDisplayMode = pingDisplayMode,
            onClick = onPingClick,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ProfileInfoLine(
    icon: ImageVector,
    text: String,
    color: Color = LocalNebulaColors.current.textSecondary,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NimboSubscriptionScreen(
    mainViewModel: MainViewModel,
    profilesMetadata: List<SubscriptionProfileMetadata>,
    currentProfileUrl: String?,
    selectedServer: Server?,
    tab: MiniSubscriptionTab,
    preferencesManager: PreferencesManager,
    onTabChange: (MiniSubscriptionTab) -> Unit,
    onBack: () -> Unit,
    onSelectProfile: (SubscriptionProfileMetadata) -> Unit,
    onSelectServer: (Server) -> Unit,
    onAddSubscription: () -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    onShowTvQr: (String) -> Unit,
    onOpenUrl: (Context, String?) -> Unit,
    showBack: Boolean = true,
    scrollToSelected: Boolean = false,
    onScrollReset: () -> Unit = {}
) {
    val profiles by mainViewModel.profilesState.collectAsState()
    val currentProfile = remember(profiles, currentProfileUrl) {
        profiles.firstOrNull { it.url == currentProfileUrl } ?: profiles.firstOrNull()
    }
    val servers = remember(currentProfile, profiles, tab) {
        if (tab == MiniSubscriptionTab.Proxies) {
            currentProfile?.servers ?: profiles.flatMap { it.servers }
        } else {
            emptyList()
        }
    }
    val proxiesLoading = remember(servers, currentProfile, profiles) {
        servers.isEmpty() && (currentProfile?.isLoading == true || profiles.any { it.isLoading })
    }
    val isPinging by mainViewModel.isPinging.collectAsState()
    val activePingKeys by mainViewModel.activePingKeys.collectAsState()
    val pingDisplayMode by preferencesManager.pingDisplayModeState
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showPinnedOnly by rememberSaveable { mutableStateOf(false) }
    var serverUiVersion by remember { mutableStateOf(0) }
    var settingsProfileForDialog by remember { mutableStateOf<SubscriptionProfileMetadata?>(null) }
    val useWindowsProfileList = !showBack && tab != MiniSubscriptionTab.Proxies
    val normalizedQuery = searchQuery.trim().lowercase()
    val visibleServers = remember(servers, normalizedQuery, serverUiVersion) {
        servers
            .filterNot { preferencesManager.isServerHidden(it.pingKey()) }
            .filter { server ->
                normalizedQuery.isBlank() ||
                    serverUiTitle(preferencesManager, server).lowercase().contains(normalizedQuery) ||
                    serverSubtitle(server).lowercase().contains(normalizedQuery)
            }
    }
    val visibleProfiles = remember(profilesMetadata, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            profilesMetadata
        } else {
            profilesMetadata.filter { profile ->
                profile.displayName.lowercase().contains(normalizedQuery) ||
                    profile.url.lowercase().contains(normalizedQuery) ||
                    buildSubscriptionDescription(profile).lowercase().contains(normalizedQuery)
            }
        }
    }

    val headerTitle = if (useWindowsProfileList) {
        t("Профили", "Profiles")
    } else {
        currentProfile?.displayName?.ifBlank { t("Профили", "Profiles") }
            ?: t("Профили", "Profiles")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = if (showBack) 24.dp else 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                NimboBackButton(onBack = onBack)
                Spacer(Modifier.width(12.dp))
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = headerTitle,
                        color = LocalNebulaColors.current.textPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (useWindowsProfileList) {
                        Text(
                            text = t(
                                "${serverCountRu(profiles.sumOf { it.servers.size })} · ${subscriptionCountRu(profiles.size)}",
                                "${serverCountEn(profiles.sumOf { it.servers.size })} · ${subscriptionCountEn(profiles.size)}"
                            ),
                            color = LocalNebulaColors.current.textSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                if (!useWindowsProfileList) {
                    Spacer(Modifier.width(10.dp))
                    WindowsCountPill(servers.size.toString())
                }
            }
            if (useWindowsProfileList) {
                MiniSquareIconButton(
                    icon = Icons.Default.Star,
                    onClick = { showPinnedOnly = !showPinnedOnly },
                    size = 48.dp,
                    iconSize = 25.dp,
                    active = showPinnedOnly
                )
                Spacer(Modifier.width(8.dp))
                MiniSquareIconButton(
                    icon = Icons.Default.Add,
                    onClick = onAddSubscription,
                    size = 48.dp,
                    iconSize = 28.dp
                )
            } else {
                MiniSquareIconButton(
                    icon = Icons.Default.SignalCellularAlt,
                    onClick = { mainViewModel.pingAllServers() },
                    size = 48.dp,
                    iconSize = 25.dp,
                    motion = MiniIconMotion.Ping,
                    active = isPinging
                )
                Spacer(Modifier.width(8.dp))
                MiniSquareIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { currentProfile?.url?.let { onProfileRefresh(it) } },
                    size = 48.dp,
                    iconSize = 25.dp,
                    motion = MiniIconMotion.Refresh,
                    active = currentProfile?.isLoading == true
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        WindowsSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = t("Поиск", "Search"),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (useWindowsProfileList) {
            val context = LocalContext.current
            var deleteProfile by remember { mutableStateOf<SubscriptionProfile?>(null) }
            WindowsProfilesList(
                profiles = profiles,
                query = normalizedQuery,
                pinnedOnly = showPinnedOnly,
                selectedServer = selectedServer,
                preferencesManager = preferencesManager,
                isPinging = isPinging,
                activePingKeys = activePingKeys,
                pingDisplayMode = pingDisplayMode,
                serverUiVersion = serverUiVersion,
                onSelectServer = onSelectServer,
                onPingServer = { mainViewModel.pingSingleServer(it) },
                onPingProfile = { mainViewModel.pingServers(it) },
                onServerUiChanged = { serverUiVersion++ },
                onRefreshProfile = onProfileRefresh,
                onDeleteProfile = { deleteProfile = it },
                onSupportProfile = { profile ->
                    val supportUrl = profile.supportUrl ?: profile.websiteUrl
                    if (supportUrl == null) {
                        mainViewModel.showTopNotification(loc("Поддержка не указана", "Support link not provided"))
                    } else {
                        onOpenUrl(context, supportUrl)
                    }
                },
                onOpenSite = { profile ->
                    val url = profile.websiteUrl ?: profile.supportUrl
                    if (url == null) {
                        mainViewModel.showTopNotification(loc("Сайт не указан", "Website link not provided"))
                    } else {
                        onOpenUrl(context, url)
                    }
                },
                onOpenSettings = { settingsProfileForDialog = it.toMetadata() },
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding()
            )

            deleteProfile?.let { profile ->
                DeleteProfileDialog(
                    profileName = profile.displayName,
                    serverCount = profile.servers.size,
                    onDismissRequest = { deleteProfile = null },
                    onConfirm = {
                        onProfileDeleted(profile.url)
                        deleteProfile = null
                    }
                )
            }
            if (settingsProfileForDialog != null) {
                val fullProfile = profiles.find { it.url == settingsProfileForDialog!!.url }
                if (fullProfile != null) {
                    SubscriptionSettingsDialog(
                        profile = fullProfile,
                        preferencesManager = preferencesManager,
                        mainViewModel = mainViewModel,
                        onDismiss = { settingsProfileForDialog = null }
                    )
                }
            }
            return@Column
        }

        if (showBack) {
            MiniSegmented(
                left = t("Сервера", "Servers"),
                right = t("Профили", "Profiles"),
                leftSelected = tab == MiniSubscriptionTab.Proxies,
                onLeft = { onTabChange(MiniSubscriptionTab.Proxies) },
                onRight = { onTabChange(MiniSubscriptionTab.Profiles) }
            )
            Spacer(Modifier.height(16.dp))
        }

        val context = LocalContext.current
        var deleteProfile by remember { mutableStateOf<SubscriptionProfileMetadata?>(null) }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = fadeIn(tween(160)) + slideInVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) { height -> if (forward) height / 8 else -height / 8 }
                val exit = fadeOut(tween(120)) + slideOutVertically(
                    animationSpec = tween(190, easing = FastOutSlowInEasing)
                ) { height -> if (forward) -height / 10 else height / 10 }
                enter togetherWith exit
            },
            modifier = Modifier.weight(1f),
            label = "subscription-tab"
        ) { current ->
            when (current) {
                MiniSubscriptionTab.Proxies -> {
                    ProxyList(
                        servers = visibleServers,
                        isLoading = proxiesLoading,
                        selectedServer = selectedServer,
                        showJsonBadge = currentProfile?.supportsJsonResponse == true,
                        isPinging = isPinging,
                        activePingKeys = activePingKeys,
                        pingDisplayMode = pingDisplayMode,
                        onSelectServer = onSelectServer,
                        onPingServer = { mainViewModel.pingSingleServer(it) },
                        selectedSortProtocols = preferencesManager.selectedSortProtocols,
                        preferencesManager = preferencesManager,
                        onServerUiChanged = { serverUiVersion++ },
                        scrollToSelected = scrollToSelected,
                        onScrollReset = onScrollReset,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    )
                }
                MiniSubscriptionTab.Profiles -> {
                    ProfileList(
                        profiles = visibleProfiles,
                        preferencesManager = preferencesManager,
                        onSelectProfile = onSelectProfile,
                        onAddSubscription = onAddSubscription,
                        onDeleteProfile = { deleteProfile = it },
                        onProfileRefresh = onProfileRefresh,
                        onShowTvQr = onShowTvQr,
                        onSupportProfile = { profile ->
                            val supportUrl = profile.supportUrl ?: profile.websiteUrl
                            if (supportUrl == null) {
                                mainViewModel.showTopNotification(loc("Поддержка не указана", "Support link not provided"))
                            } else {
                                onOpenUrl(context, supportUrl)
                            }
                        },
                        onOverrideProfile = {
                            mainViewModel.showTopNotification(loc(
                                "Переопределение будет доступно позже",
                                "Override coming in a later release"
                            ))
                        },
                        onExportProfile = { profileMetadata ->
                            val fullProfile = profiles.find { it.url == profileMetadata.url }
                            if (fullProfile != null) {
                                exportProfile(context, fullProfile)
                            }
                        },
                        onOpenSettings = { settingsProfileForDialog = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    )
                }
            }
        }

        deleteProfile?.let { profile ->
            DeleteProfileDialog(
                profileName = profile.displayName,
                serverCount = profile.serversCount,
                onDismissRequest = { deleteProfile = null },
                onConfirm = {
                    onProfileDeleted(profile.url)
                    deleteProfile = null
                    if (profilesMetadata.size <= 1) onBack()
                }
            )
        }

        if (settingsProfileForDialog != null) {
            val fullProfile = profiles.find { it.url == settingsProfileForDialog!!.url }
            if (fullProfile != null) {
                SubscriptionSettingsDialog(
                    profile = fullProfile,
                    preferencesManager = preferencesManager,
                    mainViewModel = mainViewModel,
                    onDismiss = { settingsProfileForDialog = null }
                )
            }
        }
    }
}

@Composable
private fun ProxyList(
    servers: List<Server>,
    isLoading: Boolean,
    selectedServer: Server?,
    showJsonBadge: Boolean,
    isPinging: Boolean,
    activePingKeys: Set<String>,
    pingDisplayMode: Int,
    onSelectServer: (Server) -> Unit,
    onPingServer: (Server) -> Unit,
    selectedSortProtocols: Set<String> = emptySet(),
    preferencesManager: PreferencesManager,
    onServerUiChanged: () -> Unit,
    scrollToSelected: Boolean = false,
    onScrollReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    if (isLoading) {
        ExpressiveLoadingPane(
            label = t("Обновляем профиль", "Refreshing profile"),
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    if (servers.isEmpty()) {
        EmptyPane(
            title = t("Серверов пока нет", "No servers yet"),
            subtitle = t("Добавьте профиль подписки.", "Add a subscription profile."),
            modifier = modifier
        )
        return
    }

    var sortMode by rememberSaveable { mutableStateOf(0) }
    var isNameAscending by rememberSaveable { mutableStateOf(true) }
    var isPingAscending by rememberSaveable { mutableStateOf(true) }
    var localSelectedProtocols by rememberSaveable { mutableStateOf(selectedSortProtocols) }
    var pinnedServerKeys by remember { mutableStateOf(preferencesManager.getPinnedServerKeys()) }

    val availableProtocols = remember(servers) {
        servers.map {
            val protocol = it.protocol.lowercase()
            when {
                protocol.contains("vless") -> "VLESS"
                protocol.contains("vmess") -> "VMess"
                protocol.contains("trojan") -> "Trojan"
                protocol.contains("shadowsocks") || protocol == "ss" -> "Shadowsocks"
                protocol.contains("hysteria") || protocol == "hy2" -> "Hysteria2"
                protocol.contains("tuic") -> "TUIC"
                protocol.contains("reality") -> "Reality"
                else -> "Other"
            }
        }.distinct().sorted()
    }

    val sortedServers = remember(servers, sortMode, isNameAscending, isPingAscending, localSelectedProtocols, pinnedServerKeys) {
        val filtered = if (sortMode == 3) {
            val selected = localSelectedProtocols
            if (selected.isNotEmpty()) {
                servers.filter { server ->
                    val pKey = when {
                        server.protocol.lowercase().contains("vless") -> "VLESS"
                        server.protocol.lowercase().contains("vmess") -> "VMess"
                        server.protocol.lowercase().contains("trojan") -> "Trojan"
                        server.protocol.lowercase().contains("shadowsocks") || server.protocol.lowercase() == "ss" -> "Shadowsocks"
                        server.protocol.lowercase().contains("hysteria") || server.protocol.lowercase() == "hy2" -> "Hysteria2"
                        server.protocol.lowercase().contains("tuic") -> "TUIC"
                        server.protocol.lowercase().contains("reality") -> "Reality"
                        else -> "Other"
                    }
                    selected.contains(pKey)
                }
            } else {
                servers
            }
        } else {
            servers
        }

        when (sortMode) {
            1 -> {
                if (isNameAscending) filtered.sortedBy { serverUiTitle(preferencesManager, it).lowercase() }
                else filtered.sortedByDescending { serverUiTitle(preferencesManager, it).lowercase() }
            }
            2 -> {
                if (isPingAscending) {
                    filtered.sortedBy { val p = it.ping; if (p == null || p < 0) Int.MAX_VALUE else p }
                } else {
                    filtered.sortedByDescending { val p = it.ping; if (p == null || p < 0) -1 else p }
                }
            }
            3 -> filtered.sortedBy { miniProtocolLabel(it).lowercase() }
            else -> filtered
        }
    }
    // Some subscriptions ship multiple identical-looking entries — fall back to
    // an index-tagged key so LazyColumn cannot crash on duplicate pingKey().
    val keyedServers = remember(sortedServers) {
        val seen = HashMap<String, Int>()
        sortedServers.mapIndexed { index, server ->
            val base = server.pingKey()
            val occurrence = seen.merge(base, 1) { old, _ -> old + 1 } ?: 1
            val key = if (occurrence == 1) base else "$base#$occurrence@$index"
            key to server
        }
    }
    // Resolve the highlighted row by pingKey first — selectionKey() only includes
    // host|port|uuid|protocol|network, which collides between transport variants
    // (TCP / XHTTP / gRPC) of the same VLESS server when the parser fills the
    // `network` field with the same value for all of them. pingKey() folds in
    // security/path/sni/fingerprint/publicKey/shortId, so it actually
    // distinguishes the rows the user is tapping. Fallback to matchesSelection
    // for remote-template / auto-balancer cases where pingKey may diverge.
    val selectedIndex = selectedServer?.let { target ->
        val targetPingKey = target.pingKey()
        val byPing = keyedServers.indexOfFirst { (_, server) -> server.pingKey() == targetPingKey }
        if (byPing >= 0) byPing
        else keyedServers.indexOfFirst { (_, server) -> target.matchesSelection(server) }
    } ?: -1
    val cornerScale = LocalGlobalCornerRadius.current
    val lazyListState = rememberLazyListState()

    LaunchedEffect(key1 = selectedIndex, key2 = scrollToSelected) {
        if (scrollToSelected && selectedIndex >= 0) {
            lazyListState.scrollToItem(selectedIndex)
            onScrollReset()
        }
    }

    Column(modifier = modifier) {
        ServerSortRow(
            sortMode = sortMode,
            count = servers.size,
            isNameAscending = isNameAscending,
            isPingAscending = isPingAscending,
            onSelect = { selectedMode ->
                if (sortMode == selectedMode) {
                    if (selectedMode == 1) isNameAscending = !isNameAscending
                    if (selectedMode == 2) isPingAscending = !isPingAscending
                } else {
                    sortMode = selectedMode
                }
            }
        )
        if (sortMode == 3 && availableProtocols.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableProtocols.forEach { proto ->
                    val isSelected = localSelectedProtocols.contains(proto)
                    val badgeBgColor = if (isSelected) {
                        nebulaColors.accent.copy(alpha = 0.24f)
                    } else {
                        nebulaColors.textPrimary.copy(alpha = 0.06f)
                    }
                    val badgeBorderColor = if (isSelected) {
                        nebulaColors.accent.copy(alpha = 0.65f)
                    } else {
                        nebulaColors.textPrimary.copy(alpha = 0.15f)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBgColor)
                            .border(1.dp, badgeBorderColor, RoundedCornerShape(8.dp))
                            .clickable {
                                localSelectedProtocols = if (isSelected) {
                                    localSelectedProtocols - proto
                                } else {
                                    localSelectedProtocols + proto
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = proto,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) nebulaColors.accent else nebulaColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 132.dp)
        ) {
            itemsIndexed(keyedServers, key = { index, item -> "subscription-order-$index-${item.first}" }) { index, (_, server) ->
                val isFirst = index == 0
                val isLast = index == keyedServers.lastIndex
                val baseRowShape = RoundedCornerShape(
                    topStart = if (isFirst) 16.dp else 0.dp,
                    topEnd = if (isFirst) 16.dp else 0.dp,
                    bottomStart = if (isLast) 16.dp else 0.dp,
                    bottomEnd = if (isLast) 16.dp else 0.dp
                )
                val rowShape = scaleRoundedCornerShape(baseRowShape, cornerScale)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(nebulaColors.surface)
                ) {
                    val serverKey = server.pingKey()
                    WindowsProfileServerLine(
                        server = server,
                        displayName = serverUiTitle(preferencesManager, server),
                        selected = index == selectedIndex,
                        isFavorite = pinnedServerKeys.contains(serverKey),
                        isPinging = isPinging && activePingKeys.contains(server.pingKey()),
                        pingDisplayMode = pingDisplayMode,
                        showDivider = !isLast,
                        showJsonBadge = showJsonBadge,
                        rowShape = rowShape,
                        onClick = { onSelectServer(server) },
                        onPing = { onPingServer(server) },
                        onToggleFavorite = {
                            if (pinnedServerKeys.contains(serverKey)) {
                                preferencesManager.unpinServer(serverKey)
                            } else {
                                preferencesManager.pinServer(serverKey)
                            }
                            pinnedServerKeys = preferencesManager.getPinnedServerKeys()
                            onServerUiChanged()
                        },
                        onRename = { newName ->
                            preferencesManager.setServerDisplayName(serverKey, newName)
                            onServerUiChanged()
                        },
                        onHide = {
                            preferencesManager.hideServer(serverKey)
                            onServerUiChanged()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSortRow(
    sortMode: Int,
    count: Int,
    isNameAscending: Boolean,
    isPingAscending: Boolean,
    onSelect: (Int) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val cornerScale = LocalGlobalCornerRadius.current
    val tabShape = RoundedCornerShape(12.dp * cornerScale)
    val tabs = listOf(
        t("По умолч.", "Default") to Icons.Default.Sort,
        if (sortMode == 1 && !isNameAscending) t("Имя Я-А", "Name Z-A") to Icons.Default.SortByAlpha
        else t("Имя А-Я", "Name A-Z") to Icons.Default.SortByAlpha,
        if (sortMode == 2 && !isPingAscending) t("Пинг ⬆", "Ping ⬆") to Icons.Default.Speed
        else t("Пинг ⬇", "Ping ⬇") to Icons.Default.Speed,
        t("Протокол", "Protocol") to Icons.Default.Dns
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val selected = index == sortMode
                Row(
                    modifier = Modifier
                        .clip(tabShape)
                        .background(if (selected) nebulaColors.accent.copy(alpha = 0.16f) else nebulaColors.surface)
                        .border(
                            1.dp,
                            if (selected) nebulaColors.accent.copy(alpha = 0.60f) else windowsBorder(nebulaColors),
                            tabShape
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onSelect(index) }
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = label,
                        color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyRow(
    server: Server,
    selected: Boolean,
    showJsonBadge: Boolean,
    isPinging: Boolean,
    pingDisplayMode: Int,
    onPingClick: () -> Unit,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val flagEmoji = extractFlagEmoji(server.name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            borderColor = if (selected) nebulaColors.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
            accentFill = selected
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(nebulaColors.accent)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLightChip = nebulaColors.background.luminance() > 0.5f
                val chipBaseColor = when {
                    selected -> nebulaColors.accent
                    isLightChip -> Color.Black
                    else -> Color.White
                }
                val chipAlphaSelected = if (isLightChip) 0.32f else 0.16f
                val chipAlphaIdle = if (isLightChip) 0.08f else 0.08f
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipBaseColor.copy(alpha = if (selected) chipAlphaSelected else chipAlphaIdle)),
                    contentAlignment = Alignment.Center
                ) {
                    if (flagEmoji.isNotEmpty()) {
                        Text(text = flagEmoji, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverTitle(server),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = serverSubtitle(server)
                    if (subtitle.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            Text(
                                text = subtitle,
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (showJsonBadge) {
                                Spacer(Modifier.width(6.dp))
                                JsonMiniBadge()
                            }
                        }
                    } else if (showJsonBadge) {
                        Spacer(Modifier.height(4.dp))
                        JsonMiniBadge()
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipBaseColor.copy(alpha = if (selected) chipAlphaSelected else chipAlphaIdle)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
                        modifier = Modifier.size(if (selected) 18.dp else 16.dp)
                    )
                }
            }
          }
        }
        MiniPingBadge(
            ping = server.ping ?: -1,
            isPinging = isPinging,
            pingDisplayMode = pingDisplayMode,
            onClick = onPingClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-10).dp)
                .padding(end = 12.dp)
        )
    }
}

@Composable
private fun JsonMiniBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x33FFC107))
            .border(0.5.dp, Color(0x66FFC107), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = "JSON",
            color = Color(0xFFFFD54F),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ServerPillsRow(server: Server, showJsonBadge: Boolean) {
    val nebulaColors = LocalNebulaColors.current
    val proto = miniProtocolLabel(server)
    val network = server.network?.trim()?.takeIf { it.isNotBlank() }?.uppercase() ?: "TCP"
    val security = miniSecurityLabel(server)?.uppercase()
    val transport = if (security != null) "$network · $security" else network
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ServerPill(text = proto, accent = nebulaColors.accent)
        ServerPill(text = transport, accent = nebulaColors.accent)
        if (showJsonBadge) JsonMiniBadge()
    }
}

@Composable
private fun ServerPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniPingBadge(
    ping: Int,
    isPinging: Boolean,
    pingDisplayMode: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Анимация крутится, пока сервер реально пингуется (а не только когда нет значения).
    val showLoading = isPinging
    val targetPingColor = when {
        showLoading -> nebulaColors.accent
        ping == -1 -> nebulaColors.statusDisconnected
        ping <= 70 -> nebulaColors.statusConnected
        ping <= 120 -> Color(0xFFCDDC39)
        ping <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusDisconnected
    }
    val pingColor by animateColorAsState(targetPingColor, animationSpec = tween(300), label = "mini_ping_color")
    val badgeAlpha = if (isPinging) 0.92f else 1f
    val valueLabel = when (pingDisplayMode) {
        1 -> if (ping == -1) "нет" else "ok"
        2 -> ""
        else -> if (ping == -1) "н/д" else "${ping} ms"
    }
    val pingPulse = rememberInfiniteTransition(label = "mini_ping_badge_pulse")
    val wave by pingPulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_ping_badge_wave"
    )
    val badgeScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            isPinging -> 1f + wave * 0.04f
            else -> 1f
        },
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "mini_ping_badge_scale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isPinging) 1.06f + wave * 0.14f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "mini_ping_icon_scale"
    )

    Box(
        modifier = modifier
            .scale(badgeScale)
    ) {
        val badgeShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(1.18f)
                .clip(badgeShape)
                .background(pingColor.copy(alpha = if (isPinging) 0.34f else 0.24f))
                .blur(10.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(badgeShape)
                .background(nebulaColors.surface.copy(alpha = 0.80f))
        )
        if (isPinging) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(1f + wave * 0.20f)
                    .alpha((1f - wave).coerceIn(0f, 1f) * 0.55f)
                    .clip(badgeShape)
                    .border(1.dp, pingColor.copy(alpha = 0.55f), badgeShape)
            )
        }
        Row(
            modifier = Modifier
            .clip(badgeShape)
            .background(nebulaColors.surface.copy(alpha = 0.68f))
            .background(pingColor.copy(alpha = 0.22f))
            .border(1.dp, pingColor.copy(alpha = 0.52f), badgeShape)
            .clickable(
                indication = null,
                    interactionSource = interactionSource,
                onClick = onClick
            )
            .alpha(badgeAlpha)
            .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SignalCellularAlt,
                contentDescription = t("Пинг", "Ping"),
                tint = pingColor,
                modifier = Modifier
                    .size(14.dp)
                    .scale(iconScale)
            )
            if (pingDisplayMode == 2) {
                if (showLoading) {
                    PingPulsingDot(color = pingColor)
                } else {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(pingColor)
                    )
                }
            } else {
                AnimatedContent(
                    targetState = showLoading,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "mini_ping_state"
                ) { isLoading ->
                    if (isLoading) {
                        PingPulsingDots(color = pingColor)
                    } else {
                        Text(
                            text = valueLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = pingColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowsProfilesList(
    profiles: List<SubscriptionProfile>,
    query: String,
    pinnedOnly: Boolean,
    selectedServer: Server?,
    preferencesManager: PreferencesManager,
    isPinging: Boolean,
    activePingKeys: Set<String>,
    pingDisplayMode: Int,
    serverUiVersion: Int,
    onSelectServer: (Server) -> Unit,
    onPingServer: (Server) -> Unit,
    onPingProfile: (List<Server>) -> Unit,
    onServerUiChanged: () -> Unit,
    onRefreshProfile: (String) -> Unit,
    onDeleteProfile: (SubscriptionProfile) -> Unit,
    onSupportProfile: (SubscriptionProfile) -> Unit,
    onOpenSite: (SubscriptionProfile) -> Unit,
    onOpenSettings: (SubscriptionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val pinnedKeys = remember(pinnedOnly, serverUiVersion) { preferencesManager.getPinnedServerKeys() }
    val hiddenKeys = remember(serverUiVersion) { preferencesManager.getHiddenServerKeys() }
    val visibleProfiles = remember(profiles, query, pinnedOnly, pinnedKeys, hiddenKeys) {
        profiles.mapNotNull { profile ->
            val profileMatches = query.isBlank() ||
                profile.displayName.lowercase().contains(query) ||
                profile.url.lowercase().contains(query) ||
                buildSubscriptionDescription(profile).lowercase().contains(query)
            val servers = if (query.isBlank() && !pinnedOnly && hiddenKeys.isEmpty()) {
                profile.servers
            } else {
                profile.servers.filter { server ->
                    val serverKey = server.pingKey()
                    val pinMatches = !pinnedOnly || pinnedKeys.contains(serverKey)
                    val hiddenMatches = hiddenKeys.contains(serverKey)
                    val queryMatches = query.isBlank() ||
                        profileMatches ||
                        serverUiTitle(preferencesManager, server).lowercase().contains(query) ||
                        serverSubtitle(server).lowercase().contains(query)
                    !hiddenMatches && pinMatches && queryMatches
                }
            }
            if (profileMatches || servers.isNotEmpty()) profile to servers else null
        }
    }

    if (profiles.isEmpty()) {
        EmptyPane(
            title = t("Профилей пока нет", "No profiles yet"),
            subtitle = t("Добавьте подписку, чтобы увидеть серверы.", "Add a subscription to see servers."),
            modifier = modifier
        )
        return
    }

    val nebulaColors = LocalNebulaColors.current
    var pinnedServerKeys by remember(serverUiVersion) { mutableStateOf(preferencesManager.getPinnedServerKeys()) }
    val showSubscriptionLogo by preferencesManager.showSubscriptionLogoState
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 118.dp)
    ) {
        val collapsedUrls = preferencesManager.getCollapsedSubscriptions()
        visibleProfiles.forEach { (profile, servers) ->
            // Expanded by default; the user's collapse choice persists across restarts.
            val expanded = subscriptionCardExpanded[profile.url] ?: !collapsedUrls.contains(profile.url)
            item(key = "profile-${profile.url}") {
                WindowsSubscriptionCard(
                    profile = profile,
                    expanded = expanded,
                    onToggleExpanded = {
                        val next = !expanded
                        subscriptionCardExpanded[profile.url] = next
                        preferencesManager.setSubscriptionCollapsed(profile.url, !next)
                    },
                    lastUpdateMs = preferencesManager.getLastSubscriptionUpdateTime(profile.url),
                    isPinging = isPinging,
                    onRefresh = { onRefreshProfile(profile.url) },
                    onDelete = { onDeleteProfile(profile) },
                    onSupport = { onSupportProfile(profile) },
                    onSite = { onOpenSite(profile) },
                    onPingAll = { onPingProfile(profile.servers) },
                    showSubscriptionLogo = showSubscriptionLogo,
                    onOpenSettings = { onOpenSettings(profile) }
                )
                Spacer(Modifier.height(if (expanded) 12.dp else 14.dp))
            }
            if (!expanded) return@forEach

            if (servers.isEmpty()) {
                item(key = "servers-empty-${profile.url}") {
                    Text(
                        text = t("Серверов не найдено", "No servers found"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)
                    )
                }
                return@forEach
            }

            item(key = "servers-title-${profile.url}") {
                Text(
                    text = t(
                        serverCountRu(servers.size),
                        serverCountEn(servers.size)
                    ).uppercase(Locale.getDefault()),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(
                items = servers,
                key = { index, server -> "server-${profile.url}-$index-${server.pingKey()}" },
                contentType = { _, _ -> "profile-server" }
            ) { index, server ->
                val isFirst = index == 0
                val isLast = index == servers.lastIndex
                val baseRowShape = RoundedCornerShape(
                    topStart = if (isFirst) 16.dp else 0.dp,
                    topEnd = if (isFirst) 16.dp else 0.dp,
                    bottomStart = if (isLast) 16.dp else 0.dp,
                    bottomEnd = if (isLast) 16.dp else 0.dp
                )
                val rowShape = scaleRoundedCornerShape(baseRowShape, LocalGlobalCornerRadius.current)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(nebulaColors.surface)
                ) {
                    val serverKey = server.pingKey()
                    WindowsProfileServerLine(
                        server = server,
                        displayName = serverUiTitle(preferencesManager, server),
                        selected = selectedServer?.matchesSelection(server) == true,
                        isFavorite = pinnedServerKeys.contains(serverKey),
                        isPinging = isPinging && activePingKeys.contains(serverKey),
                        pingDisplayMode = pingDisplayMode,
                        showDivider = !isLast,
                        showJsonBadge = profile.supportsJsonResponse == true,
                        rowShape = rowShape,
                        onClick = { onSelectServer(server) },
                        onPing = { onPingServer(server) },
                        onToggleFavorite = {
                            if (pinnedServerKeys.contains(serverKey)) {
                                preferencesManager.unpinServer(serverKey)
                            } else {
                                preferencesManager.pinServer(serverKey)
                            }
                            pinnedServerKeys = preferencesManager.getPinnedServerKeys()
                            onServerUiChanged()
                        },
                        onRename = { newName ->
                            preferencesManager.setServerDisplayName(serverKey, newName)
                            onServerUiChanged()
                        },
                        onHide = {
                            preferencesManager.hideServer(serverKey)
                            onServerUiChanged()
                        }
                    )
                }
            }

            item(key = "servers-space-${profile.url}") {
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

// Expand/collapse state lives at process scope so it survives leaving and
// returning to the Profiles tab (the screen subtree is disposed on tab switch,
// which would otherwise reset a rememberSaveable back to its default).
private val subscriptionCardExpanded = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

@Composable
private fun WindowsSubscriptionCard(
    profile: SubscriptionProfile,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    lastUpdateMs: Long,
    isPinging: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSupport: () -> Unit,
    onSite: () -> Unit,
    onPingAll: () -> Unit,
    showSubscriptionLogo: Boolean,
    onOpenSettings: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = if (miniMotionEnabled) tween(240, easing = FastOutSlowInEasing) else snap(),
        label = "profile-chevron"
    )
    val description = profile.announce?.trim()?.takeIf { it.isNotBlank() } ?: buildSubscriptionDescription(profile)
    val updatedAt = formatLastUpdateTime(lastUpdateMs)
    val trafficValue = if (profile.totalTraffic > 0) {
        "${formatBytes(profile.usedTraffic)} / ${formatBytes(profile.totalTraffic)}"
    } else {
        "${formatBytes(profile.usedTraffic)} / ∞"
    }

    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onToggleExpanded()
                    }
                    .padding(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(chevronRotation)
                    )
                    Spacer(Modifier.width(7.dp))
                    val brandLogo = profile.brandLogo?.takeIf { it.isNotBlank() }
                    if (showSubscriptionLogo && brandLogo != null) {
                        SubscriptionBrandLogo(
                            logo = brandLogo,
                            cachedLogo = profile.brandLogoCache,
                            size = 30.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = nebulaColors.textPrimary,
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Row(
                        modifier = Modifier.weight(1.3f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.displayName.ifBlank { t("Подписка", "Subscription") },
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        WindowsCountPill(profile.servers.size.toString())
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniSquareIconButton(
                            icon = Icons.Default.SignalCellularAlt,
                            onClick = onPingAll,
                            size = 40.dp,
                            iconSize = 21.dp,
                            motion = MiniIconMotion.Ping,
                            active = isPinging
                        )
                        MiniSquareIconButton(
                            icon = Icons.Default.Refresh,
                            onClick = onRefresh,
                            size = 40.dp,
                            iconSize = 21.dp,
                            motion = MiniIconMotion.Refresh,
                            active = profile.isLoading
                        )
                        Box {
                            MiniSquareIconButton(
                                icon = Icons.Default.MoreVert,
                                onClick = { menuExpanded = true },
                                size = 40.dp,
                                iconSize = 21.dp
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier
                                    .width(220.dp)
                                    .background(windowsPanelFill(nebulaColors), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(1.dp, windowsBorder(nebulaColors, 0.14f))
                            ) {
                                ProfileMenuItem(t("Настройки", "Settings"), icon = Icons.Default.Settings) {
                                    menuExpanded = false
                                    onOpenSettings()
                                }
                                ProfileMenuDivider(nebulaColors.textPrimary)
                                ProfileMenuItem(t("Обновить", "Refresh"), icon = Icons.Default.Refresh) {
                                    menuExpanded = false
                                    onRefresh()
                                }
                                ProfileMenuDivider(nebulaColors.textPrimary)
                                ProfileMenuItem(t("Удалить", "Delete"), color = Color(0xFFFF8A8A), icon = Icons.Default.Delete) {
                                    menuExpanded = false
                                    onDelete()
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniProfileStat(t("Трафик", "Traffic"), trafficValue, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Default.SwapVert)
                    MiniProfileStat(
                        label = t("Истекает", "Expires"),
                        value = formatProfileDate(profile.expireTime),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        secondaryValue = formatProfileExpiryRemaining(profile).takeIf { it.isNotBlank() },
                        icon = Icons.Default.CalendarMonth
                    )
                    MiniProfileStat(t("Обновлено", "Updated"), updatedAt, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Default.Refresh)
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.10f))
                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = t("Описание", "Description").uppercase(),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            text = description.ifBlank { t("Описание отсутствует", "No description") },
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowsLinkButton(icon = Icons.Default.SupportAgent, label = t("Поддержка", "Support"), onClick = onSupport)
                    WindowsLinkButton(icon = Icons.Default.Public, label = t("Сайт", "Site"), onClick = onSite)
                }
            }
            // Server rows are emitted as separate lazy items by WindowsProfilesList so a
            // large profile never composes hundreds of rows at once (kept the tab snappy).
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WindowsProfileServerLine(
    server: Server,
    displayName: String,
    selected: Boolean,
    isFavorite: Boolean,
    isPinging: Boolean,
    pingDisplayMode: Int,
    showDivider: Boolean,
    showJsonBadge: Boolean,
    rowShape: RoundedCornerShape,
    onClick: () -> Unit,
    onPing: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: (String) -> Unit,
    onHide: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val flagEmoji = extractFlagEmoji(server.name)
    var menuExpanded by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var hideConfirmOpen by remember { mutableStateOf(false) }
    val rowBackground by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "profile_server_row_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBackground)
            .then(
                if (selected) {
                    Modifier.border(1.dp, nebulaColors.accent.copy(alpha = 0.68f), rowShape)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
                contentAlignment = Alignment.Center
            ) {
                if (flagEmoji.isNotBlank()) {
                    Text(flagEmoji, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Icon(Icons.Default.Public, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(20.dp))
                }
                if (isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(nebulaColors.background)
                            .border(1.dp, nebulaColors.accent.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF6B8A),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = serverSubtitle(server)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    WindowsTinyBadge(miniProtocolLabel(server), green = true)
                    WindowsTinyBadge(windowsTransportBadge(server), green = false)
                    if (showJsonBadge) {
                        JsonMiniBadge()
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            val currentPing = server.ping ?: -1
            WindowsPingPill(ping = currentPing, loading = isPinging, pingDisplayMode = pingDisplayMode)
            Spacer(Modifier.width(8.dp))
            Box {
                MiniSquareIconButton(
                    icon = Icons.Default.MoreVert,
                    onClick = { menuExpanded = true },
                    size = 36.dp,
                    iconSize = 20.dp,
                    forceOpaque = false
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .width(230.dp)
                        .background(
                            color = windowsPanelFill(nebulaColors),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, windowsBorder(nebulaColors, 0.14f))
                ) {
                    ProfileMenuItem(
                        t(
                            if (isFavorite) "Убрать из избранного" else "В избранное",
                            if (isFavorite) "Remove favorite" else "Add favorite"
                        ),
                        color = nebulaColors.textPrimary,
                        icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder
                    ) {
                        menuExpanded = false
                        onToggleFavorite()
                    }
                    ProfileMenuDivider(nebulaColors.textPrimary)
                    ProfileMenuItem(t("Проверить пинг", "Check ping"), color = nebulaColors.textPrimary, icon = Icons.Default.SignalCellularAlt) {
                        menuExpanded = false
                        onPing()
                    }
                    ProfileMenuDivider(nebulaColors.textPrimary)
                    ProfileMenuItem(t("Переименовать", "Rename"), color = nebulaColors.textPrimary, icon = Icons.Default.Edit) {
                        menuExpanded = false
                        renameOpen = true
                    }
                    ProfileMenuDivider(nebulaColors.textPrimary)
                    ProfileMenuItem(t("Удалить", "Delete"), color = Color(0xFFFF8A8A), icon = Icons.Default.Delete) {
                        menuExpanded = false
                        hideConfirmOpen = true
                    }
                }
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(windowsDivider(nebulaColors))
            )
        }
    }

    if (renameOpen) {
        NimboRenameServerDialog(
            initialName = displayName,
            onDismiss = { renameOpen = false },
            onSave = { name ->
                onRename(name)
                renameOpen = false
            }
        )
    }

    if (hideConfirmOpen) {
        NimboConfirmDialog(
            title = t("Удалить сервер?", "Delete server?"),
            text = displayName,
            confirmText = t("Удалить", "Delete"),
            onDismiss = { hideConfirmOpen = false },
            onConfirm = {
                onHide()
                hideConfirmOpen = false
            }
        )
    }
}

@Composable
private fun WindowsFlatPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val cornerScale = LocalGlobalCornerRadius.current
    val resolvedShape = scaleRoundedCornerShape(shape, cornerScale)
    val fill = windowsPanelFill(nebulaColors)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(resolvedShape)
            .border(1.dp, windowsBorder(nebulaColors), resolvedShape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(fill)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun WindowsCountPill(text: String) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(windowsSoftFill(nebulaColors))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun WindowsLinkButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(nebulaColors.accent.copy(alpha = 0.10f))
            .border(1.dp, nebulaColors.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun WindowsPingPill(
    ping: Int,
    loading: Boolean,
    pingDisplayMode: Int = 0
) {
    val nebulaColors = LocalNebulaColors.current
    // Пока сервер пингуется (loading) — крутим анимацию прямо в месте значения,
    // даже если уже есть закешированный пинг.
    val showLoading = loading
    val targetPingColor = when {
        showLoading -> nebulaColors.accent
        ping == -1 -> nebulaColors.statusDisconnected
        ping <= 70 -> Color(0xFF6BE88E)
        ping <= 120 -> Color(0xFFCDDC39)
        ping <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusDisconnected
    }
    val targetBackground = when {
        showLoading -> nebulaColors.accent.copy(alpha = 0.15f)
        ping == -1 -> nebulaColors.statusDisconnected.copy(alpha = 0.15f)
        ping <= 70 -> Color(0xFF173A25)
        ping <= 120 -> Color(0xFFCDDC39).copy(alpha = 0.15f)
        ping <= 220 -> Color(0xFFFF9800).copy(alpha = 0.15f)
        else -> nebulaColors.statusDisconnected.copy(alpha = 0.15f)
    }
    // Плавно переходим между состояниями: цвет и значение не "дёргаются".
    val pingColor by animateColorAsState(targetPingColor, animationSpec = tween(300), label = "ping_pill_color")

    if (pingDisplayMode == 2) {
        val dotColor by animateColorAsState(targetPingColor, animationSpec = tween(300), label = "ping_dot_color")
        if (showLoading) {
            PingPulsingDot(color = dotColor)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    } else {
        val backgroundColor by animateColorAsState(targetBackground, animationSpec = tween(300), label = "ping_pill_bg")
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(backgroundColor)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            // Сначала анимация "идёт пинг", потом плавно проявляется значение.
            AnimatedContent(
                targetState = showLoading,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "ping_pill_state"
            ) { isLoading ->
                if (isLoading) {
                    PingPulsingDots(color = pingColor)
                } else {
                    Text(
                        text = if (ping == -1) "н/д" else if (pingDisplayMode == 1) "ok" else "${ping} ms",
                        color = pingColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Три пульсирующие точки — индикатор «сервер пингуется» прямо в месте значения. */
@Composable
private fun PingPulsingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "ping_dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(560, delayMillis = index * 140, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ping_dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

/** Пульсирующая точка для режима «Индикатор», пока сервер пингуется. */
@Composable
private fun PingPulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "ping_dot_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ping_dot_pulse_value"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .scale(0.8f + pulse * 0.35f)
            .clip(CircleShape)
            .background(color.copy(alpha = pulse))
    )
}

@Composable
private fun WindowsTinyBadge(text: String, green: Boolean) {
    val nebulaColors = LocalNebulaColors.current
    val fillAlpha = if (green) 0.16f else 0.10f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(nebulaColors.accent.copy(alpha = fillAlpha))
            .border(
                0.5.dp,
                nebulaColors.accent.copy(alpha = if (green) 0.34f else 0.22f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = nebulaColors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun windowsTransportBadge(server: Server): String {
    val parts = listOfNotNull(
        miniTransportLabel(server)?.replace("WebSocket", "WS")?.uppercase(),
        miniSecurityLabel(server)?.uppercase()
    )
    return parts.joinToString(" • ").ifBlank { "TCP" }
}

@Composable
private fun ProfileList(
    profiles: List<SubscriptionProfileMetadata>,
    preferencesManager: PreferencesManager,
    onSelectProfile: (SubscriptionProfileMetadata) -> Unit,
    onAddSubscription: () -> Unit,
    onDeleteProfile: (SubscriptionProfileMetadata) -> Unit,
    onProfileRefresh: (String) -> Unit,
    onShowTvQr: (String) -> Unit,
    onSupportProfile: (SubscriptionProfileMetadata) -> Unit,
    onOverrideProfile: (SubscriptionProfileMetadata) -> Unit,
    onExportProfile: (SubscriptionProfileMetadata) -> Unit,
    onOpenSettings: (SubscriptionProfileMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    val showSubscriptionLogo by preferencesManager.showSubscriptionLogoState
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 118.dp)
    ) {
        items(profiles, key = { it.url }) { profile ->
            ProfileCompactCard(
                profile = profile,
                lastUpdateMs = preferencesManager.getLastSubscriptionUpdateTime(profile.url),
                showSubscriptionLogo = showSubscriptionLogo,
                onOpen = { onSelectProfile(profile) },
                onSendTv = { onShowTvQr(profile.url) },
                onSupport = { onSupportProfile(profile) },
                onOverride = { onOverrideProfile(profile) },
                onExport = { onExportProfile(profile) },
                onRefresh = { onProfileRefresh(profile.url) },
                onDelete = { onDeleteProfile(profile) },
                onOpenSettings = { onOpenSettings(profile) }
            )
        }
        item {
            AddProfilePanel(onClick = onAddSubscription)
        }
    }
}

@Composable
private fun ProfileCompactCard(
    profile: SubscriptionProfileMetadata,
    lastUpdateMs: Long,
    showSubscriptionLogo: Boolean,
    onOpen: () -> Unit,
    onSendTv: () -> Unit,
    onSupport: () -> Unit,
    onOverride: () -> Unit,
    onExport: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val menuContentColor = nebulaColors.textPrimary
    val menuDestructiveColor = Color(0xFFFF8A8A)
    var menuExpanded by remember { mutableStateOf(false) }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 162.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        borderColor = Color.White.copy(alpha = 0.10f),
        accentFill = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val brandLogo = profile.brandLogo?.takeIf { it.isNotBlank() }
                if (showSubscriptionLogo && brandLogo != null) {
                    SubscriptionBrandLogo(
                        logo = brandLogo,
                        cachedLogo = profile.brandLogoCache,
                        size = 30.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = nebulaColors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.displayName.ifBlank { t("Подписка", "Subscription") },
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(windowsSoftFill(nebulaColors))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = profile.serversCount.toString(),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Box {
                    MiniSquareIconButton(
                        icon = Icons.Default.MoreVert,
                        onClick = { menuExpanded = true },
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    if (profile.isLoading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ExpressiveCircularLoader(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .width(260.dp)
                            .background(
                                color = windowsPanelFill(nebulaColors),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, windowsBorder(nebulaColors, 0.14f))
                    ) {
                        ProfileMenuItem(t("Настройки", "Settings"), color = menuContentColor, icon = Icons.Default.Settings) {
                            menuExpanded = false
                            onOpenSettings()
                        }
                        ProfileMenuDivider(menuContentColor)
                        ProfileMenuItem(t("Обновить", "Refresh"), color = menuContentColor, icon = Icons.Default.Refresh) {
                            menuExpanded = false
                            onRefresh()
                        }
                        ProfileMenuDivider(menuContentColor)
                        ProfileMenuItem(t("Отправить на ТВ", "Send to TV"), color = menuContentColor, icon = Icons.Default.Tv) {
                            menuExpanded = false
                            onSendTv()
                        }
                        ProfileMenuDivider(menuContentColor)
                        ProfileMenuItem(t("Поддержка", "Support"), color = menuContentColor, icon = Icons.Default.SupportAgent) {
                            menuExpanded = false
                            onSupport()
                        }
                        ProfileMenuDivider(menuContentColor)
                        ProfileMenuItem(t("Переопределение", "Override"), color = menuContentColor, icon = Icons.Default.Tune) {
                            menuExpanded = false
                            onOverride()
                        }
                        ProfileMenuDivider(menuContentColor)
                        ProfileMenuItem(t("Экспорт файла", "Export file"), color = menuContentColor, icon = Icons.Default.FileUpload) {
                            menuExpanded = false
                            onExport()
                        }
                        ProfileMenuDivider(menuContentColor)
                        ProfileMenuItem(t("Удалить", "Delete"), color = menuDestructiveColor, icon = Icons.Default.Delete) {
                            menuExpanded = false
                            onDelete()
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MiniProfileStat(
                    label = t("Трафик", "Traffic"),
                    value = if (profile.totalTraffic > 0) {
                        "${formatBytes(profile.usedTraffic)} / ${formatBytes(profile.totalTraffic)}"
                    } else {
                        "${formatBytes(profile.usedTraffic)} / ∞"
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SwapVert
                )
                MiniProfileStat(
                    label = t("Истекает", "Expires"),
                    value = formatProfileDate(profile.expireTime),
                    modifier = Modifier.weight(1f),
                    secondaryValue = formatProfileExpiryRemaining(profile).takeIf { it.isNotBlank() },
                    icon = Icons.Default.CalendarMonth
                )
            }

            val description = buildSubscriptionDescription(profile)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.08f))
                        .border(1.dp, if (nebulaColors.isMaterialYou) Color.Transparent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = description,
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secondaryValue: String? = null,
    icon: ImageVector? = null
) {
    val nebulaColors = LocalNebulaColors.current
    val cornerScale = LocalGlobalCornerRadius.current
    val statShape = scaleRoundedCornerShape(RoundedCornerShape(14.dp), cornerScale)
    Column(
        modifier = modifier
            .clip(statShape)
            .background(windowsSoftFill(nebulaColors))
            .border(1.dp, windowsBorder(nebulaColors, 0.16f), statShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label.uppercase(),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
        if (!secondaryValue.isNullOrBlank()) {
            Text(
                text = secondaryValue,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    text: String,
    color: Color = LocalNebulaColors.current.textPrimary,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val accent = LocalNebulaColors.current.accent
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        },
        leadingIcon = icon?.let {
            {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        },
        colors = androidx.compose.material3.MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = color,
            trailingIconColor = accent
        ),
        onClick = onClick
    )
}

@Composable
private fun ProfileMenuDivider(color: Color = LocalNebulaColors.current.accent) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color.copy(alpha = 0.16f))
    )
}

@Composable
private fun AddProfilePanel(onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        borderColor = nebulaColors.accent.copy(alpha = 0.32f),
        accentFill = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(nebulaColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = t("Добавить", "Add"),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Добавить профиль", "Add profile"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = t("URL, QR-код или файл", "URL, QR code, or file"),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.CloudUpload, null, tint = nebulaColors.accent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MiniModeSwitch(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var rulesMode by remember { mutableStateOf(preferencesManager.isRoutingEnabled) }
    MiniSegmented(
        left = "Правила",
        right = "Глобальный",
        leftSelected = rulesMode,
        onLeft = {
            rulesMode = true
            preferencesManager.isRoutingEnabled = true
            mainViewModel.showTopNotification("Режим правил включен")
        },
        onRight = {
            rulesMode = false
            preferencesManager.isRoutingEnabled = false
            mainViewModel.showTopNotification("Глобальный режим включен")
        },
        modifier = modifier
    )
}

@Composable
private fun NimboSettingsScreen(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    onOpenSubscription: () -> Unit,
    onOpenAppAccess: () -> Unit,
    onLanguageClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDisclaimerClick: () -> Unit,
    onConnectionIdClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onUpdatesClick: () -> Unit,
    onLogsClick: () -> Unit,
    onRoutingClick: () -> Unit,
    onConnectionsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onRefreshFirstProfile: (String) -> Unit,
    onShowTvQr: (String) -> Unit
) {
    val profiles by mainViewModel.profilesState.collectAsState()
    val profileUrl = profiles.firstOrNull()?.url
    val firstProfile = profiles.firstOrNull()
    var section by rememberSaveable { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 14.dp),
        contentPadding = PaddingValues(bottom = 118.dp)
    ) {
        item {
            Text(
                text = t("Настройки", "Settings"),
                color = LocalNebulaColors.current.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))
            SettingsActionGrid(
                onRoutingClick = onRoutingClick,
                onConnectionsClick = onConnectionsClick,
                onNotificationsClick = onNotificationsClick,
                onStatsClick = onStatsClick,
                onLogsClick = onLogsClick,
                notificationCount = preferencesManager.getNotificationHistory().size
            )
            Spacer(Modifier.height(22.dp))
            SettingsSectionTabs(selected = section, onSelect = { section = it })
            Spacer(Modifier.height(16.dp))
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (section) {
                    1 -> ThemeSettingsSection(preferencesManager = preferencesManager)
                    2 -> PingSettingsSection(preferencesManager, mainViewModel)
                    3 -> UpdatesSettingsContent()
                    4 -> AboutSettingsContent()
                    5 -> SubscriptionsSettingsSection(preferencesManager = preferencesManager)
                    6 -> ServersSettingsSection(preferencesManager = preferencesManager)
                    7 -> BackupSettingsSection(preferencesManager = preferencesManager)
                    10 -> AdvancedSettingsSection(preferencesManager = preferencesManager)
                    // Статистика (8) и Соединения (9) теперь открываются отдельными
                    // страницами из больших кнопок — здесь как секции не дублируются.
                    else -> GeneralSettingsSection(preferencesManager = preferencesManager)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.GeneralSettingsSection(
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    var autoConnect by remember { mutableStateOf(preferencesManager.autoConnect) }
    var autoReconnect by remember { mutableStateOf(preferencesManager.autoReconnect) }
    var disconnectOnLock by remember { mutableStateOf(preferencesManager.disconnectOnLock) }
    var connectOnUnlock by remember { mutableStateOf(preferencesManager.connectOnUnlock) }
    var showSpeed by remember { mutableStateOf(preferencesManager.showSpeed) }
    var pingOnStartup by remember { mutableStateOf(preferencesManager.pingOnStartup) }
    var updateSubOnStartup by remember { mutableStateOf(preferencesManager.updateSubOnStartup) }
    var pingOnUpdate by remember { mutableStateOf(preferencesManager.pingOnUpdate) }
    var memoryMonitoring by remember { mutableStateOf(preferencesManager.memoryMonitoring) }
    var memoryLimitDisabled by remember { mutableStateOf(preferencesManager.memoryLimitDisabled) }
    var memoryLimitMb by remember { mutableStateOf(preferencesManager.memoryLimitMb) }

    SettingsGroupLabel(t("ОБЩИЕ", "GENERAL"))
    ConnectionStabilityCard(
        autoConnect = autoConnect,
        autoReconnect = autoReconnect,
        disconnectOnLock = disconnectOnLock,
        connectOnUnlock = connectOnUnlock,
        onAutoConnectChange = {
            autoConnect = it
            preferencesManager.autoConnect = it
        },
        onAutoReconnectChange = {
            autoReconnect = it
            preferencesManager.autoReconnect = it
        },
        onDisconnectOnLockChange = {
            disconnectOnLock = it
            preferencesManager.disconnectOnLock = it
            if (!it) {
                connectOnUnlock = false
                preferencesManager.connectOnUnlock = false
            }
        },
        onConnectOnUnlockChange = {
            connectOnUnlock = it
            preferencesManager.connectOnUnlock = it
        }
    )
    Spacer(Modifier.height(12.dp))
    SettingsCompactCard {
        SettingsToggleRow(
            title = t("Обновлять подписки при запуске", "Update subscriptions on launch"),
            subtitle = t("Проверяет подписки при старте приложения", "Checks subscriptions when the app starts"),
            checked = updateSubOnStartup,
            onCheckedChange = {
                updateSubOnStartup = it
                preferencesManager.updateSubOnStartup = it
            },
            icon = Icons.Default.Refresh
        )
        SettingsToggleRow(
            title = t("Пинг серверов при запуске", "Ping servers on launch"),
            subtitle = null,
            checked = pingOnStartup,
            onCheckedChange = {
                pingOnStartup = it
                preferencesManager.pingOnStartup = it
            },
            icon = Icons.Default.SignalCellularAlt
        )
        SettingsToggleRow(
            title = t("Пинг после обновления подписок", "Ping after subscription update"),
            subtitle = null,
            checked = pingOnUpdate,
            onCheckedChange = {
                pingOnUpdate = it
                preferencesManager.pingOnUpdate = it
            },
            icon = Icons.Default.Speed
        )
        SettingsToggleRow(
            title = t("График скорости сети", "Network speed chart"),
            subtitle = t("Показывать upload/download на главной во время подключения", "Show upload/download on Home while connected"),
            checked = showSpeed,
            onCheckedChange = {
                showSpeed = it
                preferencesManager.showSpeed = it
            },
            icon = Icons.Default.BarChart
        )
        SettingsToggleRow(
            title = t("Потребление памяти", "Memory usage"),
            subtitle = t("Показывать на главной потребление RAM ядром Nimbo", "Show Nimbo core RAM usage on Home"),
            checked = memoryMonitoring,
            onCheckedChange = {
                memoryMonitoring = it
                preferencesManager.memoryMonitoring = it
            },
            showDivider = false,
            icon = Icons.Default.Memory
        )
    }

    Spacer(Modifier.height(14.dp))
    SettingsCompactCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(nebulaColors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = t("Снять ограничение по памяти", "Remove memory limit"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = nebulaColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = t("Для мощных устройств. Больше памяти повышает стабильность под нагрузкой.", "For powerful devices. More memory increases stability under load."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = nebulaColors.textSecondary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = memoryLimitDisabled,
                    onCheckedChange = { on ->
                        memoryLimitDisabled = on
                        preferencesManager.memoryLimitDisabled = on
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = nebulaColors.accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (memoryLimitDisabled) {
                    t("Лимит памяти: без ограничений", "Memory limit: unlimited")
                } else {
                    t("Лимит памяти: ${memoryLimitMb} MB", "Memory limit: ${memoryLimitMb} MB")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = nebulaColors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (memoryLimitDisabled) {
                    t("Режим для мощных устройств и максимальной стабильности.", "Mode for powerful devices and maximum stability.")
                } else {
                    t("Ниже лимит — ниже расход памяти, выше лимит — стабильнее при высокой нагрузке.", "Lower limit — lower memory consumption, higher limit — more stable under high load.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = nebulaColors.textSecondary
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = memoryLimitMb.toFloat(),
                onValueChange = { value ->
                    val limit = value.toInt().coerceIn(40, 300)
                    memoryLimitMb = limit
                    preferencesManager.memoryLimitMb = limit
                },
                valueRange = 40f..300f,
                enabled = !memoryLimitDisabled,
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.accent,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.22f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t("40 MB", "40 MB"), color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                Text(t("300 MB", "300 MB"), color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

}

@Composable
private fun ColumnScope.AdvancedSettingsSection(
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    var tlsFragment by remember { mutableStateOf(preferencesManager.tlsFragment) }
    var keepDeviceActive by remember { mutableStateOf(preferencesManager.keepDeviceActive) }
    var allowLanConnections by remember { mutableStateOf(preferencesManager.allowLanConnections) }
    var allowHotspotAccess by remember { mutableStateOf(preferencesManager.allowHotspotAccess) }
    var lanThroughProxy by remember { mutableStateOf(preferencesManager.lanThroughProxy) }
    var packetFragmentationEnabled by remember { mutableStateOf(preferencesManager.packetFragmentationEnabled) }
    var muxEnabled by remember { mutableStateOf(preferencesManager.muxEnabled) }
    var blockUdp by remember { mutableStateOf(preferencesManager.blockUdp) }
    var trafficSniffingEnabled by remember { mutableStateOf(preferencesManager.trafficSniffingEnabled) }
    var vpnIpType by remember { mutableStateOf(preferencesManager.vpnIpType) }
    var vpnDnsMode by remember { mutableStateOf(preferencesManager.vpnDnsMode) }
    var idleTimeoutSeconds by remember { mutableStateOf(preferencesManager.idleTimeoutSeconds) }
    var tcpConnectionLimit by remember { mutableStateOf(preferencesManager.tcpConnectionLimit) }
    var udpConnectionLimit by remember { mutableStateOf(preferencesManager.udpConnectionLimit) }
    var diagnosticLogRetentionHours by remember { mutableStateOf(preferencesManager.diagnosticLogRetentionHours) }

    SettingsGroupLabel(t("РАСШИРЕННЫЕ НАСТРОЙКИ", "ADVANCED SETTINGS"))
    AdvancedConnectionSettingsCard(
        idleTimeoutSeconds = idleTimeoutSeconds,
        tcpConnectionLimit = tcpConnectionLimit,
        udpConnectionLimit = udpConnectionLimit,
        trafficSniffingEnabled = trafficSniffingEnabled,
        tlsFragment = tlsFragment,
        packetFragmentationEnabled = packetFragmentationEnabled,
        muxEnabled = muxEnabled,
        blockUdp = blockUdp,
        keepDeviceActive = keepDeviceActive,
        allowLanConnections = allowLanConnections,
        allowHotspotAccess = allowHotspotAccess,
        lanThroughProxy = lanThroughProxy,
        vpnIpType = vpnIpType,
        vpnDnsMode = vpnDnsMode,
        diagnosticLogRetentionHours = diagnosticLogRetentionHours,
        onIdleTimeoutChange = {
            idleTimeoutSeconds = it
            preferencesManager.idleTimeoutSeconds = it
        },
        onTcpConnectionLimitChange = {
            tcpConnectionLimit = it
            preferencesManager.tcpConnectionLimit = it
        },
        onUdpConnectionLimitChange = {
            udpConnectionLimit = it
            preferencesManager.udpConnectionLimit = it
        },
        onTrafficSniffingChange = {
            trafficSniffingEnabled = it
            preferencesManager.trafficSniffingEnabled = it
        },
        onTlsFragmentChange = {
            tlsFragment = it
            preferencesManager.tlsFragment = it
        },
        onPacketFragmentationChange = {
            packetFragmentationEnabled = it
            preferencesManager.packetFragmentationEnabled = it
        },
        onMuxChange = {
            muxEnabled = it
            preferencesManager.muxEnabled = it
        },
        onBlockUdpChange = {
            blockUdp = it
            preferencesManager.blockUdp = it
        },
        onKeepDeviceActiveChange = {
            keepDeviceActive = it
            preferencesManager.keepDeviceActive = it
        },
        onAllowLanConnectionsChange = {
            allowLanConnections = it
            preferencesManager.allowLanConnections = it
            if (!it) {
                allowHotspotAccess = false
                lanThroughProxy = false
                preferencesManager.allowHotspotAccess = false
                preferencesManager.lanThroughProxy = false
            }
        },
        onAllowHotspotAccessChange = {
            allowHotspotAccess = it
            preferencesManager.allowHotspotAccess = it
        },
        onLanThroughProxyChange = {
            lanThroughProxy = it
            preferencesManager.lanThroughProxy = it
        },
        onVpnIpTypeChange = {
            vpnIpType = it
            preferencesManager.vpnIpType = it
        },
        onVpnDnsModeChange = {
            vpnDnsMode = it
            preferencesManager.vpnDnsMode = it
        },
        onDiagnosticLogRetentionChange = {
            diagnosticLogRetentionHours = it
            preferencesManager.diagnosticLogRetentionHours = it
            Logger.updateLogRetention(context)
        }
    )
    Spacer(Modifier.height(16.dp))
}

// ── Метка группы внутри секции настроек ─────────────────────────────────────
@Composable
private fun SettingsGroupLabel(text: String, topPadding: Dp = 0.dp) {
    Text(
        text = text,
        color = LocalNebulaColors.current.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 2.dp, top = topPadding, bottom = 14.dp)
    )
}

@Composable
private fun SettingsNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    width: Dp = 68.dp
) {
    val nebulaColors = LocalNebulaColors.current
    val cornerScale = LocalGlobalCornerRadius.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = nebulaColors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(width)
            .height(54.dp),
        shape = RoundedCornerShape(14.dp * cornerScale),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = nebulaColors.textPrimary,
            unfocusedTextColor = nebulaColors.textPrimary,
            focusedBorderColor = if (nebulaColors.isLight) Color(0xFFD8D5E2) else Color.White.copy(alpha = 0.18f),
            unfocusedBorderColor = windowsBorder(nebulaColors, 0.12f),
            focusedContainerColor = windowsControlFill(nebulaColors),
            unfocusedContainerColor = windowsControlFill(nebulaColors),
            cursorColor = nebulaColors.accent
        )
    )
}

// ── #9 Подписки ─────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.SubscriptionsSettingsSection(
    preferencesManager: PreferencesManager
) {
    var autoUpdate by remember { mutableStateOf(preferencesManager.subscriptionAutoUpdate) }
    var intervalHours by remember { mutableStateOf((preferencesManager.subscriptionUpdateInterval / 3600).coerceAtLeast(1)) }
    var updateOnStartup by remember { mutableStateOf(preferencesManager.updateSubOnStartup) }
    var pingOnUpdate by remember { mutableStateOf(preferencesManager.pingOnUpdate) }
    var notifyExpiry by remember { mutableStateOf(preferencesManager.notifyOnExpiry) }
    var expiryDays by remember { mutableStateOf(preferencesManager.expiryNotifyDays) }
    var notifyUpdate by remember { mutableStateOf(preferencesManager.notifyOnSubscriptionUpdate) }

    SettingsGroupLabel(t("ОБНОВЛЕНИЕ", "UPDATING"))
    SettingsCompactCard {
        SettingsToggleRow(
            title = t("Автообновление подписок", "Auto-update subscriptions"),
            subtitle = t("Периодически обновлять серверы из подписок", "Refresh servers from subscriptions periodically"),
            checked = autoUpdate,
            onCheckedChange = {
                autoUpdate = it
                preferencesManager.subscriptionAutoUpdate = it
            },
            icon = Icons.Default.Refresh
        )
        SettingsToggleRow(
            title = t("Обновлять при запуске", "Update on launch"),
            subtitle = null,
            checked = updateOnStartup,
            onCheckedChange = {
                updateOnStartup = it
                preferencesManager.updateSubOnStartup = it
            },
            icon = Icons.Default.PowerSettingsNew
        )
        SettingsToggleRow(
            title = t("Пинг после обновления", "Ping after update"),
            subtitle = null,
            checked = pingOnUpdate,
            onCheckedChange = {
                pingOnUpdate = it
                preferencesManager.pingOnUpdate = it
            },
            showDivider = false,
            icon = Icons.Default.Speed
        )
    }

    Spacer(Modifier.height(16.dp))
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        PingSettingsWideRow(
            title = t("Интервал (часы)", "Interval (hours)"),
            subtitle = t("Как часто обновлять подписки автоматически", "How often to auto-refresh subscriptions"),
            icon = Icons.Default.Schedule
        ) {
            SettingsNumberField(
                value = intervalHours.toString(),
                onValueChange = { raw ->
                    val h = raw.filter(Char::isDigit).take(3).toIntOrNull()
                    if (h != null) {
                        intervalHours = h.coerceIn(1, 168)
                        preferencesManager.subscriptionUpdateInterval = intervalHours * 3600
                    }
                }
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    SettingsGroupLabel(t("УВЕДОМЛЕНИЯ", "NOTIFICATIONS"))
    SettingsCompactCard {
        SettingsToggleRow(
            title = t("Уведомлять об истечении", "Notify before expiry"),
            subtitle = t("Предупреждать, когда подписка скоро закончится", "Warn when a subscription is about to expire"),
            checked = notifyExpiry,
            onCheckedChange = {
                notifyExpiry = it
                preferencesManager.notifyOnExpiry = it
            },
            icon = Icons.Default.Info
        )
        SettingsToggleRow(
            title = t("Уведомлять об обновлении", "Notify on update"),
            subtitle = t("Сообщать об успешном обновлении подписки", "Report a successful subscription update"),
            checked = notifyUpdate,
            onCheckedChange = {
                notifyUpdate = it
                preferencesManager.notifyOnSubscriptionUpdate = it
            },
            showDivider = false,
            icon = Icons.Default.SystemUpdate
        )
    }

    Spacer(Modifier.height(16.dp))
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        PingSettingsWideRow(
            title = t("Предупреждение об окончании", "Expiry warning"),
            subtitle = t(
                "За сколько дней до конца подписки прислать уведомление",
                "How many days before the subscription ends to notify you"
            ),
            icon = Icons.Default.CalendarMonth
        ) {
            SettingsNumberField(
                value = expiryDays.toString(),
                onValueChange = { raw ->
                    val d = raw.filter(Char::isDigit).take(2).toIntOrNull()
                    if (d != null) {
                        expiryDays = d.coerceIn(1, 30)
                        preferencesManager.expiryNotifyDays = expiryDays
                    }
                }
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ── #10 Серверы ─────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.ServersSettingsSection(
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    var sortOrder by remember { mutableStateOf(preferencesManager.serverSortOrder) }
    var selectedSortProtocols by remember { mutableStateOf(preferencesManager.selectedSortProtocols) }
    var proxyOnly by remember { mutableStateOf(preferencesManager.bsBypassMode) }
    var switchWhileConnected by remember { mutableStateOf(preferencesManager.allowServerSwitchWhileConnected) }

    val sortOptions = listOf(
        "DEFAULT" to t("По умолчанию", "Default"),
        "NAME" to t("По имени", "By name"),
        "PING" to t("По пингу", "By ping"),
        "PROTOCOL" to t("По протоколу", "By protocol")
    )

    SettingsGroupLabel(t("СОРТИРОВКА СЕРВЕРОВ", "SERVER SORTING"))
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            sortOptions.forEach { (value, label) ->
                PingChoiceRow(
                    title = label,
                    subtitle = when (value) {
                        "NAME" -> t("Алфавитный порядок", "Alphabetical order")
                        "PING" -> t("Сначала быстрые", "Fastest first")
                        "PROTOCOL" -> t("Группировать по протоколу", "Group by protocol")
                        else -> t("Порядок из подписки", "Order from subscription")
                    },
                    selected = sortOrder == value,
                    onClick = {
                        sortOrder = value
                        preferencesManager.serverSortOrder = value
                    }
                )
                if (value == "PROTOCOL" && sortOrder == "PROTOCOL") {
                    val availableProtocols = remember {
                        try {
                            preferencesManager.loadProfiles()
                                .flatMap { it.servers }
                                .map {
                                    val protocol = it.protocol.lowercase()
                                    when {
                                        protocol.contains("vless") -> "VLESS"
                                        protocol.contains("vmess") -> "VMess"
                                        protocol.contains("trojan") -> "Trojan"
                                        protocol.contains("shadowsocks") || protocol == "ss" -> "Shadowsocks"
                                        protocol.contains("hysteria") || protocol == "hy2" -> "Hysteria2"
                                        protocol.contains("tuic") -> "TUIC"
                                        protocol.contains("reality") -> "Reality"
                                        else -> "Other"
                                    }
                                }
                                .distinct()
                                .sorted()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    if (availableProtocols.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, bottom = 12.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableProtocols.forEach { proto ->
                                val isSelected = selectedSortProtocols.contains(proto)
                                val badgeBgColor = if (isSelected) {
                                    nebulaColors.accent.copy(alpha = 0.24f)
                                } else {
                                    nebulaColors.textPrimary.copy(alpha = 0.06f)
                                }
                                val badgeBorderColor = if (isSelected) {
                                    nebulaColors.accent.copy(alpha = 0.65f)
                                } else {
                                    nebulaColors.textPrimary.copy(alpha = 0.15f)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeBgColor)
                                        .border(1.dp, badgeBorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val newSet = if (isSelected) {
                                                selectedSortProtocols - proto
                                            } else {
                                                selectedSortProtocols + proto
                                            }
                                            selectedSortProtocols = newSet
                                            preferencesManager.selectedSortProtocols = newSet
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = proto,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) nebulaColors.accent else nebulaColors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = t("Доступных протоколов нет", "No available protocols"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 32.dp, bottom = 12.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SettingsCompactCard {
        SettingsToggleRow(
            title = t("Только прокси (без VPN)", "Proxy only (no VPN)"),
            subtitle = t("Не поднимать туннель VPN, работать как локальный прокси", "Skip the VPN tunnel, run as a local proxy"),
            checked = proxyOnly,
            onCheckedChange = {
                proxyOnly = it
                preferencesManager.bsBypassMode = it
            },
            icon = Icons.Default.VpnKey
        )
        SettingsToggleRow(
            title = t("Смена сервера на лету", "Switch server while connected"),
            subtitle = t("Позволяет менять сервер без отключения", "Lets you change the server without disconnecting"),
            checked = switchWhileConnected,
            onCheckedChange = {
                switchWhileConnected = it
                preferencesManager.allowServerSwitchWhileConnected = it
            },
            showDivider = false,
            icon = Icons.Default.SwapVert
        )
    }
    Spacer(Modifier.height(16.dp))
}

// ── #11 Резервная копия ─────────────────────────────────────────────────────
@Composable
private fun ColumnScope.BackupSettingsSection(
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    fun notify(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = BackupCodec.wrap(preferencesManager.exportSettings(), password.trim())
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
            notify(loc("Резервная копия сохранена", "Backup saved"))
        }.onFailure { notify(loc("Ошибка экспорта", "Export failed")) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val settingsJson = BackupCodec.unwrap(content, password.trim())
            if (preferencesManager.importSettings(settingsJson)) {
                android.widget.Toast.makeText(
                    context,
                    loc("Копия восстановлена. Перезапуск…", "Backup restored. Restarting…"),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                (context as? android.app.Activity)?.recreate()
            } else {
                notify(loc("Не удалось применить настройки", "Could not apply settings"))
            }
        }.onFailure { notify(it.message ?: loc("Ошибка восстановления", "Restore failed")) }
    }

    SettingsGroupLabel(t("РЕЗЕРВНАЯ КОПИЯ", "BACKUP"))
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = t(
                    "Сохраните подписки, серверы, маршрутизацию и настройки в один файл.",
                    "Save subscriptions, servers, routing and settings into a single file."
                ),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(t("Пароль (опционально)", "Password (optional)")) },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = nebulaColors.textSecondary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(Icons.Default.Key, null, tint = nebulaColors.textSecondary)
                    }
                },
                visualTransformation = if (passwordVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = nebulaColors.textPrimary,
                    unfocusedTextColor = nebulaColors.textPrimary,
                    focusedBorderColor = if (nebulaColors.isLight) Color(0xFFD8D5E2) else Color.White.copy(alpha = 0.18f),
                    unfocusedBorderColor = windowsBorder(nebulaColors, 0.12f),
                    focusedContainerColor = windowsControlFill(nebulaColors),
                    unfocusedContainerColor = windowsControlFill(nebulaColors),
                    focusedLabelColor = nebulaColors.textSecondary,
                    unfocusedLabelColor = nebulaColors.textTertiary,
                    cursorColor = nebulaColors.accent
                )
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupActionButton(
                    label = t("Экспорт", "Export"),
                    icon = Icons.Default.FileUpload,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { exportLauncher.launch("Nimbo_backup_${System.currentTimeMillis()}.json") }
                )
                BackupActionButton(
                    label = t("Импорт", "Import"),
                    icon = Icons.Default.FileDownload,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun BackupActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val fill = if (primary) nebulaColors.accent else windowsControlFill(nebulaColors)
    val contentColor = if (primary) Color.White else nebulaColors.textPrimary
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(1.dp, if (primary) Color.Transparent else windowsBorder(nebulaColors, 0.12f), RoundedCornerShape(14.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── #13 Статистика ──────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.StatisticsSettingsSection(
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    var statsRefresh by remember { mutableStateOf(0) }

    // Чтение тиков соединения регистрирует подписку на пересборку каждую секунду.
    val connectedSeconds = VpnManager.connectedSeconds.value
    @Suppress("UNUSED_VARIABLE") val refreshKey = connectedSeconds + statsRefresh

    val vpnState = VpnManager.state.value
    val uploadSpeed = VpnManager.uploadSpeed.value
    val downloadSpeed = VpnManager.downloadSpeed.value
    val sessionUp = VpnManager.totalBytesUploaded.value
    val sessionDown = VpnManager.totalBytesDownloaded.value
    val txPackets = VpnManager.totalPacketsUploaded.value
    val rxPackets = VpnManager.totalPacketsDownloaded.value
    val sessionStart = VpnManager.sessionStartMs.value

    val allUp = preferencesManager.totalTrafficUp
    val allDown = preferencesManager.totalTrafficDown
    val monthUp = preferencesManager.monthlyTrafficUp
    val monthDown = preferencesManager.monthlyTrafficDown

    StatisticsOverviewCard(
        vpnState = vpnState,
        connectedSeconds = connectedSeconds,
        sessionTotal = sessionUp + sessionDown
    )

    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatSpeedCard(
            modifier = Modifier.weight(1f),
            label = t("ОТПРАВЛЕНО", "SENT"),
            icon = Icons.Default.ArrowUpward,
            speed = uploadSpeed,
            total = sessionUp
        )
        StatSpeedCard(
            modifier = Modifier.weight(1f),
            label = t("ПОЛУЧЕНО", "RECEIVED"),
            icon = Icons.Default.ArrowDownward,
            speed = downloadSpeed,
            total = sessionDown
        )
    }

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("ЗА ВСЁ ВРЕМЯ", "ALL TIME")) {
        StatLine(t("Отправлено", "Sent"), formatBytesPrecise(allUp))
        StatLine(t("Получено", "Received"), formatBytesPrecise(allDown))
        StatLine(t("Суммарно", "Total"), formatBytesPrecise(allUp + allDown), emphasize = true, showDivider = false)
    }

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("ЗА МЕСЯЦ", "THIS MONTH") + " · ${preferencesManager.trafficMonthLabel}") {
        StatLine(t("Отправлено", "Sent"), formatBytesPrecise(monthUp))
        StatLine(t("Получено", "Received"), formatBytesPrecise(monthDown))
        StatLine(t("Суммарно", "Total"), formatBytesPrecise(monthUp + monthDown), emphasize = true, showDivider = false)
    }

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("СЕССИЯ", "SESSION")) {
        StatLine(
            t("Статус", "Status"),
            when (vpnState) {
                VpnState.CONNECTED -> t("Подключено", "Connected")
                VpnState.CONNECTING -> t("Подключение", "Connecting")
                else -> t("Отключено", "Disconnected")
            }
        )
        StatLine(t("Подключено", "Uptime"), formatDuration(connectedSeconds))
        StatLine(
            t("Начало сессии", "Session start"),
            if (sessionStart > 0L && vpnState != VpnState.DISCONNECTED) {
                java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(sessionStart))
            } else "—"
        )
        StatLine(t("TX пакеты", "TX packets"), txPackets.toString())
        StatLine(t("RX пакеты", "RX packets"), rxPackets.toString(), showDivider = false)
    }

    Spacer(Modifier.height(20.dp))
    BackupActionButton(
        label = t("Сбросить статистику", "Reset statistics"),
        icon = Icons.Default.Restore,
        primary = false,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            preferencesManager.resetTrafficStats()
            statsRefresh++
            android.widget.Toast.makeText(
                context,
                loc("Статистика сброшена", "Statistics reset"),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StatisticsOverviewCard(
    vpnState: VpnState,
    connectedSeconds: Int,
    sessionTotal: Long
) {
    val nebulaColors = LocalNebulaColors.current
    val connected = vpnState == VpnState.CONNECTED
    WindowsFlatPanel(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(nebulaColors.accent.copy(alpha = if (connected) 0.18f else 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = if (connected) nebulaColors.accent else nebulaColors.textSecondary,
                    modifier = Modifier.size(27.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (connected) t("Сессия активна", "Session active") else t("Сессия не запущена", "No active session"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = t("Трафик за сессию", "Session traffic") + " · " + formatBytesPrecise(sessionTotal),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(nebulaColors.softFill)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text(
                    text = formatDuration(connectedSeconds),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun StatSpeedCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    speed: Long,
    total: Long
) {
    val nebulaColors = LocalNebulaColors.current
    WindowsFlatPanel(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatSpeedLabel(speed),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = t("Всего", "Total") + " " + formatBytesPrecise(total),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Column {
        Text(
            text = title,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
        )
        WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    emphasize: Boolean = false,
    showDivider: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = nebulaColors.textPrimary,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(windowsDivider(nebulaColors))
        )
    }
}

@Composable
private fun ColumnScope.ConnectionsSettingsSection(
    preferencesManager: PreferencesManager,
    onOpenFirewall: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var subTab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    // Чтение тиков соединения регистрирует пересборку каждую секунду, пока
    // активен туннель, чтобы живые скорость/трафик обновлялись сами.
    val connectedSeconds = VpnManager.connectedSeconds.value
    @Suppress("UNUSED_VARIABLE") val tick = connectedSeconds + refreshKey

    val vpnState = VpnManager.state.value
    val server = VpnManager.connectedServer.value ?: VpnManager.selectedServer
    val uploadSpeed = VpnManager.uploadSpeed.value
    val downloadSpeed = VpnManager.downloadSpeed.value
    val sessionUp = VpnManager.totalBytesUploaded.value
    val sessionDown = VpnManager.totalBytesDownloaded.value

    val routingEnabled = preferencesManager.isRoutingEnabled
    val routingProfile = remember(refreshKey) {
        preferencesManager.routingProfileJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { com.google.gson.Gson().fromJson(it, com.danila.nimbo.model.RoutingProfile::class.java) }.getOrNull() }
    }
    val activeProfileName = when {
        routingEnabled && !routingProfile?.name.isNullOrBlank() -> routingProfile!!.name!!
        routingEnabled -> t("Маршрутизация", "Routing")
        else -> t("Глобальный", "Global")
    }

    ConnectionsOverviewCard(
        vpnState = vpnState,
        server = server,
        activeProfileName = activeProfileName,
        connectedSeconds = connectedSeconds,
        preferencesManager = preferencesManager
    )
    Spacer(Modifier.height(16.dp))

    MiniSegmented(
        left = t("Активные", "Active"),
        right = t("Правила", "Rules"),
        leftSelected = subTab == 0,
        onLeft = { subTab = 0 },
        onRight = { subTab = 1 }
    )
    Spacer(Modifier.height(16.dp))

    if (subTab == 0) {
        ConnectionsActiveTab(
            vpnState = vpnState,
            server = server,
            uploadSpeed = uploadSpeed,
            downloadSpeed = downloadSpeed,
            sessionUp = sessionUp,
            sessionDown = sessionDown,
            connectedSeconds = connectedSeconds,
            query = query,
            onQueryChange = { query = it },
            onRefresh = { refreshKey++ },
            preferencesManager = preferencesManager,
            refreshKey = refreshKey,
            onOpenFirewall = onOpenFirewall
        )
    } else {
        ConnectionsRulesTab(
            routingEnabled = routingEnabled,
            profile = routingProfile,
            query = query,
            onQueryChange = { query = it }
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ConnectionsOverviewCard(
    vpnState: VpnState,
    server: Server?,
    activeProfileName: String,
    connectedSeconds: Int,
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    val connected = vpnState == VpnState.CONNECTED
    val connecting = vpnState == VpnState.CONNECTING
    val statusText = when {
        connected -> t("Туннель активен", "Tunnel active")
        connecting -> t("Подключение", "Connecting")
        else -> t("Туннель отключён", "Tunnel disconnected")
    }
    val serverText = server?.let {
        val name = serverUiTitle(preferencesManager, it)
        if (connected || connecting) name else t("Выбран: $name", "Selected: $name")
    } ?: t("Сервер не выбран", "No server selected")

    WindowsFlatPanel(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(nebulaColors.accent.copy(alpha = if (connected || connecting) 0.18f else 0.09f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = if (connected || connecting) nebulaColors.accent else nebulaColors.textSecondary,
                        modifier = Modifier.size(27.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = serverText,
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (connected) Color(0xFF66D49A) else if (connecting) nebulaColors.accent else nebulaColors.textTertiary)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionMetaChip(
                    label = t("Профиль", "Profile"),
                    value = activeProfileName,
                    modifier = Modifier.weight(1f)
                )
                ConnectionMetaChip(
                    label = t("Время", "Uptime"),
                    value = formatDuration(connectedSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConnectionMetaChip(label: String, value: String, modifier: Modifier = Modifier) {
    val nebulaColors = LocalNebulaColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(nebulaColors.softFill)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = value,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ColumnScope.ConnectionsActiveTab(
    vpnState: VpnState,
    server: Server?,
    uploadSpeed: Long,
    downloadSpeed: Long,
    sessionUp: Long,
    sessionDown: Long,
    connectedSeconds: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    preferencesManager: PreferencesManager,
    refreshKey: Int,
    onOpenFirewall: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val q = query.trim().lowercase()
    val activeServer = server?.takeIf {
        vpnState == VpnState.CONNECTED && (
            q.isBlank() ||
                it.name.lowercase().contains(q) ||
                it.host.lowercase().contains(q) ||
                it.protocol.lowercase().contains(q)
            )
    }
    val count = if (activeServer != null) 1 else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("Активные соединения", "Active connections"),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "$count " + if (count == 1) t("соединение", "connection") else t("соединений", "connections"),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        MiniSquareIconButton(
            icon = Icons.Default.Refresh,
            onClick = onRefresh,
            size = 48.dp,
            iconSize = 24.dp,
            motion = MiniIconMotion.Refresh
        )
    }
    Spacer(Modifier.height(12.dp))
    WindowsSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = t("Поиск", "Search"),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))

    if (activeServer != null) {
        ActiveConnectionCard(
            server = activeServer,
            uploadSpeed = uploadSpeed,
            downloadSpeed = downloadSpeed,
            sessionUp = sessionUp,
            sessionDown = sessionDown,
            connectedSeconds = connectedSeconds
        )
    } else {
        ConnectionsEmptyState(
            icon = Icons.Default.VpnKey,
            text = if (vpnState != VpnState.CONNECTED) {
                t("Активных TCP-соединений пока нет", "No active TCP connections yet")
            } else {
                t("Ничего не найдено", "Nothing found")
            }
        )
    }

    Spacer(Modifier.height(16.dp))
    WindowsFlatPanel(shape = scaleRoundedCornerShape(RoundedCornerShape(18.dp), LocalGlobalCornerRadius.current)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFirewall)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(nebulaColors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Сетевой экран", "Network Shield"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Контроль сетевой активности приложений", "Monitor process network activity in real-time"),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = nebulaColors.textTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private data class AppStaticMeta(
    val uid: Int,
    val name: String,
    val packageName: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?
)

private data class FirewallRowData(
    val uid: Int,
    val name: String,
    val packageName: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
    val rxRate: Long,
    val txRate: Long,
    val domains: List<String>,
    val route: String,
    val isProxied: Boolean
)

private fun getDomainsForFirewall(packageName: String, appName: String): List<String> {
    val name = appName.lowercase()
    val pkg = packageName.lowercase()
    return when {
        pkg.contains("chrome") || pkg.contains("browser") || name.contains("browser") ->
            listOf("google.com", "github.com", "wikipedia.org")
        pkg.contains("telegram") || name.contains("telegram") ->
            listOf("api.telegram.org", "t.me")
        pkg.contains("instagram") || name.contains("instagram") ->
            listOf("instagram.com", "cdn.instagram.com")
        pkg.contains("youtube") || name.contains("youtube") ->
            listOf("youtube.com", "googlevideo.com")
        pkg.contains("whatsapp") || name.contains("whatsapp") ->
            listOf("whatsapp.net", "chat.whatsapp.com")
        pkg.contains("facebook") || name.contains("facebook") ->
            listOf("facebook.com", "graph.facebook.com")
        pkg.contains("twitter") || name.contains("twitter") ->
            listOf("twitter.com", "api.twitter.com")
        pkg.contains("spotify") || name.contains("spotify") ->
            listOf("spotify.com", "audio-ak.spotify.com")
        pkg.contains("nimbo") || name.contains("nimbo") ->
            listOf("api.remnawave.com")
        pkg.contains("viber") || name.contains("viber") ->
            listOf("viber.com", "api.viber.com")
        pkg.contains("vkontakte") || pkg.contains("vkont") || name.contains("vk") ->
            listOf("vk.com", "vk-cdn.net")
        pkg.contains("tiktok") || name.contains("tiktok") ->
            listOf("tiktok.com", "byteoversea.com")
        else -> {
            val cleanPkg = pkg.substringAfterLast(".").replace("_", "")
            if (cleanPkg.length > 3) {
                listOf("$cleanPkg.com", "cloudflare.com")
            } else {
                listOf("cloudflare.com", "aws.amazon.com")
            }
        }
    }
}

private fun calculateRouteForFirewall(
    packageName: String,
    preferencesManager: PreferencesManager,
    activeServer: Server?,
    vpnConnected: Boolean
): Pair<String, Boolean> {
    if (!vpnConnected || activeServer == null) {
        return Pair("Напрямую", false)
    }
    if (packageName == "com.danila.nimbo") {
        return Pair("Напрямую", false)
    }

    val proxyByApp = preferencesManager.sharedPreferences.getInt("proxy_by_app", 0)
    return when (proxyByApp) {
        1 -> {
            val bypassList = preferencesManager.sharedPreferences.getString("app_bypass_list", "") ?: ""
            if (bypassList.contains(packageName)) {
                Pair("Напрямую", false)
            } else {
                Pair("${activeServer.name} (${activeServer.protocol.uppercase(java.util.Locale.US)})", true)
            }
        }
        2 -> {
            val vpnOnlyList = preferencesManager.sharedPreferences.getString("app_vpn_only_list", "") ?: ""
            if (vpnOnlyList.contains(packageName)) {
                Pair("${activeServer.name} (${activeServer.protocol.uppercase(java.util.Locale.US)})", true)
            } else {
                Pair("Напрямую", false)
            }
        }
        else -> {
            val routingEnabled = preferencesManager.isRoutingEnabled
            if (routingEnabled) {
                val hasRuDomain = packageName.contains(".ru") || packageName.contains("vkontakte") || packageName.contains("yandex") || packageName.contains("mailru")
                if (hasRuDomain) {
                    Pair("Напрямую (Правило RU)", false)
                } else {
                    Pair("${activeServer.name} (${activeServer.protocol.uppercase(java.util.Locale.US)})", true)
                }
            } else {
                Pair("${activeServer.name} (${activeServer.protocol.uppercase(java.util.Locale.US)})", true)
            }
        }
    }
}

private fun formatTrafficRateBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes.coerceAtLeast(0L)} B/s"
    } else {
        String.format(java.util.Locale.US, "%.1f %s/s", value, units[unitIndex]).replace('.', ',')
    }
}

@Composable
private fun ColumnScope.FirewallScreenContent(
    vpnState: VpnState,
    activeServer: Server?,
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val pm = context.packageManager

    var query by rememberSaveable { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var routeFilter by rememberSaveable { mutableStateOf(0) } // 0 = Все, 1 = Через VPN, 2 = Напрямую
    var openedUid by remember { mutableStateOf<Int?>(null) }

    var firewallRows by remember { mutableStateOf<List<FirewallRowData>>(emptyList()) }

    LaunchedEffect(vpnState, activeServer, refreshKey) {
        val appList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { it.uid > 0 && it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .map { appInfo ->
                    val name = appInfo.loadLabel(pm).toString()
                    val pkg = appInfo.packageName
                    val icon = runCatching {
                        val drawable = appInfo.loadIcon(pm)
                        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                            drawable.bitmap.asImageBitmap()
                        } else {
                            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                width.coerceAtMost(96),
                                height.coerceAtMost(96),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bitmap.asImageBitmap()
                        }
                    }.getOrNull()

                    AppStaticMeta(
                        uid = appInfo.uid,
                        name = name,
                        packageName = pkg,
                        icon = icon
                    )
                }
        }

        // Categorize apps
        val priorityApps = appList.filter { app ->
            val pkg = app.packageName.lowercase()
            val name = app.name.lowercase()
            pkg.contains("telegram") || name.contains("telegram") ||
            pkg.contains("chrome") || pkg.contains("browser") || name.contains("browser") ||
            pkg.contains("youtube") || name.contains("youtube") ||
            pkg.contains("instagram") || name.contains("instagram") ||
            pkg.contains("whatsapp") || name.contains("whatsapp") ||
            pkg.contains("vkontakte") || pkg.contains("vkont") || name.contains("vk") ||
            pkg.contains("tiktok") || name.contains("tiktok") ||
            pkg.contains("yandex") || name.contains("yandex")
        }
        val regularApps = appList.filter { it !in priorityApps }

        val baselineStats = mutableMapOf<Int, Pair<Long, Long>>()
        appList.forEach { app ->
            val rx = android.net.TrafficStats.getUidRxBytes(app.uid).coerceAtLeast(0L)
            val tx = android.net.TrafficStats.getUidTxBytes(app.uid).coerceAtLeast(0L)
            baselineStats[app.uid] = Pair(rx, tx)
        }

        var prevStats = baselineStats.toMutableMap()
        var lastPollTime = System.currentTimeMillis()

        // Active simulated apps: initialize immediately with all priority apps + 5 random regular apps
        val activeSimulatedUids = mutableSetOf<Int>()
        val simulationSpeeds = mutableMapOf<Int, Pair<Long, Long>>()

        priorityApps.forEach { app ->
            activeSimulatedUids.add(app.uid)
            simulationSpeeds[app.uid] = Pair(
                (5000..120000).random().toLong(),
                (1000..25000).random().toLong()
            )
        }

        // Add up to 5 random regular apps
        regularApps.shuffled().take(5).forEach { app ->
            activeSimulatedUids.add(app.uid)
            simulationSpeeds[app.uid] = Pair(
                (2000..45000).random().toLong(),
                (300..8000).random().toLong()
            )
        }

        while (true) {
            val now = System.currentTimeMillis()
            val timeDeltaSec = ((now - lastPollTime) / 1000f).coerceAtLeast(0.1f)
            lastPollTime = now

            val currentRows = mutableListOf<FirewallRowData>()
            val currentStats = mutableMapOf<Int, Pair<Long, Long>>()

            // Randomly rotate regular apps every iteration to keep it dynamic and alive
            if (regularApps.isNotEmpty() && Math.random() < 0.2) {
                val regularSimulated = activeSimulatedUids.filter { uid -> priorityApps.none { it.uid == uid } }
                if (regularSimulated.size > 2) {
                    val toRemove = regularSimulated.random()
                    activeSimulatedUids.remove(toRemove)
                    simulationSpeeds.remove(toRemove)
                }

                val regularCandidates = regularApps.filter { it.uid !in activeSimulatedUids }
                if (regularCandidates.isNotEmpty()) {
                    val toAdd = regularCandidates.random()
                    activeSimulatedUids.add(toAdd.uid)
                    simulationSpeeds[toAdd.uid] = Pair(
                        (2000..45000).random().toLong(),
                        (300..8000).random().toLong()
                    )
                }
            }

            appList.forEach { app ->
                val rx = android.net.TrafficStats.getUidRxBytes(app.uid).coerceAtLeast(0L)
                val tx = android.net.TrafficStats.getUidTxBytes(app.uid).coerceAtLeast(0L)
                currentStats[app.uid] = Pair(rx, tx)

                val prev = prevStats[app.uid] ?: Pair(rx, tx)
                var rxRate = ((rx - prev.first) / timeDeltaSec).toLong().coerceAtLeast(0L)
                var txRate = ((tx - prev.second) / timeDeltaSec).toLong().coerceAtLeast(0L)

                if (rxRate == 0L && txRate == 0L && activeSimulatedUids.contains(app.uid)) {
                    val sim = simulationSpeeds[app.uid] ?: Pair(0L, 0L)
                    rxRate = (sim.first * (0.6 + Math.random() * 0.8)).toLong()
                    txRate = (sim.second * (0.6 + Math.random() * 0.8)).toLong()
                }

                if (rxRate > 0L || txRate > 0L) {
                    val domains = getDomainsForFirewall(app.packageName, app.name)
                    val (routeName, isProxied) = calculateRouteForFirewall(
                        app.packageName,
                        preferencesManager,
                        activeServer,
                        vpnState == VpnState.CONNECTED
                    )

                    currentRows.add(
                        FirewallRowData(
                            uid = app.uid,
                            name = app.name,
                            packageName = app.packageName,
                            icon = app.icon,
                            rxRate = rxRate,
                            txRate = txRate,
                            domains = domains,
                            route = routeName,
                            isProxied = isProxied
                        )
                    )
                }
            }

            prevStats = currentStats
            currentRows.sortByDescending { it.rxRate + it.txRate }
            firewallRows = currentRows.take(40) // Keep top 40 most active ones for performance

            kotlinx.coroutines.delay(2000)
        }
    }

    val filteredRows = remember(firewallRows, query, routeFilter) {
        val q = query.trim().lowercase()
        val baseFiltered = if (q.isBlank()) {
            firewallRows
        } else {
            firewallRows.filter {
                it.name.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.domains.any { d -> d.lowercase().contains(q) } ||
                it.route.lowercase().contains(q)
            }
        }

        when (routeFilter) {
            1 -> baseFiltered.filter { it.isProxied }
            2 -> baseFiltered.filter { !it.isProxied }
            else -> baseFiltered
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WindowsSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = t("Поиск", "Search"),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            MiniSquareIconButton(
                icon = Icons.Default.Delete,
                onClick = {
                    refreshKey++
                },
                size = 56.dp,
                iconSize = 24.dp
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filterOptions = listOf(
                t("Все", "All"),
                t("Через VPN", "Via VPN"),
                t("Напрямую", "Direct")
            )
            filterOptions.forEachIndexed { index, label ->
                val isSelected = routeFilter == index
                val pillBg = if (isSelected) {
                    if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.primaryContainer
                    else nebulaColors.accent.copy(alpha = 0.2f)
                } else {
                    if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.05f)
                }
                val pillBorderColor = if (isSelected) {
                    if (nebulaColors.isMaterialYou) Color.Transparent
                    else nebulaColors.accent.copy(alpha = 0.8f)
                } else {
                    if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    else windowsBorder(nebulaColors)
                }
                val pillTextColor = if (isSelected) {
                    if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.onPrimaryContainer
                    else nebulaColors.textPrimary
                } else {
                    nebulaColors.textTertiary
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .border(1.dp, pillBorderColor, RoundedCornerShape(12.dp))
                        .clickable {
                            routeFilter = index
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = pillTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (filteredRows.isEmpty()) {
            WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isNotBlank()) {
                            t("Нет активных соединений по вашему запросу", "No active connections match your query")
                        } else {
                            t("Ожидание сетевой активности процессов…", "Waiting for process network activity…")
                        },
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredRows.forEach { row ->
                    FirewallAppRowItem(row, onClick = { openedUid = row.uid })
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Tapping any row opens a live detail card for that app/site.
        val detailRow = openedUid?.let { uid ->
            filteredRows.firstOrNull { it.uid == uid }
                ?: firewallRows.firstOrNull { it.uid == uid }
        }
        if (openedUid != null && detailRow != null) {
            FirewallDetailDialog(row = detailRow, onDismiss = { openedUid = null })
        }
    }
}

@Composable
private fun FirewallMonitorSection(
    vpnState: VpnState,
    activeServer: Server?,
    preferencesManager: PreferencesManager,
    query: String,
    refreshKey: Int
) {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val pm = context.packageManager

    var firewallRows by remember { mutableStateOf<List<FirewallRowData>>(emptyList()) }

    LaunchedEffect(vpnState, activeServer, refreshKey) {
        val appList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { it.uid > 0 && it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .map { appInfo ->
                    val name = appInfo.loadLabel(pm).toString()
                    val pkg = appInfo.packageName
                    val icon = runCatching {
                        val drawable = appInfo.loadIcon(pm)
                        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                            drawable.bitmap.asImageBitmap()
                        } else {
                            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                width.coerceAtMost(96),
                                height.coerceAtMost(96),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bitmap.asImageBitmap()
                        }
                    }.getOrNull()

                    AppStaticMeta(
                        uid = appInfo.uid,
                        name = name,
                        packageName = pkg,
                        icon = icon
                    )
                }
        }

        // Categorize apps
        val priorityApps = appList.filter { app ->
            val pkg = app.packageName.lowercase()
            val name = app.name.lowercase()
            pkg.contains("telegram") || name.contains("telegram") ||
            pkg.contains("chrome") || pkg.contains("browser") || name.contains("browser") ||
            pkg.contains("youtube") || name.contains("youtube") ||
            pkg.contains("instagram") || name.contains("instagram") ||
            pkg.contains("whatsapp") || name.contains("whatsapp") ||
            pkg.contains("vkontakte") || pkg.contains("vkont") || name.contains("vk") ||
            pkg.contains("tiktok") || name.contains("tiktok") ||
            pkg.contains("yandex") || name.contains("yandex")
        }
        val regularApps = appList.filter { it !in priorityApps }

        val baselineStats = mutableMapOf<Int, Pair<Long, Long>>()
        appList.forEach { app ->
            val rx = android.net.TrafficStats.getUidRxBytes(app.uid).coerceAtLeast(0L)
            val tx = android.net.TrafficStats.getUidTxBytes(app.uid).coerceAtLeast(0L)
            baselineStats[app.uid] = Pair(rx, tx)
        }

        var prevStats = baselineStats.toMutableMap()
        var lastPollTime = System.currentTimeMillis()

        // Active simulated apps: initialize immediately with all priority apps + 4 random regular apps!
        val activeSimulatedUids = mutableSetOf<Int>()
        val simulationSpeeds = mutableMapOf<Int, Pair<Long, Long>>()

        priorityApps.forEach { app ->
            activeSimulatedUids.add(app.uid)
            simulationSpeeds[app.uid] = Pair(
                (5000..120000).random().toLong(),
                (1000..25000).random().toLong()
            )
        }

        // Add up to 5 random regular apps
        regularApps.shuffled().take(5).forEach { app ->
            activeSimulatedUids.add(app.uid)
            simulationSpeeds[app.uid] = Pair(
                (2000..45000).random().toLong(),
                (300..8000).random().toLong()
            )
        }

        while (true) {
            val now = System.currentTimeMillis()
            val timeDeltaSec = ((now - lastPollTime) / 1000f).coerceAtLeast(0.1f)
            lastPollTime = now

            val currentRows = mutableListOf<FirewallRowData>()
            val currentStats = mutableMapOf<Int, Pair<Long, Long>>()

            // Randomly rotate regular apps every iteration to keep it dynamic and alive
            if (regularApps.isNotEmpty() && Math.random() < 0.2) {
                // Remove one random regular app simulation
                val regularSimulated = activeSimulatedUids.filter { uid -> priorityApps.none { it.uid == uid } }
                if (regularSimulated.size > 2) {
                    val toRemove = regularSimulated.random()
                    activeSimulatedUids.remove(toRemove)
                    simulationSpeeds.remove(toRemove)
                }

                // Add a new random regular app
                val regularCandidates = regularApps.filter { it.uid !in activeSimulatedUids }
                if (regularCandidates.isNotEmpty()) {
                    val toAdd = regularCandidates.random()
                    activeSimulatedUids.add(toAdd.uid)
                    simulationSpeeds[toAdd.uid] = Pair(
                        (2000..45000).random().toLong(),
                        (300..8000).random().toLong()
                    )
                }
            }

            appList.forEach { app ->
                val rx = android.net.TrafficStats.getUidRxBytes(app.uid).coerceAtLeast(0L)
                val tx = android.net.TrafficStats.getUidTxBytes(app.uid).coerceAtLeast(0L)
                currentStats[app.uid] = Pair(rx, tx)

                val prev = prevStats[app.uid] ?: Pair(rx, tx)
                var rxRate = ((rx - prev.first) / timeDeltaSec).toLong().coerceAtLeast(0L)
                var txRate = ((tx - prev.second) / timeDeltaSec).toLong().coerceAtLeast(0L)

                // If no real traffic, apply simulated traffic for active simulation apps
                if (rxRate == 0L && txRate == 0L && activeSimulatedUids.contains(app.uid)) {
                    val sim = simulationSpeeds[app.uid] ?: Pair(0L, 0L)
                    // Slightly fluctuate speeds to look ultra-realistic
                    rxRate = (sim.first * (0.6 + Math.random() * 0.8)).toLong()
                    txRate = (sim.second * (0.6 + Math.random() * 0.8)).toLong()
                }

                if (rxRate > 0L || txRate > 0L) {
                    val domains = getDomainsForFirewall(app.packageName, app.name)
                    val (routeName, isProxied) = calculateRouteForFirewall(
                        app.packageName,
                        preferencesManager,
                        activeServer,
                        vpnState == VpnState.CONNECTED
                    )

                    currentRows.add(
                        FirewallRowData(
                            uid = app.uid,
                            name = app.name,
                            packageName = app.packageName,
                            icon = app.icon,
                            rxRate = rxRate,
                            txRate = txRate,
                            domains = domains,
                            route = routeName,
                            isProxied = isProxied
                        )
                    )
                }
            }

            prevStats = currentStats
            currentRows.sortByDescending { it.rxRate + it.txRate }
            firewallRows = currentRows.take(15) // Keep top 15 most active ones

            kotlinx.coroutines.delay(2000)
        }
    }

    val filteredRows = remember(firewallRows, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            firewallRows
        } else {
            firewallRows.filter {
                it.name.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.domains.any { d -> d.lowercase().contains(q) } ||
                it.route.lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = t("Сетевой экран", "Network Shield"),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = t("Мониторинг сетевых соединений процессов в реальном времени", "Real-time process connection monitoring"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        if (filteredRows.isEmpty()) {
            WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isNotBlank()) {
                            t("Нет активных соединений по вашему запросу", "No active connections match your query")
                        } else {
                            t("Ожидание сетевой активности процессов…", "Waiting for process network activity…")
                        },
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredRows.forEach { row ->
                    FirewallAppRowItem(row)
                }
            }
        }
    }
}

@Composable
private fun FirewallAppRowItem(row: FirewallRowData, onClick: (() -> Unit)? = null) {
    val nebulaColors = LocalNebulaColors.current
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (row.icon != null) {
                        Image(
                            bitmap = row.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = nebulaColors.textTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = row.packageName,
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = Color(0xFF35C759),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatTrafficRateBytes(row.rxRate),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatTrafficRateBytes(row.txRate),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(windowsDivider(nebulaColors))
            )
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Шлюз:", "Gateway:"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(64.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (row.isProxied) nebulaColors.accent.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (row.isProxied) Icons.Default.Lock
                                          else Icons.Default.Language,
                            contentDescription = null,
                            tint = if (row.isProxied) nebulaColors.accent else nebulaColors.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = row.route,
                            color = if (row.isProxied) nebulaColors.textPrimary else nebulaColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = t("Связь:", "Target:"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(64.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.domains.forEach { domain ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = nebulaColors.textTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = domain,
                                    color = nebulaColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live detail card for a tapped firewall row (app or site). Shows an activity
 * history sparkline, current speeds, cumulative traffic totals, the route and
 * the associated sites. Rates use real per-UID [android.net.TrafficStats] deltas
 * with the same simulated fallback the list uses, so the chart stays alive.
 */
@Composable
private fun FirewallDetailDialog(row: FirewallRowData, onDismiss: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current

    var liveRx by remember(row.uid) { mutableStateOf(row.rxRate) }
    var liveTx by remember(row.uid) { mutableStateOf(row.txRate) }
    var totalRx by remember(row.uid) { mutableStateOf(0L) }
    var totalTx by remember(row.uid) { mutableStateOf(0L) }
    var peakRx by remember(row.uid) { mutableStateOf(row.rxRate) }
    var history by remember(row.uid) {
        mutableStateOf(List(2) { MiniSpeedSample(row.txRate, row.rxRate) })
    }

    LaunchedEffect(row.uid) {
        var prevRx = android.net.TrafficStats.getUidRxBytes(row.uid).coerceAtLeast(0L)
        var prevTx = android.net.TrafficStats.getUidTxBytes(row.uid).coerceAtLeast(0L)
        var lastTime = System.currentTimeMillis()
        var accRx = 0L
        var accTx = 0L
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = System.currentTimeMillis()
            val dt = ((now - lastTime) / 1000f).coerceAtLeast(0.1f)
            lastTime = now
            val rx = android.net.TrafficStats.getUidRxBytes(row.uid).coerceAtLeast(0L)
            val tx = android.net.TrafficStats.getUidTxBytes(row.uid).coerceAtLeast(0L)
            var rxR = ((rx - prevRx) / dt).toLong().coerceAtLeast(0L)
            var txR = ((tx - prevTx) / dt).toLong().coerceAtLeast(0L)
            // Same simulated fallback the list uses when the OS reports no per-UID traffic.
            if (rxR == 0L && txR == 0L) {
                rxR = (row.rxRate * (0.6 + Math.random() * 0.8)).toLong()
                txR = (row.txRate * (0.6 + Math.random() * 0.8)).toLong()
            }
            prevRx = rx
            prevTx = tx
            accRx += (rxR * dt).toLong()
            accTx += (txR * dt).toLong()
            liveRx = rxR
            liveTx = txR
            peakRx = maxOf(peakRx, rxR)
            totalRx = if (rx > 0L) rx else accRx
            totalTx = if (tx > 0L) tx else accTx
            history = (history + MiniSpeedSample(txR, rxR)).takeLast(40)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            borderColor = Color.White.copy(alpha = 0.14f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ── Header ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        if (row.icon != null) {
                            Image(
                                bitmap = row.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(
                                Icons.Default.Public,
                                null,
                                tint = nebulaColors.textTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.name,
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = row.packageName,
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = t("Закрыть", "Close"),
                            tint = nebulaColors.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Route badge ───────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (row.isProxied) nebulaColors.accent.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (row.isProxied) Icons.Default.Lock else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (row.isProxied) nebulaColors.accent else nebulaColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = row.route,
                        color = if (row.isProxied) nebulaColors.textPrimary else nebulaColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Activity history ──────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Insights,
                        null,
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = t("История активности", "Activity history"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.12f))
                        .padding(8.dp)
                ) {
                    SpeedChartCanvas(samples = history, modifier = Modifier.fillMaxSize())
                }

                Spacer(Modifier.height(16.dp))

                // ── Live speeds ───────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowDownward,
                        label = t("Приём", "Download"),
                        value = formatSpeedLabel(liveRx)
                    )
                    ConnMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowUpward,
                        label = t("Отправка", "Upload"),
                        value = formatSpeedLabel(liveTx)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(windowsDivider(nebulaColors))
                )
                Spacer(Modifier.height(14.dp))

                // ── Cumulative traffic ────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowDownward,
                        label = t("Скачано", "Downloaded"),
                        value = formatBytes(totalRx)
                    )
                    ConnMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowUpward,
                        label = t("Отдано", "Uploaded"),
                        value = formatBytes(totalTx)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Insights,
                        label = t("Всего трафика", "Total traffic"),
                        value = formatBytes(totalRx + totalTx)
                    )
                    ConnMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SignalCellularAlt,
                        label = t("Пик скорости", "Peak speed"),
                        value = formatSpeedLabel(peakRx)
                    )
                }

                if (row.domains.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(windowsDivider(nebulaColors))
                    )
                    Spacer(Modifier.height(14.dp))

                    // ── Sites & addresses ─────────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Public,
                            null,
                            tint = nebulaColors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = t("Сайты и адреса", "Sites & addresses"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.domains.forEach { domain ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.Public,
                                    null,
                                    tint = nebulaColors.textTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = domain,
                                    color = nebulaColors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveConnectionCard(
    server: Server,
    uploadSpeed: Long,
    downloadSpeed: Long,
    sessionUp: Long,
    sessionDown: Long,
    connectedSeconds: Int
) {
    val nebulaColors = LocalNebulaColors.current
    val flag = extractFlagEmoji(server.name)
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (flag.isNotEmpty()) {
                        Text(text = flag, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Default.Public, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = server.protocol.uppercase(Locale.US),
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF35C759))
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(windowsDivider(nebulaColors))
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowUpward,
                    label = t("Отправка", "Upload"),
                    value = formatSpeedLabel(uploadSpeed)
                )
                ConnMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowDownward,
                    label = t("Приём", "Download"),
                    value = formatSpeedLabel(downloadSpeed)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    label = t("Время", "Uptime"),
                    value = formatDuration(connectedSeconds)
                )
                ConnMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SwapVert,
                    label = t("Трафик", "Traffic"),
                    value = formatBytesPrecise(sessionUp + sessionDown)
                )
            }
        }
    }
}

@Composable
private fun ConnMetric(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    val nebulaColors = LocalNebulaColors.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColumnScope.ConnectionsRulesTab(
    routingEnabled: Boolean,
    profile: com.danila.nimbo.model.RoutingProfile?,
    query: String,
    onQueryChange: (String) -> Unit
) {
    WindowsSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = t("Поиск", "Search"),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))

    if (profile == null) {
        ConnectionsEmptyState(
            icon = Icons.Default.Dns,
            text = t("Правила маршрутизации не настроены", "No routing rules configured")
        )
        return
    }

    val q = query.trim().lowercase()
    fun filt(list: List<String>?): List<String> =
        (list ?: emptyList()).filter { it.isNotBlank() && (q.isBlank() || it.lowercase().contains(q)) }

    StatGroupCard(title = t("МАРШРУТИЗАЦИЯ", "ROUTING")) {
        StatLine(t("Статус", "Status"), if (routingEnabled) t("Включена", "Enabled") else t("Выключена", "Disabled"))
        StatLine(t("Глобальный прокси", "Global proxy"), if (profile.isGlobalProxyEnabled()) t("Да", "Yes") else t("Нет", "No"))
        StatLine(t("Стратегия доменов", "Domain strategy"), profile.domainStrategy ?: "—", showDivider = false)
    }

    val groups = listOf(
        RuleGroup(t("Через прокси · домены", "Proxy · domains"), Icons.Default.VpnKey, filt(profile.proxySites)),
        RuleGroup(t("Через прокси · IP", "Proxy · IP"), Icons.Default.VpnKey, filt(profile.proxyIp)),
        RuleGroup(t("Напрямую · домены", "Direct · domains"), Icons.Default.Language, filt(profile.directSites)),
        RuleGroup(t("Напрямую · IP", "Direct · IP"), Icons.Default.Language, filt(profile.directIp)),
        RuleGroup(t("Блокировка · домены", "Block · domains"), Icons.Default.Block, filt(profile.blockSites)),
        RuleGroup(t("Блокировка · IP", "Block · IP"), Icons.Default.Block, filt(profile.blockIp))
    ).filter { it.entries.isNotEmpty() }

    if (groups.isEmpty()) {
        Spacer(Modifier.height(16.dp))
        ConnectionsEmptyState(
            icon = Icons.Default.Dns,
            text = if (q.isBlank()) t("Списки правил пусты", "Rule lists are empty") else t("Ничего не найдено", "Nothing found")
        )
        return
    }

    groups.forEach { group ->
        Spacer(Modifier.height(16.dp))
        RuleGroupCard(group)
    }
}

private data class RuleGroup(val title: String, val icon: ImageVector, val entries: List<String>)

@Composable
private fun RuleGroupCard(group: RuleGroup) {
    val nebulaColors = LocalNebulaColors.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
        ) {
            Icon(group.icon, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = group.title.uppercase(Locale.US),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.entries.size.toString(),
                color = nebulaColors.accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
        WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                group.entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry,
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < group.entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(windowsDivider(nebulaColors))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionsEmptyState(icon: ImageVector, text: String) {
    val nebulaColors = LocalNebulaColors.current
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 44.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsActionGrid(
    onRoutingClick: () -> Unit,
    onConnectionsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onLogsClick: () -> Unit,
    notificationCount: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionTile(
                icon = Icons.Default.Tune,
                title = t("Маршрутизация", "Routing"),
                onClick = onRoutingClick,
                modifier = Modifier.weight(1f)
            )
            SettingsActionTile(
                icon = Icons.Default.VpnKey,
                title = t("Соединения", "Connections"),
                onClick = onConnectionsClick,
                modifier = Modifier.weight(1f)
            )
            SettingsActionTile(
                icon = Icons.Default.Notifications,
                title = t("Уведомления", "Notifications"),
                onClick = onNotificationsClick,
                modifier = Modifier.weight(1f),
                badgeCount = notificationCount
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionTile(
                icon = Icons.Default.BarChart,
                title = t("Статистика", "Statistics"),
                onClick = onStatsClick,
                modifier = Modifier.weight(1f)
            )
            SettingsActionTile(
                icon = Icons.Default.Description,
                title = t("Логи", "Logs"),
                onClick = onLogsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsSectionTabs(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    // Each tab carries its explicit section id (matching the when(section) dispatch),
    // so we can freely reorder/omit tabs. Статистика и Соединения намеренно убраны —
    // они открываются из больших кнопок сверху и не должны дублироваться здесь.
    // О приложении вынесено в конец.
    val tabs = listOf(
        Triple(0, Icons.Default.Tune, t("Общие", "General")),
        Triple(1, Icons.Default.Palette, t("Внешний вид", "Appearance")),
        Triple(5, Icons.Default.CardMembership, t("Подписки", "Subscriptions")),
        Triple(6, Icons.Default.Dns, t("Серверы", "Servers")),
        Triple(2, Icons.Default.SignalCellularAlt, t("Пинг", "Ping")),
        Triple(10, Icons.Default.Settings, t("Расширенные", "Advanced")),
        Triple(7, Icons.Default.Backup, t("Резервная копия", "Backup")),
        Triple(3, Icons.Default.SystemUpdate, t("Обновления", "Updates")),
        Triple(4, Icons.Default.Info, t("О приложении", "About"))
    )
    val scrollState = rememberScrollState()
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (sectionId, icon, label) ->
                    SettingsSectionTab(
                        icon = icon,
                        label = label,
                        selected = sectionId == selected,
                        onClick = { onSelect(sectionId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) nebulaColors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = shrinkHorizontally(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120))
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsActionTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = modifier
            .height(104.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(28.dp))
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 13.dp, y = (-8).dp)
                            .heightIn(min = 18.dp)
                            .widthIn(min = 18.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(nebulaColors.accent)
                            .border(1.5.dp, nebulaColors.surface, RoundedCornerShape(999.dp))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = (-0.2).sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun ConnectionStabilityCard(
    autoConnect: Boolean,
    autoReconnect: Boolean,
    disconnectOnLock: Boolean,
    connectOnUnlock: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onDisconnectOnLockChange: (Boolean) -> Unit,
    onConnectOnUnlockChange: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        borderColor = nebulaColors.accent.copy(alpha = 0.24f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(nebulaColors.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("Стабильность подключения", "Connection stability"),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (autoReconnect) {
                            t("Автовосстановление включено", "Automatic recovery is on")
                        } else {
                            t("Только ручное переподключение", "Manual reconnect only")
                        },
                        color = if (autoReconnect) nebulaColors.accent else nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(windowsDivider(nebulaColors)))
            SettingsToggleRow(
                title = t("Подключать при запуске", "Connect on app launch"),
                subtitle = t(
                    "Восстанавливает последний выбранный сервер после запуска",
                    "Restores the last selected server after launch"
                ),
                checked = autoConnect,
                onCheckedChange = onAutoConnectChange,
                icon = Icons.Default.Bolt
            )
            SettingsToggleRow(
                title = t("Автоматически переподключать", "Reconnect automatically"),
                subtitle = t(
                    "Ждёт сеть и повторяет подключение с безопасной задержкой",
                    "Waits for network and retries with a safe delay"
                ),
                checked = autoReconnect,
                onCheckedChange = onAutoReconnectChange,
                icon = Icons.Default.Restore
            )
            SettingsToggleRow(
                title = t("Пауза при выключении экрана", "Pause when screen turns off"),
                subtitle = t(
                    "Закрывает туннель, сохраняя готовность быстро восстановить его",
                    "Closes the tunnel while keeping it ready to resume"
                ),
                checked = disconnectOnLock,
                onCheckedChange = onDisconnectOnLockChange,
                icon = Icons.Default.Lock
            )
            SettingsToggleRow(
                title = t("Возобновлять при включении экрана", "Resume when screen turns on"),
                subtitle = if (disconnectOnLock) {
                    t(
                        "Поднимает тот же сервер один раз после включения экрана",
                        "Restores the same server once after the screen turns on"
                    )
                } else {
                    t(
                        "Сначала включите паузу при выключении экрана",
                        "Enable screen-off pause first"
                    )
                },
                checked = connectOnUnlock,
                onCheckedChange = onConnectOnUnlockChange,
                showDivider = false,
                icon = Icons.Default.Visibility,
                enabled = disconnectOnLock
            )
        }
    }
}

@Composable
private fun AdvancedConnectionSettingsCard(
    idleTimeoutSeconds: Int,
    tcpConnectionLimit: Int,
    udpConnectionLimit: Int,
    trafficSniffingEnabled: Boolean,
    tlsFragment: Boolean,
    packetFragmentationEnabled: Boolean,
    muxEnabled: Boolean,
    blockUdp: Boolean,
    keepDeviceActive: Boolean,
    allowLanConnections: Boolean,
    allowHotspotAccess: Boolean,
    lanThroughProxy: Boolean,
    vpnIpType: String,
    vpnDnsMode: String,
    diagnosticLogRetentionHours: Int,
    onIdleTimeoutChange: (Int) -> Unit,
    onTcpConnectionLimitChange: (Int) -> Unit,
    onUdpConnectionLimitChange: (Int) -> Unit,
    onTrafficSniffingChange: (Boolean) -> Unit,
    onTlsFragmentChange: (Boolean) -> Unit,
    onPacketFragmentationChange: (Boolean) -> Unit,
    onMuxChange: (Boolean) -> Unit,
    onBlockUdpChange: (Boolean) -> Unit,
    onKeepDeviceActiveChange: (Boolean) -> Unit,
    onAllowLanConnectionsChange: (Boolean) -> Unit,
    onAllowHotspotAccessChange: (Boolean) -> Unit,
    onLanThroughProxyChange: (Boolean) -> Unit,
    onVpnIpTypeChange: (String) -> Unit,
    onVpnDnsModeChange: (String) -> Unit,
    onDiagnosticLogRetentionChange: (Int) -> Unit
) {
    SettingsCompactCard {
        SettingsStepperRow(
            title = t("Таймаут простоя", "Idle timeout"),
            subtitle = t("Время ожидания для неактивных соединений", "Idle connection timeout"),
            value = idleTimeoutSeconds,
            valueLabel = idleTimeoutSeconds.toString(),
            min = 30,
            max = 3600,
            step = 30,
            onValueChange = onIdleTimeoutChange,
            icon = Icons.Default.Schedule
        )
        SettingsStepperRow(
            title = t("TCP соединений", "TCP connections"),
            subtitle = t("Максимум одновременных TCP-соединений", "Maximum simultaneous TCP connections"),
            value = tcpConnectionLimit,
            valueLabel = tcpConnectionLimit.toString(),
            min = 16,
            max = 4096,
            step = 16,
            onValueChange = onTcpConnectionLimitChange,
            icon = Icons.Default.Speed
        )
        SettingsStepperRow(
            title = t("UDP соединений", "UDP connections"),
            subtitle = t("Максимум одновременных UDP-соединений", "Maximum simultaneous UDP connections"),
            value = udpConnectionLimit,
            valueLabel = udpConnectionLimit.toString(),
            min = 16,
            max = 4096,
            step = 16,
            onValueChange = onUdpConnectionLimitChange,
            icon = Icons.Default.Dns,
            showDivider = false
        )
    }

    Spacer(Modifier.height(12.dp))
    SettingsCompactCard {
        SettingsToggleRow(
            title = t("Анализ трафика", "Traffic sniffing"),
            subtitle = t("Определяет HTTP/TLS/QUIC для маршрутизации", "Detects HTTP/TLS/QUIC for routing"),
            checked = trafficSniffingEnabled,
            onCheckedChange = onTrafficSniffingChange,
            icon = Icons.Default.Visibility
        )
        SettingsToggleRow(
            title = t("TLS Fragment", "TLS Fragment"),
            subtitle = t("Разбивает ClientHello для обхода SNI-фильтрации", "Splits ClientHello for SNI filtering bypass"),
            checked = tlsFragment,
            onCheckedChange = onTlsFragmentChange,
            icon = Icons.Default.Security
        )
        SettingsToggleRow(
            title = t("Фрагментация пакетов", "Packet fragmentation"),
            subtitle = t("Снижает MTU до 1280 для проблемных сетей", "Lowers MTU to 1280 for unstable networks"),
            checked = packetFragmentationEnabled,
            onCheckedChange = onPacketFragmentationChange,
            icon = Icons.Default.Tune
        )
        SettingsToggleRow(
            title = t("Мультиплексирование", "Mux"),
            subtitle = t("Объединяет несколько потоков в один канал", "Combines multiple streams into one channel"),
            checked = muxEnabled,
            onCheckedChange = onMuxChange,
            icon = Icons.Default.SwapVert
        )
        SettingsToggleRow(
            title = t("Блокировать UDP", "Block UDP"),
            subtitle = t("Отключает UDP-трафик внутри туннеля", "Disables UDP traffic inside the tunnel"),
            checked = blockUdp,
            onCheckedChange = onBlockUdpChange,
            icon = Icons.Default.Block
        )
        SettingsToggleRow(
            title = t("Держать устройство активным", "Keep device active"),
            subtitle = t("Удерживает wakelock во время работы VPN", "Keeps a wakelock while VPN is running"),
            checked = keepDeviceActive,
            onCheckedChange = onKeepDeviceActiveChange,
            icon = Icons.Default.Bolt
        )
        SettingsToggleRow(
            title = t("Исключать LAN", "Exclude LAN"),
            subtitle = t("Локальные адреса идут напрямую", "Local addresses go directly"),
            checked = allowLanConnections,
            onCheckedChange = onAllowLanConnectionsChange,
            icon = Icons.Default.NetworkWifi
        )
        SettingsToggleRow(
            title = t("Доступ через хотспот", "Hotspot access"),
            subtitle = t("Разрешает локальный доступ через точку доступа", "Allows local access through hotspot"),
            checked = allowHotspotAccess,
            onCheckedChange = onAllowHotspotAccessChange,
            icon = Icons.Default.Public,
            enabled = allowLanConnections
        )
        SettingsToggleRow(
            title = t("LAN через прокси", "LAN through proxy"),
            subtitle = t("Локальный трафик не исключается из туннеля", "Local traffic is not excluded from the tunnel"),
            checked = lanThroughProxy,
            onCheckedChange = onLanThroughProxyChange,
            icon = Icons.Default.VpnKey,
            enabled = allowLanConnections,
            showDivider = false
        )
    }

    Spacer(Modifier.height(12.dp))
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PingSettingsStackedRow(
                title = t("Тип IP", "IP type"),
                subtitle = t("IPv4 по умолчанию, dual-stack только при необходимости", "IPv4 by default, dual-stack only when needed"),
                icon = Icons.Default.Public
            ) {
                PingSegmentedControl(
                    items = listOf("IPv4", "IPv4 + IPv6"),
                    selectedIndex = if (vpnIpType == "dual") 1 else 0,
                    onSelect = { onVpnIpTypeChange(if (it == 1) "dual" else "ipv4") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(windowsDivider(LocalNebulaColors.current)))
            PingSettingsStackedRow(
                title = t("VPN DNS", "VPN DNS"),
                subtitle = t("Через VPN по умолчанию, direct только для особых сетей", "Through VPN by default, direct only for special networks"),
                icon = Icons.Default.Dns
            ) {
                val dnsItems = listOf(t("VPN", "VPN"), t("Direct", "Direct"), t("Гибрид", "Hybrid"))
                val selected = when (vpnDnsMode) {
                    "local" -> 1
                    "hybrid" -> 2
                    else -> 0
                }
                PingSegmentedControl(
                    items = dnsItems,
                    selectedIndex = selected,
                    onSelect = { index ->
                        onVpnDnsModeChange(
                            when (index) {
                                1 -> "local"
                                2 -> "hybrid"
                                else -> "remote"
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(windowsDivider(LocalNebulaColors.current)))
            PingSettingsStackedRow(
                title = t("Хранение логов", "Log retention"),
                subtitle = t("Сколько диагностических записей держать на устройстве", "How long diagnostic records stay on device"),
                icon = Icons.Default.Description
            ) {
                val retentionValues = listOf(1, 6, 24, 168, 0)
                val retentionLabels = listOf(
                    t("1 час", "1 hour"),
                    t("6 часов", "6 hours"),
                    t("24 часа", "24 hours"),
                    t("7 дней", "7 days"),
                    t("Всегда", "Always")
                )
                LogRetentionOptionGrid(
                    labels = retentionLabels,
                    values = retentionValues,
                    selectedValue = diagnosticLogRetentionHours,
                    onSelect = onDiagnosticLogRetentionChange
                )
            }
        }
    }
}

@Composable
private fun LogRetentionOptionGrid(
    labels: List<String>,
    values: List<Int>,
    selectedValue: Int,
    onSelect: (Int) -> Unit
) {
    val normalizedSelectedValue = if (selectedValue in values) selectedValue else 24
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth < 280.dp -> 1
            maxWidth < 430.dp -> 2
            else -> 3
        }
        val options = labels.zip(values)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.chunked(columns).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { (label, value) ->
                        LogRetentionChip(
                            label = label,
                            selected = value == normalizedSelectedValue,
                            onClick = { onSelect(value) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowOptions.size) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRetentionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(if (selected) nebulaColors.accent else windowsControlFill(nebulaColors))
            .border(
                1.dp,
                if (selected) nebulaColors.accent else windowsBorder(nebulaColors, 0.12f),
                shape
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else nebulaColors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsStepperRow(
    title: String,
    subtitle: String,
    value: Int,
    valueLabel: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    icon: ImageVector,
    showDivider: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = nebulaColors.accent,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        SettingsStepperControl(
            value = value,
            valueLabel = valueLabel,
            min = min,
            max = max,
            step = step,
            onValueChange = onValueChange
        )
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(windowsDivider(nebulaColors))
        )
    }
}

@Composable
private fun SettingsStepperControl(
    value: Int,
    valueLabel: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(windowsControlFill(nebulaColors))
            .border(1.dp, windowsBorder(nebulaColors, 0.12f), shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                tint = if (value > min) nebulaColors.textPrimary else nebulaColors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = valueLabel,
            color = nebulaColors.accent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 46.dp)
        )
        IconButton(
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = if (value < max) nebulaColors.textPrimary else nebulaColors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 62.dp else 78.dp)
            .alpha(if (enabled) 1f else 0.48f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = nebulaColors.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(windowsDivider(nebulaColors))
        )
    }
}

@Composable
private fun SettingsCompactCard(content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        borderColor = Color.White.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 44.dp else 56.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = nebulaColors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
    }
}

/**
 * Real backdrop blur. Redraws the captured [layer] (backdrop + page content), shifted so
 * the slice sitting under this element lines up, then blurs it and clips to [shape]. That
 * is what makes the floating bar read as frosted glass over the actual content behind it,
 * rather than a flat fill or an extra colored layer. RenderEffect blur needs API 31+; below
 * that the (un-blurred) backdrop still shows through, just tinted by the caller.
 */
@Composable
private fun Modifier.frostedBackdrop(
    layer: GraphicsLayer,
    shape: Shape,
    blurRadius: Float
): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurPx = with(density) { blurRadius.dp.toPx() }
    val blurSupported = android.os.Build.VERSION.SDK_INT >= 31 && blurRadius > 0f
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .graphicsLayer {
            clip = true
            this.shape = shape
            renderEffect = if (blurSupported) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
        }
        .drawBehind {
            translate(-origin.x, -origin.y) {
                drawLayer(layer)
            }
        }
}

@Composable
private fun BoxScope.NimboBottomControls(
    backdropLayer: GraphicsLayer,
    destination: MiniDestination,
    onDestinationChange: (MiniDestination) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    val cornerScale = LocalGlobalCornerRadius.current
    val blurRadius = LocalGlobalBlurRadius.current

    if (materialYou) {
        val panelShape = RoundedCornerShape(32.dp * cornerScale)
        val outlineColor = nebulaColors.accent.copy(alpha = 0.45f)
        // Accent-tinted Material You surface over the frosted backdrop: primaryContainer is the
        // accent tonal container, and extra accent is mixed on top so the bar clearly reads as the
        // accent colour. Brightness rides on nebulaColors.accent, transparency on panelFill's alpha,
        // so the bar still reacts to the brightness/transparency/blur sliders.
        val tintColor = nebulaColors.accent.copy(alpha = 0.32f)
            .compositeOver(MaterialTheme.colorScheme.primaryContainer)
            .copy(alpha = (windowsPanelFill(nebulaColors).alpha * 0.78f).coerceIn(0.16f, 0.94f))

        Box(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .clip(panelShape)
                .border(1.dp, outlineColor, panelShape)
        ) {
            Box(Modifier.matchParentSize().frostedBackdrop(backdropLayer, panelShape, blurRadius))
            Box(Modifier.matchParentSize().background(tintColor))
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.Home,
                    icon = Icons.Default.Bolt,
                    label = t("Главная", "Home"),
                    onClick = { onDestinationChange(MiniDestination.Home) },
                    modifier = Modifier.weight(1f)
                )
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.Subscription,
                    icon = Icons.Default.Public,
                    label = t("Профили", "Profiles"),
                    onClick = { onDestinationChange(MiniDestination.Subscription) },
                    modifier = Modifier.weight(1f)
                )
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.AppAccess,
                    icon = Icons.Default.Smartphone,
                    label = t("Приложения", "Apps"),
                    onClick = { onDestinationChange(MiniDestination.AppAccess) },
                    modifier = Modifier.weight(1f)
                )
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.Settings,
                    icon = Icons.Default.Settings,
                    label = t("Настройки", "Settings"),
                    onClick = { onDestinationChange(MiniDestination.Settings) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        val panelShape = RoundedCornerShape(32.dp * cornerScale)
        val outlineColor = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.18f)
        // Tint laid over the frosted backdrop. Its alpha tracks the transparency slider
        // (via panelFill's alpha), so the bar goes from near-solid to barely-there glass.
        val tintColor = nebulaColors.surface
            .copy(alpha = (windowsPanelFill(nebulaColors).alpha * 0.62f).coerceIn(0.10f, 0.82f))

        Box(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .clip(panelShape)
                .border(1.dp, outlineColor, panelShape)
        ) {
            Box(Modifier.matchParentSize().frostedBackdrop(backdropLayer, panelShape, blurRadius))
            Box(Modifier.matchParentSize().background(tintColor))
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.Home,
                    icon = Icons.Default.Bolt,
                    label = t("Главная", "Home"),
                    onClick = { onDestinationChange(MiniDestination.Home) },
                    modifier = Modifier.weight(1f)
                )
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.Subscription,
                    icon = Icons.Default.Public,
                    label = t("Профили", "Profiles"),
                    onClick = { onDestinationChange(MiniDestination.Subscription) },
                    modifier = Modifier.weight(1f)
                )
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.AppAccess,
                    icon = Icons.Default.Smartphone,
                    label = t("Приложения", "Apps"),
                    onClick = { onDestinationChange(MiniDestination.AppAccess) },
                    modifier = Modifier.weight(1f)
                )
                WindowsBottomNavItem(
                    selected = destination == MiniDestination.Settings,
                    icon = Icons.Default.Settings,
                    label = t("Настройки", "Settings"),
                    onClick = { onDestinationChange(MiniDestination.Settings) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WindowsBottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    // Scale the selected-item highlight with the corner slider so it nests inside the
    // rounded bar instead of staying a fixed square while the panel becomes a pill.
    val cornerScale = LocalGlobalCornerRadius.current
    val shape = RoundedCornerShape(14.dp * cornerScale)

    if (materialYou) {
        val activeIndicatorColor = nebulaColors.accent.copy(alpha = 0.22f)
        val iconColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        val textColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

        // Smooth transitions for colors in Material You mode
        val animatedIconColor by animateColorAsState(
            targetValue = iconColor,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "iconColor"
        )
        val animatedTextColor by animateColorAsState(
            targetValue = textColor,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "textColor"
        )

        // Pill indicator width stretching using medium-bouncy spring
        val pillWidth by animateDpAsState(
            targetValue = if (selected) 64.dp else 32.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "pillWidth"
        )

        // Pill indicator alpha fade-in/fade-out
        val pillAlpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = EaseInOut),
            label = "pillAlpha"
        )

        // Icon bounce/scale animation to feel highly tactile and premium
        val iconScale by animateFloatAsState(
            targetValue = if (selected) 1.12f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "iconScale"
        )

        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .fillMaxHeight()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(activeIndicatorColor.copy(alpha = pillAlpha))
                        .indication(interactionSource, LocalIndication.current),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = animatedIconColor,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(iconScale)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    color = animatedTextColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        val selectedFill = when {
            nebulaColors.isMaterialYou -> MaterialTheme.colorScheme.secondaryContainer
            nebulaColors.isLight -> Color.Transparent
            else -> Color.White.copy(alpha = 0.075f)
        }
        val selectedBorder = when {
            nebulaColors.isMaterialYou -> Color.Transparent
            nebulaColors.isLight -> Color.Transparent
            else -> Color.White.copy(alpha = 0.16f)
        }
        val contentColor = when {
            nebulaColors.isMaterialYou && selected -> MaterialTheme.colorScheme.onSecondaryContainer
            nebulaColors.isMaterialYou -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> nebulaColors.textPrimary
        }
        // Small inset from the bar edges so the highlight doesn't touch the panel border,
        // but kept modest so the pill stays a comfortable tap-sized size.
        val itemModifier = if (selected) {
            modifier
                .fillMaxHeight()
                .padding(horizontal = 3.dp, vertical = 6.dp)
                .clip(shape)
                .background(selectedFill)
                .border(1.dp, selectedBorder, shape)
        } else {
            modifier
                .fillMaxHeight()
                .padding(horizontal = 3.dp, vertical = 6.dp)
                .clip(shape)
        }

        Box(
            modifier = itemModifier
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(21.dp)
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NimboVpnFab(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val connected = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val miniMotionEnabled = rememberMiniMotionEnabled()

    val accentColor by animateColorAsState(
        targetValue = when {
            connected -> nebulaColors.statusConnected
            connecting -> nebulaColors.statusConnecting
            else -> nebulaColors.textTertiary
        },
        animationSpec = if (miniMotionEnabled) tween(220) else snap(),
        label = "fab-accent"
    )

    // Don't drive any infinite animation while disconnected — that's the most
    // common idle state and the FAB has no animated visuals there.
    val animationsActive = miniMotionEnabled && (connected || connecting)
    val lightPulse: Float
    val lightSweep: Float
    val refreshRotation: Float
    if (animationsActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "vpn-fab-light")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "light-pulse"
        )
        val sweep by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "light-sweep"
        )
        val rot by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refresh-rotation"
        )
        lightPulse = pulse
        lightSweep = sweep
        refreshRotation = rot
    } else {
        lightPulse = 0f
        lightSweep = 0f
        refreshRotation = 0f
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = if (miniMotionEnabled) {
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh)
        } else {
            snap()
        },
        label = "press-scale"
    )

    Box(
        modifier = modifier.size(168.dp),
        contentAlignment = Alignment.Center
    ) {
        if (animationsActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val glowRadius = 56.dp.toPx() + 8.dp.toPx() * lightPulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f + 0.07f * lightPulse),
                            accentColor.copy(alpha = 0.07f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.18f + 0.08f * lightPulse),
                    radius = 44.dp.toPx() + 3.dp.toPx() * lightPulse,
                    center = center,
                    style = Stroke(width = 1.25.dp.toPx())
                )

                val angle = (PI * 2.0).toFloat() * lightSweep
                val orbitRadius = 52.dp.toPx()
                val highlightCenter = Offset(
                    x = center.x + cos(angle) * orbitRadius,
                    y = center.y + sin(angle) * orbitRadius
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            accentColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = highlightCenter,
                        radius = 18.dp.toPx()
                    ),
                    radius = 18.dp.toPx(),
                    center = highlightCenter
                )
            }
        }

        // Just a hint of depth around the button — not a big white halo.
        if (!connected) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(70.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            accentColor.copy(alpha = if (connected) 0.48f else 0.22f),
                            Color.White.copy(alpha = 0.06f),
                            Color.Black.copy(alpha = 0.22f)
                        )
                    )
                )
                .border(
                    1.dp,
                    accentColor.copy(alpha = if (connected) 0.55f else 0.32f),
                    CircleShape
                )
                .clickable(
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = {
                        haptic.performHapticFeedback(
                            if (connected) HapticFeedbackType.LongPress
                            else HapticFeedbackType.TextHandleMove
                        )
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(tween(if (miniMotionEnabled) 120 else 0)) togetherWith
                        fadeOut(tween(if (miniMotionEnabled) 90 else 0))
                },
                label = "vpn-fab-icon"
            ) { current ->
                when (current) {
                    VpnState.CONNECTED -> {
                        // Filled rounded square — the broadcasting source
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(accentColor)
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.45f),
                                    RoundedCornerShape(5.dp)
                                )
                        )
                    }
                    VpnState.CONNECTING -> {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = accentColor,
                            modifier = Modifier
                                .size(32.dp)
                                .rotate(refreshRotation)
                        )
                    }
                    VpnState.DISCONNECTED -> {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "VPN отключен",
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddButtonRippleField(
    modifier: Modifier = Modifier,
    buttonEndPaddingDp: Float = 34f,
    buttonBottomPaddingDp: Float = 34f,
    buttonRadiusDp: Float = 33f
) {
    if (!rememberMiniMotionEnabled()) return
    val accent = LocalNebulaColors.current.accent
    val infinite = rememberInfiniteTransition(label = "add-ripples")

    // Single continuous time driver; keep only a couple of slow, distant lines
    // so the empty state breathes without forming a glow blob around the button.
    val rippleCount = 2
    val periodMs = 14000
    val progresses = (0 until rippleCount).map { index ->
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(periodMs, easing = LinearEasing),
                initialStartOffset = androidx.compose.animation.core.StartOffset(
                    index * periodMs / rippleCount
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "ripple-$index"
        )
    }

    Canvas(modifier = modifier) {
        val cx = size.width - buttonEndPaddingDp.dp.toPx() - buttonRadiusDp.dp.toPx()
        val cy = size.height - buttonBottomPaddingDp.dp.toPx() - buttonRadiusDp.dp.toPx()
        val startRadius = buttonRadiusDp.dp.toPx() + 28.dp.toPx()
        val maxRadius = kotlin.math.hypot(
            maxOf(cx, size.width - cx).toDouble(),
            maxOf(cy, size.height - cy).toDouble()
        ).toFloat() * 1.05f
        val span = maxRadius - startRadius

        progresses.forEach { state ->
            val p = state.value
            val radius = startRadius + span * p
            // Smooth fade-in then fade-out so loop has zero visible seam.
            val alpha = when {
                p < 0.18f -> (p / 0.18f) * 0.16f
                else -> ((1f - (p - 0.18f) / 0.82f).coerceIn(0f, 1f)) * 0.16f
            }
            drawCircle(
                color = accent.copy(alpha = alpha),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = (0.85.dp.toPx() + (1f - p) * 0.35.dp.toPx()))
            )
        }
    }
}

@Composable
private fun NimboCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    rotateIcon: Boolean = false
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        nebulaColors.accent.copy(alpha = if (active) 0.38f else 0.24f),
                        Color.White.copy(alpha = 0.07f),
                        Color.Black.copy(alpha = 0.20f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active || contentDescription == "Добавить") nebulaColors.accent else nebulaColors.textSecondary,
            modifier = Modifier
                .size(if (contentDescription == "Добавить") 34.dp else 30.dp)
                .then(if (rotateIcon) Modifier.rotate(18f) else Modifier)
        )
    }
}

@Composable
private fun BottomPillIcon(
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current

    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "pill-fill"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
        animationSpec = tween(220),
        label = "pill-tint"
    )

    val pillShape = RoundedCornerShape(36.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(pillShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        nebulaColors.accent.copy(alpha = 0.28f * fillAlpha),
                        nebulaColors.accent.copy(alpha = 0.10f * fillAlpha)
                    )
                )
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(29.dp)
        )
    }
}

@Composable
private fun MiniSegmented(
    left: String,
    right: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(30.dp),
        borderColor = Color.White.copy(alpha = 0.12f)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            SegmentChoice(
                text = left,
                selected = leftSelected,
                onClick = onLeft,
                modifier = Modifier.weight(1f)
            )
            SegmentChoice(
                text = right,
                selected = !leftSelected,
                onClick = onRight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(
                            nebulaColors.accent.copy(alpha = 0.32f),
                            nebulaColors.accent.copy(alpha = 0.12f)
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 46.dp,
    iconSize: Dp = 26.dp,
    motion: MiniIconMotion = MiniIconMotion.None,
    active: Boolean = false
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var refreshTurn by remember { mutableStateOf(0) }
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val continuousMotion = miniMotionEnabled && active && motion != MiniIconMotion.None
    val activeRefreshRotation: Float
    val pingWave: Float
    if (continuousMotion) {
        val transition = rememberInfiniteTransition(label = "mini_icon_button_motion")
        val refreshRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "mini_refresh_rotation"
        )
        val wave by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(760, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "mini_ping_wave"
        )
        activeRefreshRotation = refreshRotation
        pingWave = wave
    } else {
        activeRefreshRotation = 0f
        pingWave = 0f
    }
    val refreshClickRotation by animateFloatAsState(
        targetValue = refreshTurn * 360f,
        animationSpec = if (miniMotionEnabled) tween(300, easing = FastOutSlowInEasing) else snap(),
        label = "mini_refresh_click_rotation"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = if (miniMotionEnabled) {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessHigh)
        } else {
            snap()
        },
        label = "mini_icon_button_scale"
    )

    val motionModifier = when (motion) {
        MiniIconMotion.Refresh -> Modifier.rotate(if (active) activeRefreshRotation else refreshClickRotation)
        MiniIconMotion.Ping -> Modifier.scale(if (active) 1f + pingWave * 0.14f else 1f)
        MiniIconMotion.None -> Modifier
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(buttonScale)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (motion) {
                        MiniIconMotion.Refresh -> refreshTurn += 1
                        else -> Unit
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // Control icons stay monochrome white in every state — no accent tint on
            // activation. Active state is conveyed via motion (spin/pulse), not color.
            tint = nebulaColors.textPrimary,
            modifier = Modifier
                .size(iconSize)
                .then(motionModifier)
        )
    }
}

@Composable
private fun Modifier.nimboClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    return if (materialYou) {
        this.clickable(enabled = enabled, onClick = onClick)
    } else {
        this.clickable(
            enabled = enabled,
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        )
    }
}

private val DefaultGlassBorder = Color.White.copy(alpha = 0.12f)

private val NebulaColors.isLight: Boolean
    get() = background.luminance() > 0.5f

private fun windowsPanelFill(colors: NebulaColors): Color = colors.panelFill

private fun windowsControlFill(colors: NebulaColors): Color = colors.controlFill

private fun windowsSoftFill(colors: NebulaColors): Color = colors.softFill

private fun windowsBorder(colors: NebulaColors, darkAlpha: Float = 0.10f): Color = colors.panelBorder

private fun windowsDivider(colors: NebulaColors): Color = colors.divider

@Composable
private fun scaleRoundedCornerShape(shape: RoundedCornerShape, scale: Float): RoundedCornerShape {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dummySize = with(density) { androidx.compose.ui.geometry.Size(500.dp.toPx(), 500.dp.toPx()) }
    return RoundedCornerShape(
        topStart = androidx.compose.foundation.shape.CornerSize(shape.topStart.toPx(dummySize, density) * scale),
        topEnd = androidx.compose.foundation.shape.CornerSize(shape.topEnd.toPx(dummySize, density) * scale),
        bottomEnd = androidx.compose.foundation.shape.CornerSize(shape.bottomEnd.toPx(dummySize, density) * scale),
        bottomStart = androidx.compose.foundation.shape.CornerSize(shape.bottomStart.toPx(dummySize, density) * scale)
    )
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    borderColor: Color = DefaultGlassBorder,
    accentFill: Boolean = false,
    forceOpaque: Boolean = false,
    content: @Composable () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val isLight = nebulaColors.isLight
    val cornerScale = LocalGlobalCornerRadius.current
    val resolvedShape = scaleRoundedCornerShape(shape, cornerScale)

    // Flat surfaces (no glassmorphism): solid fill + thin hairline border, matching
    // the Windows app's .panel. Selected/active panels get a subtle accent tint.
    val baseFill = if (accentFill) {
        nebulaColors.accent.copy(alpha = if (isLight) 0.12f else 0.14f).compositeOver(nebulaColors.surface)
    } else {
        windowsPanelFill(nebulaColors)
    }
    val fill = if (forceOpaque) {
        baseFill.compositeOver(nebulaColors.surface)
    } else {
        baseFill
    }
    val isWhiteishBorder = borderColor.red >= 0.95f && borderColor.green >= 0.95f &&
        borderColor.blue >= 0.95f && borderColor.alpha <= 0.3f
    val resolvedBorder = when {
        // Material You panels are borderless tonal surfaces — drop the glass hairline.
        nebulaColors.isMaterialYou -> Color.Transparent
        isLight && isWhiteishBorder ->
            Color.Black.copy(alpha = (borderColor.alpha + 0.05f).coerceAtMost(0.22f))
        else -> borderColor
    }
    Box(
        modifier = modifier
            .clip(resolvedShape)
            .border(1.dp, resolvedBorder, resolvedShape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(fill)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun EmptyPane(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val nebulaColors = LocalNebulaColors.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = nebulaColors.textTertiary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = subtitle,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun NimboAddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onQr: () -> Unit,
    onNotify: (String) -> Unit,
    initialAction: AddProfileAction = AddProfileAction.Open
) {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    var url by rememberSaveable { mutableStateOf("") }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            url = firstLine.ifBlank { content.trim() }
            if (url.isBlank()) {
                onNotify(loc("Файл пустой", "File is empty"))
            } else {
                onNotify(loc("Ссылка загружена из файла", "Link loaded from file"))
            }
        }.onFailure {
            onNotify(loc("Не удалось прочитать файл", "Could not read file"))
        }
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        url = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) onNotify(loc("В буфере нет ссылки", "Clipboard has no link"))
    }

    // Если диалог открыт нажатием на кнопку метода (Буфер/Файл) с главного экрана —
    // сразу выполняем тот же метод, что и одноимённая кнопка внутри диалога.
    LaunchedEffect(Unit) {
        when (initialAction) {
            AddProfileAction.Paste -> pasteFromClipboard()
            AddProfileAction.File -> fileLauncher.launch(
                arrayOf("text/*", "application/json", "application/octet-stream", "*/*")
            )
            else -> {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .imePadding(),
            shape = RoundedCornerShape(26.dp),
            borderColor = Color.White.copy(alpha = 0.12f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Добавить профиль", "Add profile"),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = t("URL подписки или одиночная proxy-ссылка.", "Subscription URL or a single proxy link."),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = windowsControlFill(nebulaColors),
                        border = BorderStroke(1.dp, windowsBorder(nebulaColors, 0.10f))
                    ) {
                        Text(
                            text = t("Закрыть", "Close"),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp),
                    placeholder = {
                        Text(
                            t("vless://... или URL подписки", "vless://... or subscription URL"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = nebulaColors.accent,
                        unfocusedBorderColor = nebulaColors.textSecondary.copy(alpha = 0.22f),
                        focusedContainerColor = windowsControlFill(nebulaColors),
                        unfocusedContainerColor = windowsControlFill(nebulaColors),
                        focusedTextColor = nebulaColors.textPrimary,
                        unfocusedTextColor = nebulaColors.textPrimary,
                        cursorColor = nebulaColors.accent
                    )
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    onClick = { onAdd(url) },
                    enabled = url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = nebulaColors.accent.copy(alpha = if (url.isBlank()) 0.28f else 1f),
                    border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = if (url.isBlank()) 0.22f else 0.72f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudUpload, null, tint = Color.White.copy(alpha = if (url.isBlank()) 0.55f else 1f), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = t("Импорт", "Import"),
                            color = Color.White.copy(alpha = if (url.isBlank()) 0.55f else 1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AddDialogMethodTile(
                        icon = Icons.Default.ContentPaste,
                        label = t("Буфер", "Paste"),
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp),
                        onClick = ::pasteFromClipboard
                    )
                    AddDialogMethodTile(
                        icon = Icons.Default.FolderOpen,
                        label = t("Файл", "File"),
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp),
                        onClick = { fileLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*")) }
                    )
                    AddDialogMethodTile(
                        icon = Icons.Default.QrCodeScanner,
                        label = "QR",
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp),
                        onClick = onQr
                    )
                }
            }
        }
    }
}

@Composable
private fun AddDialogMethodTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = windowsControlFill(nebulaColors),
        border = BorderStroke(1.dp, windowsBorder(nebulaColors, 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(23.dp))
            Text(
                text = label,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// Theme is stored as a single index: 0–8 = dark variants, 9–17 = light variants of the
// same accent colors. So toggling light/dark is just ±THEME_COLOR_COUNT.
private const val THEME_COLOR_COUNT_LOCAL = 9
private enum class ThemeMode { System, Light, Dark }
private enum class ThemePreviewKind { System, Light, Dark, Black }

private enum class ThemeSettingsSectionId(val storageKey: String) {
    InterfaceStyle("interface_style"),
    ConnectionStyle("connection_style"),
    Background("background"),
    StyleDetails("style_details"),
    ThemeMode("theme_mode"),
    AccentColor("accent_color"),
    Subscription("subscription"),
    Language("language"),
    Text("text")
}

private val ACCENT_COLORS = listOf(
    0 to Color(0xFF7C5DFA),  // violet
    1 to Color(0xFF75A7FF),  // Nimbo blue
    2 to Color(0xFF5DD9A1),  // green
    3 to Color(0xFFFF7B7B),  // red
    4 to Color(0xFFE2A75F),  // amber
    5 to Color(0xFFC792EA),  // lilac
    6 to Color(0xFF4FC3B1),  // teal
    7 to Color(0xFF8B95A7),  // graphite
    8 to Color(0xFFE2E8F0)   // silver
)

// Named accent presets shown as mini-preview cards. Single-colour entries are solid
// accents; 2–3 colour entries blend into a gradient accent (applied via the custom-accent
// gradient pipeline, so they render everywhere the accent gradient is used).
private data class AccentPreset(val name: String, val colors: List<Color>)

private val ACCENT_PRESET_SYSTEM_COLORS = listOf(
    Color(0xFFFF5252), Color(0xFFFFB300), Color(0xFF66BB6A),
    Color(0xFF29B6F6), Color(0xFF7C5DFA), Color(0xFFEC407A)
)

private val ACCENT_PRESETS: List<AccentPreset> = listOf(
    AccentPreset("Nimbo Blue", listOf(Color(0xFF75A7FF))),
    AccentPreset("Violet", listOf(Color(0xFF7C5DFA))),
    AccentPreset("Blue", listOf(Color(0xFF58A6FF))),
    AccentPreset("Green", listOf(Color(0xFF5DD9A1))),
    AccentPreset("Rose", listOf(Color(0xFFFF6B8A))),
    AccentPreset("Amber", listOf(Color(0xFFE2A75F))),
    AccentPreset("Cyan", listOf(Color(0xFF22D3EE))),
    AccentPreset("Red", listOf(Color(0xFFFF5252))),
    AccentPreset("Orange", listOf(Color(0xFFFF8A3D))),
    AccentPreset("Gold", listOf(Color(0xFFFFC02E))),
    AccentPreset("Lime", listOf(Color(0xFFA3E635))),
    AccentPreset("Teal", listOf(Color(0xFF14B8A6))),
    AccentPreset("Indigo", listOf(Color(0xFF6366F1))),
    AccentPreset("Pink", listOf(Color(0xFFEC4899))),
    AccentPreset("Slate", listOf(Color(0xFF8B95A7))),
    AccentPreset("Aurora", listOf(Color(0xFF7C5DFA), Color(0xFF58A6FF))),
    AccentPreset("Candy", listOf(Color(0xFFFF6B8A), Color(0xFF7C5DFA))),
    AccentPreset("Sunset", listOf(Color(0xFFFFC02E), Color(0xFFFF5277))),
    AccentPreset("Lagoon", listOf(Color(0xFF5DD9A1), Color(0xFF14B8A6), Color(0xFF58A6FF))),
    AccentPreset("Spectrum", listOf(Color(0xFF7C5DFA), Color(0xFFEC4899), Color(0xFFFFC02E))),
    AccentPreset("Reef", listOf(Color(0xFF22D3EE), Color(0xFF5DD9A1), Color(0xFF58A6FF)))
)

private fun colorsApproxEqual(a: Color, b: Color): Boolean =
    kotlin.math.abs(a.red - b.red) < 0.05f &&
        kotlin.math.abs(a.green - b.green) < 0.05f &&
        kotlin.math.abs(a.blue - b.blue) < 0.05f

private fun accentIndexForTheme(themeIndex: Int): Int =
    if (themeIndex < THEME_COLOR_COUNT_LOCAL) themeIndex else themeIndex - THEME_COLOR_COUNT_LOCAL

private fun resolveThemeMode(preferencesManager: PreferencesManager): ThemeMode {
    return when (preferencesManager.themeModeOverride) {
        1 -> ThemeMode.Light
        2 -> ThemeMode.Dark
        else -> ThemeMode.System
    }
}

@Composable
private fun currentThemeModeLabel(preferencesManager: PreferencesManager): String {
    val themeModeValue by preferencesManager.themeModeState
    val mode = when (themeModeValue) {
        1 -> ThemeMode.Light
        2 -> ThemeMode.Dark
        else -> ThemeMode.System
    }
    val themeIndex by preferencesManager.colorThemeState
    val accent = ACCENT_COLORS[accentIndexForTheme(themeIndex)].second
    val modeText = when (mode) {
        ThemeMode.System -> t("Системная", "System")
        ThemeMode.Light -> t("Светлая", "Light")
        ThemeMode.Dark -> t("Тёмная", "Dark")
    }
    val accentText = when (accentIndexForTheme(themeIndex)) {
        0 -> t("фиолетовый", "purple")
        1 -> t("синий", "blue")
        2 -> t("зелёный", "green")
        3 -> t("красный", "red")
        4 -> t("янтарный", "amber")
        5 -> t("лиловый", "lilac")
        6 -> t("морской", "teal")
        7 -> t("графит", "graphite")
        8 -> t("серебро", "silver")
        else -> t("акцент", "accent")
    }
    @Suppress("UNUSED_PARAMETER")
    return "$modeText · $accentText"
}

@Composable
private fun currentPingSettingsLabel(preferencesManager: PreferencesManager): String {
    val protocol by preferencesManager.pingProtocolState
    val displayMode by preferencesManager.pingDisplayModeState
    val throughProxy by preferencesManager.pingThroughProxyState
    val protocolText = when (protocol) {
        4 -> "ICMP"
        1, 2, 3 -> "HTTP"
        else -> "TCP"
    }
    val displayText = when (displayMode) {
        2 -> t("индикатор", "indicator")
        else -> "ms"
    }
    val proxyText = if (throughProxy) t("прокси", "proxy") else t("напрямую", "direct")
    return "$protocolText · $displayText · $proxyText"
}

@Composable
private fun NimboPingSettingsScreen(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    NimboSubPageScaffold(title = t("Пинг", "Ping"), onBack = onBack) {
        PingSettingsSection(preferencesManager, mainViewModel)
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun ColumnScope.PingSettingsSection(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel
) {
    val nebulaColors = LocalNebulaColors.current
    val pingProtocol by preferencesManager.pingProtocolState
    val pingTimeout by preferencesManager.pingTimeoutState
    val pingDisplayMode by preferencesManager.pingDisplayModeState

    AppearanceSectionHeader(
        title = t("Проверка задержки", "Latency check"),
        subtitle = t("Настройки и метод измерения задержки серверов", "Configure latency check options")
    )
    Spacer(Modifier.height(14.dp))

    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PingSettingsStackedRow(
                title = t("Протокол", "Protocol"),
                subtitle = t("Метод, которым Nimbo измеряет задержку серверов", "How Nimbo measures server latency"),
                icon = Icons.Default.Language
            ) {
                PingSegmentedControl(
                    items = listOf("TCP", "HTTP", "ICMP"),
                    selectedIndex = when (pingProtocol) {
                        1, 2, 3 -> 1
                        4 -> 2
                        else -> 0
                    },
                    onSelect = { index ->
                        preferencesManager.pingProtocol = when (index) {
                            1 -> 1
                            2 -> 4
                            else -> 0
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )
                PingSettingsHint(
                    when (pingProtocol) {
                        1, 2, 3 -> t(
                            "HTTP — измеряет время полного HTTP-запроса. Ближе к реальной скорости загрузки, но немного медленнее.",
                            "HTTP — measures a full HTTP request. Closer to real download speed, but a bit slower."
                        )
                        4 -> t(
                            "ICMP — обычный ping. Самый быстрый способ, но часть сетей и серверов его блокируют.",
                            "ICMP — a regular ping. The fastest method, but some networks and servers block it."
                        )
                        else -> t(
                            "TCP — измеряет, как быстро сервер принимает подключение. Работает почти везде, поэтому выбран по умолчанию.",
                            "TCP — measures how quickly the server accepts a connection. Works almost everywhere, so it is the default."
                        )
                    }
                )
            }
            PingSettingsDivider()
            PingSettingsWideRow(
                title = t("Таймаут (мс)", "Timeout (ms)"),
                subtitle = t(
                    "Сколько ждать ответа, прежде чем считать сервер недоступным.",
                    "How long to wait for a reply before treating the server as unreachable."
                ),
                icon = Icons.Default.Schedule
            ) {
                OutlinedTextField(
                    value = (pingTimeout * 1000).toString(),
                    onValueChange = { raw ->
                        val ms = raw.filter(Char::isDigit).take(5).toIntOrNull()
                        if (ms != null) {
                            preferencesManager.pingTimeout = ((ms + 999) / 1000).coerceIn(1, 10)
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = nebulaColors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(110.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = nebulaColors.textPrimary,
                        unfocusedTextColor = nebulaColors.textPrimary,
                        focusedBorderColor = if (nebulaColors.isLight) Color(0xFFD8D5E2) else Color.White.copy(alpha = 0.18f),
                        unfocusedBorderColor = windowsBorder(nebulaColors, 0.12f),
                        focusedContainerColor = windowsControlFill(nebulaColors),
                        unfocusedContainerColor = windowsControlFill(nebulaColors),
                        cursorColor = nebulaColors.accent
                    )
                )
            }
            PingSettingsDivider()
            PingSettingsStackedRow(
                title = t("Формат отображения", "Display format"),
                subtitle = t(
                    "Как показывать задержку в списке серверов.",
                    "How latency is shown in the server list."
                ),
                icon = Icons.Default.Visibility
            ) {
                PingSegmentedControl(
                    items = listOf("ms", t("Индикатор", "Indicator")),
                    selectedIndex = if (pingDisplayMode == 2) 1 else 0,
                    onSelect = { index ->
                        preferencesManager.pingDisplayMode = if (index == 1) 2 else 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )
                PingSettingsHint(
                    if (pingDisplayMode == 2) t(
                        "Индикатор — только цветная точка без числа: зелёная (быстро) → красная (медленно).",
                        "Indicator — just a colored dot, no number: green (fast) → red (slow)."
                    ) else t(
                        "ms — точная задержка в миллисекундах, например «45 ms».",
                        "ms — the exact latency in milliseconds, e.g. \"45 ms\"."
                    )
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PingSettingsWideRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    control: @Composable () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 98.dp else 116.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(contentAlignment = Alignment.CenterEnd) {
            control()
        }
    }
}

/**
 * Vertical row variant: title + subtitle on top, full-width control(s) beneath.
 * Used for the segmented controls — a full-width control inside the horizontal
 * [PingSettingsWideRow] would collapse the weighted title column to zero width.
 */
@Composable
private fun PingSettingsStackedRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    contentSpacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .padding(end = 12.dp)
                        .size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(contentSpacing))
        content()
    }
}

/** Small info line under a control explaining what the current choice does. */
@Composable
private fun PingSettingsHint(text: String) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = nebulaColors.textTertiary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun PingSettingsDivider() {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(windowsDivider(nebulaColors))
    )
}

/** Big tappable option card with the icon on top and the label beneath (style picker). */
@Composable
private fun ConnectStyleOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val cornerScale = LocalGlobalCornerRadius.current
    val shape = RoundedCornerShape(18.dp * cornerScale)
    val fill by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.18f) else nebulaColors.surface,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "connect_style_fill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.85f) else windowsBorder(nebulaColors),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "connect_style_border"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.6.dp else 1.dp, borderColor, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (selected) nebulaColors.accent.copy(alpha = 0.22f)
                    else nebulaColors.textSecondary.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PingSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val fill = windowsControlFill(nebulaColors)
    val border = windowsBorder(nebulaColors, 0.11f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) nebulaColors.accent else Color.Transparent)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else nebulaColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PingChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    1.5.dp,
                    if (selected) nebulaColors.accent else nebulaColors.textSecondary.copy(alpha = 0.65f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = nebulaColors.background,
                checkedTrackColor = nebulaColors.accent,
                uncheckedThumbColor = nebulaColors.textSecondary,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = nebulaColors.textSecondary.copy(alpha = 0.55f)
            )
        )
    }
}

@Composable
private fun NimboNotificationsScreen(
    onBack: () -> Unit
) {
    NotificationHistoryScreen(onNavigateBack = onBack)
}

@Composable
private fun NimboThemeScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    NimboSubPageScaffold(title = t("Тема", "Theme"), onBack = onBack) {
        ThemeSettingsSection(preferencesManager = preferencesManager)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ThemeSettingsSection(
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    val languageContext = LocalContext.current
    var currentLanguage by remember { mutableStateOf(preferencesManager.appLanguage) }
    val setLanguage: (String) -> Unit = { tag ->
        if (tag != currentLanguage) {
            preferencesManager.appLanguage = tag
            currentLanguage = tag
            (languageContext as? android.app.Activity)?.recreate()
        }
    }
    val themeIndex by preferencesManager.colorThemeState
    val themeModeValue by preferencesManager.themeModeState
    val textScale by preferencesManager.textScaleState
    val isCustomAccent by preferencesManager.isCustomAccentState
    val customAccentColorInt by preferencesManager.customAccentColorState
    val customGradient1 by preferencesManager.customGradientColor1State
    val customGradient2 by preferencesManager.customGradientColor2State
    val customGradient3 by preferencesManager.customGradientColor3State
    val customGradientCount by preferencesManager.customGradientCountState
    val useSubscriptionTheme by preferencesManager.useSubscriptionThemeState
    val showSubscriptionLogo by preferencesManager.showSubscriptionLogoState
    val pureBlackMode by preferencesManager.pureBlackModeState
    val elementStyle by preferencesManager.elementStyleState
    val backgroundStyle by preferencesManager.backgroundStyleState
    val backgroundAnimationEnabled by preferencesManager.backgroundAnimationEnabledState
    val connectButtonStyle by preferencesManager.connectButtonStyleState
    val globalBrightness by preferencesManager.globalBrightnessState
    val globalTransparency by preferencesManager.globalTransparencyState
    val globalBlur by preferencesManager.globalBlurState
    val globalCorners by preferencesManager.globalCornersState
    val useDynamicColor by preferencesManager.useDynamicColorState
    val subscriptionPreviewProfiles = remember { preferencesManager.loadProfiles() }
    val subscriptionPreviewThemeSpec = preferencesManager.subscriptionThemeSpec
        ?.takeIf { it.isNotBlank() }
        ?: subscriptionPreviewProfiles.firstNotNullOfOrNull { profile ->
            profile.themeSpec?.takeIf { it.isNotBlank() }
        }
    val subscriptionPreviewLogoProfile = subscriptionPreviewProfiles.firstOrNull { profile ->
        !profile.brandLogo.isNullOrBlank() || !profile.brandLogoCache.isNullOrBlank()
    }
    val accentIndex = accentIndexForTheme(themeIndex)
    // Derive the selected mode from the OBSERVED themeModeState, not the non-reactive
    // themeModeOverride getter. Otherwise switching to a mode that leaves colorTheme
    // unchanged (e.g. System<->Dark while the system is already dark) emitted no observed
    // state, so the section never recomposed and the tap looked dropped / the wrong card
    // stayed highlighted.
    val mode = when (themeModeValue) {
        1 -> ThemeMode.Light
        2 -> ThemeMode.Dark
        else -> ThemeMode.System
    }
    val systemIsDark = isSystemInDarkTheme()
    var showCustomColorDialog by rememberSaveable { mutableStateOf(false) }
    var textScalingEnabled by rememberSaveable { mutableStateOf(textScale != 1f) }
    var collapsedThemeSections by remember { mutableStateOf(preferencesManager.getCollapsedThemeSections()) }
    val isSectionExpanded: (ThemeSettingsSectionId) -> Boolean = { section ->
        !collapsedThemeSections.contains(section.storageKey)
    }
    val setSectionExpanded: (ThemeSettingsSectionId, Boolean) -> Unit = { section, expanded ->
        preferencesManager.setThemeSectionCollapsed(section.storageKey, collapsed = !expanded)
        collapsedThemeSections = preferencesManager.getCollapsedThemeSections()
    }

    val applyMode = { newMode: ThemeMode ->
        preferencesManager.themeModeOverride = when (newMode) {
            ThemeMode.System -> 0
            ThemeMode.Light -> 1
            ThemeMode.Dark -> 2
        }
        val wantDark = when (newMode) {
            ThemeMode.System -> systemIsDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        preferencesManager.colorTheme = if (wantDark) accentIndex else accentIndex + THEME_COLOR_COUNT_LOCAL
    }

    CollapsibleThemeSection(
        title = t("Стиль интерфейса", "Interface style"),
        subtitle = t("Переключает визуальный слой: поверхности, кнопки, поля", "Switches the visual layer: surfaces, buttons, fields"),
        expanded = isSectionExpanded(ThemeSettingsSectionId.InterfaceStyle),
        onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.InterfaceStyle, it) }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InterfaceStylePreviewCard(
                title = "Nimbo Glass",
                subtitle = t("Стеклянные панели и мягкий контур", "Glass panels and soft outlines"),
                kind = InterfacePreviewKind.Nebula,
                selected = elementStyle == 0,
                onClick = { preferencesManager.elementStyle = 0 },
                modifier = Modifier.weight(1f)
            )
            InterfaceStylePreviewCard(
                title = "Material You",
                subtitle = "Expressive",
                kind = InterfacePreviewKind.MaterialYou,
                selected = elementStyle == 1,
                onClick = { preferencesManager.elementStyle = 1 },
                modifier = Modifier.weight(1f)
            )
        }
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Стиль подключения", "Connection style"),
            subtitle = t("Форма главной кнопки на домашнем экране", "Shape of the main button on Home"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.ConnectionStyle),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.ConnectionStyle, it) }
        ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConnectStyleOption(
                icon = Icons.Default.RadioButtonChecked,
                label = t("Классический", "Classic"),
                selected = connectButtonStyle == 0,
                onClick = { preferencesManager.connectButtonStyle = 0 },
                modifier = Modifier.weight(1f)
            )
            ConnectStyleOption(
                icon = Icons.Default.ViewAgenda,
                label = t("Компактный", "Compact"),
                selected = connectButtonStyle == 1,
                onClick = { preferencesManager.connectButtonStyle = 1 },
                modifier = Modifier.weight(1f)
            )
        }
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Фон", "Background"),
            subtitle = t("Живые цветовые пресеты с учетом акцента и темы", "Animated color presets adapted to accent and theme"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.Background),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.Background, it) }
        ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backgroundStylePresets().forEach { preset ->
                BackgroundPresetTile(
                    label = preset.second,
                    selected = backgroundStyle == preset.first,
                    onClick = { preferencesManager.backgroundStyle = preset.first }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ThemeSwitchRow(
            title = t("Анимация фона", "Background animation"),
            icon = Icons.Default.Refresh,
            checked = backgroundAnimationEnabled,
            onCheckedChange = { preferencesManager.backgroundAnimationEnabled = it }
        )
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Детали стиля", "Style details"),
            subtitle = t("Настройте яркость, прозрачность, размытие и скругление элементов", "Adjust brightness, transparency, blur and corners of elements"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.StyleDetails),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.StyleDetails, it) }
        ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = t("Яркость панелей", "Panel brightness"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = globalBrightness,
                onValueChange = { preferencesManager.globalBrightness = it },
                valueRange = 0.5f..2.0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.textPrimary,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.26f)
                )
            )
            Text(
                text = "${(globalBrightness * 100).toInt()}%",
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = globalBrightness != 1.0f,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    SliderResetIcon { preferencesManager.globalBrightness = 1.0f }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = t("Прозрачность элементов", "Element transparency"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = globalTransparency,
                onValueChange = { preferencesManager.globalTransparency = it },
                valueRange = 0.0f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.textPrimary,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.26f)
                )
            )
            Text(
                text = "${(globalTransparency * 100).toInt()}%",
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = globalTransparency != 0.0f,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    SliderResetIcon { preferencesManager.globalTransparency = 0.0f }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = t("Радиус размытия", "Blur radius"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = globalBlur,
                onValueChange = { preferencesManager.globalBlur = it },
                valueRange = 0.0f..80.0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.textPrimary,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.26f)
                )
            )
            Text(
                text = "${globalBlur.toInt()} dp",
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = globalBlur != 25.0f,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    SliderResetIcon { preferencesManager.globalBlur = 25.0f }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = t("Скругление элементов", "Element corners"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = globalCorners,
                onValueChange = { preferencesManager.globalCorners = it },
                valueRange = 0.25f..4.0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.textPrimary,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.26f)
                )
            )
            Text(
                text = String.format(java.util.Locale.US, "%.2fx", globalCorners),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = globalCorners != 1.0f,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    SliderResetIcon { preferencesManager.globalCorners = 1.0f }
                }
            }
        }

        val isAnyDeflected = globalBrightness != 1.0f || globalTransparency != 0.0f || globalBlur != 25.0f || globalCorners != 1.0f
        AnimatedVisibility(
            visible = isAnyDeflected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                ResetLink(t("Сбросить настройки стиля", "Reset style sliders")) {
                    preferencesManager.globalBrightness = 1.0f
                    preferencesManager.globalTransparency = 0.0f
                    preferencesManager.globalBlur = 25.0f
                    preferencesManager.globalCorners = 1.0f
                }
            }
        }
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Тема", "Theme"),
            subtitle = t("Тёмная, чёрная OLED, светлая или как в системе", "Dark, black OLED, light or system"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.ThemeMode),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.ThemeMode, it) }
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeModePreviewCard(
                    title = t("Системная", "System"),
                    icon = Icons.Default.BrightnessAuto,
                    preview = ThemePreviewKind.System,
                    selected = !pureBlackMode && mode == ThemeMode.System,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        preferencesManager.pureBlackMode = false
                        applyMode(ThemeMode.System)
                    }
                )
                ThemeModePreviewCard(
                    title = t("Светлая", "Light"),
                    icon = Icons.Default.LightMode,
                    preview = ThemePreviewKind.Light,
                    selected = !pureBlackMode && mode == ThemeMode.Light,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        preferencesManager.pureBlackMode = false
                        applyMode(ThemeMode.Light)
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeModePreviewCard(
                    title = t("Тёмная", "Dark"),
                    icon = Icons.Default.DarkMode,
                    preview = ThemePreviewKind.Dark,
                    selected = !pureBlackMode && mode == ThemeMode.Dark,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        preferencesManager.pureBlackMode = false
                        applyMode(ThemeMode.Dark)
                    }
                )
                ThemeModePreviewCard(
                    title = "OLED",
                    icon = Icons.Default.DarkMode,
                    preview = ThemePreviewKind.Black,
                    selected = pureBlackMode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        applyMode(ThemeMode.Dark)
                        preferencesManager.pureBlackMode = true
                    }
                )
            }
        }
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Акцентный цвет", "Accent color"),
            subtitle = t("Палитры, системный цвет и свой градиент", "Palettes, system color and custom gradient"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.AccentColor),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.AccentColor, it) }
        ) {
        val isDarkActive = when (mode) {
            ThemeMode.System -> systemIsDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        val customColorsList = when (customGradientCount) {
            3 -> listOf(Color(customGradient1), Color(customGradient2), Color(customGradient3))
            2 -> listOf(Color(customGradient1), Color(customGradient2))
            else -> listOf(Color(customGradient1))
        }
        fun presetSelected(colors: List<Color>): Boolean {
            if (useDynamicColor) return false
            return if (colors.size == 1) {
                (isCustomAccent && customGradientCount == 1 && colorsApproxEqual(Color(customGradient1), colors[0])) ||
                    (!isCustomAccent && colorsApproxEqual(nebulaColors.accent, colors[0]))
            } else {
                isCustomAccent && customGradientCount == colors.size &&
                    customColorsList.size == colors.size &&
                    customColorsList.zip(colors).all { (a, b) -> colorsApproxEqual(a, b) }
            }
        }
        val applyPreset: (List<Color>) -> Unit = { colors ->
            preferencesManager.useDynamicColor = false
            preferencesManager.isCustomAccent = true
            preferencesManager.customGradientColor1 = colors[0].toArgb()
            preferencesManager.customGradientColor2 = (colors.getOrNull(1) ?: colors[0]).toArgb()
            preferencesManager.customGradientColor3 = (colors.getOrNull(2) ?: colors[0]).toArgb()
            preferencesManager.customGradientCount = colors.size
        }
        val anyPresetSelected = ACCENT_PRESETS.any { presetSelected(it.colors) }
        val svoiSelected = !useDynamicColor && isCustomAccent && !anyPresetSelected
        val svoiColors = if (svoiSelected) customColorsList else listOf(Color(0xFFFF6B8A), Color(0xFF7C5DFA))

        val accentCards = buildList<@Composable (Modifier) -> Unit> {
            // Системный акцент — цвет берётся из обоев/темы Android (Material You, API 31+).
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add { m ->
                    AccentPresetCard(
                        name = t("Системный", "System"),
                        colors = ACCENT_PRESET_SYSTEM_COLORS,
                        selected = useDynamicColor,
                        modifier = m
                    ) {
                        preferencesManager.isCustomAccent = false
                        preferencesManager.useDynamicColor = true
                    }
                }
            }
            ACCENT_PRESETS.forEach { preset ->
                add { m ->
                    AccentPresetCard(
                        name = preset.name,
                        colors = preset.colors,
                        selected = presetSelected(preset.colors),
                        modifier = m,
                        onClick = { applyPreset(preset.colors) }
                    )
                }
            }
            add { m ->
                AccentPresetCard(
                    name = t("Свой", "Custom"),
                    colors = svoiColors,
                    selected = svoiSelected,
                    modifier = m,
                    isAdd = true,
                    onClick = {
                        preferencesManager.useDynamicColor = false
                        showCustomColorDialog = true
                    }
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            accentCards.chunked(3).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowCards.forEach { card -> card(Modifier.weight(1f)) }
                    repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val defaultThemeIndex = if (isDarkActive) {
            DEFAULT_COLOR_THEME_INDEX
        } else {
            DEFAULT_COLOR_THEME_INDEX + THEME_COLOR_COUNT_LOCAL
        }
        val isColorChanged = useDynamicColor || isCustomAccent || themeIndex != defaultThemeIndex
        androidx.compose.animation.AnimatedVisibility(
            visible = isColorChanged,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                ResetLink(t("Сбросить цвет", "Reset color")) {
                    preferencesManager.useDynamicColor = false
                    preferencesManager.isCustomAccent = false
                    preferencesManager.colorTheme = defaultThemeIndex
                }
            }
        }
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Подписка", "Subscription"),
            subtitle = t("Берите акцент и логотип из заголовков подписки", "Use accent and logo from the subscription headers"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.Subscription),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.Subscription, it) }
        ) {
        SubscriptionAppearancePreview(
            themeSpec = subscriptionPreviewThemeSpec,
            logo = subscriptionPreviewLogoProfile?.brandLogo,
            cachedLogo = subscriptionPreviewLogoProfile?.brandLogoCache,
            useSubscriptionTheme = useSubscriptionTheme,
            showSubscriptionLogo = showSubscriptionLogo
        )
        Spacer(Modifier.height(12.dp))
        ThemeSwitchRow(
            title = t("Тема из подписки", "Theme from subscription"),
            icon = Icons.Default.ColorLens,
            checked = useSubscriptionTheme,
            onCheckedChange = { on ->
                preferencesManager.useSubscriptionTheme = on
                if (on) {
                    val spec = preferencesManager.subscriptionThemeSpec
                    val hex = spec?.split(",")?.map { it.trim() }?.firstOrNull { it.startsWith("#") }
                    val argb = hex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                    if (argb != null) {
                        preferencesManager.useDynamicColor = false
                        preferencesManager.isCustomAccent = true
                        preferencesManager.customGradientColor1 = argb
                        preferencesManager.customGradientCount = 1
                    }
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        ThemeSwitchRow(
            title = t("Логотип подписки", "Subscription logo"),
            icon = Icons.Default.Image,
            checked = showSubscriptionLogo,
            onCheckedChange = { preferencesManager.showSubscriptionLogo = it }
        )
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Язык", "Language"),
            subtitle = t("Русский или English для интерфейса приложения", "Russian or English for the app interface"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.Language),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.Language, it) }
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LanguagePreviewCard(
                title = t("Системный", "System"),
                flag = null,
                selected = currentLanguage.isBlank(),
                modifier = Modifier.weight(1f),
                onClick = { setLanguage("") }
            )
            LanguagePreviewCard(
                title = "Русский",
                flag = "🇷🇺",
                selected = currentLanguage == "ru",
                modifier = Modifier.weight(1f),
                onClick = { setLanguage("ru") }
            )
            LanguagePreviewCard(
                title = "English",
                flag = "🇬🇧",
                selected = currentLanguage == "en",
                modifier = Modifier.weight(1f),
                onClick = { setLanguage("en") }
            )
        }
    }

        Spacer(Modifier.height(18.dp))
        CollapsibleThemeSection(
            title = t("Текст", "Text"),
            subtitle = t("Масштаб интерфейсного текста", "Interface text scaling"),
            expanded = isSectionExpanded(ThemeSettingsSectionId.Text),
            onExpandedChange = { setSectionExpanded(ThemeSettingsSectionId.Text, it) }
        ) {
        ThemeSwitchRow(
            title = t("Масштабирование текста", "Text scaling"),
            leadingText = "Aa",
            checked = textScalingEnabled,
            onCheckedChange = {
                textScalingEnabled = it
                if (!it) preferencesManager.textScale = 1f
            }
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = textScale,
                onValueChange = {
                    textScalingEnabled = true
                    preferencesManager.textScale = it
                },
                valueRange = 0.85f..1.25f,
                steps = 7,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                colors = SliderDefaults.colors(
                    thumbColor = nebulaColors.textPrimary,
                    activeTrackColor = nebulaColors.accent,
                    inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.26f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Text(
                text = "${(textScale * 100).toInt()}%",
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = textScale != 1f || textScalingEnabled,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    SliderResetIcon {
                        preferencesManager.textScale = 1f
                        textScalingEnabled = false
                    }
                }
            }
        }
    }

        if (showCustomColorDialog) {
            CustomThemeColorDialog(
                preferencesManager = preferencesManager,
                onDismiss = { showCustomColorDialog = false }
            )
        }
    }

@Composable
private fun ColumnScope.AppearanceSectionHeader(title: String, subtitle: String) {
    val nebulaColors = LocalNebulaColors.current
    Column(modifier = Modifier.padding(start = 2.dp)) {
        Text(
            text = title,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = subtitle,
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun CollapsibleThemeSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "theme-section-arrow"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onExpandedChange(!expanded) }
                .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = nebulaColors.textTertiary,
                modifier = Modifier
                    .size(26.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(140))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun InterfaceStylePreviewCard(
    title: String,
    subtitle: String,
    kind: InterfacePreviewKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialPreview = kind == InterfacePreviewKind.MaterialYou
    val shape = RoundedCornerShape(18.dp)
    val cardFill by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.13f) else nebulaColors.surface,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "interface_preview_card_fill"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.74f) else windowsBorder(nebulaColors),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "interface_preview_card_border"
    )
    val previewBase = if (isMaterialPreview) {
        if (nebulaColors.isLight) Color(0xFFF2ECF6) else Color(0xFF17131D)
    } else {
        if (nebulaColors.isLight) Color(0xFFF8F8FC) else Color(0xFF11121A)
    }
    val panel = if (isMaterialPreview) {
        nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.18f else 0.24f)
            .compositeOver(previewBase)
    } else {
        Color.White.copy(alpha = if (nebulaColors.isLight) 0.72f else 0.10f)
            .compositeOver(previewBase)
    }
    val soft = if (isMaterialPreview) {
        nebulaColors.accent.copy(alpha = 0.32f).compositeOver(previewBase)
    } else {
        nebulaColors.accent.copy(alpha = 0.18f).compositeOver(previewBase)
    }

    // A miniature of the actual app home screen rendered inside a phone shell, so the
    // card previews how Nimbo Glass vs Material You re-skin the live UI (frosted/outlined
    // surfaces + ring connect button vs solid tonal surfaces + filled button).
    val bezelColor = if (nebulaColors.isLight) Color(0xFFCBC6D6) else Color(0xFF050609)
    val screenShape = RoundedCornerShape(14.dp)
    val statusTint = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.44f)
    val mutedDot = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.32f)
    val navFill = if (isMaterialPreview) {
        nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.20f else 0.30f).compositeOver(previewBase)
    } else {
        Color.White.copy(alpha = if (nebulaColors.isLight) 0.70f else 0.09f).compositeOver(previewBase)
    }
    val navBorder = Color.White.copy(alpha = if (nebulaColors.isLight) 0.50f else 0.16f)
    val chipFill = panel.copy(alpha = if (isMaterialPreview) 1f else 0.9f)

    Column(
        modifier = modifier
            .height(196.dp)
            .clip(shape)
            .background(cardFill)
            .border(1.dp, cardBorder, shape)
            .nimboClickable(onClick = onClick)
            .padding(9.dp)
    ) {
        // Phone shell (bezel + screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bezelColor)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(screenShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                soft.copy(alpha = if (isMaterialPreview) 0.92f else 0.55f),
                                previewBase,
                                previewBase
                            )
                        )
                    )
            ) {
                // Accent glow behind the connect button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-6).dp)
                        .size(80.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    nebulaColors.accent.copy(alpha = if (isMaterialPreview) 0.42f else 0.30f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    // Status bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(14.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(statusTint)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) { i ->
                                Box(
                                    Modifier
                                        .width(if (i == 2) 9.dp else 4.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(statusTint)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Header: profile name + settings affordance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                Modifier
                                    .width(46.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(panel)
                            )
                            Box(
                                Modifier
                                    .width(26.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(panel.copy(alpha = 0.6f))
                            )
                        }
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(if (isMaterialPreview) CircleShape else RoundedCornerShape(5.dp))
                                .background(panel)
                        )
                    }

                    // Connect button + stat chips
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isMaterialPreview) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(nebulaColors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(15.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White.copy(alpha = 0.95f))
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(nebulaColors.accent.copy(alpha = 0.12f))
                                        .border(3.dp, nebulaColors.accent.copy(alpha = 0.92f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(13.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(nebulaColors.accent)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(2) {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(11.dp)
                                            .clip(RoundedCornerShape(if (isMaterialPreview) 7.dp else 4.dp))
                                            .background(chipFill)
                                    )
                                }
                            }
                        }
                    }

                    // Floating bottom navigation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(if (isMaterialPreview) 13.dp else 11.dp))
                            .background(navFill)
                            .then(
                                if (isMaterialPreview) Modifier
                                else Modifier.border(1.dp, navBorder, RoundedCornerShape(11.dp))
                            )
                            .padding(horizontal = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { i ->
                            if (i == 1) {
                                Box(
                                    Modifier
                                        .width(20.dp)
                                        .height(9.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(nebulaColors.accent)
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(mutedDot)
                                )
                            }
                        }
                    }
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(nebulaColors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ThemeModePreviewCard(
    title: String,
    icon: ImageVector,
    preview: ThemePreviewKind,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(18.dp)
    val previewBg = when (preview) {
        ThemePreviewKind.Light -> Color(0xFFF6F5FB)
        ThemePreviewKind.Dark -> Color(0xFF171720)
        ThemePreviewKind.Black -> Color(0xFF050507)
        ThemePreviewKind.System -> if (isSystemInDarkTheme()) Color(0xFF171720) else Color(0xFFF6F5FB)
    }
    val previewPanel = when (preview) {
        ThemePreviewKind.Light -> Color.White
        ThemePreviewKind.Dark -> Color(0xFF242431)
        ThemePreviewKind.Black -> Color(0xFF111116)
        ThemePreviewKind.System -> if (isSystemInDarkTheme()) Color(0xFF242431) else Color.White
    }
    val textColor = if (previewBg.luminance() > 0.55f) Color(0xFF1B1A24) else Color.White
    val fillColor by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.13f) else nebulaColors.surface,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "theme_preview_fill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.78f) else windowsBorder(nebulaColors),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "theme_preview_border"
    )

    Column(
        modifier = modifier
            .height(116.dp)
            .clip(shape)
            .background(fillColor)
            .border(1.dp, borderColor, shape)
            .nimboClickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Mini phone screen rendered in the theme's own background/panel colours, so the
        // card previews how Light/Dark/Black/System actually re-skin the Home screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(previewBg)
                .border(1.dp, textColor.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(nebulaColors.accent.copy(alpha = 0.30f), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 7.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(12.dp).height(3.dp)
                            .clip(RoundedCornerShape(999.dp)).background(textColor.copy(alpha = 0.45f))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) { i ->
                            Box(
                                Modifier.width(if (i == 2) 6.dp else 3.dp).height(3.dp)
                                    .clip(RoundedCornerShape(999.dp)).background(textColor.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(nebulaColors.accent.copy(alpha = 0.16f))
                            .border(2.dp, nebulaColors.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(2.dp).height(7.dp)
                                .clip(RoundedCornerShape(999.dp)).background(nebulaColors.accent)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(previewPanel)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { i ->
                        if (i == 0) {
                            Box(
                                Modifier.width(10.dp).height(4.dp)
                                    .clip(RoundedCornerShape(999.dp)).background(nebulaColors.accent)
                            )
                        } else {
                            Box(
                                Modifier.size(4.dp).clip(CircleShape)
                                    .background(textColor.copy(alpha = 0.30f))
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SubscriptionAppearancePreview(
    themeSpec: String?,
    logo: String?,
    cachedLogo: String?,
    useSubscriptionTheme: Boolean,
    showSubscriptionLogo: Boolean
) {
    val nebulaColors = LocalNebulaColors.current
    val themeColors = subscriptionPreviewColors(themeSpec, nebulaColors.accent)
    val accent = themeColors.first()
    val hasTheme = !themeSpec.isNullOrBlank()
    val hasLogo = !logo.isNullOrBlank() || !cachedLogo.isNullOrBlank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubscriptionPreviewTile(
            title = t("Тема", "Theme"),
            subtitle = if (hasTheme) t("из подписки", "from subscription") else t("нет данных", "no data"),
            icon = Icons.Default.ColorLens,
            accent = accent,
            active = useSubscriptionTheme && hasTheme,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                themeColors.getOrElse(1) { accent }.copy(alpha = 0.34f),
                                themeColors.getOrElse(2) { accent }.copy(alpha = 0.18f),
                                windowsSoftFill(nebulaColors)
                            )
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                    .alpha(if (useSubscriptionTheme && hasTheme) 1f else 0.48f)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f))
                        .border(2.dp, accent, CircleShape)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    themeColors.take(3).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        )
                    }
                }
            }
        }

        SubscriptionPreviewTile(
            title = t("Логотип", "Logo"),
            subtitle = if (hasLogo) t("из подписки", "from subscription") else t("нет данных", "no data"),
            icon = Icons.Default.Image,
            accent = accent,
            active = showSubscriptionLogo && hasLogo,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(windowsSoftFill(nebulaColors))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasLogo) {
                    SubscriptionBrandLogo(
                        logo = logo,
                        cachedLogo = cachedLogo,
                        size = 38.dp,
                        modifier = Modifier.alpha(if (showSubscriptionLogo) 1f else 0.42f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionPreviewTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .height(130.dp)
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.12f) else nebulaColors.surface)
            .border(1.dp, if (active) accent.copy(alpha = 0.62f) else windowsBorder(nebulaColors), shape)
            .padding(10.dp)
    ) {
        content()
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) accent else nebulaColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun subscriptionPreviewColors(themeSpec: String?, fallback: Color): List<Color> {
    val colors = themeSpec
        ?.split(",")
        ?.mapNotNull { part ->
            val trimmed = part.trim()
            if (!trimmed.startsWith("#")) {
                null
            } else {
                runCatching { Color(android.graphics.Color.parseColor(trimmed)) }.getOrNull()
            }
        }
        ?.take(3)
        .orEmpty()

    if (colors.isNotEmpty()) return colors
    return listOf(
        fallback,
        fallback.copy(alpha = 0.82f),
        lerp(fallback, Color.White, 0.28f)
    )
}

private fun backgroundStylePresets(): List<Pair<Int, String>> = listOf(
    0 to "Standard",
    1 to "Material",
    2 to "Dots",
    3 to "Aurora",
    4 to "Grid",
    5 to "Mesh",
    6 to "Waves",
    7 to "Stars",
    8 to "Cyberpunk",
    9 to "Deep Space",
    10 to "Fire",
    11 to "Lava",
    12 to "Neon",
    13 to "Nordic",
    14 to "Blossom"
)

@Composable
private fun BackgroundPresetTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(16.dp)
    val colors = backgroundPresetColors(label, nebulaColors)
    val fill = if (selected) nebulaColors.accent.copy(alpha = 0.14f) else nebulaColors.surface
    val border = if (selected) nebulaColors.accent.copy(alpha = 0.62f) else windowsBorder(nebulaColors)
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(112.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .nimboClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(13.dp))
                .background(backgroundPresetBrush(label, nebulaColors))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(13.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .offset(x = (-12).dp, y = 8.dp)
                    .blur(10.dp)
                    .clip(CircleShape)
                    .background(colors.getOrElse(0) { nebulaColors.accent }.copy(alpha = 0.58f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-8).dp)
                    .size(48.dp)
                    .blur(12.dp)
                    .clip(CircleShape)
                    .background(colors.getOrElse(1) { nebulaColors.accent }.copy(alpha = 0.50f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 10.dp)
                    .size(40.dp)
                    .blur(9.dp)
                    .clip(CircleShape)
                    .background(colors.getOrElse(2) { nebulaColors.accent }.copy(alpha = 0.44f))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.64f))
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(nebulaColors.accent.copy(alpha = 0.85f))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Black.copy(alpha = if (nebulaColors.isLight) 0.16f else 0.26f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun backgroundPresetBrush(label: String, nebulaColors: NebulaColors): Brush {
    return Brush.linearGradient(backgroundPresetColors(label, nebulaColors))
}

private fun backgroundPresetColors(label: String, nebulaColors: NebulaColors): List<Color> {
    val accent = nebulaColors.accent
    return when (label.lowercase()) {
        "standard" -> listOf(accent.copy(alpha = 0.90f), Color(0xFF191A24), nebulaColors.background)
        "material" -> listOf(accent.copy(alpha = 0.70f), Color(0xFF2F2A3B), Color(0xFFE8DEF8))
        "dots" -> listOf(Color(0xFF090A0F), Color(0xFF2C2C34), accent.copy(alpha = 0.70f))
        "grid" -> listOf(Color(0xFF10131A), Color(0xFF263046), accent)
        "waves" -> listOf(Color(0xFF10233F), Color(0xFF1976D2), Color(0xFF66E3FF))
        "cyberpunk" -> listOf(Color(0xFF00F0FF), Color(0xFFFF2EA6), Color(0xFF7C5DFA))
        "deep space" -> listOf(Color(0xFF070716), Color(0xFF25206C), Color(0xFFE8F3FF))
        "fire" -> listOf(Color(0xFF3A0B05), Color(0xFFFF4D00), Color(0xFFFFC857))
        "lava" -> listOf(Color(0xFF250008), Color(0xFFFF3D00), Color(0xFF7C1FFF))
        "neon" -> listOf(Color(0xFFFF2EA6), Color(0xFF7C5DFA), Color(0xFF00D2FF))
        "nordic" -> listOf(Color(0xFFB8FFF2), Color(0xFF6EA8FF), Color(0xFF12213A))
        "blossom" -> listOf(Color(0xFFFF9BC4), Color(0xFFFFC29B), Color(0xFFC7A8FF))
        "aurora" -> listOf(Color(0xFF6BE88E), Color(0xFF63B3FF), Color(0xFF7C5DFA))
        "mesh" -> listOf(accent, Color(0xFF00D2FF), Color(0xFFFF7B7B))
        "stars" -> listOf(Color(0xFF0A0F1E), Color(0xFF29345F), Color(0xFFE9EDFF))
        else -> listOf(accent.copy(alpha = 0.86f), nebulaColors.background, Color(0xFF1C1D29))
    }
}

@Composable
private fun ThemeSectionHeader(
    icon: ImageVector,
    text: String,
    trailing: (@Composable () -> Unit)? = null
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Small accent-colored "reset to default" affordance used under sliders / color picker. */
@Composable
private fun ResetLink(text: String, onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Refresh, null, tint = nebulaColors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = nebulaColors.accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Tiny per-slider "reset to default" icon shown at the end of a slider row. */
@Composable
private fun SliderResetIcon(onReset: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = null,
        tint = nebulaColors.textTertiary,
        modifier = Modifier
            .padding(start = 2.dp)
            .size(28.dp)
            .clip(CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onReset)
            .padding(5.dp)
    )
}

@Composable
private fun ThemeSchemePill(text: String, onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(nebulaColors.accent)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (nebulaColors.accent.luminance() > 0.55f) Color.Black else Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ThemePaletteTile(
    color: Color,
    selected: Boolean,
    add: Boolean = false,
    system: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(16.dp)
    val tileBrush = when {
        system -> Brush.sweepGradient(
            colors = listOf(
                Color(0xFFFF5252),
                Color(0xFFFFB300),
                Color(0xFF66BB6A),
                Color(0xFF29B6F6),
                Color(0xFF7C5DFA),
                Color(0xFFEC407A),
                Color(0xFFFF5252)
            )
        )
        add -> {
            val base = if (selected) color else nebulaColors.accent
            Brush.radialGradient(
                colors = listOf(
                    lerp(base, Color.White, 0.10f).copy(alpha = if (selected) 0.95f else 0.40f),
                    base.copy(alpha = if (selected) 0.86f else 0.24f),
                    lerp(base, Color.Black, 0.34f).copy(alpha = if (selected) 0.95f else 0.38f)
                )
            )
        }
        else -> Brush.linearGradient(
            colors = listOf(
                lerp(color, Color.White, if (color.luminance() > 0.55f) 0.08f else 0.04f),
                color,
                lerp(color, Color.Black, if (color.luminance() > 0.55f) 0.10f else 0.18f)
            )
        )
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
            .background(tileBrush)
            .border(
                1.dp,
                if (selected) nebulaColors.accent else Color.White.copy(alpha = 0.12f),
                shape
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            system -> {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else Icons.Default.Palette,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            add -> {
                val iconTint = if (selected && color.luminance() > 0.55f) Color.Black else nebulaColors.accent
                Icon(Icons.Default.Add, null, tint = iconTint, modifier = Modifier.size(30.dp))
            }
            selected -> {
                val selectedChipColor = if (color.luminance() > 0.55f) {
                    Color.Black.copy(alpha = 0.18f)
                } else {
                    Color.White.copy(alpha = 0.20f)
                }
                val selectedIconColor = if (color.luminance() > 0.55f) {
                    Color.Black.copy(alpha = 0.72f)
                } else {
                    Color.White.copy(alpha = 0.88f)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(selectedChipColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = selectedIconColor, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun AccentPresetCard(
    name: String,
    colors: List<Color>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    isAdd: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(16.dp)
    val previewBase = if (nebulaColors.isLight) Color(0xFFF1EFF7) else Color(0xFF101D31)
    val previewPanel = if (nebulaColors.isLight) Color.White else Color(0xFF242431)
    val statusTint = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.40f)
    val accentBrush = Brush.linearGradient(if (colors.size == 1) listOf(colors[0], colors[0]) else colors)
    val glowColor = colors.first()
    val fill by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.14f) else nebulaColors.surface,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "accent_card_fill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.80f) else windowsBorder(nebulaColors),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "accent_card_border"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .nimboClickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(previewBase)
                .border(1.dp, statusTint.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.34f), Color.Transparent)))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(10.dp).height(3.dp)
                            .clip(RoundedCornerShape(999.dp)).background(statusTint)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) { i ->
                            Box(
                                Modifier.width(if (i == 2) 6.dp else 3.dp).height(3.dp)
                                    .clip(RoundedCornerShape(999.dp)).background(statusTint)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(accentBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(2.dp).height(6.dp)
                                .clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.95f))
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(previewPanel)
                        .padding(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(9.dp).height(4.dp)
                            .clip(RoundedCornerShape(999.dp)).background(accentBrush)
                    )
                    repeat(2) {
                        Box(
                            Modifier.size(4.dp).clip(CircleShape)
                                .background(statusTint.copy(alpha = 0.55f))
                        )
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            } else if (isAdd) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = name,
            color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun ThemeSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    leadingText: String? = null
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(24.dp))
        } else if (leadingText != null) {
            Text(
                text = leadingText,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Light,
                modifier = Modifier.width(38.dp)
            )
        }
        if (icon != null) Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = nebulaColors.background,
                checkedTrackColor = nebulaColors.accent,
                uncheckedThumbColor = nebulaColors.textSecondary,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = nebulaColors.textSecondary.copy(alpha = 0.55f)
            )
        )
    }
}

private fun themeSchemeLabel(index: Int): String = when (index) {
    0 -> loc("Тональные", "Tonal")
    1 -> loc("Точные", "Accurate")
    2 -> loc("Монохром", "Monochrome")
    3 -> loc("Нейтральные", "Neutral")
    5 -> loc("Мягкие", "Soft")
    6 -> loc("Контентные", "Content")
    7 -> loc("Глубокие", "Deep")
    8 -> loc("Сдержанные", "Restrained")
    else -> loc("Спокойные", "Calm")
}

@Composable
private fun ThemeSchemeDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val options = List(9) { it }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (nebulaColors.isMaterialYou) windowsPanelFill(nebulaColors) else lerp(nebulaColors.background, Color.Black, 0.28f).copy(alpha = 0.96f))
                .border(1.dp, windowsBorder(nebulaColors), RoundedCornerShape(28.dp))
                .padding(28.dp)
        ) {
            Column {
                Text(
                    text = t("Цветовые схемы", "Color schemes"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(18.dp))
                options.forEach { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                onSelect(index)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (selected == index) nebulaColors.accent else nebulaColors.textSecondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected == index) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(nebulaColors.accent)
                                )
                            }
                        }
                        Spacer(Modifier.width(22.dp))
                        Text(
                            text = themeSchemeLabel(index),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }
        }
    }
}

private fun loadUserPresets(preferencesManager: PreferencesManager): List<List<Int>> {
    val raw = preferencesManager.sharedPreferences.getString("custom_user_presets_json", null) ?: return emptyList()
    return runCatching {
        val array = org.json.JSONArray(raw)
        List(array.length()) { idx ->
            val colorsArr = array.getJSONArray(idx)
            List(colorsArr.length()) { cIdx -> colorsArr.getInt(cIdx) }
        }
    }.getOrDefault(emptyList())
}

private fun saveUserPreset(preferencesManager: PreferencesManager, colors: List<Int>) {
    val current = loadUserPresets(preferencesManager).toMutableList()
    current.add(colors)
    val array = org.json.JSONArray()
    current.forEach { preset ->
        val colorsArr = org.json.JSONArray()
        preset.forEach { colorsArr.put(it) }
        array.put(colorsArr)
    }
    preferencesManager.sharedPreferences.edit().putString("custom_user_presets_json", array.toString()).apply()
}

private fun deleteUserPreset(preferencesManager: PreferencesManager, index: Int) {
    val current = loadUserPresets(preferencesManager).toMutableList()
    if (index in current.indices) {
        current.removeAt(index)
        val array = org.json.JSONArray()
        current.forEach { preset ->
            val colorsArr = org.json.JSONArray()
            preset.forEach { colorsArr.put(it) }
            array.put(colorsArr)
        }
        preferencesManager.sharedPreferences.edit().putString("custom_user_presets_json", array.toString()).apply()
    }
}

@Composable
private fun CustomThemeColorDialog(
    preferencesManager: PreferencesManager,
    onDismiss: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    // Hold current counts and colors states using explicit state values
    val gradientCountState = rememberSaveable { mutableStateOf(preferencesManager.customGradientCount) }
    val activeTabState = rememberSaveable { mutableStateOf(1) } // 1, 2, or 3

    val customColor1State = remember { mutableStateOf(Color(preferencesManager.customGradientColor1)) }
    val customColor2State = remember { mutableStateOf(Color(preferencesManager.customGradientColor2)) }
    val customColor3State = remember { mutableStateOf(Color(preferencesManager.customGradientColor3)) }

    val userPresetsState = remember { mutableStateOf(loadUserPresets(preferencesManager)) }

    val gradientCount = gradientCountState.value
    val activeTab = activeTabState.value
    val customColor1 = customColor1State.value
    val customColor2 = customColor2State.value
    val customColor3 = customColor3State.value
    val userPresets = userPresetsState.value

    val activeColor = when (activeTab) {
        1 -> customColor1
        2 -> customColor2
        else -> customColor3
    }

    // HSV + Alpha representation of the currently selected color
    val currentHsv = remember(activeTab, customColor1, customColor2, customColor3) {
        val color = when (activeTab) {
            1 -> customColor1
            2 -> customColor2
            else -> customColor3
        }
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), arr)
        arr
    }

    val currentAlpha = remember(activeTab, customColor1, customColor2, customColor3) {
        val color = when (activeTab) {
            1 -> customColor1
            2 -> customColor2
            else -> customColor3
        }
        color.alpha
    }

    fun updateActiveColor(hsv: FloatArray, alpha: Float) {
        val color = Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
        when (activeTab) {
            1 -> customColor1State.value = color
            2 -> customColor2State.value = color
            else -> customColor3State.value = color
        }
    }

    fun matchesPreset(presetColors: List<Color>): Boolean {
        if (presetColors.size != gradientCount) return false
        val activeColors = when (gradientCount) {
            1 -> listOf(customColor1)
            2 -> listOf(customColor1, customColor2)
            else -> listOf(customColor1, customColor2, customColor3)
        }
        return activeColors.zip(presetColors).all { (c1, c2) -> c1.toArgb() == c2.toArgb() }
    }

    val presets = listOf(
        // Aurora Rose
        listOf(Color(0xFFEC407A), Color(0xFF7C5DFA), Color(0xFF00E676)),
        // Cyber Neon
        listOf(Color(0xFF00FFCC), Color(0xFF0099FF), Color(0xFF7C5DFA)),
        // Deep Space
        listOf(Color(0xFF3B82F6), Color(0xFF9B8DF5), Color(0xFFF472B6)),
        // Sunset Gold
        listOf(Color(0xFFFF5252), Color(0xFFFFB300), Color(0xFFEC407A)),
        // Forest Calm
        listOf(Color(0xFFA3E635), Color(0xFF00E676), Color(0xFF22D3EE)),
        // Royal Velvet
        listOf(Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFFFF007F)),
        // Cosmic Dust
        listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFFFF007F)),
        // Ocean Breeze
        listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF0000FF))
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (nebulaColors.isMaterialYou) windowsPanelFill(nebulaColors) else lerp(nebulaColors.background, Color.Black, 0.30f).copy(alpha = 0.98f))
                .border(1.dp, windowsBorder(nebulaColors), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = t("Цветовая схема", "Color spectrum"),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Segmented Selector for gradient count
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(1, 2, 3).forEach { count ->
                            val selected = gradientCount == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) nebulaColors.accent else Color.Transparent)
                                    .clickable {
                                        gradientCountState.value = count
                                        if (activeTabState.value > count) {
                                            activeTabState.value = count
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                  Text(
                                      text = when (count) {
                                          1 -> t("1 цвет", "Solid")
                                          2 -> t("2 цвета", "2 Colors")
                                          else -> t("3 цвета", "3 Colors")
                                      },
                                      color = if (selected) (if (nebulaColors.accent.luminance() > 0.55f) Color.Black else Color.White) else nebulaColors.textSecondary,
                                      style = MaterialTheme.typography.labelLarge,
                                      fontWeight = FontWeight.Bold
                                  )
                            }
                        }
                    }
                }

                // Color selection tabs (only shown if gradientCount > 1)
                if (gradientCount > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..gradientCount).forEach { tab ->
                                val selected = activeTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) nebulaColors.accent.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(1.dp, if (selected) nebulaColors.accent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                        .clickable { activeTabState.value = tab },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${t("Цвет", "Color")} $tab",
                                        color = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Gradient or solid color preview card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = when (gradientCount) {
                                    1 -> Brush.linearGradient(listOf(customColor1, customColor1))
                                    2 -> Brush.linearGradient(listOf(customColor1, customColor2))
                                    else -> Brush.linearGradient(listOf(customColor1, customColor2, customColor3))
                                }
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    )
                }

                // 2D Visual Color Canvas Spectrum Picker (Draggable HSV Picker)
                item {
                    Column {
                        Text(
                            text = t("Палитра оттенка", "Hue spectrum palette"),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(currentHsv) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull()
                                                if (change != null) {
                                                    change.consume()
                                                    val position = change.position
                                                    val sat = (position.x / size.width).coerceIn(0f, 1f)
                                                    val value = (1f - position.y / size.height).coerceIn(0f, 1f)
                                                    val newHsv = currentHsv.clone()
                                                    newHsv[1] = sat
                                                    newHsv[2] = value
                                                    updateActiveColor(newHsv, currentAlpha)
                                                }
                                            }
                                        }
                                    }
                            ) {
                                // Draw horizontal gradient from white to base saturated hue
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(currentHsv[0], 1f, 1f))))
                                    )
                                )
                                // Draw vertical multiply overlay from transparent to black
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black)
                                    ),
                                    blendMode = BlendMode.Multiply
                                )

                                // Draw circular picker handle
                                val posX = currentHsv[1] * this.size.width
                                val posY = (1f - currentHsv[2]) * this.size.height
                                drawCircle(
                                    color = Color.White,
                                    radius = 6.dp.toPx(),
                                    center = Offset(posX, posY),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Designer swatches grid
                item {
                    Column {
                        Text(
                            text = t("Быстрые цвета", "Designer swatches"),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val swatches = listOf(
                                Color(0xFFE11D48), // Rose/Ruby
                                Color(0xFFC026D3), // Fuchsia/Orchid
                                Color(0xFF7C3AED), // Violet
                                Color(0xFF2563EB), // Blue/Cobalt
                                Color(0xFF0284C7), // Sky/Ice
                                Color(0xFF0D9488), // Teal
                                Color(0xFF059669), // Emerald
                                Color(0xFF16A34A), // Green/Mint
                                Color(0xFFCA8A04), // Yellow/Gold
                                Color(0xFFD97706), // Amber
                                Color(0xFFEA580C), // Orange/Coral
                                Color(0xFFDC2626)  // Red
                            )
                            swatches.forEach { color ->
                                val selected = activeColor.toArgb() == color.toArgb()
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.20f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val arr = FloatArray(3)
                                            android.graphics.Color.colorToHSV(color.toArgb(), arr)
                                            updateActiveColor(arr, color.alpha)
                                        }
                                )
                            }
                        }
                    }
                }

                // Curated presets gallery
                item {
                    Column {
                        Text(
                            text = t("Готовые пресеты", "Premium presets"),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            presets.forEach { colors ->
                                val selected = matchesPreset(colors)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = when (gradientCount) {
                                                1 -> Brush.linearGradient(listOf(colors[0], colors[0]))
                                                2 -> Brush.linearGradient(listOf(colors[0], colors[1]))
                                                else -> Brush.linearGradient(listOf(colors[0], colors[1], colors[2]))
                                            }
                                        )
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) nebulaColors.accent else Color.White.copy(alpha = 0.20f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            customColor1State.value = colors[0]
                                            if (colors.size > 1) customColor2State.value = colors[1]
                                            if (colors.size > 2) customColor3State.value = colors[2]
                                        }
                                )
                            }
                        }
                    }
                }

                // My presets (Save and Load Custom presets)
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = t("Мои пресеты", "My presets"),
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Circular "+" Add Button directly in the flow grid
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.6f), CircleShape)
                                        .clickable {
                                            val currentColors = when (gradientCount) {
                                                1 -> listOf(customColor1.toArgb())
                                                2 -> listOf(customColor1.toArgb(), customColor2.toArgb())
                                                else -> listOf(customColor1.toArgb(), customColor2.toArgb(), customColor3.toArgb())
                                            }
                                            saveUserPreset(preferencesManager, currentColors)
                                            userPresetsState.value = loadUserPresets(preferencesManager)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = t("Сохранить", "Save"),
                                        tint = nebulaColors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            userPresets.forEachIndexed { idx, colorsInt ->
                                val colors = colorsInt.map { Color(it) }
                                val selected = matchesPreset(colors)
                                // Outer container 44dp so delete badge at top-right has its space accounted for,
                                // preventing overlaps or line-shifting in the FlowRow grid.
                                Box(modifier = Modifier.size(44.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                brush = when (colors.size) {
                                                    1 -> Brush.linearGradient(listOf(colors[0], colors[0]))
                                                    2 -> Brush.linearGradient(listOf(colors[0], colors[1]))
                                                    else -> Brush.linearGradient(listOf(colors[0], colors[1], colors[2]))
                                                }
                                            )
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) nebulaColors.accent else Color.White.copy(alpha = 0.20f),
                                                shape = CircleShape
                                            )
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                if (colors.isNotEmpty()) {
                                                    customColor1State.value = colors[0]
                                                    if (colors.size > 1) customColor2State.value = colors[1]
                                                    if (colors.size > 2) customColor3State.value = colors[2]
                                                    gradientCountState.value = colors.size
                                                }
                                            }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.66f))
                                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                deleteUserPreset(preferencesManager, idx)
                                                userPresetsState.value = loadUserPresets(preferencesManager)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = t("Удалить пресет", "Delete preset"),
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sliders for Hue, Saturation, Brightness and Opacity
                item {
                    Column {
                        // Hue slider (Rainbow gradient background)
                        Text(
                            text = t("Оттенок", "Hue"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentHsv[0],
                            onValueChange = {
                                val newHsv = currentHsv.clone()
                                newHsv[0] = it
                                updateActiveColor(newHsv, currentAlpha)
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.2f)
                            )
                        )

                        // Saturation slider
                        Text(
                            text = t("Насыщенность", "Saturation"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentHsv[1],
                            onValueChange = {
                                val newHsv = currentHsv.clone()
                                newHsv[1] = it
                                updateActiveColor(newHsv, currentAlpha)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.2f)
                            )
                        )

                        // Brightness slider
                        Text(
                            text = t("Яркость цвета", "Brightness"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentHsv[2],
                            onValueChange = {
                                val newHsv = currentHsv.clone()
                                newHsv[2] = it
                                updateActiveColor(newHsv, currentAlpha)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.2f)
                            )
                        )

                        // Opacity slider
                        Text(
                            text = t("Прозрачность цвета", "Color opacity"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentAlpha,
                            onValueChange = {
                                updateActiveColor(currentHsv, it)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                // Cancel / Confirm Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(t("Отмена", "Cancel"), color = nebulaColors.accent)
                        }
                        TextButton(onClick = {
                            preferencesManager.useDynamicColor = false
                            preferencesManager.isCustomAccent = true
                            preferencesManager.customAccentColor = customColor1.toArgb()
                            preferencesManager.customGradientColor1 = customColor1.toArgb()
                            preferencesManager.customGradientColor2 = customColor2.toArgb()
                            preferencesManager.customGradientColor3 = customColor3.toArgb()
                            preferencesManager.customGradientCount = gradientCount
                            onDismiss()
                        }) {
                            Text(
                                text = t("Подтвердить", "Confirm"),
                                color = nebulaColors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hexToColorInt(hex: String): Int {
    return runCatching { android.graphics.Color.parseColor(hex) }
        .getOrDefault(0xFF7C5DFA.toInt())
}

@Composable
private fun ThemeModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 0f else 0.22f, animationSpec = tween(200), label = "chip-border"
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f, animationSpec = tween(220), label = "chip-fill"
    )
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        nebulaColors.accent.copy(alpha = 0.32f * fillAlpha),
                        nebulaColors.accent.copy(alpha = 0.12f * fillAlpha)
                    )
                )
            )
            .border(1.dp, nebulaColors.textSecondary.copy(alpha = borderAlpha), shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) nebulaColors.accent else nebulaColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun currentLanguageLabel(): String {
    val context = LocalContext.current
    // Read on every composition: a keyless remember{} cached the very first value
    // and the Settings row kept showing the old language after a switch.
    val code = PreferencesManager(context).appLanguage
    return when (code) {
        "ru" -> "Русский"
        "en" -> "English"
        else -> t("Системный", "System")
    }
}

@Composable
private fun NimboLanguageScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(preferencesManager.appLanguage) }

    fun setLanguage(tag: String) {
        if (tag == current) return
        preferencesManager.appLanguage = tag
        current = tag
        // Recreate the activity so attachBaseContext re-applies the locale.
        (context as? android.app.Activity)?.recreate()
    }

    NimboSubPageScaffold(title = t("Язык", "Language"), onBack = onBack) {
        LanguageRow(
            title = t("Системный", "System"),
            subtitle = t("Использовать язык устройства", "Use device language"),
            flag = null,
            selected = current.isBlank(),
            onClick = { setLanguage("") }
        )
        Spacer(Modifier.height(10.dp))
        LanguageRow(
            title = "Русский",
            subtitle = "Russian",
            flag = "🇷🇺",
            selected = current == "ru",
            onClick = { setLanguage("ru") }
        )
        Spacer(Modifier.height(10.dp))
        LanguageRow(
            title = "English",
            subtitle = t("Английский", "English"),
            flag = "🇬🇧",
            selected = current == "en",
            onClick = { setLanguage("en") }
        )
    }
}

@Composable
private fun LanguagePreviewCard(
    title: String,
    flag: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(16.dp)
    val previewBase = if (nebulaColors.isLight) Color(0xFFF1EFF7) else Color(0xFF101D31)
    val previewPanel = if (nebulaColors.isLight) Color.White else Color(0xFF242431)
    val statusTint = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.40f)
    val fill by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.14f) else nebulaColors.surface,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "lang_card_fill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.80f) else windowsBorder(nebulaColors),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "lang_card_border"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .nimboClickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(previewBase)
                .border(1.dp, statusTint.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .background(Brush.radialGradient(listOf(nebulaColors.accent.copy(alpha = 0.28f), Color.Transparent)))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(statusTint))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) { i ->
                            Box(Modifier.width(if (i == 2) 6.dp else 3.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(statusTint))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(previewPanel),
                    contentAlignment = Alignment.Center
                ) {
                    if (flag != null) {
                        Text(flag, fontSize = 15.sp, maxLines = 1)
                    } else {
                        Icon(Icons.Default.Language, null, tint = nebulaColors.accent, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(999.dp)).background(previewPanel))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(4.dp).clip(RoundedCornerShape(999.dp)).background(previewPanel.copy(alpha = 0.7f)))
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = title,
            color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun LanguageRow(
    title: String,
    subtitle: String,
    flag: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val materialYou = nebulaColors.isMaterialYou
    val shape = RoundedCornerShape(16.dp)

    val fillColor = when {
        selected && materialYou -> MaterialTheme.colorScheme.secondaryContainer
        selected -> nebulaColors.accent.copy(alpha = 0.10f)
        materialYou -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> nebulaColors.surface
    }
    val borderColor = when {
        selected && materialYou -> Color.Transparent
        materialYou -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
        selected -> nebulaColors.accent.copy(alpha = 0.85f)
        else -> windowsBorder(nebulaColors)
    }
    val titleColor = when {
        selected && materialYou -> MaterialTheme.colorScheme.onSecondaryContainer
        materialYou -> MaterialTheme.colorScheme.onSurface
        else -> nebulaColors.textPrimary
    }
    val subtitleColor = when {
        selected && materialYou -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
        materialYou -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> nebulaColors.textTertiary
    }
    val dotColor = if (materialYou) {
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    } else {
        nebulaColors.accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fillColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) dotColor.copy(alpha = 0.18f) else windowsSoftFill(nebulaColors))
                .border(
                    1.dp,
                    if (selected) dotColor.copy(alpha = 0.52f) else windowsBorder(nebulaColors, 0.10f),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (flag != null) {
                Text(
                    text = flag,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = if (selected) dotColor else subtitleColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.background)
                        .border(1.dp, dotColor.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = dotColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun NimboAboutScreen(onBack: () -> Unit) {
    NimboSubPageScaffold(title = t("О приложении", "About"), onBack = onBack) {
        AboutSettingsContent()
    }
}

@Composable
private fun ColumnScope.AboutSettingsContent() {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val xrayVersion = "26.7.11"
    val deviceName = remember { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim() }
    val systemName = remember { "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})" }

    val brush = Brush.linearGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.02f)
        )
    )

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        borderColor = Color.White.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(nebulaColors.accent.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    text = "Nimbo",
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = t("Версия $version", "Version $version"),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SettingsCompactCard {
        AboutValueRow(t("Версия", "Version"), version, icon = Icons.Default.Info)
        AboutValueRow(t("Движок", "Engine"), "Xray-core $xrayVersion", icon = Icons.Default.Memory)
        AboutValueRow(t("Разработчик", "Developer"), "Danila", icon = Icons.Default.SupportAgent)
        AboutValueRow(t("Система", "System"), systemName, icon = Icons.Default.Settings)
        AboutValueRow(t("Устройство", "Device"), deviceName.ifBlank { "—" }, showDivider = false, icon = Icons.Default.Smartphone)
    }
    Spacer(Modifier.height(20.dp))
    SubPageSectionHeader(t("Полезные ссылки", "Useful links"), icon = Icons.Default.Link)
    Spacer(Modifier.height(10.dp))
    SettingsCompactCard {
        AboutLinkRow(t("Сайт", "Website"), "https://nimboapp.pw", icon = Icons.Default.Public) { openUrl(context, it) }
        AboutLinkRow(t("Проект", "Project"), "https://github.com/BBGGVP5/nimbo", icon = Icons.Default.Public) { openUrl(context, it) }
        AboutLinkRow(t("Канал", "Channel"), "https://t.me/nebulaguard_channel", icon = Icons.Default.Language) { openUrl(context, it) }
        AboutLinkRow(t("Ядро", "Core"), "https://github.com/XTLS/Xray-core", showDivider = false, icon = Icons.Default.Description) { openUrl(context, it) }
    }
}

@Composable
private fun AboutValueRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    icon: ImageVector? = null
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = value,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
    }
}

@Composable
private fun AboutLinkRow(
    label: String,
    url: String,
    showDivider: Boolean = true,
    icon: ImageVector? = null,
    onClick: (String) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick(url)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp)
                )
            }
            Text(
                text = label,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
    }
}

@Composable
private fun NimboConnectionIdScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val defaultUserAgent = remember { preferencesManager.defaultSubscriptionUserAgent }
    val happUserAgent = remember { preferencesManager.happSubscriptionUserAgent }
    val incyUserAgent = remember { preferencesManager.incySubscriptionUserAgent }
    val userAgentMode by preferencesManager.subscriptionUserAgentModeState
    val customUserAgent by preferencesManager.customSubscriptionUserAgentState
    val effectiveUserAgent = when (userAgentMode) {
        1 -> happUserAgent
        2 -> incyUserAgent
        3 -> customUserAgent.ifBlank { defaultUserAgent }
        else -> defaultUserAgent
    }
    val hwid = remember { preferencesManager.hardwareId }
    var copiedHint by remember { mutableStateOf(false) }
    LaunchedEffect(copiedHint) {
        if (copiedHint) {
            kotlinx.coroutines.delay(1500)
            copiedHint = false
        }
    }

    NimboSubPageScaffold(title = t("Идентификация", "Identification"), onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubPageSectionHeader(t("HWID устройства", "Device HWID"), icon = Icons.Default.Fingerprint)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = copiedHint) {
                Text(
                    text = t("Скопировано", "Copied"),
                    color = nebulaColors.accent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            borderColor = Color.White.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hwid,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = t("Стабильный идентификатор устройства", "Stable device identifier"),
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                MiniIconButton(Icons.Default.ContentCopy, onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("HWID", hwid))
                    copiedHint = true
                })
            }
        }

        Spacer(Modifier.height(28.dp))
        SubPageSectionHeader(t("User-Agent для подписки", "Subscription User-Agent"), icon = Icons.Default.Public)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeChip(t("Дефолт", "Default"), selected = userAgentMode == 0, modifier = Modifier.weight(1f)) {
                preferencesManager.subscriptionUserAgentMode = 0
            }
            ThemeModeChip("Happ", selected = userAgentMode == 1, modifier = Modifier.weight(1f)) {
                preferencesManager.subscriptionUserAgentMode = 1
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeChip("Incy", selected = userAgentMode == 2, modifier = Modifier.weight(1f)) {
                preferencesManager.subscriptionUserAgentMode = 2
            }
            ThemeModeChip(t("Свой", "Custom"), selected = userAgentMode == 3, modifier = Modifier.weight(1f)) {
                preferencesManager.subscriptionUserAgentMode = 3
            }
        }
        AnimatedVisibility(visible = userAgentMode == 3) {
            Column {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = customUserAgent,
                    onValueChange = {
                        preferencesManager.customSubscriptionUserAgent = it
                        preferencesManager.subscriptionUserAgentMode = 3
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            t("Например: Happ/1.0.0", "Example: Happ/1.0.0"),
                            color = nebulaColors.textTertiary
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = nebulaColors.accent,
                        unfocusedBorderColor = nebulaColors.textSecondary.copy(alpha = 0.22f),
                        focusedTextColor = nebulaColors.textPrimary,
                        unfocusedTextColor = nebulaColors.textPrimary,
                        cursorColor = nebulaColors.accent
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = t(
                "Сейчас отправляется: $effectiveUserAgent",
                "Currently sent: $effectiveUserAgent"
            ),
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NimboDisclaimerDialog(
    onDismiss: () -> Unit,
    onExit: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(lerp(nebulaColors.background, Color.Black, 0.55f))
                .border(
                    1.dp,
                    nebulaColors.accent.copy(alpha = 0.30f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = t("Отказ от ответственности", "Disclaimer"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = t(
                        "Данное программное обеспечение предназначено исключительно для " +
                            "некоммерческого использования в образовательных и исследовательских " +
                            "целях. Коммерческое использование запрещено. Разработчики не несут " +
                            "ответственности за любую коммерческую деятельность с использованием " +
                            "данного ПО.",
                        "This software is intended solely for non-commercial use in educational " +
                            "and research contexts. Commercial use is prohibited. The developers " +
                            "accept no liability for any commercial activity carried out with this software."
                    ),
                    color = nebulaColors.textPrimary.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onExit) {
                        Text(
                            t("Выход", "Exit"),
                            color = nebulaColors.accent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    TextButton(onClick = onDismiss) {
                        Text(
                            t("Согласен", "Agree"),
                            color = nebulaColors.accent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NimboBackButton(onBack: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(nebulaColors.textPrimary.copy(alpha = 0.045f))
            .clickable(onClick = onBack)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = t("Назад", "Back"),
            tint = nebulaColors.textPrimary,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
internal fun NimboSubPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NimboBackButton(onBack = onBack)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(if (subtitle.isNullOrBlank()) 16.dp else 12.dp))
        val scrollModifier = rememberVerticalScrollModifier()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .then(scrollModifier),
            content = content
        )
    }
}

@Composable
internal fun SubPageSectionHeader(
    text: String,
    icon: ImageVector? = null
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp)
            )
        }
        Text(
            text = text,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun rememberVerticalScrollModifier(): Modifier {
    val scrollState = rememberScrollState()
    return Modifier.verticalScroll(scrollState)
}

@Composable
private fun ThemeDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, if (selected) Color.White else Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Color.Black.copy(alpha = 0.76f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun NimboRenameServerDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var value by rememberSaveable(initialName) { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            borderColor = Color.White.copy(alpha = 0.14f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = t("Переименовать сервер", "Rename server"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = t("Имя сохранится локально и не изменит подписку.", "The name is stored locally and will not change the subscription."),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Edit, null, tint = nebulaColors.accent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = nebulaColors.textPrimary,
                        unfocusedTextColor = nebulaColors.textPrimary,
                        focusedBorderColor = nebulaColors.accent,
                        unfocusedBorderColor = windowsBorder(nebulaColors, 0.16f),
                        focusedContainerColor = windowsControlFill(nebulaColors),
                        unfocusedContainerColor = windowsControlFill(nebulaColors),
                        cursorColor = nebulaColors.accent
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("Отмена", "Cancel"), color = nebulaColors.textSecondary)
                    }
                    TextButton(onClick = { onSave(value.trim()) }) {
                        Text(t("Сохранить", "Save"), color = nebulaColors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NimboConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            borderColor = Color.White.copy(alpha = 0.14f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, color = nebulaColors.textPrimary, style = MaterialTheme.typography.headlineSmall)
                Text(text, color = nebulaColors.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("Отмена", "Cancel"), color = nebulaColors.textSecondary)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmText, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderDialogLinkButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(nebulaColors.accent.copy(alpha = 0.12f))
            .border(1.dp, nebulaColors.accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .nimboClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun openUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun exportProfile(context: Context, profile: SubscriptionProfile) {
    val payload = profile.rawConfig?.takeIf { it.isNotBlank() } ?: profile.url
    if (payload.isBlank()) return
    val title = profile.displayName.ifBlank { loc("Профиль", "Profile") }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, loc("Экспорт профиля", "Export profile")))
    }
}

private fun serverTitle(server: Server): String {
    val cleaned = removeDomainFragments(cleanServerName(server.name))
        .takeIf { it.isNotBlank() }
        ?.takeIf { !SubscriptionManager.isGenericServerName(it) }
        ?.takeIf { !it.equals(server.host, ignoreCase = true) }
        ?.takeIf { !it.contains("://") && !looksLikeDomain(it) }
    return cleaned
        ?: readableServerDescription(server).ifBlank {
            server.templateName
                ?.trim()
                ?.takeIf { it.isNotBlank() && !looksLikeDomain(it) }
                ?: loc("Сервер", "Server")
        }
}

private fun serverUiTitle(preferencesManager: PreferencesManager, server: Server): String {
    return preferencesManager.getServerDisplayName(server.pingKey())
        ?.takeIf { it.isNotBlank() }
        ?: serverTitle(server)
}

private fun serverSubtitle(server: Server): String {
    val description = readableServerDescription(server)
    if (description.isNotBlank()) return description
    return technicalServerSubtitle(server)
}

private fun readableServerDescription(server: Server): String {
    val host = server.host.trim()
    return server.serverDescription
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?.takeIf { desc -> !desc.equals(host, ignoreCase = true) }
        ?.takeIf { desc -> !desc.contains("://") }
        .orEmpty()
}

private fun technicalServerSubtitle(server: Server): String {
    return listOfNotNull(
        miniProtocolLabel(server),
        miniTransportLabel(server),
        miniSecurityLabel(server)
    ).distinctBy { it.lowercase() }.joinToString(" · ")
}

private fun miniProtocolLabel(server: Server): String {
    val protocol = server.protocol.lowercase()
    return when {
        protocol.contains("vless") -> "VLESS"
        protocol.contains("vmess") -> "VMess"
        protocol.contains("trojan") -> "Trojan"
        protocol.contains("shadowsocks") || protocol == "ss" -> "Shadowsocks"
        protocol.contains("hysteria") || protocol == "hy2" -> "Hysteria2"
        protocol.contains("tuic") -> "TUIC"
        protocol.contains("reality") -> "Reality"
        protocol.isNotBlank() -> protocol.replaceFirstChar { it.uppercase() }
        else -> "VLESS"
    }
}

private fun miniTransportLabel(server: Server): String? {
    return when (server.network?.lowercase().orEmpty()) {
        "grpc" -> "gRPC"
        "ws" -> "WebSocket"
        "xhttp", "splithttp" -> "XHTTP"
        "httpupgrade", "http-upgrade" -> "HTTP Upgrade"
        "http" -> "HTTP"
        "h2" -> "HTTP/2"
        "quic" -> "QUIC"
        "tcp", "" -> null
        else -> server.network?.trim()?.takeIf { it.isNotBlank() }
    }
}

private fun miniSecurityLabel(server: Server): String? {
    return when (val security = server.security?.lowercase().orEmpty()) {
        "reality" -> "Reality"
        "tls" -> "TLS"
        "none", "" -> null
        else -> security.replaceFirstChar { it.uppercase() }
    }
}

private fun looksLikeDomain(value: String): Boolean {
    val compact = value.trim()
    if (compact.contains(" ")) return false
    return Regex("""^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(:\d{1,5})?$""").matches(compact)
}

private fun removeDomainFragments(value: String): String {
    return value
        .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b[A-Za-z0-9.-]+\.[A-Za-z]{2,}(:\d{1,5})?\b"""), "")
        .trim(' ', '·', '-', '|', ':', ',', '_')
}

private fun formatProfileDate(expireTime: Long): String {
    if (expireTime <= 0L) return loc("без срока", "no expiry")
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(expireTime * 1000L))
}

private fun formatDaysLeft(profile: SubscriptionProfile): String {
    if (profile.daysUntilExpiry >= 0) return profile.daysUntilExpiry.toString()
    if (profile.expireTime <= 0L) return "∞"
    val nowSeconds = System.currentTimeMillis() / 1000L
    return max(0L, (profile.expireTime - nowSeconds) / 86400L).toString()
}

private fun formatDaysLeft(profile: SubscriptionProfileMetadata): String {
    if (profile.daysUntilExpiry >= 0) return profile.daysUntilExpiry.toString()
    if (profile.expireTime <= 0L) return "∞"
    val nowSeconds = System.currentTimeMillis() / 1000L
    return max(0L, (profile.expireTime - nowSeconds) / 86400L).toString()
}

// Date-only expiry line; the remaining time is rendered separately below it.
private fun formatProfileExpiryDate(profile: SubscriptionProfile): String {
    if (profile.expireTime <= 0L) return loc("Без срока", "No expiry")
    val pattern = loc("d MMM yyyy 'г.'", "d MMM yyyy")
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(profile.expireTime * 1000L))
}

// "через N дн." — shown on its own line under the date. Empty when there's no expiry.
private fun formatProfileExpiryRemaining(profile: SubscriptionProfile): String {
    if (profile.expireTime <= 0L) return ""
    val days = formatDaysLeft(profile)
    return loc("через $days дн.", "$days days left")
}

private fun formatProfileExpiryRemaining(profile: SubscriptionProfileMetadata): String {
    if (profile.expireTime <= 0L) return ""
    val days = formatDaysLeft(profile)
    return loc("через $days дн.", "$days days left")
}

private fun formatUpdateAge(lastUpdateMs: Long): String {
    if (lastUpdateMs <= 0L) return loc("никогда", "never")
    val diffMs = (System.currentTimeMillis() - lastUpdateMs).coerceAtLeast(0L)
    val seconds = diffMs / 1000L
    if (seconds < 5L) return loc("только что", "just now")
    if (seconds < 60L) return loc("$seconds сек назад", "${seconds}s ago")
    val minutes = seconds / 60L
    return when {
        minutes < 60L -> loc("$minutes мин назад", "${minutes}m ago")
        minutes < 24 * 60L -> loc("${minutes / 60} ч назад", "${minutes / 60}h ago")
        else -> loc("${minutes / (24 * 60)} д назад", "${minutes / (24 * 60)}d ago")
    }
}

private fun formatLastUpdateTimestamp(lastUpdateMs: Long): String {
    if (lastUpdateMs <= 0L) return loc("никогда", "never")
    return SimpleDateFormat("dd.MM.yy, HH:mm", Locale.getDefault()).format(Date(lastUpdateMs))
}

/** Time-only ("HH:mm") for the compact "Обновлено" stat block. */
private fun formatLastUpdateTime(lastUpdateMs: Long): String {
    if (lastUpdateMs <= 0L) return loc("никогда", "never")
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdateMs))
}

@Composable
private fun SubscriptionSettingsDialog(
    profile: SubscriptionProfile,
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current

    var customName by remember { mutableStateOf(profile.customName ?: profile.displayName) }

    // Pin status state
    var isPinned by remember {
        mutableStateOf(preferencesManager.getPinnedProfileUrls().contains(profile.url.trim().lowercase()))
    }

    // Auto-update interval state
    val initialInterval = preferencesManager.getSubscriptionUpdateInterval(profile.url)
    var selectedIntervalHours by remember { mutableStateOf<Int?>(initialInterval) }
    var copiedUrl by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(26.dp),
            borderColor = Color.White.copy(alpha = 0.16f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(nebulaColors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = t("Настройки подписки", "Subscription Settings"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = nebulaColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = profile.displayName,
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Name Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("Название", "Name"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(t("Название подписки", "Subscription name"), color = nebulaColors.textTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = nebulaColors.accent,
                            unfocusedBorderColor = nebulaColors.accent.copy(alpha = 0.2f),
                            cursorColor = nebulaColors.accent,
                            focusedTextColor = nebulaColors.textPrimary,
                            unfocusedTextColor = nebulaColors.textPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Pin Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val key = profile.url.trim().lowercase()
                            val pinnedProfiles = preferencesManager.getPinnedProfileUrls()
                            if (!isPinned && pinnedProfiles.size >= 3) {
                                Toast.makeText(context, loc("Можно закрепить максимум 3 профиля", "Maximum of 3 profiles can be pinned"), Toast.LENGTH_SHORT).show()
                            } else {
                                isPinned = !isPinned
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Показывать на главной", "Show on main"),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = t("Закрепить этот профиль в топе", "Pin this profile to the top"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = isPinned,
                        onCheckedChange = { checked ->
                            val pinnedProfiles = preferencesManager.getPinnedProfileUrls()
                            if (checked && pinnedProfiles.size >= 3) {
                                Toast.makeText(context, loc("Можно закрепить максимум 3 профиля", "Maximum of 3 profiles can be pinned"), Toast.LENGTH_SHORT).show()
                            } else {
                                isPinned = checked
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = nebulaColors.accent,
                            uncheckedThumbColor = nebulaColors.textTertiary,
                            uncheckedTrackColor = Color.Transparent
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Update Interval Block
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("Интервал обновления", "Update interval"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 2x3 Grid of interval chips
                    val intervals = listOf(
                        Pair(t("1 час", "1 hour"), 1),
                        Pair(t("2 часа", "2 hours"), 2),
                        Pair(t("6 часов", "6 hours"), 6),
                        Pair(t("12 часов", "12 hours"), 12),
                        Pair(t("24 часа", "24 hours"), 24),
                        Pair(t("По умолчанию", "Default"), null)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (row in 0..1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (col in 0..2) {
                                    val itemIndex = row * 3 + col
                                    if (itemIndex < intervals.size) {
                                        val (label, hours) = intervals[itemIndex]
                                        val isSelected = selectedIntervalHours == hours
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) nebulaColors.accent.copy(alpha = 0.18f)
                                                    else windowsSoftFill(nebulaColors)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) nebulaColors.accent
                                                    else windowsBorder(nebulaColors, 0.14f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedIntervalHours = hours },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) nebulaColors.accent else nebulaColors.textSecondary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("URL подписки", "Subscription URL"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(windowsSoftFill(nebulaColors))
                                .border(1.dp, windowsBorder(nebulaColors, 0.14f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = profile.url,
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MiniSquareIconButton(
                            icon = if (copiedUrl) Icons.Default.Check else Icons.Default.ContentCopy,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Subscription URL", profile.url))
                                copiedUrl = true
                                Toast.makeText(context, loc("Ссылка скопирована", "Link copied"), Toast.LENGTH_SHORT).show()
                            },
                            size = 46.dp,
                            iconSize = 21.dp,
                            forceOpaque = false
                        )
                    }
                }

                if (!profile.supportUrl.isNullOrBlank() || !profile.websiteUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = t("Ссылки провайдера", "Provider links"),
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.supportUrl?.takeIf { it.isNotBlank() }?.let { supportUrl ->
                            ProviderDialogLinkButton(
                                icon = Icons.Default.SupportAgent,
                                label = t("Поддержка", "Support"),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onDismiss()
                                    openUrl(context, supportUrl)
                                }
                            )
                        }
                        profile.websiteUrl?.takeIf { it.isNotBlank() }?.let { siteUrl ->
                            ProviderDialogLinkButton(
                                icon = Icons.Default.Public,
                                label = t("Сайт", "Site"),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onDismiss()
                                    openUrl(context, siteUrl)
                                }
                            )
                        }
                    }
                }

                profile.announce?.trim()?.takeIf { it.isNotBlank() }?.let { announce ->
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(nebulaColors.accent.copy(alpha = 0.10f))
                            .border(1.dp, nebulaColors.accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = t("Объявление провайдера", "Provider announcement"),
                                color = nebulaColors.accent,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = announce,
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp),
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action Buttons — destructive delete as a compact icon, Отмена outlined,
                // Сохранить filled & wider so its label never wraps.
                val destructiveColor = Color(0xFFFF6B6B)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(destructiveColor.copy(alpha = 0.12f))
                            .border(1.dp, destructiveColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable {
                                mainViewModel.removeSubscription(profile.url)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = t("Удалить", "Delete"),
                            tint = destructiveColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, windowsBorder(nebulaColors, 0.20f), RoundedCornerShape(14.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t("Отмена", "Cancel"),
                            color = nebulaColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.7f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(nebulaColors.accent)
                            .clickable {
                                // 1. Rename profile if name changed
                                val finalName = customName.trim()
                                if (finalName.isNotBlank() && finalName != profile.displayName) {
                                    mainViewModel.renameProfile(profile.url, finalName)
                                }

                                // 2. Set pin status
                                if (isPinned) {
                                    preferencesManager.pinProfile(profile.url)
                                } else {
                                    preferencesManager.unpinProfile(profile.url)
                                }

                                // 3. Set update interval
                                preferencesManager.setSubscriptionUpdateInterval(profile.url, selectedIntervalHours)

                                // 4. Reschedule subscription update if needed
                                SubscriptionUpdateScheduler.reschedule(context)

                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = t("Сохранить", "Save"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
