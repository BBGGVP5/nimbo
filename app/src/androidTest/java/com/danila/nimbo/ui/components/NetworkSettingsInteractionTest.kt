package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NetworkSettingsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Narrow(content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.25f)) {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Box(Modifier.width(320.dp).padding(16.dp)) { NimboPanel { content() } }
            }
        }
    }

    @Test fun dnsChoicesStackAt320AndDispatchExactSelectionOnce() {
        val selected = mutableIntStateOf(0)
        val changes = mutableListOf<Int>()
        compose.setContent { Narrow {
            NetworkSettingsChoices(listOf("VPN", "Direct only for special networks", "Hybrid"),
                selected.intValue, { selected.intValue = it; changes += it }, Modifier.fillMaxWidth().padding(16.dp))
        } }
        compose.onNodeWithTag("network-choices-stacked").assertExists()
        compose.onNodeWithText("VPN").assertIsSelected().performClick()
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        compose.onNodeWithText("Direct only for special networks").performClick()
        compose.runOnIdle { assertEquals(listOf(1), changes) }
        compose.onNodeWithText("Direct only for special networks").assertIsSelected()
    }

    @Test fun tlsMtuExplanationWrapsWithoutEllipsisAndDisabledControlCannotDispatch() {
        val explanation = "TLS Fragment and packet fragmentation lower MTU only when explicitly enabled; existing transport settings remain unchanged."
        var changes = 0
        compose.setContent { Narrow {
            NetworkSettingsToggleRow("TLS / MTU", explanation, false, { changes++ }, enabled = false)
        } }
        compose.onNodeWithTag("network-row-stacked").assertExists()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(explanation).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.runOnIdle {
            assertTrue(layouts.single().lineCount > 1)
            assertFalse(layouts.single().hasVisualOverflow)
            assertEquals(0, changes)
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
    }

    @Test fun urlFieldPassesTypedValueThroughWithoutNormalizingIt() {
        val value = mutableStateOf("https://example.test/original")
        compose.setContent { Narrow {
            NetworkSettingsTextField(value.value, { value.value = it }, "URL", Modifier.fillMaxWidth())
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("https://example.test/probe?x=1")
        compose.runOnIdle { assertEquals("https://example.test/probe?x=1", value.value) }
    }

    @Test fun wideChoicesStayCompact() {
        compose.setContent { NebulaGuardTheme(backgroundAnimationEnabled = false) {
            Box(Modifier.width(480.dp)) { NetworkSettingsChoices(listOf("VPN", "Direct", "Hybrid"), 0, {}) }
        } }
        compose.onNodeWithTag("network-choices-inline").assertExists()
    }
}
