package com.danila.nimbo.utils

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class NimboBrandingTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
    private fun resource(path: String) = File(app, "src/main/res/$path")

    @Test fun generatedCacheMigratesOnceWithoutReplacingImportedArtwork() {
        assertTrue(NimboBranding.shouldRefreshGeneratedIcon(true, false, null))
        assertTrue(NimboBranding.shouldRefreshGeneratedIcon(true, false, "old"))
        assertFalse(NimboBranding.shouldRefreshGeneratedIcon(true, false, NimboBranding.REVISION))
        assertFalse(NimboBranding.shouldRefreshGeneratedIcon(true, true, "old"))
        assertFalse(NimboBranding.shouldRefreshGeneratedIcon(true, true, null))
        assertTrue(NimboBranding.shouldRefreshGeneratedIcon(false, true, "old"))
    }

    @Test fun vectorAndLegacyFallbackUseExactApprovedCloudPath() {
        val master = File(app.canonicalFile.parentFile, "assets/branding/1.3.0-beta.1/cloud.svg")
        val svg = master.readText()
        assertTrue(svg.contains("d=\"${NimboBranding.CLOUD_PATH}\""))
        for (name in listOf("nimbo_cloud", "nimbo_cloud_adaptive", "icon_notification", "icon_notification_nimbo_blue", "icon_quick_settings")) {
            val xml = resource("drawable/$name.xml").readText()
            assertTrue(name, xml.contains("android:pathData=\"${NimboBranding.CLOUD_PATH}\""))
            assertTrue(name, xml.contains("android:fillColor=\"#FFFFFFFF\""))
            assertEquals(name, 1, Regex("<path ").findAll(xml).count())
        }
    }

    @Test fun allAdaptiveAliasesHaveNewForegroundAndThemedSafeLayer() {
        val files = resource("mipmap-anydpi-v26").listFiles()!!.filter { it.name.startsWith("ic_launcher") || it.name.startsWith("ic_alias_") }
        assertTrue(files.size >= 26)
        files.forEach { file ->
            val xml = file.readText()
            assertTrue(file.name, xml.contains("<foreground android:drawable=\"@drawable/nimbo_cloud_adaptive"))
            assertTrue(file.name, xml.contains("<monochrome android:drawable=\"@drawable/nimbo_cloud_adaptive\""))
            assertFalse(xml.contains("badge"))
        }
        val xml = resource("drawable/nimbo_cloud_adaptive.xml").readText()
        assertTrue(xml.contains("android:width=\"108dp\""))
        assertTrue(xml.contains("android:scaleX=\"0.78\""))
        assertTrue(xml.contains("android:scaleY=\"0.78\""))
    }

    @Test fun launcherRasterIsOpaqueAndGlyphRasterHasTransparentCornersAtEveryDensity() {
        for ((density, size) in listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)) {
            val launcher = ImageIO.read(resource("mipmap-$density/ic_launcher.png"))
            assertEquals(size, launcher.width); assertEquals(size, launcher.height)
            assertFalse(launcher.colorModel.hasAlpha())
            val glyph = ImageIO.read(resource("mipmap-$density/ic_launcher_monochrome.png"))
            assertTrue(glyph.colorModel.hasAlpha())
            assertEquals(0, glyph.getRGB(0, 0).ushr(24))
            assertEquals(255, glyph.getRGB(glyph.width / 2, glyph.height / 2).ushr(24))
        }
    }

    @Test fun manifestAndStaticShortcutUseTheNewDefaultResource() {
        val manifest = File(app, "src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertFalse(manifest.contains("ic_launcher_nimbo_blue"))
        assertTrue(resource("xml/shortcuts.xml").readText().contains("@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("@drawable/icon_quick_settings"))
    }

    @Test fun defaultPaletteAndPresetUseMonochromeWithoutRemovingChoices() {
        assertEquals(NimboBranding.BACKGROUND, AppIconManager.ICON_OPTIONS.first().backgroundColor)
        assertEquals(NimboBranding.BACKGROUND, CustomAppIconManager.presets.first().config.backgroundColor)
        assertEquals(13, AppIconManager.ICON_OPTIONS.size)
        assertEquals("AliasDefault", AppIconManager.ICON_OPTIONS.first().aliasSuffix)
        assertEquals("nimbo_custom_icon", CustomAppIconManager.CUSTOM_SHORTCUT_ID)
    }

    @Test fun oldCustomStorageAndShortcutIdentityRemainStable() {
        val manager = File(app, "src/main/java/com/danila/nimbo/utils/CustomAppIconManager.kt").readText()
        assertTrue(manager.contains("custom_launcher_icon.png"))
        assertTrue(manager.contains("!preferences.customIconUseImported && isPinnedShortcutPresent(context)"))
        val migration = manager.substringAfter("fun ensureCustomIconFile(context: Context)").substringBefore("fun customLauncherIconFile")
        assertFalse(migration.contains("requestPinShortcut"))
        assertFalse(migration.contains("setAppIcon"))
        assertTrue(migration.contains("updateShortcuts"))
    }
}
