package com.danila.nimbo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.vpn.ConnectionFailure

/**
 * Диалог неудачного подключения: настоящая причина, следующий шаг и одна кнопка,
 * которая собирает безопасную диагностику в буфер обмена — её можно сразу
 * отправить в поддержку, не объясняя, где в приложении лежат логи.
 */
@Composable
fun ConnectionErrorDialog(
    failure: ConnectionFailure,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit = {}
) {
    val colors = LocalNebulaColors.current
    val context = LocalContext.current
    val materialYou = colors.isMaterialYou
    var copied by remember { mutableStateOf(false) }
    // Строки берём заранее: t() — composable и внутри onClick не вызывается.
    val copiedMessage = t("Диагностика скопирована", "Diagnostics copied")

    val surface = if (materialYou) MaterialTheme.colorScheme.surfaceContainerHigh else colors.surface
    val title = if (materialYou) MaterialTheme.colorScheme.onSurface else colors.textPrimary
    val body = if (materialYou) MaterialTheme.colorScheme.onSurfaceVariant else colors.textSecondary
    val accent = if (materialYou) MaterialTheme.colorScheme.primary else colors.accent
    val onAccent = if (materialYou) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White
    val outline = if (materialYou) MaterialTheme.colorScheme.outlineVariant else colors.textPrimary.copy(alpha = 0.16f)
    val danger = colors.statusError

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(30.dp),
            color = surface,
            tonalElevation = 0.dp,
            shadowElevation = 20.dp
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(danger.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = danger,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = t("Не удалось подключиться", "Connection failed"),
                    color = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = failure.reason,
                    color = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = failure.nextStep,
                    color = body,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (failure.technical.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.textPrimary.copy(alpha = 0.05f))
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Text(
                            text = failure.technical,
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val report = buildSupportReport(context, failure)
                        copyToClipboard(context, report)
                        copied = true
                        onCopied(copiedMessage)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = if (copied) {
                            t("Скопировано", "Copied")
                        } else {
                            t("Скопировать логи", "Copy logs")
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = title)
                ) {
                    Text(t("Закрыть", "Close"), fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = t(
                            "В отчёт не попадают ссылки подписки, адреса и ключи",
                            "The report contains no subscription links, addresses or keys"
                        ),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Отчёт для поддержки: причина сбоя + стандартная диагностика приложения.
 * Логи проходят через LogSanitizer внутри Logger, поэтому ссылки подписки,
 * адреса серверов и ключи в буфер не попадают.
 */
private fun buildSupportReport(context: Context, failure: ConnectionFailure): String = buildString {
    appendLine("Причина: ${failure.reason}")
    appendLine("Следующий шаг: ${failure.nextStep}")
    if (failure.technical.isNotBlank()) appendLine("Сообщение ядра: ${failure.technical}")
    appendLine()
    append(Logger.buildDiagnosticReport(context))
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nimbo diagnostics", text))
    }
}
