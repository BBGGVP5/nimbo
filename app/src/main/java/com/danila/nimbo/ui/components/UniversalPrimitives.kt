@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.unit.sp
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.ui.theme.NimboHeadingFont
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.painterResource
import com.danila.nimbo.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import com.danila.nimbo.shared.ui.NimboConnectionHalo
import com.danila.nimbo.shared.ui.rememberNimboConnectionMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FastOutSlowInEasing

/** Measured floating navigation extent, including system inset and scaled labels. */
val LocalFloatingNavHeight = compositionLocalOf { 132.dp }

/** Shared native surfaces. Feature owners supply state and callbacks, never demo data. */
@Composable
fun NimboPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalNebulaColors.current
    Surface(modifier, shape = RoundedCornerShape(15.dp), color = colors.panelFill,
        contentColor = colors.textPrimary, border = BorderStroke(1.dp, colors.panelBorder),
        tonalElevation = 0.dp, content = content)
}

fun contrastingLabel(fill: Color): Color = if (fill.luminance() > 0.179f) Color.Black else Color.White

@Composable
fun NimboAction(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, busy: Boolean = false) {
    val colors = LocalNebulaColors.current
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = !busy,
        shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, colors.panelBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary,
            containerColor = colors.controlFill)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.textPrimary)
        else Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** A transition must never advertise an established connection. */
internal fun usesConnectedCloud(connected: Boolean, connecting: Boolean, disconnecting: Boolean = false): Boolean =
    connected && !connecting && !disconnecting

@Composable
fun NimboConnectionIcon(connected: Boolean, connecting: Boolean, modifier: Modifier = Modifier,
    disconnecting: Boolean = false, tint: Color = LocalContentColor.current,
    contentDescription: String? = null) {
    val showCloud = usesConnectedCloud(connected, connecting, disconnecting)
    val enabled = LocalBackgroundAnimationEnabled.current && !LocalReducedTransparencyEnabled.current
    val progress by animateFloatAsState(if (showCloud) 1f else 0f,
        animationSpec = if (enabled) tween(420, easing = FastOutSlowInEasing) else snap(), label = "connection-cloud-transform")
    Box(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PowerSettingsNew, null,
            Modifier.fillMaxSize(.78f).testTag(if (showCloud) "connection-power-layer" else "connection-power").graphicsLayer {
                alpha = 1f - progress; scaleX = 1f - .5f * progress; scaleY = scaleX; rotationZ = -45f * progress
            }, tint = tint)
        Icon(painterResource(R.drawable.nimbo_cloud), null,
            Modifier.fillMaxSize().testTag(if (showCloud) "connection-cloud" else "connection-cloud-layer").graphicsLayer {
                alpha = progress; scaleX = .55f + .45f * progress; scaleY = scaleX; translationY = (1f - progress) * 3.dp.toPx()
            }, tint = tint)
    }
}

@Composable
fun NimboConnectionControl(connected: Boolean, connecting: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false, disconnecting: Boolean = false,
    statusText: String? = null) {
    val colors = LocalNebulaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = rememberNimboConnectionMotion(
        connected = usesConnectedCloud(connected, connecting, disconnecting),
        busy = connecting || disconnecting, pressed = pressed,
        enabled = LocalBackgroundAnimationEnabled.current && !LocalReducedTransparencyEnabled.current)
    val label = when {
        disconnecting -> t("Отключение", "Disconnecting")
        connecting -> t("Отменить подключение", "Cancel connection")
        connected -> t("Отключить", "Disconnect")
        else -> t("Подключить", "Connect")
    }
    val stateLabel = when {
        disconnecting -> t("Отключение", "Disconnecting")
        connecting -> t("Подключение", "Connecting")
        connected -> t("Подключено", "Connected")
        else -> t("Отключено", "Disconnected")
    }
    val controlSemantics = Modifier.testTag("connection-control").semantics {
        contentDescription = label
        role = Role.Button
        stateDescription = stateLabel
    }
    if (compact) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).then(controlSemantics)
            .graphicsLayer { scaleX = motion.scale.value; scaleY = motion.scale.value },
            enabled = !disconnecting, shape = RoundedCornerShape(12.dp),
            interactionSource = interactionSource,
            border = BorderStroke(1.dp, colors.panelBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary,
                containerColor = colors.controlFill)) {
            NimboConnectionIcon(connected, connecting, Modifier.size(22.dp).graphicsLayer {
                scaleX = motion.iconScale.value; scaleY = motion.iconScale.value
            }, disconnecting)
            if (connecting || disconnecting) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(18.dp).testTag("connection-progress"),
                    strokeWidth = 2.dp, color = colors.textPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(statusText ?: label, style = MaterialTheme.typography.labelLarge)
        }
        NimboOperationPhrase(NimboOperation.Connection, connecting && !disconnecting, Modifier.padding(top = 8.dp))
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(172.dp).testTag("connection-ring")
                .graphicsLayer { scaleX = motion.scale.value; scaleY = motion.scale.value }
                .border(1.dp, colors.panelBorder, CircleShape), contentAlignment = Alignment.Center) {
            NimboConnectionHalo(motion, colors.accent, Modifier.fillMaxSize())
            Surface(onClick = onClick, enabled = !disconnecting,
                modifier = Modifier.size(156.dp).then(controlSemantics),
                interactionSource = interactionSource,
                shape = CircleShape, color = colors.accent, contentColor = contrastingLabel(colors.accent),
                border = BorderStroke(1.dp, colors.background)) {
                Box(contentAlignment = Alignment.Center) {
                    if (connecting || disconnecting) CircularProgressIndicator(
                        Modifier.size(140.dp).testTag("connection-progress"),
                        color = contrastingLabel(colors.accent), strokeWidth = 2.dp)
                    NimboConnectionIcon(connected, connecting, Modifier.size(48.dp).graphicsLayer {
                        scaleX = motion.iconScale.value; scaleY = motion.iconScale.value
                    }, disconnecting)
                }
            }
            }
            Text(statusText ?: label, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 15.dp))
            NimboOperationPhrase(NimboOperation.Connection, connecting && !disconnecting, Modifier.padding(top = 8.dp))
        }
    }
}

/** Header and card body toggle the same disclosure; nested actions remain independent. */
@Composable
fun NimboSubscriptionHeader(title: String, subtitle: String, expanded: Boolean,
    onToggle: (() -> Unit)?, onInfo: () -> Unit, logo: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {}) {
    val colors = LocalNebulaColors.current
    val expansionLabel = if (expanded) t("Развёрнуто", "Expanded") else t("Свёрнуто", "Collapsed")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Inside a subscription panel the surface owns disclosure, including this
        // header. A plain Row introduces no second Surface/pointer-input layer.
        Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("subscription-toggle")
            .then(if (onToggle != null) Modifier.clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = expansionLabel } else Modifier)) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                logo()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = colors.textPrimary,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(if (expanded) 180f else 0f), tint = colors.textSecondary)
            }
        }
        IconButton(onInfo, Modifier.size(48.dp).testTag("subscription-info")) {
            Icon(Icons.Default.Info, t("Информация о подписке", "Subscription information"), tint = colors.textSecondary)
        }
        trailing()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NimboServerRow(title: String, subtitle: String, selected: Boolean,
    onSelect: () -> Unit, onOpenMenu: () -> Unit,
    menu: @Composable () -> Unit, ping: @Composable () -> Unit, flag: String = "") {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth()
        .border(if (selected) 2.dp else 0.dp, if (selected) colors.accent else Color.Transparent, shape)
        .combinedClickable(onClick = onSelect, onLongClick = onOpenMenu,
            onLongClickLabel = t("Действия с сервером", "Server actions"))
        .semantics { this.selected = selected; role = Role.RadioButton }
        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.weight(1f).heightIn(min = 56.dp), color = Color.Transparent) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NimboServerFlag(flag)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Box {
                        Text(title, style = MaterialTheme.typography.titleSmall,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.accent else colors.textPrimary)
                        menu()
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Surface(color = Color.Transparent, shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)) {
            Box(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { ping() }
        }
    }
}

/** Full-width choices grow with text; the row owns a single accessible radio target. */
@Composable
fun NimboChoiceRow(title: String, selected: Boolean, onClick: () -> Unit,
    subtitle: String? = null, divider: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    val colors = LocalNebulaColors.current
    Column {
        Surface(onClick = onClick, color = if (selected) colors.controlFill else Color.Transparent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics {
                this.selected = selected
                role = Role.RadioButton
            }) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(8.dp))
                RadioButton(selected, onClick = null)
            }
        }
        if (divider) HorizontalDivider(color = colors.divider)
    }
}

/** Preview accordion server row: selection and latency in one compact, wrapping row. */
@Composable
fun NimboSubscriptionServerRow(title: String, subtitle: String, selected: Boolean,
    onClick: () -> Unit, latency: @Composable () -> Unit, flag: String = "") {
    val colors = LocalNebulaColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(9.dp),
        color = if (selected) colors.accent.copy(alpha = .22f) else Color.Transparent,
        border = if (selected) BorderStroke(2.dp, colors.accent) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 57.dp).semantics { this.selected = selected }) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NimboServerFlag(flag)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                latency()
                if (selected) Text("✓ " + t("Выбран", "Selected"), style = MaterialTheme.typography.labelSmall, color = colors.accent)
            }
        }
    }
}

val LocalNimboImportAction = compositionLocalOf<(() -> Unit)?> { null }

/** Keep words intact on narrow screens at large system font sizes.
 * Only the connection heading is fitted; the rest of the app retains system scaling.
 */
@Composable
fun NimboConnectionHeading(text: String) {
    val style = MaterialTheme.typography.headlineMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = LocalNebulaColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availablePx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val fittedSize = remember(text, style, measurer, availablePx) {
            val words = text.split(Regex("\\s+")).filter(String::isNotBlank)
            fun widestWord(size: Float): Int = words.maxOfOrNull { word ->
                measurer.measure(word, style = style.copy(fontSize = size.sp),
                    softWrap = false, maxLines = 1).size.width
            } ?: 0
            val preferred = style.fontSize.value
            if (widestWord(preferred) <= availablePx) style.fontSize else {
                // Measure at each size rather than assuming linear Android font scaling.
                var low = minOf(10f, preferred)
                var high = preferred
                repeat(10) {
                    val candidate = (low + high) / 2f
                    if (widestWord(candidate) <= availablePx - 2) low = candidate else high = candidate
                }
                low.sp
            }
        }
        Text(text, modifier = Modifier.fillMaxWidth().testTag("connection-heading").semantics { heading() },
            style = style.copy(fontSize = fittedSize), color = colors.textPrimary,
            softWrap = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun NimboBrandHeader(onImport: (() -> Unit)? = LocalNimboImportAction.current) {
    val colors = LocalNebulaColors.current
    val preferences = LocalPreferencesManager.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center) {
            Text("nimbo", fontFamily = NimboHeadingFont, fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = colors.textPrimary)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Surface(onClick = {
            preferences.pureBlackMode = false
            preferences.themeMode = if (colors.background.luminance() > 0.5f) 2 else 1
        },
            modifier = Modifier.size(48.dp), shape = CircleShape, color = colors.panelFill,
            border = BorderStroke(1.dp, colors.panelBorder)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (colors.background.luminance() > 0.5f) Icons.Default.DarkMode else Icons.Default.LightMode,
                    t("Переключить тему", "Toggle theme"), Modifier.size(20.dp), tint = colors.textPrimary)
            }
        }
        if (onImport != null) {
            Spacer(Modifier.width(8.dp))
            Surface(onClick = onImport, modifier = Modifier.size(48.dp), shape = CircleShape,
                color = colors.panelFill, border = BorderStroke(1.dp, colors.panelBorder)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, t("Добавить подписку", "Add subscription"), Modifier.size(22.dp), tint = colors.textPrimary)
                }
            }
        }
    }
}

/** Flag supplied by the provider name (emoji or country label), never inferred from IP. */
@Composable
fun NimboServerFlag(flag: String) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        if (flag.isNotBlank()) Text(flag, fontSize = 23.sp)
        else Icon(Icons.Default.SignalCellularAlt, null, Modifier.size(20.dp),
            tint = LocalNebulaColors.current.textSecondary)
    }
}

/** Use Material's clickable overload: a plain Surface adds its own pointer layer. */
@Composable
fun NimboSubscriptionPanel(expanded: Boolean, onToggle: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val label = if (expanded) t("Свернуть подписку", "Collapse subscription") else t("Развернуть подписку", "Expand subscription")
    val colors = LocalNebulaColors.current
    Surface(onClick = onToggle,
        modifier = modifier.testTag("subscription-card").semantics {
            stateDescription = label
            role = Role.Button
        },
        shape = RoundedCornerShape(15.dp), color = colors.panelFill,
        contentColor = colors.textPrimary, border = BorderStroke(1.dp, colors.panelBorder),
        tonalElevation = 0.dp, content = content)
}

@Composable
fun NimboIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    busy: Boolean = false, onClick: () -> Unit, allowCancel: Boolean = false) {
    IconButton(onClick, enabled = !busy || allowCancel, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        if (busy && allowCancel) Icon(Icons.Default.Stop, null, Modifier.size(22.dp), tint = LocalNebulaColors.current.accent)
        else if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalNebulaColors.current.accent)
        else Icon(icon, null, Modifier.size(22.dp), tint = LocalNebulaColors.current.textSecondary)
    }
}
