package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

enum class NotificationType { PING, UPDATE, SUCCESS, ERROR, NORMAL }

data class NotificationData(
    val message: String,
    val type: NotificationType = NotificationType.NORMAL,
    val id: Long = System.nanoTime()
)

@Composable
fun TopNotification(
    data: NotificationData?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var displayedData by remember { mutableStateOf(data) }
    LaunchedEffect(data) {
        if (data != null) displayedData = data
    }

    AnimatedVisibility(
        visible = data != null,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { -it - 12 }
        ) + fadeIn(tween(190)),
        exit = slideOutVertically(tween(220)) { -it - 12 } + fadeOut(tween(160)),
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 18.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        displayedData?.let { notification ->
            NotificationCard(notification, onDismiss)
        }
    }
}

@Composable
private fun NotificationCard(
    data: NotificationData,
    onDismiss: () -> Unit
) {
    NotificationSurface(
        message = data.message,
        type = data.type,
        animateIcon = true,
        showProgress = data.type == NotificationType.UPDATE || data.type == NotificationType.PING,
        actionIcon = Icons.Default.Close,
        actionDescription = "Закрыть уведомление",
        onAction = onDismiss
    )
}

@Composable
fun NotificationSurface(
    message: String,
    type: NotificationType,
    modifier: Modifier = Modifier,
    metaText: String? = null,
    animateIcon: Boolean = false,
    showProgress: Boolean = false,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val isLight = nebulaColors.background.luminance() > 0.5f
    val isPureBlack = nebulaColors.background == Color.Black
    val accent = when (type) {
        NotificationType.ERROR -> nebulaColors.statusError
        NotificationType.SUCCESS -> nebulaColors.statusConnected
        NotificationType.PING -> nebulaColors.statusConnecting
        else -> nebulaColors.accent
    }
    val title = when (type) {
        NotificationType.UPDATE -> "ОБНОВЛЕНИЕ"
        NotificationType.PING -> "ПРОВЕРКА СЕТИ"
        NotificationType.SUCCESS -> "ГОТОВО"
        NotificationType.ERROR -> "НУЖНО ВНИМАНИЕ"
        NotificationType.NORMAL -> "NIMBO"
    }
    val shape = when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape(24.dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape(18.dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(12.dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape(10.dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape(22.dp)
    }
    val isMaterial = nebulaColors.isMaterialYou || elementStyle == ElementStyleMode.MATERIAL3

    val infiniteTransition = rememberInfiniteTransition(label = "notification_motion")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "update_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notification_pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notification_glow"
    )
    val isActive = type == NotificationType.UPDATE || type == NotificationType.PING
    val baseColor = when {
        isMaterial -> nebulaColors.panelFill
        isPureBlack -> Color.Black.copy(alpha = 0.94f)
        isLight -> Color.White.copy(alpha = 0.94f)
        else -> nebulaColors.surface.copy(alpha = 0.92f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.12f),
                spotColor = accent.copy(alpha = if (isActive) glowAlpha else 0.18f)
            )
            .clip(shape)
            .background(baseColor)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (isLight) 0.09f else 0.16f),
                        Color.Transparent,
                        nebulaColors.textPrimary.copy(alpha = if (isLight) 0.015f else 0.035f)
                    )
                )
            )
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                    Modifier.dotPatternOverlay(
                        color = nebulaColors.textPrimary,
                        spacing = 9.dp,
                        radius = 0.8.dp,
                        alpha = 0.11f
                    )
                } else Modifier
            )
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (isActive) 0.36f else 0.24f),
                shape = shape
            )
    ) {
        AnimatedContent(
            targetState = Triple(message, type, metaText),
            transitionSpec = {
                if (animateIcon) {
                    (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(140))) using
                        SizeTransform(clip = false)
                } else {
                    fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                }
            },
            contentAlignment = Alignment.TopStart,
            label = "notification_content"
        ) { (currentMessage, currentType, currentMeta) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 11.dp, end = 8.dp, bottom = if (showProgress) 23.dp else 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                        .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val iconModifier = when {
                        !animateIcon -> Modifier
                        currentType == NotificationType.UPDATE -> Modifier.rotate(rotation)
                        currentType == NotificationType.PING -> Modifier.scale(pulse)
                        else -> Modifier
                    }
                    Icon(
                        imageVector = when (currentType) {
                            NotificationType.UPDATE -> Icons.Default.Refresh
                            NotificationType.PING -> Icons.Default.SignalCellularAlt
                            NotificationType.SUCCESS -> Icons.Default.CheckCircle
                            NotificationType.ERROR -> Icons.Default.Error
                            NotificationType.NORMAL -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = iconModifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                        if (currentMeta != null) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = currentMeta,
                                color = nebulaColors.textTertiary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = currentMessage,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (actionIcon != null && onAction != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAction)
                            .semantics { role = Role.Button },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = actionDescription,
                            tint = nebulaColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (showProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = accent.copy(alpha = 0.08f)
            )
        }
    }
}
