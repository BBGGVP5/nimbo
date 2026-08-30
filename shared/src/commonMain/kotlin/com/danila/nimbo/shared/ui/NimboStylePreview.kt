package com.danila.nimbo.shared.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Карточка стиля с миниатюрой интерфейса — как на Android.
 *
 * Названия и подписи те же, а рисунок собран заново и компактнее: андроидная
 * версия занимает почти пятьсот строк с ветками под светлую тему и все шесть
 * стилей. Здесь четыре, которые на iOS осмысленны, и только тёмная тема.
 */
@Composable
internal fun NimboStylePreviewCard(
    style: NimboElementStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(if (style == NimboElementStyle.DOTTED) 10.dp else 18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) NimboPalette.Accent.copy(alpha = 0.13f) else NimboPalette.Surface
            )
            .border(
                1.dp,
                if (selected) NimboPalette.Accent.copy(alpha = 0.74f) else NimboPalette.Border,
                shape
            )
            .nimboRowClickable(onClick)
            .padding(10.dp)
    ) {
        StylePhoneMock(style)
        Spacer(Modifier.height(8.dp))
        BasicText(
            style.title,
            maxLines = 1,
            style = TextStyle(
                color = if (selected) NimboPalette.Text else NimboPalette.Text.copy(alpha = 0.86f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        BasicText(
            style.subtitle,
            maxLines = 1,
            style = TextStyle(color = NimboPalette.TextSecondary, fontSize = 11.sp)
        )
    }
}

/**
 * Миниатюра домашнего экрана: шапка, круглая кнопка подключения и нижняя
 * панель. Каждый стиль перекрашивает их так же, как перекрашивает настоящий
 * интерфейс, — по картинке видно, что выбираешь.
 */
@Composable
private fun StylePhoneMock(style: NimboElementStyle) {
    val screenShape = RoundedCornerShape(12.dp)
    val base = when (style) {
        NimboElementStyle.DOTTED -> Color(0xFF101114)
        NimboElementStyle.SIGNAL -> Color(0xFF0B0F16)
        NimboElementStyle.MATERIAL_YOU -> Color(0xFF17131C)
        NimboElementStyle.NIMBO_GLASS -> Color(0xFF0A1024)
    }
    val panel = when (style) {
        NimboElementStyle.DOTTED -> NimboPalette.Accent.copy(alpha = 0.18f)
        NimboElementStyle.SIGNAL -> Color.White.copy(alpha = 0.05f)
        NimboElementStyle.MATERIAL_YOU -> NimboPalette.Accent.copy(alpha = 0.20f)
        NimboElementStyle.NIMBO_GLASS -> Color.White.copy(alpha = 0.16f)
    }
    val control = when (style) {
        NimboElementStyle.DOTTED -> NimboPalette.Accent.copy(alpha = 0.28f)
        NimboElementStyle.SIGNAL -> NimboPalette.Accent.copy(alpha = 0.42f)
        NimboElementStyle.MATERIAL_YOU -> NimboPalette.Accent.copy(alpha = 0.32f)
        NimboElementStyle.NIMBO_GLASS -> NimboPalette.Accent.copy(alpha = 0.38f)
    }
    val panelShape = when (style) {
        NimboElementStyle.DOTTED -> RoundedCornerShape(4.dp)
        NimboElementStyle.SIGNAL -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(8.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(screenShape)
            .background(Color(0xFF050609))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(base)
                .then(
                    if (style == NimboElementStyle.DOTTED) {
                        Modifier.nimboDotPattern(Color.White, spacing = 7.dp, radius = 0.6.dp, alpha = 0.16f)
                    } else {
                        Modifier
                    }
                )
                .padding(7.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Шапка: две «строки» карточки подписки.
                MockBar(width = 34.dp, color = panel, shape = panelShape)
                Spacer(Modifier.height(4.dp))
                MockBar(width = 22.dp, color = panel.copy(alpha = panel.alpha * 0.7f), shape = panelShape)

                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ConnectMock(style, control)
                }
                Spacer(Modifier.weight(1f))

                // Нижняя панель со «вкладками».
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(
                            when (style) {
                                NimboElementStyle.DOTTED -> RoundedCornerShape(4.dp)
                                NimboElementStyle.SIGNAL -> RoundedCornerShape(6.dp)
                                else -> RoundedCornerShape(999.dp)
                            }
                        )
                        .background(panel)
                        .then(
                            if (style == NimboElementStyle.DOTTED) {
                                Modifier.nimboDottedOutline(
                                    NimboPalette.Accent.copy(alpha = 0.42f),
                                    cornerRadius = 4.dp
                                )
                            } else if (style == NimboElementStyle.SIGNAL) {
                                Modifier.border(
                                    0.7.dp,
                                    Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(6.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 6.dp)
                            .clip(
                                if (style == NimboElementStyle.DOTTED) {
                                    RoundedCornerShape(2.dp)
                                } else {
                                    RoundedCornerShape(999.dp)
                                }
                            )
                            .background(control)
                    )
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.32f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectMock(style: NimboElementStyle, control: Color) {
    when (style) {
        // Material You: сплошная «таблетка» вместо кольца.
        NimboElementStyle.MATERIAL_YOU -> Box(
            modifier = Modifier
                .size(width = 34.dp, height = 20.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NimboPalette.Accent)
        )
        // Dotted: квадрат с точечным контуром.
        NimboElementStyle.DOTTED -> Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NimboPalette.Accent.copy(alpha = 0.85f))
                .nimboDottedOutline(Color.White.copy(alpha = 0.6f), cornerRadius = 6.dp)
        )
        // Стекло и Signal: кольцо с ободком, как настоящая кнопка.
        else -> Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(control.copy(alpha = 0.22f))
                .border(1.dp, control, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = 10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun MockBar(width: androidx.compose.ui.unit.Dp, color: Color, shape: RoundedCornerShape) {
    Box(
        modifier = Modifier
            .width(width)
            .height(5.dp)
            .clip(shape)
            .background(color)
    )
}
