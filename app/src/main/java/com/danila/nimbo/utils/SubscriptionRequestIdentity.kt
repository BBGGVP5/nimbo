package com.danila.nimbo.utils

import java.util.UUID

/** Canonical request identity expected by Nimbo subscription response rules. */
object SubscriptionRequestIdentity {
    fun userAgent(version: String): String = "Nimbo/${version.trim()}/Android"

    /** UUID.toString() both validates the value and normalizes it to lowercase. */
    fun canonicalUuid(value: String): String = UUID.fromString(value.trim()).toString()
}
