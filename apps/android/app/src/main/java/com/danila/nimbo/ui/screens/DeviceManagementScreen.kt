package com.danila.nimbo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.MainViewModel
import com.danila.nimbo.network.RemnawaveDevice
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.theme.LocalNebulaColors
import java.text.SimpleDateFormat
import java.util.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(
    mainViewModel: MainViewModel,
    subscriptionUrl: String,
    onNavigateBack: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val profiles by mainViewModel.profilesState.collectAsState()
    val devices by mainViewModel.devicesState.collectAsState()
    val isRefreshing by mainViewModel.isRefreshingDevices.collectAsState()

    val profile = remember(profiles, subscriptionUrl) { profiles.find { it.url == subscriptionUrl } }
    val deviceLimit = profile?.deviceLimit ?: 0
    val onlineCount = devices.size

    var showRenameDialog by remember { mutableStateOf<RemnawaveDevice?>(null) }
    var showDeleteDialog by remember { mutableStateOf<RemnawaveDevice?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // Множественный выбор и режим
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    LaunchedEffect(subscriptionUrl) {
        mainViewModel.loadDevices(subscriptionUrl)
    }

    // Автоматический выход из режима выбора, если список пуст
    LaunchedEffect(devices) {
        if (devices.isEmpty()) {
            isSelectionMode = false
            selectedIds.clear()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            GlassHeader(
                title = "Устройства",
                subtitle = if (isSelectionMode) "Выбрано: ${selectedIds.size}" else {
                    if (deviceLimit > 0) "$onlineCount шт / $deviceLimit"
                    else "$onlineCount шт / ∞"
                },
                icon = Icons.Default.Devices,
                iconColor = nebulaColors.accent,
                onBack = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedIds.clear()
                    } else {
                        onNavigateBack()
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ───── REFINED MORPHIC BUTTONS ─────

                        // Кнопка "Выбрать всё / Снять выбор" (Появляется в режиме выбора)
                        AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = slideInHorizontally { it } + fadeIn(),
                            exit = slideOutHorizontally { it } + fadeOut()
                        ) {
                            val allSelected = selectedIds.size == devices.distinctBy { it.id }.size
                            MorphicHeaderButton(
                                icon = if (allSelected) Icons.Default.CheckBoxOutlineBlank else Icons.Default.SelectAll,
                                tint = nebulaColors.accent,
                                onClick = {
                                    if (allSelected) {
                                        selectedIds.clear()
                                    } else {
                                        selectedIds.clear()
                                        selectedIds.addAll(devices.map { it.id })
                                    }
                                }
                            )
                        }

                        // Основная кнопка (Красная) - Удаление или вход в режим
                        MorphicHeaderButton(
                            icon = if (isSelectionMode && selectedIds.isNotEmpty()) Icons.Default.DeleteSweep else Icons.Default.Delete,
                            color = Color(0xFFFF5252),
                            onClick = {
                                if (isSelectionMode) {
                                    if (selectedIds.isNotEmpty()) {
                                        showDeleteSelectedDialog = true
                                    } else {
                                        showDeleteAllDialog = true
                                    }
                                } else {
                                    isSelectionMode = true
                                }
                            }
                        )

                        // Универсальная кнопка "Закрыть режим" или "Обновить"
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = isSelectionMode,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "mode_switch"
                            ) { targetSelectionMode ->
                                if (targetSelectionMode) {
                                    MorphicHeaderButton(
                                        icon = Icons.Default.Close,
                                        onClick = {
                                            isSelectionMode = false
                                            selectedIds.clear()
                                        }
                                    )
                                } else {
                                    MorphicHeaderButton(
                                        icon = Icons.Default.Refresh,
                                        isLoading = isRefreshing,
                                        onClick = { mainViewModel.loadDevices(subscriptionUrl) }
                                    )
                                }
                            }
                        }
                    }
                }
            )

            if (isRefreshing && devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveCircularLoader(color = nebulaColors.accent)
                }
            } else if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Dns,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = nebulaColors.textTertiary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Устройства не найдены",
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Они появятся здесь после подключения",
                            color = nebulaColors.textTertiary.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    mainViewModel.reorderDevices(from.index, to.index)
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Фильтруем дубликаты по ID для безопасности (защита от краша LazyColumn)
                    val uniqueDevices = devices.distinctBy { it.id }
                    val customNames = mainViewModel.preferencesManager.getCustomDeviceNames()

                    items(uniqueDevices, key = { it.id }) { device ->
                        ReorderableItem(reorderableState, key = device.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                            val isSelected = selectedIds.contains(device.id)
                            DeviceItem(
                                device = device,
                                isCustomName = customNames.containsKey(device.id),
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                isDragging = isDragging,
                                modifier = with(this) {
                                    Modifier
                                        .shadow(elevation, RoundedCornerShape(20.dp))
                                        .longPressDraggableHandle(
                                            enabled = !isSelectionMode,
                                            onDragStarted = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        )
                                },
                                onToggleSelect = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedIds.remove(device.id)
                                        else selectedIds.add(device.id)
                                    } else {
                                        // Обычное поведение (например, детали, если были бы)
                                    }
                                },
                                onRename = {
                                    newName = device.name
                                    showRenameDialog = device
                                },
                                onDelete = { showDeleteDialog = device }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }

    // Dialogs
    if (showRenameDialog != null) {
        NebulaMorphicDialog(
            onDismissRequest = { showRenameDialog = null },
            title = "Переименовать устройство",
            description = "Введите новое название для этого устройства",
            confirmButtonText = "Сохранить",
            onConfirm = {
                showRenameDialog?.let { device ->
                    mainViewModel.renameDevice(subscriptionUrl, device.id, device.hwid, newName)
                }
                showRenameDialog = null
            }
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Мой телефон", color = nebulaColors.textTertiary) },
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

    if (showDeleteDialog != null) {
        NebulaMorphicDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = "Удалить устройство?",
            description = "Это действие отвяжет HWID устройства от вашей подписки. Устройство потеряет доступ к серверам до следующей авторизации.",
            confirmButtonText = "Удалить",
            confirmButtonColor = Color(0xFFFF5252),
            onConfirm = {
                showDeleteDialog?.let { mainViewModel.deleteDevice(subscriptionUrl, it) }
                showDeleteDialog = null
            }
        )
    }

    if (showDeleteAllDialog) {
        NebulaMorphicDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = "Удалить все устройства?",
            description = "Это действие БЕЗВОЗВРАТНО отвяжет ВСЕ устройства от этой подписки. Вам придется авторизоваться заново на каждом из них.",
            confirmButtonText = "Удалить всё",
            confirmButtonColor = Color(0xFFFF5252),
            onConfirm = {
                mainViewModel.deleteDevices(subscriptionUrl, devices)
                selectedIds.clear()
                showDeleteAllDialog = false
            }
        )
    }

    if (showDeleteSelectedDialog) {
        NebulaMorphicDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = "Удалить выбранные?",
            description = "Будет удалено ${selectedIds.size} устройств(а). Они потеряют доступ к серверам.",
            confirmButtonText = "Удалить",
            confirmButtonColor = Color(0xFFFF5252),
            onConfirm = {
                val toDelete = devices.filter { selectedIds.contains(it.id) }
                mainViewModel.deleteDevices(subscriptionUrl, toDelete)
                selectedIds.clear()
                showDeleteSelectedDialog = false
            }
        )
    }
}

@Composable
fun DeviceItem(
    device: com.danila.nimbo.network.RemnawaveDevice,
    isCustomName: Boolean = false,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val lastSeen = device.lastSeenAt?.let { formatTimestamp(it) } ?: "никогда"

    // Определение иконки устройства
    val deviceIcon = remember(device.name) {
        val name = device.name.lowercase()
        when {
            name.contains("windows") || name.contains("macos") || name.contains("linux") ||
            name.contains("pc") || name.contains("desktop") || name.contains("macbook") -> Icons.Default.Computer
            name.contains("android") || name.contains("iphone") || name.contains("ios") ||
            name.contains("mobile") || name.contains("phone") || name.contains("ipad") -> Icons.Default.Smartphone
            else -> Icons.Default.Devices
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isDragging -> nebulaColors.accent.copy(alpha = 0.28f)
                    isSelected -> nebulaColors.accent.copy(alpha = 0.22f)
                    device.isCurrent -> nebulaColors.accent.copy(alpha = 0.12f)
                    else -> nebulaColors.textPrimary.copy(alpha = 0.04f)
                }
            )
            .border(
                when {
                    isDragging -> 2.dp
                    isSelected || device.isCurrent -> 1.5.dp
                    else -> 1.dp
                },
                when {
                    isDragging -> Brush.verticalGradient(listOf(nebulaColors.accent, nebulaColors.accent.copy(alpha = 0.6f)))
                    isSelected || device.isCurrent -> Brush.verticalGradient(listOf(nebulaColors.accent.copy(alpha = 0.5f), nebulaColors.accent.copy(alpha = 0.15f)))
                    else -> Brush.verticalGradient(listOf(nebulaColors.textPrimary.copy(alpha = 0.1f), Color.Transparent))
                },
                RoundedCornerShape(20.dp)
            )
            .clickable { onToggleSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Morphic Checkbox Appearance with smooth horizontal expansion
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = expandHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    expandFrom = Alignment.Start
                ) + fadeIn(),
                exit = shrinkHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    shrinkTowards = Alignment.Start
                ) + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorphicCheckbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(Modifier.width(14.dp))
                }
            }

            // Icon with pulse for current
            Box(contentAlignment = Alignment.Center) {
                if (device.isCurrent) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            .background(nebulaColors.accent.copy(alpha = pulseAlpha), RoundedCornerShape(12.dp))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            when {
                                isSelected || isDragging -> Color.Transparent // Do not layer backgrounds
                                device.isCurrent -> nebulaColors.accent.copy(alpha = 0.15f)
                                else -> nebulaColors.textPrimary.copy(alpha = 0.08f)
                            },
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        deviceIcon,
                        null,
                        tint = if (device.isCurrent) nebulaColors.accent else nebulaColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Information Column with stable weight to prevent Jitter
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.name,
                        color = nebulaColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isCustomName) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = nebulaColors.accent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                nebulaColors.accent.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                "ИЗМЕНЕНО",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = nebulaColors.accent
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    "Активен: $lastSeen",
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )

                if (device.hwid != null) {
                    Text(
                        "HWID: ${device.hwid.take(12)}...",
                        color = nebulaColors.textTertiary.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            if (device.isCurrent) {
                Surface(
                    color = nebulaColors.accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, nebulaColors.accent.copy(alpha = 0.4f))
                ) {
                    Text(
                        "ТЕКУЩЕЕ",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = nebulaColors.accent,
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MorphicSmallButton(Icons.Outlined.Edit, onRename, nebulaColors.accent)
                MorphicSmallButton(Icons.Outlined.Delete, onDelete, Color(0xFFFF5252).copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
fun MorphicSmallButton(icon: ImageVector, onClick: () -> Unit, tint: Color) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = nebulaColors.textPrimary.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(listOf(nebulaColors.textPrimary.copy(alpha = 0.15f), Color.Transparent))
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    // 🔥 FIX: RemnawaveApiClient already returns milliseconds, so we REMOVE * 1000L
    return sdf.format(Date(timestamp))
}

@Composable
fun MorphicCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val checkProgress by animateFloatAsState(if (checked) 1f else 0f)

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (checked) nebulaColors.accent.copy(alpha = 0.15f)
                else nebulaColors.textPrimary.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (checked) nebulaColors.accent.copy(alpha = 0.5f)
                else nebulaColors.textPrimary.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                null,
                tint = nebulaColors.accent,
                modifier = Modifier.size(16.dp).graphicsLayer(scaleX = checkProgress, scaleY = checkProgress)
            )
        }
    }
}

@Composable
fun MorphicHeaderButton(
    icon: ImageVector,
    tint: Color? = null,
    color: Color? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val rotation by animateFloatAsState(
        targetValue = if (isLoading) 3600f else 0f,
        animationSpec = if (isLoading) infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ) else tween(0)
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = nebulaColors.textPrimary.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(listOf(nebulaColors.textPrimary.copy(alpha = 0.2f), Color.Transparent))
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                null,
                tint = color ?: tint ?: nebulaColors.textPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer(rotationZ = if (icon == Icons.Default.Refresh) rotation else 0f)
            )
        }
    }
}
