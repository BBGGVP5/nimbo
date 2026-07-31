package com.danila.nimbo.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.vpn.VpnState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class SplashState {
    LOADING,
    READY
}

@Composable
fun SplashScreen(
    onComplete: () -> Unit,
    vpnState: VpnState = VpnState.DISCONNECTED,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "splash_new")
    var splashState by remember { mutableStateOf(SplashState.LOADING) }
    var expanded by remember { mutableStateOf(false) }
    val shouldShowSplash = vpnState == VpnState.DISCONNECTED

    val logoScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.82f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = EaseOut),
        label = "logoAlpha"
    )

    val auraShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraShift"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    val reverseRingRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverseRingRotation"
    )
    val shieldFloat by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldFloat"
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1650, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )
    val scanShift by infiniteTransition.animateFloat(
        initialValue = -34f,
        targetValue = 34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanShift"
    )

    LaunchedEffect(Unit) {
        delay(240)
        expanded = true
        delay(740)
        splashState = SplashState.READY
        delay(if (shouldShowSplash) 500 else 180)
        onComplete()
    }

    if (!shouldShowSplash) {
        LaunchedEffect(Unit) {
            delay(100)
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            nebulaColors.background,
                            nebulaColors.background.copy(alpha = 0.92f),
                            nebulaColors.surface.copy(alpha = 0.94f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )

                val cx = size.width * (0.26f + 0.10f * sin(2 * PI * auraShift).toFloat())
                val cy = size.height * (0.30f + 0.08f * cos(2 * PI * auraShift).toFloat())
                val bx = size.width * (0.73f + 0.07f * cos(2 * PI * auraShift).toFloat())
                val by = size.height * (0.70f + 0.06f * sin(2 * PI * auraShift).toFloat())

                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            nebulaColors.accent.copy(alpha = 0.24f),
                            nebulaColors.accent.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.44f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            nebulaColors.textPrimary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.42f,
                    center = Offset(bx, by)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                RotatingArcRing(
                    ringSize = 220.dp,
                    rotation = ringRotation,
                    color = nebulaColors.accent.copy(alpha = 0.64f),
                    secondary = nebulaColors.textPrimary.copy(alpha = 0.16f)
                )
                RotatingArcRing(
                    ringSize = 176.dp,
                    rotation = reverseRingRotation,
                    color = nebulaColors.accent.copy(alpha = 0.42f),
                    secondary = nebulaColors.textPrimary.copy(alpha = 0.1f)
                )

                Box(
                    modifier = Modifier
                        .size(122.dp)
                        .graphicsLayer {
                            scaleX = haloPulse
                            scaleY = haloPulse
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    nebulaColors.accent.copy(alpha = 0.34f),
                                    nebulaColors.accent.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    nebulaColors.surface.copy(alpha = 0.56f),
                                    nebulaColors.onSurface.copy(alpha = 0.16f)
                                )
                            )
                        )
                        .drawBehind {
                            drawCircle(
                                color = nebulaColors.textPrimary.copy(alpha = 0.12f),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = scanShift }
                            .height(14.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        nebulaColors.accent.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "NebulaGuard",
                        tint = nebulaColors.accent,
                        modifier = Modifier
                            .size(54.dp)
                            .graphicsLayer { translationY = shieldFloat }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "NebulaGuard",
                style = MaterialTheme.typography.headlineLarge,
                color = nebulaColors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = getSubtitleForState(splashState, vpnState),
                style = MaterialTheme.typography.bodyMedium,
                color = nebulaColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha }
            )

            Spacer(Modifier.height(24.dp))

            LoadingWave(infiniteTransition = infiniteTransition, accent = nebulaColors.accent)
        }
    }
}

@Composable
private fun RotatingArcRing(
    ringSize: androidx.compose.ui.unit.Dp,
    rotation: Float,
    color: Color,
    secondary: Color
) {
    Box(
        modifier = Modifier
            .size(ringSize)
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(this.size.width, this.size.height),
                    style = stroke
                )
                drawArc(
                    color = secondary,
                    startAngle = 145f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(this.size.width, this.size.height),
                    style = stroke
                )
            }
    )
}

@Composable
private fun LoadingWave(
    infiniteTransition: InfiniteTransition,
    accent: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val barScale by infiniteTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = EaseInOut, delayMillis = index * 95),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "barScale$index"
            )
            val barAlpha by infiniteTransition.animateFloat(
                initialValue = 0.34f,
                targetValue = 0.98f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = EaseInOut, delayMillis = index * 95),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "barAlpha$index"
            )

            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 24.dp)
                    .graphicsLayer {
                        scaleY = barScale
                        alpha = barAlpha
                    }
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.95f),
                                accent.copy(alpha = 0.4f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun getSubtitleForState(splashState: SplashState, vpnState: VpnState): String {
    return when {
        vpnState == VpnState.CONNECTED -> "VPN активен"
        splashState == SplashState.LOADING -> "Защищаем подключение..."
        else -> "Готово к работе"
    }
}

