package com.danila.nimbo.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.danila.nimbo.utils.PreferencesManager
import kotlin.math.absoluteValue

/**
 * In-Compose translator that reacts to runtime language changes.
 *
 * Activity attachBaseContext picks up [PreferencesManager.appLanguage] and rewraps
 * the configuration, so [LocalConfiguration].locales reflects the user's choice
 * the moment Settings → Язык calls `Activity.recreate()`.
 *
 * Usage: `Text(t("Настройки", "Settings"))`
 */
@Composable
fun t(ru: String, en: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (locale.language == "en") en else ru
}

/**
 * Non-Compose counterpart for VPN service notifications and other places where
 * we don't have a Compose context. Resolves against the user's stored preference
 * directly because Service contexts don't get the activity's locale override.
 */
fun tNon(context: Context, ru: String, en: String): String {
    val lang = PreferencesManager(context).appLanguage
    return if (lang == "en") en else ru
}

/**
 * Locale-aware string picker without any context. Works for callbacks fired
 * inside Activity scope (lambdas, ViewModel calls) where MainActivity has
 * already done `Locale.setDefault(...)` in attachBaseContext. Don't use this
 * in Services — pass a Context and call [tNon] instead.
 */
fun loc(ru: String, en: String): String =
    if (java.util.Locale.getDefault().language == "en") en else ru

fun formatRussianCount(
    count: Int,
    singular: String,
    paucal: String,
    plural: String
): String {
    val absoluteCount = count.toLong().absoluteValue
    val lastTwoDigits = absoluteCount % 100
    val lastDigit = absoluteCount % 10
    val word = when {
        lastTwoDigits in 11L..14L -> plural
        lastDigit == 1L -> singular
        lastDigit in 2L..4L -> paucal
        else -> plural
    }
    return "$count $word"
}

fun formatEnglishCount(count: Int, singular: String, plural: String): String =
    "$count ${if (count.toLong().absoluteValue == 1L) singular else plural}"

fun serverCountRu(count: Int): String =
    formatRussianCount(count, "сервер", "сервера", "серверов")

fun serverCountEn(count: Int): String =
    formatEnglishCount(count, "server", "servers")

fun subscriptionCountRu(count: Int): String =
    formatRussianCount(count, "подписка", "подписки", "подписок")

fun subscriptionCountEn(count: Int): String =
    formatEnglishCount(count, "subscription", "subscriptions")
