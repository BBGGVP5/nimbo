package com.danila.nimbo.shared.updates

import kotlin.test.*

class InAppReleaseNotesTest {
    @Test fun removesGalleryAndDownloadsButPreservesFeaturesAndLimits() {
        val body = """
            <p><img src="logo.png"></p>
            ## Превью нового дизайна
            Нажмите на экран для просмотра.
            <table><tr><td>preview</td></tr></table>
            ## Новый интерфейс
            - Плавающая панель
            ## Скачать
            | APK | IPA |
            ## Ограничения
            NaiveProxy пока недоступен на iOS.
        """.trimIndent()
        val actual = ReleaseNotesText.forApp(body)
        assertTrue("Плавающая панель" in actual)
        assertTrue("NaiveProxy" in actual)
        assertFalse("preview" in actual)
        assertFalse("APK" in actual)
        assertFalse("Нажмите на экран" in actual)
    }
    @Test fun keepsCodeAndRegularFeatureHeadings() {
        val text = "## Изменения\n- Android\n```\n<example>\n```\n## DNS\n- Новый режим"
        assertEquals(ReleaseNotesText.withoutPlatformHeading(text), ReleaseNotesText.forApp(text))
    }
}
