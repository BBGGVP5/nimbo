package com.danila.nimbo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.screens.UpdateUiText
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import com.danila.nimbo.ui.theme.ElementStyleMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PostUpdateDialog(
    versionName: String,
    changelog: String,
    installedAt: Long,
    onDismiss: () -> Unit,
    onShowChanges: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val mangaStyle = LocalElementStyleMode.current == ElementStyleMode.MANGA
    val language = LocalConfiguration.current.locales[0].language
    val motionEnabled = LocalBackgroundAnimationEnabled.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    // Reduced transparency lowers visual opacity elsewhere; it should not
    // freeze the completion scene or suppress its one-shot celebration.
    val animationsEnabled = motionEnabled
    val displayVersion = remember(versionName, language) {
        UpdateUiText.versionLabel(versionName, language)
    }
    val installedDate = remember(installedAt, language) {
        installedAt.takeIf { it > 0L }
            ?.let { UpdateUiText.releaseDate(Instant.ofEpochMilli(it).toString(), language) }
    }
    val hasChangelog = remember(changelog) { changelog.isNotBlank() }
    val ringProgress = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val checkProgress = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val contentProgress = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val burstProgress = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            ringProgress.snapTo(1f)
            checkProgress.snapTo(1f)
            contentProgress.snapTo(1f)
            burstProgress.snapTo(1f)
            return@LaunchedEffect
        }
        ringProgress.snapTo(0f)
        checkProgress.snapTo(0f)
        contentProgress.snapTo(0f)
        burstProgress.snapTo(0f)
        coroutineScope {
            launch {
                // Give the first frame enough time to become visible before the
                // one-shot celebration starts. On fast devices the old burst
                // could finish while the dialog window was still attaching.
                delay(650)
                burstProgress.animateTo(1f, tween(2_800, easing = LinearEasing))
            }
            launch {
                ringProgress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                checkProgress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
            }
            launch {
                delay(260)
                contentProgress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
    }

    // Монотонное время вместо infiniteRepeatable: у волн и искр свои скорости,
    // и на перезапуске 1 -> 0 они прыгали бы посреди экрана. Значение читается
    // в фазе отрисовки, поэтому кадры не вызывают рекомпозицию.
    val sceneSecondsState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            sceneSecondsState.floatValue = 0f
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                // Секунды с начала сцены: волны и искры считают своё время сами,
                // одного общего множителя им не хватало.
                sceneSecondsState.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val seconds = sceneSecondsState.floatValue
                val heart = Offset(size.width * 0.5f, size.height * 0.46f)
                val reach = size.minDimension
                val glassAlpha = if (reducedTransparency) 0.55f else 1f

                // Подложка: тёплая к центру, спокойная по краям.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            colors.background,
                            colors.accent.copy(alpha = 0.07f * glassAlpha),
                            colors.background
                        )
                    )
                )

                // Свечение дышит: без этого сцена выглядела нарисованной раз и
                // навсегда. В Manga его нет — там бумага, а не подсветка.
                if (!mangaStyle) {
                    val breath = 0.5f + 0.5f * sin(seconds * 0.9f).toFloat()
                    val glow = reach * (0.52f + breath * 0.06f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.20f * glassAlpha),
                                colors.accent.copy(alpha = 0.05f * glassAlpha),
                                Color.Transparent
                            ),
                            center = heart,
                            radius = glow
                        ),
                        radius = glow,
                        center = heart
                    )
                }

                // Волны от значка: установка закончилась — сигнал разошёлся.
                val waveColor = if (mangaStyle) colors.panelBorder else colors.accent
                repeat(3) { index ->
                    val wave = ((seconds * 0.34f) + index * 0.333f) % 1f
                    val fade = (1f - wave) * (1f - wave)
                    drawCircle(
                        color = waveColor.copy(alpha = 0.26f * fade * glassAlpha),
                        radius = reach * (0.18f + wave * 0.52f),
                        center = heart,
                        // Чернильный контур ровный по всей длине, световая волна
                        // истончается к краю — это разные вещи.
                        style = Stroke(
                            width = if (mangaStyle) 1.6f.dp.toPx() else (2.2f - wave * 1.1f).dp.toPx()
                        )
                    )
                }

                // Искры поднимаются медленно и вразнобой: положение выведено из
                // номера искры, поэтому между кадрами набор не пляшет.
                val sparkPalette = if (mangaStyle) {
                    listOf(colors.panelBorder, colors.accent)
                } else {
                    listOf(colors.accent, Color(0xFF79D7FF), Color(0xFFFFD166), Color.White)
                }
                repeat(38) { index ->
                    val column = (index * 0.6180339887f) % 1f
                    val speed = 0.05f + ((index * 7 % 11) / 11f) * 0.055f
                    val rise = ((seconds * speed) + (index % 13) / 13f) % 1f
                    val sway = sin(seconds * 0.6f + index).toFloat() * size.width * 0.015f
                    // Гаснут у краёв пути, ярче всего посередине.
                    val alpha = sin(PI * rise).toFloat().coerceIn(0f, 1f) * 0.42f * glassAlpha
                    if (alpha <= 0.01f) return@repeat
                    val sparkColor = sparkPalette[index % sparkPalette.size].copy(alpha = alpha)
                    val sparkRadius = (1.1f + (index % 4) * 0.5f).dp.toPx()
                    val sparkCenter = Offset(
                        column * size.width + sway,
                        size.height * (1.04f - rise * 1.12f)
                    )
                    if (mangaStyle) {
                        // Крапины, а не огоньки: круглая искра на бумаге читается
                        // как блик, которого в чернилах не бывает.
                        drawRect(
                            color = sparkColor,
                            topLeft = Offset(sparkCenter.x - sparkRadius, sparkCenter.y - sparkRadius),
                            size = Size(sparkRadius * 2f, sparkRadius * 2f)
                        )
                    } else {
                        drawCircle(color = sparkColor, radius = sparkRadius, center = sparkCenter)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent.copy(alpha = 0.11f))
                            .border(1.dp, colors.accent.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = t("ОБНОВЛЕНИЕ ЗАВЕРШЕНО", "UPDATE COMPLETE"),
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = contentProgress.value
                            translationY = (1f - contentProgress.value) * 26.dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(138.dp)
                            .graphicsLayer {
                                // Знак встаёт с лёгким перелётом: сухая посадка
                                // читалась как подгрузившаяся картинка.
                                val settle = sin(PI * checkProgress.value).toFloat()
                                val scale = 0.90f + ringProgress.value * 0.10f + settle * 0.05f
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        val heart = center
                        val discRadius = size.minDimension * 0.34f

                        // Свечение вокруг знака — тот же приём, что и на фоне:
                        // иначе знак выглядел наклейкой поверх сцены. В Manga
                        // ореола нет, его роль играет толстый контур.
                        if (!mangaStyle) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = 0.34f),
                                        Color.Transparent
                                    ),
                                    center = heart,
                                    radius = discRadius * 1.9f
                                ),
                                radius = discRadius * 1.9f,
                                center = heart
                            )
                        }

                        // Кольцо замыкается по ходу установки.
                        drawArc(
                            color = colors.accent.copy(alpha = 0.9f),
                            startAngle = -90f,
                            sweepAngle = 360f * ringProgress.value,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset(
                                heart.x - discRadius * 1.42f,
                                heart.y - discRadius * 1.42f
                            ),
                            size = Size(discRadius * 2.84f, discRadius * 2.84f)
                        )

                        // Заполненный круг вместо пустого контура: галочка должна
                        // читаться знаком, а не чертежом.
                        drawCircle(
                            color = colors.accent,
                            radius = discRadius * ringProgress.value,
                            center = heart
                        )
                        if (mangaStyle) {
                            drawCircle(
                                color = colors.panelBorder,
                                radius = discRadius * ringProgress.value,
                                center = heart,
                                style = Stroke(width = 2.5f.dp.toPx())
                            )
                        } else {
                            // Блик сверху: плоская заливка выглядела наклейкой.
                            drawCircle(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.Transparent
                                    ),
                                    startY = heart.y - discRadius,
                                    endY = heart.y + discRadius * 0.2f
                                ),
                                radius = discRadius * ringProgress.value,
                                center = heart
                            )
                        }

                        if (checkProgress.value > 0f) {
                            // Единый росчерк: два отрезка стыковались углом и на
                            // толстой линии давали заметный залом.
                            val path = Path().apply {
                                moveTo(size.width * 0.36f, size.height * 0.50f)
                                lineTo(size.width * 0.46f, size.height * 0.60f)
                                lineTo(size.width * 0.66f, size.height * 0.40f)
                            }
                            val measure = PathMeasure().apply { setPath(path, false) }
                            val drawn = Path()
                            measure.getSegment(0f, measure.length * checkProgress.value, drawn, true)
                            drawPath(
                                path = drawn,
                                // Контраст к акценту, а не к теме: на светлом
                                // акценте белая галочка пропадала.
                                color = if (colors.accent.luminance() > 0.6f) {
                                    Color(0xFF10131A)
                                } else {
                                    Color.White
                                },
                                style = Stroke(
                                    width = 8.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = t("Nimbo обновлён", "Nimbo is updated"),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append(displayVersion)
                            installedDate?.let { append("  ·  ").append(it) }
                        },
                        color = colors.accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentProgress.value },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasChangelog) {
                        Button(
                            onClick = onShowChanges,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                        ) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(t("Посмотреть изменения", "View changes"), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(9.dp))
                            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("Продолжить работу", "Continue"), color = colors.textSecondary)
                    }
                }
            }
        }
    }
}
