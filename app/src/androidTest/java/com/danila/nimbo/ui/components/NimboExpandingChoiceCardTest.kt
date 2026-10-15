package com.danila.nimbo.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NimboExpandingChoiceCardTest {
    @get:Rule val compose = createComposeRule()
    private val options = listOf(
        NimboChoiceOption(1, "Selected apps bypass VPN", "Other apps use VPN"),
        NimboChoiceOption(2, "Only selected apps use VPN", "Other apps connect directly")
    )

    @Test fun optionsGrowInsideTheCardAndPushTheNextControlThenSelectionCollapses() {
        val stored = mutableIntStateOf(1)
        var callbackCount = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Column(Modifier.width(320.dp)) {
                    NimboExpandingChoiceCard(options, stored.intValue, {
                        stored.intValue = it
                        callbackCount++
                    })
                    Text("Next control", Modifier.testTag("below"))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val collapsed = compose.onNodeWithTag("choice-card").fetchSemanticsNode().boundsInRoot.height
        val initialNextTop = compose.onNodeWithTag("below").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("choice-card-option-2").assertDoesNotExist()
        compose.onNodeWithTag("choice-card-header").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val intermediate = compose.onNodeWithTag("choice-card").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(240)
        val open = compose.onNodeWithTag("choice-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue("The envelope grows, rather than opening a popup", intermediate > collapsed)
        assertTrue("Expansion has intermediate frames", intermediate < open)
        assertTrue(compose.onNodeWithTag("below").fetchSemanticsNode().boundsInRoot.top > initialNextTop)
        val cardBounds = compose.onNodeWithTag("choice-card").fetchSemanticsNode().boundsInRoot
        val optionBounds = compose.onNodeWithTag("choice-card-option-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Highlight must not meet the card border", optionBounds.left > cardBounds.left)
        assertTrue("Highlight must not meet the card border", optionBounds.right < cardBounds.right)
        compose.onNodeWithTag("choice-card-option-1").assertIsSelected()
        compose.onNodeWithTag("choice-card-option-2").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithTag("choice-card-option-2").assertDoesNotExist()
        compose.onNodeWithTag("choice-card-header").assertTextContains("Only selected apps use VPN")
        compose.runOnIdle { assertEquals(2, stored.intValue); assertEquals(1, callbackCount) }
    }

    @Test fun togglingAndReversingAnimationNeverChangesSelection() {
        var callbackCount = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                NimboExpandingChoiceCard(options, 1, { callbackCount++ })
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(3) {
            compose.onNodeWithTag("choice-card-header").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("choice-card-header").performClick()
            compose.mainClock.advanceTimeBy(320)
        }
        compose.onNodeWithTag("choice-card-option-1").assertDoesNotExist()
        compose.onNodeWithTag("choice-card-header").assertTextContains("Selected apps bypass VPN")
        compose.runOnIdle { assertEquals(0, callbackCount) }
    }

    @Test fun backOnlyCollapsesAndDoesNotSelectOrNavigateAway() {
        var callbackCount = 0
        var navigationCount = 0
        val dispatcher = OnBackPressedDispatcher(Runnable { navigationCount++ })
        compose.setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val owner = remember(lifecycleOwner) {
                object : OnBackPressedDispatcherOwner {
                    override val lifecycle = lifecycleOwner.lifecycle
                    override val onBackPressedDispatcher = dispatcher
                }
            }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                NebulaGuardTheme(backgroundAnimationEnabled = false) {
                    NimboExpandingChoiceCard(options, 1, { callbackCount++ })
                }
            }
        }
        compose.onNodeWithTag("choice-card-header").performClick()
        compose.onNodeWithTag("choice-card-option-1").assertIsSelected()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithTag("choice-card-option-1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, callbackCount); assertEquals(0, navigationCount) }
        compose.runOnIdle { dispatcher.onBackPressed(); assertEquals(1, navigationCount) }
    }
}
