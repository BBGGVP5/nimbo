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
import com.danila.nimbo.model.RoutingProfile
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.i18n.formatEnglishCount
import com.danila.nimbo.ui.i18n.formatRussianCount
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.Gson

private data class RoutingPreset(
    val nameRu: String,
    val nameEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val mode: String,
    val ruleCount: Int,
    val domainStrategy: String,
    val icon: ImageVector
) {
    fun toProfile(): RoutingProfile = RoutingProfile(
        name = nameRu,
        domainStrategy = domainStrategy
    )
}

private val builtinRoutingPresets = listOf(
    RoutingPreset("Глобальный", "Global", "Весь трафик через VPN", "Route all traffic through VPN", "block-proxy-direct", 1, "AsIs", Icons.Default.Public),
    RoutingPreset("Обход LAN", "Bypass LAN", "Локальные адреса идут напрямую", "Send local addresses directly", "block-proxy-direct", 8, "AsIs", Icons.Default.Lan),
    RoutingPreset("Китай", "China", "Китайские сайты идут напрямую", "Send Chinese resources directly", "block-proxy-direct", 9, "IPIfNonMatch", Icons.Default.Language),
    RoutingPreset("Россия", "Russia", "Российские ресурсы идут напрямую", "Send Russian resources directly", "block-proxy-direct", 33, "IPIfNonMatch", Icons.Default.Route),
    RoutingPreset(
        "RoscomVPN",
        "RoscomVPN",
        "Заблокированные ресурсы через VPN, остальное напрямую",
        "Blocked resources through VPN, everything else directly",
        "block-direct-proxy",
        31,
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

    var activeName by remember {
        mutableStateOf(
            runCatching {
                preferencesManager.routingProfileJson
                    ?.let { gson.fromJson(it, RoutingProfile::class.java)?.name }
            }.getOrNull() ?: builtinRoutingPresets.first().nameRu
        )
    }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    val invalidLinkMessage = t("Неверный формат ссылки", "Invalid routing link")
    val importedMessage = t("Профиль маршрутизации добавлен", "Routing profile added")
    val decodeErrorMessage = t("Не удалось прочитать профиль", "Could not read the profile")
    val emptyClipboardMessage = t("Буфер обмена пуст", "Clipboard is empty")
    val copiedMessage = t("Ссылка скопирована", "Link copied")
    val importedProfileName = t("Импортированный профиль", "Imported profile")

    val activate: (RoutingPreset) -> Unit = { preset ->
        activeName = preset.nameRu
        preferencesManager.routingProfileJson = gson.toJson(preset.toProfile())
        preferencesManager.isRoutingEnabled = true
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
            preferencesManager.routingProfileJson = decoded
            preferencesManager.isRoutingEnabled = true
            activeName = newProfile.name?.takeIf { it.isNotBlank() } ?: importedProfileName
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

    val activePreset = builtinRoutingPresets.firstOrNull { it.nameRu == activeName }
    val activeDisplayName = activePreset?.let { t(it.nameRu, it.nameEn) } ?: activeName
    val routingEnabled = preferencesManager.isRoutingEnabled

    NimboSubPageScaffold(
        title = t("Маршрутизация", "Routing"),
        subtitle = t("Выберите, какой трафик направлять через VPN", "Choose which traffic goes through VPN"),
        onBack = onNavigateBack
    ) {
        RoutingOverviewCard(
            activeName = activeDisplayName,
            enabled = routingEnabled,
            ruleCount = activePreset?.ruleCount,
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

        Spacer(Modifier.height(24.dp))
        Text(
            text = t("ГОТОВЫЕ ПРОФИЛИ", "BUILT-IN PROFILES"),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
        )

        builtinRoutingPresets.forEach { preset ->
            RoutingProfileCard(
                preset = preset,
                active = routingEnabled && preset.nameRu == activeName,
                onActivate = { activate(preset) },
                onCopyLink = {
                    val json = gson.toJson(preset.toProfile())
                    val link = "nimbo://routing/add/" +
                        Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RoutingOverviewCard(activeName: String, enabled: Boolean, ruleCount: Int?, icon: ImageVector) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panelFill)
            .border(1.dp, colors.panelBorder, shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(28.dp))
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
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(if (accent) colors.accent.copy(alpha = 0.14f) else colors.controlFill)
            .border(1.dp, if (accent) colors.accent.copy(alpha = 0.48f) else colors.panelBorder, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (accent) colors.accent else colors.textSecondary, modifier = Modifier.size(21.dp))
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
    active: Boolean,
    onActivate: () -> Unit,
    onCopyLink: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panelFill)
            .border(1.dp, if (active) colors.accent.copy(alpha = 0.68f) else colors.panelBorder, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onActivate)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) colors.accent.copy(alpha = 0.18f) else colors.softFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(preset.icon, null, tint = if (active) colors.accent else colors.textSecondary, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t(preset.nameRu, preset.nameEn),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = t(preset.descriptionRu, preset.descriptionEn),
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
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RoutingMetaChip(
                t(
                    formatRussianCount(preset.ruleCount, "правило", "правила", "правил"),
                    formatEnglishCount(preset.ruleCount, "rule", "rules")
                )
            )
            RoutingMetaChip(preset.domainStrategy)
            RoutingMetaChip(preset.mode)
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (active) t("Используется сейчас", "Currently in use") else t("Нажмите, чтобы выбрать", "Tap to select"),
                color = if (active) colors.accent else colors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onCopyLink,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.softFill,
                    contentColor = colors.textSecondary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Ссылка", "Link"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoutingMetaChip(text: String) {
    val colors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.softFill)
            .padding(horizontal = 8.dp, vertical = 5.dp)
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
