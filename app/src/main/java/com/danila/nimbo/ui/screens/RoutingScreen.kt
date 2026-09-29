@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import com.danila.nimbo.ui.components.*
import androidx.compose.foundation.layout.*

import android.app.Application
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.BuiltinRoutingProfiles
import com.danila.nimbo.model.RoutingProfile
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.i18n.formatEnglishCount
import com.danila.nimbo.ui.i18n.formatRussianCount
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.RoutingConfigurationApplier
import com.google.gson.Gson

private data class RoutingPreset(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val mode: String,
    val domainStrategy: String,
    val icon: ImageVector
)

private val builtinRoutingPresets = listOf(
    RoutingPreset(BuiltinRoutingProfiles.GLOBAL, "Глобальный", "Global", "Весь трафик через VPN", "Route all traffic through VPN", "block-proxy-direct", "AsIs", Icons.Default.Public),
    RoutingPreset(BuiltinRoutingProfiles.BYPASS_LAN, "Обход LAN", "Bypass LAN", "Локальные адреса идут напрямую", "Send local addresses directly", "block-proxy-direct", "AsIs", Icons.Default.Lan),
    RoutingPreset(BuiltinRoutingProfiles.CHINA_DIRECT, "Китай", "China", "Китайские сайты идут напрямую", "Send Chinese resources directly", "block-proxy-direct", "IPIfNonMatch", Icons.Default.Language),
    RoutingPreset(BuiltinRoutingProfiles.RUSSIA_DIRECT, "Россия", "Russia", "Российские ресурсы идут напрямую", "Send Russian resources directly", "block-proxy-direct", "IPIfNonMatch", Icons.Default.Route),
    RoutingPreset(
        BuiltinRoutingProfiles.ROSCOMVPN,
        "RoscomVPN",
        "RoscomVPN",
        "Заблокированные ресурсы через VPN, остальное напрямую",
        "Blocked resources through VPN, everything else directly",
        "block-direct-proxy",
        "IPIfNonMatch",
        Icons.Default.Shield
    )
)

@Composable
fun RoutingScreen(onNavigateBack: () -> Unit, onOpenModules: () -> Unit = {}) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val gson = remember { Gson() }
    val clipboard = LocalClipboardManager.current

    var builtinProfiles by remember { mutableStateOf(preferencesManager.builtinRoutingProfiles()) }
    var activeProfile by remember { mutableStateOf(preferencesManager.loadRoutingProfile()) }
    var activeBuiltinId by remember { mutableStateOf(preferencesManager.activeBuiltinRoutingProfileId()) }
    var routingEnabled by remember { mutableStateOf(preferencesManager.isRoutingEnabled) }
    var importMenuExpanded by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf<RoutingProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<RoutingProfile?>(null) }

    val invalidLinkMessage = t("Неверный формат ссылки", "Invalid routing link")
    val importedMessage = t("Профиль маршрутизации добавлен", "Routing profile added")
    val decodeErrorMessage = t("Не удалось прочитать профиль", "Could not read the profile")
    val emptyClipboardMessage = t("Буфер обмена пуст", "Clipboard is empty")
    val copiedMessage = t("Ссылка скопирована", "Link copied")
    val routingAppliedMessage = t(
        "Применяем правила: VPN переподключается",
        "Applying rules: VPN is reconnecting"
    )

    fun applyRoutingChange() {
        routingEnabled = preferencesManager.isRoutingEnabled
        if (RoutingConfigurationApplier.applyToActiveTunnel(context)) {
            Toast.makeText(context, routingAppliedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val activate: (RoutingPreset) -> Unit = { preset ->
        activeProfile = preferencesManager.activateBuiltinRoutingProfile(preset.id)
        activeBuiltinId = preset.id
        builtinProfiles = preferencesManager.builtinRoutingProfiles()
        applyRoutingChange()
    }

    fun importRoutingLink(rawText: String): Boolean {
        val pasteData = rawText.trim()
        val prefixes = listOf(
            "nimbo://routing/add/", "nimbo://routing/onadd/",
            "nebula://routing/add/", "nebula://routing/onadd/",
            "happ://routing/add/", "happ://routing/onadd/",
            "nebulaguard://routing/add/"
        )
        val base64Part = prefixes.firstNotNullOfOrNull { prefix ->
            if (pasteData.startsWith(prefix, ignoreCase = true)) pasteData.drop(prefix.length) else null
        }
        if (base64Part == null) {
            Toast.makeText(context, invalidLinkMessage, Toast.LENGTH_SHORT).show()
            return false
        }

        return runCatching {
            val decoded = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
            val newProfile = gson.fromJson(decoded, RoutingProfile::class.java)
            preferencesManager.saveImportedRoutingProfile(newProfile)
            activeProfile = newProfile
            activeBuiltinId = null
            applyRoutingChange()
            Toast.makeText(context, importedMessage, Toast.LENGTH_SHORT).show()
            true
        }.getOrElse {
            Toast.makeText(context, decodeErrorMessage, Toast.LENGTH_SHORT).show()
            false
        }
    }

    if (showImportDialog) {
        NebulaMorphicDialog(
            onDismissRequest = { showImportDialog = false; importText = "" },
            title = t("Импорт маршрутизации", "Import routing"),
            description = t("Вставьте ссылку профиля Nimbo.", "Paste a Nimbo routing profile link."),
            confirmButtonText = t("Импортировать", "Import"),
            onConfirm = {
                if (importRoutingLink(importText)) {
                    showImportDialog = false
                    importText = ""
                }
            }
        ) {
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = nebulaColors.textPrimary,
                    unfocusedTextColor = nebulaColors.textPrimary,
                    focusedBorderColor = nebulaColors.accent,
                    unfocusedBorderColor = nebulaColors.textTertiary.copy(alpha = 0.3f),
                    cursorColor = nebulaColors.accent
                ),
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text(
                        "nimbo://routing/add/...",
                        color = nebulaColors.textTertiary.copy(alpha = 0.55f)
                    )
                }
            )
        }
    }

    editingProfile?.let { profile ->
        BuiltinRoutingProfileEditorDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onSave = { edited ->
                val saved = preferencesManager.saveBuiltinRoutingProfile(edited)
                builtinProfiles = preferencesManager.builtinRoutingProfiles()
                if (activeBuiltinId == saved.id) {
                    activeProfile = saved
                    applyRoutingChange()
                }
                editingProfile = null
            },
            onReset = {
                val reset = preferencesManager.resetBuiltinRoutingProfile(profile.id.orEmpty())
                builtinProfiles = preferencesManager.builtinRoutingProfiles()
                if (activeBuiltinId == reset.id) {
                    activeProfile = reset
                    applyRoutingChange()
                }
                editingProfile = null
            }
        )
    }

    deletingProfile?.let { profile ->
        NebulaMorphicDialog(
            onDismissRequest = { deletingProfile = null },
            title = t("Удалить профиль?", "Delete profile?"),
            description = t(
                "«${profile.name}» исчезнет из списка. Его можно вернуть только сбросом встроенных профилей.",
                "“${profile.name}” will be removed from the list. It can only be restored by resetting built-in profiles."
            ),
            confirmButtonText = t("Удалить", "Delete"),
            confirmButtonColor = Color(0xFFE85D75),
            headerIcon = Icons.Default.Delete,
            headerIconTint = Color(0xFFE85D75),
            onConfirm = {
                val wasActive = activeBuiltinId == profile.id
                preferencesManager.deleteBuiltinRoutingProfile(profile.id.orEmpty())
                builtinProfiles = preferencesManager.builtinRoutingProfiles()
                activeBuiltinId = preferencesManager.activeBuiltinRoutingProfileId()
                activeProfile = preferencesManager.loadRoutingProfile()
                if (wasActive) applyRoutingChange()
                deletingProfile = null
            }
        )
    }

    val activePreset = builtinRoutingPresets.firstOrNull { it.id == activeBuiltinId }
    val activeDisplayName = activeProfile?.name?.takeIf { it.isNotBlank() }
        ?: activePreset?.let { t(it.nameRu, it.nameEn) }
        ?: t("Импортированный профиль", "Imported profile")

    NimboSubPageScaffold(
        title = t("Маршрутизация", "Routing"),
        onBack = onNavigateBack
    ) {
        RoutingOverviewCard(
            activeName = activeDisplayName,
            enabled = routingEnabled && activeProfile != null,
            profile = activeProfile,
            icon = activePreset?.icon ?: Icons.Default.AccountTree
        )

        Spacer(Modifier.height(8.dp))
        val groupShape = nimboControlShape(12.dp, 3.dp)
        Row(
            Modifier.fillMaxWidth().clip(groupShape).background(nebulaColors.panelFill)
                .border(1.dp, nebulaColors.panelBorder, groupShape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenModules, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Extension, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Модули", "Modules"))
            }
            Box(Modifier.width(1.dp).height(24.dp).background(nebulaColors.divider))
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { importMenuExpanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Импорт", "Import"))
                }
                DropdownMenu(expanded = importMenuExpanded, onDismissRequest = { importMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text(t("Из буфера", "From clipboard")) }, onClick = {
                        importMenuExpanded = false
                        val clip = clipboard.getText()?.text.orEmpty().trim()
                        if (clip.isBlank()) {
                            Toast.makeText(context, emptyClipboardMessage, Toast.LENGTH_SHORT).show()
                        } else if (!importRoutingLink(clip)) {
                            importText = clip
                            showImportDialog = true
                        }
                    })
                    DropdownMenuItem(text = { Text(t("Импорт ссылки", "Import link")) }, onClick = {
                        importMenuExpanded = false
                        showImportDialog = true
                    })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("Готовые профили", "Built-in profiles"),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (preferencesManager.hasDeletedBuiltinRoutingProfiles()) {
                TextButton(onClick = {
                    preferencesManager.restoreDeletedBuiltinRoutingProfiles()
                    builtinProfiles = preferencesManager.builtinRoutingProfiles()
                }) {
                    Text(t("Вернуть", "Restore"), fontWeight = FontWeight.Bold)
                }
            }
        }

        val visiblePresets = builtinRoutingPresets.mapNotNull { preset ->
            builtinProfiles.firstOrNull { it.id == preset.id }?.let { preset to it }
        }
        Column(
            Modifier.fillMaxWidth().clip(groupShape).background(nebulaColors.panelFill)
                .border(1.dp, nebulaColors.panelBorder, groupShape).selectableGroup()
        ) {
            visiblePresets.forEachIndexed { index, (preset, profile) ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(start = 48.dp), color = nebulaColors.divider)
                }
                RoutingProfileRow(
                    preset = preset,
                    profile = profile,
                    active = routingEnabled && preset.id == activeBuiltinId,
                    onActivate = { activate(preset) },
                    onCopyLink = {
                        val json = gson.toJson(profile)
                        val link = "nimbo://routing/add/" +
                            Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        clipboard.setText(AnnotatedString(link))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                    onEdit = { editingProfile = profile },
                    onDelete = { deletingProfile = profile }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RoutingOverviewCard(activeName: String, enabled: Boolean, profile: RoutingProfile?, icon: ImageVector) {
    val colors = LocalNebulaColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = nimboControlShape(12.dp, 3.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.panelFill)
            .border(1.dp, colors.panelBorder, shape).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Текущий режим", "Current mode"), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Text(
                    if (enabled) activeName else t("Без правил профиля", "No profile rules"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (enabled && profile != null) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) t("Скрыть детали", "Hide details") else t("Детали режима", "Mode details"), tint = colors.textSecondary)
                }
            }
        }
        if (expanded && enabled && profile != null) {
            Text(
                t("Правила приложений и модулей учитываются отдельно.", "App and module rules also apply."),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            RoutingTechnicalDetails(profile, profile.domainStrategy ?: t("По умолчанию", "Default"), profile.ruleOrder ?: t("По умолчанию", "Default"))
        }
    }
}

@Composable
private fun RoutingProfileRow(
    preset: RoutingPreset,
    profile: RoutingProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onCopyLink: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val displayName = profile.name?.takeIf { it.isNotBlank() } ?: t(preset.nameRu, preset.nameEn)
    var menuExpanded by remember { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(if (active) colors.accent.copy(alpha = 0.06f) else Color.Transparent)
    ) {
        // Selection and overflow are siblings: opening actions never activates a profile.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f)
                    .selectable(selected = active, role = Role.RadioButton, onClick = onActivate)
                    .heightIn(min = 64.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = active, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textTertiary)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        profile.description?.takeIf { it.isNotBlank() } ?: t(preset.descriptionRu, preset.descriptionEn),
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, t("Действия профиля: $displayName", "Profile actions: $displayName"), tint = colors.textSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(t("Подробности", "Details")) }, onClick = {
                        menuExpanded = false
                        detailsExpanded = !detailsExpanded
                    })
                    DropdownMenuItem(text = { Text(t("Скопировать ссылку", "Copy link")) }, onClick = {
                        menuExpanded = false
                        onCopyLink()
                    })
                    DropdownMenuItem(text = { Text(t("Изменить профиль", "Edit profile")) }, onClick = {
                        menuExpanded = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text(t("Удалить профиль", "Delete profile")) }, onClick = {
                        menuExpanded = false
                        onDelete()
                    })
                }
            }
        }
        if (detailsExpanded) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(profile.description?.takeIf { it.isNotBlank() } ?: t(preset.descriptionRu, preset.descriptionEn),
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                RoutingTechnicalDetails(profile, profile.domainStrategy ?: preset.domainStrategy, profile.ruleOrder ?: preset.mode)
                TextButton(onClick = { detailsExpanded = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(t("Скрыть подробности", "Hide details"))
                }
            }
        }
    }
}

@Composable
private fun RoutingTechnicalDetails(profile: RoutingProfile, domainStrategy: String, ruleOrder: String) {
    val colors = LocalNebulaColors.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            t(formatRussianCount(BuiltinRoutingProfiles.ruleCount(profile), "правило", "правила", "правил"),
                formatEnglishCount(BuiltinRoutingProfiles.ruleCount(profile), "rule", "rules")),
            style = MaterialTheme.typography.labelMedium, color = colors.textSecondary
        )
        Text(t("Стратегия доменов: ", "Domain strategy: ") + domainStrategy,
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(t("Порядок правил: ", "Rule order: ") + ruleOrder,
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun BuiltinRoutingProfileEditorDialog(
    profile: RoutingProfile,
    onDismiss: () -> Unit,
    onSave: (RoutingProfile) -> Unit,
    onReset: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile.name.orEmpty()) }
    var description by remember(profile) { mutableStateOf(profile.description.orEmpty()) }
    var globalProxy by remember(profile) { mutableStateOf(profile.isGlobalProxyEnabled()) }
    var bypassLocalIp by remember(profile) { mutableStateOf(profile.isBypassLocalIpEnabled()) }
    var domainStrategy by remember(profile) { mutableStateOf(profile.domainStrategy ?: "IPIfNonMatch") }
    var directSites by remember(profile) { mutableStateOf(profile.directSites.orEmpty().joinToString("\n")) }
    var directIp by remember(profile) { mutableStateOf(profile.directIp.orEmpty().joinToString("\n")) }
    var proxySites by remember(profile) { mutableStateOf(profile.proxySites.orEmpty().joinToString("\n")) }
    var proxyIp by remember(profile) { mutableStateOf(profile.proxyIp.orEmpty().joinToString("\n")) }
    var blockSites by remember(profile) { mutableStateOf(profile.blockSites.orEmpty().joinToString("\n")) }
    var blockIp by remember(profile) { mutableStateOf(profile.blockIp.orEmpty().joinToString("\n")) }

    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = t("Редактирование маршрутизации", "Edit routing"),
        description = t(
            "Правила применятся при следующем подключении VPN.",
            "Rules apply on the next VPN connection."
        ),
        confirmButtonText = t("Сохранить", "Save"),
        onConfirm = {
            onSave(
                profile.copy(
                    name = name.trim(),
                    description = description.trim(),
                    globalProxy = globalProxy.toString(),
                    bypassLocalIp = bypassLocalIp.toString(),
                    domainStrategy = domainStrategy,
                    directSites = parseRoutingEntries(directSites),
                    directIp = parseRoutingEntries(directIp),
                    proxySites = parseRoutingEntries(proxySites),
                    proxyIp = parseRoutingEntries(proxyIp),
                    blockSites = parseRoutingEntries(blockSites),
                    blockIp = parseRoutingEntries(blockIp)
                )
            )
        },
        headerIcon = Icons.Default.Route
    ) {
        RoutingEditorTextField(
            label = t("Название", "Name"),
            value = name,
            onValueChange = { name = it },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        RoutingEditorTextField(
            label = t("Описание", "Description"),
            value = description,
            onValueChange = { description = it },
            minHeight = 74.dp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            t("Поведение по умолчанию", "Default behavior"),
            color = LocalNebulaColors.current.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(7.dp))
        Button(
            onClick = { globalProxy = !globalProxy },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (globalProxy) LocalNebulaColors.current.accent else LocalNebulaColors.current.softFill,
                contentColor = if (globalProxy) contrastingLabel(LocalNebulaColors.current.accent) else LocalNebulaColors.current.textPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (globalProxy) t("Весь прочий трафик через VPN", "Other traffic through VPN")
                else t("Весь прочий трафик напрямую", "Other traffic direct"),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { bypassLocalIp = !bypassLocalIp },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (bypassLocalIp) LocalNebulaColors.current.softFill else LocalNebulaColors.current.controlFill,
                contentColor = LocalNebulaColors.current.textPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (bypassLocalIp) t("Локальные IP напрямую", "Local IPs direct")
                else t("Локальные IP через правила", "Local IPs follow rules"),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            t("Стратегия доменов", "Domain strategy"),
            color = LocalNebulaColors.current.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(7.dp))
        NimboToolActions(minCellDp = 100f) {
            listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { strategy ->
                val selected = domainStrategy == strategy
                Button(
                    onClick = { domainStrategy = strategy },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) LocalNebulaColors.current.accent.copy(alpha = 0.85f) else LocalNebulaColors.current.softFill,
                        contentColor = if (selected) contrastingLabel(LocalNebulaColors.current.accent) else LocalNebulaColors.current.textSecondary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(strategy, style = MaterialTheme.typography.labelSmall, softWrap = true)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            t("ПРАВИЛА", "RULES"),
            color = LocalNebulaColors.current.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            t("По одному значению на строку: domain:example.com, geosite:ru, geoip:ru, IP или CIDR.", "One value per line: domain:example.com, geosite:ru, geoip:ru, IP, or CIDR."),
            color = LocalNebulaColors.current.textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        RoutingEditorTextField(t("Сайты напрямую", "Direct domains"), directSites, { directSites = it })
        Spacer(Modifier.height(9.dp))
        RoutingEditorTextField(t("IP напрямую", "Direct IPs"), directIp, { directIp = it })
        Spacer(Modifier.height(9.dp))
        RoutingEditorTextField(t("Сайты через VPN", "Proxy domains"), proxySites, { proxySites = it })
        Spacer(Modifier.height(9.dp))
        RoutingEditorTextField(t("IP через VPN", "Proxy IPs"), proxyIp, { proxyIp = it })
        Spacer(Modifier.height(9.dp))
        RoutingEditorTextField(t("Блокируемые сайты", "Blocked domains"), blockSites, { blockSites = it })
        Spacer(Modifier.height(9.dp))
        RoutingEditorTextField(t("Блокируемые IP", "Blocked IPs"), blockIp, { blockIp = it })
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(t("Сбросить к версии приложения", "Reset to app defaults"))
        }
    }
}

@Composable
private fun RoutingEditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 92.dp
) {
    val colors = LocalNebulaColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 56.dp else minHeight),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.textTertiary.copy(alpha = 0.3f),
            cursorColor = colors.accent
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

private fun parseRoutingEntries(raw: String): List<String> = raw
    .split(Regex("[\\n,;]+"))
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
