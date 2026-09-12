package com.danila.nimbo.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.danila.nimbo.utils.PreferencesManager

/**
 * Единый источник пользовательских настроек внутри Compose-дерева.
 *
 * Экран темы должен менять этот же экземпляр, из которого Activity читает
 * параметры NebulaGuardTheme. Иначе изменения могли ждать синхронизации между
 * двумя наблюдателями SharedPreferences и становились заметны только после
 * повторного запуска приложения.
 */
val LocalPreferencesManager = staticCompositionLocalOf<PreferencesManager> {
    error("LocalPreferencesManager не предоставлен")
}
