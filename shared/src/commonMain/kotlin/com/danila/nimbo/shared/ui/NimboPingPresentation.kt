package com.danila.nimbo.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalNimboPingDisplay = staticCompositionLocalOf { "numeric" }

internal fun normalizePingProtocol(value: String): String = when (value) {
    "http" -> "http_head"
    "nimbo", "tcp", "http_get", "http_head", "icmp" -> value
    else -> "tcp"
}

internal fun normalizePingDisplay(value: String): String = when (value) {
    "numeric", "bars", "both", "dots" -> value
    else -> "numeric"
}

internal fun pingSignalLevel(value: Int?, running: Boolean): Int = when {
    running || value == null || value < 0 -> 0
    value < 100 -> 4
    value < 200 -> 3
    value < 400 -> 2
    else -> 1
}

internal fun pingStatusDescription(value: Int?, running: Boolean): String = when {
    running -> "Проверка пинга"
    value == null -> "Пинг ещё не измерен"
    value < 0 -> "Ответ не получен или замер недоступен"
    else -> "Пинг: $value мс"
}

@Composable
internal fun NimboPingBadge(server: NimboServerUi, selected: Boolean = false) {
    val display = normalizePingDisplay(LocalNimboPingDisplay.current)
    val accessible = Modifier.clearAndSetSemantics {
        contentDescription = pingStatusDescription(server.ping, server.pingInProgress)
    }
    val level = pingSignalLevel(server.ping, server.pingInProgress)
    // Missing, failed and pending results remain distinguishable in every display mode.
    if (display == "numeric" || level == 0) {
        NimboPill(server.pingLabel, modifier = accessible, selected = selected)
        return
    }
    val foreground = if (selected) NimboPalette.Accent else NimboPalette.Text
    val inactive = NimboPalette.TextSecondary.copy(alpha = 0.28f)
    Row(
        modifier = accessible
            .nimboControlSurface(nimboStyledShape(18.dp, 2.dp), accented = selected)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (display == "dots") {
            Canvas(Modifier.size(10.dp)) { drawCircle(foreground) }
        } else {
            Canvas(Modifier.size(19.dp, 15.dp)) {
                val barWidth = size.width / 7f
                repeat(4) { index ->
                    val height = size.height * (index + 1) / 4f
                    drawRoundRect(
                        color = if (index < level) foreground else inactive,
                        topLeft = Offset(index * barWidth * 2, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 2)
                    )
                }
            }
            if (display == "both") {
                BasicText(server.pingLabel, style = TextStyle(color = foreground, fontSize = 13.sp))
            }
        }
    }
}
