package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun NimboAddProfileCard(actions: NimboUiActions) {
    var showSheet by remember { mutableStateOf(false) }
    // Keep the entry card mounted behind the dialog so keyboard focus can return to it.
    NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("Добавить подписку", style = NimboSectionTitleStyle)
            NimboPrimaryAction("Ввести ссылку", { showSheet = true }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NimboImportTile("Буфер", NimboIconName.LIST, Modifier.weight(1f), actions.onImportClipboard)
                NimboImportTile("Файл", NimboIconName.DOWNLOAD, Modifier.weight(1f), actions.onImportFile)
                NimboImportTile("QR-код", NimboIconName.SEARCH, Modifier.weight(1f), actions.onScanQr)
            }
        }
    }
    if (showSheet) NimboAddProfileSheet(actions) { showSheet = false }
}

@Composable
private fun NimboAddProfileSheet(actions: NimboUiActions, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val ready = link.trim().isNotEmpty()
    NimboSettingsDialog("Добавить профиль", onDismiss, footer = {
        NimboPrimaryAction("Импортировать", onClick = {
            focusManager.clearFocus()
            actions.onImportSubscription(link.trim())
            onDismiss()
        }, modifier = Modifier.fillMaxWidth(), enabled = ready)
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Ссылка или конфигурация", Modifier.weight(1f), style = NimboBodyStyle)
            NimboSettingsInfo("Импорт профиля", "Вставьте ссылку подписки, отдельного сервера или текст конфигурации. Ссылка обрабатывается на устройстве.")
        }
        BasicTextField(value = link, onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
                .semantics { contentDescription = "Ссылка или конфигурация профиля" }
                .nimboControlSurface(RoundedCornerShape(12.dp)).padding(12.dp),
            maxLines = 6,
            textStyle = NimboBodyStyle.copy(color = NimboPalette.Text), cursorBrush = SolidColor(NimboPalette.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (link.isEmpty()) BasicText("https://…  или  vless://…", style = NimboBodyStyle)
                    inner()
                }
            })
        SettingsDivider()
        // Stacked rows remain readable with a large system font and a short keyboard viewport.
        NimboSettingsAction("Вставить из буфера", Modifier.fillMaxWidth()) {
            focusManager.clearFocus(); actions.onImportClipboard(); onDismiss()
        }
        NimboSettingsAction("Открыть файл", Modifier.fillMaxWidth()) {
            focusManager.clearFocus(); actions.onImportFile(); onDismiss()
        }
        NimboSettingsAction("Сканировать QR-код", Modifier.fillMaxWidth()) {
            focusManager.clearFocus(); actions.onScanQr(); onDismiss()
        }
    }
}

@Composable
private fun NimboImportTile(title: String, icon: NimboIconName, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 64.dp).nimboControlSurface(RoundedCornerShape(12.dp))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NimboIcon(icon, tint = NimboPalette.TextSecondary, modifier = Modifier.size(20.dp))
        BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text))
    }
}
