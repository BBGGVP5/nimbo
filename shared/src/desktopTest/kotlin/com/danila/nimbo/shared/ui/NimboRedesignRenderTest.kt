package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders production Compose components with explicit test fixtures, not the HTML prototype. */
class NimboRedesignRenderTest {
    @Test fun renderConnectionStatesAndNativeNavigationHost() {
        val output = File("build/reports/ui-1.3.0-redesign").apply { mkdirs() }
        for (status in listOf("idle", "connecting", "connected", "failed")) {
            for ((width, height) in listOf(320 to 480, 390 to 844, 1024 to 768)) {
                val state = NimboUiState(
                    vpnState = status,
                    errorMessage = if (status == "failed") "Сервер не ответил. Проверьте сеть или выберите другой сервер." else null,
                    appearance = NimboAppearance(themeMode = "dark", textScale = if (width == 320) 1.25f else 1f),
                    activeProfileName = "Личная подписка", profileCount = 1, serverCount = 1,
                    profileTrafficLabel = "18 / 100 ГБ", profileExpiryLabel = "30 сентября", profileUpdatedLabel = "09:40",
                    activeServerId = "one", activeServerName = "Амстердам",
                    servers = listOf(NimboServerUi("one", "Амстердам", "vless", ping = 84)),
                    downloadSpeed = 12_000_000, uploadSpeed = 1_000_000,
                    speedSamples = listOf(NimboSpeedSample(500_000, 5_000_000), NimboSpeedSample(1_000_000, 12_000_000)),
                    memoryMb = 86, memorySamples = listOf(72, 80, 86)
                )
                ImageComposeScene(width, height) {
                    // Native iOS reserves its own tab panel. This host must not reserve it twice.
                    NimboAppShell(NimboScreen.HOME, state, NimboUiActions(), showBottomBar = false)
                }.use { scene ->
                    scene.render(0).close()
                    scene.render(1_000_000_000L).use { image ->
                        val bytes = image.encodeToData()!!.use { it.bytes }
                        assertTrue(bytes.size > 3000)
                        File(output, "$width-native-$status.png").writeBytes(bytes)
                    }
                }
            }
        }
    }

    @Test fun renderProductionPages() {
        val output = File("build/reports/ui-1.3.0-redesign").apply { mkdirs() }
        for ((width, height) in listOf(320 to 720, 390 to 844, 768 to 1024)) {
            for (theme in listOf("dark", "light")) {
                for (screen in NimboScreen.entries) {
                    val state = NimboUiState(
                        appearance = NimboAppearance(themeMode = theme, textScale = if (width == 320) 1.25f else 1f),
                        activeProfileName = "Личная подписка", profileCount = 1, serverCount = 2,
                        activeServerId = "one", activeServerName = "Амстердам",
                        servers = listOf(NimboServerUi("one", "Амстердам · основной", "vless", "xhttp", "reality", true, 84),
                            NimboServerUi("two", "Франкфурт · резервный сервер с длинным названием", "trojan", ping = -1)),
                        profileTrafficLabel = "18 / 100 ГБ", profileExpiryLabel = "30 сентября",
                        profileAnnounce = "Описание провайдера видно только в информации о подписке."
                    )
                    ImageComposeScene(width, height) { NimboAppShell(screen, state, NimboUiActions()) }.use { scene ->
                        scene.render(0).close()
                        scene.render(1_000_000_000L).use { image ->
                            val bytes = image.encodeToData()!!.use { it.bytes }
                            assertTrue(bytes.size > 3000)
                            File(output, "$width-$theme-${screen.wireName}.png").writeBytes(bytes)
                        }
                    }
                }
            }
        }
    }
}
