package com.danila.nimbo.ui.screens

import android.app.Application
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
fun RoutingScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val gson = remember { Gson() }
    val clipboard = LocalClipboardManager.current

    var builtinProfiles by remember { mutableStateOf(preferencesManager.builtinRoutingProfiles()) }
    var activeProfile by remember { mutableStateOf(preferencesManager.loadRoutingProfile()) }
    var activeBuiltinId by remember { mutableStateOf(preferencesManager.activeBuiltinRoutingProfileId()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf<RoutingProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<RoutingProfile?>(null) }

    val invalidLinkMessage = t("Неверный формат ссылки", "Invalid routing link")
    val importedMessage = t("Профиль маршрутизации добавлен", "Routing profile added")
    val decodeErrorMessage = t("Не удалось прочитать профиль", "Could not read the profile")
    val emptyClipboardMessage = t("Буфер обмена пуст", "Clipboard is empty")
    val copiedMessage = t("Ссылка скопирована", "Link copied")
    val activate: (RoutingPreset) -> Unit = { preset ->
        activeProfile = preferencesManager.activateBuiltinRoutingProfile(preset.id)
        activeBuiltinId = preset.id
        builtinProfiles = preferencesManager.builtinRoutingProfiles()
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
                if (activeBuiltinId == saved.id) activeProfile = saved
                editingProfile = null
            },
            onReset = {
                val reset = preferencesManager.resetBuiltinRoutingProfile(profile.id.orEmpty())
                builtinProfiles = preferencesManager.builtinRoutingProfiles()
                if (activeBuiltinId == reset.id) activeProfile = reset
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
                preferencesManager.deleteBuiltinRoutingProfile(profile.id.orEmpty())
                builtinProfiles = preferencesManager.builtinRoutingProfiles()
                activeBuiltinId = preferencesManager.activeBuiltinRoutingProfileId()
                activeProfile = preferencesManager.loadRoutingProfile()
                deletingProfile = null
            }
        )
    }

    val activePreset = builtinRoutingPresets.firstOrNull { it.id == activeBuiltinId }
    val activeDisplayName = activeProfile?.name?.takeIf { it.isNotBlank() }
        ?: activePreset?.let { t(it.nameRu, it.nameEn) }
        ?: builtinProfiles.firstOrNull()?.name
        ?: t("Нет профиля", "No profile")
    val routingEnabled = preferencesManager.isRoutingEnabled

    NimboSubPageScaffold(
        title = t("Маршрутизация", "Routing"),
        subtitle = t("Выберите, какой трафик направлять через VPN", "Choose which traffic goes through VPN"),
        onBack = onNavigateBack
    ) {
        RoutingOverviewCard(
            activeName = activeDisplayName,
            enabled = routingEnabled,
            ruleCount = activeProfile?.let(BuiltinRoutingProfiles::ruleCount),
            icon = activePreset?.icon ?: Icons.Default.AccountTree
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoutingQuickAction(
                icon = Icons.Default.ContentPaste,
                label = t("Из буфера", "From clipboard"),
                modifier = Modifier.weight(1f)
            ) {
                val clip = clipboard.getText()?.text.orEmpty().trim()
                if (clip.isBlank()) {
                    Toast.makeText(context, emptyClipboardMessage, Toast.LENGTH_SHORT).show()
                } else if (!importRoutingLink(clip)) {
                    importText = clip
                    showImportDialog = true
                }
            }
            RoutingQuickAction(
                icon = Icons.Default.FileDownload,
                label = t("Импорт ссылки", "Import link"),
                modifier = Modifier.weight(1f),
                accent = true
            ) { showImportDialog = true }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("ГОТОВЫЕ ПРОФИЛИ", "BUILT-IN PROFILES"),
                color = nebulaColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            if (preferencesManager.hasDeletedBuiltinRoutingProfiles()) {
                TextButton(onClick = {
                    preferencesManager.restoreDeletedBuiltinRoutingProfiles()
                    builtinProfiles = preferencesManager.builtinRoutingProfiles()
                }) {
                    Text(t("Вернуть", "Restore"), fontWeight = FontWeight.Bold)
                }
            }
        }

        builtinRoutingPresets.forEach { preset ->
            val profile = builtinProfiles.firstOrNull { it.id == preset.id } ?: return@forEach
            RoutingProfileCard(
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
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RoutingOverviewCard(activeName: String, enabled: Boolean, ruleCount: Int?, icon: ImageVector) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panelFill)
            .border(1.dp, colors.panelBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("Активный профиль", "Active profile"),
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = activeName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (ruleCount != null) {
                Text(
                    text = t(
                        formatRussianCount(ruleCount, "правило", "правила", "правил"),
                        formatEnglishCount(ruleCount, "rule", "rules")
                    ),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (enabled) colors.accent.copy(alpha = 0.16f) else colors.softFill)
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Text(
                text = if (enabled) t("ВКЛ", "ON") else t("ВЫКЛ", "OFF"),
                color = if (enabled) colors.accent else colors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun RoutingQuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (accent) colors.accent.copy(alpha = 0.14f) else colors.controlFill)
            .border(1.dp, if (accent) colors.accent.copy(alpha = 0.48f) else colors.panelBorder, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (accent) colors.accent else colors.textSecondary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (accent) colors.accent else colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoutingProfileCard(
    preset: RoutingPreset,
    profile: RoutingProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onCopyLink: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panelFill)
            .border(1.dp, if (active) colors.accent.copy(alpha = 0.68f) else colors.panelBorder, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onActivate)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.accent.copy(alpha = 0.18f) else colors.softFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(preset.icon, null, tint = if (active) colors.accent else colors.textSecondary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name?.takeIf { it.isNotBlank() } ?: t(preset.nameRu, preset.nameEn),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.description?.takeIf { it.isNotBlank() }
                        ?: t(preset.descriptionRu, preset.descriptionEn),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (active) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RoutingMetaChip(
                t(
                    formatRussianCount(BuiltinRoutingProfiles.ruleCount(profile), "правило", "правила", "правил"),
                    formatEnglishCount(BuiltinRoutingProfiles.ruleCount(profile), "rule", "rules")
                )
            )
            RoutingMetaChip(profile.domainStrategy ?: preset.domainStrategy)
            RoutingMetaChip(profile.ruleOrder ?: preset.mode)
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (active) t("Используется сейчас", "Currently in use") else t("Нажмите, чтобы выбрать", "Tap to select"),
                color = if (active) colors.accent else colors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            RoutingProfileIconAction(
                icon = Icons.Default.ContentCopy,
                contentDescription = t("Скопировать ссылку", "Copy link"),
                onClick = onCopyLink
            )
            Spacer(Modifier.width(6.dp))
            RoutingProfileIconAction(
                icon = Icons.Default.Edit,
                contentDescription = t("Изменить профиль", "Edit profile"),
                onClick = onEdit,
            )
            Spacer(Modifier.width(6.dp))
            RoutingProfileIconAction(
                icon = Icons.Default.Delete,
                contentDescription = t("Удалить профиль", "Delete profile"),
                danger = true,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun RoutingProfileIconAction(
    icon: ImageVector,
    contentDescription: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val tint = if (danger) Color(0xFFE85D75) else colors.textSecondary
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(if (danger) tint.copy(alpha = 0.12f) else colors.softFill)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
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
                contentColor = if (globalProxy) Color.White else LocalNebulaColors.current.textPrimary
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
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { strategy ->
                val selected = domainStrategy == strategy
                Button(
                    onClick = { domainStrategy = strategy },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) LocalNebulaColors.current.accent.copy(alpha = 0.85f) else LocalNebulaColors.current.softFill,
                        contentColor = if (selected) Color.White else LocalNebulaColors.current.textSecondary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(strategy, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
        modifier = Modifier.fillMaxWidth().height(if (singleLine) 56.dp else minHeight),
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

@Composable
private fun RoutingMetaChip(text: String) {
    val colors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.softFill)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
