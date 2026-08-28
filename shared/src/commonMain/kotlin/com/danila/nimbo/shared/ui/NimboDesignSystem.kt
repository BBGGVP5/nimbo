package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object NimboPalette {
    val Background = Color(0xFF071426)
    val BackgroundDeep = Color(0xFF020A16)
    val Surface = Color(0xE6192B45)
    val SurfaceStrong = Color(0xF3223552)
    val Control = Color(0xB5294165)
    val Soft = Color(0x5C42618D)
    val Border = Color(0x5C8DB8F4)
    val Hairline = Color(0x2E9BC2F8)
    val Accent = Color(0xFF72A8FF)
    val AccentStrong = Color(0xFF4D88F1)
    val Text = Color(0xFFF3F6FF)
    val TextSecondary = Color(0xFF9DACCA)
    val TextTertiary = Color(0xFF657696)
    val Green = Color(0xFF62DFA5)
    val Amber = Color(0xFFFFBE55)
    val Red = Color(0xFFFF7A82)
}

internal val NimboTitleStyle = TextStyle(
    color = NimboPalette.Text,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.ExtraBold
)

internal val NimboSectionTitleStyle = TextStyle(
    color = NimboPalette.Text,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    fontWeight = FontWeight.Bold
)

internal val NimboBodyStyle = TextStyle(
    color = NimboPalette.TextSecondary,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium
)

@Composable
internal fun NimboSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    strong: Boolean = false,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (strong) NimboPalette.SurfaceStrong else NimboPalette.Surface,
                        if (strong) NimboPalette.SurfaceStrong.copy(alpha = 0.90f)
                        else NimboPalette.Surface.copy(alpha = 0.78f)
                    )
                )
            )
            .border(1.dp, NimboPalette.Border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(padding),
        content = content
    )
}

@Composable
internal fun NimboPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(if (selected) NimboPalette.Accent.copy(alpha = 0.22f) else NimboPalette.Control)
            .border(
                1.dp,
                if (selected) NimboPalette.Accent.copy(alpha = 0.85f) else NimboPalette.Hairline,
                shape
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
        style = TextStyle(
            color = if (selected) NimboPalette.Accent else NimboPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

internal fun Modifier.nimboScreenPadding(): Modifier =
    fillMaxWidth().padding(horizontal = 20.dp)
