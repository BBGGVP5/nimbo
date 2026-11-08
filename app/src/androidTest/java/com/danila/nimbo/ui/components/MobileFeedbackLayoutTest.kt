package com.danila.nimbo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.LocalPreferencesManager
import com.danila.nimbo.ui.screens.MiniDestination
import com.danila.nimbo.ui.screens.NimboBottomControls
import com.danila.nimbo.ui.screens.SubscriptionDescription
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import com.danila.nimbo.utils.PreferencesManager
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MobileFeedbackLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun Fixture(scale: Float = 1f, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { PreferencesManager(context) }
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale("ru")) }
        CompositionLocalProvider(LocalPreferencesManager provides prefs, LocalConfiguration provides configuration) {
            NebulaGuardTheme(backgroundAnimationEnabled = false, textScale = scale) { content() }
        }
    }
    private fun noEllipsis(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        assertFalse(text.isLineEllipsized(0))
        assertEquals(1, text.lineCount)
    }
    @Test fun largeTextNavigationKeepsCaptionsAlignedAndDispatchesOnlySelection() {
        var selected = MiniDestination.Home
        val state = mutableStateOf(selected)
        compose.setContent { Fixture(1.3f) {
            val backdrop = rememberGraphicsLayer()
            Box(Modifier.width(320.dp).height(440.dp)) {
                NimboBottomControls({}, backdrop, state.value, true) { selected = it; state.value = it }
            }
        } }
        listOf("Главная", "Профили", "Активность", "Настройки").forEach(::noEllipsis)
        val activity = compose.onNodeWithText("Активность", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithText("Настройки", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(activity.top, settings.top, 1f)
        compose.onNodeWithText("Настройки").performClick()
        compose.runOnIdle { assertEquals(MiniDestination.Settings, selected) }
    }
    @Test fun ordinaryNavigationUsesOneAlignedRow() {
        compose.setContent { Fixture {
            val backdrop = rememberGraphicsLayer()
            Box(Modifier.width(390.dp).height(440.dp)) { NimboBottomControls({}, backdrop, MiniDestination.Home, true, {}) }
        } }
        val captions = listOf("Главная", "Профили", "Активность", "Настройки")
        captions.forEach(::noEllipsis)
        val tops = captions.map { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top }
        tops.forEach { assertEquals(tops.first(), it, 1f) }
    }
    @Test fun providerDescriptionRetainsAllLines() {
        val announcement = (1..10).joinToString("\n") { "Строка $it" }
        compose.setContent { Fixture { Box(Modifier.width(320.dp)) { SubscriptionDescription(announcement) } } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(announcement).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(10, layouts.single().lineCount)
        assertFalse(layouts.single().isLineEllipsized(9))
    }
}
