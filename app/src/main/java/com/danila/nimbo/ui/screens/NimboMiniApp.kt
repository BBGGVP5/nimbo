@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import androidx.compose.ui.platform.LocalConfiguration
import com.danila.nimbo.ui.components.contrastingLabel

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.selectableGroup

import com.danila.nimbo.ui.components.NimboToolMetric

import com.danila.nimbo.ui.components.NimboToolActions
import com.danila.nimbo.ui.components.NimboToolSection
import androidx.compose.ui.semantics.heading

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.danila.nimbo.ui.components.LocalFloatingNavHeight
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
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
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.danila.nimbo.service.SubscriptionUpdateScheduler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.network.PingProtocol
import com.danila.nimbo.network.PingDisplay
import com.danila.nimbo.network.ActiveProxyPing
import com.danila.nimbo.ui.components.PingValueContent
import com.danila.nimbo.ui.components.currentPingProtocol
import com.danila.nimbo.network.displayPingMs
import com.danila.nimbo.ui.components.NebulaInputField
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.model.Server
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.network.UpdateWorkScheduler
import com.danila.nimbo.ui.components.NimboPanel
import com.danila.nimbo.ui.components.NimboAction
import com.danila.nimbo.ui.components.NetworkSettingsRow
import com.danila.nimbo.ui.components.NetworkSettingsLabel
import com.danila.nimbo.ui.components.NetworkSettingsChoices
import com.danila.nimbo.ui.components.NetworkSettingsChoice
import com.danila.nimbo.ui.components.NetworkSettingsToggleRow
import com.danila.nimbo.ui.components.NetworkSettingsTextField
import com.danila.nimbo.ui.components.NimboConnectionControl
import com.danila.nimbo.ui.components.NimboSubscriptionHeader
import com.danila.nimbo.ui.components.DeleteProfileDialog
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.ExpressiveLoadingPane
import com.danila.nimbo.ui.components.EdgeBurstSource
import com.danila.nimbo.ui.components.EdgeBurstSourceShape
import com.danila.nimbo.ui.components.EdgeBurstSnapshot
import com.danila.nimbo.ui.components.EdgeBurstTrigger
import com.danila.nimbo.ui.components.LiquidGlassDepth
import com.danila.nimbo.ui.components.LiquidInteractionPolicy
import com.danila.nimbo.ui.components.LocalNetworkEdgeBurstEmitter
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.NimboStyleSwitch
import com.danila.nimbo.ui.components.NetworkEdgeBurstOverlay
import com.danila.nimbo.ui.components.NotificationType
import com.danila.nimbo.ui.components.QrCodeDisplayBottomSheet
import com.danila.nimbo.ui.components.QrScannerScreen
import com.danila.nimbo.ui.components.SubscriptionBrandLogo
import com.danila.nimbo.ui.screens.SubscriptionProfileMetadata
import com.danila.nimbo.ui.screens.toMetadata
import com.danila.nimbo.ui.components.ConnectionErrorDialog
import com.danila.nimbo.ui.components.TrafficDailyBars
import com.danila.nimbo.ui.components.TrafficSpeedChart
import com.danila.nimbo.utils.AppTrafficStats
import com.danila.nimbo.utils.TrafficHistory
import com.danila.nimbo.ui.components.UpdateDialog
import com.danila.nimbo.ui.components.PostUpdateDialog
import com.danila.nimbo.ui.components.cleanServerName
import com.danila.nimbo.ui.components.confirm
import com.danila.nimbo.ui.components.extractFlagEmoji
import com.danila.nimbo.ui.components.liquidGlassSurface
import com.danila.nimbo.ui.components.liquidTouchDeformation
import com.danila.nimbo.ui.components.rememberNetworkEdgeBurstController
import com.danila.nimbo.ui.components.rememberHapticSliderValueChange
import com.danila.nimbo.ui.components.performConnectionSuccessHaptic
import com.danila.nimbo.ui.components.tick
import com.danila.nimbo.ui.navigation.BottomBarScrollTracker
import com.danila.nimbo.shared.ui.backgroundPaletteColors
import com.danila.nimbo.shared.ui.drawNimboBackgroundMotion
import com.danila.nimbo.ui.components.dotPatternOverlay
import com.danila.nimbo.ui.components.dottedOutline
import com.danila.nimbo.ui.components.nimboControlBorderColor
import com.danila.nimbo.ui.components.nimboControlBorderWidth
import com.danila.nimbo.ui.components.nimboControlContainer
import com.danila.nimbo.ui.components.nimboControlShape
import com.danila.nimbo.ui.components.NimboChoiceOption
import com.danila.nimbo.ui.components.NimboExpandingChoiceCard
import com.danila.nimbo.ui.theme.BackgroundPaletteMode
import com.danila.nimbo.ui.theme.BackgroundStyleMode
import com.danila.nimbo.ui.theme.DEFAULT_COLOR_THEME_INDEX
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalBackgroundPaletteMode
import com.danila.nimbo.ui.theme.LocalBackgroundStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.backgroundPaletteModeForIndex
import com.danila.nimbo.ui.theme.backgroundStyleModeForIndex
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.BlendMode
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.shared.routing.NimboModule
import com.danila.nimbo.shared.routing.NimboModuleParser
import com.danila.nimbo.utils.AppIconManager
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.formatBytes
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnRecoveryStatus
import com.danila.nimbo.vpn.VpnState
import com.danila.nimbo.vpn.RoutingConfigurationApplier
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class MiniDestination {
    Home, Subscription, AppAccess, Settings,
    Theme, AppIcon, Language, PingSettings, About, Disclaimer, ConnectionId, Notifications, Updates, Logs,
    Routing, RoutingModules, Connections, Statistics, Firewall, WhitelistCheck, WhitelistPing,
    WhitelistHistory, CrossPlatformSync;

    fun isSettingsSubPage(): Boolean = when (this) {
        Theme, AppIcon, Language, PingSettings, About, ConnectionId, Notifications, Updates, Logs,
        Routing, RoutingModules, Connections, Statistics, Firewall, WhitelistCheck, WhitelistPing,
        WhitelistHistory, CrossPlatformSync -> true
        else -> false
    }

    /**
     * Y fraction on the Settings screen where this destination's source row sits.
     * Used as the transformOrigin for the open/close container-transform scale,
     * so the screen feels like it expands out of (and shrinks back into) the row.
     */
    fun settingsRowYFraction(): Float = when (this) {
        Language -> 0.22f
        Theme, AppIcon -> 0.30f
        PingSettings -> 0.38f
        Notifications -> 0.54f
        ConnectionId -> 0.62f
        Updates -> 0.70f
        About -> 0.78f
        Logs -> 0.86f
        WhitelistCheck, WhitelistPing, WhitelistHistory, CrossPlatformSync -> 0.50f
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
private enum class InterfacePreviewKind { IosLiquidGlass, MaterialYou, Dotted, Signal, Manga }

@Composable
fun NimboMiniApp(
    mainViewModel: MainViewModel,
    profiles: List<SubscriptionProfile>,
    onConnect: (Server) -> Unit,
    onAutoConnect: (List<Server>) -> Unit,
    onDisconnect: () -> Unit,
    onSubscriptionAdded: (String) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    initialScreen: String?
) {
    val context = LocalContext.current
    val lastPackageUpdateTime = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(System.currentTimeMillis())
    }
    // Должен быть тем же экземпляром, что и в MainActivity: так изменение
    // палитры/фона публикуется в NebulaGuardTheme в тот же кадр.
    val preferencesManager = LocalPreferencesManager.current
    val profilesMetadata by mainViewModel.profilesMetadataState.collectAsStateWithLifecycle()
    val isPinging by mainViewModel.isPinging.collectAsStateWithLifecycle()
    val edgeBurstController = rememberNetworkEdgeBurstController(
        EdgeBurstSnapshot(
            vpnState = VpnManager.state.value,
            isPinging = isPinging,
            isRefreshing = profiles.any { it.isLoading }
        )
    )
    val edgeBurstEmitter: (EdgeBurstTrigger, EdgeBurstSource?) -> Unit = remember(edgeBurstController) {
        { trigger, source -> edgeBurstController.emit(trigger, source) }
    }
    val statusParticlesEnabled by preferencesManager.statusParticlesEnabledState
    val edgeBurstAnimationsEnabled = false
    val bottomBarAutoHideEnabled by preferencesManager.bottomBarAutoHideEnabledState
    var bottomControlsVisible by remember { mutableStateOf(true) }
    var bottomControlsRevealJob by remember { mutableStateOf<Job?>(null) }
    val bottomControlsLastScheduleNanos = remember { longArrayOf(0L) }
    val bottomControlsScope = rememberCoroutineScope()

    fun scheduleBottomControlsReveal(delayMillis: Long = 420L) {
        val now = System.nanoTime()
        if (
            bottomControlsRevealJob?.isActive == true &&
            now - bottomControlsLastScheduleNanos[0] < 120_000_000L
        ) {
            return
        }
        bottomControlsLastScheduleNanos[0] = now
        bottomControlsRevealJob?.cancel()
        bottomControlsRevealJob = bottomControlsScope.launch {
            delay(delayMillis)
            bottomControlsVisible = true
        }
    }

    val bottomControlsScrollConnection = remember(bottomBarAutoHideEnabled) {
        object : NestedScrollConnection {
            private val scrollTracker = BottomBarScrollTracker()

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!bottomBarAutoHideEnabled || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                bottomControlsVisible = scrollTracker.visibleAfterScroll(
                    currentlyVisible = bottomControlsVisible,
                    availableY = available.y
                )
                scheduleBottomControlsReveal()
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                scrollTracker.reset()
                if (bottomBarAutoHideEnabled) {
                    delay(180L)
                    bottomControlsVisible = true
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

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
    var forceMihomoProfiles by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(destination) {
        if (destination != MiniDestination.Subscription) forceMihomoProfiles = false
    }
    LaunchedEffect(bottomBarAutoHideEnabled, destination) {
        bottomControlsRevealJob?.cancel()
        bottomControlsVisible = true
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
    // Mihomo subscription selection happens on the Profiles page. Observe that
    // selection here as well, so Home never keeps showing the previous Xray node.
    val externallySelectedServer = VpnManager.selectedServer
    LaunchedEffect(externallySelectedServer) {
        if (externallySelectedServer != null && selectedServer !== externallySelectedServer) {
            selectedServer = externallySelectedServer
            externallySelectedServer.profileUrl?.let { currentProfileUrl = it }
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAddAction by remember { mutableStateOf(AddProfileAction.Open) }
    var showQrScanner by remember { mutableStateOf(false) }
    var shouldScrollToSelectedServer by remember { mutableStateOf(false) }
    var showTvSheetUrl by remember { mutableStateOf<String?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    // Сцену после обновления нужно «забрать» сразу при первом запуске новой
    // сборки. Раньше флаг сбрасывался только после нажатия пользователя; при
    // повторном создании Activity после установки он успевал открыть ту же
    // сцену второй раз. Локальное состояние не прячет её в текущем окне.
    val postUpdatePromptPending = remember {
        preferencesManager.consumePostUpdateChangelogPrompt()
    }
    var showPostUpdatePrompt by remember {
        mutableStateOf(postUpdatePromptPending)
    }
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
    // is throttled to once every thirty minutes so a same-release APK replacement
    // becomes visible promptly without hitting the API on every recomposition or
    // process restart.
    LaunchedEffect(Unit) {
        if (!preferencesManager.showUpdateDialog) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val throttleMs = 30 * 60 * 1000L
        if (now - preferencesManager.lastUpdateCheckTime < throttleMs) return@LaunchedEffect

        // Do not mark a failed request as a completed check. Previously a
        // temporary DNS/network failure could hide updates for thirty minutes.
        val result = runCatching { UpdateManager.checkUpdateInBackground(context) }
        val info = result.getOrNull()
        if (result.isSuccess) {
            preferencesManager.lastUpdateCheckTime = System.currentTimeMillis()
        } else {
            Logger.e(
                "UPDATE",
                "Foreground update check failed; scheduling catch-up",
                result.exceptionOrNull() ?: IllegalStateException("Unknown update check error")
            )
            UpdateWorkScheduler.enqueueImmediate(context)
        }
        // Это может быть новая версия или исправленный APK той же версии.
        if (info != null) {
            val skipped = preferencesManager.updateDialogSkippedArtifactId
            if (skipped == null || skipped != info.artifactId || info.forceUpdate) {
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

    val actualConnectedServer by VpnManager.connectedServer
    LaunchedEffect(actualConnectedServer) {
        actualConnectedServer?.let { selectedServer = it }
    }
    val selectedAutoProfile by VpnManager.autoProfileUrl
    LaunchedEffect(selectedAutoProfile) {
        selectedAutoProfile?.let { currentProfileUrl = it }
    }

    fun selectServer(server: Server) {
        // IMPORTANT: store the exact same Server instance we received from the list.
        // We used to copy(profileUrl=…) here, which produced a Server with a different
        // selectionKey than the one rendered in ProxyList — so matchesSelection() never
        // matched it back and the row never lit up (most visible on auto-balancer rows
        // where profileUrl is null in the parsed subscription).
        selectedServer = server
        mainViewModel.selectManualServer(server)
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
        if (com.danila.nimbo.utils.ServerSwitchPolicy.shouldSwitch(vpnActive, preferencesManager.allowServerSwitchWhileConnected, isDifferentServer)) {
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
    val onOpenMihomoProfilesRemembered = remember {
        {
            forceMihomoProfiles = true
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
    val onAppIconClickRemembered = remember { { navigateTo(MiniDestination.AppIcon) } }
    val onAboutClickRemembered = remember { { navigateTo(MiniDestination.About) } }
    val onDisclaimerClickRemembered = remember { { showDisclaimer = true } }
    val onConnectionIdClickRemembered = remember { { navigateTo(MiniDestination.ConnectionId) } }
    val onNotificationsClickRemembered = remember { { navigateTo(MiniDestination.Notifications) } }
    val onUpdatesClickRemembered = remember { { navigateTo(MiniDestination.Updates) } }
    val onLogsClickRemembered = remember { { navigateTo(MiniDestination.Logs) } }
    val onRoutingClickRemembered = remember { { navigateTo(MiniDestination.Routing) } }
    val onStatsClickRemembered = remember { { navigateTo(MiniDestination.Statistics) } }
    val onWhitelistClickRemembered = remember { { navigateTo(MiniDestination.WhitelistCheck) } }
    val onCrossSyncClickRemembered = remember { { navigateTo(MiniDestination.CrossPlatformSync) } }
    val onScrollResetRemembered = remember { { shouldScrollToSelectedServer = false } }

    // Shared snapshot of the backdrop + current page. The floating bottom bar redraws
    // this layer blurred and clipped to its own bounds, so it frosts the real content
    // behind it (true glass) rather than getting an opaque fill or an extra glow layer.
    val backdropLayer = rememberGraphicsLayer()
    var floatingNavHeight by remember { mutableStateOf(132.dp) }

    val showBottomControls = destination == MiniDestination.Home ||
            destination == MiniDestination.Subscription ||
            destination == MiniDestination.AppAccess ||
            destination == MiniDestination.Connections ||
            destination == MiniDestination.Settings

    CompositionLocalProvider(com.danila.nimbo.ui.components.LocalNimboImportAction provides onNeedSubscriptionRemembered, LocalNetworkEdgeBurstEmitter provides edgeBurstEmitter, LocalFloatingNavHeight provides if (showBottomControls) floatingNavHeight else 0.dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(bottomControlsScrollConnection)
    ) {
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
                val duration = if (miniMotionEnabled) 140 else 0
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) using
                    SizeTransform(clip = false) { _, _ -> snap() }
            },
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxSize(),
            label = "mini-destination"
        ) { targetDestination ->
            when (targetDestination) {
                MiniDestination.Home -> NimboHomeScreen(
                    onSelectServer = onSelectServerRemembered,
                    mainViewModel = mainViewModel,
                    preferencesManager = preferencesManager,
                    selectedServer = selectedServer,
                    onOpenProfiles = onOpenProfilesRemembered,
                    onOpenMihomoProfiles = onOpenMihomoProfilesRemembered,
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
                    onAutoConnect = onAutoConnect,
                    onCancelAuto = onDisconnect,
                    onAddSubscription = onAddSubscriptionRemembered,
                    onProfileDeleted = onProfileDeleted,
                    onProfileRefresh = onProfileRefresh,
                    onShowTvQr = RoseOnly@{ url -> showTvSheetUrl = url },
                    onConnect = onConnectRemembered,
                    forceMihomoProfiles = forceMihomoProfiles,
                    onOpenMihomoProfiles = onOpenMihomoProfilesRemembered,
                    onCloseMihomoProfiles = { forceMihomoProfiles = false },
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
                    onAppIconClick = onAppIconClickRemembered,
                    onLanguageClick = onLanguageClickRemembered,
                    onAboutClick = onAboutClickRemembered,
                    onDisclaimerClick = onDisclaimerClickRemembered,
                    onConnectionIdClick = onConnectionIdClickRemembered,
                    onNotificationsClick = onNotificationsClickRemembered,
                    onUpdatesClick = onUpdatesClickRemembered,
                    onLogsClick = onLogsClickRemembered,
                    onRoutingClick = onRoutingClickRemembered,
                    onStatsClick = onStatsClickRemembered,
                    onWhitelistClick = onWhitelistClickRemembered,
                    onCrossSyncClick = onCrossSyncClickRemembered,
                    onRefreshFirstProfile = onProfileRefresh,
                    onShowTvQr = onShowTvQrRemembered,
                    onConnect = onConnectRemembered
                )

                MiniDestination.AppAccess -> AppProxySettingsScreen(
                    onNavigateBack = { navigateBackInMiniApp() },
                    showBack = true
                )

                MiniDestination.CrossPlatformSync -> CrossPlatformSyncScreen(
                    preferencesManager = preferencesManager,
                    mainViewModel = mainViewModel,
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Theme -> NimboThemeScreen(
                    preferencesManager = preferencesManager,
                    onBack = { navigateBackInMiniApp() },
                    onAppIconClick = onAppIconClickRemembered
                )

                MiniDestination.AppIcon -> AppIconSettingsScreen(
                    onNavigateBack = { navigateBackInMiniApp() }
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
                    onNavigateBack = { navigateBackInMiniApp() },
                    onOpenModules = { navigateTo(MiniDestination.RoutingModules) }
                )

                MiniDestination.RoutingModules -> RoutingModulesScreen(
                    preferencesManager = preferencesManager,
                    onBack = { navigateBackInMiniApp() }
                )

                MiniDestination.Connections -> NetworkIntelligenceScreen(
                    preferencesManager = preferencesManager,
                    servers = profiles.flatMap { it.servers },
                    onOpenFirewall = { navigateTo(MiniDestination.Firewall) },
                    onBack = { navigateBackInMiniApp() }
                )

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

                MiniDestination.WhitelistCheck -> ConnectivityDiagnosticsScreen(
                    onNavigateBack = { navigateBackInMiniApp() },
                    onNavigateToHistory = { navigateTo(MiniDestination.WhitelistHistory) },
                    onNavigateToPingTool = { navigateTo(MiniDestination.WhitelistPing) }
                )

                MiniDestination.WhitelistPing -> PingToolScreen(
                    mainViewModel = mainViewModel,
                    onNavigateBack = { navigateBackInMiniApp() }
                )

                MiniDestination.WhitelistHistory -> ConnectivityDiagnosticsHistoryScreen(
                    onNavigateBack = { navigateBackInMiniApp() }
                )
            }
        }
        }

        if (showBottomControls) {
            NimboBottomControls(
                backdropLayer = backdropLayer,
                onHeightMeasured = { floatingNavHeight = it },
                destination = destination,
                visible = bottomControlsVisible,
                onDestinationChange = { target ->
                    if (target == MiniDestination.Subscription && destination != MiniDestination.Subscription) {
                        subscriptionTab = MiniSubscriptionTab.Profiles
                    }
                    navigateTo(target)
                }
            )
        }

        NetworkEdgeBurstOverlay(
            event = edgeBurstController.event,
            enabled = edgeBurstAnimationsEnabled,
            modifier = Modifier.fillMaxSize()
        )

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

        // Ошибка подключения, из-за которой сервис сдался: показываем настоящую
        // причину, следующий шаг и кнопку сбора диагностики для поддержки.
        VpnManager.lastConnectionError.value?.let { failure ->
            ConnectionErrorDialog(
                failure = failure,
                onDismiss = { VpnManager.lastConnectionError.value = null },
                onCopied = { message ->
                    mainViewModel.showTopNotification(
                        message,
                        com.danila.nimbo.ui.components.NotificationType.SUCCESS
                    )
                }
            )
        }

        pendingUpdateInfo?.let { info ->
            UpdateDialog(
                updateInfo = info,
                onDismiss = {
                    preferencesManager.updateDialogSkippedVersion = info.versionName
                    preferencesManager.updateDialogSkippedArtifactId = info.artifactId
                    pendingUpdateInfo = null
                },
                onOpenHistory = {
                    pendingUpdateInfo = null
                    navigateTo(MiniDestination.Updates)
                }
            )
        }

        if (showPostUpdatePrompt) {
            PostUpdateDialog(
                versionName = preferencesManager.lastInstalledUpdateVersion.orEmpty(),
                changelog = preferencesManager.lastInstalledUpdateChangelog.orEmpty(),
                installedAt = lastPackageUpdateTime,
                onDismiss = {
                    preferencesManager.showPostUpdateChangelog = false
                    showPostUpdatePrompt = false
                },
                onShowChanges = {
                    preferencesManager.showPostUpdateChangelog = false
                    showPostUpdatePrompt = false
                    navigateTo(MiniDestination.Updates)
                }
            )
        }
    }
    }
}

/**
 * Прыжок значков нижней панели. Отдельно от движения фона: фон можно оставить
 * живым, а панель — спокойной, и наоборот.
 */
@Composable
private fun rememberNavIconMotionEnabled(): Boolean {
    // Именно общий экземпляр: у своего собственного состояние обновлялось бы
    // только по уведомлению, и выключатель до панели не доходил.
    val preferences = LocalPreferencesManager.current
    val enabled by preferences.navIconAnimationEnabledState
    val motionAllowed = rememberMiniMotionEnabled()
    return enabled && motionAllowed
}


/** Как именно оживает значок. Выбирается по смыслу вкладки, а не по порядку. */
private enum class NavIconMotionKind {
    /** Дом: подпрыгивает и мягко приседает при посадке. */
    HOP,
    /** Глобус: короткий оборот вокруг вертикальной оси. */
    SPIN,
    /** Телефон: покачивание из стороны в сторону. */
    WOBBLE,
    /** Шестерёнка: проворачивается на четверть оборота. */
    TURN
}

/** Состояние движения: размеры, подъём и поворот значка. */
private data class NavIconBounce(
    val scale: Float,
    val lift: Float,
    val rotation: Float = 0f,
    val squash: Float = 1f
)

/**
 * Короткий отклик значка на выбор вкладки.
 *
 * Подскок и поворот разведены намеренно: подскок обязан вернуться в исходное
 * состояние, а поворот — нет. Общий прогресс на оба сразу и обрывал движение:
 * на последнем кадре угол падал к нулю.
 */
@Composable
private fun rememberNavIconBounce(
    selected: Boolean,
    kind: NavIconMotionKind = NavIconMotionKind.HOP
): NavIconBounce {
    val enabled = rememberNavIconMotionEnabled()
    if (!enabled) return NavIconBounce(scale = 1f, lift = 0f)

    val density = androidx.compose.ui.platform.LocalDensity.current
    // Проигрыш идёт вперёд, от 0 к 1, и заканчивается там же, где начался.
    val play = remember { Animatable(0f) }
    // Угол накапливается: шестерёнка остаётся там, куда провернулась.
    val angle = remember { Animatable(0f) }
    // Положение покоя хранится отдельно от анимации. Читать его из самой
    // анимации нельзя: прерванное движение оставляло значок на случайном
    // угле, и этот угол становился новым «нулём».
    val restAngle = remember { mutableFloatStateOf(0f) }
    val navMotionScope = rememberCoroutineScope()
    // Первый проход — это появление значка на экране, а не выбор вкладки.
    val appeared = remember { mutableStateOf(false) }
    LaunchedEffect(selected, kind) {
        val firstPass = !appeared.value
        appeared.value = true
        if (!selected || firstPass) return@LaunchedEffect
        // Переключение вкладки не отменяет посадку предыдущего значка.
        // Scope живёт столько же, сколько панель, а не флаг selected.
        navMotionScope.launch {
        launch {
            play.snapTo(0f)
            play.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
        // Куда бы движение ни оборвалось, значок встаёт в это положение.
        val landing = when (kind) {
            NavIconMotionKind.SPIN -> restAngle.floatValue + 360f
            NavIconMotionKind.TURN -> restAngle.floatValue + 90f
            else -> restAngle.floatValue
        }
        restAngle.floatValue = landing
            when (kind) {
                NavIconMotionKind.SPIN ->
                    angle.animateTo(landing, tween(560, easing = FastOutSlowInEasing))
                NavIconMotionKind.TURN ->
                    angle.animateTo(landing, tween(520, easing = FastOutSlowInEasing))
                NavIconMotionKind.WOBBLE -> {
                    // Трубка качнулась и замерла ровно там, откуда пошла.
                    listOf(15f, -10f, 5f, 0f).forEach { offset ->
                        angle.animateTo(landing + offset, tween(115, easing = FastOutSlowInEasing))
                    }
                }
                NavIconMotionKind.HOP -> Unit
            }
        }
    }

    val value = play.value
    // Дуга: ноль в начале и в конце, единица в середине.
    val arc = kotlin.math.sin(value * kotlin.math.PI.toFloat())
    return when (kind) {
        NavIconMotionKind.HOP -> NavIconBounce(
            scale = 1f + 0.12f * arc,
            lift = with(density) { (-9).dp.toPx() } * arc,
            // В полёте значок вытягивается, на приземлении — приседает.
            squash = 1f - 0.12f * kotlin.math.sin(2f * kotlin.math.PI.toFloat() * value)
        )
        NavIconMotionKind.SPIN -> NavIconBounce(
            scale = 1f + 0.10f * arc,
            lift = 0f,
            rotation = angle.value
        )
        NavIconMotionKind.WOBBLE -> NavIconBounce(
            scale = 1f + 0.10f * arc,
            lift = with(density) { (-3).dp.toPx() } * arc,
            rotation = angle.value
        )
        NavIconMotionKind.TURN -> NavIconBounce(
            scale = 1f + 0.10f * arc,
            lift = 0f,
            rotation = angle.value
        )
    }
}

/** Движение подбирается по вкладке: так оно объясняет сам значок. */
private fun navMotionKind(destination: MiniDestination): NavIconMotionKind = when (destination) {
    MiniDestination.Home -> NavIconMotionKind.HOP
    MiniDestination.Subscription -> NavIconMotionKind.SPIN
    MiniDestination.AppAccess -> NavIconMotionKind.WOBBLE
    MiniDestination.Settings -> NavIconMotionKind.TURN
    // Остальные пункты в нижнюю панель не попадают, но перечисление общее.
    else -> NavIconMotionKind.HOP
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

/**
 * Непрерывно растущая фаза для фоновой анимации.
 *
 * Намеренно не используем `infiniteRepeatable`: при перезапуске 1 -> 0 частицы
 * (снег, искры, дождь) прыгали в начальные позиции. Кадровые часы дают
 * монотонное время, поэтому шва нет вообще, а неактивная анимация просто
 * останавливается на статичном кадре.
 *
 * Значение читается прямо внутри `Canvas { }`, то есть инвалидирует только
 * отрисовку — рекомпозиции на каждом кадре не происходит.
 */
@Composable
private fun rememberBackgroundPhase(enabled: Boolean, periodSeconds: Float = 24f): State<Float> {
    val phase = remember { mutableFloatStateOf(BACKGROUND_STATIC_PHASE) }
    LaunchedEffect(enabled, periodSeconds) {
        if (!enabled) {
            phase.floatValue = BACKGROUND_STATIC_PHASE
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                phase.floatValue =
                    BACKGROUND_STATIC_PHASE + (now - startNanos) / 1_000_000_000f / periodSeconds
            }
        }
    }
    return phase
}

/** Кадр, который показываем при выключенном движении фона. */
private const val BACKGROUND_STATIC_PHASE = 0.12f

@Composable
private fun NimboBackdrop() {
    Box(Modifier.fillMaxSize().background(LocalNebulaColors.current.background))
}

@Composable
private fun MaterialYouEmojiLayer(phase: State<Float>, isLight: Boolean) {
    val emojis = remember {
        listOf("✨", "🌐", "⚡", "🛡️", "☁️", "🔒", "🚀", "🔄", "🪄", "💠")
            .shuffled()
            .take(MATERIAL_YOU_EMOJI_SPOTS.size)
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        emojis.forEachIndexed { index, emoji ->
            val spot = MATERIAL_YOU_EMOJI_SPOTS[index]
            val fontSize = (27 + (index % 3) * 9).sp
            // offset позиционирует левый верхний угол: без поправки на кегль
            // правые эмодзи уезжали за границу экрана.
            val reserve = with(density) { fontSize.toDp() } * 1.4f
            Text(
                text = emoji,
                fontSize = fontSize,
                modifier = Modifier
                    .offset(
                        x = (boxWidth - reserve) * spot.first,
                        y = (boxHeight - reserve) * spot.second
                    )
                    .graphicsLayer {
                        // phase читается здесь, в фазе отрисовки: слой движется
                        // без рекомпозиции на каждом кадре.
                        val local = (phase.value * 0.6f + index * 0.17f) * (PI * 2.0).toFloat()
                        alpha = (if (isLight) 0.16f else 0.13f) + (index % 3) * 0.02f
                        translationX = sin(local) * 13.dp.toPx()
                        translationY = cos(local * 0.8f) * 17.dp.toPx()
                        rotationZ = sin(local) * 7f
                    }
            )
        }
    }
}

private val MATERIAL_YOU_EMOJI_SPOTS = listOf(
    0.07f to 0.13f,
    0.83f to 0.22f,
    0.14f to 0.47f,
    0.88f to 0.58f,
    0.34f to 0.78f,
    0.72f to 0.90f
)

@Composable
private fun NimboHomeScreen(
    onSelectServer: (Server) -> Unit,
    mainViewModel: MainViewModel,
    preferencesManager: PreferencesManager,
    selectedServer: Server?,
    onOpenProfiles: () -> Unit,
    onOpenMihomoProfiles: () -> Unit,
    onOpenServers: (Boolean) -> Unit,
    onRefreshProfile: (String) -> Unit,
    onOpenSupport: (SubscriptionProfile) -> Unit,
    onOpenSite: (SubscriptionProfile) -> Unit,
    onConnect: (Server) -> Unit,
    onDisconnect: () -> Unit,
    onNeedSubscription: () -> Unit,
    onAddMethod: (AddProfileAction) -> Unit = {}
) {
    val loadedProfiles by mainViewModel.profilesState.collectAsStateWithLifecycle()
    // Native companions are another representation of the same subscription, not a
    // second one-server subscription. Keep them available for the connection target.
    val profiles = remember(loadedProfiles) {
        loadedProfiles.filterNot { it.configType == "mihomo" && it.mihomoParentUrl != null }
    }
    val autoProgress by VpnManager.autoSelection
    val autoProfileUrl by VpnManager.autoProfileUrl
    val profile = loadedProfiles.firstOrNull { it.url == if (autoProgress.selected) autoProfileUrl else selectedServer?.profileUrl }
        ?: profiles.firstOrNull()
    val profileServer = profile?.servers?.firstOrNull { selectedServer?.matchesSelection(it) == true }
        ?: profile?.servers?.firstOrNull()
    val targetServer = VpnManager.connectedServer.value?.takeIf { VpnManager.state.value == VpnState.CONNECTED }
        ?: profileServer
    val coreRejection = if (VpnManager.state.value == VpnState.DISCONNECTED) targetServer?.let {
        com.danila.nimbo.vpn.VpnCorePolicy.rejection(preferencesManager.vpnCoreState.value, it)
    } else null
    val coreMismatch = coreRejection != null
    val noServer = targetServer == null && profile != null && VpnManager.state.value == VpnState.DISCONNECTED
    val needsServerChoice = coreMismatch || noServer
    val requestedCore = com.danila.nimbo.vpn.VpnCoreChoice.fromId(preferencesManager.vpnCoreState.value)
    val isPinging by mainViewModel.isPinging.collectAsStateWithLifecycle()
    val activePingKeys by mainViewModel.activePingKeys.collectAsStateWithLifecycle()
    val targetPinging = targetServer != null && activePingKeys.contains(targetServer.pingMeasurementKey())
    val vpnState = VpnManager.state.value
    val recoveryStatus = VpnManager.recoveryStatus.value
    val recoveryAttempt = VpnManager.recoveryAttempt.value
    val connectedSeconds = VpnManager.connectedSeconds.value
    val uploadSpeed = VpnManager.uploadSpeed.value
    val downloadSpeed = VpnManager.downloadSpeed.value
    val uploadedTotal = VpnManager.totalBytesUploaded.value
    val downloadedTotal = VpnManager.totalBytesDownloaded.value
    val showSpeed by preferencesManager.showSpeedState
    val showMemory by preferencesManager.memoryMonitoringState
    val compactConnectButton by preferencesManager.compactConnectButtonState
    val speedWidgetsVisible = showSpeed && vpnState == VpnState.CONNECTED
    val memoryWidgetVisible = showMemory && vpnState == VpnState.CONNECTED
    val speedSamples = rememberSpeedSamples(speedWidgetsVisible)
    val memoryState = rememberMemorySamples(memoryWidgetVisible)
    val colors = LocalNebulaColors.current

    LaunchedEffect(targetServer?.pingMeasurementKey(), vpnState) {
        val server = targetServer
        if (server != null && !com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(server) &&
            server.ping == null && vpnState == VpnState.DISCONNECTED && !isPinging) {
            mainViewModel.pingSingleServer(server)
        }
    }

    val onConnectToggle: () -> Unit = {
        when {
            recoveryStatus != VpnRecoveryStatus.IDLE -> onDisconnect()
            vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING -> onDisconnect()
            needsServerChoice -> if (requestedCore == com.danila.nimbo.vpn.VpnCoreChoice.MIHOMO) onOpenMihomoProfiles() else onOpenProfiles()
            else -> {
                val server = targetServer
                if (server == null) onOpenProfiles() else onConnect(server)
            }
        }
    }
    val instruction = when {
        recoveryStatus != VpnRecoveryStatus.IDLE -> connectionStatusText(vpnState, connectedSeconds, recoveryStatus, recoveryAttempt)
        vpnState == VpnState.CONNECTED -> formatDuration(connectedSeconds) + t(" · нажмите, чтобы отключить", " · tap to disconnect")
        vpnState == VpnState.CONNECTING -> t("Подключение · нажмите для отмены", "Connecting · tap to cancel")
        needsServerChoice -> t("Нажмите, чтобы выбрать сервер", "Tap to choose a server")
        else -> t("Нажмите, чтобы подключиться", "Tap to connect")
    }
    NimboSubscriptionPullRefresh(profiles.any { it.isLoading }, profiles.isNotEmpty(), {
        profiles.filterNot { it.isLoading }.forEach { onRefreshProfile(it.url) }
    }) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = LocalFloatingNavHeight.current + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        com.danila.nimbo.ui.components.NimboBrandHeader(onNeedSubscription)
        Spacer(Modifier.height(24.dp))
        Text("•\u00A0\u00A0" + when {
                vpnState == VpnState.CONNECTED -> t("ЗАЩИЩЁННОЕ СОЕДИНЕНИЕ", "SECURE CONNECTION")
                vpnState == VpnState.CONNECTING -> t("УСТАНАВЛИВАЕМ СОЕДИНЕНИЕ", "ESTABLISHING CONNECTION")
                needsServerChoice -> t("НУЖЕН СОВМЕСТИМЫЙ СЕРВЕР", "COMPATIBLE SERVER NEEDED")
                else -> t("ГОТОВО К ПОДКЛЮЧЕНИЮ", "READY TO CONNECT")
            }, modifier = Modifier.fillMaxWidth(), fontSize = 10.sp,
            textAlign = TextAlign.Center, color = colors.textSecondary)
        Spacer(Modifier.height(13.dp))
        com.danila.nimbo.ui.components.NimboConnectionHeading(when {
            vpnState == VpnState.CONNECTED -> t("Вы подключены", "You are connected")
            vpnState == VpnState.CONNECTING -> t("Подключение", "Connecting")
            needsServerChoice -> t("Выберите сервер", "Choose a server")
            else -> t("Интернет без границ", "Internet without borders")
        })
        Text(if (vpnState == VpnState.CONNECTED)
                t("Ваш трафик идёт через выбранный сервер", "Your traffic goes through the selected server")
            else if (needsServerChoice) t("Подключение начнётся после выбора совместимого сервера", "Select a compatible server before connecting")
            else t("Один шаг до защищённого соединения", "One step to a secure connection"),
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
        Spacer(Modifier.height(22.dp))
        val compactLabel = when {
            recoveryStatus != VpnRecoveryStatus.IDLE -> instruction
            vpnState == VpnState.CONNECTED -> t("Отключить · ${formatDuration(connectedSeconds)}", "Disconnect · ${formatDuration(connectedSeconds)}")
            vpnState == VpnState.CONNECTING -> t("Отменить подключение", "Cancel connection")
            needsServerChoice -> t("Выбрать сервер", "Choose server")
            else -> t("Подключить", "Connect")
        }
        NimboConnectionControl(vpnState == VpnState.CONNECTED, vpnState == VpnState.CONNECTING,
            onConnectToggle, modifier = Modifier.fillMaxWidth(), compact = compactConnectButton,
            statusText = if (compactConnectButton) compactLabel else instruction)
        Spacer(Modifier.height(16.dp))
        if (profile == null) {
            EmptyHomeProfilePanel(onAction = onAddMethod)
        } else if (needsServerChoice) {
            NimboPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(when {
                        coreRejection == com.danila.nimbo.vpn.VpnCorePolicy.Rejection.UNAVAILABLE -> t("Ядро недоступно", "Core unavailable")
                        requestedCore == com.danila.nimbo.vpn.VpnCoreChoice.MIHOMO -> t("Нужен сервер Mihomo", "Mihomo server needed")
                        noServer -> t("Сервер не выбран", "No server selected")
                        else -> t("Нужен сервер для ${requestedCore?.title ?: "VPN"}", "Server for ${requestedCore?.title ?: "VPN"} needed")
                    },
                        style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(if (coreRejection == com.danila.nimbo.vpn.VpnCorePolicy.Rejection.UNAVAILABLE)
                        t("Ядро Mihomo недоступно в этой сборке. Проверьте установку приложения.",
                            "Mihomo is unavailable in this build. Check the app installation.")
                    else if (requestedCore == com.danila.nimbo.vpn.VpnCoreChoice.MIHOMO)
                        t("Обычные серверы здесь не подключаются. Откройте профили Mihomo и выберите сервер в группе.",
                            "Regular servers cannot connect with this core. Open Mihomo profiles and select a server in a group.")
                    else if (noServer) t("Откройте профили и выберите сервер для подключения.", "Open profiles and select a server to connect.")
                    else t("Текущий сервер не подходит для ядра ${requestedCore?.title ?: "VPN"}. Выберите другой сервер или переключите ядро.",
                        "The current server does not match the ${requestedCore?.title ?: "VPN"} core. Choose another server or switch cores."),
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    Button(onClick = { if (requestedCore == com.danila.nimbo.vpn.VpnCoreChoice.MIHOMO) onOpenMihomoProfiles() else onOpenProfiles() }) {
                        Text(if (requestedCore == com.danila.nimbo.vpn.VpnCoreChoice.MIHOMO) t("Выбрать сервер Mihomo", "Choose a Mihomo server")
                            else t("Выбрать сервер", "Choose a server"))
                    }
                }
            }
        } else {
            val mihomoHome = com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile)
            if (mihomoHome) {
                MihomoHomeSelectedServerBar(profile, preferencesManager,
                    vpnState == VpnState.CONNECTED, onOpenMihomoProfiles)
            } else {
                val pingDisplayMode by preferencesManager.pingDisplayModeState
                WindowsSelectedServerBar(targetServer, preferencesManager,
                    onServerClick = { if (targetServer == null) onNeedSubscription() else onOpenServers(true) },
                    onListClick = { onOpenServers(false) }, isPinging = targetPinging,
                    pingDisplayMode = pingDisplayMode, autoSelected = autoProgress.selected,
                    connected = vpnState == VpnState.CONNECTED, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Мои подписки", "My subscriptions"), Modifier.weight(1f),
                    color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                TextButton(if (mihomoHome) onOpenMihomoProfiles else onOpenProfiles) {
                    Text(t("Все профили", "All profiles"), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp), tint = colors.textSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            homeSubscriptionProfiles(profiles, profile).forEachIndexed { index, subscription ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                val native = com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(subscription)
                val source = if (native) loadedProfiles.firstOrNull { it.url == subscription.mihomoParentUrl }
                    ?: subscription else subscription
                val openSubscription = if (native) onOpenMihomoProfiles else onOpenProfiles
                SubscriptionOverviewPanel(onSelectServer, mainViewModel, preferencesManager, subscription, source,
                    selectedServer, openSubscription, openSubscription, { onRefreshProfile(subscription.url) },
                    { onOpenSupport(source) }, { onOpenSite(source) })
            }
            if (speedWidgetsVisible) {
                Spacer(Modifier.height(18.dp))
                NetworkSpeedChartCard(speedSamples, uploadSpeed, downloadSpeed,
                    uploadedTotal, downloadedTotal, Modifier.fillMaxWidth())
            }
            if (memoryWidgetVisible) {
                Spacer(Modifier.height(16.dp))
                MemoryUsageCard(memoryState.currentMb, memoryState.samples, Modifier.fillMaxWidth())
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
                .padding(horizontal = 16.dp),
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionOverviewPanel(
    onSelectServer: (Server) -> Unit,
    mainViewModel: MainViewModel,
    preferencesManager: PreferencesManager,
    profile: SubscriptionProfile,
    displayProfile: SubscriptionProfile,
    selectedServer: Server?,
    onOpenProfiles: () -> Unit,
    onOpenServers: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSite: () -> Unit
) {
    val native = com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile)
    var nativeServerCount by remember(profile.url, profile.rawConfig) { mutableIntStateOf(0) }
    LaunchedEffect(profile.url, profile.rawConfig, native) {
        if (native) nativeServerCount = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.danila.nimbo.mihomo.MihomoBridge.inspect(profile.rawConfig.orEmpty())
                    .getAsJsonObject("declaredGraph")?.getAsJsonArray("proxies")?.size() ?: 0
            }.getOrDefault(0)
        }
    }
    val context = LocalContext.current
    val nativeChoice = if (native) profile.rawConfig?.takeIf(String::isNotBlank)
        ?.let(com.danila.nimbo.mihomo.MihomoProtocol::sourceHash)
        ?.let { com.danila.nimbo.mihomo.MihomoGroupChoices.last(context, it) } else null
    val pingUrl by preferencesManager.pingUrlState
    val nativePing = nativeChoice?.let { choice ->
        choice.fingerprint?.let { fingerprint ->
            com.danila.nimbo.network.MihomoPingCache.read(context, profile.url, pingUrl.trim(),
                mapOf(choice.name to fingerprint))[choice.name]
        }
    }
    val autoProgress by VpnManager.autoSelection
    val autoProfileUrl by VpnManager.autoProfileUrl
    var expanded by rememberSaveable(profile.url) { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val colors = LocalNebulaColors.current
    val isPinging by mainViewModel.isPinging.collectAsStateWithLifecycle()
    val showLogo by preferencesManager.showSubscriptionLogoState
    com.danila.nimbo.ui.components.NimboSubscriptionPanel(expanded, { expanded = !expanded }, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NimboSubscriptionHeader(displayProfile.displayName.removeSuffix(" · Mihomo").ifBlank { t("Подписка", "Subscription") },
                if (native && nativeServerCount == 0) "Mihomo" else
                    t(serverCountRu(if (native) nativeServerCount else profile.servers.size),
                        serverCountEn(if (native) nativeServerCount else profile.servers.size)),
                expanded, null, { info = true }, logo = {
                    if (showLogo && !displayProfile.brandLogo.isNullOrBlank()) SubscriptionBrandLogo(displayProfile.brandLogo, displayProfile.brandLogoCache, size = 36.dp)
                    else Box(Modifier.size(37.dp).background(colors.textPrimary, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Text(displayProfile.displayName.take(2).uppercase(), color = colors.background,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                })
            SubscriptionDescription(displayProfile.announce, expanded)
            SubscriptionQuotaSummary(displayProfile)
            SubscriptionActionsRow(preferencesManager.getLastSubscriptionUpdateTime(
                if (native) profile.mihomoSourceUrl ?: profile.url else profile.url),
                if (native) false else isPinging, profile.isLoading,
                { if (native) onOpenServers() else mainViewModel.pingServers(profile.servers) }, onRefresh,
                pingLabel = if (native) t("Открыть Nimbo Ping", "Open Nimbo Ping")
                    else t("Проверить пинг", "Check ping"))
            if (!profile.error.isNullOrBlank()) Text(sanitizeProfileErrorForUi(profile.error).orEmpty(), color = colors.statusError, style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                if (profile.servers.isNotEmpty() && !com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile)) NimboAutoFastestRow(autoProgress,
                    selected = autoProgress.selected && autoProfileUrl == profile.url,
                    onClick = { mainViewModel.selectAuto(profile) })
                if (native) {
                    nativeChoice?.let { choice ->
                        com.danila.nimbo.ui.components.NimboSubscriptionServerRow(
                            title = choice.name, subtitle = choice.group, flag = extractFlagEmoji(choice.name),
                            selected = true, onClick = onOpenServers,
                            latency = { Text(nativePing?.let {
                                com.danila.nimbo.network.displayPingLabel(it, PingProtocol.NIMBO.id) + " ms"
                            } ?: "—", color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall) })
                    }
                } else {
                    val hidden = preferencesManager.getHiddenServerKeys()
                    val visible = profile.servers.filterNot { hidden.contains(it.pingKey()) }
                    if (visible.isEmpty()) Text(t("Серверов пока нет", "No servers yet"), color = colors.textSecondary)
                    visible.take(6).forEach { server ->
                        val pingDisplayMode by preferencesManager.pingDisplayModeState
                        com.danila.nimbo.ui.components.NimboSubscriptionServerRow(
                            title = serverUiTitle(preferencesManager, server),
                            subtitle = serverSubtitle(server), flag = extractFlagEmoji(server.name),
                            selected = !autoProgress.selected && selectedServer?.matchesSelection(server) == true,
                            onClick = { onSelectServer(server) },
                            latency = { PingValueContent(server.ping ?: -1, pingDisplayMode, colors.textSecondary) }
                        )
                    }
                }
                NimboAction(Icons.AutoMirrored.Filled.List, t("Все серверы", "All servers"), onOpenServers, Modifier.fillMaxWidth())
            }
        }
    }
    if (info) SubscriptionDetailsDialog(displayProfile, { info = false }, onOpenSupport, onOpenSite,
        serverCountOverride = if (native) nativeServerCount else null)

}

@Composable
private fun MihomoHomeSelectedServerBar(
    profile: SubscriptionProfile,
    preferencesManager: PreferencesManager,
    connected: Boolean,
    onOpenServers: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val sourceHash = remember(profile.rawConfig) {
        profile.rawConfig?.takeIf { it.isNotBlank() }?.let(com.danila.nimbo.mihomo.MihomoProtocol::sourceHash)
    }
    val lastChoice = sourceHash?.let { com.danila.nimbo.mihomo.MihomoGroupChoices.last(context, it) }
    var liveSelection by remember(profile.url) {
        mutableStateOf<com.danila.nimbo.mihomo.MihomoActiveSelection?>(null)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(profile.url, sourceHash, connected, lastChoice?.group, lifecycle) {
        liveSelection = null
        if (!connected || sourceHash == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) {
                    liveSelection = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val status = com.danila.nimbo.mihomo.MihomoBridge.response("status")
                            if (status.data.get("state")?.asString != "running" ||
                                status.data.get("sourceSHA256")?.asString != sourceHash) return@runCatching null
                            val snapshot = com.danila.nimbo.mihomo.MihomoBridge.response("snapshot",
                                generation = status.generation)
                            com.danila.nimbo.mihomo.mihomoActiveSelection(snapshot.data, lastChoice?.group)
                        }.getOrNull()
                    }
                    delay(3000)
                }
            } finally { liveSelection = null }
        }
    }
    val pingUrl by preferencesManager.pingUrlState
    val savedPing = lastChoice?.let { choice ->
        choice.fingerprint?.let { fingerprint ->
            com.danila.nimbo.network.MihomoPingCache.read(context, profile.url, pingUrl.trim(),
                mapOf(choice.name to fingerprint))[choice.name]
        }
    }
    val presentation = com.danila.nimbo.mihomo.mihomoHomePresentation(
        liveSelection, lastChoice?.name, lastChoice?.group, connected,
        english = t("ru", "en") == "en")
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.divider)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onOpenServers, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                color = Color.Transparent) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(colors.controlFill, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        if (lastChoice == null && liveSelection == null) Icon(Icons.Default.AutoAwesome, null,
                            Modifier.size(20.dp), tint = colors.accent)
                        else com.danila.nimbo.ui.components.NimboServerFlag(
                            extractFlagEmoji(liveSelection?.member ?: lastChoice?.name.orEmpty()))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(presentation.title,
                            style = MaterialTheme.typography.titleSmall, color = colors.textPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(presentation.subtitle,
                            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    if (savedPing != null) Text(
                        com.danila.nimbo.network.displayPingLabel(savedPing, PingProtocol.NIMBO.id) + " ms",
                        style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
            }
            IconButton(onOpenServers, Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    t("Список серверов", "Server list"), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun EmptyHomeProfilePanel(onAction: (AddProfileAction) -> Unit) {
    NimboPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Добавьте первую подписку", "Add your first subscription"),
                style = MaterialTheme.typography.titleMedium, color = LocalNebulaColors.current.textPrimary)
            Text(t("Импортируйте ссылку, файл или QR-код от вашего провайдера.", "Import a link, file or QR code from your provider."),
                style = MaterialTheme.typography.bodyMedium, color = LocalNebulaColors.current.textSecondary)
            AddSubscriptionPrimaryAction { onAction(AddProfileAction.Open) }
            NimboAction(Icons.Default.ContentPaste, t("Из буфера обмена", "From clipboard"), { onAction(AddProfileAction.Paste) }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NimboAction(Icons.Default.FileUpload, t("Файл", "File"), { onAction(AddProfileAction.File) }, Modifier.weight(1f))
                NimboAction(Icons.Default.QrCodeScanner, t("QR-код", "QR code"), { onAction(AddProfileAction.Qr) }, Modifier.weight(1f))
            }
        }
    }

}

@Composable
private fun AddSubscriptionPrimaryAction(onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent,
            contentColor = com.danila.nimbo.ui.components.contrastingLabel(colors.accent))) {
        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(t("Добавить подписку", "Add subscription"))
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val materialYou = nebulaColors.isMaterialYou
    val haptic = LocalHapticFeedback.current
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !nebulaColors.isLiquidGlass) 0.97f else 1f,
        animationSpec = if (miniMotionEnabled) tween(100, easing = FastOutSlowInEasing) else snap(),
        label = "add-method-scale"
    )
    val controlShape = nimboControlShape(16.dp, 2.dp)
    Box(
        modifier = modifier
            .height(62.dp)
            .scale(scale)
            .then(
                if (nebulaColors.isLiquidGlass) {
                    Modifier.liquidTouchDeformation(LiquidGlassDepth.CONTROL)
                } else {
                    Modifier
                }
            )
            .clip(controlShape)
            .background(
                nimboControlContainer(
                    nebulaColors.accent.copy(alpha = 0.08f),
                    selected = false
                )
            )
            .border(
                nimboControlBorderWidth(),
                nimboControlBorderColor(nebulaColors.accent.copy(alpha = 0.18f)),
                controlShape
            )
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

internal data class MiniSpeedSample(val upload: Long, val download: Long)
private data class MiniMemoryState(val currentMb: Long, val samples: List<Long>)

@Composable
private fun rememberSpeedSamples(active: Boolean): List<MiniSpeedSample> {
    // A new connection starts a new graph instead of retaining the previous session.
    var samples by remember(active) { mutableStateOf(emptyList<MiniSpeedSample>()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                samples = (samples + MiniSpeedSample(VpnManager.uploadSpeed.value, VpnManager.downloadSpeed.value))
                    .takeLast(61)
                delay(1000)
            }
        }
    }
    return samples
}

@Composable
private fun rememberMemorySamples(active: Boolean): MiniMemoryState {
    var currentMb by remember(active) { mutableStateOf(if (active) currentAppMemoryMb() else 0L) }
    var samples by remember(active) { mutableStateOf(emptyList<Long>()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val usedMb = currentAppMemoryMb()
                currentMb = usedMb
                samples = (samples + usedMb).takeLast(32)
                delay(2000)
            }
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
    NimboConnectionControl(state == VpnState.CONNECTED, state == VpnState.CONNECTING, onClick, modifier)

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
    NimboConnectionControl(state == VpnState.CONNECTED, state == VpnState.CONNECTING,
        onClick, modifier.fillMaxWidth(), compact = true,
        statusText = connectionStatusText(state, connectedSeconds, recoveryStatus, recoveryAttempt))

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

/**
 * Сохранённый режим выбора, не разовая проверка и не команда подключения.
 *
 * Стоит там же, где человек выбирает сервер: «авто» — это ещё один вариант
 * выбора, а не настройка экраном глубже. Оформление берётся у строки
 * выбранного сервера, поэтому строка не выпадает из включённого стиля.
 */
@Composable
private fun NimboAutoFastestRow(
    progress: com.danila.nimbo.vpn.AutoSelectionProgress,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = when {
        selected && progress.running -> t("Подключаемся к рабочей локации…", "Connecting to a working location…")
        selected && progress.confirmed -> t("${progress.serverName ?: "Подключено"} · смена при сбое",
            "${progress.serverName ?: "Connected"} · switches on failure")
        selected && progress.failed -> t("Нет доступной локации. Повторите подключение.", "No available location. Try connecting again.")
        selected -> t("Выбрано · нажмите кнопку подключения", "Selected · press Connect")
        else -> t("Рабочая локация · автоматическая замена при сбое", "Working location · automatic failover")
    }
    com.danila.nimbo.ui.components.NimboAutoChoice(selected, subtitle, onClick, modifier,
        busy = selected && progress.running)
}

@Composable
private fun WindowsSelectedServerBar(
    server: Server?,
    preferencesManager: PreferencesManager,
    onServerClick: () -> Unit,
    onListClick: () -> Unit,
    isPinging: Boolean = false,
    pingDisplayMode: Int = 0,
    autoSelected: Boolean = false,
    connected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalNebulaColors.current
    Column(modifier) {
        HorizontalDivider(color = colors.divider)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onServerClick, modifier = Modifier.weight(1f).heightIn(min = 56.dp), color = Color.Transparent) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(colors.controlFill, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        if (autoSelected) Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = colors.accent)
                        else com.danila.nimbo.ui.components.NimboServerFlag(server?.let { extractFlagEmoji(it.name) }.orEmpty())
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (autoSelected && connected && server != null)
                            t("Авто · ${serverUiTitle(preferencesManager, server)}",
                                "Auto · ${serverUiTitle(preferencesManager, server)}")
                            else if (autoSelected) t("Авто · лучшая доступная", "Auto · best available")
                            else server?.let { serverUiTitle(preferencesManager, it) } ?: t("Выберите сервер", "Choose server"),
                            style = MaterialTheme.typography.titleSmall, color = if (autoSelected) colors.accent else colors.textPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (autoSelected || server != null) Text(if (autoSelected) {
                            if (connected && server != null) t("Подключено · смена при сбое", "Connected · switches on failure")
                            else t("Выбрано · подключим рабочую локацию", "Selected · we’ll connect an available location")
                        } else serverSubtitle(server!!),
                            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    if (server != null && (!autoSelected || connected)) {
                        Spacer(Modifier.width(8.dp))
                        if (isPinging) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.textSecondary)
                        else if (pingDisplayMode == 2) WindowsPingPill(server.ping ?: -1, false, pingDisplayMode)
                        else PingValueContent(server.ping ?: -1, pingDisplayMode, colors.textSecondary)
                    }
                }
            }
            IconButton(onListClick, Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Список серверов", "Server list"), tint = colors.textSecondary)
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
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    val border = windowsBorder(nebulaColors)
    val fill = windowsControlFill(nebulaColors)
    val shape = nimboControlShape(16.dp, 2.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp),
        singleLine = true,
        shape = shape,
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
            focusedBorderColor = if (mangaStyle) nebulaColors.accent else if (nebulaColors.isLight) Color(0xFFD8D5E2) else Color.White.copy(alpha = 0.14f),
            unfocusedBorderColor = if (mangaStyle) nebulaColors.panelBorder else border,
            focusedContainerColor = if (mangaStyle) nebulaColors.controlFill else fill,
            unfocusedContainerColor = if (mangaStyle) nebulaColors.controlFill else fill,
            cursorColor = nebulaColors.accent,
            focusedTextColor = nebulaColors.textPrimary,
            unfocusedTextColor = nebulaColors.textPrimary
        )
    )
}

@Composable
internal fun NetworkSpeedChartCard(
    samples: List<MiniSpeedSample>, uploadSpeed: Long, downloadSpeed: Long,
    uploadedTotal: Long, downloadedTotal: Long, modifier: Modifier = Modifier
) {
    val colors = LocalNebulaColors.current
    NimboPanel(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(t("Скорость соединения", "Connection speed"), color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeChartMetric(t("↓ Загрузка", "↓ Download"), formatBytes(downloadSpeed) + t("/с", "/s"), Modifier.weight(1f))
                HomeChartMetric(t("↑ Отдача", "↑ Upload"), formatBytes(uploadSpeed) + t("/с", "/s"), Modifier.weight(1f), muted = true)
            }
            Spacer(Modifier.height(10.dp))
            SpeedChartCanvas(samples, Modifier.fillMaxWidth().height(56.dp).testTag("home-speed-graph"))
            HomeChartTimeAxis((samples.size - 1).coerceAtLeast(0))
            Text(t("За сессию: ↓ ", "Session: ↓ ") + formatBytes(downloadedTotal) + " · ↑ " + formatBytes(uploadedTotal),
                modifier = Modifier.padding(top = 8.dp), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeChartMetric(label: String, value: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    val colors = LocalNebulaColors.current
    Column(modifier) {
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = if (muted) colors.textSecondary else colors.textPrimary,
            fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HomeChartTimeAxis(observedSeconds: Int) {
    val colors = LocalNebulaColors.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (observedSeconds > 0) t("−$observedSeconds с", "−$observedSeconds s") else "—",
            style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        if (observedSeconds > 1) Text(t("−${observedSeconds / 2} с", "−${observedSeconds / 2} s"),
            style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        Text(t("Сейчас", "Now"), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
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
    val colors = LocalNebulaColors.current
    Canvas(modifier) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(colors.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (samples.size < 2) return@Canvas
        val peak = samples.maxOf { maxOf(it.upload, it.download) }.coerceAtLeast(1L).toFloat()
        fun trace(selector: (MiniSpeedSample) -> Long): Path = Path().apply {
            samples.forEachIndexed { index, sample ->
                val x = size.width * index / (samples.size - 1)
                val y = size.height - selector(sample).coerceAtLeast(0).toFloat() / peak * size.height * 0.9f
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val down = trace { it.download }
        val up = trace { it.upload }
        val fill = Path().apply { addPath(down); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.13f), Color.Transparent)))
        drawPath(down, colors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(up, colors.textSecondary, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
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
internal fun MemoryUsageCard(memoryMb: Long, samples: List<Long>, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    NimboPanel(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(t("Память приложения", "App memory"), color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeChartMetric(t("Используется", "Used"), "$memoryMb " + t("МБ", "MB"), Modifier.weight(1f))
                HomeChartMetric(t("Пик окна", "Window peak"), "${samples.maxOrNull() ?: memoryMb} " + t("МБ", "MB"),
                    Modifier.weight(1f), muted = true)
            }
            Spacer(Modifier.height(10.dp))
            MemoryChartCanvas(samples, Modifier.fillMaxWidth().height(40.dp).testTag("home-memory-graph"))
            HomeChartTimeAxis((samples.size - 1).coerceAtLeast(0) * 2)
        }
    }
}

@Composable
private fun MemoryChartCanvas(samples: List<Long>, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Canvas(modifier) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(colors.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (samples.size < 2) return@Canvas
        val maximum = samples.maxOrNull()!!.coerceAtLeast(1).toFloat()
        val minimum = samples.minOrNull()!!.toFloat()
        val span = (maximum - minimum).coerceAtLeast(maximum * 0.35f).coerceAtLeast(1f)
        val path = Path().apply {
            samples.forEachIndexed { index, value ->
                val x = size.width * index / (samples.size - 1)
                val y = size.height * (0.82f - (value - minimum) / span * 0.64f)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.13f), Color.Transparent)))
        drawPath(path, colors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
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
    val edgeBurstEmitter = LocalNetworkEdgeBurstEmitter.current
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    var edgeBurstSource by remember { mutableStateOf<EdgeBurstSource?>(null) }
    val edgeBurstOutsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 3.dp.toPx() }
    val buttonShape = RoundedCornerShape(if (dottedStyle) size * 0.16f else size * 0.30f)
    val onClickWithBurst: () -> Unit = {
        when (motion) {
            MiniIconMotion.Ping -> edgeBurstEmitter(EdgeBurstTrigger.PING, edgeBurstSource)
            MiniIconMotion.Refresh -> edgeBurstEmitter(EdgeBurstTrigger.REFRESH, edgeBurstSource)
            MiniIconMotion.None -> Unit
        }
        onClick()
    }
    GlassPanel(
        // Radius scales with size so small (36–40dp) buttons read as rounded squares
        // rather than circles, consistent with the larger control buttons.
        modifier = Modifier
            .size(size.coerceAtLeast(48.dp))
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionInRoot()
                edgeBurstSource = EdgeBurstSource(
                    center = topLeft + Offset(
                        coordinates.size.width / 2f,
                        coordinates.size.height / 2f
                    ),
                    halfWidth = coordinates.size.width / 2f,
                    halfHeight = coordinates.size.height / 2f,
                    shape = EdgeBurstSourceShape.ROUNDED_RECT,
                    outsetPx = edgeBurstOutsetPx
                )
            },
        shape = buttonShape,
        borderColor = Color.White.copy(alpha = 0.10f),
        forceOpaque = forceOpaque
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MiniIconButton(
                icon = icon,
                onClick = onClickWithBurst,
                size = size.coerceAtLeast(48.dp),
                iconSize = iconSize,
                motion = motion,
                active = active,
                shape = buttonShape
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
            isPinging = isPinging,
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
    onAutoConnect: (List<Server>) -> Unit,
    onCancelAuto: () -> Unit,
    onAddSubscription: () -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    onShowTvQr: (String) -> Unit,
    onConnect: (Server) -> Unit,
    forceMihomoProfiles: Boolean,
    onOpenMihomoProfiles: () -> Unit,
    onCloseMihomoProfiles: () -> Unit,
    onOpenUrl: (Context, String?) -> Unit,
    showBack: Boolean = true,
    scrollToSelected: Boolean = false,
    onScrollReset: () -> Unit = {}
) {
    val coreId = preferencesManager.vpnCoreState.value
    if (forceMihomoProfiles || coreId == com.danila.nimbo.vpn.VpnCoreChoice.MIHOMO.id ||
        (coreId == com.danila.nimbo.vpn.VpnCoreChoice.AUTO.id && selectedServer?.protocol.equals("mihomo", true))) {
        MihomoProxiesScreen(onAddSubscription = onAddSubscription)
        return
    }
    val miniMotionEnabled = rememberMiniMotionEnabled()
    val loadedProfiles by mainViewModel.profilesState.collectAsStateWithLifecycle()
    val profiles = remember(loadedProfiles) { loadedProfiles.filterNot { it.configType == "mihomo" && it.mihomoParentUrl != null } }
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
    val isPinging by mainViewModel.isPinging.collectAsStateWithLifecycle()
    val activePingKeys by mainViewModel.activePingKeys.collectAsStateWithLifecycle()
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
    val ordinaryMetadata = remember(profilesMetadata, profiles) { profilesMetadata.filter { metadata -> profiles.any { it.url == metadata.url } } }
    val visibleProfiles = remember(ordinaryMetadata, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            ordinaryMetadata
        } else {
            ordinaryMetadata.filter { profile ->
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        color = LocalNebulaColors.current.textPrimary,
                        style = if (useWindowsProfileList) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = if (useWindowsProfileList) 1 else 2,
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
                    } else {
                        Text(
                            text = t(serverCountRu(servers.size), serverCountEn(servers.size)),
                            color = LocalNebulaColors.current.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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
                    size = 44.dp,
                    iconSize = 23.dp,
                    motion = MiniIconMotion.Ping,
                    active = isPinging
                )
                Spacer(Modifier.width(8.dp))
                MiniSquareIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { currentProfile?.url?.let { onProfileRefresh(it) } },
                    size = 44.dp,
                    iconSize = 23.dp,
                    motion = MiniIconMotion.Refresh,
                    active = currentProfile?.isLoading == true
                )
            }
        }

        Spacer(Modifier.height(16.dp))
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
                onSelectAuto = { mainViewModel.selectAuto(it) },
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
                onMoveProfile = { url, delta ->
                    val from = profiles.indexOfFirst { it.url == url }
                    val to = from + delta
                    if (from >= 0 && to in profiles.indices) {
                        val reordered = profiles.toMutableList()
                        val profile = reordered.removeAt(from)
                        reordered.add(to, profile)
                        mainViewModel.reorderProfiles(reordered.map { it.url })
                    }
                },
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

        val autoProgress by VpnManager.autoSelection
        val autoProfileUrl by VpnManager.autoProfileUrl
        if (tab == MiniSubscriptionTab.Proxies && currentProfile != null && currentProfile.servers.isNotEmpty() &&
            !com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(currentProfile)) {
            NimboAutoFastestRow(
                progress = autoProgress,
                selected = autoProgress.selected && autoProfileUrl == currentProfile.url,
                onClick = { mainViewModel.selectAuto(currentProfile) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val duration = if (miniMotionEnabled) 140 else 0
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) using
                    SizeTransform(clip = false) { _, _ -> snap() }
            },
            modifier = Modifier.weight(1f),
            label = "subscription-tab"
        ) { current ->
            when (current) {
                MiniSubscriptionTab.Proxies -> {
                    ProxyList(
                        servers = visibleServers,
                        isLoading = proxiesLoading,
                        selectedServer = selectedServer.takeUnless { autoProgress.selected },
                        showJsonBadge = currentProfile?.supportsJsonResponse == true,
                        isPinging = isPinging,
                        activePingKeys = activePingKeys,
                        pingDisplayMode = pingDisplayMode,
                        onSelectServer = onSelectServer,
                        onPingServer = { mainViewModel.pingSingleServer(it) },
                        onPingServers = { mainViewModel.pingServers(it) },
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
    onPingServers: (List<Server>) -> Unit = {},
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
                protocol.contains("naive") -> "NaiveProxy"
                protocol.contains("tuic") -> "TUIC"
                protocol.contains("awg") || protocol.contains("amnezia") -> "AWG"
                protocol.contains("wireguard") || protocol == "wg" -> "WireGuard"
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
                        server.protocol.lowercase().contains("naive") -> "NaiveProxy"
                        server.protocol.lowercase().contains("tuic") -> "TUIC"
                        server.protocol.lowercase().contains("awg") || server.protocol.lowercase().contains("amnezia") -> "AWG"
                        server.protocol.lowercase().contains("wireguard") || server.protocol.lowercase() == "wg" -> "WireGuard"
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
    // Ключ строки — pingMeasurementKey(): в него, в отличие от pingKey(), входит
    // имя, а подписки сплошь и рядом отдают несколько разных пунктов с одной и
    // той же точкой входа (у балансировщика все записи начинаются с одного
    // участника). Индекс в ключе не используем: он меняется при каждой
    // пересортировке и ломает состояние строк.
    val keyedServers = remember(sortedServers) {
        val seen = HashMap<String, Int>()
        sortedServers.mapIndexed { index, server ->
            val base = server.pingMeasurementKey()
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
        // Сначала по pingMeasurementKey(): он включает имя и потому различает
        // пункты подписки, делящие одну точку входа. По голому pingKey() выбор
        // «Финляндии 2» подсвечивал «БЕЗЛИМИТНЫЕ» — первую запись с тем же
        // адресом, и выглядело это как «сервер не выбирается».
        val targetMeasurementKey = target.pingMeasurementKey()
        val byMeasurement = keyedServers.indexOfFirst { (_, server) ->
            server.pingMeasurementKey() == targetMeasurementKey
        }
        if (byMeasurement >= 0) return@let byMeasurement

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
                    // Сортировать по пингу нечем, пока пинг не измерен: у свежей
                    // подписки он null у всех серверов, и нажатие выглядело как
                    // «кнопка ничего не делает». Раз человек просит порядок по
                    // задержке — сначала её и меряем.
                    if (selectedMode == 2 && !isPinging && servers.none { (it.ping ?: -1) >= 0 }) {
                        onPingServers(servers)
                    }
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
                        isPinging = isPinging && activePingKeys.contains(server.pingMeasurementKey()),
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
        t("По умолч.", "Default") to Icons.AutoMirrored.Filled.Sort,
        if (sortMode == 1 && !isNameAscending) t("Имя Я-А", "Name Z-A") to Icons.Default.SortByAlpha
        else t("Имя А-Я", "Name A-Z") to Icons.Default.SortByAlpha,
        if (sortMode == 2 && !isPingAscending) t("Пинг ⬆", "Ping ⬆") to Icons.Default.Speed
        else t("Пинг ⬇", "Ping ⬇") to Icons.Default.Speed,
        t("Протокол", "Protocol") to Icons.Default.Dns
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        tabs.chunked(2).forEachIndexed { rowIndex, rowTabs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowTabs.forEachIndexed { columnIndex, (label, icon) ->
                    val index = rowIndex * 2 + columnIndex
                    val selected = index == sortMode
                    Row(
                        modifier = Modifier
                            .weight(1f)
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
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
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
private fun ProxyRow(
    server: Server,
    selected: Boolean,
    showJsonBadge: Boolean,
    isPinging: Boolean,
    pingDisplayMode: Int,
    onPingClick: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LocalNebulaColors.current
    NimboPanel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onClick, modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                .semantics { this.selected = selected }, color = Color.Transparent) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(serverTitle(server), style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = colors.textPrimary)
                    Text(technicalServerSubtitle(server), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            Spacer(Modifier.width(8.dp))
            val pingLabel = t("Проверить пинг", "Check ping")
            Surface(onClick = onPingClick, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = pingLabel },
                color = colors.controlFill, shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                    WindowsPingPill(server.ping ?: -1, isPinging, pingDisplayMode)
                }
            }
        }
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
    val shownPing = displayPingMs(ping, currentPingProtocol()) ?: -1
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Анимация крутится, пока сервер реально пингуется (а не только когда нет значения).
    val showLoading = isPinging
    val targetPingColor = when {
        showLoading -> nebulaColors.accent
        shownPing < 0 -> nebulaColors.statusDisconnected
        shownPing <= 70 -> nebulaColors.statusConnected
        shownPing <= 120 -> Color(0xFFCDDC39)
        shownPing <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusDisconnected
    }
    val pingColor by animateColorAsState(targetPingColor, animationSpec = tween(300), label = "mini_ping_color")
    val badgeAlpha = if (isPinging) 0.92f else 1f
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
                        PingValueContent(ping, pingDisplayMode, pingColor)
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
    onSelectAuto: (SubscriptionProfile) -> Unit,
    onPingServer: (Server) -> Unit,
    onPingProfile: (List<Server>) -> Unit,
    onServerUiChanged: () -> Unit,
    onRefreshProfile: (String) -> Unit,
    onDeleteProfile: (SubscriptionProfile) -> Unit,
    onSupportProfile: (SubscriptionProfile) -> Unit,
    onOpenSite: (SubscriptionProfile) -> Unit,
    onOpenSettings: (SubscriptionProfile) -> Unit,
    onMoveProfile: (url: String, delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val autoProgress by VpnManager.autoSelection
    val autoProfileUrl by VpnManager.autoProfileUrl
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
    NimboSubscriptionPullRefresh(profiles.any { it.isLoading }, profiles.isNotEmpty() && query.isBlank(), {
        profiles.filterNot { it.isLoading }.forEach { onRefreshProfile(it.url) }
    }, modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalFloatingNavHeight.current + 16.dp)
    ) {
        visibleProfiles.forEach { (profile, servers) ->
            // Search reveals matches without changing the saved expansion choice.
            val expanded = query.isNotBlank() || (subscriptionCardExpanded[profile.url] ?: preferencesManager.isSubscriptionExpanded(profile.url, profiles.map { it.url }))
            item(key = "profile-${profile.url}") {
                val profileIndex = profiles.indexOfFirst { it.url == profile.url }
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
                    onOpenSettings = { onOpenSettings(profile) },
                    onMoveUp = if (profileIndex > 0) {
                        { onMoveProfile(profile.url, -1) }
                    } else {
                        null
                    },
                    onMoveDown = if (profileIndex >= 0 && profileIndex < profiles.lastIndex) {
                        { onMoveProfile(profile.url, 1) }
                    } else {
                        null
                    }
                )
                Spacer(Modifier.height(if (expanded) 12.dp else 14.dp))
            }
            if (!expanded) return@forEach

            if (!pinnedOnly && query.isBlank() && profile.servers.isNotEmpty() && !com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile)) {
                item(key = "auto-${profile.url}") {
                    NimboAutoFastestRow(autoProgress,
                        selected = autoProgress.selected && autoProfileUrl == profile.url,
                        onClick = { onSelectAuto(profile) },
                        modifier = Modifier.padding(bottom = 10.dp))
                }
            }
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
                    ).uppercase(Locale.ROOT),
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
                        // Keep the lazy list cheap, but respect glass opacity and
                        // the selected style instead of painting an opaque slab.
                        .background(nebulaColors.panelFill)
                ) {
                    val serverKey = server.pingKey()
                    WindowsProfileServerLine(
                        server = server,
                        displayName = serverUiTitle(preferencesManager, server),
                        selected = !autoProgress.selected && selectedServer?.matchesSelection(server) == true,
                        isFavorite = pinnedServerKeys.contains(serverKey),
                        isPinging = isPinging && activePingKeys.contains(server.pingMeasurementKey()),
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
}

// Expand/collapse state lives at process scope so it survives leaving and
// returning to the Profiles tab (the screen subtree is disposed on tab switch,
// which would otherwise reset a rememberSaveable back to its default).
private val subscriptionCardExpanded = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

@Composable
private fun SubscriptionDescription(description: String?, expanded: Boolean) {
    description?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
        Text(text, style = MaterialTheme.typography.bodySmall, color = LocalNebulaColors.current.textSecondary,
            maxLines = if (expanded) 8 else 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SubscriptionActionsRow(updated: Long, pinging: Boolean, refreshing: Boolean,
    onPing: () -> Unit, onRefresh: () -> Unit,
    pingLabel: String = t("Проверить пинг", "Check ping")) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(t("Обновлено: ", "Updated: ") + formatLastUpdateTime(updated), Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall, color = LocalNebulaColors.current.textSecondary)
        com.danila.nimbo.ui.components.NimboIconAction(Icons.Default.SignalCellularAlt, if (pinging) t("Остановить пинг", "Stop ping") else pingLabel, pinging, onPing, allowCancel = true)
        com.danila.nimbo.ui.components.NimboIconAction(Icons.Default.Refresh, t("Обновить подписку", "Refresh subscription"), refreshing, onRefresh)
    }
}

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
    onOpenSettings: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val colors = LocalNebulaColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    com.danila.nimbo.ui.components.NimboSubscriptionPanel(expanded, onToggleExpanded, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NimboSubscriptionHeader(profile.displayName.ifBlank { t("Подписка", "Subscription") },
                t(serverCountRu(profile.servers.size), serverCountEn(profile.servers.size)),
                expanded, null, { info = true }, logo = {
                    if (showSubscriptionLogo && !profile.brandLogo.isNullOrBlank()) {
                        SubscriptionBrandLogo(profile.brandLogo, profile.brandLogoCache, size = 36.dp)
                    } else Icon(painterResource(com.danila.nimbo.R.drawable.nimbo_cloud), null, Modifier.size(36.dp), tint = colors.textPrimary)
                }, trailing = {
                    Box {
                        IconButton({ menuExpanded = true }, Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, t("Действия с подпиской", "Subscription actions"), tint = colors.textSecondary)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                            containerColor = colors.panelFill, shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colors.panelBorder)) {
                            DropdownMenuItem(text = { Text(t("Настройки", "Settings")) }, onClick = { menuExpanded = false; onOpenSettings() })
                            if (onMoveUp != null) DropdownMenuItem(text = { Text(t("Переместить выше", "Move up")) }, onClick = { menuExpanded = false; onMoveUp() })
                            if (onMoveDown != null) DropdownMenuItem(text = { Text(t("Переместить ниже", "Move down")) }, onClick = { menuExpanded = false; onMoveDown() })
                            DropdownMenuItem(text = { Text(t("Удалить", "Delete"), color = colors.statusError) }, onClick = { menuExpanded = false; onDelete() })
                        }
                    }
                })
            SubscriptionDescription(profile.announce, expanded)
            SubscriptionQuotaSummary(profile)
            SubscriptionActionsRow(lastUpdateMs, isPinging, profile.isLoading, onPingAll, onRefresh)
            if (!profile.error.isNullOrBlank()) Text(sanitizeProfileErrorForUi(profile.error).orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = colors.statusError)
        }
    }
    if (info) SubscriptionDetailsDialog(profile, { info = false }, onSupport, onSite)

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
    val colors = LocalNebulaColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var hideConfirmOpen by remember { mutableStateOf(false) }
    com.danila.nimbo.ui.components.NimboServerRow(
        title = cleanServerName(displayName), subtitle = serverSubtitle(server), selected = selected,
        flag = extractFlagEmoji(server.name), onSelect = onClick, onOpenMenu = { menuExpanded = true },
        ping = { WindowsPingPill(server.ping ?: -1, isPinging, pingDisplayMode) },
        menu = {
                DropdownMenu(menuExpanded, { menuExpanded = false }, containerColor = colors.panelFill,
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.panelBorder)) {
                    DropdownMenuItem(text = { Text(if (isPinging) t("Остановить пинг", "Stop ping") else t("Пинг сервера", "Ping server")) },
                        leadingIcon = { Icon(if (isPinging) Icons.Default.Stop else Icons.Default.Speed, null) },
                        onClick = { menuExpanded = false; onPing() })
                    DropdownMenuItem(text = { Text(if (isFavorite) t("Убрать из избранного", "Remove favorite") else t("В избранное", "Add favorite")) },
                        leadingIcon = { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null) },
                        onClick = { menuExpanded = false; onToggleFavorite() })
                    DropdownMenuItem(text = { Text(t("Переименовать", "Rename")) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; renameOpen = true })
                    DropdownMenuItem(text = { Text(t("Удалить сервер", "Delete server"), color = colors.statusError) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.statusError) },
                        onClick = { menuExpanded = false; hideConfirmOpen = true })
                }
        })
    if (showDivider) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 12.dp))
    if (renameOpen) NimboRenameServerDialog(displayName, { renameOpen = false }) { name -> onRename(name); renameOpen = false }
    if (hideConfirmOpen) NimboConfirmDialog(title = t("Удалить сервер?", "Delete server?"), text = displayName,
        confirmText = t("Удалить", "Delete"), onDismiss = { hideConfirmOpen = false },
        onConfirm = { onHide(); hideConfirmOpen = false })

}

@Composable
internal fun WindowsFlatPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable () -> Unit
) {
    NimboPanel(modifier.fillMaxWidth(), content)

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
    val shape = nimboControlShape(12.dp)
    val manga = LocalElementStyleMode.current == ElementStyleMode.MANGA
    Row(
        modifier = Modifier
            .clip(shape)
            .background(nimboControlContainer(nebulaColors.textPrimary.copy(alpha = 0.055f)))
            .then(if (manga) Modifier.border(1.5.dp, nebulaColors.panelBorder, shape) else Modifier)
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
    val shownPing = displayPingMs(ping, currentPingProtocol()) ?: -1
    // Пока сервер пингуется (loading) — крутим анимацию прямо в месте значения,
    // даже если уже есть закешированный пинг.
    val showLoading = loading
    val targetPingColor = when {
        showLoading -> nebulaColors.accent
        shownPing < 0 -> nebulaColors.statusDisconnected
        shownPing <= 70 -> Color(0xFF6BE88E)
        shownPing <= 120 -> Color(0xFFCDDC39)
        shownPing <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusConnecting
    }
    val targetBackground = when {
        showLoading -> nebulaColors.accent.copy(alpha = 0.15f)
        shownPing < 0 -> nebulaColors.statusDisconnected.copy(alpha = 0.15f)
        shownPing <= 70 -> Color(0xFF173A25)
        shownPing <= 120 -> Color(0xFFCDDC39).copy(alpha = 0.15f)
        shownPing <= 220 -> Color(0xFFFF9800).copy(alpha = 0.15f)
        else -> nebulaColors.statusConnecting.copy(alpha = 0.12f)
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
                    PingValueContent(ping, pingDisplayMode, pingColor)
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
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    val fillAlpha = if (green) 0.16f else 0.10f
    // Круглая пилюля — примета стекла. В Manga это прямоугольная врезка с
    // чернильным контуром, как подпись в кадре.
    val badgeShape = if (mangaStyle) RoundedCornerShape(2.dp) else RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(badgeShape)
            .background(nebulaColors.accent.copy(alpha = fillAlpha))
            .border(
                if (mangaStyle) 1.5.dp else 0.5.dp,
                if (mangaStyle) {
                    nebulaColors.accent
                } else {
                    nebulaColors.accent.copy(alpha = if (green) 0.34f else 0.22f)
                },
                badgeShape
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
        contentPadding = PaddingValues(bottom = LocalFloatingNavHeight.current + 16.dp)
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
    var showInfo by remember { mutableStateOf(false) }
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

            NimboAction(Icons.Default.Info, t("Информация о подписке", "Subscription information"), { showInfo = true }, Modifier.fillMaxWidth())
        }
    }
    if (showInfo) NebulaMorphicDialog(onDismissRequest = { showInfo = false }, title = profile.displayName,
        confirmButtonText = t("Готово", "Done"), cancelButtonText = null, onConfirm = { showInfo = false }) {
        Text(profile.announce?.takeIf { it.isNotBlank() } ?: t("Описание отсутствует", "No description"), color = nebulaColors.textSecondary)
        Text(buildSubscriptionDescription(profile), color = nebulaColors.textSecondary, modifier = Modifier.padding(top = 12.dp))
        NimboAction(Icons.Default.SupportAgent, t("Поддержка", "Support"), onSupport, Modifier.fillMaxWidth().padding(top = 12.dp))
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
    val haptic = LocalHapticFeedback.current
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
        onClick = {
            haptic.tick()
            onClick()
        }
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
    val context = LocalContext.current
    var rulesMode by remember { mutableStateOf(preferencesManager.isRoutingEnabled) }
    MiniSegmented(
        left = "Правила",
        right = "Глобальный",
        leftSelected = rulesMode,
        onLeft = {
            rulesMode = true
            preferencesManager.isRoutingEnabled = true
            RoutingConfigurationApplier.applyToActiveTunnel(context)
            mainViewModel.showTopNotification("Режим правил включен")
        },
        onRight = {
            rulesMode = false
            preferencesManager.isRoutingEnabled = false
            RoutingConfigurationApplier.applyToActiveTunnel(context)
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
    onAppIconClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDisclaimerClick: () -> Unit,
    onConnectionIdClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onUpdatesClick: () -> Unit,
    onLogsClick: () -> Unit,
    onRoutingClick: () -> Unit,
    onStatsClick: () -> Unit,
    onWhitelistClick: () -> Unit,
    onCrossSyncClick: () -> Unit,
    onRefreshFirstProfile: (String) -> Unit,
    onShowTvQr: (String) -> Unit,
    onConnect: (Server) -> Unit
) {
    var section by rememberSaveable { mutableStateOf(-1) }
    var developerUnlocked by rememberSaveable { mutableStateOf(false) }
    val tapGate = remember { com.danila.nimbo.utils.DeveloperTapGate() }
    androidx.activity.compose.BackHandler(enabled = section >= 0) { section = -1 }
    val motionEnabled = rememberMiniMotionEnabled()
    AnimatedContent(section, transitionSpec = {
        val duration = if (motionEnabled) 140 else 0
        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) using
            SizeTransform(clip = false) { _, _ -> snap() }
    }, label = "settings-detail") { activeSection ->
    val title = when (activeSection) {
        0 -> t("Общие", "General")
        1 -> t("Внешний вид", "Appearance")
        2 -> t("Пинг серверов", "Server ping")
        3 -> t("Обновления", "Updates")
        4 -> t("О приложении", "About")
        5 -> t("Подписки", "Subscriptions")
        6 -> t("Серверы", "Servers")
        7 -> t("Резервная копия", "Backup")
        8 -> t("Ядро VPN", "VPN core")
        11 -> t("Разработчик", "Developer")
        else -> t("DNS и транспорт", "DNS and transport")
    }
    if (activeSection >= 0) {
        androidx.compose.runtime.key(activeSection) {
            NimboSubPageScaffold(title, onBack = { section = -1 }) {
                when (activeSection) {
                    0 -> GeneralSettingsSection(preferencesManager)
                    1 -> ThemeSettingsSection(preferencesManager, onAppIconClick)
                    2 -> PingSettingsSection(preferencesManager, mainViewModel)
                    3 -> UpdatesSettingsContent()
                    4 -> AboutSettingsContent()
                    5 -> SubscriptionsSettingsSection(preferencesManager)
                    6 -> ServersSettingsSection(preferencesManager)
                    7 -> BackupSettingsSection(preferencesManager)
                    8 -> NimboCoreSettings(preferencesManager, onConnect, onOpenSubscription)
                    11 -> DeveloperSettingsSection()
                    else -> AdvancedSettingsSection(preferencesManager)
                }
            }
        }
        return@AnimatedContent
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp).padding(top = 20.dp, bottom = LocalFloatingNavHeight.current + 16.dp)) {
        Box(Modifier.fillMaxWidth().clickable {
            if (tapGate.tap(android.os.SystemClock.elapsedRealtime())) { developerUnlocked = true; section = 11 }
        }) { com.danila.nimbo.ui.components.NimboBrandHeader() }
        Spacer(Modifier.height(20.dp))
        Text(t("Настройки", "Settings"), style = MaterialTheme.typography.headlineMedium,
            color = LocalNebulaColors.current.textPrimary, modifier = Modifier.semantics { heading() })
        Text(t("Главное — рядом. Остальное — по необходимости.", "The essentials, close at hand."),
            style = MaterialTheme.typography.bodySmall, color = LocalNebulaColors.current.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        SettingsGroupLabel(t("ПОДКЛЮЧЕНИЕ", "CONNECTION"))
        SettingsCompactCard {
            SettingsRow(Icons.Default.Dns, t("Ядро VPN", "VPN core"),
                com.danila.nimbo.vpn.VpnCoreChoice.fromId(preferencesManager.vpnCoreState.value)?.let {
                    if (it == com.danila.nimbo.vpn.VpnCoreChoice.AUTO) t("Авто · по протоколу сервера", "Auto · by server protocol") else it.title
                } ?: t("Выберите ядро", "Choose a core"), { section = 8 })
            SettingsRow(Icons.Default.Speed, t("Пинг серверов", "Server ping"), currentPingSettingsLabel(preferencesManager), { section = 2 })
            SettingsRow(Icons.Default.Route, t("Маршрутизация", "Routing"), t("Сайты, правила и исключения", "Sites, rules and exceptions"), onRoutingClick)
            SettingsRow(Icons.Default.Apps, t("Приложения", "Applications"), t("Какие приложения используют VPN", "Choose which apps use VPN"), onOpenAppAccess)
            SettingsRow(Icons.Default.Dns, t("DNS и транспорт", "DNS and transport"), t("DNS, TLS и параметры соединения", "DNS, TLS and connection parameters"), { section = 10 })
            SettingsRow(Icons.Default.PowerSettingsNew, t("Общие", "General"), t("Автоподключение, память и отклик", "Auto-connect, memory and haptics"), { section = 0 }, false)
        }
        Spacer(Modifier.height(20.dp))
        SettingsGroupLabel(t("ПРИЛОЖЕНИЕ", "APPLICATION"))
        SettingsCompactCard {
            SettingsRow(Icons.Default.Palette, t("Внешний вид", "Appearance"), t("Тема, цвет и доступность", "Theme, color and accessibility"), { section = 1 })
            SettingsRow(Icons.Default.Language, t("Язык", "Language"), currentLanguageLabel(), onLanguageClick)
            SettingsRow(Icons.Default.Sync, t("Синхронизация", "Sync"), t("Ваши устройства", "Your devices"), onCrossSyncClick)
            SettingsRow(Icons.Default.Backup, t("Резервная копия", "Backup"), t("Экспорт и восстановление", "Export and restore"), { section = 7 }, false)
        }
        Spacer(Modifier.height(20.dp))
        SettingsGroupLabel(t("ПРОФИЛИ", "PROFILES"))
        SettingsCompactCard {
            SettingsRow(Icons.Default.Refresh, t("Обновление подписок", "Subscription updates"), null, { section = 5 })
            SettingsRow(Icons.Default.Dns, t("Настройки серверов", "Server settings"), null, { section = 6 }, false)
        }
        Spacer(Modifier.height(20.dp))
        SettingsGroupLabel(t("ДИАГНОСТИКА", "DIAGNOSTICS"))
        SettingsCompactCard {
            SettingsRow(Icons.Default.BarChart, t("Статистика", "Statistics"), null, onStatsClick)
            SettingsRow(Icons.Default.NetworkCheck, t("Проверка сети", "Network check"), null, onWhitelistClick)
            SettingsRow(Icons.AutoMirrored.Filled.Article, t("Журнал", "Logs"), null, onLogsClick)
            SettingsRow(Icons.Default.Notifications, t("Уведомления", "Notifications"), null, onNotificationsClick)
            SettingsRow(Icons.Default.Key, t("ID подключения", "Connection ID"), null, onConnectionIdClick, false)
        }
        Spacer(Modifier.height(20.dp))
        SettingsCompactCard {
            SettingsRow(Icons.Default.SystemUpdate, t("Обновления", "Updates"), "Nimbo v${BuildConfig.VERSION_NAME}", onUpdatesClick)
            SettingsRow(Icons.Default.Info, t("О приложении", "About"), "Nimbo v${BuildConfig.VERSION_NAME}", onAboutClick)
            SettingsRow(Icons.Default.Policy, t("Условия использования", "Terms of use"), null, onDisclaimerClick, false)
            if (developerUnlocked) SettingsRow(Icons.Default.Build, t("Разработчик", "Developer"), null, { section = 11 }, false)
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
    val hapticFeedbackEnabled by preferencesManager.hapticFeedbackEnabledState
    val hapticFeedbackStrength by preferencesManager.hapticFeedbackStrengthState
    val hapticFeedbackStyle by preferencesManager.hapticFeedbackStyleState
    var hapticStrengthExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hapticFeedbackEnabled) {
        if (!hapticFeedbackEnabled) hapticStrengthExpanded = false
    }

    SettingsGroupLabel(t("ОБЩИЕ", "GENERAL"))
    BackgroundHealthPanel()
    Spacer(Modifier.height(12.dp))
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
            title = t("Виброотклик", "Haptic feedback"),
            subtitle = t(
                "Короткая вибрация при навигации, ползунках и действиях",
                "Short vibration for navigation, sliders, and actions"
            ),
            checked = hapticFeedbackEnabled,
            onCheckedChange = { preferencesManager.hapticFeedbackEnabled = it },
            icon = Icons.Default.Vibration
        )
        SettingsHapticStrengthButton(
            strength = hapticFeedbackStrength,
            style = hapticFeedbackStyle,
            expanded = hapticStrengthExpanded,
            enabled = hapticFeedbackEnabled,
            onExpandedChange = { hapticStrengthExpanded = it },
            onStrengthChange = { preferencesManager.hapticFeedbackStrength = it },
            onStyleChange = { preferencesManager.hapticFeedbackStyle = it }
        )
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
                            text = t("Автоматический бюджет памяти", "Automatic memory budget"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = nebulaColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = t("Мягкий бюджет общего Go-ядра; не лимит всей памяти приложения.", "Soft budget for the shared Go runtime, not total app memory."),
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
                    t("Бюджет ядра: автоматически · 96 MiB", "Core budget: automatic · 96 MiB")
                } else {
                    t("Бюджет ядра: ${memoryLimitMb} MiB", "Core budget: ${memoryLimitMb} MiB")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = nebulaColors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (memoryLimitDisabled) {
                    t("Go освобождает неиспользуемую память без постоянного принудительного GC.", "Go reclaims unused memory without repeated forced GC.")
                } else {
                    t("Это мягкий бюджет: живые соединения не прерываются при превышении.", "A soft budget: live connections are not stopped if it is exceeded.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = nebulaColors.textSecondary
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = memoryLimitMb.toFloat(),
                onValueChange = rememberHapticSliderValueChange(
                    value = memoryLimitMb.toFloat(),
                    valueRange = 40f..300f
                ) { value ->
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
    SettingsScrollTail()
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
    val context = LocalContext.current
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
                SubscriptionUpdateScheduler.reschedule(context)
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
                        SubscriptionUpdateScheduler.reschedule(context)
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
    SettingsScrollTail()
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
                                        protocol.contains("naive") -> "NaiveProxy"
                                        protocol.contains("tuic") -> "TUIC"
                                        protocol.contains("awg") || protocol.contains("amnezia") -> "AWG"
                                        protocol.contains("wireguard") || protocol == "wg" -> "WireGuard"
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
    SettingsScrollTail()
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

    NimboToolSection(t("Резервная копия", "Backup"),
        t("Файл содержит ваши настройки. Храните его в надёжном месте.", "The file contains your settings. Keep it in a safe place.")) {} 
    Spacer(Modifier.height(12.dp))
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
                        Icon(Icons.Default.Key, if (passwordVisible) t("Скрыть пароль", "Hide password") else t("Показать пароль", "Show password"), tint = nebulaColors.textSecondary)
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
            NimboToolActions {
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
private fun BackupActionButton(label: String, icon: ImageVector, primary: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    if (primary) Button(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = contrastingLabel(colors.accent))) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    } else NimboAction(icon, label, onClick, modifier)
}

// ── #13 Статистика ──────────────────────────────────────────────────────────
/** Строка расхода одного приложения: имя, полоса и объём. */
@Composable
private fun AppTrafficRow(
    app: com.danila.nimbo.utils.AppTraffic,
    fraction: Float,
    accent: Color,
    showDivider: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = app.label,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatBytesPrecise(app.total),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(nebulaColors.textPrimary.copy(alpha = 0.07f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent.copy(alpha = 0.85f))
            )
        }
        AnimatedVisibility(visible = selected) {
            Text("↓ ${formatBytesPrecise(app.down)}   ·   ↑ ${formatBytesPrecise(app.up)}",
                color = nebulaColors.textSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (showDivider) Spacer(Modifier.height(4.dp))
    }
}

/** Одна строка журнала сессий: когда, сколько длилась и сколько прокачано. */
@Composable
private fun TrafficSessionRow(
    session: com.danila.nimbo.utils.TrafficSession,
    downloadColor: Color,
    uploadColor: Color,
    showDivider: Boolean
) {
    val nebulaColors = LocalNebulaColors.current
    val startedAt = remember(session.startMs) {
        java.text.SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
            .format(java.util.Date(session.startMs))
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.server.ifBlank { t("Сервер", "Server") },
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = startedAt + " · " + formatDuration(session.durationSeconds.toInt()),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "↓ " + formatBytesPrecise(session.down),
                    color = downloadColor,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "↑ " + formatBytesPrecise(session.up),
                    color = uploadColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        if (showDivider) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(nebulaColors.textPrimary.copy(alpha = 0.06f))
            )
        }
    }
}

@Composable
private fun ColumnScope.StatisticsSettingsSection(
    preferencesManager: PreferencesManager
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    var statsRefresh by remember { mutableStateOf(0) }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }

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

    TrafficDashboard()

    // ── График скорости за последнюю минуту ────────────────────────────────
    val speedSamples = TrafficHistory.speedSamples
    val downloadColor = nebulaColors.accent
    val uploadColor = nebulaColors.statusConnected

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("СКОРОСТЬ ЗА МИНУТУ", "SPEED, LAST MINUTE")) {
        if (speedSamples.size < 2) {
            Text(
                text = if (vpnState == VpnState.CONNECTED) {
                    t("Собираем данные…", "Collecting data…")
                } else {
                    t("График появится после подключения", "The chart appears once connected")
                },
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 18.dp)
            )
        } else {
            TrafficSpeedChart(
                samples = speedSamples,
                downloadColor = downloadColor,
                uploadColor = uploadColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 6.dp, bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "↓ " + formatSpeedLabel(downloadSpeed),
                    color = downloadColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "↑ " + formatSpeedLabel(uploadSpeed),
                    color = uploadColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ── Расход по дням ────────────────────────────────────────────────────
    val historyRevision = TrafficHistory.revision.value
    val days = remember(historyRevision, statsRefresh, connectedSeconds / 60) {
        TrafficHistory.recentDays(limit = 7)
    }

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("ЗА НЕДЕЛЮ", "LAST 7 DAYS")) {
        TrafficDailyBars(
            days = days,
            downloadColor = downloadColor,
            uploadColor = uploadColor,
            formatBytes = { formatBytesPrecise(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }

    // ── По приложениям ────────────────────────────────────────────────────
    // Разбивку туннеля ядро не отдаёт, поэтому берём системную статистику сети.
    // Это честные цифры системы, но по всей сети, а не только через VPN — так и
    // подписано, чтобы никто не принял их за расход внутри туннеля.
    var usageAccess by remember(statsRefresh) { mutableStateOf(AppTrafficStats.hasUsageAccess(context)) }
    val appTraffic = remember(usageAccess, statsRefresh, historyRevision) {
        if (usageAccess) {
            val since = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
            AppTrafficStats.query(context, sinceMs = since)
        } else {
            emptyList()
        }
    }

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("ПО ПРИЛОЖЕНИЯМ · 7 ДНЕЙ", "BY APP · 7 DAYS")) {
        if (!usageAccess) {
            Text(
                text = t(
                    "Android отдаёт расход по приложениям только с разрешением «Доступ к данным использования».",
                    "Android shares per-app usage only with the «Usage access» permission."
                ),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            BackupActionButton(
                label = t("Выдать разрешение", "Grant permission"),
                icon = Icons.Default.Settings,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching { context.startActivity(AppTrafficStats.usageAccessSettingsIntent()) }
                    usageAccess = AppTrafficStats.hasUsageAccess(context)
                }
            )
        } else if (appTraffic.isEmpty()) {
            Text(
                text = t("Система пока не отдала данных за период.", "The system has no data for this period yet."),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            val peak = appTraffic.maxOf { it.total }.coerceAtLeast(1L)
            appTraffic.forEachIndexed { index, app ->
                AppTrafficRow(
                    app = app,
                    fraction = app.total.toFloat() / peak.toFloat(),
                    accent = nebulaColors.accent,
                    showDivider = index < appTraffic.lastIndex,
                    selected = selectedAppPackage == app.packageName,
                    onClick = { selectedAppPackage = if (selectedAppPackage == app.packageName) null else app.packageName }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = t(
                    "Данные системы: весь сетевой трафик приложения, включая обмен вне туннеля.",
                    "System data: all app network traffic, including traffic outside the tunnel."
                ),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }

    // ── Журнал сессий ─────────────────────────────────────────────────────
    val sessions = remember(historyRevision, statsRefresh, vpnState) { TrafficHistory.sessions() }
    if (sessions.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        StatGroupCard(title = t("ПОСЛЕДНИЕ СЕССИИ", "RECENT SESSIONS")) {
            sessions.take(8).forEachIndexed { index, session ->
                TrafficSessionRow(
                    session = session,
                    downloadColor = downloadColor,
                    uploadColor = uploadColor,
                    showDivider = index < sessions.take(8).lastIndex
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    StatGroupCard(title = t("ЗА ВСЁ ВРЕМЯ", "ALL TIME")) {
        Text(t("История может содержать счётчики ядра, UID или всего устройства.",
            "History may include core, app UID or device-wide counters."),
            color = nebulaColors.textSecondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp))
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
        StatLine(t("TX пакеты Android", "Android TX packets"), txPackets.toString())
        StatLine(t("RX пакеты Android", "Android RX packets"), rxPackets.toString(), showDivider = false)
    }

    Spacer(Modifier.height(20.dp))
    BackupActionButton(
        label = t("Сбросить статистику", "Reset statistics"),
        icon = Icons.Default.Restore,
        primary = false,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            preferencesManager.resetTrafficStats()
            TrafficHistory.clear()
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
            // Ряды внутри держат свою высоту сами, а вот обычный текст в конце
            // упирался в нижнюю кромку и обрезался скруглением — карточке нужен
            // небольшой вертикальный запас.
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
internal fun ColumnScope.ConnectionsSettingsSection(
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
    val routingProfile = remember(refreshKey) { preferencesManager.loadRoutingProfile() }
    val activeProfileName = when {
        routingEnabled && !routingProfile?.name.isNullOrBlank() -> routingProfile.name
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

    NimboPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VpnKey, null, Modifier.size(24.dp),
                tint = if (connected) nebulaColors.accent else nebulaColors.textSecondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(statusText, color = nebulaColors.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(serverText, color = nebulaColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                if (connected) Text(activeProfileName + " · " + formatDuration(connectedSeconds),
                    modifier = Modifier.padding(top = 4.dp), color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall)
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
            text = label.uppercase(Locale.ROOT),
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = value,
            color = nebulaColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,

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
                text = t("Текущее подключение", "Current connection"),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (vpnState == VpnState.CONNECTED) t("VPN активен", "VPN active") else t("VPN отключён", "VPN disconnected"),
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
    if (vpnState == VpnState.CONNECTED) WindowsSearchField(
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
                t("Подключитесь к VPN — здесь появится текущая сессия", "Connect to VPN to see the current session here")
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
    /** Мгновенная скорость: разница двух опросов системной статистики. */
    val rxRate: Long,
    val txRate: Long,
    /** Накопленный объём за сутки — его система отдаёт всегда. */
    val rxTotal: Long,
    val txTotal: Long,
    val domains: List<String>,
    val route: String,
    val isProxied: Boolean
)

/** Полночь текущих суток — окно, за которое просим системную статистику. */
private fun startOfTodayMillis(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

private fun getDomainsForFirewall(packageName: String, appName: String): List<String> {
    // Android's public TrafficStats API exposes per-UID byte counters, not DNS
    // ownership. Never guess domains from a package name: a made-up domain is
    // worse than an honest empty list in a diagnostic screen.
    return emptyList()
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
    if (packageName == com.danila.nimbo.BuildConfig.APPLICATION_ID) {
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

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vpnState, activeServer, refreshKey, lifecycle) {
        com.danila.nimbo.network.TemporaryAppRuleManager.expire(context)
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

        // TrafficStats.getUidRxBytes для чужого UID возвращает UNSUPPORTED (-1)
        // начиная с Android 7 — из-за этого экран навсегда застревал на «ожидании
        // активности». Реальные цифры отдаёт NetworkStatsManager, и то по
        // разрешению «Доступ к данным использования».
        val metaByUid = appList.associateBy { it.uid }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var prevTotals = mapOf<Int, Pair<Long, Long>>()
            var lastPollTime = System.currentTimeMillis()

            while (true) {
                if (!AppTrafficStats.hasUsageAccess(context)) {
                    firewallRows = emptyList()
                    kotlinx.coroutines.delay(2000)
                    continue
                }

                val now = System.currentTimeMillis()
                val timeDeltaSec = ((now - lastPollTime) / 1000f).coerceAtLeast(0.1f)
                lastPollTime = now

                val usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AppTrafficStats.query(
                        context,
                        sinceMs = startOfTodayMillis(),
                        untilMs = now,
                        limit = 60
                    )
                }

                val currentTotals = usage.associate { it.uid to (it.down to it.up) }
                val rows = usage.map { app ->
                    val meta = metaByUid[app.uid]
                    val previous = prevTotals[app.uid]
                    val rxRate = previous
                        ?.let { ((app.down - it.first) / timeDeltaSec).toLong().coerceAtLeast(0L) }
                        ?: 0L
                    val txRate = previous
                        ?.let { ((app.up - it.second) / timeDeltaSec).toLong().coerceAtLeast(0L) }
                        ?: 0L
                    val (routeName, isProxied) = calculateRouteForFirewall(
                        app.packageName,
                        preferencesManager,
                        activeServer,
                        vpnState == VpnState.CONNECTED
                    )
                    FirewallRowData(
                        uid = app.uid,
                        name = meta?.name ?: app.label,
                        packageName = app.packageName,
                        icon = meta?.icon,
                        rxRate = rxRate,
                        txRate = txRate,
                        rxTotal = app.down,
                        txTotal = app.up,
                        domains = getDomainsForFirewall(app.packageName, meta?.name ?: app.label),
                        route = routeName,
                        isProxied = isProxied
                    )
                }

                prevTotals = currentTotals
                // Сортируем по текущей активности, но список не схлопывается в пустой,
                // когда прямо сейчас никто ничего не качает.
                firewallRows = rows
                    .sortedWith(
                        compareByDescending<FirewallRowData> { it.rxRate + it.txRate }
                            .thenByDescending { it.rxTotal + it.txTotal }
                    )
                    .take(40)

                // Системная статистика обновляется рывками, чаще пяти секунд её
                // дёргать бессмысленно — и дельта скорости получается осмысленнее.
                kotlinx.coroutines.delay(5000)
            }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            query.isNotBlank() ->
                                t("Нет активных соединений по вашему запросу", "No active connections match your query")
                            !AppTrafficStats.hasUsageAccess(context) -> t(
                                "Расход по приложениям Android отдаёт только с разрешением «Доступ к данным использования».",
                                "Android shares per-app usage only with the «Usage access» permission."
                            )
                            else ->
                                t("Ожидание сетевой активности процессов…", "Waiting for process network activity…")
                        },
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (query.isBlank() && !AppTrafficStats.hasUsageAccess(context)) {
                        Spacer(Modifier.height(14.dp))
                        BackupActionButton(
                            label = t("Выдать разрешение", "Grant permission"),
                            icon = Icons.Default.Settings,
                            primary = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                runCatching {
                                    context.startActivity(AppTrafficStats.usageAccessSettingsIntent())
                                }
                            }
                        )
                    }
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
            FirewallDetailDialog(
                row = detailRow,
                onDismiss = { openedUid = null },
                onQuickDirect = {
                    com.danila.nimbo.network.TemporaryAppRuleManager.apply(
                        context,
                        detailRow.packageName,
                        com.danila.nimbo.network.TemporaryAppRoute.DIRECT,
                        durationMs = 15 * 60_000L
                    )
                    refreshKey++
                },
                onQuickVpn = {
                    com.danila.nimbo.network.TemporaryAppRuleManager.apply(
                        context,
                        detailRow.packageName,
                        com.danila.nimbo.network.TemporaryAppRoute.VPN,
                        durationMs = 15 * 60_000L
                    )
                    refreshKey++
                }
            )
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

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vpnState, activeServer, refreshKey, lifecycle) {
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

        // TrafficStats.getUidRxBytes для чужого UID возвращает UNSUPPORTED (-1)
        // начиная с Android 7 — из-за этого экран навсегда застревал на «ожидании
        // активности». Реальные цифры отдаёт NetworkStatsManager, и то по
        // разрешению «Доступ к данным использования».
        val metaByUid = appList.associateBy { it.uid }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var prevTotals = mapOf<Int, Pair<Long, Long>>()
            var lastPollTime = System.currentTimeMillis()

            while (true) {
                if (!AppTrafficStats.hasUsageAccess(context)) {
                    firewallRows = emptyList()
                    kotlinx.coroutines.delay(2000)
                    continue
                }

                val now = System.currentTimeMillis()
                val timeDeltaSec = ((now - lastPollTime) / 1000f).coerceAtLeast(0.1f)
                lastPollTime = now

                val usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AppTrafficStats.query(
                        context,
                        sinceMs = startOfTodayMillis(),
                        untilMs = now,
                        limit = 60
                    )
                }

                val currentTotals = usage.associate { it.uid to (it.down to it.up) }
                val rows = usage.map { app ->
                    val meta = metaByUid[app.uid]
                    val previous = prevTotals[app.uid]
                    val rxRate = previous
                        ?.let { ((app.down - it.first) / timeDeltaSec).toLong().coerceAtLeast(0L) }
                        ?: 0L
                    val txRate = previous
                        ?.let { ((app.up - it.second) / timeDeltaSec).toLong().coerceAtLeast(0L) }
                        ?: 0L
                    val (routeName, isProxied) = calculateRouteForFirewall(
                        app.packageName,
                        preferencesManager,
                        activeServer,
                        vpnState == VpnState.CONNECTED
                    )
                    FirewallRowData(
                        uid = app.uid,
                        name = meta?.name ?: app.label,
                        packageName = app.packageName,
                        icon = meta?.icon,
                        rxRate = rxRate,
                        txRate = txRate,
                        rxTotal = app.down,
                        txTotal = app.up,
                        domains = getDomainsForFirewall(app.packageName, meta?.name ?: app.label),
                        route = routeName,
                        isProxied = isProxied
                    )
                }

                prevTotals = currentTotals
                // Сортируем по текущей активности, но список не схлопывается в пустой,
                // когда прямо сейчас никто ничего не качает.
                firewallRows = rows
                    .sortedWith(
                        compareByDescending<FirewallRowData> { it.rxRate + it.txRate }
                            .thenByDescending { it.rxTotal + it.txTotal }
                    )
                    .take(15)

                kotlinx.coroutines.delay(5000)
            }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            query.isNotBlank() ->
                                t("Нет активных соединений по вашему запросу", "No active connections match your query")
                            !AppTrafficStats.hasUsageAccess(context) -> t(
                                "Расход по приложениям Android отдаёт только с разрешением «Доступ к данным использования».",
                                "Android shares per-app usage only with the «Usage access» permission."
                            )
                            else ->
                                t("Ожидание сетевой активности процессов…", "Waiting for process network activity…")
                        },
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (query.isBlank() && !AppTrafficStats.hasUsageAccess(context)) {
                        Spacer(Modifier.height(14.dp))
                        BackupActionButton(
                            label = t("Выдать разрешение", "Grant permission"),
                            icon = Icons.Default.Settings,
                            primary = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                runCatching {
                                    context.startActivity(AppTrafficStats.usageAccessSettingsIntent())
                                }
                            }
                        )
                    }
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
                    )
                    Text(
                        text = row.packageName,
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
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
                            text = if (row.rxRate > 0L) {
                                formatTrafficRateBytes(row.rxRate)
                            } else {
                                formatBytesPrecise(row.rxTotal)
                            },
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
                            text = if (row.txRate > 0L) {
                                formatTrafficRateBytes(row.txRate)
                            } else {
                                formatBytesPrecise(row.txTotal)
                            },
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
private fun FirewallDetailDialog(
    row: FirewallRowData,
    onDismiss: () -> Unit,
    onQuickDirect: () -> Unit,
    onQuickVpn: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    var liveRx by remember(row.uid) { mutableStateOf(row.rxRate) }
    var liveTx by remember(row.uid) { mutableStateOf(row.txRate) }
    var totalRx by remember(row.uid) { mutableStateOf(0L) }
    var totalTx by remember(row.uid) { mutableStateOf(0L) }
    var peakRx by remember(row.uid) { mutableStateOf(row.rxRate) }
    var history by remember(row.uid) {
        mutableStateOf(List(2) { MiniSpeedSample(row.txRate, row.rxRate) })
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(row.uid, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                prevRx = rx
                prevTx = tx
                accRx += (rxR * dt).toLong()
                accTx += (txR * dt).toLong()
                liveRx = rxR
                liveTx = txR
                peakRx = maxOf(peakRx, rxR)
                totalRx = if (rx > 0L) rx else accRx
                totalTx = if (tx > 0L) tx else accTx
                history = (history + MiniSpeedSample(txR, rxR)).takeLast(61)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            borderColor = nebulaColors.panelBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).navigationBarsPadding()
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
                            softWrap = true
                        )
                        Text(
                            text = row.packageName,
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = true
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

                Text(
                    text = t("Быстрое правило на 15 минут", "Quick rule for 15 minutes"),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                NimboToolActions {
                    OutlinedButton(onClick = onQuickVpn, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Через VPN", "Via VPN"))
                    }
                    OutlinedButton(onClick = onQuickDirect, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Напрямую", "Direct"))
                    }
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
                        .background(nebulaColors.controlFill)
                        .padding(8.dp)
                ) {
                    SpeedChartCanvas(samples = history, modifier = Modifier.fillMaxSize())
                }

                Spacer(Modifier.height(16.dp))

                // ── Live speeds ───────────────────────────────────────────
                NimboToolActions {
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
                NimboToolActions {
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
                NimboToolActions {
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
private fun ConnMetric(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String) {
    NimboToolMetric(label, value, modifier)
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
            Icon(icon, null, tint = nebulaColors.textTertiary, modifier = Modifier.size(24.dp))
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
    onWhitelistClick: () -> Unit,
    onCrossSyncClick: () -> Unit,
    notificationCount: Int = 0
) {
    NimboPanel(Modifier.fillMaxWidth()) {
        Column {
            SettingsRow(Icons.Default.Tune, t("Маршрутизация", "Routing"), null, onRoutingClick)
            SettingsRow(Icons.Default.VpnKey, t("Соединения", "Connections"), null, onConnectionsClick)
            SettingsRow(Icons.Default.Notifications, t("Уведомления", "Notifications"), notificationCount.takeIf { it > 0 }?.toString(), onNotificationsClick)
            SettingsRow(Icons.Default.BarChart, t("Статистика", "Statistics"), null, onStatsClick)
            SettingsRow(Icons.Default.Description, t("Журнал", "Logs"), null, onLogsClick)
            SettingsRow(Icons.Default.NetworkWifi, t("Диагностика сети", "Network diagnostics"), null, onWhitelistClick)
            SettingsRow(Icons.Default.CloudUpload, t("Синхронизация", "Sync"), null, onCrossSyncClick, showDivider = false)
        }
    }

}

@Composable
private fun SettingsSectionTabs(
    selected: Int,
    onSelect: (Int) -> Unit
) {
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
    var open by remember { mutableStateOf(false) }
    val active = tabs.firstOrNull { it.first == selected } ?: tabs.first()
    NimboPanel(Modifier.fillMaxWidth()) {
        Column {
            SettingsRow(active.second, active.third, t("Выбрать раздел", "Choose section"), { open = !open }, showDivider = open)
            if (open) tabs.forEach { (id, icon, label) ->
                SettingsRow(icon, label, null, { onSelect(id); open = false }, showDivider = id != tabs.last().first)
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    val materialYou = nebulaColors.isMaterialYou
    val haptic = LocalHapticFeedback.current
    val shape = nimboControlShape(12.dp, 2.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(
                nimboControlContainer(
                    if (selected) nebulaColors.accent.copy(alpha = 0.16f) else Color.Transparent,
                    selected = selected
                )
            )
            .then(
                if (mangaStyle && selected) Modifier.border(2.dp, nebulaColors.accent, shape)
                else Modifier
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (!selected) haptic.tick()
                    onClick()
                }
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
    val haptic = LocalHapticFeedback.current
    GlassPanel(
        modifier = modifier
            .height(104.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    haptic.tick()
                    onClick()
                }
            ),
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
                            color = com.danila.nimbo.ui.components.contrastingLabel(nebulaColors.accent),
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
        NetworkSettingsToggleRow(
            title = t("Анализ трафика", "Traffic sniffing"),
            subtitle = t("Определяет HTTP/TLS/QUIC для маршрутизации", "Detects HTTP/TLS/QUIC for routing"),
            checked = trafficSniffingEnabled,
            onCheckedChange = onTrafficSniffingChange,
            icon = Icons.Default.Visibility
        )
        NetworkSettingsToggleRow(
            title = t("TLS Fragment вручную", "Manual TLS Fragment"),
            subtitle = t(
                "Запасной режим: параметры сервиса из подписки применяются автоматически",
                "Fallback mode: provider parameters from the subscription are applied automatically"
            ),
            checked = tlsFragment,
            onCheckedChange = onTlsFragmentChange,
            icon = Icons.Default.Security
        )
        NetworkSettingsToggleRow(
            title = t("Фрагментация пакетов", "Packet fragmentation"),
            subtitle = t("Снижает MTU до 1280 для проблемных сетей", "Lowers MTU to 1280 for unstable networks"),
            checked = packetFragmentationEnabled,
            onCheckedChange = onPacketFragmentationChange,
            icon = Icons.Default.Tune
        )
        NetworkSettingsToggleRow(
            title = t("Мультиплексирование", "Mux"),
            subtitle = t("Объединяет несколько потоков в один канал", "Combines multiple streams into one channel"),
            checked = muxEnabled,
            onCheckedChange = onMuxChange,
            icon = Icons.Default.SwapVert
        )
        NetworkSettingsToggleRow(
            title = t("Блокировать UDP", "Block UDP"),
            subtitle = t("Отключает UDP-трафик внутри туннеля", "Disables UDP traffic inside the tunnel"),
            checked = blockUdp,
            onCheckedChange = onBlockUdpChange,
            icon = Icons.Default.Block
        )
        NetworkSettingsToggleRow(
            title = t("Держать устройство активным", "Keep device active"),
            subtitle = t("Удерживает wakelock во время работы VPN", "Keeps a wakelock while VPN is running"),
            checked = keepDeviceActive,
            onCheckedChange = onKeepDeviceActiveChange,
            icon = Icons.Default.Bolt
        )
        NetworkSettingsToggleRow(
            title = t("Исключать LAN", "Exclude LAN"),
            subtitle = t("Локальные адреса идут напрямую", "Local addresses go directly"),
            checked = allowLanConnections,
            onCheckedChange = onAllowLanConnectionsChange,
            icon = Icons.Default.NetworkWifi
        )
        NetworkSettingsToggleRow(
            title = t("Доступ через хотспот", "Hotspot access"),
            subtitle = t("Разрешает локальный доступ через точку доступа", "Allows local access through hotspot"),
            checked = allowHotspotAccess,
            onCheckedChange = onAllowHotspotAccessChange,
            icon = Icons.Default.Public,
            enabled = allowLanConnections
        )
        NetworkSettingsToggleRow(
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
                NetworkSettingsChoices(
                    items = listOf("IPv4", "IPv4 + IPv6"),
                    selectedIndex = if (vpnIpType == "dual") 1 else 0,
                    onSelect = { onVpnIpTypeChange(if (it == 1) "dual" else "ipv4") },
                    modifier = Modifier
                        .fillMaxWidth()
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
                NetworkSettingsChoices(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogRetentionOptionGrid(labels: List<String>, values: List<Int>, selectedValue: Int, onSelect: (Int) -> Unit) {
    val selected = selectedValue.takeIf { it in values } ?: 24
    NimboExpandingChoiceCard(
        options = labels.zip(values).map { (label, value) -> NimboChoiceOption(value, label) },
        selectedValue = selected,
        onSelect = onSelect,
        testTag = "log-retention"
    )
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
    NetworkSettingsRow(title, subtitle, icon) {
        SettingsStepperControl(value, valueLabel, min, max, step, onValueChange)
    }
    if (showDivider) HorizontalDivider(color = LocalNebulaColors.current.divider)
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
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(windowsControlFill(nebulaColors))
            .border(1.dp, windowsBorder(nebulaColors, 0.12f), shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                haptic.tick()
                onValueChange((value - step).coerceAtLeast(min))
            },
            enabled = value > min,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = t("Уменьшить", "Decrease"),
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
            onClick = {
                haptic.tick()
                onValueChange((value + step).coerceAtMost(max))
            },
            enabled = value < max,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = t("Увеличить", "Increase"),
                tint = if (value < max) nebulaColors.textPrimary else nebulaColors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, showDivider: Boolean = true, icon: ImageVector? = null,
    enabled: Boolean = true) {
    com.danila.nimbo.ui.components.NetworkSettingsToggleRow(title, subtitle, checked,
        onCheckedChange, showDivider, icon, enabled)
}

@Composable
private fun SettingsHapticStrengthButton(
    strength: Int,
    style: Int,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onStyleChange: (Int) -> Unit,
    showDivider: Boolean = true
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val labels = listOf(
        t("Лёгкая", "Light"),
        t("Средняя", "Medium"),
        t("Сильная", "Strong")
    )
    val safeStrength = strength.coerceIn(0, labels.lastIndex)
    val styleLabels = listOf(
        t("Мягкий", "Soft"),
        t("Чёткий", "Crisp"),
        t("Двойной", "Double"),
        t("Волна", "Wave"),
        t("Пульс", "Pulse"),
        t("Пружина", "Spring")
    )
    val safeStyle = style.coerceIn(0, styleLabels.lastIndex)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "haptic_strength_arrow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .alpha(if (enabled) 1f else 0.46f)
                .clickable(
                    enabled = enabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    haptic.tick()
                    onExpandedChange(!expanded)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Сила вибрации", "Vibration strength"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (enabled) {
                        t(
                            "Текущий уровень: ${labels[safeStrength]}",
                            "Current level: ${labels[safeStrength]}"
                        )
                    } else {
                        t(
                            "Сначала включите виброотклик",
                            "Enable haptic feedback first"
                        )
                    },
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) {
                    t("Скрыть настройку", "Hide setting")
                } else {
                    t("Показать настройку", "Show setting")
                },
                tint = nebulaColors.textSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(
            visible = enabled && expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Slider(
                    value = safeStrength.toFloat(),
                    onValueChange = rememberHapticSliderValueChange(
                        value = safeStrength.toFloat(),
                        valueRange = 0f..2f,
                        steps = 1
                    ) { value ->
                        onStrengthChange(value.roundToInt().coerceIn(0, 2))
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    colors = SliderDefaults.colors(
                        thumbColor = nebulaColors.accent,
                        activeTrackColor = nebulaColors.accent,
                        inactiveTrackColor = nebulaColors.textSecondary.copy(alpha = 0.24f),
                        activeTickColor = Color.White.copy(alpha = 0.72f),
                        inactiveTickColor = nebulaColors.textSecondary.copy(alpha = 0.52f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = if (index == safeStrength) {
                                nebulaColors.accent
                            } else {
                                nebulaColors.textTertiary
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (index == safeStrength) {
                                FontWeight.ExtraBold
                            } else {
                                FontWeight.SemiBold
                            },
                            textAlign = when (index) {
                                0 -> TextAlign.Start
                                labels.lastIndex -> TextAlign.End
                                else -> TextAlign.Center
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = t("Характер отклика", "Feedback style"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = t(
                        "Выберите ощущение — вариант сразу проиграется",
                        "Choose a feel — it will play immediately"
                    ),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    styleLabels.chunked(3).forEachIndexed { rowIndex, rowLabels ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowLabels.forEachIndexed { columnIndex, label ->
                                val index = rowIndex * 3 + columnIndex
                                val selected = index == safeStyle
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) nebulaColors.accent.copy(alpha = 0.20f)
                                            else nebulaColors.textPrimary.copy(alpha = 0.045f)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) nebulaColors.accent.copy(alpha = 0.72f)
                                            else nebulaColors.textPrimary.copy(alpha = 0.09f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            if (!selected) onStyleChange(index)
                                            performConnectionSuccessHaptic(
                                                context = context,
                                                enabled = enabled,
                                                strength = safeStrength,
                                                style = index
                                            )
                                        }
                                        .padding(horizontal = 6.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    val nebulaColors = LocalNebulaColors.current
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        borderColor = nebulaColors.textPrimary.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit,
    showDivider: Boolean = true) {
    com.danila.nimbo.ui.components.SettingsNavigationItem(icon, title, subtitle.orEmpty(), onClick)
    if (showDivider) HorizontalDivider(color = LocalNebulaColors.current.divider)
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
    onHeightMeasured: (Dp) -> Unit,
    backdropLayer: GraphicsLayer,
    destination: MiniDestination,
    visible: Boolean,
    onDestinationChange: (MiniDestination) -> Unit
) {
    if (!visible) return
    val colors = LocalNebulaColors.current
    val entries = listOf(
        Triple(MiniDestination.Home, Icons.Default.PowerSettingsNew, t("Главная", "Home")),
        Triple(MiniDestination.Subscription, Icons.Default.Layers, t("Профили", "Profiles")),
        Triple(MiniDestination.Connections, Icons.AutoMirrored.Filled.ShowChart, t("Активность", "Activity")),
        Triple(MiniDestination.Settings, Icons.Default.Tune, t("Настройки", "Settings"))
    )
    val navDensity = androidx.compose.ui.platform.LocalDensity.current
    val navMotionEnabled = rememberNavIconMotionEnabled()
    Surface(modifier = Modifier.align(Alignment.BottomCenter)
        .onSizeChanged { onHeightMeasured(with(navDensity) { it.height.toDp() }) }.navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp).widthIn(max = 520.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = colors.controlFill,
        border = BorderStroke(1.dp, colors.panelBorder), tonalElevation = 0.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            entries.forEach { (target, icon, label) ->
                val selected = destination == target || (target == MiniDestination.Settings && destination == MiniDestination.AppAccess)
                val interaction = remember(target) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val fill by animateColorAsState(
                    targetValue = when {
                        selected -> colors.textPrimary.copy(alpha = .14f)
                        pressed -> colors.textPrimary.copy(alpha = .07f)
                        else -> Color.Transparent
                    },
                    animationSpec = if (navMotionEnabled) tween(160) else snap(),
                    label = "bottom-nav-fill-${target.name}"
                )
                val scale by animateFloatAsState(
                    targetValue = if (pressed) .96f else 1f,
                    animationSpec = if (navMotionEnabled) tween(130) else snap(),
                    label = "bottom-nav-press-${target.name}"
                )
                Box(modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(20.dp)).background(fill)
                    .clickable(interactionSource = interaction, indication = null) { onDestinationChange(target) }
                    .semantics {
                        this.selected = selected
                        role = Role.Tab
                    }, contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) colors.textPrimary else colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center, color = if (selected) colors.textPrimary else colors.textSecondary)
                    }
                }
            }
        }
    }

}

@Composable
private fun LiquidGlassBottomNavRow(
    destination: MiniDestination,
    onDestinationChange: (MiniDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(MiniDestination.Home, Icons.Default.Home, t("Главная", "Home")),
        Triple(MiniDestination.Subscription, Icons.Default.Public, t("Профили", "Profiles")),
        Triple(MiniDestination.AppAccess, Icons.Default.Smartphone, t("Приложения", "Apps")),
        Triple(MiniDestination.Settings, Icons.Default.Settings, t("Настройки", "Settings"))
    )
    val selectedIndex = items.indexOfFirst { it.first == destination }.coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cornerScale = LocalGlobalCornerRadius.current
    val nebulaColors = LocalNebulaColors.current
    // Подсветка ездит под значками, поэтому подчиняется той же настройке: без
    // неё «выключенная анимация» оставляла на панели самое заметное движение.
    val miniMotionEnabled = rememberNavIconMotionEnabled()
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var restingIndex by remember { mutableIntStateOf(selectedIndex) }
    var lastPreviewIndex by remember { mutableIntStateOf(selectedIndex) }
    val bubblePosition = remember { Animatable(selectedIndex.toFloat()) }
    val landingImpact = remember { Animatable(0f) }
    val landingWave = remember { Animatable(1f) }
    var landingEpoch by remember { mutableIntStateOf(0) }
    var preparedLandingEpoch by remember { mutableIntStateOf(0) }
    var landingFromIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var landingVelocityIndex by remember { mutableFloatStateOf(0f) }
    var landingImpactStrength by remember { mutableFloatStateOf(0f) }

    fun requestLanding(
        fromIndex: Float,
        targetIndex: Int,
        velocityIndexPerSecond: Float,
        travelDistance: Float,
        plop: Boolean = true
    ) {
        restingIndex = targetIndex
        landingFromIndex = fromIndex
        landingVelocityIndex = velocityIndexPerSecond
        landingImpactStrength = if (plop) {
            LiquidInteractionPolicy.landingImpact(velocityIndexPerSecond, travelDistance)
        } else {
            0f
        }
        landingEpoch += 1
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != restingIndex) {
            val fromIndex = if (dragging) dragIndex else bubblePosition.value
            requestLanding(
                fromIndex = fromIndex,
                targetIndex = selectedIndex,
                velocityIndexPerSecond = 0f,
                travelDistance = kotlin.math.abs(selectedIndex - fromIndex)
            )
        }
    }

    // Признак движения намеренно не в ключах: при переключении настройки
    // эффект запускался заново и вёз подсветку с прежнего места на текущую
    // вкладку — выглядело это как самопроизвольный переход.
    LaunchedEffect(landingEpoch, dragging) {
        if (dragging) {
            landingImpact.snapTo(0f)
            landingWave.snapTo(1f)
            return@LaunchedEffect
        }
        if (landingEpoch == 0) return@LaunchedEffect
        val currentEpoch = landingEpoch
        val target = restingIndex.toFloat()
        val remainingDistance = kotlin.math.abs(target - landingFromIndex)
        bubblePosition.snapTo(landingFromIndex)
        preparedLandingEpoch = currentEpoch

        if (!miniMotionEnabled) {
            bubblePosition.snapTo(target)
            landingImpact.snapTo(0f)
            landingWave.snapTo(1f)
            return@LaunchedEffect
        }

        val contactDelay = LiquidInteractionPolicy.landingDelayMillis(remainingDistance)
        coroutineScope {
            launch {
                bubblePosition.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.56f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialVelocity = landingVelocityIndex * 0.58f
                )
            }
            if (landingImpactStrength > 0f) {
                launch {
                    delay(contactDelay)
                    haptic.confirm()
                    landingImpact.snapTo(0f)
                    landingImpact.animateTo(
                        targetValue = landingImpactStrength,
                        animationSpec = tween(72, easing = FastOutSlowInEasing)
                    )
                    landingImpact.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.48f,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    delay(contactDelay + 18L)
                    landingWave.snapTo(0f)
                    landingWave.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(430, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                landingImpact.snapTo(0f)
                landingWave.snapTo(1f)
            }
        }
    }

    val followedDragIndex by animateFloatAsState(
        targetValue = dragIndex,
        animationSpec = if (miniMotionEnabled) {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessHigh)
        } else {
            snap()
        },
        label = "liquid_tab_drag_follow"
    )
    val bubbleIndex = when {
        dragging -> followedDragIndex
        landingEpoch > 0 && preparedLandingEpoch != landingEpoch -> landingFromIndex
        else -> bubblePosition.value
    }
    val previewIndex = if (dragging) {
        LiquidInteractionPolicy.nearestTabIndex(dragIndex, items.size)
    } else {
        restingIndex
    }
    val stretch = if (dragging) LiquidInteractionPolicy.bubbleStretch(followedDragIndex) else 0f
    val bubbleScaleX by animateFloatAsState(
        targetValue = if (dragging) 1.10f + stretch * 0.30f else 1f,
        animationSpec = if (miniMotionEnabled) {
            spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "liquid_tab_bubble_scale_x"
    )
    val bubbleScaleY by animateFloatAsState(
        targetValue = if (dragging) 0.95f - stretch * 0.06f else 1f,
        animationSpec = if (miniMotionEnabled) {
            spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "liquid_tab_bubble_scale_y"
    )

    fun updateDragPosition(x: Float) {
        val newIndex = LiquidInteractionPolicy.continuousTabIndex(x, barWidthPx, items.size)
        dragIndex = newIndex
        val nearest = LiquidInteractionPolicy.nearestTabIndex(newIndex, items.size)
        if (nearest != lastPreviewIndex) {
            lastPreviewIndex = nearest
            haptic.tick()
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(barWidthPx, selectedIndex, haptic) {
                    if (barWidthPx <= 0f) return@pointerInput
                    var dragStartIndex = selectedIndex.toFloat()
                    var lastDragX = 0f
                    var lastDragTimeMillis = 0L
                    var smoothedVelocityPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            lastPreviewIndex = selectedIndex
                            dragStartIndex = bubbleIndex
                            lastDragX = offset.x
                            lastDragTimeMillis = 0L
                            smoothedVelocityPx = 0f
                            updateDragPosition(offset.x)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (lastDragTimeMillis > 0L) {
                                val elapsedMillis = (change.uptimeMillis - lastDragTimeMillis)
                                    .coerceAtLeast(1L)
                                val instantaneousVelocity =
                                    (change.position.x - lastDragX) * 1000f / elapsedMillis
                                smoothedVelocityPx =
                                    smoothedVelocityPx * 0.68f + instantaneousVelocity * 0.32f
                            }
                            lastDragX = change.position.x
                            lastDragTimeMillis = change.uptimeMillis
                            updateDragPosition(change.position.x)
                        },
                        onDragEnd = {
                            val itemWidthPx = barWidthPx / items.size
                            val velocityIndex = LiquidInteractionPolicy.landingVelocity(
                                velocityPxPerSecond = smoothedVelocityPx,
                                itemWidthPx = itemWidthPx
                            )
                            val targetIndex = LiquidInteractionPolicy.landingTargetIndex(
                                continuousIndex = dragIndex,
                                velocityIndexPerSecond = velocityIndex,
                                itemCount = items.size
                            )
                            val landingFrom = followedDragIndex
                            requestLanding(
                                fromIndex = landingFrom,
                                targetIndex = targetIndex,
                                velocityIndexPerSecond = velocityIndex,
                                travelDistance = kotlin.math.abs(targetIndex - dragStartIndex)
                            )
                            dragging = false
                            if (targetIndex != selectedIndex) {
                                onDestinationChange(items[targetIndex].first)
                            }
                        },
                        onDragCancel = {
                            requestLanding(
                                fromIndex = followedDragIndex,
                                targetIndex = selectedIndex,
                                velocityIndexPerSecond = 0f,
                                travelDistance = 0f,
                                plop = false
                            )
                            dragging = false
                        }
                    )
                }
        ) {
            if (barWidthPx > 0f) {
                val itemWidthPx = barWidthPx / items.size
                val itemWidth = with(density) { itemWidthPx.toDp() }
                val bubbleShape = RoundedCornerShape(
                    (24.dp + 4.dp * landingImpact.value) * cornerScale
                )
                if (landingWave.value < 0.999f) {
                    val waveProgress = landingWave.value.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (restingIndex * itemWidthPx).roundToInt(),
                                    y = 0
                                )
                            }
                            .width(itemWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 5.dp, vertical = 8.dp)
                            .graphicsLayer {
                                scaleX = 0.92f + waveProgress * 0.64f
                                scaleY = 0.84f + waveProgress * 0.30f
                                alpha = (1f - waveProgress) * 0.48f
                            }
                            .clip(bubbleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.18f),
                                        nebulaColors.accent.copy(alpha = 0.26f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                1.25.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.62f),
                                        nebulaColors.accent.copy(alpha = 0.74f),
                                        Color.White.copy(alpha = 0.24f),
                                        nebulaColors.accent.copy(alpha = 0.54f),
                                        Color.White.copy(alpha = 0.62f)
                                    )
                                ),
                                bubbleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (bubbleIndex * itemWidthPx).roundToInt(),
                                y = 0
                            )
                        }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 3.dp, vertical = 6.dp)
                        .graphicsLayer {
                            scaleX = bubbleScaleX * (1f + landingImpact.value * 0.18f)
                            scaleY = bubbleScaleY * (1f - landingImpact.value * 0.12f)
                            translationY = with(density) { (landingImpact.value * 1.5f).dp.toPx() }
                        }
                        .liquidGlassSurface(
                            shape = bubbleShape,
                            depth = LiquidGlassDepth.CONTROL,
                            interactive = false
                        )
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(
                                            alpha = 0.13f + landingImpact.value * 0.10f
                                        ),
                                        nebulaColors.accent.copy(
                                            alpha = 0.20f + landingImpact.value * 0.16f
                                        ),
                                        Color.White.copy(
                                            alpha = 0.06f + landingImpact.value * 0.06f
                                        )
                                    )
                                )
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    WindowsBottomNavItem(
                        selected = index == previewIndex,
                        icon = item.second,
                        label = item.third,
                        onClick = {
                            if (index != selectedIndex) {
                                val fromIndex = bubblePosition.value
                                requestLanding(
                                    fromIndex = fromIndex,
                                    targetIndex = index,
                                    velocityIndexPerSecond = 0f,
                                    travelDistance = kotlin.math.abs(index - fromIndex)
                                )
                                haptic.tick()
                                onDestinationChange(item.first)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        externalSelection = true,
                        motionKind = navMotionKind(item.first)
                    )
                }
            }
        }
    }
}

private data class MaterialYouNavEntry(
    val destination: MiniDestination,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val label: String
)

/**
 * Навигация в духе Material 3: выбранный пункт разворачивается в таблетку
 * с подписью, остальные остаются одними иконками. Ширина слота и раскрытие
 * подписи анимируются пружиной, цвета берутся из ролей динамической схемы.
 */
@Composable
private fun MaterialYouBottomNavRow(
    destination: MiniDestination,
    onSelect: (MiniDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        MaterialYouNavEntry(
            MiniDestination.Home,
            Icons.Default.Home,
            Icons.Outlined.Home,
            t("Главная", "Home")
        ),
        MaterialYouNavEntry(
            MiniDestination.Subscription,
            Icons.Default.Public,
            Icons.Outlined.Public,
            t("Профили", "Profiles")
        ),
        MaterialYouNavEntry(
            MiniDestination.AppAccess,
            Icons.Default.Smartphone,
            Icons.Outlined.Smartphone,
            t("Приложения", "Apps")
        ),
        MaterialYouNavEntry(
            MiniDestination.Settings,
            Icons.Default.Settings,
            Icons.Outlined.Settings,
            t("Настройки", "Settings")
        )
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEach { entry ->
            MaterialYouBottomNavItem(
                entry = entry,
                selected = destination == entry.destination,
                onClick = { onSelect(entry.destination) }
            )
        }
    }
}

@Composable
private fun RowScope.MaterialYouBottomNavItem(
    entry: MaterialYouNavEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val navMotionEnabled = rememberNavIconMotionEnabled()
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        // Раскрытие пункта переносит значок вбок — это тоже его движение.
        animationSpec = if (navMotionEnabled) {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        } else {
            snap()
        },
        label = "material-nav-selection"
    )
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Подпись раскрывается из-под иконки: ширина ползёт от нуля до реальной
    // ширины текста, поэтому текст не переносится и не дёргает соседние пункты.
    val labelWidth = remember(entry.label, labelStyle, density) {
        with(density) {
            measurer.measure(
                text = entry.label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false
            ).size.width.toDp()
        }
    }
    val contentColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSecondaryContainer,
        selection
    )
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = selection)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f + 2.2f * selection)
            .height(56.dp)
            .clip(CircleShape)
            .background(indicatorColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bounce = rememberNavIconBounce(selected, navMotionKind(entry.destination))
            // Плавный ход общего признака выбора оставляем подсветке, а значку
            // при выключенном движении отдаём готовое значение.
            val iconSelection = if (navMotionEnabled) selection else if (selected) 1f else 0f
            Icon(
                imageVector = if (selected) entry.selectedIcon else entry.icon,
                contentDescription = entry.label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val base = 1f + 0.08f * iconSelection
                        scaleX = base * bounce.scale * bounce.squash
                        scaleY = base * bounce.scale / bounce.squash
                        translationY = bounce.lift
                        rotationZ = bounce.rotation
                    }
            )
            Spacer(Modifier.width(8.dp * selection))
            Box(
                modifier = Modifier
                    .width(labelWidth.coerceAtMost(120.dp) * selection)
                    .clipToBounds()
            ) {
                Text(
                    text = entry.label,
                    color = contentColor,
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .graphicsLayer {
                            alpha = ((selection - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        }
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
    modifier: Modifier = Modifier,
    externalSelection: Boolean = false,
    motionKind: NavIconMotionKind = NavIconMotionKind.HOP
) {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    // Scale the selected-item highlight with the corner slider so it nests inside the
    // rounded bar instead of staying a fixed square while the panel becomes a pill.
    val cornerScale = LocalGlobalCornerRadius.current
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp * cornerScale else 14.dp * cornerScale)
    // Одна настройка на весь отклик значка: и на его собственное движение, и
    // на всё, что двигает его снаружи.
    val navMotionEnabled = rememberNavIconMotionEnabled()

    if (materialYou) {
        val activeIndicatorColor = nebulaColors.accent.copy(
            alpha = if (nebulaColors.isLight) 0.22f else 0.16f
        )
        val iconColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        val textColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

        // Smooth transitions for colors in Material You mode
        val colorSpec = if (navMotionEnabled) {
            tween<Color>(durationMillis = 200, easing = FastOutSlowInEasing)
        } else {
            snap()
        }
        val animatedIconColor by animateColorAsState(
            targetValue = iconColor,
            animationSpec = colorSpec,
            label = "iconColor"
        )
        val animatedTextColor by animateColorAsState(
            targetValue = textColor,
            animationSpec = colorSpec,
            label = "textColor"
        )

        // Pill indicator width stretching using medium-bouncy spring
        val pillWidth by animateDpAsState(
            targetValue = if (selected) 64.dp else 32.dp,
            animationSpec = if (navMotionEnabled) {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            } else {
                snap()
            },
            label = "pillWidth"
        )

        // Pill indicator alpha fade-in/fade-out
        val pillAlpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = if (navMotionEnabled) {
                tween(durationMillis = 180, easing = EaseInOut)
            } else {
                snap()
            },
            label = "pillAlpha"
        )

        // Выбранный значок крупнее — но приезжает он к этому размеру только
        // когда движение разрешено; иначе разница появляется сразу.
        val iconScale by animateFloatAsState(
            targetValue = if (selected) 1.12f else 1.0f,
            animationSpec = if (navMotionEnabled) {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            } else {
                snap()
            },
            label = "iconScale"
        )
        val bounce = rememberNavIconBounce(selected, motionKind)

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
                            .graphicsLayer {
                                scaleX = iconScale * bounce.scale * bounce.squash
                                scaleY = iconScale * bounce.scale / bounce.squash
                                translationY = bounce.lift
                                rotationZ = bounce.rotation
                            }
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
            externalSelection -> Color.Transparent
            dottedStyle -> nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.18f else 0.16f)
            nebulaColors.isLight -> Color.Transparent
            else -> Color.White.copy(alpha = 0.075f)
        }
        val selectedBorder = when {
            externalSelection || dottedStyle -> Color.Transparent
            nebulaColors.isLight -> Color.Transparent
            else -> Color.White.copy(alpha = 0.16f)
        }
        val contentColor = when {
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
                .then(
                    if (dottedStyle) {
                        Modifier
                            .dotPatternOverlay(
                                color = nebulaColors.textPrimary,
                                spacing = 7.dp,
                                radius = 0.55.dp,
                                alpha = if (nebulaColors.isLight) 0.12f else 0.16f
                            )
                            .dottedOutline(
                                color = nebulaColors.accent,
                                cornerRadius = 8.dp * cornerScale,
                                alpha = 0.94f
                            )
                    } else {
                        Modifier.border(1.dp, selectedBorder, shape)
                    }
                )
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
                // Тот же прыжок, что и в раскладке Material You: панель другая,
                // а отклик на выбор должен быть одинаковым.
                val bounce = rememberNavIconBounce(selected, motionKind)
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            scaleX = bounce.scale * bounce.squash
                            scaleY = bounce.scale / bounce.squash
                            translationY = bounce.lift
                            rotationZ = bounce.rotation
                        }
                )
                // Подпись раскрывается всегда: тумблер выключает движение
                // значков, а это движение текста — без него выбранная вкладка
                // возникала рывком.
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val materialYou = nebulaColors.isMaterialYou
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
    val materialButtonColor = if (materialYou && !connected && !connecting) {
        nebulaColors.accent
    } else {
        accentColor
    }

    // Don't drive any infinite animation while disconnected — that's the most
    // common idle state and the FAB has no animated visuals there.
    val animationsActive = miniMotionEnabled && (connected || connecting) && !dottedStyle
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
        targetValue = if (pressed && !nebulaColors.isLiquidGlass) 0.9f else 1f,
        animationSpec = if (miniMotionEnabled) {
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh)
        } else {
            snap()
        },
        label = "press-scale"
    )

    val mainButtonShape: Shape = when {
        dottedStyle -> RoundedCornerShape(14.dp)
        materialYou -> RoundedCornerShape(26.dp)
        else -> CircleShape
    }
    val mainButtonSize = when {
        dottedStyle -> 76.dp
        materialYou -> 78.dp
        else -> 70.dp
    }

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
                    .size(if (dottedStyle) 92.dp else if (materialYou) 88.dp else 76.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        shape = mainButtonShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(mainButtonSize)
                .scale(pressScale)
                .then(
                    if (nebulaColors.isLiquidGlass) {
                        Modifier.liquidTouchDeformation(LiquidGlassDepth.CONTROL)
                    } else {
                        Modifier
                    }
                )
                .clip(mainButtonShape)
                .background(
                    when {
                        dottedStyle -> Brush.linearGradient(
                            listOf(
                                nebulaColors.surface.copy(alpha = 0.98f),
                                nebulaColors.accent.copy(alpha = if (connected) 0.18f else 0.08f)
                            )
                        )
                        materialYou -> Brush.linearGradient(
                            listOf(
                                materialButtonColor.copy(alpha = if (connected) 0.90f else 0.82f),
                                materialButtonColor.copy(alpha = if (connected) 0.72f else 0.64f)
                            )
                        )
                        else -> Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = if (connected) 0.48f else 0.22f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.22f)
                            )
                        )
                    }
                )
                .then(
                    if (dottedStyle) Modifier else Modifier.border(
                        1.dp,
                        if (materialYou) Color.White.copy(alpha = 0.26f) else accentColor.copy(alpha = if (connected) 0.55f else 0.32f),
                        mainButtonShape
                    )
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
            if (dottedStyle) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .dotPatternOverlay(
                            color = nebulaColors.textPrimary,
                            spacing = 8.dp,
                            radius = 0.62.dp,
                            alpha = if (nebulaColors.isLight) 0.12f else 0.16f
                        )
                        .dottedOutline(
                            color = accentColor,
                            cornerRadius = 14.dp,
                            thickness = 1.2.dp,
                            alpha = 0.98f
                        )
                )
            }
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
                        // The connected mark follows the selected visual system rather
                        // than reusing the same glass square in every theme.
                        Box(
                            modifier = Modifier
                                .size(if (materialYou) 26.dp else 22.dp)
                                .clip(RoundedCornerShape(if (dottedStyle) 3.dp else if (materialYou) 9.dp else 5.dp))
                                .background(
                                    if (dottedStyle) accentColor else Color.White.copy(alpha = if (materialYou) 0.94f else 0.72f)
                                )
                                .then(
                                    if (dottedStyle) Modifier.dottedOutline(
                                        color = nebulaColors.textPrimary,
                                        cornerRadius = 3.dp,
                                        alpha = 0.76f
                                    ) else Modifier
                                )
                        )
                    }
                    VpnState.CONNECTING -> {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = if (materialYou) Color.White else accentColor,
                            modifier = Modifier
                                .size(32.dp)
                                .rotate(refreshRotation)
                        )
                    }
                    VpnState.DISCONNECTED -> {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "VPN отключен",
                            tint = if (materialYou) Color.White else accentColor,
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
    val colors = LocalNebulaColors.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = colors.panelFill, border = BorderStroke(1.dp, colors.panelBorder)) {
        Row(Modifier.padding(4.dp).height(IntrinsicSize.Min)) {
            SegmentChoice(left, leftSelected, onLeft, Modifier.weight(1f))
            SegmentChoice(right, !leftSelected, onRight, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SegmentChoice(text: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Surface(onClick, modifier.fillMaxHeight().heightIn(min = 48.dp).semantics {
        this.selected = selected; role = Role.Tab
    }, shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.controlFill else Color.Transparent) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) colors.accent else colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MiniIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 46.dp,
    iconSize: Dp = 26.dp,
    motion: MiniIconMotion = MiniIconMotion.None,
    active: Boolean = false,
    shape: Shape = CircleShape
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
        targetValue = if (pressed && !nebulaColors.isLiquidGlass) 0.90f else 1f,
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
            .clip(shape)
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
    } else if (nebulaColors.isLiquidGlass) {
        this
            .liquidTouchDeformation(depth = LiquidGlassDepth.CONTROL, interactive = enabled)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
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

/**
 * Popup menus cannot use the same translucent fill as in-layout glass panels:
 * Compose renders them in a separate window without our backdrop blur, so the
 * content underneath otherwise competes with menu labels. Keep the theme tint,
 * but resolve it over the page background to guarantee an opaque, readable popup.
 */
private fun windowsMenuFill(colors: NebulaColors): Color {
    val minimumAlpha = when {
        colors.isMaterialYou -> 0.98f
        colors.isLight -> 0.94f
        else -> 0.92f
    }
    return colors.panelFill
        .copy(alpha = maxOf(colors.panelFill.alpha, minimumAlpha))
        .compositeOver(colors.background)
}

private fun windowsControlFill(colors: NebulaColors): Color = colors.controlFill

private fun windowsSoftFill(colors: NebulaColors): Color = colors.softFill

private fun windowsBorder(colors: NebulaColors, darkAlpha: Float = 0.10f): Color = colors.panelBorder

private fun windowsDivider(colors: NebulaColors): Color = colors.divider

@Composable
private fun scaleRoundedCornerShape(shape: RoundedCornerShape, scale: Float): RoundedCornerShape {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Manga уже учтена в самом множителе, поэтому здесь только Dotted и
    // Signal — иначе поправка накладывалась бы дважды.
    val compactDottedCorners = when (LocalElementStyleMode.current) {
        ElementStyleMode.NOTHING_DOTS -> 0.48f
        ElementStyleMode.SIGNAL -> 0.62f
        else -> 1f
    }
    val dummySize = with(density) { androidx.compose.ui.geometry.Size(500.dp.toPx(), 500.dp.toPx()) }
    return RoundedCornerShape(
        topStart = androidx.compose.foundation.shape.CornerSize(shape.topStart.toPx(dummySize, density) * scale * compactDottedCorners),
        topEnd = androidx.compose.foundation.shape.CornerSize(shape.topEnd.toPx(dummySize, density) * scale * compactDottedCorners),
        bottomEnd = androidx.compose.foundation.shape.CornerSize(shape.bottomEnd.toPx(dummySize, density) * scale * compactDottedCorners),
        bottomStart = androidx.compose.foundation.shape.CornerSize(shape.bottomStart.toPx(dummySize, density) * scale * compactDottedCorners)
    )
}

/**
 * Чернильная тень кадра: сплошная заливка со смещением, без размытия.
 *
 * Именно она отличает нарисованную панель от «просто рамки» — на компьютере
 * стиль держится на ней.
 */
private fun Modifier.inkShadow(
    shape: RoundedCornerShape,
    color: Color,
    offset: Dp = 2.dp
): Modifier = this.drawBehind {
    val shift = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(left = shift, top = shift) {
        drawOutline(outline, color = color)
    }
}

/** Красная косая засечка перед заголовком раздела — как на компьютере. */
@Composable
private fun MangaSectionSlash(modifier: Modifier = Modifier) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .width(5.dp)
            .height(16.dp)
            .clip(MangaSlashShape)
            .background(nebulaColors.accent)
    )
}

/** Скос засечки: те же двенадцать градусов, что и в десктопной вёрстке. */
private val MangaSlashShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val slant = size.height * 0.21f
    moveTo(slant, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - slant, size.height)
    lineTo(0f, size.height)
    close()
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
    NimboPanel(modifier.fillMaxWidth(), content)

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

    com.danila.nimbo.ui.components.NebulaMorphicDialog(
        onDismissRequest = onDismiss, title = t("Добавить профиль", "Add profile"),
        description = t("Подписка или отдельный сервер", "Subscription or a single server"),
        confirmButtonText = null, cancelButtonText = null, onConfirm = { onAdd(url.trim()) }
    ) {
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Ссылка или конфигурация", "Link or configuration")) },
            placeholder = { Text("https://… / vless://…") },
            shape = RoundedCornerShape(10.dp), minLines = 2, maxLines = 5)
        Button(onClick = { onAdd(url.trim()) }, enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(9.dp)) {
            Text(t("Импортировать", "Import"))
        }
        HorizontalDivider(color = nebulaColors.divider)
        com.danila.nimbo.ui.components.NimboToolActions(minCellDp = 120f) {
            NimboAction(Icons.Default.ContentPaste, t("Буфер", "Clipboard"), ::pasteFromClipboard, Modifier.weight(1f))
            NimboAction(Icons.Default.FolderOpen, t("Файл", "File"),
                { fileLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*")) }, Modifier.weight(1f))
            NimboAction(Icons.Default.QrCodeScanner, t("QR-код", "QR code"), onQr, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddDialogMethodTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = windowsControlFill(nebulaColors),
        border = BorderStroke(1.dp, windowsBorder(nebulaColors, 0.16f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(nebulaColors.accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
            }
            Text(
                text = label,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = subtitle,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
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
    BackgroundColor("background_color"),
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
    val protocolText = if (protocol == 5) "Nimbo Ping" else if (throughProxy) {
        t("Через VPN", "Through VPN")
    } else when (protocol) {
        1 -> "HTTP GET"
        2 -> "HTTP HEAD"
        3 -> "HTTPS Strict"
        4 -> "ICMP"
        else -> "TCP"
    }
    val displayText = PingDisplay.fromId(displayMode).key
    return "$protocolText · $displayText"
}

@Composable
private fun NimboPingSettingsScreen(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    NimboSubPageScaffold(title = t("Задержка", "Latency"), onBack = onBack) {
        PingSettingsSection(preferencesManager, mainViewModel)
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ColumnScope.PingSettingsSection(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel
) {
    val nebulaColors = LocalNebulaColors.current
    val pingProtocol by preferencesManager.pingProtocolState
    val pingTimeout by preferencesManager.pingTimeoutState
    val pingUrl by preferencesManager.pingUrlState
    val pingDisplayMode by preferencesManager.pingDisplayModeState
    val pingThroughProxy by preferencesManager.pingThroughProxyState

    AppearanceSectionHeader(
        title = t("Проверка задержки", "Latency check"),
        subtitle = t(
            "Отдельно измеряйте прямой путь до ноды и HTTP-запрос через активный VPN",
            "Measure the direct node path and HTTP through the active VPN separately"
        )
    )
    Spacer(Modifier.height(14.dp))

    com.danila.nimbo.ui.components.PingDiagnosticSummary(english = t("ru", "en") == "en")

    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PingSettingsStackedRow(
                title = t("Протокол", "Protocol"),
                subtitle = t("Метод, которым Nimbo измеряет задержку серверов", "How Nimbo measures server latency"),
                icon = Icons.Default.Language
            ) {
                PingProtocol.settingsOrder.forEach { method ->
                        val id = method.id
                        val label = when (method) {
                            PingProtocol.NIMBO -> "Nimbo Ping"
                            PingProtocol.HTTP_GET -> "HTTP GET"
                            PingProtocol.HTTP_HEAD -> "HTTP HEAD"
                            PingProtocol.HTTPS_STRICT -> "HTTPS Strict"
                            else -> method.name
                        }
                        PingChoiceRow(
                            title = label,
                            subtitle = when (method) {
                                PingProtocol.NIMBO -> t("GET через сам сервер, без включения VPN", "GET through this server, without starting VPN")
                                PingProtocol.TCP -> t("Доступность адреса и порта узла", "Whether the node's address and port respond")
                                PingProtocol.HTTP_GET -> t("Ответ контрольного URL на GET", "GET response from the test URL")
                                PingProtocol.HTTP_HEAD -> t("Только заголовки контрольного URL", "Headers from the test URL only")
                                PingProtocol.HTTPS_STRICT -> t("TLS и HTTP-ответ адреса узла", "TLS and HTTP response from the node address")
                                PingProtocol.ICMP -> t("Сетевой отклик узла, если разрешён", "Network echo if the node allows it")
                            },
                            selected = pingProtocol == id,
                            onClick = { preferencesManager.pingProtocol = id }
                        )
                    }
            }
            PingSettingsDivider()
            PingSettingsWideRow(
                title = t("Через VPN", "Through VPN"),
                subtitle = if (pingProtocol == 5) t(
                    "Для Nimbo Ping маршрут сервера выбирается автоматически; VPN не нужен.",
                    "Nimbo Ping selects each server route automatically; VPN is not required."
                ) else if (pingThroughProxy) t(
                    "Проверка через выбранный VPN-маршрут.",
                    "Check through the selected VPN route."
                ) else t(
                    "Проверка напрямую, без VPN.",
                    "Check directly, without VPN."
                ),
                icon = Icons.Default.VpnLock
            ) {
                Switch(
                    checked = pingThroughProxy || pingProtocol == 5,
                    enabled = pingProtocol != 5,
                    onCheckedChange = { preferencesManager.pingThroughProxy = it }
                )
            }
            PingSettingsDivider()
            PingSettingsStackedRow(
                title = t("URL проверки", "Test URL"),
                subtitle = t("Готовый адрес или свой HTTP/HTTPS URL", "Preset or custom HTTP/HTTPS URL"),
                icon = Icons.Default.Link
            ) {
                NetworkSettingsTextField(
                    value = pingUrl,
                    onValueChange = { preferencesManager.pingUrl = it },
                    label = "URL", singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        "Google" to "https://www.gstatic.com/generate_204",
                        "Cloud" to "https://cp.cloudflare.com/generate_204",
                        "Apple" to "https://captive.apple.com/hotspot-detect.html"
                    ).forEach { (label, url) ->
                        PingUrlPresetChip(label, pingUrl == url, { preferencesManager.pingUrl = url }, Modifier.weight(1f))
                    }
                }
                if (!ActiveProxyPing.validUrl(pingUrl)) {
                    PingSettingsHint(t("Введите HTTP/HTTPS URL без логина, пароля и фрагмента.", "Enter an HTTP/HTTPS URL without credentials or a fragment."))
                }
            }
            PingSettingsDivider()
            PingSettingsStackedRow(
                title = t("Таймаут (секунды)", "Timeout (seconds)"),
                subtitle = t(
                    "1–10 секунд ожидания. Пинг сервера отображается в мс.",
                    "Wait 1–10 seconds. Server latency is shown in ms."
                ),
                icon = Icons.Default.Schedule,
                contentSpacing = 8.dp
            ) {
                com.danila.nimbo.ui.components.PingTimeoutControl(
                    seconds = pingTimeout,
                    onSecondsChange = { preferencesManager.pingTimeout = it },
                    english = t("ru", "en") == "en"
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
                PingDisplay.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { display ->
                            PingDisplayOption(display, pingDisplayMode == display.id,
                                { preferencesManager.pingDisplayMode = display.id }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
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
    NetworkSettingsRow(title, subtitle, icon, control)

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
    contentSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        NetworkSettingsLabel(title, subtitle, icon, Modifier.fillMaxWidth())
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp else 18.dp * cornerScale)
    val fill by animateColorAsState(
        targetValue = if (selected) {
            nebulaColors.accent.copy(alpha = if (dottedStyle) 0.12f else 0.18f)
        } else nebulaColors.surface,
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
            .then(
                if (dottedStyle) {
                    Modifier
                        .dotPatternOverlay(
                            color = nebulaColors.textPrimary,
                            spacing = 9.dp,
                            radius = 0.7.dp,
                            alpha = if (selected) 0.14f else 0.08f
                        )
                        .dottedOutline(
                            color = if (selected) nebulaColors.accent else borderColor,
                            cornerRadius = 8.dp,
                            thickness = if (selected) 1.4.dp else 1.dp,
                            alpha = if (selected) 0.98f else 0.68f
                        )
                } else {
                    Modifier.border(if (selected) 1.6.dp else 1.dp, borderColor, shape)
                }
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(if (dottedStyle) RoundedCornerShape(6.dp) else CircleShape)
                .background(
                    if (selected) nebulaColors.accent.copy(alpha = 0.22f)
                    else nebulaColors.textSecondary.copy(alpha = 0.10f)
                )
                .then(
                    if (dottedStyle && selected) {
                        Modifier.dottedOutline(
                            color = nebulaColors.accent,
                            cornerRadius = 6.dp,
                            alpha = 0.92f
                        )
                    } else Modifier
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val haptic = LocalHapticFeedback.current
    val fill = windowsControlFill(nebulaColors)
    val border = windowsBorder(nebulaColors, 0.11f)
    val controlShape = RoundedCornerShape(if (dottedStyle) 8.dp else 14.dp)
    Row(
        modifier = modifier
            .clip(controlShape)
            .background(fill)
            .then(
                if (dottedStyle) {
                    Modifier
                        .dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.65.dp, alpha = 0.10f)
                        .dottedOutline(nebulaColors.accent, cornerRadius = 8.dp, alpha = 0.82f)
                } else {
                    Modifier.border(1.dp, border, controlShape)
                }
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(if (dottedStyle) 5.dp else 11.dp))
                    .background(
                        when {
                            selected && dottedStyle -> nebulaColors.accent.copy(alpha = 0.18f)
                            selected -> nebulaColors.accent
                            else -> Color.Transparent
                        }
                    )
                    .then(
                        if (selected && dottedStyle) {
                            Modifier
                                .dotPatternOverlay(nebulaColors.textPrimary, spacing = 7.dp, radius = 0.52.dp, alpha = 0.16f)
                                .dottedOutline(nebulaColors.accent, cornerRadius = 5.dp, alpha = 0.98f)
                        } else Modifier
                    )
                    .clickable(
                        enabled = !selected,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        haptic.tick()
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected && !dottedStyle) Color.White else if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary,
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
    val colors = LocalNebulaColors.current
    Surface(onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).heightIn(min = 50.dp).semantics {
            this.selected = selected; role = Role.RadioButton
        },
        color = if (selected) colors.accent.copy(alpha = 0.13f) else colors.panelFill,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.panelBorder),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall,
                    maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = colors.accent)
        }
    }
}

/** Keep one card per subscription while showing the active native variant on Home. */
internal fun homeSubscriptionProfiles(
    ordinary: List<SubscriptionProfile>, selected: SubscriptionProfile?
): List<SubscriptionProfile> {
    if (selected?.configType != "mihomo") return ordinary
    val result = ordinary.map { if (it.url == selected.mihomoParentUrl) selected else it }
    return if (result.any { it.url == selected.url }) result else listOf(selected) + result
}

@Composable
private fun PingUrlPresetChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 38.dp).semantics {
        this.selected = selected; role = Role.RadioButton
    }, color = if (selected) colors.accent.copy(alpha = 0.15f) else colors.panelFill,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.panelBorder),
        shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) colors.accent else colors.textPrimary,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun PingDisplayOption(display: PingDisplay, selected: Boolean, onClick: () -> Unit,
                              modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    val title = when (display) {
        PingDisplay.NUMERIC -> t("Числа", "Numbers")
        PingDisplay.BARS -> t("Полоски", "Bars")
        PingDisplay.BOTH -> t("Числа и полоски", "Numbers and bars")
        PingDisplay.DOTS -> t("Точки", "Dots")
    }
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics {
        this.selected = selected; role = Role.RadioButton
    }, color = if (selected) colors.accent.copy(alpha = 0.13f) else colors.panelFill,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.panelBorder),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.weight(1f), color = colors.textPrimary, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            PingValueContent(87, display.id, colors.accent)
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
                checkedThumbColor = if (nebulaColors.background.luminance() > 0.5f) Color.White else nebulaColors.background,
                checkedTrackColor = nebulaColors.accent,
                uncheckedThumbColor = if (nebulaColors.background.luminance() > 0.5f) Color.White else nebulaColors.textSecondary,
                uncheckedTrackColor = nebulaColors.textSecondary.copy(alpha = if (nebulaColors.background.luminance() > 0.5f) 0.48f else 0.2f),
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
    onBack: () -> Unit,
    onAppIconClick: () -> Unit
) {
    NimboSubPageScaffold(title = t("Тема", "Theme"), onBack = onBack) {
        ThemeSettingsSection(
            preferencesManager = preferencesManager,
            onAppIconClick = onAppIconClick
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ThemeSettingsSection(
    preferencesManager: PreferencesManager,
    onAppIconClick: () -> Unit
) {
    UniversalAppearanceContent(preferencesManager, onAppIconClick)
}

@Composable
private fun ColumnScope.AppearanceSectionHeader(title: String, subtitle: String) {
    val nebulaColors = LocalNebulaColors.current
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    Column(modifier = Modifier.padding(start = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Засечка ставится только в Manga: в остальных стилях заголовок
            // держится сам, а красная полоска смотрелась бы наклейкой.
            if (mangaStyle) MangaSectionSlash()
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
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
private fun AppIconAppearanceCard(
    previewRes: Int,
    title: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    // Внешняя обрезка тоже должна следовать стилю: с сырой формой карточка
    // оставалась скруглённой даже там, где панель уже стала прямоугольной.
    val shape = scaleRoundedCornerShape(RoundedCornerShape(20.dp), LocalGlobalCornerRadius.current)

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.tick()
                onClick()
            },
        shape = shape,
        borderColor = nebulaColors.accent.copy(alpha = 0.24f),
        accentFill = !mangaStyle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconFrameShape = nimboControlShape(19.dp, 2.dp)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(iconFrameShape)
                    .background(nimboControlContainer(nebulaColors.background.copy(alpha = 0.62f)))
                    .border(
                        width = nimboControlBorderWidth(),
                        color = nimboControlBorderColor(nebulaColors.accent.copy(alpha = 0.30f)),
                        shape = iconFrameShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIconResourceImage(
                    resId = previewRes,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Иконка приложения", "App icon"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = title,
                    color = nebulaColors.accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = t(
                        "Готовые варианты, форма, цвета и своя картинка",
                        "Presets, shape, colors and your own image"
                    ),
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = nebulaColors.textSecondary,
                modifier = Modifier.size(26.dp)
            )
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
    val isLiquidPreview = kind == InterfacePreviewKind.IosLiquidGlass
    val isDottedPreview = kind == InterfacePreviewKind.Dotted
    val isSignalPreview = kind == InterfacePreviewKind.Signal
    val isMangaPreview = kind == InterfacePreviewKind.Manga
    val activeDottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val activeMangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    // Форму задаёт включённый стиль: карточка — часть интерфейса, а не часть
    // картинки внутри. Свои углы Manga сохраняет и как превью — так её видно
    // среди остальных.
    val shape = RoundedCornerShape(
        when {
            activeMangaStyle || isMangaPreview -> 3.dp
            activeDottedStyle -> 8.dp
            else -> 18.dp
        }
    )
    val cardFill by animateColorAsState(
        targetValue = if (selected) nebulaColors.accent.copy(alpha = 0.13f) else nebulaColors.surface,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "interface_preview_card_fill"
    )
    val cardBorder by animateColorAsState(
        targetValue = when {
            selected -> nebulaColors.accent.copy(alpha = 0.74f)
            // Чернильный контур в Manga держит карточку сам, без заливки.
            activeMangaStyle -> nebulaColors.panelBorder
            else -> windowsBorder(nebulaColors)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "interface_preview_card_border"
    )
    val previewBase = if (isMangaPreview) {
        // Бумага под чернилами: тёмная страница ночью, кремовая днём.
        if (nebulaColors.isLight) Color(0xFFF7F3E8) else Color(0xFF0E0E12)
    } else if (isDottedPreview) {
        if (nebulaColors.isLight) Color(0xFFF2F4F6) else Color(0xFF101114)
    } else if (isSignalPreview) {
        // Signal: спокойная чернильная подложка, на которой видны тонкие границы.
        if (nebulaColors.isLight) Color(0xFFF6F5FB) else Color(0xFF0B0F16)
    } else if (isMaterialPreview) {
        // A quiet neutral tonal base lets dynamic accents read as Material You rather
        // than making the tiny preview look like another glass gradient.
        if (nebulaColors.isLight) Color(0xFFFFF8FC) else Color(0xFF17131C)
    } else {
        if (nebulaColors.isLight) Color(0xFFEAF4FF) else Color(0xFF0A1024)
    }
    val panel = if (isMangaPreview) {
        if (nebulaColors.isLight) Color(0xFF12120F) else Color(0xFFF2ECDD)
    } else if (isDottedPreview) {
        nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.12f else 0.18f)
            .compositeOver(previewBase)
    } else if (isSignalPreview) {
        Color.White.copy(alpha = if (nebulaColors.isLight) 0.86f else 0.05f)
            .compositeOver(previewBase)
    } else if (isMaterialPreview) {
        nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.14f else 0.20f)
            .compositeOver(previewBase)
    } else {
        Color.White.copy(alpha = if (nebulaColors.isLight) 0.52f else 0.16f)
    }
    val soft = if (isMangaPreview) {
        nebulaColors.accent
    } else if (isDottedPreview) {
        nebulaColors.accent.copy(alpha = 0.28f).compositeOver(previewBase)
    } else if (isSignalPreview) {
        nebulaColors.accent.copy(alpha = 0.42f).compositeOver(previewBase)
    } else if (isMaterialPreview) {
        nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.24f else 0.32f)
            .compositeOver(previewBase)
    } else {
        nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.32f else 0.38f)
    }

    // A miniature of the actual app home screen rendered inside a phone shell, so the
    // card previews how Nimbo Glass vs Material You re-skin the live UI (frosted/outlined
    // surfaces + ring connect button vs solid tonal surfaces + filled button).
    val bezelColor = if (nebulaColors.isLight) Color(0xFFCBC6D6) else Color(0xFF050609)
    val screenShape = RoundedCornerShape(14.dp)
    val statusTint = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.44f)
    val mutedDot = if (nebulaColors.isLight) Color.Black.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.32f)
    val navFill = when {
        isMangaPreview -> previewBase
        isDottedPreview -> previewBase.copy(alpha = 0.96f)
        isSignalPreview -> Color.White.copy(alpha = if (nebulaColors.isLight) 0.9f else 0.04f)
            .compositeOver(previewBase)
        isMaterialPreview -> nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.26f else 0.34f)
            .compositeOver(previewBase)
        else -> Color.White.copy(alpha = if (nebulaColors.isLight) 0.46f else 0.14f)
    }
    val navBorder = if (isMangaPreview) {
        if (nebulaColors.isLight) Color(0xFF12120F) else Color(0xFFF2ECDD)
    } else if (isDottedPreview) {
        nebulaColors.accent.copy(alpha = 0.42f)
    } else if (isSignalPreview) {
        if (nebulaColors.isLight) Color(0xFFDCDAE4) else Color.White.copy(alpha = 0.12f)
    } else Color.White.copy(
        alpha = if (isLiquidPreview) {
            if (nebulaColors.isLight) 0.82f else 0.38f
        } else {
            if (nebulaColors.isLight) 0.50f else 0.16f
        }
    )
    val chipFill = panel.copy(alpha = if (isMaterialPreview) 1f else panel.alpha)
    val screenBrush = if (isMangaPreview) {
        // Ровная бумага: любой градиент здесь и превращал превью Manga в
        // копию стеклянного.
        Brush.verticalGradient(listOf(previewBase, previewBase))
    } else if (isSignalPreview) {
        // Приборная панель: спокойная чернильная подложка с еле заметным
        // уходом в тень книзу.
        Brush.verticalGradient(
            listOf(previewBase, previewBase, soft.copy(alpha = 0.10f).compositeOver(previewBase))
        )
    } else if (isDottedPreview) {
        Brush.verticalGradient(listOf(previewBase, soft.copy(alpha = 0.72f), previewBase))
    } else if (isMaterialPreview) {
        Brush.linearGradient(
            listOf(
                soft.copy(alpha = 0.72f),
                previewBase,
                nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.08f else 0.12f)
                    .compositeOver(previewBase)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (nebulaColors.isLight) Color(0xFFB9E8FF) else Color(0xFF143D7A),
                nebulaColors.accent.copy(alpha = if (nebulaColors.isLight) 0.62f else 0.72f)
                    .compositeOver(previewBase),
                if (nebulaColors.isLight) Color(0xFFE9C9FF) else Color(0xFF441E67),
                previewBase
            )
        )
    }

    Column(
        modifier = modifier
            .height(196.dp)
            .clip(shape)
            .background(cardFill)
            .then(
                if (activeDottedStyle) {
                    Modifier
                        .dotPatternOverlay(
                            color = nebulaColors.textPrimary,
                            spacing = 9.dp,
                            radius = 0.7.dp,
                            alpha = if (selected) 0.14f else 0.07f
                        )
                        .dottedOutline(
                            color = if (selected) nebulaColors.accent else cardBorder,
                            cornerRadius = 8.dp,
                            thickness = if (selected) 1.4.dp else 1.dp,
                            alpha = if (selected) 0.98f else 0.66f
                        )
                } else if (activeMangaStyle) {
                    Modifier.border(if (selected) 2.5.dp else 1.5.dp, cardBorder, shape)
                } else {
                    Modifier.border(1.dp, cardBorder, shape)
                }
            )
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
                    .background(screenBrush)
            ) {
                if (isDottedPreview) {
                    Canvas(Modifier.matchParentSize()) {
                        val cols = 12
                        val rows = 7
                        val dx = size.width / cols
                        val dy = size.height / rows
                        for (row in 0 until rows) for (col in 0 until cols) {
                            drawCircle(
                                color = nebulaColors.accent.copy(alpha = if ((col + row) % 5 == 0) 0.46f else 0.18f),
                                radius = 1.25.dp.toPx(),
                                center = Offset(dx * (col + 0.5f), dy * (row + 0.5f))
                            )
                        }
                    }
                }
                if (isMaterialPreview) {
                    Canvas(Modifier.matchParentSize()) {
                        // Two slow, restrained tonal fields make the compact preview feel
                        // like Material You. The weaker alpha deliberately avoids the noisy
                        // multi-colour "neon" look the previous preview had.
                        drawCircle(
                            color = nebulaColors.accent.copy(alpha = 0.10f),
                            radius = size.width * 0.34f,
                            center = Offset(size.width * 0.08f, size.height * 0.02f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = if (nebulaColors.isLight) 0.20f else 0.05f),
                            radius = size.width * 0.30f,
                            center = Offset(size.width * 0.96f, size.height * 0.92f)
                        )
                    }
                }
                if (isLiquidPreview) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.24f),
                                        Color.Transparent
                                    ),
                                    center = Offset(18f, 4f),
                                    radius = 150f
                                )
                            )
                    )
                }
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
                                .clip(if (isMaterialPreview || isLiquidPreview) CircleShape else RoundedCornerShape(5.dp))
                                .background(panel)
                                .then(
                                    if (isLiquidPreview) {
                                        Modifier.border(1.dp, navBorder, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
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
                                        .width(52.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(nebulaColors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White.copy(alpha = 0.94f))
                                    )
                                }
                            } else if (isMangaPreview) {
                                // Прямоугольная кнопка с контуром и смещённой
                                // подложкой — та же геометрия, что в приложении.
                                Box(modifier = Modifier.size(48.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(start = 4.dp, top = 4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(panel.copy(alpha = 0.55f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(end = 4.dp, bottom = 4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(nebulaColors.accent)
                                            .border(2.dp, panel, RoundedCornerShape(2.dp))
                                    )
                                }
                            } else if (isSignalPreview) {
                                // Signal: не кольцо, а строгая плашка с рамкой.
                                Box(
                                    modifier = Modifier
                                        .width(54.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(panel)
                                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(nebulaColors.accent)
                                    )
                                }
                            } else if (isDottedPreview) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(nebulaColors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(previewBase)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.34f),
                                                    nebulaColors.accent.copy(alpha = 0.18f),
                                                    Color.White.copy(alpha = 0.10f)
                                                )
                                            )
                                        )
                                        .border(1.dp, navBorder, CircleShape)
                                        .border(3.dp, nebulaColors.accent.copy(alpha = 0.62f), CircleShape),
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
                                            .clip(RoundedCornerShape(if (isMaterialPreview || isLiquidPreview) 7.dp else 4.dp))
                                            .background(chipFill)
                                            .then(
                                                if (isLiquidPreview) {
                                                    Modifier.border(
                                                        1.dp,
                                                        navBorder.copy(alpha = navBorder.alpha * 0.72f),
                                                        RoundedCornerShape(7.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Bottom navigation mirrors the selected visual language: a tonal
                    // Material You pill or a compact LED-like Dotted strip.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(if (isMaterialPreview || isLiquidPreview) 13.dp else 6.dp))
                            .background(navFill)
                            .then(
                                if (isDottedPreview) {
                                    Modifier
                                        .dotPatternOverlay(nebulaColors.textPrimary, spacing = 5.dp, radius = 0.45.dp, alpha = 0.16f)
                                        .dottedOutline(nebulaColors.accent, cornerRadius = 6.dp, alpha = 0.86f)
                                } else if (isMaterialPreview) Modifier
                                else Modifier.border(
                                    1.dp,
                                    navBorder,
                                    RoundedCornerShape(if (isLiquidPreview) 13.dp else 6.dp)
                                )
                            )
                            .padding(horizontal = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { i ->
                            if (isDottedPreview && i == 1) {
                                Box(
                                    Modifier
                                        .width(18.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(nebulaColors.accent)
                                        .dottedOutline(previewBase, cornerRadius = 2.dp, alpha = 0.90f)
                                )
                            } else if (isDottedPreview) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(mutedDot)
                                )
                            } else if (i == 1) {
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
                            .clip(
                                when {
                                    activeMangaStyle -> RoundedCornerShape(2.dp)
                                    activeDottedStyle -> RoundedCornerShape(5.dp)
                                    else -> CircleShape
                                }
                            )
                            .background(
                                if (activeDottedStyle) nebulaColors.accent.copy(alpha = 0.20f)
                                else nebulaColors.accent
                            )
                            .then(
                                if (activeDottedStyle) {
                                    Modifier.dottedOutline(
                                        color = nebulaColors.accent,
                                        cornerRadius = 5.dp,
                                        thickness = 1.2.dp,
                                        alpha = 1f
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = if (activeDottedStyle) nebulaColors.accent else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp else 18.dp)
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
            .selectableTileFrame(selected, shape, nebulaColors)
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

private data class BackgroundStylePreset(
    val id: Int,
    val titleRu: String,
    val titleEn: String,
    val icon: ImageVector
)

/** id — значение BackgroundStyleMode, менять нельзя: оно сохранено у пользователей. */
private const val BACKGROUND_STYLE_NONE_ID = 15

private fun backgroundStylePresets(): List<BackgroundStylePreset> = listOf(
    BackgroundStylePreset(0, "Круги", "Circles", Icons.Default.RadioButtonUnchecked),
    BackgroundStylePreset(1, "Кольца", "Rings", Icons.Default.RadioButtonChecked),
    BackgroundStylePreset(2, "Точки", "Dots", Icons.Default.Apps),
    BackgroundStylePreset(3, "Аврора", "Aurora", Icons.Default.BrightnessAuto),
    BackgroundStylePreset(4, "Сетка", "Grid", Icons.Default.GridView),
    BackgroundStylePreset(5, "Фигуры", "Shapes", Icons.Default.Category),
    BackgroundStylePreset(6, "Волны", "Waves", Icons.Default.Waves),
    BackgroundStylePreset(7, "Снег", "Snow", Icons.Default.AcUnit),
    BackgroundStylePreset(8, "Импульс", "Pulse", Icons.Default.Bolt),
    BackgroundStylePreset(9, "Звёзды", "Stars", Icons.Default.Star),
    BackgroundStylePreset(10, "Искры", "Embers", Icons.Default.LocalFireDepartment),
    BackgroundStylePreset(11, "Пузыри", "Bubbles", Icons.Default.BubbleChart),
    BackgroundStylePreset(12, "Неон", "Neon", Icons.AutoMirrored.Filled.ShowChart),
    BackgroundStylePreset(13, "Лучи", "Rays", Icons.Default.LightMode),
    BackgroundStylePreset(14, "Лепестки", "Petals", Icons.Default.Favorite),
    BackgroundStylePreset(16, "Дождь", "Rain", Icons.Default.Grain),
    BackgroundStylePreset(17, "Орбиты", "Orbits", Icons.Default.TrackChanges),
    BackgroundStylePreset(18, "Поток", "Signal flow", Icons.AutoMirrored.Filled.ShowChart),
    BackgroundStylePreset(BACKGROUND_STYLE_NONE_ID, "Нет", "None", Icons.Default.Block)
)

private data class BackgroundPalettePreset(
    val id: Int,
    val titleRu: String,
    val titleEn: String
)

private fun backgroundPalettePresets(): List<BackgroundPalettePreset> = listOf(
    BackgroundPalettePreset(0, "Как тема", "Theme"),
    BackgroundPalettePreset(1, "Аврора", "Aurora"),
    BackgroundPalettePreset(2, "Кибер", "Cyber"),
    BackgroundPalettePreset(3, "Космос", "Space"),
    BackgroundPalettePreset(4, "Огонь", "Fire"),
    BackgroundPalettePreset(5, "Лава", "Lava"),
    BackgroundPalettePreset(6, "Неон", "Neon"),
    BackgroundPalettePreset(7, "Север", "Nordic"),
    BackgroundPalettePreset(8, "Цветение", "Blossom"),
    BackgroundPalettePreset(9, "Океан", "Ocean"),
    BackgroundPalettePreset(10, "Закат", "Sunset"),
    BackgroundPalettePreset(11, "Лес", "Forest")
)

/**
 * Общая рамка выбранной плитки. Раньше выделение рисовалось волосяной линией
 * акцента поверх акцентной же заливки — на тёмном фоне рамка практически
 * пропадала. Теперь это заметное кольцо + фон, и переход анимирован.
 */
@Composable
private fun Modifier.selectableTileFrame(
    selected: Boolean,
    shape: RoundedCornerShape,
    nebulaColors: NebulaColors
): Modifier {
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    val effectiveShape = when {
        mangaStyle -> RoundedCornerShape(3.dp)
        dottedStyle -> RoundedCornerShape(8.dp)
        else -> shape
    }
    val fill by animateColorAsState(
        targetValue = when {
            mangaStyle -> if (selected) nebulaColors.accent.copy(alpha = 0.14f)
                .compositeOver(nebulaColors.panelFill.copy(alpha = 1f)) else nebulaColors.panelFill.copy(alpha = 1f)
            selected -> nebulaColors.accent.copy(
                alpha = when {
                    mangaStyle -> 0.14f
                    dottedStyle -> 0.12f
                    else -> 0.20f
                }
            )
            // Плитка в Manga — нарисованная панель, а не подсвеченная подложка.
            mangaStyle -> nebulaColors.panelFill
            else -> nebulaColors.surface
        },
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "tile_fill"
    )
    val border by animateColorAsState(
        targetValue = when {
            selected -> nebulaColors.accent
            mangaStyle -> nebulaColors.panelBorder
            else -> windowsBorder(nebulaColors)
        },
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "tile_border"
    )
    val width by animateDpAsState(
        targetValue = when {
            mangaStyle -> if (selected) 2.5.dp else 1.5.dp
            selected -> 2.dp
            else -> 1.dp
        },
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "tile_border_width"
    )
    return this
        .clip(effectiveShape)
        .background(fill)
        .then(
            if (dottedStyle) {
                Modifier
                    .dotPatternOverlay(
                        color = nebulaColors.textPrimary,
                        spacing = 9.dp,
                        radius = 0.68.dp,
                        alpha = if (selected) 0.14f else 0.08f
                    )
                    .dottedOutline(
                        color = if (selected) nebulaColors.accent else border,
                        cornerRadius = 8.dp,
                        thickness = if (selected) 1.4.dp else 1.dp,
                        alpha = if (selected) 0.98f else 0.64f
                    )
            } else {
                Modifier.border(width, border, effectiveShape)
            }
        )
}

@Composable
private fun BackgroundPresetTile(
    label: String,
    mode: BackgroundStyleMode,
    previewColors: List<Color>,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp else 16.dp)
    val previewShape = RoundedCornerShape(if (dottedStyle) 5.dp else 13.dp)
    val previewBase = lerp(
        nebulaColors.background,
        previewColors.firstOrNull() ?: nebulaColors.accent,
        if (nebulaColors.isLight) 0.10f else 0.26f
    )
    Column(
        modifier = modifier
            .height(106.dp)
            .selectableTileFrame(selected, shape, nebulaColors)
            .nimboClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(previewShape)
                .background(previewBase)
                .border(1.dp, Color.White.copy(alpha = 0.10f), previewShape)
        ) {
            if (mode == BackgroundStyleMode.NONE) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nebulaColors.textSecondary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp)
                )
            } else {
                // Тот же рендер, что и у настоящего фона — превью не врёт.
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawNimboBackgroundMotion(
                        mode = mode,
                        phase = 0.35f,
                        colors = previewColors,
                        isLight = nebulaColors.isLight,
                        intensity = 2.6f,
                        detail = 0.4f
                    )
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
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            color = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BackgroundColorTile(
    label: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp else 16.dp)
    val swatchShape = RoundedCornerShape(if (dottedStyle) 5.dp else 13.dp)
    Column(
        modifier = modifier
            .height(96.dp)
            .selectableTileFrame(selected, shape, nebulaColors)
            .nimboClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(swatchShape)
                .background(
                    Brush.linearGradient(
                        colors.take(3).ifEmpty { listOf(nebulaColors.accent, nebulaColors.accent) }
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), swatchShape)
        ) {
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
            color = if (selected) nebulaColors.accent else nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BackgroundRandomTile(
    label: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp else 16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(nebulaColors.surface)
            .then(
                if (dottedStyle) {
                    Modifier
                        .dotPatternOverlay(nebulaColors.textPrimary, spacing = 9.dp, radius = 0.68.dp, alpha = 0.08f)
                        .dottedOutline(nebulaColors.accent, cornerRadius = 8.dp, alpha = 0.68f)
                } else {
                    Modifier.border(1.dp, windowsBorder(nebulaColors), shape)
                }
            )
            .nimboClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.BrightnessAuto, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = nebulaColors.textPrimary, fontWeight = FontWeight.ExtraBold)
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
    val dottedStyle = LocalElementStyleMode.current == ElementStyleMode.NOTHING_DOTS
    val shape = RoundedCornerShape(if (dottedStyle) 8.dp else 16.dp)
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
            .then(
                if (dottedStyle) {
                    Modifier.dottedOutline(
                        color = if (selected) nebulaColors.accent else Color.White.copy(alpha = 0.28f),
                        cornerRadius = 8.dp,
                        thickness = if (selected) 1.4.dp else 1.dp,
                        alpha = if (selected) 1f else 0.72f
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        if (selected) nebulaColors.accent else Color.White.copy(alpha = 0.12f),
                        shape
                    )
                }
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
    subtitle: String? = null,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (nebulaColors.background.luminance() > 0.5f) Color.White else nebulaColors.background,
                checkedTrackColor = nebulaColors.accent,
                uncheckedThumbColor = if (nebulaColors.background.luminance() > 0.5f) Color.White else nebulaColors.textSecondary,
                uncheckedTrackColor = nebulaColors.textSecondary.copy(alpha = if (nebulaColors.background.luminance() > 0.5f) 0.48f else 0.2f),
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
internal fun CustomThemeColorDialog(
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
                            onValueChange = rememberHapticSliderValueChange(
                                value = currentHsv[0],
                                valueRange = 0f..360f
                            ) {
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
                            onValueChange = rememberHapticSliderValueChange(
                                value = currentHsv[1],
                                valueRange = 0f..1f
                            ) {
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
                            onValueChange = rememberHapticSliderValueChange(
                                value = currentHsv[2],
                                valueRange = 0f..1f
                            ) {
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
                            onValueChange = rememberHapticSliderValueChange(
                                value = currentAlpha,
                                valueRange = 0f..1f
                            ) {
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
private fun LanguageRow(title: String, subtitle: String, flag: String?, selected: Boolean, onClick: () -> Unit) {
    NimboPanel(Modifier.fillMaxWidth()) {
        com.danila.nimbo.ui.components.NimboChoiceRow(title, selected, onClick, subtitle, divider = false)
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
    val libXrayVersion = BuildConfig.LIBXRAY_VERSION
    val deviceName = remember { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim() }
    val systemName = remember { "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})" }

    var technicalDetails by rememberSaveable { mutableStateOf(false) }
    NimboPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).background(nebulaColors.controlFill, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(com.danila.nimbo.R.drawable.nimbo_cloud), null,
                        Modifier.size(38.dp), tint = nebulaColors.textPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("nimbo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = nebulaColors.textPrimary)
                    Text("v$version", style = MaterialTheme.typography.bodyMedium, color = nebulaColors.textSecondary)
                }
            }
            Text(t("Ваше соединение, без лишнего.", "Your connection, without distractions."),
                style = MaterialTheme.typography.bodyMedium, color = nebulaColors.textSecondary)
        }
    }
    Spacer(Modifier.height(16.dp))
    NimboPanel(Modifier.fillMaxWidth()) {
        Column {
            AboutLinkRow(t("Сайт Nimbo", "Nimbo website"), "https://nimboapp.pw", icon = Icons.Default.Public) { openUrl(context, it) }
            AboutLinkRow(t("Новости и обновления", "News and updates"), "https://t.me/nebulaguard_channel", icon = Icons.Default.Language) { openUrl(context, it) }
            AboutLinkRow(t("Исходный код", "Source code"), "https://github.com/BBGGVP5/nimbo", showDivider = false, icon = Icons.Default.Code) { openUrl(context, it) }
        }
    }
    Spacer(Modifier.height(16.dp))
    NimboPanel(Modifier.fillMaxWidth()) {
        Column {
            Surface(onClick = { technicalDetails = !technicalDetails }, color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(22.dp), tint = nebulaColors.textSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text(t("Сведения о сборке", "Build information"), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall, color = nebulaColors.textPrimary)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(if (technicalDetails) 180f else 0f), tint = nebulaColors.textSecondary)
                }
            }
            if (technicalDetails) {
                AboutValueRow(t("Библиотека Xray", "Xray library"), libXrayVersion)
                AboutValueRow(t("Разработчик", "Developer"), "Danila")
                AboutValueRow(t("Система", "System"), systemName)
                AboutValueRow(t("Устройство", "Device"), deviceName.ifBlank { "—" })
                AboutLinkRow("libXray", "https://github.com/XTLS/libXray") { openUrl(context, it) }
                AboutLinkRow("Xray-core", "https://github.com/XTLS/Xray-core", showDivider = false) { openUrl(context, it) }
            }
        }
    }
    SettingsScrollTail()
}

/** Shared scrollable gap after the last card, in addition to navigation insets. */
@Composable
private fun SettingsScrollTail() {
    Spacer(Modifier.height(16.dp))
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
                fontWeight = FontWeight.Medium
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
                .background(nebulaColors.divider)
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
                    .background(nebulaColors.divider)
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
    val subscriptionUserAgent = remember { preferencesManager.subscriptionUserAgent }
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
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            borderColor = Color.White.copy(alpha = 0.12f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = subscriptionUserAgent,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = t(
                        "Единый User-Agent для всех запросов подписки.",
                        "One User-Agent for every subscription request."
                    ),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun NimboDisclaimerDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    NimboTermsDialog(onDismiss, onExit)
}

@Composable
internal fun NimboBackButton(onBack: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val shape = nimboControlShape(16.dp, 2.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(nimboControlContainer(nebulaColors.accent.copy(alpha = 0.10f)))
            .border(
                nimboControlBorderWidth(),
                nimboControlBorderColor(nebulaColors.accent.copy(alpha = 0.22f)),
                shape
            )
            .clickable {
                haptic.tick()
                onBack()
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = t("Назад", "Back"),
            tint = nebulaColors.textPrimary,
            modifier = Modifier.size(27.dp)
        )
    }
}

@Composable
internal fun NimboSubPageScaffold(title: String, subtitle: String? = null, onBack: () -> Unit,
    showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState())) {
        NimboSubPageHeader(title, subtitle, onBack, showBack = showBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = LocalFloatingNavHeight.current + 16.dp), content = content)
    }
}

@Composable
internal fun NimboSubPageHeader(title: String, subtitle: String? = null, onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null, showBack: Boolean = true, horizontalPadding: Dp = 16.dp) {
    val colors = LocalNebulaColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onBack, Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад", "Back"), Modifier.size(22.dp), tint = colors.textPrimary)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(title, Modifier.weight(1f).semantics { heading() }, color = colors.textPrimary,
                style = if (showBack) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
            trailing?.invoke()
        }
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(4.dp))
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
        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
    ) {
        if (LocalElementStyleMode.current == ElementStyleMode.MANGA) {
            MangaSectionSlash()
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier
                    .padding(end = 7.dp)
                    .size(19.dp)
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

    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = t("Переименовать сервер", "Rename server"),
        description = t(
            "Имя сохранится локально и не изменит подписку.",
            "The name is stored locally and will not change the subscription."
        ),
        confirmButtonText = t("Сохранить", "Save"),
        cancelButtonText = t("Отмена", "Cancel"),
        onConfirm = { onSave(value.trim()) },
        headerIcon = Icons.Default.Edit
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Edit, null, tint = nebulaColors.accent)
            },
            modifier = Modifier.fillMaxWidth(),
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
    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = text,
        confirmButtonText = confirmText,
        cancelButtonText = t("Отмена", "Cancel"),
        onConfirm = onConfirm,
        confirmButtonColor = Color(0xFFFF6B6B),
        headerIcon = Icons.Default.Delete,
        headerIconTint = Color(0xFFFF6B6B)
    )
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
    val cleaned = cleanServerName(server.name)
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
        protocol.contains("naive") -> "NaiveProxy"
        protocol.contains("tuic") -> "TUIC"
        protocol.contains("awg") || protocol.contains("amnezia") -> "AWG"
        protocol.contains("wireguard") || protocol == "wg" -> "WireGuard"
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
    if (expireTime <= 0L) return "∞"
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
    if (profile.expireTime <= 0L) return "∞"
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
private fun DialogSectionLabel(
    icon: ImageVector,
    text: String
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = nebulaColors.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun DialogIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    val shape = nimboControlShape(14.dp, 2.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = shape,
        color = nimboControlContainer(containerColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(nimboControlBorderWidth(), nimboControlBorderColor(borderColor))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
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

    val saveChanges = {
        val finalName = customName.trim()
        if (finalName.isNotBlank() && finalName != profile.displayName) {
            mainViewModel.renameProfile(profile.url, finalName)
        }

        if (isPinned) {
            preferencesManager.pinProfile(profile.url)
        } else {
            preferencesManager.unpinProfile(profile.url)
        }

        preferencesManager.setSubscriptionUpdateInterval(profile.url, selectedIntervalHours)
        SubscriptionUpdateScheduler.reschedule(context)
        onDismiss()
    }

    NebulaMorphicDialog(onDismissRequest = onDismiss, title = t("Настройки профиля", "Profile settings"),
        description = profile.displayName, confirmButtonText = t("Сохранить", "Save"),
        cancelButtonText = t("Отмена", "Cancel"), onConfirm = saveChanges) {
        NetworkSettingsTextField(customName, { customName = it }, t("Название", "Name"), Modifier.fillMaxWidth())
        NetworkSettingsToggleRow(t("Показывать на главной", "Show on main"),
            t("Закрепить этот профиль в топе", "Pin this profile to the top"), isPinned,
            onCheckedChange = { checked ->
                val pinnedProfiles = preferencesManager.getPinnedProfileUrls()
                if (checked && pinnedProfiles.size >= 3) {
                    Toast.makeText(context, loc("Можно закрепить максимум 3 профиля", "Maximum of 3 profiles can be pinned"), Toast.LENGTH_SHORT).show()
                } else {
                    isPinned = checked
                }
            }, showDivider = false)
        Text(t("Интервал обновления", "Update interval"), color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleSmall)
        val intervals = listOf(
            Pair(t("1 час", "1 hour"), 1), Pair(t("2 часа", "2 hours"), 2),
            Pair(t("6 часов", "6 hours"), 6), Pair(t("12 часов", "12 hours"), 12),
            Pair(t("24 часа", "24 hours"), 24), Pair(t("По умолчанию", "Default"), null))
        NimboToolActions(minCellDp = 72f) {
            intervals.forEach { (label, hours) ->
                com.danila.nimbo.ui.components.NetworkSettingsCompactChoice(label, selectedIntervalHours == hours,
                    { selectedIntervalHours = hours })
            }
        }
        Text(t("URL подписки", "Subscription URL"), style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(profile.url, color = nebulaColors.textSecondary, style = MaterialTheme.typography.bodySmall) }
        NimboAction(if (copiedUrl) Icons.Default.Check else Icons.Default.ContentCopy, t("Копировать ссылку", "Copy link"), {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Subscription URL", profile.url))
            copiedUrl = true
            Toast.makeText(context, loc("Ссылка скопирована", "Link copied"), Toast.LENGTH_SHORT).show()
        }, Modifier.fillMaxWidth())
        NimboToolActions {
            profile.supportUrl?.takeIf { it.isNotBlank() }?.let { supportUrl ->
                NimboAction(Icons.Default.SupportAgent, t("Поддержка", "Support"), { onDismiss(); openUrl(context, supportUrl) }, Modifier.weight(1f))
            }
            profile.websiteUrl?.takeIf { it.isNotBlank() }?.let { siteUrl ->
                NimboAction(Icons.Default.Public, t("Сайт", "Site"), { onDismiss(); openUrl(context, siteUrl) }, Modifier.weight(1f))
            }
        }
        profile.announce?.trim()?.takeIf { it.isNotBlank() }?.let { announce ->
            NimboToolSection(t("Объявление провайдера", "Provider announcement"), announce) {}
        }
        NimboAction(Icons.Default.Delete, t("Удалить профиль", "Delete profile"), {
            mainViewModel.removeSubscription(profile.url)
            onDismiss()
        }, Modifier.fillMaxWidth())
    }
}

/**
 * Модули маршрутизации: наборы правил, написанные пользователем.
 *
 * Формат тот же, что в Shadowrocket и Surge, — люди переносят сюда готовые
 * наборы, и требовать переписать их в свой синтаксис значит выбросить чужую
 * работу. Правила включённых модулей применяются раньше правил профиля.
 */
@Composable
private fun RoutingModulesScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var modules by remember { mutableStateOf(preferencesManager.routingModules()) }
    var editing by remember { mutableStateOf<NimboModule?>(null) }
    // Название берётся заранее: t() читает состояние языка и внутри обработчика
    // нажатия вызвано быть не может.
    val newModuleName = t("Новый модуль", "New module")

    fun persist(next: List<NimboModule>) {
        modules = next
        preferencesManager.saveRoutingModules(next)
    }

    val current = editing
    if (current != null) {
        RoutingModuleEditor(
            module = current,
            onCancel = { editing = null },
            onSave = { saved ->
                val next = if (modules.any { it.id == saved.id }) {
                    modules.map { if (it.id == saved.id) saved else it }
                } else {
                    modules + saved
                }
                persist(next)
                editing = null
            }
        )
        return
    }

    NimboSubPageScaffold(
        title = t("Модули", "Modules"),
        onBack = onBack
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("Свои наборы правил", "Your rule sets"),
                color = LocalNebulaColors.current.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            NimboAction(
                icon = Icons.Default.Add,
                label = t("Добавить", "Add"),
                onClick = {
                    editing = NimboModule(
                        id = java.util.UUID.randomUUID().toString(),
                        name = newModuleName,
                        enabled = true,
                        text = NEW_MODULE_TEMPLATE
                    )
                }
            )
        }
        val totalRules = remember(modules) {
            modules.sumOf { NimboModuleParser.parse(it.text).rules.size }
        }
        Text(
            text = t(
                "Свои правила поверх профиля: домены и адреса, которые всегда идут напрямую, через VPN или в блок.",
                "Your own rules on top of the profile: domains and addresses that always go direct, through the VPN or to block."
            ),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (modules.isEmpty()) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = t("Модулей пока нет", "No modules yet"),
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = t(
                            "Нажмите «+» и вставьте набор правил вида DOMAIN-SUFFIX,example.com,DIRECT — подойдёт готовый список из другого приложения.",
                            "Tap + and paste rules like DOMAIN-SUFFIX,example.com,DIRECT — a ready list from another app fits as is."
                        ),
                        color = nebulaColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Text(
                text = t(
                    "${modules.size} модулей · $totalRules правил",
                    "${modules.size} modules · $totalRules rules"
                ),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )
            modules.forEach { module ->
                RoutingModuleCard(
                    module = module,
                    onToggle = { enabled ->
                        persist(modules.map { if (it.id == module.id) it.copy(enabled = enabled) else it })
                    },
                    onEdit = { editing = module },
                    onDelete = { persist(modules.filterNot { it.id == module.id }) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun RoutingModuleCard(module: NimboModule, onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit) {
    val parsed = remember(module.text) { NimboModuleParser.parse(module.text) }
    NimboToolSection(module.name,
        t("${parsed.rules.size} правил", "${parsed.rules.size} rules") +
            if (parsed.skippedLines > 0) t(" · ${parsed.skippedLines} строк не понято", " · ${parsed.skippedLines} lines skipped") else "") {
        NetworkSettingsToggleRow(t("Применять модуль", "Apply module"), null, module.enabled, onToggle, showDivider = false)
        NimboToolActions {
            NimboAction(Icons.Default.Edit, t("Изменить", "Edit"), onEdit, Modifier.weight(1f))
            NimboAction(Icons.Default.Delete, t("Удалить", "Delete"), onDelete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoutingModuleEditor(
    module: NimboModule,
    onCancel: () -> Unit,
    onSave: (NimboModule) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var name by remember(module.id) { mutableStateOf(module.name) }
    var text by remember(module.id) { mutableStateOf(module.text) }
    val parsed = remember(text) { NimboModuleParser.parse(text) }
    val resolvedName = name.trim()
        .ifBlank { parsed.name?.trim().orEmpty() }
        .ifBlank { t("Без названия", "Untitled") }
    val copiedMessage = t("Скопировано в буфер обмена", "Copied to clipboard")

    NimboSubPageScaffold(
        title = resolvedName,
        onBack = onCancel
    ) {
        // Имя набора — своё, а не строка из текста правил: списки из других
        // приложений часто приходят вообще без имени.
        Text(
            text = t("НАЗВАНИЕ", "NAME"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = loc("Название модуля", "Module name") }.padding(14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                cursorBrush = SolidColor(nebulaColors.accent),
                decorationBox = { inner ->
                    if (name.isBlank()) {
                        Text(
                            text = t("Например, «Ozon напрямую»", "For example, «Ozon direct»"),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    inner()
                }
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(t("Правила модуля", "Module rules"), color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        NimboToolActions {
            NimboAction(
                icon = Icons.Default.ContentCopy, label = t("Копировать", "Copy"), modifier = Modifier.weight(1f),
                onClick = {
                    focusManager.clearFocus()
                    copyTextToClipboard(context, text)
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }
            )
            NimboAction(
                icon = Icons.Default.Share, label = t("Поделиться", "Share"), modifier = Modifier.weight(1f),
                onClick = {
                    focusManager.clearFocus()
                    shareModuleText(context, resolvedName, text)
                }
            )
            NimboAction(
                icon = Icons.Default.Check, label = t("Сохранить", "Save"), modifier = Modifier.weight(1f),
                onClick = {
                    focusManager.clearFocus()
                    onSave(module.copy(name = resolvedName, text = text))
                }
            )
        }
        Text(
            text = t(
                "${parsed.rules.size} правил разобрано",
                "${parsed.rules.size} rules parsed"
            ) + if (parsed.skippedLines > 0) {
                " · " + t("${parsed.skippedLines} строк не понято", "${parsed.skippedLines} lines skipped")
            } else "",
            color = if (parsed.skippedLines > 0) nebulaColors.accent else nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
        )
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            // Моноширинный шрифт: правила читаются столбцами, и пропорциональный
            // превращает их в кашу.
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).semantics { contentDescription = loc("Правила модуля", "Module rules") }.padding(14.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = nebulaColors.textPrimary,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(nebulaColors.accent)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = t(
                "Поддерживаются DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP и GEOSITE с политиками DIRECT, PROXY и REJECT. Секция [General] пропускается: её настройки относятся к другому движку.",
                "Supported: DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR, GEOIP and GEOSITE with DIRECT, PROXY and REJECT. The [General] section is skipped: it configures a different engine."
            ),
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Кладёт текст набора в буфер обмена. */
private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Nimbo", text))
}

/**
 * Отдаёт набор файлом через системное окно обмена.
 *
 * Текст уходит вместе с файлом: часть приложений принимает только текст, и
 * без него отправка в мессенджер оказывалась пустой.
 */
private fun shareModuleText(context: Context, name: String, text: String) {
    val safeName = name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .trim()
        .ifBlank { "module" }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TITLE, "$safeName.conf")
        putExtra(android.content.Intent.EXTRA_SUBJECT, safeName)
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(intent, safeName)
        )
    }
}

/** Заготовка нового модуля: сразу видно формат, не нужно искать пример. */
private val NEW_MODULE_TEMPLATE = """
#!name=Мой модуль
#!desc=Свои правила маршрутизации

[Rule]
DOMAIN-SUFFIX,example.com,DIRECT
DOMAIN-KEYWORD,analytics,REJECT
GEOIP,ru,DIRECT
""".trimIndent()


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NimboSubscriptionPullRefresh(refreshing: Boolean, enabled: Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(), content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    var lastRequest by remember { mutableStateOf(0L) }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing, state = pullState, modifier = modifier,
        onRefresh = {
            val now = android.os.SystemClock.elapsedRealtime()
            if (enabled && !refreshing && now - lastRequest > 1500L) {
                lastRequest = now
                onRefresh()
            }
        }, content = content)
}
