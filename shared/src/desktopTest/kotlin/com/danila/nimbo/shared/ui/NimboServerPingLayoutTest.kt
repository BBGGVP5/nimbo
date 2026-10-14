package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NimboServerPingLayoutTest {
    @Test fun latencyNeverOverlapsNameAtPhoneWidthsAndLargeText() {
        val output = File("build/reports/server-ping-layout").apply { mkdirs() }
        for (width in listOf(320, 390, 430)) for (scale in listOf(1f, 1.5f, 2f)) {
            for (display in listOf("numeric", "both", "bars", "dots")) {
                val server = NimboServerUi("fixture", "Нидерланды · 10 Гбит/с 🚄 · Shadowsocks ✈️ очень длинное имя",
                    "ss", description = "Shadowsocks ✈️ · 10 Гбит/с 🚄", ping = 9999)
                ImageComposeScene(width, 500) {
                    CompositionLocalProvider(LocalDensity provides Density(1f, scale),
                        LocalNimboAppearance provides NimboAppearance(themeMode = "dark", textScale = scale),
                        LocalNimboDark provides true, LocalNimboPingDisplay provides display) {
                            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                                ProfileServerCard(server, false, {}, {}, {})
                            }
                    }
                }.use { scene ->
                    repeat(3) { scene.render(it * 1_000_000_000L).close() }
                    val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                    fun tagged(tag: String) = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                    val name = tagged("server-name-fixture")
                    val badge = tagged("server-latency-fixture")
                    val favorite = tagged("server-favorite-fixture")
                    val n = name.boundsInRoot; val p = badge.boundsInRoot; val f = favorite.boundsInRoot
                    assertTrue(n.bottom <= p.top, "Name/ping overlap at $width/$scale/$display: $n/$p")
                    assertTrue(n.right <= f.left, "Name/favourite overlap at $width/$scale/$display")
                    assertTrue(p.left >= 16 && p.right <= width - 16, "Badge outside card: $p")
                    assertTrue(f.width >= 44 && f.height >= 44, "Favourite target was shrunk")
                    val layouts = mutableListOf<TextLayoutResult>()
                    assertTrue(name.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
                    val layout = layouts.single()
                    assertTrue(layout.lineCount <= 2 && layout.size.width <= n.width + 1)
                    val png = scene.render(4_000_000_000L).use { frame -> frame.encodeToData()!!.use { it.bytes } }
                    File(output, "$width-$scale-$display.png").writeBytes(png)
                }
            }
        }
    }
}
