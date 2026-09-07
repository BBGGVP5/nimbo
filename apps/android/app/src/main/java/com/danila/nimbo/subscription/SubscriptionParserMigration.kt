package com.danila.nimbo.subscription

import com.danila.nimbo.shared.subscription.SubscriptionParserMigration as SharedSubscriptionParserMigration

/**
 * Версия именно формата/логики разбора подписок, а не версия приложения.
 *
 * При исправлении парсера это число нужно увеличить. Тогда уже сохранённые
 * подписки будут один раз и без уведомлений пересобраны новым кодом.
 */
object SubscriptionParserMigration {
    const val CURRENT_REVISION: Int = SharedSubscriptionParserMigration.currentRevision

    fun needsMigration(parserRevision: Int): Boolean =
        SharedSubscriptionParserMigration.needsMigration(parserRevision)

    fun pendingUrls(profiles: List<Pair<String, Int>>): List<String> =
        SharedSubscriptionParserMigration.pendingUrls(profiles)
}
