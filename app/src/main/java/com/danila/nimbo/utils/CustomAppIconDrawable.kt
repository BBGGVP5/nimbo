package com.danila.nimbo.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Environment
import java.io.File

/**
 * Legacy drawable kept for installs that already enabled one of the custom
 * aliases. New constructor applications use a pinned bitmap shortcut instead:
 * a launcher process cannot reliably read an app-private PNG through a runtime
 * Drawable, while ShortcutManager is explicitly designed for this use case.
 */
class CustomAppIconDrawable : Drawable() {

    companion object {
        private const val FILE_NAME = "custom_launcher_icon.png"
        private const val PACKAGE_NAME = "com.danila.nimbo"

        @Volatile
        private var latestBitmap: Bitmap? = null

        fun remember(bitmap: Bitmap) {
            latestBitmap = bitmap
        }

        fun forget() {
            latestBitmap = null
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val bitmap = latestBitmap ?: readBitmap() ?: return
        val bounds = bounds
        if (bounds.isEmpty) return
        val scale = maxOf(
            bounds.width() / bitmap.width.toFloat(),
            bounds.height() / bitmap.height.toFloat()
        )
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = bounds.left + (bounds.width() - width) / 2f
        val top = bounds.top + (bounds.height() - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
    }

    private fun readBitmap(): Bitmap? {
        val file = iconFile()
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun iconFile(): File {
        val dataDir = Environment.getDataDirectory().absolutePath
        return File("$dataDir/user/0/$PACKAGE_NAME/files", FILE_NAME)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = 432

    override fun getIntrinsicHeight(): Int = 432
}
