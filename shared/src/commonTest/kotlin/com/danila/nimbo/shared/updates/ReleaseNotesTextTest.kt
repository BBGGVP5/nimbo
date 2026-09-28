package com.danila.nimbo.shared.updates

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseNotesTextTest {
    @Test fun removesPlatformWrappersButKeepsFeatureHeadings() {
        for (title in listOf("# 🤖 Что нового на Android", "# 🖥️ Что нового на Windows и Linux",
            "## Что изменилось на iOS", "**What's new on Desktop**", "## Android", "# iOS",
            "## Изменения для Windows", "## What changed on Linux")) {
            assertEquals("## 🔐 Протоколы\n- Исправлено подключение.",
                ReleaseNotesText.withoutPlatformHeading("$title\n\n## 🔐 Протоколы\n- Исправлено подключение."), title)
        }
    }

    @Test fun preservesFeatureTitlesBulletsAndCode() {
        val body = "## Android: восстановление сети\n- Android\n- Что нового на iOS\n```md\n# Что нового на Android\n```\n~~~md\n# iOS\n~~~"
        assertEquals(body, ReleaseNotesText.withoutPlatformHeading(body))
    }

    @Test fun readsOnlyIosTaggedNotes() {
        val body = "<!-- nimbo:android:start -->\n# Android\n- APK\n<!-- nimbo:android:end -->\n" +
            "<!-- nimbo:ios:start -->\n# 🍎 Что нового на iOS\n## Туннель\n- IPA\n<!-- nimbo:ios:end -->"
        assertEquals("## Туннель\n- IPA", ReleaseNotesText.forPlatform(body, "ios"))
    }

    @Test fun missingIosSectionDoesNotShowAndroidNotes() {
        assertEquals("", ReleaseNotesText.forPlatform("<!-- nimbo:android:start -->\n- APK\n<!-- nimbo:android:end -->", "ios"))
    }

    @Test fun cleansAlreadySavedNotesIdempotently() {
        val once = ReleaseNotesText.withoutPlatformHeading("**Что нового на Android**\n- Сеть")
        assertEquals("- Сеть", once)
        assertEquals(once, ReleaseNotesText.withoutPlatformHeading(once))
    }
}
