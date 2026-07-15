package com.danila.nimbo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.danila.nimbo.ui.theme.*
import kotlinx.coroutines.delay

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

@Composable
fun VpnConnectButton(
    state: VpnState,
    isRefreshing: Boolean = false,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val backgroundAnimationEnabled = LocalBackgroundAnimationEnabled.current
    val reducedTransparencyEnabled = LocalReducedTransparencyEnabled.current
    val haptic = LocalHapticFeedback.current
    var seconds by remember { mutableStateOf(0) }

    val motionEnabled = backgroundAnimationEnabled && !reducedTransparencyEnabled
    val ambientMotionEnabled = motionEnabled &&
        (state == VpnState.CONNECTING || isRefreshing)

    // Анимация всасывания кнопки при подключении
    val buttonScale by animateFloatAsState(
        targetValue = when (state) {
            VpnState.CONNECTING -> 0.88f
            VpnState.CONNECTED -> 0.92f
            VpnState.DISCONNECTED -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    val pulseScale: Float
    val glowAlpha: Float
    val rotation: Float
    val refreshRotation: Float
    if (ambientMotionEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "vpnButton")

        val animatedPulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        val animatedGlowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )
        val animatedRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing)
            ),
            label = "rotation"
        )
        val animatedRefreshRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshRotation"
        )

        pulseScale = animatedPulseScale
        glowAlpha = animatedGlowAlpha
        rotation = animatedRotation
        refreshRotation = animatedRefreshRotation
    } else {
        pulseScale = 1f
        glowAlpha = if (state == VpnState.DISCONNECTED) 0.22f else 0.5f
        rotation = 0f
        refreshRotation = 0f
    }

    // Таймер подключения
    LaunchedEffect(state) {
        if (state == VpnState.CONNECTED) {
            while (true) {
                delay(1000)
                seconds++
            }
        } else {
            seconds = 0
        }
    }

    val buttonColor = when (state) {
        VpnState.CONNECTED -> nebulaColors.statusConnected
        VpnState.CONNECTING -> nebulaColors.statusConnecting
        VpnState.DISCONNECTED -> nebulaColors.textTertiary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Вращающееся кольцо
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                )
        ) {
            // Внешнее свечение
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            buttonColor.copy(alpha = glowAlpha * 0.9f),
                            buttonColor.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2
                )
            }

            // Линии обновления (крутящиеся линии вокруг кнопки)
            if (isRefreshing) {
                repeat(3) { index ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(100.dp)
                    ) {
                        drawArc(
                            color = nebulaColors.accent.copy(alpha = 0.8f),
                            startAngle = refreshRotation + (index * 120f),
                            sweepAngle = 60f,
                            useCenter = false,
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Пульсирующие кольца
            if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) {
                val ringCount = if (state == VpnState.CONNECTING && motionEnabled) 3 else 1
                repeat(ringCount) { index ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding((index + 1) * 30.dp)
                    ) {
                        drawArc(
                            color = buttonColor.copy(alpha = (0.7f - index * 0.1f) * glowAlpha),
                            startAngle = rotation - (index * 25f),
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Основное кольцо прогресса
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp)
            ) {
                val stroke = 10.dp.toPx()

                // Фоновое кольцо
                drawArc(
                    color = nebulaColors.cardBackground,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )

                // Активное кольцо
                if (state == VpnState.CONNECTING) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                buttonColor,
                                buttonColor.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                } else if (state == VpnState.CONNECTED) {
                    drawArc(
                        color = buttonColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }

            // Центральная кнопка с 3D эффектом
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .scale(buttonScale)
                    .graphicsLayer {
                        shadowElevation = 40f
                        ambientShadowColor = buttonColor.copy(alpha = 0.5f)
                        spotShadowColor = buttonColor.copy(alpha = 0.5f)
                        shape = CircleShape
                        clip = true
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                buttonColor.copy(alpha = 0.55f),
                                buttonColor.copy(alpha = 0.3f),
                                nebulaColors.surface.copy(alpha = 0.8f),
                                nebulaColors.cardBackground
                            )
                        ),
                        shape = CircleShape
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                nebulaColors.textPrimary.copy(alpha = 0.1f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, nebulaColors.textPrimary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Внешнее светящееся кольцо
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                buttonColor.copy(alpha = 0.5f),
                                buttonColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2,
                        style = Stroke(4.dp.toPx())
                    )
                }

                // Градиентная обводка
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                buttonColor,
                                buttonColor.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(4.dp.toPx())
                    )
                }

                // Внутреннее свечение
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    buttonColor.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = buttonColor,
                    modifier = Modifier
                        .size(150.dp)
                        .then(
                            if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) {
                                Modifier.scale(pulseScale)
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            shadowElevation = 30f
                            ambientShadowColor = buttonColor.copy(alpha = 0.4f)
                            spotShadowColor = buttonColor.copy(alpha = 0.4f)
                        }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Текст статуса
        when (state) {
            VpnState.CONNECTING -> {
                Text(
                    text = "Подключение...",
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.statusConnecting,
                    fontWeight = FontWeight.Medium
                )
            }
            VpnState.CONNECTED -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Подключено",
                        style = MaterialTheme.typography.titleMedium,
                        color = nebulaColors.statusConnected,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTime(seconds),
                        style = MaterialTheme.typography.bodyLarge,
                        color = nebulaColors.textSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            VpnState.DISCONNECTED -> {
                Text(
                    text = "Нажмите для подключения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = nebulaColors.textTertiary,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
