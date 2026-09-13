package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.network.PingDisplay
import com.danila.nimbo.network.pingBars

/** Uses the enclosing badge's existing color, shape and loading animation. */
@Composable
internal fun PingValueContent(ping: Int?, displayMode: Int, color: Color) {
    val mode = PingDisplay.fromId(displayMode)
    val label = if (ping == null || ping < 0) "—" else "$ping ms"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }
    ) {
        if (mode == PingDisplay.BARS || mode == PingDisplay.BOTH) {
            val bars = pingBars(ping)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(4) { index ->
                    Box(Modifier.width(3.dp).height((5 + index * 3).dp)
                        .background(color.copy(alpha = if (index < bars) 1f else 0.18f), RoundedCornerShape(1.dp)))
                }
            }
        }
        if (mode == PingDisplay.NUMERIC || mode == PingDisplay.BOTH) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}
