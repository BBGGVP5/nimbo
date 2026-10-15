package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** iOS production Compose page previews, not UIKit/simulator screenshots. No native or network calls. */
class NimboReleasePreviewTest {
    @Test fun capturePublicDemoPages() {
        val state = NimboUiState(
            appearance = NimboAppearance(themeMode = "dark"), vpnState = "idle",
            profileCount = 1, serverCount = 3, activeProfileName = "Моя подписка",
            activeServerName = "Финляндия", activeServerId = "demo-fi",
            servers = listOf(
                NimboServerUi("demo-fi", "Финляндия", "vless", security = "reality", selected = true, ping = 79),
                NimboServerUi("demo-de", "Германия", "trojan", ping = 106),
                NimboServerUi("demo-nl", "Нидерланды", "vless", ping = 92)
            ),
            profileTrafficUsed = 12, profileTrafficTotal = 100, profileTrafficLabel = "12 / 100 ГБ",
            profileExpiryLabel = "Бессрочно", profileUpdatedLabel = "12:00",
            profileAnnounce = "🇫🇮 Финляндия · 🇩🇪 Германия · 🇳🇱 Нидерланды\nДоступные локации и обновления профиля.",
            pings = mapOf("demo-fi" to 79, "demo-de" to 106, "demo-nl" to 92),
            navIconMotion = false, showSpeedWidget = false, showMemoryWidget = false,
            pingOnLaunch = false, pingAfterRefresh = false
        )
        val output = File("build/reports/release-previews")
        output.mkdirs()
        for ((screen, heading) in listOf(NimboScreen.HOME to "Мои подписки", NimboScreen.SETTINGS to "Настройки")) {
            ImageComposeScene(390, 844) {
                // RootView embeds these same pages without Compose's bottom bar.
                NimboAppShell(screen, state, NimboUiActions(onOpenCoreSettings = {}), showBottomBar = false)
            }.use { scene ->
                repeat(4) { scene.render(it * 1_000_000_000L).close() }
                val text = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString("\n") { it.text }
                assertTrue(heading in text, "Missing page content: $heading")
                assertTrue("@bbgg" !in text && "169481" !in text && "https://" !in text, "Private/network data in preview")
                val bytes = scene.render(4_000_000_000L).use { frame -> frame.encodeToData()!!.use { it.bytes } }
                assertTrue(bytes.take(4) == listOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte()))
                File(output, "ios-compose-${screen.wireName}.png").writeBytes(bytes)
            }
        }
    }
}
