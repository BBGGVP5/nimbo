package com.danila.nimbo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors

data class NimboChoiceOption(val value: Int, val title: String, val description: String? = null)

/** One envelope grows around its choices; no floating window or overlapping controls. */
@Composable
fun NimboExpandingChoiceCard(
    options: List<NimboChoiceOption>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "choice-card"
) {
    if (options.isEmpty()) return
    val colors = LocalNebulaColors.current
    val shape = nimboControlShape(12.dp, 3.dp)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selectedValue } ?: options.first()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "choice-chevron"
    )
    val expandedLabel = t("Развёрнуто", "Expanded")
    val collapsedLabel = t("Свёрнуто", "Collapsed")
    BackHandler(enabled = expanded) { expanded = false }

    Surface(
        modifier = modifier.fillMaxWidth().testTag(testTag).onPreviewKeyEvent {
            if (expanded && it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                expanded = false
                true
            } else false
        },
        shape = shape, color = colors.panelFill,
        border = BorderStroke(1.dp, colors.panelBorder)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().testTag("$testTag-header")
                    .semantics { stateDescription = if (expanded) expandedLabel else collapsedLabel }
                    .clickable(role = Role.Button, onClick = { expanded = !expanded })
                    .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(current.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    current.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ExpandMore, null, tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp).rotate(rotation))
            }
            // Compose honors the system animator duration scale, including zero.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top) + fadeIn(animationSpec = tween(150, delayMillis = 40)),
                exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top) + fadeOut(animationSpec = tween(100))
            ) {
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 12.dp))
                    options.forEach { option ->
                        key(option.value) {
                            Row(
                                Modifier.fillMaxWidth().testTag("$testTag-option-${option.value}")
                                    .background(if (option.value == selectedValue) colors.accent.copy(alpha = 0.08f)
                                        else androidx.compose.ui.graphics.Color.Transparent)
                                    .selectable(selected = option.value == selectedValue,
                                        enabled = expanded, role = Role.RadioButton, onClick = {
                                            onSelect(option.value)
                                            expanded = false
                                        })
                                    .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = option.value == selectedValue, onClick = null,
                                    enabled = expanded,
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.accent,
                                        unselectedColor = colors.textSecondary))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(option.title, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                    option.description?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
