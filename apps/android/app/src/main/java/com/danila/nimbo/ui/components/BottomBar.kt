package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.danila.nimbo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBar(navController: NavController) {
    val backStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry.value?.destination?.route
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val reducedTransparencyEnabled = LocalReducedTransparencyEnabled.current
    val backgroundAnimationEnabled = LocalBackgroundAnimationEnabled.current
    val globalBlurRadius = LocalGlobalBlurRadius.current
    val globalCornerRadius = LocalGlobalCornerRadius.current
    val panelShape = when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape((28 * globalCornerRadius).dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape((18 * globalCornerRadius).dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape((14 * globalCornerRadius).dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape((12 * globalCornerRadius).dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape((24 * globalCornerRadius).dp)
    }
    val panelBackground = when (elementStyle) {
        ElementStyleMode.MORPHISM -> listOf(
            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.44f else 0.15f),
            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.34f else 0.06f)
        )

        ElementStyleMode.MATERIAL3 -> listOf(
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 1f else 0.94f),
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 0.98f else 0.82f)
        )

        ElementStyleMode.NOTHING_DOTS -> listOf(
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 1f else 0.92f),
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 0.96f else 0.75f)
        )

        ElementStyleMode.OUTLINED -> listOf(
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 0.94f else 0.55f),
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 0.9f else 0.48f)
        )

        ElementStyleMode.SOFT_NEO -> listOf(
            nebulaColors.onSurface.copy(alpha = if (reducedTransparencyEnabled) 0.28f else 0.12f),
            nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 0.94f else 0.78f)
        )
    }

    // Проверяем есть ли выбранные элементы (с текстом)
    val hasSelectedWithText = currentRoute in listOf("home", "profiles", "settings")

    fun navigateToTopLevel(route: String) {
        if (currentRoute == route) return
        val restoredFromBackStack = navController.popBackStack(route, inclusive = false)
        if (!restoredFromBackStack) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Анимация ширины панели
    val panelWidth by animateFloatAsState(
        targetValue = if (hasSelectedWithText) 0.95f else 0.6f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "panelWidth"
    )

    val animateGradient = backgroundAnimationEnabled &&
        !reducedTransparencyEnabled &&
        elementStyle == ElementStyleMode.MORPHISM
    val gradientOffset = if (animateGradient) {
        val animatedGradient = rememberInfiniteTransition(label = "gradient")
        val offset by animatedGradient.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gradientOffset"
        )
        offset
    } else {
        0f
    }

    // Плавающая панель с эффектом морфизм (iOS style)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Блюрная обводка для эффекта преломления света (фон)
        Box(
            modifier = Modifier
                .fillMaxWidth(panelWidth)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(panelShape)
                .background(nebulaColors.surface.copy(alpha = if (reducedTransparencyEnabled) 0.92f else 0.24f))
                .blur(if (reducedTransparencyEnabled) 0.dp else globalBlurRadius.dp)
                .border(
                    if (reducedTransparencyEnabled) 1.dp else 2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.22f else 0.5f),
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.14f else 0.15f),
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.1f else 0.05f),
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.14f else 0.15f),
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.22f else 0.5f)
                        )
                    ),
                    panelShape
                )
        )

        // Основная панель с кнопками и анимированным фоном
        Row(
            modifier = Modifier
                .fillMaxWidth(panelWidth)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(panelShape)
                .background(
                    Brush.linearGradient(
                        colors = panelBackground,
                        start = Offset(0f, gradientOffset),
                        end = Offset(1000f, gradientOffset + 500f)
                    )
                )
                .then(
                    if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                        Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 13.dp, radius = 0.9.dp, alpha = 0.13f)
                    } else Modifier
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.24f else 0.35f),
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.18f else 0.15f)
                        )
                    ),
                    panelShape
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ){
            BottomNavItem(
                icon = Icons.Default.Home,
                unselectedIcon = Icons.Outlined.Home,
                label = "Главная",
                selected = currentRoute == "home",
                enabled = currentRoute != "home",
                onClick = {
                    navigateToTopLevel("home")
                },
                modifier = Modifier.weight(1f)
            )

            BottomNavItem(
                icon = Icons.Default.Person,
                unselectedIcon = Icons.Outlined.Person,
                label = "Профили",
                selected = currentRoute == "profiles",
                enabled = currentRoute != "profiles",
                onClick = {
                    navigateToTopLevel("profiles")
                },
                modifier = Modifier.weight(1f)
            )

            BottomNavItem(
                icon = Icons.Default.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                label = "Настройки",
                selected = currentRoute == "settings",
                enabled = currentRoute != "settings",
                onClick = {
                    navigateToTopLevel("settings")
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Верхний блик для эффекта стекла
        Box(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            nebulaColors.textPrimary.copy(alpha = if (reducedTransparencyEnabled) 0.03f else 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 100f
                    )
                )
        )
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val reducedTransparencyEnabled = LocalReducedTransparencyEnabled.current
    val globalCornerRadius = LocalGlobalCornerRadius.current
    val isDarkUi = nebulaColors.background.luminance() < 0.5f
    val itemShape = when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape((16 * globalCornerRadius).dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape((12 * globalCornerRadius).dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape((10 * globalCornerRadius).dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape((10 * globalCornerRadius).dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape((14 * globalCornerRadius).dp)
    }
    val iconColor = when {
        selected -> nebulaColors.accent
        reducedTransparencyEnabled -> nebulaColors.textPrimary.copy(alpha = 0.9f)
        else -> nebulaColors.textSecondary
    }

    // Оптимизированная анимация масштаба для иконки
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "iconScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressedScale"
    )

    Box(
        modifier = modifier
            .width(105.dp)  // Увеличили ширину с 100.dp до 105.dp для большего пространства
            .height(52.dp)
            .padding(horizontal = 2.dp)
            .clip(itemShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .then(
                Modifier
            )
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    nebulaColors.accent.copy(alpha = if (reducedTransparencyEnabled) 0.26f else 0.20f),
                                    nebulaColors.accent.copy(alpha = if (reducedTransparencyEnabled) 0.14f else 0.08f)
                                )
                            )
                        )
                        .then(
                            if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                                Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.8.dp, alpha = 0.15f)
                            } else Modifier
                        )
                        .border(
                            if (reducedTransparencyEnabled) 1.dp else 0.5.dp,
                            nebulaColors.accent.copy(alpha = if (reducedTransparencyEnabled) 0.72f else 0.4f),
                            itemShape
                        )
                } else Modifier
            )
            .scale(pressedScale),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),  // Уменьшили отступ для большего пространства тексту
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = if (selected) icon else unselectedIcon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
            )

            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(
                    expandFrom = Alignment.CenterHorizontally,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
                ) + fadeIn(animationSpec = tween(150)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.CenterHorizontally,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(animationSpec = tween(100))
            ) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = iconColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
