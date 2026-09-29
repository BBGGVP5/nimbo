package com.danila.nimbo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.danila.nimbo.R
import org.junit.Assert.*
import org.junit.Test

class NimboBrandingRenderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun systemGlyphsHaveTransparentBackgroundAndWhiteCloud() {
        for (id in listOf(R.drawable.icon_quick_settings, R.drawable.icon_notification_nimbo_blue, R.drawable.nimbo_cloud)) {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val drawable = requireNotNull(ContextCompat.getDrawable(context, id))
            drawable.setBounds(0, 0, 128, 128)
            drawable.draw(Canvas(bitmap))
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(Color.WHITE, bitmap.getPixel(64, 64))
        }
    }

    @Test fun adaptiveGlyphFits66dpCircularSafeZone() {
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        val drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.nimbo_cloud_adaptive))
        drawable.setBounds(0, 0, 432, 432); drawable.draw(Canvas(bitmap))
        for (y in 0 until 432) for (x in 0 until 432) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                assertTrue((x - 216) * (x - 216) + (y - 216) * (y - 216) <= 132 * 132)
            }
        }
    }

    @Test fun constructorPreservesAnImportedImageAndUsesNewCloudForGeneratedArtwork() {
        val generated = CustomAppIconManager.renderIcon(context, CustomAppIconManager.presets.first().config, 256)
        assertEquals(Color.WHITE, generated.getPixel(128, 128))
        val custom = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val bytes = java.io.ByteArrayOutputStream().also { custom.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val config = CustomAppIconManager.presets.first().config.copy(useImportedImage = true,
            importedImageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        assertEquals(Color.MAGENTA, CustomAppIconManager.renderIcon(context, config, 256).getPixel(128, 128))
    }

    @Test fun legacyDrawableHonorsRememberedUserBitmap() {
        val custom = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            CustomAppIconDrawable.remember(custom)
            val output = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            CustomAppIconDrawable().apply { setBounds(0, 0, 64, 64); draw(Canvas(output)) }
            assertEquals(Color.MAGENTA, output.getPixel(32, 32))
        } finally { CustomAppIconDrawable.forget() }
    }
}
