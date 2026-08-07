package com.danila.nimbo.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.sync.CrossSyncPairingEngine
import com.danila.nimbo.sync.CrossSyncPairingStage
import com.danila.nimbo.sync.AndroidCrossSyncBundleMapper
import com.danila.nimbo.sync.CrossSyncBundle
import com.danila.nimbo.sync.CrossSyncClient
import com.danila.nimbo.sync.CrossSyncProtocol
import com.danila.nimbo.sync.CrossSyncQr
import com.danila.nimbo.sync.PairedDesktopDevice
import com.danila.nimbo.sync.PairedSyncEngine
import com.danila.nimbo.sync.SyncCategories
import com.danila.nimbo.sync.SyncDirection
import com.danila.nimbo.sync.SyncDeviceInfo
import com.danila.nimbo.sync.SyncInventory
import com.danila.nimbo.sync.SyncWireRequest
import com.danila.nimbo.sync.SyncWireResponse
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.QrScannerScreen
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CrossPlatformSyncScreen(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val profiles by mainViewModel.profilesState.collectAsState()
    val motionEnabled = LocalBackgroundAnimationEnabled.current
    CrossSyncPairingEngine.syncPairedDevice(preferencesManager)

    val stage = CrossSyncPairingEngine.stage
    val qr = CrossSyncPairingEngine.qr
    val response = CrossSyncPairingEngine.response
    val pendingDesktopBundle = CrossSyncPairingEngine.pendingDesktopBundle
    val error = CrossSyncPairingEngine.error
    val offline = CrossSyncPairingEngine.offline
    val addedSubscriptions = CrossSyncPairingEngine.addedSubscriptions
    val nowMs = CrossSyncPairingEngine.nowMs
    val sessionLifetimeMs = CrossSyncPairingEngine.sessionLifetimeMs

    var categories by remember {
        mutableStateOf(
            SyncCategories(
                subscriptions = preferencesManager.crossSyncSubscriptions,
                appearance = preferencesManager.crossSyncAppearance,
                connection = preferencesManager.crossSyncConnection,
                automation = preferencesManager.crossSyncAutomation
            )
        )
    }
    var showScanner by remember { mutableStateOf(false) }
    var pairedSyncing by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<PairedDesktopDevice?>(null) }
    var deviceToDelete by remember { mutableStateOf<PairedDesktopDevice?>(null) }
    val scope = rememberCoroutineScope()

    fun saveCategories(next: SyncCategories) {
        categories = next
        preferencesManager.crossSyncSubscriptions = next.subscriptions
        preferencesManager.crossSyncAppearance = next.appearance
        preferencesManager.crossSyncConnection = next.connection
        preferencesManager.crossSyncAutomation = next.automation
    }

    fun resetSession(openScanner: Boolean) {
        CrossSyncPairingEngine.reset()
        showScanner = openScanner
    }

    fun handleScanned(raw: String) {
        showScanner = false
        CrossSyncPairingEngine.handleScanned(raw, preferencesManager, profiles, mainViewModel)
    }

    fun transfer(direction: SyncDirection) {
        CrossSyncPairingEngine.commit(direction, preferencesManager, profiles, mainViewModel)
    }

    fun applyDesktopData() {
        CrossSyncPairingEngine.applyDesktopData(preferencesManager, profiles, mainViewModel, context)
    }

    fun syncPairedNow(device: PairedDesktopDevice) {
        CrossSyncPairingEngine.error = null
        pairedSyncing = true
        scope.launch {
            try {
                val result = PairedSyncEngine.syncOnce(preferencesManager, device) { url, name ->
                    mainViewModel.addSubscription(url)
                    name?.takeIf { it.isNotBlank() }?.let { mainViewModel.renameProfile(url, it) }
                }
                CrossSyncPairingEngine.pairedDevices = preferencesManager.crossSyncPairedDevices
                mainViewModel.showTopNotification(
                    when {
                        result.unpaired -> "Компьютер удалён из синхронизации"
                        result.addedSubscriptions > 0 ->
                            "Синхронизировано: добавлено подписок ${result.addedSubscriptions}"
                        else -> "Синхронизировано"
                    }
                )
            } catch (cause: Throwable) {
                CrossSyncPairingEngine.failSync(cause)
            } finally {
                pairedSyncing = false
            }
        }
    }

    fun deletePairedDevice(device: PairedDesktopDevice) {
        scope.launch {
            PairedSyncEngine.unpair(preferencesManager, device)
            CrossSyncPairingEngine.pairedDevices = preferencesManager.crossSyncPairedDevices
            selectedDevice = null
            mainViewModel.showTopNotification("Компьютер удалён из синхронизации")
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Logger.i("QrScanner", "source=desktop_sync event=camera_permission_result granted=$granted")
        if (granted) resetSession(openScanner = true)
        else CrossSyncPairingEngine.error = "Для сканирования QR требуется разрешение камеры"
    }

    val continueWithCamera: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Logger.i("QrScanner", "source=desktop_sync event=continue_with_camera granted=$granted")
        if (granted) {
            resetSession(openScanner = true)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Logger.i("QrScanner", "source=desktop_sync event=local_network_permission_result granted=$granted")
        if (granted) continueWithCamera()
        else CrossSyncPairingEngine.error = "Разрешите доступ к локальной сети, чтобы телефон мог напрямую подключиться к Nimbo Desktop"
    }

    fun openScanner() {
        val localNetworkGranted = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Logger.i(
            "QrScanner",
            "source=desktop_sync event=open_requested sdk=${Build.VERSION.SDK_INT} " +
                "localNetworkPermission=$localNetworkGranted cameraPermission=$cameraGranted"
        )
        if (
            Build.VERSION.SDK_INT >= 37 &&
            !localNetworkGranted
        ) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            continueWithCamera()
        }
    }

    if (showScanner) {
        QrScannerScreen(
            onResult = ::handleScanned,
            onBack = { showScanner = false },
            title = t("Синхронизация с ПК", "Desktop sync"),
            instruction = t(
                "Наведите камеру на одноразовый QR в Nimbo Desktop",
                "Point the camera at the one-time QR in Nimbo Desktop"
            ),
            diagnosticSource = "desktop_sync"
        )
        return
    }

    val effectiveLocalBundle = CrossSyncPairingEngine.localBundle ?: AndroidCrossSyncBundleMapper.export(
        preferencesManager,
        profiles
    )
    val localInventory = effectiveLocalBundle.inventory()
    val remoteInventory = response?.desktopBundle?.inventory() ?: response?.desktopInventory
    val remoteDeviceInfo = response?.desktopBundle?.deviceInfo ?: response?.desktopDeviceInfo
    val remoteSubscriptionNames = response?.desktopBundle?.let { bundle ->
        CrossSyncProtocol.subscriptionPreviewNames(bundle.subscriptions)
    } ?: response?.desktopSubscriptions.orEmpty()
    val recommendation = remoteInventory?.let { CrossSyncProtocol.recommendDirection(localInventory, it) }
    val selectedCount = listOf(
        categories.subscriptions,
        categories.appearance,
        categories.connection,
        categories.automation
    ).count { it }
    val sessionSecondsLeft = qr?.let { SyncMotionPolicy.secondsLeft(nowMs, it.expiresAtMs) } ?: 0
    val sessionProgress = qr?.let {
        SyncMotionPolicy.progress(nowMs, it.expiresAtMs, sessionLifetimeMs)
    } ?: 0f
    val sessionActive = stage != CrossSyncPairingStage.IDLE && stage != CrossSyncPairingStage.COMPLETED
    val pairedDevices = CrossSyncPairingEngine.pairedDevices

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад", "Back"), tint = colors.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        t("Синхронизация", "Synchronization"),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        t("Nimbo Desktop ↔ Android", "Nimbo Desktop ↔ Android"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (pairedDevices.isNotEmpty()) {
            item {
                SyncGlassCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SyncRoundIcon(Icons.Default.Computer)
                        Column(Modifier.weight(1f)) {
                            Text(
                                t("Устройства", "Devices"),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                t(
                                    "Синхронизированные устройства и автосинхронизация по локальной сети.",
                                    "Synced devices and auto-sync over the local network."
                                ),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairedDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedDevice = device }
                                    .background(colors.controlFill)
                                    .padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                SyncPlatformIcon(
                                    platform = device.platform,
                                    size = 36.dp,
                                    tint = colors.accent
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(device.name, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (device.lastSyncMs > 0) {
                                            t(
                                                "Последняя синхронизация: ${syncAgoText(device.lastSyncMs)}",
                                                "Last sync: ${syncAgoText(device.lastSyncMs)}"
                                            )
                                        } else {
                                            t("Синхронизация ещё не выполнялась", "Not synced yet")
                                        },
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        if (device.autoSync) {
                                            t("Автосинхронизация включена", "Auto-sync on")
                                        } else {
                                            t("Автосинхронизация выключена", "Auto-sync off")
                                        },
                                        color = if (device.autoSync) colors.accent else colors.textTertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    null,
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SyncGlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SyncRoundIcon(Icons.Default.Lock)
                    Column(Modifier.weight(1f)) {
                        Text(t("Передача напрямую", "Direct transfer"), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            t(
                                "Без облака: AES‑256‑GCM, локальная Wi‑Fi сеть и одноразовый QR на 60 секунд.",
                                "No cloud: AES-256-GCM, local Wi-Fi and a 60-second one-time QR."
                            ),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SyncDeviceSignalBridge(
                    active = motionEnabled,
                    sessionActive = sessionActive
                )
            }
        }

        item {
            SyncGlassCard {
                Text(t("Что синхронизировать", "What to sync"), color = colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                SyncCategoryRow(t("Подписки", "Subscriptions"), t("Ссылки и пользовательские названия", "Links and custom names"), categories.subscriptions, stage == CrossSyncPairingStage.IDLE) {
                    saveCategories(categories.copy(subscriptions = it))
                }
                SyncCategoryRow(t("Оформление", "Appearance"), t("Тема, акцент, стекло и скругления", "Theme, accent, glass and rounding"), categories.appearance, stage == CrossSyncPairingStage.IDLE) {
                    saveCategories(categories.copy(appearance = it))
                }
                SyncCategoryRow(t("Подключение", "Connection"), t("Kill Switch, TLS-фрагментация, график", "Kill Switch, TLS fragmentation, chart"), categories.connection, stage == CrossSyncPairingStage.IDLE) {
                    saveCategories(categories.copy(connection = it))
                }
                SyncCategoryRow(t("Автоматизация", "Automation"), t("Язык, ping и обновления", "Language, ping and updates"), categories.automation, stage == CrossSyncPairingStage.IDLE) {
                    saveCategories(categories.copy(automation = it))
                }
            }
        }

        if (stage == CrossSyncPairingStage.IDLE) {
            item {
                Button(
                    onClick = ::openScanner,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.size(9.dp))
                    Text(t("Сканировать QR на ПК", "Scan QR on desktop"), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (stage == CrossSyncPairingStage.CONNECTING || stage == CrossSyncPairingStage.WAITING_DESKTOP || stage == CrossSyncPairingStage.WAITING_IMPORT_CONFIRMATION) {
            item {
                SyncGlassCard(border = colors.accent.copy(alpha = 0.45f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (qr == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = colors.accent,
                                strokeWidth = 3.dp
                            )
                        } else {
                            SyncCountdownIndicator(
                                progress = sessionProgress,
                                secondsLeft = sessionSecondsLeft,
                                motionEnabled = motionEnabled
                            )
                        }
                        Column {
                            Text(
                                when (stage) {
                                    CrossSyncPairingStage.WAITING_DESKTOP -> t("Подтвердите телефон на ПК", "Approve this phone on desktop")
                                    CrossSyncPairingStage.WAITING_IMPORT_CONFIRMATION -> t("Подтвердите импорт на ПК", "Confirm import on desktop")
                                    else -> t("Защищённое подключение…", "Secure connection…")
                                },
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            response?.comparisonCode?.let { code ->
                                SyncVerificationCode(code = code, motionEnabled = motionEnabled)
                            }
                        }
                    }
                }
            }
        }

        if (stage == CrossSyncPairingStage.WAITING_DESKTOP && remoteDeviceInfo != null && remoteInventory != null) {
            item {
                SyncRemoteDevicePassport(
                    info = remoteDeviceInfo,
                    inventory = remoteInventory,
                    subscriptions = remoteSubscriptionNames
                )
            }
        }

        if (stage == CrossSyncPairingStage.CHOOSE_DIRECTION && remoteInventory != null) {
            item {
                SyncDeviceComparison(
                    localInfo = effectiveLocalBundle.deviceInfo,
                    local = localInventory,
                    remoteInfo = remoteDeviceInfo,
                    remote = remoteInventory,
                    remoteSubscriptions = remoteSubscriptionNames
                )
            }
            item {
                Text(
                    t("Куда перенести данные", "Transfer direction"),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            item {
                SyncDirectionCard(
                    title = t("С компьютера на телефон", "Desktop to phone"),
                    subtitle = t("Дополнит этот телефон данными с ПК", "Adds desktop data to this phone"),
                    recommended = recommendation == SyncDirection.DESKTOP_TO_ANDROID,
                    iconFrom = Icons.Default.Computer,
                    iconTo = Icons.Default.PhoneAndroid,
                    onClick = { transfer(SyncDirection.DESKTOP_TO_ANDROID) }
                )
            }
            item {
                SyncDirectionCard(
                    title = t("С телефона на компьютер", "Phone to desktop"),
                    subtitle = t("На ПК потребуется ещё одно подтверждение", "Desktop requires one more confirmation"),
                    recommended = recommendation == SyncDirection.ANDROID_TO_DESKTOP,
                    iconFrom = Icons.Default.PhoneAndroid,
                    iconTo = Icons.Default.Computer,
                    onClick = { transfer(SyncDirection.ANDROID_TO_DESKTOP) }
                )
            }
        }

        if (stage == CrossSyncPairingStage.READY_TO_IMPORT) {
            item {
                SyncGlassCard(border = colors.accent.copy(alpha = 0.55f)) {
                    Text(t("Применить данные с компьютера?", "Apply desktop data?"), color = colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        t(
                            "Существующие подписки и настройки не удаляются безвозвратно. Новые подписки будут добавлены, совпадения пропущены.",
                            "Existing subscriptions are preserved. New links are added and duplicates are skipped."
                        ),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = ::applyDesktopData,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(t("Применить выбранное", "Apply selected"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (stage == CrossSyncPairingStage.COMPLETED) {
            item {
                SyncGlassCard(border = colors.statusConnected.copy(alpha = 0.55f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SyncCompletedIcon(motionEnabled = motionEnabled)
                        Column(Modifier.weight(1f)) {
                            Text(t("Добавить устройство", "Add a device"), color = colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (addedSubscriptions > 0) t("Синхронизация завершена: добавлено подписок $addedSubscriptions", "Sync completed: $addedSubscriptions subscriptions added")
                                else t("Синхронизация завершена. Можно добавить ещё одно устройство", "Sync completed. You can add another device"),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { resetSession(openScanner = true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.size(8.dp))
                        Text(t("Добавить устройство", "Add a device"), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t(
                            "Можно подключить несколько компьютеров — каждый синхронизируется отдельно.",
                            "You can pair several desktops — each syncs separately."
                        ),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        error?.let { message ->
            item {
                SyncGlassCard(border = colors.statusError.copy(alpha = 0.55f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            if (offline) Icons.Default.WifiOff else Icons.Default.Warning,
                            null,
                            tint = colors.statusError,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (offline) {
                                    t("Устройство не в сети", "Device is offline")
                                } else {
                                    t("Ошибка синхронизации", "Sync error")
                                },
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                message,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { resetSession(openScanner = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Понятно", "Got it"))
                    }
                }
            }
        }

        if (preferencesManager.crossSyncLastAt > 0L) {
            item {
                Text(
                    t("Последняя синхронизация", "Last synchronization") + ": " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(preferencesManager.crossSyncLastAt)) +
                        preferencesManager.crossSyncLastDevice.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                t(
                    "Не переносятся разрешения VPN, списки приложений, логи, статистика, пароли локального прокси и текущее подключение.",
                    "VPN permissions, app lists, logs, statistics, local proxy passwords and the current connection are not transferred."
                ),
                color = colors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }
    }

    selectedDevice?.let { device ->
        DeviceDetailsSheet(
            device = device,
            categories = categories,
            busy = pairedSyncing,
            onCategoryChange = ::saveCategories,
            onAutoSyncChange = { enabled ->
                preferencesManager.crossSyncPairedDevices =
                    preferencesManager.crossSyncPairedDevices.map {
                        if (it.deviceId == device.deviceId) it.copy(autoSync = enabled) else it
                    }
                CrossSyncPairingEngine.pairedDevices = preferencesManager.crossSyncPairedDevices
                Logger.i("CrossSync", "Auto-sync set to $enabled for ${device.deviceId}")
            },
            onSyncNow = { syncPairedNow(device) },
            onDelete = { deviceToDelete = device },
            onDismiss = { selectedDevice = null }
        )
    }

    deviceToDelete?.let { device ->
        NebulaMorphicDialog(
            onDismissRequest = { deviceToDelete = null },
            title = "Удалить устройство?",
            description = "${device.name} будет удалено. Синхронизация с ним прекратится, его данные на этом телефоне сохранятся.",
            confirmButtonText = "Удалить",
            onConfirm = {
                deviceToDelete = null
                deletePairedDevice(device)
            }
        )
    }
}

@Composable
private fun SyncDeviceSignalBridge(
    active: Boolean,
    sessionActive: Boolean
) {
    val colors = LocalNebulaColors.current
    val transition = rememberInfiniteTransition(label = "sync_signal_bridge")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (sessionActive) 1_250 else 2_600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_signal_travel"
    )
    val currentTravel = if (active) travel else 0.5f
    // controlFill is intentionally bright for switches and compact controls. At the
    // size of this bridge it turned into a large grey slab in dark themes, so keep
    // the panel tied to the surrounding surface and add only a restrained accent tint.
    val bridgeFill = colors.accent.copy(
        alpha = if (colors.isMaterialYou) 0.08f else 0.065f
    )
    val bridgeBorder = colors.accent.copy(
        alpha = if (colors.isMaterialYou) 0.18f else 0.14f
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bridgeFill)
            .border(1.dp, bridgeBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SyncSignalEndpoint(Icons.Default.PhoneAndroid, t("Телефон", "Phone"))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
        ) {
            val centerY = size.height / 2f
            drawLine(
                color = colors.divider,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            repeat(3) { index ->
                val local = (currentTravel + index / 3f) % 1f
                val alpha = (0.28f + 0.72f * (1f - kotlin.math.abs(local - 0.5f) * 1.25f))
                    .coerceIn(0.22f, 1f)
                drawCircle(
                    color = colors.accent.copy(alpha = alpha),
                    radius = (if (index == 0) 4.2.dp else 3.dp).toPx(),
                    center = Offset(size.width * local, centerY)
                )
            }
        }
        SyncSignalEndpoint(Icons.Default.Computer, t("Компьютер", "Desktop"))
    }
}

@Composable
private fun SyncSignalEndpoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    val colors = LocalNebulaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color = colors.textTertiary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SyncCountdownIndicator(
    progress: Float,
    secondsLeft: Int,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalNebulaColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(280, easing = LinearEasing),
        label = "sync_countdown_progress"
    )
    val urgent = secondsLeft in 0..15
    val barColor = if (urgent) colors.statusError else colors.accent
    val fraction = (if (motionEnabled) animatedProgress else progress).coerceIn(0f, 1f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = secondsLeft,
            transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(130)) },
            label = "sync_countdown_number"
        ) { value ->
            Text(
                value.toString(),
                color = barColor,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(4.5.dp))
                .background(colors.divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.5.dp))
                    .background(barColor)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(t("сек", "sec"), color = colors.textTertiary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SyncVerificationCode(code: String, motionEnabled: Boolean) {
    val colors = LocalNebulaColors.current
    val transition = rememberInfiniteTransition(label = "sync_verification_code")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sync_verification_code_pulse"
    )
    Text(
        t("Код проверки: $code", "Verification code: $code"),
        color = colors.accent,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer {
            val scale = if (motionEnabled) pulse else 1f
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
private fun SyncCompletedIcon(motionEnabled: Boolean) {
    val colors = LocalNebulaColors.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val scale by animateFloatAsState(
        targetValue = if (revealed && motionEnabled) 1f else if (motionEnabled) 0.55f else 1f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "sync_completed_scale"
    )
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale.coerceIn(0f, 1f)
            }
            .background(colors.statusConnected.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Check, null, tint = colors.statusConnected)
    }
}

@Composable
private fun SyncGlassCard(
    border: Color = LocalNebulaColors.current.panelBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNebulaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.panelFill.copy(alpha = if (colors.isMaterialYou) 1f else 0.78f))
            .border(1.dp, border.takeIf { it != Color.Transparent } ?: colors.divider, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SyncRoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = LocalNebulaColors.current
    Box(
        modifier = Modifier.size(42.dp).background(colors.accent.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = colors.accent)
    }
}

@Composable
private fun SyncCategoryRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    val colors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
        )
    }
}

@Composable
private fun SyncDeviceComparison(
    localInfo: SyncDeviceInfo?,
    local: SyncInventory,
    remoteInfo: SyncDeviceInfo?,
    remote: SyncInventory,
    remoteSubscriptions: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SyncRemoteDevicePassport(
            info = remoteInfo,
            inventory = remote,
            subscriptions = remoteSubscriptions
        )
        SyncGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncDeviceInventory(
                    icon = Icons.Default.PhoneAndroid,
                    title = localInfo?.name ?: t("Этот телефон", "This phone"),
                    inventory = local,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.Sync, null, tint = LocalNebulaColors.current.accent)
                SyncDeviceInventory(
                    icon = Icons.Default.Computer,
                    title = remoteInfo?.name ?: t("Компьютер", "Desktop"),
                    inventory = remote,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SyncRemoteDevicePassport(
    info: SyncDeviceInfo?,
    inventory: SyncInventory,
    subscriptions: List<String>
) {
    val colors = LocalNebulaColors.current
    SyncGlassCard(border = colors.accent.copy(alpha = 0.42f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(46.dp).background(colors.accent.copy(alpha = 0.16f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Computer, null, tint = colors.accent, modifier = Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    t("КОМПЬЮТЕР ОБНАРУЖЕН", "DESKTOP FOUND"),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    info?.name ?: t("Nimbo Desktop", "Nimbo Desktop"),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    listOfNotNull(info?.osName, info?.osVersion).joinToString(" · ").ifBlank { t("Настольная система", "Desktop system") },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SyncSpecPill("Nimbo ${info?.appVersion ?: "—"}", Modifier.weight(1f))
            SyncSpecPill(info?.architecture ?: t("Не указано", "Unknown"), Modifier.weight(1f))
            SyncSpecPill(t("${inventory.subscriptions} подписок", "${inventory.subscriptions} subscriptions"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        Spacer(Modifier.height(10.dp))
        Text(
            t("ПОДПИСКИ НА КОМПЬЮТЕРЕ", "DESKTOP SUBSCRIPTIONS"),
            color = colors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(7.dp))
        if (inventory.subscriptions == 0) {
            Text(t("Подписок пока нет", "No subscriptions yet"), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            subscriptions.take(5).forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                    Text(name, color = colors.textPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            val hidden = (inventory.subscriptions - subscriptions.take(5).size).coerceAtLeast(0)
            if (hidden > 0) {
                Text(
                    t("Ещё $hidden", "$hidden more"),
                    color = colors.accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SyncSpecPill(text: String, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.controlFill)
            .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun SyncDeviceInventory(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    inventory: SyncInventory,
    modifier: Modifier
) {
    val colors = LocalNebulaColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(5.dp))
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            t("Подписок: ${inventory.subscriptions}", "Subscriptions: ${inventory.subscriptions}"),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SyncDirectionCard(
    title: String,
    subtitle: String,
    recommended: Boolean,
    iconFrom: androidx.compose.ui.graphics.vector.ImageVector,
    iconTo: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panelFill.copy(alpha = if (colors.isMaterialYou) 1f else 0.78f))
            .border(
                BorderStroke(1.dp, if (recommended) colors.accent.copy(alpha = 0.65f) else colors.divider),
                shape
            )
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFrom, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            Text(" → ", color = colors.textSecondary, fontWeight = FontWeight.Bold)
            Icon(iconTo, null, tint = colors.accent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                if (recommended) {
                    Text(
                        t("СОВЕТ", "RECOMMENDED"),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun mobileSyncError(cause: Throwable): String {
    val raw = cause.message.orEmpty()
    return when {
        raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
            "Компьютер не ответил. Проверьте, что устройства в одной Wi‑Fi сети и Nimbo разрешён в брандмауэре"
        raw.contains("refused", ignoreCase = true) ->
            "Сеанс на компьютере уже закрыт. Создайте новый QR"
        raw.isNotBlank() -> raw
        else -> "Не удалось выполнить синхронизацию"
    }
}

private fun syncAgoText(lastSyncMs: Long): String {
    val seconds = (System.currentTimeMillis() - lastSyncMs) / 1000
    return when {
        seconds < 60 -> "только что"
        seconds < 3600 -> "${seconds / 60} мин назад"
        seconds < 86_400 -> "${seconds / 3600} ч назад"
        else -> "${seconds / 86_400} дн назад"
    }
}

private fun pairedDateText(pairedAtMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(pairedAtMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailsSheet(
    device: PairedDesktopDevice,
    categories: SyncCategories,
    busy: Boolean,
    onCategoryChange: (SyncCategories) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalNebulaColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (colors.isLiquidGlass) {
            colors.background.copy(alpha = 0.96f)
        } else {
            colors.surface
        },
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                SyncPlatformIcon(platform = device.platform, size = 54.dp, tint = colors.accent)
                Column(Modifier.weight(1f)) {
                    Text(device.name, color = colors.textPrimary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        listOfNotNull(
                            device.osName,
                            device.osVersion,
                            device.appVersion?.let { "Nimbo $it" }
                        ).joinToString(" · ").ifBlank { "Nimbo Desktop" },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                t("Добавлено ${pairedDateText(device.pairedAtMs)} · Последняя синхронизация ${if (device.lastSyncMs > 0) syncAgoText(device.lastSyncMs) else "ещё не было"}",
                  "Paired ${pairedDateText(device.pairedAtMs)} · Last sync ${if (device.lastSyncMs > 0) syncAgoText(device.lastSyncMs) else "never"}"),
                color = colors.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                t("ПОДПИСКИ НА УСТРОЙСТВЕ", "SUBSCRIPTIONS ON THE DEVICE"),
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            if (device.lastSubscriptionCount <= 0) {
                Text(
                    t("Подписок на устройстве пока нет", "No subscriptions on the device yet"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val shown = device.lastSubscriptionNames.take(8)
                shown.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                        Text(name, color = colors.textPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                val hidden = (device.lastSubscriptionCount - shown.size).coerceAtLeast(0)
                if (hidden > 0) {
                    Text(
                        t("Ещё $hidden подписок", "$hidden more subscriptions"),
                        color = colors.accent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

            Text(
                t("ЧТО СИНХРОНИЗИРУЕТСЯ С УСТРОЙСТВОМ", "WHAT IS SYNCED WITH THIS DEVICE"),
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            SyncCategoryRow(t("Подписки", "Subscriptions"), t("Ссылки и пользовательские названия", "Links and custom names"), categories.subscriptions, enabled = true) {
                onCategoryChange(categories.copy(subscriptions = it))
            }
            SyncCategoryRow(t("Оформление", "Appearance"), t("Тема, акцент, стекло и скругления", "Theme, accent, glass and rounding"), categories.appearance, enabled = true) {
                onCategoryChange(categories.copy(appearance = it))
            }
            SyncCategoryRow(t("Подключение", "Connection"), t("Kill Switch, TLS-фрагментация, график", "Kill Switch, TLS fragmentation, chart"), categories.connection, enabled = true) {
                onCategoryChange(categories.copy(connection = it))
            }
            SyncCategoryRow(t("Автоматизация", "Automation"), t("Язык, ping и обновления", "Language, ping and updates"), categories.automation, enabled = true) {
                onCategoryChange(categories.copy(automation = it))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t("Автосинхронизация", "Auto-sync"),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        t("Автоматически обновлять данные при подключении к локальной сети.", "Automatically update when connected to the local network."),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = device.autoSync,
                    onCheckedChange = onAutoSyncChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.surface
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassIconButton(icon = Icons.Default.Sync, color = colors.accent, onClick = onSyncNow)
                    Text(t("Синхронизировать", "Sync now"), color = colors.textTertiary, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassIconButton(icon = Icons.Default.Delete, color = Color(0xFFFF5252), onClick = onDelete)
                    Text(t("Удалить устройство", "Remove device"), color = Color(0xFFFF5252), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SyncPlatformIcon(
    platform: String,
    size: Dp,
    tint: Color
) {
    val isAndroid = platform.equals("android", ignoreCase = true)
    Box(
        modifier = Modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(size.value * 0.38f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.2f)) {
            if (isAndroid) drawAndroidRobot(tint) else drawMonitor(tint)
        }
    }
}

private fun DrawScope.drawAndroidRobot(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.09f
    drawLine(color, Offset(w * 0.28f, h * 0.06f), Offset(w * 0.37f, h * 0.26f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.72f, h * 0.06f), Offset(w * 0.63f, h * 0.26f), stroke, StrokeCap.Round)
    val head = Path().apply {
        fillType = PathFillType.EvenOdd
        arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, w, h * 1.05f), 180f, 180f, false)
        close()
        addRect(androidx.compose.ui.geometry.Rect(w * 0.24f, h * 0.42f, w * 0.39f, h * 0.6f))
        addRect(androidx.compose.ui.geometry.Rect(w * 0.61f, h * 0.42f, w * 0.76f, h * 0.6f))
    }
    drawPath(head, color)
    val legs = Path().apply {
        moveTo(w * 0.14f, h * 0.62f)
        lineTo(w * 0.14f, h * 0.92f)
        lineTo(w * 0.33f, h * 0.92f)
        lineTo(w * 0.33f, h * 0.62f)
        moveTo(w * 0.67f, h * 0.62f)
        lineTo(w * 0.67f, h * 0.92f)
        lineTo(w * 0.86f, h * 0.92f)
        lineTo(w * 0.86f, h * 0.62f)
        close()
    }
    drawPath(legs, color)
}

private fun DrawScope.drawMonitor(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.08f, h * 0.08f),
        size = Size(w * 0.84f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.09f)
    )
    drawLine(color, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.84f), w * 0.09f, StrokeCap.Round)
    drawLine(color, Offset(w * 0.3f, h * 0.88f), Offset(w * 0.7f, h * 0.88f), w * 0.09f, StrokeCap.Round)
}
