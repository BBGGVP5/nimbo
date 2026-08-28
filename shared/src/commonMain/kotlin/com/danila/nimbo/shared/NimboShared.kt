package com.danila.nimbo.shared

/** Stable public entry point consumed by Android, iOS and Desktop shells. */
object NimboShared {
    const val schemaVersion: Int = 1

    fun runtimeLabel(platform: String): String = "Nimbo shared/$schemaVersion ($platform)"
}
