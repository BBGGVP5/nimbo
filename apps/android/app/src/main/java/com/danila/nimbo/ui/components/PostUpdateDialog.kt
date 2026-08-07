package com.danila.nimbo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun PostUpdateDialog(
    versionName: String,
    changelog: String,
    installedAt: Long,
    onDismiss: () -> Unit,
    onShowChanges: () -> Unit
) {
    val colors = LocalNebulaColors.current
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
    // Капсулы медленно ползут через весь экран слева направо и целиком уходят
    // за правый край. Скорости держим близкими: при большом разбросе поток
    // читался как хаотичное мельтешение, а не спокойное движение.
    val ribbonSegments = remember {
        listOf(
            UpdateRibbonSegment(0.07f, 0.34f, -14f, 10.5f, 0.00f, 1.00f, 0.17f),
            UpdateRibbonSegment(0.18f, 0.28f, -12f, 8.0f, 0.30f, 1.08f, 0.13f),
            UpdateRibbonSegment(0.29f, 0.52f, -13f, 12.5f, 0.62f, 0.92f, 0.19f),
            UpdateRibbonSegment(0.40f, 0.25f, -11f, 8.5f, 0.16f, 1.04f, 0.12f),
            UpdateRibbonSegment(0.52f, 0.46f, -14f, 11.5f, 0.78f, 0.96f, 0.18f),
            UpdateRibbonSegment(0.64f, 0.39f, -12f, 10.5f, 0.44f, 1.02f, 0.15f),
            UpdateRibbonSegment(0.76f, 0.49f, -15f, 12.0f, 0.08f, 0.90f, 0.17f),
            UpdateRibbonSegment(0.88f, 0.30f, -12f, 8.5f, 0.54f, 1.06f, 0.13f)
        )
    }

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

    // Монотонное время вместо infiniteRepeatable: у капсул разные скорости, и на
    // перезапуске 1 -> 0 они прыгали бы посреди экрана. Значение читается в фазе
    // отрисовки, поэтому кадры не вызывают рекомпозицию.
    val linePhaseState = remember { mutableFloatStateOf(0.32f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            linePhaseState.floatValue = 0.32f
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                // 38 секунд на один проезд: должно именно ползти, а не пролетать.
                linePhaseState.floatValue = (now - startNanos) / 1_000_000_000f / 38f
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
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            colors.background,
                            colors.accent.copy(alpha = if (reducedTransparency) 0.05f else 0.12f),
                            colors.background
                        )
                    )
                )

                // Каждая капсула едет по своей строке от левого края к правому и
                // возвращается, полностью уйдя за границу, — стык проезда не виден.
                val linePhase = linePhaseState.floatValue
                ribbonSegments.forEachIndexed { index, segment ->
                    val angle = segment.angleDegrees / 180f * PI
                    val slope = tan(angle).toFloat()
                    val length = size.width * segment.lengthFraction
                    val travel = wrapUnit(linePhase * segment.speed + segment.phaseOffset)
                    // Стартуем целиком слева за экраном и уезжаем целиком вправо.
                    val startX = -length + travel * (size.width + length * 2f)
                    // Ключевое: Y смещается по тому же наклону, что и сама капсула,
                    // поэтому она ползёт вдоль собственной оси — наискосок. Раньше
                    // наклонная капсула ехала строго вбок и выглядела летящей.
                    val startY = size.height * segment.rowY + (startX - size.width * 0.5f) * slope
                    val start = androidx.compose.ui.geometry.Offset(startX, startY)
                    val end = androidx.compose.ui.geometry.Offset(
                        startX + cos(angle).toFloat() * length,
                        startY + sin(angle).toFloat() * length
                    )
                    val stroke = segment.strokeDp.dp.toPx()
                    val segmentColor = when (index % 4) {
                        0 -> colors.accent
                        1 -> Color(0xFF79D7FF)
                        2 -> colors.accent.copy(red = (colors.accent.red + 0.10f).coerceAtMost(1f))
                        else -> Color(0xFFA78BFA)
                    }

                    drawLine(
                        color = segmentColor.copy(alpha = segment.alpha * 0.20f),
                        start = start,
                        end = end,
                        strokeWidth = stroke * 2.6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                segmentColor.copy(alpha = segment.alpha * 0.18f),
                                segmentColor.copy(alpha = segment.alpha),
                                Color(0xFF9BE7FF).copy(alpha = segment.alpha * 0.82f)
                            ),
                            start = start,
                            end = end
                        ),
                        start = start,
                        end = end,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )

                    // Блик идёт по капсуле лишь немного быстрее её самой:
                    // на тройной скорости он мельтешил и сбивал спокойный ход.
                    val highlightProgress = wrapUnit(linePhase * segment.speed * 1.35f + segment.phaseOffset)
                    val highlightFade = sin(PI * highlightProgress).toFloat().coerceIn(0f, 1f)
                    val highlightHalf = 0.11f
                    val highlightStart = (highlightProgress - highlightHalf).coerceIn(0f, 1f)
                    val highlightEnd = (highlightProgress + highlightHalf).coerceIn(0f, 1f)
                    val lightStart = androidx.compose.ui.geometry.Offset(
                        start.x + (end.x - start.x) * highlightStart,
                        start.y + (end.y - start.y) * highlightStart
                    )
                    val lightEnd = androidx.compose.ui.geometry.Offset(
                        start.x + (end.x - start.x) * highlightEnd,
                        start.y + (end.y - start.y) * highlightEnd
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = highlightFade * 0.56f),
                                Color.Transparent
                            ),
                            start = lightStart,
                            end = lightEnd
                        ),
                        start = lightStart,
                        end = lightEnd,
                        strokeWidth = stroke * 0.72f,
                        cap = StrokeCap.Round
                    )
                }

                if (burstProgress.value < 0.995f) {
                    val palette = listOf(
                        colors.accent,
                        Color(0xFF79D7FF),
                        Color(0xFFFFD166),
                        Color(0xFFFF8DDA),
                        Color.White
                    )
                    repeat(112) { index ->
                        val fromLeft = index % 2 == 0
                        val burstRow = (index / 2) % 2
                        val delay = (index % 28) * 0.009f
                        val local = ((burstProgress.value - delay) / 0.82f).coerceIn(0f, 1f)
                        if (local <= 0f || local >= 1f) return@repeat
                        val eased = 1f - (1f - local) * (1f - local)
                        val spread = (index % 56) / 55f
                        val angleDegrees = if (fromLeft) {
                            -68f + spread * 136f
                        } else {
                            112f + spread * 136f
                        }
                        val angle = angleDegrees / 180f * PI
                        val originY = size.height * if (burstRow == 0) 0.38f else 0.66f
                        val origin = if (fromLeft) {
                            androidx.compose.ui.geometry.Offset(-2.dp.toPx(), originY)
                        } else {
                            androidx.compose.ui.geometry.Offset(size.width + 2.dp.toPx(), originY)
                        }
                        val distance = size.width * (0.24f + (index % 9) * 0.042f) * eased
                        val gravity = size.height * 0.038f * local * local
                        val directionX = cos(angle).toFloat()
                        val directionY = sin(angle).toFloat()
                        val center = androidx.compose.ui.geometry.Offset(
                            origin.x + directionX * distance,
                            origin.y + directionY * distance + gravity
                        )
                        val alpha = sin(PI * local).toFloat().coerceIn(0f, 1f) * 0.96f
                        val particleColor = palette[index % palette.size].copy(alpha = alpha)
                        val trail = (9f + (index % 4) * 2.8f).dp.toPx() * (1f - local * 0.42f)
                        drawLine(
                            color = particleColor.copy(alpha = alpha * 0.62f),
                            start = androidx.compose.ui.geometry.Offset(
                                center.x - directionX * trail,
                                center.y - directionY * trail
                            ),
                            end = center,
                            strokeWidth = (1.3f + (index % 3) * 0.45f).dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = particleColor,
                            radius = (2.4f + (index % 4) * 0.62f).dp.toPx(),
                            center = center
                        )
                    }
                }
            }

            if (colors.isMaterialYou) {
                MaterialUpdateEmojiBackground(
                    phase = linePhaseState,
                    animated = animationsEnabled
                )
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
                                scaleX = 0.88f + ringProgress.value * 0.12f
                                scaleY = scaleX
                            }
                    ) {
                        val stroke = 7.dp.toPx()
                        drawCircle(colors.accent.copy(alpha = 0.12f), radius = size.minDimension * 0.46f)
                        drawArc(
                            color = colors.accent,
                            startAngle = -90f,
                            sweepAngle = 360f * ringProgress.value,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        val first = (checkProgress.value * 2f).coerceIn(0f, 1f)
                        val second = ((checkProgress.value - 0.5f) * 2f).coerceIn(0f, 1f)
                        val a = androidx.compose.ui.geometry.Offset(size.width * 0.31f, size.height * 0.53f)
                        val b = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.66f)
                        val c = androidx.compose.ui.geometry.Offset(size.width * 0.71f, size.height * 0.39f)
                        val firstEnd = androidx.compose.ui.geometry.Offset(
                            a.x + (b.x - a.x) * first,
                            a.y + (b.y - a.y) * first
                        )
                        if (first > 0f) drawLine(colors.textPrimary, a, firstEnd, stroke, StrokeCap.Round)
                        if (second > 0f) {
                            val secondEnd = androidx.compose.ui.geometry.Offset(
                                b.x + (c.x - b.x) * second,
                                b.y + (c.y - b.y) * second
                            )
                            drawLine(colors.textPrimary, b, secondEnd, stroke, StrokeCap.Round)
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

private data class UpdateRibbonSegment(
    /** Вертикальная строка, по которой едет капсула, доля высоты экрана. */
    val rowY: Float,
    val lengthFraction: Float,
    val angleDegrees: Float,
    val strokeDp: Float,
    /** Стартовый сдвиг по времени: разносит капсулы, чтобы шли друг за другом. */
    val phaseOffset: Float,
    /** Своя скорость у каждой капсулы — поток не выглядит марширующим строем. */
    val speed: Float,
    val alpha: Float
)

/** Нормализует время в 0..1 для бесконечного проезда. */
private fun wrapUnit(value: Float): Float {
    val v = value % 1f
    return if (v < 0f) v + 1f else v
}

/**
 * Тот же поток, что и у капсул, только эмодзи: каждый едет по своей строке
 * слева направо, друг за другом. Фаза читается внутри `graphicsLayer`, то есть
 * в фазе отрисовки — кадры не вызывают рекомпозицию всего диалога.
 */
@Composable
private fun MaterialUpdateEmojiBackground(
    phase: State<Float>,
    animated: Boolean
) {
    // remember is intentionally scoped to this dialog: reopening the update
    // screen produces another calm combination instead of a static wallpaper.
    val emojis = remember {
        listOf(
            "✨", "🛡️", "🌐", "⚡", "✅", "📱",
            "💻", "🔄", "🎉", "☁️", "🔒", "🚀"
        ).shuffled().take(MATERIAL_EMOJI_LANES.size)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val laneWidth = maxWidth
        val laneHeight = maxHeight
        emojis.forEachIndexed { index, emoji ->
            val lane = MATERIAL_EMOJI_LANES[index]
            Text(
                text = emoji,
                modifier = Modifier
                    .graphicsLayer {
                        val travel = wrapUnit(phase.value * lane.speed + lane.phaseOffset)
                        val widthPx = laneWidth.toPx()
                        val own = size.width.coerceAtLeast(1f)
                        val x = -own + travel * (widthPx + own * 2f)
                        translationX = x
                        // Тот же наклон, что и у капсул: эмодзи ползёт наискосок,
                        // а не съезжает строго вбок.
                        val slope = tan(lane.angleDegrees / 180f * PI).toFloat()
                        translationY = laneHeight.toPx() * lane.rowY + (x - widthPx * 0.5f) * slope
                        alpha = 0.13f + (index % 3) * 0.02f
                        rotationZ = lane.angleDegrees * 0.5f
                    },
                fontSize = (24 + (index % 3) * 6).sp
            )
        }
    }
}

private data class MaterialEmojiLane(
    val rowY: Float,
    val phaseOffset: Float,
    val speed: Float,
    val angleDegrees: Float
)

private val MATERIAL_EMOJI_LANES = listOf(
    MaterialEmojiLane(0.10f, 0.00f, 0.94f, -14f),
    MaterialEmojiLane(0.26f, 0.34f, 1.04f, -12f),
    MaterialEmojiLane(0.42f, 0.68f, 0.90f, -15f),
    MaterialEmojiLane(0.58f, 0.18f, 1.06f, -13f),
    MaterialEmojiLane(0.74f, 0.52f, 0.96f, -14f),
    MaterialEmojiLane(0.90f, 0.80f, 1.00f, -12f)
)
