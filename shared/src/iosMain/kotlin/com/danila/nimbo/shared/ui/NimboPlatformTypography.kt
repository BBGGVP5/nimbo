@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.danila.nimbo.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager

/** Uses the app's bundled full TTFs, also registered with CoreText for SwiftUI.
 * The manual XCFramework build does not run Compose's Xcode resource-copy task. */
@Composable
internal actual fun nimboPlatformFontFamily(heading: Boolean): FontFamily = remember(heading) {
    val family = if (heading) "manrope" else "golos"
    val weights = listOf("regular" to FontWeight.Normal, "medium" to FontWeight.Medium,
        "semibold" to FontWeight.SemiBold, "bold" to FontWeight.Bold) +
        if (heading) listOf("extrabold" to FontWeight.ExtraBold) else emptyList()
    FontFamily(weights.map { (name, weight) ->
        val file = "${family}_$name"
        Font(identity = file, weight = weight, getData = {
            val bundle = NSBundle.mainBundle
            val path = bundle.pathForResource(file, "ttf")
                ?: bundle.pathForResource(file, "ttf", "Fonts")
                ?: error("Missing bundled Nimbo font: $file")
            val data = NSFileManager.defaultManager.contentsAtPath(path)
                ?: error("Unreadable Nimbo font: $file")
            data.bytes!!.reinterpret<ByteVar>().readBytes(data.length.toInt())
        })
    })
}
