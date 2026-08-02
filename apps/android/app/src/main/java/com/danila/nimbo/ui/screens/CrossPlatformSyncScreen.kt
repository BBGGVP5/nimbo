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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.sync.AndroidCrossSyncBundleMapper
import com.danila.nimbo.sync.CrossSyncBundle
import com.danila.nimbo.sync.CrossSyncClient
import com.danila.nimbo.sync.CrossSyncProtocol
import com.danila.nimbo.sync.CrossSyncQr
import com.danila.nimbo.sync.SyncCategories
import com.danila.nimbo.sync.SyncDirection
import com.danila.nimbo.sync.SyncDeviceInfo
import com.danila.nimbo.sync.SyncInventory
import com.danila.nimbo.sync.SyncWireRequest
import com.danila.nimbo.sync.SyncWireResponse
import com.danila.nimbo.ui.components.QrScannerScreen
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class MobileSyncStage {
    IDLE,
    CONNECTING,
    WAITING_DESKTOP,
    CHOOSE_DIRECTION,
    READY_TO_IMPORT,
    WAITING_IMPORT_CONFIRMATION,
    COMPLETED
}

@Composable
fun CrossPlatformSyncScreen(
    preferencesManager: PreferencesManager,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val profiles by mainViewModel.profilesState.collectAsState()
    val scope = rememberCoroutineScope()
    val client = remember { CrossSyncClient() }
    val motionEnabled = LocalBackgroundAnimationEnabled.current

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
    var scanHandled by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(MobileSyncStage.IDLE) }
    var qr by remember { mutableStateOf<CrossSyncQr?>(null) }
    var response by remember { mutableStateOf<SyncWireResponse?>(null) }
    var localBundle by remember { mutableStateOf<CrossSyncBundle?>(null) }
    var pendingDesktopBundle by remember { mutableStateOf<CrossSyncBundle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addedSubscriptions by remember { mutableStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionLifetimeMs by remember { mutableLongStateOf(75_000L) }

    LaunchedEffect(qr?.sessionId, stage) {
        while (
            qr != null &&
            stage != MobileSyncStage.IDLE &&
            stage != MobileSyncStage.COMPLETED
        ) {
            nowMs = System.currentTimeMillis()
            delay(250L)
        }
    }

    fun saveCategories(next: SyncCategories) {
        categories = next
        preferencesManager.crossSyncSubscriptions = next.subscriptions
        preferencesManager.crossSyncAppearance = next.appearance
        preferencesManager.crossSyncConnection = next.connection
        preferencesManager.crossSyncAutomation = next.automation
    }

    fun resetSession(openScanner: Boolean) {
        error = null
        response = null
        qr = null
        localBundle = null
        pendingDesktopBundle = null
        addedSubscriptions = 0
        sessionLifetimeMs = 75_000L
        nowMs = System.currentTimeMillis()
        stage = MobileSyncStage.IDLE
        scanHandled = false
        showScanner = openScanner
    }

    fun applyResponse(next: SyncWireResponse) {
        response = next
        error = next.message.takeIf { next.state == "rejected" }
        stage = when (next.state) {
            "awaiting_approval" -> MobileSyncStage.WAITING_DESKTOP
            "paired" -> MobileSyncStage.CHOOSE_DIRECTION
            "awaiting_import_confirmation" -> MobileSyncStage.WAITING_IMPORT_CONFIRMATION
            "completed" -> MobileSyncStage.COMPLETED
            "rejected", "cancelled", "expired" -> MobileSyncStage.IDLE
            else -> stage
        }
    }

    suspend fun pollUntilDecision(sessionQr: CrossSyncQr): SyncWireResponse {
        while (scope.isActive && System.currentTimeMillis() < sessionQr.expiresAtMs) {
            delay(800)
            val next = client.exchange(sessionQr, SyncWireRequest(action = "status"))
            applyResponse(next)
            if (next.state in setOf("paired", "completed", "rejected", "cancelled", "expired")) {
                return next
            }
        }
        throw IllegalStateException("Сеанс истёк. Обновите QR на компьютере")
    }

    fun handleScanned(raw: String) {
        if (scanHandled) return
        scanHandled = true
        showScanner = false
        error = null
        stage = MobileSyncStage.CONNECTING
        scope.launch {
            try {
                val parsed = CrossSyncProtocol.parseQr(raw)
                val scannedAt = System.currentTimeMillis()
                sessionLifetimeMs = (parsed.expiresAtMs - scannedAt).coerceAtLeast(1_000L)
                nowMs = scannedAt
                val exported = AndroidCrossSyncBundleMapper.export(
                    preferencesManager,
                    profiles
                )
                qr = parsed
                localBundle = exported
                val hello = client.exchange(
                    parsed,
                    SyncWireRequest(
                        action = "hello",
                        deviceName = exported.deviceName,
                        bundle = exported.filtered(categories)
                    )
                )
                applyResponse(hello)
                if (hello.state == "awaiting_approval") pollUntilDecision(parsed)
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                stage = MobileSyncStage.IDLE
            }
        }
    }

    fun transfer(direction: SyncDirection) {
        val sessionQr = qr ?: return
        val exported = localBundle ?: return
        error = null
        stage = if (direction == SyncDirection.DESKTOP_TO_ANDROID) {
            MobileSyncStage.CONNECTING
        } else {
            MobileSyncStage.WAITING_IMPORT_CONFIRMATION
        }
        scope.launch {
            try {
                val next = client.exchange(
                    sessionQr,
                    SyncWireRequest(
                        action = "commit",
                        deviceName = exported.deviceName,
                        bundle = exported.filtered(categories),
                        direction = direction,
                        categories = categories
                    )
                )
                applyResponse(next)
                when (direction) {
                    SyncDirection.DESKTOP_TO_ANDROID -> {
                        pendingDesktopBundle = next.desktopBundle
                            ?: throw IllegalStateException("Компьютер не передал данные")
                        stage = MobileSyncStage.READY_TO_IMPORT
                    }
                    SyncDirection.ANDROID_TO_DESKTOP -> {
                        val final = if (next.state == "completed") next else pollUntilDecision(sessionQr)
                        if (final.state == "completed") {
                            preferencesManager.crossSyncLastAt = System.currentTimeMillis()
                            preferencesManager.crossSyncLastDevice = "Nimbo Desktop"
                            runCatching {
                                client.exchange(sessionQr, SyncWireRequest(action = "receipt"))
                            }
                            stage = MobileSyncStage.COMPLETED
                        } else if (final.state == "rejected") {
                            throw IllegalStateException(final.message ?: "Импорт отклонён на компьютере")
                        }
                    }
                }
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                stage = if (response?.state in setOf("rejected", "cancelled", "expired")) {
                    MobileSyncStage.IDLE
                } else {
                    MobileSyncStage.CHOOSE_DIRECTION
                }
            }
        }
    }

    fun applyDesktopData() {
        val sessionQr = qr ?: return
        val incoming = pendingDesktopBundle ?: return
        error = null
        stage = MobileSyncStage.CONNECTING
        scope.launch {
            try {
                val languageBefore = preferencesManager.appLanguage
                AndroidCrossSyncBundleMapper.applySettings(preferencesManager, incoming, categories)
                val missing = AndroidCrossSyncBundleMapper.missingSubscriptions(profiles, incoming, categories)
                missing.forEach { subscription ->
                    mainViewModel.addSubscription(subscription.url)
                    subscription.name?.takeIf { it.isNotBlank() }?.let { name ->
                        mainViewModel.renameProfile(subscription.url, name)
                    }
                }
                addedSubscriptions = missing.size
                preferencesManager.crossSyncLastAt = System.currentTimeMillis()
                preferencesManager.crossSyncLastDevice = incoming.deviceName
                client.exchange(sessionQr, SyncWireRequest(action = "receipt"))
                stage = MobileSyncStage.COMPLETED
                mainViewModel.showTopNotification(
                    if (missing.isEmpty()) "Настройки синхронизированы" else "Синхронизация завершена: добавлено ${missing.size}"
                )
                if (preferencesManager.appLanguage != languageBefore) {
                    delay(900)
                    (context as? Activity)?.recreate()
                }
            } catch (cause: Throwable) {
                error = mobileSyncError(cause)
                stage = MobileSyncStage.READY_TO_IMPORT
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Logger.i("QrScanner", "source=desktop_sync event=camera_permission_result granted=$granted")
        if (granted) resetSession(openScanner = true)
        else error = "Для сканирования QR требуется разрешение камеры"
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
        else error = "Разрешите доступ к локальной сети, чтобы телефон мог напрямую подключиться к Nimbo Desktop"
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

    val effectiveLocalBundle = localBundle ?: AndroidCrossSyncBundleMapper.export(
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
    val sessionActive = stage != MobileSyncStage.IDLE && stage != MobileSyncStage.COMPLETED
    val syncRotationTransition = rememberInfiniteTransition(label = "sync_header_rotation")
    val syncRotation by syncRotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_header_rotation_value"
    )

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
                Icon(
                    Icons.Default.Sync,
                    null,
                    tint = colors.accent,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            rotationZ = if (sessionActive && motionEnabled) syncRotation else 0f
                        }
                )
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
                                "Без облака: AES‑256‑GCM, локальная Wi‑Fi сеть и одноразовый QR на 75 секунд.",
                                "No cloud: AES-256-GCM, local Wi-Fi and a 75-second one-time QR."
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
                SyncCategoryRow(t("Подписки", "Subscriptions"), t("Ссылки и пользовательские названия", "Links and custom names"), categories.subscriptions, stage == MobileSyncStage.IDLE) {
                    saveCategories(categories.copy(subscriptions = it))
                }
                SyncCategoryRow(t("Оформление", "Appearance"), t("Тема, акцент, стекло и скругления", "Theme, accent, glass and rounding"), categories.appearance, stage == MobileSyncStage.IDLE) {
                    saveCategories(categories.copy(appearance = it))
                }
                SyncCategoryRow(t("Подключение", "Connection"), t("Kill Switch, TLS-фрагментация, график", "Kill Switch, TLS fragmentation, chart"), categories.connection, stage == MobileSyncStage.IDLE) {
                    saveCategories(categories.copy(connection = it))
                }
                SyncCategoryRow(t("Автоматизация", "Automation"), t("Язык, ping и обновления", "Language, ping and updates"), categories.automation, stage == MobileSyncStage.IDLE) {
                    saveCategories(categories.copy(automation = it))
                }
            }
        }

        if (stage == MobileSyncStage.IDLE) {
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

        if (stage == MobileSyncStage.CONNECTING || stage == MobileSyncStage.WAITING_DESKTOP || stage == MobileSyncStage.WAITING_IMPORT_CONFIRMATION) {
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
                                motionEnabled = motionEnabled,
                                modifier = Modifier.size(58.dp)
                            )
                        }
                        Column {
                            Text(
                                when (stage) {
                                    MobileSyncStage.WAITING_DESKTOP -> t("Подтвердите телефон на ПК", "Approve this phone on desktop")
                                    MobileSyncStage.WAITING_IMPORT_CONFIRMATION -> t("Подтвердите импорт на ПК", "Confirm import on desktop")
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

        if (stage == MobileSyncStage.WAITING_DESKTOP && remoteDeviceInfo != null && remoteInventory != null) {
            item {
                SyncRemoteDevicePassport(
                    info = remoteDeviceInfo,
                    inventory = remoteInventory,
                    subscriptions = remoteSubscriptionNames
                )
            }
        }

        if (stage == MobileSyncStage.CHOOSE_DIRECTION && remoteInventory != null) {
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

        if (stage == MobileSyncStage.READY_TO_IMPORT) {
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

        if (stage == MobileSyncStage.COMPLETED) {
            item {
                SyncGlassCard(border = colors.statusConnected.copy(alpha = 0.55f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SyncCompletedIcon(motionEnabled = motionEnabled)
                        Column(Modifier.weight(1f)) {
                            Text(t("Синхронизация завершена", "Sync completed"), color = colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (addedSubscriptions > 0) t("Добавлено подписок: $addedSubscriptions", "Subscriptions added: $addedSubscriptions")
                                else t("Выбранные данные перенесены", "Selected data was transferred"),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { resetSession(openScanner = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Синхронизировать ещё раз", "Sync again"))
                    }
                }
            }
        }

        error?.let { message ->
            item {
                SyncGlassCard(border = colors.statusError.copy(alpha = 0.55f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null, tint = colors.statusError)
                        Text(message, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { resetSession(openScanner = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Начать заново", "Start over"))
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
    val ringColor = if (urgent) colors.statusError else colors.accent

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.divider,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * if (motionEnabled) animatedProgress else progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = secondsLeft,
                transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(130)) },
                label = "sync_countdown_number"
            ) { value ->
                Text(
                    value.toString(),
                    color = ringColor,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(t("сек", "sec"), color = colors.textTertiary, style = MaterialTheme.typography.labelSmall)
        }
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
