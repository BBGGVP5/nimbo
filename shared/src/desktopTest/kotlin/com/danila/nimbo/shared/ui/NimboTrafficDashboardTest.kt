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
            AdBlockingCard(NimboUiState(), NimboUiActions(onSetAdBlocking = { choices += it }, onToggleVpn = { connections++ }))
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
}
