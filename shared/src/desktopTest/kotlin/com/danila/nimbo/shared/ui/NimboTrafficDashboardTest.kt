package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.semantics.*
import java.io.File
import kotlin.test.*

class NimboTrafficDashboardTest {
    @Test fun phoneTransferCardsStayAdjacentInsidePaddedSessionForLargeText() {
        for (width in listOf(320, 360, 390)) for (scale in listOf(1f, 1.25f)) {
            ImageComposeScene(width, 800) {
                NimboAppShell(NimboScreen.STATS, NimboUiState(
                    appearance = NimboAppearance(textScale = scale), vpnState = "connected",
                    sessionAvailable = true, downloadTotal = 4194304, uploadTotal = 1048576
                ), NimboUiActions(), showBottomBar = false)
            }.use { scene ->
                scene.render(0).close(); scene.render(1_000_000_000L).close()
                val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                val down = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "traffic-download-card" }.boundsInRoot
                val up = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "traffic-upload-card" }.boundsInRoot
                assertEquals(down.top, up.top, "directions stacked at width=$width scale=$scale")
                assertTrue(down.right < up.left)
                assertTrue(up.right <= width)
            }
        }
    }
    @Test fun dashboardRendersMeasuredUnavailableAndZeroForEachStyle() {
        val output = File("build/reports/traffic-dashboard").apply { mkdirs() }
        for (style in listOf("glass", "material", "dotted", "signal")) {
            for (theme in listOf("dark", "light")) {
                for (width in listOf(360, 800, 1100)) {
                    for (mode in listOf("measured", "unavailable", "zero")) {
                        val state = NimboUiState(
                            appearance = NimboAppearance(themeMode = theme), elementStyle = style,
                            vpnState = "connected", sessionAvailable = true,
                            downloadTotal = 4194304, uploadTotal = 1048576,
                            downloadSpeed = 123456, uploadSpeed = 65432,
                            routeTraffic = when (mode) {
                                "measured" -> NimboRouteTraffic(60, 40, 100, 200)
                                "zero" -> NimboRouteTraffic()
                                else -> null
                            }, tcpConnections = if (mode == "unavailable") null else 0,
                            udpConnections = if (mode == "unavailable") null else 3
                        )
                        ImageComposeScene(width, 1000) {
                            NimboAppShell(NimboScreen.STATS, state, NimboUiActions(), showBottomBar = false)
                        }.use { scene ->
                            scene.render(0).close()
                            scene.render(1_000_000_000L).use { image ->
                                if (mode == "measured") File(output, "$width-$theme-$style.png")
                                    .writeBytes(image.encodeToData()!!.use { it.bytes })
                            }
                            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                            val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                            assertTrue("Статистика" in texts)
                            assertFalse("Блокировка рекламы" in texts, "statistics must not duplicate settings")
                            assertTrue("Текущая сессия" in texts)
                            val routeDescriptions = nodes.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                            val expected = when (mode) { "measured" -> "25% VPN"; "zero" -> "0 Б"; else -> "Нет данных" }
                            assertTrue("Маршруты: $expected" in routeDescriptions)
                            assertTrue(nodes.any { it.config.getOrNull(SemanticsActions.ScrollBy) != null })
                        }
                    }
                }
            }
        }
    }

    @Test fun toggleSavesPreferenceWithoutConnectingOrReportingBlockedCounts() {
        val choices = mutableListOf<Boolean>()
        var connections = 0
        ImageComposeScene(360, 700) {
            AdBlockingSettingsCard(NimboUiState(), NimboUiActions(onSetAdBlocking = { choices += it }, onToggleVpn = { connections++ }))
        }.use { scene ->
            scene.render(0).close(); scene.render(1_000_000_000L).close()
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            val switch = nodes.first { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Switch }
            assertEquals(androidx.compose.ui.state.ToggleableState.Off, switch.config.getOrNull(SemanticsProperties.ToggleableState))
            assertTrue(switch.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
            assertEquals(listOf(true), choices)
            assertEquals(0, connections)
        }
    }

    @Test fun routingContainsOneCompactAdSettingInEveryStyleAndSize() {
        val output = File("build/reports/ad-blocking-settings").apply { mkdirs() }
        for (width in listOf(360, 800, 1100)) for (theme in listOf("dark", "light")) for (style in listOf("glass", "material", "dotted", "signal")) {
            ImageComposeScene(width, 1000) {
                NimboAppShell(NimboScreen.ROUTING, NimboUiState(
                    appearance = NimboAppearance(themeMode = theme), elementStyle = style
                ), NimboUiActions(), showBottomBar = false)
            }.use { scene ->
                scene.render(0).close()
                scene.render(1_000_000_000L).use { image ->
                    File(output, "$width-$theme-$style.png").writeBytes(image.encodeToData()!!.use { it.bytes })
                }
                val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                val switches = nodes.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch &&
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Блокировка рекламы" } }
                assertEquals(1, switches.size, "routing must expose one named ad switch")
                assertEquals(androidx.compose.ui.state.ToggleableState.Off, switches.single().config.getOrNull(SemanticsProperties.ToggleableState))
                val description = nodes.first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                    text.text == "Фильтрует рекламные домены. Не убирает всю рекламу." } }
                assertTrue(description.boundsInRoot.left >= 0 && description.boundsInRoot.right <= width)
                assertTrue(description.boundsInRoot.height < 60, "description must stay compact")
                assertFalse(nodes.any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text.contains("зашифрованный DNS") } },
                    "detailed caveats belong in the info dialog")
            }
        }
    }

    @Test fun settingDoesNotMistakeSavedPreferenceForActiveSessionState() {
        for ((state, expected) in listOf(
            NimboUiState() to "Со следующего подключения",
            NimboUiState(vpnState = "connected", adBlockingEnabled = true, activeAdBlockingEnabled = false) to "Изменится при следующем подключении",
            NimboUiState(vpnState = "connected", adBlockingEnabled = true, activeAdBlockingEnabled = true) to "Текущее подключение: включено",
            NimboUiState(vpnState = "connected", activeAdBlockingEnabled = false) to "Текущее подключение: выключено",
            NimboUiState(vpnState = "connected", adBlockingEnabled = true) to "Со следующего подключения · текущая сессия неизвестна"
        )) {
            ImageComposeScene(360, 700) {
                NimboAppShell(NimboScreen.ROUTING, state, NimboUiActions(), showBottomBar = false)
            }.use { scene ->
                scene.render(0).close(); scene.render(1_000_000_000L).close()
                val texts = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                assertTrue(expected in texts)
            }
        }
    }
}
