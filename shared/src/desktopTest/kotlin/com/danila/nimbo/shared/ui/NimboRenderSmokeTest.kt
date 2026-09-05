package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real shared UI rendering, not a separately drawn marketing mockup. */
class NimboRenderSmokeTest {
    @Test fun renderSmallLightSettings() {
        val output = File("build/reports/ui-beta5").apply { mkdirs() }
        for (style in listOf("glass", "material", "dotted", "manga")) {
            ImageComposeScene(320, 720) {
                NimboAppShell(
                    NimboScreen.SETTINGS,
                    NimboUiState(
                        appearance = NimboAppearance(themeMode = "light", textScale = 1.25f),
                        elementStyle = style, backgroundMotion = false
                    ),
                    NimboUiActions()
                )
            }.use { scene ->
                scene.render(0).close()
                scene.render(1_000_000_000L).use { image ->
                    val bytes = image.encodeToData()!!.use { it.bytes }
                    assertTrue(bytes.size > 5_000)
                    File(output, "$style-light-small.png").writeBytes(bytes)
                }
            }
        }
    }

    @Test fun renderMobileStyles() {
        val output = File("build/reports/ui-beta5").apply { mkdirs() }
        for (style in listOf("glass", "material", "dotted", "manga")) {
            for (screen in listOf(NimboScreen.HOME, NimboScreen.PROFILES, NimboScreen.SETTINGS)) {
                val state = NimboUiState(
                    appearance = NimboAppearance(themeMode = "dark"),
                    elementStyle = style,
                    backgroundMotion = false,
                    appVersion = "1.2.0 Beta 5",
                    activeProfileName = "Личная подписка",
                    activeServerName = "Нидерланды · основной",
                    serverCount = 2,
                    profileCount = 1,
                    servers = listOf(
                        NimboServerUi("1", "Нидерланды · основной", "vless", "xhttp", "reality", true, 42),
                        NimboServerUi("2", "Германия · резервный", "vless", "grpc", "tls", false, 58)
                    )
                )
                ImageComposeScene(390, 844) {
                    NimboAppShell(screen, state, NimboUiActions())
                }.use { scene ->
                    scene.render(0).close()
                    scene.render(1_000_000_000L).use { image ->
                        val bytes = image.encodeToData()!!.use { it.bytes }
                        assertTrue(bytes.size > 5_000)
                        File(output, "$style-${screen.name.lowercase()}.png").writeBytes(bytes)
                    }
                }
            }
        }
    }
}
