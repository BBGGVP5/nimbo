package com.danila.nimbo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val iconColor: Color? = null,
    val glowColor: Color
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Default.Shield,
        title = "Безопасность",
        description = "NebulaGuard защищает ваш трафик с помощью современных протоколов шифрования. Все данные надёжно зашифрованы.",
        glowColor = AccentCyan
    ),
    OnboardingPage(
        icon = Icons.Default.Bolt,
        title = "Скорость",
        description = "Высокоскоростные серверы по всему миру обеспечивают минимальные задержки и максимальную скорость соединения.",
        glowColor = AccentPurple
    ),
    OnboardingPage(
        icon = Icons.Default.Dns,
        title = "Профили",
        description = "Добавьте подписку и выбирайте серверы из списка. Пинг каждого сервера отображается для удобного выбора.",
        glowColor = AccentPink
    ),
    OnboardingPage(
        icon = Icons.Default.Settings,
        title = "Настройки",
        description = "Гибкая настройка приложения под ваши нужды. Kill Switch, автоподключение и другие функции.",
        glowColor = AccentPurple
    ),
    OnboardingPage(
        icon = Icons.Default.Person,
        title = "Разработчики",
        description = "Сделано с любовью командой NebulaGuard. Поддержите проект и следите за обновлениями.",
        glowColor = AccentCyan
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(nebulaColors.background)
    ) {
        // Фоновые градиенты
        AnimatedGradientBackground()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            nebulaColors.accent.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // Индикатор прогресса
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(nebulaColors.textPrimary.copy(alpha = 0.08f))
                    .border(1.dp, nebulaColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(onboardingPages.size) { index ->
                    val isSelected = pagerState.currentPage == index

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(
                                if (isSelected) {
                                    RoundedCornerShape(6.dp)
                                } else {
                                    CircleShape
                                }
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                nebulaColors.accent.copy(alpha = 0.95f),
                                                nebulaColors.accent.copy(alpha = 0.65f)
                                            )
                                        )
                                    )
                                } else {
                                    Modifier.background(nebulaColors.textTertiary.copy(alpha = 0.3f))
                                }
                            )
                            .border(
                                if (isSelected) 1.dp else 0.dp,
                                if (isSelected) nebulaColors.accent.copy(alpha = 0.45f) else Color.Transparent,
                                if (isSelected) RoundedCornerShape(6.dp) else CircleShape
                            )
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .width(24.dp)
                                        .height(8.dp)
                                } else {
                                    Modifier
                                        .size(8.dp)
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Контент страниц с анимацией
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                OnboardingPageContent(
                    page = onboardingPages[page],
                    pageOffset = pageOffset
                )
            }

            Spacer(Modifier.height(32.dp))

            // Кнопки навигации
            if (pagerState.currentPage < onboardingPages.size - 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onComplete() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = nebulaColors.textSecondary),
                        border = BorderStroke(1.dp, nebulaColors.textPrimary.copy(alpha = 0.18f)),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Пропустить")
                    }

                    val nextInteractionSource = remember { MutableInteractionSource() }
                    val nextIsPressed by nextInteractionSource.collectIsPressedAsState()
                    val nextScale by animateFloatAsState(
                        targetValue = if (nextIsPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                        label = "nextScale"
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        interactionSource = nextInteractionSource,
                        modifier = Modifier
                            .shadow(10.dp, RoundedCornerShape(14.dp))
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        nebulaColors.accent.copy(alpha = 0.30f),
                                        nebulaColors.accent.copy(alpha = 0.16f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                nebulaColors.accent.copy(alpha = 0.36f),
                                RoundedCornerShape(14.dp)
                            )
                            .graphicsLayer {
                                scaleX = nextScale
                                scaleY = nextScale
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text(
                            text = "Далее",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Button(
                        onClick = {
                            uriHandler.openUri("https://t.me/nebulaguardd_bot")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        nebulaColors.accent.copy(alpha = 0.30f),
                                        nebulaColors.accent.copy(alpha = 0.16f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                nebulaColors.accent.copy(alpha = 0.36f),
                                RoundedCornerShape(14.dp)
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("VPN Бот")
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            uriHandler.openUri("https://t.me/nebulaguard_channel")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(6.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        nebulaColors.accent.copy(alpha = 0.26f),
                                        nebulaColors.accent.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                nebulaColors.accent.copy(alpha = 0.34f),
                                RoundedCornerShape(14.dp)
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = nebulaColors.accent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Канал Telegram")
                    }

                    Spacer(Modifier.height(20.dp))

                    val startInteractionSource = remember { MutableInteractionSource() }
                    val startIsPressed by startInteractionSource.collectIsPressedAsState()
                    val startScale by animateFloatAsState(
                        targetValue = if (startIsPressed) 0.96f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                        label = "startScale"
                    )

                    Button(
                        onClick = { onComplete() },
                        interactionSource = startInteractionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(10.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        nebulaColors.accent.copy(alpha = 0.30f),
                                        nebulaColors.accent.copy(alpha = 0.16f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                nebulaColors.accent.copy(alpha = 0.36f),
                                RoundedCornerShape(14.dp)
                            )
                            .graphicsLayer {
                                scaleX = startScale
                                scaleY = startScale
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text(
                            text = "Начать использование",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    pageOffset: Float
) {
    val nebulaColors = LocalNebulaColors.current
    val floatTransition = rememberInfiniteTransition(label = "onboarding_float")
    val absoluteOffset = kotlin.math.abs(pageOffset)
    
    // Плавное затухание по аналогии с Google
    val alpha = 1f - absoluteOffset.coerceIn(0f, 1f) * 0.6f
    
    // Плавное масштабирование
    val scale = 1f - absoluteOffset.coerceIn(0f, 1f) * 0.15f
    
    // Параллакс эффект для иконки
    val parallaxX = pageOffset * 60f
    val floatY by floatTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconFloatY"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 24.dp)
            .graphicsLayer {
                this.alpha = alpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            nebulaColors.textPrimary.copy(alpha = 0.09f),
                            nebulaColors.textPrimary.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Иконка в градиентном круге + Параллакс
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = parallaxX
                    translationY = floatY
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(page.glowColor.copy(alpha = 0.3f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(nebulaColors.textPrimary.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = page.iconColor ?: nebulaColors.accent,
                    modifier = Modifier.size(76.dp)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Заголовок
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                lineBreak = LineBreak.Heading
            ),
            color = nebulaColors.textPrimary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                translationX = pageOffset * 20f
            }
        )

        Spacer(Modifier.height(20.dp))

        // Описание
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineBreak = LineBreak.Paragraph,
                hyphens = Hyphens.Auto
            ),
            color = nebulaColors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
            modifier = Modifier.graphicsLayer {
                translationX = pageOffset * 40f
            }
        )
            }
        }
    }
}

