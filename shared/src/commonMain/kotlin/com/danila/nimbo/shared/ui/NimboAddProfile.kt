package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Добавление подписки — та же вёрстка, что на Android.
 *
 * Раньше пустой экран предлагал одну строку «Добавьте подписку», и способы
 * импорта приходилось искать. Здесь они на виду: крупная кнопка для обычного
 * случая и три плитки быстрого импорта.
 */
@Composable
internal fun NimboAddProfileCard(actions: NimboUiActions) {
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        NimboAddProfileSheet(actions = actions, onDismiss = { showSheet = false })
        return
    }

    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        padding = PaddingValues(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(nimboStyledShape(18.dp, 3.dp))
                        .background(nimboStyledContainer(NimboPalette.Accent.copy(alpha = 0.22f))),
                    contentAlignment = Alignment.Center
                ) {
                    NimboIcon(NimboIconName.ADD, tint = NimboPalette.Accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        "БЫСТРЫЙ СТАРТ",
                        style = TextStyle(
                            color = NimboPalette.Accent,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    BasicText("Добавьте подписку", style = NimboTitleStyle.copy(fontSize = 24.sp))
                    BasicText(
                        "Ссылка, QR-код или готовый файл профиля",
                        style = NimboBodyStyle.copy(fontSize = 13.sp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            NimboPrimaryAction("Добавить подписку") { showSheet = true }

            Spacer(Modifier.height(14.dp))
            NimboLabeledDivider("БЫСТРЫЙ ИМПОРТ")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NimboImportTile("Буфер", null, NimboIconName.LIST, Modifier.weight(1f), actions.onImportClipboard)
                NimboImportTile("Файл", null, NimboIconName.DOWNLOAD, Modifier.weight(1f), actions.onImportFile)
                NimboImportTile("QR-код", null, NimboIconName.SEARCH, Modifier.weight(1f), actions.onScanQr)
            }
        }
    }
}

/**
 * Окно импорта: поле для ссылки и те же три способа.
 *
 * Ссылку разбирает само устройство — об этом сказано прямо под полем: люди
 * справедливо опасаются вставлять ссылку подписки в незнакомое приложение.
 */
@Composable
private fun NimboAddProfileSheet(actions: NimboUiActions, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    val ready = link.trim().length > 6

    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        padding = PaddingValues(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(nimboStyledShape(16.dp, 3.dp))
                        .background(nimboStyledContainer(NimboPalette.Accent.copy(alpha = 0.22f))),
                    contentAlignment = Alignment.Center
                ) {
                    NimboIcon(NimboIconName.CLOUD, tint = NimboPalette.Accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicText("Добавить профиль", style = NimboTitleStyle.copy(fontSize = 22.sp))
                    BasicText("Подписка или отдельный сервер", style = NimboBodyStyle.copy(fontSize = 13.sp))
                }
                NimboIconButton(
                    NimboIconName.DELETE,
                    modifier = Modifier.size(36.dp),
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(14.dp))
            BasicText(
                "ССЫЛКА ИЛИ КОНФИГУРАЦИЯ",
                style = NimboBodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .nimboControlSurface(nimboStyledShape(16.dp, 2.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    textStyle = TextStyle(color = NimboPalette.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(NimboPalette.Accent),
                    decorationBox = { inner ->
                        if (link.isEmpty()) {
                            BasicText(
                                "https://…  или  vless://…",
                                style = NimboBodyStyle.copy(fontSize = 15.sp)
                            )
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Ссылка обрабатывается только на устройстве",
                style = NimboBodyStyle.copy(fontSize = 12.sp)
            )

            Spacer(Modifier.height(14.dp))
            NimboPrimaryAction("Импорт", enabled = ready) {
                actions.onImportSubscription(link.trim())
                onDismiss()
            }

            Spacer(Modifier.height(14.dp))
            NimboLabeledDivider("ДРУГИЕ СПОСОБЫ")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NimboImportTile("Буфер", "Вставить", NimboIconName.LIST, Modifier.weight(1f)) {
                    actions.onImportClipboard()
                    onDismiss()
                }
                NimboImportTile("Файл", "Открыть", NimboIconName.DOWNLOAD, Modifier.weight(1f)) {
                    actions.onImportFile()
                    onDismiss()
                }
                NimboImportTile("QR", "Сканировать", NimboIconName.SEARCH, Modifier.weight(1f)) {
                    actions.onScanQr()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun NimboPrimaryAction(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = nimboStyledShape(18.dp, 3.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    nimboStyledContainer(NimboPalette.Accent, selected = true)
                } else {
                    nimboStyledContainer(NimboPalette.Control)
                }
            )
            .then(if (enabled) Modifier.nimboRowClickable(onClick) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NimboIcon(
            NimboIconName.ADD,
            tint = if (enabled) NimboPalette.Background else NimboPalette.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                // Текст на заливке акцента: на светлой кнопке белый пропадает.
                color = if (enabled) NimboPalette.Background else NimboPalette.TextSecondary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        )
        BasicText(
            "›",
            style = TextStyle(
                color = if (enabled) NimboPalette.Background else NimboPalette.TextSecondary,
                fontSize = 20.sp
            )
        )
    }
}

@Composable
private fun NimboLabeledDivider(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(NimboPalette.Hairline)
        )
        BasicText(
            text,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = NimboBodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(NimboPalette.Hairline)
        )
    }
}

@Composable
private fun NimboImportTile(
    title: String,
    subtitle: String?,
    icon: NimboIconName,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = nimboStyledShape(16.dp, 2.dp)
    Column(
        modifier = modifier
            .nimboControlSurface(shape)
            .nimboRowClickable(onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NimboIcon(icon, tint = NimboPalette.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        BasicText(
            title,
            style = TextStyle(
                color = NimboPalette.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        if (subtitle != null) {
            BasicText(subtitle, style = NimboBodyStyle.copy(fontSize = 11.sp))
        }
    }
}
