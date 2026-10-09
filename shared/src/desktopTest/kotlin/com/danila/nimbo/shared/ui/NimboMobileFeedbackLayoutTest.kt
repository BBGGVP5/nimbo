package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.use
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Production Compose pages only; native SwiftUI navigation is checked on iOS separately. */
class NimboMobileFeedbackLayoutTest {
    @Test fun sectionBaselineAndSettingsStayAlignedAtPhoneWidths() {
        val output = File("build/reports/mobile-feedback").apply { mkdirs() }
        for (width in listOf(390, 430)) for (theme in listOf("light", "dark")) {
            val state = NimboUiState(
                appearance = NimboAppearance(themeMode = theme, textScale = 1.25f),
                vpnState = "connected", profileCount = 1, serverCount = 2,
                activeProfileName = "Моя подписка", activeServerName = "Финляндия",
                activeServerId = "demo", nativeBottomClearance = 100f,
                servers = listOf(NimboServerUi("demo", "Финляндия", "naive", selected = true)),
                profileAnnounce = "Демонстрационный профиль", navIconMotion = false,
                showMemoryWidget = false, showSpeedWidget = false
            )
            for (page in listOf(NimboScreen.HOME, NimboScreen.SETTINGS)) {
                ImageComposeScene(width, 932) {
                    NimboAppShell(page, state, NimboUiActions(onOpenCoreSettings = {}, onOpenOnDemandSettings = {}), showBottomBar = false)
                }.use { scene ->
                    repeat(4) { scene.render(it * 1_000_000_000L).close() }
                    val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                    if (page == NimboScreen.HOME) {
                        val baselines = listOf("Мои подписки", "Все профили ↗").map { label ->
                            val node = nodes.single { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
                            val layouts = mutableListOf<TextLayoutResult>()
                            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
                            assertTrue(node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= width)
                            val layout = layouts.single()
                            // Skia reports fractional advance overflow after rounding to integer pixels.
                            assertTrue(layout.lineCount == 1 && !layout.isLineEllipsized(0) &&
                                layout.getLineRight(0) <= layout.size.width + 1f &&
                                layout.getLineBottom(0) <= layout.size.height + 1f,
                                "$label clipped at $width/$theme: ${layout.size}")
                            node.boundsInRoot.top + layouts.single().firstBaseline
                        }
                        assertTrue(abs(baselines[0] - baselines[1]) <= 1f, "Section baselines differ: $baselines")
                    } else {
                        val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                        assertTrue("Автоподключение" in texts)
                        assertTrue("Настройки" in texts)
                    }
                    val bytes = scene.render(4_000_000_000L).use { frame -> frame.encodeToData()!!.use { it.bytes } }
                    File(output, "$theme-$width-${page.wireName}.png").writeBytes(bytes)
                }
            }
        }
    }
}
