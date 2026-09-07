package com.danila.nimbo.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Base64
import com.danila.nimbo.MainActivity
import com.danila.nimbo.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class CustomIconShape(val title: String) {
    SQUIRCLE("Сквиркл"),
    ROUNDED("Скруглённый квадрат"),
    CIRCLE("Круг"),
    CLOVER("Клевер"),
    FLOWER("Цветок"),
    ARCH("Арка");

    companion object {
        fun fromIndex(index: Int): CustomIconShape = entries.getOrElse(index) { SQUIRCLE }
    }
}

enum class CustomCloudStyle(val title: String) {
    ORIGINAL("Объёмное"),
    SOLID("Одноцветное"),
    OUTLINE("Контурное");

    companion object {
        fun fromIndex(index: Int): CustomCloudStyle = entries.getOrElse(index) { ORIGINAL }
    }
}

data class CustomAppIconConfig(
    val shape: CustomIconShape,
    val backgroundColor: Int,
    val cloudColor: Int,
    val cloudStyle: CustomCloudStyle,
    val useImportedImage: Boolean,
    val importedImageBase64: String?
)

data class CustomAppIconPreset(
    val title: String,
    val config: CustomAppIconConfig
)

enum class CustomLauncherIconResult {
    UPDATED,
    REQUESTED,
    UNSUPPORTED
}

object CustomAppIconManager {
    @Volatile
    private var cachedNotificationSignature: Int? = null

    @Volatile
    private var cachedNotificationBitmap: Bitmap? = null

    private const val CUSTOM_LAUNCHER_ICON_FILE = "custom_launcher_icon.png"
    const val CUSTOM_SHORTCUT_ID = "nimbo_custom_icon"

    val backgroundPalette = listOf(
        0xFF1769E0.toInt(),
        0xFF0C1738.toInt(),
        0xFF6A4CFF.toInt(),
        0xFF008D78.toInt(),
        0xFFFF6B35.toInt(),
        0xFFF2F5FC.toInt()
    )

    val cloudPalette = listOf(
        0xFFF4F7FF.toInt(),
        0xFF9ED1FF.toInt(),
        0xFF78F0D0.toInt(),
        0xFFFFD166.toInt(),
        0xFFFF8EA1.toInt(),
        0xFF151A2F.toInt()
    )

    val presets = listOf(
        CustomAppIconPreset(
            title = "Nimbo",
            config = CustomAppIconConfig(
                CustomIconShape.SQUIRCLE,
                0xFF1769E0.toInt(),
                0xFFF4F7FF.toInt(),
                CustomCloudStyle.ORIGINAL,
                false,
                null
            )
        ),
        CustomAppIconPreset(
            title = "Полночь",
            config = CustomAppIconConfig(
                CustomIconShape.ROUNDED,
                0xFF0C1738.toInt(),
                0xFFF4F7FF.toInt(),
                CustomCloudStyle.SOLID,
                false,
                null
            )
        ),
        CustomAppIconPreset(
            title = "Аврора",
            config = CustomAppIconConfig(
                CustomIconShape.SQUIRCLE,
                0xFF6A4CFF.toInt(),
                0xFF9ED1FF.toInt(),
                CustomCloudStyle.SOLID,
                false,
                null
            )
        ),
        CustomAppIconPreset(
            title = "Мята",
            config = CustomAppIconConfig(
                CustomIconShape.CIRCLE,
                0xFF008D78.toInt(),
                0xFF78F0D0.toInt(),
                CustomCloudStyle.SOLID,
                false,
                null
            )
        ),
        CustomAppIconPreset(
            title = "Закат",
            config = CustomAppIconConfig(
                CustomIconShape.ROUNDED,
                0xFFFF6B35.toInt(),
                0xFFFFD166.toInt(),
                CustomCloudStyle.OUTLINE,
                false,
                null
            )
        ),
        CustomAppIconPreset(
            title = "Жемчуг",
            config = CustomAppIconConfig(
                CustomIconShape.SQUIRCLE,
                0xFFF2F5FC.toInt(),
                0xFF151A2F.toInt(),
                CustomCloudStyle.OUTLINE,
                false,
                null
            )
        )
    )

    fun config(preferences: PreferencesManager): CustomAppIconConfig = CustomAppIconConfig(
        shape = CustomIconShape.fromIndex(preferences.customIconShape),
        backgroundColor = preferences.customIconBackgroundColor,
        cloudColor = preferences.customIconCloudColor,
        cloudStyle = CustomCloudStyle.fromIndex(preferences.customIconCloudStyle),
        useImportedImage = preferences.customIconUseImported,
        importedImageBase64 = preferences.customAppIconBase64
    )

    fun renderIcon(
        context: Context,
        config: CustomAppIconConfig,
        size: Int = 256
    ): Bitmap {
        val safeSize = size.coerceIn(64, 1024)
        val result = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val iconPath = shapePath(config.shape, safeSize.toFloat())
        canvas.save()
        canvas.clipPath(iconPath)

        val imported = if (config.useImportedImage) decodeBase64(config.importedImageBase64) else null
        if (imported != null) {
            drawCenterCrop(canvas, imported, safeSize)
            canvas.restore()
            return result
        }

        canvas.drawColor(config.backgroundColor)
        val cloudRes = if (config.cloudStyle == CustomCloudStyle.ORIGINAL) {
            R.mipmap.ic_launcher_foreground
        } else {
            R.mipmap.ic_launcher_monochrome
        }
        val cloud = BitmapFactory.decodeResource(context.resources, cloudRes)
        if (cloud != null) {
            val inset = (safeSize * 0.13f).toInt()
            val destination = Rect(inset, inset, safeSize - inset, safeSize - inset)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (config.cloudStyle != CustomCloudStyle.ORIGINAL) {
                paint.colorFilter = PorterDuffColorFilter(config.cloudColor, PorterDuff.Mode.SRC_IN)
            }
            if (config.cloudStyle == CustomCloudStyle.OUTLINE) {
                // A soft larger silhouette makes the edge readable while the centre
                // remains translucent, which feels closer to an outlined cloud.
                val outlinePaint = Paint(paint).apply { alpha = 255 }
                val outlineInset = (safeSize * 0.095f).toInt()
                canvas.drawBitmap(
                    cloud,
                    null,
                    Rect(outlineInset, outlineInset, safeSize - outlineInset, safeSize - outlineInset),
                    outlinePaint
                )
                paint.colorFilter = PorterDuffColorFilter(config.backgroundColor, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(cloud, null, destination, paint)
        }
        canvas.restore()
        return result
    }

    /**
     * Uses the exact same path as the generated shortcut and notification icon.
     * This keeps the Android-style picker honest instead of showing approximate
     * Compose shapes that differ from the exported bitmap.
     */
    fun renderShapePreview(
        shape: CustomIconShape,
        color: Int = 0xFFF4F7FF.toInt(),
        size: Int = 128
    ): Bitmap {
        val safeSize = size.coerceIn(48, 512)
        return Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawPath(
                shapePath(shape, safeSize.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            )
        }
    }

    /**
     * Renders an icon through Android's adaptive-icon pipeline instead of
     * drawing an approximate rounded square in Compose.  The framework mask is
     * the same base mask that is used for [Icon.createWithAdaptiveBitmap], so
     * the picker shows the safe area and crop a launcher will use.
     *
     * A launcher is still free to apply its own final shape (circle, squircle,
     * rounded square, etc.). Android intentionally does not expose an API for
     * applications to replace that system-wide shape.
     */
    fun renderSystemMaskedPreview(
        context: Context,
        config: CustomAppIconConfig,
        size: Int = 256
    ): Bitmap = renderSystemMaskedPreview(
        context = context,
        artwork = renderIcon(context, config, size),
        size = size
    )

    fun renderSystemMaskedPreview(
        context: Context,
        artwork: Bitmap,
        size: Int = artwork.width
    ): Bitmap {
        val safeSize = size.coerceIn(64, 1024)
        val preview = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val adaptiveIcon = Icon.createWithAdaptiveBitmap(artwork).loadDrawable(context)
        adaptiveIcon?.let { drawable ->
            drawable.setBounds(0, 0, safeSize, safeSize)
            drawable.draw(Canvas(preview))
        } ?: run {
            // Defensive fallback for a broken/very unusual launcher image
            // implementation: the picker must still show the composed artwork.
            Canvas(preview).drawBitmap(
                artwork,
                null,
                Rect(0, 0, safeSize, safeSize),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }
        return preview
    }

    fun notificationLargeIcon(
        context: Context,
        preferences: PreferencesManager = PreferencesManager(context)
    ): Bitmap {
        val iconConfig = config(preferences)
        val signature = if (preferences.customNotificationIconEnabled) {
            iconConfig.hashCode()
        } else {
            Int.MIN_VALUE
        }
        cachedNotificationBitmap?.takeIf { cachedNotificationSignature == signature }?.let { return it }

        return synchronized(this) {
            cachedNotificationBitmap?.takeIf { cachedNotificationSignature == signature } ?: run {
                val bitmap = if (preferences.customNotificationIconEnabled) {
                    renderIcon(context, iconConfig, 192)
                } else {
                    val source = BitmapFactory.decodeResource(context.resources, R.drawable.nimbo_beta_notification)
                    Bitmap.createScaledBitmap(source, 192, 192, true)
                }
                cachedNotificationSignature = signature
                cachedNotificationBitmap = bitmap
                bitmap
            }
        }
    }

    private fun buildPinnedShortcut(context: Context, bitmap: Bitmap): ShortcutInfo {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfo.Builder(context, CUSTOM_SHORTCUT_ID)
            .setShortLabel("Nimbo")
            .setLongLabel("Nimbo — своя иконка")
            // A regular bitmap is shown as a plain square by many launchers.
            // Adaptive bitmap lets the current launcher apply its own real
            // icon mask, exactly like it does for the built-in aliases.
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(launchIntent)
            .build()
    }

    fun isPinnedShortcutPresent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        return manager.pinnedShortcuts.any { it.id == CUSTOM_SHORTCUT_ID }
    }

    private fun updateOrRequestPinnedShortcut(context: Context, bitmap: Bitmap): CustomLauncherIconResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CustomLauncherIconResult.UNSUPPORTED
        val manager = context.getSystemService(ShortcutManager::class.java)
            ?: return CustomLauncherIconResult.UNSUPPORTED
        if (isPinnedShortcutPresent(context)) {
            return if (manager.updateShortcuts(listOf(buildPinnedShortcut(context, bitmap)))) {
                CustomLauncherIconResult.UPDATED
            } else {
                CustomLauncherIconResult.UNSUPPORTED
            }
        }
        if (!manager.isRequestPinShortcutSupported) return CustomLauncherIconResult.UNSUPPORTED
        return if (manager.requestPinShortcut(buildPinnedShortcut(context, bitmap), null)) {
            CustomLauncherIconResult.REQUESTED
        } else {
            CustomLauncherIconResult.UNSUPPORTED
        }
    }

    /**
     * Сохраняет собранную иконку для уведомлений и создаёт/обновляет один
     * стабильный системный ярлык Nimbo. Произвольную bitmap-иконку нельзя
     * надёжно подменить у PackageManager-alias после установки приложения;
     * ShortcutManager — поддерживаемый Android способ для рабочего стола.
     */
    fun applyCustomLauncherIcon(context: Context, bitmap: Bitmap): CustomLauncherIconResult {
        runCatching {
            customLauncherIconFile(context).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }.onFailure { return CustomLauncherIconResult.UNSUPPORTED }
        CustomAppIconDrawable.remember(bitmap)
        val result = updateOrRequestPinnedShortcut(context, bitmap)
        if (result != CustomLauncherIconResult.UNSUPPORTED) {
            // The arbitrary bitmap is carried by the system shortcut. Keep the
            // normal app icon alias active so a launcher never tries to read a
            // private file through a custom Drawable from another process.
            val preferences = PreferencesManager(context)
            AppIconManager.setAppIcon(context, preferences.selectedAppIcon)
        }
        return result
    }

    /**
     * Гарантирует наличие PNG иконки в app storage ещё до первого применения,
     * чтобы CustomAppIconDrawable никогда не остался без изображения.
     */
    fun ensureCustomIconFile(context: Context) {
        val file = customLauncherIconFile(context)
        if (file.exists()) {
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.let { CustomAppIconDrawable.remember(it) }
            }
            return
        }
        runCatching {
            val bitmap = renderIcon(context, config(PreferencesManager(context)), 432)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            CustomAppIconDrawable.remember(bitmap)
        }
    }

    fun customLauncherIconFile(context: Context): java.io.File =
        java.io.File(context.filesDir, CUSTOM_LAUNCHER_ICON_FILE)

    private fun decodeBase64(value: String?): Bitmap? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(value, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, size: Int) {
        val scale = max(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (size - width) / 2f
        val top = (size - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun shapePath(shape: CustomIconShape, size: Float): Path = Path().apply {
        val edge = size * 0.025f
        val bounds = RectF(edge, edge, size - edge, size - edge)
        when (shape) {
            CustomIconShape.CIRCLE -> addOval(bounds, Path.Direction.CW)
            CustomIconShape.ROUNDED -> addRoundRect(bounds, size * 0.18f, size * 0.18f, Path.Direction.CW)
            CustomIconShape.SQUIRCLE -> addRoundRect(bounds, size * 0.29f, size * 0.29f, Path.Direction.CW)
            CustomIconShape.CLOVER -> addClover(size, edge)
            CustomIconShape.FLOWER -> addFlower(size)
            CustomIconShape.ARCH -> addArch(size, edge)
        }
    }

    private fun Path.addClover(size: Float, edge: Float) {
        val far = size - edge
        val mid = size / 2f
        moveTo(mid, edge)
        cubicTo(size * 0.72f, edge, far, size * 0.14f, far, size * 0.33f)
        cubicTo(far, size * 0.43f, size * 0.92f, size * 0.48f, size * 0.88f, mid)
        cubicTo(size * 0.92f, size * 0.52f, far, size * 0.57f, far, size * 0.67f)
        cubicTo(far, size * 0.86f, size * 0.72f, far, mid, far)
        cubicTo(size * 0.28f, far, edge, size * 0.86f, edge, size * 0.67f)
        cubicTo(edge, size * 0.57f, size * 0.08f, size * 0.52f, size * 0.12f, mid)
        cubicTo(size * 0.08f, size * 0.48f, edge, size * 0.43f, edge, size * 0.33f)
        cubicTo(edge, size * 0.14f, size * 0.28f, edge, mid, edge)
        close()
    }

    private fun Path.addFlower(size: Float) {
        val centre = size / 2f
        val pointCount = 16
        val points = List(pointCount) { index ->
            val angle = -PI / 2.0 + index * (2.0 * PI / pointCount)
            val radius = size * if (index % 2 == 0) 0.475f else 0.405f
            Pair(
                centre + (cos(angle) * radius).toFloat(),
                centre + (sin(angle) * radius).toFloat()
            )
        }
        val first = points.first()
        val last = points.last()
        moveTo((last.first + first.first) / 2f, (last.second + first.second) / 2f)
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % points.size]
            quadTo(
                point.first,
                point.second,
                (point.first + next.first) / 2f,
                (point.second + next.second) / 2f
            )
        }
        close()
    }

    private fun Path.addArch(size: Float, edge: Float) {
        val far = size - edge
        moveTo(size * 0.13f, far)
        lineTo(size * 0.13f, size * 0.43f)
        cubicTo(size * 0.13f, size * 0.18f, size * 0.29f, edge, size * 0.50f, edge)
        cubicTo(size * 0.71f, edge, size * 0.87f, size * 0.18f, size * 0.87f, size * 0.43f)
        lineTo(size * 0.87f, far)
        close()
    }
}
