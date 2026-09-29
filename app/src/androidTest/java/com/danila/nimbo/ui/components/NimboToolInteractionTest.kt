@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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

class NimboToolInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun Narrow(content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Box(Modifier.width(320.dp).padding(16.dp)) { content() }
            }
        }
    }
    private fun noOverflow(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.runOnIdle { assertFalse(layouts.single().hasVisualOverflow) }
    }
    @Test fun commandsStackWrapAndDispatchOnlyTheirOwnCallback() {
        var copy = 0; var share = 0
        val label = "Copy the complete diagnostic report"
        compose.setContent { Narrow { NimboToolActions {
            NimboAction(Icons.Default.Refresh, label, { copy++ }, Modifier.weight(1f))
            NimboAction(Icons.Default.Refresh, "Share", { share++ }, Modifier.weight(1f))
        } } }
        compose.onNodeWithTag("tool-actions-1").assertExists()
        noOverflow(label)
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(1, copy); assertEquals(0, share) }
    }
    @Test fun busyActionCannotDispatch() {
        var calls = 0
        compose.setContent { Narrow { NimboAction(Icons.Default.Refresh, "Running", { calls++ }, busy = true) } }
        compose.onNodeWithText("Running").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun notificationMessageWrapsAndDeleteIsIndependent() {
        var deleted = 0
        val message = "A long diagnostic error from an actual operation must remain entirely readable at increased text size."
        compose.setContent { Narrow { NotificationSurface(message, NotificationType.ERROR,
            metaText = "12:34", actionIcon = Icons.Default.Refresh, actionDescription = "Delete event", onAction = { deleted++ }) } }
        noOverflow(message)
        compose.onNodeWithContentDescription("Delete event").performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }
    @Test fun metricHasNoInventedUnitsOrTruncation() {
        compose.setContent { Narrow { NimboToolMetric("Actual provider result", "Unavailable — no samples") } }
        noOverflow("Unavailable — no samples")
    }
    @Test fun longDialogDescriptionAndActionsAreScrollable() {
        var saved = 0
        val description = List(24) { "Real explanation, not a clipped preview." }.joinToString(" ")
        compose.setContent { Narrow { NebulaMorphicDialog({}, "Routing configuration", description,
            confirmButtonText = "Save configuration", cancelButtonText = "Cancel", onConfirm = { saved++ }) } }
        compose.onNodeWithText("Save configuration").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }
}
