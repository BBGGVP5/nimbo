package com.danila.nimbo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.danila.nimbo.mihomo.*
import com.danila.nimbo.network.ActiveProxyPing
import com.danila.nimbo.network.NimboNodePing
import com.danila.nimbo.network.MihomoPingFailure
import com.danila.nimbo.network.MihomoPingResult
import com.danila.nimbo.network.MihomoPingCache
import com.danila.nimbo.network.PingProtocol
import com.danila.nimbo.network.displayPingLabel
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.ui.components.SubscriptionBrandLogo
import com.danila.nimbo.service.SubscriptionUpdateEvents
import com.danila.nimbo.ui.components.LocalFloatingNavHeight
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ordinary Profiles destination, not a separate YAML/settings dashboard. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun MihomoProxiesScreen(onAddSubscription: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val ui = remember(context) { MihomoProfilesUi(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = LocalNebulaColors.current
    val pingPreferences = remember(context) { PreferencesManager(context.applicationContext) }
    val pingUrl by pingPreferences.pingUrlState
    var browsingUrl by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showInformation by remember { mutableStateOf(false) }
    var chooseSubscription by remember { mutableStateOf(false) }
    var subscriptionExpanded by rememberSaveable { mutableStateOf(true) }
    var choicesVersion by remember { mutableIntStateOf(0) }
    var measurement by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var measuringName by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var measuredCount by remember { mutableIntStateOf(0) }
    var measurementTotal by remember { mutableIntStateOf(0) }
    val delays = remember { mutableStateMapOf<String, Long>() }
    val failures = remember { mutableStateMapOf<String, MihomoPingFailure>() }
    val unmeasurable = remember { mutableStateListOf<String>() }
    val importedName = t("Импортированный профиль", "Imported profile")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { ui.perform {
            val source = withContext(Dispatchers.IO) { readMihomoDocument(context, uri) }
            ui.importYaml(importedName, source)
        } }
    }
    LaunchedEffect(ui, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ui.load()
            launch { SubscriptionUpdateEvents.updates.collect { ui.load(false) } }
            while (true) { delay(3000); ui.poll() }
        }
    }
    val row = ui.profiles.firstOrNull { it.profile.url == browsingUrl }
        ?: ui.profiles.firstOrNull { it.profile.url == ui.selectedUrl } ?: ui.profiles.firstOrNull()
    val live = ui.runtime?.takeIf { it.session.controls(row?.sourceHash.orEmpty()) }
    val choices = remember(row?.sourceHash, choicesVersion) {
        row?.let { MihomoGroupChoices.read(context, it.sourceHash) }.orEmpty()
    }
    val groups = remember(row?.inspection, live?.snapshot, choices) { mihomoProxyGroups(row?.inspection, live?.snapshot, choices) }
    val group = groups.firstOrNull { it.name == groupName } ?: groups.firstOrNull()
    LaunchedEffect(row?.sourceHash) { groupName = null; query = "" }
    val members = group?.members.orEmpty().filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }
    val declaredProxies = remember(row?.inspection) {
        row?.inspection?.getAsJsonObject("declaredGraph")?.getAsJsonArray("proxies")
            ?.mapNotNull { element ->
                val proxy = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val name = proxy["name"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return@mapNotNull null
                name to proxy
            }?.toMap().orEmpty()
    }
    val nodeFingerprints = remember(declaredProxies) {
        declaredProxies.mapValues { (_, proxy) -> MihomoPingCache.fingerprint(proxy.toString()) }
    }
    LaunchedEffect(row?.sourceHash, group?.name, pingUrl, nodeFingerprints) {
        measurement?.cancel()
        measurement = null
        measuringName = null
        delays.clear(); failures.clear(); unmeasurable.clear()
        if (row != null && group != null)
            delays.putAll(MihomoPingCache.read(context, row.profile.url, pingUrl.trim(), nodeFingerprints)
                .filterKeys { it in group.members }.mapValues { it.value.toLong() })
        measuredCount = 0; measurementTotal = 0
    }
    val description = row?.parent?.announce?.takeIf { it.isNotBlank() }
        ?: row?.profile?.announce?.takeIf { it.isNotBlank() }
    val subscriptionSource = row?.parent ?: row?.profile
    val logo = row?.parent?.brandLogo ?: row?.profile?.brandLogo
    val logoCache = row?.parent?.brandLogoCache ?: row?.profile?.brandLogoCache
    val canMeasure = row != null && group != null && !ui.busy
    val invalidPingUrlMessage = t("Проверьте адрес Nimbo Ping в настройках", "Check the Nimbo Ping URL in settings")
    val interruptedMessage = t("Проверка прервана. Повторите позже.", "Test interrupted. Try again later.")
    suspend fun measureNode(currentRow: MihomoUiProfile, name: String, target: String, timeout: Int) {
        val fingerprint = nodeFingerprints[name] ?: return
        val activeSession = live?.session?.takeIf { it.controls(currentRow.sourceHash) }
        val result = if (activeSession != null) {
            try {
                MihomoPingResult(ui.nimboDelay(activeSession, name, target, timeout))
            } catch (error: MihomoException) {
                if (error.code == "STALE_GENERATION" || error.code == "NOT_RUNNING")
                    NimboNodePing.measureMihomo(context, currentRow.profile.rawConfig.orEmpty(), name, target, timeout)
                else MihomoPingResult(-1, if (error.code == "DELAY_FAILED") MihomoPingFailure.GET_FAILED else MihomoPingFailure.UNAVAILABLE)
            }
        } else NimboNodePing.measureMihomo(context, currentRow.profile.rawConfig.orEmpty(), name, target, timeout)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO) {
            MihomoPingCache.write(context, currentRow.profile.url, target, name, fingerprint,
                result.delayMs.takeIf { it >= 0 })
        }
        if (result.delayMs >= 0) { delays[name] = result.delayMs.toLong(); failures.remove(name) }
        else { failures[name] = result.reason ?: MihomoPingFailure.UNAVAILABLE; delays.remove(name) }
    }
    fun measureOnly(name: String) {
        if (measurement != null && measuringName == name) { measurement?.cancel(); return }
        val currentRow = row ?: return
        if (measurement != null || ui.busy || name !in declaredProxies) return
        val target = pingUrl.trim()
        if (!ActiveProxyPing.validUrl(target)) { notice = invalidPingUrlMessage; return }
        val timeout = pingPreferences.pingTimeout.coerceIn(1, 10) * 1000
        measuredCount = 0; measurementTotal = 1; notice = null
        measurement = scope.launch {
            measuringName = name
            try {
                measureNode(currentRow, name, target, timeout)
                measuredCount = 1
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { notice = interruptedMessage }
            finally {
                if (measurement === kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]) {
                    measurement = null; measuringName = null
                }
            }
        }
    }
    fun toggleMeasurement() {
        if (measurement != null) { measurement?.cancel(); return }
        val currentGroup = group ?: return
        val currentRow = row ?: return
        if (!canMeasure) return
        val target = pingUrl.trim()
        if (!ActiveProxyPing.validUrl(target)) {
            notice = invalidPingUrlMessage
            return
        }
        val timeout = pingPreferences.pingTimeout.coerceIn(1, 10) * 1000
        measurementTotal = currentGroup.members.count { it in declaredProxies }
        measuredCount = 0
        notice = null
        measurement = scope.launch {
            try {
                for (name in currentGroup.members) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (name !in declaredProxies) { unmeasurable.add(name); continue }
                    measuringName = name
                    measureNode(currentRow, name, target, timeout)
                    measuredCount++
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { notice = interruptedMessage }
            finally {
                if (measurement === kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]) {
                    measurement = null; measuringName = null
                }
            }
        }
    }
        val message = when {
            ui.error in setOf("PROFILE_ACTIVE", "ACTIVE_PROFILE") -> t("Отключите VPN перед обновлением или удалением подписки.", "Disconnect VPN before refreshing or removing this subscription.")
            ui.error == "STALE_GENERATION" -> t("Подключение изменилось. Повторите действие.", "Connection changed. Please try again.")
            ui.error == "ANDROID_DNS_REQUIRED" -> t("Нет доступной сети с DNS для проверки серверов.", "No network with DNS is available for a server test.")
            ui.error == "BUSY" -> t("Ядро сейчас переключается. Повторите проверку через несколько секунд.", "The core is switching. Retry the test in a few seconds.")
            ui.error != null || row?.error != null -> t("Не удалось применить профиль. Конфигурация требует проверки совместимости.", "Profile could not be applied. Configuration compatibility needs checking.")
            !ui.available && ui.loaded -> t("Ядро недоступно в этой сборке", "Core unavailable in this build")
            else -> notice
        }
    val bottom = LocalFloatingNavHeight.current + 20.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("mihomo-integrated-profiles"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Профили", "Profiles"), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                    Text("Mihomo  ·  ${declaredProxies.size} " + t("серверов", "servers") + "  ·  ${groups.size} " + t("групп", "groups"), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                IconButton(onClick = onAddSubscription) { Icon(Icons.Default.Add, t("Добавить подписку", "Add subscription"), tint = colors.textPrimary) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("Действия", "Actions"), tint = colors.textPrimary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(t("Импорт из файла", "Import file")) }, onClick = { menu = false; picker.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text(t("Обновить подписки", "Refresh subscriptions")) }, enabled = !ui.busy,
                            onClick = { menu = false; scope.launch { ui.refreshSubscriptions() } })
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(onClick = { subscriptionExpanded = !subscriptionExpanded },
                shape = RoundedCornerShape(22.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.textSecondary.copy(alpha = .16f)),
                modifier = Modifier.testTag("mihomo-subscription-card")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SubscriptionBrandLogo(logo, logoCache, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subscriptionSource?.displayName?.removeSuffix(" · Mihomo") ?: t("Нет подписок", "No subscriptions"), color = colors.textPrimary,
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!subscriptionExpanded) Text("${declaredProxies.size} " + t("серверов", "servers") + " · ${groups.size} " + t("групп", "groups"),
                                color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(if (subscriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            t("Развернуть подписку", "Expand subscription"), tint = colors.textSecondary)
                    }
                    if (subscriptionExpanded) {
                        if (description != null) Text(description, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color = colors.textSecondary.copy(alpha = .14f))
                        Text("${declaredProxies.size} " + t("серверов", "servers") + " · ${groups.size} " + t("групп", "groups"),
                            color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = ::toggleMeasurement, enabled = measurement != null || canMeasure,
                            contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).testTag("mihomo-nimbo-ping")) {
                            Icon(if (measurement != null) Icons.Default.Stop else Icons.Default.Speed,
                                null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (measurement != null) "$measuredCount/$measurementTotal" else "Nimbo Ping", maxLines = 1)
                        }
                        TextButton(onClick = { chooseSubscription = true }, enabled = ui.profiles.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.testTag("mihomo-subscription-switch")) {
                            Text(t("Сменить", "Switch"))
                        }
                        IconButton(onClick = { showInformation = true }, enabled = row != null) {
                            Icon(Icons.Default.Info, t("О подписке", "Subscription information"), tint = colors.textSecondary)
                        }
                        IconButton(onClick = { scope.launch { if (row != null) ui.perform { ui.refreshUrl(row) } else ui.refreshSubscriptions() } }, enabled = !ui.busy) {
                            Icon(Icons.Default.Refresh, t("Обновить", "Refresh"), tint = colors.textSecondary)
                        }
                    }
                }
            }
        }
        if (ui.busy || !ui.loaded) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent) }
        if (message != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (groups.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("mihomo-group-tabs")) {
                items(groups, key = { it.name }) { tab ->
                    FilterChip(selected = tab.name == group?.name, onClick = { groupName = tab.name; query = "" },
                        label = { Text(tab.name, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent.copy(alpha = .16f), selectedLabelColor = colors.accent))
                }
            }
        }
        if (group != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(t("Поиск прокси", "Search proxies")) }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (group.selectable) t("Выберите сервер", "Choose a server") else t("Автоматическая группа", "Automatic group"),
                        style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
            }
        }
        items(members, key = { it }) { member ->
            val subgroup = groups.firstOrNull { it.name == member }
            val proxy = live?.snapshot?.getAsJsonObject("proxies")?.get(member)?.asJsonObject ?: declaredProxies[member]
            val type = subgroup?.type ?: proxy?.get("type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: if (member == "DIRECT") "Direct" else "Proxy"
            val latency = delays[member]
            val selected = group?.selected == member
            var menuExpanded by remember(member) { mutableStateOf(false) }
            val selectMember: () -> Unit = {
                if (group?.selectable == true && row != null) scope.launch { ui.perform {
                    // Choosing a proxy is also an explicit choice of its subscription.
                    // Otherwise Home can retain a stale Xray node and reject Connect.
                    if (ui.selectedUrl != row.profile.url ||
                        VpnManager.selectedServer?.profileUrl != row.profile.url ||
                        VpnManager.selectedServer?.protocol != "mihomo") ui.select(row.profile)
                    if (live != null) ui.control(live.session, "select", JsonObject().apply { addProperty("group", group.name); addProperty("name", member) })
                    withContext(Dispatchers.IO) {
                        MihomoGroupChoices.write(context, row.sourceHash, group.name, member, nodeFingerprints[member])
                    }
                    choicesVersion++
                } }
                else if (subgroup != null) groupName = subgroup.name
            }
            Surface(shape = RoundedCornerShape(18.dp), color = if (selected) colors.accent.copy(alpha = .22f) else colors.surface,
                border = BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) colors.accent else colors.textSecondary.copy(alpha = .16f)),
                modifier = Modifier.height(132.dp).testTag("mihomo-proxy-$member")
                    .combinedClickable(enabled = !ui.busy, onClick = selectMember,
                        onLongClick = { menuExpanded = true }, onLongClickLabel = t("Действия с сервером", "Server actions"))
                    .semantics { this.selected = selected }) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) {
                            Text(member, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            DropdownMenu(menuExpanded, { menuExpanded = false }, containerColor = colors.panelFill,
                                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.panelBorder)) {
                                if (member in declaredProxies) DropdownMenuItem(
                                    text = { Text(if (measuringName == member) t("Остановить пинг", "Stop ping") else t("Пинг сервера", "Ping server")) },
                                    enabled = !ui.busy && (measurement == null || measuringName == member),
                                    leadingIcon = { Icon(if (measuringName == member) Icons.Default.Stop else Icons.Default.Speed, null) },
                                    onClick = { menuExpanded = false; measureOnly(member) })
                            }
                        }
                        if (selected) Surface(shape = androidx.compose.foundation.shape.CircleShape, color = colors.accent) {
                            Icon(Icons.Default.Check, t("Выбран", "Selected"), Modifier.padding(3.dp).size(18.dp),
                                tint = com.danila.nimbo.ui.components.contrastingLabel(colors.accent))
                        }
                    }
                    Text(if (subgroup != null) "$type · ${subgroup.selected}" else type,
                        color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50),
                            color = if (latency != null) colors.accent.copy(alpha = .14f) else colors.textSecondary.copy(alpha = .12f)) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, null, Modifier.size(14.dp), tint = if (latency != null) colors.accent else colors.textSecondary)
                                Spacer(Modifier.width(5.dp))
                                Text(latency?.let { "${displayPingLabel(it.toInt(), PingProtocol.NIMBO.id)} ms" }
                                    ?: if (failures.containsKey(member)) t("н/д", "N/A")
                                    else if (member in unmeasurable || subgroup != null) t("Группа", "Group")
                                else if (member == "DIRECT") t("Напрямую", "Direct") else "—",
                                    color = if (latency != null) colors.accent else colors.textSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (measuringName == member) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)

                    }
                }
            }
        }
        if (ui.loaded && row == null) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Добавьте подписку — её группы появятся здесь", "Add a subscription to see its groups here"), color = colors.textSecondary)
                Button(onClick = onAddSubscription) { Text(t("Добавить подписку", "Add subscription")) }
            }
        }
    }
    if (showInformation && subscriptionSource != null) SubscriptionDetailsDialog(
        profile = subscriptionSource, onDismiss = { showInformation = false },
        onSupport = { subscriptionSource.supportUrl?.let { runCatching { uriHandler.openUri(it) } } },
        onSite = { subscriptionSource.websiteUrl?.let { runCatching { uriHandler.openUri(it) } } },
        serverCountOverride = declaredProxies.size
    )
    if (chooseSubscription) ModalBottomSheet(onDismissRequest = { chooseSubscription = false },
        containerColor = colors.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(t("Подписки", "Subscriptions"), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Text(t("Выбор подписки не включает VPN", "Choosing a subscription does not start VPN"),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(ui.profiles, key = { it.profile.url }) { item ->
                    val info = item.parent ?: item.profile
                    val current = item.profile.url == ui.selectedUrl
                    Surface(onClick = { browsingUrl = item.profile.url; chooseSubscription = false
                        scope.launch { ui.perform { ui.select(item.profile) } } },
                        enabled = item.error == null && !ui.busy,
                        shape = RoundedCornerShape(18.dp), color = if (current) colors.accent.copy(alpha = .12f) else colors.background,
                        border = BorderStroke(1.dp, if (current) colors.accent else colors.textSecondary.copy(alpha = .14f))) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            SubscriptionBrandLogo(info.brandLogo, info.brandLogoCache, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.displayName.removeSuffix(" · Mihomo"), color = colors.textPrimary,
                                    style = MaterialTheme.typography.titleSmall)
                                Text(info.announce?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull().orEmpty()
                                    .ifBlank { t("${item.inspection?.getAsJsonObject("declaredGraph")?.getAsJsonArray("proxies")?.size() ?: 0} серверов", "Servers") },
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            if (current) Icon(Icons.Default.CheckCircle, t("Выбрана", "Selected"), tint = colors.accent)
                        }
                    }
                }
            }
        }
    }
}
