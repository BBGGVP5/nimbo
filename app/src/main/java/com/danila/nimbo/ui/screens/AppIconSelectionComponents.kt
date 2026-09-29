@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danila.nimbo.R
import com.danila.nimbo.utils.AppIconManager

@Composable
internal fun LauncherIconGallery(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val selected = AppIconManager.ICON_OPTIONS.getOrElse(selectedIndex) { AppIconManager.ICON_OPTIONS.first() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(colors.surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconResourceImage(selected.previewRes, Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Иконка приложения", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(selected.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.Check, "Выбрано", tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Text("Готовые варианты меняют основную иконку Nimbo. Нажмите, чтобы выбрать.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        LazyVerticalGrid(
            columns = GridCells.Adaptive((88f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp),
            modifier = Modifier.weight(1f).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            itemsIndexed(AppIconManager.ICON_OPTIONS, key = { _, option -> option.aliasSuffix }) { index, option ->
                IconChoiceTile(
                    label = option.title,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    description = "${option.title}. ${option.description}"
                ) {
                    AppIconResourceImage(option.previewRes, Modifier.size(56.dp))
                }
            }
        }
    }
}

/** Uses actual available width, including split screen, and allows text to grow. */
@Composable
internal fun IconChoiceGrid(count: Int, content: @Composable (Int) -> Unit) {
    val minCellWidth = (88f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + 8.dp) / (minCellWidth + 8.dp)).toInt().coerceIn(1, count)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until count).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { index -> Box(Modifier.weight(1f)) { content(index) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun IconChoiceTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String = label,
    artwork: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        color = if (selected) colors.surfaceVariant else Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                artwork()
                if (selected) Icon(
                    Icons.Default.Check, contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(18.dp)
                        .background(colors.primary, CircleShape).padding(2.dp)
                )
            }
            Text(label, color = colors.onSurface, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
internal fun IconEditorHeading(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun IconEditorToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun IconColorRow(
    title: String,
    colors: List<Int>,
    selectedColor: Int,
    onSelected: (Int) -> Unit,
    onOpenPalette: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconEditorHeading(title)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.forEach { value ->
                val selected = value == selectedColor
                val color = Color(value)
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSelected(value) })
                        .semantics { contentDescription = "$title, #%06X".format(value and 0xFFFFFF) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(34.dp).background(color, CircleShape)
                        .border(if (selected) 2.dp else 1.dp,
                            if (selected) scheme.onSurface else scheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp),
                            tint = if (color.luminance() > 0.179f) Color.Black else Color.White)
                    }
                }
            }
            if (onOpenPalette != null) {
                IconButton(onClick = onOpenPalette, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "Выбрать свой цвет. Текущий #%06X".format(selectedColor and 0xFFFFFF) }) {
                    Canvas(Modifier.size(34.dp)) {
                        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        drawCircle(scheme.surface, radius = size.minDimension * 0.3f)
                        drawCircle(Color(selectedColor), radius = size.minDimension * 0.23f)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NotificationIconPreview(bitmap: Bitmap, usesCustomIcon: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nimbo · пример уведомления", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            Text(if (usesCustomIcon) "Ваша картинка справа" else "Стандартная картинка справа",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (usesCustomIcon) Image(bitmap.asImageBitmap(), "Большая картинка уведомления", Modifier.size(44.dp))
        else Image(painterResource(R.drawable.nimbo_beta_notification), "Стандартная картинка уведомления", Modifier.size(44.dp))
    }
}
