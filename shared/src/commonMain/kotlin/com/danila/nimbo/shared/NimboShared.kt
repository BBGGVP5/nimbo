package com.danila.nimbo.shared

import com.danila.nimbo.shared.subscription.SubscriptionPayloadParser

/** Stable public entry point consumed by Android, iOS and Desktop shells. */
object NimboShared {
    const val schemaVersion: Int = 2

    fun runtimeLabel(platform: String): String = "Nimbo shared/$schemaVersion ($platform)"

    /** Stable JSON bridge for Swift and Desktop shells. Secrets remain in the returned model. */
    fun parseSubscriptionPayload(payload: String, source: String? = null): String =
        SubscriptionPayloadParser.parseToJson(payload, source)
}
