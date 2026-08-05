package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.model.UpdateKind
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.screens.MarkdownChangelog
import com.danila.nimbo.ui.screens.UpdateUiText
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val language = LocalConfiguration.current.locales[0].language
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate,
            dismissOnClickOutside = !updateInfo.forceUpdate,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    colors.accent.copy(alpha = 0.42f),
                                    colors.textPrimary.copy(alpha = 0.10f)
                                )
                            )
                        ),
                        RoundedCornerShape(30.dp)
                    ),
                shape = RoundedCornerShape(30.dp),
                color = colors.surface.copy(alpha = 0.97f),
                tonalElevation = 0.dp,
                shadowElevation = 20.dp
            ) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(colors.accent.copy(alpha = 0.15f))
                                .border(1.dp, colors.accent.copy(alpha = 0.30f), RoundedCornerShape(19.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SystemUpdateAlt,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(29.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (updateInfo.kind == UpdateKind.REPAIR) {
                                    t("Исправление версии", "Version repair")
                                } else {
                                    t("Доступно обновление", "Update available")
                                },
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Nimbo v${updateInfo.versionName.removePrefix("v")}",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogChip(
                            icon = { Icon(Icons.Default.Android, null, Modifier.size(15.dp)) },
                            text = "Android"
                        )
                        if (updateInfo.fileSize > 0L) {
                            DialogChip(text = UpdateUiText.fileSize(updateInfo.fileSize, language))
                        }
                        DialogChip(text = if (updateInfo.sha256 != null) "SHA-256" else t("Подпись APK", "APK signature"))
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        t("Что изменилось", "What's changed"),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.textPrimary.copy(alpha = 0.045f))
                            .border(1.dp, colors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        MarkdownChangelog(
                            content = updateInfo.changelog?.takeIf(String::isNotBlank)
                                ?: t(
                                    "Исправления ошибок и улучшения стабильности.",
                                    "Bug fixes and stability improvements."
                                ),
                            color = colors.textSecondary,
                            itemAlignment = Alignment.Start
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(17.dp))
                            .background(colors.accent.copy(alpha = 0.08f))
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t(
                                "Перед установкой Nimbo проверит файл и подпись приложения.",
                                "Nimbo verifies the file and app signature before installation."
                            ),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!updateInfo.forceUpdate) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.textPrimary.copy(alpha = 0.06f),
                                    contentColor = colors.textSecondary
                                )
                            ) {
                                Text(t("Позже", "Later"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Button(
                            onClick = onUpdate,
                            modifier = Modifier.weight(1.35f).height(52.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                if (updateInfo.kind == UpdateKind.REPAIR) {
                                    t("Установить", "Install")
                                } else {
                                    t("Обновить", "Update")
                                },
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogChip(
    text: String,
    icon: (@Composable () -> Unit)? = null
) {
    val colors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accent.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        icon?.invoke()
        Text(text, color = colors.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
