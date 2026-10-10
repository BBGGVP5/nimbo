package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import kotlin.test.*

/** Real production pointer path; not just calls to the swipe predicate. */
class NimboEdgeBackInteractionTest {
    @Test fun edgeBackReturnsFromSettingsDetailWithoutChangingTabOrPreferences() {
        val opened = mutableListOf<String>()
        var writes = 0
        ImageComposeScene(430, 932) {
            NimboAppShell(NimboScreen.SETTINGS,
                NimboUiState(nativeTopClearance = 59f, nativeBottomClearance = 134f),
                NimboUiActions(onOpenScreen = { opened += it }, onSetPing = { _, _ -> writes++ }),
                showBottomBar = false)
        }.use { scene ->
            var frame = 0L
            fun settle() { repeat(20) { frame += 16_000_000L; scene.render(frame).close() } }
            fun texts() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun click(label: String) {
                val node = texts().single { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } }
                assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
                settle()
            }
            fun swipe(start: Offset, delta: Offset) {
                scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch)
                repeat(12) { step ->
                    scene.sendPointerEvent(PointerEventType.Move, start + delta * ((step + 1) / 12f), type = PointerType.Touch)
                    frame += 16_000_000L; scene.render(frame).close()
                }
                scene.sendPointerEvent(PointerEventType.Release, start + delta, type = PointerType.Touch)
                settle()
            }
            fun detailVisible() = texts().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "‹ Настройки" } }
            settle(); click("Пинг серверов"); assertTrue(detailVisible())
            swipe(Offset(80f, 180f), Offset(110f, 0f))
            assertTrue(detailVisible(), "Drag away from the edge must not navigate")
            swipe(Offset(10f, 180f), Offset(20f, 0f))
            assertTrue(detailVisible(), "Short edge drag must not navigate")
            swipe(Offset(10f, 180f), Offset(110f, 5f))
            assertFalse(detailVisible(), "Edge swipe should invoke the visible back action")
            assertTrue(opened.isEmpty(), "Detail back must not jump to Home")
            assertEquals(0, writes)
            swipe(Offset(10f, 180f), Offset(110f, 5f))
            assertEquals(listOf(NimboScreen.HOME.wireName), opened)
        }
    }
}
