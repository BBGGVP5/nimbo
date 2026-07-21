package com.danila.nimbo.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.danila.nimbo.ui.components.*
import com.danila.nimbo.ui.theme.*
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.BuildConfig
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current

    var killSwitch by remember { mutableStateOf(preferencesManager.killSwitch) }
    var autoConnect by remember { mutableStateOf(preferencesManager.autoConnect) }
    var showSpeed by remember { mutableStateOf(preferencesManager.showSpeed) }
    var tunnelMode by remember { mutableStateOf(preferencesManager.tunnelMode) }
    var colorTheme by remember { mutableStateOf(preferencesManager.colorTheme) }

    LaunchedEffect(killSwitch) { preferencesManager.killSwitch = killSwitch }
    LaunchedEffect(autoConnect) { preferencesManager.autoConnect = autoConnect }
    LaunchedEffect(showSpeed) { preferencesManager.showSpeed = showSpeed }
    LaunchedEffect(tunnelMode) { preferencesManager.tunnelMode = tunnelMode }
    LaunchedEffect(colorTheme) { preferencesManager.colorTheme = colorTheme }

    val clipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    var showUrlSchemesDialog by remember { mutableStateOf(false) }

    if (showUrlSchemesDialog) {
        UrlSchemesDialog(
            onDismiss = { showUrlSchemesDialog = false },
            onCopy = { text ->
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("URL Scheme", text))
                Toast.makeText(context, "Схема скопирована", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // ✅ ВОТ ЭТО ТЕБЕ НЕ ХВАТАЛО
        ) {

            // 🔥 НОВЫЙ HEADER
            GlassHeader(
                title = "О приложении",
                icon = Icons.Default.Info,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp) // чтобы не упиралось в bottom bar
            ) {

                // Иконка приложения и название
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Иконка приложения
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(nebulaColors.accent.copy(alpha = 0.3f), Color.Transparent)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(48.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "NebulaGuard",
                            style = MaterialTheme.typography.headlineMedium,
                            color = nebulaColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "VPN Client",
                            style = MaterialTheme.typography.bodyMedium,
                            color = nebulaColors.textTertiary
                        )

                        Spacer(Modifier.height(16.dp))

                        // Версия
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(nebulaColors.textPrimary.copy(alpha = 0.06f))
                                    .border(
                                        1.dp,
                                        nebulaColors.textPrimary.copy(alpha = 0.1f),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = nebulaColors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                     Text(
                                        text = "NebulaCore: ${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = nebulaColors.textSecondary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(nebulaColors.textPrimary.copy(alpha = 0.06f))
                                    .border(
                                        1.dp,
                                        nebulaColors.textPrimary.copy(alpha = 0.1f),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = nebulaColors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                     Text(
                                        text = "Xray Core: 26.7.11",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = nebulaColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Информация о приложении
                GlassCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutInfoRow(
                            icon = Icons.Default.Person,
                            label = "Разработчик",
                            value = "Danila"
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                        )

                        AboutInfoRow(
                            icon = Icons.Default.Business,
                            label = "Организация",
                            value = "NebulaGuard Team"
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                        )

                        AboutInfoRow(
                            icon = Icons.Default.Description,
                            label = "Лицензия",
                            value = "NebulaGuard License"
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Устройство
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Smartphone, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Устройство", style = MaterialTheme.typography.titleMedium, color = nebulaColors.textPrimary)
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                        )
                        
                        val hwid = com.danila.nimbo.utils.AppVersionManager.getHWID(context)

                        AboutInfoRow(
                            icon = Icons.Default.Android,
                            label = "ОС",
                            value = "Android ${android.os.Build.VERSION.RELEASE}"
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                        )

                        AboutInfoRow(
                            icon = Icons.Default.Devices,
                            label = "Модель",
                            value = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, null, tint = nebulaColors.textSecondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("HWID", style = MaterialTheme.typography.bodyMedium, color = nebulaColors.textPrimary)
                                    Text(hwid, style = MaterialTheme.typography.bodySmall, color = nebulaColors.textTertiary)
                                }
                            }
                            IconButton(onClick = { 
                                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("HWID", hwid))
                                android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, tint = nebulaColors.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Описание
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(nebulaColors.accent.copy(alpha = 0.25f), Color.Transparent)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = nebulaColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Описание",
                                style = MaterialTheme.typography.titleSmall,
                                color = nebulaColors.textSecondary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "NebulaGuard — это современный VPN-клиент для безопасного и приватного доступа в интернет. Приложение поддерживает различные протоколы включая VLESS, VMess, Trojan и Shadowsocks, обеспечивая надежное шифрование и защиту ваших данных.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = nebulaColors.textTertiary,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4,
                            textAlign = TextAlign.Justify
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Приложение предоставляет удобный интерфейс для управления серверами, мониторинга скорости соединения и настройки параметров подключения.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = nebulaColors.textTertiary,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4,
                            textAlign = TextAlign.Justify
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                GlassSection(title = "Интеграция", icon = Icons.Default.Link) {
                    com.danila.nimbo.ui.components.SettingsNavigationItem(
                        icon = Icons.Default.Link,
                        title = "Схемы URL-адресов",
                        subtitle = "Автоматизация и интеграция",
                        onClick = { showUrlSchemesDialog = true }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Кнопка сброса настроек
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFFDC3545).copy(alpha = 0.25f), Color.Transparent)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFDC3545),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Сброс настроек",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFDC3545)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Эта кнопка вернет все настройки приложения к значениям по умолчанию. Ваши профили и подписки будут сохранены.",
                            style = MaterialTheme.typography.bodySmall,
                            color = nebulaColors.textTertiary,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3,
                            textAlign = TextAlign.Justify
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                killSwitch = false
                                autoConnect = false
                                showSpeed = true
                                tunnelMode = 0
                                colorTheme = DEFAULT_COLOR_THEME_INDEX
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC3545)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Сбросить все настройки",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Копирайт
                Text(
                    text = "NebulaGuard — Безопасность под контролем",
                    style = MaterialTheme.typography.bodyMedium,
                    color = nebulaColors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun UrlSchemesDialog(onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(32.dp)),
            color = nebulaColors.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = nebulaColors.textPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Схемы URL-адресов",
                        style = MaterialTheme.typography.titleLarge,
                        color = nebulaColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(nebulaColors.surface)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text("Примечание", style = MaterialTheme.typography.titleSmall, color = nebulaColors.textSecondary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Используйте эти схемы для автоматизации через Shortcuts, Tasker или другие приложения.\nНажмите на схему, чтобы скопировать.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = nebulaColors.textTertiary,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    item { SchemeSectionTitle("ЗАПУСТИТЬ ТУННЕЛЬ") }
                    item { SchemeItemCard(listOf("nimbo://connect" to null, "nimbo://open" to null), onCopy) }

                    item { SchemeSectionTitle("ОСТАНОВИТЬ СОЕДИНЕНИЕ") }
                    item { SchemeItemCard(listOf("nimbo://disconnect" to null, "nimbo://close" to null), onCopy) }

                    item { SchemeSectionTitle("ПЕРЕКЛЮЧИТЬ СОЕДИНЕНИЕ") }
                    item { SchemeItemCard(listOf("nimbo://toggle" to null), onCopy) }

                    item { SchemeSectionTitle("ДОБАВИТЬ КОНФИГУРАЦИЮ") }
                    item { SchemeItemCard(listOf(
                        "nimbo://import/{base64}" to "Импорт (автоопределение типа)",
                        "nimbo://add/{url}" to "Добавить по URL"
                    ), onCopy) }

                    item { SchemeSectionTitle("МАРШРУТИЗАЦИЯ") }
                    item { SchemeItemCard(listOf(
                        "nimbo://routing/add/{base64}" to "Добавить маршруты",
                        "nimbo://routing/onadd/{base64}" to "Добавить и применить"
                    ), onCopy) }
                }
            }
        }
    }
}

@Composable
fun SchemeSectionTitle(title: String) {
    val nebulaColors = LocalNebulaColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = nebulaColors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SchemeItemCard(items: List<Pair<String, String?>>, onCopy: (String) -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(nebulaColors.surface)
            .padding(vertical = 4.dp)
    ) {
        Column {
            items.forEachIndexed { index, pair ->
                val (code, subtitle) = pair
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopy(code) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = code,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = nebulaColors.textPrimary
                            )
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = nebulaColors.textTertiary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}
