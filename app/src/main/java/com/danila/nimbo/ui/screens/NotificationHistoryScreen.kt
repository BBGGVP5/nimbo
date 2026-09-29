package com.danila.nimbo.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.NotificationItem
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.ui.components.*
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class NotificationFilter(val type: NotificationType?) {
    ALL(null), ERROR(NotificationType.ERROR), UPDATE(NotificationType.UPDATE),
    PING(NotificationType.PING), SUCCESS(NotificationType.SUCCESS), NORMAL(NotificationType.NORMAL)
}

@Composable
fun NotificationHistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val preferences = LocalPreferencesManager.current
    val history by preferences.notificationHistoryState
    var selectedFilter by rememberSaveable { mutableStateOf(NotificationFilter.ALL) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(preferences.showNotificationSpeed) }
    var showTime by remember { mutableStateOf(preferences.showNotificationConnectionTime) }
    val groupedHistory = remember(history, selectedFilter) {
        history.filter { selectedFilter.type == null || it.type == selectedFilter.type }
            .sortedByDescending { it.timestamp }.groupBy { startOfDay(it.timestamp) }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        NimboSubPageHeader(t("Уведомления", "Notifications"),
            t("${history.size} событий в истории", "${history.size} events in history"), onNavigateBack,
            trailing = {
                if (history.isNotEmpty()) IconButton(onClick = { showClearConfirmation = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.DeleteSweep, t("Очистить историю", "Clear history"), tint = colors.textSecondary)
                }
            })
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = LocalFloatingNavHeight.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "settings") {
                NimboPanel(Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationItem(Icons.Default.Settings, t("Настройки уведомлений", "Notification settings"),
                            if (showSettings) t("Скрыть настройки", "Hide settings")
                            else t("Скорость, время и системные разрешения", "Speed, time and system permissions")) {
                            showSettings = !showSettings
                        }
                        if (showSettings) {
                            HorizontalDivider(color = colors.divider)
                            SettingsSwitch(Icons.Default.Speed, t("Скорость соединения", "Connection speed"),
                                t("В постоянном уведомлении VPN", "In the ongoing VPN notification"), showSpeed) {
                                showSpeed = it
                                preferences.showNotificationSpeed = it
                            }
                            SettingsSwitch(Icons.Default.Timer, t("Время подключения", "Connection time"),
                                t("В постоянном уведомлении VPN", "In the ongoing VPN notification"), showTime) {
                                showTime = it
                                preferences.showNotificationConnectionTime = it
                            }
                            SettingsNavigationItem(Icons.Default.Notifications, t("Настройки Android", "Android settings"),
                                t("Разрешения, звук и категории", "Permissions, sound and categories")) {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }
                        }
                    }
                }
            }
            item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NotificationFilter.entries, key = { it.name }) { filter ->
                        FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter },
                            modifier = Modifier.heightIn(min = 48.dp), label = { Text(filterLabel(filter)) })
                    }
                }
            }
            if (groupedHistory.isEmpty()) {
                item(key = "empty") {
                    NimboToolSection(if (history.isEmpty()) t("История пуста", "No events yet")
                        else t("Нет событий в этом фильтре", "No matching events"),
                        if (history.isEmpty()) t("Сохранённые уведомления появятся здесь.", "Saved notifications will appear here.")
                        else t("Выберите другую категорию.", "Choose another category.")) {}
                }
            }
            groupedHistory.forEach { (day, notifications) ->
                item(key = "day-$day") {
                    Text(dayLabel(day), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
                }
                items(notifications, key = { it.id }) { item ->
                    NotificationTimelineCard(item) { preferences.removeNotificationFromHistory(item.id) }
                }
            }
        }
    }
    if (showClearConfirmation) NebulaMorphicDialog(
        onDismissRequest = { showClearConfirmation = false },
        title = t("Очистить историю?", "Clear history?"),
        description = t("Все ${history.size} уведомлений будут удалены без возможности восстановления.",
            "All ${history.size} notifications will be permanently removed."),
        confirmButtonText = t("Очистить", "Clear"), cancelButtonText = t("Отмена", "Cancel"),
        confirmButtonColor = colors.statusError, headerIcon = Icons.Default.DeleteSweep,
        headerIconTint = colors.statusError, onConfirm = {
            preferences.clearNotificationHistory()
            selectedFilter = NotificationFilter.ALL
            showClearConfirmation = false
        })
}

@Composable
private fun filterLabel(filter: NotificationFilter): String = when (filter) {
    NotificationFilter.ALL -> t("Все", "All")
    NotificationFilter.ERROR -> t("Ошибки", "Errors")
    NotificationFilter.UPDATE -> t("Обновления", "Updates")
    NotificationFilter.PING -> t("Сеть", "Network")
    NotificationFilter.SUCCESS -> t("Готово", "Success")
    NotificationFilter.NORMAL -> t("События", "Events")
}

@Composable
private fun NotificationTimelineCard(item: NotificationItem, onDelete: () -> Unit) {
    val locale = Locale.getDefault()
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    NotificationSurface(message = item.message, type = item.type,
        metaText = timeFormat.format(Date(item.timestamp)), actionIcon = Icons.Default.Delete,
        actionDescription = t("Удалить уведомление", "Delete notification"), onAction = onDelete)
}

internal fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun dayLabel(dayStart: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    return when (dayStart) {
        today -> t("Сегодня", "Today")
        yesterday -> t("Вчера", "Yesterday")
        else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(dayStart))
    }
}
