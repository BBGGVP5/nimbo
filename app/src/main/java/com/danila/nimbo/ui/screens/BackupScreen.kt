package com.danila.nimbo.ui.screens

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.QrCodeDisplayBottomSheet
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object BackupCodec {
    private const val FORMAT = "nebula_backup_v1"
    private const val ITERATIONS = 120_000
    private const val KEY_SIZE = 256

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun wrap(settingsJson: String, password: String): String {
        if (password.isBlank()) {
            return JSONObject().apply {
                put("format", FORMAT)
                put("encrypted", false)
                put("data", Base64.encodeToString(settingsJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            }.toString()
        }

        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(settingsJson.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("format", FORMAT)
            put("encrypted", true)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
    }

    fun unwrap(input: String, password: String): String {
        val root = JSONObject(input)
        val format = root.optString("format")
        if (format != FORMAT) {
            if (root.has("prefs") || root.optString("format") == "nebula_settings_v2") return input
            throw IllegalArgumentException("Неверный формат резервной копии")
        }
        val encrypted = root.optBoolean("encrypted", false)
        val data = root.optString("data")
        if (data.isBlank()) throw IllegalArgumentException("Пустая резервная копия")

        if (!encrypted) {
            return String(Base64.decode(data, Base64.DEFAULT), Charsets.UTF_8)
        }

        if (password.isBlank()) throw IllegalArgumentException("Введите пароль для расшифровки")
        val salt = Base64.decode(root.optString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(root.optString("iv"), Base64.DEFAULT)
        val encryptedBytes = Base64.decode(data, Base64.DEFAULT)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(encryptedBytes)
        return String(decrypted, Charsets.UTF_8)
    }

    fun buildLink(payload: String): String {
        val encoded = Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "nimbo://backup/import/$encoded"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(app) }
    val nebulaColors = LocalNebulaColors.current

    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var exportedPayload by remember { mutableStateOf<String?>(null) }
    var exportedLink by remember { mutableStateOf<String?>(null) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showLargeLinkDialog by remember { mutableStateOf(false) }

    val scrollState = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    fun notify(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun buildExportPayload(): String {
        val settingsJson = preferencesManager.exportSettings()
        return BackupCodec.wrap(settingsJson, password.trim())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = buildExportPayload()
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
            exportedPayload = payload
            exportedLink = BackupCodec.buildLink(payload)
            notify("Резервная копия сохранена")
        }.onFailure {
            notify("Ошибка экспорта файла")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
            val settingsJson = BackupCodec.unwrap(content, password.trim())
            if (preferencesManager.importSettings(settingsJson)) {
                Toast.makeText(context, "Резервная копия восстановлена. Перезапуск...", Toast.LENGTH_LONG).show()
                (context as? Activity)?.recreate()
            } else {
                notify("Не удалось применить настройки")
            }
        }.onFailure {
            notify(it.message ?: "Ошибка восстановления")
        }
    }

    if (showLargeLinkDialog) {
        NebulaMorphicDialog(
            onDismissRequest = { showLargeLinkDialog = false },
            title = "Отсканируйте на другом устройстве",
            description = "Резервная копия слишком большая для ссылки. Используйте файл.",
            confirmButtonText = "Понятно",
            onConfirm = { showLargeLinkDialog = false }
        ) { }
    }

    if (showQrSheet && !exportedLink.isNullOrBlank()) {
        QrCodeDisplayBottomSheet(
            url = exportedLink.orEmpty(),
            onDismiss = { showQrSheet = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            GlassHeader(
                title = "Резервное копирование",
                icon = Icons.Default.Description,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Создать резервную копию",
                            style = MaterialTheme.typography.titleLarge,
                            color = nebulaColors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Сохранит подписки, серверы, маршрутизацию и ваши настройки в один файл.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = nebulaColors.textSecondary
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { advancedExpanded = !advancedExpanded }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (advancedExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = nebulaColors.accent
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Дополнительные параметры",
                                style = MaterialTheme.typography.titleMedium,
                                color = nebulaColors.textPrimary
                            )
                        }

                        if (advancedExpanded) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Пароль (опционально)") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(Icons.Default.Key, contentDescription = null)
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors()
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                exportLauncher.launch("NebulaGuard_backup_${System.currentTimeMillis()}.json")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = nebulaColors.accent)
                        ) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Экспортировать")
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BackupActionTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.QrCode2,
                                title = "Показать QR",
                                compact = true,
                                onClick = {
                                    runCatching {
                                        val payload = buildExportPayload()
                                        val link = BackupCodec.buildLink(payload)
                                        exportedPayload = payload
                                        exportedLink = link
                                        if (link.length > 2500) {
                                            showLargeLinkDialog = true
                                        } else {
                                            showQrSheet = true
                                        }
                                    }.onFailure {
                                        notify("Не удалось сформировать QR")
                                    }
                                }
                            )
                            BackupActionTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.ContentCopy,
                                title = "Копировать ссылку",
                                compact = true,
                                onClick = {
                                    runCatching {
                                        val payload = exportedPayload ?: buildExportPayload()
                                        val link = exportedLink ?: BackupCodec.buildLink(payload).also { exportedLink = it }
                                        exportedPayload = payload
                                        if (link.length > 2500) {
                                            showLargeLinkDialog = true
                                            return@runCatching
                                        }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("NebulaGuard backup link", link))
                                        notify("Ссылка скопирована")
                                    }.onFailure {
                                        notify("Не удалось скопировать ссылку")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Восстановить", style = MaterialTheme.typography.titleLarge, color = nebulaColors.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Выберите файл резервной копии и примените сохраненные настройки.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = nebulaColors.textSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        BackupActionTile(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.FileDownload,
                            title = "Выбрать файл",
                            compact = true,
                            horizontal = true,
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = nebulaColors.accent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        RowIconText(
                            icon = Icons.Default.Info,
                            text = "Файл не содержит пароли SOCKS5 и VPN-разрешения — они регенерируются автоматически на новом устройстве."
                        )
                        Spacer(Modifier.height(8.dp))
                        RowIconText(
                            icon = Icons.Default.FileUpload,
                            text = "Если ссылка слишком длинная для QR/буфера, используйте перенос через файл."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    compact: Boolean = false,
    horizontal: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    Surface(
        modifier = modifier
            .height(
                when {
                    compact && horizontal -> 64.dp
                    compact -> 86.dp
                    else -> 112.dp
                }
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = nebulaColors.surface.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            nebulaColors.accent.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (horizontal) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
                    Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
                    Text(
                        text = title,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                        color = nebulaColors.textPrimary
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = nebulaColors.accent, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
                    Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
                    Text(
                        text = title,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = nebulaColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun RowIconText(
    icon: ImageVector,
    text: String
) {
    val nebulaColors = LocalNebulaColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = nebulaColors.accent)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = nebulaColors.textSecondary
        )
    }
}

