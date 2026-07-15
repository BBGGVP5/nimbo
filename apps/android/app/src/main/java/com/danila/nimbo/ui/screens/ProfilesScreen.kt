package com.danila.nimbo.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.DeleteProfileDialog
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.QrScannerScreen
import com.danila.nimbo.ui.components.SubscriptionBrandLogo
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.MainViewModel
import kotlinx.coroutines.launch
import com.google.gson.annotations.SerializedName
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import java.util.LinkedHashMap
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.formatBytes
import com.danila.nimbo.utils.PreferencesManager

private fun sanitizeProfileErrorForUi(error: String?): String? {
    if (error.isNullOrBlank()) return null
    val normalized = error.trim()
    val lower = normalized.lowercase()

    if (
        lower.contains("failed to connect") ||
        lower.contains("connect to") ||
        lower.contains("connection refused") ||
        lower.contains("timed out") ||
        lower.contains("timeout") ||
        lower.contains("unable to resolve host") ||
        lower.contains("unknownhost")
    ) {
        return "Не удалось подключиться к серверу подписки"
    }

    val redacted = normalized
        .replace(Regex("""https?://\S+"""), "[url]")
        .replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b"""), "[ip]")
        .replace(Regex("""(?i)\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b"""), "[host]")
        .replace(Regex("""(?i)\bport\s*\d+\b"""), "порт")
        .take(120)

    return "Ошибка: $redacted"
}

data class SubscriptionProfile(
    @SerializedName("url") val url: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("servers") val servers: List<Server> = emptyList(),
    @SerializedName("isLoading") val isLoading: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("uploadTotal") val uploadTotal: Long = 0L,
    @SerializedName("downloadTotal") val downloadTotal: Long = 0L,
    @SerializedName("totalTraffic") val totalTraffic: Long = 0L,
    @SerializedName("expireTime") val expireTime: Long = 0L,
    @SerializedName("deviceCount") val deviceCount: Int = 0,
    @SerializedName("deviceLimit") val deviceLimit: Int = 0,
    @SerializedName("onlineDevices") val onlineDevices: Int = 0,
    @SerializedName("announce") val announce: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("daysUntilExpiry") val daysUntilExpiry: Long = -1L,
    @SerializedName("website_url") val websiteUrl: String? = null,
    @SerializedName("support_url") val supportUrl: String? = null,
    @SerializedName("bonusDaysApplied") val bonusDaysApplied: Boolean = false,
    @SerializedName("autoUpdateInterval") val autoUpdateInterval: Int? = null,
    @SerializedName("rawConfig") val rawConfig: String? = null,
    @SerializedName("configType") val configType: String? = null,
    @SerializedName("supportsJsonResponse") val supportsJsonResponse: Boolean? = null,
    @SerializedName("customName") val customName: String? = null,
    @SerializedName("originalName") val originalName: String? = null,
    @SerializedName("accountId") val accountId: String? = null,
    @SerializedName("numericId") val numericId: String? = null,
    @SerializedName("telegramId") val telegramId: String? = null,
    @SerializedName("brandLogo") val brandLogo: String? = null,
    @SerializedName("brandLogoCache") val brandLogoCache: String? = null,
    @SerializedName("themeSpec") val themeSpec: String? = null,
    @SerializedName("templates") val templates: List<SubscriptionTemplateCache> = emptyList()
) {
    val displayName: String get() = customName ?: username ?: name
    val usedTraffic: Long get() = uploadTotal + downloadTotal
    val isUnlimitedDevices: Boolean get() = deviceLimit <= 0
}

data class SubscriptionTemplateCache(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("templateType") val templateType: String,
    @SerializedName("viewPosition") val viewPosition: Int,
    @SerializedName("isDefault") val isDefault: Boolean = false,
    @SerializedName("config") val config: String? = null,
    @SerializedName("fetchedAtMs") val fetchedAtMs: Long? = null
)

@Immutable
data class SubscriptionProfileMetadata(
    val url: String,
    val name: String,
    val displayName: String,
    val serversCount: Int,
    val isLoading: Boolean,
    val error: String?,
    val uploadTotal: Long,
    val downloadTotal: Long,
    val totalTraffic: Long,
    val expireTime: Long,
    val deviceCount: Int,
    val deviceLimit: Int,
    val onlineDevices: Int,
    val announce: String?,
    val username: String?,
    val daysUntilExpiry: Long,
    val websiteUrl: String?,
    val supportUrl: String?,
    val customName: String?,
    val originalName: String?,
    val accountId: String?,
    val numericId: String?,
    val telegramId: String?,
    val brandLogo: String?,
    val brandLogoCache: String?
) {
    val usedTraffic: Long get() = uploadTotal + downloadTotal
    val isUnlimitedDevices: Boolean get() = deviceLimit <= 0
}

fun SubscriptionProfile.toMetadata(): SubscriptionProfileMetadata {
    return SubscriptionProfileMetadata(
        url = this.url,
        name = this.name,
        displayName = this.displayName,
        serversCount = this.servers.size,
        isLoading = this.isLoading,
        error = this.error,
        uploadTotal = this.uploadTotal,
        downloadTotal = this.downloadTotal,
        totalTraffic = this.totalTraffic,
        expireTime = this.expireTime,
        deviceCount = this.deviceCount,
        deviceLimit = this.deviceLimit,
        onlineDevices = this.onlineDevices,
        announce = this.announce,
        username = this.username,
        daysUntilExpiry = this.daysUntilExpiry,
        websiteUrl = this.websiteUrl,
        supportUrl = this.supportUrl,
        customName = this.customName,
        originalName = this.originalName,
        accountId = this.accountId,
        numericId = this.numericId,
        telegramId = this.telegramId,
        brandLogo = this.brandLogo,
        brandLogoCache = this.brandLogoCache
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    mainViewModel: MainViewModel,
    profiles: List<SubscriptionProfileMetadata>,
    onSubscriptionAdded: (String) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileRefresh: (String) -> Unit,
    onOpenServers: (SubscriptionProfileMetadata) -> Unit,
    onAddSheetVisibilityChange: (Boolean) -> Unit
) {
    var manualUrl by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = remember { PreferencesManager(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    var pinnedProfiles by remember { mutableStateOf(preferencesManager.getPinnedProfileUrls()) }
    val showSubscriptionLogo by preferencesManager.showSubscriptionLogoState

    val mergedProfiles = remember(profiles, pinnedProfiles) {
        val merged = LinkedHashMap<String, SubscriptionProfileMetadata>()
        fun score(profile: SubscriptionProfileMetadata): Int {
            val stableScore = if (!profile.isLoading) 1000 else 0
            val noErrorScore = if (profile.error.isNullOrBlank()) 200 else 0
            return profile.serversCount * 100 + stableScore + noErrorScore
        }

        profiles.forEach { profile ->
            val key = profile.url.trim().lowercase()
            val current = merged[key]
            if (current == null || score(profile) >= score(current)) {
                merged[key] = profile
            }
        }
        val mergedList = merged.values.toList()
        val originalOrder = mergedList.withIndex().associate { it.value.url.lowercase() to it.index }
        mergedList.sortedWith(
            compareByDescending<SubscriptionProfileMetadata> { pinnedProfiles.contains(it.url.trim().lowercase()) }
                .thenBy { originalOrder[it.url.lowercase()] ?: Int.MAX_VALUE }
        )
    }

    // Clipboard checking
    LaunchedEffect(showAddDialog, showManualDialog) {
        if (showAddDialog || showManualDialog) {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val primaryData = cm.primaryClip
                if (primaryData != null && primaryData.itemCount > 0) {
                    val text = primaryData.getItemAt(0).text?.toString()
                    // No check needed here if we're not using clipboardUrl anymore
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(showAddDialog, showQrScanner, showManualDialog) {
        onAddSheetVisibilityChange(showAddDialog || showQrScanner || showManualDialog)
    }

    val qrCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showQrScanner = true
        else scope.launch { snackbarHostState.showSnackbar("Требуется разрешение камеры") }
    }

    fun tryAddSubscription(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        onSubscriptionAdded(trimmed)
    }

    // QR logic moved below as overlay

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                GlassHeader(
                    title = "Профили",
                    subtitle = "${mergedProfiles.size} шт",
                    icon = Icons.Default.Folder,
                    iconColor = nebulaColors.accent
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !showAddDialog && !showManualDialog && !showQrScanner,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = nebulaColors.accent.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(bottom = 90.dp, end = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, "Добавить")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedGradientBackground()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ваши подписки",
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = nebulaColors.accent.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                nebulaColors.accent.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "${mergedProfiles.size}",
                                color = nebulaColors.accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (mergedProfiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    null,
                                    tint = nebulaColors.textTertiary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text("Нет подписок", color = nebulaColors.textPrimary)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(mergedProfiles, key = { it.url.lowercase() }) { profile ->
                                ProfileCard(
                                    mainViewModel = mainViewModel,
                                    profile = profile,
                                    isPinned = pinnedProfiles.contains(profile.url.trim().lowercase()),
                                    showSubscriptionLogo = showSubscriptionLogo,
                                    onDelete = { showDeleteDialogFor = profile.url },
                                    onRefresh = { onProfileRefresh(profile.url) },
                                    onOpenServers = { onOpenServers(profile) },
                                    onTogglePin = {
                                        val key = profile.url.trim().lowercase()
                                        val currentlyPinned = pinnedProfiles.contains(key)
                                        if (!currentlyPinned && pinnedProfiles.size >= 3) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Можно закрепить максимум 3 профиля")
                                            }
                                        } else {
                                            if (currentlyPinned) {
                                                preferencesManager.unpinProfile(profile.url)
                                            } else {
                                                preferencesManager.pinProfile(profile.url)
                                            }
                                            pinnedProfiles = preferencesManager.getPinnedProfileUrls()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Add Dialogs
            if (showAddDialog) {
                AddSubscriptionDialog(
                    onDismiss = { showAddDialog = false },
                    onUrlClick = {
                        showAddDialog = false
                        showManualDialog = true
                    },
                    onQrClick = {
                        showAddDialog = false
                        qrCodeLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            if (showManualDialog) {
                ManualInputDialog(
                    url = manualUrl,
                    onUrlChange = { manualUrl = it },
                    onDismiss = { showManualDialog = false },
                    onAdd = {
                        if (manualUrl.isNotBlank()) {
                            tryAddSubscription(manualUrl)
                            showManualDialog = false
                            manualUrl = ""
                        }
                    },
                    onPaste = {
                        try {
                            val cm =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) manualUrl = text
                        } catch (_: Exception) {
                        }
                    }
                )
            }

            showDeleteDialogFor?.let { profileUrl ->
                val profile = mergedProfiles.firstOrNull { it.url == profileUrl }
                DeleteProfileDialog(
                    profileName = profile?.displayName.orEmpty(),
                    serverCount = profile?.serversCount,
                    onDismissRequest = { showDeleteDialogFor = null },
                    onConfirm = {
                        onProfileDeleted(profileUrl)
                        showDeleteDialogFor = null
                    }
                )
            }
        }

        // QR Overlay
        AnimatedVisibility(
            visible = showQrScanner,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            if (showQrScanner) {
                QrScannerScreen(
                    onResult = { result ->
                        showQrScanner = false
                        tryAddSubscription(result)
                    },
                    onBack = { showQrScanner = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileCard(
    mainViewModel: MainViewModel,
    profile: SubscriptionProfileMetadata,
    isPinned: Boolean,
    showSubscriptionLogo: Boolean,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onOpenServers: () -> Unit,
    onTogglePin: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()
    val visibleServersCount = profile.serversCount
    val pingPulseScale = remember { Animatable(1f) }
    var menuExpanded by remember { mutableStateOf(false) }

    suspend fun performPingPulse() {
        repeat(3) {
            pingPulseScale.animateTo(1.3f, tween(600, easing = FastOutSlowInEasing))
            pingPulseScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    val updateRotation = remember { Animatable(0f) }
    LaunchedEffect(profile.isLoading) {
        if (profile.isLoading) {
            updateRotation.animateTo(
                targetValue = updateRotation.value + 360f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart)
            )
        } else {
            val current = updateRotation.value % 360f
            if (current > 0f) {
                updateRotation.animateTo(
                    targetValue = updateRotation.value + (360f - current),
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .combinedClickable(
                onClick = onOpenServers,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            nebulaColors.textPrimary.copy(alpha = 0.10f),
                            nebulaColors.textPrimary.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = nebulaColors.textTertiary)
                    Spacer(Modifier.width(8.dp))
                    val brandLogo = profile.brandLogo?.takeIf { it.isNotBlank() }
                    if (showSubscriptionLogo && brandLogo != null) {
                        SubscriptionBrandLogo(
                            logo = brandLogo,
                            cachedLogo = profile.brandLogoCache,
                            size = 30.dp
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.displayName, color = nebulaColors.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            if (isPinned) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Закреплено",
                                    tint = nebulaColors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (profile.customName != null && profile.originalName != null) {
                            Text("(${profile.originalName})", color = nebulaColors.textTertiary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$visibleServersCount серв.", color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassIconButton(
                            icon = Icons.Default.Speed,
                            color = nebulaColors.accent,
                            iconModifier = Modifier.scale(pingPulseScale.value)
                        ) {
                            scope.launch { performPingPulse() }
                            mainViewModel.pingAllServers()
                        }

                        GlassIconButton(
                            icon = Icons.Default.Refresh,
                            color = nebulaColors.accent,
                            iconModifier = Modifier.rotate(updateRotation.value),
                            onClick = onRefresh
                        )

                        GlassIconButton(
                            icon = Icons.Outlined.Delete,
                            color = Color(0xFFFF5252),
                            onClick = onDelete
                        )
                    }
                }
            }

            // Статистика подписки (Трафик и Устройства)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Трафик
                val used = formatBytes(profile.downloadTotal + profile.uploadTotal)
                val total = if (profile.totalTraffic > 0) formatBytes(profile.totalTraffic) else "∞"

                Surface(
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp),
                    color = nebulaColors.textPrimary.copy(alpha = 0.04f),
                    border = BorderStroke(0.5.dp, nebulaColors.textPrimary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDownload, null, tint = nebulaColors.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$used / $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = nebulaColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }

                // Устройства
                Surface(
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(12.dp),
                    color = nebulaColors.textPrimary.copy(alpha = 0.04f),
                    border = BorderStroke(0.5.dp, nebulaColors.textPrimary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Devices, null, tint = nebulaColors.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        val limitText = if (profile.isUnlimitedDevices) "∞" else "${profile.deviceLimit}"
                        Text(
                            "${profile.onlineDevices} шт / $limitText",
                            style = MaterialTheme.typography.labelSmall,
                            color = nebulaColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            val menuContentColor = if (nebulaColors.accent.luminance() > 0.55f) Color.Black else Color.White
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = nebulaColors.accent.copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, menuContentColor.copy(alpha = 0.18f))
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isPinned) "Открепить профиль" else "Закрепить профиль",
                            color = menuContentColor
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = menuContentColor
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = menuContentColor,
                        leadingIconColor = menuContentColor
                    )
                )
            }

            val safeError = sanitizeProfileErrorForUi(profile.error)
            if (safeError != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF5252).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(safeError, color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp).then(iconModifier))
        }
    }
}

@Composable
fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onUrlClick: () -> Unit,
    onQrClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = "Добавить подписку",
        description = "Выберите удобный способ добавления подписки",
        confirmButtonText = null,
        onConfirm = {}
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = {
                    onDismiss()
                    onQrClick()
                },
                shape = RoundedCornerShape(20.dp),
                color = nebulaColors.accent.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(nebulaColors.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = nebulaColors.accent)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Сканировать QR-код", style = MaterialTheme.typography.titleMedium, color = nebulaColors.textPrimary)
                        Text("Мгновенный импорт через камеру", style = MaterialTheme.typography.bodySmall, color = nebulaColors.textSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = nebulaColors.textTertiary)
                }
            }

            Surface(
                onClick = {
                    onDismiss()
                    onUrlClick()
                },
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Link, null, tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ввести ссылку вручную", style = MaterialTheme.typography.titleMedium, color = nebulaColors.textPrimary)
                        Text("Вставьте URL вашей подписки", style = MaterialTheme.typography.bodySmall, color = nebulaColors.textSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = nebulaColors.textTertiary)
                }
            }
        }
    }
}

@Composable
fun ManualInputDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = "Ввод ссылки",
        description = "Вставьте URL подписки из буфера обмена или введите вручную",
        confirmButtonText = "Добавить",
        onConfirm = {
            if (url.isNotBlank()) {
                onAdd()
            }
        }
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ссылка подписки", color = nebulaColors.textTertiary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = nebulaColors.accent,
                unfocusedBorderColor = nebulaColors.accent.copy(alpha = 0.2f),
                cursorColor = nebulaColors.accent,
                focusedTextColor = nebulaColors.textPrimary,
                unfocusedTextColor = nebulaColors.textPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                IconButton(onClick = onPaste) {
                    Icon(Icons.Default.ContentPaste, "Вставить", tint = nebulaColors.accent)
                }
            }
        )
    }
}
