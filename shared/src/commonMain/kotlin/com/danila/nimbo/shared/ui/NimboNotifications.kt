package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

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

internal fun notificationTimeoutMillis(kind: NimboNotificationKind, recommendedMillis: Long? = null): Long =
    maxOf(when (kind) {
        NimboNotificationKind.UPDATE -> 8_000L
        NimboNotificationKind.ERROR -> 6_000L
        else -> 4_000L
    }, recommendedMillis ?: 0L)

internal fun shouldDismissNotification(current: NimboNotification?, expectedId: String): Boolean =
    current?.id == expectedId

/** Repair legacy same-second IDs without losing history or breaking delete actions. */
internal fun normalizeNotificationIds(items: List<NimboNotification>): List<NimboNotification> {
    val seen = mutableSetOf<String>()
    return items.mapIndexed { index, item ->
        val base = item.id.ifBlank { "legacy-${item.timestampSeconds}-$index" }
        var id = base
        var duplicate = 0
        while (!seen.add(id)) id = "$base-duplicate-$index-${duplicate++}"
        if (id == item.id) item else item.copy(id = id)
    }
}

@Composable
private fun notificationColor(kind: NimboNotificationKind): Color = when (kind) {
    NimboNotificationKind.ERROR -> NimboPalette.Red
    NimboNotificationKind.SUCCESS -> NimboPalette.Green
    else -> NimboPalette.TextSecondary
}

@Composable
internal fun NimboToast(notification: NimboNotification, onDismiss: () -> Unit) {
    val icon = when (notification.kind) {
        NimboNotificationKind.SUCCESS -> Icons.Default.CheckCircle
        NimboNotificationKind.ERROR -> Icons.Default.Error
        NimboNotificationKind.UPDATE -> Icons.Default.Refresh
        NimboNotificationKind.ACTIVITY -> Icons.Default.Info
    }
    NimboSurface(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("notification-banner")
        .semantics { liveRegion = LiveRegionMode.Polite }, cornerRadius = 18.dp,
        padding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, notification.kind.title, Modifier.size(20.dp), tint = notificationColor(notification.kind))
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (notification.title.isNotBlank() && notification.title != notification.kind.title) {
                    BasicText(notification.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NimboSectionTitleStyle)
                }
                // Only the transient banner is bounded; history keeps the complete text.
                BasicText(notification.message, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = NimboBodyStyle.copy(color = NimboPalette.Text))
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss)
                .testTag("notification-action").semantics { contentDescription = "Закрыть уведомление" },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, null, Modifier.size(20.dp), tint = NimboPalette.TextSecondary)
            }
        }
    }
}

@Composable
internal fun NimboNotificationsScreen(state: NimboUiState, actions: NimboUiActions) {
    var filter by remember { mutableStateOf<NimboNotificationKind?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val visible = state.notifications.filter { filter == null || it.kind == filter }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimboSettingsBack("Настройки") { actions.onOpenScreen(NimboScreen.SETTINGS.wireName) }
        NimboPageHeading("Уведомления")
        NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            NimboSettingsSelector("Показать", listOf(NimboDropdownOption("all", "Все · ${state.notifications.size}")) +
                NimboNotificationKind.entries.map { kind ->
                    NimboDropdownOption(kind.wireName, "${kind.title} · ${state.notifications.count { it.kind == kind }}")
                }, filter?.wireName ?: "all") { key ->
                filter = NimboNotificationKind.entries.firstOrNull { it.wireName == key }
            }
        }
        if (state.notifications.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("${visible.size} записей", Modifier.weight(1f), style = NimboBodyStyle)
            NimboSettingsAction("Очистить всё") { confirmClear = true }
        }
        if (visible.isEmpty()) NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(16.dp)) {
            BasicText(if (state.notifications.isEmpty()) "Уведомлений пока нет" else "В этой категории нет уведомлений", style = NimboBodyStyle)
        }
        visible.forEach { item -> key(item.id) { NimboNotificationRow(item) { actions.onDeleteNotification(item.id) } } }
    }
    if (confirmClear) NimboSettingsConfirmation("Очистить историю?", "Будут удалены все уведомления, включая другие категории.", "Очистить всё",
        onDismiss = { confirmClear = false }, onConfirm = { actions.onClearNotifications(); confirmClear = false })
}

@Composable
private fun NimboNotificationRow(item: NimboNotification, onDelete: () -> Unit) {
    NimboSurface(Modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(item.kind.title, style = NimboBodyStyle.copy(color = notificationColor(item.kind)))
                    if (item.title.isNotBlank() && item.title != item.kind.title) {
                        BasicText(item.title, style = NimboSectionTitleStyle)
                    }
                }
                NimboIconButton(NimboIconName.DELETE, Modifier.size(44.dp)
                    .semantics { contentDescription = "Удалить уведомление: ${item.title}" }, onClick = onDelete)
            }
            SelectionContainer { BasicText(item.message, style = NimboBodyStyle.copy(color = NimboPalette.Text)) }
            BasicText(item.timeLabel, style = NimboBodyStyle)
        }
    }
}
