package com.danila.nimbo.ui.components

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalNebulaColors
import kotlinx.coroutines.delay

internal const val OPERATION_PHRASE_INTERVAL_MS = 4_500L
enum class NimboOperation { Connection, Download, Update, Sync }

internal fun operationPhrases(operation: NimboOperation): List<Pair<String, String>> = when (operation) {
    NimboOperation.Connection -> listOf(
        "Прокладываем путь…" to "Finding a path…",
        "Готовим облако…" to "Getting the cloud ready…",
        "Сверяем маршрут…" to "Checking the route…",
        "Настраиваем туннель…" to "Setting up the tunnel…",
        "Собираем соединение…" to "Putting the connection together…",
        "Проверяем сеть…" to "Checking the network…",
        "Готовим защищённый канал…" to "Preparing a secure channel…",
        "Подбираем путь для трафика…" to "Choosing a traffic path…",
        "Соединяем точки…" to "Connecting the dots…",
        "Настраиваем облачный маршрут…" to "Setting up the cloud route…"
    )
    NimboOperation.Download -> listOf("Забираем новую версию…" to "Fetching the new version…", "Ещё немного…" to "Just a little longer…", "Спасибо за терпение" to "Thanks for your patience")
    NimboOperation.Update -> listOf("Готовим обновление…" to "Preparing the update…", "Ещё немного…" to "Just a little longer…", "Спасибо за терпение" to "Thanks for your patience")
    NimboOperation.Sync -> listOf("Знакомим устройства…" to "Introducing your devices…", "Ещё немного…" to "Just a little longer…", "Спасибо за терпение" to "Thanks for your patience")
}

/** Secondary copy only: never a clock, progress estimate, or replacement for actual status. */
@Composable
fun NimboOperationPhrase(operation: NimboOperation, active: Boolean, modifier: Modifier = Modifier,
    rotate: Boolean = LocalBackgroundAnimationEnabled.current) {
    if (!active) return
    val owner = LocalLifecycleOwner.current
    val rotationEnabled = rotate && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
    var index by remember(operation) { mutableIntStateOf(0) }
    val phrases = remember(operation) { operationPhrases(operation) }
    LaunchedEffect(owner, operation, rotationEnabled) {
        index = 0
        if (rotationEnabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(OPERATION_PHRASE_INTERVAL_MS)
                index = (index + 1) % phrases.size
            }
        }
    }
    val phrase = phrases[index]
    Text(t(phrase.first, phrase.second), color = LocalNebulaColors.current.textSecondary,
        style = MaterialTheme.typography.bodySmall,
        // Keep changing decorative copy out of accessibility announcements; the real status remains accessible.
        modifier = modifier.testTag("operation-phrase").clearAndSetSemantics { })
}
