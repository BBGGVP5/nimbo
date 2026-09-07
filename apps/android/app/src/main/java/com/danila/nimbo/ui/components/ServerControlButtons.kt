package com.danila.nimbo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalNebulaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ServerControlButtons(
    onRefresh: () -> Unit,
    onPingAll: () -> Unit,
    onOpenServers: () -> Unit = {},
    onAdd: () -> Unit = {},
    isPinging: Boolean = false,
    isUpdating: Boolean = false,
    showExtraButtons: Boolean = true
) {
    var clickedBtn by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun triggerAnim(name: String) {
        clickedBtn = name
        scope.launch { delay(250); clickedBtn = null }
    }

    val pingPulseScale = remember { Animatable(1f) }

    suspend fun performPingPulse() {
        repeat(3) {
            pingPulseScale.animateTo(1.3f, tween(600, easing = FastOutSlowInEasing))
            pingPulseScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    val updateRotation = remember { Animatable(0f) }
    LaunchedEffect(isUpdating) {
        if (isUpdating) {
            updateRotation.animateTo(
                targetValue = updateRotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    tween(1000, easing = LinearEasing),
                    RepeatMode.Restart
                )
            )
        } else {
            val current = updateRotation.value % 360f
            if (current > 0f) {
                updateRotation.animateTo(
                    targetValue = updateRotation.value + (360f - current),
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val addRotation by animateFloatAsState(
        targetValue = if (clickedBtn == "add") 360f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "add_rot"
    )
    val dnsOffset by animateDpAsState(
        targetValue = if (clickedBtn == "dns") (-6).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "dns_offset"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MorphButton(
            icon = Icons.Default.Refresh,
            color = LocalNebulaColors.current.accent,
            onClick = { triggerAnim("refresh"); onRefresh() },
            modifier = Modifier.weight(1f),
            iconModifier = Modifier.rotate(updateRotation.value)
        )

        MorphButton(
            icon = Icons.Default.Speed,
            color = LocalNebulaColors.current.accent,
            onClick = {
                triggerAnim("ping")
                scope.launch { performPingPulse() }
                onPingAll()
            },
            modifier = Modifier.weight(1f),
            iconModifier = Modifier.scale(pingPulseScale.value)
        )

        if (showExtraButtons) {
            MorphButton(
                icon = Icons.Default.Dns,
                color = Color(0xFF4CAF50),
                onClick = { triggerAnim("dns"); onOpenServers() },
                modifier = Modifier.weight(1f),
                iconModifier = Modifier.offset(y = dnsOffset)
            )

            MorphButton(
                icon = Icons.Default.Add,
                color = Color(0xFFFF9800),
                onClick = { triggerAnim("add"); onAdd() },
                modifier = Modifier.weight(1f),
                iconModifier = Modifier.rotate(addRotation)
            )
        }
    }
}

@Composable
fun MorphButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.35f else 0.25f,
        animationSpec = tween(150),
        label = "bgAlpha"
    )

    Surface(
        onClick = {
            if (isPressed) return@Surface
            isPressed = true
            onClick()
            scope.launch {
                delay(150)
                isPressed = false
            }
        },
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            color.copy(alpha = backgroundAlpha),
                            color.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    1.dp,
                    color.copy(alpha = 0.4f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp).then(iconModifier)
            )
        }
    }
}

@Composable
fun TopBarMorphIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf<Boolean>(false) }
    val scope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.35f else 0.25f,
        animationSpec = tween(120),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                0.5.dp,
                color.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                pressed = true
                onClick()
                scope.launch {
                    kotlinx.coroutines.delay(120)
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp).then(iconModifier)
        )
    }
}

