package com.danila.nimbo.ui.screens

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danila.nimbo.R
import com.danila.nimbo.ui.components.LocalFloatingNavHeight
import com.danila.nimbo.ui.components.NebulaMorphicDialog
import com.danila.nimbo.ui.components.NimboToolActions
import com.danila.nimbo.utils.AppIconManager
import com.danila.nimbo.utils.CustomAppIconConfig
import com.danila.nimbo.utils.CustomAppIconManager
import com.danila.nimbo.utils.CustomCloudStyle
import com.danila.nimbo.utils.CustomIconShape
import com.danila.nimbo.utils.CustomLauncherIconResult
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun AppIconSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val preferencesManager = remember(context) { PreferencesManager(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var selectedAppIcon by remember { mutableStateOf(readLauncherPresetIndex(context, preferencesManager.selectedAppIcon)) }
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
    var applyingShortcut by remember { mutableStateOf(false) }
    var importingImage by remember { mutableStateOf(false) }
    var shortcutPresent by remember { mutableStateOf(CustomAppIconManager.isPinnedShortcutPresent(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shortcutPresent = CustomAppIconManager.isPinnedShortcutPresent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedAppIcon) { preferencesManager.selectedAppIcon = selectedAppIcon }
    LaunchedEffect(customIconShape, customIconBackgroundColor, customIconCloudColor,
        customIconCloudStyle, customIconUseImported, customNotificationIconEnabled) {
        preferencesManager.customIconShape = customIconShape
        preferencesManager.customIconBackgroundColor = customIconBackgroundColor
        preferencesManager.customIconCloudColor = customIconCloudColor
        preferencesManager.customIconCloudStyle = customIconCloudStyle
        preferencesManager.customIconUseImported = customIconUseImported
        preferencesManager.customNotificationIconEnabled = customNotificationIconEnabled
    }
    val customConfig = remember(customIconShape, customIconBackgroundColor, customIconCloudColor,
        customIconCloudStyle, customIconUseImported, customIconBase64) {
        CustomAppIconConfig(CustomIconShape.fromIndex(customIconShape), customIconBackgroundColor,
            customIconCloudColor, CustomCloudStyle.fromIndex(customIconCloudStyle),
            customIconUseImported, customIconBase64)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importingImage = true
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val value = encodeImageToBase64(context, uri)
                if (!value.isNullOrBlank()) {
                    customIconBase64 = value
                    customIconUseImported = true
                    preferencesManager.customAppIconBase64 = value
                    preferencesManager.customIconUseImported = true
                    iconSyncMessage = "Изображение загружено. Можно добавить или обновить отдельный ярлык."
                } else iconSyncMessage = "Не удалось обработать изображение."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                iconSyncMessage = "Не удалось загрузить изображение. Попробуйте другой файл."
            } finally {
                importingImage = false
            }
        }
    }

    // Insets belong to the fixed root, before the scrolling content. This also
    // respects insets consumed by a host and protects landscape display cutouts.
    Box(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            NimboSubPageHeader(title = "Иконка приложения", onBack = onNavigateBack)
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(colors.surface).padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Готовые", "Конструктор").forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.surfaceVariant else Color.Transparent)
                            .selectable(selected, role = Role.Tab, onClick = { selectedTab = index })
                            .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val bottomPadding = LocalFloatingNavHeight.current + 20.dp
            if (selectedTab == 0) {
                LauncherIconGallery(selectedIndex = selectedAppIcon,
                    onSelect = { index -> if (index != selectedAppIcon) showIconConfirmDialog = index },
                    bottomPadding = bottomPadding,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp))
            } else {
                // Build the editor previews only while this tab is visible.
                val artwork = remember(customConfig) { CustomAppIconManager.renderIcon(context, customConfig) }
                val launcherPreview = remember(artwork) { CustomAppIconManager.renderSystemMaskedPreview(context, artwork) }
                val silhouetteColor = colors.onSurface.toArgb()
                val shapePreviews = remember(silhouetteColor) {
                    CustomIconShape.entries.map { CustomAppIconManager.renderShapePreview(it, silhouetteColor) }
                }
                val cloudPreviews = remember(customConfig.shape, customConfig.backgroundColor, customConfig.cloudColor) {
                    CustomCloudStyle.entries.map {
                        CustomAppIconManager.renderIcon(context,
                            customConfig.copy(cloudStyle = it, useImportedImage = false, importedImageBase64 = null), 128)
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(launcherPreview.asImageBitmap(), "Предпросмотр отдельного ярлыка", Modifier.size(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Свой ярлык", color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Отдельный ярлык на рабочем столе. Основная иконка приложения остаётся выбранной во вкладке «Готовые».",
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch(arrayOf("image/*")) },
                        enabled = !importingImage, modifier = Modifier.fillMaxWidth()) {
                        Text(if (importingImage) "Загрузка…" else if (customIconBase64.isNullOrBlank()) "Загрузить изображение" else "Заменить изображение")
                    }
                    if (!customIconBase64.isNullOrBlank()) {
                        IconEditorToggle("Своё изображение", "Использовать вместо облака", customIconUseImported,
                            onCheckedChange = { customIconUseImported = it })
                        TextButton(onClick = {
                            customIconBase64 = null
                            customIconUseImported = false
                            preferencesManager.customAppIconBase64 = null
                            preferencesManager.customIconUseImported = false
                            iconSyncMessage = "Загруженное изображение удалено из конструктора."
                        }) { Text("Удалить изображение") }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    IconEditorHeading("Форма рисунка")
                    IconChoiceGrid(CustomIconShape.entries.size) { index ->
                        IconChoiceTile(CustomIconShape.entries[index].title, customConfig.shape.ordinal == index,
                            onClick = { customIconShape = index }) {
                            Image(shapePreviews[index].asImageBitmap(), null, Modifier.size(36.dp))
                        }
                    }
                    Text("Здесь показан силуэт рисунка. Внешнюю форму ярлыка задаёт лаунчер — он может обрезать края.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (!customIconUseImported || customIconBase64.isNullOrBlank()) {
                        IconEditorHeading("Облако")
                        IconChoiceGrid(CustomCloudStyle.entries.size) { index ->
                            IconChoiceTile(CustomCloudStyle.entries[index].title, customConfig.cloudStyle.ordinal == index,
                                onClick = { customIconCloudStyle = index }) {
                                Image(cloudPreviews[index].asImageBitmap(), null, Modifier.size(48.dp))
                            }
                        }
                        IconColorRow("Цвет фона", CustomAppIconManager.backgroundPalette, customIconBackgroundColor,
                            onSelected = { customIconBackgroundColor = it }, onOpenPalette = { showBackgroundColorPicker = true })
                        if (customConfig.cloudStyle != CustomCloudStyle.ORIGINAL) {
                            IconColorRow("Цвет облака", CustomAppIconManager.cloudPalette, customIconCloudColor,
                                onSelected = { customIconCloudColor = it })
                        }
                    }
                    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !applyingShortcut && !importingImage,
                        onClick = {
                            val requestedConfig = customConfig
                            scope.launch {
                                applyingShortcut = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val bitmap = CustomAppIconManager.renderIcon(context, requestedConfig, 432)
                                        CustomAppIconManager.applyCustomLauncherIcon(context, bitmap)
                                    }
                                    shortcutPresent = CustomAppIconManager.isPinnedShortcutPresent(context)
                                    iconSyncMessage = when (result) {
                                        CustomLauncherIconResult.UPDATED -> "Отдельный ярлык Nimbo обновлён."
                                        CustomLauncherIconResult.REQUESTED -> "Запрос отправлен лаунчеру. Если появится системное окно, подтвердите добавление ярлыка."
                                        CustomLauncherIconResult.UNSUPPORTED -> "Лаунчер не разрешил добавить или обновить ярлык. Готовые иконки доступны в соседней вкладке."
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    iconSyncMessage = "Не удалось обновить ярлык. Попробуйте ещё раз."
                                } finally {
                                    applyingShortcut = false
                                }
                            }
                        }) {
                        Text(if (applyingShortcut) "Подготовка…" else if (shortcutPresent) "Обновить ярлык" else "Добавить ярлык на рабочий стол",
                            textAlign = TextAlign.Center)
                    }
                    iconSyncMessage?.let { message ->
                        Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    IconEditorToggle("Картинка в уведомлении", "Использовать рисунок из конструктора",
                        customNotificationIconEnabled, onCheckedChange = { customNotificationIconEnabled = it })
                    NotificationIconPreview(artwork, customNotificationIconEnabled)
                    Text("Меняется только большая картинка в поддерживаемых уведомлениях. Маленький значок в строке состояния остаётся системным монохромным.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }

    showIconConfirmDialog?.let { index ->
        NebulaMorphicDialog(
            onDismissRequest = { showIconConfirmDialog = null },
            title = "Иконка «${AppIconManager.iconTitleByIndex(index)}»",
            description = "Изменить основную иконку Nimbo? Лаунчер может обновить её с задержкой. Отдельный ярлык из конструктора сохранится.",
            confirmButtonText = "Применить",
            onConfirm = {
                showIconConfirmDialog = null
                AppIconManager.setAppIcon(context, index)
                selectedAppIcon = readLauncherPresetIndex(context, selectedAppIcon)
            }
        )
    }
    if (showBackgroundColorPicker) FullColorPickerDialog(
        initialColor = customIconBackgroundColor, accent = colors.primary,
        textPrimary = colors.onSurface, textSecondary = colors.onSurfaceVariant, surfaceColor = colors.surface,
        onDismiss = { showBackgroundColorPicker = false },
        onApply = { customIconBackgroundColor = it; showBackgroundColorPicker = false }
    )
}

// The shared getCurrentIconIndex returns -1 when a pinned custom shortcut exists.
// A shortcut can coexist with a launcher preset, so read the actual preset alias
// here without changing the shared manager's contract for other screens.
private fun readLauncherPresetIndex(context: android.content.Context, fallback: Int): Int {
    val manager = context.packageManager
    val index = AppIconManager.ICON_OPTIONS.indexOfFirst { option ->
        runCatching {
            val component = android.content.ComponentName(context, "${context.packageName}.${option.aliasSuffix}")
            val state = manager.getComponentEnabledSetting(component)
            state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    manager.getActivityInfo(component, 0).enabled)
        }.getOrDefault(false)
    }
    return if (index >= 0) index else fallback.coerceIn(AppIconManager.ICON_OPTIONS.indices)
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
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).imePadding(),
            color = surfaceColor.copy(alpha = 0.98f),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.42f))
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
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

                NimboToolActions {
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
                imageView.setImageResource(R.drawable.nimbo_cloud)
            }
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
