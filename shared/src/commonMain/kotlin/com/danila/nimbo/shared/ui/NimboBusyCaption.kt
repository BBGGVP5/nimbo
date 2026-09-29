package com.danila.nimbo.shared.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Decorative copy only. Never used to derive a connection state or download progress. */
internal enum class NimboBusyKind(val captions: List<String>) {
    CONNECTION(listOf("Прокладываем путь…", "Готовим облако…", "Сверяем маршрут…",
        "Настраиваем туннель…", "Собираем соединение…", "Проверяем сеть…",
        "Готовим защищённый канал…", "Подбираем путь для трафика…",
        "Соединяем точки…", "Настраиваем облачный маршрут…")),
    DOWNLOAD(listOf("Забираем новую версию…", "Готовим свежий Nimbo…", "Наводим порядок…", "Ещё немного…")),
    SYNC(listOf("Собираем настройки…", "Готовим передачу…", "Всё по своим местам…", "Ещё немного…"))
}

internal fun busyCaption(kind: NimboBusyKind, step: Int): String =
    kind.captions[step.mod(kind.captions.size)]

@Composable
internal fun NimboBusyCaption(kind: NimboBusyKind, rotate: Boolean = true) {
    var step by remember(kind) { mutableIntStateOf(0) }
    LaunchedEffect(kind, rotate) {
        step = 0
        val motion = coroutineContext[MotionDurationScale]
        if (rotate) while (isActive && motion?.scaleFactor != 0f) {
            delay(4500)
            if (motion?.scaleFactor != 0f) step = (step + 1) % kind.captions.size
        }
    }
    // The primary status remains accessible; rotating flavor text must not interrupt VoiceOver.
    // No transition animation; disabling icon animation also freezes this secondary text.
    BasicText(busyCaption(kind, step), Modifier.clearAndSetSemantics { },
        style = NimboBodyStyle.copy(fontSize = 12.sp, color = NimboPalette.TextTertiary))
}
