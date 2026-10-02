package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Coordinates exercise the shared/iOS pointer path, unlike invoking OnClick semantics. */
class NimboSubscriptionPointerTest {
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun ImageComposeScene.settle() { render(System.nanoTime()).close(); render(System.nanoTime()).close() }
    private fun ImageComposeScene.tap(position: Offset, type: PointerType = PointerType.Touch) {
        sendPointerEvent(PointerEventType.Press, position, type = type)
        sendPointerEvent(PointerEventType.Release, position, type = type)
        settle()
    }
    private fun ImageComposeScene.textPosition(text: String) = nodes().first {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text }
    }.boundsInRoot.center
    private fun ImageComposeScene.labelPosition(label: String) = nodes().first {
        label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }.boundsInRoot.center
    private fun state() = NimboUiState(profileCount = 1, serverCount = 1, activeProfileName = "Provider",
        profileTrafficLabel = "5 / 10 GB", profileTrafficUsed = 5, profileTrafficTotal = 10,
        profileExpiryLabel = "Tomorrow", profileUpdatedLabel = "today", profileAnnounce = "Private provider details",
        servers = listOf(NimboServerUi("one", "Amsterdam", "vless")))

    @Test fun everyNonActionRegionTogglesExactlyOnceWithTouchAndMouse() {
        for (pointerType in listOf(PointerType.Touch, PointerType.Mouse)) {
            val expanded = mutableStateOf(false)
            var toggles = 0
            ImageComposeScene(360, 640) {
                Column(Modifier.width(320.dp)) {
                    NimboSubscriptionHeader(state(), NimboUiActions(), expanded.value) {
                        toggles++; expanded.value = !expanded.value
                    }
                }
            }.use { scene ->
                scene.settle()
                val card = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "subscription-card" }.boundsInRoot
                val positions = listOf(scene.textPosition("Provider"), scene.textPosition("5 / 10 GB"),
                    scene.textPosition("Tomorrow"), scene.textPosition("Обновлено today"),
                    scene.labelPosition("Остаток трафика"),
                    Offset(card.center.x, card.top + 3f), Offset(card.left + 3f, card.center.y))
                var expected = 0
                for (point in positions) repeat(2) {
                    scene.tap(point, pointerType)
                    expected++
                    assertEquals(expected, toggles, "Coordinate $point with $pointerType must toggle once")
                    assertEquals(expected % 2 == 1, expanded.value)
                }
            }
        }
    }

    @Test fun enabledAndDisabledActionsAndInfoDoNotToggle() {
        val state = mutableStateOf(state())
        var toggles = 0
        var pings = 0
        var refreshes = 0
        ImageComposeScene(360, 640) {
            Column(Modifier.width(320.dp)) {
                NimboSubscriptionHeader(state.value,
                    NimboUiActions(onPingAll = { pings++ }, onRefreshProfile = { refreshes++ }), false) { toggles++ }
            }
        }.use { scene ->
            scene.settle()
            scene.tap(scene.labelPosition("Проверить пинг"))
            scene.tap(scene.labelPosition("Обновить подписку"))
            assertEquals(1, pings)
            assertEquals(1, refreshes)
            assertEquals(0, toggles)
            state.value = state.value.copy(pingInProgress = true)
            scene.settle()
            val cancel = scene.nodes().first { "Остановить пинг" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue(!cancel.config.contains(SemanticsProperties.Disabled))
            scene.tap(cancel.boundsInRoot.center)
            assertEquals(2, pings)
            assertEquals(0, toggles)
            scene.tap(scene.labelPosition("Информация"))
            assertEquals(0, toggles)
            assertTrue(scene.nodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Private provider details" }
            })
        }
    }

    @Test fun bothPagesKeepServerSelectionIndependentAfterPointerDisclosure() {
        for (screen in listOf(NimboScreen.HOME, NimboScreen.PROFILES)) {
            var selections = 0
            var connects = 0
            ImageComposeScene(390, 1400) {
                NimboAppShell(screen, state(), NimboUiActions(onSelectServer = { selections++ }, onToggleVpn = { connects++ }), showBottomBar = false)
            }.use { scene ->
                scene.settle()
                // Home also shows this provider name in its connection panel.
                // Hit the subscription header's coordinates, not that duplicate.
                val header = scene.nodes().single {
                    it.config.getOrNull(SemanticsProperties.TestTag) == "subscription-header"
                }
                scene.tap(header.boundsInRoot.center)
                assertEquals("Серверы показаны", scene.nodes().single {
                    it.config.getOrNull(SemanticsProperties.TestTag) == "subscription-card"
                }.config.getOrNull(SemanticsProperties.StateDescription), "$screen must expand before selecting a server")
                scene.tap(scene.textPosition("Amsterdam"))
                assertEquals(1, selections)
                assertEquals(0, connects)
                val card = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "subscription-card" }
                assertEquals("Серверы показаны", card.config.getOrNull(SemanticsProperties.StateDescription))
                scene.tap(scene.textPosition("Обновлено today"))
                assertEquals("Серверы скрыты", scene.nodes().first {
                    it.config.getOrNull(SemanticsProperties.TestTag) == "subscription-card"
                }.config.getOrNull(SemanticsProperties.StateDescription))
            }
        }
    }
}
