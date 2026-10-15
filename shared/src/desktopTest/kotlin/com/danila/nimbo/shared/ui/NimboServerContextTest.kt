package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NimboServerContextTest {
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
    private fun ImageComposeScene.settle() { render(0).close(); render(1_000_000_000L).close() }

    @Test fun selectedRowHoldOpensSingleServerPingWithoutChangingSelection() {
        val server = mutableStateOf(NimboServerUi("selected-id", "Finland", "vless", selected = true))
        val pings = mutableListOf<String>()
        var selections = 0
        ImageComposeScene(390, 600) {
            ProfileServerCard(server.value, false, { selections++ }, {}, { pings += it })
        }.use { scene ->
            scene.settle()
            fun openActions() {
                val hold = scene.nodes().first { it.config.getOrNull(SemanticsActions.OnLongClick) != null }
                assertEquals(true, hold.config.getOrNull(SemanticsProperties.Selected))
                assertTrue(hold.config[SemanticsActions.OnLongClick].action!!.invoke())
                scene.settle()
                assertEquals(0, selections)
            }
            fun choose(text: String) {
                val item = scene.nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text } }
                assertNotNull(item.config.getOrNull(SemanticsActions.OnClick))
                assertTrue(item.config[SemanticsActions.OnClick].action!!.invoke())
                scene.settle()
            }
            openActions(); choose("Пинг сервера")
            assertEquals(listOf("selected-id"), pings)
            server.value = server.value.copy(pingInProgress = true)
            scene.settle()
            openActions(); choose("Остановить пинг")
            assertEquals(listOf("selected-id", "selected-id"), pings)
            assertEquals(0, selections)
        }
    }
}
