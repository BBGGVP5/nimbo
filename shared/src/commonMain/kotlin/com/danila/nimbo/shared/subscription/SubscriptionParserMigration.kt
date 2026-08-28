package com.danila.nimbo.shared.subscription

/**
 * Version of the subscription parsing contract rather than the app version.
 * Incrementing it silently reparses already stored subscriptions after an update.
 */
object SubscriptionParserMigration {
    const val currentRevision: Int = 1

    fun needsMigration(parserRevision: Int): Boolean = parserRevision < currentRevision

    fun pendingUrls(profiles: List<Pair<String, Int>>): List<String> = profiles
        .asSequence()
        .filter { (_, revision) -> needsMigration(revision) }
        .map { (url, _) -> url.trim() }
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
}
