package com.danila.nimbo.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.danila.nimbo.R
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.jellyScrollAnimation
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.AppIconManager
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun AppIconSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    var selectedAppIcon by remember { mutableStateOf(preferencesManager.selectedAppIcon) }
    var customIconBase64 by remember { mutableStateOf(preferencesManager.customAppIconBase64) }
    var showIconConfirmDialog by remember { mutableStateOf<Int?>(null) }
    var iconSyncMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedAppIcon) {
        preferencesManager.selectedAppIcon = selectedAppIcon
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val encoded = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            runCatching { encodeImageToBase64(context, uri) }
                .onSuccess { value ->
                    if (!value.isNullOrBlank()) {
                        customIconBase64 = value
                        preferencesManager.customAppIconBase64 = value
                        iconSyncMessage = "Своя иконка сохранена."
                    } else {
                        iconSyncMessage = "Не удалось обработать изображение."
                    }
                }
                .onFailure {
                    iconSyncMessage = "Ошибка при выборе изображения."
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .jellyScrollAnimation()
        ) {
            GlassHeader(
                title = "Иконка приложения",
                icon = Icons.Default.Apps,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassSection(title = "Выбор иконки", icon = Icons.Default.Apps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconArtwork(
                            previewRes = AppIconManager.iconPreviewByIndex(selectedAppIcon),
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .border(
                                    2.dp,
                                    nebulaColors.accent.copy(alpha = 0.45f),
                                    RoundedCornerShape(32.dp)
                                )
                        )

                        Text(
                            text = AppIconManager.iconTitleByIndex(selectedAppIcon),
                            color = nebulaColors.textSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        Text(
                            text = AppIconManager.iconDescriptionByIndex(selectedAppIcon),
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = nebulaColors.onSurface.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppIconManager.ICON_PREVIEWS.chunked(4).forEachIndexed { rowIndex, rowIcons ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowIcons.forEachIndexed { colIndex, iconRes ->
                                        val index = rowIndex * 4 + colIndex
                                        val isSelected = selectedAppIcon == index

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isSelected) {
                                                        nebulaColors.accent.copy(alpha = 0.20f)
                                                    } else {
                                                        Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) {
                                                        nebulaColors.accent
                                                    } else {
                                                        nebulaColors.onSurface.copy(alpha = 0.12f)
                                                    },
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    if (selectedAppIcon != index) {
                                                        showIconConfirmDialog = index
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppIconArtwork(
                                                previewRes = iconRes,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                            )

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(4.dp),
                                                    contentAlignment = Alignment.BottomEnd
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = nebulaColors.accent,
                                                        modifier = Modifier
                                                            .size(19.dp)
                                                            .background(Color.Black, CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (rowIcons.size < 4) {
                                        repeat(4 - rowIcons.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                GlassSection(title = "Своя иконка", icon = Icons.Default.Apps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!customIconBase64.isNullOrBlank()) {
                            AppIconBase64Artwork(
                                base64 = customIconBase64,
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .border(
                                        2.dp,
                                        nebulaColors.accent.copy(alpha = 0.45f),
                                        RoundedCornerShape(32.dp)
                                    )
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(32.dp)),
                                color = nebulaColors.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Нет", color = nebulaColors.textTertiary)
                                }
                            }
                        }

                        Text(
                            text = "Можно выбрать свою иконку из галереи. Она сохраняется локально на устройстве.",
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { galleryLauncher.launch(arrayOf("image/*")) }
                            ) {
                                Text("Выбрать из галереи")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    customIconBase64 = null
                                    preferencesManager.customAppIconBase64 = null
                                    iconSyncMessage = "Своя иконка удалена."
                                }
                            ) {
                                Text("Удалить")
                            }
                        }

                        if (!iconSyncMessage.isNullOrBlank()) {
                            Text(
                                text = iconSyncMessage!!,
                                color = nebulaColors.textTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(96.dp))
            }
        }
    }

    showIconConfirmDialog?.let { index: Int ->
        NebulaMorphicDialog(
            onDismissRequest = { showIconConfirmDialog = null },
            title = "Смена иконки",
            description = "Для обновления иконки на рабочем столе может потребоваться перезапуск лаунчера. Применить?",
            confirmButtonText = "Применить",
            onConfirm = {
                showIconConfirmDialog = null
                selectedAppIcon = index
                AppIconManager.setAppIcon(context, index)
            }
        )
    }
}

@Composable
private fun AppIconArtwork(
    previewRes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AppIconResourceImage(
            resId = previewRes,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            scaleType = ImageView.ScaleType.FIT_CENTER
        )
    }
}

@Composable
fun AppIconResourceImage(
    resId: Int,
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                this.scaleType = scaleType
                adjustViewBounds = true
            }
        },
        modifier = modifier,
        update = { imageView ->
            imageView.scaleType = scaleType
            runCatching {
                imageView.setImageResource(resId)
            }.onFailure {
                imageView.setImageResource(R.drawable.sprite_0000)
            }
        }
    )
}

@Composable
private fun AppIconBase64Artwork(
    base64: String?,
    modifier: Modifier = Modifier
) {
    val bytes = remember(base64) {
        runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
    }
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
            }
        },
        modifier = modifier,
        update = { imageView ->
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bmp != null) imageView.setImageBitmap(bmp)
            else imageView.setImageResource(R.mipmap.ic_launcher_nimbo_blue_v2)
        }
    )
}

private suspend fun encodeImageToBase64(
    context: android.content.Context,
    uri: android.net.Uri
): String? = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return@withContext null

    val scaled = android.graphics.Bitmap.createScaledBitmap(bytes, 256, 256, true)
    val output = ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, output)
    Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
