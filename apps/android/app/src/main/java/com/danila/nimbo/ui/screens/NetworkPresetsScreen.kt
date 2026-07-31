package com.danila.nimbo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.NetworkPreset
import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.SettingsSwitch
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.NetworkPresetType
import com.danila.nimbo.utils.NetworkProfileManager
import com.danila.nimbo.utils.PreferencesManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed

private fun defaultPresetIcons(): List<String> = listOf(
    "🏠", "📶", "🌍", "🛰", "⚡", "🛡", "🚀", "🌐", "🎯", "🧭", "🔒", "💼", "🎮", "🎬", "📱", "💻"
)

private fun fallbackIconByType(type: NetworkPresetType): String = when (type) {
    NetworkPresetType.HOME -> "🏠"
    NetworkPresetType.PUBLIC_WIFI -> "📶"
    NetworkPresetType.ROAMING -> "🌍"
    NetworkPresetType.OTHER -> "🧭"
}

private fun sanitizeIconGlyph(input: String): String {
    return input.trim().take(2)
}

private fun chooseBestServerVariant(variants: List<Server>): Server {
    return variants
        .sortedWith(
            compareByDescending<Server> { it.isPingValid() }
                .thenByDescending { (it.ping ?: -1) >= 0 }
                .thenBy { it.ping ?: Int.MAX_VALUE }
                .thenByDescending { it.pingTimestamp ?: 0L }
        )
        .firstOrNull() ?: variants.first()
}

@Composable
fun NetworkPresetsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val nebulaColors = LocalNebulaColors.current

    var presets by remember { mutableStateOf(NetworkProfileManager.getPresets(context)) }
    var activePresetId by remember { mutableStateOf(NetworkProfileManager.getActivePresetId(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<NetworkPreset?>(null) }

    val allServersRaw = remember(presets) {
        prefs.loadProfiles().flatMap { it.servers }
    }
    val allServers = allServersRaw

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GlassHeader(
                title = "Профили сети",
                icon = Icons.Default.Tune,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                GlassSection(title = "Автоприменение", icon = Icons.Default.NetworkWifi) {
                    var autoApply by remember { mutableStateOf(NetworkProfileManager.isAutoApplyEnabled(context)) }
                    SettingsSwitch(
                        icon = Icons.Default.Tune,
                        title = "Автоприменять по сети",
                        subtitle = "Дом / Публичный Wi-Fi / Роуминг",
                        checked = autoApply,
                        onCheckedChange = {
                            autoApply = it
                            NetworkProfileManager.setAutoApplyEnabled(context, it)
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                GlassSection(title = "Пресеты", icon = Icons.Default.Home) {
                    presets.forEachIndexed { index, preset ->
                        PresetRow(
                            preset = preset,
                            isActive = activePresetId == preset.id,
                            onActivate = {
                                activePresetId = preset.id
                                NetworkProfileManager.setActivePresetId(context, preset.id)
                            },
                            onEdit = { editingPreset = preset },
                            onDelete = {
                                NetworkProfileManager.deleteCustomPreset(context, preset.id)
                                presets = NetworkProfileManager.getPresets(context)
                                activePresetId = NetworkProfileManager.getActivePresetId(context)
                            }
                        )
                        if (index != presets.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = nebulaColors.textTertiary.copy(alpha = 0.12f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = nebulaColors.accent)
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = "Создать новый пресет",
                            color = nebulaColors.textPrimary,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("Новый пресет") }
        var type by remember { mutableStateOf(NetworkPresetType.HOME) }
        var showAddIconDialog by remember { mutableStateOf(false) }
        var customIconText by remember { mutableStateOf("") }
        var iconPool by remember { mutableStateOf(defaultPresetIcons()) }
        var selectedIcon by remember { mutableStateOf(fallbackIconByType(type)) }

        NebulaMorphicDialog(
            onDismissRequest = { showCreateDialog = false },
            title = "Создать пресет",
            description = "Будет создан на основе выбранного типа.",
            confirmButtonText = "Создать",
            onConfirm = {
                val base = NetworkProfileManager.getPresets(context).firstOrNull { it.type == type }
                    ?: NetworkProfileManager.getPresets(context).first()
                val created = NetworkProfileManager.createCustomPresetFrom(context, base, name)
                NetworkProfileManager.savePreset(
                    context,
                    created.copy(
                        type = type,
                        iconGlyph = sanitizeIconGlyph(selectedIcon).ifBlank { fallbackIconByType(type) }
                    )
                )
                presets = NetworkProfileManager.getPresets(context)
                showCreateDialog = false
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                leadingIcon = {
                    Text(
                        text = sanitizeIconGlyph(selectedIcon).ifBlank { fallbackIconByType(type) },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                placeholder = { Text("Например: Домашний Wi-Fi") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.05f),
                    unfocusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.03f),
                    focusedBorderColor = nebulaColors.accent.copy(alpha = 0.5f),
                    unfocusedBorderColor = nebulaColors.textPrimary.copy(alpha = 0.12f),
                    focusedTextColor = nebulaColors.textPrimary,
                    unfocusedTextColor = nebulaColors.textPrimary
                )
            )
            Spacer(Modifier.height(8.dp))
            PresetTypeSelector(
                selected = type,
                onSelected = { type = it }
            )
            Spacer(Modifier.height(10.dp))
            IconPickerCarousel(
                title = "Иконка пресета",
                icons = iconPool,
                selectedIcon = selectedIcon,
                onSelect = { selectedIcon = it },
                onAddClick = { showAddIconDialog = true }
            )
        }

        if (showAddIconDialog) {
            NebulaMorphicDialog(
                onDismissRequest = { showAddIconDialog = false },
                title = "Добавить иконку",
                description = "Вставьте emoji или символ.",
                confirmButtonText = "Добавить",
                cancelButtonText = "Отмена",
                onConfirm = {
                    val glyph = sanitizeIconGlyph(customIconText)
                    if (glyph.isNotBlank()) {
                        if (!iconPool.contains(glyph)) {
                            iconPool = iconPool + glyph
                        }
                        selectedIcon = glyph
                    }
                    customIconText = ""
                    showAddIconDialog = false
                }
            ) {
                OutlinedTextField(
                    value = customIconText,
                    onValueChange = { customIconText = it },
                    label = { Text("Иконка") },
                    placeholder = { Text("Например: 🔥") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.05f),
                        unfocusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.03f),
                        focusedTextColor = nebulaColors.textPrimary,
                        unfocusedTextColor = nebulaColors.textPrimary
                    )
                )
            }
        }
    }

    editingPreset?.let { preset ->
        EditPresetDialog(
            preset = preset,
            allServers = allServers,
            onDismiss = { editingPreset = null },
            onSave = { updated ->
                NetworkProfileManager.savePreset(context, updated)
                presets = NetworkProfileManager.getPresets(context)
                editingPreset = null
            }
        )
    }
}

@Composable
private fun PresetRow(
    preset: NetworkPreset,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val iconGlyph = sanitizeIconGlyph(preset.iconGlyph.orEmpty()).ifBlank { fallbackIconByType(preset.type) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onActivate() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isActive) nebulaColors.accent.copy(alpha = 0.15f) else nebulaColors.textPrimary.copy(alpha = 0.07f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconGlyph,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = preset.name,
                color = if (isActive) nebulaColors.accent else nebulaColors.textPrimary,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = when {
                    preset.selectedServerKeys.isNotEmpty() -> "Серверов: ${preset.selectedServerKeys.size}"
                    preset.serverHost.isNullOrBlank() -> "Сервер: авто"
                    else -> "Сервер: 1 выбран"
                },
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = nebulaColors.textSecondary,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .clickable { onEdit() }
        )
        if (!preset.id.startsWith("preset_") || preset.id.startsWith("preset_custom_")) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = nebulaColors.statusError,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
private fun EditPresetDialog(
    preset: NetworkPreset,
    allServers: List<Server>,
    onDismiss: () -> Unit,
    onSave: (NetworkPreset) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    var showAddIconDialog by remember(preset.id) { mutableStateOf(false) }
    var customIconText by remember(preset.id) { mutableStateOf("") }
    var iconPool by remember(preset.id) {
        mutableStateOf((defaultPresetIcons() + sanitizeIconGlyph(preset.iconGlyph.orEmpty())).filter { it.isNotBlank() }.distinct())
    }
    var selectedIcon by remember(preset.id) {
        mutableStateOf(sanitizeIconGlyph(preset.iconGlyph.orEmpty()).ifBlank { fallbackIconByType(preset.type) })
    }
    val sortedServers = remember(allServers) {
        allServers
            .groupBy {
                "${it.name}|${it.uuid}|${it.host}|${it.port}|${it.profileUrl}|${it.templateUuid.orEmpty()}|${it.templateName.orEmpty()}"
            }
            .values
            .map(::chooseBestServerVariant)
    }
    val defaultKeys = remember(preset.id, sortedServers) {
        when {
            preset.selectedServerKeys.isNotEmpty() -> preset.selectedServerKeys.toSet()
            !preset.serverHost.isNullOrBlank() -> {
                sortedServers.filter {
                    it.host.equals(preset.serverHost, ignoreCase = true) &&
                        (preset.serverPort == null || it.port == preset.serverPort) &&
                        (preset.serverUuid.isNullOrBlank() || it.uuid == preset.serverUuid)
                }.map { NetworkProfileManager.buildServerKey(it) }.toSet()
            }
            else -> emptySet()
        }
    }
    var selectedServerKeys by remember(preset.id) { mutableStateOf(defaultKeys) }
    var updateSubOnStartup by remember(preset.id) { mutableStateOf(preset.updateSubOnStartup) }
    var pingOnStartup by remember(preset.id) { mutableStateOf(preset.pingOnStartup) }
    var pingOnUpdate by remember(preset.id) { mutableStateOf(preset.pingOnUpdate) }

    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = "Изменить пресет",
        description = "Выберите серверы, и приложение само возьмет самый быстрый доступный.",
        cancelButtonText = "Закрыть",
        confirmButtonText = "Сохранить",
        onConfirm = {
            val firstSelected = sortedServers.firstOrNull {
                selectedServerKeys.contains(NetworkProfileManager.buildServerKey(it))
            }
            onSave(
                preset.copy(
                    name = name,
                    serverHost = firstSelected?.host,
                    serverPort = firstSelected?.port,
                    serverUuid = firstSelected?.uuid,
                    serverProfileUrl = firstSelected?.profileUrl,
                    selectedServerKeys = selectedServerKeys.toList(),
                    iconGlyph = sanitizeIconGlyph(selectedIcon).ifBlank { fallbackIconByType(preset.type) },
                    pingOnStartup = pingOnStartup,
                    pingOnUpdate = pingOnUpdate,
                    updateSubOnStartup = updateSubOnStartup
                )
            )
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название") },
            leadingIcon = {
                Text(
                    text = sanitizeIconGlyph(selectedIcon).ifBlank { fallbackIconByType(preset.type) },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            placeholder = { Text("Название пресета") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.05f),
                unfocusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.03f),
                focusedBorderColor = nebulaColors.accent.copy(alpha = 0.5f),
                unfocusedBorderColor = nebulaColors.textPrimary.copy(alpha = 0.12f),
                focusedTextColor = nebulaColors.textPrimary,
                unfocusedTextColor = nebulaColors.textPrimary
            )
        )
        Spacer(Modifier.height(8.dp))
        IconPickerCarousel(
            title = "Иконка пресета",
            icons = iconPool,
            selectedIcon = selectedIcon,
            onSelect = { selectedIcon = it },
            onAddClick = { showAddIconDialog = true }
        )
        Spacer(Modifier.height(8.dp))
        MultiServerSelector(
            servers = sortedServers,
            selectedKeys = selectedServerKeys,
            onToggleServer = { key ->
                selectedServerKeys = if (selectedServerKeys.contains(key)) {
                    selectedServerKeys - key
                } else {
                    selectedServerKeys + key
                }
            },
            onClear = { selectedServerKeys = emptySet() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (selectedServerKeys.isEmpty()) {
                "Автовыбор по общим правилам."
            } else {
                "Выбрано ${selectedServerKeys.size}. При применении берется самый быстрый доступный."
            },
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            icon = Icons.Default.NetworkWifi,
            title = "Пинг при запуске",
            subtitle = "Пинговать серверы при старте приложения",
            checked = pingOnStartup,
            onCheckedChange = { pingOnStartup = it }
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            icon = Icons.Default.NetworkWifi,
            title = "Пинг после обновления",
            subtitle = "Пинговать серверы после обновления подписок",
            checked = pingOnUpdate,
            onCheckedChange = { pingOnUpdate = it }
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            icon = Icons.Default.NetworkWifi,
            title = "Обновлять при запуске",
            subtitle = "Обновлять подписки при старте для этого пресета",
            checked = updateSubOnStartup,
            onCheckedChange = { updateSubOnStartup = it }
        )
    }

    if (showAddIconDialog) {
        NebulaMorphicDialog(
            onDismissRequest = { showAddIconDialog = false },
            title = "Добавить иконку",
            description = "Вставьте emoji или символ.",
            confirmButtonText = "Добавить",
            cancelButtonText = "Отмена",
            onConfirm = {
                val glyph = sanitizeIconGlyph(customIconText)
                if (glyph.isNotBlank()) {
                    if (!iconPool.contains(glyph)) {
                        iconPool = iconPool + glyph
                    }
                    selectedIcon = glyph
                }
                customIconText = ""
                showAddIconDialog = false
            }
        ) {
            OutlinedTextField(
                value = customIconText,
                onValueChange = { customIconText = it },
                label = { Text("Иконка") },
                placeholder = { Text("Например: 🛰") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.05f),
                    unfocusedContainerColor = nebulaColors.onSurface.copy(alpha = 0.03f),
                    focusedTextColor = nebulaColors.textPrimary,
                    unfocusedTextColor = nebulaColors.textPrimary
                )
            )
        }
    }
}

@Composable
private fun IconPickerCarousel(
    title: String,
    icons: List<String>,
    selectedIcon: String,
    onSelect: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Text(
        text = title,
        color = nebulaColors.textSecondary,
        style = MaterialTheme.typography.labelMedium
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icons.forEach { icon ->
            val isSelected = icon == selectedIcon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) nebulaColors.accent.copy(alpha = 0.2f) else nebulaColors.onSurface.copy(alpha = 0.05f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) nebulaColors.accent else nebulaColors.textPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(icon) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(nebulaColors.onSurface.copy(alpha = 0.05f))
                .border(0.5.dp, nebulaColors.textPrimary.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+", color = nebulaColors.accent, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MultiServerSelector(
    servers: List<Server>,
    selectedKeys: Set<String>,
    onToggleServer: (String) -> Unit,
    onClear: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var expanded by remember { mutableStateOf(true) }
    val selectedServers = servers.filter { selectedKeys.contains(NetworkProfileManager.buildServerKey(it)) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Выбор серверов",
                        style = MaterialTheme.typography.labelLarge,
                        color = nebulaColors.textPrimary
                    )
                    Text(
                        text = if (selectedServers.isEmpty()) "Автовыбор" else "Выбрано: ${selectedServers.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = nebulaColors.textTertiary
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { expanded = !expanded },
                        label = {
                            Text(
                                if (expanded) "Свернуть" else "Развернуть",
                                maxLines = 1
                            )
                        }
                    )
                    AssistChip(
                        onClick = onClear,
                        label = { Text("Сброс", maxLines = 1) }
                    )
                }
            }

            if (selectedServers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedServers.forEach { server ->
                        InputChip(
                            selected = true,
                            onClick = { onToggleServer(NetworkProfileManager.buildServerKey(server)) },
                            label = { Text(server.name) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = nebulaColors.onSurface.copy(alpha = 0.05f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(servers) { server ->
                            val key = NetworkProfileManager.buildServerKey(server)
                            val selected = selectedKeys.contains(key)
                            val ping = server.ping ?: -1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleServer(key) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = server.name,
                                        color = if (selected) nebulaColors.accent else nebulaColors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    PresetServerPingChip(ping = ping)
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = nebulaColors.accent
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

@Composable
private fun PresetServerPingChip(ping: Int) {
    val nebulaColors = LocalNebulaColors.current
    val pingColor = when {
        ping == -1 -> nebulaColors.statusDisconnected
        ping <= 70 -> nebulaColors.statusConnected
        ping <= 120 -> Color(0xFFCDDC39)
        ping <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusDisconnected
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(pingColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = pingColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (ping == -1) "н/д" else "${ping}мс",
                style = MaterialTheme.typography.labelSmall,
                color = pingColor
            )
        }
    }
}

@Composable
private fun PresetTypeSelector(
    selected: NetworkPresetType,
    onSelected: (NetworkPresetType) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val items = listOf(
        NetworkPresetType.HOME to "Дом",
        NetworkPresetType.PUBLIC_WIFI to "Публичный Wi-Fi",
        NetworkPresetType.ROAMING to "Роуминг"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (type, title) ->
            val selectedItem = selected == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (selectedItem) nebulaColors.accent.copy(alpha = 0.2f) else nebulaColors.textPrimary.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelected(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (selectedItem) nebulaColors.accent else nebulaColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

