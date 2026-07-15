package com.danila.nimbo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.NotificationItem
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.NotificationSurface
import com.danila.nimbo.ui.components.NotificationType
import com.danila.nimbo.ui.components.dotPatternOverlay
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class NotificationFilter(
    val label: String,
    val type: NotificationType?
) {
    ALL("Все", null),
    ERROR("Ошибки", NotificationType.ERROR),
    UPDATE("Обновления", NotificationType.UPDATE),
    PING("Сеть", NotificationType.PING),
    SUCCESS("Готово", NotificationType.SUCCESS),
    NORMAL("События", NotificationType.NORMAL)
}

private data class NotificationVisual(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

@Composable
fun NotificationHistoryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = remember { PreferencesManager(context) }
    var history by remember { mutableStateOf(preferencesManager.getNotificationHistory()) }
    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val filteredHistory = remember(history, selectedFilter) {
        selectedFilter.type?.let { type -> history.filter { it.type == type } } ?: history
    }
    val groupedHistory = remember(filteredHistory) {
        filteredHistory.groupBy { startOfDay(it.timestamp) }.toSortedMap(reverseOrder())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            NotificationHistoryHeader(
                count = history.size,
                onBack = onNavigateBack,
                onClear = { showClearConfirmation = true },
                showClear = history.isNotEmpty()
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "summary") {
                    NotificationSummaryCard(history)
                }

                item(key = "filters") {
                    NotificationFilters(
                        selected = selectedFilter,
                        history = history,
                        onSelect = { selectedFilter = it }
                    )
                }

                if (filteredHistory.isEmpty()) {
                    item(key = "empty-${selectedFilter.name}") {
                        NotificationEmptyState(
                            hasAnyHistory = history.isNotEmpty(),
                            selectedFilter = selectedFilter
                        )
                    }
                } else {
                    groupedHistory.forEach { (dayStart, notifications) ->
                        item(key = "day-$dayStart") {
                            NotificationDayHeader(
                                dayStart = dayStart,
                                count = notifications.size
                            )
                        }
                        itemsIndexed(
                            items = notifications,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            NotificationHistoryItem(
                                item = item,
                                animationDelayMs = (index.coerceAtMost(6) * 42).toLong(),
                                onDelete = {
                                    preferencesManager.removeNotificationFromHistory(item.id)
                                    history = preferencesManager.getNotificationHistory()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        NebulaMorphicDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = "Очистить историю?",
            description = "Все ${history.size} уведомлений будут удалены без возможности восстановления.",
            confirmButtonText = "Очистить",
            cancelButtonText = "Отмена",
            confirmButtonColor = nebulaColors.statusError,
            headerIcon = Icons.Default.DeleteSweep,
            headerIconTint = nebulaColors.statusError,
            onConfirm = {
                preferencesManager.clearNotificationHistory()
                history = emptyList()
                selectedFilter = NotificationFilter.ALL
                showClearConfirmation = false
            }
        )
    }
}

@Composable
private fun NotificationHistoryHeader(
    count: Int,
    onBack: () -> Unit,
    onClear: () -> Unit,
    showClear: Boolean
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NimboBackButton(onBack = onBack)

        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Уведомления",
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = if (count == 0) "Центр событий Nimbo" else "$count событий в истории",
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(visible = showClear) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(nebulaColors.statusError.copy(alpha = 0.1f))
                    .border(1.dp, nebulaColors.statusError.copy(alpha = 0.2f), RoundedCornerShape(15.dp))
                    .clickable(onClick = onClear)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Очистить историю",
                    tint = nebulaColors.statusError,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationSummaryCard(history: List<NotificationItem>) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val shape = notificationShape(elementStyle, large = true)
    val latest = history.firstOrNull()
    val successCount = history.count { it.type == NotificationType.SUCCESS }
    val errorCount = history.count { it.type == NotificationType.ERROR }
    val activityCount = history.count {
        it.type == NotificationType.UPDATE || it.type == NotificationType.PING
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, spotColor = nebulaColors.accent.copy(alpha = 0.14f))
            .clip(shape)
            .background(notificationPanelBrush(nebulaColors.accent))
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                    Modifier.dotPatternOverlay(
                        color = nebulaColors.textPrimary,
                        spacing = 10.dp,
                        radius = 0.8.dp,
                        alpha = 0.1f
                    )
                } else Modifier
            )
            .border(1.dp, nebulaColors.accent.copy(alpha = 0.22f), shape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(nebulaColors.accent.copy(alpha = 0.14f), RoundedCornerShape(17.dp))
                        .border(1.dp, nebulaColors.accent.copy(alpha = 0.26f), RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = history.size,
                        transitionSpec = {
                            (slideInVertically(tween(220)) { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically(tween(160)) { -it / 2 } + fadeOut())
                        },
                        label = "history_count"
                    ) { count ->
                        Text(
                            text = count.toString(),
                            color = nebulaColors.accent,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ЦЕНТР СОБЫТИЙ",
                        color = nebulaColors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (history.isEmpty()) "Здесь появятся важные события" else "Всё важное в одном месте",
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = latest?.let { "Последнее: ${relativeNotificationTime(it.timestamp)}" }
                            ?: "Обновления, ошибки и результаты проверок",
                        color = nebulaColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            NotificationDistributionBar(history)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Готово",
                    value = successCount,
                    color = nebulaColors.statusConnected
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Ошибки",
                    value = errorCount,
                    color = nebulaColors.statusError
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Активность",
                    value = activityCount,
                    color = nebulaColors.statusConnecting
                )
            }
        }
    }
}

@Composable
private fun NotificationDistributionBar(history: List<NotificationItem>) {
    val nebulaColors = LocalNebulaColors.current
    val segments = listOf(
        history.count { it.type == NotificationType.SUCCESS } to nebulaColors.statusConnected,
        history.count { it.type == NotificationType.ERROR } to nebulaColors.statusError,
        history.count { it.type == NotificationType.UPDATE } to nebulaColors.accent,
        history.count { it.type == NotificationType.PING } to nebulaColors.statusConnecting,
        history.count { it.type == NotificationType.NORMAL } to nebulaColors.textSecondary
    ).filter { it.first > 0 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(nebulaColors.textPrimary.copy(alpha = 0.06f)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEach { (count, color) ->
            Box(
                modifier = Modifier
                    .weight(count.toFloat())
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier,
    label: String,
    value: Int,
    color: Color
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(
                text = value.toString(),
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NotificationFilters(
    selected: NotificationFilter,
    history: List<NotificationItem>,
    onSelect: (NotificationFilter) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = null,
                tint = nebulaColors.textTertiary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "ФИЛЬТРЫ",
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 6.dp)
        ) {
            items(NotificationFilter.entries, key = { it.name }) { filter ->
                val selectedNow = selected == filter
                val color = filter.type?.let { notificationVisual(it).color } ?: nebulaColors.accent
                val count = filter.type?.let { type -> history.count { it.type == type } } ?: history.size
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selectedNow) color.copy(alpha = 0.17f)
                            else nebulaColors.textPrimary.copy(alpha = 0.045f)
                        )
                        .border(
                            1.dp,
                            if (selectedNow) color.copy(alpha = 0.42f)
                            else nebulaColors.textPrimary.copy(alpha = 0.08f),
                            CircleShape
                        )
                        .clickable { onSelect(filter) }
                        .semantics { role = Role.Tab }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = filter.label,
                        color = if (selectedNow) color else nebulaColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = count.toString(),
                        color = if (selectedNow) color else nebulaColors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDayHeader(dayStart: Long, count: Int) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 3.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel(dayStart).uppercase(Locale.getDefault()),
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(nebulaColors.textPrimary.copy(alpha = 0.08f))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = count.toString(),
            color = nebulaColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NotificationHistoryItem(
    item: NotificationItem,
    animationDelayMs: Long,
    onDelete: () -> Unit
) {
    var visible by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        delay(animationDelayMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it / 4 }
        ),
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 5 }
    ) {
        NotificationTimelineCard(item = item, onDelete = onDelete)
    }
}

@Composable
private fun NotificationTimelineCard(
    item: NotificationItem,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    NotificationSurface(
        message = item.message,
        type = item.type,
        metaText = timeFormat.format(Date(item.timestamp)),
        actionIcon = Icons.Default.Delete,
        actionDescription = "Удалить уведомление",
        onAction = onDelete
    )
}

@Composable
private fun NotificationEmptyState(
    hasAnyHistory: Boolean,
    selectedFilter: NotificationFilter
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val shape = notificationShape(elementStyle, large = true)
    val title = if (hasAnyHistory) "Здесь пока пусто" else "История чиста"
    val message = if (hasAnyHistory) {
        "Уведомлений категории «${selectedFilter.label}» ещё нет"
    } else {
        "Обновления подписок, результаты пинга и важные события появятся здесь"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = nebulaColors.textPrimary.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .background(nebulaColors.accent.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
                    .border(1.dp, nebulaColors.accent.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasAnyHistory) Icons.Default.FilterAlt else Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = nebulaColors.accent.copy(alpha = 0.72f),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(15.dp))
            Text(
                text = title,
                color = nebulaColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = message,
                color = nebulaColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun notificationVisual(type: NotificationType): NotificationVisual {
    val colors = LocalNebulaColors.current
    return when (type) {
        NotificationType.SUCCESS -> NotificationVisual(
            Icons.Default.CheckCircle,
            colors.statusConnected,
            "ГОТОВО"
        )
        NotificationType.ERROR -> NotificationVisual(
            Icons.Default.Error,
            colors.statusError,
            "НУЖНО ВНИМАНИЕ"
        )
        NotificationType.UPDATE -> NotificationVisual(
            Icons.Default.Refresh,
            colors.accent,
            "ОБНОВЛЕНИЕ"
        )
        NotificationType.PING -> NotificationVisual(
            Icons.Default.SignalCellularAlt,
            colors.statusConnecting,
            "ПРОВЕРКА СЕТИ"
        )
        NotificationType.NORMAL -> NotificationVisual(
            Icons.Default.Info,
            colors.textSecondary,
            "NIMBO"
        )
    }
}

@Composable
private fun notificationPanelBrush(accent: Color): Brush {
    val colors = LocalNebulaColors.current
    val isLight = colors.background.luminance() > 0.5f
    return Brush.linearGradient(
        listOf(
            if (isLight) Color.White.copy(alpha = 0.94f) else colors.surface.copy(alpha = 0.88f),
            accent.copy(alpha = if (isLight) 0.08f else 0.14f),
            if (isLight) Color.White.copy(alpha = 0.88f) else colors.surface.copy(alpha = 0.78f)
        )
    )
}

private fun notificationShape(elementStyle: ElementStyleMode, large: Boolean): RoundedCornerShape {
    val radius = when (elementStyle) {
        ElementStyleMode.MORPHISM -> if (large) 26.dp else 22.dp
        ElementStyleMode.MATERIAL3 -> if (large) 22.dp else 18.dp
        ElementStyleMode.NOTHING_DOTS -> if (large) 16.dp else 13.dp
        ElementStyleMode.OUTLINED -> if (large) 14.dp else 11.dp
        ElementStyleMode.SOFT_NEO -> if (large) 24.dp else 20.dp
    }
    return RoundedCornerShape(radius)
}

private fun startOfDay(timestamp: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun dayLabel(dayStart: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
    return when (dayStart) {
        today -> "Сегодня"
        yesterday -> "Вчера"
        else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(dayStart))
    }
}

private fun relativeNotificationTime(timestamp: Long): String {
    val elapsedMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = minute * 60
    val day = hour * 24
    return when {
        elapsedMs < minute -> "только что"
        elapsedMs < hour -> "${elapsedMs / minute} мин назад"
        elapsedMs < day -> "${elapsedMs / hour} ч назад"
        elapsedMs < day * 7 -> "${elapsedMs / day} дн назад"
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp))
    }
}
