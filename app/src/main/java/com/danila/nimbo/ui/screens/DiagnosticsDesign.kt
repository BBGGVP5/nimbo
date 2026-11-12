package com.danila.nimbo.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.contrastingLabel
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalGlobalCornerRadius
import com.danila.nimbo.ui.theme.LocalNebulaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The three network tools share one header switcher; switching never stacks screens. */
enum class DiagnosticsTool { CHECK, PING, SPEED }

/** Status colours stay semantic in every style: green works, amber restricted, red down. */
internal val DiagnosticsOk = Color(0xFF7FD6A4)
internal val DiagnosticsWarn = Color(0xFFF2B35B)
internal val DiagnosticsBad = Color(0xFFEF7B72)

@Composable
private fun scaled(radius: Dp): Dp = radius * LocalGlobalCornerRadius.current

/** Small all-caps label, as in instrument panels: "СТАТУС СЕТИ", "ПИНГ, МС". */
@Composable
internal fun DiagnosticsEyebrow(text: String, modifier: Modifier = Modifier,
    color: Color = LocalNebulaColors.current.textTertiary) {
    Text(text.uppercase(), modifier, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium))
}

@Composable
internal fun DiagnosticsToolTabs(selected: DiagnosticsTool, onSelect: (DiagnosticsTool) -> Unit,
    modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    val outer = RoundedCornerShape(scaled(18.dp))
    val inner = RoundedCornerShape(scaled(14.dp))
    Row(modifier.fillMaxWidth().clip(outer).background(colors.controlFill)
        .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            Triple(DiagnosticsTool.CHECK, Icons.Default.NetworkCheck, t("Проверка", "Check")),
            Triple(DiagnosticsTool.PING, Icons.Default.Sensors, t("Пинг", "Ping")),
            Triple(DiagnosticsTool.SPEED, Icons.Default.Speed, t("Скорость", "Speed")),
        ).forEach { (tool, icon, label) ->
            val active = tool == selected
            Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(inner)
                .background(if (active) colors.softFill else Color.Transparent)
                .clickable(enabled = !active) { onSelect(tool) }
                .semantics { role = Role.Tab; this.selected = active }
                .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (active) colors.accent else colors.textSecondary)
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (active) colors.textPrimary else colors.textSecondary, maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Large instrument card with a faint dot grid behind the content. */
@Composable
internal fun DiagnosticsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalNebulaColors.current
    val dot = colors.textPrimary.copy(alpha = 0.05f)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(scaled(26.dp)), color = colors.panelFill,
        contentColor = colors.textPrimary, border = BorderStroke(1.dp, colors.panelBorder)) {
        Column(Modifier.drawBehind {
            val step = 14.dp.toPx()
            val radius = 0.9.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) { drawCircle(dot, radius, Offset(x, y)); x += step }
                y += step
            }
        }.padding(20.dp), content = content)
    }
}

/** Scalloped "cookie" outline used for the status badge. */
private val ScallopShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(cx, cy)
    val points = 120
    for (i in 0..points) {
        val a = 2 * PI * i / points
        val rr = r * (0.9f + 0.1f * cos(10 * a).toFloat())
        val x = cx + rr * cos(a).toFloat()
        val y = cy + rr * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
internal fun DiagnosticsBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(64.dp).background(tint, ScallopShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(28.dp), tint = contrastingLabel(tint))
    }
}

@Composable
internal fun DiagnosticsHeroTitle(title: String, description: String?) {
    val colors = LocalNebulaColors.current
    Text(title, color = colors.textPrimary, modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 38.sp))
    if (!description.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(description, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DiagnosticsHeroMetric(label: String, value: String, modifier: Modifier = Modifier,
    valueColor: Color = LocalNebulaColors.current.textPrimary) {
    Column(modifier) {
        DiagnosticsEyebrow(label)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, maxLines = 1,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun DiagnosticsPrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    val colors = LocalNebulaColors.current
    val label = contrastingLabel(colors.accent)
    Button(onClick, modifier.fillMaxWidth().height(58.dp), enabled = enabled && !busy,
        shape = RoundedCornerShape(scaled(29.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = label,
            disabledContainerColor = colors.accent.copy(alpha = if (busy) 0.55f else 0.25f),
            disabledContentColor = label.copy(alpha = 0.8f))) {
        if (busy) ExpressiveCircularLoader(Modifier.size(20.dp), strokeWidth = 2.dp, color = label)
        else Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary pill: tonal (filled) or outlined. */
@Composable
internal fun DiagnosticsSecondaryButton(text: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, tonal: Boolean = false, enabled: Boolean = true) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(scaled(26.dp))
    val content: @Composable () -> Unit = {
        Icon(icon, null, Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    if (tonal) {
        Button(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.softFill, contentColor = colors.textPrimary,
                disabledContainerColor = colors.controlFill, disabledContentColor = colors.textTertiary)) { content() }
    } else {
        OutlinedButton(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape,
            border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = if (enabled) 0.16f else 0.08f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary,
                disabledContentColor = colors.textTertiary)) { content() }
    }
}

@Composable
internal fun DiagnosticsSectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val colors = LocalNebulaColors.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DiagnosticsEyebrow(title, Modifier.weight(1f))
        if (actionLabel != null) TextButton(onAction) {
            Text(actionLabel, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pill text field with a leading icon, used for the ping target. */
@Composable
internal fun DiagnosticsTargetField(value: String, onValueChange: (String) -> Unit, placeholder: String,
    icon: ImageVector, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Uri) {
    val colors = LocalNebulaColors.current
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(scaled(18.dp)))
        .background(colors.controlFill).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = colors.textTertiary, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(value, onValueChange, Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done))
        }
    }
}

@Composable
internal fun DiagnosticsChips(
    items: List<String>,
    selected: String?,
    label: (String) -> String = { it },
    onPick: (String) -> Unit
) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(scaled(14.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val active = item == selected
            Box(Modifier.heightIn(min = 44.dp).clip(shape)
                .background(if (active) colors.softFill else Color.Transparent)
                .border(1.dp, colors.textPrimary.copy(alpha = if (active) 0.22f else 0.12f), shape)
                .clickable { onPick(item) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(label(item), color = colors.textPrimary,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}

/** Compact metric tile: label, value, unit — the three boxes under the speed gauge. */
@Composable
internal fun DiagnosticsSmallMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Surface(modifier, shape = RoundedCornerShape(scaled(20.dp)), color = colors.panelFill,
        border = BorderStroke(1.dp, colors.panelBorder)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            DiagnosticsEyebrow(label)
            Spacer(Modifier.height(6.dp))
            Text(value, color = colors.textPrimary, maxLines = 1,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(unit, color = colors.textTertiary, maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

/** Rounded-up gauge ceiling: 100, 250, 500, 1000… so the tick labels stay readable. */
internal fun speedGaugeMax(peak: Double): Double {
    val steps = listOf(100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10000.0)
    return steps.firstOrNull { peak * 1.15 <= it } ?: steps.last()
}

/** 270° speedometer with tick labels and a knob at the current value. */
@Composable
internal fun DiagnosticsSpeedGauge(value: Double, scaleMax: Double, phase: String, server: String?,
    progress: Float) {
    val colors = LocalNebulaColors.current
    val animated by animateFloatAsState(value.toFloat(),
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), label = "gauge")
    val animatedProgress by animateFloatAsState(progress,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "gaugeProgress")
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = colors.textTertiary)
    DiagnosticsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiagnosticsEyebrow(phase, Modifier.weight(1f))
            if (!server.isNullOrBlank()) DiagnosticsEyebrow(server, color = colors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(236.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2 + 26.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val fraction = (animated / scaleMax.toFloat()).coerceIn(0f, 1f)
                drawArc(colors.softFill, 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0f) drawArc(colors.accent, 135f, 270f * fraction, false, topLeft, arc,
                    style = Stroke(stroke, cap = StrokeCap.Round))
                val center = Offset(size.width / 2, size.height / 2)
                val radius = arc.width / 2
                val knobAngle = Math.toRadians((135f + 270f * fraction).toDouble())
                val knob = Offset(center.x + radius * cos(knobAngle).toFloat(), center.y + radius * sin(knobAngle).toFloat())
                drawCircle(colors.accent, stroke * 0.95f, knob)
                drawCircle(colors.textPrimary, stroke * 0.42f, knob)
                for (i in 0..4) {
                    val a = Math.toRadians((135f + 270f * i / 4f).toDouble())
                    val labelRadius = radius + stroke / 2 + 15.dp.toPx()
                    val dotRadius = radius - stroke / 2 - 9.dp.toPx()
                    drawCircle(colors.textTertiary, 1.5.dp.toPx(),
                        Offset(center.x + dotRadius * cos(a).toFloat(), center.y + dotRadius * sin(a).toFloat()))
                    val tick = scaleMax * i / 4
                    val text = if (tick >= 100 || tick == 0.0) tick.toInt().toString() else "%.1f".format(tick)
                    val layout = textMeasurer.measure(text, tickStyle)
                    drawText(layout, topLeft = Offset(
                        center.x + labelRadius * cos(a).toFloat() - layout.size.width / 2f,
                        center.y + labelRadius * sin(a).toFloat() - layout.size.height / 2f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 30.dp)) {
                Text(if (animated > 0f) "%.0f".format(animated) else "—", color = colors.textPrimary,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold))
                Text(t("Мбит/с", "Mbps"), color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
        LinearProgressIndicator(progress = { animatedProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = colors.accent, trackColor = colors.softFill, drawStopIndicator = {})
    }
}
