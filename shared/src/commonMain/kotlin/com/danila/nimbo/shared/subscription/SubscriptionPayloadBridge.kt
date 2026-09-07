@file:Suppress("FunctionName", "unused")

package com.danila.nimbo.shared.subscription

/** Explicit top-level bridge name kept stable for Swift generated headers. */
fun NimboParseSubscriptionPayload(payload: String, source: String?): String =
    SubscriptionPayloadParser.parseToJson(payload, source)
