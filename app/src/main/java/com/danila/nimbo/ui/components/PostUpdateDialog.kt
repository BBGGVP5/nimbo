package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.screens.UpdateReleaseNotes
import com.danila.nimbo.ui.screens.UpdateUiText
import java.time.Instant

@Composable
fun PostUpdateDialog(
    versionName: String,
    changelog: String,
    installedAt: Long,
    onDismiss: () -> Unit,
    onShowChanges: () -> Unit
) {
    val language = LocalConfiguration.current.locales[0].language
    val installedDate = installedAt.takeIf { it > 0L }?.let {
        UpdateUiText.releaseDate(Instant.ofEpochMilli(it).toString(), language)
    }
    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = t("Nimbo обновлён", "Nimbo updated"),
        description = listOfNotNull(UpdateUiText.versionLabel(versionName, language), installedDate).joinToString(" · "),
        confirmButtonText = t("Продолжить", "Continue"),
        cancelButtonText = null,
        onConfirm = onDismiss
    ) {
        UpdateReleaseNotes(
            content = changelog.ifBlank {
                t(
                    "Для этой установки подробный список изменений не сохранён.",
                    "Detailed release notes were not saved for this installation."
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        NimboAction(Icons.Default.History, t("История изменений", "Release history"), onShowChanges, Modifier.fillMaxWidth())
    }
}
