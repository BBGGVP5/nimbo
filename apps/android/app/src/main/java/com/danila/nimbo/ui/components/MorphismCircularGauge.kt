package com.danila.nimbo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun MorphismCircularGauge(
    progress: Float,
    centerText: String,
    subText: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    val nebulaColors = LocalNebulaColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = modifier
            .size(size)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 🌌 Внешнее свечение (Glow)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .blur(12.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
        )

        // 💿 Основная подложка в стиле морфизма
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            nebulaColors.cardBackground.copy(alpha = 0.4f),
                            nebulaColors.cardBackground.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent,
                            color.copy(alpha = 0.3f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 🎨 Отрисовка прогресса (Радикальный круг)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val strokeWidth = 10.dp.toPx()
            
            // Фоновый круг
            drawCircle(
                color = color.copy(alpha = 0.1f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Дуга прогресса
            drawArc(
                brush = Brush.sweepGradient(
                    0f to color.copy(alpha = 0.5f),
                    0.5f to color,
                    1f to color.copy(alpha = 0.5f)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // 📝 Центральный текст и иконка
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = centerText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = nebulaColors.textPrimary
            )
            
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = nebulaColors.textTertiary
            )
        }
    }
}

