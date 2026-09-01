package com.danila.nimbo.network

import com.danila.nimbo.model.UpdateKind

/** Pure policy that prevents an undeliverable notification from being consumed. */
internal object UpdateNotificationPolicy {
    const val REPAIR_REMINDER_INTERVAL_MS = 12L * 60L * 60L * 1000L

    /**
     * Раньше уведомление о версии показывалось ровно один раз: как только
     * артефакт попадал в lastIdentity, повторов не было никогда. Если человек
     * смахнул карточку, был в «Не беспокоить» или просто не увидел её, обновление
     * молча терялось — а у давно не открывавшегося приложения та единственная
     * попытка приходилась на давнее прошлое. Теперь о той же версии напоминаем
     * ещё несколько раз с суточным интервалом.
     */
    const val VERSION_REMINDER_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val MAX_VERSION_REMINDERS = 3

    fun canPost(
        permissionGranted: Boolean,
        appNotificationsEnabled: Boolean,
        channelEnabled: Boolean
    ): Boolean = permissionGranted && appNotificationsEnabled && channelEnabled

    fun shouldPost(
        identity: String,
        lastIdentity: String?,
        kind: UpdateKind,
        lastNotifiedAt: Long,
        now: Long,
        notifiedCount: Int = 0,
        skippedIdentity: String? = null
    ): Boolean {
        // Пользователь нажал «Позже» именно для этой сборки — не докучаем.
        if (skippedIdentity != null && skippedIdentity == identity) return false
        if (identity != lastIdentity) return true

        val elapsed = (now - lastNotifiedAt).coerceAtLeast(0L)
        if (kind == UpdateKind.REPAIR) {
            return elapsed >= REPAIR_REMINDER_INTERVAL_MS
        }
        if (notifiedCount >= MAX_VERSION_REMINDERS) return false
        return elapsed >= VERSION_REMINDER_INTERVAL_MS
    }
}
