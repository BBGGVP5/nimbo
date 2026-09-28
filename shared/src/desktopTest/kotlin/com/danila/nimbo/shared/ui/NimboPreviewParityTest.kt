package com.danila.nimbo.shared.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Production rendering; fixture values are deliberately confined to this test. */
class NimboPreviewParityTest {
    private val fixture = NimboUiState(
        vpnState = "connected", appearance = NimboAppearance(themeMode = "dark"),
        profileCount = 1, serverCount = 2, activeProfileName = "NebulaGuard",
        activeServerName = "Амстердам", activeServerId = "nl", connectionDuration = "00:24:18",
        servers = listOf(NimboServerUi("nl", "Амстердам", "vless", security = "reality", selected = true, ping = 106),
            NimboServerUi("de", "Франкфурт — резервный сервер", "trojan", ping = 80)),
        profileTrafficUsed = 118, profileTrafficTotal = 200, profileTrafficLabel = "118 / 200 ГБ",
        profileExpiryLabel = "До 30 сентября", profileUpdatedLabel = "09:40",
        profileAnnounce = "Описание провайдера доступно только в информации о подписке.",
        supportUrl = "https://provider.example/support", websiteUrl = "https://provider.example",
        downloadSpeed = 12000000, uploadSpeed = 1000000,
        speedSamples = listOf(NimboSpeedSample(10000, 1000000), NimboSpeedSample(1000000, 12000000))
    )
    private val output = File("build/reports/preview-parity-2026-09-23/shared")
    private fun ImageComposeScene.settle() { repeat(3) { render(it * 1_000_000_000L).close() } }
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
    private fun ImageComposeScene.png(): ByteArray = render(4_000_000_000L).use { frame -> frame.encodeToData()!!.use { it.bytes } }
    private fun save(name: String, bytes: ByteArray) { output.mkdirs(); File(output, "$name.png").writeBytes(bytes) }

    @Test fun retiredPreferencesDoNotChangePixelsButCompactControlDoes() {
        fun capture(state: NimboUiState): ByteArray = ImageComposeScene(390, 844) {
            NimboAppShell(NimboScreen.HOME, state, NimboUiActions(), showBottomBar = false)
        }.use { scene -> scene.settle(); scene.png() }
        val baseline = capture(fixture)
        save("390-home-universal", baseline)
        for (style in listOf("material", "dotted", "signal", "manga")) {
            val restored = capture(fixture.copy(elementStyle = style,
                backgroundStyle = 18, backgroundMotion = true, statusParticles = true,
                appearance = fixture.appearance.copy(brightness = 2f, transparency = 1f, corners = .25f)))
            assertTrue(baseline.contentEquals(restored), "Old $style preferences changed the universal layout")
        }
        val compact = capture(fixture.copy(connectStyle = "compact"))
        save("390-home-compact", compact)
        assertTrue(!baseline.contentEquals(compact), "Compact control must change the home layout")
    }

    @Test fun renderRealPhoneTabletAndSystemLargeTextPages() {
        for ((width, height, scale) in listOf(Triple(390, 844, 1f), Triple(320, 640, 2f), Triple(768, 1024, 1f), Triple(1280, 800, 1f))) {
            for (theme in listOf("light", "dark")) {
                for (page in listOf(NimboScreen.HOME, NimboScreen.PROFILES, NimboScreen.SETTINGS, NimboScreen.STATS)) {
                    ImageComposeScene(width, height) {
                        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                            NimboAppShell(page, fixture.copy(appearance = fixture.appearance.copy(themeMode = theme)),
                                NimboUiActions(), showBottomBar = false)
                        }
                    }.use { scene ->
                        scene.settle()
                        save("$width-$theme-text$scale-${page.wireName}", scene.png())
                        assertTrue(scene.nodes().isNotEmpty())
                    }
                }
            }
        }
    }

    @Test fun providerSheetUsesRealFactsAndKeepsCloseOutsideScrollingContent() {
        for ((width, height, scale) in listOf(Triple(390, 844, 1f), Triple(320, 480, 2f))) {
            ImageComposeScene(width, height) {
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    NimboAppShell(NimboScreen.PROFILES, fixture, NimboUiActions(), showBottomBar = false)
                }
            }.use { scene ->
                scene.settle()
                scene.nodes().first { "Информация" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                    .config[SemanticsActions.OnClick].action!!.invoke()
                scene.settle()
                val close = scene.nodes().single {
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Закрыть" } &&
                        it.config.getOrNull(SemanticsActions.OnClick) != null
                }.boundsInRoot
                assertTrue(close.height >= 44 && close.top >= 0 && close.bottom <= height, "Close clipped: $close")
                assertEquals(.41f, fixture.remainingQuotaFraction)
                save("$width-text$scale-provider-info", scene.png())
            }
        }
    }

    @Test fun renderWideDesktopNavigationPreview() {
        ImageComposeScene(1280, 800) {
            NimboAppShell(NimboScreen.HOME, fixture, NimboUiActions(), showBottomBar = true)
        }.use { scene ->
            scene.settle()
            save("1280-desktop-home-with-rail", scene.png())
            assertTrue(scene.nodes().any {
                "Навигация: Профили" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            })
        }
    }

    @Test fun largeTextConnectionHeadingsNeverSplitInsideWords() {
        for (status in listOf("connected", "connecting", "disconnecting", "failed", "disconnected")) {
            val state = fixture.copy(vpnState = status)
            ImageComposeScene(320, 640) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    NimboAppShell(NimboScreen.HOME, state, NimboUiActions(), showBottomBar = false)
                }
            }.use { scene ->
                scene.settle()
                val node = scene.nodes().single {
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == state.connectionTitle }
                }
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
                val layout = results.single()
                assertTrue(!layout.hasVisualOverflow, "Clipped $status heading")
                for (line in 1 until layout.lineCount) {
                    val start = layout.getLineStart(line)
                    assertTrue(start > 0 && (state.connectionTitle[start - 1].isWhitespace() || state.connectionTitle[start].isWhitespace()),
                        "Heading $status wraps inside a word at $start")
                }
            }
        }
    }
}
