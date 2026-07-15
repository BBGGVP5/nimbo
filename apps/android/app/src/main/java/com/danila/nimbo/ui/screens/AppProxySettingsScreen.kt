package com.danila.nimbo.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.components.NebulaInputField
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.*
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var cachedApps: List<AppInfo>? = null

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.Bitmap,
    val isSystemApp: Boolean,
    val searchName: String = appName.lowercase(),
    val searchPackage: String = packageName.lowercase()
)

private suspend fun loadInstalledApps(
    packageManager: PackageManager,
    ownPackageName: String
): List<AppInfo> = withContext(Dispatchers.IO) {
    val installedApps = runCatching {
        packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
    }.getOrElse { emptyList() }

    installedApps.mapNotNull { packageInfo ->
        runCatching {
            val appInfo = packageInfo.applicationInfo ?: return@runCatching null
            if (packageInfo.packageName == ownPackageName) return@runCatching null
            AppInfo(
                packageName = packageInfo.packageName,
                appName = appInfo.loadLabel(packageManager).toString(),
                icon = appInfo.loadIcon(packageManager).toBitmap(),
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.getOrNull()
    }.sortedBy { it.appName.lowercase() }
}

private enum class AppFilterMode { All, Enabled, Disabled }
private enum class AppSortMode { Default, Az, Za }

private val NebulaColors.appsLight: Boolean
    get() = background.luminance() > 0.5f

private fun appPanelFill(colors: NebulaColors): Color = colors.panelFill

private fun appControlFill(colors: NebulaColors): Color = colors.controlFill

private fun appSoftFill(colors: NebulaColors): Color = colors.softFill

private fun appBorder(colors: NebulaColors, darkAlpha: Float = 0.10f): Color = colors.panelBorder

private fun appDivider(colors: NebulaColors): Color = colors.divider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProxySettingsScreen(
    onNavigateBack: () -> Unit,
    showBack: Boolean = true
) {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = remember { PreferencesManager(context) }
    val packageManager = context.packageManager
    var proxyMode by remember { mutableIntStateOf(preferencesManager.proxyByApp.coerceIn(0, 2).takeIf { it != 0 } ?: 1) }
    var bypassList by remember { mutableStateOf(preferencesManager.getAppBypassList()) }
    var vpnOnlyList by remember { mutableStateOf(preferencesManager.getAppVpnOnlyList()) }
    var customRuleIcons by remember { mutableStateOf(preferencesManager.getCustomRuleIcons()) }

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf(AppFilterMode.All) }
    var sortMode by rememberSaveable { mutableStateOf(AppSortMode.Default) }
    var showAddDialog by remember { mutableStateOf(false) }

    var allApps by remember { mutableStateOf<List<AppInfo>>(cachedApps ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedApps == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()

    fun refreshAppsList() {
        scope.launch {
            isRefreshing = true
            cachedApps = null
            val apps = loadInstalledApps(packageManager, context.packageName)
            cachedApps = apps
            allApps = apps
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        cachedApps?.let { cached ->
            allApps = cached
            isLoading = false
            return@LaunchedEffect
        }
        val apps = loadInstalledApps(packageManager, context.packageName)
        cachedApps = apps
        allApps = apps
        isLoading = false
    }

    val selectedSet = if (proxyMode == 2) vpnOnlyList else bypassList
    val installedPackageNames = remember(allApps) {
        allApps.mapTo(HashSet()) { it.packageName }
    }
    // Manually-added rules (domains / package names) live in the selected set but
    // aren't installed apps, so surface them as their own rows.
    val customEntries = remember(selectedSet, installedPackageNames, searchQuery) {
        val query = searchQuery.lowercase()
        selectedSet.filter { it !in installedPackageNames && it.lowercase().contains(query) }.sorted()
    }
    val filteredApps = remember(allApps, searchQuery, filterMode, sortMode, selectedSet) {
        val query = searchQuery.lowercase()
        val filtered = allApps.filter {
            val matchesQuery = it.searchName.contains(query) || it.searchPackage.contains(query)
            val enabled = selectedSet.contains(it.packageName)
            val matchesFilter = when (filterMode) {
                AppFilterMode.All -> true
                AppFilterMode.Enabled -> enabled
                AppFilterMode.Disabled -> !enabled
            }
            matchesQuery && matchesFilter
        }
        when (sortMode) {
            AppSortMode.Default -> filtered
            AppSortMode.Az -> filtered.sortedBy { it.appName.lowercase() }
            AppSortMode.Za -> filtered.sortedByDescending { it.appName.lowercase() }
        }
    }

    LaunchedEffect(proxyMode) {
        preferencesManager.proxyByApp = proxyMode
    }
    LaunchedEffect(bypassList) {
        preferencesManager.setAppBypassList(bypassList)
    }
    LaunchedEffect(vpnOnlyList) {
        preferencesManager.setAppVpnOnlyList(vpnOnlyList)
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { refreshAppsList() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = if (showBack) 12.dp else 10.dp),
            contentPadding = PaddingValues(bottom = 118.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = t("Назад", "Back"),
                            tint = nebulaColors.textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = t("Маршрутизация по приложениям", "App routing"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowsModeButton(
                    active = proxyMode == 1,
                    text = t("В обход для выбранных", "Bypass selected"),
                    modifier = Modifier.weight(1f),
                    onClick = { proxyMode = 1 }
                )
                WindowsModeButton(
                    active = proxyMode == 2,
                    text = t("Через VPN для выбранных", "VPN for selected"),
                    modifier = Modifier.weight(1f),
                    onClick = { proxyMode = 2 }
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = if (proxyMode == 2) {
                    t("Выбранные приложения будут идти через VPN.", "Selected apps will go through VPN.")
                } else {
                    t("Выбранные приложения будут идти напрямую, минуя VPN.", "Selected apps will go directly, bypassing VPN.")
                },
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (android.os.Build.VERSION.SDK_INT >= 37) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (nebulaColors.isMaterialYou) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            nebulaColors.accent.copy(alpha = 0.08f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (nebulaColors.isMaterialYou) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            nebulaColors.accent.copy(alpha = 0.20f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = t("Системное исключение приложений (Android 17)", "System App Exclusion (Android 17)"),
                                color = if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.onPrimaryContainer else nebulaColors.textPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = t(
                                "На Android 17+ вы можете настроить исключения приложений через стандартный интерфейс системы.",
                                "On Android 17+, you can also configure app exclusions using the system-managed VPN settings."
                            ),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        val failedToOpenSettingsMsg = t("Не удалось открыть настройки", "Failed to open settings")
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = android.content.Intent("android.settings.VPN_APP_EXCLUSION_SETTINGS").apply {
                                        putExtra(android.content.Intent.EXTRA_PACKAGE_NAME, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    android.widget.Toast.makeText(
                                        context,
                                        failedToOpenSettingsMsg,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = t("Открыть настройки", "Open Settings"),
                                color = if (nebulaColors.isMaterialYou) MaterialTheme.colorScheme.onPrimary else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val appSearchHeight = 64.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WindowsAppSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(appSearchHeight)
                )
                WindowsAppAddButton(
                    modifier = Modifier
                        .size(appSearchHeight),
                    onClick = { showAddDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))
            val subNoRulesMsg = t("В подписке нет правил приложений", "No app rules in subscription")
            val subAllAddedMsg = t("Все правила уже добавлены", "All rules already added")
            val subLoadedPrefix = t("Загружено правил: ", "Loaded rules: ")
            WindowsAppActionButton(
                icon = Icons.Default.CloudDownload,
                label = t("Загрузить из подписки", "Load from subscription"),
                modifier = Modifier.fillMaxWidth()
            ) {
                val direct = preferencesManager.getSubscriptionAppDirectList()
                val proxy = preferencesManager.getSubscriptionAppProxyList()
                if (direct.isEmpty() && proxy.isEmpty()) {
                    android.widget.Toast.makeText(context, subNoRulesMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val beforeBypass = bypassList.size
                    val beforeVpn = vpnOnlyList.size
                    bypassList = bypassList + direct
                    vpnOnlyList = vpnOnlyList + proxy
                    val added = (bypassList.size - beforeBypass) + (vpnOnlyList.size - beforeVpn)
                    val msg = if (added > 0) "$subLoadedPrefix$added" else subAllAddedMsg
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WindowsAppFilterChip(t("Все", "All"), filterMode == AppFilterMode.All) { filterMode = AppFilterMode.All }
                WindowsAppFilterChip(t("Включённые", "Enabled"), filterMode == AppFilterMode.Enabled) { filterMode = AppFilterMode.Enabled }
                WindowsAppFilterChip(t("Выключенные", "Disabled"), filterMode == AppFilterMode.Disabled) { filterMode = AppFilterMode.Disabled }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val sortLabel = when (sortMode) {
                    AppSortMode.Default -> t("По умолчанию", "Default")
                    AppSortMode.Az -> "A-Z"
                    AppSortMode.Za -> "Z-A"
                }
                WindowsAppActionButton(
                    icon = Icons.Default.Sort,
                    label = sortLabel,
                    onClick = {
                        sortMode = when (sortMode) {
                            AppSortMode.Default -> AppSortMode.Az
                            AppSortMode.Az -> AppSortMode.Za
                            AppSortMode.Za -> AppSortMode.Default
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = t("Выбрано ${selectedSet.size} из ${allApps.size}", "Selected ${selectedSet.size} of ${allApps.size}"),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
        }

        if (customEntries.isNotEmpty()) {
            itemsIndexed(
                items = customEntries,
                key = { _, entry -> "custom:$entry" }
            ) { index, entry ->
                val isFirst = index == 0
                val isLast = index == customEntries.lastIndex
                val rowShape = RoundedCornerShape(
                    topStart = if (isFirst) 18.dp else 0.dp,
                    topEnd = if (isFirst) 18.dp else 0.dp,
                    bottomStart = if (isLast) 18.dp else 0.dp,
                    bottomEnd = if (isLast) 18.dp else 0.dp
                )
                val rowBg = if (nebulaColors.isMaterialYou) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    nebulaColors.accent.copy(alpha = 0.06f)
                }
                val borderStroke = if (nebulaColors.isMaterialYou) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                } else {
                    BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.30f))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(rowBg)
                        .border(borderStroke, rowShape)
                ) {
                    WindowsCustomRow(
                        entry = entry,
                        iconSource = customRuleIcons[entry],
                        allApps = allApps,
                        showDivider = !isLast,
                        onRemove = {
                            if (proxyMode == 2) {
                                vpnOnlyList = vpnOnlyList - entry
                            } else {
                                bypassList = bypassList - entry
                            }
                            if (customRuleIcons.containsKey(entry)) {
                                customRuleIcons = customRuleIcons - entry
                                preferencesManager.setCustomRuleIcons(customRuleIcons)
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        item {
            if (isLoading) {
                AppsLoadingState()
            }
        }

        if (!isLoading) {
            if (filteredApps.isEmpty()) {
                item {
                    WindowsAppListPanel {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("Приложений не найдено", "No apps found"),
                                color = nebulaColors.textTertiary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredApps,
                    key = { _, app -> app.packageName }
                ) { index, app ->
                    val isSelected = selectedSet.contains(app.packageName)
                    val isFirst = index == 0
                    val isLast = index == filteredApps.lastIndex
                    val rowShape = RoundedCornerShape(
                        topStart = if (isFirst) 18.dp else 0.dp,
                        topEnd = if (isFirst) 18.dp else 0.dp,
                        bottomStart = if (isLast) 18.dp else 0.dp,
                        bottomEnd = if (isLast) 18.dp else 0.dp
                    )
                    val rowBg = if (isSelected) {
                        if (nebulaColors.isMaterialYou) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            nebulaColors.accent.copy(alpha = 0.06f)
                        }
                    } else {
                        appPanelFill(nebulaColors)
                    }
                    val borderStroke = if (isSelected) {
                        if (nebulaColors.isMaterialYou) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        } else {
                            BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.30f))
                        }
                    } else {
                        null
                    }
                    val colModifier = if (borderStroke != null) {
                        Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(rowBg)
                            .border(borderStroke, rowShape)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(rowBg)
                    }
                    Column(
                        modifier = colModifier
                    ) {
                        WindowsAppRow(
                            app = app,
                            isSelected = isSelected,
                            showDivider = !isLast,
                            onToggle = { checked ->
                                if (proxyMode == 2) {
                                    vpnOnlyList = if (checked) vpnOnlyList + app.packageName else vpnOnlyList - app.packageName
                                } else {
                                    bypassList = if (checked) bypassList + app.packageName else bypassList - app.packageName
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    }

    if (showAddDialog) {
        AddCustomRuleDialog(
            isVpnMode = proxyMode == 2,
            allApps = allApps,
            onAdd = { value, iconSource ->
                val v = value.trim()
                if (v.isNotBlank()) {
                    if (proxyMode == 2) {
                        vpnOnlyList = vpnOnlyList + v
                    } else {
                        bypassList = bypassList + v
                    }
                    if (iconSource != null) {
                        customRuleIcons = customRuleIcons + (v to iconSource)
                        preferencesManager.setCustomRuleIcons(customRuleIcons)
                    }
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun AppsLoadingState() {
    val nebulaColors = LocalNebulaColors.current
    val transition = rememberInfiniteTransition(label = "apps-loading")
    val iconScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "apps-loading-icon"
    )
    val shimmerX by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "apps-loading-shimmer"
    )
    val shimmerBrush = Brush.horizontalGradient(
        colors = listOf(
            nebulaColors.textPrimary.copy(alpha = 0.05f),
            nebulaColors.accent.copy(alpha = 0.17f),
            nebulaColors.textPrimary.copy(alpha = 0.05f)
        ),
        startX = shimmerX,
        endX = shimmerX + 420f
    )

    WindowsAppListPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .scale(iconScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(nebulaColors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = nebulaColors.accent,
                    modifier = Modifier.size(27.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = t("Сканируем приложения", "Scanning apps"),
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = t("Подготавливаем список и иконки", "Preparing the list and icons"),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        repeat(4) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.58f else 0.72f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.78f else 0.52f)
                            .height(9.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
private fun WindowsAppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou
    val shape = RoundedCornerShape(16.dp)

    val containerBg = if (isMaterialYou) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        appControlFill(nebulaColors)
    }
    val borderStrokeColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    } else {
        appBorder(nebulaColors, darkAlpha = 0.16f)
    }
    val textCursorBrush = if (isMaterialYou) {
        SolidColor(MaterialTheme.colorScheme.primary)
    } else {
        SolidColor(nebulaColors.accent)
    }
    val placeholderColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    } else {
        nebulaColors.textTertiary
    }
    val iconColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        nebulaColors.textTertiary
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = textCursorBrush,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (isMaterialYou) MaterialTheme.colorScheme.onSurface else nebulaColors.textPrimary,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(containerBg)
                    .border(1.dp, borderStrokeColor, shape)
                    .padding(start = 15.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(11.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = t("Поиск приложений", "Search apps"),
                            color = placeholderColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = t("Очистить", "Clear"),
                            tint = placeholderColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun WindowsAppAddButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou
    val buttonShape = if (isMaterialYou) RoundedCornerShape(18.dp) else RoundedCornerShape(16.dp)
    // Material You: filled tonal container (no outline) — the Expressive look, not a thin accent border.
    val containerColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        nebulaColors.accent.copy(alpha = 0.12f)
    }
    val contentColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        nebulaColors.accent
    }
    val borderStroke = if (isMaterialYou) {
        null
    } else {
        BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.55f))
    }

    Surface(
        onClick = onClick,
        shape = buttonShape,
        color = containerColor,
        border = borderStroke,
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = t("Добавить", "Add"),
                tint = contentColor,
                modifier = Modifier.size(29.dp)
            )
        }
    }
}

@Composable
private fun WindowsCustomRow(
    entry: String,
    iconSource: String?,
    allApps: List<AppInfo>,
    showDivider: Boolean,
    onRemove: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isMaterialYou = nebulaColors.isMaterialYou
            val iconBg = if (isMaterialYou) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else nebulaColors.accent.copy(alpha = 0.12f)
            val iconTint = if (isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent

            val resolvedIcon = rememberRuleIcon(entry, iconSource, allApps)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (resolvedIcon != null) {
                    Image(
                        bitmap = resolvedIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(Icons.Default.Public, null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            val titleColor = if (isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent
            val subColor = if (isMaterialYou) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else nebulaColors.accent.copy(alpha = 0.7f)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry,
                    color = titleColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = t("Своё правило", "Custom rule"),
                    color = subColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = t("Удалить", "Delete"), tint = nebulaColors.textTertiary)
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(appDivider(nebulaColors))
            )
        }
    }
}

@Composable
private fun AddCustomRuleDialog(
    isVpnMode: Boolean,
    allApps: List<AppInfo>,
    onAdd: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    var text by remember { mutableStateOf("") }
    // Explicit icon override from the app picker. When null, the icon is auto-resolved from the text.
    var pickedIconSource by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }

    val trimmed = text.trim()
    val matchedApp = remember(trimmed, allApps) { allApps.firstOrNull { it.packageName == trimmed } }
    val effectiveSource = pickedIconSource ?: when {
        matchedApp != null -> "app:$trimmed"
        looksLikeDomain(trimmed) -> "fav:${hostOf(trimmed)}"
        else -> null
    }
    val previewIcon = rememberRuleIcon(trimmed, pickedIconSource, allApps)
    val typeLabel = when {
        trimmed.isBlank() -> t("Введите домен или приложение", "Enter a domain or app")
        matchedApp != null -> t("Приложение: ${matchedApp.appName}", "App: ${matchedApp.appName}")
        looksLikeDomain(trimmed) -> t("Сайт — иконка загрузится автоматически", "Website — icon loads automatically")
        else -> t("Своё правило", "Custom rule")
    }

    val isMaterialYou = nebulaColors.isMaterialYou
    val accent = if (isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent
    val onAccent = if (isMaterialYou) MaterialTheme.colorScheme.onPrimary else Color.White

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = nebulaColors.surface,
            border = BorderStroke(1.dp, appBorder(nebulaColors))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                // ── Header ───────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showAppPicker) Icons.Default.Apps else Icons.Default.Add,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showAppPicker) t("Выбор приложения", "Choose app") else t("Добавить правило", "Add rule"),
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isVpnMode) t("Через VPN", "Through VPN") else t("В обход VPN", "Bypass VPN"),
                            color = accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                if (showAppPicker) {
                    NebulaInputField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        label = t("Поиск", "Search"),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    val q = appQuery.lowercase()
                    val list = remember(appQuery, allApps) {
                        allApps.filter { it.searchName.contains(q) || it.searchPackage.contains(q) }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        items(list, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        text = app.packageName
                                        pickedIconSource = "app:${app.packageName}"
                                        showAppPicker = false
                                        appQuery = ""
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.appName,
                                        color = nebulaColors.textPrimary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        color = nebulaColors.textTertiary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (isVpnMode) {
                            t("Домен или приложение пойдут через VPN.", "Domain or app will go through VPN.")
                        } else {
                            t("Домен или приложение пойдут в обход VPN.", "Domain or app will bypass VPN.")
                        },
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(16.dp))
                    NebulaInputField(
                        value = text,
                        onValueChange = { text = it },
                        label = t("например, youtube.com или com.example", "e.g. youtube.com or com.example"),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    // Live preview card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(appControlFill(nebulaColors))
                            .border(BorderStroke(1.dp, appBorder(nebulaColors)), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (previewIcon != null) {
                                    Image(
                                        bitmap = previewIcon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Public,
                                        null,
                                        tint = accent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (trimmed.isBlank()) t("Новое правило", "New rule") else trimmed,
                                    color = nebulaColors.textPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = typeLabel,
                                    color = nebulaColors.textTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    WindowsAppActionButton(
                        icon = Icons.Default.Apps,
                        label = t("Выбрать приложение", "Choose app"),
                        modifier = Modifier.fillMaxWidth()
                    ) { showAppPicker = true }
                }

                Spacer(Modifier.height(20.dp))

                // ── Actions ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showAppPicker) {
                        TextButton(onClick = {
                            showAppPicker = false
                            appQuery = ""
                        }) {
                            Text(t("Назад", "Back"), color = accent, fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(t("Отмена", "Cancel"), color = nebulaColors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            onClick = { onAdd(trimmed, effectiveSource) },
                            enabled = trimmed.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (trimmed.isNotBlank()) accent else accent.copy(alpha = 0.30f)
                        ) {
                            Text(
                                text = t("Добавить", "Add"),
                                color = onAccent.copy(alpha = if (trimmed.isNotBlank()) 1f else 0.7f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowsModeButton(
    active: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou

    val cardColor = if (isMaterialYou) {
        if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    } else {
        if (active) nebulaColors.accent.copy(alpha = if (nebulaColors.appsLight) 0.08f else 0.10f) else appPanelFill(nebulaColors)
    }
    val cardBorder = if (isMaterialYou) {
        if (active) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, if (active) nebulaColors.accent.copy(alpha = 0.72f) else appBorder(nebulaColors))
    }
    val textColor = if (isMaterialYou) {
        if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        if (active) nebulaColors.accent else nebulaColors.textTertiary
    }

    Surface(
        modifier = modifier
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = cardBorder
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WindowsAppActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou

    val containerColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        appControlFill(nebulaColors)
    }
    val contentColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.primary
    } else {
        nebulaColors.textSecondary
    }
    val borderCol = if (isMaterialYou) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, appBorder(nebulaColors))
    }

    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = borderCol
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WindowsAppFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou

    val chipBg = if (isMaterialYou) {
        if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        if (active) nebulaColors.accent.copy(alpha = if (nebulaColors.appsLight) 0.08f else 0.10f) else appSoftFill(nebulaColors)
    }
    val chipBorder = if (isMaterialYou) {
        if (active) BorderStroke(0.dp, Color.Transparent)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        if (active) BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.82f)) else BorderStroke(1.dp, appBorder(nebulaColors))
    }
    val textColor = if (isMaterialYou) {
        if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        if (active) nebulaColors.accent else nebulaColors.textTertiary
    }

    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(chipBg)
            .border(chipBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WindowsAppListPanel(content: @Composable ColumnScope.() -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou

    val fillColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    } else {
        appPanelFill(nebulaColors)
    }
    val borderCol = if (isMaterialYou) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
    } else {
        BorderStroke(1.dp, appBorder(nebulaColors))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = fillColor,
        border = borderCol
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun WindowsAppRow(
    app: AppInfo,
    isSelected: Boolean,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isSelected) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            val isMaterialYou = nebulaColors.isMaterialYou
            val appTitleColor = if (isSelected) {
                if (isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent
            } else {
                if (isMaterialYou) MaterialTheme.colorScheme.onSurface else nebulaColors.textPrimary
            }
            val appSubColor = if (isSelected) {
                if (isMaterialYou) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else nebulaColors.accent.copy(alpha = 0.7f)
            } else {
                if (isMaterialYou) MaterialTheme.colorScheme.onSurfaceVariant else nebulaColors.textTertiary
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = appTitleColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = appSubColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            WindowsCheckBox(checked = isSelected)
        }
        if (showDivider && !isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(appDivider(nebulaColors))
            )
        }
    }
}

@Composable
private fun WindowsCheckBox(checked: Boolean) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou
    val checkShape = if (isMaterialYou) CircleShape else RoundedCornerShape(9.dp)
    val checkColor = if (isMaterialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent
    val borderCol = if (checked) checkColor else {
        if (isMaterialYou) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f)
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(checkShape)
            .background(if (checked) checkColor else Color.Transparent)
            .border(2.dp, borderCol, checkShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            val iconColor = if (isMaterialYou) MaterialTheme.colorScheme.onPrimary else Color.White
            Icon(Icons.Default.Check, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun TabItem(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val bgColor by animateColorAsState(
        if (isSelected) nebulaColors.accent.copy(alpha = 0.18f) else Color.Transparent,
        label = "tabBg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) nebulaColors.accent else nebulaColors.textTertiary,
        label = "tabContent"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun ProxyModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val cardColor by animateColorAsState(
        targetValue = if (isSelected) nebulaColors.accent.copy(alpha = 0.13f)
        else appControlFill(nebulaColors),
        label = "proxyModeCardColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) nebulaColors.accent.copy(alpha = 0.46f)
        else appBorder(nebulaColors),
        label = "proxyModeCardBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) nebulaColors.accent else nebulaColors.textPrimary.copy(alpha = 0.9f),
        label = "proxyModeCardTitle"
    )

    Surface(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) nebulaColors.accent else nebulaColors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AppProxyItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isSelected) },
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = if (isSelected) 0.07f else 0.028f),
        border = BorderStroke(
            1.dp,
            if (isSelected) nebulaColors.accent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.07f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = nebulaColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle(it) },
                modifier = Modifier.size(36.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = nebulaColors.accent,
                    uncheckedColor = nebulaColors.textTertiary.copy(alpha = 0.4f),
                    checkmarkColor = Color.White
                )
            )
        }
    }
}

// Extension function to convert Drawable to Bitmap
fun android.graphics.drawable.Drawable.toBitmap(): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

// ── Custom-rule icon resolution ─────────────────────────────────────────────
// Source strings: "app:<package>", "file:<absolutePath>", "fav:<host>".
// Decoded bitmaps are cached in-memory so favicons/files aren't re-fetched on every recomposition.

private val ruleIconCache = mutableMapOf<String, ImageBitmap>()

private fun hostOf(raw: String): String {
    var s = raw.trim().lowercase()
    val scheme = s.indexOf("://")
    if (scheme >= 0) s = s.substring(scheme + 3)
    s = s.substringBefore('/').substringBefore(':')
    return s
}

private fun looksLikeDomain(raw: String): Boolean {
    val h = hostOf(raw)
    if (h.isBlank() || h.any { it.isWhitespace() }) return false
    val parts = h.split('.')
    return parts.size >= 2 && parts.all { it.isNotBlank() } && parts.last().length >= 2
}

private suspend fun loadBitmapFromUrl(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }.getOrNull()
}

private suspend fun loadBitmapFromFile(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}

private suspend fun loadAppIconBitmap(context: android.content.Context, pkg: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
    }

/**
 * Resolves the icon for a custom rule entry. Uses an explicit [iconSource] when given,
 * otherwise auto-resolves: an installed app's icon for a package name, or the site favicon
 * for a domain. Returns null while loading or when nothing could be resolved.
 */
@Composable
private fun rememberRuleIcon(
    entry: String,
    iconSource: String?,
    allApps: List<AppInfo>
): ImageBitmap? {
    val context = LocalContext.current
    val installed = remember(entry, allApps) { allApps.firstOrNull { it.packageName == entry } }
    val source = remember(entry, iconSource, installed) {
        iconSource ?: when {
            installed != null -> "app:$entry"
            looksLikeDomain(entry) -> "fav:${hostOf(entry)}"
            else -> null
        }
    }
    return produceState<ImageBitmap?>(
        initialValue = source?.let { ruleIconCache[it] },
        key1 = source,
        key2 = installed
    ) {
        val src = source
        if (src == null) {
            value = null
            return@produceState
        }
        ruleIconCache[src]?.let { value = it; return@produceState }
        // Fast path: a matched installed app already carries a decoded bitmap.
        if (installed != null && src == "app:$entry") {
            val bmp = installed.icon.asImageBitmap()
            ruleIconCache[src] = bmp
            value = bmp
            return@produceState
        }
        val bmp = when {
            src.startsWith("app:") -> loadAppIconBitmap(context, src.removePrefix("app:"))
            src.startsWith("file:") -> loadBitmapFromFile(src.removePrefix("file:"))
            src.startsWith("fav:") -> loadBitmapFromUrl(
                "https://www.google.com/s2/favicons?sz=64&domain=${src.removePrefix("fav:")}"
            )
            else -> null
        }
        if (bmp != null) ruleIconCache[src] = bmp
        value = bmp
    }.value
}
