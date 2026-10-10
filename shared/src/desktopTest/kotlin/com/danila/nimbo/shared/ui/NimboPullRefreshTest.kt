package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercise the actual nested-scroll wrappers, not a mocked refresh button. */
class NimboPullRefreshTest {
    @Test fun homeAndProfilesRefreshOnceAndDoNotRepeatWhileLoading() {
        for (screen in listOf(NimboScreen.HOME, NimboScreen.PROFILES)) {
            var requests = 0
            val state = mutableStateOf(NimboUiState(profileCount = 1, serverCount = 30,
                activeProfileName = "Fixture", servers = (1..30).map { NimboServerUi("$it", "Node $it", "vless") }))
            ImageComposeScene(390, 844) {
                NimboAppShell(screen, state.value, NimboUiActions(onRefreshProfile = {
                    requests++; state.value = state.value.copy(profileRefreshing = true)
                }), showBottomBar = false)
            }.use { scene ->
                var frame = 0L
                fun settle() { repeat(20) { frame += 16_000_000L; scene.render(frame).close() } }
                fun pull() {
                    val start = Offset(20f, 160f)
                    scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch)
                    for (step in 1..28) {
                        scene.sendPointerEvent(PointerEventType.Move, start + Offset(0f, step * 10f), type = PointerType.Touch)
                        frame += 16_000_000L; scene.render(frame).close()
                    }
                    scene.sendPointerEvent(PointerEventType.Release, start + Offset(0f, 280f), type = PointerType.Touch)
                    settle()
                }
                settle(); pull()
                assertEquals(1, requests, "$screen should refresh from its scroll top")
                pull(); assertEquals(1, requests, "$screen must coalesce while loading")
            }
        }
    }
}
