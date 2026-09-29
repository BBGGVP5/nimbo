package com.danila.nimbo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danila.nimbo.sync.*
import com.danila.nimbo.service.SubscriptionUpdateEvents
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CloudSyncPanel(preferences: PreferencesManager) {
    val context = LocalContext.current
    val vault = remember { CloudCredentials(context.applicationContext) }
    var saved by remember { mutableStateOf<CloudSettings?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<List<SubscriptionProfile>?>(null) }
    var knownEtag by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val colors = LocalNebulaColors.current
    val client = remember { CloudWebDav() }
    val uploaded = t("Профили сохранены в облаке", "Profiles saved to cloud")
    val imported = t("Профили синхронизированы. VPN не запускался.", "Profiles synced. VPN was not started.")
    val failed = t("Не удалось выполнить операцию. Проверьте адрес и пароли.", "Operation failed. Check the address and passwords.")
    val conflict = t("В облаке уже есть копия или она изменилась. Сначала загрузите её.", "Cloud copy exists or changed. Download it first.")
    val disconnected = t("Для применения профилей отключите VPN", "Disconnect VPN to apply profiles")
    LaunchedEffect(vault) {
        val loaded = withContext(Dispatchers.IO) { vault.load() }
        saved = loaded
        knownEtag = loaded?.let { withContext(Dispatchers.IO) { vault.readRevision(it.url) } }
    }
    Surface(shape = RoundedCornerShape(22.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Icon(Icons.Default.CloudSync, null, tint = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text(t("Профили в облаке", "Cloud profiles"), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            }
            Text(t("WebDAV · подписки и конфигурации, зашифрованные вашим паролем. Отправка и загрузка — по нажатию.",
                "WebDAV · subscriptions and configurations encrypted with your password. Upload and download are manual."),
                color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { showSettings = true }) { Text(t("Настроить", "Configure")) }
                TextButton(enabled = saved != null && !busy, onClick = { scope.launch {
                    busy = true; notice = null
                    try {
                        val settings = saved ?: return@launch
                        val source = withContext(Dispatchers.IO) { CloudProfiles.encode(preferences.loadProfiles()) }
                        knownEtag = withContext(Dispatchers.IO) { client.upload(settings, source, knownEtag) }
                        withContext(Dispatchers.IO) { vault.saveRevision(settings.url, knownEtag) }
                        notice = uploaded
                    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notice = if ((e as? CloudFailure)?.reason == "CONFLICT") conflict else failed }
                    finally { busy = false }
                } }) { Text(t("Отправить", "Upload")) }
                TextButton(enabled = saved != null && !busy, onClick = { scope.launch {
                    busy = true; notice = null
                    try {
                        val result = withContext(Dispatchers.IO) { client.download(saved ?: error("No settings")) }
                        pending = withContext(Dispatchers.Default) { CloudProfiles.decode(result.source) }
                        knownEtag = result.etag
                        withContext(Dispatchers.IO) { vault.saveRevision(saved!!.url, result.etag) }
                    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notice = failed }
                    finally { busy = false }
                } }) { Text(t("Загрузить", "Download")) }
            }
        }
    }
    if (pending != null) AlertDialog(onDismissRequest = { pending = null }, title = { Text(t("Применить профили?", "Apply profiles?")) },
        text = { Text(t("Подписки с совпадающими адресами обновятся. Остальные локальные профили сохранятся. VPN не будет запущен.",
            "Subscriptions with matching addresses will be updated. Other local profiles will be kept. VPN will not start.")) },
        confirmButton = { TextButton(onClick = {
            val incoming = pending.orEmpty(); pending = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        check(!preferences.vpnConnectionDesired && VpnManager.state.value == VpnState.DISCONNECTED)
                        preferences.updateProfiles { local ->
                            check(!preferences.vpnConnectionDesired && VpnManager.state.value == VpnState.DISCONNECTED)
                            CloudProfiles.merge(local, incoming)
                        }
                        SubscriptionUpdateEvents.notifyProfilesChanged()
                    }
                    notice = imported
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notice = disconnected }
            }
        }) { Text(t("Применить", "Apply")) } }, dismissButton = { TextButton(onClick = { pending = null }) { Text(t("Отмена", "Cancel")) } })
    if (showSettings) {
        var url by remember { mutableStateOf(saved?.url.orEmpty()) }
        var user by remember { mutableStateOf(saved?.user.orEmpty()) }
        var password by remember { mutableStateOf(saved?.password.orEmpty()) }
        var encryption by remember { mutableStateOf(saved?.encryptionPassword.orEmpty()) }
        var settingsError by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("WebDAV") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Полный HTTPS-адрес файла nimbo.json в существующей папке облака. Используйте пароль приложения WebDAV.",
                    "Full HTTPS URL of nimbo.json in an existing cloud folder. Use a WebDAV app password."), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, label = { Text(t("Адрес файла", "File URL")) }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text(t("Логин", "Username")) }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text(t("Пароль WebDAV", "WebDAV password")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(encryption, { encryption = it }, label = { Text(t("Пароль шифрования · от 8 знаков", "Encryption password · 8+ characters")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (settingsError) Text(failed)
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            if (!url.startsWith("https://") || encryption.length < 8) { settingsError = true; return@TextButton }
            scope.launch {
                busy = true
                try {
                    val settings = CloudSettings(url.trim(), user, password, encryption)
                    withContext(Dispatchers.IO) { vault.save(settings); vault.saveRevision(settings.url, null) }
                    saved = settings; knownEtag = null; showSettings = false
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; settingsError = true }
                finally { busy = false }
            }
        }) { Text(t("Сохранить", "Save")) } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text(t("Отмена", "Cancel")) } })
    }
}
