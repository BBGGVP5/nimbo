package com.danila.nimbo.ui.components

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PingTimeoutControlTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enterSecondsAndKeepInvalidOrCancelledDraftOutOfPreferences() {
        val stored = mutableIntStateOf(3)
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                PingTimeoutControl(stored.intValue, { stored.intValue = it }, english = true)
            }
        }
        compose.onNodeWithTag("ping-timeout-edit").performClick()
        compose.onNodeWithTag("ping-timeout-input").performTextReplacement("3000")
        compose.onNodeWithTag("ping-timeout-save").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(3, stored.intValue) }
        compose.onNodeWithTag("ping-timeout-input").performTextReplacement("")
        compose.onNodeWithTag("ping-timeout-save").assertIsNotEnabled()
        compose.onNodeWithTag("ping-timeout-input").performTextReplacement("7")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(3, stored.intValue) }
        compose.onNodeWithTag("ping-timeout-edit").performClick()
        compose.onNodeWithTag("ping-timeout-input").assertTextContains("3")
        compose.onNodeWithTag("ping-timeout-input").performTextReplacement("7")
        compose.onNodeWithTag("ping-timeout-save").performClick()
        compose.runOnIdle { assertEquals(7, stored.intValue) }
        compose.onNodeWithText("7 s").assertExists()
        compose.onNodeWithTag("ping-timeout-edit").performClick()
        compose.onNodeWithTag("ping-timeout-input").performTextReplacement("10")
        compose.onNodeWithTag("ping-timeout-input").performImeAction()
        compose.runOnIdle { assertEquals(10, stored.intValue) }
        compose.onNodeWithContentDescription("Increase timeout by one second").assertIsNotEnabled()
    }
}
