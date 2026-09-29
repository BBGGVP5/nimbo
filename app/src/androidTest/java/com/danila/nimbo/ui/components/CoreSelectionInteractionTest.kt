package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.screens.NimboCoreSettingsContent
import com.danila.nimbo.ui.screens.MihomoProfileCard
import com.danila.nimbo.ui.screens.MihomoUiSession
import com.danila.nimbo.ui.screens.mihomoCanEnableLanProxy
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.ui.theme.NebulaGuardTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CoreSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choicesAreSingleSelectAndMihomoCannotBeActivatedAtNarrowWidth() {
        val selected = mutableStateOf("auto")
        val changes = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                NebulaGuardTheme(backgroundAnimationEnabled = false) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        NimboCoreSettingsContent(selected.value, { selected.value = it; changes += it }, "Xray")
                    }
                }
            }
        }
        compose.onNodeWithTag("vpn-core-auto").assertIsSelected().performClick()
        compose.onNodeWithTag("vpn-core-awg").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("vpn-core-auto").assertIsNotSelected()
        compose.onNodeWithTag("vpn-core-mihomo").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("awg"), changes) }
    }

    @Test fun compiledMihomoCanBeSelectedAndProfilesEntryDoesNotChangeActiveEngine() {
        val selected = mutableStateOf("auto")
        var opened = 0
        compose.setContent {
            NebulaGuardTheme(backgroundAnimationEnabled = false) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    NimboCoreSettingsContent(selected.value, { selected.value = it }, "AmneziaWG",
                        mihomoAvailable = true, onOpenMihomoProfiles = { opened++ })
                }
            }
        }
        compose.onNodeWithTag("vpn-core-mihomo").performScrollTo().assertIsEnabled().performClick().assertIsSelected()
        compose.onNodeWithTag("mihomo-open-profiles").performScrollTo().performClick()
        compose.onNodeWithTag("vpn-core-active").assertTextContains("AmneziaWG", substring = true)
        compose.runOnIdle { assertEquals("mihomo", selected.value); assertEquals(1, opened) }
    }

    @Test fun profileActionsDoNotExpandCardAndActiveRemovalIsDisabled() {
        var selections = 0
        var exports = 0
        var removals = 0
        val expanded = mutableStateOf(false)
        val active = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                NebulaGuardTheme(backgroundAnimationEnabled = false) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        MihomoProfileCard(SubscriptionProfile(url = "mihomo://test", name = "Original YAML"),
                            selected = false, active = active.value, expanded = expanded.value, busy = false,
                            canSelect = true, canRemove = !active.value,
                            onExpand = { expanded.value = !expanded.value }, onSelect = { selections++ },
                            onExport = { exports++ }, onRemove = { removals++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("mihomo-profile-select").performScrollTo().performClick()
        compose.onNodeWithTag("mihomo-profile-export").performScrollTo().performClick()
        compose.onNodeWithTag("mihomo-profile-remove").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, selections); assertEquals(1, exports); assertEquals(1, removals)
            assertFalse(expanded.value)
            active.value = true
        }
        compose.onNodeWithTag("mihomo-profile-remove").assertIsNotEnabled()
        compose.onNodeWithTag("mihomo-profile-expand").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(expanded.value) }
    }

    @Test fun runtimeControlsRequireMatchingSourceReadyTunAndSameResponseGeneration() {
        val active = MihomoUiSession(42, "source-a", "running", true)
        assertTrue(active.controls("source-a"))
        assertFalse(active.controls("source-b"))
        assertFalse(active.controls(""))
        assertFalse(active.copy(tunReady = false).controls("source-a"))
        assertFalse(active.copy(state = "starting").controls("source-a"))
        assertFalse(active.copy(state = "stopped").controls("source-a"))
        assertFalse(active.copy(generation = 0).controls("source-a"))
        assertTrue(active.sameRunningSession(active.copy()))
        assertFalse(active.sameRunningSession(active.copy(generation = 43)))
        assertFalse(active.sameRunningSession(active.copy(sourceHash = "source-b")))
        assertFalse(active.sameRunningSession(active.copy(tunReady = false)))
        assertTrue(active.copy(state = "starting").protects("source-a"))
        assertTrue(active.copy(state = "failed").protects("source-a"))
        assertFalse(active.copy(state = "stopped").protects("source-a"))
        assertFalse(active.protects("source-b"))
    }

    @Test fun lanChangeRequiresOnlyDirectLanConflictAndNoCurrentOrDesiredConnection() {
        val lanOnly = listOf("direct LAN bypass")
        assertTrue(mihomoCanEnableLanProxy(lanOnly, disconnected = true, vpnDesired = false))
        assertFalse(mihomoCanEnableLanProxy(lanOnly, disconnected = false, vpnDesired = false))
        assertFalse(mihomoCanEnableLanProxy(lanOnly, disconnected = true, vpnDesired = true))
        assertFalse(mihomoCanEnableLanProxy(null, disconnected = true, vpnDesired = false))
        assertFalse(mihomoCanEnableLanProxy(emptyList(), disconnected = true, vpnDesired = false))
        assertFalse(mihomoCanEnableLanProxy(listOf("IPv6"), disconnected = true, vpnDesired = false))
        assertFalse(mihomoCanEnableLanProxy(lanOnly + "kill switch (persistent blocking TUN is unavailable)",
            disconnected = true, vpnDesired = false))
        assertFalse(mihomoCanEnableLanProxy(lanOnly + "routing rules/modules", disconnected = true, vpnDesired = false))
    }
}
