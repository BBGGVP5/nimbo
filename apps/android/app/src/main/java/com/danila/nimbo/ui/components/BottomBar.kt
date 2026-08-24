package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalGlobalCornerRadius
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled

private data class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

@Composable
fun BottomBar(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val colors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    val cornerScale = LocalGlobalCornerRadius.current
    val destinations = listOf(
        BottomDestination("home", t("Главная", "Home"), Icons.Filled.Home, Icons.Outlined.Home),
        BottomDestination("profiles", t("Профили", "Profiles"), Icons.Filled.Person, Icons.Outlined.Person),
        BottomDestination("settings", t("Настройки", "Settings"), Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    val panelCorner = when (elementStyle) {
        ElementStyleMode.LIQUID_GLASS -> (34 * cornerScale).dp
        ElementStyleMode.MATERIAL_EXPRESSIVE -> (32 * cornerScale).dp
        ElementStyleMode.NOTHING_DOTS -> (10 * cornerScale).dp
        ElementStyleMode.OUTLINED -> (16 * cornerScale).dp
        ElementStyleMode.SOFT_NEO -> (28 * cornerScale).dp
        ElementStyleMode.SIGNAL -> (18 * cornerScale).dp
    }
    val panelShape = RoundedCornerShape(panelCorner)
    fun navigate(route: String) {
        if (currentRoute == route) return
        if (!navController.popBackStack(route, inclusive = false)) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val basePanelModifier = Modifier
            .fillMaxWidth()
            .height(72.dp)

        val styledPanelModifier = when (elementStyle) {
            ElementStyleMode.LIQUID_GLASS -> basePanelModifier
                .liquidGlassSurface(panelShape, LiquidGlassDepth.FLOATING)

            ElementStyleMode.MATERIAL_EXPRESSIVE -> basePanelModifier
                .shadow(
                    elevation = 8.dp,
                    shape = panelShape,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                    panelShape
                )

            ElementStyleMode.NOTHING_DOTS -> basePanelModifier
                .clip(panelShape)
                .background(colors.surface.copy(alpha = if (reducedTransparency) 0.99f else 0.96f))
                .dotPatternOverlay(
                    color = colors.textPrimary,
                    spacing = 9.dp,
                    radius = 0.72.dp,
                    alpha = 0.10f
                )
                .dottedOutline(
                    color = colors.accent,
                    cornerRadius = panelCorner,
                    alpha = 0.78f
                )

            // Signal: ровная подложка и волосяная граница вместо градиента.
            ElementStyleMode.SIGNAL -> basePanelModifier
                .clip(panelShape)
                .background(colors.surface.copy(alpha = if (reducedTransparency) 0.98f else 0.94f))
                .border(1.dp, colors.textPrimary.copy(alpha = 0.09f), panelShape)

            else -> basePanelModifier
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surface.copy(alpha = if (reducedTransparency) 0.98f else 0.90f),
                            colors.surface.copy(alpha = if (reducedTransparency) 0.94f else 0.76f)
                        )
                    )
                )
                .border(1.dp, colors.panelBorder, panelShape)
        }

        Row(
            modifier = styledPanelModifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                BottomNavItem(
                    icon = destination.selectedIcon,
                    unselectedIcon = destination.icon,
                    label = destination.label,
                    selected = destination.route == currentRoute,
                    onClick = { navigate(destination.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navigationPressedScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navigationIconScale"
    )
    val itemCorner = when (elementStyle) {
        ElementStyleMode.MATERIAL_EXPRESSIVE -> 26.dp
        ElementStyleMode.NOTHING_DOTS -> 6.dp
        // Signal: не капсула, а прямоугольная плашка со скруглением панели.
        ElementStyleMode.SIGNAL -> 12.dp
        else -> 24.dp
    }
    val itemShape = RoundedCornerShape(itemCorner)
    val itemBackground = when {
        !selected -> Color.Transparent
        elementStyle == ElementStyleMode.MATERIAL_EXPRESSIVE -> MaterialTheme.colorScheme.primaryContainer
        elementStyle == ElementStyleMode.LIQUID_GLASS -> colors.accent.copy(
            alpha = if (reducedTransparency) 0.26f else 0.16f
        )
        elementStyle == ElementStyleMode.SIGNAL -> colors.accent.copy(alpha = 0.13f)
        else -> colors.accent.copy(alpha = 0.16f)
    }
    val itemBorder = when {
        !selected || elementStyle == ElementStyleMode.MATERIAL_EXPRESSIVE -> Color.Transparent
        elementStyle == ElementStyleMode.LIQUID_GLASS -> Color.White.copy(
            alpha = if (reducedTransparency) 0.18f else 0.30f
        )
        elementStyle == ElementStyleMode.NOTHING_DOTS -> Color.Transparent
        elementStyle == ElementStyleMode.SIGNAL -> colors.accent.copy(alpha = 0.32f)
        else -> colors.accent.copy(alpha = 0.28f)
    }
    val contentColor = when {
        selected && elementStyle == ElementStyleMode.MATERIAL_EXPRESSIVE ->
            MaterialTheme.colorScheme.onPrimaryContainer
        selected -> colors.accent
        elementStyle == ElementStyleMode.MATERIAL_EXPRESSIVE ->
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> colors.textSecondary
    }

    Box(
        modifier = modifier
            .height(58.dp)
            .scale(pressedScale)
            .clip(itemShape)
            .background(itemBackground)
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS && selected) {
                    Modifier.dottedOutline(
                        color = colors.accent,
                        cornerRadius = itemCorner,
                        alpha = 0.92f
                    )
                } else {
                    Modifier.border(1.dp, itemBorder, itemShape)
                }
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                enabled = !selected,
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) icon else unselectedIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + fadeIn(tween(180)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut(tween(100))
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
