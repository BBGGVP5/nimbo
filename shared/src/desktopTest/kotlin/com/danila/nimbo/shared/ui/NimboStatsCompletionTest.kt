package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import java.io.File
import kotlin.test.*

class NimboStatsCompletionTest {
    @Test fun automaticSelectionIsDisabledWhileProbingOrWithoutServers() {
        for ((servers, searching, disabled) in listOf(
            Triple(emptyList<NimboServerUi>(), false, true),
            Triple(listOf(NimboServerUi("a", "Amsterdam", "vless")), true, true),
            Triple(listOf(NimboServerUi("a", "Amsterdam", "vless")), false, false)
        )) {
            ImageComposeScene(320, 240) { AutoFastestCard(servers, searching, {}) }.use { scene ->
                scene.render(0).close(); scene.render(1_000_000_000L).close()
                val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                    .first { it.config.getOrNull(SemanticsActions.OnClick) != null }
                assertEquals(disabled, node.config.getOrNull(SemanticsProperties.Disabled) != null)
            }
        }
    }

    @Test fun disconnectedCountersAreNotPresentedAsHistory() {
        val state = NimboUiState(downloadTotal = 999999, uploadTotal = 77777)
        assertNull(trafficSummary(state))
        val recorded = state.copy(sessions = listOf(NimboSessionUi("Today", "3 min", 1024, 2048)))
        assertEquals(NimboTrafficSummary("Последняя сессия", 1024, 2048), trafficSummary(recorded))
        assertEquals(NimboTrafficSummary("Текущая сессия", 999999, 77777), trafficSummary(recorded.copy(vpnState = "connected")))
    }

    @Test fun singleMeasurementCannotBecomeHistoryLine() {
        assertFalse(hasMeasuredHistory(0))
        assertFalse(hasMeasuredHistory(1))
        assertTrue(hasMeasuredHistory(2))
        assertTrue(hasMeasuredHistory(60))
    }

    @Test fun statsRendersEmptyActiveAndLongHistoryInBothThemes() {
        val output = File("build/reports/ui-completion/stats").apply { mkdirs() }
        for (theme in listOf("dark", "light")) {
            for (width in listOf(320, 390, 768)) {
                for (status in listOf("empty", "connected", "history")) {
                    val state = NimboUiState(
                        appearance = NimboAppearance(themeMode = theme, textScale = if (width == 320) 1.25f else 1f),
                        vpnState = if (status == "connected") status else "idle",
                        activeServerName = "Франкфурт · резервный сервер с длинным названием",
                        serverCount = 121, profileTrafficLabel = "1023.9 ГБ из 2048.0 ГБ",
                        downloadTotal = 1099511627775, uploadTotal = 1099511627775,
                        downloadSpeed = 12345678, uploadSpeed = 6789012,
                        speedSamples = listOf(NimboSpeedSample(100, 300), NimboSpeedSample(300, 100), NimboSpeedSample(200, 400)),
                        sessions = if (status != "history") emptyList() else List(1000) {
                            NimboSessionUi("Запись $it · 20 сентября 2026 года, 23:59", "12 часов 50 минут", 1099511627775, 1048576)
                        }
                    )
                    ImageComposeScene(width, if (width == 320) 640 else 1000) {
                        NimboAppShell(NimboScreen.STATS, state, NimboUiActions(), showBottomBar = false)
                    }.use { scene ->
                        scene.render(0).close()
                        scene.render(1_000_000_000L).use { image ->
                            File(output, "$width-$theme-$status.png").writeBytes(image.encodeToData()!!.use { it.bytes })
                        }
                        val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                        val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                        if (status == "empty") assertTrue("Пока нет подключений" in texts)
                        if (status == "connected") assertTrue("Текущая сессия" in texts)
                        if (status == "history") {
                            assertTrue("Последняя сессия" in texts)
                            assertFalse(texts.any { it.startsWith("Запись 999 ") }, "History must be lazily composed")
                        }
                        assertTrue(nodes.any { it.config.getOrNull(SemanticsActions.ScrollBy) != null })
                    }
                }
            }
        }
    }

    @Test fun busySubscriptionActionsWrapWithoutLosingIndependentRefresh() {
        ImageComposeScene(320, 844) {
            NimboAppShell(NimboScreen.PROFILES,
                NimboUiState(profileCount = 1, activeProfileName = "Длинное название подписки", pingInProgress = true,
                    appearance = NimboAppearance(themeMode = "dark", textScale = 1.25f)), NimboUiActions())
        }.use { scene ->
            scene.render(0).close(); scene.render(1_000_000_000L).close()
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            val refresh = nodes.first { "Обновить подписку" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            val ping = nodes.first { "Проверить пинг" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue(refresh.boundsInRoot.right <= 320)
            assertTrue(refresh.boundsInRoot.left >= 0)
            assertTrue(refresh.boundsInRoot.width >= 44)
            assertNotNull(ping.config.getOrNull(SemanticsProperties.Disabled))
            assertNull(refresh.config.getOrNull(SemanticsProperties.Disabled))
        }
    }
}
