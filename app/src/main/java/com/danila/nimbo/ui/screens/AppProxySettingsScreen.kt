@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import com.danila.nimbo.ui.components.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogProperties

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.components.NebulaInputField
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.components.nimboControlBorderColor
import com.danila.nimbo.ui.components.nimboControlBorderWidth
import com.danila.nimbo.ui.components.nimboControlContainer
import com.danila.nimbo.ui.components.nimboControlShape
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
    val elementStyle = LocalElementStyleMode.current
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
    var toolbarExpanded by remember { mutableStateOf(false) }
    var systemHelpExpanded by rememberSaveable { mutableStateOf(false) }

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
                .padding(horizontal = 16.dp)
                .padding(top = if (showBack) 12.dp else 10.dp),
            contentPadding = PaddingValues(bottom = com.danila.nimbo.ui.components.LocalFloatingNavHeight.current + 16.dp)
    ) {
        item {
            NimboSubPageHeader(t("Маршрутизация по приложениям", "App routing"), onBack = onNavigateBack, horizontalPadding = 0.dp)

            Spacer(Modifier.height(8.dp))
            AppRoutingModeSelector(mode = proxyMode, onModeChange = { proxyMode = it })

            Spacer(Modifier.height(12.dp))
            val subNoRulesMsg = t("В подписке нет правил приложений", "No app rules in subscription")
            val subAllAddedMsg = t("Все правила уже добавлены", "All rules already added")
            val subLoadedPrefix = t("Загружено правил: ", "Loaded rules: ")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WindowsAppSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).height(48.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { toolbarExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, t("Действия и сортировка", "Actions and sorting"), tint = nebulaColors.textSecondary)
                    }
                    DropdownMenu(expanded = toolbarExpanded, onDismissRequest = { toolbarExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(t("Добавить правило", "Add rule")) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { toolbarExpanded = false; showAddDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text(t("Загрузить из подписки", "Load from subscription")) },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                            onClick = {
                                toolbarExpanded = false
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
                        )
                        HorizontalDivider()
                        AppSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(when (mode) {
                                    AppSortMode.Default -> t("Порядок по умолчанию", "Default order")
                                    AppSortMode.Az -> t("По имени: А–Я", "Name: A–Z")
                                    AppSortMode.Za -> t("По имени: Я–А", "Name: Z–A")
                                }) },
                                leadingIcon = { RadioButton(selected = sortMode == mode, onClick = null) },
                                onClick = { sortMode = mode; toolbarExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().selectableGroup().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppSelectionFilter(t("Все", "All"), filterMode == AppFilterMode.All, Modifier.weight(1f).fillMaxHeight()) { filterMode = AppFilterMode.All }
                AppSelectionFilter(t("Выбранные", "Selected"), filterMode == AppFilterMode.Enabled, Modifier.weight(1f).fillMaxHeight()) { filterMode = AppFilterMode.Enabled }
                AppSelectionFilter(t("Остальные", "Unselected"), filterMode == AppFilterMode.Disabled, Modifier.weight(1f).fillMaxHeight()) { filterMode = AppFilterMode.Disabled }
            }

            if (android.os.Build.VERSION.SDK_INT >= 37) {
                TextButton(
                    onClick = { systemHelpExpanded = !systemHelpExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Исключения Android 17", "Android 17 exclusions"),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(if (systemHelpExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (systemHelpExpanded) t("Скрыть пояснение", "Hide explanation") else t("Показать пояснение", "Show explanation"))
                }
                if (systemHelpExpanded) {
                    Text(
                        t("На Android 17+ исключения приложений также можно настроить в системных настройках VPN.",
                            "On Android 17+, app exclusions can also be configured in system VPN settings."),
                        style = MaterialTheme.typography.bodySmall, color = nebulaColors.textSecondary
                    )
                    val failedToOpenSettingsMsg = t("Не удалось открыть настройки", "Failed to open settings")
                    TextButton(onClick = {
                        runCatching {
                            val intent = android.content.Intent("android.settings.VPN_APP_EXCLUSION_SETTINGS").apply {
                                putExtra(android.content.Intent.EXTRA_PACKAGE_NAME, context.packageName)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            android.widget.Toast.makeText(context, failedToOpenSettingsMsg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(t("Открыть настройки", "Open settings"))
                    }
                }
            }

            Text(
                text = t("Выбрано: ${selectedSet.size} · Показано приложений: ${filteredApps.size}",
                    "Selected: ${selectedSet.size} · Apps shown: ${filteredApps.size}"),
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            )
        }

        if (customEntries.isNotEmpty()) {
            itemsIndexed(
                items = customEntries,
                key = { _, entry -> "custom:$entry" }
            ) { index, entry ->
                val isFirst = index == 0
                val isLast = index == customEntries.lastIndex
                val edgeRadius = if (elementStyle == ElementStyleMode.MANGA) 3.dp else 18.dp
                val rowShape = RoundedCornerShape(
                    topStart = if (isFirst) edgeRadius else 0.dp,
                    topEnd = if (isFirst) edgeRadius else 0.dp,
                    bottomStart = if (isLast) edgeRadius else 0.dp,
                    bottomEnd = if (isLast) edgeRadius else 0.dp
                )
                val rowBg = if (elementStyle == ElementStyleMode.MANGA) {
                    nebulaColors.controlFill
                } else if (nebulaColors.isMaterialYou) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    nebulaColors.accent.copy(alpha = 0.06f)
                }
                val borderStroke = if (elementStyle == ElementStyleMode.MANGA) {
                    BorderStroke(2.dp, nebulaColors.accent)
                } else if (nebulaColors.isMaterialYou) {
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
                    val edgeRadius = if (elementStyle == ElementStyleMode.MANGA) 3.dp else 18.dp
                    val rowShape = RoundedCornerShape(
                        topStart = if (isFirst) edgeRadius else 0.dp,
                        topEnd = if (isFirst) edgeRadius else 0.dp,
                        bottomStart = if (isLast) edgeRadius else 0.dp,
                        bottomEnd = if (isLast) edgeRadius else 0.dp
                    )
                    val rowBg = if (isSelected) {
                        if (elementStyle == ElementStyleMode.MANGA) {
                            nebulaColors.accent.copy(alpha = 0.14f)
                        } else if (nebulaColors.isMaterialYou) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            nebulaColors.accent.copy(alpha = 0.06f)
                        }
                    } else {
                        appPanelFill(nebulaColors)
                    }
                    val borderStroke = if (isSelected) {
                        if (elementStyle == ElementStyleMode.MANGA) {
                            BorderStroke(2.dp, nebulaColors.accent)
                        } else if (nebulaColors.isMaterialYou) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        } else {
                            BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.30f))
                        }
                    } else {
                        if (elementStyle == ElementStyleMode.MANGA) BorderStroke(1.5.dp, nebulaColors.panelBorder) else null
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
    NimboToolSection(t("Загрузка приложений", "Loading apps"),
        t("Читаем список установленных приложений", "Reading installed applications")) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = LocalNebulaColors.current.textSecondary,
            trackColor = LocalNebulaColors.current.controlFill)
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
    val shape = nimboControlShape(16.dp, 3.dp)
    
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

    val searchLabel = t("Поиск приложений", "Search apps")
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = textCursorBrush,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (isMaterialYou) MaterialTheme.colorScheme.onSurface else nebulaColors.textPrimary,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = modifier.semantics { contentDescription = searchLabel },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(nimboControlContainer(containerBg))
                    .border(
                        nimboControlBorderWidth(),
                        nimboControlBorderColor(borderStrokeColor),
                        shape
                    )
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 620.dp).heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).imePadding().navigationBarsPadding(),
            shape = RoundedCornerShape(18.dp),
            color = nebulaColors.surface,
            border = BorderStroke(1.dp, appBorder(nebulaColors))
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                    NetworkSettingsTextField(
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
                            .heightIn(min = 120.dp, max = 256.dp)
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
                                    )
                                    Text(
                                        app.packageName,
                                        color = nebulaColors.textTertiary,
                                        style = MaterialTheme.typography.labelSmall,
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
                    NetworkSettingsTextField(
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
                                )
                                Text(
                                    text = typeLabel,
                                    color = nebulaColors.textTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
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
                NimboToolActions {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoutingModeSelector(mode: Int, onModeChange: (Int) -> Unit) {
    val choices = listOf(
        t("Выбранные — напрямую", "Selected apps bypass VPN") to
            t("Остальные приложения используют VPN", "Other apps use VPN"),
        t("Только выбранные — через VPN", "Only selected apps use VPN") to
            t("Остальные приложения идут напрямую", "Other apps connect directly")
    )
    NimboExpandingChoiceCard(
        options = choices.mapIndexed { index, (title, description) ->
            NimboChoiceOption(index + 1, title, description)
        },
        selectedValue = mode,
        onSelect = onModeChange,
        testTag = "app-routing-mode"
    )
}

@Composable
private fun AppSelectionFilter(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    val shape = nimboControlShape(10.dp, 3.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.controlFill)
            .border(1.dp, if (selected) colors.accent else colors.panelBorder, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            color = if (selected) colors.accent else colors.textSecondary)
    }
}

@Composable
private fun WindowsAppActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    NimboAction(icon, label, onClick, modifier)
}

@Composable
private fun WindowsAppListPanel(content: @Composable ColumnScope.() -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val isMaterialYou = nebulaColors.isMaterialYou
    val shape = nimboControlShape(18.dp, 3.dp)
    
    val fillColor = if (isMaterialYou) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    } else {
        appPanelFill(nebulaColors)
    }
    val borderCol = if (isMaterialYou) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
    } else {
        BorderStroke(nimboControlBorderWidth(), nimboControlBorderColor(appBorder(nebulaColors)))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = nimboControlContainer(fillColor),
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
    val iconShape = nimboControlShape(10.dp, 2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(iconShape)
                    .background(appSoftFill(nebulaColors)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = appSubColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,

                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Checkbox(
                checked = isSelected, onCheckedChange = null, modifier = Modifier.size(48.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = nebulaColors.accent,
                    uncheckedColor = nebulaColors.textTertiary,
                    checkmarkColor = contrastingLabel(nebulaColors.accent)
                )
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp)
                    .height(1.dp)
                    .background(appDivider(nebulaColors))
            )
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
            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = onToggle),
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
                .heightIn(min = 64.dp)
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
                onCheckedChange = null,
                modifier = Modifier.size(48.dp),
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
