package com.danila.nimbo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.DayTraffic
import com.danila.nimbo.utils.SpeedSample
import com.danila.nimbo.utils.TrafficHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * График скорости за последнюю минуту: две залитые области — приём и отдача.
 * Точки приходят из [TrafficHistory], то есть из той же агрегированной
 * статистики ядра, что и счётчики на главной.
 */
@Composable
fun TrafficSpeedChart(
    samples: List<SpeedSample>,
    downloadColor: Color,
    uploadColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalNebulaColors.current
    val gridColor = colors.textPrimary.copy(alpha = 0.07f)

    Canvas(modifier = modifier) {
        drawSpeedGrid(gridColor)
        if (samples.size < 2) return@Canvas

        // Общий масштаб для обеих кривых: иначе отдача с её мелкими значениями
        // визуально спорила бы с приёмом и выглядела такой же «толстой».
        val peak = samples.maxOf { maxOf(it.up, it.down) }.coerceAtLeast(1L).toFloat()

        drawSpeedSeries(samples.map { it.down.toFloat() }, peak, downloadColor, fill = true)
        drawSpeedSeries(samples.map { it.up.toFloat() }, peak, uploadColor, fill = false)
    }
}

private fun DrawScope.drawSpeedGrid(color: Color) {
    val rows = 3
    for (index in 1 until rows) {
        val y = size.height * index / rows
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawSpeedSeries(
    values: List<Float>,
    peak: Float,
    color: Color,
    fill: Boolean
) {
    if (values.size < 2) return
    val stepX = size.width / (values.size - 1).toFloat()
    val line = Path()
    values.forEachIndexed { index, value ->
        val x = stepX * index
        val y = size.height - (value / peak).coerceIn(0f, 1f) * size.height
        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }

    if (fill) {
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0f))
            )
        )
    }

    drawPath(
        path = line,
        color = color,
        style = Stroke(width = if (fill) 2.5f.dp.toPx() else 1.8f.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * Столбики расхода по дням. Высота — суммарный трафик дня, внутри столбика
 * приём и отдача разделены цветом.
 */
@Composable
fun TrafficDailyBars(
    days: List<DayTraffic>,
    downloadColor: Color,
    uploadColor: Color,
    formatBytes: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val colors = LocalNebulaColors.current
    val peak = days.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
    val dayFormat = rememberDayLabelFormatter()

    // Высоты считаются явно, а не через weight: у weight значения нормализуются,
    // и день без трафика красился целиком цветом отдачи. Плюс подписи вынесены
    // из ряда со столбиками, иначе самый высокий столбик не помещался в карточку.
    val chartHeight = 104.dp
    val emptyHeight = 4.dp
    val minBarHeight = 10.dp

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                val fraction = (day.total.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
                val barHeight = if (day.total <= 0L) {
                    emptyHeight
                } else {
                    minBarHeight + (chartHeight - minBarHeight) * fraction
                }
                val downHeight = if (day.total > 0L) {
                    barHeight * (day.down.toFloat() / day.total.toFloat())
                } else {
                    0.dp
                }
                val upHeight = barHeight - downHeight

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.textPrimary.copy(alpha = 0.07f))
                ) {
                    if (day.total > 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(upHeight)
                                .background(uploadColor.copy(alpha = 0.9f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(downHeight)
                                .background(downloadColor.copy(alpha = 0.9f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEach { day ->
                Text(
                    text = dayFormat(day.date),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBytes(days.sumOf { it.total }),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(downloadColor, "↓ " + formatBytes(days.sumOf { it.down }))
                LegendDot(uploadColor, "↑ " + formatBytes(days.sumOf { it.up }))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val colors = LocalNebulaColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** «2026-08-23» -> «23.08», без создания форматтера на каждый столбик. */
@Composable
private fun rememberDayLabelFormatter(): (String) -> String {
    val formatter = androidx.compose.runtime.remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US) to SimpleDateFormat("dd.MM", Locale.US)
    }
    return { raw ->
        runCatching {
            val parsed: Date = formatter.first.parse(raw) ?: return@runCatching raw
            formatter.second.format(parsed)
        }.getOrDefault(raw)
    }
}
