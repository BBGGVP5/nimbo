package com.danila.nimbo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.model.UpdateInfo
import com.danila.nimbo.ui.screens.MarkdownChangelog
import com.danila.nimbo.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate,
            dismissOnClickOutside = !updateInfo.forceUpdate,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Фон-затемнение с анимацией
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isVisible) 0.6f else 0f }
                    .background(Color.Black)
            )

            // Основное окно диалога
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + scaleIn(spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f)),
                exit = fadeOut(tween(300)) + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f) // Сделали шире
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(32.dp))
                        .background(nebulaColors.textPrimary.copy(alpha = 0.03f)) // Полупрозрачная база
                        .border(
                            BorderStroke(1.dp, Brush.linearGradient(
                                listOf(nebulaColors.textPrimary.copy(alpha = 0.2f), nebulaColors.textPrimary.copy(alpha = 0.05f))
                            )),
                            RoundedCornerShape(32.dp)
                        )
                ) {
                    // Эффект стекла (размытие фона)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .blur(30.dp) // Настоящий Glassmorphism
                            .background(
                                Brush.verticalGradient(
                                    listOf(nebulaColors.surface.copy(alpha = 0.8f), nebulaColors.surface.copy(alpha = 0.9f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Тематическая анимация "iOS Face ID" стиля
                        var isCheckmark by remember { mutableStateOf(false) }
                        val arrowOffset = remember { Animatable(-40f) }
                        val iconAlpha = remember { Animatable(0f) }
                        val iconScale = remember { Animatable(1f) }

                        LaunchedEffect(Unit) {
                            while (true) {
                                // 1. Сброс и появление
                                isCheckmark = false
                                iconScale.snapTo(1f)
                                arrowOffset.snapTo(-50f)
                                launch { iconAlpha.animateTo(1f, tween(200)) }

                                // 2. Быстрое "падение" точно в центр
                                arrowOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.6f))

                                // 3. Моментальное превращение в галочку + "Бэмс"
                                isCheckmark = true
                                launch { iconScale.animateTo(1.3f, tween(100)) }
                                delay(100)
                                iconScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy))

                                // 4. Пауза и постепенное исчезновение
                                delay(3000)
                                iconAlpha.animateTo(0f, tween(400))
                                isCheckmark = false
                                delay(300)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(nebulaColors.accent.copy(alpha = 0.08f))
                                .border(1.dp, nebulaColors.accent.copy(alpha = 0.2f), RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Полоска исчезает при появлении галочки
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isCheckmark,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(nebulaColors.accent.copy(alpha = 0.4f))
                                )
                            }

                            // Анимированный контент (Стрелка -> Галочка)
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                         alpha = iconAlpha.value
                                         scaleX = iconScale.value
                                         scaleY = iconScale.value
                                         translationY = if (isCheckmark) 0f else arrowOffset.value * 2f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = isCheckmark,
                                    transitionSpec = {
                                        (fadeIn(tween(400)) + scaleIn(initialScale = 0.6f)) togetherWith
                                        (fadeOut(tween(400)) + scaleOut(targetScale = 0.6f))
                                    },
                                    label = "iconTransform"
                                ) { checked ->
                                    val rotation by animateFloatAsState(
                                        targetValue = if (checked) 360f else 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.6f),
                                        label = "iconRotation"
                                    )

                                    Icon(
                                        imageVector = if (checked) Icons.Default.Check else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = nebulaColors.accent,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .graphicsLayer { rotationZ = rotation }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        Text(
                            "Доступна новая версия ✨",
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        val displayVersion = "v" + updateInfo.versionName.replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "").trim()

                        Text(
                            "Nimbo $displayVersion",
                            color = nebulaColors.accent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(Modifier.height(20.dp))

                        // Ченджлог с прокруткой
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(nebulaColors.textPrimary.copy(alpha = 0.04f))
                                .border(0.5.dp, nebulaColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = nebulaColors.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "ЧТО НОВОГО:",
                                    color = nebulaColors.textTertiary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))

                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.verticalScroll(scrollState)
                            ) {
                                MarkdownChangelog(
                                    content = updateInfo.changelog
                                        ?: "Улучшения производительности и исправление ошибок.",
                                    color = nebulaColors.textSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        // Кнопки
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!updateInfo.forceUpdate) {
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = nebulaColors.textPrimary.copy(alpha = 0.04f),
                                        contentColor = nebulaColors.textSecondary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("Позже", fontWeight = FontWeight.Medium)
                                }
                            }

                            Button(
                                onClick = onUpdate,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(54.dp)
                                    .border(0.5.dp, nebulaColors.accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = nebulaColors.accent.copy(alpha = 0.3f),
                                    contentColor = Color.White // Keep white here as it's on accent
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Text("ОБНОВИТЬ", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
