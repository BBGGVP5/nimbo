package com.danila.nimbo.ui.components

import androidx.compose.ui.test.onNodeWithContentDescription

import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import com.danila.nimbo.ui.screens.NetworkSpeedChartCard
import com.danila.nimbo.ui.screens.MemoryUsageCard
import com.danila.nimbo.ui.screens.MiniSpeedSample
import com.danila.nimbo.ui.screens.SyncCategoryRow
import com.danila.nimbo.ui.screens.SyncVerificationCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UniversalInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun subscriptionBodyTogglesButNestedActionsDoNot() {
        var toggles = 0
        var info = 0
        var ping = 0
        var refresh = 0
        var menu = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboSubscriptionPanel(false, { toggles++ }) {
                    Column {
                        NimboSubscriptionHeader("Provider", "Subscription", false,
                            { toggles++ }, { info++ }, logo = {})
                        Text("Provider description", androidx.compose.ui.Modifier.testTag("subscription-description"))
                        NimboIconAction(Icons.Default.SignalCellularAlt, "Ping") { ping++ }
                        NimboIconAction(Icons.Default.Refresh, "Refresh") { refresh++ }
                        NimboIconAction(Icons.Default.Refresh, "Menu") { menu++ }
                    }
                }
            }
        }
        compose.onNodeWithTag("subscription-info").performClick()
        compose.onNodeWithContentDescription("Ping").performClick()
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.runOnIdle {
            assertEquals(0, toggles)
            assertEquals(1, info)
            assertEquals(1, ping)
            assertEquals(1, refresh)
            assertEquals(1, menu)
        }
        compose.onNodeWithTag("subscription-description", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onNodeWithTag("subscription-toggle").performClick()
        compose.runOnIdle { assertEquals(2, toggles) }
        compose.onNodeWithTag("subscription-disclosure").assertDoesNotExist()
    }

    @Test fun themePreviewCardsAreSelectable() {
        val selection = mutableStateOf(0)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                com.danila.nimbo.ui.screens.NimboThemePreviewGrid(selection.value, null) { selection.value = it }
            }
        }
        compose.onNodeWithTag("theme-preview-0").assertIsSelected()
        compose.onNodeWithTag("theme-preview-3").performClick().assertIsSelected()
        compose.onNodeWithTag("theme-preview-0").assertIsNotSelected()
        compose.runOnIdle { assertEquals(3, selection.value) }
    }

    @Test fun serverPingAndMenuDoNotSelectServer() {
        var selections = 0
        var pings = 0
        var favorites = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboServerRow("Server", "Provider description", false, { selections++ },
                    { pings++ }, menu = {
                        androidx.compose.material3.IconButton({ favorites++ }, androidx.compose.ui.Modifier.testTag("server-menu-action")) {
                            Text("Menu")
                        }
                    }, ping = { Text("Not measured") }, flag = "🇫🇮")
            }
        }
        compose.onNodeWithTag("server-ping").performClick()
        compose.onNodeWithTag("server-menu-action").performClick()
        compose.runOnIdle {
            assertEquals(0, selections)
            assertEquals(1, pings)
            assertEquals(1, favorites)
        }
        compose.onNodeWithText("Server").performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun autoIsASelectionAndDoesNotStartConnectionUntilConnectIsPressed() {
        var connections = 0
        val autoSelected = mutableStateOf(false)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Column {
                    NimboAutoChoice(autoSelected.value, "Working location",
                        onSelect = { autoSelected.value = true })
                    NimboConnectionControl(false, false, { connections++ })
                }
            }
        }
        compose.onNodeWithTag("auto-mode-choice").assertIsNotSelected().performClick()
        compose.onNodeWithTag("auto-mode-choice").assertIsSelected()
        compose.runOnIdle { assertEquals(0, connections) }
        compose.onNodeWithTag("connection-control").performClick()
        compose.runOnIdle { assertEquals(1, connections) }
    }

    @Test fun busyActionDoesNotDispatchDuplicateWork() {
        var refreshes = 0
        val busy = mutableStateOf(true)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboAction(Icons.Default.Refresh, "Refresh", { refreshes++ }, busy = busy.value)
            }
        }
        compose.onNodeWithText("Refresh").assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }
    @Test fun roundConnectionCloudTracksActualStateAndDisconnects() = connectionContract(compact = false)

    @Test fun compactConnectionCloudTracksActualStateAndDisconnects() = connectionContract(compact = true)

    private fun connectionContract(compact: Boolean) {
        val state = mutableStateOf(Triple(false, false, false))
        var clicks = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboConnectionControl(state.value.first, state.value.second, { clicks++ },
                    compact = compact, disconnecting = state.value.third)
            }
        }
        if (!compact) {
            compose.onNodeWithTag("connection-control").assertWidthIsEqualTo(156.dp).assertHeightIsEqualTo(156.dp)
            compose.onNodeWithTag("connection-ring", useUnmergedTree = true).assertWidthIsEqualTo(172.dp).assertHeightIsEqualTo(172.dp)
        }
        compose.onNodeWithTag("connection-power", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-cloud", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("connection-control").performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            state.value = Triple(false, true, false)
        }
        compose.onNodeWithTag("connection-progress", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-power", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-cloud", useUnmergedTree = true).assertDoesNotExist()
        // Connecting remains cancellable; it must not become a disabled busy action.
        compose.onNodeWithTag("connection-control").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, clicks); state.value = Triple(true, false, false) }
        compose.onNodeWithTag("connection-cloud", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-power", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("connection-progress", useUnmergedTree = true).assertDoesNotExist()
        // Same real callback owns disconnect, regardless of round/compact presentation.
        compose.onNodeWithTag("connection-control").performClick()
        compose.runOnIdle { assertEquals(3, clicks); state.value = Triple(true, false, true) }
        compose.onNodeWithTag("connection-cloud", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("connection-power", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-progress", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-control").assertIsNotEnabled()
        compose.runOnIdle { state.value = Triple(false, false, false) }
        compose.onNodeWithTag("connection-power", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("connection-control").assertIsEnabled()
    }
    @Test fun syncCategoryDispatchesOnceAndRespectsDisabledSession() {
        var changes = 0
        val enabled = mutableStateOf(true)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Column {
                    SyncCategoryRow("Subscriptions", "Links and names", false, enabled.value) { changes++ }
                    SyncVerificationCode("123456", motionEnabled = false)
                }
            }
        }
        compose.onNodeWithText("123456").assertExists()
        compose.onNodeWithText("Subscriptions").performClick()
        compose.runOnIdle { assertEquals(1, changes); enabled.value = false }
        compose.onNodeWithText("Subscriptions").assertIsNotEnabled()
    }

    @Test fun secondaryCopyNeverReplacesStatusAndStopsWhenOperationEnds() {
        val active = mutableStateOf(true)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Column {
                    Text("Actual status")
                    NimboOperationPhrase(NimboOperation.Sync, active.value, rotate = false)
                }
            }
        }
        compose.onNodeWithText("Actual status").assertExists()
        compose.onNodeWithTag("operation-phrase", useUnmergedTree = true).assertExists()
        compose.runOnIdle { active.value = false }
        compose.onNodeWithTag("operation-phrase", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Actual status").assertExists()
    }
    @Test fun mobileChartsKeepDedicatedPlotRegionsAndRealMemoryValue() {
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Column {
                    NetworkSpeedChartCard(listOf(MiniSpeedSample(1024L, 4096L)), 1024L, 4096L, 2048L, 8192L)
                    MemoryUsageCard(77L, listOf(78L, 77L))
                }
            }
        }
        compose.onNodeWithTag("home-speed-graph").assertHeightIsEqualTo(56.dp)
        compose.onNodeWithTag("home-memory-graph").assertHeightIsEqualTo(40.dp)
        compose.onNodeWithText("77", substring = true).assertExists()
        compose.onNodeWithText("78", substring = true).assertExists()
    }
}
