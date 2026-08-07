package com.danila.nimbo.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.danila.nimbo.R
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.jellyScrollAnimation
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.AppIconManager
import com.danila.nimbo.utils.CustomAppIconConfig
import com.danila.nimbo.utils.CustomAppIconManager
import com.danila.nimbo.utils.CustomAppIconPreset
import com.danila.nimbo.utils.CustomLauncherIconResult
import com.danila.nimbo.utils.CustomCloudStyle
import com.danila.nimbo.utils.CustomIconShape
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

    var selectedAppIcon by remember { mutableStateOf(AppIconManager.getCurrentIconIndex(context)) }
    var customIconBase64 by remember { mutableStateOf(preferencesManager.customAppIconBase64) }
    var customIconShape by remember { mutableStateOf(preferencesManager.customIconShape) }
    var customIconBackgroundColor by remember { mutableStateOf(preferencesManager.customIconBackgroundColor) }
    var customIconCloudColor by remember { mutableStateOf(preferencesManager.customIconCloudColor) }
    var customIconCloudStyle by remember { mutableStateOf(preferencesManager.customIconCloudStyle) }
    var customIconUseImported by remember { mutableStateOf(preferencesManager.customIconUseImported) }
    var customNotificationIconEnabled by remember { mutableStateOf(preferencesManager.customNotificationIconEnabled) }
    var showIconConfirmDialog by remember { mutableStateOf<Int?>(null) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var iconSyncMessage by remember { mutableStateOf<String?>(null) }
    var customIconActive by remember { mutableStateOf(AppIconManager.isCustomIconActive(context)) }
    var lastAppliedConfig by remember { mutableStateOf<CustomAppIconConfig?>(null) }

    LaunchedEffect(selectedAppIcon) {
        preferencesManager.selectedAppIcon = selectedAppIcon
    }

    LaunchedEffect(
        customIconShape,
        customIconBackgroundColor,
        customIconCloudColor,
        customIconCloudStyle,
        customIconUseImported,
        customNotificationIconEnabled
    ) {
        preferencesManager.customIconShape = customIconShape
        preferencesManager.customIconBackgroundColor = customIconBackgroundColor
        preferencesManager.customIconCloudColor = customIconCloudColor
        preferencesManager.customIconCloudStyle = customIconCloudStyle
        preferencesManager.customIconUseImported = customIconUseImported
        preferencesManager.customNotificationIconEnabled = customNotificationIconEnabled
    }

    val customConfig = remember(
        customIconShape,
        customIconBackgroundColor,
        customIconCloudColor,
        customIconCloudStyle,
        customIconUseImported,
        customIconBase64
    ) {
        CustomAppIconConfig(
            shape = CustomIconShape.fromIndex(customIconShape),
            backgroundColor = customIconBackgroundColor,
            cloudColor = customIconCloudColor,
            cloudStyle = CustomCloudStyle.fromIndex(customIconCloudStyle),
            useImportedImage = customIconUseImported,
            importedImageBase64 = customIconBase64
        )
    }
    val customArtwork = remember(customConfig) {
        CustomAppIconManager.renderIcon(context, customConfig)
    }
    val customPreview = remember(customConfig) {
        CustomAppIconManager.renderSystemMaskedPreview(context, customArtwork)
    }
    // Notifications use the artwork itself; launcher previews deliberately go
    // through Android's adaptive-icon renderer above.
    val notificationPreview = customArtwork
    val shapePreviews = remember(
        customConfig,
        customIconShape,
        customIconBackgroundColor,
        customIconCloudColor,
        customIconCloudStyle,
        customIconUseImported,
        customIconBase64
    ) {
        CustomIconShape.entries.map { shape ->
            CustomAppIconManager.renderSystemMaskedPreview(
                context = context,
                config = customConfig.copy(shape = shape),
                size = 128
            )
        }
    }
    val cloudPreviews = remember(
        customIconShape,
        customIconBackgroundColor,
        customIconCloudColor
    ) {
        CustomCloudStyle.entries.map { cloudStyle ->
            CustomAppIconManager.renderSystemMaskedPreview(
                context,
                customConfig.copy(
                    cloudStyle = cloudStyle,
                    useImportedImage = false,
                    importedImageBase64 = null
                ),
                192
            )
        }
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
                        customIconUseImported = true
                        preferencesManager.customAppIconBase64 = value
                        preferencesManager.customIconUseImported = true
                        iconSyncMessage = "Своя иконка загружена и выбрана в конструкторе."
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
                onBack = onNavigateBack,
                bordered = false
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassSection(title = "Выбор иконки", icon = Icons.Default.Apps) {
                    LauncherIconGallery(
                        selectedIndex = selectedAppIcon,
                        customActive = customIconActive,
                        onSelect = { index ->
                            if (customIconActive) customIconActive = false
                            if (selectedAppIcon != index) showIconConfirmDialog = index
                        },
                        accent = nebulaColors.accent,
                        primaryText = nebulaColors.textPrimary,
                        secondaryText = nebulaColors.textSecondary,
                        tertiaryText = nebulaColors.textTertiary,
                        surface = nebulaColors.onSurface
                    )
                }

                Spacer(Modifier.height(12.dp))

                GlassSection(title = "Конструктор иконки", icon = Icons.Default.Apps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CustomIconBitmapArtwork(
                            bitmap = customPreview,
                            modifier = Modifier
                                .size(124.dp)
                                .border(2.dp, nebulaColors.accent.copy(alpha = 0.45f), RoundedCornerShape(36.dp))
                        )

                        Text(
                            "Соберите отдельный стиль для уведомлений и ярлыка Nimbo на рабочем столе.",
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { galleryLauncher.launch(arrayOf("image/*")) }
                        ) {
                            Text(if (customIconBase64.isNullOrBlank()) "Загрузить своё изображение" else "Заменить изображение")
                        }

                        if (!customIconBase64.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(nebulaColors.onSurface.copy(alpha = 0.055f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Своё изображение", color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Использовать его вместо фирменного облака",
                                        color = nebulaColors.textTertiary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = customIconUseImported,
                                    onCheckedChange = { customIconUseImported = it }
                                )
                            }
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    customIconBase64 = null
                                    customIconUseImported = false
                                    preferencesManager.customAppIconBase64 = null
                                    preferencesManager.customIconUseImported = false
                                    iconSyncMessage = "Загруженное изображение удалено."
                                }
                            ) { Text("Удалить загруженное изображение") }
                        }

                        AndroidShapePicker(
                            previews = shapePreviews,
                            descriptions = CustomIconShape.entries.map { it.title },
                            selectedIndex = customIconShape,
                            onSelected = { customIconShape = it },
                            accent = nebulaColors.accent,
                            textColor = nebulaColors.textPrimary
                        )

                        Text(
                            "Внешнюю форму задаёт лаунчер телефона. Предпросмотр использует системную adaptive-маску; выбранный ниже силуэт остаётся внутри неё.",
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        IconPreviewOptionRow(
                            title = "Облако",
                            previews = cloudPreviews,
                            descriptions = CustomCloudStyle.entries.map { it.title },
                            selectedIndex = customIconCloudStyle,
                            onSelected = { customIconCloudStyle = it },
                            accent = nebulaColors.accent,
                            textColor = nebulaColors.textPrimary
                        )

                        IconColorRow(
                            title = "Цвет фона",
                            colors = CustomAppIconManager.backgroundPalette,
                            selectedColor = customIconBackgroundColor,
                            onSelected = { customIconBackgroundColor = it },
                            onOpenPalette = { showBackgroundColorPicker = true },
                            textColor = nebulaColors.textPrimary,
                            accent = nebulaColors.accent
                        )

                        if (customIconCloudStyle != CustomCloudStyle.ORIGINAL.ordinal) {
                            IconColorRow(
                                title = "Цвет облака",
                                colors = CustomAppIconManager.cloudPalette,
                                selectedColor = customIconCloudColor,
                                onSelected = { customIconCloudColor = it },
                                textColor = nebulaColors.textPrimary,
                                accent = nebulaColors.accent
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(nebulaColors.onSurface.copy(alpha = 0.055f))
                                .clickable { customNotificationIconEnabled = !customNotificationIconEnabled }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Для уведомлений", color = nebulaColors.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Показывать собранную иконку справа в уведомлении",
                                    color = nebulaColors.textTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = customNotificationIconEnabled,
                                onCheckedChange = { customNotificationIconEnabled = it }
                            )
                        }

                        NotificationIconPreview(
                            bitmap = notificationPreview,
                            usesCustomIcon = customNotificationIconEnabled,
                            primaryText = nebulaColors.textPrimary,
                            secondaryText = nebulaColors.textSecondary,
                            surface = nebulaColors.surface,
                            accent = nebulaColors.accent
                        )

                        val needsReapply = customConfig != lastAppliedConfig
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !customIconActive || needsReapply,
                            onClick = {
                                scope.launch {
                                    val rendered = withContext(Dispatchers.IO) {
                                        CustomAppIconManager.renderIcon(context, customConfig, 432)
                                    }
                                    val result = withContext(Dispatchers.Default) {
                                        CustomAppIconManager.applyCustomLauncherIcon(context, rendered)
                                    }
                                    if (result != CustomLauncherIconResult.UNSUPPORTED) {
                                        lastAppliedConfig = customConfig
                                    }
                                    customIconActive = result != CustomLauncherIconResult.UNSUPPORTED
                                    iconSyncMessage = when (result) {
                                        CustomLauncherIconResult.UPDATED ->
                                            "Иконка на рабочем столе обновлена. Используется тот же ярлык Nimbo."
                                        CustomLauncherIconResult.REQUESTED ->
                                            "Подтвердите добавление ярлыка Nimbo в системном окне."
                                        CustomLauncherIconResult.UNSUPPORTED ->
                                            "Лаунчер не разрешил пользовательский ярлык. Предустановленные иконки доступны выше."
                                    }
                                }
                            }
                        ) {
                            Text(
                                when {
                                    !customIconActive -> "Применить как иконку"
                                    needsReapply -> "Обновить ярлык"
                                    else -> "Своя иконка установлена"
                                }
                            )
                        }

                        Text(
                            "Android применяет собранный вариант к отдельному ярлыку Nimbo через системную adaptive-иконку. Предустановленные варианты выше меняют основную иконку приложения.",
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )

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
                // A pinned custom shortcut is a separate Android object. Keep
                // it available for users who want both variants, while the
                // main launcher alias switches cleanly to the preset.
                AppIconManager.setAppIcon(context, index)
            }
        )
    }

    if (showBackgroundColorPicker) {
        FullColorPickerDialog(
            initialColor = customIconBackgroundColor,
            accent = nebulaColors.accent,
            textPrimary = nebulaColors.textPrimary,
            textSecondary = nebulaColors.textSecondary,
            surfaceColor = nebulaColors.surface,
            onDismiss = { showBackgroundColorPicker = false },
            onApply = { selectedColor ->
                customIconBackgroundColor = selectedColor
                showBackgroundColorPicker = false
            }
        )
    }
}

@Composable
private fun LauncherIconGallery(
    selectedIndex: Int,
    customActive: Boolean,
    onSelect: (Int) -> Unit,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    tertiaryText: Color,
    surface: Color
) {
    val selected = AppIconManager.ICON_OPTIONS.getOrElse(selectedIndex) {
        AppIconManager.ICON_OPTIONS.first()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(surface.copy(alpha = 0.055f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReadyLauncherIcon(
                index = selectedIndex,
                modifier = Modifier.size(84.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selected.title,
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = selected.description,
                    color = secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (customActive) {
                        "Сейчас установлена ваша иконка из конструктора"
                    } else {
                        "Выбрана для рабочего стола"
                    },
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "Все варианты используют фирменное облако Nimbo и корректную безопасную зону Android.",
            color = tertiaryText,
            style = MaterialTheme.typography.bodySmall
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            AppIconManager.ICON_OPTIONS.chunked(3).forEachIndexed { rowIndex, rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rowOptions.forEachIndexed { columnIndex, option ->
                        val index = rowIndex * 3 + columnIndex
                        val isSelected = index == selectedIndex
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelect(index) },
                            color = if (isSelected) accent.copy(alpha = 0.17f) else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 1.8.dp else 1.dp,
                                if (isSelected) accent else surface.copy(alpha = 0.13f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    ReadyLauncherIcon(
                                        index = index,
                                        modifier = Modifier.size(68.dp)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Выбрано",
                                            tint = accent,
                                            modifier = Modifier
                                                .size(19.dp)
                                                .background(Color.Black.copy(alpha = 0.82f), CircleShape)
                                        )
                                    }
                                }
                                Text(
                                    text = option.title,
                                    color = primaryText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ReadyLauncherIcon(index: Int, modifier: Modifier = Modifier) {
    val option = AppIconManager.ICON_OPTIONS.getOrElse(index) { AppIconManager.ICON_OPTIONS.first() }
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        AppIconResourceImage(
            resId = option.previewRes,
            modifier = Modifier.fillMaxSize(),
            scaleType = ImageView.ScaleType.FIT_CENTER
        )
    }
}

@Composable
private fun IconPresetGrid(
    presets: List<CustomAppIconPreset>,
    currentConfig: CustomAppIconConfig,
    onSelected: (CustomAppIconPreset) -> Unit,
    accent: Color,
    textColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val previews = remember(presets) {
        presets.associateWith { preset ->
            CustomAppIconManager.renderIcon(context, preset.config, 160)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        presets.chunked(3).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowPresets.forEach { preset ->
                    val selected = !currentConfig.useImportedImage &&
                        currentConfig.shape == preset.config.shape &&
                        currentConfig.backgroundColor == preset.config.backgroundColor &&
                        currentConfig.cloudColor == preset.config.cloudColor &&
                        currentConfig.cloudStyle == preset.config.cloudStyle
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelected(preset) },
                        color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 1.6.dp else 1.dp,
                            if (selected) accent else textColor.copy(alpha = 0.13f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CustomIconBitmapArtwork(
                                bitmap = checkNotNull(previews[preset]),
                                modifier = Modifier.size(70.dp)
                            )
                            Text(
                                preset.title,
                                color = textColor,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IconPreviewOptionRow(
    title: String,
    previews: List<Bitmap>,
    descriptions: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    accent: Color,
    textColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = textColor, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            previews.forEachIndexed { index, bitmap ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .semantics {
                            contentDescription = descriptions.getOrElse(index) { "$title ${index + 1}" }
                        }
                        .clickable { onSelected(index) },
                    color = if (selected) accent.copy(alpha = 0.16f) else textColor.copy(alpha = 0.035f),
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) accent else textColor.copy(alpha = 0.14f)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomIconBitmapArtwork(
                            bitmap = bitmap,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = descriptions.getOrNull(index)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Выбрано",
                                tint = accent,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidShapePicker(
    previews: List<Bitmap>,
    descriptions: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    accent: Color,
    textColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Силуэт внутри иконки", color = textColor, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = textColor.copy(alpha = 0.035f),
            border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                previews.forEachIndexed { index, bitmap ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) accent.copy(alpha = 0.22f)
                                else textColor.copy(alpha = 0.055f)
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) accent else textColor.copy(alpha = 0.08f),
                                shape = CircleShape
                            )
                            .semantics {
                                contentDescription = descriptions.getOrElse(index) { "Форма ${index + 1}" }
                            }
                            .clickable { onSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = descriptions.getOrElse(selectedIndex) { "Форма иконки" },
            color = textColor.copy(alpha = 0.58f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun IconColorRow(
    title: String,
    colors: List<Int>,
    selectedColor: Int,
    onSelected: (Int) -> Unit,
    onOpenPalette: (() -> Unit)? = null,
    textColor: Color,
    accent: Color
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = textColor, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            colors.forEach { colorValue ->
                val color = Color(colorValue)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (colorValue == selectedColor) 3.dp else 1.dp,
                            if (colorValue == selectedColor) textColor else textColor.copy(alpha = 0.18f),
                            CircleShape
                        )
                        .clickable { onSelected(colorValue) }
                )
            }
            if (onOpenPalette != null) {
                val isCustomColor = selectedColor !in colors
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            if (isCustomColor) 3.dp else 1.dp,
                            if (isCustomColor) accent else textColor.copy(alpha = 0.18f),
                            CircleShape
                        )
                        .clickable(onClick = onOpenPalette),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red
                                )
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor))
                            .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomIconBitmapArtwork(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
private fun NotificationIconPreview(
    bitmap: Bitmap,
    usesCustomIcon: Boolean,
    primaryText: Color,
    secondaryText: Color,
    surface: Color,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surface.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CustomIconBitmapArtwork(
            bitmap = bitmap,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentDescription = "Предпросмотр иконки уведомления"
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nimbo · подключено",
                color = primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = if (usesCustomIcon) {
                    "В уведомлении будет ваш собранный вариант"
                } else {
                    "Включите «Для уведомлений», чтобы использовать собранную иконку"
                },
                color = secondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accent, CircleShape)
        )
    }
}

@Composable
private fun FullColorPickerDialog(
    initialColor: Int,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableStateOf(initialHsv[2]) }
    var saturationAreaSize by remember { mutableStateOf(IntSize.Zero) }
    var hueAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val markerRadius = with(LocalDensity.current) { 8.dp.toPx() }
    val selectedColorInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    val selectedColor = Color(selectedColorInt)

    fun updateSaturationAndValue(x: Float, y: Float) {
        if (saturationAreaSize.width <= 0 || saturationAreaSize.height <= 0) return
        saturation = (x / saturationAreaSize.width).coerceIn(0f, 1f)
        value = 1f - (y / saturationAreaSize.height).coerceIn(0f, 1f)
    }

    fun updateHue(x: Float) {
        if (hueAreaSize.width <= 0) return
        hue = ((x / hueAreaSize.width).coerceIn(0f, 1f) * 360f).coerceAtMost(359.999f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            color = surfaceColor.copy(alpha = 0.98f),
            shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.42f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .border(2.dp, Color.White.copy(alpha = 0.72f), CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Цвет фона", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Выберите любой оттенок", color = textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "#%06X".format(selectedColorInt and 0xFFFFFF),
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .onSizeChanged { saturationAreaSize = it }
                        .pointerInput(hue, saturationAreaSize) {
                            detectTapGestures { updateSaturationAndValue(it.x, it.y) }
                        }
                        .pointerInput(hue, saturationAreaSize) {
                            detectDragGestures { change, _ ->
                                updateSaturationAndValue(change.position.x, change.position.y)
                            }
                        }
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.White, Color.hsv(hue, 1f, 1f))
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    )
                    val markerX = saturation * size.width
                    val markerY = (1f - value) * size.height
                    drawCircle(Color.Black.copy(alpha = 0.48f), markerRadius + 2f, androidx.compose.ui.geometry.Offset(markerX, markerY))
                    drawCircle(Color.White, markerRadius, androidx.compose.ui.geometry.Offset(markerX, markerY), style = Stroke(3f))
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { hueAreaSize = it }
                        .pointerInput(hueAreaSize) {
                            detectTapGestures { updateHue(it.x) }
                        }
                        .pointerInput(hueAreaSize) {
                            detectDragGestures { change, _ -> updateHue(change.position.x) }
                        }
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    )
                    val markerX = (hue / 360f) * size.width
                    drawCircle(Color.Black.copy(alpha = 0.5f), markerRadius + 2f, androidx.compose.ui.geometry.Offset(markerX, size.height / 2f))
                    drawCircle(Color.White, markerRadius, androidx.compose.ui.geometry.Offset(markerX, size.height / 2f), style = Stroke(3f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(onClick = { onApply(selectedColorInt) }) {
                        Text("Применить", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
