package com.danila.nimbo.shared.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NimboRedesignInteractionTest {
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
    private fun ImageComposeScene.texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
    private fun ImageComposeScene.settle() { render(0).close(); render(1_000_000_000L).close() }
    private fun ImageComposeScene.clickLabel(label: String) {
        val node = nodes().first { label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
    }

    @Test fun wideDesktopUsesLeftNavigationAndOpensProfiles() {
        ImageComposeScene(1280, 800) {
            NimboAppShell(NimboScreen.HOME, NimboUiState(), NimboUiActions())
        }.use { scene ->
            scene.settle()
            val profiles = scene.nodes().first {
                "Навигация: Профили" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            assertTrue(profiles.boundsInRoot.right <= 350f, "Wide navigation belongs in the left rail")
            assertTrue(profiles.config[SemanticsActions.OnClick].action!!.invoke())
            scene.settle()
            assertTrue("Профили" in scene.texts())
        }
    }

    @Test fun wideDesktopRestoresAllExistingPages() {
        ImageComposeScene(1280, 800) {
            NimboAppShell(NimboScreen.HOME, NimboUiState(), NimboUiActions())
        }.use { scene ->
            scene.settle()
            for (screen in NimboScreen.entries) {
                assertTrue(scene.nodes().any {
                    "Навигация: ${screen.title}" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                }, "Missing desktop page: ${screen.title}")
            }
            for (screen in listOf(NimboScreen.ROUTING, NimboScreen.MODULES,
                NimboScreen.ROUTING_PROFILES, NimboScreen.NOTIFICATIONS)) {
                scene.clickLabel("Навигация: ${screen.title}")
                val selected = scene.nodes().first {
                    "Навигация: ${screen.title}" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                }
                assertEquals("Текущая страница", selected.config.getOrNull(SemanticsProperties.StateDescription))
            }
        }
    }

    @Test fun shortDesktopWindowKeepsSidebarScrollable() {
        ImageComposeScene(1280, 480) {
            NimboAppShell(NimboScreen.HOME, NimboUiState(), NimboUiActions())
        }.use { scene ->
            scene.settle()
            val scrolls = scene.nodes().filter {
                it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            }
            val rail = scrolls.first { it.boundsInRoot.left < 300f }
            assertTrue(rail.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f,
                "The short sidebar must have scrollable overflow")
            assertTrue(rail.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 500f))
            scene.settle()
            assertTrue(rail.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
            val settings = scene.nodes().first {
                "Навигация: Настройки" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            assertTrue(settings.boundsInRoot.bottom <= 480f, "Settings must be reachable in a short window")
        }
    }

    @Test fun mobileHomeKeepsApprovedCenteredComposition() {
        val state = NimboUiState(vpnState = "connected", profileCount = 1, serverCount = 1,
            activeProfileName = "Provider", activeServerId = "one",
            servers = listOf(NimboServerUi("one", "Amsterdam", "vless")))
        ImageComposeScene(390, 844) {
            NimboAppShell(NimboScreen.HOME, state, NimboUiActions(), showBottomBar = false)
        }.use { scene ->
            scene.settle()
            assertTrue("nimbo" in scene.texts())
            assertFalse("Главная" in scene.texts())
            assertTrue("Пинг" in scene.texts())
            assertTrue("Обновить" in scene.texts())
            val power = scene.nodes().first {
                "Отключить" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }.boundsInRoot
            assertTrue(kotlin.math.abs(power.center.x - 195f) < 2f, "Mobile power belongs at the screen center, not the desktop card's right edge")
            assertTrue(power.width >= 118f)
        }
    }

    @Test fun connectionStylesKeepUniversalDisconnectAction() {
        for (style in listOf("classic", "compact")) {
            var toggles = 0
            var imports = 0
            val state = NimboUiState(vpnState = "connected", connectStyle = style)
            ImageComposeScene(390, 844) {
                NimboAppShell(NimboScreen.HOME, state,
                    NimboUiActions(onToggleVpn = { toggles++ }, onAddProfile = { imports++ }))
            }.use { scene ->
                scene.settle()
                val disconnect = scene.nodes().first { node ->
                    node.config.getOrNull(SemanticsActions.OnClick) != null &&
                        ("Отключить" in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() ||
                         node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Отключить" })
                }
                if (style == "classic") {
                    assertTrue(kotlin.math.abs(disconnect.boundsInRoot.width - disconnect.boundsInRoot.height) < 1f,
                        "Classic button stays circular")
                } else {
                    assertTrue(disconnect.boundsInRoot.width > disconnect.boundsInRoot.height * 2f,
                        "Compact control must be a labeled, wide button")
                }
                assertTrue(disconnect.config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, toggles)
                assertEquals(0, imports)
            }
        }
    }

    @Test fun subscriptionActionsAreIndependentAndProviderDescriptionIsVisible() {
        var pings = 0
        var refreshes = 0
        var selections = 0
        val state = NimboUiState(profileCount = 1, serverCount = 1, activeProfileName = "Provider",
            profileAnnounce = "Provider information only", servers = listOf(NimboServerUi("one", "Amsterdam", "vless")))
        val actions = NimboUiActions(onPingAll = { pings++ }, onRefreshProfile = { refreshes++ }, onSelectServer = { selections++ })
        ImageComposeScene(390, 844) { NimboAppShell(NimboScreen.PROFILES, state, actions) }.use { scene ->
            scene.settle()
            assertTrue("Provider information only" in scene.texts())
            assertFalse("Amsterdam" in scene.texts())
            scene.clickLabel("Проверить пинг")
            scene.clickLabel("Обновить подписку")
            assertEquals(1, pings)
            assertEquals(1, refreshes)
            assertEquals(0, selections)
            assertFalse("Amsterdam" in scene.texts())
            scene.clickLabel("Информация")
            assertTrue("Provider information only" in scene.texts())
            assertEquals(0, selections)
        }
    }

    @Test fun subscriptionHeaderExpandsAndCollapsesWithoutConnecting() {
        var connects = 0
        val state = NimboUiState(profileCount = 1, serverCount = 1, activeProfileName = "Provider",
            servers = listOf(NimboServerUi("one", "Amsterdam", "vless")))
        ImageComposeScene(390, 844) { NimboAppShell(NimboScreen.PROFILES, state, NimboUiActions(onToggleVpn = { connects++ })) }.use { scene ->
            scene.settle()
            for (expected in listOf(true, false)) {
                val header = scene.nodes().first { it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Серверы ") == true }
                header.config[SemanticsActions.OnClick].action!!.invoke()
                scene.settle()
                assertEquals(expected, "Amsterdam" in scene.texts())
            }
            assertEquals(0, connects)
        }
    }
}
