package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real down/up events on the root exercise hit testing; semantics clicks do not. */
class SubscriptionTilePointerTest {
    @get:Rule val compose = createComposeRule()

    private fun tap(node: SemanticsNodeInteraction) {
        val position = node.fetchSemanticsNode().boundsInRoot.center
        val root = compose.onRoot()
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performTouchInput { click(position - origin) }
        compose.waitForIdle()
    }

    @Test fun headerDescriptionQuotaFooterAndPaddingEachToggleExactlyOnceInBothStates() {
        val expanded = mutableStateOf(false)
        var toggles = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboSubscriptionPanel(expanded.value, { toggles++; expanded.value = !expanded.value },
                    Modifier.width(320.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NimboSubscriptionHeader("Provider", "3 servers", expanded.value, null, {}, logo = {})
                        Text("Provider description", Modifier.fillMaxWidth().testTag("description"))
                        Box(Modifier.fillMaxWidth().height(4.dp).testTag("quota"))
                        Row(Modifier.fillMaxWidth()) {
                            Text("Updated today", Modifier.weight(1f).testTag("footer"))
                            NimboIconAction(Icons.Default.Refresh, "Refresh") {}
                        }
                    }
                }
            }
        }
        val regions = listOf(
            compose.onNodeWithText("Provider", useUnmergedTree = true),
            compose.onNodeWithTag("subscription-toggle", useUnmergedTree = true),
            compose.onNodeWithTag("description", useUnmergedTree = true),
            compose.onNodeWithTag("quota", useUnmergedTree = true),
            compose.onNodeWithTag("footer", useUnmergedTree = true)
        )
        var expected = 0
        for (region in regions) {
            repeat(2) {
                tap(region)
                expected++
                compose.runOnIdle {
                    assertEquals(expected, toggles)
                    assertEquals(expected % 2 == 1, expanded.value)
                }
            }
        }
        // Top and side padding are outside all child bounds. This is the region
        // that a clickable modifier around a non-clickable Surface can miss.
        val card = compose.onNodeWithTag("subscription-card").fetchSemanticsNode().boundsInRoot
        for (point in listOf(Offset(card.center.x, card.top + 3f), Offset(card.left + 3f, card.center.y))) {
            repeat(2) {
                val root = compose.onRoot()
                val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
                root.performTouchInput { click(point - origin) }
                expected++
                compose.runOnIdle {
                    assertEquals(expected, toggles)
                    assertEquals(expected % 2 == 1, expanded.value)
                }
            }
        }
        compose.onNodeWithTag("subscription-disclosure").assertDoesNotExist()
    }

    @Test fun childActionsAndDisabledTargetsDoNotToggleOrSelectServers() {
        var toggles = 0
        var infos = 0
        var pings = 0
        var refreshes = 0
        var menus = 0
        var selections = 0
        var serverPings = 0
        var serverMenus = 0
        val busy = mutableStateOf(false)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboSubscriptionPanel(true, { toggles++ }, Modifier.width(320.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        NimboSubscriptionHeader("Provider", "1 server", true, null, { infos++ }, logo = {}, trailing = {
                            IconButton({ menus++ }, Modifier.size(48.dp).testTag("menu"), enabled = !busy.value) {
                                Text("Menu")
                            }
                        })
                        Row {
                            NimboIconAction(Icons.Default.SignalCellularAlt, "Ping", busy.value) { pings++ }
                            NimboIconAction(Icons.Default.Refresh, "Refresh", busy.value) { refreshes++ }
                        }
                        NimboServerRow("Server", "Location", false, { selections++ }, { serverPings++ },
                            menu = {
                                NimboIconAction(Icons.Default.MoreVert, "Server menu", busy.value) { serverMenus++ }
                            }, ping = { Text("20 ms") })
                        NimboSubscriptionServerRow("Home server", "Location", false,
                            onClick = { selections++ }, latency = { Text("30 ms") })
                    }
                }
            }
        }
        tap(compose.onNodeWithTag("subscription-info"))
        tap(compose.onNodeWithContentDescription("Ping"))
        tap(compose.onNodeWithContentDescription("Refresh"))
        tap(compose.onNodeWithTag("menu"))
        tap(compose.onNodeWithTag("server-ping"))
        tap(compose.onNodeWithContentDescription("Server menu"))
        compose.runOnIdle {
            assertEquals(listOf(1, 1, 1, 1, 1, 1), listOf(infos, pings, refreshes, menus, serverPings, serverMenus))
            assertEquals(0, toggles)
            assertEquals(0, selections)
            busy.value = true
        }
        for (target in listOf(compose.onNodeWithContentDescription("Ping"),
            compose.onNodeWithContentDescription("Refresh"), compose.onNodeWithTag("menu"),
            compose.onNodeWithContentDescription("Server menu"))) {
            target.assertIsNotEnabled()
            tap(target)
        }
        compose.runOnIdle {
            assertEquals(listOf(1, 1, 1, 1), listOf(pings, refreshes, menus, serverMenus))
            assertEquals(0, toggles)
            assertEquals(0, selections)
        }
        tap(compose.onNodeWithText("Server", useUnmergedTree = true))
        compose.runOnIdle { assertEquals(1, selections); assertEquals(0, toggles) }
        tap(compose.onNodeWithText("Home server", useUnmergedTree = true))
        compose.runOnIdle { assertEquals(2, selections); assertEquals(0, toggles) }
    }
}
