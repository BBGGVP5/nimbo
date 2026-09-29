@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors

/** Presentation only. Never changes option ordering or feature state. */
internal fun toolGridColumns(widthDp: Float, fontScale: Float, minCellDp: Float = 144f): Int =
    ((widthDp + 8f) / (minCellDp * fontScale.coerceAtLeast(1f) + 8f)).toInt().coerceIn(1, 4)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NimboToolActions(modifier: Modifier = Modifier, minCellDp: Float = 144f, maxColumns: Int = 4,
    content: @Composable FlowRowScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = toolGridColumns(maxWidth.value, LocalDensity.current.fontScale, minCellDp).coerceAtMost(maxColumns.coerceAtLeast(1))
        FlowRow(Modifier.fillMaxWidth().testTag("tool-actions-$columns"),
            maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun NimboToolSection(title: String, description: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalNebulaColors.current
    NimboPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            if (!description.isNullOrBlank()) Text(description, color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
internal fun NimboToolMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalNebulaColors.current
    Column(modifier.padding(vertical = 4.dp)) {
        Text(value, color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
