package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Уведомления внутри приложения — те же, что на Android.
 *
 * Всплывающая полоса сообщает о том, что произошло прямо сейчас, а история
 * помнит прошлое: без неё сообщение исчезало через пару секунд, и понять,
 * почему подписка не обновилась, было нельзя.
 */
enum class NimboNotificationKind(val wireName: String, val title: String) {
    SUCCESS("success", "Готово"),
    ERROR("error", "Ошибка"),
    UPDATE("update", "Обновление"),
    ACTIVITY("activity", "Активность");

    companion object {
        fun fromWireName(value: String?): NimboNotificationKind =
            entries.firstOrNull { it.wireName == value } ?: ACTIVITY
    }
}

data class NimboNotification(
    val id: String,
    val title: String,
    val message: String,
    val kind: NimboNotificationKind,
    /** Секунды эпохи: время форматирует платформа, у неё есть локаль. */
    val timestampSeconds: Long,
    val timeLabel: String
)

/** Всплывающая полоса поверх экрана. */
@Composable
internal fun NimboToast(notification: NimboNotification, onDismiss: () -> Unit) {
    val accent = when (notification.kind) {
        NimboNotificationKind.ERROR -> Color(0xFFFF6B6B)
        NimboNotificationKind.SUCCESS -> Color(0xFF4ADE80)
        NimboNotificationKind.UPDATE -> NimboPalette.Accent
        NimboNotificationKind.ACTIVITY -> NimboPalette.Accent
    }
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        padding = PaddingValues(14.dp),
        onClick = onDismiss
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(nimboStyledShape(2.dp, 1.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    notification.title,
                    style = TextStyle(
                        color = accent,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                BasicText(
                    notification.message,
                    style = TextStyle(
                        color = NimboPalette.Text,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/** История уведомлений с теми же фильтрами, что на Android. */
@Composable
internal fun NimboNotificationsScreen(state: NimboUiState, actions: NimboUiActions) {
    var filter by remember { mutableStateOf<NimboNotificationKind?>(null) }
    val visible = state.notifications.filter { filter == null || it.kind == filter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 44.dp, bottom = 116.dp)
            .nimboScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(
            "‹ Настройки",
            modifier = Modifier.nimboRowClickable {
                actions.onOpenScreen(NimboScreen.SETTINGS.wireName)
            },
            style = TextStyle(
                color = NimboPalette.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        BasicText("Уведомления", style = NimboTitleStyle)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NimboPill(
                "Все ${state.notifications.size}",
                modifier = Modifier.weight(1f),
                selected = filter == null,
                onClick = { filter = null }
            )
            NimboNotificationKind.entries.take(3).forEach { kind ->
                val count = state.notifications.count { it.kind == kind }
                NimboPill(
                    "${kind.title} $count",
                    modifier = Modifier.weight(1f),
                    selected = filter == kind,
                    onClick = { filter = if (filter == kind) null else kind }
                )
            }
        }

        if (state.notifications.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "${visible.size} записей",
                    modifier = Modifier.weight(1f),
                    style = NimboBodyStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                NimboIconPill(NimboIconName.DELETE, "Очистить", onClick = actions.onClearNotifications)
            }
        }

        if (visible.isEmpty()) {
            NimboSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column {
                    BasicText("Пока пусто", style = NimboSectionTitleStyle)
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        "Здесь появятся сообщения о подписке, обновлениях и ошибках подключения.",
                        style = NimboBodyStyle
                    )
                }
            }
        }

        visible.forEach { item ->
            NimboNotificationRow(item) { actions.onDeleteNotification(item.id) }
        }
    }
}

@Composable
private fun NimboNotificationRow(item: NimboNotification, onDelete: () -> Unit) {
    val accent = when (item.kind) {
        NimboNotificationKind.ERROR -> Color(0xFFFF6B6B)
        NimboNotificationKind.SUCCESS -> Color(0xFF4ADE80)
        else -> NimboPalette.Accent
    }
    NimboSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        padding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(nimboStyledShape(2.dp, 1.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(nimboStyledShape(6.dp, 2.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(
                                if (LocalNimboElementStyle.current == NimboElementStyle.MANGA) 1.5.dp else 1.dp,
                                nimboStyledBorder(accent.copy(alpha = 0.4f)),
                                nimboStyledShape(6.dp, 2.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        BasicText(
                            item.title.uppercase(),
                            style = TextStyle(
                                color = accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(item.timeLabel, style = NimboBodyStyle.copy(fontSize = 11.sp))
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    item.message,
                    style = TextStyle(
                        color = NimboPalette.Text,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                )
            }
            Spacer(Modifier.width(8.dp))
            NimboIconButton(
                NimboIconName.DELETE,
                modifier = Modifier.size(34.dp),
                onClick = onDelete
            )
        }
    }
}
