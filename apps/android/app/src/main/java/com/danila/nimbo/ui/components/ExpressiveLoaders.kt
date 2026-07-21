package com.danila.nimbo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ExpressiveCircularLoader(
    modifier: Modifier = Modifier.size(64.dp),
    color: Color = LocalNebulaColors.current.accent,
    strokeWidth: Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive-loader")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 470f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive-loader-rotation"
    )
    
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive-loader-sweep"
    )

    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val radius = min(size.width, size.height) / 2f - stroke * 1.7f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // Draw background subtle track (clean smooth circle)
        drawArc(
            color = color.copy(alpha = 0.14f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // M3 sweeping progress values (expand and contract dynamically)
        val sweepAngleTarget = if (sweepPhase < 0.5f) {
            val t = sweepPhase / 0.5f
            30f + 250f * FastOutSlowInEasing.transform(t)
        } else {
            val t = (sweepPhase - 0.5f) / 0.5f
            280f - 250f * FastOutSlowInEasing.transform(t)
        }

        val startAngleOffset = if (sweepPhase < 0.5f) {
            0f
        } else {
            val t = (sweepPhase - 0.5f) / 0.5f
            250f * FastOutSlowInEasing.transform(t)
        }

        val startAngle = rotation + startAngleOffset
        val sweep = sweepAngleTarget
        val segments = 72 // Beautiful crisp rendering
        val activePath = Path()
        
        repeat(segments + 1) { index ->
            val t = index / segments.toFloat()
            val currentAngleDeg = startAngle + sweep * t
            val angleRad = Math.toRadians(currentAngleDeg.toDouble())
            
            // Premium, organic scalloped wave (6 waves around a full circle)
            // It has smooth, rounded flowing peaks and valleys matching Android 15/16/17 wavy seekbar
            val angleInPeriod = currentAngleDeg * (6f / 360f) * 2f * Math.PI
            val wave = sin(angleInPeriod - sweepPhase * 2f * Math.PI).toFloat()
            
            // Dampen near the tips so the line meets the main circle nicely
            val easingBulge = sin(t * PI).toFloat()
            val r = radius + wave * stroke * 0.55f * easingBulge
            
            val point = Offset(
                x = center.x + cos(angleRad).toFloat() * r,
                y = center.y + sin(angleRad).toFloat() * r
            )
            
            if (index == 0) {
                activePath.moveTo(point.x, point.y)
            } else {
                activePath.lineTo(point.x, point.y)
            }
        }

        drawPath(
            path = activePath,
            color = color.copy(alpha = 0.95f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun ExpressiveLoadingPane(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LocalNebulaColors.current.accent
) {
    val nebulaColors = LocalNebulaColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveCircularLoader(color = color)
        Text(
            text = label,
            color = nebulaColors.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
