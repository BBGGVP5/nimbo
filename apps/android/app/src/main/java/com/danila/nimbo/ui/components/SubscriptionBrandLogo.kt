package com.danila.nimbo.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.LocalGlobalCornerRadius
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.SubscriptionLogoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SubscriptionBrandLogo(
    logo: String?,
    cachedLogo: String? = null,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    val cornerScale = LocalGlobalCornerRadius.current
    val shape = RoundedCornerShape(10.dp * cornerScale)
    val borderColor = nebulaColors.textPrimary.copy(alpha = 0.15f)

    val logoSource = SubscriptionLogoCache.displayLogo(logo, cachedLogo)
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = logoSource
    ) {
        value = withContext(Dispatchers.IO) {
            logoSource
                ?.let { SubscriptionLogoCache.loadLogoBytes(it) }
                ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }

    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(nebulaColors.textPrimary.copy(alpha = 0.03f))
                .border(1.dp, borderColor, shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(nebulaColors.textPrimary.copy(alpha = 0.05f))
                .border(1.dp, borderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = nebulaColors.accent,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}
