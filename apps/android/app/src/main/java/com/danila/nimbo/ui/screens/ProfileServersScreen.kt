package com.danila.nimbo.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.PingManager
import com.danila.nimbo.network.PingProtocol
import com.danila.nimbo.ui.components.*
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.theme.*
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.filterServersForPolicies
import com.danila.nimbo.vpn.VpnManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileServersScreen(
    mainViewModel: com.danila.nimbo.MainViewModel,
    navController: NavController,
    profile: SubscriptionProfile,
    onServerSelected: (Server) -> Unit,
    onProfileRefresh: (String) -> Unit
) {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { com.danila.nimbo.utils.PreferencesManager(context) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Live state from ViewModel
    val profiles by mainViewModel.profilesState.collectAsState()
    val isPinging by mainViewModel.isPinging.collectAsState()
    val activePingKeys by mainViewModel.activePingKeys.collectAsState()
    
    // Find the current live profile to get updated server pings
    val currentProfile = remember(profiles, profile.url) { 
        profiles.find { it.url == profile.url } ?: profile 
    }
    
    val pullToRefreshState = rememberPullToRefreshState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocolFilters by rememberSaveable { mutableStateOf(setOf<String>()) }
    val autoBypassByNetwork = preferencesManager.autoBypassByNetwork
    val activeNetworkType by mainViewModel.activeNetworkType.collectAsState()
    val bsOnlyModeActive by mainViewModel.isBypassOnlyMode.collectAsState()
    val domainReachableForBypass by mainViewModel.isAutoBypassControlReachable.collectAsState()

    // Dialog States
    var showRenameDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(profile.customName ?: profile.originalName ?: profile.name) }
    var subscriptionExpanded by remember { mutableStateOf(preferencesManager.serverListSubscriptionExpanded) }

    val policyFilteredServers = remember(
        currentProfile.servers,
        autoBypassByNetwork,
        activeNetworkType,
        bsOnlyModeActive
    ) {
        filterServersForPolicies(
            servers = currentProfile.servers,
            autoBypassByNetwork = autoBypassByNetwork,
            networkType = activeNetworkType,
            shouldUseBypassOnly = bsOnlyModeActive,
            blockBypassWhenDomainReachable = autoBypassByNetwork &&
                domainReachableForBypass &&
                !bsOnlyModeActive
        )
    }
    
    // Derived ping map from the live profile servers
    val serverPings = remember(currentProfile.servers) {
        currentProfile.servers
            .groupBy { it.pingMeasurementKey() }
            .mapValues { (_, keyedServers) ->
                keyedServers.mapNotNull { it.ping?.takeIf { ping -> ping >= 0 } }.minOrNull() ?: -1
            }
    }

    val availableProtocolFilters = remember(policyFilteredServers) {
        policyFilteredServers
            .map { protocolFilterKey(it.protocol) }
            .distinct()
            .sortedWith(compareBy<String> { PROTOCOL_FILTER_ORDER.indexOf(it).let { idx -> if (idx == -1) Int.MAX_VALUE else idx } }.thenBy { it })
    }

    LaunchedEffect(availableProtocolFilters) {
        selectedProtocolFilters = selectedProtocolFilters.intersect(availableProtocolFilters.toSet())
    }

    val protocolFilteredServers = remember(policyFilteredServers, selectedProtocolFilters) {
        if (selectedProtocolFilters.isEmpty()) {
            policyFilteredServers
        } else {
            policyFilteredServers.filter { server ->
                selectedProtocolFilters.contains(protocolFilterKey(server.protocol))
            }
        }
    }

    val filteredServers = remember(protocolFilteredServers, searchQuery) {
        if (searchQuery.isBlank()) protocolFilteredServers
        else protocolFilteredServers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.host.contains(searchQuery, ignoreCase = true)
        }
    }

    // serverPings is now derived from the live profile above

    var sortOrder by remember { mutableStateOf(preferencesManager.serverSortOrder) }
    var pinnedServerKeys by remember { mutableStateOf(preferencesManager.getPinnedServerKeys()) }

    LaunchedEffect(sortOrder) {
        if (sortOrder == "PROTOCOL") {
            selectedProtocolFilters = preferencesManager.selectedSortProtocols
        }
    }

    val sortedServers = remember(filteredServers, sortOrder, serverPings, pinnedServerKeys) {
        val ordered = when (sortOrder) {
            "PING" -> filteredServers.sortedWith(compareBy<Server> { server ->
                val key = server.pingMeasurementKey()
                val ping = serverPings[key] ?: -1
                if (ping == -1) Int.MAX_VALUE else ping
            }.thenBy { it.name })

            "NAME" -> filteredServers.sortedBy { it.name }
            "PROTOCOL" -> filteredServers.sortedWith(compareBy<Server> { protocolFilterKey(it.protocol) }.thenBy { it.name })
            else -> filteredServers // "DEFAULT"
        }
        val (pinned, regular) = ordered.partition { pinnedServerKeys.contains(it.pingKey()) }
        pinned + regular
    }

    fun refreshCurrentProfileInSubscriptionOrder() {
        sortOrder = "DEFAULT"
        preferencesManager.serverSortOrder = "DEFAULT"
        mainViewModel.refreshSubscription(currentProfile.url)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    refreshCurrentProfileInSubscriptionOrder()
                    kotlinx.coroutines.delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ───── NEW PREMIUM HEADER CARD ─────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = 6.dp)
                            .statusBarsPadding()
                    ) {
                        val isUpdatingSubs by mainViewModel.isRefreshingSubscriptions.collectAsState()

                        // Back Button (Separate row for better UX)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(nebulaColors.textPrimary.copy(alpha = 0.08f))
                                    .clickable { navController.popBackStack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    null,
                                    tint = nebulaColors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            
                            // Profile Name
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentProfile.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = nebulaColors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentProfile.originalName != null && currentProfile.originalName != currentProfile.displayName && currentProfile.originalName != "Подписка") {
                                    Text(
                                        text = currentProfile.originalName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = nebulaColors.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.padding(start = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 🔥 Refresh & Ping Buttons moved here from card
                                TopBarMorphIcon(
                                    icon = Icons.Default.Refresh,
                                    color = nebulaColors.accent,
                                    onClick = { refreshCurrentProfileInSubscriptionOrder() },
                                    iconModifier = if (isUpdatingSubs) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "update_header")
                                        val rotation by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "rotation"
                                        )
                                        Modifier.rotate(rotation)
                                    } else Modifier
                                )

                                val pingPulseScale = remember { Animatable(1f) }
                                suspend fun performPingPulse() {
                                    repeat(3) {
                                        pingPulseScale.animateTo(1.3f, tween(600, easing = FastOutSlowInEasing))
                                        pingPulseScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                                    }
                                }

                                TopBarMorphIcon(
                                    icon = Icons.Default.Speed,
                                    color = nebulaColors.accent,
                                    iconModifier = Modifier.scale(pingPulseScale.value),
                                    onClick = {
                                        scope.launch { performPingPulse() }
                                        mainViewModel.pingAllServers()
                                    }
                                )
                            }
                        }
                        

                        val selectedServer = remember(currentProfile.servers, VpnManager.selectedServer) {
                            val active = VpnManager.selectedServer
                            if (active != null) {
                                currentProfile.servers.find { it.matchesSelection(active) }
                            } else {
                                null
                            }
                        }
                        val selectedServerPing = remember(selectedServer, serverPings) {
                            selectedServer?.let { serverPings[it.pingMeasurementKey()] ?: it.ping }
                        }

                        SubscriptionInfoCard(
                            profileName = currentProfile.displayName,
                            originalName = currentProfile.originalName,
                            subscriptionUrl = currentProfile.url,
                            announce = currentProfile.announce,
                            daysUntilExpiry = currentProfile.daysUntilExpiry,
                            usedTraffic = currentProfile.uploadTotal + currentProfile.downloadTotal,
                            totalTraffic = currentProfile.totalTraffic,
                            deviceLimit = currentProfile.deviceLimit,
                            onlineDevices = currentProfile.onlineDevices,
                            onDeviceClick = {
                                val encodedUrl = java.net.URLEncoder.encode(currentProfile.url, "UTF-8")
                                navController.navigate("device_management/$encodedUrl")
                            },
                            websiteUrl = currentProfile.websiteUrl,
                            supportUrl = currentProfile.supportUrl,
                            numericId = currentProfile.numericId,
                            selectedServer = selectedServer,
                            selectedServerPing = selectedServerPing,
                            pingDisplayMode = preferencesManager.pingDisplayModeState.value,
                            onRename = { 
                                renameValue = currentProfile.displayName
                                showRenameDialog = true 
                            },
                            onDelete = {
                                mainViewModel.removeSubscription(currentProfile.url)
                                navController.popBackStack()
                            },
                            onShowQr = { showQrDialog = true },
                            onRefresh = { refreshCurrentProfileInSubscriptionOrder() },
                            onPingAll = { mainViewModel.pingAllServers() },
                            isPinging = isPinging,
                            isUpdating = isUpdatingSubs,
                            showActions = false,
                            isExpanded = subscriptionExpanded,
                            onUpdateExpanded = {
                                subscriptionExpanded = it
                                preferencesManager.serverListSubscriptionExpanded = it
                            }
                        )
                        // ServerControlButtons removed: integrated into card header
                    }
                }

                // ───── SEARCH BAR ─────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            placeholder = {
                                Text(
                                    "Поиск серверов...",
                                    color = nebulaColors.textTertiary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    tint = LocalNebulaColors.current.accent
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            null,
                                            tint = nebulaColors.textTertiary
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = nebulaColors.textPrimary.copy(alpha = 0.05f),
                                unfocusedContainerColor = nebulaColors.textPrimary.copy(alpha = 0.03f),
                                focusedBorderColor = LocalNebulaColors.current.accent.copy(alpha = 0.5f),
                                unfocusedBorderColor = nebulaColors.textPrimary.copy(alpha = 0.1f),
                                cursorColor = LocalNebulaColors.current.accent,
                                focusedTextColor = nebulaColors.textPrimary,
                                unfocusedTextColor = nebulaColors.textPrimary
                            )
                        )
                    }
                }

                // ───── SORT TABS ─────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sorts = listOf(
                            "DEFAULT" to "По порядку",
                            "PING" to "По пингу",
                            "NAME" to "По имени",
                            "PROTOCOL" to "По протоколу"
                        )
                        sorts.forEach { (key, label) ->
                            val isSelected = sortOrder == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) LocalNebulaColors.current.accent else LocalNebulaColors.current.textPrimary.copy(
                                            alpha = 0.05f
                                        )
                                    )
                                    .clickable {
                                        sortOrder = key
                                        preferencesManager.serverSortOrder = key
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.White else LocalNebulaColors.current.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            val allSelected = selectedProtocolFilters.isEmpty()
                            ProtocolFilterChip(
                                label = "Все",
                                selected = allSelected,
                                onClick = { selectedProtocolFilters = emptySet() }
                            )
                        }
                        items(availableProtocolFilters, key = { it }) { protocol ->
                            ProtocolFilterChip(
                                label = protocol,
                                selected = selectedProtocolFilters.contains(protocol),
                                onClick = {
                                    selectedProtocolFilters = if (selectedProtocolFilters.contains(protocol)) {
                                        selectedProtocolFilters - protocol
                                    } else {
                                        selectedProtocolFilters + protocol
                                    }
                                }
                            )
                        }
                    }
                }


                // ───── EMPTY ─────
                if (profile.servers.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(top = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                null,
                                tint = nebulaColors.textTertiary,
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                "Нет серверов",
                                style = MaterialTheme.typography.titleMedium,
                                color = nebulaColors.textTertiary
                            )
                        }
                    }
                }

                // ───── LIST (Используем index для уникальности ключа и предотвращения крашей при дубликатах) ─────
                // ───── СЕРВЕРЫ ─────
                itemsIndexed(
                    sortedServers,
                    key = { index, server -> "${server.pingKey()}_$index" }
                ) { _, server ->

                    val key = server.pingMeasurementKey()
                    val ping = serverPings[key] ?: -1
                    val selectedServer = VpnManager.selectedServer
                    val isSelected = selectedServer?.matchesSelection(server) == true

                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ServerListItem(
                            server = server,
                            ping = ping,
                            isPinging = isPinging && activePingKeys.contains(key),
                            isPinned = pinnedServerKeys.contains(server.pingKey()),
                            showJsonBadge = currentProfile.supportsJsonResponse == true,
                            pingDisplayMode = preferencesManager.pingDisplayModeState.value,
                            isSelected = isSelected,
                            onPingClick = { mainViewModel.pingSingleServer(server) },
                            onTogglePin = {
                                val keyToToggle = server.pingKey()
                                val currentlyPinned = pinnedServerKeys.contains(keyToToggle)
                                if (!currentlyPinned && pinnedServerKeys.size >= 5) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Можно закрепить максимум 5 серверов")
                                    }
                                } else {
                                    if (currentlyPinned) {
                                        preferencesManager.unpinServer(keyToToggle)
                                    } else {
                                        preferencesManager.pinServer(keyToToggle)
                                    }
                                    pinnedServerKeys = preferencesManager.getPinnedServerKeys()
                                }
                            },
                            onClick = {
                                onServerSelected(server)
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }

        // ───── RENAME DIALOG ─────
        if (showRenameDialog) {
            NebulaMorphicDialog(
                onDismissRequest = { showRenameDialog = false },
                title = "Переименовать профиль",
                description = "Введите новое название для вашей подписки",
                confirmButtonText = "Сохранить",
                onConfirm = {
                    mainViewModel.renameProfile(profile.url, renameValue)
                    showRenameDialog = false
                }
            ) {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Новое имя", color = nebulaColors.textTertiary) },
                    singleLine = true,
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
        }

        // ───── QR DIALOG ─────
        if (showQrDialog) {
            QrCodeDisplayBottomSheet(
                url = profile.url,
                onDismiss = { showQrDialog = false }
            )
        }
    }
}

@Composable
private fun ProtocolFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) nebulaColors.accent.copy(alpha = 0.22f)
                else nebulaColors.textPrimary.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (selected) nebulaColors.accent.copy(alpha = 0.48f)
                else nebulaColors.textPrimary.copy(alpha = 0.14f),
                RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) nebulaColors.textPrimary else nebulaColors.textSecondary
        )
    }
}

private val PROTOCOL_FILTER_ORDER = listOf(
    "VLESS",
    "VMess",
    "Trojan",
    "Shadowsocks",
    "Hysteria2",
    "TUIC",
    "Reality",
    "Other"
)

private fun protocolFilterKey(protocolRaw: String?): String {
    val protocol = protocolRaw.orEmpty().lowercase()
    return when {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ServerListItem(
    server: Server,
    ping: Int?,
    isPinging: Boolean,
    isPinned: Boolean,
    showJsonBadge: Boolean,
    pingDisplayMode: Int,
    onPingClick: () -> Unit,
    onTogglePin: () -> Unit,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val flagEmoji = extractFlagEmoji(server.name)
    val serverName = cleanServerName(server.name).ifBlank { "Сервер" }
    var menuExpanded by remember { mutableStateOf(false) }

    // Получаем информацию о протоколе и транспорте
    val protocolInfo = buildProtocolInfo(server, showJsonBadge)
    val materialYou = nebulaColors.isMaterialYou

    val fillBrush = when {
        isSelected && materialYou -> null
        isSelected -> Brush.linearGradient(
            colors = listOf<Color>(
                nebulaColors.accent.copy(alpha = 0.20f),
                nebulaColors.accent.copy(alpha = 0.05f)
            )
        )
        materialYou -> null
        else -> Brush.linearGradient(
            colors = listOf<Color>(
                nebulaColors.textPrimary.copy(alpha = 0.08f),
                nebulaColors.textPrimary.copy(alpha = 0.02f)
            )
        )
    }
    val fillColor = when {
        isSelected && materialYou -> MaterialTheme.colorScheme.primaryContainer
        materialYou -> MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Transparent
    }
    val borderStrokeColor = when {
        materialYou -> Color.Transparent
        isSelected -> nebulaColors.accent.copy(alpha = 0.6f)
        else -> nebulaColors.textPrimary.copy(alpha = 0.12f)
    }

    val titleTextColor = when {
        isSelected && materialYou -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> nebulaColors.textPrimary
    }
    val subtitleTextColor = when {
        isSelected && materialYou -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        else -> nebulaColors.textSecondary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(fillColor)
                .then(if (fillBrush != null) Modifier.background(fillBrush) else Modifier)
                .border(1.dp, borderStrokeColor, RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
        ) {

            // 🔥 glow слой (морфизм) - отключаем для Material You
            if (!materialYou) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf<Color>(
                                    if (isSelected) nebulaColors.accent.copy(alpha = 0.20f) else nebulaColors.accent.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                radius = 600f
                            ),
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 86.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected && materialYou) {
                                        Brush.radialGradient(
                                            colors = listOf<Color>(
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                                                Color.Transparent
                                            )
                                        )
                                    } else {
                                        Brush.radialGradient(
                                            colors = listOf<Color>(
                                                nebulaColors.accent.copy(alpha = 0.25f),
                                                Color.Transparent
                                            )
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (flagEmoji.isNotEmpty()) {
                                Text(flagEmoji)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (isSelected && materialYou) MaterialTheme.colorScheme.onPrimaryContainer else nebulaColors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = serverName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = titleTextColor,
                                    maxLines = 2
                                )
                                if (isPinned) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Закреплено",
                                        tint = if (isSelected && materialYou) MaterialTheme.colorScheme.onPrimaryContainer else nebulaColors.accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Протокол и транспорт (Morph Blocks)
                            if (protocolInfo.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    protocolInfo.forEach { info ->
                                        val isJsonBadge = info == "JSON"
                                        val badgeBg = when (info) {
                                            "JSON" -> Color(0x33FFC107)
                                            else -> if (isSelected && materialYou) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f) else nebulaColors.accent.copy(alpha = 0.12f)
                                        }
                                        val badgeBorder = when (info) {
                                            "JSON" -> Color(0x66FFC107)
                                            else -> if (isSelected && materialYou) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f) else nebulaColors.accent.copy(alpha = 0.28f)
                                        }
                                        val badgeText = when (info) {
                                            "JSON" -> Color(0xFFFFD54F)
                                            else -> if (isSelected && materialYou) MaterialTheme.colorScheme.onPrimaryContainer else nebulaColors.accent
                                        }
                                        Box(
                                            modifier = Modifier
                                                .widthIn(
                                                    min = if (isJsonBadge) 44.dp else 0.dp,
                                                    max = if (isJsonBadge) 74.dp else 220.dp
                                                )
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeBg)
                                                .border(
                                                    0.5.dp,
                                                    badgeBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = info,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeText,
                                                fontWeight = if (isJsonBadge) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        PingBoundaryBadge(
            ping = ping ?: -1,
            isPinging = isPinging,
            pingDisplayMode = pingDisplayMode,
            onClick = onPingClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-10).dp)
                .padding(end = 10.dp)
        )

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
                        if (isPinned) "Открепить сервер" else "Закрепить сервер",
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
    }
}

@Composable
private fun AnimatedPingIndicator(
    pingColor: Color,
    pingDisplayMode: Int,
    label: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "server_ping_badge_animated")
    val loadingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ping_loading_alpha"
    )
    val loadingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ping_loading_rotation"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ping_glow_pulse"
    )

    val badgeShape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .alpha(loadingAlpha)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(1.18f)
                .clip(badgeShape)
                .background(pingColor.copy(alpha = glowPulse))
                .blur(10.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(badgeShape)
                .background(nebulaColors.surface.copy(alpha = 0.80f))
        )
        Row(
            modifier = Modifier
                .clip(badgeShape)
                .background(nebulaColors.surface.copy(alpha = 0.68f))
                .background(pingColor.copy(alpha = glowPulse))
                .border(1.dp, pingColor.copy(alpha = 0.58f), badgeShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Пинг",
                tint = pingColor,
                modifier = Modifier
                    .size(13.dp)
                    .rotate(loadingRotation)
            )
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) { it / 2 } + fadeIn(tween(220))).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) { -it / 2 } + fadeOut(tween(160))
                    ).using(SizeTransform(clip = false))
                },
                label = "ping_label"
            ) { current ->
                Text(
                    text = current,
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

@Composable
private fun StaticPingIndicator(
    pingColor: Color,
    pingDisplayMode: Int,
    label: String,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val badgeShape = RoundedCornerShape(999.dp)
    
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(1.18f)
                .clip(badgeShape)
                .background(pingColor.copy(alpha = 0.24f))
                .blur(10.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(badgeShape)
                .background(nebulaColors.surface.copy(alpha = 0.80f))
        )
        Row(
            modifier = Modifier
                .clip(badgeShape)
                .background(nebulaColors.surface.copy(alpha = 0.68f))
                .background(pingColor.copy(alpha = 0.18f))
                .border(1.dp, pingColor.copy(alpha = 0.42f), badgeShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Пинг",
                tint = pingColor,
                modifier = Modifier.size(13.dp)
            )
            if (pingDisplayMode == 2) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(pingColor)
                )
            } else {
                Text(
                    text = label,
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

@Composable
private fun PingBoundaryBadge(
    ping: Int,
    isPinging: Boolean,
    pingDisplayMode: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val showLoading = isPinging && ping == -1

    val targetColor = when {
        showLoading -> nebulaColors.textTertiary
        ping == -1 -> nebulaColors.statusDisconnected
        ping <= 70 -> nebulaColors.statusConnected
        ping <= 120 -> Color(0xFFCDDC39)
        ping <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusDisconnected
    }
    val pingColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "ping_color"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "ping_press_scale"
    )

    val label = when (pingDisplayMode) {
        1 -> if (ping == -1 && !showLoading) "нет" else "ok"
        2 -> ""
        else -> if (showLoading) "···" else if (ping == -1) "н/д" else "${ping}мс"
    }

    Box(
        modifier = modifier.scale(pressScale)
    ) {
        if (showLoading) {
            AnimatedPingIndicator(
                pingColor = pingColor,
                pingDisplayMode = pingDisplayMode,
                label = label,
                onClick = onClick
            )
        } else {
            StaticPingIndicator(
                pingColor = pingColor,
                pingDisplayMode = pingDisplayMode,
                label = label,
                onClick = onClick
            )
        }
    }
}


/**
 * Строит список информации о протоколе, транспорте и безопасности
 */
private fun buildProtocolInfo(server: Server, showJsonBadge: Boolean): List<String> {
    val result = mutableListOf<String>()
    val serverDescription = server.serverDescription
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    val protocol = server.protocol.lowercase()
    val protocolLabel = when {
        protocol.contains("vless") -> "VLESS"
        protocol.contains("vmess") -> "VMess"
        protocol.contains("trojan") -> "Trojan"
        protocol.contains("shadowsocks") || protocol.contains("ss") -> "Shadowsocks"
        protocol.contains("hysteria") || protocol == "hy2" -> "Hysteria2"
        protocol.contains("tuic") -> "TUIC"
        protocol.contains("reality") -> "Reality"
        protocol.isNotEmpty() -> protocol.replaceFirstChar { it.uppercase() }
        else -> null
    }

    serverDescription?.let { result.add(it) }
    protocolLabel?.let { result.add(it) }
    val descriptionLower = serverDescription?.lowercase()

    // Транспорт
    server.network?.lowercase()?.let { network ->
        val networkLabel: String? = when (network) {
            "grpc" -> "gRPC"
            "ws" -> "WebSocket"
            "xhttp", "splithttp" -> "XHTTP"
            "httpupgrade", "http-upgrade" -> "HTTP Upgrade"
            "http" -> "HTTP"
            "h2" -> "HTTP/2"
            "quic" -> "QUIC"
            "tcp" -> null // TCP не показываем, это по умолчанию
            else -> network.takeIf { it.isNotEmpty() }
        }
        if (networkLabel != null) {
            val duplicatedByDescription = descriptionLower?.contains(networkLabel.lowercase()) == true
            if (!duplicatedByDescription) result.add(networkLabel)
        }
    }

    // Безопасность
    server.security?.lowercase()?.let { security ->
        when (security) {
            "reality" -> {
                if (!result.contains("Reality")) result.add("Reality")
            }

            "tls" -> result.add("TLS")
            "none" -> {}
            else -> if (security.isNotEmpty() && !result.contains(security.replaceFirstChar { it.uppercase() })) {
                result.add(security.replaceFirstChar { it.uppercase() })
            }
        }
    }

    if (showJsonBadge) result.add("JSON")
    return result.distinctBy { it.lowercase() }
}
