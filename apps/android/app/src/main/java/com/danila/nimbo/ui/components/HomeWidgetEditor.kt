package com.danila.nimbo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.danila.nimbo.model.HomeWidget
import com.danila.nimbo.model.WidgetConfig
import com.danila.nimbo.model.WidgetType
import com.danila.nimbo.ui.theme.*
import com.danila.nimbo.ui.viewmodel.HomeWidgetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)

/**
 * Верхняя панель редактирования с кнопками Сохранить/Отмена
 */
@Composable
fun EditModeTopBar(
    hasChanges: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAddWidget: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state = topAppBarState)

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentCyan.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "Редактирование",
                    style = MaterialTheme.typography.titleMedium,
                    color = nebulaColors.textPrimary,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Отмена",
                    tint = nebulaColors.textSecondary
                )
            }
        },
        actions = {
            // Кнопка добавления виджета
            IconButton(onClick = onAddWidget) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentPurple.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить виджет",
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Кнопка сохранения
            AnimatedVisibility(
                visible = hasChanges,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Surface(
                    onClick = onSave,
                    shape = RoundedCornerShape(10.dp),
                    color = StatusConnected.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StatusConnected,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Сохранить",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatusConnected
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = when (elementStyle) {
                ElementStyleMode.MATERIAL3 -> nebulaColors.surface.copy(alpha = 0.86f)
                ElementStyleMode.OUTLINED -> Color.Transparent
                else -> Color.Transparent
            },
            scrolledContainerColor = when (elementStyle) {
                ElementStyleMode.MATERIAL3 -> nebulaColors.surface.copy(alpha = 0.9f)
                ElementStyleMode.OUTLINED -> Color.Transparent
                else -> Color.Transparent
            }
        ),
        scrollBehavior = scrollBehavior
    )
}

/**
 * Карточка виджета в режиме редактирования
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditableWidgetCard(
    widget: HomeWidget,
    config: WidgetConfig,
    isEditMode: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: (() -> Unit)? = null,
    onDragStop: (() -> Unit)? = null,
    onDrag: (Offset) -> Unit = {}
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    var isPressed by remember { mutableStateOf(false) }

    // Анимация нажатия
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scale"
    )

    // Анимация границы для активного виджета
    val borderColor by animateColorAsState(
        targetValue = if (isEditMode) nebulaColors.textPrimary.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(200),
        label = "borderColor"
    )

    val dragModifier = if (isEditMode && !config.isSystem) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isPressed = true
                    onDragStart?.invoke()
                },
                onDragEnd = {
                    isPressed = false
                    onDragStop?.invoke()
                },
                onDragCancel = {
                    isPressed = false
                    onDragStop?.invoke()
                },
                onDrag = { change: PointerInputChange, dragAmount: Offset ->
                    change.consume()
                    onDrag(dragAmount)
                }
            )
        }
    } else {
        Modifier
    }

    val cardShape = if (widget.type == WidgetType.VPN_BUTTON) CircleShape else when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape(20.dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape(14.dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(10.dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape(8.dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape(18.dp)
    }
    val cardBackground = if (widget.type == WidgetType.VPN_BUTTON) Color.Transparent else Color.Unspecified

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                if (widget.type != WidgetType.VPN_BUTTON) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                nebulaColors.textPrimary.copy(alpha = 0.08f),
                                nebulaColors.textPrimary.copy(alpha = 0.02f)
                            )
                        )
                    )
                } else Modifier
            )
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS && widget.type != WidgetType.VPN_BUTTON) {
                    Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.8.dp, alpha = 0.11f)
                } else Modifier
            )
            .border(
                if (isEditMode) 2.dp else 1.dp,
                if (elementStyle == ElementStyleMode.OUTLINED) nebulaColors.onSurface.copy(alpha = 0.28f) else borderColor,
                cardShape
            )
            .then(dragModifier)
            .scale(scale)
    ) {
        // Glow эффект
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            nebulaColors.textPrimary.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        radius = 500f
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка виджета
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    nebulaColors.textPrimary.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = config.icon,
                        contentDescription = null,
                        tint = nebulaColors.textPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Информация о виджете
                Column {
                    Text(
                        text = config.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = nebulaColors.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = config.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = nebulaColors.textTertiary,
                        maxLines = 2
                    )
                }
            }

            // Кнопка удаления/добавления
            if (isEditMode) {
                Surface(
                    onClick = {
                        if (canRemove) {
                            onRemove()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (canRemove)
                        StatusDisconnected.copy(alpha = 0.15f)
                    else
                        Color.Transparent,
                    enabled = canRemove
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (canRemove) Icons.Default.Close else Icons.Default.Block,
                            contentDescription = if (canRemove) "Удалить" else "Нельзя удалить",
                            tint = if (canRemove) StatusDisconnected else nebulaColors.textTertiary.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Список виджетов в режиме редактирования
 */
@Composable
fun EditableWidgetsList(
    widgets: List<HomeWidget>,
    isEditMode: Boolean,
    onWidgetHide: (String) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    getWidgetConfig: (WidgetType) -> WidgetConfig,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var lastDragY by remember { mutableStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = widgets,
            key = { it.id }
        ) { widget ->
            val config = getWidgetConfig(widget.type)
            val currentIndex = widgets.indexOf(widget)

            EditableWidgetCard(
                widget = widget,
                config = config,
                isEditMode = isEditMode,
                canRemove = !config.isSystem,
                onRemove = { onWidgetHide(widget.id) },
                onDragStart = {
                    draggedIndex = currentIndex
                    lastDragY = 0f
                },
                onDragStop = {
                    draggedIndex = null
                    lastDragY = 0f
                },
                onDrag = { dragAmount ->
                    val deltaY = dragAmount.y - lastDragY
                    lastDragY = dragAmount.y

                    // Порог для перемещения (100 пикселей)
                    if (Math.abs(deltaY) > 100) {
                        val direction = if (deltaY > 0) 1 else -1
                        val newIndex = (currentIndex + direction).coerceIn(0, widgets.size - 1)

                        if (newIndex != currentIndex) {
                            onMoveWidget(currentIndex, newIndex)
                            lastDragY = 0f
                        }
                    }
                }
            )
        }
    }
}

/**
 * Панель доступных виджетов для добавления
 */
@Composable
fun AvailableWidgetsPanel(
    activeWidgets: List<HomeWidget>,  // Добавлен параметр активных виджетов
    hiddenWidgets: List<HomeWidget>,
    allWidgetConfigs: List<WidgetConfig>,
    onAddWidget: (WidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    // Показываем все неактивные несистемные виджеты
    val availableConfigs = allWidgetConfigs.filter { config ->
        !config.isSystem && activeWidgets.none { w -> w.type == config.type }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter),
            shape = when (elementStyle) {
                ElementStyleMode.MATERIAL3 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ElementStyleMode.OUTLINED -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                else -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            },
            color = nebulaColors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Добавить виджет",
                        style = MaterialTheme.typography.titleLarge,
                        color = nebulaColors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = nebulaColors.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Список доступных виджетов
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = availableConfigs,
                        key = { it.type.name }
                    ) { config ->
                        AvailableWidgetItem(
                            config = config,
                            onClick = {
                                onAddWidget(config.type)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableWidgetItem(
    config: WidgetConfig,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    Surface(
        onClick = onClick,
        shape = when (elementStyle) {
            ElementStyleMode.MORPHISM -> RoundedCornerShape(14.dp)
            ElementStyleMode.MATERIAL3 -> RoundedCornerShape(12.dp)
            ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(10.dp)
            ElementStyleMode.OUTLINED -> RoundedCornerShape(8.dp)
            ElementStyleMode.SOFT_NEO -> RoundedCornerShape(16.dp)
        },
        color = if (elementStyle == ElementStyleMode.OUTLINED) Color.Transparent else nebulaColors.cardBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (elementStyle) {
                ElementStyleMode.OUTLINED -> nebulaColors.onSurface.copy(alpha = 0.28f)
                ElementStyleMode.NOTHING_DOTS -> nebulaColors.accent.copy(alpha = 0.2f)
                else -> nebulaColors.onSurface.copy(alpha = 0.12f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                        Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.8.dp, alpha = 0.11f)
                    } else Modifier
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentPurple.copy(alpha = 0.2f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = config.icon,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = config.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = nebulaColors.textPrimary
                    )
                    Text(
                        text = config.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = nebulaColors.textTertiary
                    )
                }
            }

            // Кнопка добавления (+)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentCyan.copy(alpha = 0.15f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
