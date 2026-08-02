package com.danila.nimbo.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import com.danila.nimbo.R

object AppIconManager {

    data class IconOption(
        val aliasSuffix: String,
        @DrawableRes val previewRes: Int,
        val title: String,
        val description: String,
        val backgroundColor: Int
    )

    private val SELECTABLE_ALIASES = listOf(
        "AliasDefault",
        "AliasSprite0000",
        "AliasSprite0001",
        "AliasSprite0002",
        "AliasSprite0004",
        "AliasSprite0005"
    )

    private val ALL_ALIAS_SUFFIXES = listOf(
        *SELECTABLE_ALIASES.toTypedArray(),
        "AliasSprite0006",
        "AliasSprite0007",
        "AliasSprite0008",
        "AliasSprite0009",
        "AliasSprite0010",
        "AliasSprite0011",
        "AliasSprite0012",
        "AliasSprite0013",
        "AliasSprite0014",
        "AliasSprite0015",
        "AliasSprite0016",
        "AliasSprite0017",
        "AliasSprite0018"
    )

    val ICON_OPTIONS = listOf(
        IconOption("AliasDefault", R.mipmap.ic_launcher_nimbo_blue_v2, "Nimbo Beta", "Фирменная синяя иконка с аккуратной отметкой Beta", 0xFF1769E0.toInt()),
        IconOption("AliasSprite0000", R.mipmap.ic_alias_0000, "Небо", "Чистый голубой фон и фирменное облако", 0xFF277BE8.toInt()),
        IconOption("AliasSprite0001", R.mipmap.ic_alias_0001, "Полночь", "Глубокий тёмно-синий вариант", 0xFF0C1738.toInt()),
        IconOption("AliasSprite0002", R.mipmap.ic_alias_0002, "Аврора", "Сине-фиолетовый градиент", 0xFF6548F5.toInt()),
        IconOption("AliasSprite0004", R.mipmap.ic_alias_0004, "Мята", "Спокойный бирюзовый вариант", 0xFF008D78.toInt()),
        IconOption("AliasSprite0005", R.mipmap.ic_alias_0005, "Жемчуг", "Светлый нейтральный фон", 0xFFF2F5FC.toInt())
    )

    val ICON_PREVIEWS: List<Int> = ICON_OPTIONS.map(IconOption::previewRes)

    @DrawableRes
    fun iconPreviewByIndex(index: Int): Int = ICON_OPTIONS.getOrElse(index) { ICON_OPTIONS.first() }.previewRes

    fun iconTitleByIndex(index: Int): String = ICON_OPTIONS.getOrElse(index) { ICON_OPTIONS.first() }.title

    fun iconDescriptionByIndex(index: Int): String = ICON_OPTIONS.getOrElse(index) { ICON_OPTIONS.first() }.description

    fun iconBackgroundByIndex(index: Int): Int = ICON_OPTIONS.getOrElse(index) { ICON_OPTIONS.first() }.backgroundColor

    private fun aliases(context: Context): List<String> {
        val pkg = context.packageName
        return SELECTABLE_ALIASES.map { "$pkg.$it" }
    }

    private fun allAliases(context: Context): List<String> {
        val pkg = context.packageName
        return ALL_ALIAS_SUFFIXES.map { "$pkg.$it" }
    }

    /**
     * Переключает иконку приложения на выбранный индекс.
     * ПРЕДУПРЕЖДЕНИЕ: Это действие обычно приводит к закрытию приложения системой.
     */
    fun setAppIcon(context: Context, targetIndex: Int) {
        val aliases = aliases(context)
        if (targetIndex !in aliases.indices) {
            Log.e("AppIconManager", "Invalid icon index: $targetIndex")
            return
        }

        val pm = context.packageManager

        // Сначала включаем новый alias, затем выключаем остальные.
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(context, aliases[targetIndex]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }.onFailure { e ->
            Log.e("AppIconManager", "Failed to enable target alias index=$targetIndex", e)
            return
        }

        val selectedAlias = aliases[targetIndex]
        allAliases(context).forEach { aliasName ->
            if (aliasName == selectedAlias) return@forEach
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(context, aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }.onFailure { e ->
                Log.e("AppIconManager", "Failed to disable alias: $aliasName", e)
            }
        }

        Log.d("AppIconManager", "App icon switched to index: $targetIndex")
    }

    /**
     * Получает текущий индекс включенного алиаса
     */
    fun getCurrentIconIndex(context: Context): Int {
        val pm = context.packageManager
        val aliasNames = aliases(context)

        aliasNames.forEachIndexed { index, aliasName ->
            runCatching {
                val state = pm.getComponentEnabledSetting(ComponentName(context, aliasName))
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return index
                }
            }.onFailure { e ->
                Log.w("AppIconManager", "Alias not found or inaccessible: $aliasName", e)
            }
        }

        aliasNames.forEachIndexed { index, aliasName ->
            runCatching {
                val component = ComponentName(context, aliasName)
                val state = pm.getComponentEnabledSetting(component)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    pm.getActivityInfo(component, 0).enabled
                ) {
                    return index
                }
            }.onFailure { e ->
                Log.w("AppIconManager", "Alias not found or inaccessible: $aliasName", e)
            }
        }

        return PreferencesManager(context).selectedAppIcon.coerceIn(ICON_OPTIONS.indices)
    }

    /**
     * Страховка от состояния, где все alias выключены или битые после обновления/смены packageName.
     */
    fun ensureValidAliasState(context: Context, preferredIndex: Int) {
        val pm = context.packageManager
        val aliases = aliases(context)

        var hasEnabled = false
        aliases.forEach { aliasName ->
            runCatching {
                val state = pm.getComponentEnabledSetting(ComponentName(context, aliasName))
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    hasEnabled = true
                }
            }
        }

        if (!hasEnabled) {
            val safeIndex = preferredIndex.coerceIn(0, aliases.lastIndex)
            Log.w("AppIconManager", "No enabled alias found. Recovering with index=$safeIndex")
            setAppIcon(context, safeIndex)
        }
    }
}

