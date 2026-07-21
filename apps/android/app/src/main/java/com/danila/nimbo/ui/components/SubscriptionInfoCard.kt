package com.danila.nimbo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.theme.*

@Composable
fun SubscriptionInfoCard(
    profileName: String,
    originalName: String? = null,
    subscriptionUrl: String,
    announce: String?,
    daysUntilExpiry: Long,
    usedTraffic: Long,
    totalTraffic: Long,
    deviceLimit: Int,
    onlineDevices: Int,
    onDeviceClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    websiteUrl: String? = null,
    supportUrl: String? = null,
    numericId: String? = null,
    selectedServer: Server? = null,
    selectedServerPing: Int? = null,
    pingDisplayMode: Int = 0,
    onRefresh: () -> Unit = {},
    onPingAll: () -> Unit = {},
    isPinging: Boolean = false,
    isUpdating: Boolean = false,
    showActions: Boolean = true,
    isExpanded: Boolean = true,
    onUpdateExpanded: (Boolean) -> Unit = {}
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(24.dp)

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "chevron_rotation"
    )

    val isDangerExpiry = daysUntilExpiry in 0..3
    val expiryInfinite = rememberInfiniteTransition(label = "expiry_pulse")
    val expiryPulseAlpha by expiryInfinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "expiry_pulse_alpha"
    )

    val isMaterialYou = nebulaColors.isMaterialYou
    val fillBrush = if (isMaterialYou) null else Brush.linearGradient(
        colors = listOf(
            nebulaColors.accent.copy(alpha = 0.10f),
            nebulaColors.textPrimary.copy(alpha = 0.04f)
        )
    )
    val fillColor = if (isMaterialYou) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
    
    val borderBrush = if (isMaterialYou) null else Brush.verticalGradient(
        listOf(
            nebulaColors.accent.copy(alpha = 0.35f),
            nebulaColors.accent.copy(alpha = 0.10f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(fillColor)
            .then(if (fillBrush != null) Modifier.background(fillBrush) else Modifier)
            .then(
                if (borderBrush != null) {
                    Modifier.border(width = 1.dp, brush = borderBrush, shape = cardShape)
                } else Modifier
            )
            .clickable { onUpdateExpanded(!isExpanded) }
            .padding(22.dp)
    ) {
        Column {
            // Header: Name & Expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(nebulaColors.accent.copy(alpha = 0.4f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(1.dp, nebulaColors.accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud, 
                            null, 
                            tint = nebulaColors.accent, 
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = nebulaColors.accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Devices, null, tint = nebulaColors.accent, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(4.dp))
                                val limitText = if (deviceLimit <= 0) "∞" else "$deviceLimit"
                                AnimatedContent(
                                    targetState = "$onlineDevices шт / $limitText",
                                    transitionSpec = {
                                        (slideInVertically { it / 2 } + fadeIn(tween(180)))
                                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(160)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "device_count"
                                ) { current ->
                                    Text(
                                        text = current,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = nebulaColors.accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Expiry Badge First
                    val expiryTint = if (daysUntilExpiry > 3) nebulaColors.accent else Color(0xFFFF5252)
                    val expiryBgAlpha = if (isDangerExpiry) expiryPulseAlpha * 0.22f else 0.12f
                    val expiryBorderAlpha = if (isDangerExpiry) expiryPulseAlpha * 0.55f else 0.25f
                    Surface(
                        color = expiryTint.copy(alpha = expiryBgAlpha),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            expiryTint.copy(alpha = expiryBorderAlpha)
                        )
                    ) {
                        AnimatedContent(
                            targetState = formatDays(daysUntilExpiry).uppercase(),
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically { it / 2 })
                                    .togetherWith(fadeOut(tween(160)) + slideOutVertically { -it / 2 })
                                    .using(SizeTransform(clip = false))
                            },
                            label = "expiry_text"
                        ) { current ->
                            Text(
                                text = current,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = expiryTint,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (showActions) {
                        TopBarMorphIcon(
                            icon = Icons.Default.Refresh,
                            color = nebulaColors.accent,
                            onClick = onRefresh,
                            iconModifier = if (isUpdating) {
                                val infiniteTransition = rememberInfiniteTransition(label = "update")
                                val rotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "rotation"
                                )
                                Modifier.rotate(rotation)
                            } else Modifier
                        )

                        TopBarMorphIcon(
                            icon = Icons.Default.Speed,
                            color = nebulaColors.accent,
                            onClick = onPingAll,
                            iconModifier = if (isPinging) {
                                val infiniteTransition = rememberInfiniteTransition(label = "ping")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                Modifier.scale(scale)
                            } else Modifier
                        )
                    }

                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            // Announcement / Description
            if (!announce.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = nebulaColors.accent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = announce,
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (selectedServer != null) {
                Spacer(Modifier.height(14.dp))
                SelectedServerSummary(
                    server = selectedServer,
                    ping = selectedServerPing,
                    pingDisplayMode = pingDisplayMode
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(280)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(animationSpec = tween(160))
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))

                    // Stats: Traffic Blocks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatItem(
                            icon = Icons.Default.CloudDownload,
                            label = "Использовано",
                            value = formatTraffic(usedTraffic),
                            modifier = Modifier.weight(1f)
                        )
                        StatItem(
                            icon = Icons.Default.DataUsage,
                            label = "Лимит",
                            value = if (totalTraffic > 0) formatTraffic(totalTraffic) else "∞",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (totalTraffic > 0) {
                        Spacer(Modifier.height(14.dp))
                        TrafficProgressBar(
                            used = usedTraffic,
                            total = totalTraffic
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action Blocks: Row of 4 Premium Blocks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionBlock(
                            icon = Icons.Outlined.ContentCopy,
                            label = "Копир.",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Subscription URL", subscriptionUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                            }
                        )
                        ActionBlock(
                            icon = Icons.Default.QrCode,
                            label = "QR",
                            modifier = Modifier.weight(1f),
                            onClick = onShowQr
                        )
                        ActionBlock(
                            icon = Icons.Default.Devices,
                            label = "Устр.",
                            modifier = Modifier.weight(1f),
                            onClick = onDeviceClick
                        )
                        ActionBlock(
                            icon = Icons.Outlined.Edit,
                            label = "Имя",
                            modifier = Modifier.weight(1f),
                            onClick = onRename
                        )
                        ActionBlock(
                            icon = Icons.Outlined.Delete,
                            label = "Удалить",
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFFF5252),
                            onClick = onDelete
                        )
                    }

                    // External Links: Profile Buttons
                    val finalWebsiteUrl = websiteUrl ?: "https://t.me/nebulaguardd_bot"
                    
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LinkButton(
                            icon = Icons.Default.Language,
                            label = if (websiteUrl != null) "Личный кабинет" else "Сайт",
                            modifier = Modifier.weight(1f)
                        ) { openUrl(context, finalWebsiteUrl) }

                        if (supportUrl != null) {
                            LinkButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                label = "Поддержка",
                                modifier = Modifier.weight(1f)
                            ) { openUrl(context, supportUrl) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedServerSummary(
    server: Server,
    ping: Int?,
    pingDisplayMode: Int
) {
    val nebulaColors = LocalNebulaColors.current
    val flag = extractFlagEmoji(server.name)
    val name = cleanServerName(server.name).ifBlank { "Сервер" }
    val pingValue = ping ?: -1
    val pingColor = when {
        pingValue == -1 -> nebulaColors.statusDisconnected
        pingValue <= 70 -> nebulaColors.statusConnected
        pingValue <= 120 -> Color(0xFFCDDC39)
        pingValue <= 220 -> Color(0xFFFF9800)
        else -> nebulaColors.statusDisconnected
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = nebulaColors.textPrimary.copy(alpha = 0.055f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(nebulaColors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (flag.isNotEmpty()) {
                    Text(flag, fontSize = 18.sp)
                } else {
                    Icon(Icons.Default.Public, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Выбранный сервер",
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    name,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(pingColor.copy(alpha = 0.14f))
                    .border(1.dp, pingColor.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Speed, null, tint = pingColor, modifier = Modifier.size(13.dp))
                if (pingDisplayMode == 2) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(pingColor)
                    )
                } else {
                    Text(
                        text = if (pingDisplayMode == 1) {
                            if (pingValue == -1) "нет" else "ok"
                        } else {
                            if (pingValue == -1) "н/д" else "${pingValue}мс"
                        },
                        color = pingColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    
    val containerColor = if (materialYou) MaterialTheme.colorScheme.surfaceContainerLow else nebulaColors.textPrimary.copy(alpha = 0.04f)
    val borderStroke = if (materialYou) null else androidx.compose.foundation.BorderStroke(
        1.dp,
        nebulaColors.textPrimary.copy(alpha = 0.1f)
    )
    val iconColor = if (materialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = nebulaColors.textTertiary,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(160)))
                        .using(SizeTransform(clip = false))
                },
                label = "stat_value"
            ) { current ->
                Text(
                    text = current,
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrafficProgressBar(
    used: Long,
    total: Long
) {
    val nebulaColors = LocalNebulaColors.current
    val safeTotal = total.coerceAtLeast(1L)
    val rawProgress = (used.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "traffic_progress"
    )

    val barColor = when {
        rawProgress >= 0.9f -> Color(0xFFFF5252)
        rawProgress >= 0.7f -> Color(0xFFFF9800)
        else -> nebulaColors.accent
    }
    val animatedBarColor by animateColorAsState(
        targetValue = barColor,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "traffic_bar_color"
    )

    val shimmerTransition = rememberInfiniteTransition(label = "traffic_shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "traffic_shimmer_offset"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Трафик",
                style = MaterialTheme.typography.labelSmall,
                color = nebulaColors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            AnimatedContent(
                targetState = "${(rawProgress * 100).toInt()}%",
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(140)))
                        .using(SizeTransform(clip = false))
                },
                label = "traffic_percent"
            ) { current ->
                Text(
                    text = current,
                    style = MaterialTheme.typography.labelSmall,
                    color = animatedBarColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(nebulaColors.textPrimary.copy(alpha = 0.07f))
                .border(
                    1.dp,
                    nebulaColors.textPrimary.copy(alpha = 0.08f),
                    RoundedCornerShape(999.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                animatedBarColor.copy(alpha = 0.75f),
                                animatedBarColor
                            )
                        )
                    )
            ) {
                if (progress > 0.02f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.35f),
                                        Color.Transparent
                                    ),
                                    startX = shimmerOffset * 400f,
                                    endX = (shimmerOffset + 0.4f) * 400f
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBlock(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "action_block_scale"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.14f else 0.07f,
        animationSpec = tween(durationMillis = 140),
        label = "action_block_bg"
    )
    
    val containerColor = when {
        materialYou && isPressed -> MaterialTheme.colorScheme.surfaceVariant
        materialYou -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> nebulaColors.textPrimary.copy(alpha = bgAlpha)
    }
    val borderStroke = if (materialYou) null else androidx.compose.foundation.BorderStroke(
        1.dp,
        Brush.verticalGradient(listOf(nebulaColors.textPrimary.copy(alpha = 0.12f), Color.Transparent))
    )
    val iconColor = color ?: if (materialYou) MaterialTheme.colorScheme.primary else nebulaColors.accent
    val textColor = color ?: if (materialYou) MaterialTheme.colorScheme.onSurfaceVariant else nebulaColors.textSecondary

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(68.dp)
            .scale(pressScale),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = borderStroke,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                tint = (color ?: nebulaColors.accent).copy(alpha = 0.95f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = (color ?: nebulaColors.textSecondary),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

 @Composable
private fun LinkButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val materialYou = nebulaColors.isMaterialYou
    
    val containerColor = if (materialYou) MaterialTheme.colorScheme.secondaryContainer else nebulaColors.accent.copy(alpha = 0.14f)
    val contentColor = if (materialYou) MaterialTheme.colorScheme.onSecondaryContainer else nebulaColors.accent
    val borderStroke = if (materialYou) null else androidx.compose.foundation.BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.3f))
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = borderStroke,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun formatDays(days: Long): String {
    return when {
        days < 0 -> "Бессрочно"
        days == 0L -> "Истекает сегодня"
        days == 1L -> "1 день"
        days in 2..4 -> "$days дня"
        else -> "$days дней"
    }
}

private fun formatTraffic(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroup = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroup.toDouble()), units[digitGroup])
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

