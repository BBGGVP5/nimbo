package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.screens.MihomoProfileCard
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MihomoTilePointerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wholeTileTogglesButNestedAndDisabledButtonsDoNot() {
        var toggles = 0
        var selects = 0
        var exports = 0
        val expanded = mutableStateOf(false)
        val busy = mutableStateOf(false)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Box(Modifier.width(340.dp)) {
                    MihomoProfileCard(
                        profile = SubscriptionProfile(name = "Mihomo test"),
                        selected = false,
                        active = false,
                        expanded = expanded.value,
                        busy = busy.value,
                        canSelect = true,
                        canRemove = false,
                        onExpand = { toggles++; expanded.value = !expanded.value },
                        onSelect = { selects++ },
                        onExport = { exports++ },
                        onRemove = {},
                    )
                }
            }
        }
        fun tap(point: Offset) {
            val root = compose.onRoot()
            val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
            root.performTouchInput { click(point - origin) }
            compose.waitForIdle()
        }
        fun tapTag(tag: String) = tap(compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center)
        repeat(2) {
            val bounds = compose.onNodeWithTag("mihomo-profile-card").fetchSemanticsNode().boundsInRoot
            tap(Offset(bounds.left + 3, bounds.center.y))
        }
        compose.runOnIdle { assertEquals(2, toggles) }
        tapTag("mihomo-profile-select")
        tapTag("mihomo-profile-export")
        tapTag("mihomo-profile-remove") // Disabled child consumes the press.
        compose.runOnIdle { busy.value = true }
        tapTag("mihomo-profile-select")
        tapTag("mihomo-profile-export")
        compose.runOnIdle {
            assertEquals(2, toggles)
            assertEquals(1, selects)
            assertEquals(1, exports)
        }
    }
}
