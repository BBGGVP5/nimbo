package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalNebulaColors
import kotlinx.coroutines.delay

enum class NotificationType { PING, UPDATE, SUCCESS, ERROR, NORMAL }

data class NotificationData(
    val message: String,
    val type: NotificationType = NotificationType.NORMAL,
    val id: Long = System.nanoTime()
)

/** Finite defaults for every kind; assistive technology may request additional reading time. */
internal fun notificationDurationMillis(type: NotificationType): Long = when (type) {
    NotificationType.UPDATE, NotificationType.PING -> 8_000L
    NotificationType.ERROR -> 6_000L
    else -> 4_000L
}

internal fun notificationTimeoutMillis(type: NotificationType, recommendedMillis: Long?): Long =
    maxOf(notificationDurationMillis(type), recommendedMillis ?: 0L)

@Composable
fun TopNotification(
    data: NotificationData?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var displayedData by remember { mutableStateOf(data) }
    var dismissedId by remember { mutableStateOf<Long?>(null) }
    val dismiss by rememberUpdatedState(onDismiss)
    val accessibility = LocalAccessibilityManager.current
    val animate = LocalBackgroundAnimationEnabled.current
    LaunchedEffect(data?.id) {
        val notification = data ?: return@LaunchedEffect
        displayedData = notification
        val recommended = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = notificationDurationMillis(notification.type),
            containsIcons = true, containsText = true, containsControls = true
        )
        delay(notificationTimeoutMillis(notification.type, recommended))
        dismissedId = notification.id
        dismiss()
    }

    // The outer box owns system insets. The inner banner is bounded on tablets/landscape.
    Box(modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = data != null && data.id != dismissedId,
            enter = fadeIn(tween(if (animate) 140 else 0)),
            exit = fadeOut(tween(if (animate) 100 else 0)),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            displayedData?.let { notification ->
                NotificationSurface(
                    message = notification.message,
                    type = notification.type,
                    modifier = Modifier.testTag("notification-banner").semantics { liveRegion = LiveRegionMode.Polite },
                    compact = true,
                    actionIcon = Icons.Default.Close,
                    actionDescription = t("Закрыть уведомление", "Dismiss notification"),
                    onAction = { dismissedId = notification.id; dismiss() }
                )
            }
        }
    }
}

@Composable
fun NotificationSurface(
    message: String,
    type: NotificationType,
    modifier: Modifier = Modifier,
    metaText: String? = null,
    animateIcon: Boolean = false,
    showProgress: Boolean = false,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val colors = LocalNebulaColors.current
    val title = when (type) {
        NotificationType.UPDATE -> t("Обновление", "Update")
        NotificationType.PING -> t("Проверка сети", "Network check")
        NotificationType.SUCCESS -> t("Готово", "Done")
        NotificationType.ERROR -> t("Нужно внимание", "Action needed")
        NotificationType.NORMAL -> "Nimbo"
    }
    val icon = when (type) {
        NotificationType.UPDATE -> Icons.Default.Refresh
        NotificationType.PING -> Icons.Default.SignalCellularAlt
        NotificationType.SUCCESS -> Icons.Default.CheckCircle
        NotificationType.ERROR -> Icons.Default.Error
        NotificationType.NORMAL -> Icons.Default.Info
    }
    val status = when (type) {
        NotificationType.ERROR -> colors.statusError
        NotificationType.SUCCESS -> colors.statusConnected
        else -> colors.textSecondary
    }
    NimboPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, if (compact) title else null, Modifier.size(20.dp), tint = status)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (!compact) {
                        Text(listOfNotNull(title, metaText).joinToString(" · "), color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                    // Ellipsis only bounds the transient banner; history retains the complete text.
                    Text(message, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                }
                if (actionIcon != null && onAction != null) IconButton(onAction,
                    Modifier.size(48.dp).testTag("notification-action")) {
                    Icon(actionIcon, actionDescription, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            if (showProgress) LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp),
                color = colors.textSecondary, trackColor = colors.controlFill)
        }
    }
}
