package com.danila.nimbo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import com.danila.nimbo.ui.i18n.t
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danila.nimbo.utils.SupportDiagnosticStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.compose.runtime.Composable
internal fun DeveloperSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = t("Отправить диагностику Nimbo", "Share Nimbo diagnostics")
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(t("Отчёт, конфиг ядра и заголовки подписки сохраняются без паролей, ключей, адресов серверов и ссылок. Отправка — только через выбранное вами приложение.", "Save the report, core configuration and subscription headers without credentials, server addresses or links. Share only through an app you choose."))
        Button(enabled = !busy, modifier = Modifier.padding(top = 16.dp), onClick = {
            busy = true; error = false
            scope.launch {
                try {
                    val files = withContext(Dispatchers.IO) { SupportDiagnosticStore.export(context) }
                    SupportDiagnosticStore.share(context, files, shareTitle)
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }) { Text(if (busy) t("Сохраняем…", "Saving…") else t("Снять диагностику", "Capture diagnostics")) }
        if (error) Text(t("Не удалось сохранить или открыть отчёт. Попробуйте ещё раз.", "Could not save or open the report. Please try again."), Modifier.padding(top = 8.dp))
    }
}
