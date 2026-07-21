package com.danila.nimbo.ui.i18n

/** Maps internal component tags to sources that make sense in user diagnostics. */
object LogPresentation {
    fun source(tag: String, isEnglish: Boolean): String {
        fun text(ru: String, en: String) = if (isEnglish) en else ru

        return when (tag) {
            "MyVpnService", "VpnManager", "XrayManager" -> text("VPN", "VPN")
            "SubscriptionManager", "SubscriptionUpdateWorker" -> text("Подписки", "Subscriptions")
            "PingManager" -> text("Проверка серверов", "Server checks")
            "UpdateManager", "UpdateWorker" -> text("Обновления", "Updates")
            "Logger" -> text("Диагностика", "Diagnostics")
            else -> text("Система", "System")
        }
    }
}
