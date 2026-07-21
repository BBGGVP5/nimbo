package com.danila.nimbo.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import com.danila.nimbo.R

object AppIconManager {

    private val ALIAS_SUFFIXES = listOf(
        "AliasDefault",
        "AliasSprite0000",
        "AliasSprite0001",
        "AliasSprite0002",
        "AliasSprite0004",
        "AliasSprite0005",
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

    val ICON_PREVIEWS = listOf(
        R.mipmap.ic_launcher_nimbo_blue_v2,
        R.mipmap.ic_alias_0000,
        R.mipmap.ic_alias_0001,
        R.mipmap.ic_alias_0002,
        R.mipmap.ic_alias_0004,
        R.mipmap.ic_alias_0005,
        R.mipmap.ic_alias_0006,
        R.mipmap.ic_alias_0007,
        R.mipmap.ic_alias_0008,
        R.mipmap.ic_alias_0009,
        R.mipmap.ic_alias_0010,
        R.mipmap.ic_alias_0011,
        R.mipmap.ic_alias_0012,
        R.mipmap.ic_alias_0013,
        R.mipmap.ic_alias_0014,
        R.mipmap.ic_alias_0015,
        R.mipmap.ic_alias_0016,
        R.mipmap.ic_alias_0017,
        R.mipmap.ic_alias_0018
    )

    @DrawableRes
    fun iconPreviewByIndex(index: Int): Int = ICON_PREVIEWS.getOrElse(index) { R.mipmap.ic_launcher_nimbo_blue_v2 }

    fun iconTitleByIndex(index: Int): String = when (index) {
        0 -> "Nimbo Cloud"
        1 -> "Leather"
        2 -> "Graphite"
        3 -> "Smoke"
        4 -> "Steel"
        5 -> "Ocean"
        6 -> "Emerald"
        7 -> "Aurora"
        8 -> "Amethyst"
        9 -> "Frost"
        10 -> "Oak"
        11 -> "Walnut"
        12 -> "Neon Pulse"
        13 -> "Cyber Glow"
        14 -> "Pixel Hero"
        15 -> "Amber"
        16 -> "Indigo"
        17 -> "Violet"
        18 -> "Teal"
        else -> "Nimbo Cloud"
    }

    fun iconDescriptionByIndex(index: Int): String = when (index) {
        0 -> "Фирменное облачко Nimbo с сине-графитовым фоном"
        1 -> "Кожаная фактура и теплый винтажный акцент"
        2 -> "Тёмный металлический стиль для строгого вида"
        3 -> "Дымный эффект и атмосферная мягкая текстура"
        4 -> "Холодная сталь с индустриальным характером"
        5 -> "Глубокие синие оттенки, вдохновленные океаном"
        6 -> "Яркий зелёный щит с энергичным контрастом"
        7 -> "Сине-фиолетовый градиент в футуристичном тоне"
        8 -> "Благородный фиолетовый стиль с мягким свечением"
        9 -> "Ледяная палитра с эффектом холодного стекла"
        10 -> "Тёплая древесная текстура, натуральный стиль"
        11 -> "Тёмное дерево с аккуратной премиальной подачей"
        12 -> "Неоновый контур в духе ретро-future"
        13 -> "Киберпанк-свечение и насыщенный контраст"
        14 -> "Пиксель-арт щит для олдскульного вайба"
        15 -> "Золотисто-янтарный акцент с мягким теплом"
        16 -> "Глубокий индиго: спокойный и технологичный"
        17 -> "Выразительный фиолетовый для яркого образа"
        18 -> "Свежий бирюзовый, чистый и современный"
        else -> "Фирменный стиль NebulaGuard"
    }

    private fun aliases(context: Context): List<String> {
        val pkg = context.packageName
        return ALIAS_SUFFIXES.map { "$pkg.$it" }
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

        aliases.forEachIndexed { index, aliasName ->
            if (index == targetIndex) return@forEachIndexed
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

        return PreferencesManager(context).selectedAppIcon.coerceIn(ICON_PREVIEWS.indices)
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

