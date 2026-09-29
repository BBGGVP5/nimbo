package com.danila.nimbo.shared.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.use
import com.danila.nimbo.shared.routing.NimboModule
import com.danila.nimbo.shared.routing.NimboRoutingProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Executes production accessibility handlers. No copied controls, reflection or production hooks. */
class NimboSettingsRedesignInteractionTest {
    private data class Call(val action: String, val values: List<String> = emptyList())
    private fun call(action: String, vararg values: String) = Call(action, values.toList())
    private fun SemanticsNode.texts() = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun SemanticsNode.hasLabel(label: String): Boolean =
        label in config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private inner class Fixture(theme: String = "dark") {
        val calls = mutableListOf<Call>()
        val state = mutableStateOf(NimboUiState(
            appearance = NimboAppearance(themeMode = theme, textScale = 1.25f, haptics = false),
            elementStyle = "glass", backgroundMotion = false, statusParticles = false,
            pingTimeoutMs = 1500, pingUrl = "https://probe.example.test/original",
            appVersion = "1.3.0-beta.1", updateVersion = "1.3.0-beta.2",
            updateStatus = "Доступна тестовая сборка", updateDownloadStatus = "Загружено 37%",
            updateNotes = "Test release notes: keep existing settings.", updateChannel = "beta", updateNotify = true
        ))
        private fun record(action: String, vararg values: String) { calls += call(action, *values) }
        // Instrument every bridge callback, including unrelated operations, so extra actions fail assertions.
        val actions = NimboUiActions(
            onToggleVpn = { record("onToggleVpn") }, onAddProfile = { record("onAddProfile") },
            onRefreshProfile = { record("onRefreshProfile") }, onOpenProfileSettings = { record("onOpenProfileSettings") },
            onSelectServer = { record("onSelectServer", it) }, onSaveAppRule = { record("onSaveAppRule", it) },
            onOpenDiagnostics = { record("onOpenDiagnostics") }, onOpenAbout = { record("onOpenAbout") },
            onOpenSystemSettings = { record("onOpenSystemSettings") }, onOpenUrl = { record("onOpenUrl", it) },
            onToggleFavorite = { record("onToggleFavorite", it) }, onPingServer = { record("onPingServer", it) },
            onPingAll = { record("onPingAll") }, onConnectFastest = { record("onConnectFastest") },
            onSetRouting = { key, value -> record("onSetRouting", key, value) },
            onOpenScreen = { record("onOpenScreen", it) },
            onSetAppearance = { key, value ->
                record("onSetAppearance", key, value)
                if (key == "themeMode") state.value = state.value.copy(appearance = state.value.appearance.copy(themeMode = value))
            },
            onSetPing = { key, value ->
                record("onSetPing", key, value)
                state.value = when (key) {
                    "protocol" -> state.value.copy(pingProtocol = value)
                    "timeoutMs" -> state.value.copy(pingTimeoutMs = value.toInt())
                    "display" -> state.value.copy(pingDisplay = value)
                    "url" -> state.value.copy(pingUrl = value)
                    else -> error("Unexpected ping preference: $key")
                }
            },
            onSaveModule = { id, name, text -> record("onSaveModule", id, name, text) },
            onToggleModule = { record("onToggleModule", it) }, onDeleteModule = { record("onDeleteModule", it) },
            onCopyText = { record("onCopyText", it) }, onExportModule = { name, text -> record("onExportModule", name, text) },
            onSelectRoutingProfile = { record("onSelectRoutingProfile", it) },
            onSaveRoutingProfile = { record("onSaveRoutingProfile", it.toString()) },
            onResetRoutingProfiles = { record("onResetRoutingProfiles") }, onDismissToast = { record("onDismissToast") },
            onDeleteNotification = { record("onDeleteNotification", it) }, onClearNotifications = { record("onClearNotifications") },
            onOpenUpdate = { record("onOpenUpdate") }, onCheckUpdate = { record("onCheckUpdate") },
            onDownloadUpdate = { record("onDownloadUpdate") },
            onSetUpdate = { key, value ->
                record("onSetUpdate", key, value)
                state.value = when (key) {
                    "channel" -> state.value.copy(updateChannel = value)
                    "notify" -> state.value.copy(updateNotify = value.toBooleanStrict())
                    else -> error("Unexpected update preference: $key")
                }
            },
            onImportSubscription = { record("onImportSubscription", it) }, onImportClipboard = { record("onImportClipboard") },
            onImportFile = { record("onImportFile") }, onScanQr = { record("onScanQr") },
            onExportBackup = { record("onExportBackup") }, onImportBackup = { record("onImportBackup") },
            onOpenSync = { record("onOpenSync") }
        )
        private var frameTime = 0L
        fun scene(screen: NimboScreen = NimboScreen.SETTINGS, height: Int = 480) = ImageComposeScene(320, height) {
            NimboAppShell(screen, state.value, actions, showBottomBar = false)
        }
        fun ImageComposeScene.settle() {
            // Monotonic time also settles focus-driven scrolling after a selector opens.
            repeat(3) { render(frameTime).close(); frameTime += 1_000_000_000L }
        }
        fun ImageComposeScene.nodes(): List<SemanticsNode> =
            semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        fun ImageComposeScene.texts() = nodes().flatMap { it.texts() }
        fun ImageComposeScene.revealAction(node: SemanticsNode): SemanticsNode {
            // Production keyboard focus requests bring off-screen controls into their scroll viewport.
            node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            settle()
            // Restored modal focus may already be on this control, so RequestFocus alone
            // need not bring it into view again. Exercise its real scroll ancestor too.
            repeat(4) {
                val current = nodes().single { it.id == node.id }
                if (current.boundsInRoot.width > 0f && current.boundsInRoot.height >= 44f) return@repeat
                val scrollParent = generateSequence(current.parent) { it.parent }
                    .firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy)?.action != null }
                assertNotNull(scrollParent, "Clipped action has no scroll ancestor: ${current.texts()}")
                val delta = current.positionInRoot.y + current.size.height / 2f - scrollParent.boundsInRoot.center.y
                assertTrue(scrollParent.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta))
                settle()
            }
            val visible = nodes().single { it.id == node.id }
            assertTrue(visible.boundsInRoot.width > 0f && visible.boundsInRoot.height >= 44f,
                "Action is not reachable after focus/scroll: ${visible.texts()} ${visible.boundsInRoot}")
            return visible
        }
        fun ImageComposeScene.dialogNodes(): List<SemanticsNode> {
            val owner = semanticsOwners.lastOrNull { owner ->
                owner.getAllSemanticsNodes(mergingEnabled = true).any { it.config.getOrNull(SemanticsProperties.IsDialog) != null }
            }
            return assertNotNull(owner, "Expected a production Dialog semantics owner").getAllSemanticsNodes(mergingEnabled = true)
        }
        fun ImageComposeScene.clickText(label: String, role: Role? = null) {
            val matches = nodes().filter {
                label in it.texts() && it.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
                    (role == null || it.config.getOrNull(SemanticsProperties.Role) == role)
            }
            val node = matches.singleOrNull()
            assertNotNull(node, "Expected one clickable '$label'; found ${matches.size}. Texts: ${texts()}")
            assertTrue(revealAction(node).config[SemanticsActions.OnClick].action!!.invoke(), "OnClick rejected '$label'")
            settle()
        }
        fun ImageComposeScene.clickLabel(label: String) {
            val node = nodes().singleOrNull { it.hasLabel(label) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
            assertNotNull(node, "Expected clickable contentDescription '$label'")
            assertTrue(revealAction(node).config[SemanticsActions.OnClick].action!!.invoke())
            settle()
        }
        fun ImageComposeScene.setField(label: String, value: String) {
            val node = nodes().singleOrNull { it.hasLabel(label) && it.config.getOrNull(SemanticsActions.SetText)?.action != null }
            assertNotNull(node, "Expected the real editable field: $label")
            assertTrue(node.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
            settle()
        }
        fun ImageComposeScene.setUrl(value: String) = setField("URL проверки пинга", value)
        fun ImageComposeScene.clickDialogText(label: String) {
            val node = dialogNodes().single { label in it.texts() && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
            assertTrue(revealAction(node).config[SemanticsActions.OnClick].action!!.invoke())
            settle()
        }
        fun ImageComposeScene.assertDialogActionVisible(label: String) {
            val node = dialogNodes().single { label in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) != null }
            val bounds = node.boundsInRoot
            assertTrue(bounds.width > 0 && bounds.height >= 44f && bounds.left >= 0 && bounds.top >= 0 &&
                bounds.right <= 320f && bounds.bottom <= 480f, "Dialog action '$label' clipped: $bounds")
        }
        fun ImageComposeScene.assertClosed() {
            assertFalse(nodes().any { it.config.getOrNull(SemanticsProperties.IsDialog) != null }, "Dialog remained open")
        }
        fun ImageComposeScene.snapshot(name: String) {
            val output = File("build/reports/ui-1.3.0-redesign/shared-settings").apply { mkdirs() }
            render(frameTime).use { image ->
                frameTime += 1_000_000_000L
                val bytes = assertNotNull(image.encodeToData()).use { it.bytes }
                assertTrue(bytes.size > 3000, "Empty screenshot: $name")
                File(output, "$name.png").writeBytes(bytes)
            }
        }
    }

    @Test fun everySettingsSubpageOpensAndReturnsWithoutCallingTheBridge() = with(Fixture()) {
        scene().use { scene ->
            scene.settle()
            val pages = listOf("Общие" to "Виброотклик", "Внешний вид" to "Акцентный цвет",
                "Подписка" to "Обновлять при запуске", "Пинг серверов" to "Контрольный URL",
                "Резервная копия" to "Восстановить из файла", "Обновления" to "Скачать файл сборки",
                "О приложении" to "Устройство")
            for ((page, marker) in pages) {
                scene.clickText(page)
                assertTrue(marker in scene.texts(), "Missing body of $page")
                scene.clickText("‹ Настройки")
                assertTrue("Подключение" in scene.texts(), "Did not return from $page")
                assertEquals(emptyList(), calls, "Navigation to $page must not persist or start an operation")
            }
            scene.clickText("Доступна 1.3.0-beta.2")
            assertTrue("Скачать файл сборки" in scene.texts())
            assertEquals(emptyList(), calls, "The update banner opens details without downloading")
        }
    }

    @Test fun pingSelectorsSaveExactlyOneOriginalKeyValueAndCancelDoesNotSave() = with(Fixture()) {
        scene().use { scene ->
            scene.settle(); scene.clickText("Пинг серверов")
            val expected = mutableListOf<Call>()
            for ((label, key) in listOf("Nimbo Ping" to "nimbo", "TCP до узла" to "tcp",
                "HTTP GET" to "http_get", "HTTP HEAD" to "http_head", "ICMP Ping" to "icmp")) {
                scene.clickText("Метод")
                scene.clickText("Закрыть")
                assertEquals(expected, calls)
                scene.clickText("Метод")
                scene.clickText(label, Role.RadioButton)
                expected += call("onSetPing", "protocol", key)
                assertEquals(expected, calls)
                assertEquals(key, state.value.pingProtocol)
                scene.assertClosed()
            }
            scene.clickText("Таймаут")
            assertTrue("1.5 с" in scene.dialogNodes().flatMap { it.texts() }, "Custom persisted timeout disappeared")
            scene.clickText("Закрыть")
            assertEquals(1500, state.value.pingTimeoutMs)
            assertEquals(expected, calls)
            for (millis in listOf(1000, 2000, 3000, 5000, 10000, 1500)) {
                // 1500 remains an offered value only until the first preset is saved.
                if (millis == 1500) state.value = state.value.copy(pingTimeoutMs = millis)
                scene.settle(); scene.clickText("Таймаут")
                scene.clickText("${millis / 1000.0} с", Role.RadioButton)
                expected += call("onSetPing", "timeoutMs", millis.toString())
                assertEquals(expected, calls)
                assertEquals(millis, state.value.pingTimeoutMs)
            }
            for ((label, key) in listOf("Цифры (мс)" to "numeric", "Шкала" to "bars", "Шкала и цифры" to "both", "Точки" to "dots")) {
                scene.clickText("Отображение"); scene.clickText(label, Role.RadioButton)
                expected += call("onSetPing", "display", key)
                assertEquals(expected, calls)
            }
        }
    }

    @Test fun customUrlDraftCancelValidationSaveAndPresetUseProductionField() = with(Fixture()) {
        scene().use { scene ->
            scene.settle(); scene.clickText("Пинг серверов")
            scene.setUrl("https://draft.example.test/discard")
            assertEquals(emptyList(), calls, "Editing is not saving")
            scene.clickText("‹ Настройки"); scene.clickText("Пинг серверов")
            val field = scene.nodes().single { it.hasLabel("URL проверки пинга") && it.config.getOrNull(SemanticsActions.SetText) != null }
            assertEquals("https://probe.example.test/original", field.config[SemanticsProperties.EditableText].text)
            scene.setUrl("https://")
            val save = scene.nodes().single { "Сохранить URL" in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue(save.config.getOrNull(SemanticsProperties.Disabled) != null, "An incomplete host must disable Save")
            assertEquals(emptyList(), calls)
            scene.setUrl("  https://probe.example.test/health?check=1  ")
            scene.clickText("Сохранить URL")
            val expected = mutableListOf(call("onSetPing", "url", "https://probe.example.test/health?check=1"))
            assertEquals(expected, calls)
            assertEquals("https://probe.example.test/health?check=1", state.value.pingUrl)
            scene.clickText("Контрольный URL"); scene.clickText("Закрыть")
            assertEquals(expected, calls)
            scene.clickText("Контрольный URL"); scene.clickText("Cloudflare", Role.RadioButton)
            expected += call("onSetPing", "url", "https://cp.cloudflare.com/generate_204")
            assertEquals(expected, calls)
            assertEquals("https://cp.cloudflare.com/generate_204", state.value.pingUrl)
            scene.assertClosed()
        }
    }

    @Test fun updateActionsAndChannelAreIndependentAndStatusUsesRealState() = with(Fixture()) {
        scene().use { scene ->
            scene.settle(); scene.clickText("Обновления")
            assertTrue("Загружено 37%" in scene.texts())
            val progress = scene.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) }.single()
            assertEquals(0.37f, progress.current)
            assertEquals(emptyList(), calls)
            scene.clickText("Проверить обновление")
            scene.clickText("Скачать файл сборки")
            scene.clickText("Страница релиза")
            val expected = mutableListOf(call("onCheckUpdate"), call("onDownloadUpdate"), call("onOpenUpdate"))
            assertEquals(expected, calls)
            scene.clickText("Сборки"); scene.clickText("Закрыть")
            assertEquals(expected, calls)
            scene.clickText("Сборки"); scene.clickText("Стабильный", Role.RadioButton)
            expected += call("onSetUpdate", "channel", "stable")
            assertEquals(expected, calls)
            assertEquals("stable", state.value.updateChannel)
            // The notify label is a parent semantics node; activate its real Switch child.
            val toggle = scene.nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
            assertTrue(toggle.config[SemanticsActions.OnClick].action!!.invoke())
            scene.settle()
            expected += call("onSetUpdate", "notify", "false")
            assertEquals(expected, calls)
            assertFalse(state.value.updateNotify)
            state.value = state.value.copy(updateDownloadStatus = "Не удалось скачать: test connection error")
            scene.settle()
            assertTrue("Не удалось скачать: test connection error" in scene.texts())
            assertFalse(scene.nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null })
            assertEquals(expected, calls, "Rendering an error must not retry or save a preference")
        }
    }

    @Test fun systemSubscriptionBackupAndAppearanceActionsKeepTheirCallbacks() = with(Fixture()) {
        scene().use { scene ->
            scene.settle()
            val expected = mutableListOf<Call>()
            val pages = listOf(
                "Общие" to listOf("Системные настройки VPN" to "onOpenSystemSettings", "Перенос с другого устройства" to "onOpenSync"),
                "Подписка" to listOf("Настройки подписки" to "onOpenProfileSettings", "Обновить сейчас" to "onRefreshProfile"),
                "Резервная копия" to listOf("Сохранить копию" to "onExportBackup", "Восстановить из файла" to "onImportBackup"),
                "О приложении" to listOf("О приложении" to "onOpenAbout", "Диагностика" to "onOpenDiagnostics", "Язык и уведомления" to "onOpenSystemSettings")
            )
            for ((page, links) in pages) {
                scene.clickText(page)
                for ((label, action) in links) {
                    scene.clickText(label); expected += call(action); assertEquals(expected, calls)
                }
                scene.clickText("‹ Настройки")
            }
            scene.clickText("Маршрутизация")
            expected += call("onOpenScreen", "routing")
            assertEquals(expected, calls)
            scene.clickText("‹ Настройки"); expected += call("onOpenScreen", "settings")
            scene.clickText("О приложении"); scene.clickText("Уведомления")
            expected += call("onOpenScreen", "notifications")
            assertEquals(expected, calls)
            scene.clickText("‹ Настройки"); expected += call("onOpenScreen", "settings")
            scene.clickText("Внешний вид")
            scene.clickText("Режим"); scene.clickText("Закрыть")
            assertEquals(expected, calls)
            scene.clickText("Режим"); scene.clickText("Светлая", Role.RadioButton)
            expected += call("onSetAppearance", "themeMode", "light")
            assertEquals(expected, calls)
            assertEquals("light", state.value.appearance.themeMode)
        }
    }

    @Test fun connectionHistoryOpensOnlyStatsWithoutChangingPreferencesOrVpn() = with(Fixture()) {
        scene().use { scene ->
            scene.settle(); scene.clickText("Общие")
            val before = state.value
            assertEquals(emptyList(), calls, "Opening General must not start an operation")
            scene.clickText("История подключений")
            assertEquals(listOf(call("onOpenScreen", NimboScreen.STATS.wireName)), calls,
                "History must emit exactly one stats route and no other bridge callback")
            assertEquals(before, state.value, "Opening history must not change settings or VPN state")
        }
    }

    @Test fun descriptionsStayInInfoAndClosingInfoDoesNotSaveOrPing() = with(Fixture()) {
        scene().use { scene ->
            scene.settle(); scene.clickText("Пинг серверов")
            assertFalse(scene.texts().any { "а не точное RTT" in it })
            scene.clickLabel("Информация: Метод")
            assertTrue(scene.dialogNodes().flatMap { it.texts() }.any { "а не точное RTT" in it })
            scene.clickText("Закрыть"); scene.assertClosed()
            assertEquals(emptyList(), calls)
        }
    }

    @Test fun lightAndDarkUpdateDialogsFitSmallLargeTextViewportWithoutStartingDownload() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            scene().use { scene ->
                scene.settle(); scene.clickText("Обновления")
                scene.snapshot("$theme-320x480-text125-update-progress")
                for ((trigger, title, name) in listOf(
                    Triple("Сборки", "Сборки", "channel"),
                    Triple("Информация: Страница релиза", "Страница релиза", "release-info")
                )) {
                    if (name == "channel") scene.clickText(trigger) else scene.clickLabel(trigger)
                    val modal = scene.dialogNodes()
                    val close = modal.single { "Закрыть" in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    val header = modal.single { title in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) == null }
                    val body = modal.single { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                    for ((label, node) in listOf("header" to header, "close" to close, "body" to body)) {
                        val bounds = node.boundsInRoot
                        assertTrue(bounds.width > 0f && bounds.height > 0f && bounds.left >= 0f && bounds.top >= 0f &&
                            bounds.right <= 320f && bounds.bottom <= 480f, "$theme update $name $label is clipped: $bounds")
                    }
                    assertTrue(close.boundsInRoot.height >= 44f)
                    assertTrue(body.boundsInRoot.top >= header.boundsInRoot.bottom && body.boundsInRoot.bottom <= close.boundsInRoot.top)
                    scene.snapshot("$theme-320x480-text125-update-$name-modal")
                    scene.clickText("Закрыть"); scene.assertClosed()
                    assertEquals(emptyList(), calls, "Update modal cancellation must not download, check, open a URL or change channel")
                }
                state.value = state.value.copy(updateDownloadStatus = "Не удалось скачать: test connection error")
                scene.settle()
                assertTrue("Не удалось скачать: test connection error" in scene.texts())
                scene.snapshot("$theme-320x480-text125-update-error")
                assertEquals(emptyList(), calls)
            }
        }
    }

    @Test fun lightAndDarkSmallLargeTextSelectorKeepsHeaderAndCloseOutsideScrollingBody() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            scene(height = 320).use { scene ->
                scene.settle(); scene.clickText("Внешний вид"); scene.clickText("Цвет")
                fun closeNode() = scene.dialogNodes().single { "Закрыть" in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) != null }
                fun headerNode() = scene.dialogNodes().single { "Цвет" in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) == null }
                fun bodyNode() = scene.dialogNodes().single { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                fun assertViewport(bounds: Rect, label: String) {
                    assertTrue(bounds.width > 0f && bounds.height > 0f, "$theme $label is clipped away")
                    assertTrue(bounds.left >= 0f && bounds.top >= 0f && bounds.right <= 320f && bounds.bottom <= 320f,
                        "$theme $label is outside 320×320: $bounds")
                }
                val closeBefore = closeNode().boundsInRoot
                val headerBefore = headerNode().boundsInRoot
                assertViewport(closeBefore, "close"); assertViewport(headerBefore, "header")
                assertTrue(closeBefore.height >= 44f, "Close must retain its 44dp target")
                val bodyBounds = bodyNode().boundsInRoot
                assertTrue(bodyBounds.top >= headerBefore.bottom && bodyBounds.bottom <= closeBefore.top,
                    "The body must fit between fixed header and footer")
                assertTrue(bodyNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
                assertTrue(bodyNode().config[SemanticsActions.ScrollBy].action!!.invoke(0f, -10_000f))
                scene.settle()
                scene.snapshot("$theme-320x320-text125-selector-top")
                val before = bodyNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                assertTrue(bodyNode().config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f))
                scene.settle()
                assertTrue(bodyNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before)
                assertEquals(closeBefore, closeNode().boundsInRoot, "Close moved with the body")
                assertEquals(headerBefore, headerNode().boundsInRoot, "Header moved with the body")
                val lastChoice = scene.dialogNodes().single { "Терракота" in it.texts() && it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                assertViewport(lastChoice.boundsInRoot, "last option")
                scene.snapshot("$theme-320x320-text125-selector-bottom")
                scene.clickText("Закрыть"); scene.assertClosed()
                assertEquals(emptyList(), calls, "Opening, scrolling and cancelling must not save an appearance key")
            }
        }
    }

    @Test fun themeColorAndTextControlsStayReachableWithoutLegacyLayouts() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
        scene().use { scene ->
            scene.settle(); scene.clickText("Внешний вид")
            val expected = mutableListOf<Call>()
            for ((row, label, key, value) in listOf(
                listOf("Цвет", "Терракота", "accentHex", "C77E67")
            )) {
                scene.clickText(row); scene.clickText("Закрыть")
                assertEquals(expected, calls)
                scene.clickText(row); scene.clickText(label, Role.RadioButton)
                expected += call("onSetAppearance", key, value)
                assertEquals(expected, calls)
            }
            scene.setField("Акцентный цвет HEX", "zzzzzz")
            assertEquals(expected, calls)
            val disabled = scene.nodes().single { it.hasLabel("Сохранить цвет") }
            assertTrue(disabled.config.getOrNull(SemanticsProperties.Disabled) != null)
            scene.setField("Акцентный цвет HEX", "123456"); scene.clickLabel("Сохранить цвет")
            expected += call("onSetAppearance", "accentHex", "123456")
            scene.clickLabel("Сбросить цвет")
            expected += call("onSetAppearance", "accentHex", "E8E8E8")
            for ((label, key, value) in listOf(
                Triple("Масштаб текста", "textScale", 1.1f)
            )) {
                val slider = scene.nodes().single { it.hasLabel(label) && it.config.getOrNull(SemanticsActions.SetProgress) != null }
                assertTrue(slider.config[SemanticsActions.SetProgress].action!!.invoke(value))
                scene.settle()
                expected += call("onSetAppearance", key, value.toString())
                assertEquals(expected, calls)
            }
            for (retired in listOf("Стиль интерфейса", "Стиль подключения", "Анимация фона", "Прозрачность стекла", "Скругление элементов")) {
                assertFalse(retired in scene.texts(), "Retired layout control returned: $retired")
            }
            assertEquals(expected, calls)
            scene.snapshot("advanced-$theme-appearance-details")
        }
        }
    }

    @Test fun compactConnectionSettingUsesAppearanceBridge() = with(Fixture()) {
        scene().use { scene ->
            scene.settle()
            scene.clickText("Внешний вид")
            scene.clickText("Компактная кнопка подключения", Role.Switch)
            assertEquals(listOf(call("onSetAppearance", "connectStyle", "compact")), calls)
        }
    }

    @Test fun routingDnsAndNamedSwitchActionsAreIndependent() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            state.value = state.value.copy(routingBypassLocal = false, routingSniffing = true)
            scene(NimboScreen.ROUTING).use { scene ->
                scene.settle(); scene.clickText("Обход локальных сетей", Role.Switch)
                scene.clickText("Определение доменов", Role.Switch)
                val expected = mutableListOf(call("onSetRouting", "bypassLocal", "true"), call("onSetRouting", "sniffing", "false"))
                assertEquals(expected, calls)
                for ((title, key) in listOf("Cloudflare" to "cloudflare", "Google" to "google", "AdGuard" to "adguard", "Системный" to "system")) {
                    scene.clickText("Набор серверов"); scene.clickText("Закрыть")
                    assertEquals(expected, calls)
                    scene.clickText("Набор серверов")
                    if (key == "adguard") scene.snapshot("advanced-$theme-routing-dns")
                    scene.clickText(title, Role.RadioButton)
                    expected += call("onSetRouting", "dns", key)
                    assertEquals(expected, calls)
                }
                scene.clickText("Профили"); expected += call("onOpenScreen", "routing-profiles")
                scene.clickText("‹ Маршрутизация"); expected += call("onOpenScreen", "routing")
                scene.clickText("Модули"); expected += call("onOpenScreen", "modules")
                assertEquals(expected, calls)
            }
        }
    }

    @Test fun moduleEditorPreservesDraftsAndConfirmsDeletionWithoutSavingOnCancel() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            val module = NimboModule("module-fixture", "Длинное имя модуля для домашней сети и рабочего подключения ".repeat(4), true,
                "#!desc=Only in module info\nDOMAIN-SUFFIX,example.test,DIRECT\nnot a valid rule")
            state.value = state.value.copy(modules = listOf(module))
            scene(NimboScreen.MODULES).use { scene ->
                scene.settle(); scene.snapshot("advanced-$theme-module-list-long")
                assertFalse("Only in module info" in scene.texts())
                scene.clickLabel("Информация: ${module.name}")
                assertTrue("Only in module info" in scene.dialogNodes().flatMap { it.texts() })
                scene.assertDialogActionVisible("Закрыть"); scene.snapshot("advanced-$theme-module-info-long-title")
                scene.clickText("Закрыть")
                assertEquals(emptyList(), calls)
                scene.clickText("Модуль включён", Role.Switch)
                val expected = mutableListOf(call("onToggleModule", module.id))
                assertEquals(expected, calls)
                scene.clickLabel("Редактировать: ${module.name}")
                scene.setField("Название модуля", "Edited module")
                scene.setField("Правила маршрутизации модуля", "DOMAIN-SUFFIX,updated.test,PROXY")
                scene.clickText("‹ Модули")
                scene.assertDialogActionVisible("Не сохранять"); scene.assertDialogActionVisible("Отмена")
                scene.snapshot("advanced-$theme-module-discard")
                scene.clickDialogText("Отмена")
                assertTrue(scene.nodes().any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Edited module" })
                assertEquals(expected, calls)
                scene.clickLabel("Копировать правила"); expected += call("onCopyText", "DOMAIN-SUFFIX,updated.test,PROXY")
                scene.clickLabel("Экспортировать модуль"); expected += call("onExportModule", "Edited module", "DOMAIN-SUFFIX,updated.test,PROXY")
                scene.clickText("Сохранить модуль"); expected += call("onSaveModule", module.id, "Edited module", "DOMAIN-SUFFIX,updated.test,PROXY")
                assertEquals(expected, calls)
                scene.clickLabel("Редактировать: ${module.name}"); scene.clickLabel("Удалить модуль")
                scene.assertDialogActionVisible("Удалить"); scene.assertDialogActionVisible("Отмена")
                scene.snapshot("advanced-$theme-module-delete-long")
                scene.clickDialogText("Отмена"); assertEquals(expected, calls)
                scene.clickLabel("Удалить модуль"); scene.clickDialogText("Удалить")
                expected += call("onDeleteModule", module.id)
                assertEquals(expected, calls)
                scene.clickText("Новый модуль")
                scene.setField("Название модуля", "New module")
                scene.clickText("‹ Модули"); scene.clickDialogText("Не сохранять")
                scene.assertClosed(); assertEquals(expected, calls, "Discarding a new draft must not create a module")
            }
        }
    }

    @Test fun routingProfileEditorSavesAllRuleListsOrderAndStrategyOnlyOnSave() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            val profile = NimboRoutingProfile("routing-fixture", "Рабочая маршрутизация с очень длинным названием для маленького экрана",
                "Profile info only", builtin = true)
            state.value = state.value.copy(routingProfiles = listOf(profile), routingProfileId = "other")
            scene(NimboScreen.ROUTING_PROFILES).use { scene ->
                scene.settle(); scene.snapshot("advanced-$theme-routing-profile-list")
                scene.clickText(profile.name, Role.RadioButton)
                val expected = mutableListOf(call("onSelectRoutingProfile", profile.id))
                scene.clickLabel("Информация: ${profile.name}")
                assertTrue("Profile info only" in scene.dialogNodes().flatMap { it.texts() })
                scene.assertDialogActionVisible("Закрыть"); scene.clickText("Закрыть")
                assertEquals(expected, calls)
                scene.clickLabel("Редактировать: ${profile.name}")
                scene.setField("Название", "Edited routing")
                scene.setField("Описание", "Edited description")
                val fields = listOf("Сайты напрямую" to "domain:direct.test", "IP напрямую" to "192.0.2.0/24",
                    "Сайты через VPN" to "domain:proxy.test", "IP через VPN" to "198.51.100.0/24",
                    "Блокируемые сайты" to "domain:blocked.test", "Блокируемые IP" to "203.0.113.0/24")
                for ((label, value) in fields) scene.setField(label, "  $value  \n\n")
                scene.clickText("Через VPN", Role.Switch); scene.clickText("Локальные адреса напрямую", Role.Switch)
                scene.clickText("Стратегия доменов"); scene.snapshot("advanced-$theme-routing-strategy")
                scene.clickText("AsIs", Role.RadioButton)
                scene.clickText("Порядок правил"); scene.snapshot("advanced-$theme-routing-order")
                scene.clickText("Блокировка → напрямую → VPN", Role.RadioButton)
                scene.clickText("‹ Профили"); scene.snapshot("advanced-$theme-routing-discard")
                scene.clickDialogText("Отмена"); assertEquals(expected, calls, "Editing never persists before Save")
                scene.clickText("Сохранить профиль")
                val saved = profile.copy(name = "Edited routing", description = "Edited description", globalProxy = false,
                    bypassLocalIp = false, domainStrategy = "AsIs", ruleOrder = "block-direct-proxy",
                    directSites = listOf(fields[0].second), directIp = listOf(fields[1].second),
                    proxySites = listOf(fields[2].second), proxyIp = listOf(fields[3].second),
                    blockSites = listOf(fields[4].second), blockIp = listOf(fields[5].second))
                expected += call("onSaveRoutingProfile", saved.toString())
                assertEquals(expected, calls)
                scene.clickText("Вернуть исходные профили")
                scene.assertDialogActionVisible("Восстановить"); scene.snapshot("advanced-$theme-routing-reset")
                scene.clickDialogText("Отмена"); assertEquals(expected, calls)
                scene.clickText("Вернуть исходные профили"); scene.clickDialogText("Восстановить")
                expected += call("onResetRoutingProfiles"); assertEquals(expected, calls)
            }
        }
    }

    @Test fun importDialogHasFixedSubmitAndIndependentClipboardFileQrCallbacks() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            scene(NimboScreen.PROFILES).use { scene ->
                scene.settle(); scene.clickText("Ввести ссылку")
                scene.assertDialogActionVisible("Импортировать"); scene.assertDialogActionVisible("Закрыть")
                scene.snapshot("advanced-$theme-import-empty")
                scene.setField("Ссылка или конфигурация профиля", "vless://fixture@example.test:443#example")
                scene.clickDialogText("Закрыть"); assertEquals(emptyList(), calls)
                scene.clickText("Ввести ссылку")
                val field = scene.nodes().single { it.hasLabel("Ссылка или конфигурация профиля") && it.config.getOrNull(SemanticsActions.SetText) != null }
                assertEquals("", field.config[SemanticsProperties.EditableText].text)
                val config = "{\n  \"outbounds\": []\n}"
                scene.setField("Ссылка или конфигурация профиля", "  $config  ")
                scene.snapshot("advanced-$theme-import-multiline")
                scene.assertDialogActionVisible("Импортировать")
                scene.clickDialogText("Импортировать")
                val expected = mutableListOf(call("onImportSubscription", config))
                assertEquals(expected, calls)
                for ((label, action) in listOf("Вставить из буфера" to "onImportClipboard", "Открыть файл" to "onImportFile", "Сканировать QR-код" to "onScanQr")) {
                    scene.clickText("Ввести ссылку"); scene.clickDialogText(label)
                    expected += call(action); assertEquals(expected, calls); scene.assertClosed()
                }
            }
        }
    }

    @Test fun notificationFiltersClearConfirmationAndLongToastStayIndependent() {
        for (theme in listOf("light", "dark")) with(Fixture(theme)) {
            val messages = NimboNotificationKind.entries.mapIndexed { index, kind ->
                NimboNotification("notice-$index", "${kind.title}: подробное сообщение с длинным заголовком ".repeat(3),
                    "Сообщение $index. ".repeat(35), kind, 1L, "12:34")
            }
            state.value = state.value.copy(notifications = messages)
            scene(NimboScreen.NOTIFICATIONS).use { scene ->
                scene.settle(); scene.snapshot("advanced-$theme-notifications-long")
                for (kind in NimboNotificationKind.entries) {
                    scene.clickText("Показать"); scene.clickText("${kind.title} · 1", Role.RadioButton)
                    assertTrue(scene.texts().contains(messages.single { it.kind == kind }.message))
                    assertEquals(emptyList(), calls)
                }
                scene.clickText("Очистить всё")
                scene.assertDialogActionVisible("Очистить всё"); scene.assertDialogActionVisible("Отмена")
                scene.snapshot("advanced-$theme-notifications-clear")
                scene.clickDialogText("Отмена"); assertEquals(emptyList(), calls)
                val active = messages.single { it.kind == NimboNotificationKind.ACTIVITY }
                scene.clickLabel("Удалить уведомление: ${active.title}")
                val expected = mutableListOf(call("onDeleteNotification", active.id))
                assertEquals(expected, calls)
                scene.clickText("Очистить всё"); scene.clickDialogText("Очистить всё")
                expected += call("onClearNotifications"); assertEquals(expected, calls)
                state.value = state.value.copy(notifications = emptyList(), toast = messages[1])
                scene.settle(); scene.snapshot("advanced-$theme-toast-long-error")
                val close = scene.nodes().single { "Закрыть" in it.texts() && it.config.getOrNull(SemanticsActions.OnClick) != null }
                assertTrue(close.boundsInRoot.height >= 44f && close.boundsInRoot.bottom <= 480f)
                scene.clickText("Закрыть"); expected += call("onDismissToast")
                assertEquals(expected, calls)
            }
        }
    }

}
