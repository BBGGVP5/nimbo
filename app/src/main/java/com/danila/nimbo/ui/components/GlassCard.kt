package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    NimboPanel(modifier) { Box(content = content) }

}


