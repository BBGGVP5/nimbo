package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object NimboPalette {
    val Background = Color(0xFF071426)
    val BackgroundDeep = Color(0xFF020A16)
    val Surface = Color(0xDA172842)
    val SurfaceStrong = Color(0xEE203654)
    val Control = Color(0xA92B456A)
    val Soft = Color(0x5C42618D)
    val Border = Color(0x5C8DB8F4)
    val Hairline = Color(0x2E9BC2F8)
    val Accent = Color(0xFF72A8FF)
    val AccentStrong = Color(0xFF4D88F1)
    val Text = Color(0xFFF3F6FF)
    val TextSecondary = Color(0xFF9DACCA)
    val TextTertiary = Color(0xFF657696)
    val Green = Color(0xFF62DFA5)
    val Amber = Color(0xFFFFBE55)
    val Red = Color(0xFFFF7A82)
}

internal val NimboTitleStyle = TextStyle(
    color = NimboPalette.Text,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.ExtraBold
)

internal val NimboSectionTitleStyle = TextStyle(
    color = NimboPalette.Text,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    fontWeight = FontWeight.Bold
)

internal val NimboBodyStyle = TextStyle(
    color = NimboPalette.TextSecondary,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium
)

@Composable
internal fun NimboSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    strong: Boolean = false,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        if (strong) NimboPalette.SurfaceStrong.copy(alpha = 0.96f)
                        else NimboPalette.Surface.copy(alpha = 0.90f),
                        if (strong) NimboPalette.SurfaceStrong.copy(alpha = 0.90f)
                        else NimboPalette.BackgroundDeep.copy(alpha = 0.58f)
                    )
                )
            )
            .border(1.dp, NimboPalette.Border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(padding),
        content = content
    )
}

internal enum class NimboIconName {
    HOME, PROFILES, APPS, SETTINGS, ADD, SEARCH, REFRESH, MORE, LIST,
    CLOUD, POWER, ROUTE, CONNECTION, NOTIFICATIONS, STATS, SYNC, LOGS,
    SUPPORT, SITE, SECURITY, INFO, DELETE, DOWNLOAD, FAVORITE
}

private fun iconVector(name: NimboIconName, selected: Boolean): ImageVector = when (name) {
    NimboIconName.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    NimboIconName.PROFILES -> if (selected) Icons.Filled.Public else Icons.Outlined.Public
    NimboIconName.APPS -> if (selected) Icons.Filled.Apps else Icons.Outlined.Apps
    NimboIconName.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    NimboIconName.ADD -> Icons.Filled.Add
    NimboIconName.SEARCH -> Icons.Filled.Search
    NimboIconName.REFRESH -> Icons.Filled.Refresh
    NimboIconName.MORE -> Icons.Filled.MoreVert
    NimboIconName.LIST -> Icons.Filled.List
    NimboIconName.CLOUD -> Icons.Filled.Cloud
    NimboIconName.POWER -> Icons.Filled.PowerSettingsNew
    NimboIconName.ROUTE -> Icons.Filled.Route
    NimboIconName.CONNECTION -> Icons.Filled.VpnKey
    NimboIconName.NOTIFICATIONS -> Icons.Filled.Notifications
    NimboIconName.STATS -> Icons.Filled.BarChart
    NimboIconName.SYNC -> Icons.Filled.Sync
    NimboIconName.LOGS -> Icons.Filled.Description
    NimboIconName.SUPPORT -> Icons.Filled.Security
    NimboIconName.SITE -> Icons.Filled.Language
    NimboIconName.SECURITY -> Icons.Filled.Security
    NimboIconName.INFO -> Icons.Filled.Info
    NimboIconName.DELETE -> Icons.Filled.Delete
    NimboIconName.DOWNLOAD -> Icons.Filled.CloudDownload
    NimboIconName.FAVORITE -> Icons.Filled.Favorite
}

@Composable
internal fun NimboIcon(
    name: NimboIconName,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = NimboPalette.Text,
    selected: Boolean = false
) {
    Icon(
        imageVector = iconVector(name, selected),
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}

@Composable
internal fun NimboIconButton(
    name: NimboIconName,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) NimboPalette.Accent.copy(alpha = 0.20f) else NimboPalette.Control)
            .border(
                1.dp,
                if (selected) NimboPalette.Accent.copy(alpha = 0.72f) else NimboPalette.Border,
                shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        NimboIcon(
            name = name,
            tint = if (selected) NimboPalette.Accent else NimboPalette.Text,
            selected = selected
        )
    }
}

@Composable
internal fun NimboPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(if (selected) NimboPalette.Accent.copy(alpha = 0.22f) else NimboPalette.Control)
            .border(
                1.dp,
                if (selected) NimboPalette.Accent.copy(alpha = 0.85f) else NimboPalette.Hairline,
                shape
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
        style = TextStyle(
            color = if (selected) NimboPalette.Accent else NimboPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

internal fun Modifier.nimboScreenPadding(): Modifier =
    fillMaxWidth().padding(horizontal = 20.dp)
