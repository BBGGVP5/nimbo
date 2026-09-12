package com.danila.nimbo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun VpnToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {

    val nebulaColors = LocalNebulaColors.current
    val color by animateColorAsState(
        targetValue =
            if (enabled) nebulaColors.statusConnected
            else nebulaColors.textTertiary,
        animationSpec = tween(400),
        label = "vpnToggleColor"
    )

    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {

        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f),
                checkedThumbColor = Color.White, // Keep white here as it's on accent
                uncheckedTrackColor = nebulaColors.textTertiary.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.White
            )
        )

    }
}
