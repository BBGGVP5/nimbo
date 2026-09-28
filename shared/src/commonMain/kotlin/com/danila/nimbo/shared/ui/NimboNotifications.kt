package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
private fun notificationColor(kind: NimboNotificationKind): Color = when (kind) {
    NimboNotificationKind.ERROR -> NimboPalette.Red
    NimboNotificationKind.SUCCESS -> NimboPalette.Green
    else -> NimboPalette.TextSecondary
}

@Composable
internal fun NimboToast(notification: NimboNotification, onDismiss: () -> Unit) {
    NimboSurface(Modifier.fillMaxWidth().heightIn(max = 240.dp).semantics { liveRegion = LiveRegionMode.Polite },
        cornerRadius = 18.dp, padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(notification.kind.title, style = NimboBodyStyle.copy(color = notificationColor(notification.kind)))
                    BasicText(notification.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = NimboSectionTitleStyle)
                }
                NimboSettingsAction("Закрыть", onClick = onDismiss)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SelectionContainer { BasicText(notification.message, style = NimboBodyStyle.copy(color = NimboPalette.Text)) }
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
                    BasicText(item.title, style = NimboSectionTitleStyle)
                }
                NimboIconButton(NimboIconName.DELETE, Modifier.size(44.dp)
                    .semantics { contentDescription = "Удалить уведомление: ${item.title}" }, onClick = onDelete)
            }
            SelectionContainer { BasicText(item.message, style = NimboBodyStyle.copy(color = NimboPalette.Text)) }
            BasicText(item.timeLabel, style = NimboBodyStyle)
        }
    }
}
